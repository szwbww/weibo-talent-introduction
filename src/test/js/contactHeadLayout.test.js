const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const staticDir = path.join(__dirname, "..", "..", "main", "resources", "static");
const appJsPath = path.join(staticDir, "app.js");
const appJsSource = fs.readFileSync(appJsPath, "utf-8");
const stylesCssPath = path.join(staticDir, "styles.css");
const stylesCssSource = fs.readFileSync(stylesCssPath, "utf-8");
const indexHtmlPath = path.join(staticDir, "index.html");
const indexHtmlSource = fs.readFileSync(indexHtmlPath, "utf-8");

function extractFn(name) {
    const regex = new RegExp("(?:async\\s+)?function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}");
    const match = appJsSource.match(regex);
    if (!match) throw new Error("Could not find " + name + " in app.js");
    return match[0];
}

function makeEl(overrides = {}) {
    return {
        value: "",
        innerHTML: "",
        hidden: false,
        disabled: false,
        dataset: {},
        setAttribute() {},
        ...overrides
    };
}

describe("contact head layout C (P2 S-8)", () => {
    it("loadContactDetail source contains all S-8 ids and no removed artifacts", () => {
        const fnSource = extractFn("loadContactDetail");

        for (const id of [
            "senderBindingToggle",
            "senderBindingPop",
            "senderBindingSelect",
            "senderBindingDirtyNote",
            "sendManualMailBtn",
            "contactHeadMoreRow",
            "contactHeadMoreToggle",
            "manualMailOption"
        ]) {
            assert.ok(fnSource.includes('id="' + id + '"'), 'loadContactDetail must render id="' + id + '"');
        }
        assert.ok(!fnSource.includes("contact-head-mail-row"), "contact-head-mail-row must not appear in loadContactDetail");
        assert.ok(!fnSource.includes("sender-binding-editor"), "sender-binding-editor must not appear in loadContactDetail");
        // The new S-8 action-bar template must not carry inline styles (metadata-grid legacy style= attributes are pre-existing;
        // the plan's global criterion is that the app.js style= hit set is unchanged — verified separately).
        const actionsTemplate = fnSource.slice(
            fnSource.indexOf('$("#contactHeadActions").innerHTML'),
            fnSource.indexOf("const banner = renderManualAttentionBanner(contact);")
        );
        assert.ok(actionsTemplate.length > 0, "action-bar template region must be found");
        assert.ok(!actionsTemplate.includes('style="'), "new action-bar template must not introduce inline styles");
        assert.ok(
            fnSource.includes('data-original="${boundSenderAccountCode}"'),
            "data-original must be written from the trimmed binding value (I-3/R-1)"
        );
        assert.ok(fnSource.includes('id="senderBindingSelect" data-original="'),
            "data-original must be on the senderBindingSelect element");
    });

    it("updateSenderBindingDirtyState drives all three guards together (I-2)", () => {
        function makeSandbox(selectValue, original) {
            const pill = makeEl();
            const note = makeEl({ hidden: true });
            const sendBtn = makeEl({ disabled: false });
            const select = makeEl({ value: selectValue, dataset: { original } });
            const els = {
                senderBindingSelect: select,
                senderBindingToggle: pill,
                senderBindingDirtyNote: note,
                sendManualMailBtn: sendBtn
            };
            const sandbox = {
                $: (sel) => els[sel.replace(/^#/, "")]
            };
            vm.createContext(sandbox);
            vm.runInContext(extractFn("updateSenderBindingDirtyState"), sandbox);
            return { sandbox, els };
        }

        // dirty: select.value !== dataset.original
        const dirty = makeSandbox("ACC_B", "ACC_A");
        dirty.sandbox.updateSenderBindingDirtyState();
        assert.ok(dirty.els.senderBindingSelect.value !== (dirty.els.senderBindingSelect.dataset.original || ""));
        assert.strictEqual(dirty.els.senderBindingDirtyNote.hidden, false, "dirty note must be visible");
        assert.strictEqual(dirty.els.sendManualMailBtn.disabled, true, "send button must be disabled");
        assert.strictEqual(dirty.els.senderBindingToggle.dataset.dirty, "true", "pill must carry data-dirty=true");

        // clean: select.value === dataset.original
        const clean = makeSandbox("ACC_A", "ACC_A");
        clean.sandbox.updateSenderBindingDirtyState();
        assert.ok(!(clean.els.senderBindingSelect.value !== (clean.els.senderBindingSelect.dataset.original || "")));
        assert.strictEqual(clean.els.senderBindingDirtyNote.hidden, true, "dirty note must be hidden when clean");
        assert.strictEqual(clean.els.sendManualMailBtn.disabled, false, "send button must be enabled when clean");
        assert.strictEqual(clean.els.senderBindingToggle.dataset.dirty, "false", "pill must carry data-dirty=false when clean");
    });

    it("unbound expert with a selected account is judged dirty (I-3)", () => {
        const pill = makeEl();
        const note = makeEl({ hidden: true });
        const sendBtn = makeEl({ disabled: false });
        const select = makeEl({ value: "ACC_A", dataset: { original: "" } });
        const els = {
            senderBindingSelect: select,
            senderBindingToggle: pill,
            senderBindingDirtyNote: note,
            sendManualMailBtn: sendBtn
        };
        const sandbox = { $: (sel) => els[sel.replace(/^#/, "")] };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("updateSenderBindingDirtyState"), sandbox);
        sandbox.updateSenderBindingDirtyState();

        // dataset.original === "" (unbound) while a real account is selected -> dirty, never inferred clean
        assert.strictEqual(note.hidden, false);
        assert.strictEqual(sendBtn.disabled, true);
        assert.strictEqual(pill.dataset.dirty, "true");
    });

    it("whitespace-only boundSenderAccountCode renders unbound and empty (R-1/V-1)", () => {
        const fnSource = extractFn("loadContactDetail");
        const assignStart = fnSource.indexOf('$("#contactHeadActions").innerHTML = `');
        assert.ok(assignStart !== -1, "header template assignment must exist");
        const tmplStart = fnSource.indexOf("`", assignStart) + 1;
        const tmplEnd = fnSource.indexOf("`;", tmplStart);
        assert.ok(tmplStart !== -1 && tmplEnd !== -1 && tmplEnd > tmplStart, "header template literal must be extractable");
        const headerTemplate = fnSource.slice(tmplStart, tmplEnd);

        const derivationMatch = fnSource.match(/const boundSenderAccountCode = ([^;]+);/);
        assert.ok(derivationMatch, "loadContactDetail must derive a trimmed boundSenderAccountCode local");

        const sandbox = {};
        vm.createContext(sandbox);
        vm.runInContext(`
            const contact = { boundSenderAccountCode: "   ", id: 5, senderAccountChanged: false, operatorStatus: "", currentIndexLevel: "", autoReplyEnabled: false };
            const state = { contactHeadExpanded: false };
            const options = [];
            const operatorStatusOptions = [];
            const indexLevelOptions = [];
            const renderMailSendOptionGroups = () => "";
            const optionsFromArray = () => "";
            const boundSenderAccountCode = ${derivationMatch[1]};
            const materials = null;
            var html = \`${headerTemplate}\`;
        `, sandbox);

        // whitespace-only binding must render exactly as unbound (I-9): gray dot + 未绑定 + empty data-original
        assert.ok(sandbox.html.includes('class="sender-binding-dot is-unbound"'), "whitespace-only binding must render the unbound dot");
        assert.ok(sandbox.html.includes('>未绑定<'), "whitespace-only binding must render 未绑定 label");
        assert.ok(sandbox.html.includes('id="senderBindingSelect" data-original=""'), "whitespace-only binding must render empty data-original");
        assert.ok(!sandbox.html.includes('data-original="   "'), "whitespace-only binding must not leak whitespace into data-original");
        assert.ok(!sandbox.html.includes('sender-binding-dot"></span>'), "whitespace-only binding must not render a bound dot");
    });

    it("send-manual-mail keeps senderAccountCode null and never reads the select (I-1)", async () => {
        let captured = null;
        const els = {
            manualMailOption: makeEl({ value: "INTRODUCTION:1" })
        };
        const sandbox = {
            $: (sel) => els[sel.replace(/^#/, "")] || null,
            api: async (url, options) => {
                captured = options.body;
                return {};
            },
            showStatus: () => {},
            loadContactDetail: async () => {},
            loadContacts: async () => {}
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("handleContactAction"), sandbox);

        await sandbox.handleContactAction({ dataset: { id: "5", action: "send-manual-mail" } });

        const body = JSON.parse(captured);
        assert.strictEqual(body.senderAccountCode, null, "request body senderAccountCode must stay null");
        assert.strictEqual(body.optionType, "INTRODUCTION");
        assert.strictEqual(body.optionValue, "1");

        const fnSource = extractFn("handleContactAction");
        const branchStart = fnSource.indexOf('action === "send-manual-mail"');
        assert.ok(branchStart !== -1, "send-manual-mail branch must exist");
        const branchEnd = fnSource.indexOf('if (action === "', branchStart + 1);
        const sendBranch = fnSource.slice(branchStart, branchEnd === -1 ? fnSource.length : branchEnd);
        assert.ok(sendBranch.includes("senderAccountCode: null"), "send-manual-mail branch must keep senderAccountCode: null");
        assert.ok(!sendBranch.includes("senderBindingSelect"), "send-manual-mail branch must not read senderBindingSelect");
    });

    it("toggle-contact-head-more flips state.contactHeadExpanded without reset (I-4)", async () => {
        const row = makeEl();
        const toggle = makeEl();
        toggle.setAttribute = (name, value) => { toggle[name] = value; };
        const els = {
            contactHeadMoreRow: row,
            contactHeadMoreToggle: toggle
        };
        const sandbox = {
            state: { contactHeadExpanded: false },
            $: (sel) => els[sel.replace(/^#/, "")] || null
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("handleContactAction"), sandbox);

        await sandbox.handleContactAction({ dataset: { action: "toggle-contact-head-more" } });
        assert.strictEqual(sandbox.state.contactHeadExpanded, true, "first toggle must expand");
        assert.strictEqual(row.hidden, false, "row must be visible when expanded");
        assert.strictEqual(toggle["aria-expanded"], "true", "toggle must carry aria-expanded=true");

        await sandbox.handleContactAction({ dataset: { action: "toggle-contact-head-more" } });
        assert.strictEqual(sandbox.state.contactHeadExpanded, false, "second toggle must collapse");
        assert.strictEqual(row.hidden, true, "row must be hidden when collapsed");
        assert.strictEqual(toggle["aria-expanded"], "false", "toggle must carry aria-expanded=false");

        assert.ok(extractFn("loadContactDetail").includes("state.contactHeadExpanded"),
            "loadContactDetail must read state.contactHeadExpanded for rendering");
        assert.ok((appJsSource.match(/contactHeadExpanded/g) || []).length >= 3,
            "contactHeadExpanded must appear at least 3 times in app.js (state declaration, render read, toggle branch)");
    });

    it("updateExpertTagEditor passes dataset.layout back to the renderer (I-7)", () => {
        const editor = makeEl({ dataset: { orcid: "0000-0001", layout: "inline" }, outerHTML: "" });
        const sandbox = {
            document: { getElementById: (id) => (id === "expertTagEditor" ? editor : null) }
        };
        vm.createContext(sandbox);
        vm.runInContext(`
            const expertTagLabels = {};
            function escapeHtml(v) {
                return String(v == null ? "" : v);
            }
        `, sandbox);
        vm.runInContext(extractFn("renderExpertTagEditor"), sandbox);
        vm.runInContext(extractFn("updateExpertTagEditor"), sandbox);

        sandbox.updateExpertTagEditor("0000-0001", ["t1"], "CANDIDATE", "expertTagEditor");
        assert.ok(editor.outerHTML.includes("is-inline"), "re-render must keep the inline layout");
        assert.ok(editor.outerHTML.includes('data-layout="inline"'), "re-render must keep data-layout=inline");

        // section (or missing) layout falls back to the block form
        editor.dataset.layout = "section";
        sandbox.updateExpertTagEditor("0000-0001", ["t1"], "CANDIDATE", "expertTagEditor");
        assert.ok(editor.outerHTML.includes("detail-section"), "re-render must fall back to section layout");
        assert.ok(!editor.outerHTML.includes("is-inline"), "section layout must not render is-inline");
    });

    it("detail sub-tab data-panel counts are unchanged (I-10)", () => {
        const mailPreviewTotal = (appJsSource.match(/data-panel="mail-preview"/g) || []).length;
        const templateTotal = (appJsSource.match(/data-panel="template"/g) || []).length;
        const mailPreviewPanelDivs = (appJsSource.match(/<div class="detail-tab-panel" data-panel="mail-preview"/g) || []).length;

        assert.equal(mailPreviewPanelDivs, 2);
        assert.equal(mailPreviewTotal, templateTotal);
    });

    it("styles.css declares every S-1..S-6 class and I-8/I-9 rules", () => {
        const required = [
            ".contact-head-main-row",
            ".contact-head-divider",
            ".sender-binding-pill",
            '.sender-binding-pill[data-dirty="true"]',
            ".sender-binding-dot",
            ".sender-binding-dot.is-unbound",
            '.sender-binding-pill[data-dirty="true"] .sender-binding-dot',
            ".dropdown-menu.sender-binding-pop",
            ".sender-binding-pop-label",
            ".sender-binding-pop #senderBindingSelect",
            ".sender-binding-pop-hint",
            ".sender-binding-pop-foot",
            ".contact-head-dirty-note",
            ".contact-head-actions .button[disabled]",
            '.contact-head-more-toggle[aria-expanded="true"]',
            ".expert-tag-editor.is-inline",
            ".expert-tag-editor.is-inline .inbound-tag-editor-chips",
            '.expert-tag-editor.is-inline[data-expanded="true"]',
            ".expert-tag-add-btn",
            ".expert-tag-more-btn",
            ".expert-tag-nodoc",
            ".expert-tag-nodoc::before",
            ".expert-tag-editor.is-inline.tag-editor-loading"
        ];
        for (const cls of required) {
            assert.ok(stylesCssSource.includes(cls), cls + " must exist in styles.css");
        }

        // I-8: the loading override exists exactly once with min-height: 0
        assert.strictEqual(
            (stylesCssSource.match(/\.expert-tag-editor\.is-inline\.tag-editor-loading/g) || []).length,
            1,
            "is-inline.tag-editor-loading rule must appear exactly once"
        );
        assert.ok(stylesCssSource.includes(".expert-tag-editor.is-inline.tag-editor-loading {\n    min-height: 0;\n}"),
            "I-8 rule body must be min-height: 0");

        // I-9: is-unbound exists at least once
        assert.ok((stylesCssSource.match(/is-unbound/g) || []).length >= 1, "is-unbound must exist in styles.css");
    });

    it("DOM-stub trap guard: container and select generator still exist (K-dom-stub-tests-hide-dangling-refs)", () => {
        assert.ok(indexHtmlSource.includes('id="contactHeadActions"'), "index.html must still declare #contactHeadActions");
        const loadFn = extractFn("loadContactDetail");
        assert.ok(loadFn.includes('id="senderBindingSelect"'), "loadContactDetail must generate #senderBindingSelect");
        assert.ok(loadFn.includes("updateSenderBindingDirtyState();"), "loadContactDetail must initialize the dirty gate after fill");
    });
});

// ── plan 02: expert material tags (I2-1..I2-8, S2-1..S2-4) ──

const MATERIAL_ITEMS = [
    { code: "CV", label: "简历", status: "PROVIDED" },
    { code: "PASSPORT", label: "护照", status: "PENDING" },
    { code: "DEGREE", label: "学位", status: "DECLINED" },
    { code: "EMPLOYMENT", label: "工作", status: "PENDING" },
    { code: "PUBLICATIONS", label: "出版", status: "PROVIDED" },
    { code: "PATENTS", label: "专利", status: "PENDING" },
    { code: "RESEARCH", label: "研究", status: "DECLINED" }
];

function extractMaterialStateMap() {
    const match = appJsSource.match(/const EXPERT_MATERIAL_STATE_MAP = \{[\s\S]*?\n\};/);
    if (!match) throw new Error("Could not find EXPERT_MATERIAL_STATE_MAP in app.js");
    return match[0];
}

describe("expert material tags (plan 02)", () => {
    it("loadContactDetail fetches materials with isolated catch and renders the row after contactHeadMoreRow (I2-1/I2-5/S2-1)", () => {
        const fnSource = extractFn("loadContactDetail");
        const headerStart = fnSource.indexOf('$("#contactHeadActions").innerHTML');
        const headerEnd = fnSource.indexOf("const banner = renderManualAttentionBanner(contact);");
        const headerTemplate = fnSource.slice(headerStart, headerEnd);

        const materialsFetch = fnSource.indexOf("/api/expert-contacts/${contactId}/materials");
        assert.ok(materialsFetch !== -1, "loadContactDetail must request the materials endpoint");
        assert.ok(fnSource.indexOf("showStatus(\"材料状态加载失败: \" + error.message, \"error\")") > materialsFetch,
            "materials fetch must carry its own catch that shows one load-failure status");
        assert.ok(fnSource.includes("renderExpertMaterialRow(materials, contact.id)"),
            "loadContactDetail must render the material row from the fetched array");
        assert.ok(fnSource.includes('Array.isArray(materials) ? renderExpertMaterialRow(materials, contact.id) : ""'),
            "material row must be guarded by Array.isArray so failures render nothing");
        assert.ok(headerTemplate.indexOf('id="contactHeadMoreRow"') !== -1, "header template must keep contactHeadMoreRow");
        assert.ok(headerTemplate.indexOf("renderExpertMaterialRow(materials, contact.id)") > headerTemplate.indexOf('id="contactHeadMoreRow"'),
            "material row must be placed after contactHeadMoreRow");
    });

    it("materials GET rejection is isolated: siblings resolve, one error status, materials null (I2-5)", async () => {
        const fnSource = extractFn("loadContactDetail");
        const paStart = fnSource.indexOf("Promise.all([");
        assert.ok(paStart !== -1, "loadContactDetail must use Promise.all");
        const paEnd = fnSource.indexOf("]);", paStart) + 3;
        const promiseAllExpr = fnSource.slice(paStart, paEnd);
        assert.ok(promiseAllExpr.includes("/api/expert-contacts/${contactId}/materials"),
            "materials must be part of the parallel load");

        const calls = [];
        const sandbox = {
            contactId: 7,
            api: async (url) => {
                calls.push(url);
                if (url.endsWith("/materials")) throw new Error("materials down");
                if (url.includes("/documents")) return [];
                if (url.includes("/operator-action-logs")) return { records: [] };
                return { contact: { id: 7, expertName: "X" } };
            },
            loadMailSendOptions: async () => [],
            showStatus: (message, type) => { calls.push("status:" + message + ":" + type); }
        };
        vm.createContext(sandbox);
        vm.runInContext("var result = " + promiseAllExpr + ";", sandbox);
        const [detail, options, documents, logs, materials] = await sandbox.result;

        assert.strictEqual(detail.contact.id, 7, "detail must still resolve when materials fails");
        assert.strictEqual(materials, null, "failed materials must resolve to null");
        assert.ok(calls.includes("/api/expert-contacts/7/materials"), "materials endpoint must be called");
        assert.ok(calls.includes("status:材料状态加载失败: materials down:error"), "one load-failure status must be shown");
        const statusCalls = calls.filter((c) => typeof c === "string" && c.startsWith("status:"));
        assert.strictEqual(statusCalls.length, 1, "exactly one error status for the materials failure");
    });

    it("renderExpertMaterialRow renders exactly 7 ordered Chinese tags with tri-state visuals (I2-1/I2-4/S2-1/S2-2/S2-3)", () => {
        const sandbox = {
            escapeHtml: (v) => String(v == null ? "" : v)
        };
        vm.createContext(sandbox);
        vm.runInContext(extractMaterialStateMap() + "\n" + extractFn("renderExpertMaterialRow"), sandbox);

        const html = sandbox.renderExpertMaterialRow(MATERIAL_ITEMS, 42);

        // I2-1: exactly 7 tags, no 8th, no English request body
        assert.strictEqual((html.match(/class="expert-material-tag is-/g) || []).length, 7, "exactly 7 material tags");
        assert.ok(!html.includes("Your latest English"), "English request text must not be rendered");
        assert.ok(!html.includes("requestText"), "request text key must not be rendered");
        assert.ok(!html.includes("编辑材料"), "no edit-materials button allowed");
        assert.ok(!html.includes("保存材料"), "no save-materials button allowed");

        // order follows the API array: 简历 护照 学位 工作 出版 专利 研究
        const order = ["简历", "护照", "学位", "工作", "出版", "专利", "研究"];
        const positions = order.map((label) => html.indexOf(">" + label + "<"));
        for (let i = 0; i < positions.length; i++) {
            assert.ok(positions[i] !== -1, order[i] + " tag must exist");
            if (i > 0) assert.ok(positions[i] > positions[i - 1], order[i] + " must follow " + order[i - 1]);
        }

        // S2-1 skeleton: row carries data-contact-id and label precedes the tag container
        assert.ok(html.includes('<div class="contact-head-status-row" id="expertMaterialRow" data-contact-id="42">'),
            "material row must carry contact id");
        assert.ok(html.indexOf('<span class="contact-head-label">材料</span>') < html.indexOf('class="expert-material-tags"'),
            "材料 label must precede the tag container");
        assert.ok(html.includes('class="expert-material-tags" aria-label="专家材料状态"'), "tag container must carry the aria label");

        // I2-4 tri-state visuals, one per state
        const cvIdx = html.indexOf('data-material-code="CV"');
        const cvTag = html.slice(cvIdx - 220, cvIdx + 320);
        assert.ok(cvTag.includes('class="expert-material-tag is-provided"'), "PROVIDED must render is-provided");
        assert.ok(cvTag.includes('expert-material-tag-mark" aria-hidden="true">✓</span>'), "PROVIDED must render the ✓ mark");
        assert.ok(cvTag.includes('aria-label="简历：已提供，点击修改"'), "aria-label must carry Chinese material and status");

        const passportIdx = html.indexOf('data-material-code="PASSPORT"');
        const passportTag = html.slice(passportIdx - 220, passportIdx + 320);
        assert.ok(passportTag.includes('class="expert-material-tag is-pending"'), "PENDING must render is-pending");
        assert.ok(!passportTag.includes("expert-material-tag-mark"), "PENDING must not render a mark");
        assert.ok(passportTag.includes('aria-label="护照：待提供，点击修改"'), "PENDING aria-label must be 待提供");

        const degreeIdx = html.indexOf('data-material-code="DEGREE"');
        const degreeTag = html.slice(degreeIdx - 220, degreeIdx + 320);
        assert.ok(degreeTag.includes('class="expert-material-tag is-declined"'), "DECLINED must render is-declined");
        assert.ok(degreeTag.includes('expert-material-tag-mark" aria-hidden="true">⊘</span>'), "DECLINED must render the ⊘ mark");
        assert.ok(degreeTag.includes('aria-label="学位：暂不愿提供，点击修改"'), "DECLINED aria-label must be 暂不愿提供");

        // S2-3: each tag has exactly the three fixed menu items
        assert.strictEqual((html.match(/data-material-action="set-status"/g) || []).length, 21, "7 tags x 3 status items");
        assert.strictEqual((html.match(/>待提供</g) || []).length, 7, "待提供 menu item in every tag");
        assert.strictEqual((html.match(/>✓ 已提供</g) || []).length, 7, "✓ 已提供 menu item in every tag");
        assert.strictEqual((html.match(/>⊘ 暂不愿提供</g) || []).length, 7, "⊘ 暂不愿提供 menu item in every tag");

        // I2-6/S2-1: material DOM uses data-material-action only, no inline styles
        assert.ok(!html.includes("data-action="), "material DOM must never use the generic data-action attribute");
        assert.ok(!html.includes("style="), "material DOM must not carry inline styles");
    });

    it("saveExpertMaterialStatus PUTs once and replaces the row silently on success (I2-2/I2-3)", async () => {
        const sandbox = {
            escapeHtml: (v) => String(v == null ? "" : v)
        };
        vm.createContext(sandbox);
        vm.runInContext(extractMaterialStateMap() + "\n" + extractFn("renderExpertMaterialRow") + "\n" + extractFn("saveExpertMaterialStatus"), sandbox);

        const items = [
            { disabled: false, isConnected: true },
            { disabled: false, isConnected: true },
            { disabled: false, isConnected: true }
        ];
        const tag = { dataset: { materialCode: "CV" } };
        const row = { dataset: { contactId: "7" }, outerHTML: "old-row" };
        const wrapper = {
            querySelector: (sel) => (sel === ".expert-material-tag" ? tag : null),
            querySelectorAll: (sel) => (sel === ".dropdown-item" ? items : [])
        };
        const button = {
            closest: (sel) => (sel === "#expertMaterialRow" ? row : sel === ".dropdown" ? wrapper : null),
            dataset: { materialStatus: "PROVIDED" }
        };

        let captured = null;
        let resolveApi;
        const apiPromise = new Promise((resolve) => { resolveApi = resolve; });
        let statusCalls = 0;
        sandbox.api = (url, options) => { captured = { url, options }; return apiPromise; };
        sandbox.showStatus = () => { statusCalls++; };

        const pending = sandbox.saveExpertMaterialStatus(button);
        await Promise.resolve();
        assert.ok(items.every((i) => i.disabled === true), "menu items must be disabled during the request");
        resolveApi(MATERIAL_ITEMS);
        await pending;

        assert.strictEqual(captured.url, "/api/expert-contacts/7/materials/CV", "PUT URL must be exact");
        assert.strictEqual(captured.options.method, "PUT", "method must be PUT");
        assert.deepStrictEqual(JSON.parse(captured.options.body), { status: "PROVIDED" }, "body must be the raw status");
        assert.ok(row.outerHTML.includes('id="expertMaterialRow"'), "success must replace the row with the full render");
        assert.strictEqual((row.outerHTML.match(/class="expert-material-tag is-/g) || []).length, 7,
            "replaced row must re-render all 7 tags from the PUT response");
        assert.strictEqual(statusCalls, 0, "success must never call showStatus");

        const saveSource = extractFn("saveExpertMaterialStatus");
        assert.ok(!saveSource.includes("showStatus"), "saveExpertMaterialStatus itself must not show success status");
    });

    it("saveExpertMaterialStatus keeps old DOM and restores buttons on failure (I2-3)", async () => {
        const sandbox = {
            escapeHtml: (v) => String(v == null ? "" : v)
        };
        vm.createContext(sandbox);
        vm.runInContext(extractMaterialStateMap() + "\n" + extractFn("renderExpertMaterialRow") + "\n" + extractFn("saveExpertMaterialStatus"), sandbox);

        const items = [
            { disabled: false, isConnected: true },
            { disabled: false, isConnected: true },
            { disabled: false, isConnected: true }
        ];
        const tag = { dataset: { materialCode: "CV" } };
        const row = { dataset: { contactId: "7" }, outerHTML: "old-row" };
        const wrapper = {
            querySelector: (sel) => (sel === ".expert-material-tag" ? tag : null),
            querySelectorAll: (sel) => (sel === ".dropdown-item" ? items : [])
        };
        const button = {
            closest: (sel) => (sel === "#expertMaterialRow" ? row : sel === ".dropdown" ? wrapper : null),
            dataset: { materialStatus: "PROVIDED" }
        };
        sandbox.api = async () => { throw new Error("网络错误"); };
        sandbox.showStatus = () => {};

        await assert.rejects(() => sandbox.saveExpertMaterialStatus(button), /网络错误/);
        assert.strictEqual(row.outerHTML, "old-row", "failed PUT must keep the old row untouched");
        assert.ok(items.every((i) => i.disabled === false), "failed PUT must restore the menu buttons");
    });

    it("toggleExpertMaterialMenu opens one menu and closes others; outside click and Escape close all (I2-6)", () => {
        const tagA = makeEl();
        tagA.setAttribute = (name, value) => { tagA[name] = value; };
        const menuA = makeEl({ hidden: true, previousElementSibling: tagA });
        const tagB = makeEl();
        tagB.setAttribute = (name, value) => { tagB[name] = value; };
        const menuB = makeEl({ hidden: true, previousElementSibling: tagB });
        const row = { querySelectorAll: (sel) => (sel === ".dropdown-menu" ? [menuA, menuB] : []) };
        const wrapperA = { querySelector: (sel) => (sel === ".dropdown-menu" ? menuA : null) };
        const buttonA = { closest: (sel) => (sel === ".dropdown" ? wrapperA : null) };
        buttonA.setAttribute = (name, value) => { buttonA[name] = value; };
        const els = { expertMaterialRow: row };
        const sandbox = { $: (sel) => els[sel.replace(/^#/, "")] || null };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("closeExpertMaterialMenus") + "\n" + extractFn("toggleExpertMaterialMenu"), sandbox);

        sandbox.toggleExpertMaterialMenu(buttonA);
        assert.strictEqual(menuA.hidden, false, "toggle must open the target menu");
        assert.strictEqual(buttonA["aria-expanded"], "true", "target button must report aria-expanded=true");
        assert.strictEqual(menuB.hidden, true, "other menus must stay closed");
        assert.strictEqual(tagB["aria-expanded"], "false", "closed sibling tag must report aria-expanded=false");

        sandbox.toggleExpertMaterialMenu(buttonA);
        assert.strictEqual(menuA.hidden, true, "second toggle must close the menu");
        assert.strictEqual(buttonA["aria-expanded"], "false", "closed button must report aria-expanded=false");
        assert.strictEqual(tagA["aria-expanded"], "false", "closed tag must report aria-expanded=false");

        // outside click / Escape wiring lives in bindEvents next to the sender-binding logic
        assert.ok(appJsSource.includes(`document.addEventListener("click", () => {
        const pop = $("#senderBindingPop");
        if (pop && !pop.hidden) {
            pop.hidden = true;
            $("#senderBindingToggle")?.setAttribute("aria-expanded", "false");
        }
        closeExpertMaterialMenus();
    });`), "document click must close material menus without touching sender-binding logic");

        assert.ok(appJsSource.includes(`document.addEventListener("keydown", (event) => {
        if (event.key !== "Escape") return;
        const pop = $("#senderBindingPop");
        if (pop && !pop.hidden) {
            pop.hidden = true;
            $("#senderBindingToggle")?.setAttribute("aria-expanded", "false");
        }
        closeExpertMaterialMenus();
    });`), "Escape must close material menus without touching sender-binding logic");
    });

    it("material branch precedes generic action in the contact-head click handler (I2-6/I2-3)", () => {
        const firstStart = appJsSource.indexOf('$("#contactHeadActions").addEventListener("click", async (event) => {');
        assert.ok(firstStart !== -1, "first contact-head click listener must exist");
        const secondStart = appJsSource.indexOf('$("#contactHeadActions").addEventListener("click", (event) => {', firstStart + 1);
        assert.ok(secondStart !== -1, "sender-binding click listener must still exist");
        const firstListener = appJsSource.slice(firstStart, secondStart);

        const materialIdx = firstListener.indexOf('closest("button[data-material-action]")');
        const genericIdx = firstListener.indexOf('closest("button[data-action]")');
        assert.ok(materialIdx !== -1, "material branch must exist");
        assert.ok(genericIdx !== -1, "generic action branch must exist");
        assert.ok(materialIdx < genericIdx, "material branch must be checked before the generic action branch");
        assert.ok(firstListener.includes("event.stopPropagation();"), "material clicks must stopPropagation");
        assert.ok(firstListener.includes("saveExpertMaterialStatus(materialButton).catch((error) => {"),
            "set-status must go through saveExpertMaterialStatus");
        assert.ok(firstListener.includes('showStatus("材料状态保存失败: " + error.message, "error")'),
            "failure must produce exactly one error status");
        assert.ok(firstListener.includes('toggleExpertMaterialMenu(materialButton)'), "toggle must open the menu");
    });

    it("styles.css appends the S2-2 rules verbatim and leaves shared rules intact (S2-1/S2-2/S2-3)", () => {
        const s22Block = `.expert-material-tags {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 6px;
    min-width: 0;
}

.expert-material-tag {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    height: 26px;
    padding: 0 10px;
    border: 1px solid var(--border);
    border-radius: 999px;
    background: var(--bg-main);
    color: var(--text-secondary);
    font-family: var(--font-body);
    font-size: 11px;
    font-weight: 600;
    line-height: 1;
    white-space: nowrap;
    cursor: pointer;
    transition: var(--transition);
}

.expert-material-tag:hover {
    border-color: var(--primary);
    color: var(--primary);
}

.expert-material-tag:focus-visible {
    border-color: var(--primary);
    outline: none;
    box-shadow: 0 0 0 2px rgba(var(--primary-rgb), 0.18);
}

.expert-material-tag.is-provided {
    border-color: var(--success-border);
    background: var(--success-bg);
    color: var(--success);
}

.expert-material-tag.is-declined {
    border-color: rgba(148, 163, 184, 0.35);
    background: rgba(148, 163, 184, 0.12);
    color: var(--text-muted);
    text-decoration: line-through;
    opacity: 0.72;
}

.expert-material-tag-mark {
    font-size: 10px;
    font-weight: 700;
}

.expert-material-tag.is-pending .expert-material-tag-mark {
    display: none;
}

.expert-material-tag-caret {
    color: currentColor;
    font-size: 9px;
    opacity: 0.7;
}`;
        assert.ok(stylesCssSource.includes(s22Block), "S2-2 rules must be appended verbatim");

        assert.ok(stylesCssSource.includes(`.contact-head-status-row,
.contact-head-mail-row {
    display: flex;
    align-items: center;
    gap: 8px;
    min-width: 0;
    flex-wrap: wrap;
}`), "shared contact-head-status-row rule must be unchanged");

        assert.ok(stylesCssSource.includes(`.contact-head-label {
    font-size: 11px;
    font-weight: 600;
    color: var(--text-muted);
    width: 85px;
    min-width: 85px;
    white-space: nowrap;
    text-transform: uppercase;
    letter-spacing: 0.3px;
}`), "shared contact-head-label rule must be unchanged");

        assert.ok(stylesCssSource.includes(`.dropdown-menu {
    position: absolute;
    top: calc(100% + 4px);
    right: 0;
    min-width: 180px;
    background: var(--panel-bg);
    border: 1px solid var(--panel-border);
    border-radius: var(--radius-md);
    box-shadow: var(--shadow-lg);
    padding: 4px;
    z-index: var(--z-overlay);
    display: flex;
    flex-direction: column;
    gap: 1px;
}`), "shared dropdown-menu rule must be unchanged");
    });

    it("showExpertDetail hides and clears head actions without fetching materials (I2-8)", () => {
        const fnSource = extractFn("showExpertDetail");
        assert.ok(fnSource.includes('$("#contactHeadActions").hidden = true;'), "showExpertDetail must hide #contactHeadActions");
        assert.ok(fnSource.includes('$("#contactHeadActions").innerHTML = "";'), "showExpertDetail must clear #contactHeadActions");
        assert.ok(!fnSource.includes("/materials"), "showExpertDetail must never request materials");
    });
});
