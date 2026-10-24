# Child04 execution brief

## Identity and authority
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master
- Branch: fast/discovery-recheck-master
- Exact approved plan: docs/plans/2026-10-24/discovery-recheck-04-qualification.md
- Approved identity: sha256:04b07f58de1d6e9199e0919047cfffde967896f1bd4cfcad526b40a7d412dd0d
- Product base: bc5ec812dedd7ac6aef5ea0e4d9094e19b2a9f4a
- Depends on03 terminal LIGHT_PASS_WITH_NOTES. Full ten-child manifest checkpoint VALID in both dirty-allowance and clean modes; clean checkpoint HEAD3a7dac56a3b8978c2b2178ef3b70a452ecbda533.
- Read full exact plan from disk; use execute-p immutable plan/worktree gates. User master invocation authorizes local implementation commit, not changing the plan.

## Authorized Files


仅下列 9 个文件。新增文件明确标注；其余文件不得顺带修改。

| 文件 | 变更 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/discovery/archive/PaperQualificationEvidenceExtractor.kt` | 主体、句式与实现版本 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveModels.kt` | 新增BIRTH_DATE kind |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperAnalysisService.kt` | 缓存资格解析版本 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRules.kt` | 锚定姓名、复合职称、评估时间 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorker.kt` | 支持冻结rule3 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRegistrationService.kt` | 新run冻结rule3 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/archive/PaperQualificationEvidenceExtractorTest.kt` | 简介正负/日期/版本 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationRulesTest.kt` | 三反事实及旧版本 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationWorkerTest.kt` | 新旧冻结版本兼容 |



## Key invariants / global constraints
- Only nine listed files, at most two subsystems. Existing amendments A1/A2 authorize child01 only; A3/A4 child03 only. New required file or unresolved product decision => PLAN_CONFLICT; do not silently expand.
- Unique biography subject before same-paragraph pronouns; stop on new author/heading/references. Distinguish completed degrees, doctoral student and each professor role. Negation/conflict never positive; a student clause cannot erase a separate completed degree.
- Paper employment/student facts PUBLICATION_TIME, never current employment. Birth only explicit complete valid date <= full publication date or Dec31 of known publication year; never infer age from graduation/year-only.
- Rule>=3 anchored ORCID person/other-names source matching snapshot identity/fact only for full-token rotations/unique surname+initials. No global Apollo fuzzy/word-order change; spelling/initial omissions remain conflicts.
- Compound title parsing rule3 only, retain retired/emeritus/student/assistant-to negatives and existing accepted roles/SELF/Apollo branches. Old rule0/1/2 outcomes unchanged; new runs freeze3, resumption uses frozen version; explicit evaluatedAt drives trace/age together.
- No admission/ES business writes in this child; original Worker→Rules→Policy/Gate→Commit→projection remains authority. No side-effectful historic re-evaluation or backfill. Preserve manual/contact/SBIR/APPLICATION protections, queue identity compatibility and all mail/contact/outreach behavior.
- Rule implementation/analysis cache versions separate from queue/proof compatibility; first JSON/body SHA unchanged. Do not reset or re-sign old facts/progress.

## Required commands and runtime evidence
- `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=PaperQualificationEvidenceExtractorTest,DiscoveryVerificationRulesTest,DiscoveryVerificationWorkerTest,DiscoveryPaperAnalysisServiceTest -DmysqlIt=true` (includes bound complete JS and syntax checks; analysis tests read-only unless already listed, do not modify unlisted tests).
- Cover every exact acceptance: thirteen positive-person structural biography fixtures and four adjacent negatives, completed/student/negation/date boundaries, anchored Agbo ordering vs unbound/spelling negatives, R.Krishnamoorthy still UNKNOWN, Brendan compound role MET but SEARCH_INCOMPLETE PENDING, old-version field-for-field compatibility and frozen-date repeatability.
- Runtime smoke outside JUnit through actual qualification extractor→real evidence structure/Rules evaluation, not prefilled desired result. Observe explicit facts/excerpts/temporal scopes, false-positive controls, rule2 versus3/frozen evaluatedAt outputs; actual Registration/Worker version selection should be exercised with isolated fixture boundaries where needed. No live production queries/writes or provider costs.
- Run tests/build/lint only after implementation complete. Final required commands must follow last product/test edit. Temporary smoke outside repo, remove scaffolds; report exact command/exit/count/skip/limitations. Tests alone are not runtime proof.
- If referenced approved audit fixtures are missing from retained target, identify exact missing reference to controller; do not fabricate names or treat absence as silent scope reduction. Synthetic structural fixtures may use audited patterns without copyrighted full texts as plan specifies.

## Prior outputs / downstream contracts
- 02 content format uses magic bytes; safe DOCTYPE, analysisVersion2, formatVERSION1, qualificationVERSION2 currently and parserVersion dynamic keys; first extraction stays immutable. Continue these shared files from prior product, no regression.
- 03 layoutRULE_VERSION20261024; DiscoveryIdentity.VERSION20260925 and EXTRACTION_VERSION20261004 unchanged; unique mailbox proofs and strict ambiguity remain. No automatic old-cache invalidation.
- Completed03 commands primary347 (two explicit permitted private corpus/live network skips), consumption299 -DmysqlIt=true (zero skips), JS1827 pass and independent runtime smoke. No private full-batch replay claim; that is child10.
- 05 later persists independent epochs;07 later wires frozen evaluationAt through Gate/Worker evidence. Here expose exact optional Rules evaluatedAt and version3 support as plan requires, no early second workflow.
- Prior record-only warnings/fixture terminal count-progress SQL mismatch are not tasks; do not repair unrelated behavior.

## Commit / evidence
- Explicit local implementation authority: `feat(fast-p): implement 04`, nine authorized product/test paths only; no reports/logs in product commit.
- Append full execute-p result to /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master/docs/plans/fast/discovery-recheck-master/children/04/execution.md; all child logs append-only.
- No full review-p/verify-p/repair-p/fix-v inside fast-p loop; controller acquires independent light verifier afterward.
- No approved plan/ledger/other-worktree edits, later-child investigation, unrelated fixes, push/merge/history rewrite.
