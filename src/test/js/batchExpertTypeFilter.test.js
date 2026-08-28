const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const appJsPath = path.join(__dirname, "..", "..", "main", "resources", "static", "app.js");
const appJsSource = fs.readFileSync(appJsPath, "utf-8");

function extractFn(name) {
    const regex = new RegExp("(?:async\\s+)?function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}");
    const match = appJsSource.match(regex);
    if (!match) throw new Error("Could not find " + name + " in app.js");
    return match[0];
}

// The multi-picker registry is a top-level `var` assignment; capture its object literal.
function extractRegistry() {
    const start = appJsSource.indexOf("var BATCH_MULTI_PICKER_REGISTRY = {");
    if (start < 0) throw new Error("Could not find BATCH_MULTI_PICKER_REGISTRY in app.js");
    const bodyStart = appJsSource.indexOf("{", start);
    let depth = 0;
    let end = -1;
    for (let i = bodyStart; i < appJsSource.length; i++) {
        const ch = appJsSource[i];
        if (ch === "{") depth++;
        else if (ch === "}") {
            depth--;
            if (depth === 0) { end = i + 1; break; }
        }
    }
    if (end < 0) throw new Error("Could not balance BATCH_MULTI_PICKER_REGISTRY literal");
    return "var BATCH_MULTI_PICKER_REGISTRY = " + appJsSource.slice(bodyStart, end) + ";";
}

function createElementStore() {
    const store = new Map();
    function el(id) {
        if (!store.has(id)) {
            store.set(id, {
                id,
                textContent: "",
                className: "",
                innerHTML: "",
                hidden: null,
                disabled: false,
                value: "",
                checked: false,
                title: "",
                dataset: {},
                parentElement: null
            });
        }
        return store.get(id);
    }
    return { el, get: (id) => store.get(id) };
}

function createSandbox(extra) {
    const store = createElementStore();
    const sandbox = Object.assign({
        document: { getElementById: (id) => store.el(id) },
        escapeHtml: (v) => String(v == null ? "" : v)
            .replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;").replaceAll("'", "&#39;"),
        showStatus: () => {},
        api: async () => ({}),
        hideBatchConfigEditor: () => {},
        loadBatchConfigList: () => {},
        readBatchTagPickerValue: () => [],
        readBatchRegionPickerValue: () => [],
        readBatchMultiPickerValue: () => [],
        gateToggleChecked: () => false,
        resolveBatchTemplateMailType: () => "INTRODUCTION"
    }, extra || {});
    vm.createContext(sandbox);
    sandbox.__store = store;
    return sandbox;
}

describe("Batch ExpertType Filter (child 02)", () => {
    it("registry registers both expert-type pickers with correct previewKind", () => {
        const sb = createSandbox();
        vm.runInContext(extractRegistry(), sb);
        vm.runInContext(extractFn("batchExpertTypeOptions"), sb);

        const editor = vm.runInContext("BATCH_MULTI_PICKER_REGISTRY.batchConfigEditorExpertTypes", sb);
        const manual = vm.runInContext("BATCH_MULTI_PICKER_REGISTRY.batchManualExpertTypes", sb);
        assert.ok(editor, "editor registry entry must exist");
        assert.ok(manual, "manual registry entry must exist");
        assert.strictEqual(editor.previewKind, "editor");
        assert.strictEqual(manual.previewKind, "manual");
        assert.strictEqual(editor.emptyText, "没有匹配类型");
        assert.strictEqual(manual.emptyText, "没有匹配类型");
    });

    it("batchExpertTypeOptions lists enum values plus UNCLASSIFIED with child-01 chip labels", () => {
        const sb = createSandbox();
        vm.runInContext(extractFn("batchExpertTypeOptions"), sb);
        const options = [...vm.runInContext("batchExpertTypeOptions()", sb)];
        assert.deepStrictEqual(options.map((o) => o.value), [
            "PRODUCTION_RND", "ACADEMIC_RND", "HYBRID_RND", "SERVICE_ONLY", "OUT_OF_SCOPE", "UNKNOWN", "UNCLASSIFIED"
        ]);
        // I2-1: labels identical to child 01 chip text (app.js expertTypeLabels).
        assert.deepStrictEqual(Object.fromEntries(options.map((o) => [o.value, o.label])), {
            PRODUCTION_RND: "生产研发",
            ACADEMIC_RND: "学术科研",
            HYBRID_RND: "混合研发",
            SERVICE_ONLY: "纯服务",
            OUT_OF_SCOPE: "医学越界",
            UNKNOWN: "未知",
            UNCLASSIFIED: "未分类"
        });
    });

    it("readBatchMultiPickerValue returns empty array for empty hidden input", () => {
        const sb = createSandbox();
        vm.runInContext(extractFn("readBatchMultiPickerValue"), sb);
        // Default stub value is "" → must yield [] (unlimited semantics).
        const values = [...vm.runInContext('readBatchMultiPickerValue("batchConfigEditorExpertTypes")', sb)];
        assert.deepStrictEqual(values, []);
    });

    it("editor preview snapshot payload includes expertTypes key", () => {
        const sb = createSandbox();
        vm.runInContext(extractFn("buildConfigEditorRecipientSnapshot"), sb);
        const snapshot = { ...vm.runInContext("buildConfigEditorRecipientSnapshot()", sb) };
        assert.ok(Object.prototype.hasOwnProperty.call(snapshot, "expertTypes"), "editor preview snapshot must carry expertTypes");
        assert.deepStrictEqual(snapshot.expertTypes, []);
    });

    it("editor save payload includes expertTypes key", async () => {
        let captured = null;
        const sb = createSandbox({
            api: async (url, opts) => {
                captured = opts && opts.body ? JSON.parse(opts.body) : null;
                return {};
            },
            // I3-1/I3-6: 编辑器保存要求研发类型非空（空集合被前端拦截），
            // 新建配置默认三类（与 batchExpertTypeOptions 前三个 value 逐字一致）。
            readBatchMultiPickerValue: () => ["PRODUCTION_RND", "ACADEMIC_RND", "HYBRID_RND"]
        });
        sb.batchTaskState = { editorAutoEnabled: false, editorMode: "create", editorId: null };
        sb.__store.el("batchConfigEditorName").value = "任务X";
        vm.runInContext(extractFn("saveBatchConfigEditor"), sb);
        await vm.runInContext("saveBatchConfigEditor()", sb);
        assert.ok(captured, "api must be invoked with a payload");
        assert.ok(Object.prototype.hasOwnProperty.call(captured, "expertTypes"), "editor save payload must carry expertTypes");
        assert.deepStrictEqual(captured.expertTypes, ["PRODUCTION_RND", "ACADEMIC_RND", "HYBRID_RND"]);
    });

    it("editor save allows empty expertTypes for MATERIAL_REMINDER (R1)", async () => {
        let apiCalls = [];
        const statuses = [];
        const sb = createSandbox({
            api: async (url, opts) => {
                apiCalls.push({ url, body: opts && opts.body ? JSON.parse(opts.body) : null });
                return {};
            },
            // R1: MATERIAL_REMINDER 保留历史空集合行为 —— 类型判定经 resolveBatchTemplateMailType 收口到 INTRODUCTION。
            resolveBatchTemplateMailType: () => "MATERIAL_REMINDER",
            readBatchMultiPickerValue: () => [],
            showStatus: (msg, kind) => statuses.push({ msg, kind })
        });
        sb.batchTaskState = { editorAutoEnabled: false, editorMode: "create", editorId: null };
        sb.__store.el("batchConfigEditorName").value = "材料提醒任务";
        sb.__store.el("batchConfigEditorTemplateId").value = "7";
        vm.runInContext(extractFn("saveBatchConfigEditor"), sb);
        await vm.runInContext("saveBatchConfigEditor()", sb);
        assert.strictEqual(apiCalls.length, 1, "MATERIAL_REMINDER empty types must reach the API route");
        assert.ok(apiCalls[0].url.startsWith("/api/mail/batch-send/configs"), "create/update route must be the same");
        assert.deepStrictEqual(apiCalls[0].body.expertTypes, [], "payload must carry empty expertTypes");
        assert.ok(
            !statuses.some((s) => s.msg === "请至少选择一个研发类型"),
            "no INTRODUCTION-only error may be emitted for MATERIAL_REMINDER"
        );
    });

    it("editor save still blocks empty expertTypes for INTRODUCTION with exact error (R1)", async () => {
        let apiCalls = 0;
        const statuses = [];
        const sb = createSandbox({
            api: async () => { apiCalls++; return {}; },
            // default resolveBatchTemplateMailType -> INTRODUCTION
            readBatchMultiPickerValue: () => [],
            showStatus: (msg, kind) => statuses.push({ msg, kind })
        });
        sb.batchTaskState = { editorAutoEnabled: false, editorMode: "create", editorId: null };
        sb.__store.el("batchConfigEditorName").value = "任务Y";
        vm.runInContext(extractFn("saveBatchConfigEditor"), sb);
        await vm.runInContext("saveBatchConfigEditor()", sb);
        assert.strictEqual(apiCalls, 0, "INTRODUCTION empty types must not reach the API");
        assert.ok(
            statuses.some((s) => s.msg === "请至少选择一个研发类型" && s.kind === "error"),
            "INTRODUCTION empty types must emit the exact error text"
        );
    });

    it("manual form values payload includes expertTypes key", () => {
        const sb = createSandbox();
        vm.runInContext(extractFn("readManualFormValues"), sb);
        const values = { ...vm.runInContext("readManualFormValues()", sb) };
        assert.ok(Object.prototype.hasOwnProperty.call(values, "expertTypes"), "manual payload must carry expertTypes");
        assert.deepStrictEqual(values.expertTypes, []);
    });
});
