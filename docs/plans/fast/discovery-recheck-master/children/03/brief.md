# Child 03 execution brief

## Identity and authority
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master
- Branch: fast/discovery-recheck-master
- Exact approved plan: docs/plans/2026-10-24/discovery-recheck-03-identity-parser.md
- Identity: sha256:199a71d6d89625a0faaf2aa5f3abe37a42f6740c2c1ad03220fad934426c87a9
- Child product base: 5487f3b45085f20f8db8554fb94888de1446c378
- Depends on 02: LIGHT_PASS_WITH_NOTES, zero repairs; complete ten-child checkpoint passed both dirty-allowance and clean modes; clean checkpoint HEAD9186a79c6ce78a8e9d253dbe5051d2b0a5a0ae54.
- Read full exact plan from disk; use execute-p immutable plan/worktree gates. User invocation authorizes local child implementation commit.

## Authorized Files


仅下列 6 个文件。新增文件明确标注；其余文件不得顺带修改。

| 文件 | 变更 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PdfAuthorContactLayout.kt` | 布局/标记/版本 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolver.kt` | 显式邮箱作者标签 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/PlainTextEmailExtractor.kt` | 限定混淆词替换 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPaperLayoutRegressionTest.kt` | 真实布局结构正负fixture |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/SourceAuthorEmailResolverTest.kt` | 标签与归属冲突 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/PlainTextEmailExtractorTest.kt` | 伪邮箱及回归 |



## Invariants/global constraints
- At most six listed files/two subsystems here; no plan edits or scope expansions. Master child01-only 11-file test exception does not apply.
- Unique verifiable email ownership only: explicit source owner tags or complete author-area unique marker; shared/unknown/damaged/adjacent author cannot be inferred. Do not add proof in a script or bypass results conflict handling.
- Remove hard x/y cropping, preserve structural reference/author/contact segment boundaries. Do not guess closest author or globally open fuzzy identity.
- Bare at/dot only explicit email/contact context and complete address shape; bracketed forms remain. No provider blacklists.
- Raise only layout implementation RULE_VERSION=20261024; DiscoveryIdentity.VERSION=20260925 and EXTRACTION_VERSION=20261004 remain unchanged, old queued sources stay admissible. New version must flow into existing first/analysis parserVersion without adding unauthorized modifications.
- Original first/SHA/history unchanged; no automatic backfill, admission/ES writes, mailbox/contact/outreach behavior change. Binding, new expert, qualification, proposed/committed admission and projection are separate concepts.
- No second admission chain; no changes to saved Apollo search/detail configuration or SELF acceptance. All manual/contact/SBIR/APPLICATION protections remain.

## Required commands / runtime proof
- `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryPaperLayoutRegressionTest,SourceAuthorEmailResolverTest,PlainTextEmailExtractorTest,*EmailExtractorTest,*Queue*Test,*DiscoveryIdentity*Test,*Source*Test` (includes bound JS/syntax checks; covers existing source/queue/identity tests without editing them).
- If existing source/queue consumption classes fall outside these selectors, inspect actual test names and run those existing classes freshly too; record exact resolved command/names and counts. Do not treat missing/skipped compatibility evidence as passing.
- Runtime smoke in disposable standalone harness through actual production plain/PDF parser/resolver and DiscoveryIdentity.validEvidence: generated PDF at x32 and y96% restores full unique-owner name; explicit email(owner initials) works only unique; shared/unknown/adjacent/reference negatives bind nobody; available-at URL produces no mailbox while explicit obfuscation still works. Print actual outcomes and proof validity; no prefilled proof or source-text assertions as behavior proof.
- Observe new layout version in actual capture-first/analysis version output and unchanged queue/identity constants/compatibility. Reuse real fixture construction helpers as needed, not precomputed test verdicts. Temporary source outside repo and remove it afterward.
- Run build/tests/lint only after implementation complete, not mid-flight. Record every command/exit/count/skip and boundary. Do not re-query production to confirm already established parser failures.

## Prior outputs / downstream contract
- 01 single-flight counts complete, no restore of old six-scan path; O-01 fixture terminal failed-progress/SQL discrepancy record-only, do not repair.
- 02 shared PaperContentFormat now byte-based; analysisVersion2, formatVERSION1, qualificationVERSION2 currently. First-outcome summary correct 3/2/1 or unknown, safe DOCTYPE enabled; lease/history unchanged. Continue existing patterns; do not alter these out-of-scope files.
- O-02 unrelated compiler warnings remain record-only.
- Existing parserVersion composition should read layout.RULE_VERSION dynamically; preserve its downstream capture/analysis consumers and queue proof compatibility. If exact plan cannot be achieved in listed files, PLAN_CONFLICT with evidence.

## Commit / evidence
- Local product commit authority: `feat(fast-p): implement 03`, six authorized product/test paths only. No fast-p reports/logs in product commit.
- Append full execute-p execution result to /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master/docs/plans/fast/discovery-recheck-master/children/03/execution.md; no rewrites of logs.
- Independent light verifier follows; no review-p/verify-p/repair-p/fix-v inside fast-p loop.
- No later-child review, unrelated fixes, other-worktree edits, push/merge/history rewrite, plan/ledger modification.
