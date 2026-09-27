# Discovery admission repair execution

- Status: READY_FOR_VERIFICATION — execution evidence only; independent aggregate verification and A1–A6 human acceptance remain pending.
- Approval: human-originated `$execute-p /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-26-discovery-repair-00-master/docs/plans/fix/discovery-repair-00-master/repair.md` in this invocation; approval for the exact repair artifact under its Review-Fast-P Execution Handoff.
- Executor: Main (`execute-p` invocation).
- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-26-discovery-repair-00-master/docs/plans/fix/discovery-repair-00-master/repair.md`.
- Plan SHA-256: `e2a4a5dbeb94037791020193c77d2a01b3a9ebe303bcd796db054696a21c9c86` (checked at start and after implementation).
- Execution ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-26-discovery-repair-00-master/docs/plans/fix/discovery-repair-00-master/repair.md@e2a4a5dbeb94037791020193c77d2a01b3a9ebe303bcd796db054696a21c9c86` (NEW).
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-26-discovery-repair-00-master`.
- Branch: `fast/2026-09-26-discovery-repair-00-master`.
- Git directory: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-2026-09-26-discovery-repair-00-master`.
- Pre-execution code SHA: `4ad9e79b034798ee78f12c3285faf5882991b3bc` (prior product boundary); pre-execution HEAD: `ac3278345bfba9257218bd88d7d395c3325f60ad` (subsequent review/plan evidence).
- Post-execution code SHA: `8051fa894d96f065b8b9ef2b39463262b2cf8c50`.
- Product commit: `8051fa894d96f065b8b9ef2b39463262b2cf8c50`, subject `fix(discovery): preserve email validation and historical document IDs`; verified as branch HEAD and reachable on the target branch before the separate evidence commit.
- Implementation boundary: `4ad9e79b034798ee78f12c3285faf5882991b3bc..8051fa894d96f065b8b9ef2b39463262b2cf8c50` (intervening docs-only review/plan commits are not repair product changes).

## Completed requirements

| Finding | Implementation and proof |
|---|---|
| V-1 / R-1 | Discovery revalidation calls the existing full email validator when `requireValidEmail` is enabled; rejected validation joins the RAW qualification reasons as `EMAIL:<reason>`, blocks candidate creation, and triggers conditional candidate removal. Validator exception returns retryable `WriteFailed`; disabled configuration skips `validate`. Both automatic RAW-promotion and candidate-revalidation loops exercise the real revalidator with a real eligibility service. |
| V-2 / R-2 | Removed both `source.orcidId == docId` guards. RAW and CANDIDATE still use the real ES `_id`, stable academic identity, RAW snapshot equality and seq/primary-term CAS. Isolated HTTP tests exercise real revalidation plus writer for `OLD-DOC` with a different historical `orcidId`: candidate create or CAS delete; the existing writer scenario additionally checks identity changes, 404, 409, 500, and preservation of non-qualification fields. |

## Changed product and test files (only authorized scope)

- `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationService.kt` — full discovery email gate; historic business key no longer acts as ES locator.
- `src/main/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterService.kt` — remove only the incorrect business-key equality while retaining identity/snapshot/CAS checks.
- `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertRevalidationServiceBehaviorTest.kt` — admission, rejection, exception, disabled gate, automatic entries.
- `src/test/kotlin/com/weibo/talentintroduction/expert/service/ExpertIndexWriterServiceTest.kt` — actual revalidation plus isolated HTTP writer create/delete and historical-key CAS scenarios.

## Commands and results

| Exact command (target worktree, JDK11) | Result |
|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertRevalidationServiceBehaviorTest,ExpertIndexWriterServiceTest` before production changes | Exit 1 as intended: 58 tests, 5 failures, 0 errors, 0 skipped; full-email bypass and historical-key guard reproduced. |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=ExpertRevalidationServiceBehaviorTest,ExpertIndexWriterServiceTest,ExpertRevalidationServiceTest,CandidateEligibilityServiceTest,CandidateEligibilityServiceEnhancedTest,ExpertDiscoveryServiceTest,ExpertAcademicEnrichmentJobServiceTest,ExpertAcademicEnrichmentWorkerTest,OperatorStatusWriteSeamGuardTest,ExpertClassificationVersionGateGuardTest` | Exit 0: 279 tests, 0 failures, 0 errors, 0 skipped. |
| `git diff --check` | Exit 0, no whitespace errors. |
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn clean package` | Exit 0: JUnit 4,149 tests, 0 failures, 0 errors, 13 skipped; Maven-bound Node 1,194 tests / 235 suites, 1,194 pass, 0 fail, 0 skipped; WAR built. |

No production access, data cleanup, deployment, push, merge or migration. Deviations: none. Required commands were run fresh in this invocation after the final product/test state; historical review outputs were only baseline. Target worktree and plan identity were checked before product commit and will be checked again before evidence commit and handoff. A1–A6 remain PENDING; no independent PASS is claimed.
