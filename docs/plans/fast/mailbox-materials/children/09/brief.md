# Fast-P Child Brief — 09 AI 所选材料获取与分析衔接

- Master: docs/plans/2026-09-07/00-mailbox-materials-master.md (commit a61ecb5)
- Plan: docs/plans/2026-09-07/09-material-analysis-selection.md — the complete approved contract. Read it first. S-2 DOM block inside the plan is authoritative; no new CSS (08 shipped the .em-analysis rules).
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-materials (branch fast/mailbox-materials)
- Base SHA: 47a72d885498ff473560d26901d2596ffdddb2cd (= child 08 Code head)
- Execution report: docs/plans/fast/mailbox-materials/children/09/execution.md

## Role
Use execute-p (read skill://execute-p first). Implement ONLY child 09. Serialized run: children 10+ untouched. You are the sole writer.

## Authorized files (exactly the plan's 5-file list)
1. src/main/resources/static/app.js (modify)
2. src/main/resources/static/expert-materials.js (MODIFY-EXTEND: file created by child 08 at this path; plan wording 新增 predates 08. Extend the selection/API surface for selectionOnly + acquisition-state consumption — same authorized path, no new file)
3. src/test/js/expertMaterialAnalysisFlow.test.js (new)
4. src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentAnalysisService.kt (modify)
5. src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentAnalysisServiceTest.kt (modify)

No other files. Proof requiring an unlisted file → return PLAN_CONFLICT.

## Constraints
- Invariants I-1..I-3 + S-1/S-2/S-5 of the plan.
- I-1: opening AI only GETs materials metadata + history results; if materials were pre-selected carry over only analysis-supported selected ids with exclusion reasons listed; none selected → default = supported-format CV/degree ids across the COMPLETE expert set (materials summary defaultAnalysisAttachmentIds); >500 default candidates → explicit notice 默认材料超过500份，请分批选择, NO silent first-500 auto-check; freeze contactId+attachmentIds at submit; no drift after later filter changes.
- I-2: analysis only when ALL selected are STORED; missing ones first POST …/transfers; any FAILED/SOURCE_UNAVAILABLE blocks the whole batch with the failing items shown; retry requests only the failed ids; acquire state via the SHARED store subscription (never a second download-state copy); AI called exactly ONCE when all ready and the intentToken still belongs to the current window; button disabled to prevent duplicate submits; M=0 → button 开始分析; M>0 → 获取所选文件并分析.
- I-3: close destroys intentToken + subscriptions; downloads already requested continue; server-side analysis already sent keeps existing semantics (results re-readable via history; never claim server-analysis cancellation); acquisition failures show attachmentIds/file names/reasons; retry re-validates the whole frozen snapshot; expert switch never reuses the prior expert's token; support comes from SERVER capability flags (analysisSupported/canAnalyze) — never fake by extension; JPEG shows 当前不支持图片文字识别; scanned-empty PDF shows per-file reason, no success conclusion; existing result edit/add/re-evaluate/save APIs and result schema untouched; history results never cleared by the frontend before analysis.
- Backend (file #4/#5): in ExpertDocumentAnalysisService, AFTER extract and BEFORE building prompt/calling LLM/deleteAll: if ANY selected item is unsupported or yields empty text → throw the ORIGINAL AnalysisFailedException with message naming attachmentId/fileName and 不支持格式/无可读文字; never silently filter and report partial success as complete; existing GlobalExceptionHandler ANALYSIS_FAILED mapping kept; tests: 1 readable PDF + 1 empty PDF → LLM call 0, deleteAll call 0, old results preserved; all-readable path and field edit/add/clear unchanged.
- app.js additions must remain inert until child 11 registers resources (guard on window.ExpertMaterials like child 08).
- Environment: node v25; JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home (mvn test). Fast-p evidence excluded from your commit.

## Required commands (run all; exact output + exit codes in execution.md)
1. node --check src/main/resources/static/app.js && node --check src/main/resources/static/expert-materials.js
2. node --test src/test/js/expertMaterialAnalysisFlow.test.js
3. node --test src/test/js/*.test.js (full JS suite green)
4. JAVA_HOME=... mvn test -Dtest=ExpertDocumentAnalysisServiceTest
5. JAVA_HOME=... mvn test (full suite)

## Commit
Commit the implementation locally as: `feat(fast-p): implement 09` — 5 authorized files only. No push/merge/amend/rebase.

## Acceptance for return
Return only: READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT, commit SHA, command summary, report path. Write full result to execution.md (files changed, per-command evidence, I-1..I-3 + S-2 checks, deviations).
