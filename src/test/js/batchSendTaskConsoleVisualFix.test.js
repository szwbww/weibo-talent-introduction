const fs = require("fs");
const path = require("path");
const assert = require("assert");
const { describe, it } = require("node:test");

const root = path.join(__dirname, "..", "..", "main", "resources");
const html = fs.readFileSync(path.join(root, "static", "index.html"), "utf-8");
const css = fs.readFileSync(path.join(root, "static", "styles.css"), "utf-8");
const app = fs.readFileSync(path.join(root, "static", "app.js"), "utf-8");
const migrationPath = path.join(root, "db", "migration", "V74__repair_batch_send_task_config_encoding.sql");

describe("batch send task console visual repair", () => {
    it("uses a scoped horizontal modal header with subtitle", () => {
        assert.ok(html.includes('class="modal-header batch-send-task-header"'));
        assert.ok(html.includes("管理定时任务、手动执行与执行日志"));
        assert.ok(css.includes(".batch-send-task-header"));
        assert.ok(css.includes(".batch-send-task-subtitle"));
    });

    it("renders compact scoped status switches instead of global checkboxes", () => {
        assert.ok(app.includes('class="batch-task-status-toggle"'));
        assert.ok(app.includes('class="batch-task-status-switch"'));
        assert.ok(app.includes('c.autoEnabled ? "已启用" : "已停用"'));
        assert.ok(css.includes('.batch-task-status-toggle input[type="checkbox"]'));
        assert.ok(css.includes("width: 36px"));
        assert.ok(css.includes("height: 20px"));
        assert.match(css, /\.batch-task-scope-line\s*\{[^}]*display:\s*block;/);
    });

    it("uses content-adaptive modal height and a task count footer", () => {
        assert.match(css, /\.task-modal\.batch-send-task-modal\s*\{[\s\S]*?height:\s*auto;/);
        assert.ok(html.includes('id="batchConfigCount"'));
        assert.ok(app.includes('count.textContent = "共 " + configs.length + " 个任务"'));
    });

    it("bumps the stylesheet cache key", () => {
        assert.ok(html.includes('styles.css?v=20260729-trust-reply-unsupported-answer-v1-04'));
        assert.ok(html.includes('trust-reply-workbench.js?v=20260729-trust-reply-unsupported-answer-v1-04'));
        assert.ok(html.includes('app.js?v=20260729-trust-reply-unsupported-answer-v1-04'));
    });

    it("removes send-type controls and template filtering from the config editor", () => {
        assert.ok(!html.includes('id="batchConfigEditorMailType"'));
        assert.ok(!app.includes("batchConfigEditorMailType"));
        assert.match(app, /function fillBatchConfigEditorTemplateSelector\(selectedId\)/);
        const editorSelector = app.match(/function fillBatchConfigEditorTemplateSelector\(selectedId\)\s*\{[\s\S]*?\n\}/)[0];
        assert.ok(!editorSelector.includes("enabledTyped"));
        assert.ok(!editorSelector.includes("mailType"));
    });

    it("groups the config editor into clear task sections", () => {
        assert.ok(html.includes('class="batch-config-editor"'));
        ["基础信息", "收件范围", "发送控制", "定时调度"].forEach((title) => {
            assert.ok(html.includes(`<h4>${title}</h4>`), `missing editor section: ${title}`);
        });
        assert.ok(css.includes(".batch-config-editor-section"));
        assert.ok(css.includes(".batch-config-editor-grid"));
        assert.ok(app.includes('classList.add("is-editing")'));
        assert.ok(app.includes('classList.remove("is-editing")'));
    });

    it("keeps enable control outside the config editor", () => {
        assert.ok(!html.includes('id="batchConfigEditorAutoEnabled"'));
        assert.ok(!html.includes('class="batch-config-schedule-toggle"'));
        assert.ok(app.includes("editorAutoEnabled"));
        assert.ok(!app.includes('getElementById("batchConfigEditorAutoEnabled")'));
        assert.ok(!css.includes(".batch-config-schedule-toggle"));
        assert.match(css, /\.batch-config-editor-actions\s*\{[\s\S]*?position:\s*sticky;/);
    });

    it("hides the manual tab only while the editor is open", () => {
        assert.ok(html.includes('id="batchManualTab"'));
        assert.ok(app.includes('manualTab.hidden = true'));
        assert.ok(app.includes('manualTab.hidden = false'));
    });

    it("uses a searchable task combobox on the manual page", () => {
        assert.ok(html.includes('role="combobox"'));
        assert.ok(html.includes('aria-controls="batchManualSourceDropdown"'));
        assert.ok(html.includes('class="batch-manual-source-combobox"'));
        assert.ok(css.includes(".batch-manual-source-combobox"));
    });

    it("groups and stabilizes the manual execution form", () => {
        assert.ok(html.includes('class="batch-manual-source-layout"'));
        assert.ok(html.includes('class="batch-manual-section"'));
        assert.ok(html.includes("模板与收件范围"));
        assert.ok(html.includes("发送控制"));
        assert.match(css, /\.batch-manual-actions-sticky\s*\{[\s\S]*?position:\s*sticky;/);
    });

    it("uses searchable multi-select tag pickers in editor and manual forms", () => {
        assert.ok(html.includes('data-tag-picker="batchConfigEditorTags"'));
        assert.ok(html.includes('data-tag-picker="batchManualTags"'));
        assert.ok(html.includes('id="batchConfigEditorTagsSearch"'));
        assert.ok(html.includes('id="batchManualTagsSearch"'));
        assert.ok(html.includes('id="batchConfigEditorTagsDropdown"'));
        assert.ok(html.includes('id="batchManualTagsDropdown"'));
        assert.ok(css.includes(".batch-tag-picker"));
        assert.ok(css.includes(".batch-tag-picker-chip"));
        assert.ok(app.includes("loadBatchTagOptions"));
        assert.ok(app.includes("readBatchTagPickerValue"));
    });

    it("repairs legacy seed names with an ASCII-only utf8mb4 migration", () => {
        assert.ok(fs.existsSync(migrationPath), "V74 repair migration must exist");
        const sql = fs.readFileSync(migrationPath, "utf-8");
        assert.match(sql, /ALTER TABLE batch_send_task_config[\s\S]*CHARACTER SET utf8mb4/i);
        assert.ok(sql.includes("E9BB98E8AEA4E4BB8BE7BB8DE982AEE4BBB6E4BBBBE58AA1"));
        assert.ok(sql.includes("E69D90E69699E68F90E98692E4BBBBE58AA1"));
        assert.match(sql, /CONVERT\(UNHEX\(/i);
        assert.ok(!sql.includes("默认介绍邮件任务"), "migration must stay ASCII-only");
        assert.ok(!sql.includes("材料提醒任务"), "migration must stay ASCII-only");
    });
});
