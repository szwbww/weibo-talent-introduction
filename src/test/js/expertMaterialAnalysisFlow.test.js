"use strict";

// 子计划 09 行为测试：AI 选件衔接共享材料组件（S-2 / I-1..I-3）。
// 真实 DOM 能力足够的最小树（innerHTML 解析 + querySelector/closest/事件冒泡/
// dataset），绝不是“getElementById 恒返回元素”的空 stub —— 与 K-dom-stub-tests-
// hide-dangling-refs 同理，本文件另外对 app.js 自身渲染模板做源文本存在性断言。
//
// 覆盖：
// - I-1：打开 AI 零 POST、零文件抓取（只 GET 元数据+历史）；已选带入可分析子集、
//   JPEG 行 checkbox 禁用并说明“当前不支持图片文字识别”；无已选默认 CV/学位跨页勾选；
//   默认候选 >500 只提示不悄悄截断；提交冻结快照，之后取消勾选/翻页不漂移。
// - I-2：缺 M 件只获取 M 件；全部 STORED 后恰好一次 ai-analysis；任何失败 → 0 次分析
//   并展示失败 attachmentId/文件名/原因；重试只请求失败项，重试后就绪仍按原快照一次分析。
// - I-3：关窗销毁 intentToken（下载继续、零后续自动分析、重开不自动提交）；
//   历史结果绝不被前端清空（无 DELETE/clear）；空文本 PDF 显示逐文件原因；
//   结果编辑（PUT）沿用原接口。

const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const ROOT = path.join(__dirname, "..", "..", "main", "resources", "static");
const componentSource = fs.readFileSync(path.join(ROOT, "expert-materials.js"), "utf-8");
const appSource = fs.readFileSync(path.join(ROOT, "app.js"), "utf-8");

// ════════════════════════════════════════════════════════════════════════
// 最小真实 DOM（test-only）：innerHTML 解析 + id 注册 + 事件冒泡
// ════════════════════════════════════════════════════════════════════════

function decodeEntities(value) {
    return String(value)
        .replaceAll("&amp;", "&")
        .replaceAll("&lt;", "<")
        .replaceAll("&gt;", ">")
        .replaceAll("&quot;", '"')
        .replaceAll("&#039;", "'")
        .replaceAll("&#39;", "'")
        .replaceAll("&nbsp;", " ");
}

const VOID_TAGS = new Set(["input", "br", "hr", "img", "meta", "link", "area", "base", "embed", "source", "track", "wbr", "col"]);

class MiniEvent {
    constructor(type, opts) {
        this.type = type;
        this.target = null;
        this.currentTarget = null;
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

function collectText(node) {
    if (node.nodeType === 3) return node.data;
    return node.childNodes.map((child) => collectText(child)).join("");
}

function parseToken(token) {
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

function selectorMatches(el, tokens) {
    if (!el || el.nodeType !== 1) return false;
    const matchToken = (el2, token) => {
        if (token.tag && el2.tagName.toLowerCase() !== token.tag) return false;
        if (token.id && el2.getAttribute("id") !== token.id) return false;
        for (const cls of token.classes) {
            if (!el2.classList.contains(cls)) return false;
        }
        for (const { name, value } of token.attrs) {
            const actual = el2.getAttribute(name);
            if (value === null) {
                if (actual === null) return false;
            } else if (actual !== value) {
                return false;
            }
        }
        return true;
    };
    if (tokens.length === 1) return matchToken(el, tokens[0]);
    const target = tokens[tokens.length - 1];
    if (!matchToken(el, target)) return false;
    let ancestor = el.parentNode;
    for (let idx = tokens.length - 2; idx >= 0; idx -= 1) {
        while (ancestor && ancestor.nodeType === 1 && !matchToken(ancestor, tokens[idx])) {
            ancestor = ancestor.parentNode;
        }
        if (!ancestor || ancestor.nodeType !== 1) return false;
        ancestor = ancestor.parentNode;
    }
    return true;
}

function unregisterIds(node) {
    if (node.nodeType !== 1) return;
    const id = node.getAttribute && node.getAttribute("id");
    if (id && node.ownerDocument) node.ownerDocument._unregisterId(id);
    (node.childNodes || []).forEach((child) => unregisterIds(child));
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
        const raw = String(value);
        if (name === "id" && this.ownerDocument) {
            const prev = this.attributes.get("id");
            if (prev && prev !== raw) this.ownerDocument._unregisterId(prev);
            this.ownerDocument._registerId(raw, this);
        }
        if (name.startsWith("data-") && this._dataset) {
            const key = name.slice(5).replace(/-([a-z])/g, (_, c) => c.toUpperCase());
            this._dataset[key] = String(value);
        }
        this.attributes.set(name, raw);
        if (name === "class") {
            this._classes = new Set(raw.split(/\s+/).filter(Boolean));
        }
        if (name === "value") this._value = raw;
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
        if (name === "id" && this.ownerDocument) {
            this.ownerDocument._unregisterId(this.getAttribute("id"));
        }
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
    set type(value) {
        this.attributes.set("type", String(value));
    }
    get type() {
        return this.getAttribute("type") || "";
    }
    set innerHTML(html) {
        (this.childNodes.slice() || []).forEach((child) => this.removeChild(child));
        if (html === "" || html === null || html === undefined) return;
        this.ownerDocument._parseInto(this, String(html));
    }
    get innerHTML() {
        const serialize = (node) => {
            if (node.nodeType === 3) return node.data;
            const attrs = [...node.attributes.entries()]
                .map(([k, v]) => (v === "" ? ` ${k}` : ` ${k}="${String(v).replaceAll('"', "&quot;")}"`))
                .join("");
            const inner = node.childNodes.map((c) => serialize(c)).join("");
            return `<${node.tagName.toLowerCase()}${attrs}>${inner}</${node.tagName.toLowerCase()}>`;
        };
        return this.childNodes.map((c) => serialize(c)).join("");
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
        unregisterIds(child);
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
        this.childNodes.forEach((child) => unregisterIds(child));
        this.childNodes = [];
        if (value !== "" && value !== null && value !== undefined) {
            this.childNodes.push(new MiniText(String(value)));
        }
    }
    get textContent() {
        return collectText(this);
    }
    matches(selector) {
        const tokens = String(selector).trim().split(/\s+/)
            .filter((part) => part && part !== ">")
            .map(parseToken);
        return selectorMatches(this, tokens);
    }
    closest(selector) {
        const tokens = String(selector).trim().split(/\s+/)
            .filter((part) => part && part !== ">")
            .map(parseToken);
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
        const tokens = String(selector).trim().split(/\s+/)
            .filter((part) => part && part !== ">")
            .map(parseToken);
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

class MiniDocument {
    constructor() {
        this.root = new MiniElement("#document", this);
        this.root.nodeType = 9;
        this._ids = new Map();
    }
    _registerId(id, element) {
        this._ids.set(String(id), element);
    }
    _unregisterId(id) {
        this._ids.delete(String(id));
    }
    createElement(tag) {
        return new MiniElement(tag, this);
    }
    createTextNode(data) {
        return new MiniText(String(data));
    }
    getElementById(id) {
        return this._ids.get(String(id)) || null;
    }
    querySelector(selector) {
        return this.root.querySelector(selector);
    }
    querySelectorAll(selector) {
        return this.root.querySelectorAll(selector);
    }
    _parseInto(parent, html) {
        const stack = [];
        let i = 0;
        const text = html;
        const pushText = (raw) => {
            if (!raw) return;
            const decoded = decodeEntities(raw);
            if (!decoded) return;
            const current = stack.length ? stack[stack.length - 1] : parent;
            current.childNodes.push(new MiniText(decoded));
            decoded; // noop
        };
        while (i < text.length) {
            const lt = text.indexOf("<", i);
            if (lt === -1) {
                pushText(text.slice(i));
                break;
            }
            pushText(text.slice(i, lt));
            if (text.startsWith("<!--", lt)) {
                const end = text.indexOf("-->", lt + 4);
                i = end === -1 ? text.length : end + 3;
                continue;
            }
            const closeMatch = /^<\s*\/([a-zA-Z][a-zA-Z0-9-]*)\s*>/.exec(text.slice(lt));
            if (closeMatch) {
                const tag = closeMatch[1].toLowerCase();
                if (stack.length && stack[stack.length - 1].tagName.toLowerCase() === tag) {
                    stack.pop();
                }
                i = lt + closeMatch[0].length;
                continue;
            }
            const openMatch = /^<\s*([a-zA-Z][a-zA-Z0-9-]*)([^>]*)>/.exec(text.slice(lt));
            if (!openMatch) {
                pushText(text.slice(lt, lt + 1));
                i = lt + 1;
                continue;
            }
            const tag = openMatch[1].toLowerCase();
            const attrText = openMatch[2];
            const element = this.createElement(tag);
            let a = 0;
            while (a < attrText.length) {
                while (a < attrText.length && /\s/.test(attrText[a])) a += 1;
                if (a >= attrText.length) break;
                const nameMatch = /^[^\s=/>]+/.exec(attrText.slice(a));
                if (!nameMatch) { a += 1; continue; }
                const name = nameMatch[0];
                a += nameMatch[0].length;
                let value = "";
                while (a < attrText.length && /\s/.test(attrText[a])) a += 1;
                if (attrText[a] === "=") {
                    a += 1;
                    while (a < attrText.length && /\s/.test(attrText[a])) a += 1;
                    if (attrText[a] === '"' || attrText[a] === "'") {
                        const quote = attrText[a];
                        const end = attrText.indexOf(quote, a + 1);
                        value = attrText.slice(a + 1, end === -1 ? attrText.length : end);
                        a = end === -1 ? attrText.length : end + 1;
                    } else {
                        const endMatch = /^[^\s>]*/.exec(attrText.slice(a));
                        value = endMatch ? endMatch[0] : "";
                        a += value.length;
                    }
                }
                element.setAttribute(name, decodeEntities(value));
            }
            const current = stack.length ? stack[stack.length - 1] : parent;
            current.appendChild(element);
            if (!VOID_TAGS.has(tag)) {
                stack.push(element);
            }
            i = lt + openMatch[0].length;
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
// 伪后端：真实分页/状态机/transfer/analysis 语义（可多联系人）
// ════════════════════════════════════════════════════════════════════════

const CONTACT_RE = /\/api\/expert-contacts\/(\d+)\//;

function defaultSummaryFor(catalog) {
    const summary = { total: 0, stored: 0, metadataOnly: 0, active: 0, failed: 0, sourceUnavailable: 0, defaultAnalysisAttachmentIds: [] };
    catalog.forEach((item) => {
        summary.total += 1;
        if (item.storageState === "STORED") summary.stored += 1;
        else if (item.storageState === "METADATA_ONLY") {
            summary.metadataOnly += 1;
            if ((item.documentType === "CV" || item.documentType === "PHD_DEGREE" ||
                item.documentType === "MASTER_DEGREE" || item.documentType === "BACHELOR_DEGREE") &&
                item.analysisSupported === true) {
                summary.defaultAnalysisAttachmentIds.push(item.attachmentId);
            }
        } else if (item.storageState === "QUEUED" || item.storageState === "DOWNLOADING") summary.active += 1;
        else if (item.storageState === "FAILED") summary.failed += 1;
        else summary.sourceUnavailable += 1;
    });
    summary.defaultAnalysisAttachmentIds.sort((a, b) => a - b);
    return summary;
}

function makeItem(id, opts) {
    const options = opts || {};
    const fileName = options.fileName || `材料-${String(id).padStart(3, "0")}.pdf`;
    const contentType = options.contentType || "application/pdf";
    const state = options.state || "METADATA_ONLY";
    const analysisSupported = options.analysisSupported !== undefined ? options.analysisSupported
        : contentType === "application/pdf" || contentType.startsWith("text/");
    return {
        attachmentId: id,
        documentId: 1000 + id,
        source: options.noSource ? null
            : { type: "INBOUND_PROCESSING", id: 2000 + (id % 3), subject: `来信 ${id % 3}`, receivedAt: "2026-09-07T14:42:00", accountCode: "test" },
        fileName,
        contentType,
        documentType: options.documentType || "OTHER",
        documentStatus: "PENDING_REVIEW",
        actualSize: state === "STORED" ? 182500 : null,
        encodedSize: 182500,
        storageState: state,
        bytesDownloaded: state === "DOWNLOADING" ? 64000 : 0,
        error: options.error || null,
        canFetch: state === "METADATA_ONLY" || state === "FAILED",
        canDownload: state === "STORED",
        canPreview: state === "STORED",
        analysisSupported,
        canAnalyze: analysisSupported && state === "STORED",
        downloadUrl: state === "STORED" ? `/api/expert-contacts/1/attachments/${id}/download` : null,
        previewUrl: state === "STORED" ? `/api/expert-contacts/1/attachments/${id}/preview` : null
    };
}

class FakeServer {
    constructor(contacts) {
        // contacts: { contactId: { catalog, history?, analysisError?, summaryOverride?, onAnalysis? } }
        this.contacts = new Map();
        Object.keys(contacts).forEach((key) => {
            const spec = contacts[key];
            this.contacts.set(Number(key), {
                catalog: (spec.catalog || []).map((row) => Object.assign({}, row)),
                history: spec.history || [],
                analysisError: spec.analysisError || null,
                summaryOverride: spec.summaryOverride || null,
                onAnalysis: spec.onAnalysis || null,
                defaultIds: spec.defaultIds || null
            });
        });
        this.gets = [];
        this.posts = [];
        this.puts = [];
        this.analysisCalls = [];
        this.transferCalls = [];
        this.deleteCalls = [];
    }
    apiAdapter() {
        const server = this;
        return async function api(pathArg, options) {
            const opts = options || {};
            const url = String(pathArg);
            const method = opts.method || "GET";
            if (method === "POST") {
                server.posts.push({ url, body: JSON.parse(opts.body || "{}"), options: opts });
                return server.handlePost(url, JSON.parse(opts.body || "{}"));
            }
            if (method === "PUT") {
                server.puts.push({ url, body: JSON.parse(opts.body || "{}"), options: opts });
                return { ok: true };
            }
            if (method === "DELETE") {
                server.deleteCalls.push({ url });
                return { ok: true };
            }
            server.gets.push({ url, options: opts });
            return server.handleGet(url);
        };
    }
    resolve(url) {
        const match = CONTACT_RE.exec(url);
        const contactId = match ? Number(match[1]) : null;
        const spec = this.contacts.get(contactId) || { catalog: [], history: [] };
        return { contactId, spec, path: url.replace(CONTACT_RE, "") };
    }
    handleGet(url) {
        const { spec, path } = this.resolve(url);
        if (path.startsWith("ai-analysis")) {
            return { fields: spec.history || [] };
        }
        if (path.startsWith("documents")) {
            return { records: spec.catalog || [] };
        }
        if (path.startsWith("materials")) {
            const queryIndex = path.indexOf("?");
            const raw = queryIndex === -1 ? "" : path.slice(queryIndex + 1);
            const params = new URLSearchParams(raw);
            const page = Number(params.get("page") || "0");
            const size = Number(params.get("size") || "10");
            const q = (params.get("q") || "").trim().toLowerCase();
            const state = params.get("state") || "";
            let filtered = (spec.catalog || []).filter((item) => {
                if (q && !item.fileName.toLowerCase().includes(q)) return false;
                if (state && item.storageState !== state) return false;
                return true;
            });
            const total = filtered.length;
            const pageItems = filtered.slice(page * size, page * size + size);
            const summary = spec.summaryOverride
                ? Object.assign(defaultSummaryFor(spec.catalog), spec.summaryOverride)
                : defaultSummaryFor(spec.catalog);
            return { items: pageItems, total, page, size, summary };
        }
        return { items: [], total: 0, page: 0, size: 10, summary: defaultSummaryFor([]) };
    }
    handlePost(url, body) {
        const { spec, path } = this.resolve(url);
        if (path.startsWith("materials/transfers")) {
            const ids = (body.attachmentIds || []).map(Number);
            this.transferCalls.push({ url, ids });
            let acceptedCount = 0;
            let alreadyReadyCount = 0;
            const items = ids.map((id) => {
                const item = spec.catalog.find((entry) => entry.attachmentId === id);
                if (!item) return { attachmentId: id, transferId: null, state: null };
                if (item.storageState === "STORED") {
                    alreadyReadyCount += 1;
                    return { attachmentId: id, transferId: 9000 + id, state: "STORED" };
                }
                if (item.storageState === "QUEUED" || item.storageState === "DOWNLOADING") {
                    return { attachmentId: id, transferId: 9000 + id, state: item.storageState };
                }
                if (item.storageState === "SOURCE_UNAVAILABLE") {
                    return { attachmentId: id, transferId: null, state: "SOURCE_UNAVAILABLE" };
                }
                item.storageState = "QUEUED";
                item.canFetch = false;
                acceptedCount += 1;
                return { attachmentId: id, transferId: 9000 + id, state: "QUEUED" };
            });
            return { items, acceptedCount, alreadyReadyCount };
        }
        if (path.startsWith("ai-analysis")) {
            const ids = (body.attachmentIds || []).map(Number);
            this.analysisCalls.push({ url, ids });
            const itemById = (id) => (spec.catalog || []).find((entry) => entry.attachmentId === id);
            ids.forEach((id) => {
                const item = itemById(id);
                if (!item || item.storageState !== "STORED") {
                    const err = new Error(`附件 #${id} 未就绪，请先获取该文件（MATERIAL_NOT_READY）`);
                    err.status = 409;
                    throw err;
                }
            });
            if (spec.analysisError) {
                if (typeof spec.analysisError === "function") {
                    const message = spec.analysisError(ids, spec);
                    if (message) throw new Error(message);
                } else {
                    throw new Error(String(spec.analysisError));
                }
            }
            const fields = ids.map((id, index) => ({
                id: 500 + id,
                fieldKey: `field_${id}`,
                fieldLabel: `字段 ${id}`,
                value: `值 ${id}`,
                sourceAttachmentId: id,
                sourceFileName: itemById(id) ? itemById(id).fileName : null,
                sourceExcerpt: "sample",
                verified: true,
                displayOrder: index
            }));
            if (spec.onAnalysis) spec.onAnalysis(ids, fields);
            spec.history = fields;
            return { fields };
        }
        return {};
    }
    /** 测试驱动：模拟服务端 worker 推进某附件状态（随后 GET 即反映）。 */
    setState(contactId, attachmentId, state, error) {
        const spec = this.contacts.get(Number(contactId));
        const item = spec && spec.catalog.find((entry) => entry.attachmentId === Number(attachmentId));
        if (!item) throw new Error(`setState: attachment ${attachmentId} not found`);
        item.storageState = state;
        item.error = error || null;
        item.canFetch = state === "METADATA_ONLY" || state === "FAILED";
        item.canDownload = state === "STORED";
        if (state === "STORED") item.actualSize = 182500;
    }
    analysisUrlCalls() {
        return this.analysisCalls.map((call) => call.ids);
    }
    transferIds() {
        return this.transferCalls.map((call) => call.ids);
    }
}

// ════════════════════════════════════════════════════════════════════════
// 组件 + app.js 提取函数沙箱
// ════════════════════════════════════════════════════════════════════════

const AI_MODAL_SKELETON = `
<div class="modal-shell" id="aiAnalysisModal" hidden>
    <button class="modal-backdrop" id="aiAnalysisModalBackdrop" type="button" aria-label="关闭 AI 分析"></button>
    <section class="panel editor-panel modal-panel ai-analysis-modal" role="dialog" aria-modal="true" aria-labelledby="aiAnalysisModalTitle">
        <div class="panel-head modal-head">
            <h2 id="aiAnalysisModalTitle">AI 智能分析</h2>
            <button class="button secondary" id="aiAnalysisModalCloseBtn" type="button" data-action="close-ai-analysis">×</button>
        </div>
        <div class="modal-body ai-analysis-modal-body" id="aiAnalysisModalBody"></div>
        <div class="modal-actions ai-analysis-modal-footer" id="aiAnalysisModalFooter"></div>
    </section>
</div>
`;

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

const AI_FUNCTIONS = [
    "aiAnalysisMaterialsCapable",
    "enterAiAnalysisSelectMode",
    "teardownAiAnalysisPicker",
    "teardownAiAnalysisSession",
    "renderAiAnalysisMaterialsSelect",
    "handleAiMaterialsSnapshot",
    "applyAiAnalysisEntrySelectionIfNeeded",
    "aiAnalysisSelectionIds",
    "aiAnalysisItemStorageState",
    "aiAnalysisFailureLines",
    "updateAiAnalysisSelectChrome",
    "stepAiAnalysisRunIfReady",
    "retryAiAnalysisFetch",
    "fireAiAnalysis",
    "isDefaultAiAnalysisDocument",
    "openAiAnalysisModal",
    "closeAiAnalysisModal",
    "renderAiAnalysisFileSelect",
    "renderAiAnalysisResults",
    "renderAiAnalysisModal",
    "startAiAnalysis",
    "saveAiAnalysisField",
    "addAiAnalysisField"
];

function bootSandbox(server, options) {
    const opts = options || {};
    const doc = new MiniDocument();
    const appHost = doc.createElement("div");
    appHost.id = "appHost";
    appHost.setAttribute("id", "appHost");
    doc.root.appendChild(appHost);
    const inlineHost = doc.createElement("div");
    inlineHost.id = "inlineHost";
    inlineHost.setAttribute("id", "inlineHost");
    doc.root.appendChild(inlineHost);
    const statusBar = doc.createElement("div");
    statusBar.id = "statusBar";
    statusBar.setAttribute("id", "statusBar");
    doc.root.appendChild(statusBar);
    const modalWrap = doc.createElement("div");
    doc.root.appendChild(modalWrap);
    modalWrap.innerHTML = AI_MODAL_SKELETON;

    const showStatusCalls = [];
    const sandbox = {
        document: doc,
        URLSearchParams,
        AbortController,
        setTimeout,
        clearTimeout,
        setImmediate,
        console,
        contextPath: "",
        api: server ? server.apiAdapter() : async () => ({}),
        $: (selector) => doc.querySelector(selector),
        $$: (selector) => Array.from(doc.querySelectorAll(selector)),
        escapeHtml: (value) => String(value ?? "")
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#039;"),
        labelDocumentType: (value) => value || "",
        labelDocumentStatus: (value) => value || "",
        formatFileSize: (value) => String(value == null ? "" : value),
        showStatus: (message, type) => { showStatusCalls.push({ message, type }); },
        showStatusCalls,
        aiAnalysisState: {
            contactId: null,
            documents: [],
            results: [],
            mode: "select",
            error: null,
            materialsMode: false,
            pickerHost: null,
            pickerView: null,
            unsubscribe: null,
            snapshot: null,
            intentToken: 0,
            selectionApplied: false,
            defaultOverLimit: false,
            run: null
        },
        AI_ANALYSIS_SELECT_LIMIT: 500,
        AI_ANALYSIS_DEFAULT_TYPES: new Set(["CV", "PHD_DEGREE", "MASTER_DEGREE", "BACHELOR_DEGREE"])
    };
    if (opts.component !== false) {
        sandbox.window = sandbox;
        sandbox.globalThis = sandbox;
        vm.createContext(sandbox);
        vm.runInContext(componentSource, sandbox, { filename: "expert-materials.js" });
        sandbox.ExpertMaterials.configure({
            api: server.apiAdapter(),
            contextPath: "",
            pollMs: opts.pollMs || 25,
            debounceMs: 10
        });
    } else {
        sandbox.window = sandbox;
        sandbox.globalThis = sandbox;
        sandbox.expertMaterialsAvailable = () => false;
        vm.createContext(sandbox);
    }
    const ctx = sandbox;
    AI_FUNCTIONS.forEach((name) => {
        const src = extractFunction(name);
        const fn = vm.runInContext(`(${src})`, ctx);
        ctx[name] = fn;
    });
    if (opts.component !== false) {
        ctx.expertMaterialsAvailable = () => typeof ctx.window.ExpertMaterials === "object" && !!ctx.window.ExpertMaterials;
    }
    return { doc, sandbox: ctx, appHost, inlineHost, modalWrap, showStatusCalls };
}

function flush() {
    return new Promise((resolve) => setImmediate(() => setImmediate(() => setImmediate(() => setImmediate(resolve)))));
}
function wait(ms) {
    return new Promise((resolve) => setTimeout(resolve, ms));
}
function click(el) {
    el.dispatchEvent(new MiniEvent("click", { bubbles: true }));
}
function change(el) {
    el.dispatchEvent(new MiniEvent("change", { bubbles: true }));
}

function modalEl(sandbox, selector) {
    const body = sandbox.document.getElementById("aiAnalysisModalBody");
    return body ? body.querySelector(selector) : null;
}
function modalHidden(sandbox) {
    return sandbox.document.getElementById("aiAnalysisModal").hidden;
}
function statusText(sandbox) {
    const el = modalEl(sandbox, '[data-role="ai-analysis-status"]');
    return el ? el.textContent : "";
}
function ctaText(sandbox) {
    const el = modalEl(sandbox, '[data-role="ai-analysis-cta"]');
    return el ? el.textContent : "";
}
function ctaDisabled(sandbox) {
    const el = modalEl(sandbox, '[data-role="ai-analysis-cta"]');
    return el ? el.disabled : true;
}
function errorText(sandbox) {
    const el = modalEl(sandbox, '[data-role="ai-analysis-error"]');
    return el ? (el.hidden ? "" : el.textContent) : "";
}
function pickerHost(sandbox) {
    return modalEl(sandbox, "[data-material-picker]");
}
function pickerRows(sandbox) {
    const host = pickerHost(sandbox);
    return host ? Array.from(host.querySelectorAll(".em-row")) : [];
}
function pickerCheckboxes(sandbox) {
    return pickerRows(sandbox)
        .map((row) => ({ row, box: row.querySelector("input[type=checkbox]") }))
        .filter((entry) => entry.box);
}
function clickCta(sandbox) {
    const cta = modalEl(sandbox, '[data-role="ai-analysis-cta"]');
    assert.ok(cta, "CTA 按钮必须存在");
    cta.fire("click");
}
function cleanup(sandbox, doc) {
    try {
        if (sandbox && typeof sandbox.closeAiAnalysisModal === "function") {
            sandbox.closeAiAnalysisModal();
        }
    } catch (e) { /* noop */ }
    try {
        if (sandbox && sandbox.ExpertMaterials && doc) {
            const inline = doc.getElementById("inlineHost");
            if (inline) sandbox.ExpertMaterials.unmountHostsIn(inline);
            const hosts = doc.querySelectorAll("[data-expert-materials-host]");
            hosts.forEach((host) => sandbox.ExpertMaterials.unmountHostsIn(host));
            const appRoot = doc.getElementById("appHost");
            if (appRoot) sandbox.ExpertMaterials.unmountHostsIn(appRoot);
        }
    } catch (e) { /* noop */ }
}

// ════════════════════════════════════════════════════════════════════════
// 场景目录
// ════════════════════════════════════════════════════════════════════════

function simpleCatalog(opts) {
    const options = opts || {};
    const out = [];
    (options.rows || []).forEach((row) => {
        out.push(makeItem(row.id, row.opts || {}));
    });
    return out;
}

describe("expert-material-analysis-flow: I-1 打开只 GET、带入/默认勾选、>500 提示", () => {
    it("打开 AI：零 POST、零文件内容抓取（只 GET 材料元数据 + 历史结果）", async () => {
        const server = new FakeServer({
            5: {
                catalog: simpleCatalog({
                    rows: [
                        { id: 1, opts: { fileName: "cv.pdf", documentType: "CV", state: "STORED" } },
                        { id: 2, opts: { fileName: "degree.pdf", documentType: "PHD_DEGREE", state: "METADATA_ONLY" } }
                    ]
                }),
                history: []
            }
        });
        const { doc, sandbox } = bootSandbox(server, {});
        try {
            await sandbox.openAiAnalysisModal(5);
            await flush();
            assert.strictEqual(modalHidden(sandbox), false, "AI 弹窗打开");
            assert.strictEqual(server.posts.length, 0, "打开期间零 POST（无 transfers/无 ai-analysis）");
            assert.strictEqual(server.deleteCalls.length, 0, "零 DELETE");
            const urls = server.gets.map((g) => g.url);
            assert.ok(urls.some((u) => u.includes("/5/materials?")), `材料元数据 GET 发出: ${urls.join(" | ")}`);
            assert.ok(urls.some((u) => u.includes("/5/ai-analysis")), "历史结果 GET 发出");
            urls.forEach((u) => {
                assert.ok(!u.includes("/attachments/") && !u.includes("/download") && !u.includes("/preview"),
                    `不允许文件内容抓取: ${u}`);
            });
            assert.ok(pickerHost(sandbox), "S-2 选件区已挂载 data-material-picker");
            assert.ok(modalEl(sandbox, ".em-analysis-note"), "em-analysis-note 存在");
            assert.ok(modalEl(sandbox, ".em-analysis-actions"), "em-analysis-actions 存在");
        } finally {
            cleanup(sandbox, doc);
        }
    });

    it("app.js 源文本包含 S-2 渲染模板标记（防 DOM stub 假绿）", () => {
        // K-dom-stub-tests-hide-dangling-refs：渲染函数写进 app.js 的模板必须真实存在。
        const renderFn = extractFunction("renderAiAnalysisMaterialsSelect");
        ["em-analysis", "data-material-picker", "em-analysis-note", "em-analysis-actions",
            "data-role=\"ai-analysis-status\"", "data-role=\"ai-analysis-cta\"",
            "仅分析所选文件。未获取的文件将在确认后下载到服务器；图片暂不支持文字识别。"
        ].forEach((marker) => {
            assert.ok(renderFn.includes(marker), `renderAiAnalysisMaterialsSelect 模板必须含 ${marker}`);
        });
        const noteFn = extractFunction("applyAiAnalysisEntrySelectionIfNeeded");
        assert.ok(noteFn.includes("默认材料超过500份，请分批选择") || noteFn.includes("defaultOverLimit"),
            ">500 分支必须在带入/默认逻辑中显式处理");
    });

    it("有已选：带入可分析子集；JPEG 行禁选并说明 当前不支持图片文字识别", async () => {
        const server = new FakeServer({
            7: {
                catalog: simpleCatalog({
                    rows: [
                        { id: 1, opts: { fileName: "cv.pdf", documentType: "CV", state: "STORED" } },
                        { id: 2, opts: { fileName: "degree.pdf", documentType: "PHD_DEGREE", state: "METADATA_ONLY" } },
                        { id: 3, opts: { fileName: "photo.jpg", contentType: "image/jpeg", documentType: "OTHER", state: "STORED" } }
                    ]
                }),
                history: []
            }
        });
        const { doc, sandbox, inlineHost } = bootSandbox(server, {});
        try {
            // 专家页 inline 面板先选 1、2、3（下载选择可含图片），共享 store 同一选择集
            sandbox.ExpertMaterials.mount({ host: inlineHost, contactId: 7, mode: "inline" });
            await flush();
            const boxes = Array.from(inlineHost.querySelectorAll(".em-row input[type=checkbox]"));
            boxes.forEach((box) => { box.checked = true; change(box); });
            await flush();

            await sandbox.openAiAnalysisModal(7);
            await flush();
            assert.strictEqual(statusText(sandbox), "已选 2 份，已存 1 份，需获取 1 份",
                "只统计可分析候选（JPEG 被排除但原因可见）");
            const jpegRow = pickerRows(sandbox).find((row) => row.dataset.attachmentId === "3");
            assert.ok(jpegRow, "JPEG 行存在");
            const jpegBox = jpegRow.querySelector("input[type=checkbox]");
            assert.strictEqual(jpegBox.disabled, true, "JPEG checkbox 禁用（服务端能力决定，非扩展名伪装）");
            assert.ok(jpegRow.textContent.includes("当前不支持图片文字识别"), "JPEG 行显示 当前不支持图片文字识别");
            assert.strictEqual(jpegRow.querySelector("input[type=checkbox]").checked, false, "AI 视图内 JPEG 呈现为未选");
            const cvRow = pickerRows(sandbox).find((row) => row.dataset.attachmentId === "1");
            assert.strictEqual(cvRow.querySelector("input[type=checkbox]").checked, true, "可分析已选被带入勾选");
            assert.strictEqual(ctaDisabled(sandbox), false, "有候选时 CTA 可用");
            assert.strictEqual(ctaText(sandbox), "获取所选文件并分析", "缺 1 件 → 获取所选文件并分析");
        } finally {
            cleanup(sandbox, doc);
        }
    });

    it("无已选：默认勾选跨完整专家集合的 CV/学位（跨页可见）", async () => {
        // 25 份材料：CV/学位全部 METADATA_ONLY 且分布在不同页（summary 提供 default ids）
        const rows = [];
        for (let id = 1; id <= 25; id += 1) {
            const isDefault = id % 4 === 0; // 6 个 CV/学位默认候选，分散在第 1、2、3 页
            rows.push({
                id,
                opts: {
                    fileName: isDefault ? `默认-${id}.pdf` : `其他-${id}.pdf`,
                    documentType: isDefault ? "CV" : "OTHER",
                    state: isDefault ? "METADATA_ONLY" : "STORED"
                }
            });
        }
        const server = new FakeServer({ 9: { catalog: simpleCatalog({ rows }), history: [] } });
        const { doc, sandbox } = bootSandbox(server, {});
        try {
            await sandbox.openAiAnalysisModal(9);
            await flush();
            // 默认候选 6 个 ≤500 → 自动勾选
            assert.ok(statusText(sandbox).startsWith("已选 6 份"), `状态含默认计数: ${statusText(sandbox)}`);
            const state = sandbox.ExpertMaterials.getState(9);
            assert.strictEqual(state.selection.size, 6, "共享 store 选择集 = 6 个默认候选（跨页保存）");
            assert.ok(statusText(sandbox).includes("需获取 6 份"), "默认候选均为未存 → 需获取");
            // 默认勾选跨页可见：翻到第 3 页，附件 24（CV 默认候选）应为勾选态
            const pager = pickerHost(sandbox).querySelectorAll(".em-pager .button");
            click(pager[1]); await flush();
            click(pager[1]); await flush();
            const pageRows = pickerRows(sandbox);
            assert.ok(pageRows.some((row) => row.dataset.attachmentId === "24"),
                "第 3 页包含默认候选行");
            const row24 = pageRows.find((row) => row.dataset.attachmentId === "24");
            assert.strictEqual(row24.querySelector("input[type=checkbox]").checked, true,
                "默认勾选跨页保留可见（无静默截断）");
        } finally {
            cleanup(sandbox, doc);
        }
    });

    it("默认候选 >500：明确提示分批选择，绝不悄悄勾选前 500", async () => {
        const server = new FakeServer({
            11: {
                catalog: simpleCatalog({
                    rows: [
                        { id: 1, opts: { fileName: "cv.pdf", documentType: "CV", state: "METADATA_ONLY" } },
                        { id: 2, opts: { fileName: "degree.pdf", documentType: "PHD_DEGREE", state: "STORED" } }
                    ]
                }),
                history: [],
                summaryOverride: { defaultAnalysisAttachmentIds: Array.from({ length: 501 }, (_, i) => i + 1) }
            }
        });
        const { doc, sandbox } = bootSandbox(server, {});
        try {
            await sandbox.openAiAnalysisModal(11);
            await flush();
            assert.strictEqual(statusText(sandbox), "默认材料超过500份，请分批选择", "明确提示");
            const state = sandbox.ExpertMaterials.getState(11);
            assert.strictEqual(state.selection.size, 0, ">500 时零自动勾选（无静默截断前 500）");
            assert.strictEqual(ctaDisabled(sandbox), true, "未选择 → CTA 禁用");
            assert.ok(server.transferCalls.length === 0 && server.analysisCalls.length === 0, "未发生任何提交/分析");
        } finally {
            cleanup(sandbox, doc);
        }
    });

    it("提交冻结快照：提交后取消勾选/翻页不改变分析名单", async () => {
        const server = new FakeServer({
            13: {
                catalog: simpleCatalog({
                    rows: [
                        { id: 1, opts: { fileName: "cv.pdf", documentType: "CV", state: "STORED" } },
                        { id: 2, opts: { fileName: "degree.pdf", documentType: "PHD_DEGREE", state: "METADATA_ONLY" } }
                    ]
                }),
                history: []
            }
        });
        const { doc, sandbox, inlineHost } = bootSandbox(server, {});
        try {
            sandbox.ExpertMaterials.mount({ host: inlineHost, contactId: 13, mode: "inline" });
            await flush();
            const boxes = Array.from(inlineHost.querySelectorAll(".em-row input[type=checkbox]"));
            boxes.forEach((box) => { box.checked = true; change(box); });
            await flush();
            await sandbox.openAiAnalysisModal(13);
            await flush();
            assert.strictEqual(statusText(sandbox), "已选 2 份，已存 1 份，需获取 1 份");

            await sandbox.startAiAnalysis(); // 冻结 [1,2]；缺 2 先获取
            await flush();
            assert.strictEqual(server.transferCalls.length, 1, "只发一次获取请求");
            assert.deepStrictEqual(server.transferCalls[0].ids, [2], "缺 1 件只取 1 件");
            assert.ok(ctaDisabled(sandbox), "获取中按钮禁用");

            // 提交后取消勾选 1（“不跟随之后选择变化”）
            const row1 = inlineHost.querySelector('[data-attachment-id="1"] input[type=checkbox]');
            row1.checked = false;
            change(row1);
            await flush();

            server.setState(13, 2, "STORED");
            await wait(160); // 轮询推进
            assert.strictEqual(server.analysisCalls.length, 1, "就绪后恰好一次分析");
            assert.deepStrictEqual(server.analysisCalls[0].ids, [1, 2], "分析名单 = 提交时冻结快照");
        } finally {
            cleanup(sandbox, doc);
        }
    });
});

describe("expert-material-analysis-flow: I-2 获取后分析 / 失败 0 次分析 / 重试失败项", () => {
    it("缺 1 件先获取，全部 STORED 后恰好一次 ai-analysis（body=冻结名单）", async () => {
        const server = new FakeServer({
            15: {
                catalog: simpleCatalog({
                    rows: [
                        { id: 1, opts: { fileName: "cv.pdf", documentType: "CV", state: "STORED" } },
                        { id: 2, opts: { fileName: "degree.pdf", documentType: "PHD_DEGREE", state: "METADATA_ONLY" } }
                    ]
                }),
                history: []
            }
        });
        const { doc, sandbox } = bootSandbox(server, {});
        try {
            await sandbox.openAiAnalysisModal(15);
            await flush();
            // 手动勾选两件
            const boxes = pickerCheckboxes(sandbox);
            boxes.forEach((entry) => { entry.box.checked = true; change(entry.box); });
            await flush();
            assert.strictEqual(statusText(sandbox), "已选 2 份，已存 1 份，需获取 1 份");

            await sandbox.startAiAnalysis();
            await flush();
            assert.strictEqual(server.transferCalls.length, 1, "M>0 → 一次 transfers POST");
            assert.deepStrictEqual(server.transferCalls[0].ids, [2], "仅获取缺失 1 件");
            assert.strictEqual(server.analysisCalls.length, 0, "未就绪前 0 次分析");

            server.setState(15, 2, "STORED");
            await wait(180);
            assert.strictEqual(server.analysisCalls.length, 1, "全部就绪后恰好一次分析");
            assert.deepStrictEqual(server.analysisCalls[0].ids, [1, 2]);
            await wait(120);
            assert.strictEqual(server.analysisCalls.length, 1, "分析不重复触发");
            assert.strictEqual(sandbox.aiAnalysisState.mode, "results", "成功后进入结果模式");
        } finally {
            cleanup(sandbox, doc);
        }
    });

    it("获取失败：0 次分析，展示失败 attachmentId/文件名/原因；重试只请求失败项并最终一次分析", async () => {
        const server = new FakeServer({
            17: {
                catalog: simpleCatalog({
                    rows: [
                        { id: 1, opts: { fileName: "cv.pdf", documentType: "CV", state: "STORED" } },
                        { id: 2, opts: { fileName: "degree.pdf", documentType: "PHD_DEGREE", state: "METADATA_ONLY" } },
                        { id: 3, opts: { fileName: "thesis.pdf", documentType: "OTHER", state: "METADATA_ONLY" } }
                    ]
                }),
                history: []
            }
        });
        const { doc, sandbox } = bootSandbox(server, {});
        try {
            await sandbox.openAiAnalysisModal(17);
            await flush();
            const boxes = pickerCheckboxes(sandbox);
            boxes.forEach((entry) => { entry.box.checked = true; change(entry.box); });
            await flush();
            await sandbox.startAiAnalysis();
            await flush();
            assert.strictEqual(server.transferCalls.length, 1);
            assert.deepStrictEqual(server.transferCalls[0].ids, [2, 3], "缺 2 件取 2 件");
            assert.strictEqual(server.analysisCalls.length, 0);

            // 2 成功、3 失败（TIMEOUT）
            server.setState(17, 2, "STORED");
            server.setState(17, 3, "FAILED", { code: "TIMEOUT", message: "连接超时" });
            await wait(180);
            assert.strictEqual(server.analysisCalls.length, 0, "任一项失败 → 整批 0 次分析");
            assert.ok(statusText(sandbox).includes("1 份获取失败"), `失败状态: ${statusText(sandbox)}`);
            const err = errorText(sandbox);
            assert.ok(err.includes("3"), "错误区包含 attachmentId");
            assert.ok(err.includes("thesis.pdf"), "错误区包含文件名");
            assert.ok(err.includes("连接超时"), "错误区包含原因");
            assert.strictEqual(ctaText(sandbox), "重试获取失败文件并分析", "失败 CTA = 重试");

            // 重试只请求失败项（3），不再重取已存 2
            await sandbox.startAiAnalysis();
            await flush();
            assert.strictEqual(server.transferCalls.length, 2, "第二次提交 = 重试");
            assert.deepStrictEqual(server.transferCalls[1].ids, [3], "重试只请求失败项");
            assert.strictEqual(server.analysisCalls.length, 0, "重试未就绪前仍 0 次分析");

            server.setState(17, 3, "STORED");
            await wait(180);
            assert.strictEqual(server.analysisCalls.length, 1, "重试后全部就绪 → 一次分析");
            assert.deepStrictEqual(server.analysisCalls[0].ids, [1, 2, 3], "仍按原冻结快照全量分析");
        } finally {
            cleanup(sandbox, doc);
        }
    });

    it("M=0（全部已存）→ 直接开始分析；空文本 PDF 逐文件原因展示且旧结果不被清空", async () => {
        const server = new FakeServer({
            19: {
                catalog: simpleCatalog({
                    rows: [
                        { id: 1, opts: { fileName: "cv.pdf", documentType: "CV", state: "STORED" } },
                        { id: 2, opts: { fileName: "scan-copy.pdf", documentType: "PHD_DEGREE", state: "STORED" } }
                    ]
                }),
                history: [{ id: 700, fieldKey: "name", fieldLabel: "姓名", value: "Alice Chen", displayOrder: 0 }],
                analysisError: (ids, spec) => {
                    if (ids.includes(2)) return "附件 scan-copy.pdf(attachmentId=2) 无可读文字，无法分析";
                    return null;
                }
            }
        });
        const { doc, sandbox } = bootSandbox(server, {});
        try {
            // 打开即历史结果 → results 模式（结果编辑/重新评估结构保留）
            await sandbox.openAiAnalysisModal(19);
            await flush();
            assert.strictEqual(sandbox.aiAnalysisState.mode, "results", "有历史结果先进结果页");
            const resultsInput = sandbox.document.getElementById("aiAnalysisModalBody").querySelector(".analysis-field-input");
            assert.ok(resultsInput, "结果字段输入框存在（原编辑结构）");
            assert.strictEqual(resultsInput.value, "Alice Chen", "历史结果展示");

            // 重新分析 → 进入选件（无选择 → 无默认候选，因为都是 STORED CV/学位不在默认集）
            await sandbox.enterAiAnalysisSelectMode(true);
            await flush();
            assert.strictEqual(sandbox.aiAnalysisState.mode, "select");
            // 勾选空文本 PDF 与可读 PDF（两件均已存）
            const rows = pickerRows(sandbox);
            rows.forEach(({ querySelector }) => undefined);
            pickerCheckboxes(sandbox).forEach((entry) => { entry.box.checked = true; change(entry.box); });
            await flush();
            assert.ok(statusText(sandbox).includes("需获取 0 份"), "两件均已存");
            assert.strictEqual(ctaText(sandbox), "开始分析", "M=0 → 开始分析");

            await sandbox.startAiAnalysis();
            await flush();
            assert.strictEqual(server.transferCalls.length, 0, "M=0 不发出获取请求");
            assert.strictEqual(server.analysisCalls.length, 1);
            assert.deepStrictEqual(server.analysisCalls[0].ids, [1, 2]);

            // 服务端空文本 PDF → ANALYSIS_FAILED message 原样展示（逐文件原因）
            await wait(60);
            assert.strictEqual(sandbox.aiAnalysisState.mode, "select", "分析失败回到选件");
            const err = errorText(sandbox);
            assert.ok(err.includes("scan-copy.pdf"), `展示文件名: ${err}`);
            assert.ok(err.includes("无可读文字"), `展示原因: ${err}`);
            assert.ok(err.includes("attachmentId=2"), `展示 attachmentId: ${err}`);
            assert.strictEqual(sandbox.aiAnalysisState.results.length, 1,
                "失败不清空前端已有结果状态（旧结果保留）");
            assert.strictEqual(server.deleteCalls.length, 0, "前端绝不对历史结果发起 DELETE");
        } finally {
            cleanup(sandbox, doc);
        }
    });
});

describe("expert-material-analysis-flow: I-3 关窗意图销毁 / 历史结果 / 旧版回落", () => {
    it("获取中关窗：token 销毁、下载继续、零后续自动分析；重开不自动提交", async () => {
        const server = new FakeServer({
            21: {
                catalog: simpleCatalog({
                    rows: [
                        { id: 1, opts: { fileName: "cv.pdf", documentType: "CV", state: "STORED" } },
                        { id: 2, opts: { fileName: "degree.pdf", documentType: "PHD_DEGREE", state: "METADATA_ONLY" } }
                    ]
                }),
                history: []
            }
        });
        const { doc, sandbox, inlineHost } = bootSandbox(server, { pollMs: 30 });
        try {
            // 专家页 inline host 保持挂载（真实页面形态：关窗后共享 store 仍轮询下载）
            sandbox.ExpertMaterials.mount({ host: inlineHost, contactId: 21, mode: "inline" });
            await flush();
            await sandbox.openAiAnalysisModal(21);
            await flush();
            pickerCheckboxes(sandbox).forEach((entry) => { entry.box.checked = true; change(entry.box); });
            await flush();
            await sandbox.startAiAnalysis();
            await flush();
            assert.strictEqual(server.transferCalls.length, 1);
            assert.strictEqual(server.analysisCalls.length, 0);

            // 关窗（下载仍在途）
            sandbox.closeAiAnalysisModal();
            await flush();
            const getsBefore = server.gets.length;
            server.setState(21, 2, "STORED");
            await wait(220); // 轮询窗口
            assert.ok(server.gets.length > getsBefore, "已请求的下载继续（共享 store 轮询 GET 增长）");
            assert.strictEqual(server.analysisCalls.length, 0, "关窗后零后续自动分析（intentToken 已销毁）");

            // 重开：显示已存状态，不自动提交分析
            await sandbox.openAiAnalysisModal(21);
            await flush();
            assert.strictEqual(server.analysisCalls.length, 0, "重开不自动提交");
            assert.ok(statusText(sandbox).includes("已存"), `重开读出新状态: ${statusText(sandbox)}`);
            assert.strictEqual(ctaText(sandbox), "开始分析", "全部已存后 CTA=开始分析");
        } finally {
            cleanup(sandbox, doc);
        }
    });

    it("历史结果：前端绝不先清空（重开仍可见、可经 PUT 编辑原接口）", async () => {
        const server = new FakeServer({
            23: {
                catalog: simpleCatalog({
                    rows: [{ id: 1, opts: { fileName: "cv.pdf", documentType: "CV", state: "STORED" } }]
                }),
                history: [{ id: 700, fieldKey: "name", fieldLabel: "姓名", value: "Alice Chen", sourceFileName: "cv.pdf", displayOrder: 0 }]
            }
        });
        const { doc, sandbox } = bootSandbox(server, {});
        try {
            await sandbox.openAiAnalysisModal(23);
            await flush();
            assert.strictEqual(sandbox.aiAnalysisState.mode, "results");
            // 结果编辑沿用原 PUT 接口（wrapper 拦截 PUT 并返回服务端形态的更新字段）
            const adapter = server.apiAdapter();
            sandbox.api = async (url, options) => {
                if (options && options.method === "PUT" && url.includes("/ai-analysis/700")) {
                    const value = JSON.parse(options.body || "{}").value || "";
                    server.puts.push({ url, body: { value }, options });
                    return { id: 700, fieldKey: "name", fieldLabel: "姓名", value, displayOrder: 0 };
                }
                return adapter(url, options);
            };
            await sandbox.saveAiAnalysisField(700, "Alice C. Chen");
            assert.strictEqual(server.puts.length, 1, "PUT /ai-analysis/{fieldId} 原接口被调用");
            assert.ok(server.puts[0].url.includes("/ai-analysis/700"), `PUT 目标: ${server.puts[0].url}`);
            assert.strictEqual(server.puts[0].body.value, "Alice C. Chen");
            assert.strictEqual(sandbox.aiAnalysisState.results[0].value, "Alice C. Chen", "本地结果同步更新");

            // 重开弹窗：历史 GET 仍返回原结果（全程无 DELETE/clear）
            sandbox.closeAiAnalysisModal();
            await sandbox.openAiAnalysisModal(23);
            await flush();
            assert.strictEqual(sandbox.aiAnalysisState.mode, "results", "重开仍先展示历史结果");
            assert.ok(server.deleteCalls.length === 0, "从未对历史结果发起 DELETE");
            const reopenedInput = sandbox.document.getElementById("aiAnalysisModalBody").querySelector(".analysis-field-input");
            assert.strictEqual(reopenedInput && reopenedInput.value, "Alice C. Chen", "重开后结果可重读");
        } finally {
            cleanup(sandbox, doc);
        }
    });

    it("组件缺失/无 09 API → 回退旧路径（documents + 历史 GET、零 POST；footer 原按钮）", async () => {
        const server = new FakeServer({
            25: {
                catalog: simpleCatalog({
                    rows: [{ id: 1, opts: { fileName: "cv.pdf", documentType: "CV", state: "STORED" } }]
                }),
                history: []
            }
        });
        const { doc, sandbox } = bootSandbox(server, { component: false });
        try {
            await sandbox.openAiAnalysisModal(25);
            await flush();
            assert.strictEqual(modalHidden(sandbox), false);
            assert.strictEqual(sandbox.aiAnalysisState.materialsMode, false, "旧路径");
            const urls = server.gets.map((g) => g.url);
            assert.ok(urls.some((u) => u.includes("/25/documents")), "旧路径 GET /documents");
            assert.ok(urls.some((u) => u.includes("/25/ai-analysis")), "旧路径 GET 历史");
            assert.strictEqual(server.posts.length, 0, "打开零 POST");
            const footer = sandbox.document.getElementById("aiAnalysisModalFooter");
            assert.ok(footer.textContent.includes("开始分析"), "旧 footer 主按钮保留");
            assert.ok(footer.textContent.includes("取消"), "旧 footer 取消保留");
        } finally {
            cleanup(sandbox, doc);
        }
    });
});

