# Batch Console Editor And Manual Execution Refinement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Simplify scheduled-task editing and make manual execution reliably inherit, search, display, and execute supported task templates.

**Architecture:** Keep mail type internal and derive it from the selected compose template. Add small frontend helpers for editor state, manual source selection, searchable configuration loading, and supported template resolution; reuse the existing API and confirmation flow. Preserve scheduled enabled state internally while leaving enable/disable control exclusively in the task list.

**Tech Stack:** Static HTML/CSS, browser JavaScript, Node.js `node:test`, Spring Boot/Kotlin backend validation.

## Global Constraints

- Work directly on `main`, as explicitly authorized by the user.
- Do not add frontend dependencies.
- Do not expose a send-type field or scheduled-enabled field in either form.
- Only `INTRODUCTION` and `MATERIAL_REMINDER` compose templates are selectable for batch execution.
- A null template means `INTRODUCTION`.
- Preserve unrelated working-tree changes.
- Implement every behavior through a red-green TDD cycle.

---

### Task 1: Scheduled Editor Isolation And External Enable Control

**Files:**
- Modify: `src/main/resources/static/index.html`
- Modify: `src/main/resources/static/app.js`
- Modify: `src/main/resources/static/styles.css`
- Test: `src/test/js/batchSendTaskConsoleVisualFix.test.js`

**Interfaces:**
- Consumes: `batchTaskState.editorMode`, `BatchSendTaskConfigView.autoEnabled`, `showBatchConfigEditor(config)`, `hideBatchConfigEditor()`.
- Produces: `batchTaskState.editorAutoEnabled: boolean`; editor state that hides the manual tab and preserves enabled state without a form control.

- [ ] **Step 1: Write failing editor-state assertions**

Add assertions requiring removal of `batchConfigEditorAutoEnabled`, editor-state hiding of the manual tab, restoration on exit, and no schedule switch markup or CSS.

```js
it("keeps enable control outside the config editor", () => {
    assert.ok(!html.includes('id="batchConfigEditorAutoEnabled"'));
    assert.ok(!html.includes('class="batch-config-schedule-toggle"'));
    assert.ok(app.includes("editorAutoEnabled"));
    assert.ok(!app.includes('getElementById("batchConfigEditorAutoEnabled")'));
});

it("hides the manual tab only while the editor is open", () => {
    assert.ok(html.includes('id="batchManualTab"'));
    assert.ok(app.includes('manualTab.hidden = true'));
    assert.ok(app.includes('manualTab.hidden = false'));
});
```

- [ ] **Step 2: Run the targeted test and verify RED**

Run: `node --test src/test/js/batchSendTaskConsoleVisualFix.test.js`

Expected: FAIL because the checkbox remains and the manual tab has no editor-state visibility handling.

- [ ] **Step 3: Implement editor isolation and enabled-state preservation**

Add `id="batchManualTab"` to the existing manual tab. Remove the editor checkbox block and its schedule-switch CSS. Add `editorAutoEnabled` to initial/reset state. In `showBatchConfigEditor(config)`, set `editorAutoEnabled` to `config ? config.autoEnabled : false` and hide the manual tab. In `hideBatchConfigEditor()`, restore the manual tab. Build save payloads with:

```js
autoEnabled: Boolean(batchTaskState.editorAutoEnabled)
```

Keep frequency/time inputs; only enable/disable control moves outside the editor.

- [ ] **Step 4: Run the targeted test and verify GREEN**

Run: `node --test src/test/js/batchSendTaskConsoleVisualFix.test.js`

Expected: PASS.

---

### Task 2: Reliable Manual Source Selection And Searchable Combobox

**Files:**
- Modify: `src/main/resources/static/index.html`
- Modify: `src/main/resources/static/app.js`
- Modify: `src/main/resources/static/styles.css`
- Create: `src/test/js/batchSendTaskConsoleInteraction.test.js`

**Interfaces:**
- Consumes: `/api/mail/batch-send/configs?q={query}`, `deepCloneConfig(config)`, `switchBatchSendTab("manual")`.
- Produces: `applyBatchManualSource(config)`; `loadBatchManualSourceOptions(query)`; a `role="combobox"` search field that opens on focus and filters on input.

- [ ] **Step 1: Write failing runtime tests for row-to-manual selection**

Use a VM sandbox with element stubs and assert that `openManualTabFromConfig(7)` synchronizes state, visible query, hidden IDs, source summary, and manual form before/after switching tabs.

```js
it("selects the clicked config when opening the manual tab", () => {
    const sb = createSandbox([{ id: 7, configName: "材料提醒任务", mailType: "MATERIAL_REMINDER" }]);
    sb.openManualTabFromConfig(7);
    assert.strictEqual(sb.batchTaskState.manualSource.id, 7);
    assert.strictEqual(sb.elements.batchManualSourceQuery.value, "材料提醒任务");
    assert.strictEqual(sb.elements.batchManualSourceId.value, "7");
    assert.strictEqual(sb.switchedTab, "manual");
});
```

- [ ] **Step 2: Write failing combobox loading tests**

Assert empty-query focus calls `/api/mail/batch-send/configs`, renders all options, non-empty input URL-encodes `q`, and stale responses cannot replace newer results.

```js
it("opens all task options for an empty query", async () => {
    await sb.loadBatchManualSourceOptions("");
    assert.strictEqual(sb.apiCalls[0], "/api/mail/batch-send/configs");
    assert.strictEqual(sb.dropdown.hidden, false);
});
```

- [ ] **Step 3: Run the interaction test and verify RED**

Run: `node --test src/test/js/batchSendTaskConsoleInteraction.test.js`

Expected: FAIL because shared source application and empty-query dropdown loading do not exist.

- [ ] **Step 4: Implement shared source application and combobox behavior**

Create `applyBatchManualSource(config)` to clone the source/draft, update query and hidden identity inputs, call `updateManualSourceInfo()`, and fill the form. Make both `openManualTabFromConfig(id)` and `selectBatchManualSource(id)` call it. Normalize IDs with `Number(c.id) === Number(id)`.

Replace the plain input wrapper with labeled combobox markup containing search and chevron. On focus, call `loadBatchManualSourceOptions("")`; on input, debounce and call it with the query. Render an explicit no-results row. Set `aria-expanded` consistently and close on Escape/outside click.

- [ ] **Step 5: Run the interaction test and verify GREEN**

Run: `node --test src/test/js/batchSendTaskConsoleInteraction.test.js`

Expected: PASS.

---

### Task 3: Complete Supported Template Selection And Manual Page Layout

**Files:**
- Modify: `src/main/resources/static/index.html`
- Modify: `src/main/resources/static/app.js`
- Modify: `src/main/resources/static/styles.css`
- Test: `src/test/js/batchSendTaskConsoleInteraction.test.js`
- Test: `src/test/js/batchSendTaskConsoleVisualFix.test.js`

**Interfaces:**
- Consumes: `/api/compose-templates`, template fields `id`, `templateName`, `mailType`, `enabled`.
- Produces: `supportedBatchComposeTemplates()`; `resolveBatchTemplateMailType(templateId)`; refreshed editor/manual selectors after asynchronous lookup completion.

- [ ] **Step 1: Write failing template behavior tests**

Assert selectors show enabled introduction and material-reminder templates, exclude disabled/unsupported templates, null template resolves to `INTRODUCTION`, material template resolves to `MATERIAL_REMINDER`, and manual execution snapshot uses the currently selected template type.

```js
it("shows all enabled supported batch templates", () => {
    sb.batchTaskState.preloadedTemplates = [
        { id: 1, templateName: "项目介绍", mailType: "INTRODUCTION", enabled: true },
        { id: 2, templateName: "材料提醒", mailType: "MATERIAL_REMINDER", enabled: true },
        { id: 3, templateName: "QA", mailType: "QA_AUTO_REPLY", enabled: true }
    ];
    sb.fillBatchManualTemplateSelector(2);
    assert.match(sb.templateSelect.innerHTML, /项目介绍/);
    assert.match(sb.templateSelect.innerHTML, /材料提醒/);
    assert.doesNotMatch(sb.templateSelect.innerHTML, /QA/);
    assert.strictEqual(sb.resolveBatchTemplateMailType(2), "MATERIAL_REMINDER");
});
```

- [ ] **Step 2: Write failing manual layout assertions**

Require `batch-manual-source-layout`, `batch-manual-section`, grouped “模板与收件范围” and “发送控制” headings, and sticky manual actions.

- [ ] **Step 3: Run both tests and verify RED**

Run: `node --test src/test/js/batchSendTaskConsoleInteraction.test.js src/test/js/batchSendTaskConsoleVisualFix.test.js`

Expected: FAIL because templates are type-filtered and the old flat field-card layout remains.

- [ ] **Step 4: Implement supported template derivation and async refresh**

Implement:

```js
function supportedBatchComposeTemplates() {
    return (batchTaskState.preloadedTemplates || []).filter(function(t) {
        return t.enabled && (t.mailType === "INTRODUCTION" || t.mailType === "MATERIAL_REMINDER");
    });
}

function resolveBatchTemplateMailType(templateId) {
    if (!templateId) return "INTRODUCTION";
    var template = supportedBatchComposeTemplates().find(function(t) {
        return Number(t.id) === Number(templateId);
    });
    return template ? template.mailType : "INTRODUCTION";
}
```

Use the same supported list in scheduled/manual selectors. Include `mailType` in `readManualFormValues()` and use `values.mailType` in the execution snapshot. After `preloadBatchSendLookups()` receives templates, refill visible selectors while retaining current selected IDs.

- [ ] **Step 5: Implement grouped manual layout**

Restructure the manual panel into source selection/summary, “模板与收件范围”, and “发送控制” sections. Reuse editor section/grid styling, retain every existing field/diff element ID, and make the execution action bar sticky. Add responsive one-column rules below 760px.

- [ ] **Step 6: Run both tests and verify GREEN**

Run: `node --test src/test/js/batchSendTaskConsoleInteraction.test.js src/test/js/batchSendTaskConsoleVisualFix.test.js`

Expected: PASS.

---

### Task 4: Full Verification

**Files:**
- Verify all modified frontend and test files.

**Interfaces:**
- Consumes: completed Tasks 1-3.
- Produces: test and diff evidence for handoff.

- [ ] **Step 1: Run all Node tests**

Run: `node --test src/test/js/*.test.js`

Expected: all tests pass with zero failures.

- [ ] **Step 2: Run the full Maven suite with Java 11**

Run: `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test`

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Check working-tree integrity**

Run: `git diff --check && git status --short`

Expected: no whitespace errors; unrelated user changes remain present and untouched.
