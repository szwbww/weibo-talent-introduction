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

function extractConst(name) {
    const regex = new RegExp("const\\s+" + name + "\\s*=\\s*\\d+;");
    const match = appJsSource.match(regex);
    if (!match) throw new Error("Could not find const " + name + " in app.js");
    return match[0];
}

function createBatchCollectSandbox() {
    const store = new Map();
    function el(id) {
        if (!store.has(id)) {
            store.set(id, { id, value: "" });
        }
        return store.get(id);
    }

    const sandbox = {
        $: (sel) => el(sel.replace(/^#/, "")),
        state: { contactsTotalHits: 0 },
        api: async () => ({ experts: [] }),
        URLSearchParams
    };

    vm.createContext(sandbox);
    vm.runInContext(extractConst("ES_MAX_RESULT_WINDOW"), sandbox);
    vm.runInContext(extractConst("ES_PAGE_SIZE_MAX"), sandbox);
    vm.runInContext(extractFn("collectBatchMailContactIds"), sandbox);
    return sandbox;
}

function createTagFetchSandbox() {
    const sandbox = {
        api: async () => ({ tags: [] }),
        URLSearchParams
    };
    vm.createContext(sandbox);
    vm.runInContext(extractFn("fetchExpertTagsFromEs"), sandbox);
    vm.runInContext(extractFn("renderExpertTagEditor"), sandbox);
    vm.runInContext(`
        const expertTagLabels = {
            auto_promoted: "自动晋升",
            verified: "已验证",
            discovered: "新发现",
            "承诺回复材料": "承诺回复材料"
        };
        function escapeHtml(v) {
            return String(v == null ? "" : v);
        }
    `, sandbox);
    return sandbox;
}

function createMailboxExpertTagSandbox() {
    const sandbox = {
        expertTagLabels: {
            discovered: "新发现",
            "承诺回复材料": "承诺回复材料"
        },
        escapeHtml: (v) => String(v == null ? "" : v)
    };
    vm.createContext(sandbox);
    vm.runInContext(extractFn("renderExpertTagEditor"), sandbox);
    vm.runInContext(extractFn("renderMailboxExpertTagEditor"), sandbox);
    return sandbox;
}

describe("collectBatchMailContactIds pagination (P1-1)", () => {
    it("requests from=0,size=1000 and from=1000,size=201 when totalHits=1201", async () => {
        const sb = createBatchCollectSandbox();
        sb.$("#expertIndexLevel").value = "CANDIDATE";
        sb.$("#contactStatusFilter").value = "";
        sb.$("#expertTagFilter").value = "承诺回复材料";
        sb.$("#expertEmailDomainFilter").value = "";
        sb.$("#expertRegionFilter").value = "";
        sb.state.contactsTotalHits = 1201;

        const requests = [];
        sb.api = async (url) => {
            requests.push(url);
            if (url.includes("from=0")) {
                return {
                    experts: Array.from({ length: 1000 }, (_, i) => ({
                        contactId: i % 2 === 0 ? i + 1 : null
                    }))
                };
            }
            if (url.includes("from=1000")) {
                return {
                    experts: Array.from({ length: 201 }, (_, i) => ({
                        contactId: (1000 + i) % 2 === 0 ? 1000 + i + 1 : null
                    }))
                };
            }
            throw new Error("unexpected url: " + url);
        };

        const contactIds = await sb.collectBatchMailContactIds();

        assert.strictEqual(requests.length, 2);
        assert.ok(requests[0].includes("from=0"));
        assert.ok(requests[0].includes("size=1000"));
        assert.ok(requests[1].includes("from=1000"));
        assert.ok(requests[1].includes("size=201"));
        assert.ok(requests[0].includes("tag="));
        assert.strictEqual(contactIds.length, 601);
    });

    it("throws when totalHits exceeds ES max_result_window", async () => {
        const sb = createBatchCollectSandbox();
        sb.$("#expertIndexLevel").value = "CANDIDATE";
        sb.$("#contactStatusFilter").value = "";
        sb.$("#expertTagFilter").value = "";
        sb.$("#expertEmailDomainFilter").value = "";
        sb.$("#expertRegionFilter").value = "";
        sb.state.contactsTotalHits = 10001;

        await assert.rejects(
            () => sb.collectBatchMailContactIds(),
            (err) => err.message.includes("10001") && err.message.includes("10000")
        );
    });

    it("throws when a page fetch fails", async () => {
        const sb = createBatchCollectSandbox();
        sb.$("#expertIndexLevel").value = "CANDIDATE";
        sb.$("#contactStatusFilter").value = "";
        sb.$("#expertTagFilter").value = "";
        sb.$("#expertEmailDomainFilter").value = "";
        sb.$("#expertRegionFilter").value = "";
        sb.state.contactsTotalHits = 1201;

        sb.api = async (url) => {
            if (url.includes("from=1000")) {
                throw new Error("network down");
            }
            return { experts: [{ contactId: 1 }] };
        };

        await assert.rejects(
            () => sb.collectBatchMailContactIds(),
            (err) => err.message.includes("已中止批量发送")
        );
    });
});

describe("fetchExpertTagsFromEs authoritative tags (P1-2)", () => {
    it("loads tags from /api/experts/profile instead of list cache", async () => {
        const sb = createTagFetchSandbox();
        let profileUrl = "";
        sb.api = async (url) => {
            profileUrl = url;
            return { orcidId: "0000-0001", tags: ["承诺回复材料"] };
        };

        const tags = await sb.fetchExpertTagsFromEs("0000-0001", "CANDIDATE");

        assert.ok(profileUrl.includes("/api/experts/profile"));
        assert.ok(profileUrl.includes("orcidId=0000-0001"));
        assert.ok(profileUrl.includes("level=CANDIDATE"));
        assert.deepStrictEqual(tags, ["承诺回复材料"]);
    });

    it("renders ES tags in editor even when list cache has no tags", () => {
        const sb = createTagFetchSandbox();
        const html = sb.renderExpertTagEditor(["承诺回复材料"], "0000-0001", "CANDIDATE");
        assert.ok(html.includes("承诺回复材料"));
        assert.ok(html.includes('data-orcid="0000-0001"'));
    });

    it("can render a mailbox-scoped expert tag editor", () => {
        const sb = createTagFetchSandbox();
        const html = sb.renderExpertTagEditor(["discovered"], "0000-0002", "APPLICATION", "mailboxExpertTagEditor");
        assert.ok(html.includes('id="mailboxExpertTagEditor"'));
        assert.ok(html.includes('data-orcid="0000-0002"'));
        assert.ok(html.includes('data-level="APPLICATION"'));
    });

    it("renders mailbox expert tags from unmatched processing contact payload", () => {
        const sb = createMailboxExpertTagSandbox();
        const html = sb.renderMailboxExpertTagEditor(
            { orcidId: "0000-0002-4464-150X", currentIndexLevel: "CANDIDATE" },
            ["discovered"],
            "mailboxProcessingExpertTagEditor"
        );

        assert.ok(html.includes("专家标签"));
        assert.ok(html.includes('id="mailboxProcessingExpertTagEditor"'));
        assert.ok(html.includes('data-orcid="0000-0002-4464-150X"'));
        assert.ok(html.includes('data-level="CANDIDATE"'));
        assert.ok(html.includes("新发现"));
    });
});

function extractFnBalanced(name) {
    const startRe = new RegExp("(?:async\\s+)?function\\s+" + name + "\\s*\\(");
    const startMatch = startRe.exec(appJsSource);
    if (!startMatch) throw new Error("Could not find " + name + " in app.js");
    const braceStart = appJsSource.indexOf("{", startMatch.index);
    let depth = 0;
    let mode = "code";
    let templateExprDepth = 0;
    for (let i = braceStart; i < appJsSource.length; i++) {
        const c = appJsSource[i];
        const n = appJsSource[i + 1];
        if (mode === "code" || (mode === "templateExpr" && templateExprDepth > 0)) {
            if (mode === "code" || mode === "templateExpr") {
                if (c === "'" && mode === "code") { mode = "squote"; continue; }
                if (c === '"' && mode === "code") { mode = "dquote"; continue; }
                if (c === "`" && mode === "code") { mode = "template"; continue; }
                if (c === "/" && n === "/" && mode === "code") { mode = "linecomment"; i++; continue; }
                if (c === "/" && n === "*" && mode === "code") { mode = "blockcomment"; i++; continue; }
                if (c === "'" && mode === "templateExpr") { mode = "squoteInExpr"; continue; }
                if (c === '"' && mode === "templateExpr") { mode = "dquoteInExpr"; continue; }
                if (c === "`" && mode === "templateExpr") { mode = "templateInExpr"; continue; }
                if (c === "{") {
                    if (mode === "code") depth++;
                    else templateExprDepth++;
                } else if (c === "}") {
                    if (mode === "templateExpr") {
                        templateExprDepth--;
                        if (templateExprDepth === 0) mode = "template";
                    } else {
                        depth--;
                        if (depth === 0) return appJsSource.slice(startMatch.index, i + 1);
                    }
                }
            }
        } else if (mode === "squote" || mode === "squoteInExpr") {
            if (c === "\\") { i++; continue; }
            if (c === "'") mode = mode === "squote" ? "code" : "templateExpr";
        } else if (mode === "dquote" || mode === "dquoteInExpr") {
            if (c === "\\") { i++; continue; }
            if (c === '"') mode = mode === "dquote" ? "code" : "templateExpr";
        } else if (mode === "template" || mode === "templateInExpr") {
            if (c === "\\") { i++; continue; }
            if (c === "`") mode = mode === "template" ? "code" : "templateExpr";
            else if (c === "$" && n === "{") {
                i++;
                if (mode === "template") {
                    mode = "templateExpr";
                    templateExprDepth = 1;
                } else {
                    templateExprDepth++;
                }
            }
        } else if (mode === "linecomment") {
            if (c === "\n") mode = "code";
        } else if (mode === "blockcomment") {
            if (c === "*" && n === "/") { mode = "code"; i++; }
        }
    }
    throw new Error("Unbalanced braces for " + name);
}

function createMockEl(tagName, attrs = {}) {
    const el = {
        tagName: String(tagName).toUpperCase(),
        id: attrs.id || "",
        className: attrs.className || attrs.class || "",
        value: attrs.value || "",
        disabled: false,
        hidden: !!attrs.hidden,
        required: !!attrs.required,
        children: [],
        childNodes: [],
        options: [],
        _listeners: new Map(),
        _text: "",
        _html: "",
        parentElement: null,
        ownerDocument: null,
        get textContent() {
            if (this.children.length) {
                return this.children.map((c) => c.textContent).join("");
            }
            return this._text;
        },
        set textContent(v) {
            this._text = String(v == null ? "" : v);
            this._html = "";
            this.children = [];
            this.childNodes = [];
            if (this.tagName === "SELECT") this.options = [];
        },
        get innerHTML() {
            return this._html;
        },
        set innerHTML(html) {
            this._html = String(html == null ? "" : html);
            this._text = "";
            this.children = [];
            this.childNodes = [];
            if (this.tagName === "SELECT") this.options = [];
            if (this.ownerDocument) {
                this.ownerDocument._hydrate(this, this._html);
            }
        },
        appendChild(child) {
            child.parentElement = this;
            child.ownerDocument = this.ownerDocument;
            this.children.push(child);
            this.childNodes.push(child);
            if (this.tagName === "SELECT" && child.tagName === "OPTION") {
                this.options.push(child);
                if (!this.value) this.value = child.value;
            }
            if (child.id && this.ownerDocument) {
                this.ownerDocument._byId.set(child.id, child);
            }
            return child;
        },
        querySelector(sel) {
            return this.ownerDocument ? this.ownerDocument._queryFrom(this, sel) : null;
        },
        addEventListener(type, fn) {
            if (!this._listeners.has(type)) this._listeners.set(type, new Set());
            this._listeners.get(type).add(fn);
        },
        removeEventListener(type, fn) {
            this._listeners.get(type)?.delete(fn);
        },
        dispatchEvent(type, event = {}) {
            const list = [...(this._listeners.get(type) || [])];
            list.forEach((fn) => fn(event));
        },
        showModal() {
            this.open = true;
        },
        close() {
            this.open = false;
        }
    };
    if (attrs.id) el.id = attrs.id;
    return el;
}

function createBatchMailDialogSandbox() {
    const byId = new Map();

    function hydrate(container, html) {
        const re = /<([a-zA-Z0-9]+)([^>]*?)(\/?)>/g;
        let m;
        while ((m = re.exec(html)) !== null) {
            const tag = m[1];
            const attrStr = m[2] || "";
            const idMatch = attrStr.match(/\bid\s*=\s*"([^"]+)"/);
            if (!idMatch) continue;
            const id = idMatch[1];
            const classMatch = attrStr.match(/\bclass\s*=\s*"([^"]*)"/);
            const el = createMockEl(tag, {
                id,
                className: classMatch ? classMatch[1] : "",
                hidden: /\bhidden\b/.test(attrStr),
                required: /\brequired\b/.test(attrStr)
            });
            el.ownerDocument = doc;
            byId.set(id, el);
            container.children.push(el);
            container.childNodes.push(el);
        }
    }

    function queryFrom(root, sel) {
        const walk = (node, pred) => {
            if (pred(node)) return node;
            for (const child of node.children || []) {
                const found = walk(child, pred);
                if (found) return found;
            }
            return null;
        };
        if (sel === "button[type=submit]") {
            return walk(root, (n) => n.tagName === "BUTTON" && n.type === "submit");
        }
        if (sel === "[data-action='action-dialog-cancel']" || sel === '[data-action="action-dialog-cancel"]') {
            return walk(root, (n) => n.getAttribute && n.getAttribute("data-action") === "action-dialog-cancel"
                || n.dataset?.action === "action-dialog-cancel"
                || n["data-action"] === "action-dialog-cancel");
        }
        if (sel.startsWith("#")) {
            return byId.get(sel.slice(1)) || null;
        }
        return null;
    }

    const doc = {
        _byId: byId,
        _hydrate: hydrate,
        _queryFrom: queryFrom,
        getElementById(id) {
            return byId.get(id) || null;
        },
        createElement(tag) {
            const el = createMockEl(tag);
            el.ownerDocument = doc;
            return el;
        },
        querySelector(sel) {
            return queryFrom({ children: [...byId.values()] }, sel);
        }
    };

    const dialog = createMockEl("dialog", { id: "actionDialog" });
    dialog.ownerDocument = doc;
    const form = createMockEl("form", { id: "actionDialogForm" });
    form.ownerDocument = doc;
    const titleEl = createMockEl("h3", { id: "actionDialogTitle" });
    titleEl.ownerDocument = doc;
    const bodyEl = createMockEl("div", { id: "actionDialogBody" });
    bodyEl.ownerDocument = doc;
    const cancelBtn = createMockEl("button");
    cancelBtn.ownerDocument = doc;
    cancelBtn.type = "button";
    cancelBtn["data-action"] = "action-dialog-cancel";
    cancelBtn.dataset = { action: "action-dialog-cancel" };
    cancelBtn.getAttribute = (name) => (name === "data-action" ? "action-dialog-cancel" : null);
    const submitBtn = createMockEl("button");
    submitBtn.ownerDocument = doc;
    submitBtn.type = "submit";
    form.appendChild(titleEl);
    form.appendChild(bodyEl);
    form.appendChild(cancelBtn);
    form.appendChild(submitBtn);
    dialog.appendChild(form);

    byId.set("actionDialog", dialog);
    byId.set("actionDialogForm", form);
    byId.set("actionDialogTitle", titleEl);
    byId.set("actionDialogBody", bodyEl);

    const previewResolvers = new Map();
    const previewRejecters = new Map();
    const apiCalls = [];

    const sandbox = {
        document: doc,
        escapeHtml: (value) => String(value ?? "")
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#039;"),
        api: async (url) => {
            apiCalls.push(url);
            return new Promise((resolve, reject) => {
                previewResolvers.set(url, resolve);
                previewRejecters.set(url, reject);
            });
        },
        __apiCalls: apiCalls,
        __resolvePreview(url, payload) {
            const resolve = previewResolvers.get(url);
            if (!resolve) throw new Error("no pending preview for " + url);
            previewResolvers.delete(url);
            previewRejecters.delete(url);
            resolve(payload);
        },
        __rejectPreview(url, err) {
            const reject = previewRejecters.get(url);
            if (!reject) throw new Error("no pending preview for " + url);
            previewResolvers.delete(url);
            previewRejecters.delete(url);
            reject(err instanceof Error ? err : new Error(String(err)));
        },
        __byId: byId,
        __submitBtn: submitBtn,
        __form: form,
        __cancelBtn: cancelBtn,
        __dialog: dialog
    };

    vm.createContext(sandbox);
    vm.runInContext(extractFnBalanced("openBatchTagMailDialog"), sandbox);
    return sandbox;
}

function sampleOptions() {
    return [
        {
            optionType: "COMPOSE_TEMPLATE",
            optionValue: "10",
            optionName: "Material Reminder Email",
            subject: "Gentle Follow-up",
            mailType: "MATERIAL_REMINDER",
            templateCode: "MATERIAL_REMINDER"
        },
        {
            optionType: "COMPOSE_TEMPLATE",
            optionValue: "20",
            optionName: "Intro Template",
            subject: "Hello",
            mailType: "INTRODUCTION",
            templateCode: "INTRODUCTION"
        }
    ];
}

function settle() {
    return new Promise((r) => setImmediate(r));
}

describe("openBatchTagMailDialog preview (Task 3)", () => {
    it("requests compose-templates preview for selected COMPOSE_TEMPLATE id", async () => {
        const sb = createBatchMailDialogSandbox();
        const p = sb.openBatchTagMailDialog("标签：承诺回复", 2, 2, sampleOptions());
        await settle();
        assert.ok(sb.__apiCalls.includes("/api/compose-templates/10/preview"));
        sb.__resolvePreview("/api/compose-templates/10/preview", {
            subject: "Subj A",
            body: "Body A ${senderName}"
        });
        await settle();
        assert.strictEqual(sb.__byId.get("batchMailPreviewSubject").textContent, "Subj A");
        assert.strictEqual(sb.__byId.get("batchMailPreviewBody").textContent, "Body A ${senderName}");
        assert.strictEqual(sb.__submitBtn.disabled, false);
        sb.__cancelBtn.dispatchEvent("click");
        assert.strictEqual(await p, null);
    });

    it("shows MATERIAL_REMINDER notice even when optionName is renamed", async () => {
        const sb = createBatchMailDialogSandbox();
        const options = sampleOptions();
        options[0].optionName = "Completely Renamed Template";
        const p = sb.openBatchTagMailDialog("summary", 1, 1, options);
        await settle();
        sb.__resolvePreview("/api/compose-templates/10/preview", { subject: "S", body: "B" });
        await settle();
        const notice = sb.__byId.get("batchMailReminderNotice");
        assert.strictEqual(notice.hidden, false);
        assert.strictEqual(notice.textContent, "发送完成后保留当前专家标签。");
        sb.__cancelBtn.dispatchEvent("click");
        await p;
    });

    it("hides reminder notice for non-MATERIAL_REMINDER templates", async () => {
        const sb = createBatchMailDialogSandbox();
        const p = sb.openBatchTagMailDialog("summary", 1, 1, sampleOptions());
        await settle();
        sb.__resolvePreview("/api/compose-templates/10/preview", { subject: "S", body: "B" });
        await settle();

        const select = sb.__byId.get("batchMailOption");
        select.value = "COMPOSE_TEMPLATE:20";
        select.dispatchEvent("change");
        await settle();
        assert.ok(sb.__apiCalls.includes("/api/compose-templates/20/preview"));
        sb.__resolvePreview("/api/compose-templates/20/preview", { subject: "Intro", body: "Hi" });
        await settle();

        const notice = sb.__byId.get("batchMailReminderNotice");
        assert.strictEqual(notice.hidden, true);
        assert.strictEqual(sb.__byId.get("batchMailPreviewSubject").textContent, "Intro");
        sb.__cancelBtn.dispatchEvent("click");
        await p;
    });

    it("sets subject/body via textContent so HTML payload does not create elements", async () => {
        const sb = createBatchMailDialogSandbox();
        const p = sb.openBatchTagMailDialog("summary", 1, 1, sampleOptions());
        await settle();
        const evil = '<img onerror="window.__xss=1" src="x">';
        sb.__resolvePreview("/api/compose-templates/10/preview", {
            subject: evil,
            body: evil
        });
        await settle();
        const subjectEl = sb.__byId.get("batchMailPreviewSubject");
        const bodyEl = sb.__byId.get("batchMailPreviewBody");
        assert.strictEqual(subjectEl.textContent, evil);
        assert.strictEqual(bodyEl.textContent, evil);
        assert.strictEqual(subjectEl.children.length, 0);
        assert.strictEqual(bodyEl.children.length, 0);
        assert.strictEqual(bodyEl.innerHTML, "");
        sb.__cancelBtn.dispatchEvent("click");
        await p;
    });

    it("disables submit while loading and keeps disabled on failure", async () => {
        const sb = createBatchMailDialogSandbox();
        const p = sb.openBatchTagMailDialog("summary", 1, 1, sampleOptions());
        await settle();
        assert.strictEqual(sb.__submitBtn.disabled, true);
        assert.ok(sb.__byId.get("batchMailPreviewBody").textContent.includes("正在加载邮件预览"));
        sb.__rejectPreview("/api/compose-templates/10/preview", new Error("boom"));
        await settle();
        assert.strictEqual(sb.__submitBtn.disabled, true);
        assert.ok(
            sb.__byId.get("batchMailPreviewBody").textContent.startsWith("邮件预览加载失败：")
        );
        assert.ok(sb.__byId.get("batchMailPreviewBody").textContent.includes("boom"));
        assert.strictEqual(sb.__dialog.open, true);
        sb.__cancelBtn.dispatchEvent("click");
        await p;
    });

    it("discards stale preview responses when switching templates quickly", async () => {
        const sb = createBatchMailDialogSandbox();
        const p = sb.openBatchTagMailDialog("summary", 1, 1, sampleOptions());
        await settle();
        const select = sb.__byId.get("batchMailOption");
        select.value = "COMPOSE_TEMPLATE:20";
        select.dispatchEvent("change");
        await settle();

        sb.__resolvePreview("/api/compose-templates/10/preview", {
            subject: "STALE",
            body: "old"
        });
        await settle();
        assert.notStrictEqual(sb.__byId.get("batchMailPreviewSubject").textContent, "STALE");

        sb.__resolvePreview("/api/compose-templates/20/preview", {
            subject: "FRESH",
            body: "new"
        });
        await settle();
        assert.strictEqual(sb.__byId.get("batchMailPreviewSubject").textContent, "FRESH");
        assert.strictEqual(sb.__byId.get("batchMailPreviewBody").textContent, "new");
        sb.__cancelBtn.dispatchEvent("click");
        await p;
    });

    it("cleanup restores submit disabled=false and removes listeners", async () => {
        const sb = createBatchMailDialogSandbox();
        const p = sb.openBatchTagMailDialog("summary", 1, 1, sampleOptions());
        await settle();
        sb.__resolvePreview("/api/compose-templates/10/preview", { subject: "S", body: "B" });
        await settle();
        assert.strictEqual(sb.__submitBtn.disabled, false);

        sb.__submitBtn.disabled = true;
        sb.__cancelBtn.dispatchEvent("click");
        await p;

        assert.strictEqual(sb.__submitBtn.disabled, false);
        assert.strictEqual(sb.__dialog.open, false);
        assert.strictEqual(sb.__form._listeners.get("submit")?.size || 0, 0);
        assert.strictEqual(sb.__cancelBtn._listeners.get("click")?.size || 0, 0);
        const select = sb.__byId.get("batchMailOption");
        assert.strictEqual(select._listeners.get("change")?.size || 0, 0);
    });

    it("submit payload only returns mailOption", async () => {
        const sb = createBatchMailDialogSandbox();
        const p = sb.openBatchTagMailDialog("summary", 1, 1, sampleOptions());
        await settle();
        sb.__resolvePreview("/api/compose-templates/10/preview", { subject: "S", body: "B" });
        await settle();
        sb.__form.dispatchEvent("submit", { preventDefault() {} });
        const payload = await p;
        assert.strictEqual(payload.mailOption, "COMPOSE_TEMPLATE:10");
        assert.strictEqual(Object.keys(payload).length, 1);
        assert.ok(!("tag" in payload) && !("preview" in payload));
    });

    it("uses S-1 skeleton ids/classes and fixed hint copy", async () => {
        const sb = createBatchMailDialogSandbox();
        const p = sb.openBatchTagMailDialog("筛选摘要", 3, 2, sampleOptions());
        await settle();
        assert.strictEqual(sb.__byId.get("batchMailFilterSummary").textContent, "筛选摘要");
        assert.strictEqual(
            sb.__byId.get("batchMailRecipientSummary").textContent,
            "命中 3 位专家，其中 2 位可发送（已建立联系）"
        );
        assert.ok(sb.__byId.get("batchMailPreviewSection"));
        assert.strictEqual(sb.__byId.get("batchMailPreviewBody").className, "pre");
        assert.ok(sb.__byId.get("batchMailPreviewHint").className.includes("text-muted"));
        assert.ok(sb.__byId.get("batchMailReminderNotice").className.includes("text-muted"));
        assert.strictEqual(
            sb.__byId.get("batchMailPreviewHint").textContent,
            "预览保留模板变量；实际发送时按专家和发件账号替换。"
        );
        const bodyHtml = sb.__byId.get("actionDialogBody").innerHTML;
        assert.ok(bodyHtml.includes("筛选条件："));
        assert.ok(bodyHtml.includes("主题："));
        assert.ok(!/style\s*=/.test(bodyHtml));
        sb.__resolvePreview("/api/compose-templates/10/preview", { subject: "S", body: "B" });
        await settle();
        sb.__cancelBtn.dispatchEvent("click");
        await p;
    });

    it("openBatchTagMailDialog source has no style= and no reminder-only button", () => {
        const src = extractFnBalanced("openBatchTagMailDialog");
        assert.ok(!/style\s*=/.test(src));
        assert.ok(!src.includes("发送提醒邮件"));
        assert.ok(appJsSource.includes('id="batchTagMailBtn"') || fs.readFileSync(
            path.join(__dirname, "..", "..", "main", "resources", "static", "index.html"),
            "utf-8"
        ).includes('id="batchTagMailBtn"'));
        assert.ok(!appJsSource.includes("发送提醒邮件"));
        const batchBtnMatches = fs.readFileSync(
            path.join(__dirname, "..", "..", "main", "resources", "static", "index.html"),
            "utf-8"
        ).match(/id="batchTagMailBtn"/g);
        assert.strictEqual(batchBtnMatches.length, 1);
    });
});
