# Child 10-history — Verify Log

## Light Verification: LIGHT_PASS_WITH_NOTES
Child: `10-history`; `docs/plans/2026-10-13/discovery-person-10-history.md`
Boundary: `657849948e400b054eb5b3c0c8d91c1e10260342..cd6a38d662da881ec453839921922a30602eacb3`
Verifier: Verify10

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | `git show --stat cd6a38d` = exactly 6 added files (Service 647, Controller 115, ServiceTest 484, ControllerTest 203, reconcile.py 452, test_reconcile.py 235; 2136 insertions, 0 deletions). `git diff --name-status 6578499..cd6a38d` other entries are only `docs/plans/fast/**` + `ledger.md` controller bookkeeping; no generated outputs (`__pycache__` absent). |
| Plan and invariants | PASS | I-1: `readMember` (`DiscoveryPersonMaintenanceService.kt:356`) re-reads RAW/CANDIDATE/APPLICATION via `readDiscoveryDocument`, tags/`identityVerification`/`seq_no`/`primary_term`, SQL relation revision; any read failure/non-discovery/layer-identity conflict/out-of-scope member → `CHANGED_OR_FAILED` (`:104-135`, `:242-249`); no ES write method used. I-2: `reviewGroup` (`:130`) whole-member checks (empty author keys/multi same-type keys/disconnected/bridge existing roots/relation-fingerprint change → `REVIEW_REQUIRED`); one commit per group via 04 `DiscoveryPersonService.register` → `DiscoveryPersonRepository.createPerson`/`attachToPerson` which are `@Transactional` (`DiscoveryPersonRepository.kt:83,111`); failure keeps `repository.persons` empty (test `a member that changed after preview fails the whole group...`, C1 11/11). I-3: `importHistoryProtection` (`:279`) is read-only `stateOf`+08 probe, never `reserveIntroduction`; `rollbackPreview` (`:306`) emits proposals only, `safeToRollback=false` on later members/`SENT`/`UNKNOWN`. Plan steps 1-6 present: preview classes+b per-email reasons (`:88-180`), `canMerge`≠`canSend` (`:176,512`), restricted apply (rule version+preview digest+per-group expectedFingerprint, `:183-228`), idempotent UNCHANGED, CLI atomic temp+`os.replace`+0700/0600 (`reconcile.py:102-137`) with no full email list in main report, breakpoint/resume (`completed_group_keys`), no production apply (no callers of the service outside the controller). |
| Required commands | PASS | C1 `mvn -Dtest=DiscoveryPersonMaintenanceServiceTest,DiscoveryPersonMaintenanceControllerTest test` exit 0; Surefire Service tests=11/errors=0/failures=0, Controller tests=7/errors=0/failures=0 (18 total); JS gate in same run `tests 1677 / pass 1677 / fail 0 / skipped 0`; BUILD SUCCESS. C2 `python3 -m unittest discover -s tools/discovery-person-recovery -p 'test_*.py'` exit 0, `Ran 14 tests — OK`. Counts match expected (~18 / ~14); no new failures. |
| Downstream interfaces | PASS (N/A) | `grep -rn DiscoveryPersonMaintenanceService src/main` shows only the controller; no other module depends on the new service/endpoints. Deliverables are artifacts for the separately authorized application stage; nothing downstream consumes a new API. |

### AUTO_FIX
- N/A

### RECORD_ONLY
- O-1 (D1) History-protection "import" is READ-ONLY. `importHistoryProtection` calls only `stateOf` (+08 admission probe) and writes no guard row. Adjudication: this preserves the intended OUTCOME — 09 `collectHistory` (called on every `reserve`) reads all members of the merged person (`findMembersByPerson`) for `mail_record` OUTBOUND INTRODUCTION SENT, `mail_send_attempt` SENT/DELIVERY_IN_PROGRESS/DELIVERY_UNKNOWN, `expert_contact` by orcid/email, and `expert_email_alias`, so an old successful/unknown send for any member still blocks the merged person's future first-touch. Writing a `RESERVED` row from the discarded ticket would permanently block first outreach, so read-only is the safe, outcome-preserving choice. No plan requirement genuinely unmet → RECORD_ONLY, flagged for human review.
- O-2 (D2) `previewDigest` binds rule version + sorted frozen docId set (`:498`), not mutable classification/evidence; per-group changes are caught by the per-group `expectedFingerprint` compare (`:292-296`). Adjudication: apply cannot force unrelated/stale changes — each group independently re-validated server-side; a changed member still fails its group's fingerprint/实时复核 without affecting siblings. Consistent with A-2 "移标组跳过". → RECORD_ONLY.
- O-3 `@Transactional` on `applyGroup` (`:231-232`) is self-invoked from `apply` (`:228`) and thus bypassed by the proxy; group atomicity is nevertheless provided by `DiscoveryPersonRepository.createPerson`/`attachToPerson`, each of which is itself `@Transactional`, and the only other step (`importHistoryProtection`) is read-only. No partial-merge risk proven; notation only.

### Required Action
- COMPLETE_CHILD
