const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const appPath = path.join(__dirname, "..", "..", "main", "resources", "static", "app.js");
const appSource = fs.readFileSync(appPath, "utf-8");

function extractFn(name) {
    const regex = new RegExp("(?:async\\s+)?function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}");
    const match = appSource.match(regex);
    return match ? match[0] : null;
}

function element(value = "") {
    return {
        value,
        textContent: "",
        innerHTML: "",
        hidden: true,
        dataset: {},
        setAttribute() {},
        querySelectorAll() { return []; }
    };
}

describe("batch send task console interactions", () => {
    it("selects and displays the clicked config before opening the manual tab", () => {
        const applySource = extractFn("applyBatchManualSource");
        assert.ok(applySource, "applyBatchManualSource must centralize visible and internal source state");

        const elements = {
            batchManualSourceQuery: element(),
            batchManualSourceId: element(),
            batchManualSourceUpdatedAt: element()
        };
        const calls = { sourceInfo: false, form: false, switchedTab: null };
        const sandbox = {
            batchTaskState: {
                configs: [{
                    id: "7",
                    configName: "材料提醒任务",
                    mailType: "MATERIAL_REMINDER",
                    updatedAt: "2026-07-15T08:00:00"
                }],
                manualSource: null,
                manualDraft: null
            },
            document: { getElementById: (id) => elements[id] || null },
            deepCloneConfig: (config) => ({ ...config }),
            updateManualSourceInfo: () => { calls.sourceInfo = true; },
            fillManualFormFromDraft: () => { calls.form = true; },
            switchBatchSendTab: (tab) => { calls.switchedTab = tab; }
        };
        vm.createContext(sandbox);
        vm.runInContext(applySource, sandbox);
        vm.runInContext(extractFn("openManualTabFromConfig"), sandbox);

        sandbox.openManualTabFromConfig(7);

        assert.strictEqual(sandbox.batchTaskState.manualSource.id, "7");
        assert.strictEqual(elements.batchManualSourceQuery.value, "材料提醒任务");
        assert.strictEqual(elements.batchManualSourceId.value, "7");
        assert.strictEqual(elements.batchManualSourceUpdatedAt.value, "2026-07-15T08:00:00");
        assert.strictEqual(calls.sourceInfo, true);
        assert.strictEqual(calls.form, true);
        assert.strictEqual(calls.switchedTab, "manual");
    });

    it("loads all task options when the searchable combobox opens with an empty query", async () => {
        const loadOptions = extractFn("loadBatchManualSourceOptions");
        assert.ok(loadOptions, "loadBatchManualSourceOptions must support an empty query");

        const apiCalls = [];
        const rendered = [];
        const sandbox = {
            batchManualSourceRequestToken: 0,
            api: async (url) => {
                apiCalls.push(url);
                return [{ id: 1, configName: "默认介绍邮件任务" }];
            },
            renderBatchManualSourceDropdown: (configs) => rendered.push(configs),
            renderBatchManualSourceEmpty: () => {},
            console
        };
        vm.createContext(sandbox);
        vm.runInContext(loadOptions, sandbox);

        await sandbox.loadBatchManualSourceOptions("");

        assert.deepStrictEqual(apiCalls, ["/api/mail/batch-send/configs"]);
        assert.strictEqual(rendered.length, 1);
        assert.strictEqual(rendered[0][0].configName, "默认介绍邮件任务");
    });

    it("shows every enabled supported batch template and derives its mail type", () => {
        const supportedTemplates = extractFn("supportedBatchComposeTemplates");
        const resolveMailType = extractFn("resolveBatchTemplateMailType");
        assert.ok(supportedTemplates, "supportedBatchComposeTemplates must exist");
        assert.ok(resolveMailType, "resolveBatchTemplateMailType must exist");

        const templateSelect = element();
        const sandbox = {
            batchTaskState: {
                preloadedTemplates: [
                    { id: 1, templateName: "项目介绍", mailType: "INTRODUCTION", enabled: true },
                    { id: 2, templateName: "材料提醒", mailType: "MATERIAL_REMINDER", enabled: true },
                    { id: 3, templateName: "已停用", mailType: "INTRODUCTION", enabled: false },
                    { id: 4, templateName: "QA", mailType: "QA_AUTO_REPLY", enabled: true }
                ]
            },
            document: { getElementById: (id) => id === "batchManualTemplateId" ? templateSelect : null },
            escapeHtml: (value) => String(value == null ? "" : value)
        };
        vm.createContext(sandbox);
        vm.runInContext(supportedTemplates, sandbox);
        vm.runInContext(resolveMailType, sandbox);
        vm.runInContext(extractFn("fillBatchManualTemplateSelector"), sandbox);

        sandbox.fillBatchManualTemplateSelector(2);

        assert.match(templateSelect.innerHTML, /项目介绍/);
        assert.match(templateSelect.innerHTML, /材料提醒/);
        assert.doesNotMatch(templateSelect.innerHTML, /已停用/);
        assert.doesNotMatch(templateSelect.innerHTML, /QA/);
        assert.strictEqual(templateSelect.value, "2");
        assert.strictEqual(sandbox.resolveBatchTemplateMailType(null), "INTRODUCTION");
        assert.strictEqual(sandbox.resolveBatchTemplateMailType(2), "MATERIAL_REMINDER");
    });

    it("builds manual execution mail type from the currently selected template", () => {
        const readValues = extractFn("readManualFormValues");
        assert.ok(readValues, "readManualFormValues must exist");
        assert.ok(readValues.includes("resolveBatchTemplateMailType"));

        const confirmExecution = extractFn("confirmManualExecution");
        assert.ok(confirmExecution.includes("mailType: values.mailType"));
    });

    it("refreshes template selectors after asynchronous lookup loading", () => {
        const preload = extractFn("preloadBatchSendLookups");
        assert.ok(preload.includes("refreshBatchTemplateSelectors"));
    });

    it("retains the manual draft template when async templates arrive", () => {
        const refreshSelectors = extractFn("refreshBatchTemplateSelectors");
        assert.ok(refreshSelectors, "refreshBatchTemplateSelectors must exist");
        const manualSelect = element("");
        const selected = [];
        const sandbox = {
            batchTaskState: { manualDraft: { templateId: 2 }, editorMode: null, configs: [] },
            document: {
                getElementById: (id) => id === "batchManualTemplateId" ? manualSelect : null
            },
            fillBatchConfigEditorTemplateSelector: () => {},
            fillBatchManualTemplateSelector: (id) => selected.push(id)
        };
        vm.createContext(sandbox);
        vm.runInContext(refreshSelectors, sandbox);

        sandbox.refreshBatchTemplateSelectors();

        assert.deepStrictEqual(selected, [2]);
    });

    it("detaches a selected task when its search text is cleared and removes diff state", () => {
        const detachSource = extractFn("detachBatchManualSourcePreservingDraft");
        const searchSource = extractFn("handleManualSourceSearch");
        assert.ok(detachSource, "manual source detachment helper must exist");
        assert.ok(searchSource, "manual source search handler must exist");

        const elements = {
            batchManualSourceQuery: element(""),
            batchManualSourceId: element("7"),
            batchManualSourceUpdatedAt: element("2026-07-15T08:00:00")
        };
        const calls = { cleared: 0, sourceInfo: 0, loaded: [] };
        const currentValues = { funnelLevel: "APPLICATION", tags: ["AI"], roundsPerRun: 1 };
        const sandbox = {
            batchTaskState: {
                manualSource: { id: 7, configName: "材料提醒任务" },
                manualDraft: { funnelLevel: "CANDIDATE", tags: [] }
            },
            batchManualSourceSearchTimer: null,
            document: { getElementById: (id) => elements[id] || null },
            readManualFormValues: () => currentValues,
            updateManualSourceInfo: () => { calls.sourceInfo += 1; },
            clearAllDiffMarkers: () => { calls.cleared += 1; },
            loadBatchManualSourceOptions: (query) => calls.loaded.push(query),
            clearTimeout: () => {},
            setTimeout: (callback) => { callback(); return 1; }
        };
        vm.createContext(sandbox);
        vm.runInContext(detachSource, sandbox);
        vm.runInContext(searchSource, sandbox);

        sandbox.handleManualSourceSearch();

        assert.strictEqual(sandbox.batchTaskState.manualSource, null);
        assert.strictEqual(sandbox.batchTaskState.manualDraft.funnelLevel, "APPLICATION");
        assert.deepStrictEqual(Array.from(sandbox.batchTaskState.manualDraft.tags), ["AI"]);
        assert.strictEqual(elements.batchManualSourceId.value, "");
        assert.strictEqual(elements.batchManualSourceUpdatedAt.value, "");
        assert.strictEqual(calls.cleared, 1);
        assert.strictEqual(calls.sourceInfo, 1);
        assert.deepStrictEqual(calls.loaded, [""]);
    });

    it("merges tag aggregations and retains configured tags without duplicates", () => {
        const normalizeTags = extractFn("normalizeBatchTags");
        const mergeTags = extractFn("mergeBatchTagOptions");
        assert.ok(normalizeTags, "normalizeBatchTags must exist");
        assert.ok(mergeTags, "mergeBatchTagOptions must exist");

        const sandbox = {};
        vm.createContext(sandbox);
        vm.runInContext(normalizeTags, sandbox);
        vm.runInContext(mergeTags, sandbox);

        const merged = sandbox.mergeBatchTagOptions([
            [{ tag: "AI", count: 3 }, { tag: "STEM", count: 2 }],
            [{ tag: "AI", count: 5 }, { tag: "材料待补", count: 1 }]
        ], ["历史标签", " AI "]);

        assert.deepStrictEqual(Array.from(merged), ["AI", "STEM", "材料待补", "历史标签"]);
    });

    it("toggles multiple tag picker values and exposes a string array", () => {
        const normalizeTags = extractFn("normalizeBatchTags");
        const readTags = extractFn("readBatchTagPickerValue");
        const setTags = extractFn("setBatchTagPickerValue");
        const toggleTag = extractFn("toggleBatchTagPickerValue");
        assert.ok(normalizeTags && readTags && setTags && toggleTag, "shared tag picker helpers must exist");

        const hidden = element("");
        const rendered = [];
        const sandbox = {
            document: { getElementById: (id) => id === "batchManualTags" ? hidden : null },
            renderBatchTagPicker: (id) => rendered.push(id),
            notifyBatchTagPickerChanged: () => {}
        };
        vm.createContext(sandbox);
        vm.runInContext(normalizeTags, sandbox);
        vm.runInContext(readTags, sandbox);
        vm.runInContext(setTags, sandbox);
        vm.runInContext(toggleTag, sandbox);

        sandbox.toggleBatchTagPickerValue("batchManualTags", "AI");
        sandbox.toggleBatchTagPickerValue("batchManualTags", "STEM");
        assert.deepStrictEqual(Array.from(sandbox.readBatchTagPickerValue("batchManualTags")), ["AI", "STEM"]);
        sandbox.toggleBatchTagPickerValue("batchManualTags", "AI");
        assert.deepStrictEqual(Array.from(sandbox.readBatchTagPickerValue("batchManualTags")), ["STEM"]);
        assert.strictEqual(rendered.length, 3);
    });

    it("shows semantic original values for unrestricted filters", () => {
        const formatDiffValue = extractFn("formatManualDiffValue");
        const computeDiffs = extractFn("computeManualDiffs");
        assert.ok(formatDiffValue, "manual diff values need a semantic formatter");
        assert.ok(computeDiffs.includes("formatManualDiffValue"), "manual diffs must use the semantic formatter");

        const sandbox = {
            batchTaskState: { preloadedTemplates: [] },
            supportedBatchComposeTemplates: () => []
        };
        vm.createContext(sandbox);
        vm.runInContext(formatDiffValue, sandbox);

        assert.strictEqual(sandbox.formatManualDiffValue("funnelLevel", null), "全部层级");
        assert.strictEqual(sandbox.formatManualDiffValue("emailDomain", ""), "全部服务商");
        assert.strictEqual(sandbox.formatManualDiffValue("discipline", null), "全部学科");
        assert.strictEqual(sandbox.formatManualDiffValue("templateId", null), "系统默认介绍邮件模板");
        assert.strictEqual(sandbox.formatManualDiffValue("tags", []), "(无)");
    });

    it("toggles multiple region picker values in BATCH_REGION_OPTIONS order (I-1)", () => {
        const regionOptionsSrc = appSource.match(/var BATCH_REGION_OPTIONS = \[[\s\S]*?\];/);
        assert.ok(regionOptionsSrc, "BATCH_REGION_OPTIONS must be defined");
        const readRegions = extractFn("readBatchRegionPickerValue");
        const setRegions = extractFn("setBatchRegionPickerValue");
        const toggleRegion = extractFn("toggleBatchRegionPickerValue");
        assert.ok(readRegions && setRegions && toggleRegion, "region picker helpers must exist");

        const hidden = element("");
        const rendered = [];
        const sandbox = {
            document: { getElementById: (id) => id === "batchConfigEditorRegions" ? hidden : null },
            renderBatchRegionPicker: (id) => rendered.push(id)
        };
        vm.createContext(sandbox);
        vm.runInContext(regionOptionsSrc[0], sandbox);
        vm.runInContext(readRegions, sandbox);
        vm.runInContext(setRegions, sandbox);
        vm.runInContext(toggleRegion, sandbox);

        sandbox.toggleBatchRegionPickerValue("batchConfigEditorRegions", "China");
        sandbox.toggleBatchRegionPickerValue("batchConfigEditorRegions", "Europe");
        const expectedOrder = Array.from(sandbox.BATCH_REGION_OPTIONS
            .filter((o) => o.value === "China" || o.value === "Europe")
            .map((o) => o.value));
        assert.deepStrictEqual(
            Array.from(sandbox.readBatchRegionPickerValue("batchConfigEditorRegions")),
            expectedOrder,
            "toggled values must come back in BATCH_REGION_OPTIONS order"
        );
        sandbox.toggleBatchRegionPickerValue("batchConfigEditorRegions", "China");
        assert.deepStrictEqual(
            Array.from(sandbox.readBatchRegionPickerValue("batchConfigEditorRegions")),
            ["Europe"]
        );
        assert.strictEqual(rendered.length, 3);
    });

    it("BATCH_REGION_OPTIONS values are the 9 English region constants verbatim (G-1)", () => {
        const regionOptionsSrc = appSource.match(/var BATCH_REGION_OPTIONS = \[[\s\S]*?\];/);
        assert.ok(regionOptionsSrc, "BATCH_REGION_OPTIONS must be defined");
        const sandbox = {};
        vm.createContext(sandbox);
        vm.runInContext(regionOptionsSrc[0], sandbox);

        assert.deepStrictEqual(
            Array.from(sandbox.BATCH_REGION_OPTIONS.map((o) => o.value)),
            ["China", "Asia (Japan & Korea)", "Asia (Other)", "Europe", "North America", "South America", "Africa", "Oceania", "Other"]
        );
        assert.deepStrictEqual(
            Array.from(sandbox.BATCH_REGION_OPTIONS.map((o) => o.label)),
            Array.from(sandbox.BATCH_REGION_OPTIONS.map((o) => o.value)),
            "label equals value until child 05 localizes the display text"
        );
    });

    it("saveBatchConfigEditor assembles cron from frequency+time, not the cron input (I-2)", async () => {
        const saveConfig = extractFn("saveBatchConfigEditor");
        assert.ok(saveConfig, "saveBatchConfigEditor must exist");

        const elements = {};
        function el(id) {
            if (!elements[id]) elements[id] = { id, value: "", disabled: false };
            return elements[id];
        }
        const apiBodies = [];
        const sandbox = {
            document: { getElementById: (id) => el(id) },
            batchTaskState: { editorMode: "create", editorId: null, editorAutoEnabled: true },
            readBatchTagPickerValue: () => [],
            readBatchRegionPickerValue: () => [],
            showStatus: () => {},
            api: async (url, options) => { apiBodies.push(JSON.parse(options.body)); return {}; },
            hideBatchConfigEditor: () => {},
            loadBatchConfigList: () => {}
        };
        vm.createContext(sandbox);
        vm.runInContext(saveConfig, sandbox);

        el("batchConfigEditorName").value = "每日任务";
        el("batchConfigEditorFrequency").value = "daily";
        el("batchConfigEditorTime").value = "07:30";
        el("batchConfigEditorCron").value = "0 0 9 ? * MON#2";

        await sandbox.saveBatchConfigEditor();

        assert.strictEqual(apiBodies.length, 1);
        assert.strictEqual(apiBodies[0].cron, "0 30 7 * * ?", "daily mode must assemble cron from frequency+time");
        assert.ok(!("dailyCap" in apiBodies[0]), "payload must no longer carry dailyCap");
    });

    it("saveBatchConfigEditor takes the cron input as the single source in custom mode (I-2)", async () => {
        const saveConfig = extractFn("saveBatchConfigEditor");
        assert.ok(saveConfig, "saveBatchConfigEditor must exist");

        const elements = {};
        function el(id) {
            if (!elements[id]) elements[id] = { id, value: "", disabled: false };
            return elements[id];
        }
        const apiBodies = [];
        const sandbox = {
            document: { getElementById: (id) => el(id) },
            batchTaskState: { editorMode: "create", editorId: null, editorAutoEnabled: true },
            readBatchTagPickerValue: () => [],
            readBatchRegionPickerValue: () => [],
            showStatus: () => {},
            api: async (url, options) => { apiBodies.push(JSON.parse(options.body)); return {}; },
            hideBatchConfigEditor: () => {},
            loadBatchConfigList: () => {}
        };
        vm.createContext(sandbox);
        vm.runInContext(saveConfig, sandbox);

        el("batchConfigEditorName").value = "自定义任务";
        el("batchConfigEditorFrequency").value = "custom";
        el("batchConfigEditorTime").value = "08:00";
        el("batchConfigEditorCron").value = "0 0 9 ? * MON#2";

        await sandbox.saveBatchConfigEditor();

        assert.strictEqual(apiBodies.length, 1);
        assert.strictEqual(apiBodies[0].cron, "0 0 9 ? * MON#2", "custom mode must take the cron input verbatim");
    });

    it("showBatchConfigEditor echoes a daily cron as daily frequency (I-2)", () => {
        const showEditor = extractFn("showBatchConfigEditor");
        assert.ok(showEditor, "showBatchConfigEditor must exist");

        const elements = {};
        function el(id) {
            if (!elements[id]) {
                elements[id] = { id, value: "", textContent: "", hidden: true, classList: { add() {}, remove() {} } };
            }
            return elements[id];
        }
        const sandbox = {
            batchTaskState: { editorAutoEnabled: false },
            document: { getElementById: (id) => el(id) },
            setBatchTagPickerValue: () => {},
            setBatchRegionPickerValue: () => {},
            syncBatchConfigEditorScheduleFields: () => {},
            fillBatchConfigEditorTemplateSelector: () => {},
            fillBatchConfigEditorProviderSelect: () => {},
            updateBatchConfigVolumeHint: () => {}
        };
        vm.createContext(sandbox);
        vm.runInContext(showEditor, sandbox);

        sandbox.showBatchConfigEditor({ id: 1, configName: "任务", cron: "0 15 3 * * ?", tags: [], regions: [] });

        assert.strictEqual(el("batchConfigEditorFrequency").value, "daily");
        assert.strictEqual(el("batchConfigEditorTime").value, "03:15");
    });

    it("showBatchConfigEditor echoes an unmatched cron as custom with the raw expression (I-2)", () => {
        const showEditor = extractFn("showBatchConfigEditor");
        assert.ok(showEditor, "showBatchConfigEditor must exist");

        const elements = {};
        function el(id) {
            if (!elements[id]) {
                elements[id] = { id, value: "", textContent: "", hidden: true, classList: { add() {}, remove() {} } };
            }
            return elements[id];
        }
        const sandbox = {
            batchTaskState: { editorAutoEnabled: false },
            document: { getElementById: (id) => el(id) },
            setBatchTagPickerValue: () => {},
            setBatchRegionPickerValue: () => {},
            syncBatchConfigEditorScheduleFields: () => {},
            fillBatchConfigEditorTemplateSelector: () => {},
            fillBatchConfigEditorProviderSelect: () => {},
            updateBatchConfigVolumeHint: () => {}
        };
        vm.createContext(sandbox);
        vm.runInContext(showEditor, sandbox);

        sandbox.showBatchConfigEditor({ id: 1, configName: "任务", cron: "0 0 9 ? * MON#2", tags: [], regions: [] });

        assert.strictEqual(el("batchConfigEditorFrequency").value, "custom");
        assert.strictEqual(el("batchConfigEditorCron").value, "0 0 9 ? * MON#2");
    });

    it("updateBatchConfigVolumeHint renders rounds × size (S-2)", () => {
        const updateHint = extractFn("updateBatchConfigVolumeHint");
        assert.ok(updateHint, "updateBatchConfigVolumeHint must exist");

        const hint = { innerHTML: "" };
        const sandbox = {
            document: {
                getElementById: (id) => id === "batchConfigEditorVolumeHint" ? hint
                    : id === "batchConfigEditorRoundsPerRun" ? { value: "2" }
                    : id === "batchConfigEditorRoundSize" ? { value: "20" } : null
            }
        };
        vm.createContext(sandbox);
        vm.runInContext(updateHint, sandbox);

        sandbox.updateBatchConfigVolumeHint();

        assert.ok(hint.innerHTML.includes("40"), "2 rounds × 20 per round must render as 40");
        assert.ok(hint.innerHTML.includes("单次调度最多发送"), "hint must carry the fixed copy");
    });

    it("cronToDisplayText falls back to the raw expression for custom cron (X-4)", () => {
        const display = extractFn("cronToDisplayText");
        assert.ok(display, "cronToDisplayText must exist");
        const sandbox = { escapeHtml: (v) => String(v == null ? "" : v) };
        vm.createContext(sandbox);
        vm.runInContext(display, sandbox);

        assert.strictEqual(sandbox.cronToDisplayText("0 0 9 ? * MON#2"), "0 0 9 ? * MON#2");
        assert.strictEqual(sandbox.cronToDisplayText("0 0 9 * * ?"), "每天 09:00");
        assert.strictEqual(sandbox.cronToDisplayText("0 0 9 ? * MON"), "周一 09:00");
        assert.strictEqual(sandbox.cronToDisplayText(""), "—");
    });
});
