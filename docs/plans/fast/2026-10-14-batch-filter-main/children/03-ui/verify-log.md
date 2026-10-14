# Light Verification Log — 03-ui

（由轻量验证者写入；append-only）

## Light Verification: LIGHT_PASS_WITH_NOTES
Child: 03-ui (docs/plans/2026-10-14/batch-filter-03-ui.md)
Boundary: 9c9457cadda70e95dc605f720d652d3e4617b803..50dc450b9e3d68b4cc44ad3a660d8a00936bb662
Verifier: Verify03Ui

### Four Gates
|Gate|Result|Evidence|
|---|---|---|
|1 Authorized scope|PASS|`git diff --stat 9c9457c..50dc450` touches only app.js, index.html and 3 JS tests under src/ plus docs. `git show --stat 50dc450` = exactly the 5 authorized paths (app.js 93+/-, index.html 36+/-, batchEmailVerification 39+, gateTemplateFilter 8+/-, recipientPreviewDiagnostics 319+, 460/35). `git diff --stat ...styles.css` empty (CSS diff zero). Commit message literal `feat(fast-p): implement 03-ui`. No other product file touched.|
|2 Plan & invariants|PASS|I-1: app.js:23656-23699 diagnosticsHtml renders asOf (`统计时间：`), baseline/excludedUnique/eligibleBeforeLiveVerification/awaitingVerification directly from res, overlap warnline, per-hit `原因：KEY N 人；仅显示前 N 条`, `（样本已截断）`, `统计未完成` for partial; reasonHitsDetail never summed into excludedUnique (res.excludedUnique used verbatim, app.js:23664-23668). I-2: app.js:23285-23286 + index.html:1492/1759 copy `关闭时不因个性化缺项排除` / `开启后按模板必填字段筛选`; `grep -rn "计入失败\|门禁拦下" src/main/resources/static` = none. I-3: scheduleRecipientPreview still 500ms (app.js:23605-23611); seq guard on both then/catch (app.js:23639, 23722, 23742); error path writes hint.textContent with no popup; escapeHtml on asOf/reasonKey/docId/orcidId (app.js:23673-23690); expansion is details/summary read-only (no send/verify calls). S-1/S-2 DOM skeleton matches plan verbatim; reused classes only (`batch-gate-excluded`, `batch-gate-warnline`); no new class/id/inline style in either file (diff grep `style=` empty). Real index.html still declares the 2 container ids (1559/1779) and 2 gate-hint ids (1492/1759); 16/16 `?v=` keys = `20261014-batch-filter`, 0 hits of old key.|
|3 Required commands|PASS|Fresh in worktree: recipientPreviewDiagnostics.test.js exit 0, tests 13 pass 13 fail 0; batchEmailVerification.test.js exit 0, tests 42 pass 42 fail 0; gateTemplateFilter.test.js exit 0, tests 8 pass 8 fail 0; `node --test src/test/js/*.test.js` exit 0, tests 1671 pass 1671 fail 0; `node --check src/main/resources/static/app.js` exit 0. Matches implementer-reported counts.|
|4 Downstream interfaces|PASS|Consumes exact preview DTO field names (`baseline`, `excludedUnique`, `eligibleBeforeLiveVerification`, `awaitingVerification`, `reasonHitsDetail`, `asOf`, `partial`) defined at ManualInitialOutreachService.kt:2463-2479 / BatchRecipientPreflightService.kt:392-402; no frontend recomputation of regions/admission/history-email validity/reason counts. Legacy (no `baseline`) responses fall back to baseHintHtml + `详细解释不可用` (app.js:23703-23707), never padded with zeros; `hasDiagnostics` uses a null check so a legitimate baseline=0 still renders.|

### AUTO_FIX
- N/A

### RECORD_ONLY
- Deviation (1) HTTP-status-gated `预估不可用`: app.js:23745 keeps `预估失败：<message>` for statusless errors (only a set `error.status` -> `预估不可用`). Satisfies plan I-3/A-3 500->`预估不可用`; the split is forced by unlisted pre-existing `batchSendTaskConsoleInteraction.test.js:2117` (asserts `预估失败：boom`) and `:1183`; no explicit requirement violated.
- Deviation (2) retained `门禁过滤已排除`: app.js:23660 emits the required `因模板缺项排除 N 人` plus `（门禁过滤已排除）`; the plan forbids only `计入失败`, and `batchSendTaskConsoleInteraction.test.js:2038` (I4b-1) requires the retained phrase. Not a violation.
- Deviation (3) plain-text `详细解释不可用`: app.js:23705 uses `<span>详细解释不可用</span>` (no `batch-gate-warnline`); S-1's warnline-placement sentence is ambiguous for this legacy note and `batchSendTaskConsoleInteraction.test.js:1960` (G7) asserts no `batch-gate-warnline` for a baseline-less response, so the plan does not uniquely determine a classed repair and the conflicting test is outside the authorized change set. Classified RECORD_ONLY, not AUTO_FIX.
- Observation: `verificationPhase` from the upstream payload is not consumed by the render path; I-1's display obligations (four counts + asOf + `待执行验证`) are met without it, so no task.

### Required Action
- COMPLETE_CHILD
