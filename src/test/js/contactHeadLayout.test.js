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
