const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const appPath = path.join(__dirname, "..", "..", "main", "resources", "static", "app.js");
const indexPath = path.join(__dirname, "..", "..", "main", "resources", "static", "index.html");
const appSource = fs.readFileSync(appPath, "utf-8");
const indexSource = fs.readFileSync(indexPath, "utf-8");

const EDITOR_PICKER = "batchConfigEditorSenderAccounts";
const MANUAL_PICKER = "batchManualSenderAccounts";
const MANUAL_FIELD = "manualFieldSenderAccounts";

// K-dom-stub-tests-hide-dangling-refs: getElementById in the stubs always returns an element,
// so every new id is also asserted against the real index.html source text.
const NEW_IDS = [
    EDITOR_PICKER, EDITOR_PICKER + "Chips", EDITOR_PICKER + "Search", EDITOR_PICKER + "Dropdown",
    MANUAL_PICKER, MANUAL_PICKER + "Chips", MANUAL_PICKER + "Search", MANUAL_PICKER + "Dropdown",
    MANUAL_FIELD
];

const ALLOWED_CLASSES = new Set([
    "batch-config-field", "batch-config-field-label",
    "batch-tag-picker", "batch-tag-picker-control", "batch-tag-picker-chips",
    "batch-tag-picker-search", "batch-tag-picker-chevron", "batch-tag-picker-dropdown",
    "batch-config-diff-badge", "batch-config-diff-original"
]);

function extractFn(name) {
    const regex = new RegExp("(?:async\\s+)?function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}");
    const match = appSource.match(regex);
    if (!match) throw new Error("Could not find " + name + " in app.js");
    return match[0];
}

// The multi-picker registry is a top-level `var` assignment; capture its object literal.
function extractRegistry() {
    const start = appSource.indexOf("var BATCH_MULTI_PICKER_REGISTRY = {");
    if (start < 0) throw new Error("Could not find BATCH_MULTI_PICKER_REGISTRY in app.js");
    const bodyStart = appSource.indexOf("{", start);
    let depth = 0;
    let end = -1;
    for (let i = bodyStart; i < appSource.length; i++) {
        const ch = appSource[i];
        if (ch === "{") depth++;
        else if (ch === "}") {
            depth--;
            if (depth === 0) { end = i + 1; break; }
        }
    }
    if (end < 0) throw new Error("Could not balance BATCH_MULTI_PICKER_REGISTRY literal");
    return "var BATCH_MULTI_PICKER_REGISTRY = " + appSource.slice(bodyStart, end) + ";";
}

function element(value = "") {
    return {
        value,
        textContent: "",
        innerHTML: "",
        hidden: true,
        checked: false,
        disabled: false,
        dataset: {},
        style: {},
        classList: { added: [], add(c) { this.added.push(c); }, remove() {}, toggle() {} },
        setAttribute() {},
        addEventListener() {},
        querySelector() { return null; },
        querySelectorAll() { return []; }
    };
}

function elementStore() {
    const store = new Map();
    return {
        el(id) { if (!store.has(id)) store.set(id, element()); return store.get(id); },
        get(id) { return this.el(id); },
        set(id, value) { store.set(id, value); }
    };
}

function sandbox(extra) {
    const store = elementStore();
    const ctx = Object.assign({
        console,
        document: {
            getElementById: (id) => store.el(id),
            querySelector: () => null,
            querySelectorAll: () => []
        },
        escapeHtml: (v) => String(v == null ? "" : v)
    }, extra || {});
    vm.createContext(ctx);
    ctx.__store = store;
    return ctx;
}

function load(ctx, ...names) {
    for (const name of names) vm.runInContext(extractFn(name), ctx);
    return ctx;
}

function normalized(source) {
    return source.replace(/\s+/g, " ").trim();
}

function fieldBlock(pickerId) {
    const pickerIdx = indexSource.indexOf('data-tag-picker="' + pickerId + '"');
    assert.ok(pickerIdx >= 0, pickerId + " picker must exist in index.html");
    const start = indexSource.lastIndexOf('<div class="batch-config-field"', pickerIdx);
    const dropdownIdx = indexSource.indexOf('id="' + pickerId + 'Dropdown"', pickerIdx);
    assert.ok(start >= 0 && dropdownIdx > start, "the picker must live inside a batch-config-field block");
    // 1st </div> closes the dropdown, 2nd closes .batch-tag-picker, 3rd closes .batch-config-field.
    let end = indexSource.indexOf("</div>", dropdownIdx) + "</div>".length;
    end = indexSource.indexOf("</div>", end) + "</div>".length;
    end = indexSource.indexOf("</div>", end) + "</div>".length;
    return indexSource.slice(start, end);
}

describe("batch sender account filter: real DOM contract (S-1/S-2, I-3)", () => {
    it("registers every new id and data-tag-picker exactly once in the real index.html", () => {
        for (const id of NEW_IDS) {
            const hits = (indexSource.match(new RegExp('id="' + id + '"', "g")) || []).length;
            assert.strictEqual(hits, 1, id + " must appear exactly once in index.html");
        }
        for (const picker of [EDITOR_PICKER, MANUAL_PICKER]) {
            const hits = (indexSource.match(new RegExp('data-tag-picker="' + picker + '"', "g")) || []).length;
            assert.strictEqual(hits, 1, picker + " must be registered exactly once as a data-tag-picker");
        }
    });

    it("matches the S-1/S-2 DOM blocks verbatim", () => {
        const editorBlock = normalized(`
            <div class="batch-config-field">
                <span class="batch-config-field-label">发件邮箱</span>
                <div class="batch-tag-picker" data-tag-picker="${EDITOR_PICKER}">
                    <div class="batch-tag-picker-control">
                        <div id="${EDITOR_PICKER}Chips" class="batch-tag-picker-chips"></div>
                        <input type="search" id="${EDITOR_PICKER}Search" class="batch-tag-picker-search" placeholder="搜索发件邮箱；不选则全部可发送账号" autocomplete="off" aria-controls="${EDITOR_PICKER}Dropdown" aria-expanded="false">
                        <span class="batch-tag-picker-chevron" aria-hidden="true">⌄</span>
                    </div>
                    <input type="hidden" id="${EDITOR_PICKER}" value="">
                    <div id="${EDITOR_PICKER}Dropdown" class="batch-tag-picker-dropdown" role="listbox" aria-multiselectable="true" hidden></div>
                </div>
            </div>`);
        assert.ok(normalized(indexSource).includes(editorBlock), "the scheduled panel block must match S-1 verbatim");

        const manualBlock = normalized(`
            <div class="batch-config-field" id="${MANUAL_FIELD}">
                <span class="batch-config-field-label">发件邮箱</span>
                <div class="batch-tag-picker" data-tag-picker="${MANUAL_PICKER}">
                    <div class="batch-tag-picker-control">
                        <div id="${MANUAL_PICKER}Chips" class="batch-tag-picker-chips"></div>
                        <input type="search" id="${MANUAL_PICKER}Search" class="batch-tag-picker-search" placeholder="搜索发件邮箱；不选则全部可发送账号" autocomplete="off" aria-controls="${MANUAL_PICKER}Dropdown" aria-expanded="false">
                        <span class="batch-tag-picker-chevron" aria-hidden="true">⌄</span>
                    </div>
                    <input type="hidden" id="${MANUAL_PICKER}" value="">
                    <div id="${MANUAL_PICKER}Dropdown" class="batch-tag-picker-dropdown" role="listbox" aria-multiselectable="true" hidden></div>
                </div>
                <span class="batch-config-diff-badge" hidden>已修改</span>
                <div class="batch-config-diff-original" hidden></div>
            </div>`);
        assert.ok(normalized(indexSource).includes(manualBlock), "the manual panel block must match S-2 verbatim");
    });

    it("reuses only existing classes, adds no inline style and keeps the picker contract (S-1/S-2)", () => {
        for (const picker of [EDITOR_PICKER, MANUAL_PICKER]) {
            const block = fieldBlock(picker);
            const classes = [...block.matchAll(/class="([^"]*)"/g)]
                .flatMap((m) => m[1].split(/\s+/).filter(Boolean));
            const unknown = classes.filter((c) => !ALLOWED_CLASSES.has(c));
            assert.deepStrictEqual(unknown, [], "new DOM must reuse the existing picker classes only: " + unknown.join(","));
            assert.ok(!/\sstyle="/.test(block), "no inline style is allowed (I-3)");
            assert.ok(block.includes('<input type="hidden" id="' + picker + '" value="">'),
                "each picker must keep its hidden value input (I2b-3)");
            assert.ok(block.includes('aria-controls="' + picker + 'Dropdown"'),
                "the search box must point at its own dropdown");
            assert.ok(block.includes('role="listbox" aria-multiselectable="true" hidden'),
                "the dropdown must stay a hidden multi-select listbox");
            assert.ok(block.includes('placeholder="搜索发件邮箱；不选则全部可发送账号"'),
                "the search placeholder must state the empty-selection meaning");
            assert.ok(block.includes(">发件邮箱</span>"), "the field label must be 发件邮箱");
        }
        const manual = fieldBlock(MANUAL_PICKER);
        assert.ok(manual.includes('class="batch-config-diff-badge" hidden>已修改</span>')
            && manual.includes('class="batch-config-diff-original" hidden>'),
            "the manual picker must keep the 已修改 badge and original-value node (S-2)");
        assert.ok(fieldBlock(EDITOR_PICKER).indexOf('id="' + MANUAL_FIELD + '"') < 0,
            "the scheduled block must not carry the manual diff wrapper id");
    });

    it("replaces both section sub-texts while keeping the parent element and class (S-1/S-2)", () => {
        assert.ok(!indexSource.includes("所有条件同时生效，留空表示不限制"), "the old scheduled sub-text must be replaced");
        assert.ok(!indexSource.includes("邮件类型根据模板自动确定；留空筛选项表示不限制"), "the old manual sub-text must be replaced");
        assert.match(indexSource,
            /<div class="batch-config-editor-section-heading">\s*<h4>收件范围<\/h4>\s*<span>所有条件同时生效；已绑定发件账号的专家会跳过；发件邮箱留空使用全部可发送账号<\/span>\s*<\/div>/,
            "the scheduled 收件范围 heading must keep its wrapper class and carry the new sub-text");
        assert.match(indexSource,
            /<div class="batch-manual-section-heading">\s*<h4>模板与收件范围<\/h4>\s*<span>邮件类型根据模板自动确定；已绑定发件账号的专家会跳过；发件邮箱留空使用全部可发送账号<\/span>\s*<\/div>/,
            "the manual 模板与收件范围 heading must keep its wrapper class and carry the new sub-text");
    });

    it("keeps the 11 versioned static asset keys on one common value (I-3)", () => {
        // The literal itself must stay only in index.html (taskActivityCenter.test.js I-8 scans
        // src/test/js for it), so this test derives the value instead of pinning it.
        const keys = [...indexSource.matchAll(/\?v=([^"']+)/g)].map((m) => m[1]);
        assert.strictEqual(keys.length, 11, "index.html must keep exactly the 11 versioned asset keys");
        const distinct = [...new Set(keys)];
        assert.strictEqual(distinct.length, 1, "every versioned key must share one value: " + distinct.join(", "));
        assert.match(distinct[0], /^[0-9]{8}-[a-z0-9-]+$/, "the shared key must be <yyyymmdd>-<slug>");
    });
});

describe("batch sender account filter: registration and read paths (I-1/I-2/I-3)", () => {
    it("registers both pickers in the registry and binds them in the batch events", () => {
        const registry = extractRegistry();
        assert.ok(registry.includes(EDITOR_PICKER + ": {"), "the editor picker must be registered");
        assert.ok(registry.includes(MANUAL_PICKER + ": {"), "the manual picker must be registered");

        const bindEvents = extractFn("bindBatchSendTaskEvents");
        assert.ok(bindEvents.includes('bindBatchMultiPicker("' + EDITOR_PICKER + '")'),
            "the editor picker must be bound in bindBatchSendTaskEvents");
        assert.ok(bindEvents.includes('bindBatchMultiPicker("' + MANUAL_PICKER + '")'),
            "the manual picker must be bound in bindBatchSendTaskEvents");
    });

    it("wires every snapshot and form path to its own picker id", () => {
        const preload = extractFn("preloadBatchSendLookups");
        assert.ok(preload.includes('"/api/mail/sender-accounts"'),
            "the batch dialog must preload the live read-only account list");
        assert.ok(preload.includes('renderBatchMultiPicker("' + EDITOR_PICKER + '")'));
        assert.ok(preload.includes('renderBatchMultiPicker("' + MANUAL_PICKER + '")'));

        assert.ok(extractFn("buildConfigEditorRecipientSnapshot").includes('readBatchMultiPickerValue("' + EDITOR_PICKER + '")'),
            "the editor estimate snapshot must read the sender picker");
        assert.ok(extractFn("saveBatchConfigEditor").includes('readBatchMultiPickerValue("' + EDITOR_PICKER + '")'),
            "the config POST/PUT payload must read the sender picker");
        assert.ok(extractFn("showBatchConfigEditor").includes('setBatchMultiPickerValue("' + EDITOR_PICKER + '"'),
            "the config GET echo must write the sender picker");
        assert.ok(extractFn("deepCloneConfig").includes("senderAccountCodes"),
            "deepCloneConfig must carry senderAccountCodes into the manual draft");
        assert.ok(extractFn("fillManualFormDefaults").includes("senderAccountCodes: []"),
            "a standalone manual run must default to an empty selection");
        assert.ok(extractFn("fillManualFormFromDraft").includes('setBatchMultiPickerValue("' + MANUAL_PICKER + '"'),
            "the manual form must echo the inherited selection");
        assert.ok(extractFn("readManualFormValues").includes('readBatchMultiPickerValue("' + MANUAL_PICKER + '")'),
            "readManualFormValues must read the manual sender picker");
        assert.ok(extractFn("buildManualExecutionSnapshot").includes("senderAccountCodes"),
            "the manual execution snapshot must carry senderAccountCodes");
        assert.ok(extractFn("normalizeManualSnapshot").includes("senderAccountCodes"));
        assert.ok(extractFn("formatManualDiffValue").includes('key === "senderAccountCodes"'));
        assert.ok(extractFn("computeManualDiffs").includes('{ key: "senderAccountCodes", label: "发件邮箱" }'),
            "the sender selection must participate in manual diff detection");
        assert.ok(extractFn("computeAndRenderDiffs").includes('senderAccountCodes: "' + MANUAL_FIELD + '"'),
            "the diff renderer must target the manual sender field wrapper");
        assert.ok(extractFn("clearAllDiffMarkers").includes('"' + MANUAL_FIELD + '"'),
            "clearing diff markers must cover the manual sender field");
    });
});

describe("batch sender account filter: options come from the live logical accounts (I-1)", () => {
    function optionContext(accounts) {
        const ctx = sandbox({ batchTaskState: { preloadedSenderAccounts: accounts } });
        vm.runInContext(extractRegistry(), ctx);
        load(ctx, "batchSenderAccountOptions", "readBatchMultiPickerValue", "setBatchMultiPickerValue", "renderBatchMultiPicker");
        return ctx;
    }

    it("keeps two logical codes of one physical inbox as two options and labels disabled accounts", () => {
        const ctx = optionContext([
            { accountCode: "LuKai", senderEmail: "lukai@example.com", enabled: true, inboundMailboxCode: "SHARED", imapUsername: "shared@imap.example.com" },
            { accountCode: "LuKai_QF", senderEmail: "lukai.qf@example.com", enabled: false, inboundMailboxCode: "SHARED", imapUsername: "shared@imap.example.com" },
            { accountCode: "Other", senderEmail: "other@example.com", enabled: true, inboundMailboxCode: "OTHER" }
        ]);
        const options = Array.from(ctx.batchSenderAccountOptions());
        assert.deepStrictEqual(Array.from(options.map((o) => o.value)), ["LuKai", "LuKai_QF", "Other"],
            "value must be the raw accountCode and no IMAP-owner grouping may collapse aliases");
        assert.deepStrictEqual(Array.from(options.map((o) => o.label)), [
            "lukai@example.com · LuKai",
            "lukai.qf@example.com · LuKai_QF（已停用，本次不会发信）",
            "other@example.com · Other"
        ], "label must be `senderEmail · accountCode`, with disabled accounts marked");
    });

    it("shows a historical code that is missing from the live list instead of wiping it", () => {
        const ctx = optionContext([]);
        ctx.setBatchMultiPickerValue(MANUAL_PICKER, ["LuKai_QF"]);
        assert.strictEqual(ctx.__store.get(MANUAL_PICKER).value, "LuKai_QF",
            "an empty account list must not clear the stored selection");
        ctx.renderBatchMultiPicker(MANUAL_PICKER);
        const chips = ctx.__store.get(MANUAL_PICKER + "Chips").innerHTML;
        assert.ok(chips.includes("LuKai_QF"), "the historical code must stay visible as a chip");
        assert.deepStrictEqual(Array.from(ctx.readBatchMultiPickerValue(MANUAL_PICKER)), ["LuKai_QF"],
            "the read path must still return the historical code");
    });

    function preloadContext(accountLoader) {
        const rendered = [];
        const ctx = sandbox({
            batchTaskState: { preloadedSenderAccounts: [], preloadedTemplates: [] },
            api: accountLoader,
            loadBatchTagOptions: async () => {},
            refreshBatchTemplateSelectors: () => {},
            loadBatchSendTypeProviders: async () => [],
            renderBatchMultiPicker: (id) => rendered.push(id)
        });
        ctx.__rendered = rendered;
        load(ctx, "preloadBatchSendLookups");
        return ctx;
    }

    it("preloads the live account list into both pickers", async () => {
        const accounts = [{ accountCode: "LuKai", senderEmail: "lukai@example.com", enabled: true }];
        const ctx = preloadContext(async (url) => (url === "/api/mail/sender-accounts" ? accounts : []));

        await ctx.preloadBatchSendLookups();

        assert.deepStrictEqual(Array.from(ctx.batchTaskState.preloadedSenderAccounts), accounts,
            "the batch dialog must preload the live read-only account list");
        assert.ok(ctx.__rendered.includes(EDITOR_PICKER) && ctx.__rendered.includes(MANUAL_PICKER),
            "both sender pickers must re-render once the accounts arrive");
    });

    it("keeps the previously loaded accounts when the account request fails", async () => {
        const accounts = [{ accountCode: "LuKai", senderEmail: "lukai@example.com", enabled: true }];
        const ctx = preloadContext(async (url) => {
            if (url === "/api/mail/sender-accounts") throw new Error("network down");
            return [];
        });
        ctx.batchTaskState.preloadedSenderAccounts = accounts;

        await ctx.preloadBatchSendLookups();

        assert.deepStrictEqual(Array.from(ctx.batchTaskState.preloadedSenderAccounts), accounts,
            "a failed account request must not wipe the already loaded options");
    });
});

describe("batch sender account filter: config save/echo and manual inheritance (I-2)", () => {
    const STORED_CONFIG = {
        id: 7,
        configName: "任务A",
        cron: "0 0 9 * * ?",
        tags: [],
        regions: [],
        emailDomains: [],
        expertTypes: ["PRODUCTION_RND"],
        senderAccountCodes: ["A"],
        templateId: null
    };

    function flowContext(posted) {
        const ctx = sandbox({
            batchTaskState: {
                editorMode: "create", editorId: null, editorAutoEnabled: false,
                manualSource: null, manualDraft: null,
                preloadedSenderAccounts: [], preloadedTemplates: []
            },
            api: async (url, opts) => { posted.push({ url, body: JSON.parse(opts.body) }); return {}; },
            showStatus: () => {}, hideBatchConfigEditor: () => {}, loadBatchConfigList: () => {},
            readBatchTagPickerValue: () => [], readBatchRegionPickerValue: () => [],
            gateToggleChecked: () => false,
            resolveBatchTemplateMailType: () => "INTRODUCTION",
            setBatchTagPickerValue: () => {}, setBatchRegionPickerValue: () => {},
            fillBatchConfigEditorTemplateSelector: () => {}, syncBatchConfigEditorScheduleFields: () => {},
            updateBatchConfigVolumeHint: () => {}, fillBatchManualTemplateSelector: () => {},
            computeAndRenderDiffs: () => {}, updateGateToggleLabel: () => {},
            renderBatchMultiPicker: () => {}, scheduleRecipientPreview: () => {}
        });
        load(ctx, "readBatchMultiPickerValue", "setBatchMultiPickerValue", "isCronClock", "padClock",
            "saveBatchConfigEditor", "showBatchConfigEditor", "deepCloneConfig", "fillManualFormFromDraft");
        return ctx;
    }

    function fillEditorForm(ctx) {
        const store = ctx.__store;
        store.get("batchConfigEditorName").value = "任务A";
        store.get("batchConfigEditorFrequency").value = "daily";
        store.get("batchConfigEditorTime").value = "09:00";
        store.get("batchConfigEditorRoundSize").value = "50";
        store.get("batchConfigEditorRoundsPerRun").value = "1";
        store.get("batchConfigEditorPerMailIntervalSec").value = "1";
        store.get("batchConfigEditorPerRoundIntervalSec").value = "60";
        store.get("batchConfigEditorSelfCheckTtlMin").value = "30";
        store.get("batchConfigEditorExpertTypes").value = "PRODUCTION_RND";
        store.get(EDITOR_PICKER).value = "A";
    }

    it("saves selection A, echoes it back from the config GET and inherits it into the manual panel", async () => {
        const posted = [];
        const ctx = flowContext(posted);
        fillEditorForm(ctx);

        await ctx.saveBatchConfigEditor();
        assert.strictEqual(posted.length, 1, "the save must issue exactly one config request");
        assert.strictEqual(posted[0].url, "/api/mail/batch-send/configs");
        assert.deepStrictEqual(Array.from(posted[0].body.senderAccountCodes), ["A"],
            "the config POST payload must carry the ordered selection");

        ctx.showBatchConfigEditor(STORED_CONFIG);
        assert.strictEqual(ctx.__store.get(EDITOR_PICKER).value, "A",
            "reopening the config must echo senderAccountCodes into the picker");

        const clone = ctx.deepCloneConfig(STORED_CONFIG);
        assert.deepStrictEqual(Array.from(clone.senderAccountCodes), ["A"],
            "deepCloneConfig must carry the selection into the manual draft");
        clone.senderAccountCodes.push("Z");
        assert.deepStrictEqual(Array.from(STORED_CONFIG.senderAccountCodes), ["A"],
            "the clone must be a copy, not a shared reference");

        ctx.batchTaskState.manualDraft = ctx.deepCloneConfig(STORED_CONFIG);
        ctx.fillManualFormFromDraft();
        assert.strictEqual(ctx.__store.get(MANUAL_PICKER).value, "A",
            "the manual panel must inherit the source task selection");
    });

    it("keeps a historical code that the live account list no longer offers through echo and re-save", async () => {
        const posted = [];
        const ctx = flowContext(posted);
        fillEditorForm(ctx);

        ctx.showBatchConfigEditor(Object.assign({}, STORED_CONFIG, { senderAccountCodes: ["LuKai_QF"] }));
        assert.strictEqual(ctx.__store.get(EDITOR_PICKER).value, "LuKai_QF",
            "an unknown historical code must be echoed verbatim, never silently replaced by []");

        load(ctx, "buildConfigEditorRecipientSnapshot");
        ctx.resolveBatchTemplateMailType = () => "INTRODUCTION";
        assert.deepStrictEqual(Array.from(ctx.buildConfigEditorRecipientSnapshot().senderAccountCodes), ["LuKai_QF"],
            "the estimate snapshot must keep the historical code");

        await ctx.saveBatchConfigEditor();
        assert.deepStrictEqual(Array.from(posted[0].body.senderAccountCodes), ["LuKai_QF"],
            "the save payload must keep the historical code so the server can validate it");
    });
});

describe("batch sender account filter: manual diff and execution snapshots (I-2/S-2)", () => {
    const SOURCE_CONFIG = {
        id: 7,
        configName: "任务A",
        templateId: null,
        mailType: "INTRODUCTION",
        funnelLevel: "",
        tags: [],
        regions: [],
        emailDomains: [],
        discipline: "",
        operatorStatuses: [],
        expertTypes: ["PRODUCTION_RND", "ACADEMIC_RND", "HYBRID_RND"],
        senderAccountCodes: ["A"],
        researchDirectionFilter: "ANY",
        gateFilterEnabled: false,
        roundSize: 50,
        roundsPerRun: 1,
        perMailIntervalMs: 1000,
        perRoundIntervalMs: 60000,
        selfCheckTtlMinutes: 30
    };

    function manualContext() {
        const ctx = sandbox({
            batchTaskState: { manualSource: SOURCE_CONFIG, manualDraft: null, preloadedSenderAccounts: [] },
            readBatchTagPickerValue: () => [], readBatchRegionPickerValue: () => [],
            gateToggleChecked: () => false,
            resolveBatchTemplateMailType: () => "INTRODUCTION"
        });
        const badge = { hidden: true };
        const original = { hidden: true, textContent: "" };
        const senderField = element();
        senderField.querySelector = (sel) =>
            sel === ".batch-config-diff-badge" ? badge : (sel === ".batch-config-diff-original" ? original : null);
        ctx.__store.set(MANUAL_FIELD, senderField);
        ctx.__diffNodes = { badge, original, senderField };

        load(ctx, "readBatchMultiPickerValue", "readManualFormValues", "normalizeManualSnapshot",
            "formatManualDiffValue", "computeManualDiffs", "computeAndRenderDiffs",
            "buildManualExecutionSnapshot", "buildConfigEditorRecipientSnapshot");

        const store = ctx.__store;
        store.get("batchManualTemplateId").value = "";
        store.get("batchManualFunnelLevel").value = "";
        store.get("batchManualEmailDomains").value = "";
        store.get("batchManualDiscipline").value = "";
        store.get("batchManualResearchDirectionFilter").value = "ANY";
        store.get("batchManualOperatorStatuses").value = "";
        store.get("batchManualExpertTypes").value = "PRODUCTION_RND,ACADEMIC_RND,HYBRID_RND";
        store.get("batchManualRoundSize").value = "50";
        store.get("batchManualRoundsPerRun").value = "1";
        store.get("batchManualPerMailIntervalSec").value = "1";
        store.get("batchManualPerRoundIntervalSec").value = "60";
        store.get("batchManualSelfCheckTtlMin").value = "30";
        store.get(MANUAL_PICKER).value = "B";
        store.get(EDITOR_PICKER).value = "B";
        return ctx;
    }

    it("reports 已修改 with the original value A against the new value B", () => {
        const ctx = manualContext();
        const diffs = Array.from(ctx.computeManualDiffs());
        assert.strictEqual(diffs.length, 1, "only the sender selection changed");
        assert.strictEqual(diffs[0].key, "senderAccountCodes");
        assert.strictEqual(diffs[0].label, "发件邮箱");
        assert.strictEqual(diffs[0].oldDisplay, "A");
        assert.strictEqual(diffs[0].newDisplay, "B");

        ctx.computeAndRenderDiffs();
        const nodes = ctx.__diffNodes;
        assert.ok(nodes.senderField.classList.added.includes("is-config-diff"),
            "the manual sender field must be marked as modified");
        assert.strictEqual(nodes.badge.hidden, false, "the 已修改 badge must be shown");
        assert.strictEqual(nodes.original.hidden, false, "the original value must be shown");
        assert.strictEqual(nodes.original.textContent, "原：A", "the original value must be the source task value");
    });

    it("carries the changed selection B into the estimate and execution snapshots", () => {
        const ctx = manualContext();
        const manual = ctx.buildManualExecutionSnapshot();
        assert.deepStrictEqual(Array.from(manual.senderAccountCodes), ["B"],
            "the manual execution snapshot must carry the changed selection");
        assert.strictEqual(manual.templateId, null);
        assert.strictEqual(manual.mailType, "INTRODUCTION");

        const editor = ctx.buildConfigEditorRecipientSnapshot();
        assert.deepStrictEqual(Array.from(editor.senderAccountCodes), ["B"],
            "the editor estimate snapshot must read the same field");
    });

    it("posts an empty selection for a standalone manual run", async () => {
        const posted = [];
        const ctx = sandbox({
            batchTaskState: { manualSource: null, manualDraft: null, preloadedSenderAccounts: [] },
            api: async (url, opts) => { posted.push(JSON.parse(opts.body)); return { executionId: 42 }; },
            showStatus: () => {}, closeBatchManualConfirmDialog: () => {},
            openBatchExecutionLogs: () => {}, openBatchConfigLogs: () => {},
            readBatchTagPickerValue: () => [], readBatchRegionPickerValue: () => [],
            resolveBatchTemplateMailType: () => "INTRODUCTION"
        });
        load(ctx, "readBatchMultiPickerValue", "readManualFormValues",
            "buildManualExecutionSnapshot", "confirmManualExecution");
        ctx.__store.get(MANUAL_PICKER).value = "";

        assert.deepStrictEqual(Array.from(ctx.readManualFormValues().senderAccountCodes), [],
            "an untouched standalone manual form reads as an empty selection");

        await ctx.confirmManualExecution();
        assert.strictEqual(posted.length, 1);
        assert.strictEqual(posted[0].sourceConfigId, null);
        assert.deepStrictEqual(Array.from(posted[0].snapshot.senderAccountCodes), [],
            "a standalone manual run with no selection must send senderAccountCodes: []");
    });
});
