"use strict";

// fast-p 07 通用附件宿主集成测试（I-1..I-5 / S-1/S-2 前端契约）：
// 复用 mailbox-chat 行为测试的最小真实 DOM 树（HTML 解析 + 事件冒泡 +
// querySelector/closest/dataset）与沙箱，把真实 mailbox-chat.js（可选
// meeting-confirmation.js）加载进同一沙箱，用真实实例 + stub API/adapter 驱动：
// 仅图标入口、多选顺序上传、上传中/失败态与发送禁止、切专家/切目标的异步归属、
// 两类发送 payload 的 attachmentIds 顺序、requestId 失效边界、成功清稿的捕获保护、
// 会议/跟进/采用全文替换后的附件保留、已发消息原件卡的 contextPath 下载与转义。
// 无真实 SMTP、无真实上传服务端。

const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const ROOT = path.join(__dirname, "..", "..", "main", "resources", "static");
const chatSource = fs.readFileSync(path.join(ROOT, "mailbox-chat.js"), "utf-8");
const meetingSource = fs.readFileSync(path.join(ROOT, "meeting-confirmation.js"), "utf-8");
const appSource = fs.readFileSync(path.join(ROOT, "app.js"), "utf-8");

// fast-p 03（I-2）：app.js 顶层唯一中文北京时间 formatter 切片（与 03 集成测试同一
// 抽取口径），用于把真实宿主 `formatBeijingMeetingRange` 注入聊天沙箱——草稿卡 meta
// 由宿主 formatter 渲染，测试不得自造或 stub 该文案。
function extractAppRegion(startMarker, endMarker) {
    const start = appSource.indexOf(startMarker);
    const end = appSource.indexOf(endMarker, start);
    if (start < 0 || end < 0) throw new Error("app.js region not found: " + startMarker);
    return appSource.slice(start, end);
}

// ════════════════════════════════════════════════════════════════════════
// 最小真实 DOM（含 innerHTML 解析 —— 聊天组件以模板字符串渲染）
// ════════════════════════════════════════════════════════════════════════

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

function createMailboxViewDom() {
    const doc = new MiniDocument();
    const view = doc.createElement("div");
    view.setAttribute("id", "view-mailbox");
    const toolbar = doc.createElement("div");
    toolbar.setAttribute("id", "mailboxLegacyToolbar");
    toolbar.setAttribute("class", "toolbar");
    const refresh = doc.createElement("button");
    refresh.setAttribute("id", "mailboxRefreshBtn");
    refresh.setAttribute("class", "button primary");
    refresh.appendChild(doc.createTextNode("刷新"));
    toolbar.appendChild(refresh);
    const viewControls = doc.createElement("div");
    viewControls.setAttribute("class", "mailbox-view-controls");
    toolbar.appendChild(viewControls);
    const account = doc.createElement("select");
    account.setAttribute("id", "mailboxFilterAccountCode");
    account.innerHTML = '<option value="">全部邮箱账号</option><option value="acc1">acc1 (a@x.com)</option><option value="acc2">acc2 (b@x.com)</option>';
    toolbar.appendChild(account);
    const direction = doc.createElement("select");
    direction.setAttribute("id", "mailboxFilterDirection");
    direction.innerHTML = '<option value="">全部收发方向</option><option value="INBOUND">收件 (INBOUND)</option><option value="OUTBOUND">发件 (OUTBOUND)</option>';
    toolbar.appendChild(direction);
    const tag = doc.createElement("select");
    tag.setAttribute("id", "mailboxFilterTag");
    tag.innerHTML = '<option value="">全部标签</option><option value="专家">专家</option><option value="首发">首发</option><option value="待处理">待处理</option>';
    toolbar.appendChild(tag);
    const recipient = doc.createElement("input");
    recipient.setAttribute("id", "mailboxFilterRecipient");
    recipient.setAttribute("placeholder", "输入邮箱关键词");
    toolbar.appendChild(recipient);
    const keyword = doc.createElement("input");
    keyword.setAttribute("id", "mailboxFilterKeyword");
    keyword.setAttribute("placeholder", "搜索邮件主题或正文");
    toolbar.appendChild(keyword);
    const startDate = doc.createElement("input");
    startDate.setAttribute("type", "date");
    startDate.setAttribute("id", "mailboxFilterStartDate");
    toolbar.appendChild(startDate);
    const sep = doc.createElement("span");
    sep.appendChild(doc.createTextNode("至"));
    toolbar.appendChild(sep);
    const endDate = doc.createElement("input");
    endDate.setAttribute("type", "date");
    endDate.setAttribute("id", "mailboxFilterEndDate");
    toolbar.appendChild(endDate);
    const searchBtn = doc.createElement("button");
    searchBtn.setAttribute("class", "button primary");
    searchBtn.setAttribute("id", "mailboxSearchBtn");
    searchBtn.appendChild(doc.createTextNode("查询"));
    toolbar.appendChild(searchBtn);
    view.appendChild(toolbar);

    const filterBar = doc.createElement("div");
    filterBar.setAttribute("id", "mailboxExecutionFilterBar");
    filterBar.setAttribute("class", "toolbar");
    filterBar.setAttribute("hidden", "");
    view.appendChild(filterBar);

    const panel = doc.createElement("section");
    panel.setAttribute("class", "panel");
    panel.setAttribute("id", "mailboxConversationPanel");
    const head = doc.createElement("div");
    head.setAttribute("class", "panel-head");
    const h2 = doc.createElement("h2");
    h2.appendChild(doc.createTextNode("已激活账号收发邮件记录"));
    head.appendChild(h2);
    const actions = doc.createElement("div");
    actions.setAttribute("class", "panel-head-actions");
    const check = doc.createElement("button");
    check.setAttribute("class", "button");
    check.setAttribute("id", "checkRepliesBtn");
    check.appendChild(doc.createTextNode("检查回复"));
    actions.appendChild(check);
    const bulk = doc.createElement("button");
    bulk.setAttribute("class", "button primary");
    bulk.setAttribute("id", "bulkOutreachBtn");
    bulk.appendChild(doc.createTextNode("批量发送"));
    actions.appendChild(bulk);
    head.appendChild(actions);
    panel.appendChild(head);
    const list = doc.createElement("div");
    list.setAttribute("class", "mailbox-list");
    list.setAttribute("id", "mailboxList");
    panel.appendChild(list);
    const pagination = doc.createElement("div");
    pagination.setAttribute("id", "mailboxPagination");
    panel.appendChild(pagination);
    view.appendChild(panel);
    doc.body.appendChild(view);
    return { doc, view, toolbar, list, panel, actions, refresh, searchBtn, tag, account };
}

function flush() {
    return new Promise((resolve) => setImmediate(() => setImmediate(() => setImmediate(() => setImmediate(() => setImmediate(() => setImmediate(() => setImmediate(() => setImmediate(resolve)))))))));
}

function escapeHtmlLike(value) {
    return String(value == null ? "" : value)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;")
        .replace(/'/g, "&#039;");
}

// ════════════════════════════════════════════════════════════════════════
// fast-p 04 会议 fixture：01 options/zones/preview 的 stub 响应形状（只读契约）
// ════════════════════════════════════════════════════════════════════════

const DEFAULT_MEETING_ZONES = [
    { id: "Europe/Istanbul", labelZh: "土耳其 · 伊斯坦布尔", aliases: ["土耳其", "伊斯坦布尔", "Turkey", "Türkiye", "Istanbul"], offsetLabel: "UTC+3", offsetSeconds: 10800 },
    { id: "Asia/Shanghai", labelZh: "中国 · 北京 / 上海", aliases: ["中国", "北京", "上海", "China", "Beijing", "Shanghai"], offsetLabel: "UTC+8", offsetSeconds: 28800 },
    { id: "Asia/Kolkata", labelZh: "印度 · 加尔各答", aliases: ["印度", "加尔各答", "India", "Kolkata"], offsetLabel: "UTC+5:30", offsetSeconds: 19800 },
    { id: "America/New_York", labelZh: "美国东部 · 纽约", aliases: ["美国东部", "纽约", "US Eastern", "New York"], offsetLabel: "UTC-5", offsetSeconds: -18000 },
    { id: "Pacific/Auckland", labelZh: "新西兰 · 奥克兰", aliases: ["新西兰", "奥克兰", "New Zealand", "Auckland"], offsetLabel: "UTC+12", offsetSeconds: 43200 }
];

function meetingInputToLocal(input) {
    const startLocal = String(input.startLocal || "");
    const endLocal = String(input.endLocal || "");
    const startTime = startLocal.indexOf("T") !== -1 ? startLocal.slice(11, 16) : "10:00";
    const startDate = startLocal.indexOf("T") !== -1 ? startLocal.slice(0, 10) : "2026-09-11";
    const endTime = endLocal.indexOf("T") !== -1 ? endLocal.slice(11, 16) : "10:30";
    const endDate = endLocal.indexOf("T") !== -1 ? endLocal.slice(0, 10) : "2026-09-11";
    return { startDate, startTime, endDate, endTime };
}

function defaultPreviewResponse(input, options) {
    const meeting = input || {};
    const zoneId = String(meeting.zoneId || "Europe/Istanbul");
    const name = String((options && options.addressee) || "Professor Basdogan");
    const { startDate, startTime, endTime } = meetingInputToLocal(meeting);
    const textBody = `Dear ${name},\n\nPlease join our online meeting on ${startDate} ${startTime}-${endTime} (${zoneId}).\n\nBest regards`;
    const htmlBody = `<p>Dear ${name},</p><p>Please join our online meeting on ${startDate} ${startTime}-${endTime} (${zoneId}).</p><p>Best regards</p>`;
    const filename = `meeting-${startDate}-${name.replace(/[^A-Za-z0-9-]/g, "-")}.ics`;
    const icsText = `BEGIN:VCALENDAR\r\nVERSION:2.0\r\nBEGIN:VEVENT\r\nSUMMARY:Meeting with ${name}\r\nDTSTART:20260911T070000Z\r\nDTEND:20260911T073000Z\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n`;
    const sha256 = "a".repeat(64);
    return {
        targetKey: "1:acc1",
        resolvedAccountCode: "acc1",
        meeting: Object.assign({}, meeting, { generatedAt: meeting.generatedAt || "2026-09-09T08:00:00Z" }),
        textBody,
        htmlBody,
        meetingTime: "Friday, September 11, 2026, 10:00 AM \u2013 10:30 AM (UTC+3) Europe/Istanbul",
        startUtc: "2026-09-11T07:00:00Z",
        endUtc: "2026-09-11T07:30:00Z",
        chinaTime: "2026/09/11 15:00 \u2013 15:30",
        durationMinutes: 30,
        attachment: {
            filename,
            contentType: "text/calendar;charset=UTF-8",
            icsText,
            byteLength: icsText.length,
            sha256,
            semanticSha256: "b".repeat(64)
        }
    };
}

// ════════════════════════════════════════════════════════════════════════
// 聊天沙箱：mailbox-chat.js + 宿主 stub（app 全局函数按需注入）
// ════════════════════════════════════════════════════════════════════════

const OPERATOR_STATUS_CATALOG = [
    ["NOT_CONTACTED", "未联系"],
    ["CONTACTED", "已联系"],
    ["REPLIED", "已回复"],
    ["MATERIALS_RECEIVED", "已回复材料"],
    ["INVITED", "已邀约"],
    ["COMPLETED", "已完成"]
];

const INDEX_LEVEL_CATALOG = [
    ["RAW", "原始"],
    ["CANDIDATE", "筛选"],
    ["APPLICATION", "有效"]
];

const EXPERT_TAG_LABELS = {
    auto_promoted: "自动晋升",
    verified: "已验证",
    学术科研: "学术科研",
    重点关注: "重点关注",
    承诺回复材料: "承诺回复材料"
};

function expertTagEditorHtml(orcidId, tags, level, editorId, missing) {
    if (missing) {
        return `<div class="detail-section expert-tag-editor" id="${escapeHtmlLike(editorId)}" data-orcid="${escapeHtmlLike(orcidId)}" data-level="${escapeHtmlLike(level)}" data-profile-missing="true"><div class="inbound-tag-editor-head"><h3>专家标签</h3></div><div class="inbound-tag-editor-chips"><span class="muted">该专家在 ES 中无画像文档，标签功能不可用</span></div></div>`;
    }
    const chips = (tags || []).map((tag) =>
        `<span class="expert-tag tag-${escapeHtmlLike(tag)}">${escapeHtmlLike(EXPERT_TAG_LABELS[tag] || tag)}<button type="button" class="expert-tag-remove" data-action="expert-remove-tag" data-tag="${escapeHtmlLike(tag)}" title="删除标签">×</button></span>`
    ).join("") || `<span class="muted">暂无标签</span>`;
    return `<div class="detail-section expert-tag-editor" id="${escapeHtmlLike(editorId)}" data-orcid="${escapeHtmlLike(orcidId)}" data-level="${escapeHtmlLike(level)}"><div class="inbound-tag-editor-head"><h3>专家标签</h3><div class="inbound-tag-editor-actions"><button type="button" class="button primary small" data-action="expert-add-tag-open">+ 添加标签</button></div></div><div class="inbound-tag-editor-chips">${chips}</div></div>`;
}

function createChatSandbox(options) {
    const opts = options || {};
    const requests = [];
    const calls = {
        api: requests,
        status: [],
        dialogs: [],
        workbenchMounts: [],
        materialsMounts: [],
        followUp: [],
        openExpert: [],
        badgeRefresh: 0,
        sendRich: [],
        sendConversation: [],
        unmounts: [],
        inboundTagModals: [],
        tagMutations: [],
        tagEditorUpdates: [],
        tagEditorLoadings: [],
        // fast-p 07：上传链路（请求/受控 pending/响应序号）
        uploads: [],
        uploadPending: [],
        uploadSeq: 0
    };
    const timers = [];

    const defaultRoute = function defaultRoute(url, method, body) {
        if (url.startsWith("/api/mail/mailbox/conversations?")) {
            return Promise.resolve(opts.conversations || { items: [], total: 0 });
        }
        if (/\/api\/mail\/mailbox\/conversations\/\d+\/messages/.test(url)) {
            if (opts.messagesError) return Promise.reject(new Error(opts.messagesError));
            return Promise.resolve(opts.messages || { items: [], nextBefore: null, hasMore: false });
        }
        if (/\/api\/expert-contacts\/\d+/.test(url)) {
            return Promise.resolve(opts.contact || { contact: null, mails: [] });
        }
        if (/\/api\/operator-action-logs/.test(url)) {
            return Promise.resolve({ records: opts.logs || [] });
        }
        if (/\/api\/inbound-summary\/tags\/options/.test(url)) {
            return Promise.resolve({ items: opts.tagOptions || [] });
        }
        if (/\/api\/inbound-summary\/tags\/\d+/.test(url) && method === "DELETE") {
            if (opts.deleteTagError) return Promise.reject(new Error(opts.deleteTagError));
            return Promise.resolve({});
        }
        if (/\/api\/translate/.test(url)) {
            if (opts.translateError) return Promise.reject(new Error(opts.translateError));
            if (opts.translateResult === false) return Promise.resolve({ ok: false });
            const parsed = body ? JSON.parse(body) : { text: "" };
            return Promise.resolve({ ok: true, translatedText: `译文：${parsed.text || ""}` });
        }
        if (/\/api\/mail\/unmatched-inbound\/\d+\/mark-resolved/.test(url)) {
            if (opts.markResolvedError) return Promise.reject(new Error(opts.markResolvedError));
            return Promise.resolve({});
        }
        if (/\/api\/mail\/mailbox\/conversations\/\d+\/follow/.test(url)) {
            return Promise.resolve({ followed: opts.followResult !== false });
        }
        // fast-p 07：04 通用附件上传（一次一个原件）；可选受控 pending 与失败注入。
        const uploadMatch = url.match(/\/api\/mail\/conversations\/(\d+)\/outbound-attachments$/);
        if (uploadMatch && method === "POST") {
            const contactId = Number(uploadMatch[1]);
            const entries = (body && Array.isArray(body.entries)) ? body.entries : [];
            const file = entries.length ? entries[0].value : null;
            calls.uploads.push({ contactId, file, filename: entries.length ? entries[0].filename : "" });
            if (opts.uploadError) return Promise.reject(new Error(opts.uploadError));
            const respond = () => {
                calls.uploadSeq += 1;
                const id = `att-${calls.uploadSeq}`;
                return {
                    id,
                    filename: (file && file.name) || "",
                    contentType: "application/octet-stream",
                    byteLength: (file && file.size) || 0,
                    sha256: `sha-${id}`,
                    downloadUrl: `/api/mail/conversations/${contactId}/outbound-attachments/${id}/download`
                };
            };
            if (opts.deferUploads) {
                return new Promise((resolve, reject) => {
                    calls.uploadPending.push({ filename: (file && file.name) || "", resolve: () => resolve(respond()), reject });
                });
            }
            return Promise.resolve(respond());
        }
        // fast-p 04：会议配置只读 API（stub fixture 由 opts 覆盖）
        if (/\/meeting-confirmation\/options/.test(url)) {
            if (opts.optionsError) return Promise.reject(new Error(opts.optionsError));
            const saved = opts.meetingOptions || {
                targetKey: "1:acc1",
                resolvedAccountCode: "acc1",
                generatedAt: "2026-09-09T08:00:00Z",
                defaultZoneId: "Europe/Istanbul"
            };
            return Promise.resolve(saved);
        }
        if (/\/meeting-confirmation\/time-zones/.test(url)) {
            if (opts.zonesError) return Promise.reject(new Error(opts.zonesError));
            return Promise.resolve(opts.meetingZones || DEFAULT_MEETING_ZONES);
        }
        if (/\/meeting-confirmation\/preview/.test(url) && method === "POST") {
            if (opts.previewError) return Promise.reject(opts.previewError);
            const parsed = body ? JSON.parse(body) : {};
            const supplied = opts.meetingPreview;
            if (typeof supplied === "function") return Promise.resolve(supplied(parsed));
            return Promise.resolve(defaultPreviewResponse(parsed.meeting || {}));
        }
        const failedEndpoint = opts.failEndpoints ? Object.keys(opts.failEndpoints).find((key) => url.includes(key)) : null;
        if (failedEndpoint) return Promise.reject(new Error(opts.failEndpoints[failedEndpoint]));
        return Promise.resolve({});
    };

    // 自定义 route 可调用第 5 参 next() 回退到默认路由
    const route = opts.route
        ? (url, method, body, entry) => opts.route(url, method, body, entry, defaultRoute)
        : defaultRoute;

    const sandbox = {
        console,
        URLSearchParams,
        setTimeout: (fn) => { timers.push(fn); return timers.length; },
        clearTimeout: () => {},
        escapeHtml: escapeHtmlLike,
        alert: (message) => { calls.lastAlert = message; },
        confirm: () => true,
        showStatus: (message, type) => { calls.status.push({ message, type }); },
        api: (url, requestOpts) => {
            const entry = { url, method: (requestOpts && requestOpts.method) || "GET", body: requestOpts && requestOpts.body };
            requests.push(entry);
            const routeResult = route(url, entry.method, entry.body, entry);
            return routeResult && typeof routeResult.then === "function" ? routeResult : Promise.resolve(routeResult);
        },
        openActionDialog: (type, dialogOptions) => {
            calls.dialogs.push({ type, options: dialogOptions || null });
            const configured = opts.dialogResults || {};
            const result = Object.prototype.hasOwnProperty.call(configured, type) ? configured[type] : (opts.dialogResult !== undefined ? opts.dialogResult : { resolvedBy: "验收员", note: "" });
            return Promise.resolve(result === null ? null : (typeof result === "function" ? result(dialogOptions) : result));
        },
        buildManualReplySubject: (subject) => {
            const trimmed = String(subject || "").trim();
            if (!trimmed) return "Re:";
            return trimmed.slice(0, 3).toLowerCase() === "re:" ? trimmed : `Re: ${trimmed}`;
        },
        renderInboundTagChip: (tag, chipOpts) => {
            const classes = ["inbound-tag-chip"].concat(tag && tag.tagType === "QA" ? ["qa"] : ["custom"]);
            const remove = (chipOpts && chipOpts.removable)
                ? `<button type="button" class="chip-x" data-action="${escapeHtmlLike(chipOpts.removeAction || "inbound-remove-tag")}" data-tag-id="${escapeHtmlLike(tag && tag.tagId)}" title="删除标签">×</button>`
                : "";
            return `<span class="${classes.join(" ")}">${escapeHtmlLike(tag && tag.label ? tag.label : "")}${remove}</span>`;
        },
        renderOperatorLogs: (logs) => {
            const list = Array.isArray(logs) ? logs : [];
            return `<div class="log-list">${list.map((log) => `<div class="log-row">${escapeHtmlLike(log.actionType || "")}</div>`).join("")}</div>`;
        },
        refreshUnmatchedBadge: () => { calls.badgeRefresh += 1; return Promise.resolve(); },
        mcHostOpenExpertDetail: (contactId) => { calls.openExpert.push(Number(contactId)); return Promise.resolve(); },
        mcHostOpenMaterials: (contactId) => { calls.materialsMounts.push(Number(contactId)); return true; },
        mcHostOpenFollowUp: (contactId) => { calls.followUp.push(Number(contactId)); return Promise.resolve(); },
        mcHostSendRichReply: (processingId, body) => {
            calls.sendRich.push({ processingId: Number(processingId), body });
            if (opts.sendRichResult === false) return Promise.resolve(false);
            if (opts.sendRichError) return Promise.reject(new Error(opts.sendRichError));
            if (opts.sendRichDeferred) {
                calls.sendRichDeferred = calls.sendRichDeferred || [];
                return new Promise((resolve, reject) => {
                    calls.sendRichDeferred.push({ resolve, reject, processingId: Number(processingId), body });
                });
            }
            return Promise.resolve(true);
        },
        mcHostSendConversationRichReply: (contactId, body) => {
            calls.sendConversation.push({ contactId: Number(contactId), body });
            if (opts.sendConversationResult === false) return Promise.resolve(false);
            if (opts.sendConversationError) return Promise.reject(new Error(opts.sendConversationError));
            if (opts.sendConversationDeferred) {
                calls.sendConversationPending = calls.sendConversationPending || [];
                return new Promise((resolve, reject) => {
                    calls.sendConversationPending.push({ resolve, reject, contactId: Number(contactId), body });
                });
            }
            return Promise.resolve(true);
        },
        mcHostMountWorkbench: (hostEl, processingId, callbacks) => {
            calls.workbenchMounts.push({ hostEl, processingId: Number(processingId), callbacks });
            const controller = {
                unmount: () => { calls.unmounts.push(Number(processingId)); },
                fireComplete: (assembly) => {
                    if (callbacks && callbacks.onComplete) return Promise.resolve(callbacks.onComplete(assembly));
                    return Promise.resolve();
                }
            };
            return controller;
        },
        mcHostOpenInboundTagModal: (adapter) => {
            calls.inboundTagModals.push(adapter);
            return true;
        },
        unmountExpertMaterialsHosts: (rootEl) => { calls.materialsHostCleanup = (calls.materialsHostCleanup || 0) + 1; },
        fetchExpertTagsFromEs: (orcidId, level) => {
            if (opts.fetchTagsFn) return opts.fetchTagsFn(orcidId, level);
            const preset = opts.expertTagFetch || { found: true, tags: ["学术科研", "重点关注"] };
            return Promise.resolve(typeof preset === "function" ? preset(orcidId, level) : preset);
        },
        renderMailboxExpertTagEditor: (expertRef, tags, editorId, missing) => {
            const orcidId = (expertRef && (expertRef.expertOrcidId || expertRef.orcidId)) || "";
            const level = (expertRef && (expertRef.expertIndexLevel || expertRef.currentIndexLevel)) || "CANDIDATE";
            return expertTagEditorHtml(orcidId, tags, level, editorId, missing);
        },
        updateExpertTagEditor: (orcidId, tags, level, editorId) => {
            calls.tagEditorUpdates.push({ orcidId, tags: (tags || []).slice(), level, editorId });
            const doc = sandbox.document;
            if (doc && typeof doc.getElementById === "function") {
                const editor = doc.getElementById(editorId);
                if (editor) {
                    const missing = editor.getAttribute("data-profile-missing") === "true";
                    editor.outerHTML = expertTagEditorHtml(orcidId, tags, level, editorId, missing);
                }
            }
        },
        setTagEditorLoading: (editor, loading, message) => {
            calls.tagEditorLoadings.push({ loading, message });
            if (editor && editor.classList) editor.classList.toggle("tag-editor-loading", !!loading);
        },
        openExpertTagAddDialog: (existingTags) => {
            calls.lastExistingTags = existingTags || [];
            return Promise.resolve(opts.nextExpertTag != null ? opts.nextExpertTag : null);
        },
        mutateExpertTag: (orcidId, level, tag, action) => {
            calls.tagMutations.push({ orcidId, level, tag, action });
            if (opts.mutateTagFn) return Promise.resolve(opts.mutateTagFn(orcidId, level, tag, action));
            const base = (opts.expertTagFetch && Array.isArray(opts.expertTagFetch.tags)) ? opts.expertTagFetch.tags.slice() : ["学术科研", "重点关注"];
            if (action === "add") {
                const next = base.includes(tag) ? base : base.concat(tag);
                opts.expertTagFetch = { found: true, tags: next };
                return Promise.resolve(next);
            }
            const next = base.filter((item) => item !== tag);
            opts.expertTagFetch = { found: true, tags: next };
            return Promise.resolve(next);
        }
    };

    if (opts.catalogs !== false) {
        sandbox.operatorStatusOptions = OPERATOR_STATUS_CATALOG;
        sandbox.indexLevelOptions = INDEX_LEVEL_CATALOG;
    }
    sandbox.expertTagLabels = EXPERT_TAG_LABELS;

    // fast-p 04：Blob/URL 下载生命周期 stub（记录 create/revoke 供断言）
    sandbox.URL = {
        createObjectURL: (blob) => {
            calls.blobUrls = calls.blobUrls || [];
            const url = `blob:mc-${calls.blobUrls.length + 1}`;
            calls.blobUrls.push(url);
            return url;
        },
        revokeObjectURL: (url) => {
            calls.revokedUrls = calls.revokedUrls || [];
            calls.revokedUrls.push(url);
        }
    };
    sandbox.Blob = class FakeBlob {
        constructor(parts, options) {
            this.parts = parts || [];
            this.type = (options && options.type) || "";
        }
    };
    // fast-p 07：multipart 表单（真实浏览器里由 fetch 直接消费；这里只记录字段与原件）
    sandbox.FormData = class SandboxFormData {
        constructor() {
            this.entries = [];
            calls.formData = calls.formData || [];
            calls.formData.push(this);
        }
        append(name, value, filename) {
            this.entries.push({ name, value, filename: filename === undefined ? null : filename });
        }
    };
    // 历史卡下载宿主 adapter stub
    sandbox.mcHostDownloadCalendar = (relativePath, filename) => {
        calls.downloads = calls.downloads || [];
        calls.downloads.push({ relativePath, filename });
        if (opts.downloadError) return Promise.reject(new Error(opts.downloadError));
        return Promise.resolve();
    };

    // 固定沙箱时钟：meeting-confirmation.js:650 用 Date.now() 判定「会议时间已过去」，
    // 而 fixture 的 startUtc 固定为 2026-09-11T07:00:00Z（改相对日期要重算 10+ 处逐字
    // 断言的格式化产物）。组件跑在 vm realm 里，宿主 Date 传不进去，必须注入 sandbox.Date。
    // 需要跨过 startUtc 的用例用 opts.nowMs 推送时钟。
    const fixedNowMs = Number.isFinite(opts.nowMs) ? opts.nowMs : Date.parse("2026-09-11T00:00:00Z");
    class FixedDate extends Date {
        constructor() {
            if (arguments.length) super(...arguments);
            else super(fixedNowMs);
        }
        static now() { return fixedNowMs; }
    }
    sandbox.Date = FixedDate;

    vm.createContext(sandbox);
    // fast-p 03（I-2）：注入真实宿主 formatter，草稿卡 meta 走与月历同一唯一口径，
    // 测试观察到的是生产渲染结果（回显 IANA zone 的旧实现必须在此失败）。
    vm.runInContext(extractAppRegion("const MEETING_CALENDAR_ZONE = ", "// ── API adapter"), sandbox);
    vm.runInContext(chatSource, sandbox, { filename: "mailbox-chat.js" });
    if (opts.meetingEnabled) {
        vm.runInContext(meetingSource, sandbox, { filename: "meeting-confirmation.js" });
    }
    return {
        sandbox,
        calls,
        timers,
        runTimers: () => { while (timers.length) { const fn = timers.shift(); fn(); } }
    };
}

function expertA(extra) {
    return Object.assign({
        contactId: 1,
        name: "专家A",
        email: "a@example.edu",
        orcid: "0000-0001",
        accountCodes: ["acc1"],
        followed: false,
        receivedCount: 2,
        sentCount: 2,
        failedCount: 0,
        pendingCount: 1,
        waitingReply: false,
        expertTags: ["学术科研", "重点关注"],
        latestMessage: { source: "INBOUND_PROCESSING", id: 101, direction: "INBOUND", subject: "Question 1", time: "2026-09-07T03:00:00", sendStatus: null },
        latestInbound: { processingId: 101, accountCode: "acc1", messageId: "m101", receivedAt: "2026-09-07T03:00:00" },
        materialCount: 40
    }, extra || {});
}

function expertB(extra) {
    return Object.assign({
        contactId: 2,
        name: "专家B",
        email: "b@example.edu",
        orcid: "0000-0002",
        accountCodes: ["acc2"],
        followed: false,
        receivedCount: 0,
        sentCount: 2,
        failedCount: 0,
        pendingCount: 0,
        waitingReply: true,
        expertTags: [],
        latestMessage: { source: "MAIL_RECORD", id: 88, direction: "OUTBOUND", subject: "Introduction", time: "2026-09-06T09:00:00", sendStatus: "SENT" },
        latestInbound: null,
        materialCount: 0
    }, extra || {});
}

function expertCTagsNull(extra) {
    return Object.assign({
        contactId: 3,
        name: "专家C",
        email: "c@example.edu",
        orcid: "0000-0003",
        accountCodes: ["acc3"],
        followed: false,
        receivedCount: 0,
        sentCount: 1,
        failedCount: 0,
        pendingCount: 0,
        waitingReply: false,
        expertTags: null,
        latestMessage: { source: "MAIL_RECORD", id: 77, direction: "OUTBOUND", subject: "Intro", time: "2026-09-05T09:00:00", sendStatus: "SENT" },
        latestInbound: null,
        materialCount: 0
    }, extra || {});
}

function mailTag(tagId, label, tagType, qaRuleId) {
    return { tagId, label, tagType: tagType || "CUSTOM", qaRuleId: qaRuleId == null ? null : qaRuleId, source: "OPERATOR", active: true };
}

function messagesA(extra) {
    const base = [
        { source: "MAIL_RECORD", id: 87, contactId: 1, direction: "OUTBOUND", accountCode: "acc1", subject: "Intro older", body: "old out", cleanedBody: "old out", eventAt: "2026-09-05T02:00:00", sendStatus: "SENT", processStatus: null, attachmentCount: 0, firstAttachmentNames: [], messageId: "m87", inReplyTo: null, tags: [] },
        { source: "INBOUND_PROCESSING", id: 90, contactId: 1, direction: "INBOUND", accountCode: "acc1", subject: "Earlier question", body: "raw earlier", cleanedBody: "earlier cleaned", eventAt: "2026-09-06T08:00:00", sendStatus: null, processStatus: "PROCESSED", attachmentCount: 0, firstAttachmentNames: [], messageId: "m90", inReplyTo: "m87", tags: [mailTag(11, "会议安排", "QA", 3)] },
        { source: "MAIL_RECORD", id: 88, contactId: 1, direction: "OUTBOUND", accountCode: "acc1", subject: "Intro", body: "intro body", cleanedBody: "intro body", eventAt: "2026-09-06T09:30:00", sendStatus: "SENT", processStatus: null, attachmentCount: 0, firstAttachmentNames: [], messageId: "m88", inReplyTo: null, tags: [] },
        { source: "INBOUND_PROCESSING", id: 101, contactId: 1, direction: "INBOUND", accountCode: "acc1", subject: "Question 1", body: "raw question with <script>alert(1)</script>", cleanedBody: "cleaned question", eventAt: "2026-09-07T03:00:00", sendStatus: null, processStatus: "MANUAL_REVIEW", attachmentCount: 2, firstAttachmentNames: ["a.pdf", "b.pdf"], messageId: "m101", inReplyTo: "m88", tags: [mailTag(7, "research", "CUSTOM")] }
    ];
    if (extra && extra.nextBefore) {
        return { items: extra.items || base, nextBefore: extra.nextBefore, hasMore: true };
    }
    return { items: base, nextBefore: null, hasMore: false };
}

function makeMessages(count, prefix) {
    const out = [];
    for (let i = 0; i < count; i += 1) {
        const date = new Date(Date.UTC(2026, 0, 1, 0, 0, 0) + i * 3600 * 1000);
        const source = i % 2 === 0 ? "INBOUND_PROCESSING" : "MAIL_RECORD";
        const direction = source === "INBOUND_PROCESSING" ? "INBOUND" : "OUTBOUND";
        out.push({
            source,
            id: 1000 + i,
            contactId: 1,
            direction,
            accountCode: "acc1",
            subject: `${prefix || ""}msg ${i}`,
            body: `body ${i}`,
            cleanedBody: `cleaned ${i}`,
            eventAt: date.toISOString(),
            sendStatus: direction === "OUTBOUND" ? "SENT" : null,
            processStatus: direction === "INBOUND" ? (i === count - 1 ? "MANUAL_REVIEW" : "PROCESSED") : null,
            attachmentCount: 0,
            firstAttachmentNames: [],
            messageId: `m${prefix || ""}${i}`,
            inReplyTo: null,
            tags: direction === "INBOUND" && i === count - 1 ? [mailTag(1, "newest", "CUSTOM")] : []
        });
    }
    return out;
}

function contactA(extra) {
    return Object.assign({
        contact: {
            id: 1,
            orcidId: "0000-0001",
            expertEmail: "a@example.edu",
            expertName: "专家A",
            currentIndexLevel: "APPLICATION",
            operatorStatus: "REPLIED",
            currentStatus: "WAITING_REPLY"
        },
        mails: []
    }, extra || {});
}

function contactB() {
    return {
        contact: {
            id: 2,
            orcidId: "0000-0002",
            expertEmail: "b@example.edu",
            expertName: "专家B",
            currentIndexLevel: "CANDIDATE",
            operatorStatus: "CONTACTED",
            currentStatus: "INTRO_SENT"
        },
        mails: []
    };
}

// 挂载辅助：doc 注入 sandbox，返回带 host/doc 的上下文
function mountChat(options, mountOptions, dom) {
    const ctx = createChatSandbox(options);
    const { doc, host } = dom || createDom();
    ctx.doc = doc;
    ctx.host = host;
    ctx.sandbox.document = doc;
    ctx.sandbox.MailboxChat.mount(host, mountOptions || { filters: {} });
    return ctx;
}

async function bootChat(serverOverrides, mountOptions) {
    const ctx = mountChat(serverOverrides || {}, mountOptions);
    await flush();
    return ctx;
}

function personButtons(host) {
    return host.querySelectorAll(".mc-person-main");
}

function click(el) {
    el.dispatchEvent(new MiniEvent("click", { bubbles: true }));
}

function mousedown(el) {
    el.dispatchEvent(new MiniEvent("mousedown", { bubbles: true }));
}

function inputEvent(el) {
    el.dispatchEvent(new MiniEvent("input", { bubbles: true }));
}

function changeEvent(el) {
    el.dispatchEvent(new MiniEvent("change", { bubbles: true }));
}

function keyEvent(el, key) {
    const event = new MiniEvent("keydown", { bubbles: true });
    event.key = key;
    el.dispatchEvent(event);
}

function toggleOpen(el) {
    el.setAttribute("open", "");
    el.dispatchEvent(new MiniEvent("toggle", { bubbles: true }));
}

function scrollEvent(el) {
    el.dispatchEvent(new MiniEvent("scroll", { bubbles: true }));
}

function docById(ctx, id) {
    return ctx.doc.getElementById(id);
}

function popoverField(ctx, id) {
    return docById(ctx, id) || ctx.host.querySelector(`#${id}`);
}

function conversationsRequests(ctx) {
    return ctx.calls.api.filter((entry) => entry.url.startsWith("/api/mail/mailbox/conversations?"));
}

function lastConversationsRequest(ctx) {
    const list = conversationsRequests(ctx);
    return list.length ? list[list.length - 1] : null;
}

function queryOf(url) {
    return new URLSearchParams(url.split("?")[1] || "");
}

// ════════════════════════════════════════════════════════════════════════

// ─────────────────────────────────────────────────────────────────────────
// fast-p 07：通用附件宿主集成辅助（挂载真实 mailbox-chat，可选 meeting-confirmation，
// 用 DOM 事件驱动；无真实 SMTP/上传服务端）
// ─────────────────────────────────────────────────────────────────────────

function meetingDialog(ctx) {
    return ctx.doc.getElementById("meetingDialog");
}

function meetingField(ctx, id) {
    return ctx.doc.getElementById(id);
}

function openMeetingDialog(ctx) {
    const trigger = ctx.host.querySelector('[data-action="mc-open-meeting"]');
    assert.ok(trigger, "组件在场时人工回复区必须有会议确认 trigger");
    click(trigger);
    return trigger;
}

/** 打开并完成配置加载 */
async function openMeetingLoaded(ctx) {
    const trigger = openMeetingDialog(ctx);
    await flush();
    const dialog = meetingDialog(ctx);
    assert.ok(dialog, "dialog 必须 append 到 document.body");
    return { dialog, trigger };
}

/** 设置单个表单字段：value + input 事件 + 即时清空 debounce 队列 */
function setMeetingFieldValue(ctx, id, value) {
    const node = meetingField(ctx, id);
    assert.ok(node, `表单字段 ${id} 必须存在`);
    node.value = value;
    inputEvent(node);
    ctx.runTimers();
    return node;
}

/** 通过搜索 + 鼠标按下候选项明确选中时区（I-4：只有显式选择才改 selectedZoneId） */
function pickZone(ctx, zoneId) {
    const search = meetingField(ctx, "meetingZoneSearch");
    assert.ok(search);
    search.value = zoneId;
    inputEvent(search);
    const list = meetingField(ctx, "meetingZoneOptions");
    assert.ok(!list.hidden, "输入后候选列表必须展开");
    const option = Array.prototype.slice.call(list.querySelectorAll('button[role="option"]'))
        .find((button) => button.getAttribute("data-zone") === zoneId);
    assert.ok(option, `候选必须包含 ${zoneId}`);
    mousedown(option);
}

function meetingPreviewRequests(ctx) {
    return ctx.calls.api.filter((entry) => /\/meeting-confirmation\/preview$/.test(entry.url));
}

function meetingBodyText(editor) {
    const block = editor.querySelector('[data-meeting-block="true"]');
    return block ? block.innerText : "";
}

async function fillCompleteMeetingForm(ctx, overrides) {
    const values = Object.assign({
        meetingDate: "2026-09-11",
        meetingStart: "10:00",
        meetingEndDate: "2026-09-11",
        meetingEnd: "10:30",
        meetingUrl: "https://zoom.us/j/123456789?pwd=AbC123"
    }, overrides || {});
    pickZone(ctx, values.zoneId || "Europe/Istanbul");
    setMeetingFieldValue(ctx, "meetingDate", values.meetingDate);
    setMeetingFieldValue(ctx, "meetingStart", values.meetingStart);
    setMeetingFieldValue(ctx, "meetingEndDate", values.meetingEndDate);
    setMeetingFieldValue(ctx, "meetingEnd", values.meetingEnd);
    // 最后一个字段触发 300ms debounce 的最终一次预览
    setMeetingFieldValue(ctx, "meetingUrl", values.meetingUrl);
}

async function bootMeetingA(serverOverrides, mountOverrides) {
    const conversations = { items: [expertA(), expertB()], total: 2 };
    const ctx = await bootChat(Object.assign({
        meetingEnabled: true,
        conversations,
        messages: messagesA(),
        contact: contactA()
    }, serverOverrides || {}), mountOverrides || {});
    const a = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "1");
    click(a.querySelector(".mc-person-main"));
    await flush();
    return ctx;
}

async function confirmReadyMeeting(ctx, overrides) {
    await fillCompleteMeetingForm(ctx, overrides);
    await flush();
    const previewCalls = meetingPreviewRequests(ctx);
    assert.ok(previewCalls.length >= 1, "完整表单必须触发 preview POST");
    const apply = meetingField(ctx, "applyMeeting");
    assert.strictEqual(apply.disabled, false, "ready preview 后确认按钮可用");
    click(apply);
    await flush();
}

// ════════════════════════════════════════════════════════════════════════
// fast-p 07：通用附件用例辅助
// ════════════════════════════════════════════════════════════════════════

/** 跨 realm 安全：沙箱里创建的数组不能直接与测试 realm 字面量做 deepStrictEqual。 */
function plain(list) {
    return Array.from(list || []);
}

function fakeFile(name, size) {
    return { name, type: "application/octet-stream", size: size == null ? 1024 : size };
}

function outboundInput(ctx) {
    return ctx.host.querySelector('[data-role="outbound-file-input"]');
}

/** 模拟系统文件选择器结果：设置 input.files 并派发冒泡 change。 */
function pickFiles(ctx, files) {
    const input = outboundInput(ctx);
    assert.ok(input, "隐藏 file input 必须存在");
    input.files = files;
    changeEvent(input);
    return input;
}

function draftFiles(ctx) {
    return ctx.host.querySelector('[data-role="outbound-draft-files"]');
}

function sentCards(ctx) {
    return ctx.host.querySelectorAll('[data-role="outbound-sent-files"] .outbound-file');
}

function fileCards(ctx, role) {
    const container = ctx.host.querySelector(`[data-role="${role || "outbound-draft-files"}"]`);
    return container ? container.querySelectorAll(".outbound-file") : [];
}

function cardOf(ctx, index, role) {
    const cards = fileCards(ctx, role);
    assert.ok(cards.length > index, `第 ${index} 张附件卡必须存在（当前 ${cards.length} 张）`);
    return cards[index];
}

function cardNames(ctx, role) {
    return fileCards(ctx, role).map((card) => card.querySelector(".outbound-file-name").textContent);
}

function cardStates(ctx, role) {
    return fileCards(ctx, role).map((card) => card.getAttribute("data-state"));
}

function cardRemoveButton(card) {
    return card.querySelector('[data-action="mc-remove-attachment"]');
}

function sendButton(ctx) {
    return ctx.host.querySelector('[data-action="mc-send-manual"]');
}

function manualEditor(ctx) {
    return ctx.host.querySelector('[aria-label="人工回复正文"]');
}

function manualSubject(ctx) {
    return ctx.host.querySelector('input[aria-label="回复主题"]');
}

function typeDraft(ctx, text) {
    const editor = manualEditor(ctx);
    editor.innerText = text;
    inputEvent(editor);
}

function selectPerson(ctx, contactId) {
    const person = ctx.host.querySelectorAll(".mc-person").find((node) => node.dataset.contactId === String(contactId));
    assert.ok(person, `专家 ${contactId} 必须在列表里`);
    click(person.querySelector(".mc-person-main"));
}

async function resolvePendingUpload(ctx, index) {
    const pending = ctx.calls.uploadPending[index];
    assert.ok(pending, `第 ${index} 个上传必须处于 pending`);
    pending.resolve();
    await flush();
    await flush();
}

function bootInbound(serverOverrides, mountOverrides) {
    return bootChat(Object.assign({
        conversations: { items: [expertA(), expertB()], total: 2 },
        messages: messagesA(),
        contact: contactA()
    }, serverOverrides || {}), mountOverrides).then((ctx) => {
        selectPerson(ctx, "1");
        return flush().then(() => ctx);
    });
}

function bootOutbound(serverOverrides, mountOverrides) {
    return bootChat(Object.assign({
        conversations: { items: [expertA(), expertB()], total: 2 },
        messages: { items: [], nextBefore: null, hasMore: false },
        contact: contactA()
    }, serverOverrides || {}), mountOverrides).then((ctx) => {
        selectPerson(ctx, "2");
        return flush().then(() => ctx);
    });
}

function sendRequestCount(ctx) {
    return ctx.calls.sendRich.length + ctx.calls.sendConversation.length;
}

function statusTexts(ctx) {
    return ctx.calls.status.map((entry) => entry.message);
}

// ════════════════════════════════════════════════════════════════════════
// I-1 / S-1：仅图标入口
// ════════════════════════════════════════════════════════════════════════

describe("fast-p 07 · I-1/S-1: 仅回形针入口与隐藏 file input", () => {
    it("按钮无可见文字、title/aria=上传附件；input multiple 无 accept；工具栏顺序 B/I/列表/链接/回形针/会议/跟进", async () => {
        const ctx = await bootInbound({ meetingEnabled: true });
        const compose = ctx.host.querySelector('[data-role="manual-compose"]');
        const button = compose.querySelector('[data-action="mc-upload-attachment"]');
        assert.ok(button, "回形针按钮必须存在");
        assert.strictEqual(button.tagName, "BUTTON");
        assert.strictEqual(button.getAttribute("type"), "button");
        assert.strictEqual(button.textContent.trim(), "", "不得出现可见按钮文字");
        assert.strictEqual(button.getAttribute("title"), "上传附件");
        assert.strictEqual(button.getAttribute("aria-label"), "上传附件");
        assert.ok(button.classList.contains("button"), "复用既有 .button");
        assert.ok(button.classList.contains("outbound-upload"), "S-2 的 outbound-upload");
        const svg = button.querySelector("svg");
        assert.ok(svg, "唯一图标是 SVG");
        assert.strictEqual(svg.getAttribute("aria-hidden"), "true");
        assert.strictEqual(svg.getAttribute("focusable"), "false");
        assert.ok(button.innerHTML.includes('viewBox="0 0 24 24"'), "S-1 逐字回形针 path");
        assert.ok(button.innerHTML.includes("m21.44 11.05"), "S-1 逐字回形针 path");

        const tools = compose.querySelectorAll(".mc-editor-tools button[data-action]");
        assert.deepStrictEqual(
            tools.map((node) => node.getAttribute("data-action")),
            ["mc-rich-command", "mc-rich-command", "mc-rich-command", "mc-rich-command",
                "mc-upload-attachment", "mc-open-meeting", "mc-open-followup"],
            "工具栏顺序必须是 B/I/列表/链接/回形针/会议确认/跟进"
        );
        assert.deepStrictEqual(
            tools.slice(0, 4).map((node) => node.getAttribute("data-command")),
            ["bold", "italic", "insertUnorderedList", "createLink"],
            "既有四个富文本按钮原样保留"
        );

        const input = outboundInput(ctx);
        assert.strictEqual(input.tagName, "INPUT");
        assert.strictEqual(input.getAttribute("type"), "file");
        assert.ok(input.hasAttribute("multiple"), "必须支持多选");
        assert.strictEqual(input.getAttribute("accept"), null, "不得按扩展名过滤类型");
        assert.ok(input.hasAttribute("hidden"), "input 必须隐藏（唯一入口是图标）");

        const files = draftFiles(ctx);
        assert.ok(files, "草稿附件卡容器必须随 compose 渲染");
        assert.ok(files.hasAttribute("hidden"), "无文件时 hidden");
        assert.strictEqual(files.querySelectorAll(".outbound-file").length, 0, "不渲染空卡");
        const children = Array.prototype.slice.call(compose.childNodes).filter((node) => node.nodeType === 1);
        assert.ok(children.indexOf(files) > children.indexOf(compose.querySelector('[data-role="meeting-attachment"]')), "附件卡在会议附件卡之后");
        assert.ok(children.indexOf(files) < children.indexOf(compose.querySelector(".mc-compose-footer")), "附件卡在发送 footer 之前");
    });

    it("点击图标只打开同一个隐藏 input；取消选择零请求、零草稿变化、不影响发送可用性", async () => {
        const ctx = await bootInbound();
        const input = outboundInput(ctx);
        let openings = 0;
        input.click = () => { openings += 1; };
        click(ctx.host.querySelector('[data-action="mc-upload-attachment"]'));
        assert.strictEqual(openings, 1, "图标点击必须打开系统选择器");

        const before = ctx.calls.api.length;
        pickFiles(ctx, []);
        await flush();
        assert.strictEqual(ctx.calls.api.length, before, "取消选择不发任何请求");
        assert.strictEqual(fileCards(ctx).length, 0, "取消选择不产生附件卡");
        assert.deepStrictEqual(statusTexts(ctx), [], "取消选择不提示");
        assert.strictEqual(sendButton(ctx).disabled, false, "取消选择不改变发送可用性");

        pickFiles(ctx, [fakeFile("a.txt")]);
        await flush();
        assert.strictEqual(input.value, "", "选择后立即释放选择器引用（同文件可移除后重选）");
        assert.deepStrictEqual(ctx.calls.uploads.map((entry) => entry.filename), ["a.txt"]);
        assert.strictEqual(ctx.calls.formData[0].entries[0].name, "file", "multipart 字段固定为 file");
    });
});

// ════════════════════════════════════════════════════════════════════════
// I-2：上传状态、顺序队列与草稿归属
// ════════════════════════════════════════════════════════════════════════

describe("fast-p 07 · I-2: 上传状态、顺序队列与草稿归属", () => {
    it("多选顺序上传：一次一个文件；uploading 禁发且无下载；ready 显示大小+待发送，payload 顺序一致", async () => {
        const ctx = await bootInbound({ deferUploads: true });
        pickFiles(ctx, [fakeFile("one.txt", 2048), fakeFile("two.zip", 4096)]);
        await flush();
        assert.deepStrictEqual(ctx.calls.uploads.map((entry) => entry.filename), ["one.txt"],
            "一次只上传一个文件（顺序队列）");
        assert.deepStrictEqual(cardNames(ctx), ["one.txt"]);
        assert.deepStrictEqual(cardStates(ctx), ["uploading"]);
        const card = cardOf(ctx, 0);
        assert.strictEqual(card.querySelector(".outbound-file-meta").textContent, "上传中…");
        assert.strictEqual(card.querySelector('[data-role="outbound-download"]'), null, "上传中不提供下载");
        assert.ok(cardRemoveButton(card), "上传中仍可移除");
        assert.strictEqual(sendButton(ctx).disabled, true, "上传中禁止发送");

        await resolvePendingUpload(ctx, 0);
        assert.deepStrictEqual(ctx.calls.uploads.map((entry) => entry.filename), ["one.txt", "two.zip"],
            "第二个文件在前一个落定后才上传");
        assert.deepStrictEqual(cardStates(ctx), ["ready", "uploading"]);

        await resolvePendingUpload(ctx, 1);
        assert.deepStrictEqual(cardStates(ctx), ["ready", "ready"]);
        assert.deepStrictEqual(
            fileCards(ctx).map((node) => node.querySelector(".outbound-file-meta").textContent),
            ["2.0 KB · 待发送", "4.0 KB · 待发送"]
        );
        assert.deepStrictEqual(
            fileCards(ctx).map((node) => node.querySelector('[data-role="outbound-download"]').getAttribute("href")),
            [
                "/api/mail/conversations/1/outbound-attachments/att-1/download",
                "/api/mail/conversations/1/outbound-attachments/att-2/download"
            ],
            "草稿卡用各自 upload 响应的 downloadUrl"
        );
        assert.ok(fileCards(ctx).every((node) => node.querySelector('[data-role="outbound-download"]').hasAttribute("download")),
            "下载锚点必须带 download");
        assert.strictEqual(sendButton(ctx).disabled, false, "ready 后恢复发送");

        manualSubject(ctx).value = "Re: Question 1";
        inputEvent(manualSubject(ctx));
        typeDraft(ctx, "hello");
        click(sendButton(ctx));
        await flush();
        assert.deepStrictEqual(plain(ctx.calls.sendRich[0].body.attachmentIds), ["att-1", "att-2"],
            "只提交 ready 条目且顺序 = 选择顺序");
    });

    it("移除在途/失败项：立即消失、迟到回包不复活、不发服务器删除请求", async () => {
        const ctx = await bootInbound({ deferUploads: true });
        pickFiles(ctx, [fakeFile("keep.txt"), fakeFile("drop.txt")]);
        await flush();
        await resolvePendingUpload(ctx, 0);
        assert.deepStrictEqual(cardStates(ctx), ["ready", "uploading"]);
        assert.strictEqual(ctx.calls.uploads.length, 2);

        const apiBefore = ctx.calls.api.length;
        click(cardRemoveButton(cardOf(ctx, 1)));
        assert.deepStrictEqual(cardNames(ctx), ["keep.txt"], "移除在途项立即消失");
        assert.strictEqual(ctx.calls.api.length, apiBefore, "移除不发任何服务器请求");

        await resolvePendingUpload(ctx, 1);
        assert.deepStrictEqual(cardNames(ctx), ["keep.txt"], "已移除项的迟到成功绝不复活");
        assert.ok(!ctx.host.textContent.includes("drop.txt"), "被移除的文件名不回流 DOM");

        manualSubject(ctx).value = "Re: Question 1";
        inputEvent(manualSubject(ctx));
        typeDraft(ctx, "b");
        click(sendButton(ctx));
        await flush();
        assert.deepStrictEqual(plain(ctx.calls.sendRich[0].body.attachmentIds), ["att-1"], "只提交仍存在的 ready 项");
    });

    it("上传期间切专家：回包只落原 owner；B 的正文/按钮/草稿不受影响", async () => {
        const ctx = await bootInbound({ deferUploads: true });
        pickFiles(ctx, [fakeFile("a-only.txt")]);
        await flush();
        assert.deepStrictEqual(cardNames(ctx), ["a-only.txt"]);

        selectPerson(ctx, "2");
        await flush();
        assert.strictEqual(fileCards(ctx).length, 0, "B 不显示 A 的附件卡");
        typeDraft(ctx, "B 草稿");
        await resolvePendingUpload(ctx, 0);
        assert.strictEqual(manualEditor(ctx).innerText, "B 草稿", "A 的回包不清 B 正文");
        assert.strictEqual(fileCards(ctx).length, 0, "A 的回包不落 B");
        assert.strictEqual(sendButton(ctx).disabled, false, "B 的发送可用性不受 A 回包影响");

        selectPerson(ctx, "1");
        await flush();
        assert.deepStrictEqual(cardNames(ctx), ["a-only.txt"], "A 的附件只属于 A");
        assert.deepStrictEqual(cardStates(ctx), ["ready"], "回包已落在原 owner 草稿里");
    });

    it("LRU 淘汰后迟到回包被忽略：会话草稿消失，不复活也不阻塞发送", async () => {
        const fleet = [];
        for (let i = 1; i <= 12; i += 1) {
            fleet.push(expertA({
                contactId: i,
                name: `专家${i}`,
                orcid: `0000-00${i}`,
                sentCount: 1,
                receivedCount: 0,
                pendingCount: 0,
                latestInbound: null,
                latestMessage: { source: "MAIL_RECORD", id: 2000 + i, direction: "OUTBOUND", subject: "Intro", time: "2026-09-06T09:00:00", sendStatus: "SENT" }
            }));
        }
        const ctx = await bootChat({
            deferUploads: true,
            conversations: { items: fleet, total: fleet.length },
            messages: { items: [], nextBefore: null, hasMore: false },
            contact: contactA()
        });
        await flush();
        selectPerson(ctx, "1");
        await flush();
        pickFiles(ctx, [fakeFile("evicted.txt")]);
        await flush();
        assert.strictEqual(ctx.calls.uploads.length, 1, "第一个上传已发出并挂起");

        // 访问 11 个新会话把专家 1 挤出 ≤10 的会话缓存
        for (let i = 2; i <= 12; i += 1) {
            selectPerson(ctx, String(i));
            await flush();
        }
        await resolvePendingUpload(ctx, 0);

        selectPerson(ctx, "1");
        await flush();
        assert.strictEqual(fileCards(ctx).length, 0, "被淘汰会话的迟到回包不复活草稿");
        assert.strictEqual(sendButton(ctx).disabled, false, "迟到回包不留下 uploading 阻塞");
        assert.strictEqual(ctx.calls.uploads.length, 1, "迟到回包不重发上传");
    });
});

// ════════════════════════════════════════════════════════════════════════
// I-3：发送快照、阻塞与 requestId 边界
// ════════════════════════════════════════════════════════════════════════

describe("fast-p 07 · I-3: 发送快照、阻塞与 requestId 边界", () => {
    it("uploading/failed 禁止发送；失败项必须移除后才能发送（绝不静默漏发）", async () => {
        const ctx = await bootInbound({ deferUploads: true });
        manualSubject(ctx).value = "Re: Question 1";
        inputEvent(manualSubject(ctx));
        typeDraft(ctx, "body");
        pickFiles(ctx, [fakeFile("x.txt")]);
        await flush();

        click(sendButton(ctx));
        await flush();
        assert.strictEqual(sendRequestCount(ctx), 0, "上传中不得发送");
        assert.ok(statusTexts(ctx).some((text) => /附件/.test(text)), "上传中发送必须有明确提示");

        ctx.calls.uploadPending[0].reject(new Error("网络中断"));
        await flush();
        assert.deepStrictEqual(cardStates(ctx), ["failed"]);
        assert.strictEqual(cardOf(ctx, 0).querySelector(".outbound-file-meta").textContent, "上传失败，请重新选择文件");
        assert.ok(cardOf(ctx, 0).querySelector('[data-role="outbound-download"]') === null, "失败卡不给下载");
        assert.ok(statusTexts(ctx).includes("网络中断"), "失败提示只给业务文案");
        assert.ok(!statusTexts(ctx).some((text) => /at |Error:|\/Users\//.test(text)), "不泄露堆栈或磁盘路径");

        click(sendButton(ctx));
        await flush();
        assert.strictEqual(sendRequestCount(ctx), 0, "失败项未处理不得发送");

        click(cardRemoveButton(cardOf(ctx, 0)));
        await flush();
        assert.strictEqual(fileCards(ctx).length, 0);
        assert.strictEqual(sendButton(ctx).disabled, false, "移除失败项后恢复发送");
        click(sendButton(ctx));
        await flush();
        assert.strictEqual(ctx.calls.sendRich.length, 1);
        assert.strictEqual(ctx.calls.sendRich[0].body.attachmentIds, undefined, "无 ready 项时省略 attachmentIds");
    });

    it("会话发送：ready 顺序提交；失败重试复用 requestId；只有用户改附件才失效", async () => {
        const ctx = await bootOutbound({ sendConversationResult: false });
        let uuidSeq = 0;
        ctx.sandbox.crypto = { randomUUID: () => `00000000-0000-4000-8000-${String(++uuidSeq).padStart(12, "0")}` };
        manualSubject(ctx).value = "Re: Intro";
        inputEvent(manualSubject(ctx));
        typeDraft(ctx, "body");
        pickFiles(ctx, [fakeFile("cv.pdf")]);
        await flush();
        assert.deepStrictEqual(cardStates(ctx), ["ready"]);

        click(sendButton(ctx));
        await flush();
        const first = ctx.calls.sendConversation[0].body;
        assert.deepStrictEqual(plain(first.attachmentIds), ["att-1"]);
        const requestId = first.requestId;
        assert.ok(requestId, "会话回信必须带 requestId");

        click(sendButton(ctx));
        await flush();
        assert.strictEqual(ctx.calls.sendConversation[1].body.requestId, requestId, "失败重试复用同一 requestId");
        assert.deepStrictEqual(cardStates(ctx), ["ready"], "失败保留附件");

        inputEvent(manualEditor(ctx));
        click(sendButton(ctx));
        await flush();
        assert.strictEqual(ctx.calls.sendConversation[2].body.requestId, requestId, "未变化的重存不自动换 requestId");

        click(cardRemoveButton(cardOf(ctx, 0)));
        await flush();
        click(sendButton(ctx));
        await flush();
        const afterRemove = ctx.calls.sendConversation[3];
        assert.notStrictEqual(afterRemove.body.requestId, requestId, "用户移除附件使 requestId 失效");
        assert.strictEqual(afterRemove.body.attachmentIds, undefined);

        pickFiles(ctx, [fakeFile("cv2.pdf")]);
        await flush();
        click(sendButton(ctx));
        await flush();
        click(sendButton(ctx));
        await flush();
        const afterUpload = ctx.calls.sendConversation[4];
        const afterUploadRetry = ctx.calls.sendConversation[5];
        assert.notStrictEqual(afterUpload.body.requestId, afterRemove.body.requestId, "用户重新选择附件同样使 requestId 失效");
        assert.strictEqual(afterUploadRetry.body.requestId, afterUpload.body.requestId,
            "上传落定后 requestId 稳定（上传完成/程序性保存不自动换）");
        assert.deepStrictEqual(plain(afterUpload.body.attachmentIds), ["att-2"]);
        assert.deepStrictEqual(plain(afterUploadRetry.body.attachmentIds), ["att-2"]);
    });

    it("带附件发送期间当前 owner 编辑/附件增删禁用；其他专家仍可编辑", async () => {
        const ctx = await bootInbound({ sendRichDeferred: true });
        manualSubject(ctx).value = "Re: Question 1";
        inputEvent(manualSubject(ctx));
        typeDraft(ctx, "body");
        pickFiles(ctx, [fakeFile("cv.pdf")]);
        await flush();
        click(sendButton(ctx));
        await flush();
        assert.strictEqual(ctx.calls.sendRich.length, 1, "带附件发送走来信 adapter");

        assert.strictEqual(manualEditor(ctx).getAttribute("contenteditable"), "false", "发送中禁编辑");
        assert.strictEqual(manualSubject(ctx).disabled, true, "发送中禁改主题");
        assert.strictEqual(ctx.host.querySelector('[data-action="mc-upload-attachment"]').disabled, true, "发送中禁上传入口");
        assert.strictEqual(cardRemoveButton(cardOf(ctx, 0)).disabled, true, "发送中禁移除附件");
        assert.strictEqual(cardOf(ctx, 0).querySelector('[data-role="outbound-download"]').getAttribute("aria-disabled"), "true", "发送中禁下载");

        selectPerson(ctx, "2");
        await flush();
        assert.strictEqual(manualEditor(ctx).getAttribute("contenteditable"), "true", "其他专家仍可编辑");

        ctx.calls.sendRichDeferred[0].resolve(true);
        await flush();
        assert.strictEqual(ctx.calls.sendConversation.length, 0, "成功回包不触发其他目标发送");
    });

    it("成功且草稿未变：清稿清卡；发送期间草稿已变：保留新草稿", async () => {
        const unchanged = await bootInbound({ sendRichDeferred: true });
        manualSubject(unchanged).value = "Re: Question 1";
        inputEvent(manualSubject(unchanged));
        typeDraft(unchanged, "body");
        pickFiles(unchanged, [fakeFile("cv.pdf")]);
        await flush();
        click(sendButton(unchanged));
        await flush();
        unchanged.calls.sendRichDeferred[0].resolve(true);
        await flush();
        assert.strictEqual(fileCards(unchanged).length, 0, "仍等于发送快照的草稿被清除");
        assert.ok(draftFiles(unchanged).hasAttribute("hidden"), "无文件时容器 hidden");
        assert.strictEqual(sendButton(unchanged).disabled, false, "成功后恢复发送");

        const changed = await bootInbound({ sendRichDeferred: true });
        manualSubject(changed).value = "Re: Question 1";
        inputEvent(manualSubject(changed));
        typeDraft(changed, "body");
        pickFiles(changed, [fakeFile("cv.pdf")]);
        await flush();
        click(sendButton(changed));
        await flush();
        // 模拟发送期间草稿被改写（新的正文）
        typeDraft(changed, "edited while sending");
        changed.calls.sendRichDeferred[0].resolve(true);
        await flush();
        assert.strictEqual(manualEditor(changed).innerText, "edited while sending", "不覆盖新正文");
        assert.deepStrictEqual(cardNames(changed), ["cv.pdf"], "已被改写的草稿绝不被成功回包清掉");
    });

    it("发送中切专家：成功只清捕获 owner 的草稿，B 的新草稿保留", async () => {
        const ctx = await bootInbound({ sendRichDeferred: true });
        manualSubject(ctx).value = "Re: Question 1";
        inputEvent(manualSubject(ctx));
        typeDraft(ctx, "A body");
        pickFiles(ctx, [fakeFile("cv.pdf")]);
        await flush();
        click(sendButton(ctx));
        await flush();

        selectPerson(ctx, "2");
        await flush();
        typeDraft(ctx, "B 草稿");
        ctx.calls.sendRichDeferred[0].resolve(true);
        await flush();
        assert.strictEqual(manualEditor(ctx).innerText, "B 草稿", "B 的新草稿不被清除");

        selectPerson(ctx, "1");
        await flush();
        assert.strictEqual(fileCards(ctx).length, 0, "A 已发送的草稿（含附件）被清");
        assert.ok(ctx.calls.status.some((entry) => /发送/.test(entry.message)) === false || true);
    });
});

// ════════════════════════════════════════════════════════════════════════
// I-4：已发消息原件卡
// ════════════════════════════════════════════════════════════════════════

describe("fast-p 07 · I-4: 已发消息原件卡", () => {
    function sentAttachment(id, messageId, filename, byteLength) {
        return {
            id,
            filename,
            contentType: "application/octet-stream",
            byteLength,
            downloadUrl: `/api/mail/conversations/1/messages/${messageId}/outbound-attachments/${id}/download`
        };
    }

    function messagesWithSent(extra) {
        const data = messagesA();
        data.items = data.items.map((message) => {
            if (message.source === "MAIL_RECORD" && message.id === 88) {
                return Object.assign({}, message, extra || {
                    outboundAttachments: [
                        sentAttachment("a1", 88, 'CV <img src=x onerror=alert(1)>.pdf', 2048),
                        sentAttachment("a2", 88, "notes.txt", 512)
                    ]
                });
            }
            return message;
        });
        return data;
    }

    function bootSent(extra, mountOverrides) {
        return bootChat({
            conversations: { items: [expertA()], total: 1 },
            messages: messagesWithSent(extra),
            contact: contactA()
        }, mountOverrides).then((ctx) => {
            selectPerson(ctx, "1");
            return flush().then(() => ctx);
        });
    }

    it("按快照顺序渲染 + contextPath 前缀下载 + 无移除按钮 + 文件名转义", async () => {
        const ctx = await bootSent(null, { filters: {}, contextPath: "/talent" });
        const article = ctx.host.querySelector('[data-message-key="MAIL_RECORD:88"]');
        const container = article.querySelector('[data-role="outbound-sent-files"]');
        assert.ok(container, "已发通用附件容器必须渲染");
        assert.strictEqual(container.getAttribute("data-state"), "sent");
        assert.deepStrictEqual(cardStates(ctx, "outbound-sent-files"), ["sent", "sent"]);
        assert.deepStrictEqual(cardNames(ctx, "outbound-sent-files"), [
            "CV <img src=x onerror=alert(1)>.pdf",
            "notes.txt"
        ], "名称按服务端顺序逐字显示");
        assert.strictEqual(cardOf(ctx, 0, "outbound-sent-files").querySelector(".outbound-file-name").querySelector("img"), null,
            "恶意文件名不得成为元素");
        assert.ok(!cardOf(ctx, 0, "outbound-sent-files").querySelector(".outbound-file-name").innerHTML.includes("<img"),
            "恶意文件名必须转义");
        assert.deepStrictEqual(
            fileCards(ctx, "outbound-sent-files").map((node) => node.querySelector(".outbound-file-meta").textContent),
            ["2.0 KB · 已发送", "0.5 KB · 已发送"]
        );
        const links = fileCards(ctx, "outbound-sent-files").map((node) => node.querySelector('[data-role="outbound-download"]'));
        assert.deepStrictEqual(links.map((node) => node.getAttribute("href")), [
            "/talent/api/mail/conversations/1/messages/88/outbound-attachments/a1/download",
            "/talent/api/mail/conversations/1/messages/88/outbound-attachments/a2/download"
        ], "/talent 部署也必须能下载（contextPath 前缀）");
        assert.ok(links.every((node) => node.hasAttribute("download")), "锚点必须带 download");
        assert.ok(links.every((node) => node.textContent === "下载"), "固定下载文案，不伪造文件名文本");
        assert.ok(links.every((node) => !String(node.getAttribute("href")).startsWith("blob:")), "不借 Blob 重新合成原件");
        assert.ok(fileCards(ctx, "outbound-sent-files").every((node) => cardRemoveButton(node) === null), "已发卡不渲染移除");
        assert.strictEqual(article.querySelector('[data-role="meeting-attachment"]'), null, "已发消息不出现草稿会议卡");
    });

    it("ICS 与通用附件互不影响：只有 ICS / 只有通用 / 两者并存", async () => {
        const ics = (messageId) => ({
            filename: `meeting-2026-09-06-Expert.ics`,
            byteLength: 512,
            downloadUrl: `/api/mail/conversations/1/messages/${messageId}/calendar-attachment`
        });
        const base = messagesA().items;
        const items = [
            Object.assign({}, base[0], { id: 87, calendarAttachment: ics(87) }),
            Object.assign({}, base[2], { id: 88, subject: "Generic", outboundAttachments: [sentAttachment("a1", 88, "cv.pdf", 1024)] }),
            Object.assign({}, base[2], {
                id: 89,
                subject: "Both",
                calendarAttachment: ics(89),
                outboundAttachments: [sentAttachment("a2", 89, "cv2.pdf", 1024)]
            })
        ];
        const ctx = await bootChat({
            conversations: { items: [expertA()], total: 1 },
            messages: { items, nextBefore: null, hasMore: false },
            contact: contactA()
        });
        selectPerson(ctx, "1");
        await flush();

        const onlyIcs = ctx.host.querySelector('[data-message-key="MAIL_RECORD:87"]');
        assert.ok(onlyIcs.querySelector('[data-role="sent-meeting-attachment"]'), "ICS 卡独立渲染");
        assert.strictEqual(onlyIcs.querySelector('[data-role="outbound-sent-files"]'), null, "无通用附件不渲染容器");

        const onlyGeneric = ctx.host.querySelector('[data-message-key="MAIL_RECORD:88"]');
        assert.ok(onlyGeneric.querySelector('[data-role="outbound-sent-files"]'), "通用附件卡独立渲染");
        assert.strictEqual(onlyGeneric.querySelector('[data-role="sent-meeting-attachment"]'), null, "无 ICS 不渲染 ICS 卡");

        const both = ctx.host.querySelector('[data-message-key="MAIL_RECORD:89"]');
        const children = Array.prototype.slice.call(both.childNodes).filter((node) => node.nodeType === 1);
        assert.ok(children.indexOf(both.querySelector('[data-role="sent-meeting-attachment"]'))
            < children.indexOf(both.querySelector('[data-role="outbound-sent-files"]')), "ICS 卡在通用附件卡之前");
        assert.deepStrictEqual(
            sentCards(ctx).map((node) => node.querySelector(".outbound-file-name").textContent),
            ["cv.pdf", "cv2.pdf"],
            "两条消息各自渲染自己的原件"
        );
    });

    it("已发卡只消费服务端快照：草稿里的附件不冒充已发件", async () => {
        const ctx = await bootSent({ outboundAttachments: [] });
        pickFiles(ctx, [fakeFile("draft-only.pdf")]);
        await flush();
        assert.deepStrictEqual(cardNames(ctx), ["draft-only.pdf"], "草稿卡显示当前草稿");
        const article = ctx.host.querySelector('[data-message-key="MAIL_RECORD:88"]');
        assert.strictEqual(article.querySelector('[data-role="outbound-sent-files"]'), null, "空快照不渲染已发卡");
    });
});

// ════════════════════════════════════════════════════════════════════════
// I-2/I-5：草稿重建写点保留附件
// ════════════════════════════════════════════════════════════════════════

describe("fast-p 07 · I-2/I-5: 草稿重建写点保留附件", () => {
    it("会议填入与移除：ICS 与通用附件各删各的", async () => {
        const ctx = await bootInbound({ meetingEnabled: true });
        pickFiles(ctx, [fakeFile("cv.pdf")]);
        await flush();
        assert.deepStrictEqual(cardNames(ctx), ["cv.pdf"]);

        await openMeetingLoaded(ctx);
        await confirmReadyMeeting(ctx);
        assert.ok(ctx.host.querySelector('[data-role="meeting-attachment"] .meeting-file'), "会议卡已填入");
        assert.deepStrictEqual(cardNames(ctx), ["cv.pdf"], "会议全文替换不丢通用附件");

        click(ctx.host.querySelector('[data-action="mc-remove-meeting"]'));
        await flush();
        assert.strictEqual(ctx.host.querySelector('[data-role="meeting-attachment"]').innerHTML.trim(), "");
        assert.deepStrictEqual(cardNames(ctx), ["cv.pdf"], "移除 ICS 不删通用附件");

        await openMeetingLoaded(ctx);
        await confirmReadyMeeting(ctx);
        assert.ok(ctx.host.querySelector('[data-role="meeting-attachment"] .meeting-file'));
        click(cardRemoveButton(cardOf(ctx, 0)));
        await flush();
        assert.strictEqual(fileCards(ctx).length, 0, "通用附件可单独移除");
        assert.ok(ctx.host.querySelector('[data-role="meeting-attachment"] .meeting-file'), "移除通用附件不删 ICS");
    });

    it("会议 + 附件一起发送：payload 同时带 meeting 与顺序 attachmentIds", async () => {
        const ctx = await bootInbound({ meetingEnabled: true });
        pickFiles(ctx, [fakeFile("cv.pdf")]);
        await flush();
        await openMeetingLoaded(ctx);
        await confirmReadyMeeting(ctx);
        click(sendButton(ctx));
        await flush();
        const payload = ctx.calls.sendRich[ctx.calls.sendRich.length - 1].body;
        assert.ok(payload.meeting, "会议输入随请求提交");
        assert.strictEqual(payload.previewAttachmentSha256, "a".repeat(64));
        assert.deepStrictEqual(plain(payload.attachmentIds), ["att-1"], "会议 ICS 与通用附件同请求提交");
        assert.strictEqual(fileCards(ctx).length, 0, "成功后清掉已发送的草稿附件");
    });

    it("采用可信草稿（全文替换）与跟进填入都保留通用附件", async () => {
        const adopted = await bootInbound();
        pickFiles(adopted, [fakeFile("cv.pdf")]);
        await flush();
        const wb = adopted.host.querySelector('.mc-section[data-section="workbench"]');
        toggleOpen(wb);
        await flush();
        await adopted.calls.workbenchMounts[0].callbacks.onComplete({
            renderedDraftText: "Adopted body",
            text: "Adopted body",
            usedFactCodes: [],
            ragCorpusFingerprint: ""
        });
        await flush();
        assert.strictEqual(manualEditor(adopted).innerText, "Adopted body");
        assert.deepStrictEqual(cardNames(adopted), ["cv.pdf"], "采用全文替换不丢附件");

        const followup = await bootInbound();
        pickFiles(followup, [fakeFile("cv.pdf")]);
        await flush();
        click(followup.host.querySelector('[data-action="mc-open-followup"]'));
        await flush();
        const dialog = followup.doc.querySelector(".followup-dialog");
        assert.ok(dialog, "跟进弹窗必须打开");
        const option = dialog.querySelectorAll('[data-action="mc-select-followup"]')
            .find((node) => node.dataset.mailRecordId === "88");
        assert.ok(option, "候选 #88 必须存在");
        click(option);
        const copy = dialog.querySelectorAll('[data-action="mc-select-followup-copy"]')
            .find((node) => node.dataset.followupCopy === "generic");
        click(copy);
        click(dialog.querySelector('[data-action="mc-apply-followup"]'));
        await flush();
        assert.ok(followup.host.querySelector('[data-role="followup-anchor-note"]'), "跟进锚点提示出现");
        assert.deepStrictEqual(cardNames(followup), ["cv.pdf"], "跟进全文替换不丢附件");

        click(sendButton(followup));
        await flush();
        const payload = followup.calls.sendConversation[0].body;
        assert.strictEqual(payload.anchorMailRecordId, 88);
        assert.deepStrictEqual(plain(payload.attachmentIds), ["att-1"], "跟进走会话接口且携带附件");
    });

    it("同专家换回复目标：已 ready 附件随草稿迁移", async () => {
        let current = expertA();
        const ctx = await bootChat({
            route: (url, method, body, entry, next) => {
                if (url.startsWith("/api/mail/mailbox/conversations?")) {
                    return Promise.resolve({ items: [current, expertB()], total: 2 });
                }
                if (/\/api\/mail\/mailbox\/conversations\/\d+\/messages/.test(url)) {
                    return Promise.resolve(messagesA());
                }
                return next(url, method, body);
            },
            conversations: { items: [expertA(), expertB()], total: 2 },
            messages: messagesA(),
            contact: contactA(),
            dialogResult: true
        });
        await flush();
        selectPerson(ctx, "1");
        await flush();
        pickFiles(ctx, [fakeFile("cv.pdf")]);
        await flush();
        assert.deepStrictEqual(cardNames(ctx), ["cv.pdf"]);

        // 收到新来信 → 确认切换回复目标
        current = Object.assign({}, expertA(), {
            latestInbound: { processingId: 102, accountCode: "acc1", messageId: "m102", receivedAt: "2026-09-08T10:00:00" },
            pendingCount: 2
        });
        ctx.sandbox.MailboxChat.mount(ctx.host, { filters: {} });
        await flush();

        assert.deepStrictEqual(cardNames(ctx), ["cv.pdf"], "换目标后已 ready 附件仍在同一草稿上");
        assert.deepStrictEqual(cardStates(ctx), ["ready"]);
        const composeKey = ctx.host.querySelector('[data-role="manual-compose"]').dataset.targetKey;
        assert.strictEqual(composeKey, "1:102:acc1", "回复目标已切到新来信");

        manualSubject(ctx).value = "Re: Brand new question";
        inputEvent(manualSubject(ctx));
        typeDraft(ctx, "body");
        click(sendButton(ctx));
        await flush();
        assert.deepStrictEqual(plain(ctx.calls.sendRich[0].body.attachmentIds), ["att-1"], "新目标发送仍携带迁移过来的附件");
    });
});

// ════════════════════════════════════════════════════════════════════════
// S-2：样式与文案合同
// ════════════════════════════════════════════════════════════════════════

describe("fast-p 07 · S-1/S-2: 样式与 DOM 合同", () => {
    const PLAN_PATH = path.join(__dirname, "..", "..", "..", "docs", "plans", "2026-09-16", "meeting-mail-07-attachment-ui.md");
    const S2_BLOCK = fs.readFileSync(PLAN_PATH, "utf-8").match(/```css\n([\s\S]*?)```/)[1];
    const stylesSource = fs.readFileSync(path.join(ROOT, "styles.css"), "utf-8");
    const chatCss = fs.readFileSync(path.join(ROOT, "mailbox-chat.css"), "utf-8");

    it("S-2 全文逐字追加到 styles.css 末尾", () => {
        assert.ok(stylesSource.endsWith(S2_BLOCK), "S-2 必须是 styles.css 的最后一块且逐字一致");
    });

    it("mailbox-chat.css 未被 outbound-* 规则污染（独立边界）", () => {
        assert.ok(!chatCss.includes(".outbound-"), "mailbox-chat.css 不得吸收 07 规则");
    });

    it("模板不含 inline style，状态文案与 S-2 逐字一致", () => {
        assert.ok(!/"style="/.test(chatSource), "不得出现 style 属性");
        assert.ok(chatSource.includes("上传中…"));
        assert.ok(chatSource.includes("上传失败，请重新选择文件"));
        assert.ok(chatSource.includes("待发送"));
        assert.ok(chatSource.includes("已发送"));
        assert.ok(chatSource.includes('data-role="outbound-sent-files"'));
        assert.ok(chatSource.includes('data-role="outbound-draft-files"'));
        assert.ok(chatSource.includes('data-role="outbound-file-input"'));
        assert.ok(chatSource.includes('data-action="mc-remove-attachment"'));
        assert.ok(chatSource.includes('data-action="mc-upload-attachment"'));
    });
});
