"use strict";

// fast-p 04 组件单元测试（T1/T2/S-2/S-4 契约）：meeting-confirmation.js 纯导出
// （filterZones / normalizeMeetingText / sanitizeDraftHtml / planMeetingInsertion）
// 与真实组件挂载（create/open/close/dispose + 表单/时区键盘/预览状态机）。
// 载入方式：Node require 模块本体；通过 global.document 注入最小真实 DOM 树，
// dispatch 事件驱动；不是源码 includes 验收。无 npm 依赖、无真实网络。

const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it, before, after } = require("node:test");

const ROOT = path.join(__dirname, "..", "..", "main", "resources", "static");
const meetingSource = fs.readFileSync(path.join(ROOT, "meeting-confirmation.js"), "utf-8");
// 直接 require（module.exports 测试入口；浏览器 IIFE 分支不执行）
const Meeting = require(path.join(ROOT, "meeting-confirmation.js"));


class MiniEvent {
    constructor(type, opts) {
        this.type = type;
        this.target = null;
        this.currentTarget = null;
        this.bubbles = !!(opts && opts.bubbles);
        this.key = null;
        this.preventDefault = () => {};
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

function decodeEntities(value) {
    return String(value)
        .replace(/&#039;/g, "'")
        .replace(/&quot;/g, '"')
        .replace(/&lt;/g, "<")
        .replace(/&gt;/g, ">")
        .replace(/&amp;/g, "&");
}

function encodeText(value) {
    return String(value).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

function encodeAttr(value) {
    return String(value).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

function parseToken(token) {
    const tagMatch = token.match(/^[a-zA-Z][a-zA-Z0-9-]*/);
    const rest = token.slice(tagMatch ? tagMatch[0].length : 0);
    const out = { tag: tagMatch ? tagMatch[0].toLowerCase() : null, classes: [], attrs: [], id: null };
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
        .filter((part) => part && part !== ">")
        .map(parseToken);
}

function tokenMatches(el, token) {
    if (!el || el.nodeType !== 1) return false;
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

const VOID_TAGS = new Set(["input", "br", "img", "hr", "meta", "link", "area", "base", "col", "embed", "source", "track", "wbr"]);

function collectText(node) {
    if (node.nodeType === 3) return node.data;
    return node.childNodes.map((child) => collectText(child)).join("");
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
        this._textCache = null;
        this._value = "";
        this.checked = false;
        this.disabled = false;
        this.hidden = false;
        this._open = false;
        this._selected = false;
        // 简版滚动/布局度量（无真实排版；测试可显式赋值）
        this.scrollTop = 0;
        this.scrollHeight = 0;
        this.clientHeight = 0;
        this.offsetTop = 0;
        this.offsetHeight = 0;
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
                    if (key === "toJSON") return undefined;
                    return self.attributes.get(toAttr(key));
                },
                set(_t, key, value) {
                    if (value === undefined || value === null) self.attributes.delete(toAttr(key));
                    else self.attributes.set(toAttr(key), String(value));
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
        const raw = value === undefined ? "" : String(value);
        this.attributes.set(name, raw);
        if (name === "class") this._classes = new Set(raw.split(/\s+/).filter(Boolean));
        if (name === "value") this._value = raw;
        if (name === "disabled") this.disabled = true;
        if (name === "hidden") this.hidden = true;
        if (name === "checked") this.checked = true;
        if (name === "selected") this._selected = true;
        if (name === "open") this._open = true;
        this._textCache = null;
    }
    getAttribute(name) {
        return this.attributes.has(name) ? this.attributes.get(name) : null;
    }
    hasAttribute(name) {
        return this.attributes.has(name);
    }
    removeAttribute(name) {
        this.attributes.delete(name);
        if (name === "class") this._classes = new Set();
        if (name === "value") this._value = "";
        if (name === "disabled") this.disabled = false;
        if (name === "hidden") this.hidden = false;
        if (name === "checked") this.checked = false;
        if (name === "selected") this._selected = false;
        if (name === "open") this._open = false;
        this._textCache = null;
    }

    get value() {
        return this._value;
    }
    set value(value) {
        this._value = value == null ? "" : String(value);
        this.attributes.set("value", this._value);
        this._textCache = null;
    }
    get open() {
        return this._open;
    }
    get selected() {
        return this._selected;
    }
    set selected(value) {
        this._selected = !!value;
    }

    appendChild(child) {
        if (child.parentNode) child.parentNode.removeChild(child);
        child.parentNode = this;
        this.childNodes.push(child);
        this._textCache = null;
        return child;
    }
    insertBefore(child, refNode) {
        if (child.parentNode) child.parentNode.removeChild(child);
        const idx = refNode ? this.childNodes.indexOf(refNode) : -1;
        if (idx === -1) {
            this.childNodes.push(child);
        } else {
            this.childNodes.splice(idx, 0, child);
        }
        child.parentNode = this;
        this._textCache = null;
        return child;
    }
    removeChild(child) {
        const idx = this.childNodes.indexOf(child);
        if (idx === -1) return child;
        this.childNodes.splice(idx, 1);
        child.parentNode = null;
        this._textCache = null;
        return child;
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
    focus() {
        if (this.ownerDocument) this.ownerDocument.activeElement = this;
    }

    get textContent() {
        return collectText(this);
    }
    set textContent(value) {
        this.childNodes = [];
        if (value !== "" && value !== null && value !== undefined) {
            this.childNodes.push(new MiniText(String(value)));
        }
        this._textCache = null;
    }
    get innerText() {
        return this.textContent;
    }
    set innerText(value) {
        this.textContent = value;
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
    dispatchEvent(event) {
        event.target = this;
        let node = this;
        while (node) {
            if (node.nodeType === 1 || node.nodeType === 9) {
                event.currentTarget = node;
                for (const fn of node.listeners.get(event.type) || []) fn(event);
            }
            node = event.bubbles === false ? null : node.parentNode;
        }
        return true;
    }
    fire(type, bubbles = true) {
        this.dispatchEvent(new MiniEvent(type, { bubbles }));
    }

    serialize() {
        const tag = this.tagName.toLowerCase();
        const attrs = [];
        this.attributes.forEach((value, name) => {
            attrs.push(value === "" ? name : `${name}="${encodeAttr(value)}"`);
        });
        const openTag = attrs.length ? `<${tag} ${attrs.join(" ")}` : `<${tag}`;
        if (VOID_TAGS.has(tag)) return `${openTag}>`;
        const inner = this.childNodes.map((child) => child.nodeType === 3 ? encodeText(child.data) : child.serialize()).join("");
        return `${openTag}>${inner}</${tag}>`;
    }

    get innerHTML() {
        return this.childNodes.map((child) => child.nodeType === 3 ? encodeText(child.data) : child.serialize()).join("");
    }
    set innerHTML(value) {
        this.childNodes = [];
        this._textCache = null;
        if (value == null || value === "") return;
        parseFragmentInto(String(value), this, this.ownerDocument);
    }
    get outerHTML() {
        return this.serialize();
    }
    set outerHTML(value) {
        if (!this.parentNode) {
            this.innerHTML = value;
            return;
        }
        const parent = this.parentNode;
        const frag = [];
        parseFragmentInto(String(value), null, this.ownerDocument, frag);
        const idx = parent.childNodes.indexOf(this);
        parent.childNodes.splice(idx, 1, ...frag);
        frag.forEach((node) => { node.parentNode = parent; });
        this.parentNode = null;
        parent._textCache = null;
    }

    insertAdjacentHTML(position, value) {
        const where = String(position || "").toLowerCase();
        const frag = [];
        parseFragmentInto(String(value), null, this.ownerDocument, frag);
        if (where === "afterbegin") {
            this.childNodes.splice(0, 0, ...frag);
        } else if (where === "beforeend") {
            this.childNodes.push(...frag);
        } else {
            throw new Error("insertAdjacentHTML position not supported: " + position);
        }
        frag.forEach((node) => { node.parentNode = this; });
        this._textCache = null;
    }
}

class MiniDocument {
    constructor() {
        this.root = new MiniElement("#document", this);
        this.root.nodeType = 9;
    }
    get body() {
        return this.root;
    }
    createElement(tag) {
        return new MiniElement(tag, this);
    }
    createTextNode(data) {
        return new MiniText(String(data));
    }
    getElementById(id) {
        const found = this.root.querySelectorAll(`#${id}`);
        return found[0] || null;
    }
    querySelector(selector) {
        return this.root.querySelector(selector);
    }
    querySelectorAll(selector) {
        return this.root.querySelectorAll(selector);
    }
    addEventListener(type, fn) {
        this.root.addEventListener(type, fn);
    }
    removeEventListener(type, fn) {
        this.root.removeEventListener(type, fn);
    }
}

const ATTR_TOKEN = /\s+([a-zA-Z_:][a-zA-Z0-9_:.\-]*)(?:\s*=\s*("[^"]*"|'[^']*'|[^\s"'=<>`]+))?/g;

function parseAttrs(raw, el) {
    ATTR_TOKEN.lastIndex = 0;
    let match;
    while ((match = ATTR_TOKEN.exec(raw)) !== null) {
        const name = match[1];
        let value = match[2];
        if (value === undefined) {
            value = "";
        } else {
            if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
                value = value.slice(1, -1);
            }
            value = decodeEntities(value);
        }
        el.setAttribute(name, value);
    }
}

function parseFragmentInto(html, parent, doc, out) {
    const nodes = out || [];
    let pos = 0;
    const stack = [];
    while (pos < html.length) {
        const lt = html.indexOf("<", pos);
        if (lt === -1) {
            const text = decodeEntities(html.slice(pos));
            if (text) {
                const node = new MiniText(text);
                if (stack.length) stack[stack.length - 1].appendChild(node);
                else nodes.push(node);
            }
            break;
        }
        if (lt > pos) {
            const text = decodeEntities(html.slice(pos, lt));
            if (text) {
                const node = new MiniText(text);
                if (stack.length) stack[stack.length - 1].appendChild(node);
                else nodes.push(node);
            }
        }
        if (html.startsWith("</", lt)) {
            const gt = html.indexOf(">", lt);
            const closeName = html.slice(lt + 2, gt > -1 ? gt : html.length).trim().toLowerCase();
            if (stack.length && stack[stack.length - 1].tagName.toLowerCase() === closeName) {
                stack.pop();
            } else if (stack.length) {
                for (let idx = stack.length - 1; idx >= 0; idx -= 1) {
                    if (stack[idx].tagName.toLowerCase() === closeName) {
                        stack.length = idx;
                        break;
                    }
                }
            }
            pos = gt === -1 ? html.length : gt + 1;
            continue;
        }
        const gt = html.indexOf(">", lt);
        if (gt === -1) {
            const text = decodeEntities(html.slice(lt));
            if (text) {
                const node = new MiniText(text);
                if (stack.length) stack[stack.length - 1].appendChild(node);
                else nodes.push(node);
            }
            break;
        }
        const raw = html.slice(lt + 1, gt);
        const selfClose = raw.endsWith("/");
        const inner = selfClose ? raw.slice(0, -1) : raw;
        const nameMatch = inner.match(/^([a-zA-Z][a-zA-Z0-9-]*)([\s\S]*)$/);
        if (!nameMatch) {
            pos = gt + 1;
            continue;
        }
        const tag = nameMatch[1].toLowerCase();
        const el = doc.createElement(tag);
        parseAttrs(nameMatch[2], el);
        if (stack.length) stack[stack.length - 1].appendChild(el);
        else nodes.push(el);
        if (!(VOID_TAGS.has(tag) || selfClose)) {
            stack.push(el);
        }
        pos = gt + 1;
    }
    if (parent) {
        nodes.forEach((node) => parent.appendChild(node));
    }
}

function createDom() {
    const doc = new MiniDocument();
    const host = doc.createElement("div");
    doc.body.appendChild(host);
    return { doc, host };
}



// ════════════════════════════════════════════════════════════════════════
// 组件运行环境：global.document/URL/Blob stub + 可控定时器
// ════════════════════════════════════════════════════════════════════════

const originalDocument = global.document;
const originalSetTimeout = global.setTimeout;
const originalClearTimeout = global.clearTimeout;
const originalURL = global.URL;
const originalBlob = global.Blob;

let pendingTimers = [];
let blobLog = { created: [], revoked: [] };

function installGlobalDom(doc) {
    global.document = doc;
    global.URL = {
        createObjectURL: () => {
            const url = `blob:unit-${blobLog.created.length + 1}`;
            blobLog.created.push(url);
            return url;
        },
        revokeObjectURL: (url) => { blobLog.revoked.push(url); }
    };
    global.Blob = class FakeBlob {
        constructor(parts, options) {
            this.parts = parts || [];
            this.type = (options && options.type) || "";
        }
    };
    pendingTimers = [];
    global.setTimeout = (fn) => { pendingTimers.push(fn); return pendingTimers.length; };
    global.clearTimeout = () => {};
}

function restoreGlobalDom() {
    global.document = originalDocument;
    global.setTimeout = originalSetTimeout;
    global.clearTimeout = originalClearTimeout;
    global.URL = originalURL;
    global.Blob = originalBlob;
    pendingTimers = [];
}

function runTimers() {
    const list = pendingTimers.splice(0);
    list.forEach((fn) => fn());
}

function flush() {
    return new Promise((resolve) => setImmediate(() => setImmediate(() => setImmediate(() => setImmediate(resolve)))));
}

function eventOn(el, type, key) {
    const ev = new MiniEvent(type, { bubbles: true });
    if (key != null) ev.key = key;
    el.dispatchEvent(ev);
    return ev;
}

function clickEl(el) {
    eventOn(el, "click");
}

function inputInto(el, value) {
    el.value = value;
    eventOn(el, "input");
}

// ---- 组件 stub API（01 只读形状；正文由服务端通用模板渲染，弹窗不传模板） ----
// 01 契约：尾部新增 countryCode/countryLabelZh/countryLabelEn/canonicalZoneId/
// endOffsetSeconds/localTimeIssue。date-only 模式无 endOffsetSeconds；会议模式两端都有。

const BASE_ZONES = [
    { id: "Brazil/East", labelZh: "巴西 · 东部", aliases: ["巴西", "圣保罗"],
        offsetLabel: "UTC-3", offsetSeconds: -10800, countryCode: "BR", countryLabelZh: "巴西",
        countryLabelEn: "Brazil", canonicalZoneId: "America/Sao_Paulo" },
    { id: "America/Sao_Paulo", labelZh: "巴西 · 圣保罗", aliases: ["巴西", "圣保罗"],
        offsetLabel: "UTC-3", offsetSeconds: -10800, countryCode: "BR", countryLabelZh: "巴西",
        countryLabelEn: "Brazil", canonicalZoneId: "America/Sao_Paulo" },
    { id: "America/Noronha", labelZh: "巴西 · 洛罗尼亚", aliases: ["巴西", "洛罗尼亚"],
        offsetLabel: "UTC-2", offsetSeconds: -7200, countryCode: "BR", countryLabelZh: "巴西",
        countryLabelEn: "Brazil", canonicalZoneId: "America/Noronha" },
    { id: "America/Manaus", labelZh: "巴西 · 马瑙斯", aliases: ["巴西", "马瑙斯"],
        offsetLabel: "UTC-4", offsetSeconds: -14400, countryCode: "BR", countryLabelZh: "巴西",
        countryLabelEn: "Brazil", canonicalZoneId: "America/Manaus" },
    { id: "America/Rio_Branco", labelZh: "巴西 · 里约布兰科", aliases: ["巴西", "里约布兰科"],
        offsetLabel: "UTC-5", offsetSeconds: -18000, countryCode: "BR", countryLabelZh: "巴西",
        countryLabelEn: "Brazil", canonicalZoneId: "America/Rio_Branco" },
    { id: "Europe/Istanbul", labelZh: "土耳其 · 伊斯坦布尔", aliases: ["土耳其", "伊斯坦布尔", "Turkey", "Türkiye", "Istanbul"],
        offsetLabel: "UTC+3", offsetSeconds: 10800, countryCode: "TR", countryLabelZh: "土耳其",
        countryLabelEn: "Turkey", canonicalZoneId: "Europe/Istanbul" },
    { id: "Asia/Shanghai", labelZh: "中国 · 北京 / 上海", aliases: ["中国", "北京", "上海", "China", "Beijing", "Shanghai"],
        offsetLabel: "UTC+8", offsetSeconds: 28800, countryCode: "CN", countryLabelZh: "中国",
        countryLabelEn: "China", canonicalZoneId: "Asia/Shanghai" },
    { id: "Asia/Urumqi", labelZh: "中国 · 乌鲁木齐", aliases: ["中国", "乌鲁木齐", "Urumqi"],
        offsetLabel: "UTC+8", offsetSeconds: 28800, countryCode: "CN", countryLabelZh: "中国",
        countryLabelEn: "China", canonicalZoneId: "Asia/Urumqi" },
    { id: "Asia/Kolkata", labelZh: "印度 · 加尔各答", aliases: ["印度", "加尔各答", "India", "Kolkata"],
        offsetLabel: "UTC+5:30", offsetSeconds: 19800, countryCode: "IN", countryLabelZh: "印度",
        countryLabelEn: "India", canonicalZoneId: "Asia/Kolkata" },
    { id: "America/New_York", labelZh: "美国东部 · 纽约", aliases: ["美国东部", "纽约", "US Eastern", "New York"],
        offsetLabel: "UTC-5", offsetSeconds: -18000, countryCode: "US", countryLabelZh: "美国",
        countryLabelEn: "United States", canonicalZoneId: "America/New_York" },
    { id: "Asia/Kathmandu", labelZh: "尼泊尔 · 加德满都", aliases: ["尼泊尔", "加德满都", "Nepal", "Kathmandu"],
        offsetLabel: "UTC+5:45", offsetSeconds: 20700, countryCode: "NP", countryLabelZh: "尼泊尔",
        countryLabelEn: "Nepal", canonicalZoneId: "Asia/Kathmandu" },
    { id: "UTC", labelZh: "协调世界时", aliases: ["协调世界时", "Coordinated Universal Time"],
        offsetLabel: "UTC+0", offsetSeconds: 0, countryCode: "UTC", countryLabelZh: "协调世界时",
        countryLabelEn: "Coordinated Universal Time", canonicalZoneId: "UTC" }
];

// 目录模式（date-only）：无 endOffsetSeconds/localTimeIssue。
const UNIT_ZONES = BASE_ZONES.map((zone) => Object.assign({}, zone));
// 会议模式：offsetSeconds=实际起点、endOffsetSeconds=实际终点（本 fixture 起止同偏移）。
const UNIT_MEETING_ZONES = BASE_ZONES.map((zone) =>
    Object.assign({}, zone, { endOffsetSeconds: zone.offsetSeconds, localTimeIssue: null }));

function makeStubApi(overrides) {
    const requests = [];
    const api = (url, opts) => {
        requests.push({ url, method: (opts && opts.method) || "GET", body: opts && opts.body });
        if (/\/meeting-confirmation\/options/.test(url)) {
            return Promise.resolve(overrides && overrides.options !== undefined
                ? overrides.options
                : { targetKey: "1:acc1", resolvedAccountCode: "acc1",
                    generatedAt: "2026-09-09T08:00:00Z", defaultZoneId: "Europe/Istanbul" });
        }
        if (/\/meeting-confirmation\/time-zones/.test(url)) {
            if (overrides && overrides.zonesError) return Promise.reject(overrides.zonesError);
            if (overrides && typeof overrides.zones === "function") {
                return Promise.resolve(overrides.zones(url));
            }
            if (overrides && Array.isArray(overrides.zones)) return Promise.resolve(overrides.zones);
            return Promise.resolve(/startLocal=/.test(url) ? UNIT_MEETING_ZONES : UNIT_ZONES);
        }
        if (/\/meeting-confirmation\/preview/.test(url)) {
            if (overrides && overrides.previewError) return Promise.reject(overrides.previewError);
            const parsed = JSON.parse((opts && opts.body) || "{}");
            const input = parsed.meeting || {};
            if (overrides && typeof overrides.preview === "function") return Promise.resolve(overrides.preview(parsed));
            const name = "Professor Basdogan";
            const start = String(input.startLocal || "2026-09-11T10:00");
            const end = String(input.endLocal || "2026-09-11T10:30");
            const zoneId = String(input.zoneId || "Europe/Istanbul");
            const text = `Dear ${name},\n\nmeeting ${start} - ${end} (${zoneId}).\n\nBest`;
            const html = `<p>Dear ${name},</p><p>meeting ${start} - ${end} (${zoneId}).</p>`;
            const ics = `BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nDTSTART:20260911T070000Z\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n`;
            return Promise.resolve({
                targetKey: "1:acc1",
                resolvedAccountCode: "acc1",
                meeting: Object.assign({}, input, { generatedAt: input.generatedAt || "2026-09-09T08:00:00Z" }),
                textBody: text,
                htmlBody: html,
                meetingTime: "Friday, September 11, 2026, 10:00 AM - 10:30 AM (UTC+3)",
                startUtc: "2026-09-11T07:00:00Z",
                endUtc: "2026-09-11T07:30:00Z",
                chinaTime: "2026/09/11 15:00 - 15:30",
                durationMinutes: 30,
                attachment: {
                    filename: `meeting-${start.slice(0, 10)}-${name.replace(/[^A-Za-z0-9-]/g, "-")}.ics`,
                    contentType: "text/calendar;charset=UTF-8",
                    icsText: ics,
                    byteLength: ics.length,
                    sha256: "a".repeat(64),
                    semanticSha256: "b".repeat(64)
                }
            });
        }
        return Promise.resolve({});
    };
    api.requests = requests;
    return api;
}

function mountComponent(opts) {
    const dom = createDom();
    installGlobalDom(dom.doc);
    const statuses = [];
    const api = makeStubApi(opts && opts.api);
    const controller = Meeting.create({
        api,
        contextPath: opts && opts.contextPath ? opts.contextPath : "",
        onApply: opts && opts.onApply ? opts.onApply : () => true,
        onStatus: (message, type) => statuses.push({ message, type })
    });
    const doc = dom.doc;
    const el = (id) => doc.getElementById(id);
    return { doc, controller, api, statuses, el, dom, openArgs: opts && opts.openArgs };
}

function openComponent(mount) {
    const dom = mount.dom;
    const trigger = dom.doc.createElement("button");
    trigger.appendChild(dom.doc.createTextNode("trigger"));
    dom.doc.body.appendChild(trigger);
    trigger.focus = () => { trigger._focused = true; };
    dom.doc.activeElement = trigger;
    mount.controller.open({
        ownerKey: "u|scope|1",
        targetKey: "1:101:acc1",
        contactId: 1,
        processingId: 101,
        senderAccountCode: "acc1",
        expertLabel: "专家A",
        editorHtml: "",
        editorText: "",
        savedMeeting: null
    });
    return trigger;
}

function openComponentWith(mount, savedMeeting) {
    const dom = mount.dom;
    const trigger = dom.doc.createElement("button");
    dom.doc.body.appendChild(trigger);
    dom.doc.activeElement = trigger;
    mount.controller.open({
        ownerKey: "u|scope|1",
        targetKey: "1:101:acc1",
        contactId: 1,
        processingId: 101,
        senderAccountCode: "acc1",
        expertLabel: "专家A",
        editorHtml: "",
        editorText: "",
        savedMeeting: savedMeeting
            ? { input: Object.assign({ templateId: 0, templateBody: "", expertSalutation: "", senderSignature: "" },
                savedMeeting) }
            : null
    });
    return trigger;
}

/** 填写完整起止+链接并驱动 目录→分组→预览 两段 debounce。 */
async function fillMeetingFields(mount, values) {
    const v = Object.assign({
        date: "2026-09-11", start: "10:00", endDate: "2026-09-11", end: "10:30",
        url: "https://zoom.us/j/1?pwd=x"
    }, values || {});
    inputInto(mount.el("meetingDate"), v.date);
    inputInto(mount.el("meetingStart"), v.start);
    inputInto(mount.el("meetingEndDate"), v.endDate);
    inputInto(mount.el("meetingEnd"), v.end);
    inputInto(mount.el("meetingUrl"), v.url);
    runTimers();          // 会议模式目录请求
    await flush();        // 目录落地 → 分组/自动选择 → 预览排队
    runTimers();          // 预览 debounce
    await flush();
}

/** 2026-10-07 09:00–09:30：巴西 4 个偏移组（-2/-3/-4/-5）。 */
async function loadBrazilGroups(mount) {
    inputInto(mount.el("meetingDate"), "2026-10-07");
    inputInto(mount.el("meetingStart"), "09:00");
    inputInto(mount.el("meetingEndDate"), "2026-10-07");
    inputInto(mount.el("meetingEnd"), "09:30");
    runTimers();
    await flush();
    pickCountry(mount, "BR");
}

function pickCountry(mount, code) {
    const select = mount.el("meetingCountry");
    select.value = code;
    eventOn(select, "change");
}

/** 多组国家自输入过滤并 mousedown 第一行候选。 */
function pickGroupOption(mount, query) {
    const search = mount.el("meetingZoneSearch");
    search.value = query;
    eventOn(search, "input");
    const list = mount.el("meetingZoneOptions");
    assert.strictEqual(list.hidden, false, "候选必须展开");
    const option = list.querySelectorAll('button[role="option"]')[0];
    assert.ok(option, `候选必须匹配 ${query}`);
    eventOn(option, "mousedown");
    return option;
}

// ════════════════════════════════════════════════════════════════════════

describe("fast-p 04 纯导出：filterZones（I-5 搜索语义）", () => {
    const ZONES = UNIT_ZONES.concat([
        { id: "Pacific/Auckland", labelZh: "新西兰 · 奥克兰", aliases: ["新西兰", "奥克兰"], offsetLabel: "UTC+12", offsetSeconds: 43200 }
    ]);

    it("空查询返回全部（不截断）", () => {
        const many = [];
        for (let i = 0; i < 500; i += 1) {
            many.push({ id: "Zone/" + i, labelZh: "城市" + i, aliases: [], offsetLabel: "UTC+8", offsetSeconds: 28800 });
        }
        const all = Meeting.filterZones(many, "");
        assert.strictEqual(all.length, 500, "空查询返回全部，不截断");
    });

    it("中文/别名/id 联合命中：土耳其、Türkiye、istanbul、Europe/Istanbul", () => {
        assert.ok(Meeting.filterZones(ZONES, "土耳其").some((z) => z.id === "Europe/Istanbul"));
        assert.ok(Meeting.filterZones(ZONES, "Türkiye").some((z) => z.id === "Europe/Istanbul"));
        assert.ok(Meeting.filterZones(ZONES, "istanbul").some((z) => z.id === "Europe/Istanbul"));
        assert.ok(Meeting.filterZones(ZONES, "Europe/Istanbul").some((z) => z.id === "Europe/Istanbul"));
        assert.strictEqual(Meeting.filterZones(ZONES, "zzzz-no-zone").length, 0);
    });

    it("偏移归一：UTC+03:00、utc+3、UTC−3（Unicode 减号）、+5:30 半小时间隔", () => {
        assert.ok(Meeting.filterZones(ZONES, "UTC+03:00").some((z) => z.id === "Europe/Istanbul"));
        assert.ok(Meeting.filterZones(ZONES, "utc+3").some((z) => z.id === "Europe/Istanbul"));
        assert.strictEqual(Meeting.filterZones(ZONES, "UTC+03:00").length, 1, "偏移秒数相等才算命中");
        assert.ok(Meeting.filterZones(ZONES, "UTC\u22125").some((z) => z.id === "America/New_York"), "Unicode 减号归一");
        assert.ok(Meeting.filterZones(ZONES, "UTC+5:30").some((z) => z.id === "Asia/Kolkata"), "半小时间隔命中");
        assert.ok(Meeting.filterZones(ZONES, "UTC+5").some((z) => z.id === "Asia/Kolkata"), "整小时查询命中 +5:30 偏移");
        assert.ok(!Meeting.filterZones(ZONES, "UTC+9").some((z) => z.id === "Asia/Kolkata"));
    });

    it("大小写/空白/全角归一后匹配；结果不截断", () => {
        assert.strictEqual(Meeting.filterZones(ZONES, "  EUROPE/ISTANBUL  ").length, 1);
        assert.strictEqual(Meeting.filterZones(ZONES, "ＩＳＴＡＮＢＵＬ").length, 1, "全角字母 NFKC");
        assert.ok(Meeting.filterZones(ZONES, "utc+8").length >= 1);
    });
});

describe("fast-p 02 纯导出：groupMeetingZones（F-1/F-2 国家＋本场偏移分组）", () => {
    it("同国相同起止偏移合并为一行；不跨国合并同偏移", () => {
        const groups = Meeting.groupMeetingZones(UNIT_MEETING_ZONES, "BR");
        assert.deepStrictEqual(groups.map((g) => Meeting.groupOffsetLabel(g)),
            ["UTC-2", "UTC-3", "UTC-4", "UTC-5"], "巴西 4 个偏移组，起点降序");
        assert.strictEqual(groups.length, 4, "UTC-3 只出现一次");
        // 美国 + 中国都在 fixture 里但同偏移，跨国绝不合并
        const us = Meeting.groupMeetingZones(UNIT_MEETING_ZONES, "US");
        assert.strictEqual(us.length, 1);
        assert.strictEqual(us[0].members.every((m) => m.countryCode === "US"), true);
    });

    it("Sao_Paulo 与 Brazil/East 同组，代表取有效 canonical（不猜城市）", () => {
        const groups = Meeting.groupMeetingZones(UNIT_MEETING_ZONES, "BR");
        const minusThree = groups.find((g) => g.startOffsetSeconds === -10800);
        assert.deepStrictEqual(minusThree.memberIds.slice().sort(),
            ["America/Sao_Paulo", "Brazil/East"], "旧别名与标准 ID 同组");
        assert.strictEqual(minusThree.representative.id, "America/Sao_Paulo", "代表 id==canonicalZoneId");
        assert.strictEqual(minusThree.members.length, 2);
    });

    it("两端偏移不同用 → 保留 DST 变化；小数偏移保留分钟", () => {
        const straddle = [
            { id: "America/New_York", offsetLabel: "UTC-5", offsetSeconds: -18000, endOffsetSeconds: -14400,
                countryCode: "US", countryLabelZh: "美国", canonicalZoneId: "America/New_York", localTimeIssue: null }
        ];
        const groups = Meeting.groupMeetingZones(straddle, "US");
        assert.strictEqual(groups.length, 1);
        assert.strictEqual(Meeting.groupOffsetLabel(groups[0]), "UTC-5 → UTC-4");
        assert.strictEqual(Meeting.formatOffsetSeconds(19800), "UTC+5:30");
        assert.strictEqual(Meeting.formatOffsetSeconds(20700), "UTC+5:45");
        assert.strictEqual(Meeting.formatOffsetSeconds(0), "UTC+0");
        const india = Meeting.groupMeetingZones(UNIT_MEETING_ZONES, "IN");
        assert.strictEqual(Meeting.groupOffsetLabel(india[0]), "UTC+5:30", "分钟偏移保留");
    });

    it("缺国家元信息/无 endOffsetSeconds/带 localTimeIssue 的项不进组", () => {
        const mixed = [
            { id: "A", offsetSeconds: 3600, endOffsetSeconds: 3600, countryCode: "XX", canonicalZoneId: "A" },
            { id: "B", offsetSeconds: 3600, countryCode: "XX", canonicalZoneId: "B" }, // 无 endOffset
            { id: "C", offsetSeconds: 3600, endOffsetSeconds: 3600, countryCode: "XX", canonicalZoneId: "C",
                localTimeIssue: "该当地时间不存在，请避开夏令时跳时区区间" },
            { id: "D", offsetSeconds: 3600, endOffsetSeconds: 3600, canonicalZoneId: "D" }, // 无国家
            { id: "E", offsetSeconds: 3600, endOffsetSeconds: 3600, countryCode: "YY", canonicalZoneId: "E" }
        ];
        const groups = Meeting.groupMeetingZones(mixed, "XX");
        assert.strictEqual(groups.length, 1);
        assert.deepStrictEqual(groups[0].memberIds, ["A"], "只保留有国家元信息且有效的成员");
        assert.deepStrictEqual(Meeting.groupMeetingZones(mixed, "YY")[0].memberIds, ["E"]);
        assert.deepStrictEqual(Meeting.groupMeetingZones(mixed, ""), [], "空国家不分组");
    });

    it("排序：起点偏移降序 → 终点偏移降序 → 代表 ID 字典序", () => {
        const zones = [
            { id: "Z/Zulu", offsetSeconds: 0, endOffsetSeconds: -3600, countryCode: "ZZ", canonicalZoneId: "Z/Zulu" },
            { id: "A/Alpha", offsetSeconds: 0, endOffsetSeconds: 0, countryCode: "ZZ", canonicalZoneId: "A/Alpha" },
            { id: "M/Mike", offsetSeconds: 3600, endOffsetSeconds: 7200, countryCode: "ZZ", canonicalZoneId: "M/Mike" },
            { id: "N/November", offsetSeconds: 3600, endOffsetSeconds: 3600, countryCode: "ZZ", canonicalZoneId: "N/November" }
        ];
        const groups = Meeting.groupMeetingZones(zones, "ZZ");
        assert.deepStrictEqual(groups.map((g) => g.representative.id),
            ["M/Mike", "N/November", "A/Alpha", "Z/Zulu"]);
    });

    it("buildCountryOptions：UTC 显式特殊项、无国家 SystemV 排除、中文名排序同码排序", () => {
        const options = Meeting.buildCountryOptions(UNIT_ZONES.concat([
            { id: "SystemV/EST5", offsetLabel: "UTC-5", offsetSeconds: -18000, countryCode: null }
        ]));
        assert.strictEqual(options.some((o) => o.code === "UTC"), true, "UTC 显式特殊项");
        assert.strictEqual(options.find((o) => o.code === "UTC").label, "协调世界时");
        assert.strictEqual(options.some((o) => !o.code), false, "null 国家不进列表");
        assert.deepStrictEqual(options.map((o) => o.code), ["BR", "US", "NP", "TR", "UTC", "IN", "CN"], "中文名排序");
    });
});

describe("fast-p 04 纯导出：normalizeMeetingText", () => {
    it("NBSP/全角/回车/多空格/空行归一，行间边界保留", () => {
        const raw = "Dear \u00a0Prof\u3000Basdogan\r\n\r\n  10:00 - 10:30   \r\n";
        assert.strictEqual(Meeting.normalizeMeetingText(raw), "Dear Prof Basdogan\n10:00 - 10:30");
        assert.strictEqual(Meeting.normalizeMeetingText(""), "");
        assert.strictEqual(Meeting.normalizeMeetingText(null), "");
        assert.strictEqual(Meeting.normalizeMeetingText("  a\nb  "), "a\nb");
    });
});

describe("fast-p 04 纯导出：sanitizeDraftHtml（I-6 白名单）", () => {
    function sanitize(html) {
        const dom = createDom();
        return Meeting.sanitizeDraftHtml(html, dom.doc);
    }

    it("保留 p/b/strong/i/em/u/ul/ol/li/br/span；href 白名单 + target/rel 固定", () => {
        const out = sanitize('<p>a <b>bold</b><i>it</i><u>un</u></p><ul><li>one</li></ul><a href="https://zoom.us/j/1?pwd=x">join</a><a href="mailto:a@b.c">mail</a>');
        assert.ok(out.includes("<b>bold</b>"));
        assert.ok(out.includes("<i>it</i>"));
        assert.ok(out.includes("<u>un</u>"));
        assert.ok(out.includes("<ul><li>one</li></ul>"));
        assert.ok(out.includes('href="https://zoom.us/j/1?pwd=x"'));
        assert.ok(out.includes('target="_blank"'));
        assert.ok(out.includes('rel="noopener noreferrer"'));
        assert.ok(out.includes('href="mailto:a@b.c"'));
    });

    it("script/style/iframe/object 连内容删除；事件/style 属性删除；未知标签 unwrap", () => {
        const out = sanitize('<p>ok</p><script>alert(1)</script><style>p{color:red}</style><iframe src="x"></iframe><object data="y"></object><font color="red">txt</font><h2>heading</h2><div onclick="evil()" style="color:red">divtext</div>');
        assert.ok(!out.includes("alert"), "script 内容删除");
        assert.ok(!out.includes("color:red"));
        assert.ok(!out.includes("onclick"));
        assert.ok(!out.includes("<iframe"));
        assert.ok(!out.includes("<object"));
        assert.ok(!out.includes("<font"));
        assert.ok(!out.includes("<h2"));
        assert.ok(out.includes("txt"));
        assert.ok(out.includes("heading"));
        assert.ok(out.includes("divtext"), "普通 div 保留子文本");
        assert.ok(out.includes("<p>ok</p>"));
    });

    it("javascript: 链接去 href 保留文本；其它 div class/data 剥除", () => {
        const out = sanitize('<a href="javascript:alert(1)">bad</a><a href="https://ok">good</a>');
        assert.ok(!out.includes("javascript:"));
        assert.ok(out.includes(">bad</a>") || out.includes("bad"));
        assert.ok(out.includes('href="https://ok"'));
        const block = sanitize('<div class="meeting-body-block" data-meeting-block="true" data-x="1"><p>keep</p></div>');
        assert.ok(block.includes('class="meeting-body-block"'));
        assert.ok(block.includes('data-meeting-block="true"'));
        assert.ok(!block.includes("data-x"), "其它属性删除");
        const plain = sanitize('<div class="other" data-meeting-block="false"><p>strip</p></div>');
        assert.ok(!plain.includes("meeting-body-block"), "非会议标记 div 剥除 class");
    });

    it("脏邮件 HTML 只显示文本/去事件，不把事件脚本带进编辑器", () => {
        const out = sanitize('<p>Hello <a href="https://zoom.us/j/9" onclick="window.x=1">link</a></p><img src="x" onerror="alert(2)"><br>');
        assert.ok(!out.includes("onerror") && !out.includes("onclick"));
        assert.ok(!out.includes("<img"));
        assert.ok(out.includes("Hello"));
    });

    it("空/无文档输入为空串（防注入）", () => {
        assert.strictEqual(Meeting.sanitizeDraftHtml("", null), "");
        assert.strictEqual(Meeting.sanitizeDraftHtml(null, null), "");
        assert.strictEqual(Meeting.sanitizeDraftHtml("<script>x</script>", null), "");
    });
});

describe("fast-p 04 纯导出：planMeetingInsertion（T3 插入语义）", () => {
    const previewResult = {
        htmlBody: "<p>Dear Prof,</p><p>meeting 2026-09-11 10:00 - 10:30</p>"
    };

    function editorWith(html) {
        const dom = createDom();
        const editor = dom.doc.createElement("div");
        if (html) editor.innerHTML = html;
        return { editor, doc: dom.doc };
    }

    function meetingBlockHtml(html) {
        return '<div class="meeting-body-block" data-meeting-block="true"><p>x</p></div>';
    }

    it("空正文 → replace（qaClear）；正文已存在 → append（保留 QA）", () => {
        const empty = editorWith("");
        let plan = Meeting.planMeetingInsertion(empty.editor, null, previewResult, "append");
        assert.strictEqual(plan.action, "replace");
        assert.strictEqual(plan.qaClear, true);
        assert.ok(plan.allowed);
        const withText = editorWith("<p>typed note</p>");
        plan = Meeting.planMeetingInsertion(withText.editor, null, previewResult, "append");
        assert.strictEqual(plan.action, "append");
        assert.strictEqual(plan.qaClear, false);
    });

    it("显式 replace：始终 replace + qaClear", () => {
        const ed = editorWith("<p>typed</p>");
        const plan = Meeting.planMeetingInsertion(ed.editor, null, previewResult, "replace");
        assert.strictEqual(plan.action, "replace");
        assert.strictEqual(plan.qaClear, true);
    });

    it("已保存 + 唯一未手改块 → update（原位替换，不清 QA）", () => {
        const saved = { blockText: "Dear Prof meeting 2026-09-11 10:00 - 10:30" };
        const ed = editorWith(meetingBlockHtml(null));
        // 块文本与 saved.blockText 一致（构造块内容文本）
        const dom2 = createDom();
        const editor2 = dom2.doc.createElement("div");
        editor2.innerHTML = '<div class="meeting-body-block" data-meeting-block="true"><p>Dear Prof meeting 2026-09-11 10:00 - 10:30</p></div>';
        const plan = Meeting.planMeetingInsertion(editor2, saved, previewResult, "append");
        assert.strictEqual(plan.allowed, true);
        assert.strictEqual(plan.action, "update");
        assert.strictEqual(plan.qaClear, false);
    });

    it("已保存但块被手改 → allowed=false reason=hand-edited（禁止追加/静默覆盖）", () => {
        const saved = { blockText: "Dear Prof meeting 2026-09-11 10:00 - 10:30" };
        const dom2 = createDom();
        const editor2 = dom2.doc.createElement("div");
        editor2.innerHTML = '<div class="meeting-body-block" data-meeting-block="true"><p>Dear Prof meeting hand changed</p></div>';
        const plan = Meeting.planMeetingInsertion(editor2, saved, previewResult, "append");
        assert.strictEqual(plan.allowed, false);
        assert.strictEqual(plan.reason, "hand-edited");
    });

    it("块内容经 sanitize 白名单（script 剥离）", () => {
        const ed = editorWith("");
        const plan = Meeting.planMeetingInsertion(ed.editor, null, {
            htmlBody: "<p>ok</p><script>alert(1)</script>"
        }, "append");
        assert.ok(plan.blockHtml.includes("<p>ok</p>"));
        assert.ok(!plan.blockHtml.includes("alert"));
    });
});

describe("fast-p 04 组件：create/open/close/dispose 生命周期（S-2）", () => {
    after(() => restoreGlobalDom());

    it("open 创建唯一 dialog 于 body；options/zones 请求发生；取消可关闭；dispose 移除", async () => {
        const mount = mountComponent();
        const trigger = openComponent(mount);
        await flush();
        const dialog = mount.doc.getElementById("meetingDialog");
        assert.ok(dialog, "dialog 必须存在");
        assert.ok(mount.doc.body.contains(dialog));
        assert.strictEqual(mount.doc.getElementById("meetingDialog"), dialog);
        assert.strictEqual(mount.api.requests.filter((r) => /options/.test(r.url)).length, 1);
        assert.strictEqual(mount.api.requests.filter((r) => /time-zones/.test(r.url)).length, 1);
        assert.ok(dialog.hasAttribute("open"), "showModal/兜底 open");
        // 取消关闭且恢复焦点
        mount.controller.close({ restoreFocus: true });
        assert.strictEqual(dialog.hasAttribute("open"), false);
        assert.strictEqual(trigger._focused, true, "关闭恢复触发按钮焦点");
        mount.controller.dispose();
        assert.strictEqual(mount.doc.getElementById("meetingDialog"), null, "dispose 移除 dialog");
        mount.controller.dispose();
    });

    it("重复 create 先 dispose 旧实例（全局 id 唯一）", async () => {
        const m1 = mountComponent();
        const t1 = openComponent(m1);
        await flush();
        assert.ok(m1.doc.getElementById("meetingDialog"));
        const m2 = mountComponent();
        await flush();
        // 旧 dialog 被移除，新 dialog 就位
        assert.strictEqual(m1.doc.getElementById("meetingDialog"), m2.doc.getElementById("meetingDialog") || null);
        m2.controller.dispose();
        m1.controller.dispose();
    });

    it("backdrop（dialog 自身点击）不关闭；Esc 在列表关闭态才关弹窗", async () => {
        const mount = mountComponent();
        openComponent(mount);
        await flush();
        const dialog = mount.doc.getElementById("meetingDialog");
        clickEl(dialog);
        assert.strictEqual(dialog.hasAttribute("open"), true, "点击 backdrop 不关闭");
        // 第二次 Esc 关闭
        eventOn(dialog, "keydown", "Escape");
        assert.strictEqual(dialog.hasAttribute("open"), false);
        mount.controller.dispose();
    });

    it("loading 阶段字段禁用、确认/下载禁用；填写完整后按国家自动选时区并预览", async () => {
        const mount = mountComponent();
        openComponent(mount);
        await flush();
        assert.strictEqual(mount.el("meetingName"), null, "称呼输入已移除");
        assert.strictEqual(mount.el("meetingTemplate"), null, "模板选择已移除");
        assert.strictEqual(mount.el("templateText"), null, "模板正文已移除");
        assert.strictEqual(mount.el("meetingSignature"), null, "签名输入已移除");
        assert.strictEqual(mount.el("meetingCountry").value, "TR", "默认国家来自 options.defaultZoneId");
        assert.strictEqual(mount.el("meetingCountrySummary").textContent, "填写完整会议日期和时间后显示时区。");
        assert.strictEqual(mount.el("meetingZoneField").hidden, true, "未填完整时间不显示时区选择");
        assert.strictEqual(mount.el("meetingZoneSearch").value, "", "未填完整时间不给 noon 结果当选项");
        assert.strictEqual(mount.el("meetingDate").value, "");
        // 初次打开只有 date-only 目录请求
        const dateOnly = mount.api.requests.filter((r) => /time-zones/.test(r.url));
        assert.strictEqual(dateOnly.length, 1);
        assert.ok(!/startLocal=/.test(dateOnly[0].url), "未填完整时间不发会议模式请求");
        // 填写完整 → 会议模式目录 → 土耳其单组自动采用 → 预览
        await fillMeetingFields(mount);
        assert.strictEqual(mount.el("meetingZoneField").hidden, true, "土耳其一个 group 整块隐藏");
        assert.strictEqual(mount.el("meetingCountrySummary").textContent, "土耳其（UTC+3）");
        assert.strictEqual(mount.el("meetingZoneSearch").value, "土耳其（UTC+3）");
        assert.strictEqual(mount.el("meetingZoneHint").textContent,
            "土耳其（UTC+3） · 日期和时间均按此时区填写");
        assert.strictEqual(mount.el("applyMeeting").disabled, false, "ready 后可确认");
        const zoneCalls = mount.api.requests.filter((r) => /time-zones/.test(r.url));
        assert.ok(/startLocal=2026-09-11T10%3A00/.test(zoneCalls[zoneCalls.length - 1].url));
        assert.ok(/endLocal=2026-09-11T10%3A30/.test(zoneCalls[zoneCalls.length - 1].url));
        const filename = mount.el("meetingFilename").textContent;
        assert.match(filename, /^meeting-2026-09-11-/);
        // I-3：新请求只携带时区/起止本地时间/Zoom/generatedAt
        const previews = mount.api.requests.filter((r) => /preview/.test(r.url));
        const payload = JSON.parse(previews[previews.length - 1].body);
        assert.strictEqual(payload.meeting.zoneId, "Europe/Istanbul", "提交真实 zoneId");
        assert.deepStrictEqual(Object.keys(payload.meeting).sort(),
            ["endLocal", "generatedAt", "startLocal", "zoneId", "zoomUrl"]);
        mount.controller.dispose();
    });

    it("savedMeeting 的旧别名 raw ID 原样提交；分组选择才用 canonical 代表", async () => {
        const mount = mountComponent();
        openComponentWith(mount, {
            zoneId: "Brazil/East",
            startLocal: "2026-10-07T09:00",
            endLocal: "2026-10-07T09:30",
            zoomUrl: "https://zoom.us/j/9?pwd=saved",
            generatedAt: "2026-09-09T08:00:00Z"
        });
        await flush();
        runTimers();
        await flush();
        assert.strictEqual(mount.el("meetingCountry").value, "BR");
        assert.strictEqual(mount.el("meetingZoneSearch").value, "巴西（UTC-3）", "国家＋UTC 展示");
        const previews = mount.api.requests.filter((r) => /preview/.test(r.url));
        assert.ok(previews.length >= 1, "saved 完整时间直接预览");
        assert.strictEqual(JSON.parse(previews[previews.length - 1].body).meeting.zoneId, "Brazil/East",
            "旧别名 raw ID 不静默换成员");
        mount.controller.dispose();
    });

    it("saved 的 SystemV/未知 zone 认不出国家：保留 raw、阻断应用、提示重选国家", async () => {
        const mount = mountComponent();
        openComponentWith(mount, {
            zoneId: "SystemV/EST5",
            startLocal: "2026-10-07T09:00",
            endLocal: "2026-10-07T09:30",
            zoomUrl: "https://zoom.us/j/9?pwd=saved",
            generatedAt: "2026-09-09T08:00:00Z"
        });
        await flush();
        runTimers();
        await flush();
        assert.strictEqual(mount.el("meetingCountry").value, "", "不回退中国/上海");
        assert.strictEqual(mount.el("meetingCountrySummary").textContent,
            "该旧时区没有国家归属，请重新选择国家和时区");
        assert.strictEqual(mount.el("applyMeeting").disabled, true, "不允许应用");
        assert.strictEqual(mount.api.requests.filter((r) => /preview/.test(r.url)).length, 0);
        mount.controller.dispose();
    });
});

describe("fast-p 02 组件：国家选择与多时区分组（F-1/F-2/F-3/F-5）", () => {
    it("巴西多组必选；候选只显示国家＋UTC、无城市/IANA；选择后摘要与 payload 同步", async () => {
        const mount = mountComponent();
        openComponent(mount);
        await flush();
        await loadBrazilGroups(mount);
        const search = mount.el("meetingZoneSearch");
        const list = mount.el("meetingZoneOptions");
        assert.strictEqual(mount.el("meetingCountry").value, "BR");
        assert.strictEqual(mount.el("meetingZoneField").hidden, false, "多个 group 才显示选择器");
        assert.strictEqual(mount.el("meetingCountrySummary").textContent, "该国家/地区有多个时区，请选择。");
        eventOn(search, "focus");
        assert.strictEqual(list.hidden, false);
        assert.strictEqual(list.querySelectorAll('button[role="option"]').length, 4, "巴西 4 个偏移组");
        const labels = Array.prototype.slice.call(list.querySelectorAll('[data-role="zone-label"]'))
            .map((n) => n.textContent);
        const offsets = Array.prototype.slice.call(list.querySelectorAll('[data-role="zone-offset"]'))
            .map((n) => n.textContent);
        assert.deepStrictEqual(labels, ["巴西", "巴西", "巴西", "巴西"], "候选只有国家名，无城市");
        assert.deepStrictEqual(offsets, ["UTC-2", "UTC-3", "UTC-4", "UTC-5"], "偏移升序外的降序排列");
        const visible = Array.prototype.slice.call(list.querySelectorAll('button[role="option"]'))
            .map((b) => b.textContent).join(" ");
        assert.strictEqual(visible.includes("America/Sao_Paulo"), false, "候选不展示原始 IANA");
        assert.strictEqual(visible.includes("East Time"), false, "无 East Time");
        assert.strictEqual(visible.includes("圣保罗"), false, "候选不展示城市");
        assert.strictEqual(list.innerHTML.includes("<small"), false, "候选删除 IANA small");
        assert.strictEqual(list.querySelectorAll('button[role="option"]')[1].getAttribute("data-zone"),
            "America/Sao_Paulo", "data-zone 仍是真实提交 ID");
        // 未显式选择不能应用
        assert.strictEqual(mount.el("applyMeeting").disabled, true, "多组未选不得应用");
        // 过滤到 -3 组（同组只一行），Enter 显式选中 canonical 代表
        search.value = "Sao_Paulo";
        eventOn(search, "input");
        assert.strictEqual(list.querySelectorAll('button[role="option"]').length, 1, "别名与标准 ID 同组只一行");
        eventOn(search, "keydown", "Enter");
        assert.strictEqual(search.value, "巴西（UTC-3）");
        assert.strictEqual(mount.el("meetingZoneHint").textContent, "巴西（UTC-3） · 日期和时间均按此时区填写");
        assert.strictEqual(mount.el("meetingCountrySummary").textContent, "巴西（UTC-3）");
        assert.strictEqual(list.hidden, true, "选择后关闭候选");
        // 提交真实 zoneId（canonical 代表），不新增字段
        inputInto(mount.el("meetingUrl"), "https://zoom.us/j/1?pwd=x");
        runTimers();
        await flush();
        const previews = mount.api.requests.filter((r) => /preview/.test(r.url));
        const payload = JSON.parse(previews[previews.length - 1].body);
        assert.strictEqual(payload.meeting.zoneId, "America/Sao_Paulo");
        assert.deepStrictEqual(Object.keys(payload.meeting).sort(),
            ["endLocal", "generatedAt", "startLocal", "zoneId", "zoomUrl"]);
        mount.controller.dispose();
    });

    it("键盘 active/selected 分离：Arrow 移动 focused 不改 selected；aria 同步", async () => {
        const mount = mountComponent();
        openComponent(mount);
        await flush();
        await loadBrazilGroups(mount);
        const search = mount.el("meetingZoneSearch");
        const list = mount.el("meetingZoneOptions");
        // 先显式选中 UTC-5（Rio_Branco）
        eventOn(search, "focus");
        search.value = "Rio_Branco";
        eventOn(search, "input");
        eventOn(search, "keydown", "Enter");
        assert.strictEqual(search.value, "巴西（UTC-5）");
        // Arrow 只移动 focused
        eventOn(search, "focus");
        assert.strictEqual(list.hidden, false, "focus 重新展开全部 4 组");
        eventOn(search, "keydown", "ArrowDown");
        const first = list.querySelector('button[role="option"]');
        assert.ok(first.classList.contains("focused"));
        assert.strictEqual(search.getAttribute("aria-activedescendant"), first.getAttribute("id"));
        assert.strictEqual(mount.el("meetingZoneHint").textContent, "巴西（UTC-5） · 日期和时间均按此时区填写",
            "Arrow 不改 selected");
        eventOn(search, "keydown", "ArrowDown");
        const second = list.querySelectorAll('button[role="option"]')[1];
        assert.ok(second.classList.contains("focused"));
        assert.ok(!first.classList.contains("focused"));
        eventOn(search, "keydown", "Enter");
        assert.strictEqual(search.value, "巴西（UTC-3）", "Enter 选中 active 组（canonical 代表）");
        assert.strictEqual(mount.el("meetingZoneHint").textContent, "巴西（UTC-3） · 日期和时间均按此时区填写");
        mount.controller.dispose();
    });

    it("焦点在被隐藏时区块外移；单一 group 自动采用并隐藏选择器", async () => {
        const mount = mountComponent();
        openComponent(mount);
        await flush();
        await loadBrazilGroups(mount);
        const search = mount.el("meetingZoneSearch");
        eventOn(search, "focus");
        mount.doc.activeElement = search;
        pickCountry(mount, "IN");
        assert.strictEqual(mount.el("meetingZoneField").hidden, true, "印度单组整块隐藏");
        assert.strictEqual(mount.el("meetingCountrySummary").textContent, "印度（UTC+5:30）");
        assert.strictEqual(mount.el("meetingZoneSearch").value, "印度（UTC+5:30）");
        assert.strictEqual(mount.el("meetingZoneOptions").hidden, true, "隐藏时列表关闭");
        assert.strictEqual(search.getAttribute("aria-activedescendant"), null, "清理 aria-activedescendant");
        assert.strictEqual(mount.doc.activeElement, mount.el("meetingCountrySearch"), "焦点移出被隐藏区域");
        mount.controller.dispose();
    });

    it("0 个有效 group 提示调整时间并阻断；UTC 特殊项可显式选择", async () => {
        const mount = mountComponent({
            api: {
                zones: (url) => (/startLocal=/.test(url)
                    ? UNIT_MEETING_ZONES.map((zone) => Object.assign({}, zone, { endOffsetSeconds: null }))
                    : UNIT_ZONES)
            }
        });
        openComponent(mount);
        await flush();
        inputInto(mount.el("meetingDate"), "2026-09-11");
        inputInto(mount.el("meetingStart"), "10:00");
        inputInto(mount.el("meetingEndDate"), "2026-09-11");
        inputInto(mount.el("meetingEnd"), "10:30");
        inputInto(mount.el("meetingUrl"), "https://zoom.us/j/1?pwd=x");
        runTimers();
        await flush();
        assert.strictEqual(mount.el("meetingCountrySummary").textContent,
            "该时间没有可用时区，请调整日期或时间。");
        assert.strictEqual(mount.el("applyMeeting").disabled, true);
        assert.strictEqual(mount.api.requests.filter((r) => /preview/.test(r.url)).length, 0, "无分组不发预览");
        mount.controller.dispose();
    });

    it("改期后原组成员落入多个新偏移对：清选择并要求重选（F-2）", async () => {
        const catalog = (summer) => [
            { id: "America/Denver", labelZh: "美国 · 丹佛", aliases: ["美国", "丹佛"],
                offsetLabel: summer ? "UTC-6" : "UTC-7", offsetSeconds: summer ? -21600 : -25200,
                endOffsetSeconds: summer ? -21600 : -25200, countryCode: "US", countryLabelZh: "美国",
                canonicalZoneId: "America/Denver", localTimeIssue: null },
            { id: "America/Phoenix", labelZh: "美国 · 凤凰城", aliases: ["美国", "凤凰城"],
                offsetLabel: "UTC-7", offsetSeconds: -25200, endOffsetSeconds: -25200,
                countryCode: "US", countryLabelZh: "美国", canonicalZoneId: "America/Phoenix",
                localTimeIssue: null }
        ];
        const mount = mountComponent({
            api: { zones: (url) => (/startLocal=/.test(url) ? catalog(/2026-07-15/.test(url)) : UNIT_ZONES) }
        });
        openComponentWith(mount, {
            zoneId: "America/Denver", startLocal: "2026-01-15T09:00", endLocal: "2026-01-15T09:30",
            zoomUrl: "https://zoom.us/j/1?pwd=x", generatedAt: "2026-09-09T08:00:00Z"
        });
        await flush();
        runTimers();
        await flush();
        assert.strictEqual(mount.el("meetingCountry").value, "US");
        assert.strictEqual(mount.el("meetingCountrySummary").textContent, "美国（UTC-7）", "冬同组");
        // 改到夏季：Denver -6 / Phoenix -7 → 原组拆开
        inputInto(mount.el("meetingDate"), "2026-07-15");
        runTimers();
        await flush();
        runTimers();
        await flush();
        assert.match(mount.el("meetingLoadStatus").textContent, /原时区选项已分开，请重新选择/);
        assert.strictEqual(mount.el("meetingZoneSearch").value, "", "清空原选择");
        assert.strictEqual(mount.el("meetingCountrySummary").textContent, "该国家/地区有多个时区，请选择。");
        assert.strictEqual(mount.el("meetingZoneField").hidden, false, "分拆后重新显示选择器");
        assert.strictEqual(mount.el("applyMeeting").disabled, true, "分拆后禁止应用");
        assert.strictEqual(mount.el("meetingDate").value, "2026-07-15", "日期不因分拆回退");
        assert.strictEqual(mount.el("meetingUrl").value, "https://zoom.us/j/1?pwd=x", "链接不因分拆回退");
        mount.controller.dispose();
    });

    it("逆序目录响应：旧 seq 结果不得覆盖当前分组/选择（F-4）", async () => {
        const pending = [];
        const catalog = (offset) => ([
            { id: "Europe/Istanbul", labelZh: "土耳其 · 伊斯坦布尔", aliases: [],
                offsetLabel: "UTC+" + offset, offsetSeconds: offset * 3600,
                endOffsetSeconds: offset * 3600, countryCode: "TR", countryLabelZh: "土耳其",
                canonicalZoneId: "Europe/Istanbul", localTimeIssue: null }
        ]);
        const mount = mountComponent({
            api: {
                zones: (url) => {
                    if (!/startLocal=/.test(url)) return UNIT_ZONES;
                    const offset = /11%3A00/.test(url) ? 4 : 3;
                    return new Promise((resolve) => pending.push({ offset, resolve: () => resolve(catalog(offset)) }));
                }
            }
        });
        openComponent(mount);
        await flush();
        inputInto(mount.el("meetingDate"), "2026-09-11");
        inputInto(mount.el("meetingStart"), "10:00");
        inputInto(mount.el("meetingEndDate"), "2026-09-11");
        inputInto(mount.el("meetingEnd"), "10:30");
        inputInto(mount.el("meetingUrl"), "https://zoom.us/j/1?pwd=x");
        runTimers();
        await flush();
        assert.strictEqual(pending.length, 1);
        inputInto(mount.el("meetingEnd"), "11:00");
        runTimers();
        await flush();
        assert.strictEqual(pending.length, 2);
        // 新结果先回：采用 offset +4
        pending[1].resolve();
        await flush();
        runTimers();
        await flush();
        assert.strictEqual(mount.el("meetingCountrySummary").textContent, "土耳其（UTC+4）");
        assert.strictEqual(mount.el("applyMeeting").disabled, false);
        // 旧结果后回：seq 过期，不得覆盖
        pending[0].resolve();
        await flush();
        runTimers();
        await flush();
        assert.strictEqual(mount.el("meetingCountrySummary").textContent, "土耳其（UTC+4）", "旧目录不得覆盖");
        assert.strictEqual(mount.el("meetingZoneSearch").value, "土耳其（UTC+4）");
        const previews = mount.api.requests.filter((r) => /preview/.test(r.url));
        assert.strictEqual(JSON.parse(previews[previews.length - 1].body).meeting.endLocal, "2026-09-11T11:00",
            "当前预览属于最新起止");
        mount.controller.dispose();
    });

    it("switch 国家清空原选择并要求重选（不静默沿用旧国家）", async () => {
        const mount = mountComponent();
        openComponent(mount);
        await flush();
        await fillMeetingFields(mount);
        assert.strictEqual(mount.el("meetingCountry").value, "TR");
        assert.strictEqual(mount.el("meetingZoneSearch").value, "土耳其（UTC+3）", "单组自动采用");
        assert.strictEqual(mount.el("applyMeeting").disabled, false, "已自动采用的可继续");
        // 切到多组国家：清空原选择，必须重新选择
        pickCountry(mount, "BR");
        assert.strictEqual(mount.el("meetingCountrySummary").textContent, "该国家/地区有多个时区，请选择。");
        assert.strictEqual(mount.el("meetingZoneSearch").value, "", "切国家清空旧选择");
        assert.strictEqual(mount.el("applyMeeting").disabled, true, "多组未选不得应用");
        // 明确选择后恢复可继续使用
        pickGroupOption(mount, "Sao_Paulo");
        assert.strictEqual(mount.el("meetingZoneSearch").value, "巴西（UTC-3）");
        runTimers();
        await flush();
        assert.strictEqual(mount.el("applyMeeting").disabled, false, "明确选择后可应用");
        mount.controller.dispose();
    });
});

describe("fast-p 02 组件：时区目录异步门禁（F-4）", () => {
    it("目录失败阻断预览/应用并可重试；不沿用旧目录", async () => {
        let fail = true;
        const failing = mountComponent({
            api: {
                zones: (url) => {
                    if (fail && /startLocal=/.test(url)) return Promise.reject(new Error("network down"));
                    return /startLocal=/.test(url) ? UNIT_MEETING_ZONES : UNIT_ZONES;
                }
            }
        });
        openComponent(failing);
        await flush();
        inputInto(failing.el("meetingDate"), "2026-09-11");
        inputInto(failing.el("meetingStart"), "10:00");
        inputInto(failing.el("meetingEndDate"), "2026-09-11");
        inputInto(failing.el("meetingEnd"), "10:30");
        inputInto(failing.el("meetingUrl"), "https://zoom.us/j/1?pwd=x");
        runTimers();
        await flush();
        assert.match(failing.el("meetingLoadStatus").textContent, /会议时区加载失败，请重试/);
        assert.strictEqual(failing.el("meetingLoadStatus").hidden, false);
        assert.strictEqual(failing.el("retryMeeting").hidden, false, "重试按钮出现");
        assert.strictEqual(failing.el("applyMeeting").disabled, true, "失败时不可应用");
        assert.strictEqual(failing.el("downloadMeeting").getAttribute("aria-disabled"), "true", "下载不可用");
        assert.strictEqual(failing.api.requests.filter((r) => /preview/.test(r.url)).length, 0);
        // 重试只重发当前起止目录，不重置表单
        fail = false;
        const before = failing.api.requests.filter((r) => /time-zones/.test(r.url)).length;
        clickEl(failing.el("retryMeeting"));
        await flush();
        runTimers();
        await flush();
        const after = failing.api.requests.filter((r) => /time-zones/.test(r.url));
        assert.ok(after.length > before, "重试重新请求目录");
        assert.strictEqual(failing.el("meetingDate").value, "2026-09-11", "重试保留表单日期");
        assert.strictEqual(failing.el("meetingUrl").value, "https://zoom.us/j/1?pwd=x");
        failing.controller.dispose();
    });

    it("目录缺 01 国家元信息：阻断预览/应用并提示刷新", async () => {
        const mount = mountComponent({
            api: {
                zones: () => Promise.resolve([
                    { id: "Europe/Istanbul", labelZh: "土耳其 · 伊斯坦布尔", aliases: [], offsetLabel: "UTC+3", offsetSeconds: 10800 }
                ])
            }
        });
        openComponent(mount);
        await flush();
        assert.match(mount.el("meetingLoadStatus").textContent, /会议时区配置版本不匹配，请刷新后重试/);
        inputInto(mount.el("meetingDate"), "2026-09-11");
        inputInto(mount.el("meetingStart"), "10:00");
        inputInto(mount.el("meetingEndDate"), "2026-09-11");
        inputInto(mount.el("meetingEnd"), "10:30");
        inputInto(mount.el("meetingUrl"), "https://zoom.us/j/1?pwd=x");
        runTimers();
        await flush();
        assert.strictEqual(mount.el("applyMeeting").disabled, true, "缺元信息阻断应用");
        assert.strictEqual(mount.api.requests.filter((r) => /preview/.test(r.url)).length, 0);
        mount.controller.dispose();
    });

    it("只改 Zoom 链接不重取目录", async () => {
        const mount = mountComponent();
        openComponent(mount);
        await flush();
        await fillMeetingFields(mount);
        const before = mount.api.requests.filter((r) => /time-zones/.test(r.url)).length;
        inputInto(mount.el("meetingUrl"), "https://zoom.us/j/2?pwd=y");
        runTimers();
        await flush();
        assert.strictEqual(mount.api.requests.filter((r) => /time-zones/.test(r.url)).length, before,
            "Zoom 链接变化不取目录");
        assert.ok(mount.api.requests.filter((r) => /preview/.test(r.url)).length >= 2, "仍重做预览校验");
        mount.controller.dispose();
    });

    it("关闭使在途目录失效；重开不复用上一目标状态（F-4）", async () => {
        const pending = [];
        const mount = mountComponent({
            api: {
                zones: (url) => (/startLocal=/.test(url)
                    ? new Promise((resolve) => pending.push(resolve))
                    : UNIT_ZONES)
            }
        });
        openComponent(mount);
        await flush();
        inputInto(mount.el("meetingDate"), "2026-09-11");
        inputInto(mount.el("meetingStart"), "10:00");
        inputInto(mount.el("meetingEndDate"), "2026-09-11");
        inputInto(mount.el("meetingEnd"), "10:30");
        runTimers();
        await flush();
        assert.strictEqual(pending.length, 1, "会议目录请求在途");
        mount.controller.close({});
        pending[0](UNIT_MEETING_ZONES);
        await flush();
        runTimers();
        await flush();
        assert.strictEqual(mount.el("meetingDialog").hasAttribute("open"), false);
        assert.strictEqual(mount.el("meetingCountrySummary").textContent.includes("土耳其（UTC+3）"), false,
            "关闭后旧目录响应不得落地");
        assert.strictEqual(mount.el("meetingZoneSearch").value, "");
        assert.strictEqual(mount.el("applyMeeting").disabled, true);
        // 重开（另一目标）：不复用上一人分组/选择
        openComponentWith(mount, null);
        await flush();
        assert.strictEqual(mount.el("meetingCountry").value, "TR");
        assert.strictEqual(mount.el("meetingCountrySummary").textContent,
            "填写完整会议日期和时间后显示时区。");
        assert.strictEqual(mount.el("meetingZoneSearch").value, "");
        mount.controller.dispose();
    });
});

describe("fast-p 04 组件：apply 契约（onApply 参数/返回值）", () => {
    it("apply 携带 {preview, mode, capturedEditorRevision}；true 关闭、false 显示目标变化错误", async () => {
        const calls = [];
        const mount = mountComponent({
            onApply: (targetKey, payload) => {
                calls.push({ targetKey, payload });
                return payload.mode === "replace";
            }
        });
        openComponent(mount);
        await flush();
        await fillMeetingFields(mount);
        const dialog = mount.doc.getElementById("meetingDialog");
        clickEl(mount.el("applyMeeting"));
        await flush();
        assert.strictEqual(calls.length, 1);
        assert.strictEqual(calls[0].targetKey, "1:101:acc1");
        assert.strictEqual(calls[0].payload.mode, "append");
        assert.ok(calls[0].payload.preview && calls[0].payload.preview.htmlBody);
        assert.strictEqual(calls[0].payload.capturedEditorRevision, 0);
        // false → 错误提示、弹窗不关
        assert.match(mount.el("meetingError").textContent, /回复目标已变化，请重新打开会议确认/);
        assert.strictEqual(dialog.hasAttribute("open"), true);
        // replace 模式 true → 关闭
        const mode = mount.el("insertMode");
        if (!mode.hidden) {
            mode.value = "replace";
            eventOn(mode, "change");
        }
        clickEl(mount.el("applyMeeting"));
        await flush();
        assert.strictEqual(calls.length, 2);
        assert.strictEqual(calls[1].payload.mode, "replace");
        assert.strictEqual(dialog.hasAttribute("open"), false);
        mount.controller.dispose();
    });
});

describe("会议国家搜索", () => {
    after(() => restoreGlobalDom());

    it("中文、英文和代码过滤；搜索不修改已选国家，鼠标选择才更新时区", async () => {
        const mount = mountComponent();
        openComponent(mount);
        await flush();
        await fillMeetingFields(mount);
        const search = mount.el("meetingCountrySearch");
        assert.ok(search, "真实模板提供国家搜索框");
        assert.strictEqual(search.value, "土耳其");
        const list = mount.el("meetingCountryOptions");
        for (const query of ["巴西", "bRaZiL", " br "]) {
            inputInto(search, query);
            const buttons = list.querySelectorAll('button[role="option"]');
            assert.strictEqual(buttons.length, 1);
            assert.strictEqual(buttons[0].getAttribute("data-country"), "BR");
            assert.strictEqual(mount.el("meetingCountry").value, "TR");
            assert.strictEqual(mount.el("meetingZoneSearch").value, "土耳其（UTC+3）");
        }
        eventOn(list.querySelector('button[role="option"]'), "mousedown");
        assert.strictEqual(search.value, "巴西");
        assert.strictEqual(list.hidden, true);
        assert.strictEqual(mount.el("meetingCountry").value, "BR");
        assert.strictEqual(mount.el("meetingZoneSearch").value, "");
        assert.strictEqual(mount.el("applyMeeting").disabled, true);
        assert.strictEqual(mount.el("meetingZoneField").hidden, false);
        mount.controller.dispose();
    });

    it("键盘选择、空结果 Enter、Esc/失焦恢复、清空搜索和重开", async () => {
        const mount = mountComponent();
        openComponent(mount);
        await flush();
        const search = mount.el("meetingCountrySearch");
        assert.ok(search);
        const list = mount.el("meetingCountryOptions");
        inputInto(search, "ind");
        eventOn(search, "keydown", "ArrowDown");
        assert.ok(search.getAttribute("aria-activedescendant"));
        eventOn(search, "keydown", "Enter");
        assert.strictEqual(search.value, "印度");
        assert.strictEqual(mount.el("meetingCountry").value, "IN");
        inputInto(search, "不存在的国家");
        assert.ok(list.textContent.includes("没有匹配的国家/地区"));
        const enter = new MiniEvent("keydown", { bubbles: true });
        enter.key = "Enter";
        let prevented = false;
        enter.preventDefault = () => { prevented = true; };
        search.dispatchEvent(enter);
        assert.strictEqual(prevented, true);
        assert.strictEqual(mount.el("meetingCountry").value, "IN");
        eventOn(search, "keydown", "Escape");
        assert.strictEqual(search.value, "印度");
        assert.strictEqual(mount.el("meetingDialog").hasAttribute("open"), true);
        inputInto(search, "China");
        eventOn(search, "blur");
        assert.strictEqual(search.value, "印度");
        inputInto(search, "");
        assert.strictEqual(list.querySelectorAll('button[role="option"]').length, 7);
        eventOn(search, "keydown", "Tab");
        assert.strictEqual(list.hidden, true);
        assert.strictEqual(search.value, "印度");
        inputInto(search, "Brazil");
        clickEl(mount.el("cancelMeeting"));
        openComponent(mount);
        await flush();
        assert.strictEqual(search.value, "土耳其");
        assert.strictEqual(list.hidden, true);
        assert.strictEqual(search.getAttribute("aria-expanded"), "false");
        mount.controller.dispose();
    });
});
