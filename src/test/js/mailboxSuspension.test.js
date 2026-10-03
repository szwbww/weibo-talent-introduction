"use strict";

// 02 挂起交互功能测试（I-1..I-7 / S-1..S-4）：
// 复用行为测试的最小真实 DOM（HTML 解析 + 事件冒泡）驱动真实 mailbox-chat.js，
// 断言 Tab 默认进入与计数、行内挂起原因、原位处理确认、完成提示行与异步隔离。

const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const ROOT = path.join(__dirname, "..", "..", "main", "resources", "static");
const chatSource = fs.readFileSync(path.join(ROOT, "mailbox-chat.js"), "utf-8");
const appSource = fs.readFileSync(path.join(ROOT, "app.js"), "utf-8");

function extractFn(name, source) {
    const regex = new RegExp("(?:async\\s+)?function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}");
    const match = (source || appSource).match(regex);
    if (!match) throw new Error("Could not find " + name + " in source");
    return match[0];
}

// ════════════════════════════════════════════════════════════════════════
// 以下 Mini DOM / 沙箱代码与 mailboxChatBehavior.test.js 同源（本片功能测试专用副本）
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

    get nextSibling() {
        if (!this.parentNode) return null;
        const idx = this.parentNode.childNodes.indexOf(this);
        if (idx === -1) return null;
        return this.parentNode.childNodes[idx + 1] || null;
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

// fast-p 2026-10-02 c3：所在地目录桩（与随包目录同结构；顺序即资源顺序）。
const CONTACT_TIMING_CATALOG = {
    sourceVersion: "2026c",
    sourceUrl: "https://data.iana.org/time-zones/tzdb/zone.tab",
    defaultPolicy: "preview-28-overrides-otherwise-first-zone-tab-row",
    countries: [
        { code: "BR", labelZh: "巴西", defaultZoneId: "America/Sao_Paulo", zones: [
            { id: "America/Sao_Paulo", labelZh: "巴西 · 圣保罗" },
            { id: "America/Manaus", labelZh: "巴西 · 马瑙斯" }
        ] },
        { code: "CN", labelZh: "中国", defaultZoneId: "Asia/Shanghai", zones: [
            { id: "Asia/Shanghai", labelZh: "Asia/Shanghai" },
            { id: "Asia/Urumqi", labelZh: "中国 · 乌鲁木齐" }
        ] },
        { code: "IN", labelZh: "印度", defaultZoneId: "Asia/Kolkata", zones: [
            { id: "Asia/Kolkata", labelZh: "印度 · 加尔各答" }
        ] },
        { code: "US", labelZh: "美国", defaultZoneId: "America/New_York", zones: [
            { id: "America/New_York", labelZh: "美国东部 · 纽约" },
            { id: "America/Los_Angeles", labelZh: "美国 · 洛杉矶" }
        ] }
    ]
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
    // fast-p c3：所在地配置的服务端桩状态（PUT 写入，GET/timing 读取）。
    const contactLocations = Object.assign({}, opts.contactLocations || {});
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
        unmatchedMounts: [],
        unmatchedReleases: 0,
        unmatchedUnmounts: []
    };
    const timers = [];

    const defaultRoute = function defaultRoute(url, method, body) {
        if (url.startsWith("/api/mail/mailbox/conversations?")) {
            return Promise.resolve(opts.conversations || { items: [], total: 0 });
        }
        if (url === "/api/auth/me") {
            return Promise.resolve(opts.authMe !== undefined
                ? opts.authMe
                : { authenticated: true, username: "admin", mustChangePassword: false });
        }
        if (/^\/api\/mail\/mailbox\/conversations\/\d+\/suspension$/.test(url)) {
            const id = Number(url.split("/")[5]);
            if (method === "GET") {
                if (opts.suspensionError) return Promise.reject(new Error(opts.suspensionError));
                if (typeof opts.suspension === "function") return Promise.resolve(opts.suspension(id));
                if (opts.suspension !== undefined) return Promise.resolve(opts.suspension);
                return Promise.resolve({ contactId: id, suspended: false, suspendReason: null, suspensionPendingCount: 0, followed: false });
            }
            if (method === "PUT") {
                if (opts.suspendError) return Promise.reject(new Error(opts.suspendError));
                const parsed = body ? JSON.parse(body) : {};
                const current = (typeof opts.suspension === "function" ? opts.suspension(id) : opts.suspension) || {};
                const next = {
                    contactId: id,
                    suspended: true,
                    suspendReason: parsed.reason == null ? null : String(parsed.reason),
                    suspensionPendingCount: Number(current.suspensionPendingCount) || 0,
                    followed: current.followed === true
                };
                opts.suspension = next;
                return Promise.resolve(next);
            }
            if (method === "DELETE") {
                if (opts.resumeError) return Promise.reject(new Error(opts.resumeError));
                const current = (typeof opts.suspension === "function" ? opts.suspension(id) : opts.suspension) || {};
                const next = {
                    contactId: id,
                    suspended: false,
                    suspendReason: null,
                    suspensionPendingCount: Number(current.suspensionPendingCount) || 0,
                    followed: current.followed === true
                };
                opts.suspension = next;
                return Promise.resolve(next);
            }
        }
        if (url.startsWith("/api/mail/unmatched-inbound?")) {
            if (opts.unmatchedError) return Promise.reject(new Error(opts.unmatchedError));
            return Promise.resolve(opts.unmatched || { records: [], totalCount: 0, manualReviewTotal: 0, countsByReasonType: {} });
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
            const id = Number(url.split("/")[4]);
            return Promise.resolve({ id, processStatus: "PROCESSED", resolvedBy: "admin" });
        }
        // fast-p 01：引用邮件模板只读链路（列表 + preview-draft）。竞态用例用 opts.route 自行控制。
        if (url === "/api/compose-templates/preview-draft") {
            if (opts.composePreviewError) return Promise.reject(new Error(opts.composePreviewError));
            return Promise.resolve(opts.composePreview === undefined ? null : opts.composePreview);
        }
        if (url === "/api/compose-templates") {
            if (opts.composeTemplatesError) return Promise.reject(new Error(opts.composeTemplatesError));
            return Promise.resolve(opts.composeTemplates || []);
        }
        if (/\/api\/mail\/mailbox\/conversations\/\d+\/follow/.test(url)) {
            return Promise.resolve({ followed: opts.followResult !== false });
        }
        // fast-p c3：所在地目录 / 配置 / 推荐时间（只读消费 + 唯一写路径 PUT /{contactId}）。
        if (url === "/api/mail/contact-locations/countries") {
            if (opts.contactCatalogError) return Promise.reject(new Error(opts.contactCatalogError));
            return Promise.resolve(opts.contactCatalog || CONTACT_TIMING_CATALOG);
        }
        if (/^\/api\/mail\/contact-locations\/\d+\/timing$/.test(url)) {
            const id = Number(url.split("/")[4]);
            if (opts.contactTimingError) return Promise.reject(new Error(opts.contactTimingError));
            if (typeof opts.contactTiming === "function") return Promise.resolve(opts.contactTiming(id, contactLocationView(id)));
            if (opts.contactTiming !== undefined) return Promise.resolve(opts.contactTiming);
            return Promise.resolve({ location: contactLocationView(id), recommendation: null });
        }
        if (/^\/api\/mail\/contact-locations\/\d+$/.test(url)) {
            const id = Number(url.split("/")[4]);
            if (method === "PUT") {
                if (opts.contactLocationSaveError) return Promise.reject(new Error(opts.contactLocationSaveError));
                const parsed = body ? JSON.parse(body) : {};
                contactLocations[id] = {
                    countryCode: parsed.countryCode,
                    zoneId: parsed.zoneId == null ? null : String(parsed.zoneId)
                };
                return Promise.resolve(contactLocationView(id));
            }
            if (opts.contactLocationError) return Promise.reject(new Error(opts.contactLocationError));
            if (typeof opts.contactLocation === "function") return Promise.resolve(opts.contactLocation(id));
            if (opts.contactLocation !== undefined) return Promise.resolve(opts.contactLocation);
            return Promise.resolve(contactLocationView(id));
        }
        const failedEndpoint = opts.failEndpoints ? Object.keys(opts.failEndpoints).find((key) => url.includes(key)) : null;
        if (failedEndpoint) return Promise.reject(new Error(opts.failEndpoints[failedEndpoint]));
        return Promise.resolve({});
    };

    function catalogCountry(code) {
        return CONTACT_TIMING_CATALOG.countries.find((country) => country.code === String(code)) || null;
    }

    /** 与后端 ContactLocationView 同形：未配置 configured=false 且 zone 三字段为 null。 */
    function contactLocationView(contactId) {
        const id = Number(contactId);
        const saved = contactLocations[id] || null;
        if (!saved) {
            return {
                contactId: id, configured: false, countryCode: null, countryLabel: null,
                zoneId: null, effectiveZoneId: null, zoneLabel: null, usingDefaultZone: false
            };
        }
        const country = catalogCountry(saved.countryCode);
        const zoneId = saved.zoneId || (country ? country.defaultZoneId : null);
        const entry = country ? country.zones.find((zone) => zone.id === zoneId) : null;
        return {
            contactId: id,
            configured: true,
            countryCode: saved.countryCode,
            countryLabel: country ? country.labelZh : saved.countryCode,
            zoneId: saved.zoneId == null ? null : saved.zoneId,
            effectiveZoneId: zoneId,
            zoneLabel: entry ? entry.labelZh : zoneId,
            usingDefaultZone: !saved.zoneId
        };
    }

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
            return Promise.resolve(true);
        },
        mcHostSendConversationRichReply: (contactId, body) => {
            calls.sendConversation.push({ contactId: Number(contactId), body });
            if (opts.sendConversationResult === false) return Promise.resolve(false);
            if (opts.sendConversationError) return Promise.reject(new Error(opts.sendConversationError));
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
        // 待匹配详情宿主适配（app.js 真实实现见文件末尾 lease 测试）：
        // 这里只记录聊天侧调用协议并把面板节点挂进宿主，用于断言 mount/release 边界。
        mcHostMountUnmatchedDetail: (hostEl, processingId) => {
            calls.unmatchedMounts.push({ hostEl, id: Number(processingId) });
            const panel = opts.unmatchedPanel || null;
            if (panel && hostEl && typeof hostEl.appendChild === "function") hostEl.appendChild(panel);
            if (opts.unmatchedMountError) return Promise.reject(new Error(opts.unmatchedMountError));
            return Promise.resolve();
        },
        mcHostReleaseUnmatchedDetail: () => {
            calls.unmatchedReleases += 1;
            const panel = opts.unmatchedPanel || null;
            if (panel && panel.parentNode) {
                calls.unmatchedUnmounts.push(panel);
                const home = opts.unmatchedPanelHome || panel.ownerDocument.body;
                home.appendChild(panel);
            }
            return undefined;
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

    const mediaListeners = new Set();
    const media = { matches: !!opts.mobile,
        addEventListener: (type, fn) => mediaListeners.add(fn),
        removeEventListener: (type, fn) => mediaListeners.delete(fn) };
    const frames = [];
    sandbox.matchMedia = () => media;
    sandbox.requestAnimationFrame = (fn) => { frames.push(fn); return frames.length; };
    vm.createContext(sandbox);
    vm.runInContext(chatSource, sandbox, { filename: "mailbox-chat.js" });
    return {
        sandbox,
        calls,
        timers,
        contactLocations,
        mediaListeners,
        resize: (mobile) => { media.matches = mobile; mediaListeners.forEach((fn) => fn({ matches: mobile })); },
        runFrames: () => { while (frames.length) frames.shift()(); },
        runTimers: () => { while (timers.length) { const fn = timers.shift(); fn(); } }
    };
}

// ════════════════════════════════════════════════════════════════════════
// 02 专用夹具与辅助
// ════════════════════════════════════════════════════════════════════════

function suspendExpert(id, extra) {
    return Object.assign({
        contactId: id,
        name: "专家" + id,
        email: `e${id}@example.edu`,
        orcid: `0000-000${id}`,
        accountCodes: ["acc1"],
        followed: false,
        receivedCount: 2,
        sentCount: 1,
        failedCount: 0,
        pendingCount: 2,
        waitingReply: true,
        expertTags: [],
        latestMessage: { source: "INBOUND_PROCESSING", id: 101, direction: "INBOUND", subject: "Q1", time: "2026-10-02T03:00:00", sendStatus: null },
        latestInbound: { processingId: 101, accountCode: "acc1", messageId: "m101", receivedAt: "2026-10-02T03:00:00" },
        materialCount: 3,
        suspended: false,
        suspendReason: null,
        suspensionPendingCount: 2
    }, extra || {});
}

function inboundMsg(id, contactId, processStatus, extra) {
    return Object.assign({
        source: "INBOUND_PROCESSING",
        id,
        contactId,
        direction: "INBOUND",
        accountCode: "acc1",
        subject: `Q${id}`,
        body: `body ${id}`,
        cleanedBody: `cleaned ${id}`,
        eventAt: "2026-10-02T03:00:00",
        sendStatus: null,
        processStatus,
        attachmentCount: 0,
        firstAttachmentNames: [],
        messageId: `m${id}`,
        inReplyTo: null,
        tags: []
    }, extra || {});
}

function suspendMessages(items) {
    return { items: items || [inboundMsg(101, 1, "MANUAL_REVIEW"), inboundMsg(102, 1, "MANUAL_REVIEW")], nextBefore: null, hasMore: false };
}

function mountChat(options, mountOptions, dom) {
    const ctx = createChatSandbox(options || {});
    const target = dom || createDom();
    ctx.doc = target.doc;
    ctx.host = target.host;
    ctx.sandbox.document = target.doc;
    ctx.sandbox.MailboxChat.mount(target.host, mountOptions || { filters: {} });
    return ctx;
}

async function bootChat(serverOverrides, mountOptions, dom) {
    const ctx = mountChat(serverOverrides, mountOptions, dom);
    await flush();
    return ctx;
}

function click(el) {
    el.dispatchEvent(new MiniEvent("click", { bubbles: true }));
}

function elementChildren(el) {
    return (el.childNodes || []).filter((node) => node.nodeType === 1);
}

function inputEvent(el) {
    el.dispatchEvent(new MiniEvent("input", { bubbles: true }));
}

function keyEvent(el, key) {
    const event = new MiniEvent("keydown", { bubbles: true });
    event.key = key;
    el.dispatchEvent(event);
}

function queryOf(url) {
    return new URLSearchParams(url.split("?")[1] || "");
}

function conversationsRequests(ctx) {
    return ctx.calls.api.filter((entry) => entry.url.startsWith("/api/mail/mailbox/conversations?")
        && queryOf(entry.url).get("size") !== "1");
}

function countRequests(ctx) {
    return ctx.calls.api.filter((entry) => entry.url.startsWith("/api/mail/mailbox/conversations?")
        && queryOf(entry.url).get("size") === "1");
}

function lastListRequest(ctx) {
    const list = conversationsRequests(ctx);
    return list.length ? list[list.length - 1] : null;
}

function chipButton(ctx, key) {
    return ctx.host.querySelectorAll(".mc-filter").find((chip) => chip.dataset.chip === key) || null;
}

function reasonForm(ctx) {
    return ctx.host.querySelector(".mailbox-suspend-inline-reason");
}

function openExpert(ctx, contactId) {
    const person = ctx.host.querySelectorAll(".mc-person").find((p) => String(p.dataset.contactId) === String(contactId));
    click(person.querySelector(".mc-person-main"));
}

describe("02 · I-1 默认 Tab 探测与计数", () => {
    it("普通首次进入：pending total>0 保留待处理，只发一次列表请求", async () => {
        const ctx = await bootChat({ conversations: { items: [suspendExpert(1)], total: 1 } });
        assert.strictEqual(chipButton(ctx, "pending").getAttribute("aria-pressed"), "true");
        const list = conversationsRequests(ctx);
        assert.strictEqual(list.length, 1, "保留待处理不再二次查询");
        assert.strictEqual(queryOf(list[0].url).get("pendingOnly"), "true");
    });

    it("pending total=0 且无 focus：这一次切到关注并重查", async () => {
        const ctx = await bootChat({ conversations: { items: [], total: 0 } });
        assert.strictEqual(chipButton(ctx, "followed").getAttribute("aria-pressed"), "true");
        const list = conversationsRequests(ctx);
        assert.ok(list.length >= 2, "0 结果后重查关注");
        assert.strictEqual(queryOf(list[list.length - 1].url).get("followed"), "true");
    });

    it("focus 深链接不被默认探测覆盖", async () => {
        const ctx = await bootChat(
            { conversations: { items: [suspendExpert(1)], total: 1 }, messages: suspendMessages(), contact: { contact: { id: 1 } } },
            { filters: {}, focus: { contactId: 1, email: "e1@example.edu" } }
        );
        const first = conversationsRequests(ctx)[0];
        assert.strictEqual(queryOf(first.url).get("pendingOnly"), null, "focus mount 不按默认暂定 pending 查询");
        assert.ok(ctx.host.querySelector('[data-message-key="INBOUND_PROCESSING:101"]'), "定位到目标专家的会话");
    });

    it("Tab 计数从列表 total 取，失败隐藏不显示假 0", async () => {
        const ctx = await bootChat({
            conversations: { items: [suspendExpert(1)], total: 7 }
        });
        const pendingSpan = chipButton(ctx, "pending").querySelector(".mailbox-suspend-count");
        const suspendedSpan = chipButton(ctx, "suspended").querySelector(".mailbox-suspend-count");
        assert.strictEqual(pendingSpan.textContent, "7");
        assert.strictEqual(pendingSpan.hidden, false);
        assert.strictEqual(suspendedSpan.textContent, "7");
        assert.strictEqual(suspendedSpan.hidden, false);
    });

    it("计数请求失败：对应 span 隐藏且无 0", async () => {
        const ctx = await bootChat({
            conversations: { items: [suspendExpert(1)], total: 7 },
            route: (url, method, body, entry, next) => {
                if (url.startsWith("/api/mail/mailbox/conversations?") && queryOf(url).get("suspendedOnly") === "true") {
                    return Promise.reject(new Error("boom"));
                }
                return next(url, method, body);
            }
        });
        const suspendedSpan = chipButton(ctx, "suspended").querySelector(".mailbox-suspend-count");
        assert.strictEqual(suspendedSpan.hidden, true);
        assert.strictEqual(suspendedSpan.textContent, "");
    });
});

describe("02 · S-2 卡片/详情挂起入口", () => {
    it("未挂起且 N>0：footer 状态 N 条待处理、data-pending=true、按钮为挂起", async () => {
        const ctx = await bootChat({ conversations: { items: [suspendExpert(1)], total: 1 } });
        const person = ctx.host.querySelector('.mc-person[data-contact-id="1"]');
        const footer = person.querySelector(".mailbox-suspend-card-footer");
        assert.ok(footer, "有 footer");
        assert.strictEqual(footer.querySelector(".mailbox-suspend-state").getAttribute("data-pending"), "true");
        assert.match(footer.querySelector(".mailbox-suspend-state").textContent, /2 条待处理/);
        assert.strictEqual(footer.querySelector('[data-action="mc-suspension"]').textContent, "挂起");
    });

    it("未挂起且 N=0：不显示 footer", async () => {
        const ctx = await bootChat({ conversations: { items: [suspendExpert(1, { suspensionPendingCount: 0 })], total: 1 } });
        const person = ctx.host.querySelector('.mc-person[data-contact-id="1"]');
        assert.ok(!person.querySelector(".mailbox-suspend-card-footer"));
    });

    it("已挂起且 N>0：中性灰状态、data-pending=false、按钮为取消挂起", async () => {
        const ctx = await bootChat({
            conversations: { items: [suspendExpert(1, { suspended: true, suspendReason: "等待材料", suspensionPendingCount: 3 })], total: 1 }
        });
        const footer = ctx.host.querySelector('.mc-person[data-contact-id="1"] .mailbox-suspend-card-footer');
        assert.strictEqual(footer.querySelector(".mailbox-suspend-state").getAttribute("data-pending"), "false");
        assert.match(footer.querySelector(".mailbox-suspend-state").textContent, /已挂起 · 3 条待处理/);
        assert.strictEqual(footer.querySelector('[data-action="mc-suspension"]').textContent, "取消挂起");
    });

    it("详情：挂起按钮在 mc-actions 最前，banner 渲染原因与引导", async () => {
        const ctx = await bootChat({
            conversations: { items: [suspendExpert(1, { suspended: true, suspendReason: "等待材料\n下周跟进", suspensionPendingCount: 0 })], total: 1 },
            suspension: { contactId: 1, suspended: true, suspendReason: "等待材料\n下周跟进", suspensionPendingCount: 0, followed: false },
            messages: suspendMessages([inboundMsg(90, 1, "PROCESSED")]),
            contact: { contact: { id: 1 } }
        });
        openExpert(ctx, 1);
        await flush();
        const actions = ctx.host.querySelector(".mc-actions");
        assert.strictEqual(elementChildren(actions)[0].getAttribute("data-action"), "mc-suspension");
        const banner = ctx.host.querySelector(".mailbox-suspend-banner");
        assert.ok(banner, "banner 显示");
        assert.match(banner.textContent, /消息已全部处理 · 等待结束挂起/);
        assert.match(banner.textContent, /挂起原因：等待材料/);
        assert.ok(!banner.querySelector("button"), "banner 内无按钮");
    });
});

describe("02 · S-3 行内挂起原因", () => {
    async function bootForSuspend(overrides) {
        const ctx = await bootChat(Object.assign({
            conversations: { items: [suspendExpert(1)], total: 1 },
            contact: { contact: { id: 1 } }
        }, overrides || {}));
        return ctx;
    }

    it("点击挂起先 GET，确认后 PUT reason，成功后关闭表单", async () => {
        const ctx = await bootForSuspend();
        click(ctx.host.querySelector('.mc-person[data-contact-id="1"] [data-action="mc-suspension"]'));
        await flush();
        const form = reasonForm(ctx);
        assert.ok(form, "插入原因表单");
        const siblings = elementChildren(form.parentNode);
        const idx = siblings.indexOf(form);
        assert.ok(idx > 0 && siblings[idx - 1].classList.contains("mc-person"), "位于卡片之后");
        const textarea = form.querySelector("textarea");
        assert.strictEqual(textarea.getAttribute("maxlength"), "500");
        textarea.value = "  等待材料  ";
        inputEvent(textarea);
        assert.strictEqual(form.querySelector(".mailbox-suspend-reason-count").textContent, "8 / 500");
        click(form.querySelector('[data-action="mc-suspension-reason-confirm"]'));
        await flush();
        const put = ctx.calls.api.find((e) => e.method === "PUT" && /\/suspension$/.test(e.url));
        assert.ok(put, "发 PUT");
        assert.strictEqual(put.body, JSON.stringify({ reason: "等待材料" }), "trim 后提交");
        assert.ok(!reasonForm(ctx), "成功后关闭表单");
    });

    it("空白原因合法，PUT reason:null；PUT 失败保留输入与表单并显示错误", async () => {
        const ctx = await bootForSuspend({ suspendError: "挂起冲突" });
        click(ctx.host.querySelector('.mc-person[data-contact-id="1"] [data-action="mc-suspension"]'));
        await flush();
        const form = reasonForm(ctx);
        const textarea = form.querySelector("textarea");
        textarea.value = "  ";
        inputEvent(textarea);
        click(form.querySelector('[data-action="mc-suspension-reason-confirm"]'));
        await flush();
        assert.ok(reasonForm(ctx), "失败保留表单");
        assert.strictEqual(reasonForm(ctx).querySelector("textarea").value, "  ", "保留原输入");
        const error = reasonForm(ctx).querySelector(".mailbox-suspend-error");
        assert.strictEqual(error.hidden, false);
        assert.match(error.textContent, /挂起冲突/);
    });

    it("取消/Escape 移除表单并恢复触发按钮焦点；同一实例只保留一份表单", async () => {
        const ctx = await bootForSuspend();
        const trigger = ctx.host.querySelector('.mc-person[data-contact-id="1"] [data-action="mc-suspension"]');
        click(trigger);
        await flush();
        assert.ok(reasonForm(ctx));
        // 再点一次（详情/另一入口）不应产生第二份
        click(ctx.host.querySelector('.mc-person[data-contact-id="1"] [data-action="mc-suspension"]'));
        await flush();
        assert.strictEqual(ctx.host.querySelectorAll(".mailbox-suspend-inline-reason").length, 1);
        keyEvent(reasonForm(ctx).querySelector("textarea"), "Escape");
        assert.ok(!reasonForm(ctx), "Escape 关闭");
        click(trigger);
        await flush();
        click(reasonForm(ctx).querySelector('[data-action="mc-suspension-reason-cancel"]'));
        assert.ok(!reasonForm(ctx), "取消关闭");
    });

    it("原因以文本转义渲染（XSS 不执行）且换行保留", async () => {
        const ctx = await bootForSuspend({
            suspension: { contactId: 1, suspended: true, suspendReason: "<img src=x onerror=alert(1)>", suspensionPendingCount: 2, followed: false },
            messages: suspendMessages([inboundMsg(101, 1, "MANUAL_REVIEW")])
        });
        openExpert(ctx, 1);
        await flush();
        const banner = ctx.host.querySelector(".mailbox-suspend-banner");
        assert.ok(banner);
        assert.ok(!banner.querySelector("img"));
        assert.match(banner.querySelector(".mailbox-suspend-banner-content").textContent, /<img src=x onerror=alert\(1\)>/);
    });
});

describe("02 · I-4 取消/结束挂起后的归类", () => {
    async function bootSuspended(state, overrides) {
        const ctx = await bootChat(Object.assign({
            conversations: { items: [suspendExpert(1, { suspended: true, suspPend: true })], total: 1 },
            suspension: Object.assign({ contactId: 1, suspended: true, suspendReason: null, suspensionPendingCount: 0, followed: false }, state),
            messages: suspendMessages([inboundMsg(90, 1, "PROCESSED")]),
            contact: { contact: { id: 1 } }
        }, overrides || {}));
        return ctx;
    }

    it("回包 count>0 → 待处理；0+followed → 关注；0+未关注 → 已回复", async () => {
        const cases = [
            { state: { suspensionPendingCount: 1, followed: false }, expect: "pending" },
            { state: { suspensionPendingCount: 0, followed: true }, expect: "followed" },
            { state: { suspensionPendingCount: 0, followed: false }, expect: "replied" }
        ];
        for (const c of cases) {
            const ctx = await bootSuspended(c.state);
            click(ctx.host.querySelector('.mc-person[data-contact-id="1"] [data-action="mc-suspension"]'));
            await flush();
            const del = ctx.calls.api.find((e) => e.method === "DELETE" && /\/suspension$/.test(e.url));
            assert.ok(del, "取消挂起直接 DELETE");
            assert.strictEqual(chipButton(ctx, c.expect).getAttribute("aria-pressed"), "true", `归类 ${c.expect}`);
        }
    });

    it("DELETE 失败：不移走卡片并原位报错", async () => {
        const ctx = await bootSuspended({ suspensionPendingCount: 2 }, { resumeError: "取消失败" });
        const btn = ctx.host.querySelector('.mc-person[data-contact-id="1"] [data-action="mc-suspension"]');
        click(btn);
        await flush();
        assert.ok(ctx.calls.api.some((e) => e.method === "DELETE" && /\/suspension$/.test(e.url)));
        const error = ctx.host.querySelector('.mailbox-suspend-error[data-role="suspension-action"]');
        assert.ok(error && error.hidden === false);
        assert.match(error.textContent, /取消失败/);
        assert.ok(ctx.host.querySelector('.mc-person[data-contact-id="1"]'), "卡片未移走");
    });
});

describe("02 · I-7 原位处理确认", () => {
    async function bootPending(overrides) {
        return bootChat(Object.assign({
            conversations: { items: [suspendExpert(1)], total: 1 },
            messages: suspendMessages([inboundMsg(101, 1, "MANUAL_REVIEW")]),
            contact: { contact: { id: 1 } }
        }, overrides || {}));
    }

    function article(ctx, key) {
        return ctx.host.querySelector(`[data-message-key="${key}"]`);
    }

    it("第一次点击只进入原位确认，无 POST；取消无 POST", async () => {
        const ctx = await bootPending();
        click(ctx.host.querySelector('.mc-person[data-contact-id="1"] .mc-person-main'));
        await flush();
        const pendingBtn = article(ctx, "INBOUND_PROCESSING:101").querySelector('[data-action="mc-mark-resolved"]');
        assert.strictEqual(pendingBtn.textContent, "待处理");
        assert.ok(!pendingBtn.textContent.includes("✓"), "无 ✓ 图标");
        click(pendingBtn);
        await flush();
        assert.ok(!ctx.calls.api.some((e) => /mark-resolved/.test(e.url)), "确认前不发 POST");
        assert.ok(article(ctx, "INBOUND_PROCESSING:101").querySelector('[data-action="mc-process-confirm"]'));
        click(article(ctx, "INBOUND_PROCESSING:101").querySelector('[data-action="mc-process-cancel"]'));
        await flush();
        assert.ok(!ctx.calls.api.some((e) => /mark-resolved/.test(e.url)), "取消不发 POST");
        assert.ok(article(ctx, "INBOUND_PROCESSING:101").querySelector('[data-action="mc-mark-resolved"]'));
    });

    it("确认恰 1 次 POST（body note:null），成功显示服务端 resolvedBy", async () => {
        let resolved = false;
        const ctx = await bootPending({
            route: (url, method, body, entry, next) => {
                if (/\/messages/.test(url)) {
                    const items = [inboundMsg(101, 1, resolved ? "PROCESSED" : "MANUAL_REVIEW")];
                    return Promise.resolve({ items, nextBefore: null, hasMore: false });
                }
                if (/mark-resolved/.test(url)) {
                    resolved = true;
                    return Promise.resolve({ id: 101, processStatus: "PROCESSED", resolvedBy: "admin" });
                }
                return next(url, method, body);
            }
        });
        click(ctx.host.querySelector('.mc-person[data-contact-id="1"] .mc-person-main'));
        await flush();
        click(article(ctx, "INBOUND_PROCESSING:101").querySelector('[data-action="mc-mark-resolved"]'));
        await flush();
        click(article(ctx, "INBOUND_PROCESSING:101").querySelector('[data-action="mc-process-confirm"]'));
        await flush();
        const posts = ctx.calls.api.filter((e) => /mark-resolved/.test(e.url));
        assert.strictEqual(posts.length, 1, "恰一次 POST");
        assert.strictEqual(posts[0].body, JSON.stringify({ note: null }));
        const label = article(ctx, "INBOUND_PROCESSING:101").querySelector(".mailbox-suspend-processed-label");
        assert.ok(label, "已处理标签");
        assert.match(label.textContent, /已处理 · admin/, "显示服务端账号");
    });

    it("失败保留确认与行内错误，可重试", async () => {
        let fail = true;
        const ctx = await bootPending({
            route: (url, method, body, entry, next) => {
                if (/\/messages/.test(url)) {
                    const items = [inboundMsg(101, 1, fail ? "MANUAL_REVIEW" : "PROCESSED")];
                    return Promise.resolve({ items, nextBefore: null, hasMore: false });
                }
                if (/mark-resolved/.test(url)) {
                    if (fail) return Promise.reject(new Error("标记失败"));
                    return Promise.resolve({ id: 101, processStatus: "PROCESSED", resolvedBy: "admin" });
                }
                return next(url, method, body);
            }
        });
        click(ctx.host.querySelector('.mc-person[data-contact-id="1"] .mc-person-main'));
        await flush();
        click(article(ctx, "INBOUND_PROCESSING:101").querySelector('[data-action="mc-mark-resolved"]'));
        await flush();
        click(article(ctx, "INBOUND_PROCESSING:101").querySelector('[data-action="mc-process-confirm"]'));
        await flush();
        const art = article(ctx, "INBOUND_PROCESSING:101");
        assert.ok(art.querySelector('[data-action="mc-process-confirm"]'), "失败保留确认");
        assert.match(art.querySelector(".mc-inline-error").textContent, /标记失败/);
        fail = false;
        click(art.querySelector('[data-action="mc-process-confirm"]'));
        await flush();
        assert.ok(article(ctx, "INBOUND_PROCESSING:101").querySelector(".mailbox-suspend-processed-label"));
    });

    it("回包 id 不一致按失败处理，不假装成功", async () => {
        const ctx = await bootPending({
            route: (url, method, body, entry, next) => {
                if (/mark-resolved/.test(url)) return Promise.resolve({ id: 999, processStatus: "PROCESSED", resolvedBy: "admin" });
                return next(url, method, body);
            }
        });
        click(ctx.host.querySelector('.mc-person[data-contact-id="1"] .mc-person-main'));
        await flush();
        click(article(ctx, "INBOUND_PROCESSING:101").querySelector('[data-action="mc-mark-resolved"]'));
        await flush();
        click(article(ctx, "INBOUND_PROCESSING:101").querySelector('[data-action="mc-process-confirm"]'));
        await flush();
        const art = article(ctx, "INBOUND_PROCESSING:101");
        assert.ok(art.querySelector('[data-action="mc-process-confirm"]'), "仍停留确认态");
        assert.ok(!art.querySelector(".mailbox-suspend-processed-label"));
    });
});

describe("02 · I-3 完成提示行", () => {
    async function bootCompletion(state, messages) {
        return bootChat({
            conversations: { items: [suspendExpert(1, { suspended: true })], total: 1 },
            suspension: Object.assign({ contactId: 1, suspended: true, suspendReason: "等待材料", suspensionPendingCount: 0, followed: false }, state || {}),
            messages: suspendMessages(messages || [inboundMsg(90, 1, "PROCESSED"), inboundMsg(101, 1, "MANUAL_REVIEW")]),
            contact: { contact: { id: 1 } }
        });
    }

    it("suspended 且跨账号计数 0：最后一条 PROCESSED 来信 footer 后出现唯一提示行", async () => {
        const ctx = await bootCompletion();
        click(ctx.host.querySelector('.mc-person[data-contact-id="1"] .mc-person-main'));
        await flush();
        const lines = ctx.host.querySelectorAll(".mailbox-suspend-completion-line");
        assert.strictEqual(lines.length, 1, "唯一提示行");
        const article = ctx.host.querySelector('[data-message-key="INBOUND_PROCESSING:90"]');
        assert.ok(article.querySelector(".mailbox-suspend-completion-line"), "位于 PROCESSED 来信内部");
        assert.ok(!ctx.calls.api.some((e) => e.method === "DELETE"), "未自动结束");
    });

    it("计数>0 时无提示行；无合格锚点也不凭空生成", async () => {
        const ctx = await bootCompletion({ suspensionPendingCount: 3 });
        click(ctx.host.querySelector('.mc-person[data-contact-id="1"] .mc-person-main'));
        await flush();
        assert.strictEqual(ctx.host.querySelectorAll(".mailbox-suspend-completion-line").length, 0);

        const ctx2 = await bootCompletion({ suspensionPendingCount: 0 }, [inboundMsg(101, 1, "MANUAL_REVIEW")]);
        click(ctx2.host.querySelector('.mc-person[data-contact-id="1"] .mc-person-main'));
        await flush();
        assert.strictEqual(ctx2.host.querySelectorAll(".mailbox-suspend-completion-line").length, 0, "无 PROCESSED 锚点不生成");
        assert.ok(ctx2.host.querySelector(".mailbox-suspend-banner"), "仍保留 banner 取消入口");
    });

    it("继续挂起：零 DELETE，提示行变已继续挂起且保留结束按钮", async () => {
        const ctx = await bootCompletion();
        click(ctx.host.querySelector('.mc-person[data-contact-id="1"] .mc-person-main'));
        await flush();
        click(ctx.host.querySelector('[data-action="mc-suspension-keep"]'));
        await flush();
        assert.ok(!ctx.calls.api.some((e) => e.method === "DELETE"), "继续挂起不写数据");
        const line = ctx.host.querySelector(".mailbox-suspend-completion-line");
        assert.match(line.textContent, /已继续挂起/);
        assert.ok(line.querySelector('[data-action="mc-suspension-end"]'), "保留结束按钮");
        assert.ok(!line.querySelector('[data-action="mc-suspension-keep"]'));
    });

    it("结束挂起：恰 1 次 DELETE 后按回包归类", async () => {
        const ctx = await bootCompletion({ suspensionPendingCount: 0, followed: true });
        click(ctx.host.querySelector('.mc-person[data-contact-id="1"] .mc-person-main'));
        await flush();
        click(ctx.host.querySelector('[data-action="mc-suspension-end"]'));
        await flush();
        const dels = ctx.calls.api.filter((e) => e.method === "DELETE" && /\/suspension$/.test(e.url));
        assert.strictEqual(dels.length, 1);
        assert.strictEqual(chipButton(ctx, "followed").getAttribute("aria-pressed"), "true");
    });
});

describe("02 · I-5/I-6 守卫与身份", () => {
    it("身份未就绪：挂起/处理按钮 disabled，点击提示读取失败", async () => {
        const ctx = await bootChat({
            conversations: { items: [suspendExpert(1)], total: 1 },
            authMe: { authenticated: false, username: null, mustChangePassword: false },
            messages: suspendMessages([inboundMsg(101, 1, "MANUAL_REVIEW")]),
            contact: { contact: { id: 1 } }
        });
        const cardBtn = ctx.host.querySelector('.mc-person[data-contact-id="1"] [data-action="mc-suspension"]');
        assert.strictEqual(cardBtn.disabled, true);
        click(cardBtn);
        await flush();
        assert.ok(!ctx.calls.api.some((e) => /\/1\/suspension$/.test(e.url) && e.method !== "GET"), "未就绪不发写请求");
        assert.ok(ctx.calls.status.some((s) => s.message.includes("登录状态读取失败")));
    });

    it("迟到的挂起状态回包不覆盖新专家（convEpoch 守卫）", async () => {
        let release = null;
        const ctx = await bootChat({
            conversations: { items: [suspendExpert(1), suspendExpert(2)], total: 2 },
            messages: suspendMessages([inboundMsg(101, 1, "MANUAL_REVIEW")]),
            contact: { contact: { id: 1 } },
            route: (url, method, body, entry, next) => {
                if (/^\/api\/mail\/mailbox\/conversations\/1\/suspension$/.test(url)) {
                    return new Promise((resolve) => { release = () => resolve({ contactId: 1, suspended: true, suspendReason: "A", suspensionPendingCount: 0, followed: false }); });
                }
                return next(url, method, body);
            }
        });
        click(ctx.host.querySelector('.mc-person[data-contact-id="1"] .mc-person-main'));
        await flush();
        // 立即切到专家 2
        click(ctx.host.querySelector('.mc-person[data-contact-id="2"] .mc-person-main'));
        await flush();
        if (release) release();
        await flush();
        assert.ok(!ctx.host.querySelector(".mailbox-suspend-banner"), "专家 1 的迟到状态不得挂到专家 2");
    });
});

describe("02 · R-1 迟到挂起回包绑定详情上下文（V-1）", () => {
    it("移动端返回列表：迟到的挂起状态回包不写入隐藏详情（V-1）", async () => {
        let release = null;
        const ctx = await bootChat({
            mobile: true,
            conversations: { items: [suspendExpert(1)], total: 1 },
            messages: suspendMessages([inboundMsg(101, 1, "MANUAL_REVIEW")]),
            contact: { contact: { id: 1 } },
            route: (url, method, body, entry, next) => {
                if (url === "/api/mail/mailbox/conversations/1/suspension" && method === "GET") {
                    return new Promise((resolve) => {
                        release = () => resolve({ contactId: 1, suspended: true, suspendReason: "迟到A", suspensionPendingCount: 0, followed: false });
                    });
                }
                return next(url, method, body);
            }
        });
        openExpert(ctx, 1);
        await flush();
        click(ctx.host.querySelector('[data-action="mobile-mailbox-back"]'));
        await flush();
        if (release) release();
        await flush();
        assert.ok(!ctx.host.querySelector(".mailbox-suspend-banner"), "迟到的挂起状态不得写入详情");
        const detailButton = ctx.host.querySelector('.mc-actions [data-action="mc-suspension"]');
        assert.ok(!detailButton || detailButton.textContent.trim() !== "取消挂起", "详情按钮不得被迟到回包改写");
        assert.strictEqual(ctx.host.querySelector(".mobile-core-mailbox").getAttribute("data-mobile-pane"), "list", "不得抢回详情");
    });

    it("移动端返回列表：迟到的原因表单 GET 不打开隐藏详情的表单（V-1）", async () => {
        let defer = false;
        let release = null;
        const ctx = await bootChat({
            mobile: true,
            conversations: { items: [suspendExpert(1)], total: 1 },
            suspension: (id) => ({ contactId: id, suspended: false, suspendReason: null, suspensionPendingCount: 2, followed: false }),
            messages: suspendMessages([inboundMsg(101, 1, "MANUAL_REVIEW")]),
            contact: { contact: { id: 1 } },
            route: (url, method, body, entry, next) => {
                if (defer && url === "/api/mail/mailbox/conversations/1/suspension" && method === "GET") {
                    return new Promise((resolve) => {
                        release = () => resolve({ contactId: 1, suspended: false, suspendReason: null, suspensionPendingCount: 2, followed: false });
                    });
                }
                return next(url, method, body);
            }
        });
        openExpert(ctx, 1);
        await flush();
        defer = true;
        click(ctx.host.querySelector('.mc-actions [data-action="mc-suspension"]'));
        await flush();
        click(ctx.host.querySelector('[data-action="mobile-mailbox-back"]'));
        await flush();
        if (release) release();
        await flush();
        assert.ok(!reasonForm(ctx), "迟到的原因 GET 不得为隐藏详情打开表单");
    });

    it("切到专家 B 并打开 B 的表单后：A 的迟到 PUT 不撤下 B 的表单（V-1）", async () => {
        let releasePut = null;
        const ctx = await bootChat({
            conversations: { items: [suspendExpert(1), suspendExpert(2)], total: 2 },
            suspension: (id) => ({ contactId: id, suspended: false, suspendReason: null, suspensionPendingCount: 2, followed: false }),
            messages: suspendMessages([inboundMsg(101, 1, "MANUAL_REVIEW")]),
            contact: { contact: { id: 1 } },
            route: (url, method, body, entry, next) => {
                if (url === "/api/mail/mailbox/conversations/1/suspension" && method === "PUT") {
                    return new Promise((resolve) => {
                        releasePut = () => resolve({ contactId: 1, suspended: true, suspendReason: "A", suspensionPendingCount: 0, followed: false });
                    });
                }
                return next(url, method, body);
            }
        });
        openExpert(ctx, 1);
        await flush();
        click(ctx.host.querySelector('.mc-actions [data-action="mc-suspension"]'));
        await flush();
        const formA = reasonForm(ctx);
        assert.ok(formA, "A 的表单已打开");
        formA.querySelector("textarea").value = "A 原因";
        click(formA.querySelector('[data-action="mc-suspension-reason-confirm"]'));
        await flush();
        openExpert(ctx, 2);
        await flush();
        click(ctx.host.querySelector('.mc-actions [data-action="mc-suspension"]'));
        await flush();
        const formB = reasonForm(ctx);
        assert.ok(formB && String(formB.dataset.contactId) === "2", "B 的表单已打开");
        if (releasePut) releasePut();
        await flush();
        assert.strictEqual(reasonForm(ctx), formB, "A 的迟到 PUT 不得撤下 B 的表单");
        assert.ok(!ctx.calls.status.some((entry) => entry.message.includes("已挂起该会话")), "迟到 PUT 不得报成功");
    });

    it("移动端返回列表：迟到的取消挂起（DELETE）不切换 Tab 不报成功（V-1）", async () => {
        let releaseDel = null;
        const ctx = await bootChat({
            mobile: true,
            conversations: { items: [suspendExpert(1, { suspended: true, suspensionPendingCount: 0 })], total: 1 },
            suspension: { contactId: 1, suspended: true, suspendReason: null, suspensionPendingCount: 0, followed: false },
            messages: suspendMessages([inboundMsg(101, 1, "MANUAL_REVIEW")]),
            contact: { contact: { id: 1 } },
            route: (url, method, body, entry, next) => {
                if (url === "/api/mail/mailbox/conversations/1/suspension" && method === "DELETE") {
                    return new Promise((resolve) => {
                        releaseDel = () => resolve({ contactId: 1, suspended: false, suspendReason: null, suspensionPendingCount: 0, followed: false });
                    });
                }
                return next(url, method, body);
            }
        });
        openExpert(ctx, 1);
        await flush();
        click(ctx.host.querySelector('.mc-actions [data-action="mc-suspension"]'));
        await flush();
        click(ctx.host.querySelector('[data-action="mobile-mailbox-back"]'));
        await flush();
        if (releaseDel) releaseDel();
        await flush();
        assert.strictEqual(chipButton(ctx, "replied").getAttribute("aria-pressed"), "false", "迟到 DELETE 不得切换 Tab");
        assert.ok(!ctx.calls.status.some((entry) => /已(结束|取消)挂起/.test(entry.message)), "迟到 DELETE 不得报成功");
    });

    it("切到专家 B 后：A 的迟到结束挂起（DELETE）不改 B 详情与导航（V-1）", async () => {
        let releaseDel = null;
        const ctx = await bootChat({
            conversations: { items: [suspendExpert(1, { suspended: true, suspensionPendingCount: 0 }), suspendExpert(2)], total: 2 },
            suspension: (id) => (id === 1
                ? { contactId: 1, suspended: true, suspendReason: "等待材料", suspensionPendingCount: 0, followed: false }
                : { contactId: id, suspended: false, suspendReason: null, suspensionPendingCount: 2, followed: false }),
            messages: suspendMessages([inboundMsg(90, 1, "PROCESSED"), inboundMsg(101, 1, "MANUAL_REVIEW")]),
            contact: { contact: { id: 1 } },
            route: (url, method, body, entry, next) => {
                if (url === "/api/mail/mailbox/conversations/1/suspension" && method === "DELETE") {
                    return new Promise((resolve) => {
                        releaseDel = () => resolve({ contactId: 1, suspended: false, suspendReason: null, suspensionPendingCount: 0, followed: false });
                    });
                }
                return next(url, method, body);
            }
        });
        openExpert(ctx, 1);
        await flush();
        const endButton = ctx.host.querySelector('[data-action="mc-suspension-end"]');
        assert.ok(endButton, "完成行已出现");
        click(endButton);
        await flush();
        openExpert(ctx, 2);
        await flush();
        if (releaseDel) releaseDel();
        await flush();
        assert.strictEqual(chipButton(ctx, "replied").getAttribute("aria-pressed"), "false", "迟到 DELETE 不得切换 Tab");
        assert.ok(!ctx.calls.status.some((entry) => entry.message.includes("已结束挂起")), "迟到 DELETE 不得报成功");
        assert.ok(ctx.host.querySelector(".mc-header"), "B 的详情仍在");
    });
});
