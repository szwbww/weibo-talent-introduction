# Repair Execution: meeting-mail-master

- Plan: docs/plans/fix/meeting-mail-master/repair.md
- Plan canonical path: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-meeting-mail-master/docs/plans/fix/meeting-mail-master/repair.md
- Plan SHA-256: `38bf452eefd30f7ac09925bd29e296167a6e2dfa8150a7e74c464e2fe963bfe2`
- Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-meeting-mail-master/docs/plans/fix/meeting-mail-master/repair.md@38bf452eefd30f7ac09925bd29e296167a6e2dfa8150a7e74c464e2fe963bfe2`
- Execution epoch: NEW (no prior commit or report named this plan identity; no `fix(meeting-mail): reject blank outbound attachment snapshots` commit existed and this file did not exist before this invocation)
- Approval basis: human invocation `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-meeting-mail-master/docs/plans/fix/meeting-mail-master/repair.md` on 2026-09-17. The plan's own `## Review-Fast-P Execution Handoff` names exactly this invocation as the authorizing approval, so no separate approval artifact exists.
- Executor: `Main` (single inline executor for this `execute-p` invocation)
- Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-meeting-mail-master
- Target branch: fast/meeting-mail-master
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-meeting-mail-master@fast/meeting-mail-master@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-meeting-mail-master`
- Pre-execution code SHA: `b104987e5e81267b6087d7665eb21619e6f42711`
- Post-execution code SHA: `06dfb878f68e909540e9ef7c4ea63e519beded6e`
- Implementation boundary: `b104987e5e81267b6087d7665eb21619e6f42711..06dfb878f68e909540e9ef7c4ea63e519beded6e`
- Evidence HEAD: the docs-only commit `docs(review-fast-p): record repair execution` carrying this file (see `git log` on the branch; this file cannot record its own SHA)

## Task Status

| Requirement | Status | Files | Evidence |
|---|---|---|---|
| R-1 (V-1) Reject blank persisted snapshots | IMPLEMENTED | `OutboundAttachmentModels.kt`, `OutboundAttachmentServiceTest.kt` | `parseOrThrow` returns null only for SQL NULL and throws `OutboundAttachmentException` (409) for any non-null blank input; the codec test asserts 409 for `""` and `"   "` while `parseOrNull` stays null |

## Change

- `parseOrThrow`: `if (json.isNullOrBlank()) return null` replaced by `if (json == null) return null` followed by `if (json.isBlank()) throw OutboundAttachmentException.conflict("通用附件快照已损坏：空白不是「没有附件」")`. Null remains the only absence signal; every non-null blank value is corruption.
- Object/function KDoc corrected: SQL NULL is the sole no-outbound-attachment representation; blank strings are no longer listed as an absence form.
- Test `snapshot codec round trips ordered fields and treats null as absent`: the two blank-as-null assertions were removed; `parseOrThrow(null)`/`parseOrNull(null)` stay.
- Test `snapshot codec rejects empty corrupt oversize and unknown shapes`: `""` and `"   "` added to the corrupt-case list, whose loop already asserts a 409 `OutboundAttachmentException` from `parseOrThrow` and `null` from `parseOrNull`.
- No schema, migration, controller, API, UI, storage, fingerprint or MIME change; `parseOrNull`'s presentation semantics (null for anything unusable) are unchanged.

## Changed Files

- `src/main/kotlin/com/weibo/talentintroduction/mail/service/OutboundAttachmentModels.kt` — strict parse distinguishes NULL from non-null blank corruption.
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/OutboundAttachmentServiceTest.kt` — discriminating regression assertions for empty-string and whitespace snapshots.

Both files are exactly the plan's Authorized Files; the product commit staged nothing else (`git show --name-only 06dfb87` returns these two paths).

## Commands

| # | Command | Result | Evidence |
|---|---|---|---|
| 1 | `JAVA_HOME=<zulu-11> PATH=<zulu-11>/bin:$PATH mvn test -Dtest=OutboundAttachmentServiceTest` | PASS | exit 0, `BUILD SUCCESS`; `OutboundAttachmentServiceTest` 28 run / 0 failures / 0 errors / 0 skipped; the same invocation also ran the bound JS stage 944/944 |
| 2 | `... mvn test -Dtest=ManualReplySendAttemptServiceTest,OutboundAttachmentFlowTest,MailboxConversationControllerTest -DmysqlIt=true -Dapi.version=1.44 -DfailIfNoTests=false` | PASS | exit 0, `BUILD SUCCESS`; 92 run / 0 failures / 0 errors / 0 skipped (ManualReply 56, Flow 9, MailboxConversation 27 against real MySQL) |
| 3 | `node --test src/test/js/*.test.js` | PASS | exit 0; tests 944 / suites 183 / pass 944 / fail 0 / skipped 0 |
| 4 | `... DOCKER_API_VERSION=1.44 mvn test` | PASS | `MVN_EXIT=0`, `BUILD SUCCESS`; `Tests run: 3452, Failures: 0, Errors: 0, Skipped: 13` |
| 5 | `... DOCKER_API_VERSION=1.44 mvn test -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true -Dapi.version=1.44` | FAIL (excluded, pre-existing) | exit 1; 23 run / 0 failures / 1 error / 0 skipped; sole error `V124 allows material attached promotion audit trigger:233->execute:1216 SQLIntegrityConstraintViolation` on `fk_eap_contact`. The plan lists this finding as **Excluded** (outside the implementation boundary). Not repaired, not reclassified. |

Red/green evidence for the behavioral change: with the pre-repair `isNullOrBlank` behaviour restored temporarily, `mvn test -Dtest=OutboundAttachmentServiceTest -DskipNodeTests=true` failed with `Tests run: 28, Failures: 1` — `snapshot codec rejects empty corrupt oversize and unknown shapes(OutboundAttachmentServiceTest.kt:502)`: `Expected ... OutboundAttachmentException to be thrown, but nothing was thrown`. The fixed file was restored byte-identically (md5 `c58e293281e29a1fd54bc29729b52644` before and after) and the suite re-ran green.

## Environment

- JDK 11 (`zulu-11`) for every Maven command; local MySQL 8.0.46 container `ti-mysql-it` on `127.0.0.1:3306` (database `talent_introduction`) for the `-DmysqlIt=true` group; Docker/OrbStack with `DOCKER_API_VERSION=1.44` for testcontainers.
- Commands 1-5 all ran freshly in this invocation, after the final implementation state.

## Deviations

- **Flaky full-suite test, not repaired (out of authorized scope).** One full-suite run (command 4, first attempt) failed on `UnmatchedInboundAiReplyTurnKnowledgeTest.real endpoint keeps same phase progress at one hertz:1090` with `expected: <1> but was: <2>`. In isolation the same class passes (`Tests run: 37, Failures: 0, Errors: 0`), and two subsequent full runs passed with `3452 / 0 / 0 / 13`, so the assertion is timing-sensitive under full-suite load (a 1 Hz progress-throttle window elapsing between two `onActivity` calls). It touches the AI-reply progress reporter, not the snapshot codec, and the file is not authorized by this plan.
- No other deviation: no extra files, no history rewrite, no push/merge, and the plan identity was unchanged before and after execution.

## Freshness

- Plan identity rechecked: YES (recomputed `38bf452eefd30f7ac09925bd29e296167a6e2dfa8150a7e74c464e2fe963bfe2`, unchanged)
- Worktree identity rechecked: YES (`--expect-root`/`--expect-branch`/`--expect-git-dir` matched before staging and committing)
- Reported commits reachable from target branch: YES (`06dfb87` is `HEAD` of `fast/meeting-mail-master` at product-commit time; the evidence commit follows it)
- Required commands run this invocation: YES (all five; command 5's failure is the plan's excluded pre-existing finding)
- Historical evidence used only as baseline: YES (the earlier fast-p run's 3452/0/0/13 is corroborated, not substituted — the same counts were produced freshly here)
