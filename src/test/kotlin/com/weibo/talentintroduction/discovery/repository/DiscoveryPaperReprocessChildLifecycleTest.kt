package com.weibo.talentintroduction.discovery.repository

import com.weibo.talentintroduction.discovery.domain.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

/** Real MySQL regression for failed parent / still-RUNNING child review slots. */
@EnabledIfSystemProperty(named = "paperReprocessIt", matches = "true")
class DiscoveryPaperReprocessChildLifecycleTest {
    private val f = ReprocessMysqlFixture
    private val verification get() = DiscoveryVerificationRepository(f.jdbc)
    private val repository get() = f.repository()
    private fun tx(action: () -> Unit) { TransactionTemplate(f.manager).executeWithoutResult { action() } }
    @BeforeEach fun clean() {
        f.jdbc.update("DELETE FROM discovery_verification_step")
        f.clean()
    }

    private fun parent(): ReprocessRun {
        val source = f.task()
        f.seedPapers(source, 1)
        val created = f.create(source)
        f.jdbc.update("""UPDATE discovery_paper_reprocess_run SET mode='ENFORCE',state='RUNNING',
            scope_json=JSON_SET(scope_json,'$.verificationSettings',JSON_OBJECT()) WHERE execution_id=?""", created.executionId)
        return repository.claimRun("fixture-worker")!!
    }

    private fun child(id: Long, parent: Long? = null, phase: String = "RUNNING") {
        f.task(id)
        f.jdbc.update("UPDATE task_execution SET task_type='DISCOVERY_RECHECK_PREPARE' WHERE id=?", id)
        val settings = parent?.let { """{"paperReprocessParentId":$it}""" } ?: "{}"
        tx {
            verification.createRecheckRun(id, VerificationMode.ENFORCE, false, settings,
                RecheckSpec("paper-reprocess", "request-$id", "{}", RecheckEvidenceMode.REUSE,
                    Instant.now(), "fixture-code", "fixture-parser"), Instant.now())
            f.jdbc.update("""UPDATE discovery_verification_run SET input_closed=1,
                recheck_json=JSON_SET(recheck_json,'$.control.phase',?,'$.control.scopeHash',REPEAT('c',64))
                WHERE execution_id=?""", phase, id)
        }
    }

    private fun item(run: Long, state: String, epoch: Long = run, admitted: Boolean = false): Long {
        val now = Instant.now().verificationUtc()
        f.jdbc.update("""INSERT INTO discovery_verification_item
            (owner_execution_id,expert_doc_id,identity_hash,policy_version,review_epoch,source_name,source_item_key,
            batch_number,snapshot_json,fact_fingerprint,state,evidence_json,admission_revision,
            lease_token,lease_until,next_attempt_at,created_at,updated_at)
            VALUES (?,?,?,?,?,'OPENALEX','fixture',1,'{}',?,?,?,?,'old-worker',?,?,?,?)""",
            run, "doc-$run-$state", "a".repeat(64), VerificationMode.ENFORCE.policyVersion, epoch, "b".repeat(64), state, """{"proof":"keep"}""",
            if (admitted) 17L else null, now.plusMinutes(5), now.plusMinutes(5), now, now)
        if (state == "RESERVED") f.jdbc.update("UPDATE discovery_verification_item SET batch_number=0 WHERE expert_doc_id=?", "doc-$run-$state")
        if (state in setOf("COMPLETED", "ERROR")) f.jdbc.update("UPDATE discovery_verification_item SET completed_at=? WHERE expert_doc_id=?", now, "doc-$run-$state")
        if (state == "COMPLETED") f.jdbc.update("""UPDATE discovery_verification_item SET outcome='DIRECT_CLEAR',
            qualification='MET',route='DIRECT' WHERE expert_doc_id=?""", "doc-$run-$state")
        return f.jdbc.queryForObject("SELECT id FROM discovery_verification_item WHERE expert_doc_id=?",
            Long::class.java, "doc-$run-$state")!!
    }

    private fun phase(id: Long) = verification.recheck(verification.findRun(id)!!).control.phase

    @Test fun `failure atomically closes ordinary and child work without changing completed evidence`() {
        val parent = parent()
        tx { verification.createRun(parent.executionId, VerificationMode.ENFORCE, false,
            """{"paperReprocessParentId":${parent.executionId}}""", Instant.now()) }
        val ordinary = item(parent.executionId, "RUNNING", 0)
        child(9500, parent.executionId)
        val unfinished = listOf("RESERVED", "QUEUED", "RUNNING", "RETRY_WAIT").map { item(9500, it) }
        val completed = item(9500, "COMPLETED", admitted = true)
        val errored = item(9500, "ERROR")
        val before = listOf(completed, errored).map { verification.findItem(it) }
        assertEquals(listOf(9500L), verification.activeRechecks("paper-reprocess").map { it.executionId })

        assertTrue(repository.finish(parent.executionId, parent.leaseToken!!, parent.generation,
            ReprocessState.FAILED, "FIXTURE_FAILURE"))
        assertEquals(RecheckPhase.CANCELLED, phase(9500))
        (unfinished + ordinary).forEach {
            val row = verification.findItem(it)!!
            assertEquals(VerificationState.CANCELLED, row.state)
            assertNull(row.leaseToken); assertNull(row.leaseUntil); assertNull(row.nextAttemptAt)
        }
        assertTrue(verification.findRun(parent.executionId)!!.inputClosed)
        assertEquals(before, listOf(completed, errored).map { verification.findItem(it) })
        assertTrue(verification.activeRechecks("paper-reprocess").isEmpty())
        assertFalse(verification.paperRunPermission(9500))
        tx { assertFalse(verification.renewLease(unfinished[2], "old-worker", 0, Instant.now(), 60)) }
        assertEquals("FAILED", f.jdbc.queryForObject("SELECT status FROM task_execution WHERE id=?", String::class.java, parent.executionId))
        // The successful preparation audit is not rewritten as a failed verification audit.
        assertEquals("SUCCESS", f.jdbc.queryForObject("SELECT status FROM task_execution WHERE id=9500", String::class.java))
    }

    @Test fun `historical orphan is ignored by reads and reconciled without changing unrelated work`() {
        val parent = parent()
        child(9500, parent.executionId); val abandoned = item(9500, "RUNNING")
        child(9501); val unrelated = item(9501, "QUEUED")
        val before = verification.findItem(unrelated)
        f.jdbc.update("UPDATE discovery_paper_reprocess_run SET state='FAILED',active_key=NULL WHERE execution_id=?", parent.executionId)
        assertEquals(listOf(9501L), verification.activeRechecks("paper-reprocess").map { it.executionId })
        assertEquals(RecheckPhase.RUNNING, phase(9500)) // GET does not mutate.
        tx { verification.reconcileRecheck(9500, Instant.now()) }
        assertEquals(RecheckPhase.CANCELLED, phase(9500))
        assertEquals(VerificationState.CANCELLED, verification.findItem(abandoned)!!.state)
        assertEquals(before, verification.findItem(unrelated))
        assertEquals(RecheckPhase.RUNNING, phase(9501))
        val settled = verification.findRun(9500)
        tx { verification.reconcileRecheck(9500, Instant.now()) }
        assertEquals(settled, verification.findRun(9500))
    }

    @Test fun `expired lease alone retains slot until parent recovery records interruption`() {
        val parent = parent(); child(9500, parent.executionId); item(9500, "RUNNING")
        f.jdbc.update("UPDATE discovery_paper_reprocess_run SET lease_until=? WHERE execution_id=?",
            Instant.now().minusSeconds(10).verificationUtc(), parent.executionId)
        assertEquals(listOf(9500L), verification.activeRechecks("paper-reprocess").map { it.executionId })
        tx { verification.reconcileRecheck(9500, Instant.now()) }
        assertEquals(RecheckPhase.RUNNING, phase(9500))
        assertNull(repository.claimRun("recovery-worker"))
        assertEquals(ReprocessState.INTERRUPTED, repository.find(parent.executionId)!!.state)
        assertEquals(RecheckPhase.CANCELLED, phase(9500))
        assertTrue(verification.activeRechecks("paper-reprocess").isEmpty())
    }

    @Test fun `cancel closes child immediately and explicit retry preserves completed peers`() {
        val parent = parent(); child(9500, parent.executionId)
        val pending = item(9500, "QUEUED")
        val complete = item(9500, "COMPLETED", admitted = true)
        val before = verification.findItem(complete)
        repository.cancel(parent.executionId, repository.find(parent.executionId)!!.revision)
        assertEquals(RecheckPhase.CANCELLED, phase(9500))
        tx { verification.cancelPaperReprocessChildren(parent.executionId) } // existing service call stays idempotent
        assertEquals(before, verification.findItem(complete))
        // Existing explicit parent retry reinstates permission before resuming its own child.
        f.jdbc.update("UPDATE discovery_paper_reprocess_run SET state='RUNNING',cancel_requested=0 WHERE execution_id=?", parent.executionId)
        tx { assertTrue(verification.resumeCancelledPaperRecheck(9500, parent.executionId, Instant.now())) }
        assertEquals(VerificationState.QUEUED, verification.findItem(pending)!!.state)
        assertEquals(before, verification.findItem(complete))
        assertEquals(RecheckPhase.RUNNING, phase(9500))
    }

    @Test fun `ready paper orphan is scheduled for cleanup but manual ready review is retained`() {
        val parent = parent(); child(9500, parent.executionId, "READY"); item(9500, "RESERVED")
        child(9501, phase = "READY")
        assertTrue(9500L in verification.unsettledRechecks())
        assertFalse(9501L in verification.unsettledRechecks())
        tx { verification.reconcileRecheck(9500, Instant.now()) }
        assertEquals(RecheckPhase.READY, phase(9500))
        repository.finish(parent.executionId, parent.leaseToken!!, parent.generation, ReprocessState.FAILED)
        assertEquals(RecheckPhase.CANCELLED, phase(9500))
        assertEquals(RecheckPhase.READY, phase(9501))
    }

    @Test fun `child cleanup failure rolls back parent termination and preserves ownership`() {
        val parent = parent(); child(9500, parent.executionId); item(9500, "RUNNING")
        val before = repository.find(parent.executionId)
        val failing = org.mockito.Mockito.mock(DiscoveryVerificationRepository::class.java)
        org.mockito.Mockito.doThrow(IllegalStateException("fixture child cleanup failure"))
            .`when`(failing).settleStoppedPaperReprocess(org.mockito.Mockito.eq(parent.executionId),
                org.mockito.Mockito.any(Instant::class.java) ?: Instant.EPOCH)
        val failingRepository = DiscoveryPaperReprocessRepository(f.jdbc, f.manager, f.mapper, f.tasks, failing)
        val error = assertThrows(IllegalStateException::class.java) {
            failingRepository.finish(parent.executionId, parent.leaseToken!!, parent.generation, ReprocessState.FAILED)
        }
        assertEquals("fixture child cleanup failure", error.message)
        assertEquals(before, repository.find(parent.executionId))
        assertEquals(RecheckPhase.RUNNING, phase(9500))
        assertEquals("RUNNING", f.jdbc.queryForObject("SELECT status FROM task_execution WHERE id=?", String::class.java, parent.executionId))
    }
}
