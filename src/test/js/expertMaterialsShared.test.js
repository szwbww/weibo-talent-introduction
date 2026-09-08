"use strict";

// 子计划 08 行为测试：真实 DOM 能力足够的最小树（真正的 querySelector /
// closest / 事件冒泡 / dataset），绝不是“getElementById 恒返回元素”的空 stub。
// 覆盖 I-1..I-4 / S-1 / S-5 的跨 host 共享 store、跨专家陈旧响应、10 行窗口、
// 跨页选择、筛选保留选择、跨专家提交隔离、500 上限、请求 epoch、unmount 停轮询、
// 无 contactId 空态（app.js 宿主）。

const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const ROOT = path.join(__dirname, "..", "..", "main", "resources", "static");
const componentSource = fs.readFileSync(path.join(ROOT, "expert-materials.js"), "utf-8");
const appSource = fs.readFileSync(path.join(ROOT, "app.js"), "utf-8");

// ════════════════════════════════════════════════════════════════════════
// 最小真实 DOM（test-only；覆盖组件用到的全部能力）
// ════════════════════════════════════════════════════════════════════════

class MiniEvent {
    constructor(type, opts) {
        this.type = type;
        this.target = null;
        this.bubbles = !!(opts && opts.bubbles);
    }
}

class MiniText {
    constructor(data) {
        this.nodeType = 3;
        this.parentNode = null;
        this.data = String(data);
    }
    get textContent() {
        return this.data;
    }
}

function parseToken(token) {
    // 支持: tag | .class | #id | [attr] | [attr=value] | tag.class | tag[attr=value]
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
        } else if (rest[i] === "#") {
            let j = i + 1;
            while (j < rest.length && /[\w-]/.test(rest[j])) j += 1;
            out.id = rest.slice(i + 1, j);
            i = j;
        } else if (rest[i] === "[") {
            let j = rest.indexOf("]", i);
            if (j === -1) j = rest.length;
            const raw = rest.slice(i + 1, j);
            const eq = raw.indexOf("=");
            if (eq === -1) {
                out.attrs.push({ name: raw.trim(), value: null });
            } else {
                let name = raw.slice(0, eq).trim();
                let value = raw.slice(eq + 1).trim();
                if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.slice(1, -1);
                }
                out.attrs.push({ name, value });
            }
            i = j + 1;
        } else {
            i += 1;
        }
    }
    return out;
}

function parseSelector(selector) {
    return String(selector).trim().split(/\s+/)
        .filter((part) => part && part !== ">") // 子代组合符按后代处理（本组件查询无需严格子代）
        .map(parseToken);
}

function tokenMatches(el, token) {
    if (token.tag && el.tagName.toLowerCase() !== token.tag) return false;
    if (token.id && el.getAttribute("id") !== token.id) return false;
    for (const cls of token.classes) {
        if (!el.classList.contains(cls)) return false;
    }
    for (const { name, value } of token.attrs) {
        const actual = el.getAttribute(name);
        if (value === null) {
            if (actual === null) return false;
        } else if (actual !== value) {
            return false;
        }
    }
    return true;
}

function selectorMatches(el, tokens) {
    if (tokens.length === 1) return tokenMatches(el, tokens[0]);
    // 后代组合：最后一段必须命中 el，前面各段按祖先链匹配
    const target = tokens[tokens.length - 1];
    if (!tokenMatches(el, target)) return false;
    let ancestor = el.parentNode;
    for (let idx = tokens.length - 2; idx >= 0; idx -= 1) {
        while (ancestor && ancestor.nodeType === 1 && !tokenMatches(ancestor, tokens[idx])) {
            ancestor = ancestor.parentNode;
        }
        if (!ancestor || ancestor.nodeType !== 1) return false;
        ancestor = ancestor.parentNode;
    }
    return true;
}

class MiniElement {
    constructor(tagName, ownerDocument) {
        this.ownerDocument = ownerDocument;
        this.tagName = String(tagName).toUpperCase();
        this.nodeType = 1;
        this.parentNode = null;
        this.childNodes = [];
        this.attributes = new Map();
        this.listeners = new Map();
        this._classes = new Set();
        this._dataset = null;
        this._textContentCache = null;
        this._value = "";
        this.checked = false;
        this.indeterminate = false;
        this.disabled = false;
        this.hidden = false;
        this._selectedIndex = 0;
        this._dialogOpen = false;
    }

    get children() {
        return this.childNodes.filter((node) => node.nodeType === 1);
    }
    get firstChild() {
        return this.childNodes[0] || null;
    }
    get lastChild() {
        return this.childNodes[this.childNodes.length - 1] || null;
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
            contains(name) { return self._classes.has(String(name)); },
            toggle(name, force) {
                const want = force === undefined ? !self._classes.has(String(name)) : !!force;
                if (want) self._classes.add(String(name));
                else self._classes.delete(String(name));
                return want;
            }
        };
    }
    get className() {
        return [...this._classes].join(" ");
    }
    set className(value) {
        this._classes = new Set(String(value).split(/\s+/).filter(Boolean));
    }

    get dataset() {
        if (!this._dataset) {
            const self = this;
            const toAttr = (key) => "data-" + String(key).replace(/([A-Z])/g, (m) => "-" + m.toLowerCase());
            const toKey = (attr) => attr.slice(5).replace(/-([a-z])/g, (_, c) => c.toUpperCase());
            this._dataset = new Proxy({}, {
                get(_t, key) {
                    const attr = toAttr(key);
                    return self.attributes.has(attr) ? self.attributes.get(attr) : undefined;
                },
                set(_t, key, value) {
                    const attr = toAttr(key);
                    if (value === undefined || value === null) {
                        self.attributes.delete(attr);
                    } else {
                        self.attributes.set(attr, String(value));
                    }
                    return true;
                },
                deleteProperty(_t, key) {
                    self.attributes.delete(toAttr(key));
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
        for (const child of this.children) {
            if (child.tagName === "OPTION" && child.getAttribute("value") === String(value)) {
                child._selected = true;
            } else if (child.tagName === "OPTION") {
                child._selected = false;
            }
        }
    }
    get value() {
        return this._value;
    }
    set type(value) {
        this.attributes.set("type", String(value));
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
    contains(node) {
        let cur = node;
        while (cur) {
            if (cur === this) return true;
            cur = cur.parentNode;
        }
        return false;
    }

    set textContent(value) {
        this.childNodes = [];
        if (value !== "" && value !== null && value !== undefined) {
            this.childNodes.push(new MiniText(String(value)));
        }
        this._textContentCache = null;
    }
    get textContent() {
        if (this._textContentCache === null) {
            this._textContentCache = collectText(this);
        }
        return this._textContentCache;
    }

    matches(selector) {
        return selectorMatches(this, parseSelector(selector));
    }
    closest(selector) {
        const tokens = parseSelector(selector);
        let node = this;
        while (node && node.nodeType === 1) {
            if (selectorMatches(node, tokens)) return node;
            node = node.parentNode;
        }
        return null;
    }
    querySelector(selector) {
        return this.querySelectorAll(selector)[0] || null;
    }
    querySelectorAll(selector) {
        const tokens = parseSelector(selector);
        const out = [];
        const walk = (node) => {
            for (const child of node.childNodes) {
                if (child.nodeType !== 1) continue;
                if (selectorMatches(child, tokens)) out.push(child);
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
    listenerCount(type) {
        if (type) return (this.listeners.get(type) || []).length;
        let count = 0;
        for (const list of this.listeners.values()) count += list.length;
        return count;
    }
    dispatchEvent(event) {
        event.target = this;
        let node = this;
        while (node && node.nodeType === 1) {
            event.currentTarget = node;
            for (const fn of node.listeners.get(event.type) || []) fn(event);
            node = event.bubbles === false ? null : node.parentNode;
        }
        return true;
    }

    // dialog 模拟：真实浏览器中 Esc/关闭的焦点恢复由原生 dialog 负责
    showModal() {
        this._dialogOpen = true;
        this.attributes.set("open", "");
    }
    close() {
        this._dialogOpen = false;
        this.removeAttribute("open");
        this.dispatchEvent(new MiniEvent("close", { bubbles: false }));
    }

    click() {
        this.dispatchEvent(new MiniEvent("click", { bubbles: true }));
    }
    fire(type, bubbles = true) {
        this.dispatchEvent(new MiniEvent(type, { bubbles }));
    }
}

function collectText(node) {
    if (node.nodeType === 3) return node.data;
    return node.childNodes.map((child) => collectText(child)).join("");
}

class MiniDocument {
    constructor() {
        this.root = new MiniElement("#document", this);
        this.root.nodeType = 9;
    }
    createElement(tag) {
        return new MiniElement(tag, this);
    }
    createTextNode(data) {
        return new MiniText(String(data));
    }
}

function createHost() {
    const doc = new MiniDocument();
    const host = doc.createElement("div");
    doc.root.appendChild(host);
    return { doc, host };
}

function click(el) {
    el.dispatchEvent(new MiniEvent("click", { bubbles: true }));
}
function change(el) {
    el.dispatchEvent(new MiniEvent("change", { bubbles: true }));
}
function input(el) {
    el.dispatchEvent(new MiniEvent("input", { bubbles: true }));
}
function prevBtn(host) {
    return host.querySelectorAll(".em-pager .button")[0] || null;
}
function nextBtn(host) {
    return host.querySelectorAll(".em-pager .button")[1] || null;
}

function flush() {
    return new Promise((resolve) => setImmediate(() => setImmediate(() => setImmediate(() => setImmediate(resolve)))));
}
function wait(ms) {
    return new Promise((resolve) => setTimeout(resolve, ms));
}

// ════════════════════════════════════════════════════════════════════════
// 伪后端：真实分页/筛选语义（q=文件名包含、state、source+sourceId、summary 全集）
// ════════════════════════════════════════════════════════════════════════

function makeCatalog(count, opts) {
    const options = opts || {};
    const items = [];
    for (let i = 1; i <= count; i += 1) {
        const state = options.stateOf ? options.stateOf(i) : (i === 1 ? "STORED" : "METADATA_ONLY");
        const fileName = options.fileName ? options.fileName(i) : `附件-${String(i).padStart(4, "0")}.pdf`;
        items.push({
            attachmentId: i,
            documentId: 1000 + i,
            source: options.noSource
                ? null
                : { type: "INBOUND_PROCESSING", id: 2000 + (i % 3), subject: `来信主题 ${i % 3}`, receivedAt: "2026-09-07T14:42:00", accountCode: "test" },
            fileName,
            contentType: "application/pdf",
            documentType: i % 5 === 0 ? "CV" : "OTHER",
            documentStatus: "PENDING_REVIEW",
            actualSize: state === "STORED" ? 182500 : null,
            encodedSize: state === "STORED" ? 182500 : 183000,
            storageState: state,
            bytesDownloaded: state === "DOWNLOADING" ? 64000 : 0,
            error: state === "FAILED"
                ? { code: "TIMEOUT", message: "连接超时" }
                : (options.errorFor && options.errorFor(i)) || null,
            canFetch: state === "METADATA_ONLY" || state === "FAILED",
            canDownload: state === "STORED",
            canPreview: state === "STORED",
            analysisSupported: true,
            canAnalyze: state === "STORED",
            downloadUrl: state === "STORED" ? `/api/expert-contacts/${opts.contactId || 1}/attachments/${i}/download` : null,
            previewUrl: state === "STORED" ? `/api/expert-contacts/${opts.contactId || 1}/attachments/${i}/preview` : null
        });
    }
    return items;
}

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
    constructor(catalogOpts, hooks) {
        const options = typeof catalogOpts === "function" ? catalogOpts() : catalogOpts;
        this.catalog = options && Array.isArray(options) ? options.slice() : makeCatalog(options.count || 40, options);
        this.hooks = hooks || {};
        this.gets = [];
        this.posts = [];
        this.deferred = []; // 未 resolve 的 GET（用于竞态测试）
        this.holdGets = false;
    }

    apiAdapter() {
        const server = this;
        return async function api(pathArg, options) {
            const opts = options || {};
            const url = String(pathArg);
            if (opts.method === "POST") {
                server.posts.push({ url, body: JSON.parse(opts.body || "{}"), options: opts });
                return server.handlePost(url, JSON.parse(opts.body || "{}"));
            }
            const record = { url, options: opts };
            server.gets.push(record);
            const data = server.handleGet(url);
            if (server.holdGets) {
                return new Promise((resolve) => server.deferred.push({ record, resolve, data }));
            }
            return data;
        };
    }

    handleGet(url) {
        const queryIndex = url.indexOf("?");
        const raw = queryIndex === -1 ? "" : url.slice(queryIndex + 1);
        const params = new URLSearchParams(raw);
        const page = Number(params.get("page") || "0");
        const size = Number(params.get("size") || "10");
        const q = (params.get("q") || "").trim().toLowerCase();
        const state = params.get("state") || "";
        const source = params.get("source") || "";
        const sourceId = params.get("sourceId") || "";
        let filtered = this.catalog.filter((item) => {
            if (q && !item.fileName.toLowerCase().includes(q)) return false;
            if (state && item.storageState !== state) return false;
            if (source) {
                if (!item.source || item.source.type !== source || String(item.source.id) !== String(sourceId)) return false;
            }
            return true;
        });
        if (this.hooks.onFilter) filtered = this.hooks.onFilter(filtered, { page, size, q, state, source, sourceId });
        const total = filtered.length;
        const pageItems = filtered.slice(page * size, page * size + size);
        return { items: pageItems, total, page, size, summary: this.hooks.onSummary ? this.hooks.onSummary(this.catalog) : summarize(this.catalog) };
    }

    handlePost(url, body) {
        if (this.hooks.beforePost) this.hooks.beforePost(url, body);
        if (this.hooks.failPost) {
            const failure = this.hooks.failPost(url, body);
            if (failure) {
                const err = new Error(failure.message || "提交失败，请重试");
                err.status = failure.status || 500;
                throw err;
            }
        }
        const ids = (body && Array.isArray(body.attachmentIds) ? body.attachmentIds : []).map(Number);
        let acceptedCount = 0;
        let alreadyReadyCount = 0;
        const items = ids.map((id) => {
            const item = this.catalog.find((entry) => entry.attachmentId === id);
            if (!item) return { attachmentId: id, transferId: null, state: null };
            if (item.storageState === "STORED") {
                alreadyReadyCount += 1;
                return { attachmentId: id, transferId: 9000 + id, state: "STORED" };
            }
            if (item.storageState === "QUEUED" || item.storageState === "DOWNLOADING") {
                return { attachmentId: id, transferId: 9000 + id, state: item.storageState };
            }
            if (item.storageState === "SOURCE_UNAVAILABLE") {
                return { attachmentId: id, transferId: null, state: null };
            }
            item.storageState = "QUEUED";
            item.canFetch = false;
            acceptedCount += 1;
            return { attachmentId: id, transferId: 9000 + id, state: "QUEUED" };
        });
        const response = { items, acceptedCount, alreadyReadyCount };
        if (this.hooks.afterPost) this.hooks.afterPost(url, body, response);
        return response;
    }

    getCounts() {
        return { gets: this.gets.length, posts: this.posts.length };
    }
    lastGet() {
        return this.gets[this.gets.length - 1] || null;
    }
    getUrls() {
        return this.gets.map((record) => record.url);
    }
}

// ════════════════════════════════════════════════════════════════════════
// 组件 vm 沙箱
// ════════════════════════════════════════════════════════════════════════

function loadComponent(server) {
    const doc = new MiniDocument();
    const sandbox = {
        document: doc,
        URLSearchParams,
        AbortController,
        setTimeout,
        clearTimeout,
        console
    };
    sandbox.window = sandbox;
    sandbox.globalThis = sandbox;
    vm.createContext(sandbox);
    vm.runInContext(componentSource, sandbox, { filename: "expert-materials.js" });
    assert.strictEqual(typeof sandbox.ExpertMaterials, "object", "window.ExpertMaterials must be exported");
    sandbox.ExpertMaterials.configure({ api: server.apiAdapter(), contextPath: "", pollMs: 2000 });
    return sandbox;
}

function query(view, selector) {
    return view.root.querySelector(selector);
}

// ════════════════════════════════════════════════════════════════════════
// 提取 app.js 顶层函数（大括号感知；复用既有测试的模式）
// ════════════════════════════════════════════════════════════════════════

function extractFunction(name) {
    const pattern = new RegExp("(?:async\\s+)?function\\s+" + name + "\\s*\\(");
    const match = pattern.exec(appSource);
    if (!match) throw new Error(`Could not find function ${name} in app.js`);
    const openBrace = appSource.indexOf("{", match.index);
    if (openBrace === -1) throw new Error(`Could not extract function ${name}`);
    // 上下文栈：code（数自身花括号）/ tpl（模板文本）。模板 ${...} 内的代码
    // 进入独立 code 上下文，其结束花括号只弹回模板，不影响函数体计数。
    const fnCtx = { kind: "code", n: 0, fn: true };
    const stack = [fnCtx];
    let inString = null;
    let inComment = null;
    let i = openBrace;
    for (; i < appSource.length; i += 1) {
        const top = stack[stack.length - 1];
        const ch = appSource[i];
        const next = appSource[i + 1];
        if (top.kind === "tpl") {
            if (ch === "\\") { i += 1; continue; }
            if (ch === "`") { stack.pop(); continue; }
            if (ch === "$" && next === "{") {
                stack.push({ kind: "code", n: 0, fn: false });
                i += 1;
                continue;
            }
            continue;
        }
        // top.kind === "code"
        if (inComment === "line") {
            if (ch === "\n") inComment = null;
            continue;
        }
        if (inComment === "block") {
            if (ch === "*" && next === "/") { inComment = null; i += 1; }
            continue;
        }
        if (inString) {
            if (ch === "\\") { i += 1; continue; }
            if (ch === inString) inString = null;
            continue;
        }
        if (ch === "/" && next === "/") { inComment = "line"; i += 1; continue; }
        if (ch === "/" && next === "*") { inComment = "block"; i += 1; continue; }
        if (ch === '"' || ch === "'") { inString = ch; continue; }
        if (ch === "`") { stack.push({ kind: "tpl" }); continue; }
        if (ch === "{") { top.n += 1; continue; }
        if (ch === "}") {
            if (top.n === 0) {
                if (top.fn) {
                    return appSource.slice(match.index, i + 1);
                }
                stack.pop();
            } else {
                top.n -= 1;
                if (top.fn && top.n === 0) {
                    return appSource.slice(match.index, i + 1);
                }
            }
        }
    }
    throw new Error(`Could not extract function ${name}`);
}

// ════════════════════════════════════════════════════════════════════════
// 套件
// ════════════════════════════════════════════════════════════════════════

describe("expert-materials: I-1 同一 store 跨 inline/drawer host（无第二 fetch 队列）", () => {
    it("两 host 同一 contactId：一次初始 GET、同一行数据、提交只产生一次 POST", async () => {
        const server = new FakeServer({ count: 40, contactId: 7 });
        const sandbox = loadComponent(server);
        const { doc, host: inlineHost } = createHost();
        const drawerHost = doc.createElement("div");
        doc.root.appendChild(drawerHost);

        const inline = sandbox.ExpertMaterials.mount({ host: inlineHost, contactId: 7, mode: "inline" });
        await flush();
        const getsAfterInline = server.gets.length;
        assert.strictEqual(getsAfterInline, 1, "首次挂载应恰好一次 GET");
        assert.ok(server.gets[0].url.includes("/api/expert-contacts/7/materials?page=0&size=10"), "GET 必须是分页 materials 列表");

        const drawer = sandbox.ExpertMaterials.mount({ host: drawerHost, contactId: 7, mode: "drawer" });
        assert.ok(drawerHost.querySelector("dialog.em-drawer"), "drawer host 必须包含原生 dialog");
        await flush();
        assert.ok(server.gets.length <= 2, "第二 host 不得发起第二套列表加载（最多一次静默刷新）");

        const inlineRows = inlineHost.querySelectorAll(".em-row").length;
        const drawerRows = drawerHost.querySelectorAll(".em-row").length;
        assert.strictEqual(inlineRows, 10, "inline 每页 10 行");
        assert.strictEqual(drawerRows, 10, "drawer 与 inline 同一渲染");

        // 行勾选共享同一 selection store
        const inlineFirst = inlineHost.querySelectorAll(".em-row")[0].querySelector("input[type=checkbox]");
        inlineFirst.checked = true;
        change(inlineFirst);
        const drawerFirst = drawerHost.querySelectorAll(".em-row")[0].querySelector("input[type=checkbox]");
        assert.strictEqual(drawerFirst.checked, true, "drawer 的行勾选状态与 inline 共享");

        // 从 inline 提交 → 只一条 POST 且 payload 为该 expert 的所选 id
        const submitBtn = () => inlineHost.querySelector('[data-action="transfer"]');
        assert.strictEqual(submitBtn().disabled, false, "已选 1 份时主按钮可用");
        const drawerSubmit = drawerHost.querySelector('[data-action="transfer"]');
        click(submitBtn());
        // 提交在途：两个 host 的提交/清空按钮同步禁用（共享 store，单队列）
        assert.strictEqual(submitBtn().disabled, true, "提交中 inline 按钮禁用");
        assert.strictEqual(drawerSubmit.disabled, true, "提交中另一 host 按钮同步禁用");
        assert.ok(submitBtn().textContent.includes("正在提交"), "提交中文案为“正在提交…”");
        await flush();
        assert.strictEqual(server.posts.length, 1, "共享 store 只产生一次 POST");
        assert.deepStrictEqual(server.posts[0].body.attachmentIds, [1]);
        assert.strictEqual(drawerSubmit.disabled, false, "提交完成后按钮恢复");
        assert.strictEqual(submitBtn().disabled, false, "提交完成后 inline 按钮恢复");

        inline.unmount();
        drawer.unmount();
    });

    it("selectionOnly 与 inline 共享同一 store 与行渲染，只隐藏传输 footer", async () => {
        const server = new FakeServer({ count: 40, contactId: 9 });
        const sandbox = loadComponent(server);
        const { host: inlineHost } = createHost();
        const { host: pickerHost } = createHost();
        sandbox.ExpertMaterials.mount({ host: inlineHost, contactId: 9, mode: "inline" });
        await flush();
        sandbox.ExpertMaterials.mount({ host: pickerHost, contactId: 9, mode: "selectionOnly" });
        await flush();

        assert.strictEqual(inlineHost.querySelectorAll(".em-row").length, 10);
        assert.strictEqual(pickerHost.querySelectorAll(".em-row").length, 10, "selectionOnly 使用同一行渲染器");
        assert.ok(pickerHost.querySelector(".em-row input[type=checkbox]"), "selectionOnly 保留行勾选");
        assert.strictEqual(pickerHost.querySelector(".em-selection"), null, "selectionOnly 不渲染传输 footer 的选择框");
        assert.strictEqual(pickerHost.querySelector('[data-action="transfer"]'), null, "selectionOnly 无获取所选按钮");
        assert.strictEqual(pickerHost.querySelector('[data-action="clear"]'), null, "selectionOnly 无清空选择按钮");
        assert.ok(inlineHost.querySelector(".em-selection"), "inline 保留传输 footer");
    });
});

describe("expert-materials: I-2 请求 epoch / 陈旧响应 / 防抖 / GET 只读", () => {
    it("请求 epoch：先发搜索 q1 挂起，再发 q2 完成，迟到的 q1 响应被丢弃", async () => {
        const server = new FakeServer({ count: 40, contactId: 3 });
        const sandbox = loadComponent(server);
        sandbox.ExpertMaterials.configure({ debounceMs: 60 });
        const { host } = createHost();
        const instance = sandbox.ExpertMaterials.mount({ host, contactId: 3, mode: "inline" });
        await flush();
        assert.strictEqual(server.gets.length, 1, "初始 GET 完成");
        server.holdGets = true;
        const search = host.querySelector('.em-filters input[type="search"]');
        search.value = "附件-0";
        input(search);
        await wait(120); // 防抖后发出 q1 并挂起
        await flush();
        assert.strictEqual(server.gets.length, 2, "q1 搜索请求在途");
        search.value = "附件-0002";
        input(search);
        await wait(120); // 防抖后发出 q2（epoch 推进，q1 过期）
        await flush();
        assert.strictEqual(server.gets.length, 3, "q2 搜索请求在途");
        server.deferred[1].resolve(server.deferred[1].data);  // q2 先完成
        await flush();
        let rows = host.querySelectorAll(".em-row");
        assert.strictEqual(rows.length, 1, "q2 只命中 附件-0002");
        assert.strictEqual(rows[0].querySelector(".em-file strong").textContent, "附件-0002.pdf");
        assert.ok(host.querySelector(".em-pager span").textContent.includes("共 1 份"), "pager 显示 q2 的总数");
        server.deferred[0].resolve(server.deferred[0].data);  // q1 迟到
        await flush();
        rows = host.querySelectorAll(".em-row");
        assert.strictEqual(rows.length, 1, "迟到的 q1 响应被丢弃");
        assert.strictEqual(rows[0].querySelector(".em-file strong").textContent, "附件-0002.pdf", "DOM 保持 q2 结果");
        assert.ok(host.querySelector(".em-pager span").textContent.includes("共 1 份"), "总数仍为 q2 的 1 份");
        instance.unmount();
    });

    it("跨专家切换：A 的迟到响应绝不写入 B 的 DOM", async () => {
        const serverA = new FakeServer({ count: 12, contactId: 5 });
        const serverB = new FakeServer({ count: 12, contactId: 6 });
        serverA.holdGets = true;
        const sandboxA = loadComponent(serverA);
        const { host: hostA } = createHost();
        const viewA = sandboxA.ExpertMaterials.mount({ host: hostA, contactId: 5, mode: "inline" });
        await flush();
        assert.strictEqual(serverA.gets.length, 1);
        viewA.unmount(); // 切走专家：unmount 释放
        const sandboxB = loadComponent(serverB);
        const { host: hostB } = createHost();
        sandboxB.ExpertMaterials.mount({ host: hostB, contactId: 6, mode: "inline" });
        await flush();
        // A 的迟到响应此时才完成
        serverA.deferred[0].resolve(serverA.deferred[0].data);
        await flush();
        const bRows = hostB.querySelectorAll(".em-row");
        assert.strictEqual(bRows.length, 10, "B 面板已渲染自己的数据");
        const bNames = Array.from(bRows).map((row) => row.querySelector(".em-file strong").textContent);
        assert.ok(bNames.every((name) => name.startsWith("附件-00")), "B 只显示专家 B 的文件");
        assert.strictEqual(hostA.isConnected, true, "host 元素仍在，但其内容不应再变化");
    });

    it("搜索 300ms 防抖：连续输入只产生一次最终 q 的请求", async () => {
        const server = new FakeServer({ count: 40, contactId: 11 });
        const sandbox = loadComponent(server);
        sandbox.ExpertMaterials.configure({ debounceMs: 60 });
        const { host } = createHost();
        sandbox.ExpertMaterials.mount({ host, contactId: 11, mode: "inline" });
        await flush();
        const getsBefore = server.gets.length;
        const search = host.querySelector('.em-filters input[type="search"]');
        search.value = "附件-0";
        input(search);
        await wait(20);
        search.value = "附件-00";
        input(search);
        await wait(20);
        search.value = "附件-001";
        input(search);
        await wait(140); // 超过防抖窗口
        const getsAfter = server.gets.length;
        assert.strictEqual(getsAfter - getsBefore, 1, "防抖后只发一次搜索请求");
        assert.ok(server.lastGet().url.includes("q=%E9%99%84%E4%BB%B6-001"), "请求携带最终搜索词（URL 编码）");
        await flush();
        const rows = host.querySelectorAll(".em-row");
        assert.strictEqual(rows.length, 10);
        assert.ok(rows.every((row) => row.querySelector(".em-file strong").textContent.includes("附件-001")));
    });

    it("打开清单只 GET：无任何 POST/文件请求", async () => {
        const server = new FakeServer({ count: 10, contactId: 4 });
        const sandbox = loadComponent(server);
        const { host } = createHost();
        const instance = sandbox.ExpertMaterials.mount({ host, contactId: 4, mode: "inline" });
        await flush();
        assert.strictEqual(server.posts.length, 0, "仅打开/渲染不得 POST");
        assert.ok(server.gets.length >= 1);
        const bad = server.gets.some((record) => /download|preview|transfers|reconcile/i.test(record.url));
        assert.strictEqual(bad, false, "列表请求 URL 不含 download/preview/transfers/reconcile");
        instance.unmount();
    });
});

describe("expert-materials: I-3 1000 附件窗口 / 跨页选择 / 筛选保留 / 提交隔离 / 500 上限", () => {
    it("1000 条目录只渲染当前页 10 行，长文件名 title 可查看全文", async () => {
        const longName = "非常长的文件名-" + "X".repeat(300) + "-最终版本-final-final.pdf";
        const server = new FakeServer({
            count: 1000,
            fileName: (i) => (i === 1000 ? longName : `材料${String(i).padStart(4, "0")}-科研论文.pdf`)
        });
        const sandbox = loadComponent(server);
        const { host } = createHost();
        sandbox.ExpertMaterials.mount({ host, contactId: 2, mode: "inline" });
        await flush();
        assert.strictEqual(host.querySelectorAll(".em-row").length, 10, "1000 条也只渲染 10 行（窗口化）");
        const pagerInfo = host.querySelector(".em-pager > span").textContent;
        assert.ok(pagerInfo.includes("共 1000 份"), "pager 显示完整总数");
        assert.ok(pagerInfo.includes("第 1/100 页"), "pager 显示 100 页");
        // 翻到第 100 页（最后一页只含 id=1000）
        let next = nextBtn(host);
        let guard = 0;
        while (next && !next.disabled && guard < 100) {
            click(next);
            await flush();
            next = nextBtn(host);
            guard += 1;
        }
        const rows = host.querySelectorAll(".em-row");
        assert.ok(host.querySelector(".em-pager span").textContent.includes("第 100/100 页"), `停在第 100 页（循环 ${guard} 次）`);
        assert.strictEqual(rows.length, 10, "第 100 页仍只渲染 10 行");
        const longRow = Array.from(rows).find((row) => row.querySelector(".em-file strong").getAttribute("title") === longName);
        assert.ok(longRow, "1000 号长文件名文件出现在末页");
        const strong = longRow.querySelector(".em-file strong");
        assert.strictEqual(strong.getAttribute("title"), longName, "长文件名 title 保留全文");
        assert.strictEqual(strong.textContent.length, longName.length, "文本内容完整（可见省略仅由 CSS 完成）");
        sandbox.ExpertMaterials.unmountHostsIn(host);
    });

    it("翻两页勾 20 份，POST 恰好 20 个去重 id；筛选清空后仍显示已选 20", async () => {
        const server = new FakeServer({ count: 40, contactId: 13 });
        const sandbox = loadComponent(server);
        const { host } = createHost();
        sandbox.ExpertMaterials.mount({ host, contactId: 13, mode: "inline" });
        await flush();

        const headerCheck = () => host.querySelector(".em-table-head input[type=checkbox]");
        const selectPage = async () => {
            const check = headerCheck();
            check.checked = true;
            change(check);
            await flush();
        };
        await selectPage(); // page0: id 1..10
        click(nextBtn(host));
        await flush();
        await selectPage(); // page1: id 11..20
        assert.ok(host.querySelector(".em-selection strong").textContent.includes("已选 20 份"), "跨页已选 20 份");

        // 筛选（搜索）只改变可见行；选择计数保持 20，不被过滤隐藏/丢弃
        const search = host.querySelector('.em-filters input[type="search"]');
        search.value = "附件-00";
        input(search);
        await wait(400);
        await flush();
        assert.ok(host.querySelector(".em-selection strong").textContent.includes("已选 20 份"), "筛选后选择计数不变");
        assert.strictEqual(host.querySelectorAll(".em-row").length, 10, "筛选结果仍只渲染 10 行");

        // 清空筛选：恢复全量并提交
        search.value = "";
        input(search);
        await wait(400);
        await flush();
        assert.ok(host.querySelector(".em-selection strong").textContent.includes("已选 20 份"), "清空筛选后仍 20 份");
        const submit = host.querySelector('[data-action="transfer"]');
        assert.strictEqual(submit.disabled, false);
        click(submit);
        await flush();
        assert.strictEqual(server.posts.length, 1);
        const sent = server.posts[0].body.attachmentIds.slice().sort((a, b) => a - b);
        assert.deepStrictEqual(sent, Array.from({ length: 20 }, (_, i) => i + 1), "POST 恰好 20 个所选 id，无隐式全选");
        assert.strictEqual(server.posts[0].url.includes("/13/materials/transfers"), true, "POST 目标是该专家的 transfers");
        sandbox.ExpertMaterials.unmountHostsIn(host);
    });

    it("专家 B 永不收到专家 A 的提交；清空选择只清当前联系人", async () => {
        const serverA = new FakeServer({ count: 40, contactId: 21 });
        const serverB = new FakeServer({ count: 40, contactId: 22 });
        const sandboxA = loadComponent(serverA);
        const sandboxB = loadComponent(serverB);
        const { host: hostA } = createHost();
        const { host: hostB } = createHost();
        sandboxA.ExpertMaterials.mount({ host: hostA, contactId: 21, mode: "inline" });
        await flush();
        sandboxB.ExpertMaterials.mount({ host: hostB, contactId: 22, mode: "inline" });
        await flush();

        const aRow = hostA.querySelectorAll(".em-row")[0].querySelector("input[type=checkbox]");
        aRow.checked = true;
        change(aRow);
        click(hostA.querySelector('[data-action="transfer"]'));
        await flush();
        assert.strictEqual(serverA.posts.length, 1);
        assert.strictEqual(serverB.posts.length, 0, "B 不接收 A 的提交");
        assert.deepStrictEqual(serverA.posts[0].body.attachmentIds, [1]);

        // B 选择自己的文件后提交
        const bRow = hostB.querySelectorAll(".em-row")[0].querySelector("input[type=checkbox]");
        bRow.checked = true;
        change(bRow);
        click(hostB.querySelector('[data-action="transfer"]'));
        await flush();
        assert.strictEqual(serverB.posts.length, 1);
        assert.deepStrictEqual(serverB.posts[0].body.attachmentIds, [1], "B 提交的是 B 的 id 集合");
        // 清空只影响 A
        click(hostA.querySelector('[data-action="clear"]'));
        await flush();
        assert.ok(hostA.querySelector(".em-selection strong").textContent.includes("已选 0 份"));
        assert.ok(hostB.querySelector(".em-selection strong").textContent.includes("已选 1 份"), "B 的选择不受 A 清空影响");
        sandboxA.ExpertMaterials.unmountHostsIn(hostA);
        sandboxB.ExpertMaterials.unmountHostsIn(hostB);
    });

    it("超过 500 份上限：不发出 POST，错误区给出明确原因，选择保留", async () => {
        const server = new FakeServer({ count: 1000, contactId: 31 });
        const sandbox = loadComponent(server);
        const { host } = createHost();
        sandbox.ExpertMaterials.mount({ host, contactId: 31, mode: "inline" });
        await flush();
        const headerCheck = () => host.querySelector(".em-table-head input[type=checkbox]");
        const selectPage = async () => {
            const check = headerCheck();
            if (check.disabled) return false;
            check.checked = true;
            change(check);
            await flush();
            return true;
        };
        let guard = 0;
        while (guard < 60) {
            const next = nextBtn(host);
            if (next.disabled) break;
            await selectPage();
            click(next);
            await flush();
            guard += 1;
        }
        // 已选应远超 500（循环至少勾了 60 页），再补选当前页确保 >500
        await selectPage();
        const countText = host.querySelector(".em-selection strong").textContent;
        const selected = Number(countText.match(/已选 (\d+) 份/)[1]);
        assert.ok(selected >= 501, `已选应超过 500（实际 ${selected}）`);
        const postsBefore = server.posts.length;
        click(host.querySelector('[data-action="transfer"]'));
        await flush();
        assert.strictEqual(server.posts.length, postsBefore, "超过上限不得发出 POST");
        const errorBox = host.querySelector(".em-error");
        assert.strictEqual(errorBox.hidden, false, "超过上限必须显示原因");
        assert.ok(errorBox.textContent.includes("500"), "错误文案说明 500 份上限");
        assert.ok(host.querySelector(".em-selection strong").textContent.includes(`已选 ${selected} 份`), "失败保留选择");
        sandbox.ExpertMaterials.unmountHostsIn(host);
    });
});

describe("expert-materials: 轮询 / unmount 释放 / 抽屉关闭队列继续", () => {
    it("活动任务可见时按 2s 轮询（测试注入短周期），unmount 停止轮询并释放全部监听", async () => {
        const server = new FakeServer({ count: 10, contactId: 41 });
        const sandbox = loadComponent(server);
        sandbox.ExpertMaterials.configure({ pollMs: 30 });
        const { host } = createHost();
        const instance = sandbox.ExpertMaterials.mount({ host, contactId: 41, mode: "inline" });
        await flush();
        // 勾选一行 METADATA_ONLY（id=2）提交制造 QUEUED 活动任务
        const rowCheck = host.querySelectorAll(".em-row")[1].querySelector("input[type=checkbox]");
        rowCheck.checked = true;
        change(rowCheck);
        click(host.querySelector('[data-action="transfer"]'));
        await flush();
        const getsAtSubmit = server.gets.length;
        await wait(150); // 多个轮询周期
        const getsPolling = server.gets.length;
        assert.ok(getsPolling > getsAtSubmit, "活动任务期间轮询刷新（GET 数增长）");
        // 提交后的行显示 QUEUED + progress
        const stateCell = host.querySelectorAll(".em-row")[1].querySelector(".em-state");
        assert.strictEqual(stateCell.getAttribute("data-state"), "QUEUED");
        assert.ok(stateCell.querySelector("progress"), "QUEUED 行含 progress 元素");

        const rootBefore = host.querySelector(".expert-materials");
        instance.unmount();
        await wait(120);
        const getsAfterUnmount = server.gets.length;
        assert.strictEqual(getsAfterUnmount, getsPolling, "unmount 后轮询停止，GET 不再增长");
        assert.strictEqual(host.querySelector(".expert-materials"), null, "组件 DOM 已释放");
        assert.strictEqual(countTreeListeners(rootBefore), 0, "unmount 释放该视图全部监听");
        assert.strictEqual(host.listenerCount(), 0, "host 自身无遗留监听");
    });

    it("drawer 关闭（Esc/关闭按钮同路径）只释放 UI：另一 host 轮询继续、重开状态一致", async () => {
        const server = new FakeServer({ count: 20, contactId: 42 });
        const sandbox = loadComponent(server);
        sandbox.ExpertMaterials.configure({ pollMs: 40 });
        const { doc, host: inlineHost } = createHost();
        const drawerHost = doc.createElement("div");
        doc.root.appendChild(drawerHost);
        sandbox.ExpertMaterials.mount({ host: inlineHost, contactId: 42, mode: "inline" });
        await flush();
        const drawer = sandbox.ExpertMaterials.mount({ host: drawerHost, contactId: 42, mode: "drawer" });
        await flush();
        // 通过 drawer 提交一行 METADATA_ONLY（id=2）→ QUEUED
        const dCheck = drawerHost.querySelectorAll(".em-row")[1].querySelector("input[type=checkbox]");
        dCheck.checked = true;
        change(dCheck);
        click(drawerHost.querySelector('[data-action="transfer"]'));
        await flush();
        const getsAfterSubmit = server.gets.length;
        // 关闭 drawer（关闭按钮 → dialog.close → close 事件 → 视图释放）
        click(drawerHost.querySelector('[data-action="close"]'));
        await flush();
        assert.strictEqual(drawerHost.querySelector("dialog.em-drawer"), null, "关闭后 drawer UI 已释放");
        await wait(160);
        assert.ok(server.gets.length > getsAfterSubmit, "inline host 仍在 → 服务器队列任务继续轮询");
        const drawer2 = sandbox.ExpertMaterials.mount({ host: drawerHost, contactId: 42, mode: "drawer" });
        await flush();
        const stateCell = drawerHost.querySelectorAll(".em-row")[1].querySelector(".em-state");
        assert.ok(["QUEUED", "STORED", "DOWNLOADING"].includes(stateCell.getAttribute("data-state")), "重开 drawer 看到服务器任务真实状态");
        // 显式清理（关闭 drawer 后只剩 inline 视图）
        drawer2.unmount();
        sandbox.ExpertMaterials.unmountHostsIn(inlineHost);
        await flush();
    });
});

describe("expert-materials: I-4 app.js 宿主 —— 无 contactId 空态与真实 contactId 挂载", () => {
    it("app.js 的 loadContactDetail 在 DOM 写入前释放旧视图、写入后按真实 contactId 挂载", () => {
        const fn = extractFunction("loadContactDetail");
        const renderFn = extractFunction("renderExpertDocuments");
        const unmountPos = fn.indexOf('typeof unmountExpertMaterialsHosts === "function"');
        const innerHtmlPos = fn.indexOf("contactDetail.innerHTML = `");
        const mountPos = fn.indexOf("mountExpertMaterialsInline(contact.id, contactDetail)");
        assert.ok(unmountPos > -1, "loadContactDetail 必须包含组件卸载守卫");
        assert.ok(mountPos > -1, "loadContactDetail 必须包含 inline 挂载调用");
        assert.ok(unmountPos < innerHtmlPos && innerHtmlPos < mountPos, "顺序：unmount → innerHTML 写入 → mount");
        assert.ok(fn.indexOf("renderExpertDocuments(documents, contactId)") > -1, "loadContactDetail 经 renderExpertDocuments 输出资料区");
        assert.ok(renderFn.indexOf("data-expert-materials-host") > -1, "组件存在时 host 由 renderExpertDocuments 输出");
        assert.ok(!fn.includes("ExpertMaterials.mount({ host, contactId: null"), "不得向 null contactId 挂载");
        assert.ok(renderFn.indexOf("尚未建立联系") === -1, "renderExpertDocuments 不含无联系文案（该文案属于 showExpertDetail 空态）");
    });

    it("showExpertDetail（无 contactId 的 ES 专家）渲染静态空态，零材料请求、零挂载", async () => {
        const serverCalls = [];
        const mountCalls = [];
        const windowStub = {
            ExpertMaterials: {
                configure: () => {},
                mount: (opts) => { mountCalls.push(opts); return { unmount() {} }; },
                unmountHostsIn: () => 0
            }
        };
        const elements = {};
        function makeEl() {
            return {
                hidden: true,
                innerHTML: "",
                scrollTop: 0,
                classList: { add() {}, remove() {}, contains() { return false; } },
                addEventListener() {}, removeEventListener() {},
                querySelector() { return null; }
            };
        }
        function $(sel) {
            if (!elements[sel]) elements[sel] = makeEl();
            return elements[sel];
        }
        const sandbox = {
            window: windowStub,
            console,
            URLSearchParams,
            $,
            showStatus() {},
            api: async (url) => {
                serverCalls.push(url);
                if (String(url).includes("/api/experts/profile")) throw new Error("es down");
                return { found: true, tags: [] };
            },
            escapeHtml: (v) => String(v == null ? "" : v),
            backToListBtnHtml: () => "",
            fetchExpertTagsFromEs: async () => ({ found: true, tags: [] }),
            renderExpertTagEditor: () => "",
            indexLevelLabels: {},
            renderDetailSubTabs: () => "",
            renderAcademicProfilePanel: () => "",
            badge: () => "",
            labelStatus: () => "",
            renderKeywords: () => "",
            requestAnimationFrame: () => {},
            expertMaterialsAvailable: () => true
        };
        const ctx = vm.createContext(sandbox);
        // 真实 app.js 的 renderNoContactMaterialsEmpty 与 showExpertDetail 注入同一沙箱
        const noContactFn = vm.runInContext(`(${extractFunction("renderNoContactMaterialsEmpty")})`, ctx);
        sandbox.renderNoContactMaterialsEmpty = noContactFn;
        const showFn = vm.runInContext(`(${extractFunction("showExpertDetail")})`, ctx);
        await showFn.call(sandbox, {
            orcidId: "0000-0000-0000-0001",
            displayName: "无联系专家",
            email: "none@example.org",
            indexLevel: "CANDIDATE",
            contactId: null
        });

        const html = elements["#contactDetail"].innerHTML;
        assert.ok(html.includes("尚未建立联系，暂无资料"), "无 contactId 必须显示明确空态");
        assert.ok(html.includes('class="expert-materials"'), "空态使用 S-1 面板样式");
        assert.ok(html.includes("0 份"), "空态计数为 0 份");
        assert.strictEqual(mountCalls.length, 0, "无 contactId 不挂载材料组件");
        const materialRequests = serverCalls.filter((url) => /materials|documents/.test(String(url)));
        assert.strictEqual(materialRequests.length, 0, "无 contactId 不请求 materials/documents");
        assert.ok(!html.includes("document-row"), "ES 空态不输出旧 document-row");
    });

    it("showExpertDetail 不挂载网络组件（入口真实挂载只发生在 loadContactDetail）", () => {
        const fn = extractFunction("showExpertDetail");
        assert.ok(fn.indexOf('typeof unmountExpertMaterialsHosts === "function"') > -1, "showExpertDetail 在写 DOM 前也释放旧组件视图");
        assert.ok(fn.indexOf("mountExpertMaterialsInline(") === -1, "showExpertDetail 本身不挂载网络组件（无 contactId）");
        assert.ok(fn.indexOf("noContactMaterialsHtml") > -1, "空态由 noContactMaterialsHtml 变量注入");
    });

    it("mountExpertMaterialsInline 只对真实正 contactId 挂载并注入 api/contextPath", async () => {
        const server = new FakeServer({ count: 10, contactId: 77 });
        const doc = new MiniDocument();
        const hostEl = doc.createElement("div");
        doc.root.appendChild(hostEl);
        const componentSandbox = {
            document: doc,
            URLSearchParams,
            AbortController,
            setTimeout,
            clearTimeout,
            console
        };
        componentSandbox.window = componentSandbox;
        componentSandbox.globalThis = componentSandbox;
        vm.createContext(componentSandbox);
        vm.runInContext(componentSource, componentSandbox, { filename: "expert-materials.js" });

        const hostDiv = doc.createElement("div");
        hostDiv.setAttribute("data-expert-materials-host", "");
        hostDiv.setAttribute("data-contact-id", "77");
        hostEl.appendChild(hostDiv);

        const sandbox = {
            window: componentSandbox,
            console,
            URLSearchParams,
            api: server.apiAdapter(),
            contextPath: "/talent",
            labelDocumentType: (v) => v || "",
            labelDocumentStatus: (v) => v || "",
            formatFileSize: (v) => String(v),
            expertMaterialsAvailable: () => true
        };
        const ctx = vm.createContext(sandbox);
        const mountFn = vm.runInContext(`(${extractFunction("mountExpertMaterialsInline")})`, ctx);
        const result = await mountFn.call(sandbox, 77, hostEl);
        assert.ok(result && typeof result.unmount === "function", "mount 返回带 unmount 的实例");
        await flush();
        assert.strictEqual(server.gets.length, 1, "挂载即发一次分页 GET");
        assert.ok(server.gets[0].url.includes("/api/expert-contacts/77/materials?page=0&size=10"));
        assert.ok(hostDiv.querySelector(".expert-materials"), "组件面板渲染进 data host");
        // 无效/缺失 contactId：不挂载、不发请求
        assert.strictEqual(await mountFn.call(sandbox, null, hostEl), null, "null contactId 返回 null 不挂载");
        assert.strictEqual(await mountFn.call(sandbox, 0, hostEl), null, "0/NaN contactId 返回 null 不挂载");
        result.unmount();
        assert.strictEqual(hostDiv.querySelector(".expert-materials"), null);
        assert.strictEqual(server.gets.length, 1, "无效 id 不发任何请求");
    });

    it("app.js 旧路径（无 window.ExpertMaterials）保持原渲染：renderExpertDocuments 仍输出 document-row", () => {
        // 组件不存在时（资源注册前）renderExpertDocuments 必须维持原行为。
        const sandbox = {
            expertMaterialsAvailable: () => false,
            contextPath: "",
            escapeHtml: (v) => String(v == null ? "" : v),
            labelDocumentType: (v) => v || "",
            labelDocumentStatus: (v) => v || "",
            formatFileSize: () => "1 KB"
        };
        const ctx = vm.createContext(sandbox);
        const legacy = vm.runInContext(`(${extractFunction("renderExpertDocuments")})`, ctx);
        const html = legacy([
            { fileName: "a.pdf", documentType: "CV", documentStatus: "PENDING_REVIEW", fileSize: 1024, downloadUrl: "/x", createdAt: "" }
        ], 1);
        assert.ok(html.includes("document-row"), "无组件时保留 document-row 渲染");
        assert.ok(html.includes("AI 智能分析"), "无组件时保留原 AI 按钮");
        assert.ok(!html.includes("data-expert-materials-host"), "无组件时不输出 data host");
    });
});

function countTreeListeners(root) {
    let count = root.listenerCount();
    root.childNodes.forEach((child) => {
        if (child.nodeType === 1) count += countTreeListeners(child);
    });
    return count;
}
