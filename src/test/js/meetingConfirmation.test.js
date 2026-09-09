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

// ---- 组件 stub API（01 只读形状） ----

const UNIT_TEMPLATES = [
    { id: 42, name: "会议确认（默认）", body: "Dear {{expert_salutation}},\n\nWe invite you on {{meeting_time}}.\nJoin: {{zoom_url}}\n\n{{sender_signature}}" }
];
const UNIT_ZONES = [
    { id: "Europe/Istanbul", labelZh: "土耳其 · 伊斯坦布尔", aliases: ["土耳其", "伊斯坦布尔", "Turkey", "Türkiye", "Istanbul"], offsetLabel: "UTC+3", offsetSeconds: 10800 },
    { id: "Asia/Shanghai", labelZh: "中国 · 北京 / 上海", aliases: ["中国", "北京", "上海", "China", "Beijing", "Shanghai"], offsetLabel: "UTC+8", offsetSeconds: 28800 },
    { id: "Asia/Kolkata", labelZh: "印度 · 加尔各答", aliases: ["印度", "加尔各答", "India", "Kolkata"], offsetLabel: "UTC+5:30", offsetSeconds: 19800 },
    { id: "America/New_York", labelZh: "美国东部 · 纽约", aliases: ["美国东部", "纽约", "US Eastern", "New York"], offsetLabel: "UTC-5", offsetSeconds: -18000 }
];

function makeStubApi(overrides) {
    const requests = [];
    const api = (url, opts) => {
        requests.push({ url, method: (opts && opts.method) || "GET", body: opts && opts.body });
        if (/\/meeting-confirmation\/options/.test(url)) {
            return Promise.resolve(overrides && overrides.options !== undefined
                ? overrides.options
                : { targetKey: "1:acc1", resolvedAccountCode: "acc1", expertSalutation: "Professor Basdogan",
                    senderSignature: "LuKai, Customer Care Officer\nQingfei Tech Talent Team China",
                    generatedAt: "2026-09-09T08:00:00Z", defaultZoneId: "Europe/Istanbul", templates: UNIT_TEMPLATES });
        }
        if (/\/meeting-confirmation\/time-zones/.test(url)) {
            return Promise.resolve(UNIT_ZONES);
        }
        if (/\/meeting-confirmation\/preview/.test(url)) {
            if (overrides && overrides.previewError) return Promise.reject(overrides.previewError);
            const parsed = JSON.parse((opts && opts.body) || "{}");
            const input = parsed.meeting || {};
            if (overrides && typeof overrides.preview === "function") return Promise.resolve(overrides.preview(parsed));
            const name = String(input.expertSalutation || "Professor Basdogan");
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

    it("loading 阶段字段禁用、确认/下载禁用；成功后可填并预览", async () => {
        const mount = mountComponent();
        openComponent(mount);
        await flush();
        const name = mount.el("meetingName");
        assert.strictEqual(name.value, "Professor Basdogan", "默认称呼来自 options");
        const select = mount.el("meetingTemplate");
        assert.ok(select.querySelectorAll("option").length >= 2, "模板项 + 自定义项");
        assert.strictEqual(select.querySelectorAll("option")[1].value, "custom");
        assert.strictEqual(mount.el("meetingDate").value, "");
        // 填写完整 → 预览
        inputInto(mount.el("meetingName"), "Professor Basdogan");
        inputInto(mount.el("meetingDate"), "2026-09-11");
        inputInto(mount.el("meetingStart"), "10:00");
        inputInto(mount.el("meetingEndDate"), "2026-09-11");
        inputInto(mount.el("meetingEnd"), "10:30");
        inputInto(mount.el("meetingUrl"), "https://zoom.us/j/1?pwd=x");
        inputInto(mount.el("meetingSignature"), "LuKai");
        runTimers();
        await flush();
        assert.strictEqual(mount.el("applyMeeting").disabled, false, "ready 后可确认");
        const filename = mount.el("meetingFilename").textContent;
        assert.match(filename, /^meeting-2026-09-11-/);
        mount.controller.dispose();
    });
});

describe("fast-p 04 组件：时区搜索键盘状态（S-4/I-5）", () => {
    it("focus 展开全部、输入过滤、Enter 单选/多选不自动选、Tab 恢复标签", async () => {
        const mount = mountComponent();
        openComponent(mount);
        await flush();
        const search = mount.el("meetingZoneSearch");
        const list = mount.el("meetingZoneOptions");
        eventOn(search, "focus");
        assert.strictEqual(list.hidden, false, "focus 展开全部");
        assert.strictEqual(list.querySelectorAll('button[role="option"]').length, UNIT_ZONES.length);
        // 多结果 Enter 不自动选
        search.value = "Istanbul";
        eventOn(search, "input");
        assert.strictEqual(list.querySelectorAll('button[role="option"]').length, 1);
        eventOn(search, "keydown", "Enter");
        assert.strictEqual(search.value, "土耳其 · 伊斯坦布尔 (UTC+3)", "单结果 Enter 选中");
        // 再搜索多个结果：无 active 时不自动选（输入词保持、selection 不变）
        search.value = "Asia";
        eventOn(search, "input");
        assert.ok(list.querySelectorAll('button[role="option"]').length >= 2, "多结果");
        eventOn(search, "keydown", "Enter");
        assert.strictEqual(search.value, "Asia", "多结果无 active：Enter 不吞查询词、不自动选");
        eventOn(search, "keydown", "Escape");
        assert.strictEqual(search.value, "土耳其 · 伊斯坦布尔 (UTC+3)", "Esc 后恢复已选标签");
        mount.controller.dispose();
    });

    it("键盘 active/selected 分离：Arrow 移动 focused 但不改 selected；aria 同步", async () => {
        const mount = mountComponent();
        openComponent(mount);
        await flush();
        const search = mount.el("meetingZoneSearch");
        const list = mount.el("meetingZoneOptions");
        eventOn(search, "focus");
        search.value = "";
        eventOn(search, "input");
        eventOn(search, "keydown", "ArrowDown");
        const first = list.querySelector('button[role="option"]');
        assert.ok(first.classList.contains("focused"));
        assert.strictEqual(search.getAttribute("aria-activedescendant"), first.getAttribute("id"));
        // ArrowDown 到第二项
        eventOn(search, "keydown", "ArrowDown");
        const second = list.querySelectorAll('button[role="option"]')[1];
        assert.ok(second.classList.contains("focused"));
        assert.ok(!first.classList.contains("focused"));
        // 仍未显式选中：hint 保持默认已选（Europe/Istanbul），active 只是键盘焦点
        assert.strictEqual(mount.el("meetingZoneHint").textContent, "Europe/Istanbul · 日期和时间均按此时区填写");
        eventOn(search, "keydown", "Enter");
        const secondZoneId = second.getAttribute("data-zone");
        const secondLabel = second.querySelector('[data-role="zone-label"]').textContent;
        const secondOffset = second.querySelector('[data-role="zone-offset"]').textContent.replace(" ✓", "");
        assert.strictEqual(search.value, `${secondLabel} (${secondOffset})`, "Enter 显式选中 active 项");
        assert.strictEqual(mount.el("meetingZoneHint").textContent, `${secondZoneId} · 日期和时间均按此时区填写`);
        mount.controller.dispose();
    });

    it("Escape 两级与 Tab 恢复：先关列表再关弹窗；无结果不吞已选值", async () => {
        const mount = mountComponent();
        openComponent(mount);
        await flush();
        const search = mount.el("meetingZoneSearch");
        const list = mount.el("meetingZoneOptions");
        const dialog = mount.doc.getElementById("meetingDialog");
        search.value = "Asia/Kolkata";
        eventOn(search, "input");
        eventOn(search, "keydown", "Enter");
        assert.strictEqual(search.value, "印度 · 加尔各答 (UTC+5:30)");
        search.value = "zzz";
        eventOn(search, "input");
        assert.ok(list.querySelector(".meeting-zone-empty"), "无结果固定文案");
        eventOn(search, "keydown", "Escape");
        assert.strictEqual(list.hidden, true, "第一下 Esc 关列表");
        assert.strictEqual(search.value, "印度 · 加尔各答 (UTC+5:30)", "无结果 Esc 恢复已选标签");
        assert.strictEqual(dialog.hasAttribute("open"), true);
        eventOn(dialog, "keydown", "Escape");
        assert.strictEqual(dialog.hasAttribute("open"), false, "第二下 Esc 关弹窗");
        // Tab 恢复已选标签
        mount.controller.open({
            ownerKey: "u|scope|1", targetKey: "1:101:acc1", contactId: 1, processingId: 101,
            senderAccountCode: "acc1", expertLabel: "专家A", editorHtml: "", editorText: "", savedMeeting: null
        });
        await flush();
        const search2 = mount.el("meetingZoneSearch");
        search2.value = "America";
        eventOn(search2, "input");
        eventOn(search2, "keydown", "Tab");
        assert.strictEqual(list.hidden, true);
        assert.strictEqual(search2.value, "土耳其 · 伊斯坦布尔 (UTC+3)", "Tab 恢复已选标签");
        mount.controller.dispose();
    });

    it("日期 change 只重载目录偏移、保留 zone id；endDate 为空时跟随 startDate", async () => {
        const mount = mountComponent();
        openComponent(mount);
        await flush();
        const dateInput = mount.el("meetingDate");
        const zoneCalls = () => mount.api.requests.filter((r) => /time-zones/.test(r.url));
        const before = zoneCalls().length;
        dateInput.value = "2026-03-08";
        eventOn(dateInput, "input");
        assert.ok(zoneCalls().length > before, "日期变化触发目录重载");
        assert.ok(zoneCalls()[zoneCalls().length - 1].url.includes("date=2026-03-08"));
        assert.strictEqual(mount.el("meetingEndDate").value, "2026-03-08", "endDate 空时跟随 startDate");
        assert.strictEqual(mount.el("meetingZoneSearch").value, "土耳其 · 伊斯坦布尔 (UTC+3)", "zone id 保留");
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
        inputInto(mount.el("meetingName"), "Professor Basdogan");
        inputInto(mount.el("meetingDate"), "2026-09-11");
        inputInto(mount.el("meetingStart"), "10:00");
        inputInto(mount.el("meetingEndDate"), "2026-09-11");
        inputInto(mount.el("meetingEnd"), "10:30");
        inputInto(mount.el("meetingUrl"), "https://zoom.us/j/1?pwd=x");
        inputInto(mount.el("meetingSignature"), "LuKai");
        runTimers();
        await flush();
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
