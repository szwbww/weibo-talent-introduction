# Frontend UX & Style Optimization Implementation Plan

> **For Antigravity:** REQUIRED WORKFLOW: Use `.agent/workflows/execute-plan.md` to execute this plan in single-flow mode.

**Goal:** Fix P0 interaction hazards (multi-layer Escape collision, RAG verbatim tag leakage, hidden/layout-shifting status bar, blocking browser dialogs), ship P1 workflow accelerators across Mailbox, RAG Workbench, and Expert Contacts, unify console modal/theme/table styling, and launch a real-entry local frontend preview server.

**Architecture:** Pure frontend evolution inside `src/main/resources/static/` and `src/test/js/`, strictly preserving all byte-locked CSS contracts (`mailbox-chat.css`, `styles.css` literal contract blocks) and unified `?v=20261125-ux-polish` asset cache keys in `index.html`. Scoped visual refinements live in `console-shell.css` and `console-theme.css`, backed by dedicated Node `node:test` suites and a loopback preview server mounting real static assets.

**Tech Stack:** Vanilla ES2020 JavaScript, HTML5 `<dialog>` / ARIA, CSS Custom Properties, Node.js built-in test runner (`node:test`), Python `ThreadingHTTPServer` (preview fixture host).

---

### Task 1: Overlay Stack, Non-Blocking Toast Stack, Action Dialog Link Mode & Theme Polish

**Files:**
- Modify: `src/main/resources/static/index.html`
- Modify: `src/main/resources/static/console-shell.css`
- Modify: `src/main/resources/static/console-theme.css`
- Modify: `src/main/resources/static/app.js`
- Test: `src/test/js/uxOverlayAndToast.test.js`

**Steps:**
1. Write `src/test/js/uxOverlayAndToast.test.js` covering:
   - Single `Escape` closes `#previewDrawer` without closing underlying `#composeTemplateModal` / `#qaRuleModal` / `#replySnippetModal`.
   - Dirty editor modal prompts confirmation on backdrop/Escape close, while clean modal closes immediately.
   - `showStatus` renders into `#consoleToastStack` with severity, deduplication, and manual close while keeping `#statusBar` text updated without layout shift.
   - `ACTION_DIALOG_SCHEMAS["insert-link"]` validates HTTP(S)/mailto URLs and normalizes bare domains.
   - `console-theme.css` neutralizes default secondary `.button` in dark theme while preserving `.button.primary`, `.button.secondary`, and `.button.danger`.
2. Implement `#consoleToastStack` and unified Escape + dirty modal guard + `insert-link` dialog + non-blocking alert bridge in `app.js`, and z-index / modal / dark button refinements in `console-shell.css` & `console-theme.css`.
3. Run `node --test src/test/js/uxOverlayAndToast.test.js src/test/js/consoleTheme.test.js src/test/js/overlayAndDialogContrast.test.js`.

---

### Task 2: RAG Trust Reply Workbench Verbatim Isolation, Fact Picker & Copy Feedback

**Files:**
- Modify: `src/main/resources/static/trust-reply-workbench.js`
- Modify: `src/main/resources/static/console-shell.css`
- Test: `src/test/js/uxWorkbenchEnhancements.test.js`

**Steps:**
1. Write `src/test/js/uxWorkbenchEnhancements.test.js` verifying:
   - Editing a `VERBATIM` paragraph in real DOM structure (with `<span class="trust-reply-para-tag" contenteditable="false">逐字</span>`) never appends `"逐字"` into `buildAssembly(state).text`.
   - Pressing `Enter` in `[data-role="add-fact-input"]` triggers `addFact` and re-composes.
   - Fact suggestion dropdown filters `/api/rag-kb/facts` by `factCode` / `title` and clicking an option adds the fact.
   - Clicking `copy-draft` shows `"已复制 ✓"` feedback and cleans up timers on `unmount()`.
2. Update `trust-reply-workbench.js` (zero inline `style=`, preserving `OPTION_KEYS` and exported pure functions) and add scoped `.trust-reply-*` dropdown styles in `console-shell.css`.
3. Run `node --test src/test/js/uxWorkbenchEnhancements.test.js src/test/js/ragWorkbenchRender.test.js src/test/js/trustReplyWorkbenchSharedMount.test.js`.

---

### Task 3: MailboxChat Resizable Split Pane, Keyboard Triage & Jump-to-Reply Anchor

**Files:**
- Modify: `src/main/resources/static/mailbox-chat.js`
- Modify: `src/main/resources/static/console-shell.css`
- Test: `src/test/js/uxMailboxEnhancements.test.js`

**Steps:**
1. Write `src/test/js/uxMailboxEnhancements.test.js` verifying:
   - `.mc-resizer` inside `aside.mc-experts` adjusts `--mc-left-width` (`280px`..`520px`), persists to `localStorage`, resets on `dblclick`, and keeps `.mail-chat` child structure intact.
   - `/` focuses search input, `j`/`k` (or `ArrowDown`/`ArrowUp` on list) navigates conversations when focus is outside editable inputs, and `Ctrl/Meta+Enter` inside `.mc-editor` triggers send.
   - `data-action="mc-jump-compose"` opens manual compose `<details>` and scrolls/focuses `.mc-editor`.
   - `.mc-person` sets `data-has-pending="true"` when `displayPendingCount` or `pendingCount > 0`.
2. Implement in `mailbox-chat.js` and `console-shell.css` (all new classes prefixed `mc-`, zero edits to byte-locked `mailbox-chat.css`).
3. Run `node --test src/test/js/uxMailboxEnhancements.test.js src/test/js/mailboxChatStyle.test.js src/test/js/mailboxChatBehavior.test.js`.

---

### Task 4: Contacts Quick Search, Reset Bar, Viewport-Priority Cache & Table Typography

**Files:**
- Modify: `src/main/resources/static/index.html`
- Modify: `src/main/resources/static/app.js`
- Modify: `src/main/resources/static/contact-details.js`
- Modify: `src/main/resources/static/console-shell.css`
- Test: `src/test/js/uxContactsEnhancements.test.js`

**Steps:**
1. Write `src/test/js/uxContactsEnhancements.test.js` verifying:
   - `#contactQuickSearch` filters visible contacts and queries `/api/mail/unmatched-inbound/search-contacts` for cross-page jump; `#contactClearFiltersBtn` resets active filters to defaults.
   - DB-mode filter notice `#contactDbModeNotice` displays clearly when `needsAttention` or `replyMode` disables ES-only filters.
   - `contact-details.js` retains cached profiles/notes across `loadContacts` calls (bounded LRU) and schedules viewport-visible rows ahead of off-screen rows while keeping `cachedRead` signature intact.
   - Duplicate `#loadQaAuditBtn` listener in `app.js` is removed; QA rules table supports instant keyword filtering (`#qaRuleSearchInput`).
2. Implement in `index.html`, `app.js`, `contact-details.js`, and `console-shell.css` (including `tabular-nums` table alignment and flat hover refinement), and bump unified asset cache key in `index.html` to `20261125-ux-polish`.
3. Run `node --test src/test/js/uxContactsEnhancements.test.js src/test/js/contactDetailsCache.test.js src/test/js/contactsLayoutDefault.test.js src/test/js/expertFilterLayout.test.js src/test/js/loadContactsFilter.test.js`.

---

### Task 5: Full Regression Suite & Real-Entry Interactive Frontend Preview Server

**Files:**
- Create: `docs/mockups/ux-optimization-preview/server.py`
- Create: `docs/mockups/ux-optimization-preview/preview-fixtures.js`

**Steps:**
1. Run syntax checks (`node --check`) on all modified JS files and execute the entire frontend test suite (`node --test src/test/js/*.test.js`).
2. Create `docs/mockups/ux-optimization-preview/server.py` + `preview-fixtures.js` serving 100% real `src/main/resources/static/` assets on `http://127.0.0.1:18780` with interactive in-memory API fixtures for Mailbox, RAG Workbench, Contacts, Templates + Preview Drawer, Accounts, and QA Rules.
3. Start the loopback preview server as a background daemon, verify HTTP 200 responses on key routes/assets, and update `docs/plans/task.md`.
