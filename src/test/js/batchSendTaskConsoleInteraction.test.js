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

        const snapshot = extractFn("buildManualExecutionSnapshot");
        assert.ok(snapshot, "manual execution snapshot must exist");
        assert.ok(snapshot.includes("mailType: values.mailType"));

        const confirmExecution = extractFn("confirmManualExecution");
        assert.ok(confirmExecution.includes("var snapshot = buildManualExecutionSnapshot();"));
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
        assert.strictEqual(sandbox.formatManualDiffValue("emailDomains", []), "全部服务商");
        assert.strictEqual(sandbox.formatManualDiffValue("discipline", null), "全部学科");
        assert.strictEqual(sandbox.formatManualDiffValue("templateId", null), "系统默认介绍邮件模板");
        assert.strictEqual(sandbox.formatManualDiffValue("tags", []), "(无)");
    });

    it("toggles multiple region picker values in BATCH_REGION_OPTIONS order (I-1)", () => {
        const regionOptionsSrc = appSource.match(/var BATCH_REGION_OPTIONS = \[[\s\S]*?\];/);
        assert.ok(regionOptionsSrc, "BATCH_REGION_OPTIONS must be defined");
        const regionLabelsSrc = appSource.match(/var REGION_LABELS = \{[\s\S]*?\};/);
        assert.ok(regionLabelsSrc, "REGION_LABELS must be defined");
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
        vm.runInContext(regionLabelsSrc[0], sandbox);
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
        const regionLabelsSrc = appSource.match(/var REGION_LABELS = \{[\s\S]*?\};/);
        assert.ok(regionLabelsSrc, "REGION_LABELS must be defined");
        const sandbox = {};
        vm.createContext(sandbox);
        vm.runInContext(regionLabelsSrc[0], sandbox);
        vm.runInContext(regionOptionsSrc[0], sandbox);

        assert.deepStrictEqual(
            Array.from(sandbox.BATCH_REGION_OPTIONS.map((o) => o.value)),
            ["China", "Asia (Japan & Korea)", "Asia (Other)", "Europe", "North America", "South America", "Africa", "Oceania", "Other"]
        );
        assert.deepStrictEqual(
            Array.from(sandbox.BATCH_REGION_OPTIONS.map((o) => o.label)),
            Array.from(sandbox.BATCH_REGION_OPTIONS.map((o) => sandbox.REGION_LABELS[o.value])),
            "labels must come from the single REGION_LABELS authority (child 05 I-2)"
        );
    });

    it("REGION_LABELS keys are the 9 English region constants verbatim (I-1/I-2)", () => {
        const regionLabelsSrc = appSource.match(/var REGION_LABELS = \{[\s\S]*?\};/);
        assert.ok(regionLabelsSrc, "REGION_LABELS must be defined");
        const sandbox = {};
        vm.createContext(sandbox);
        vm.runInContext(regionLabelsSrc[0], sandbox);

        const expectedKeys = ["China", "Asia (Japan & Korea)", "Asia (Other)", "Europe", "North America", "South America", "Africa", "Oceania", "Other"];
        assert.deepStrictEqual(Object.keys(sandbox.REGION_LABELS), expectedKeys);
        assert.strictEqual(sandbox.REGION_LABELS["China"], "中国");
        assert.strictEqual(sandbox.REGION_LABELS["Asia (Japan & Korea)"], "亚洲（日韩）");
        assert.strictEqual(sandbox.REGION_LABELS["Other"], "其他");
    });

    it("regionLabel returns the raw value for unknown regions (I-2)", () => {
        const regionLabelSrc = extractFn("regionLabel");
        assert.ok(regionLabelSrc, "regionLabel must exist");
        const regionLabelsSrc = appSource.match(/var REGION_LABELS = \{[\s\S]*?\};/);
        assert.ok(regionLabelsSrc, "REGION_LABELS must be defined");
        const sandbox = {};
        vm.createContext(sandbox);
        vm.runInContext(regionLabelsSrc[0], sandbox);
        vm.runInContext(regionLabelSrc, sandbox);

        assert.strictEqual(sandbox.regionLabel("Mars"), "Mars");
        assert.strictEqual(sandbox.regionLabel("Europe"), "欧洲");
        assert.strictEqual(sandbox.regionLabel(""), "");
        assert.strictEqual(sandbox.regionLabel(null), "");
    });

    it("saveBatchConfigEditor sends English region constants even though the UI shows Chinese (I-1)", async () => {
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
            readBatchRegionPickerValue: () => ["China", "Europe"],
            readBatchMultiPickerValue: () => [],
            showStatus: () => {},
            api: async (url, options) => { apiBodies.push(JSON.parse(options.body)); return {}; },
            hideBatchConfigEditor: () => {},
            loadBatchConfigList: () => {}
        };
        vm.createContext(sandbox);
        vm.runInContext(saveConfig, sandbox);

        el("batchConfigEditorName").value = "地区任务";
        el("batchConfigEditorFrequency").value = "daily";
        el("batchConfigEditorTime").value = "07:30";
        el("batchConfigEditorCron").value = "";

        await sandbox.saveBatchConfigEditor();

        assert.strictEqual(apiBodies.length, 1);
        assert.deepStrictEqual(apiBodies[0].regions, ["China", "Europe"],
            "payload regions must stay English constants (I-1)");
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
            readBatchMultiPickerValue: () => [],
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
            readBatchMultiPickerValue: () => [],
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
            setBatchMultiPickerValue: () => {},
            updateBatchConfigVolumeHint: () => {}
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("isCronClock"), sandbox);
        vm.runInContext(extractFn("padClock"), sandbox);
        vm.runInContext(showEditor, sandbox);

        sandbox.showBatchConfigEditor({ id: 1, configName: "任务", cron: "0 15 3 * * ?", tags: [], regions: [] });

        assert.strictEqual(el("batchConfigEditorFrequency").value, "daily");
        assert.strictEqual(el("batchConfigEditorTime").value, "03:15");
    });

    it("U1: echoes a range cron (0 0 9-17 * * ?) as custom with the raw expression (I1-1)", () => {
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
            setBatchMultiPickerValue: () => {},
            updateBatchConfigVolumeHint: () => {}
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("isCronClock"), sandbox);
        vm.runInContext(extractFn("padClock"), sandbox);
        vm.runInContext(showEditor, sandbox);

        sandbox.showBatchConfigEditor({ id: 1, configName: "任务", cron: "0 0 9-17 * * ?", tags: [], regions: [] });

        assert.strictEqual(el("batchConfigEditorFrequency").value, "custom");
        assert.strictEqual(el("batchConfigEditorCron").value, "0 0 9-17 * * ?");
    });

    it("U2: echoes a list cron (0 0 9,12,15 * * ?) as custom with the raw expression (I1-1)", () => {
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
            setBatchMultiPickerValue: () => {},
            updateBatchConfigVolumeHint: () => {}
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("isCronClock"), sandbox);
        vm.runInContext(extractFn("padClock"), sandbox);
        vm.runInContext(showEditor, sandbox);

        sandbox.showBatchConfigEditor({ id: 1, configName: "任务", cron: "0 0 9,12,15 * * ?", tags: [], regions: [] });

        assert.strictEqual(el("batchConfigEditorFrequency").value, "custom");
        assert.strictEqual(el("batchConfigEditorCron").value, "0 0 9,12,15 * * ?");
    });

    it("U3: does not drop the day-of-month field (0 0 9 1 * ? stays custom) (I1-1)", () => {
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
            setBatchMultiPickerValue: () => {},
            updateBatchConfigVolumeHint: () => {}
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("isCronClock"), sandbox);
        vm.runInContext(extractFn("padClock"), sandbox);
        vm.runInContext(showEditor, sandbox);

        sandbox.showBatchConfigEditor({ id: 1, configName: "任务", cron: "0 0 9 1 * ?", tags: [], regions: [] });

        assert.strictEqual(el("batchConfigEditorFrequency").value, "custom");
        assert.strictEqual(el("batchConfigEditorCron").value, "0 0 9 1 * ?");
    });

    it("U4: echoes a weekday-range cron (0 0 9 ? * MON-FRI) as custom with the raw expression (I1-1)", () => {
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
            setBatchMultiPickerValue: () => {},
            updateBatchConfigVolumeHint: () => {}
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("isCronClock"), sandbox);
        vm.runInContext(extractFn("padClock"), sandbox);
        vm.runInContext(showEditor, sandbox);

        sandbox.showBatchConfigEditor({ id: 1, configName: "任务", cron: "0 0 9 ? * MON-FRI", tags: [], regions: [] });

        assert.strictEqual(el("batchConfigEditorFrequency").value, "custom");
        assert.strictEqual(el("batchConfigEditorCron").value, "0 0 9 ? * MON-FRI");
    });

    it("U5: echoes hourly cron (0 0 * * * ?) as hourly with empty time and cron box (N1-1)", () => {
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
            setBatchMultiPickerValue: () => {},
            updateBatchConfigVolumeHint: () => {}
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("isCronClock"), sandbox);
        vm.runInContext(extractFn("padClock"), sandbox);
        vm.runInContext(showEditor, sandbox);

        sandbox.showBatchConfigEditor({ id: 1, configName: "任务", cron: "0 0 * * * ?", tags: [], regions: [] });

        assert.strictEqual(el("batchConfigEditorFrequency").value, "hourly");
        assert.strictEqual(el("batchConfigEditorTime").value, "");
        assert.strictEqual(el("batchConfigEditorCron").value, "");
    });

    it("U6: echoes daily cron (0 15 3 * * ?) as daily 03:15 with empty cron box (N1-1)", () => {
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
            setBatchMultiPickerValue: () => {},
            updateBatchConfigVolumeHint: () => {}
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("isCronClock"), sandbox);
        vm.runInContext(extractFn("padClock"), sandbox);
        vm.runInContext(showEditor, sandbox);

        sandbox.showBatchConfigEditor({ id: 1, configName: "任务", cron: "0 15 3 * * ?", tags: [], regions: [] });

        assert.strictEqual(el("batchConfigEditorFrequency").value, "daily");
        assert.strictEqual(el("batchConfigEditorTime").value, "03:15");
        assert.strictEqual(el("batchConfigEditorCron").value, "");
    });

    it("U7: echoes weekly cron (0 30 9 ? * MON) as weekly 09:30 with empty cron box (N1-1)", () => {
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
            setBatchMultiPickerValue: () => {},
            updateBatchConfigVolumeHint: () => {}
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("isCronClock"), sandbox);
        vm.runInContext(extractFn("padClock"), sandbox);
        vm.runInContext(showEditor, sandbox);

        sandbox.showBatchConfigEditor({ id: 1, configName: "任务", cron: "0 30 9 ? * MON", tags: [], regions: [] });

        assert.strictEqual(el("batchConfigEditorFrequency").value, "weekly");
        assert.strictEqual(el("batchConfigEditorTime").value, "09:30");
        assert.strictEqual(el("batchConfigEditorCron").value, "");
    });

    it("U8: rejects an out-of-range minute (0 70 9 * * ?) as custom (I1-1)", () => {
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
            setBatchMultiPickerValue: () => {},
            updateBatchConfigVolumeHint: () => {}
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("isCronClock"), sandbox);
        vm.runInContext(extractFn("padClock"), sandbox);
        vm.runInContext(showEditor, sandbox);

        sandbox.showBatchConfigEditor({ id: 1, configName: "任务", cron: "0 70 9 * * ?", tags: [], regions: [] });

        assert.strictEqual(el("batchConfigEditorFrequency").value, "custom");
        assert.strictEqual(el("batchConfigEditorCron").value, "0 70 9 * * ?");
    });

    it("U9: clears the cron box when the reused DOM switches from custom to daily (I1-2)", () => {
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
            setBatchMultiPickerValue: () => {},
            updateBatchConfigVolumeHint: () => {}
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("isCronClock"), sandbox);
        vm.runInContext(extractFn("padClock"), sandbox);
        vm.runInContext(showEditor, sandbox);

        sandbox.showBatchConfigEditor({ id: 1, configName: "任务A", cron: "0 0 9-17 * * ?", tags: [], regions: [] });
        assert.strictEqual(el("batchConfigEditorCron").value, "0 0 9-17 * * ?");

        sandbox.showBatchConfigEditor({ id: 2, configName: "任务B", cron: "0 15 3 * * ?", tags: [], regions: [] });

        assert.strictEqual(el("batchConfigEditorFrequency").value, "daily");
        assert.strictEqual(el("batchConfigEditorTime").value, "03:15");
        assert.strictEqual(el("batchConfigEditorCron").value, "", "reused DOM must not leak task A's cron");
    });

    it("U10: new task (null config) keeps daily 09:00 defaults with an empty cron box (I1-3)", () => {
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
            setBatchMultiPickerValue: () => {},
            updateBatchConfigVolumeHint: () => {}
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("isCronClock"), sandbox);
        vm.runInContext(extractFn("padClock"), sandbox);
        vm.runInContext(showEditor, sandbox);

        sandbox.showBatchConfigEditor(null);

        assert.strictEqual(el("batchConfigEditorFrequency").value, "daily");
        assert.strictEqual(el("batchConfigEditorTime").value, "09:00");
        assert.strictEqual(el("batchConfigEditorCron").value, "");
    });

    it("U11: custom mode saves the raw range cron verbatim through saveBatchConfigEditor (N1-2)", async () => {
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
            readBatchMultiPickerValue: () => [],
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
        el("batchConfigEditorCron").value = "0 0 9-17 * * ?";

        await sandbox.saveBatchConfigEditor();

        assert.strictEqual(apiBodies.length, 1);
        assert.strictEqual(apiBodies[0].cron, "0 0 9-17 * * ?", "custom mode must save the range cron verbatim");
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
            setBatchMultiPickerValue: () => {},
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

    it("deepCloneConfig preserves roundsPerRun from the selected source config (V-1)", () => {
        const clone = extractFn("deepCloneConfig");
        assert.ok(clone, "deepCloneConfig must exist");

        const sandbox = {};
        vm.createContext(sandbox);
        vm.runInContext(clone, sandbox);

        const withRounds = sandbox.deepCloneConfig({ id: 7, configName: "每日介绍", roundsPerRun: 2, roundSize: 20 });
        assert.strictEqual(withRounds.roundsPerRun, 2, "roundsPerRun must survive the clone");
        assert.strictEqual(withRounds.roundSize, 20, "roundSize must survive the clone");

        const withoutRounds = sandbox.deepCloneConfig({ configName: "旧任务", roundSize: 20 });
        assert.strictEqual(withoutRounds.roundsPerRun, 1, "missing roundsPerRun must default to 1");
    });

    it("independent manual draft defaults roundsPerRun to 1 (V-1)", () => {
        const defaults = extractFn("fillManualFormDefaults");
        assert.ok(defaults, "fillManualFormDefaults must exist");

        const sandbox = {
            batchTaskState: {},
            fillManualFormFromDraft: () => {}
        };
        vm.createContext(sandbox);
        vm.runInContext(defaults, sandbox);

        sandbox.fillManualFormDefaults();

        assert.strictEqual(sandbox.batchTaskState.manualDraft.roundsPerRun, 1,
            "independent manual draft must carry roundsPerRun 1");
    });

    it("configured-source manual confirmation renders the round count, not undefined (V-1)", () => {
        const applySource = extractFn("applyBatchManualSource");
        const confirm = extractFn("showBatchManualConfirm");
        assert.ok(applySource, "applyBatchManualSource must exist");
        assert.ok(confirm, "showBatchManualConfirm must exist");

        const elements = {
            batchManualSourceQuery: element(),
            batchManualSourceId: element(),
            batchManualSourceUpdatedAt: element(),
            batchManualConfirmTitle: element(),
            batchManualConfirmBody: element(),
            batchManualConfirmDialog: element()
        };
        const sandbox = {
            batchTaskState: { manualSource: null, manualDraft: null },
            document: { getElementById: (id) => elements[id] || null },
            deepCloneConfig: null, // filled below with the real implementation
            updateManualSourceInfo: () => {},
            fillManualFormFromDraft: () => {},
            computeManualDiffs: () => [],
            escapeHtml: (v) => String(v == null ? "" : v)
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("deepCloneConfig"), sandbox);
        vm.runInContext(applySource, sandbox);
        vm.runInContext(confirm, sandbox);

        sandbox.applyBatchManualSource({ id: 7, configName: "每日介绍", roundsPerRun: 2, roundSize: 20 });
        assert.strictEqual(sandbox.batchTaskState.manualSource.roundsPerRun, 2,
            "manualSource must retain the configured round count");
        assert.strictEqual(sandbox.batchTaskState.manualDraft.roundsPerRun, 2,
            "manualDraft must retain the configured round count");

        sandbox.showBatchManualConfirm();

        assert.ok(elements.batchManualConfirmBody.innerHTML.includes("轮次: 2 轮"),
            "confirmation summary must render 轮次: 2 轮, not undefined");
        assert.ok(!elements.batchManualConfirmBody.innerHTML.includes("undefined"),
            "confirmation summary must not contain undefined");
    });

    it("schedules recipient previews when either region picker changes (I-1)", () => {
        const notifyRegionChange = extractFn("notifyBatchRegionPickerChanged");
        assert.ok(notifyRegionChange, "notifyBatchRegionPickerChanged must exist");

        const scheduled = [];
        const sandbox = {
            batchTaskState: { manualDraft: {} },
            readBatchRegionPickerValue: () => ["China"],
            computeAndRenderDiffs: () => {},
            scheduleRecipientPreview: (kind) => scheduled.push(kind)
        };
        vm.createContext(sandbox);
        vm.runInContext(notifyRegionChange, sandbox);

        sandbox.notifyBatchRegionPickerChanged("batchManualRegions");
        sandbox.notifyBatchRegionPickerChanged("batchConfigEditorRegions");

        assert.deepStrictEqual(scheduled, ["manual", "editor"]);
        assert.deepStrictEqual(
            Array.from(sandbox.batchTaskState.manualDraft.regions),
            ["China"],
            "manual region changes must still update the draft"
        );
    });

    it("uses one complete manual snapshot for preview and execution (I-2)", () => {
        const buildSnapshot = extractFn("buildManualExecutionSnapshot");
        const confirmExecution = extractFn("confirmManualExecution");
        assert.ok(buildSnapshot, "buildManualExecutionSnapshot must exist");
        assert.ok(confirmExecution.includes("var snapshot = buildManualExecutionSnapshot();"),
            "manual execution must reuse the preview snapshot builder");

        const values = {
            mailType: "INTRODUCTION",
            roundSize: 30,
            roundsPerRun: 2,
            perMailIntervalMs: 3000,
            perRoundIntervalMs: 120000,
            selfCheckTtlMinutes: 30,
            funnelLevel: "CANDIDATE",
            tags: ["AI"],
            regions: ["China"],
            emailDomains: ["university.edu"],
            discipline: "STEM",
            operatorStatuses: ["NOT_CONTACTED"],
            templateId: 7
        };
        const sandbox = { readManualFormValues: () => values, Number };
        vm.createContext(sandbox);
        vm.runInContext(buildSnapshot, sandbox);

        assert.deepStrictEqual(
            JSON.parse(JSON.stringify(sandbox.buildManualExecutionSnapshot())),
            values
        );
    });

    it("renders the current recipient preview error but ignores an obsolete failure (I-3)", async () => {
        const refresh = extractFn("refreshRecipientPreview");
        assert.ok(refresh, "refreshRecipientPreview must exist");

        const hint = element();
        let rejectRequest;
        const sandbox = {
            recipientPreviewRequestSeq: { editor: 0, manual: 0 },
            recipientPreviewHintId: () => "batchManualRecipientHint",
            buildManualRecipientSnapshot: () => ({}),
            buildManualExecutionSnapshot: () => ({}),
            document: { getElementById: () => hint },
            api: () => new Promise((resolve, reject) => { rejectRequest = reject; }),
            console: { warn: () => {} }
        };
        vm.createContext(sandbox);
        vm.runInContext(refresh, sandbox);

        sandbox.refreshRecipientPreview("manual");
        rejectRequest(new Error("roundSize must be a number"));
        await new Promise((resolve) => setImmediate(resolve));

        assert.strictEqual(hint.textContent, "预估失败：roundSize must be a number");
        assert.ok(hint.innerHTML.includes("计算中"), "error must use textContent, not unsafe HTML");

        sandbox.refreshRecipientPreview("manual");
        sandbox.recipientPreviewRequestSeq.manual += 1;
        rejectRequest(new Error("obsolete"));
        await new Promise((resolve) => setImmediate(resolve));

        assert.strictEqual(hint.textContent, "预估失败：roundSize must be a number");
    });

    it("V1: setBatchMultiPickerValue + readBatchMultiPickerValue roundtrip keeps the comma contract (I2b-3)", () => {
        const readValue = extractFn("readBatchMultiPickerValue");
        const setValue = extractFn("setBatchMultiPickerValue");
        assert.ok(readValue && setValue, "multi picker helpers must exist");

        const hidden = element("");
        const rendered = [];
        const sandbox = {
            document: { getElementById: (id) => id === "batchConfigEditorEmailDomains" ? hidden : null },
            renderBatchMultiPicker: (id) => rendered.push(id)
        };
        vm.createContext(sandbox);
        vm.runInContext(readValue, sandbox);
        vm.runInContext(setValue, sandbox);

        sandbox.setBatchMultiPickerValue("batchConfigEditorEmailDomains", ["a.com", "b.com"]);

        assert.strictEqual(hidden.value, "a.com,b.com", "values must be comma-joined in the hidden input (I2b-3)");
        assert.deepStrictEqual(
            Array.from(sandbox.readBatchMultiPickerValue("batchConfigEditorEmailDomains")),
            ["a.com", "b.com"]
        );
        assert.strictEqual(rendered.length, 1, "set must trigger a render");
    });

    it("V2: renderBatchMultiPicker draws one chip per value and marks selected options (S2b-1)", () => {
        const readValue = extractFn("readBatchMultiPickerValue");
        const setValue = extractFn("setBatchMultiPickerValue");
        const renderValue = extractFn("renderBatchMultiPicker");
        assert.ok(renderValue, "renderBatchMultiPicker must exist");

        const hidden = element("");
        const search = element("");
        const chips = element("");
        const dropdown = element("");
        const sandbox = {
            BATCH_MULTI_PICKER_REGISTRY: {
                batchConfigEditorEmailDomains: {
                    options: () => [
                        { value: "a.com", label: "a.com" },
                        { value: "b.com", label: "b.com" },
                        { value: "c.com", label: "c.com" }
                    ],
                    emptyText: "没有匹配服务商",
                    previewKind: "editor"
                }
            },
            document: {
                getElementById: (id) => ({
                    "batchConfigEditorEmailDomains": hidden,
                    "batchConfigEditorEmailDomainsSearch": search,
                    "batchConfigEditorEmailDomainsChips": chips,
                    "batchConfigEditorEmailDomainsDropdown": dropdown
                }[id] || null)
            },
            escapeHtml: (v) => String(v == null ? "" : v)
        };
        vm.createContext(sandbox);
        vm.runInContext(readValue, sandbox);
        vm.runInContext(setValue, sandbox);
        vm.runInContext(renderValue, sandbox);

        sandbox.setBatchMultiPickerValue("batchConfigEditorEmailDomains", ["a.com", "b.com"]);

        const chipCount = (chips.innerHTML.match(/class="batch-tag-picker-chip"/g) || []).length;
        assert.strictEqual(chipCount, 2, "chips HTML must contain 2 .batch-tag-picker-chip (S2b-1)");
        assert.ok(dropdown.innerHTML.includes("is-selected"), "selected options must carry is-selected");
        assert.ok(dropdown.innerHTML.includes("✓"), "selected options must show the check mark");
        assert.ok(dropdown.innerHTML.includes("c.com"), "unselected option must remain listed");
    });

    it("V3: renderBatchMultiPicker shows the registry emptyText when no option matches (I2b-1)", () => {
        const readValue = extractFn("readBatchMultiPickerValue");
        const renderValue = extractFn("renderBatchMultiPicker");
        assert.ok(renderValue, "renderBatchMultiPicker must exist");

        const hidden = element("a.com");
        const search = element("");
        const chips = element("");
        const dropdown = element("");
        const sandbox = {
            BATCH_MULTI_PICKER_REGISTRY: {
                batchConfigEditorEmailDomains: {
                    options: () => [],
                    emptyText: "没有匹配服务商",
                    previewKind: "editor"
                }
            },
            document: {
                getElementById: (id) => ({
                    "batchConfigEditorEmailDomains": hidden,
                    "batchConfigEditorEmailDomainsSearch": search,
                    "batchConfigEditorEmailDomainsChips": chips,
                    "batchConfigEditorEmailDomainsDropdown": dropdown
                }[id] || null)
            },
            escapeHtml: (v) => String(v == null ? "" : v)
        };
        vm.createContext(sandbox);
        vm.runInContext(readValue, sandbox);
        vm.runInContext(renderValue, sandbox);

        sandbox.renderBatchMultiPicker("batchConfigEditorEmailDomains");

        assert.strictEqual(dropdown.innerHTML, '<div class="batch-tag-picker-empty">没有匹配服务商</div>',
            "empty state must use meta.emptyText");
    });

    it("V4: showBatchConfigEditor echoes emailDomains into the picker hidden input (IP-1)", () => {
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
            renderBatchMultiPicker: () => {},
            syncBatchConfigEditorScheduleFields: () => {},
            fillBatchConfigEditorTemplateSelector: () => {},
            updateBatchConfigVolumeHint: () => {}
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("readBatchMultiPickerValue"), sandbox);
        vm.runInContext(extractFn("setBatchMultiPickerValue"), sandbox);
        vm.runInContext(extractFn("isCronClock"), sandbox);
        vm.runInContext(extractFn("padClock"), sandbox);
        vm.runInContext(showEditor, sandbox);

        sandbox.showBatchConfigEditor({ id: 1, configName: "任务", cron: "0 15 3 * * ?", tags: [], regions: [], emailDomains: ["a.com"] });
        assert.strictEqual(el("batchConfigEditorEmailDomains").value, "a.com",
            "config emailDomains must be echoed into the hidden input (IP-1)");

        sandbox.showBatchConfigEditor(null);
        assert.strictEqual(el("batchConfigEditorEmailDomains").value, "",
            "new task must start with an empty emailDomains picker");
    });

    it("V5: saveBatchConfigEditor payload carries emailDomains from the picker (IP-1)", async () => {
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
            readBatchMultiPickerValue: () => ["a.com", "b.com"],
            showStatus: () => {},
            api: async (url, options) => { apiBodies.push(JSON.parse(options.body)); return {}; },
            hideBatchConfigEditor: () => {},
            loadBatchConfigList: () => {}
        };
        vm.createContext(sandbox);
        vm.runInContext(saveConfig, sandbox);

        el("batchConfigEditorName").value = "多选服务商任务";
        el("batchConfigEditorFrequency").value = "daily";
        el("batchConfigEditorTime").value = "07:30";
        el("batchConfigEditorCron").value = "";

        await sandbox.saveBatchConfigEditor();

        assert.strictEqual(apiBodies.length, 1);
        assert.deepStrictEqual(apiBodies[0].emailDomains, ["a.com", "b.com"],
            "payload emailDomains must come from the picker (IP-1)");
        assert.ok(!("emailDomain" in apiBodies[0]), "payload must not carry the old emailDomain key");
    });

    it("V6: normalizeManualSnapshot sorts emailDomains so order never reads as changed (I2b-5)", () => {
        const normalize = extractFn("normalizeManualSnapshot");
        assert.ok(normalize, "normalizeManualSnapshot must exist");

        const sandbox = { Number };
        vm.createContext(sandbox);
        vm.runInContext(normalize, sandbox);

        const a = sandbox.normalizeManualSnapshot({ emailDomains: ["b.com", "a.com"], tags: [], regions: [] });
        const b = sandbox.normalizeManualSnapshot({ emailDomains: ["a.com", "b.com"], tags: [], regions: [] });

        assert.deepStrictEqual(Array.from(a.emailDomains), ["a.com", "b.com"], "emailDomains must be sorted");
        assert.deepStrictEqual(Array.from(a.emailDomains), Array.from(b.emailDomains),
            "same set in different order must normalize identically (I2b-5)");
    });

    it("V7: formatManualDiffValue renders the emailDomains list or 全部服务商 (I2b-4 #2)", () => {
        const formatDiffValue = extractFn("formatManualDiffValue");
        assert.ok(formatDiffValue, "formatManualDiffValue must exist");

        const sandbox = {
            batchTaskState: { preloadedTemplates: [] },
            supportedBatchComposeTemplates: () => []
        };
        vm.createContext(sandbox);
        vm.runInContext(formatDiffValue, sandbox);

        assert.strictEqual(sandbox.formatManualDiffValue("emailDomains", []), "全部服务商");
        assert.strictEqual(sandbox.formatManualDiffValue("emailDomains", ["a.com", "b.com"]), "a.com、b.com");
    });

    it("V8: computeManualDiffs flags emailDomains only when the domain set differs (I2b-4 #3)", () => {
        const normalize = extractFn("normalizeManualSnapshot");
        const formatDiffValue = extractFn("formatManualDiffValue");
        const computeDiffs = extractFn("computeManualDiffs");
        assert.ok(normalize && formatDiffValue && computeDiffs, "diff pipeline helpers must exist");

        function makeConfig(emailDomains) {
            return {
                id: 1, templateId: null, mailType: "INTRODUCTION", funnelLevel: "",
                tags: [], regions: [], emailDomains, discipline: "", operatorStatus: "",
                roundSize: 50, roundsPerRun: 1, perMailIntervalMs: 1000, perRoundIntervalMs: 60000,
                selfCheckTtlMinutes: 30, configName: "任务", updatedAt: null
            };
        }
        function runDiffs(sourceDomains, draftDomains) {
            const sandbox = {
                batchTaskState: { manualSource: makeConfig(sourceDomains) },
                readManualFormValues: () => makeConfig(draftDomains),
                supportedBatchComposeTemplates: () => [],
                operatorStatusOptions: []
            };
            vm.createContext(sandbox);
            vm.runInContext(normalize, sandbox);
            vm.runInContext(formatDiffValue, sandbox);
            vm.runInContext(computeDiffs, sandbox);
            return sandbox.computeManualDiffs();
        }

        const diffsWhenExtended = runDiffs(["a.com"], ["a.com", "b.com"]);
        assert.ok(diffsWhenExtended.some((d) => d.key === "emailDomains"),
            "draft with an extra domain must be flagged as diff (I2b-4 #3)");

        const diffsWhenSame = runDiffs(["a.com"], ["a.com"]);
        assert.ok(!diffsWhenSame.some((d) => d.key === "emailDomains"),
            "identical domains must not be flagged as diff");
    });

    it("V9: renderBatchConfigRow joins emailDomains into the scope line (S2b-3)", () => {
        const renderRow = extractFn("renderBatchConfigRow");
        assert.ok(renderRow, "renderBatchConfigRow must exist");

        function makeConfig(emailDomains) {
            return {
                id: 1, configName: "多选服务商任务", mailType: "INTRODUCTION", autoEnabled: false,
                funnelLevel: null, tags: [], regions: [], emailDomains, discipline: null,
                templateId: null, cron: null, nextFireTime: null, lastExecutedAt: null
            };
        }
        const sandbox = {
            escapeHtml: (v) => String(v == null ? "" : v),
            regionLabel: (v) => v || "",
            cronToDisplayText: () => "",
            renderBatchConfigStatusToggle: () => ""
        };
        vm.createContext(sandbox);
        vm.runInContext(renderRow, sandbox);

        const html = sandbox.renderBatchConfigRow(makeConfig(["a.com", "b.com"]));
        assert.ok(html.includes("服务商: a.com, b.com"), "scope line must join domains with ', ' (S2b-3)");
        assert.ok(/<span class="batch-task-scope-line">服务商: a.com, b.com<\/span>/.test(html),
            "provider scope line must be wrapped in .batch-task-scope-line");

        const emptyHtml = sandbox.renderBatchConfigRow(makeConfig([]));
        assert.ok(!emptyHtml.includes("服务商:"), "empty emailDomains must not render a provider line");
        assert.ok(emptyHtml.includes("无限制"), "empty filters must show 无限制");
    });

    it("V10: existing tag and region picker readers keep their exact behavior (I2b-2)", () => {
        const readTags = extractFn("readBatchTagPickerValue");
        const readRegions = extractFn("readBatchRegionPickerValue");
        assert.ok(readTags && readRegions, "existing picker readers must exist");

        const tagHidden = element("AI,STEM,AI");
        const regionHidden = element("China,Europe");
        const sandbox = {
            document: {
                getElementById: (id) => id === "batchManualTags" ? tagHidden : (id === "batchConfigEditorRegions" ? regionHidden : null)
            }
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("normalizeBatchTags"), sandbox);
        vm.runInContext(readTags, sandbox);
        vm.runInContext(readRegions, sandbox);

        assert.deepStrictEqual(Array.from(sandbox.readBatchTagPickerValue("batchManualTags")), ["AI", "STEM"],
            "tag reader must trim and dedupe, preserving first-seen order");
        assert.deepStrictEqual(Array.from(sandbox.readBatchRegionPickerValue("batchConfigEditorRegions")), ["China", "Europe"],
            "region reader must split/trim/filter, preserving order");
    });

    it("W1: batchOperatorStatusOptions derives English values and Chinese labels from operatorStatusOptions (I3b-2/I3b-3)", () => {
        const statusOptionsSrc = appSource.match(/const operatorStatusOptions = \[[\s\S]*?\];/);
        assert.ok(statusOptionsSrc, "operatorStatusOptions constant must exist in app.js (I3b-3)");
        const fnSrc = extractFn("batchOperatorStatusOptions");
        assert.ok(fnSrc, "batchOperatorStatusOptions must exist (I3b-3)");
        assert.ok(fnSrc.includes("operatorStatusOptions"),
            "batchOperatorStatusOptions must derive from the existing constant (I3b-3)");

        const sandbox = {};
        vm.createContext(sandbox);
        vm.runInContext(statusOptionsSrc[0], sandbox);
        vm.runInContext(fnSrc, sandbox);

        const options = sandbox.batchOperatorStatusOptions();
        assert.ok(options.length >= 3, "status options must not be empty");
        options.forEach((o) => {
            assert.match(o.value, /^[A-Z_]+$/, "value must be an English enum name (I3b-2)");
            assert.match(o.label, /[\u4e00-\u9fff]/, "label must be Chinese (I3b-2)");
            assert.notStrictEqual(o.value, o.label, "value and label must not be identical (I3b-2)");
        });
    });

    it("W2: operator status values stay English enum names in the hidden input (I3b-2)", () => {
        const setValue = extractFn("setBatchMultiPickerValue");
        assert.ok(setValue, "setBatchMultiPickerValue must exist");

        const hidden = element("");
        const rendered = [];
        const sandbox = {
            document: { getElementById: (id) => id === "batchConfigEditorOperatorStatuses" ? hidden : null },
            renderBatchMultiPicker: (id) => rendered.push(id)
        };
        vm.createContext(sandbox);
        vm.runInContext(setValue, sandbox);

        sandbox.setBatchMultiPickerValue("batchConfigEditorOperatorStatuses", ["NOT_CONTACTED", "CONTACTED"]);

        assert.strictEqual(hidden.value, "NOT_CONTACTED,CONTACTED",
            "hidden input must carry English enum names, comma-joined (I3b-2)");
        assert.ok(!hidden.value.includes("未联系"), "Chinese labels must never enter the hidden input (I3b-2)");
        assert.strictEqual(rendered.length, 1, "set must trigger a render");
    });

    it("W3: picker chips show Chinese labels while data-remove-tag keeps English values (I3b-2)", () => {
        const readValue = extractFn("readBatchMultiPickerValue");
        const setValue = extractFn("setBatchMultiPickerValue");
        const renderValue = extractFn("renderBatchMultiPicker");
        assert.ok(renderValue, "renderBatchMultiPicker must exist");

        const hidden = element("");
        const search = element("");
        const chips = element("");
        const dropdown = element("");
        const sandbox = {
            BATCH_MULTI_PICKER_REGISTRY: {
                batchConfigEditorOperatorStatuses: {
                    options: () => [
                        { value: "NOT_CONTACTED", label: "未联系" },
                        { value: "CONTACTED", label: "已联系" },
                        { value: "REPLIED", label: "已回复" }
                    ],
                    emptyText: "没有匹配状态",
                    previewKind: "editor"
                }
            },
            document: {
                getElementById: (id) => ({
                    "batchConfigEditorOperatorStatuses": hidden,
                    "batchConfigEditorOperatorStatusesSearch": search,
                    "batchConfigEditorOperatorStatusesChips": chips,
                    "batchConfigEditorOperatorStatusesDropdown": dropdown
                }[id] || null)
            },
            escapeHtml: (v) => String(v == null ? "" : v)
        };
        vm.createContext(sandbox);
        vm.runInContext(readValue, sandbox);
        vm.runInContext(setValue, sandbox);
        vm.runInContext(renderValue, sandbox);

        sandbox.setBatchMultiPickerValue("batchConfigEditorOperatorStatuses", ["NOT_CONTACTED", "CONTACTED"]);

        assert.ok(chips.innerHTML.includes("未联系"), "chip must show the Chinese label (I3b-2)");
        assert.ok(chips.innerHTML.includes("已联系"), "chip must show the Chinese label (I3b-2)");
        assert.ok(chips.innerHTML.includes('data-remove-tag="NOT_CONTACTED"'),
            "chip remove button must carry the English enum value (I3b-2)");
        assert.ok(chips.innerHTML.includes('data-remove-tag="CONTACTED"'),
            "chip remove button must carry the English enum value (I3b-2)");
    });

    it("W4: showBatchConfigEditor echoes operatorStatuses into the picker hidden input (IP-1)", () => {
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
            renderBatchMultiPicker: () => {},
            syncBatchConfigEditorScheduleFields: () => {},
            fillBatchConfigEditorTemplateSelector: () => {},
            updateBatchConfigVolumeHint: () => {}
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("readBatchMultiPickerValue"), sandbox);
        vm.runInContext(extractFn("setBatchMultiPickerValue"), sandbox);
        vm.runInContext(extractFn("isCronClock"), sandbox);
        vm.runInContext(extractFn("padClock"), sandbox);
        vm.runInContext(showEditor, sandbox);

        sandbox.showBatchConfigEditor({ id: 1, configName: "任务", cron: "0 15 3 * * ?", tags: [], regions: [], operatorStatuses: ["CONTACTED"] });
        assert.strictEqual(el("batchConfigEditorOperatorStatuses").value, "CONTACTED",
            "config operatorStatuses must be echoed into the hidden input (IP-1)");

        sandbox.showBatchConfigEditor(null);
        assert.strictEqual(el("batchConfigEditorOperatorStatuses").value, "",
            "new task must start with an empty status picker");
    });

    it("W5: saveBatchConfigEditor payload carries English operatorStatuses from the picker (IP-1)", async () => {
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
            readBatchMultiPickerValue: () => ["CONTACTED"],
            showStatus: () => {},
            api: async (url, options) => { apiBodies.push(JSON.parse(options.body)); return {}; },
            hideBatchConfigEditor: () => {},
            loadBatchConfigList: () => {}
        };
        vm.createContext(sandbox);
        vm.runInContext(saveConfig, sandbox);

        el("batchConfigEditorName").value = "状态筛选任务";
        el("batchConfigEditorFrequency").value = "daily";
        el("batchConfigEditorTime").value = "07:30";
        el("batchConfigEditorCron").value = "";

        await sandbox.saveBatchConfigEditor();

        assert.strictEqual(apiBodies.length, 1);
        assert.deepStrictEqual(apiBodies[0].operatorStatuses, ["CONTACTED"],
            "payload operatorStatuses must come from the picker as English values (IP-1)");
        assert.ok(!("operatorStatus" in apiBodies[0]), "payload must not carry the old operatorStatus key");
    });

    it("W6: formatManualDiffValue renders status list or 全部状态 with Chinese labels (I3b-4 #2)", () => {
        const formatDiffValue = extractFn("formatManualDiffValue");
        assert.ok(formatDiffValue, "formatManualDiffValue must exist");

        const sandbox = {
            batchTaskState: { preloadedTemplates: [] },
            supportedBatchComposeTemplates: () => [],
            operatorStatusOptions: [["NOT_CONTACTED", "未联系"], ["CONTACTED", "已联系"]]
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("operatorStatusLabel"), sandbox);
        vm.runInContext(formatDiffValue, sandbox);

        assert.strictEqual(sandbox.formatManualDiffValue("operatorStatuses", []), "全部状态");
        assert.strictEqual(sandbox.formatManualDiffValue("operatorStatuses", ["NOT_CONTACTED"]), "未联系");
        assert.strictEqual(sandbox.formatManualDiffValue("operatorStatuses", ["NOT_CONTACTED", "CONTACTED"]), "未联系、已联系");
    });

    it("W7: normalizeManualSnapshot sorts operatorStatuses so order never reads as changed (I3b-5)", () => {
        const normalize = extractFn("normalizeManualSnapshot");
        assert.ok(normalize, "normalizeManualSnapshot must exist");

        const sandbox = { Number };
        vm.createContext(sandbox);
        vm.runInContext(normalize, sandbox);

        const a = sandbox.normalizeManualSnapshot({ operatorStatuses: ["CONTACTED", "NOT_CONTACTED"], tags: [], regions: [], emailDomains: [] });
        const b = sandbox.normalizeManualSnapshot({ operatorStatuses: ["NOT_CONTACTED", "CONTACTED"], tags: [], regions: [], emailDomains: [] });

        assert.deepStrictEqual(Array.from(a.operatorStatuses), ["CONTACTED", "NOT_CONTACTED"],
            "operatorStatuses must be sorted (I3b-5)");
        assert.deepStrictEqual(Array.from(a.operatorStatuses), Array.from(b.operatorStatuses),
            "same status set in different order must normalize identically (I3b-5)");
    });

    it("W8: computeManualDiffs flags operatorStatuses once the field participates in diffs (I3b-4 #3 gap fix)", () => {
        const normalize = extractFn("normalizeManualSnapshot");
        const formatDiffValue = extractFn("formatManualDiffValue");
        const computeDiffs = extractFn("computeManualDiffs");
        assert.ok(normalize && formatDiffValue && computeDiffs, "diff pipeline helpers must exist");

        function makeConfig(operatorStatuses) {
            return {
                id: 1, templateId: null, mailType: "INTRODUCTION", funnelLevel: "",
                tags: [], regions: [], emailDomains: [], discipline: "", operatorStatuses,
                roundSize: 50, roundsPerRun: 1, perMailIntervalMs: 1000, perRoundIntervalMs: 60000,
                selfCheckTtlMinutes: 30, configName: "任务", updatedAt: null
            };
        }
        function runDiffs(sourceStatuses, draftStatuses) {
            const sandbox = {
                batchTaskState: { manualSource: makeConfig(sourceStatuses) },
                readManualFormValues: () => makeConfig(draftStatuses),
                supportedBatchComposeTemplates: () => [],
                operatorStatusOptions: [["NOT_CONTACTED", "未联系"], ["CONTACTED", "已联系"]]
            };
            vm.createContext(sandbox);
            vm.runInContext(extractFn("operatorStatusLabel"), sandbox);
            vm.runInContext(normalize, sandbox);
            vm.runInContext(formatDiffValue, sandbox);
            vm.runInContext(computeDiffs, sandbox);
            return sandbox.computeManualDiffs();
        }

        const diffsWhenExtended = runDiffs([], ["CONTACTED"]);
        assert.ok(diffsWhenExtended.some((d) => d.key === "operatorStatuses"),
            "draft with a selected status must be flagged as diff when the source has none (gap fix)");

        const diffsWhenSame = runDiffs(["CONTACTED"], ["CONTACTED"]);
        assert.ok(!diffsWhenSame.some((d) => d.key === "operatorStatuses"),
            "identical status sets must not be flagged as diff");
    });

    it("W9: renderBatchConfigRow adds the 状态 scope line with Chinese labels (S3b-3)", () => {
        const renderRow = extractFn("renderBatchConfigRow");
        assert.ok(renderRow, "renderBatchConfigRow must exist");

        function makeConfig(operatorStatuses) {
            return {
                id: 1, configName: "状态筛选任务", mailType: "INTRODUCTION", autoEnabled: false,
                funnelLevel: null, tags: [], regions: [], emailDomains: [], operatorStatuses, discipline: null,
                templateId: null, cron: null, nextFireTime: null, lastExecutedAt: null
            };
        }
        const sandbox = {
            escapeHtml: (v) => String(v == null ? "" : v),
            regionLabel: (v) => v || "",
            operatorStatusOptions: [["NOT_CONTACTED", "未联系"]],
            cronToDisplayText: () => "",
            renderBatchConfigStatusToggle: () => ""
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("operatorStatusLabel"), sandbox);
        vm.runInContext(renderRow, sandbox);

        const html = sandbox.renderBatchConfigRow(makeConfig(["NOT_CONTACTED"]));
        assert.ok(html.includes("状态: 未联系"), "scope line must render 状态: with the Chinese label (S3b-3)");
        assert.ok(/<span class="batch-task-scope-line">状态: 未联系<\/span>/.test(html),
            "status scope line must be wrapped in .batch-task-scope-line");

        const emptyHtml = sandbox.renderBatchConfigRow(makeConfig([]));
        assert.ok(!emptyHtml.includes("状态:"), "empty operatorStatuses must not render a status line");
        assert.ok(emptyHtml.includes("无限制"), "empty filters must show 无限制");
    });

    it("W10: regression — P2b email-domain picker behavior stays intact (N3b-2)", () => {
        const setValue = extractFn("setBatchMultiPickerValue");
        const readValue = extractFn("readBatchMultiPickerValue");
        assert.ok(setValue && readValue, "multi picker helpers must exist");

        const hidden = element("");
        const sandbox = {
            document: { getElementById: (id) => id === "batchManualEmailDomains" ? hidden : null },
            renderBatchMultiPicker: () => {}
        };
        vm.createContext(sandbox);
        vm.runInContext(setValue, sandbox);
        vm.runInContext(readValue, sandbox);

        sandbox.setBatchMultiPickerValue("batchManualEmailDomains", ["university.edu", "research.cn"]);
        assert.strictEqual(hidden.value, "university.edu,research.cn",
            "email domain comma contract must be unchanged (N3b-2)");
        assert.deepStrictEqual(
            Array.from(sandbox.readBatchMultiPickerValue("batchManualEmailDomains")),
            ["university.edu", "research.cn"]
        );
    });
});
