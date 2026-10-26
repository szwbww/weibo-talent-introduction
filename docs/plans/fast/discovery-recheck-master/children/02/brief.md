# Child 02 execution brief

## Identity and authority
- Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master
- Branch: fast/discovery-recheck-master
- Exact approved child: docs/plans/2026-10-24/discovery-recheck-02-archive.md
- Approved identity: sha256:01b4abd5cf06e535848abb65b1d9bc62194c225df4f7dfa12e20e6d2a002b13f
- Product base: 7d0cb8c276533576d79f28fe970ccfbac4114d9e
- Depends on: 01 (terminal LIGHT_PASS_WITH_NOTES; both complete-manifest child checkpoints VALID, exit 0; clean checkpoint HEAD b6858ccea21be66f3d93c4c5952e03d7b293c862).
- Read the complete exact child plan from disk and bind execute-p identity/worktree gates.

## Authorized Files


仅下列 10 个文件。新增文件明确标注；其余文件不得顺带修改。

| 文件 | 变更 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/discovery/archive/PaperContentFormat.kt` | 新增格式纯函数 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveService.kt` | 计数与保存/预览识别 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperAnalysisService.kt` | 分析格式、缓存版本 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/archive/PaperQualificationEvidenceExtractor.kt` | DOCTYPE兼容且防XXE |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperArchiveServiceTest.kt` | 计数、旧MIME预览 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/archive/DiscoveryPaperAnalysisServiceTest.kt` | 分析格式/缓存 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/archive/PaperQualificationEvidenceExtractorTest.kt` | DOCTYPE正负测试 |
| `src/main/resources/static/discovery-papers.js` | 说明文本 |
| `src/main/resources/static/index.html` | 统一版本键 |
| `src/test/js/discoveryPapers.test.js` | 新文本与旧行为 |



## Global invariants
- Only authorized child files, at most 10 files/2 subsystems. Child 01's 11-file exception does not apply here. New required scope or behavior => PLAN_CONFLICT, not inline expansion.
- Admission chain remains original Worker→Rules→Policy/Gate→Commit→projection; this child changes no admission or ES write path.
- First extraction/history and body SHA are immutable; no automatic historical re-analysis or rewrite. Reads/analysis keep lease/finally deletion protection.
- Binding ≠ expert ≠ qualification ≠ proposed/committed admission ≠ projection. XML/PDF fixes must not fabricate identity or acceptance.
- Preserve manual/contact/SBIR/APPLICATION protections, mail/contact/outreach behavior, ordinary queue compatibility, SELF and saved Apollo search/detail settings.

## Required commands and runtime proof
- `env JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=DiscoveryPaperArchiveServiceTest,DiscoveryPaperAnalysisServiceTest,PaperQualificationEvidenceExtractorTest` (includes bound all-JS/syntax checks).
- `node --test src/test/js/discoveryPapers.test.js`.
- `node --check src/main/resources/static/discovery-papers.js`.
- Resource regression: `node --test src/test/js/meetingConfirmationAssets.test.js src/test/js/trustReplyWorkbenchSharedMount.test.js`.
- Runtime smoke actual changed format/preview/analysis methods with generated PDF and DOCTYPE/XXE fixtures using an isolated disposable harness: observe detected type, response Content-Type, correct 3/2/1 or unknown summary, first/SHA preservation, preview zero-analysis writes, external entity content not exposed and zero external requests. Tests alone are not runtime proof. Use no live production mutations.
- Changed UI surface: exercise real discovery-papers.js in a real Chromium tab (read xd://eval/browser) with isolated fixture transport if live app cannot run; confirm the actual new unanalysed text and bindings display. Record exact scope/limits; no browser/manual acceptance claim without observation. Close tab, remove temporary smoke scaffolds.
- Run build/tests/lint only after implementation is complete, not mid-flight. Record commands/exit/counts/skips honestly; do not treat skipped mandatory evidence as passing.

## Prior-child outputs and downstream contracts
- Child01 product commit 7d0cb8c276533576d79f28fe970ccfbac4114d9e: single-flight counts API and scanAll completeness checks; do not restore six-scan counts or change its behavior.
- Current index has 17 uniformly versioned assets; child02 upgrades all to 20261024-paper-format as exact plan prescribes.
- Child01 authorized discoveryApolloSettings.test.js to retire old-plan global-key binding; shared-key/DOM/CSS/Apollo regressions now pass. This does not authorize editing that test during child02; if another out-of-scope failure appears, report exact conflict.
- Child01 RECORD_ONLY O-01: fixture FAILED.scanned=5000 is not production conditional-SQL fidelity. Do not repair unrelated count progress.
- Preserve new format/qualification parserVersion keys and analysisVersion=2; first version unchanged. Later04/06 continue from your qualification extractor/analysis changes.

## Commit and evidence
- Explicit local implementation authority: `feat(fast-p): implement 02`, authorized product/test files only, no fast-p reports.
- Append full execute-p execution report to /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-discovery-recheck-master/docs/plans/fast/discovery-recheck-master/children/02/execution.md. All child logs append-only.
- No full review-p/verify-p/repair-p/fix-v inside fast-p loop; independent light verifier follows implementation.
- Do not edit approved plans, ledger, other worktrees or later children; no push/merge/history rewrite.
