# Fast-P Child Brief — 08 共享材料组件与专家页内嵌

- Master: docs/plans/2026-09-07/00-mailbox-materials-master.md (commit a61ecb5)
- Plan: docs/plans/2026-09-07/08-shared-materials-frontend.md — the complete approved contract. Read it first. Style contract S-1 block inside the plan is the ONLY production CSS source (copy verbatim); frontend-baseline.md documents existing DOM/CSS (read-only reference).
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-materials (branch fast/mailbox-materials)
- Base SHA: 62d86310634e5cb1a9ae522f536cb2c91b0fc2b9 (= child 07 Code head)
- Execution report: docs/plans/fast/mailbox-materials/children/08/execution.md

## Role
Use execute-p (read skill://execute-p first). Implement ONLY child 08. Serialized run: children 09+ untouched. You are the sole writer.

## Authorized files (exactly the plan's 5-file list)
1. src/main/resources/static/expert-materials.js (new)
2. src/main/resources/static/expert-materials.css (new)
3. src/main/resources/static/app.js (modify)
4. src/test/js/expertMaterialsShared.test.js (new)
5. src/test/js/expertMaterialsStyle.test.js (new)

No other files. Proof requiring an unlisted file → return PLAN_CONFLICT. index.html script/CSS registration belongs to child 11 — do NOT touch index.html.

## Constraints
- Invariants I-1..I-4 + style contract S-1..S-5 of the plan: S-1 CSS + DOM must be copied byte-verbatim from the plan's code blocks (style test asserts byte equality); no inline styles, no undeclared classes, no modification of existing global rules (reuse styles.css button classes 802/838 etc.); window.ExpertMaterials IIFE with mount({host,contactId,mode})/unmount; one store per contactId shared across inline/drawer/selectionOnly; mode is layout-only; page switches never reset server-side tasks; 10 rows/page, header checkbox selects only the current page, selection persists per contactId across pages/filters, POST sends only selected (cap 500, never implicit select-all); GET never triggers POST/fetch; 300ms search debounce + request epoch so stale experts never overwrite; partial row-state updates only (no rebuilding inputs/editors); 2s polling only while the task activity is visible, hidden pages pause and re-fetch on next mount; AbortController cancels read requests only — submitted POST server tasks never cancelled; QUEUED shown only after submit success, failures keep selection + reason, submit disabled while in-flight; drawer = native <dialog class="em-drawer">, close/Esc releases UI only (queue untouched); selectionOnly hides the transfer footer but uses the same renderer.
- app.js changes are inert until the component loads (guard on window.ExpertMaterials): mount inline after loadContactDetail writes current-contact DOM; legacy renderExpertDocuments stays when the component is absent (progressive) and only emits the data host when present; unmount subscription/listeners on expert switch; BOTH expert-detail entries mount by real contactId; original ES experts WITHOUT contactId never mount the networked component — show 尚未建立联系，暂无资料 empty state, no requests to undefined/null ids; the original AI entry calls the child-09 bridge when loaded, else the original function; all other detail events unchanged.
- Existing JS tests use extractFn + DOM stubs: for the NEW tests use a DOM-capable adapter (real querySelector etc.); ALSO add a source-text existence assertion for each new id your render code writes (per repo K-dom-stub-tests-hide-dangling-refs the stub always returns elements — new id used via getElementById must be asserted present in the real index.html or in the app.js inline template you actually ship; since index.html registration lands in child 11, assert against YOUR OWN generated DOM/source per plan S-1 DOM whitelist).
- master contract: UI renders only 10 material rows / 20 experts / 50 messages; materials never load with all experts.
- Environment: node v25 (node --test / node --check). JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home for mvn test (exec-plugin runs the JS suite in the test phase).
- Fast-p evidence excluded from your commit.

## Required commands (run all; exact output + exit codes in execution.md)
1. node --check src/main/resources/static/expert-materials.js && node --check src/main/resources/static/app.js
2. node --test src/test/js/expertMaterialsShared.test.js src/test/js/expertMaterialsStyle.test.js
3. node --test src/test/js/*.test.js (full JS suite — existing contract tests must stay green)
4. JAVA_HOME=... mvn test (full suite incl. Node exec phase)

## Commit
Commit the implementation locally as: `feat(fast-p): implement 08` — 5 authorized files only. No push/merge/amend/rebase.

## Acceptance for return
Return only: READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT, commit SHA, command summary, report path. Write full result to execution.md (files changed, per-command evidence, I-1..I-4 + S-1 byte-compare evidence, deviations).
