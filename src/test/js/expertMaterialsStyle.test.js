"use strict";

// 子计划 08 样式测试（S-1 契约）：
// 1) 落地的 expert-materials.css 与计划 08 / ui-style-contract 的 S-1 CSS 代码块
//    逐字（字节）一致 —— 不增一行、不减一行。
// 2) DOM 白名单：真实挂载（inline + drawer + selectionOnly + 各种存储状态行）
//    后遍历整棵树 —— 每个 class 必须存在于 expert-materials.css 或既有
//    styles.css；不允许任何 id（组件全用 class + aria）；不允许任何 inline style。
// 3) data-state 取值限定在六个存储状态；行结构白名单按 S-1。
//    与 K-dom-stub-tests-hide-dangling-refs 同理：不靠“getElementById 恒返回
//    元素”的空 stub，而是真实树遍历核对每个渲染产物。

const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const ROOT = path.join(__dirname, "..", "..", "main", "resources", "static");
const cssSource = fs.readFileSync(path.join(ROOT, "expert-materials.css"), "utf-8");
const stylesSource = fs.readFileSync(path.join(ROOT, "styles.css"), "utf-8");
const componentSource = fs.readFileSync(path.join(ROOT, "expert-materials.js"), "utf-8");

const PLAN_08 = path.join(__dirname, "..", "..", "..", "docs", "plans", "2026-09-07", "08-shared-materials-frontend.md");
const STYLE_CONTRACT = path.join(__dirname, "..", "..", "..", "docs", "plans", "2026-09-07", "ui-style-contract.md");

const STORAGE_STATES = ["METADATA_ONLY", "QUEUED", "DOWNLOADING", "STORED", "FAILED", "SOURCE_UNAVAILABLE"];

// ════════════════════════════════════════════════════════════════════════
// 计划文档 S-1 CSS 代码块提取
// ════════════════════════════════════════════════════════════════════════

function extractS1Css(planPath) {
    const text = fs.readFileSync(planPath, "utf-8");
    const blocks = [...text.matchAll(/```css\n([\s\S]*?)\n```/g)];
    for (const block of blocks) {
        if (block[1].trimStart().startsWith(".expert-materials{")) {
            return block[1];
        }
    }
    throw new Error(`S-1 CSS block not found in ${planPath}`);
}

// ════════════════════════════════════════════════════════════════════════
// 真实 DOM 能力的最小树（专供渲染 + 遍历；与共享测试同一语义，独立副本）
// ════════════════════════════════════════════════════════════════════════

class MiniEvent {
    constructor(type) {
        this.type = type;
        this.target = null;
    }
}

class MiniText {
    constructor(data) {
        this.nodeType = 3;
        this.parentNode = null;
        this.data = String(data);
    }
}

class MiniElement {
    constructor(tagName) {
        this.tagName = String(tagName).toUpperCase();
        this.nodeType = 1;
        this.parentNode = null;
        this.childNodes = [];
        this.attributes = new Map();
        this.listeners = new Map();
        this._classes = new Set();
        this._dataset = null;
        this._value = "";
        this.checked = false;
        this.indeterminate = false;
        this.disabled = false;
        this.hidden = false;
        this._dialogOpen = false;
    }
    get children() {
        return this.childNodes.filter((node) => node.nodeType === 1);
    }
    get isConnected() {
        let node = this;
        while (node && node.parentNode) node = node.parentNode;
        return !!node && node.nodeType === 9;
    }
    get classList() {
        const self = this;
        return {
            add(...names) { names.forEach((n) => self._classes.add(String(n))); },
            remove(...names) { names.forEach((n) => self._classes.delete(String(n))); },
            contains(name) { return self._classes.has(String(name)); }
        };
    }
    get className() {
        return [...this._classes].join(" ");
    }
    get dataset() {
        if (!this._dataset) {
            const self = this;
            const toAttr = (key) => "data-" + String(key).replace(/([A-Z])/g, (m) => "-" + m.toLowerCase());
            this._dataset = new Proxy({}, {
                get(_t, key) {
                    const attr = toAttr(key);
                    return self.attributes.has(attr) ? self.attributes.get(attr) : undefined;
                },
                set(_t, key, value) {
                    const attr = toAttr(key);
                    if (value === undefined || value === null) self.attributes.delete(attr);
                    else self.attributes.set(attr, String(value));
                    return true;
                }
            });
        }
        return this._dataset;
    }
    setAttribute(name, value) {
        if (name.startsWith("data-") && this._dataset) {
            const key = name.slice(5).replace(/-([a-z])/g, (_, c) => c.toUpperCase());
            this._dataset[key] = String(value);
        }
        this.attributes.set(name, String(value));
        if (name === "value") this._value = String(value);
        if (name === "disabled") this.disabled = true;
        if (name === "hidden") this.hidden = true;
        if (name === "open") this._dialogOpen = true;
        if (name === "checked") this.checked = true;
    }
    getAttribute(name) {
        return this.attributes.has(name) ? this.attributes.get(name) : null;
    }
    hasAttribute(name) {
        return this.attributes.has(name);
    }
    removeAttribute(name) {
        this.attributes.delete(name);
        if (name === "value") this._value = "";
        if (name === "disabled") this.disabled = false;
        if (name === "hidden") this.hidden = false;
        if (name === "open") this._dialogOpen = false;
        if (name === "checked") this.checked = false;
    }
    set value(value) {
        this._value = String(value);
        this.attributes.set("value", String(value));
    }
    get value() {
        return this._value;
    }
    get type() {
        return this.getAttribute("type") || "";
    }
    appendChild(child) {
        if (child.parentNode) child.parentNode.removeChild(child);
        child.parentNode = this;
        this.childNodes.push(child);
        return child;
    }
    removeChild(child) {
        const idx = this.childNodes.indexOf(child);
        if (idx === -1) return child;
        this.childNodes.splice(idx, 1);
        child.parentNode = null;
        return child;
    }
    replaceChild(next, prev) {
        const idx = this.childNodes.indexOf(prev);
        if (idx === -1) throw new Error("replaceChild: node not found");
        if (next.parentNode) next.parentNode.removeChild(next);
        this.childNodes[idx] = next;
        next.parentNode = this;
        prev.parentNode = null;
        return prev;
    }
    remove() {
        if (this.parentNode) this.parentNode.removeChild(this);
    }
    set textContent(value) {
        this.childNodes = [];
        if (value !== "" && value !== null && value !== undefined) {
            this.childNodes.push(new MiniText(String(value)));
        }
    }
    get textContent() {
        const collect = (node) => (node.nodeType === 3 ? node.data : node.childNodes.map((c) => collect(c)).join(""));
        return collect(this);
    }
    parseTokens(selector) {
        return String(selector).trim().split(/\s+/)
            .filter((part) => part && part !== ">")
            .map((token) => {
                const tagMatch = token.match(/^[a-zA-Z][a-zA-Z0-9-]*/);
                const rest = token.slice(tagMatch ? tagMatch[0].length : 0);
                const out = { tag: tagMatch ? tagMatch[0].toLowerCase() : null, classes: [], attrs: [] };
                let i = 0;
                while (i < rest.length) {
                    if (rest[i] === ".") {
                        let j = i + 1;
                        while (j < rest.length && /[\w-]/.test(rest[j])) j += 1;
                        out.classes.push(rest.slice(i + 1, j));
                        i = j;
                    } else if (rest[i] === "[") {
                        let j = rest.indexOf("]", i);
                        if (j === -1) j = rest.length;
                        const raw = rest.slice(i + 1, j);
                        const eq = raw.indexOf("=");
                        if (eq === -1) out.attrs.push({ name: raw.trim(), value: null });
                        else {
                            let value = raw.slice(eq + 1).trim();
                            if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) value = value.slice(1, -1);
                            out.attrs.push({ name: raw.slice(0, eq).trim(), value });
                        }
                        i = j + 1;
                    } else {
                        i += 1;
                    }
                }
                return out;
            });
    }
    tokenMatch(el, token) {
        if (token.tag && el.tagName.toLowerCase() !== token.tag) return false;
        for (const cls of token.classes) {
            if (!el._classes.has(cls)) return false;
        }
        for (const { name, value } of token.attrs) {
            const actual = el.attributes.has(name) ? el.attributes.get(name) : null;
            if (value === null) {
                if (actual === null) return false;
            } else if (actual !== value) {
                return false;
            }
        }
        return true;
    }
    matchesTokens(el, tokens) {
        if (tokens.length === 1) return this.tokenMatch(el, tokens[0]);
        if (!this.tokenMatch(el, tokens[tokens.length - 1])) return false;
        let ancestor = el.parentNode;
        for (let idx = tokens.length - 2; idx >= 0; idx -= 1) {
            while (ancestor && ancestor.nodeType === 1 && !this.tokenMatch(ancestor, tokens[idx])) {
                ancestor = ancestor.parentNode;
            }
            if (!ancestor || ancestor.nodeType !== 1) return false;
            ancestor = ancestor.parentNode;
        }
        return true;
    }
    querySelector(selector) {
        return this.querySelectorAll(selector)[0] || null;
    }
    querySelectorAll(selector) {
        const tokens = this.parseTokens(selector);
        const out = [];
        const walk = (node) => {
            for (const child of node.childNodes) {
                if (child.nodeType !== 1) continue;
                if (this.matchesTokens(child, tokens)) out.push(child);
                walk(child);
            }
        };
        walk(this);
        return out;
    }
    addEventListener(type, fn) {
        const list = this.listeners.get(type) || [];
        list.push(fn);
        this.listeners.set(type, list);
    }
    removeEventListener(type, fn) {
        const list = this.listeners.get(type) || [];
        this.listeners.set(type, list.filter((item) => item !== fn));
    }
    dispatchEvent(event) {
        event.target = this;
        let node = this;
        while (node && node.nodeType === 1) {
            for (const fn of node.listeners.get(event.type) || []) fn(event);
            node = node.parentNode;
        }
        return true;
    }
    fire(type) {
        this.dispatchEvent(new MiniEvent(type));
    }
}

class MiniDocument {
    constructor() {
        this.root = new MiniElement("#document");
        this.root.nodeType = 9;
    }
    createElement(tag) {
        return new MiniElement(tag);
    }
    createTextNode(data) {
        return new MiniText(String(data));
    }
}

function flush() {
    return new Promise((resolve) => setImmediate(() => setImmediate(() => setImmediate(() => setImmediate(resolve)))));
}

// 覆盖全部六个存储状态 + 错误/来源不可用说明的目录
const CATALOG = [1, 2, 3, 4, 5, 6].map((i) => {
    const state = ["STORED", "METADATA_ONLY", "QUEUED", "DOWNLOADING", "FAILED", "SOURCE_UNAVAILABLE"][i - 1];
    const fileName = `材料文件-状态示例-${i}.pdf`;
    return {
        attachmentId: i,
        documentId: 2000 + i,
        source: i === 5 || i === 6
            ? null
            : { type: "INBOUND_PROCESSING", id: 900 + i, subject: "材料补充请求", receivedAt: "2026-09-07T14:42:00", accountCode: "test" },
        fileName,
        contentType: "application/pdf",
        documentType: "CV",
        documentStatus: "PENDING_REVIEW",
        actualSize: state === "STORED" ? 182500 : null,
        encodedSize: 183000,
        storageState: state,
        bytesDownloaded: state === "DOWNLOADING" ? 64000 : 0,
        error: state === "FAILED"
            ? { code: "TIMEOUT", message: "连接超时，请重试" }
            : (state === "SOURCE_UNAVAILABLE" ? { code: "SOURCE_UNAVAILABLE", message: "来信源已不可用（UIDVALIDITY 变更）" } : null),
        canFetch: state === "METADATA_ONLY" || state === "FAILED",
        canDownload: state === "STORED",
        canPreview: state === "STORED",
        analysisSupported: true,
        canAnalyze: state === "STORED",
        downloadUrl: state === "STORED" ? "/api/expert-contacts/1/attachments/1/download" : null,
        previewUrl: state === "STORED" ? "/api/expert-contacts/1/attachments/1/preview" : null
    };
});

function summarize(catalog) {
    const summary = { total: catalog.length, stored: 0, metadataOnly: 0, active: 0, failed: 0, sourceUnavailable: 0, defaultAnalysisAttachmentIds: [] };
    catalog.forEach((item) => {
        if (item.storageState === "STORED") summary.stored += 1;
        else if (item.storageState === "METADATA_ONLY") summary.metadataOnly += 1;
        else if (item.storageState === "QUEUED" || item.storageState === "DOWNLOADING") summary.active += 1;
        else if (item.storageState === "FAILED") summary.failed += 1;
        else summary.sourceUnavailable += 1;
    });
    return summary;
}

class FakeServer {
    constructor() {
        this.catalog = CATALOG.map((item) => Object.assign({}, item));
        this.gets = 0;
    }
    apiAdapter() {
        const server = this;
        return async function api(url) {
            server.gets += 1;
            const qIndex = url.indexOf("?");
            const params = new URLSearchParams(qIndex === -1 ? "" : url.slice(qIndex + 1));
            const page = Number(params.get("page") || "0");
            const size = Number(params.get("size") || "10");
            const items = this && this.catalog ? this.catalog : server.catalog;
            const pageItems = items.slice(page * size, page * size + size);
            return { items: pageItems, total: items.length, page, size, summary: summarize(items) };
        };
    }
}

function loadComponent(server) {
    const doc = new MiniDocument();
    const sandbox = { document: doc, URLSearchParams, AbortController, setTimeout, clearTimeout, console };
    sandbox.window = sandbox;
    sandbox.globalThis = sandbox;
    vm.createContext(sandbox);
    vm.runInContext(componentSource, sandbox, { filename: "expert-materials.js" });
    sandbox.ExpertMaterials.configure({ api: server.apiAdapter(), contextPath: "", pollMs: 2000 });
    return { sandbox, doc };
}

function walkTree(node, fn) {
    fn(node);
    node.childNodes.forEach((child) => {
        if (child.nodeType === 1) walkTree(child, fn);
    });
}

// ════════════════════════════════════════════════════════════════════════

describe("S-1: 落地 CSS 与计划契约逐字一致", () => {
    it("expert-materials.css 字节等于计划 08 的 S-1 代码块", () => {
        const block = extractS1Css(PLAN_08);
        assert.strictEqual(cssSource, block, "expert-materials.css 必须与 08 计划 S-1 代码块逐字一致");
    });
    it("expert-materials.css 字节等于 ui-style-contract 的 S-1 代码块", () => {
        const block = extractS1Css(STYLE_CONTRACT);
        assert.strictEqual(cssSource, block, "expert-materials.css 必须与 ui-style-contract S-1 代码块逐字一致");
        assert.ok(cssSource.includes(".expert-materials{"), "CSS 以 .expert-materials 规则开头");
        assert.ok(cssSource.includes("@media(prefers-reduced-motion:reduce)"), "CSS 以 reduced-motion 媒体规则收尾");
        assert.ok(!/\n\s*\n\s*\n/.test(cssSource), "契约块内部没有因拷贝产生的空行漂移");
    });
});

describe("S-1: DOM 白名单 —— 渲染产物 class/id/inline-style 全量核对", () => {
    it("inline / drawer / selectionOnly 渲染树：全部 class 已声明，无 id，无 inline style，data-state 合法", async () => {
        const server = new FakeServer();
        const { sandbox, doc } = loadComponent(server);
        const hosts = [];
        const inlineHost = doc.createElement("div");
        doc.root.appendChild(inlineHost);
        hosts.push(inlineHost);
        const drawerHost = doc.createElement("div");
        doc.root.appendChild(drawerHost);
        hosts.push(drawerHost);
        const pickerHost = doc.createElement("div");
        doc.root.appendChild(pickerHost);
        hosts.push(pickerHost);
        try {
            sandbox.ExpertMaterials.mount({ host: inlineHost, contactId: 1, mode: "inline" });
            await flush();
            sandbox.ExpertMaterials.mount({ host: drawerHost, contactId: 1, mode: "drawer" });
            await flush();
            sandbox.ExpertMaterials.mount({ host: pickerHost, contactId: 1, mode: "selectionOnly" });
            await flush();

        // 收集渲染树实际写出的 class / id / style
        const usedClasses = new Set();
        const usedIds = [];
        const styleAttrs = [];
        const dataStates = new Set();
        hosts.forEach((host) => {
            walkTree(host, (node) => {
                if (node.nodeType !== 1) return;
                node._classes.forEach((cls) => usedClasses.add(cls));
                if (node.hasAttribute("id")) usedIds.push(node.getAttribute("id"));
                if (node.hasAttribute("style")) styleAttrs.push(node.tagName);
                const state = node.getAttribute("data-state");
                if (state) dataStates.add(state);
            });
        });

        // 组件产物里六个状态各有一行（含错误小字与来源不可用行）
        assert.ok(dataStates.has("STORED") && dataStates.has("METADATA_ONLY") && dataStates.has("QUEUED")
            && dataStates.has("DOWNLOADING") && dataStates.has("FAILED") && dataStates.has("SOURCE_UNAVAILABLE"),
        "渲染树应覆盖全部六种存储状态");
        for (const state of dataStates) {
            assert.ok(STORAGE_STATES.includes(state), `未知 data-state: ${state}`);
        }

        // 白名单：expert-materials.css（S-1）+ 既有全局 styles.css
        const cssClasses = new Set();
        for (const source of [cssSource, stylesSource]) {
            for (const m of source.matchAll(/\.([A-Za-z][A-Za-z0-9_-]*)/g)) {
                cssClasses.add(m[1]);
            }
        }
        const missing = [...usedClasses].filter((cls) => !cssClasses.has(cls)).sort();
        assert.deepStrictEqual(missing, [], "渲染产物每个 class 都必须已声明（S-1 或既有 styles.css）");
        assert.deepStrictEqual(usedIds, [], "组件渲染产物不允许使用元素 id（全部 class/aria 定位）");
        assert.deepStrictEqual(styleAttrs, [], "组件渲染产物不允许出现 inline style");

        // 关键结构按 S-1 DOM 白名单存在
        for (const host of hosts) {
            assert.ok(host.querySelector(".expert-materials"), "root section.expert-materials");
            assert.ok(host.querySelector(".expert-materials header"), "header");
            assert.ok(host.querySelector(".em-policy"), ".em-policy 说明行");
            assert.ok(host.querySelector(".em-stats"), ".em-stats 统计");
            assert.ok(host.querySelector(".em-filters"), ".em-filters 筛选区（搜索/来源/状态）");
            assert.ok(host.querySelector(".em-table-head"), ".em-table-head 表头");
            assert.ok(host.querySelector(".em-rows"), ".em-rows 行区");
            assert.ok(host.querySelector(".em-row"), ".em-row 行");
            assert.ok(host.querySelector(".em-file"), ".em-file 文件名区");
            assert.ok(host.querySelector(".em-state"), ".em-state 状态区");
            assert.ok(host.querySelector(".em-empty"), ".em-empty 空态");
            assert.ok(host.querySelector(".em-error"), ".em-error 错误区");
            assert.ok(host.querySelector(".em-pager"), ".em-pager 分页");
        }
        assert.ok(inlineHost.querySelector(".em-selection"), "inline 有选择 footer");
        assert.ok(inlineHost.querySelector('[data-action="transfer"]'), "inline 有获取所选按钮");
        assert.ok(drawerHost.querySelector("dialog.em-drawer"), "drawer 模式使用原生 dialog.em-drawer");
        assert.strictEqual(pickerHost.querySelector(".em-selection"), null, "selectionOnly 隐藏传输 footer");

        // S-1 固定文案抽查（行状态唯一文案）
        const statesText = inlineHost.querySelectorAll(".em-state");
        const texts = Array.from(statesText).map((el) => el.textContent);
        assert.ok(texts.some((t) => t.includes("已存服务器")), "STORED 行文案 已存服务器");
        assert.ok(texts.some((t) => t.includes("仅文件信息")), "METADATA_ONLY 行文案 仅文件信息");
        assert.ok(texts.some((t) => t.includes("排队中")), "QUEUED 行文案 排队中");
        assert.ok(texts.some((t) => t.includes("获取中")), "DOWNLOADING 行文案 获取中");
        assert.ok(texts.some((t) => t.includes("获取失败")), "FAILED 行文案 获取失败");
        assert.ok(texts.some((t) => t.includes("来源不可用")), "SOURCE_UNAVAILABLE 行文案 来源不可用");
        const failedRow = Array.from(inlineHost.querySelectorAll(".em-state")).find((el) => el.getAttribute("data-state") === "FAILED");
        assert.ok(failedRow.textContent.includes("连接超时"), "FAILED 行展示 error.message");
        const sourceRow = Array.from(inlineHost.querySelectorAll(".em-state")).find((el) => el.getAttribute("data-state") === "SOURCE_UNAVAILABLE");
        assert.ok(sourceRow.textContent.includes("UIDVALIDITY"), "SOURCE_UNAVAILABLE 行说明具体原因");

        } finally {
            sandbox.ExpertMaterials.unmountHostsIn(inlineHost);
            sandbox.ExpertMaterials.unmountHostsIn(drawerHost);
            sandbox.ExpertMaterials.unmountHostsIn(pickerHost);
            await flush();
        }
    });

    it("文件名可访问名称/标题保留全文，省略由 CSS 完成（无 JS 截断字符）", async () => {
        const server = new FakeServer();
        const { sandbox, doc } = loadComponent(server);
        const host = doc.createElement("div");
        doc.root.appendChild(host);
        try {
            sandbox.ExpertMaterials.mount({ host, contactId: 1, mode: "inline" });
            await flush();
            const row = host.querySelector(".em-row");
            const strong = row.querySelector(".em-file strong");
            const fileName = strong.textContent;
            assert.ok(fileName.includes("材料文件-状态示例"), "完整文件名渲染进 DOM 文本");
            assert.ok(strong.getAttribute("title") === fileName, "title 与文本同为完整文件名");
            const checkbox = row.querySelector("input[type=checkbox]");
            assert.ok(checkbox.getAttribute("aria-label").includes(fileName), "checkbox 可访问名称包含完整文件名");
        } finally {
            sandbox.ExpertMaterials.unmountHostsIn(host);
            await flush();
        }
    });
});
