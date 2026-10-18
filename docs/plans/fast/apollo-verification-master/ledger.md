# Fast-P Ledger — master: docs/plans/2026-10-18/apollo-verification-master.md

- Status: READY_FOR_HUMAN_REVIEW
- Master plan: docs/plans/2026-10-18/apollo-verification-master.md (commit 00c0c9e75926ec9a8a9487fccf40d35d6275df3f)
- Amendments: A1,A2,A3,A4,A5,A6,A7,A8,A9,A10,A11,A12
- Master base: 3efc192faf043badc1ef27bef124a71d3120c4c5
- Branch: fast/apollo-verification-master
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-apollo-verification-master
- Finalization mode: NORMAL
- Finalization repair parent: N/A
- Started: 2026-10-18T19:18:14.142629+08:00
- Current child: N/A
- Waiting role: N/A
- Agent attempt: 0
- Last agent error: N/A
- Pause reason: N/A
- Resume from: N/A

## Baseline
- Invocation authorizes exact untracked plan snapshots, copied byte-for-byte into isolated worktree. Original user worktree untouched.
- Audited base e7c3422c176be971d823e2b82b1bca63ebb04741; execution base 3efc192faf043badc1ef27bef124a71d3120c4c5. `git diff --name-status <audit-base> HEAD` returned only app.js, index.html, styles.css. Child 07 implementation step 1 explicitly preserves this concurrent manual-recipient-preview delta.
- Migration inventory `glob src/main/resources/db/migration/V15*`: V157 latest, no V158.
- Validator `python3 /Users/lukai/.agents/skills/fast-p/scripts/validate_fast_p.py --help`: exit 0, --through-child supported.
- Docker info initially failed because OrbStack stopped; `orbctl start` exit 0; subsequent `docker info --format '{{.ServerVersion}}'` exit 0, engine 29.8.2.
- JDK11 java -version exit 0: Zulu 11.0.15. Maven 3.9.11 available; JAVA_HOME explicitly set for all Maven commands.
- No actual provider secrets/account limits read; default-disabled offline implementation only. Runtime enabling and real-account limits remain human rollout checks, not inferred from design caps.
- Baseline `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryAdmissionPolicyTest`: exit 1. Kotlin: 22 run, 0 failures/errors/skipped. Bound Node suite: 1694 tests, 1653 pass, 41 fail, 0 skipped; Maven fails at exec:node-test. Raw session output: artifact://16. Failures include missing resetRecipientPreview/setRecipientPreviewState in extraction sandboxes and old debounce expectations; meetingConfirmationAssets rejects cache key containing preview. These are pre-existing baseline failures, not authorized unrelated repairs.

## Children
| ID | Plan | Plan identity | Depends on | Epoch | State | Base | Implementation | Fix round | Fix commits | Code head | Evidence commit | Notes |
|---|---|---|---|---:|---|---|---|---:|---|---|---|---|
| 01 | docs/plans/2026-10-18/apollo-01-ledger.md | sha256:72c5e37ba421270d7089a4702b2e019e206c8af4a6f0166882da0d77b86e917a | none | 1 | LIGHT_PASS_WITH_NOTES | 3efc192faf043badc1ef27bef124a71d3120c4c5 | 772429192fdab1fd7ac75cb084f08f45aec5c965 | 0 | — | 772429192fdab1fd7ac75cb084f08f45aec5c965 | 045c028426bbca4df579136be507d41a103e9876 | Writer ApolloLedgerImplementer; verifier ApolloLedgerVerifier; O-01 existing Node failures |
| 02 | docs/plans/2026-10-18/apollo-02-evidence.md | sha256:0dca87e1a9e6b61b247f78e54ebf60e71afde280556c50e6f06a07921d6b4b3f | 01 | 1 | LIGHT_PASS_WITH_NOTES | 772429192fdab1fd7ac75cb084f08f45aec5c965 | 5c05491b963c4d1d9793ebdc1f8469b175f170d8 | 1 | 69a236aa0b15b07755d42c713f24da944a7727e6 | 69a236aa0b15b07755d42c713f24da944a7727e6 | 4a05d7e4a7c16eb2d4b12819672ce2233b33f523 | Writers ApolloEvidenceImplementer ApolloEvidenceFixer1; verifier ApolloEvidenceReverifier1; findings CLOSED; O-02-01 existing Node failures |
| 03 | docs/plans/2026-10-18/apollo-03-admission.md | commit:1793d335cec0c73f10e1b26c1d0652b11c63c0a7 | 01,02 | 2 | LIGHT_PASS_WITH_NOTES | 69a236aa0b15b07755d42c713f24da944a7727e6 | 98c7b78fde54f69b72f175b94b2ab0eed25d86ab | 0 | — | 98c7b78fde54f69b72f175b94b2ab0eed25d86ab | 61f0e39acff1ab7acfe93c834db839e9ff5e175c | Writer ApolloAdmissionImplementerE2; verifier ApolloAdmissionVerifierE2; O-03-E2-01/02 record only |
| 04 | docs/plans/2026-10-18/apollo-04-worker.md | commit:bb9eda7df6fde5f4eb0a5d433a1058f008d42b16 | 01,02,03 | 2 | LIGHT_PASS_WITH_NOTES | 98c7b78fde54f69b72f175b94b2ab0eed25d86ab | a0103c2ea9284c030ad0b0b10c4177474c4f4d47 | 0 | — | a0103c2ea9284c030ad0b0b10c4177474c4f4d47 | 5a3482bf5dca311e1a25b30d46a2c75db8caf6ac | Writer ApolloWorkerImplementerE2; verifier ApolloWorkerVerifierE2; evidence closing correction1; O-04-E2-01/02 |
| 05 | docs/plans/2026-10-18/apollo-05-integration.md | commit:a58d457ec2921759c455b6f6edae0e6d94b85220 | 01,02,03,04 | 2 | LIGHT_PASS_WITH_NOTES | a0103c2ea9284c030ad0b0b10c4177474c4f4d47 | 083fce2f4b97c88b89a226394ebbf1342001a2d4 | 1 | 1cef08bd7e8bedc47cd2ca6d3f9823c2465e8848 | 1cef08bd7e8bedc47cd2ca6d3f9823c2465e8848 | 6f6b75ed311ef111d91775c51d02ae41a515f5c8 | Writers ApolloIntegrationImplementerE2/ApolloIntegrationFixerE2R1; verifier ApolloIntegrationReverifierE2R1; F01/02/03 CLOSED; O-05-E2-01/02/03 record only; evidence correction2 |
| 06 | docs/plans/2026-10-18/apollo-06-reporting.md | commit:f9435ee70c585a158b3a3414c3bf549c35c7044b | 01,02,03,04,05 | 2 | LIGHT_PASS_WITH_NOTES | 1cef08bd7e8bedc47cd2ca6d3f9823c2465e8848 | 096d21504bcfbaea321bd2b535ee6c62beef31a8 | 1 | cd105de17d7100466543675727a68fe1370008cb | cd105de17d7100466543675727a68fe1370008cb | 8e81e6ed6e25ff63f934dc627888c9f2f7c33ceb | Writers ApolloReportingImplementer/E2/FixerE2R1; verifier ApolloReportingReverifierE2R1; F06-01/02 CLOSED; O06-01/02 record only |
| 07 | docs/plans/2026-10-18/apollo-07-frontend.md | commit:00c0c9e75926ec9a8a9487fccf40d35d6275df3f | 01,02,03,04,05,06 | 1 | LIGHT_PASS_WITH_NOTES | cd105de17d7100466543675727a68fe1370008cb | 70f438d2bd529aaca60b2b28d66874354171705b | 1 | e473ffc714b840a2f42c9ca826da7c37e36ab9d7 | e473ffc714b840a2f42c9ca826da7c37e36ab9d7 | 647dc51ba60bad442c9922251693565553177349 | Writers ApolloFrontendImplementer/ApolloFrontendFixerR1; verifier ApolloFrontendReverifierR1; F07-01/02 CLOSED; independent browser retained; baseline40/fixture-transport limits record only |

## Amendments
| ID | Plan | Before | After | Master rule | Reason | Approval |
|---|---|---|---|---|---|---|
| A1 | docs/plans/2026-10-18/apollo-03-admission.md | commit:3f4ba12b8a1d04c1cc00cf1788dbb4f627e3ec26 | commit:1793d335cec0c73f10e1b26c1d0652b11c63c0a7 | I-6/I-13 | Authorize repository bulk item/evidence read to satisfy <=500-doc identity and evidence parity without N+1 | HUMAN:批准该计划并执行 (2026-10-18T20:27:54+08:00) |
| A2 | docs/plans/2026-10-18/apollo-verification-master.md | commit:3f4ba12b8a1d04c1cc00cf1788dbb4f627e3ec26 | commit:1793d335cec0c73f10e1b26c1d0652b11c63c0a7 | 实现方案 / I-6/I-13 | Synchronize child03 authorized file limit from 9 to 10 for the approved repository seam | HUMAN:批准该计划并执行 (2026-10-18T20:27:54+08:00) |
| A3 | docs/plans/2026-10-18/apollo-04-worker.md | commit:62f2ba3d9235f2257dcdd4bfc32c70da2ec19b8e | commit:bb9eda7df6fde5f4eb0a5d433a1058f008d42b16 | I-6/I-9 | Authorize existing ORCID client and regression tests for durable per-endpoint completion and resume | HUMAN:批准该计划并执行 (2026-10-18T21:06:49+08:00) |
| A4 | docs/plans/2026-10-18/apollo-verification-master.md | commit:1793d335cec0c73f10e1b26c1d0652b11c63c0a7 | commit:bb9eda7df6fde5f4eb0a5d433a1058f008d42b16 | 实现方案 / I-6/I-9 | Synchronize child04 file limit 7 to 9 for the approved ORCID persistence/resume seam | HUMAN:批准该计划并执行 (2026-10-18T21:06:49+08:00) |
| A5 | docs/plans/2026-10-18/apollo-05-integration.md | commit:0a0bb9c722f57a10462a8031a0e4e4f62f8ae143 | commit:a58d457ec2921759c455b6f6edae0e6d94b85220 | I-1/I-2 / C05-EXECUTION-CALLERS | Authorize three existing caller tests for explicit real execution/context contract migration,10→13; no production scope change | HUMAN:批准该计划并执行 (recorded2026-10-18T22:00:25+08:00) |
| A6 | docs/plans/2026-10-18/apollo-verification-master.md | commit:bb9eda7df6fde5f4eb0a5d433a1058f008d42b16 | commit:a58d457ec2921759c455b6f6edae0e6d94b85220 | 实现方案 / I-6/I-11 | Synchronize05 cap10→13 as caller-test-only exception to <=10; production scope unchanged | HUMAN:批准该计划并执行 (recorded2026-10-18T22:00:25+08:00) |
| A7 | docs/plans/2026-10-18/apollo-06-reporting.md | commit:85a2c24ac82991c06c081437ca618bb9e9cc13ba | commit:f9435ee70c585a158b3a3414c3bf549c35c7044b | I-10/I-11/I-12 / C-3:123 | Authorize existing worker read-only auth-latch accessor and test,7→9, so capabilities reports current CONFIG_AUTH without inference/reset | HUMAN:批准该计划并执行 后续也都授权 执行完所有子计划 (recorded2026-10-18T23:56:38+08:00) |
| A8 | docs/plans/2026-10-18/apollo-verification-master.md | commit:a58d457ec2921759c455b6f6edae0e6d94b85220 | commit:f9435ee70c585a158b3a3414c3bf549c35c7044b | 实现方案 / I-10/I-11/I-12 | Synchronize06 cap7→9 and record advance authority for necessary direct dependency/caller/test scope amendments without changing business goals | HUMAN:批准该计划并执行 后续也都授权 执行完所有子计划 (recorded2026-10-18T23:56:38+08:00) |
| A9 | docs/plans/2026-10-18/apollo-07-frontend.md | commit:2ddf49514057f4a274441bd74c1a72c46a231715 | commit:d0c1e1a74fc337c93cba9df1db35b67732abb0b4 | I-8/I-10/I-14 / S-5 | Authorize four existing read-only batch API/SQL/test dependencies for cumulativeProfiles/updatedAt/full reasons required by frozen DOM,7→11 | HUMAN:批准该计划并执行 后续也都授权 执行完所有子计划 (recorded2026-10-18T23:56:38+08:00; concrete mapping2026-10-19T00:50:59+08:00) |
| A10 | docs/plans/2026-10-18/apollo-verification-master.md | commit:f9435ee70c585a158b3a3414c3bf549c35c7044b | commit:d0c1e1a74fc337c93cba9df1db35b67732abb0b4 | 实现方案 / I-14 / S-5 | Synchronize07 cap7→11 as necessary read-only dependency exception, preserving frozen visual/business contract | HUMAN:批准该计划并执行 后续也都授权 执行完所有子计划 (recorded2026-10-18T23:56:38+08:00; concrete mapping2026-10-19T00:50:59+08:00) |
| A11 | docs/plans/2026-10-18/apollo-07-frontend.md | commit:d0c1e1a74fc337c93cba9df1db35b67732abb0b4 | commit:00c0c9e75926ec9a8a9487fccf40d35d6275df3f | I-14 / S-1/S-8 | Authorize two existing tests solely to retire obsolete CSS-at-EOF assertions conflicting with mandated append;11→13; no new production scope | HUMAN:批准该计划并执行 后续也都授权 执行完所有子计划 (recorded2026-10-18T23:56:38+08:00; concrete mapping2026-10-19T01:00:37+08:00) |
| A12 | docs/plans/2026-10-18/apollo-verification-master.md | commit:d0c1e1a74fc337c93cba9df1db35b67732abb0b4 | commit:00c0c9e75926ec9a8a9487fccf40d35d6275df3f | 实现方案 / I-14 | Synchronize07 cap11→13 for two required obsolete CSS-position test retirements, no production expansion | HUMAN:批准该计划并执行 后续也都授权 执行完所有子计划 (recorded2026-10-18T23:56:38+08:00; concrete mapping2026-10-19T01:00:37+08:00) |

## Pause / Resume Authority
- Child03 execution report records exact missing seam and rejected workarounds; no product file was changed for child03 and no verification was fabricated.
- Prior proposed amendment approved by user “批准该计划并执行”; A1/A2 and plan-only commit 1793d335cec0c73f10e1b26c1d0652b11c63c0a7 record exact authority. Child03 restarts at Epoch2 with zero repairs; Epoch1 reports retained.
- Resume identity checks: retained HEAD 3f4ba12b8a1d04c1cc00cf1788dbb4f627e3ec26 before amendment, branch/root matched, product/index clean, prior product head an ancestor. No replay of child01/02.
- Child01 and child02 completed-prefix checkpoint validation returned VALID, both dirty-artifact and clean modes, before child03 dispatch. No aggregate/final verification performed.

## Approved amendment scope receipt
- `grep pattern="\|[0-9]+\|`.*DiscoveryVerificationRepository.kt`" path=apollo-01-ledger.md;apollo-03-admission.md;apollo-04-worker.md;apollo-06-reporting.md` returned repository entries at 01:56, 03:65, 04:57, 06:56. The approved child03 addition reuses an existing union path; no new union file.

## Epoch 2 resume validation observation
- Extra current-HEAD `--through-child 02` check returned INVALID solely because its tail rule rejects plan-only amendment commit 1793d335cec0c73f10e1b26c1d0652b11c63c0a7 after child02 code head; A1/A2 themselves validated with no identity/ancestry/artifact errors.
- This is a mid-child03 authority-pause resume, not advancement from a newly terminal child02: child03 was already dispatched and reported PLAN_CONFLICT in Epoch1. Fast-p Set Up Once step5 directs resuming the recorded active role/boundary in this case. Historical child02 clean checkpoint passed before that dispatch and its artifacts remain untouched. Resume child03 IMPLEMENTER only; do not claim this extra check passed or change prior product boundaries.
- The mandatory child03 terminal checkpoint will validate the complete prefix through03 before child04 dispatch; by then the approved plan-only amendment is inside the reviewed child03 boundary, not after terminal code.

## Child04 pause / required authority
- Child03 terminal checkpoint passed VALID in both dirty-artifact and clean modes with complete seven-child manifest and A1/A2; child01/02 were not rerun. Clean checkpoint evidence HEAD: ccdaad239792d33089e0dfea5f048e33acc3c26a.
- Child04 was dispatched only after that gate; its execution report proves missing per-request ORCID response persistence/resume access. No product files changed for04; no tests/smoke/independent verification claimed.
- Smallest proposed amendment, not approved: add OrcidVerificationEvidenceClient.kt and OrcidVerificationEvidenceClientTest.kt to04 (7→9), expose durable endpoint completion and saved-result/cursor resume using the existing client. Preserve permit/cap/allowlist semantics; no duplicated HTTP implementation or full-read replay workaround.
- Await explicit approval, then record separate plan-only amendment commit and ledger amendment rows, resume04 in a new epoch with zero repair rounds. Completed01–03 remain closed. No whole-system verification performed.

## Child04 Epoch2 approved resume
- User “批准该计划并执行” approved the exact preceding proposal; A3/A4 bind scope/plan identities. Resume check found clean product/index at retained HEAD62f2ba3d9235f2257dcdd4bfc32c70da2ec19b8e, matching root/branch and prior product ancestor.
- This resumes already-active child04 IMPLEMENTER after its authority pause, per Set Up Once step5. Historical through03 clean checkpoint VALID remains unchanged; do not rerun completed children or apply the known current-tail checkpoint limitation to an in-flight amendment. Mandatory through04 checkpoint must pass before05 dispatch.
- New epoch2 resets fix_round to0; all prior reports and amendments retained.

## Child04 checkpoint evidence correction 1
- Initial artifact gate rejected A4 Before as not continuing the master amendment chain and Required Action with trailing prose. Corrected A4 Before to A2 After1793d335cec0c73f10e1b26c1d0652b11c63c0a7, the same actual pre-amendment master content; preserved actual approval/After and appended an exact canonical action line to the existing verifier report. No product changes, new test run or new verifier verdict; no repair round consumed.
- A4 Before content receipt: `git rev-parse 1793d335cec0c73f10e1b26c1d0652b11c63c0a7:docs/plans/2026-10-18/apollo-verification-master.md 62f2ba3d9235f2257dcdd4bfc32c70da2ec19b8e:docs/plans/2026-10-18/apollo-verification-master.md` returned identical blob IDs `da924a07c9b00efe4276515f4f9e2744c6dce703`.

## Child05 pause / required authority
- Child04 checkpoint passed VALID with complete manifest in dirty-artifact and clean modes before05 dispatch; clean checkpoint HEAD6ff5989012605e810daa5f1d926c359a3563e36d. No completed child replay.
- Exact caller-reference receipts, governing requirements and inferred matcher incompatibility are in children/05/execution.md. No tests were run for05; no failure or pass fabricated.
- Proposed amendment, not approved: authorize existing ExpertDiscoveryControllerMvcTest.kt, ExpertDiscoveryControllerTest.kt and ExpertDiscoverySchedulerTest.kt solely for migration of the explicit real execution/context callable contract. Child05 scope10→13 requires an explicit test-only exception to the master's per-child <=10 cap; production file scope unchanged.
- Child05 provisional authorized edits were surgically removed; no product commit. Await approval, then amend plans in a separate commit, record Before/After master/child identities and exact approval, and resume05 epoch2 with zero repair rounds. Do not replay01–04 or dispatch06/07 now.

## Child05 Epoch2 approved resume
- User “批准该计划并执行” approves the exact preceding test-only exception; plan-only commita58d457ec2921759c455b6f6edae0e6d94b85220 andA5/A6 record its identities/authority. Retained HEAD0a0bb9c722f57a10462a8031a0e4e4f62f8ae143 was clean with matching root/branch and prior product ancestor.
- Resume already-active05 under Set Up Once step5; completed through04 clean checkpoint remains VALID and untouched. Do not rerun closed children or add current-tail validation after an in-flight plan amendment; mandatory through05 gate precedes06 dispatch.
- New epoch2 resets fix_round=0; epoch1 conflict retained. Production scope unchanged; only three enumerated existing caller-test migrations added.

## Child05 checkpoint evidence correction1
- Initial checkpoint INVALID only because fixer round1 SHA line included subject text and controller closing heading isolated its canonical SHA. Original fixer/verifier reports unchanged; normalized the controller-added heading level and appended truthful correction records in replacement docs-only evidencec84abc228b1b3dca11da9d222c8924d8746478df. No product/test changes, rerun or new repair.
- Correction2: first correction added a redundant canonical action block; normalized only that controller closing format, preserving original independent action. Replacement docs-only evidence6f6b75ed311ef111d91775c51d02ae41a515f5c8. No product/verification changes.

## Child06 pause / required authority
- Through05 dirty-artifact and clean checkpoints both VALID; clean checkpoint HEADdf04e9c4737a61a0d859d21753e77a2bb9401740 preceded06 dispatch. Completed01–05 unchanged.
- C-3:123 requires capabilities to show current CONFIG_AUTH block; private process-local worker authBlocked has no authorized reader. Exact source/reference receipts and rejected historical-inference/reset workarounds in children/06/execution.md.
- Proposed amendment, not approved: authorize existing DiscoveryVerificationWorker.kt and DiscoveryVerificationWorkerTest.kt for a read-only current auth-latch accessor and regression coverage; consume via already-authorized query/controller.06 file cap7→9, no new state/table/reset/retry/inference or changed worker behavior.
- Keep authorized committed06 workbcc031775d112e1d67f20441e1c321d4b6a08967. Executor's final backend236 passed and actual HTTP/MySQL/F123/10000-row EXPLAIN smoke are evidence, not independent acceptance. Full Node41 failures remain recorded; no global-green or06terminal checkpoint claimed. Await approval, then plan-only amendment/ledger authority and epoch2 resume;07 PENDING.

## Child06 Epoch2 approved resume
- User approved exact auth-latch proposal and all remaining child execution, including necessary direct dependency/caller/test scope amendments. Every later concrete amendment must still have exact plan-only commit and ledger row citing this instruction before product changes; no new business goals, unrelated fixes, destructive history operations or deployment.
- Resume identity: clean retained HEAD85a2c24ac82991c06c081437ca618bb9e9cc13ba, matching root/branch, productbcc031775d112e1d67f20441e1c321d4b6a08967 ancestor. Plan-only amendmentf9435ee70c585a158b3a3414c3bf549c35c7044b recordsA7/A8. Existing06 work retained, not reimplemented.
- Resume already-active06 epoch2 with fix_round0 under Set Up Once step5. Prior through05 checkpoint remains closed/VALID; mandatory through06 checkpoint precedes07. No completed child replay or current-tail checkpoint substitution.

## Child07 necessary read-dependency authority
- Writer found existing VerificationBatch exposes only batchNumber/counts/suggested; frozenS5 requires reliable full cumulative/updated time/reasons despite server paging. Reference receipts in writer message: QueryService batches51–64 andDTO166; existingreasons only records173/item176. No authorized workaround can synthesize full values from current page.
- A9/A10 apply user's advance authority to exact four existing read-only backend/test files; plan-only commit precedes their product edits. Child07 retains epoch1/fix0; no human pause or completed06 replay. Frozen CSS/DOM and statistics/writer behavior unchanged.

## Child07 obsolete CSS assertion authority
- Fresh full Node1712/1669pass/43fail exposed two new failures beyond existing41: expertManualProfile.test.js:911–915 andexpertMeetingVideo.test.js:977–982 require previousCSSblocks atEOF, incompatible with mandated frozenApolloappend. A11/A12 authorize retiring only obsolete implementation-position assertions and any resulting unused test constants; do not repin at a new source location. Production scope unchanged; other behavior checks retained.

## Child07 verifier availability event
- Role VERIFIER, attempt1: ApolloFrontendVerifier returned cancelled/aborted because its reviewer sandbox allowed only read-only Git and xd transport, not required command/browser execution or report append. No report was written and no fresh command/browser verification was completed; source-only F07-01/F07-02 are candidates, not exercised findings.
- Action RETRY: acquired fresh capability-complete independent ApolloFrontendVerifierCapable with report-only repository authority and necessary command/browser tools; reset agent_attempt0. Product head unchanged70f438d2bd529aaca60b2b28d66874354171705b, fix_round0. Controller did not verify/fix inline or pause user.

## Final artifact closing
- All children01–07 terminal LIGHT_PASS_WITH_NOTES; current child07 clean through-child checkpoint VALID at b216cb8a2a4aac60e7c387db5d4fabc4c5b01c82. Final code head e473ffc714b840a2f42c9ca826da7c37e36ab9d7.
- Handoff compiled only from recorded child execution/verification/repair artifacts. Independent07 screenshots/JSON copied without rerun into children/07/browser/independent-r1/. No new aggregate diff review, product check, test suite or whole-system verification.
- Final-mode validation (no through-child) must be VALID before handoff is emitted: dirty-artifact validation, docs-only ledger/handoff commit, then clean validation. All local product/evidence commits and the worktree retained; no merge, push or deployment.
