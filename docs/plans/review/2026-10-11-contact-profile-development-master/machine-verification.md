# Aggregate Machine Verification — Contact Profile Development Master

## Epoch 1 — 2026-10-12T10:48:31+0800

- Master plan: `docs/plans/2026-10-11/contact-profile-development-master.md` (sha256 `3428e4e7f4c21c2cb5148e745884b988117719a84f478a52beee3a69d04fa204`)
- Governing master identity: worktree sha256 `3428e4e7f4c21c2cb5148e745884b988117719a84f478a52beee3a69d04fa204`; recorded `commit 91a79dcda9d4ddf55525c801f9c9b9b454321f63`
- Master identity state: `CONSISTENT`; invoked identity is the same. Recorded amendments A1–A4 were read from the selected fast-p ledger.
- Boundary: `96b89bb203019881a7fc36be557d30ad2a9c43a3..0e4b5a89507e03350cddb883f7266c494317e25f`
- Reviewer: `/root/aggregate_reviewer` (fresh reviewer; distinct from fast-p writers/verifiers recorded as Impl01–05 and Verify01–05)
- Result: `FAIL`
- Convergence: `INITIAL`
- Repair artifact/result: `docs/plans/fix/contact-profile-development-master/repair.md` — `DRAFT_READY`
- Manual acceptance: `PENDING`

## Verification Result: FAIL

Plan: `docs/plans/2026-10-11/contact-profile-development-master.md`

Implementation boundary: `96b89bb203019881a7fc36be557d30ad2a9c43a3..0e4b5a89507e03350cddb883f7266c494317e25f`

Convergence: `INITIAL`

Mandatory machine evidence was available. Initial sandbox target-directory write restrictions were resolved by rerunning the exact commands in the approved environment; this is not a product failure or evidence gap.

### Commands

| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=ExpertProfileIdentityResolverTest,ExpertManualProfileServiceTest,ExpertManualProfileControllerTest test` | PASS | exit 0; 37/37 |
| `DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.41 '-DargLine=-Dapi.version=1.41' -DmigrationIt=true -Dtest=FlywayMigrationIntegrationTest test` | PASS | exit 0; 44/44 |
| `node --test src/test/js/expertManualProfile.test.js` | PASS | exit 0; 24/24 |
| `node --test src/test/js/expertContactSharedNote.test.js src/test/js/expertManualProfile.test.js` | PASS | exit 0; 48/48 |
| `node --test src/test/js/expertMeetingVideo.test.js` | PASS | exit 0; 25/25 |
| `node --test src/test/js/*.test.js` | PASS | exit 0; 1641/1641 |
| `node --check src/main/resources/static/app.js` | PASS | exit 0 |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=ExpertMeetingVideoServiceTest,ExpertMeetingVideoControllerTest,ExpertMeetingVideoStreamingIntegrationTest test` | PASS | exit 0; 39/39; ordinary streaming layer 3 skipped |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DvideoLargeIt=true -Dtest=ExpertMeetingVideoServiceTest,ExpertMeetingVideoControllerTest,ExpertMeetingVideoStreamingIntegrationTest test` | PASS | exit 0; 39/39; 0 skipped; real 1 GiB path |

### Contract Matrix

| ID | Verdict | Evidence |
|---|---|---|
| O-1 | FAIL | 19-field profile works in the focused suite, but F-4 violates the required code-point field contract. |
| O-2 | PASS | 03 focused Node suite 48/48; contact identity/read path inspected. |
| O-3 | FAIL | F-1, F-2, and F-3 affect video upload lifecycle and known-name behavior. |
| O-4 | PASS | 01/02/03 focused suites and identity resolver cover contact and no-contact persistence paths. |
| MN-1 | PASS; human pending | Node full suite 1641/1641; static diff check. |
| MN-2 | PASS; human pending | Node full suite; non-authorized email, phase, meeting, material, and CV paths unchanged. |
| MN-3 | PASS | 03 focused Node suite confirms existing contact-owned note behavior. |
| MN-4 | PASS | Existing 100 MiB material and 10 MiB reply-attachment paths unchanged; full Node suite passed. |
| I-1 | PASS | 01/03/04 source and focused tests retain canonical expert storage and actual-contact notes. |
| I-2 | PASS | Only V153/V154 create the two private tables; Flyway migration suite 44/44. |
| I-3 | FAIL | F-3 violates the uncertain-submission state and F-4 violates required field validation. |
| I-4 | FAIL | F-1 violates two-slot lifecycle and F-2 violates pre-stream name precheck; remaining 1 GiB, 64 KiB, capacity, and Range evidence passed. |
| I-5 | FAIL; human pending | Ordered child/approval ledger is consistent, but the aggregate boundary has F-1–F-4. |

### Finding Lineage

| Finding | State | Evidence |
|---|---|---|
| F-1 | NEW | `ExpertMeetingVideoService.kt:145-146` cleanup can bypass semaphore release. |
| F-2 | NEW | `ExpertMeetingVideoService.kt:107,113` streams a known duplicate before its conflict response. |
| F-3 | NEW | `app.js:12775-12787,12885-12893` treats no progress as no submission after `xhr.send`. |
| F-4 | NEW | `app.js:11428,11799` native UTF-16 maxlength conflicts with code-point contract. |

### Findings

#### P1

- F-1: `src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMeetingVideoService.kt:145-146` calls `Files.deleteIfExists(tempPath)` before `uploadSlots.release()`. A cleanup `IOException` skips release and can permanently consume both upload slots, violating 04 I-6/I-9.
- F-2: `src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMeetingVideoService.kt:107,113` calls `writeStreamedFile` before the duplicate-key response. The required normalized expert/name precheck must run before body reading; the unique key remains the final race arbiter. This can consume 1 GiB and a slot for a known conflict.
- F-3: `src/main/resources/static/app.js:12775-12787,12885-12893` has sent the XHR but uses `uploadedBytes > 0` as submission proof. Abort, error, or timeout before first progress may have reached the service; retrying it violates 05 I-3/I-4 unknown-result rules.

#### P2

- F-4: `src/main/resources/static/app.js:11428,11799` emits native HTML `maxlength` while the explicit validator counts Unicode code points. Browser UTF-16 length can reject a legal astral-character value before the contract validator. This is a mandatory contract violation and is included in the repair.

#### Observations

- V154 comment drift: `src/main/resources/db/migration/V154__create_expert_meeting_video.sql:8` says production does not disable placeholder replacement, while `src/main/resources/application.yml:13` configures `false`. This is not a required behavior violation and is excluded from repair.

### Evidence Boundaries

- The complete code-boundary `git diff --check` reports only pre-existing whitespace in plan/copied-evidence documentation; product and test paths pass.
- Manual A-1 through A-5 are pending human checks and were not simulated.

### Fast-P RECORD_ONLY Re-evaluation

| Source item | Master requirement | Result | Evidence |
|---|---|---|---|
| Child 02 O-1 | I-3/I-5 cache-key/style serial contract | Resolved, no product finding | A1–A4 record rule, reason, and HUMAN approval; selected worktree plans/ledger agree. |
| Child 04 O-1 | I-4 pre-stream same-name protection | Promoted to F-2/P1 | Source at `ExpertMeetingVideoService.kt:107,113` confirms stream precedes duplicate handling. |
| Child 04 O-2 | Documentation accuracy | Observation | V154 line 8 conflicts with application config line 13; behavior unaffected. |

### Next Action

Verification failed with an eligible initial repair. See the bounded repair plan below. No product code was modified by this review.

## Repair Planning Result: DRAFT_READY

Baseline plan: `docs/plans/2026-10-11/contact-profile-development-master.md`

Verification result: `FAIL / INITIAL`

Repair artifact: `docs/plans/fix/contact-profile-development-master/repair.md`

### Included Findings

- F-1, F-2, F-3, F-4

### Excluded Findings

- V154 comment drift — non-behavioral observation outside the required repair scope.

### Required Human Decision

- Approve the exact repair artifact with `$execute-p docs/plans/fix/contact-profile-development-master/repair.md`.

No implementation was performed.
