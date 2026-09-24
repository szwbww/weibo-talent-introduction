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
                elements: {},
                querySelector: () => null,
                querySelectorAll: () => []
            });
        }
        return store.get(id);
    }
    return { el, get: (id) => store.get(id) };
}

function createSandbox(blocks) {
    const store = createStore();
    const form = store.el("composeTemplateForm");
    form.subject = { value: "Professor ${expertFamilyName|Professor} - ${researchFields|Your Field}" };
    store.el("composeTemplateSubject").value = form.subject.value;
    const sandbox = {
        state: {
            composeTemplates: [],
            qaRules: [],
            replySnippets: [],
            composeTemplatePreviewExperts: [],
            composeTemplatePreviewAccounts: [],
            selectedComposeTemplateId: null,
            selectedSubjectSnippetId: null,
            previewDrawer: {
                targetId: "composeTemplate"
            }
        },
        composeTemplatePreviewRequestId: 0,
        $: (sel) => store.el(sel.replace(/^#/, "")),
        $$: (sel) => sel === "#composeTemplateBlocksList .compose-template-block-row" ? blocks : [],
        escapeHtml: (v) => String(v == null ? "" : v)
            .replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;").replaceAll("'", "&#39;"),
        composeBlockTypeLabels: {
            QA_RULE: "QA 规则",
            REPLY_SNIPPET: "回复片段",
            CUSTOM_TEXT: "自定义文本"
        },
        replySnippetTypeLabels: {
            greeting: "问候"
        },
        isPreviewDrawerOpen: () => true,
        isComposeTemplatePreviewTarget: () => true,
        refreshVariableEditors: async () => {},
        updatePreviewCoverage: () => {},
        updatePreviewVariantSwitcher: () => {},
        api: async () => ({
            subject: "Professor Professor - Your Field",
            body: "Dear Professor, from Chen Jingjing",
            blocks: [{ blockOrder: 0, blockType: "CUSTOM_TEXT", included: true }],
            fallbackKeys: [],
            toEmail: "ada@mit.edu",
            variables: [
                { key: "senderName", label: "发件人姓名", value: "Chen Jingjing", filled: true, usedFallback: false }
            ]
        })
    };
    vm.createContext(sandbox);
    [
        "replySnippetDisplayLabel",
        "subjectSnippetLabel",
        "subjectSnippetIsEligible",
        "findSubjectSnippet",
        "subjectSnippetStatus",
        "collectComposeTemplateSubject",
        "placeholderDefaultFallback",
        "composeTemplatePreviewExpertLabel",
        "composeTemplatePreviewAccountLabel",
        "findComposeTemplatePreviewOption",
        "renderComposeTemplateBlockRows",
        "composeTemplateBlockRowHtml",
        "collectComposeTemplateBlocksFromForm",
        "collectComposeTemplatePreviewContext",
        "collectComposeTemplatePreviewSampleText",
        "refreshComposeTemplatePreview",
        "renderComposeTemplatePreviewHtml",
        "renderComposeTemplatePreviewVariableRows",
        "renderComposeTemplatePreviewInDrawer",
        "renderServerComposeTemplatePreview",
        "randomComposeTemplatePreviewExpert",
        "updatePreviewVariantSwitcher",
        "shouldDockPreviewInComposeTemplate"
    ].forEach((name) => vm.runInContext(extractFn(name), sandbox));
    sandbox.__store = store;
    return sandbox;
}

function customTextRow(text) {
    return {
        querySelector(selector) {
            if (selector === '[data-field="blockType"]') return { value: "CUSTOM_TEXT" };
            if (selector === '[data-field="refId"]') return null;
            if (selector === '[data-field="customText"]') return { value: text };
            return null;
        }
    };
}

const indexHtmlPath = path.join(__dirname, "..", "..", "main", "resources", "static", "index.html");
const indexHtmlSource = fs.readFileSync(indexHtmlPath, "utf-8");

/** Keys `/api/qa/template-variables-meta` returns today (MailPlaceholderService.VARIABLE_LABELS). */
const VARIABLE_META_KEYS = [
    "senderEmail", "senderName", "senderTitle", "teamName", "countryName",
    "expertName", "expertFamilyName", "researchFields", "institution", "keyword",
    "expertCountry", "employment", "hIndex", "worksCount", "lastPublicationYear",
    "degree", "recentWorkTitle", "patentTitle", "primaryResearchField",
    "pendingExpertMaterials", "unsubscribeUrl"
];

function createVarEditorSandbox() {
    const textareas = {};
    const expertKeys = new Set([
        "expertName", "expertFamilyName", "researchFields", "institution", "keyword",
        "expertCountry", "employment", "hIndex", "worksCount", "lastPublicationYear",
        "degree", "recentWorkTitle", "patentTitle", "primaryResearchField"
    ]);
    const sandbox = {
        state: {
            variableMeta: VARIABLE_META_KEYS.map((key) => ({
                key,
                label: key,
                nullable: expertKeys.has(key),
                example: ""
            }))
        },
        EXPERT_VAR_KEY_SET: expertKeys,
        SENDER_VAR_KEY_SET: new Set([
            "senderEmail", "senderName", "senderTitle", "teamName", "countryName", "senderDisplayName"
        ]),
        document: {
            activeElement: null,
            getElementById: (id) => textareas[id] || null,
            querySelector: () => null,
            querySelectorAll: () => []
        },
        Event: function Event() {},
        escapeHtml: (v) => String(v == null ? "" : v)
            .replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;").replaceAll("'", "&#39;")
    };
    vm.createContext(sandbox);
    [
        "parsePlaceholderToken",
        "validatePlaceholderText",
        "brokenPlaceholderFragments",
        "isComposeTemplateVarTarget",
        "isReplySnippetVarTarget",
        "replySnippetPlaceholderWarning",
        "placeholderDefaultFallback",
        "renderVarChipButtons",
        "renderVarInsertMenuContent",
        "resolveVarTextarea",
        "rememberVarSelection",
        "resolveVarInsertRange",
        "insertVarAtCursor",
        "updateVarValidationForTarget",
        "bindVarChipBar"
    ].forEach((name) => vm.runInContext(extractFn(name), sandbox));

    sandbox.__textarea = (id, value) => {
        const textarea = {
            id,
            value,
            selectionStart: value.length,
            selectionEnd: value.length,
            dataset: {},
            closest: () => null,
            focus() {},
            dispatchEvent() {}
        };
        textareas[id] = textarea;
        return textarea;
    };
    sandbox.__chip = (targetId, key, nullable, fallback) => {
        const listeners = {};
        return {
            dataset: {
                varTarget: targetId,
                varKey: key,
                varNullable: nullable ? "true" : "false",
                varFallback: fallback || ""
            },
            addEventListener(type, fn) {
                (listeners[type] = listeners[type] || []).push(fn);
            },
            click() {
                (listeners.click || []).forEach((fn) => fn());
            }
        };
    };
    return sandbox;
}

describe("compose template server preview", () => {
    it("docks compose template preview inside the editor", () => {
        const sb = createSandbox([]);

        assert.equal(sb.shouldDockPreviewInComposeTemplate("composeTemplate", false), true);
        assert.equal(sb.shouldDockPreviewInComposeTemplate("qaRuleReplyBody", false), false);
        assert.equal(sb.shouldDockPreviewInComposeTemplate("composeTemplate", true), false);
    });

    it("renders server preview response in drawer", () => {
        const sb = createSandbox([customTextRow("Dear ${expertFamilyName|Professor}, from ${senderName}")]);
        sb.renderComposeTemplatePreviewInDrawer({
            subject: "Professor Professor - Your Field",
            body: "Dear Professor, from Chen Jingjing",
            blocks: [{ blockOrder: 0, blockType: "CUSTOM_TEXT", included: true }],
            fallbackKeys: [],
            toEmail: "ada@mit.edu",
            variables: []
        });

        assert.equal(sb.__store.get("previewMailTo").textContent, "ada@mit.edu");
        assert.equal(sb.__store.get("previewMailSubject").textContent, "Professor Professor - Your Field");
        assert.equal(sb.__store.get("previewMailBody").textContent, "Dear Professor, from Chen Jingjing");
    });

    it("strict placeholder mode shows skipped blocks from server", () => {
        const sb = createSandbox([
            customTextRow("Visible ${senderName}"),
            customTextRow("Hidden ${researchFields}")
        ]);
        sb.renderComposeTemplatePreviewInDrawer({
            subject: "Subject",
            body: "Visible Chen Jingjing",
            blocks: [
                { blockOrder: 0, blockType: "CUSTOM_TEXT", included: true },
                { blockOrder: 1, blockType: "CUSTOM_TEXT", included: false, skipReason: "存在未满足占位符" }
            ],
            fallbackKeys: ["researchFields"],
            toEmail: "expert@example.com",
            variables: []
        });

        assert.equal(sb.__store.get("previewMailBody").textContent, "Visible Chen Jingjing");
        assert.ok(sb.__store.get("previewComposeSkipped").textContent.includes("已跳过 1 段"));
    });

    it("refresh calls preview-draft endpoint", async () => {
        const sb = createSandbox([customTextRow("To ${expertName}")]);
        let called = false;
        let requestPayload = null;
        sb.api = async (url, options) => {
            if (url === "/api/compose-templates/preview-draft") {
                called = true;
                requestPayload = JSON.parse(options.body);
                return {
                    subject: "Subject",
                    body: "To Ada Smith",
                    blocks: [],
                    fallbackKeys: [],
                    toEmail: "ada@mit.edu",
                    variables: []
                };
            }
            throw new Error("unexpected url: " + url);
        };

        await sb.refreshComposeTemplatePreview();

        assert.equal(requestPayload.subjectSnippetId, null);
        assert.equal(Object.hasOwn(requestPayload, "variantIndex"), false);
        assert.equal(called, true);
        assert.equal(sb.__store.get("previewMailBody").textContent, "To Ada Smith");
    });

    it("sends the referenced snippet ID and source text for draft preview", async () => {
        const sb = createSandbox([customTextRow("Body")]);
        sb.state.replySnippets = [{ id: 44, name: "Intro topic", content: "Source topic", enabled: true }];
        sb.state.selectedSubjectSnippetId = 44;
        sb.__store.get("composeTemplateSubject").value = "Source topic";
        let payload = null;
        sb.api = async (_url, options) => {
            payload = JSON.parse(options.body);
            return { subject: "Source topic", body: "Body", blocks: [], fallbackKeys: [], toEmail: "ada@mit.edu", variables: [] };
        };
        await sb.refreshComposeTemplatePreview();
        assert.equal(payload.subject, "Source topic");
        assert.equal(payload.subjectSnippetId, 44);
        assert.equal(Object.hasOwn(payload, "variantIndex"), false);
    });

    it("random sample uses preview random-expert endpoint", async () => {
        const sb = createSandbox([customTextRow("Dear ${expertName}")]);
        const calls = [];
        let previewPayload = null;
        sb.api = async (url, options) => {
            calls.push(url);
            if (url === "/api/qa/preview/random-expert") {
                return {
                    expert: {
                        orcidId: "0000-0001",
                        displayName: "Ada Smith",
                        email: "ada@mit.edu",
                        indexLevel: "CANDIDATE"
                    },
                    matchCount: 1,
                    totalCount: 10,
                    error: null
                };
            }
            if (url === "/api/compose-templates/preview-draft") {
                previewPayload = JSON.parse(options.body);
                return {
                    subject: "Subject",
                    body: "Dear Ada Smith",
                    blocks: [],
                    fallbackKeys: [],
                    toEmail: "ada@mit.edu",
                    variables: []
                };
            }
            throw new Error("unexpected url: " + url);
        };
        sb.showStatus = () => {};

        await sb.randomComposeTemplatePreviewExpert();

        assert.ok(calls.includes("/api/qa/preview/random-expert"));
        assert.equal(sb.__store.get("previewComposeExpertInput").value, "Ada Smith <ada@mit.edu>");
        assert.equal(previewPayload.orcidId, "0000-0001");
        assert.equal(previewPayload.expertEmail, "ada@mit.edu");
    });

    it("compose block editor omits QA_RULE option and defaults to CUSTOM_TEXT", () => {
        const sb = createSandbox([]);
        const defaultRow = sb.composeTemplateBlockRowHtml(0, {});
        assert.ok(!defaultRow.includes('<option value="QA_RULE">'));
        assert.ok(defaultRow.includes('<option value="CUSTOM_TEXT" selected'));
        assert.ok(defaultRow.includes('data-field="customText"'));

        const blocks = sb.collectComposeTemplateBlocksFromForm();
        assert.equal(blocks.length, 0);
    });

    it("allows the last compose block to be removed without recreating an empty block", () => {
        const sb = createSandbox([]);

        sb.renderComposeTemplateBlockRows([]);

        assert.equal(sb.__store.get("composeTemplateBlocksList").innerHTML, "");
    });

    it("compose block row keeps QA sample text branch for legacy preview", () => {
        const sb = createSandbox([]);
        sb.state.qaRules = [{ id: 1, replyBody: "Legacy QA body" }];
        const row = {
            querySelector(selector) {
                if (selector === '[data-field="blockType"]') return { value: "QA_RULE" };
                if (selector === '[data-field="refId"]') return { value: "1" };
                return null;
            }
        };
        sb.$$ = () => [row];
        assert.equal(
            sb.collectComposeTemplatePreviewSampleText(),
            'Professor ${expertFamilyName|Professor} - ${researchFields|Your Field}\nLegacy QA body'
        );
    });
});

describe("compose template variable editor (I-1/I-4)", () => {
    it("relaxes bare tokens for the template editor but keeps QA rules strict", () => {
        const sb = createVarEditorSandbox();

        // template editor: `${key}` (the mandatory form) and `${key|默认值}` are both legal
        assert.equal(sb.validatePlaceholderText("Hello ${institution}", { lenient: true }).valid, true);
        assert.equal(
            sb.validatePlaceholderText("Topic ${primaryResearchField|your research area}", { lenient: true }).valid,
            true
        );
        // unknown key, blank default and broken token are rejected even in the template editor
        assert.equal(sb.validatePlaceholderText("${bogus}", { lenient: true }).valid, false);
        assert.equal(sb.validatePlaceholderText("${institution|}", { lenient: true }).valid, false);
        assert.equal(sb.validatePlaceholderText("${institution|   }", { lenient: true }).valid, false);
        const broken = sb.validatePlaceholderText("Hello ${institution", { lenient: true });
        assert.equal(broken.valid, false);
        assert.deepEqual(broken.violations, ["${institution"]);

        // QA rule editors keep the pre-existing severity
        assert.equal(sb.validatePlaceholderText("Hello ${institution}").valid, false);
        assert.equal(sb.validatePlaceholderText("Hello ${institution|your institution}").valid, true);
        assert.equal(sb.validatePlaceholderText("Hello ${senderName}").valid, true);
    });

    it("inserts a bare token in the template editor and the defaulted token elsewhere", () => {
        const sb = createVarEditorSandbox();
        const subject = sb.__textarea("composeTemplateSubject", "Hello ");
        const qaBody = sb.__textarea("qaRuleAnswerBody", "Dear expert,");
        const chips = [
            { target: "composeTemplateSubject", nullable: true, fallback: "your institution" },
            { target: "qaRuleAnswerBody", nullable: true, fallback: "your institution" }
        ].map((spec) => sb.__chip(spec.target, "institution", spec.nullable, spec.fallback));

        sb.bindVarChipBar({ querySelectorAll: () => chips });
        chips.forEach((chip) => chip.click());

        assert.equal(subject.value, "Hello ${institution}");
        assert.equal(qaBody.value, "Dear expert,${institution|your institution}");
    });

    it("every variable-meta key reaches the insert menu, including institution/primaryResearchField", () => {
        const sb = createVarEditorSandbox();
        const metas = sb.state.variableMeta;

        assert.ok(metas.some((meta) => meta.key === "institution"));
        assert.ok(metas.some((meta) => meta.key === "primaryResearchField"));

        const html = sb.renderVarInsertMenuContent("composeTemplateSubject");

        metas.forEach((meta) => {
            assert.ok(html.includes(`data-var-key="${meta.key}"`), `${meta.key} must be in the menu`);
        });
        assert.equal((html.match(/data-var-key="/g) || []).length, metas.length);
    });

    it("index.html documents the mandatory form of a template placeholder", () => {
        assert.ok(
            indexHtmlSource.includes("${变量名} 为必填变量，缺值会阻止发送"),
            "editor hint must state that `${变量名}` is mandatory"
        );
        assert.ok(indexHtmlSource.includes("${变量名|默认值} 缺值时使用默认值"));
    });

    it("reopening the editor keeps block order and custom text", () => {
        const sb = createSandbox([]);
        const texts = ["First ${institution}", "Second"];

        sb.renderComposeTemplateBlockRows([
            { blockOrder: 0, blockType: "CUSTOM_TEXT", customText: texts[0] },
            { blockOrder: 1, blockType: "CUSTOM_TEXT", customText: texts[1] }
        ]);
        const html = sb.__store.get("composeTemplateBlocksList").innerHTML;
        assert.deepEqual(
            [...html.matchAll(/data-block-index="(\d+)"/g)].map((match) => Number(match[1])),
            [0, 1]
        );
        assert.ok(html.indexOf("First") < html.indexOf("Second"), "rendered order must follow blockOrder");

        sb.$$ = () => texts.map((text) => customTextRow(text));
        const blocks = sb.collectComposeTemplateBlocksFromForm();
        assert.deepEqual(blocks.map((block) => block.blockOrder), [0, 1]);
        assert.deepEqual(blocks.map((block) => block.customText), texts);
    });
});


describe("reply snippet placeholder validation", () => {
    for (const target of ["replySnippetContent", "replySnippetVariant-0"]) {
        it(`${target} inserts bare variables and warns without disabling save`, () => {
            const sb = createVarEditorSandbox();
            const input = sb.__textarea(target, "Topic ");
            const hint = sb.__textarea(`varHint-${target}`, "");
            const submit = { disabled: false };
            input.closest = () => ({ querySelector: () => submit });
            const chip = sb.__chip(target, "primaryResearchField", true, "");
            sb.bindVarChipBar({ querySelectorAll: () => [chip] });
            chip.click();
            assert.equal(input.value, "Topic ${primaryResearchField}");
            assert.equal(submit.disabled, false);
            assert.equal(hint.hidden, false);
            assert.match(hint.textContent, /未设置默认值.*发送门槛过滤/);
            assert.equal(hint.className, "var-validation-hint");
            input.value = "Topic ${primaryResearchField|research}";
            assert.equal(sb.updateVarValidationForTarget(target, input), true);
            assert.equal(hint.hidden, true);
            for (const invalid of ["${bogus}", "${primaryResearchField", "${primaryResearchField|}"]) {
                input.value = invalid;
                assert.equal(sb.updateVarValidationForTarget(target, input), false);
                assert.equal(submit.disabled, true);
            }
        });
    }
});
