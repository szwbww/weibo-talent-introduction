# Mail Reply Collapse Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Shorten the inbound-mail processing panel by collapsing history and reply workflows by default, and remove unsupported single-rule QA reply.

**Architecture:** Use native `details`/`summary` elements so collapsed state needs no JavaScript state. Preserve the existing reply controls and handlers inside each details body. Remove only the obsolete single-rule QA renderer and its data-loading dependency.

**Tech Stack:** Vanilla JavaScript, HTML templates, CSS, Node test runner.

## Global Constraints

- Preserve current composed, AI, and manual-rich reply behavior.
- Default all four sections to collapsed.
- Do not alter unrelated existing worktree changes.

---

### Task 1: Mail processing workflow UI

**Files:**
- Modify: `src/main/resources/static/app.js`
- Modify: `src/main/resources/static/styles.css`
- Modify: `src/test/js/unmatchedQaReplySource.test.js`

**Interfaces:**
- Consumes: existing `renderComposedReplyWorkbenchHtml`, `renderAiReplyPanelHtml`, `showUnmatchedDetail`, and `formatMailTime`.
- Produces: native collapsed workflow markup using `.reply-workflow-detail`.

- [ ] **Step 1: Write the failing test**

Assert that source contains four collapsed `.reply-workflow-detail` sections and no single-rule QA renderer or action.

- [ ] **Step 2: Run test to verify it fails**

Run: `node --test src/test/js/unmatchedQaReplySource.test.js`
Expected: FAIL because reply sections are not wrapped and the QA renderer remains.

- [ ] **Step 3: Write minimal implementation**

Wrap history, composed, AI, and manual-rich sections with native closed `details`; add compact summary metadata and focused CSS; remove `buildUnmatchedQaReplyHtml`, QA-rule loading, injection, and action branch.

- [ ] **Step 4: Run test to verify it passes**

Run: `node --test src/test/js/unmatchedQaReplySource.test.js`
Expected: PASS.

- [ ] **Step 5: Run frontend verification**

Run: `node --check src/main/resources/static/app.js && node --test src/test/js/*.test.js`
Expected: syntax check and all Node tests pass.
