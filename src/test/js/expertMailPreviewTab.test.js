const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const staticDir = path.join(__dirname, "..", "..", "main", "resources", "static");
const appJsPath = path.join(staticDir, "app.js");
const appJsSource = fs.readFileSync(appJsPath, "utf-8");
const stylesCssSource = fs.readFileSync(path.join(staticDir, "styles.css"), "utf-8");

function extractFn(name) {
    const regex = new RegExp("(?:async\\s+)?function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}");
    const match = appJsSource.match(regex);
    if (!match) throw new Error("Could not find " + name + " in app.js");
    return match[0];
}

function createStore() {
    const store = new Map();
    function el(id) {
        if (!store.has(id)) {
            store.set(id, {
                id,
                innerHTML: "",
                textContent: "",
                value: "",
                checked: false,
                hidden: false,
                querySelector: () => null,
                querySelectorAll: () => []
            });
        }
        return store.get(id);
    }
    return { el, get: (id) => store.get(id) };
}

function createSandbox(overrides = {}) {
    const store = createStore();
    const sandbox = {
        state: {
            composeTemplates: [],
            contacts: [],
            previewDrawer: {
                targetId: null,
                contactId: null,
                orcidId: null,
                expertEmail: null,
                variantIndex: 0,
                variantPoolSize: 1
            }
        },
        expertMailPreviewRequestId: 0,
        $: (sel) => store.el(sel.replace(/^#/, "")),
        escapeHtml: (v) => String(v == null ? "" : v)
            .replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;").replaceAll("'", "&#39;"),
        api: async () => ({
            subject: "Subject",
            body: "Body",
            blocks: [],
            fallbackKeys: [],
            toEmail: "ada@mit.edu",
            variables: []
        }),
        showStatus: () => {},
        setView: () => {},
        switchMailTemplatesSubTab: () => {},
        openComposeTemplateEditor: () => {},
        loadComposeTemplatePreviewOptions: async () => {},
        openComposeTemplatePreview: async () => {},
        loadComposeTemplates: async () => {},
        document: {
            createElement: (tag) => ({ tagName: tag, className: "", textContent: "", remove() {} })
        },
        ...overrides
    };
    vm.createContext(sandbox);
    return { sandbox, store };
}

function makePanel() {
    const blocksPills = [];
    const element = () => ({
        value: "",
        textContent: "",
        querySelectorAll: () => [],
        appendChild: () => {},
        remove: () => {}
    });
    const els = {
        '[data-role="mail-preview-template"]': element(),
        ".expert-mail-preview-subject": element(),
        '[data-role="mail-preview-body"]': element(),
        '[data-role="mail-preview-to"]': element(),
        ".expert-mail-preview-meta": element(),
        '[data-role="mail-preview-blocks"]': {
            textContent: "",
            appendChild: (child) => blocksPills.push(child)
        }
    };
    return {
        dataset: {},
        innerHTML: "",
        querySelector: (sel) => els[sel] || null,
        blocksPills
    };
}

function makeJumpSandbox(order, templates) {
    const { sandbox, store } = createSandbox();
    sandbox.loadComposeTemplates = async () => {
        order.push("loadComposeTemplates");
        sandbox.state.composeTemplates = templates;
    };
    sandbox.setView = (view) => order.push("setView:" + view);
    sandbox.switchMailTemplatesSubTab = (tab) => order.push("switchMailTemplatesSubTab:" + tab);
    sandbox.openComposeTemplateEditor = (t) => order.push("openComposeTemplateEditor:" + (t && t.templateName));
    sandbox.loadComposeTemplatePreviewOptions = async () => order.push("loadComposeTemplatePreviewOptions");
    sandbox.openComposeTemplatePreview = async () => order.push("openComposeTemplatePreview");
    sandbox.showStatus = (message, type) => order.push("showStatus:" + type);
    return { sandbox, store };
}

describe("expert detail mail preview tab", () => {
    it("renderDetailSubTabs renders exactly 4 tabs, mail-preview last, first 3 unchanged (N-4)", () => {
        const { sandbox } = createSandbox();
        vm.runInContext(extractFn("renderDetailSubTabs"), sandbox);
        const html = vm.runInContext("renderDetailSubTabs()", sandbox);

        assert.equal((html.match(/data-sub-tab="/g) || []).length, 4);
        assert.ok(html.includes('data-sub-tab="mail-preview"'));
        assert.ok(html.includes("邮件预览"));
        assert.ok(html.includes('data-sub-tab="academic"'));
        assert.ok(html.includes("学术档案"));
        assert.ok(html.includes('data-sub-tab="contact"'));
        assert.ok(html.includes("联系详情"));
        assert.ok(html.includes('data-sub-tab="template"'));
        assert.ok(html.includes("模板预览"));
    });

    it("renderExpertMailPreview sends strictPlaceholders=false (I-6)", async () => {
        let captured = null;
        const { sandbox } = createSandbox({
            api: async (url, options) => {
                captured = JSON.parse(options.body);
                return { subject: "S", body: "B", blocks: [], fallbackKeys: [], toEmail: "e@x.com", variables: [] };
            }
        });
        sandbox.state.composeTemplates = [{ id: 1, templateName: "T1", enabled: true, subject: "Subj", blocks: [] }];
        vm.runInContext(extractFn("javaStringHashCode"), sandbox);
        vm.runInContext(extractFn("renderExpertMailPreview"), sandbox);
        const panel = makePanel();
        panel.querySelector('[data-role="mail-preview-template"]').value = "1";

        await sandbox.renderExpertMailPreview(panel, "0000-0001");

        assert.ok(captured);
        assert.equal(captured.strictPlaceholders, false);
    });

    it("renderExpertMailPreview maps blocks to exactly 4 explicit fields (I-1)", async () => {
        let captured = null;
        const { sandbox } = createSandbox({
            api: async (url, options) => {
                captured = JSON.parse(options.body);
                return { subject: "S", body: "B", blocks: [], fallbackKeys: [], toEmail: "e@x.com", variables: [] };
            }
        });
        sandbox.state.composeTemplates = [{
            id: 1,
            templateName: "T1",
            enabled: true,
            subject: "Subj",
            blocks: [
                { id: 99, blockOrder: 0, blockType: "CUSTOM_TEXT", refId: null, refDisplayName: "X", customText: "Hello" },
                { id: 98, blockOrder: 1, blockType: "REPLY_SNIPPET", refId: 7, refDisplayName: "尊语 #1", customText: null }
            ]
        }];
        vm.runInContext(extractFn("javaStringHashCode"), sandbox);
        vm.runInContext(extractFn("renderExpertMailPreview"), sandbox);
        const panel = makePanel();
        panel.querySelector('[data-role="mail-preview-template"]').value = "1";

        await sandbox.renderExpertMailPreview(panel, "0000-0001");

        assert.equal(captured.blocks.length, 2);
        for (const block of captured.blocks) {
            assert.deepEqual(Object.keys(block).sort(), ["blockOrder", "blockType", "customText", "refId"]);
            assert.ok(!("id" in block));
            assert.ok(!("refDisplayName" in block));
        }
        assert.equal(captured.blocks[0].customText, "Hello");
        assert.equal(captured.blocks[1].refId, 7);
    });

    it("renderExpertMailPreview only calls POST /api/compose-templates/preview-draft (I-1)", async () => {
        const calls = [];
        const { sandbox } = createSandbox({
            api: async (url, options) => {
                calls.push({ url, method: options?.method || "GET" });
                return { subject: "S", body: "B", blocks: [], fallbackKeys: [], toEmail: "e@x.com", variables: [] };
            }
        });
        sandbox.state.composeTemplates = [{ id: 1, templateName: "T1", enabled: true, subject: "Subj", blocks: [] }];
        vm.runInContext(extractFn("javaStringHashCode"), sandbox);
        vm.runInContext(extractFn("renderExpertMailPreview"), sandbox);
        const panel = makePanel();
        panel.querySelector('[data-role="mail-preview-template"]').value = "1";

        await sandbox.renderExpertMailPreview(panel, "0000-0001");

        assert.equal(calls.length, 1);
        assert.equal(calls[0].url, "/api/compose-templates/preview-draft");
        assert.equal(calls[0].method, "POST");

        const fnSource = extractFn("renderExpertMailPreview");
        assert.ok(!/compose-templates\/\$\{[^}]*\}\/preview/.test(fnSource));
        assert.ok(!/\/api\/compose-templates\/[^"']*\/preview"/.test(fnSource));
    });

    it("renderExpertMailPreview exposes the server-provided refDisplayName for preview blocks (V-1)", async () => {
        const { sandbox } = createSandbox({
            api: async () => ({
                subject: "S",
                body: "B",
                blocks: [
                    { blockOrder: 0, blockType: "REPLY_SNIPPET", refId: 7, refDisplayName: "尊语 #1", included: true }
                ],
                fallbackKeys: [],
                toEmail: "e@x.com",
                variables: []
            })
        });
        sandbox.state.composeTemplates = [{ id: 1, templateName: "T1", enabled: true, subject: "Subj", blocks: [] }];
        vm.runInContext(extractFn("javaStringHashCode"), sandbox);
        vm.runInContext(extractFn("renderExpertMailPreview"), sandbox);
        const panel = makePanel();
        panel.querySelector('[data-role="mail-preview-template"]').value = "1";

        await sandbox.renderExpertMailPreview(panel, "0000-0001");

        assert.equal(panel.blocksPills.length, 1);
        assert.equal(panel.blocksPills[0].className, "compose-block-pill");
        assert.equal(panel.blocksPills[0].textContent, "尊语 #1");
    });

    it("renderExpertMailPreview derives variantIndex from the trimmed ORCID via Java hashCode (V-2)", async () => {
        let captured = null;
        const { sandbox } = createSandbox({
            api: async (url, options) => {
                captured = JSON.parse(options.body);
                return { subject: "S", body: "B", blocks: [], fallbackKeys: [], toEmail: "e@x.com", variables: [] };
            }
        });
        sandbox.state.composeTemplates = [{ id: 1, templateName: "T1", enabled: true, subject: "Subj", blocks: [] }];
        vm.runInContext(extractFn("javaStringHashCode"), sandbox);
        vm.runInContext(extractFn("renderExpertMailPreview"), sandbox);
        const panel = makePanel();
        panel.querySelector('[data-role="mail-preview-template"]').value = "1";

        await sandbox.renderExpertMailPreview(panel, "0000-0002");

        assert.equal(captured.variantIndex, -2035179089);
    });

    it("openTemplateEditorForExpert loads templates before setView; no setView when template missing (I-3)", async () => {
        const order = [];
        const templates = [{ id: 5, templateName: "T5", enabled: true, subject: "Subj", blocks: [] }];
        const { sandbox } = makeJumpSandbox(order, templates);
        sandbox.state.contacts = [{ orcidId: "0000-0001", displayName: "Ada", email: "ada@mit.edu", contactId: 9 }];
        vm.runInContext(extractFn("ensureComposeTemplatesLoaded"), sandbox);
        vm.runInContext(extractFn("composeTemplatePreviewExpertLabel"), sandbox);
        vm.runInContext(extractFn("openTemplateEditorForExpert"), sandbox);

        await sandbox.openTemplateEditorForExpert(5, "0000-0001");

        assert.deepEqual(order, [
            "loadComposeTemplates",
            "setView:mail-templates",
            "switchMailTemplatesSubTab:compose-templates",
            "openComposeTemplateEditor:T5",
            "loadComposeTemplatePreviewOptions",
            "openComposeTemplatePreview"
        ]);

        const order2 = [];
        const { sandbox: sb2 } = makeJumpSandbox(order2, templates);
        vm.runInContext(extractFn("ensureComposeTemplatesLoaded"), sb2);
        vm.runInContext(extractFn("composeTemplatePreviewExpertLabel"), sb2);
        vm.runInContext(extractFn("openTemplateEditorForExpert"), sb2);

        await sb2.openTemplateEditorForExpert(999, "0000-0001");

        assert.ok(!order2.some((step) => step.startsWith("setView")));
        assert.ok(order2.includes("showStatus:error"));
    });

    it("openTemplateEditorForExpert double-writes input value and previewDrawer state (I-4)", async () => {
        const order = [];
        const templates = [{ id: 5, templateName: "T5", enabled: true, subject: "Subj", blocks: [] }];
        const { sandbox, store } = makeJumpSandbox(order, templates);
        sandbox.state.contacts = [{ orcidId: "0000-0001", displayName: "Ada Smith", email: "ada@mit.edu", contactId: 9 }];
        vm.runInContext(extractFn("ensureComposeTemplatesLoaded"), sandbox);
        vm.runInContext(extractFn("composeTemplatePreviewExpertLabel"), sandbox);
        vm.runInContext(extractFn("openTemplateEditorForExpert"), sandbox);

        await sandbox.openTemplateEditorForExpert(5, "0000-0001");

        const expectedLabel = sandbox.composeTemplatePreviewExpertLabel({
            displayName: "Ada Smith",
            expertEmail: "ada@mit.edu"
        });
        assert.equal(expectedLabel, "Ada Smith <ada@mit.edu>");
        assert.equal(store.get("previewComposeExpertInput").value, expectedLabel);
        assert.equal(sandbox.state.previewDrawer.orcidId, "0000-0001");
        assert.equal(sandbox.state.previewDrawer.contactId, 9);
        assert.equal(sandbox.state.previewDrawer.expertEmail, "ada@mit.edu");
    });

    it("renders the mail-preview panel in both detail render functions (I-5)", () => {
        const mailPreviewTotal = (appJsSource.match(/data-panel="mail-preview"/g) || []).length;
        const templateTotal = (appJsSource.match(/data-panel="template"/g) || []).length;
        const mailPreviewPanelDivs = (appJsSource.match(/<div class="detail-tab-panel" data-panel="mail-preview"/g) || []).length;

        assert.equal(mailPreviewPanelDivs, 2);
        assert.equal(mailPreviewTotal, templateTotal);
    });

    it("declares S-3/S-4 CSS rules and panel DOM without inline styles", () => {
        assert.ok(stylesCssSource.includes(".expert-mail-preview-toolbar"));
        assert.ok(stylesCssSource.includes(".expert-mail-preview-subject"));
        assert.ok(stylesCssSource.includes(".expert-mail-preview-meta"));

        const panelFn = extractFn("loadExpertMailPreview")
            + "\n" + extractFn("renderExpertMailPreview")
            + "\n" + extractFn("openTemplateEditorForExpert");
        assert.ok(panelFn.includes('class="pre" data-role="mail-preview-body"'));
        assert.ok(panelFn.includes('data-role="mail-preview-template"'));
        assert.ok(panelFn.includes("在模板编辑器中打开"));
        assert.ok(!panelFn.includes('style="'));
    });
});
