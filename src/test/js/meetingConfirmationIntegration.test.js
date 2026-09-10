"use strict";

// fast-p 04 会议确认宿主集成测试（T3/T4/S-3/S-5/I-1..I-7 前端契约）：
// 复用 mailbox-chat 行为测试的最小真实 DOM 树（HTML 解析 + 事件冒泡 +
// querySelector/closest/dataset）与沙箱，把 meeting-confirmation.js 真正加载进
// 同一沙箱（sandbox.MailboxMeeting），用真实 mailbox-chat 实例 + stub API/adapter
// 驱动：trigger 门禁、弹窗选项/搜索/预览、确认填入（块/卡/草稿快照）、编辑更新、
// 手改 stale、移除、QA 采用清除、retarget 迁移、发送锁与 revision 清稿、晚响应
// 不串目标、历史已发送日历卡。无真实 SMTP；meeting 组件缺席时全部旧路径保持。

const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const ROOT = path.join(__dirname, "..", "..", "main", "resources", "static");
const chatSource = fs.readFileSync(path.join(ROOT, "mailbox-chat.js"), "utf-8");
const meetingSource = fs.readFileSync(path.join(ROOT, "meeting-confirmation.js"), "utf-8");
const appSource = fs.readFileSync(path.join(ROOT, "app.js"), "utf-8");

function extractFn(name, source) {
    const regex = new RegExp("(?:async\\s+)?function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}");
    const match = (source || appSource).match(regex);
    if (!match) throw new Error("Could not find " + name + " in source");
    return match[0];
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
        unmounts: [],
        inboundTagModals: [],
        tagMutations: [],
        tagEditorUpdates: [],
        tagEditorLoadings: []
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
    // 历史卡下载宿主 adapter stub
    sandbox.mcHostDownloadCalendar = (relativePath, filename) => {
        calls.downloads = calls.downloads || [];
        calls.downloads.push({ relativePath, filename });
        if (opts.downloadError) return Promise.reject(new Error(opts.downloadError));
        return Promise.resolve();
    };

    vm.createContext(sandbox);
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
// fast-p 04：会议确认宿主集成（挂载真实 mailbox-chat + meeting-confirmation，
// 用 DOM 事件驱动；无真实 SMTP）
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

describe("fast-p 04: 组件门禁与 S-3 人工回复区（trigger/附件卡）", () => {
    it("组件缺席：不渲染 trigger/附件卡，旧按钮顺序与发送路径不变", async () => {
        const ctx = await bootChat({ conversations: { items: [expertA()], total: 1 }, messages: messagesA(), contact: contactA() });
        const a = ctx.host.querySelectorAll(".mc-person")[0];
        click(a.querySelector(".mc-person-main"));
        await flush();
        const tools = ctx.host.querySelectorAll('[data-role="manual-compose"] .mc-editor-tools .button');
        assert.deepStrictEqual(Array.prototype.slice.call(tools).map((b) => b.getAttribute("data-command")),
            ["bold", "italic", "insertUnorderedList", "createLink"], "组件缺席时四按钮原样");
        assert.strictEqual(ctx.host.querySelector('[data-action="mc-open-meeting"]'), null, "无 trigger");
        assert.strictEqual(ctx.host.querySelector('[data-role="meeting-attachment"]'), null, "无附件容器");
        // 发送仍走原 payload（无 meeting 字段）
        const editor = ctx.host.querySelector('[aria-label="人工回复正文"]');
        editor.innerText = "hello";
        inputEvent(editor);
        click(ctx.host.querySelector('[data-action="mc-send-manual"]'));
        await flush();
        const payload = ctx.calls.sendRich[ctx.calls.sendRich.length - 1].body;
        assert.strictEqual(payload.meeting, undefined);
        assert.strictEqual(payload.previewAttachmentSha256, undefined);
    });

    it("组件缺席：历史 calendarAttachment 卡仍由服务端数据渲染并可下载", async () => {
        const withCalendar = messagesA();
        withCalendar.items = withCalendar.items.map((message) => {
            if (message.source === "MAIL_RECORD" && message.id === 88) {
                return Object.assign({}, message, {
                    calendarAttachment: {
                        filename: "meeting-2026-09-06-Expert.ics",
                        byteLength: 512,
                        downloadUrl: "/api/mail/conversations/1/messages/88/calendar-attachment"
                    }
                });
            }
            return message;
        });
        const ctx = await bootChat({ conversations: { items: [expertA()], total: 1 }, messages: withCalendar, contact: contactA() });
        const a = ctx.host.querySelectorAll(".mc-person")[0];
        click(a.querySelector(".mc-person-main"));
        await flush();
        const article = ctx.host.querySelector('[data-message-key="MAIL_RECORD:88"]');
        const sentCard = article.querySelector('[data-role="sent-meeting-attachment"]');
        assert.ok(sentCard, "已发送日历卡必须渲染");
        assert.ok(sentCard.querySelector('[data-state="sent"]'), "SENT 徽标");
        const download = sentCard.querySelector('[data-action="mc-download-sent-meeting"]');
        assert.ok(download.getAttribute("href").includes("/api/mail/conversations/1/messages/88/calendar-attachment"));
        click(download);
        await flush();
        assert.deepStrictEqual(ctx.calls.downloads, [{
            relativePath: "/api/mail/conversations/1/messages/88/calendar-attachment",
            filename: "meeting-2026-09-06-Expert.ics"
        }]);
    });

    it("组件在场：trigger 位于四个富文本按钮之后，附件容器在 editor 与 footer 之间", async () => {
        const ctx = await bootMeetingA();
        const compose = ctx.host.querySelector('[data-role="manual-compose"]');
        const tools = compose.querySelector('.mc-editor-tools').querySelectorAll("button");
        assert.strictEqual(tools.length, 5);
        assert.strictEqual(tools[4].getAttribute("data-action"), "mc-open-meeting");
        const editor = compose.querySelector('[aria-label="人工回复正文"]');
        const container = compose.querySelector('[data-role="meeting-attachment"]');
        const footer = compose.querySelector(".mc-compose-footer");
        assert.ok(container, "附件容器存在");
        const children = Array.prototype.slice.call(compose.childNodes).filter((n) => n.nodeType === 1);
        assert.ok(children.indexOf(editor) < children.indexOf(container), "容器在 editor 之后");
        assert.ok(children.indexOf(container) < children.indexOf(footer), "容器在 footer 之前");
        assert.ok(!container.querySelector(".meeting-file"), "无附件时容器为空（:empty 隐藏）");
    });

    it("dialog 唯一、body 直属；unmount 移除 dialog", async () => {
        const ctx = await bootMeetingA();
        await openMeetingLoaded(ctx);
        assert.strictEqual(ctx.doc.body.querySelectorAll("#meetingDialog").length, 1);
        ctx.sandbox.MailboxChat.unmount(ctx.host);
        assert.strictEqual(meetingDialog(ctx), null, "unmount 必须移除 dialog");
    });
});

describe("fast-p 04: 弹窗加载与表单默认值（options/zones 只读）", () => {
    it("并行拉 options/zones；上下文/默认值正确；只剩时区与时间字段；确认禁用", async () => {
        const ctx = await bootMeetingA();
        await openMeetingLoaded(ctx);
        const optsCalls = ctx.calls.api.filter((e) => /\/meeting-confirmation\/options/.test(e.url));
        const zoneCalls = ctx.calls.api.filter((e) => /\/meeting-confirmation\/time-zones/.test(e.url));
        assert.ok(optsCalls.length >= 1, "options 请求发生");
        assert.ok(zoneCalls.length >= 1, "time-zones 请求发生");
        assert.ok(optsCalls[0].url.includes("contactId=1"));
        assert.ok(optsCalls[0].url.includes("senderAccountCode=acc1"));
        assert.match(meetingField(ctx, "meetingContext").textContent, /专家A · 回复账号 acc1/);
        // I-5：模板/称呼/签名节点整体移除
        ["meetingTemplate", "templateDetails", "templateText", "resetTemplate",
            "meetingName", "meetingSignature"].forEach((id) => {
            assert.strictEqual(meetingField(ctx, id), null, `${id} 必须已从弹窗移除`);
        });
        assert.ok(meetingField(ctx, "meetingZoneSearch"), "时区字段保留");
        assert.ok(meetingField(ctx, "meetingUrl"), "Zoom 链接字段保留");
        assert.ok(meetingField(ctx, "inspectIcs"), "ICS 操作保留");
        assert.strictEqual(meetingField(ctx, "meetingZoneSearch").value, "土耳其 · 伊斯坦布尔 (UTC+3)");
        assert.strictEqual(meetingField(ctx, "meetingDate").value, "");
        assert.strictEqual(meetingField(ctx, "applyMeeting").disabled, true, "未预览前确认禁用");
    });

    it("options 不带模板目录也能继续：表单可用，模板缺失由 preview 400 呈现", async () => {
        const ctx = await bootMeetingA({ meetingTemplates: [] });
        await openMeetingLoaded(ctx);
        assert.strictEqual(meetingField(ctx, "meetingLoadStatus").hidden, true, "options 成功即 ready，不报模板目录");
        assert.strictEqual(meetingField(ctx, "meetingUrl").disabled, false);
        await fillCompleteMeetingForm(ctx);
        await flush();
        assert.ok(meetingPreviewRequests(ctx).length >= 1, "模板可用性由服务端 preview 判定");
    });

    it("配置加载失败：状态文案 + 重试可用；重试成功进入 ready", async () => {
        let fail = true;
        const ctx = await bootMeetingA({
            route: (url, method, body, entry, next) => {
                if (fail && /\/meeting-confirmation\/(options|time-zones)/.test(url)) {
                    return Promise.reject(new Error("network down"));
                }
                return next(url, method, body);
            }
        });
        await openMeetingLoaded(ctx);
        assert.match(meetingField(ctx, "meetingLoadStatus").textContent, /会议配置加载失败，请重试/);
        const retry = meetingField(ctx, "retryMeeting");
        assert.strictEqual(retry.hidden, false, "重试按钮仅网络/服务错误时显示");
        fail = false;
        click(retry);
        await flush();
        assert.strictEqual(meetingField(ctx, "meetingLoadStatus").hidden, true);
        assert.strictEqual(meetingField(ctx, "meetingZoneSearch").value, "土耳其 · 伊斯坦布尔 (UTC+3)");
    });
});

describe("fast-p 04: 时区搜索（S-4/I-5 键盘与显式选择）", () => {
    it("搜索过滤与空结果固定文案；清空不丢已选 id", async () => {
        const ctx = await bootMeetingA();
        await openMeetingLoaded(ctx);
        const search = meetingField(ctx, "meetingZoneSearch");
        search.value = "zzzz-no-zone";
        inputEvent(search);
        const list = meetingField(ctx, "meetingZoneOptions");
        assert.ok(!list.hidden);
        assert.ok(list.querySelector(".meeting-zone-empty"), "空结果唯一固定文案");
        assert.match(list.querySelector(".meeting-zone-empty").textContent, /没有匹配的时区/);
        // 清空搜索：候选恢复全部、selected 保持
        search.value = "";
        inputEvent(search);
        assert.strictEqual(list.querySelectorAll('button[role="option"]').length, DEFAULT_MEETING_ZONES.length);
        assert.strictEqual(meetingField(ctx, "meetingZoneSearch").value, "");
    });

    it("中文/别名/偏移命中；显式选择后 input=labelZh (offsetLabel)、hint=zoneId", async () => {
        const ctx = await bootMeetingA();
        await openMeetingLoaded(ctx);
        const search = meetingField(ctx, "meetingZoneSearch");
        search.value = "土耳其";
        inputEvent(search);
        let list = meetingField(ctx, "meetingZoneOptions");
        let labels = Array.prototype.slice.call(list.querySelectorAll('[data-role="zone-label"]')).map((n) => n.textContent);
        assert.ok(labels.some((l) => l.includes("土耳其 · 伊斯坦布尔")), "中文命中");
        search.value = "Türkiye";
        inputEvent(search);
        list = meetingField(ctx, "meetingZoneOptions");
        assert.strictEqual(list.querySelectorAll('button[role="option"]').length, 1, "别名命中唯一");
        search.value = "UTC+03:00";
        inputEvent(search);
        assert.ok(list.querySelectorAll('button[role="option"]').length >= 1, "UTC+03:00 归一命中");
        search.value = "Europe/Istanbul";
        inputEvent(search);
        pickZone(ctx, "Europe/Istanbul");
        assert.strictEqual(meetingField(ctx, "meetingZoneSearch").value, "土耳其 · 伊斯坦布尔 (UTC+3)");
        assert.strictEqual(meetingField(ctx, "meetingZoneHint").textContent, "Europe/Istanbul · 日期和时间均按此时区填写");
        assert.strictEqual(meetingField(ctx, "meetingZoneOptions").hidden, true, "选择后关闭候选");
    });

    it("键盘：ArrowDown/Up + focused；Enter 显式选中；Escape 两级；Esc/Tab 恢复已选标签", async () => {
        const ctx = await bootMeetingA();
        await openMeetingLoaded(ctx);
        const search = meetingField(ctx, "meetingZoneSearch");
        const list = meetingField(ctx, "meetingZoneOptions");
        search.value = "Europe/Istanbul";
        inputEvent(search);
        keyEvent(search, "ArrowDown");
        assert.ok(list.querySelector('button[role="option"].focused'), "ArrowDown 首项 focused");
        keyEvent(search, "ArrowDown");
        keyEvent(search, "ArrowUp");
        keyEvent(search, "Enter");
        assert.strictEqual(meetingField(ctx, "meetingZoneSearch").value, "土耳其 · 伊斯坦布尔 (UTC+3)", "Enter 显式选中");
        // Escape 两级：第一下只关列表并恢复标签
        search.value = "America";
        inputEvent(search);
        assert.strictEqual(list.hidden, false);
        keyEvent(search, "Escape");
        assert.strictEqual(list.hidden, true, "第一下 Esc 只关列表");
        assert.strictEqual(meetingField(ctx, "meetingZoneSearch").value, "土耳其 · 伊斯坦布尔 (UTC+3)", "Esc 恢复已选标签");
        assert.strictEqual(meetingDialog(ctx).hasAttribute("open"), true, "第一下 Esc 不关弹窗");
        // 第二下 Esc 关弹窗
        keyEvent(meetingDialog(ctx), "Escape");
        assert.strictEqual(meetingDialog(ctx).hasAttribute("open"), false, "第二下 Esc 关闭弹窗");
    });

    it("鼠标 mousedown 选时区：从上海切到 Istanbul，标签/选中值/预览 payload 同步", async () => {
        const ctx = await bootMeetingA();
        await openMeetingLoaded(ctx);
        const search = meetingField(ctx, "meetingZoneSearch");
        // 先显式选中上海
        pickZone(ctx, "Asia/Shanghai");
        assert.strictEqual(search.value, "中国 · 北京 / 上海 (UTC+8)");
        assert.strictEqual(meetingField(ctx, "meetingZoneHint").textContent,
            "Asia/Shanghai · 日期和时间均按此时区填写");
        // blur 在 mousedown 之后仍可能出现：不得回退已选值
        search.dispatchEvent(new MiniEvent("blur", { bubbles: true }));
        assert.strictEqual(search.value, "中国 · 北京 / 上海 (UTC+8)");
        // 鼠标按下 Istanbul 候选项（I-4）
        pickZone(ctx, "Europe/Istanbul");
        assert.strictEqual(search.value, "土耳其 · 伊斯坦布尔 (UTC+3)", "点击后立即显示新时区");
        assert.strictEqual(meetingField(ctx, "meetingZoneHint").textContent,
            "Europe/Istanbul · 日期和时间均按此时区填写");
        assert.strictEqual(meetingField(ctx, "meetingZoneOptions").hidden, true, "选择后关闭候选");
        await fillCompleteMeetingForm(ctx);
        await flush();
        const previews = meetingPreviewRequests(ctx);
        const payload = JSON.parse(previews[previews.length - 1].body);
        assert.strictEqual(payload.meeting.zoneId, "Europe/Istanbul", "payload 时区为显式选择值");
        assert.deepStrictEqual(Object.keys(payload.meeting).sort(),
            ["endLocal", "generatedAt", "startLocal", "zoneId", "zoomUrl"], "只发最小会议字段");
    });

    it("日期变化：重拉目录且保留 zone id；endDate 自动跟随不隐式改时区", async () => {
        const ctx = await bootMeetingA();
        await openMeetingLoaded(ctx);
        const zonesBefore = ctx.calls.api.filter((e) => /\/time-zones/.test(e.url)).length;
        setMeetingFieldValue(ctx, "meetingDate", "2026-03-08");
        const zoneCalls = ctx.calls.api.filter((e) => /\/time-zones/.test(e.url));
        assert.ok(zoneCalls.length > zonesBefore, "日期变化必须重新 GET 目录");
        assert.ok(zoneCalls[zoneCalls.length - 1].url.includes("date=2026-03-08"));
        assert.strictEqual(meetingField(ctx, "meetingEndDate").value, "2026-03-08");
        assert.strictEqual(meetingField(ctx, "meetingZoneSearch").value, "土耳其 · 伊斯坦布尔 (UTC+3)");
    });
});

describe("fast-p 04: 预览生成与右栏（T2/S-2 绑定）", () => {
    it("完整表单 debounce 后 POST；右栏值均来自响应", async () => {
        const ctx = await bootMeetingA();
        await openMeetingLoaded(ctx);
        await fillCompleteMeetingForm(ctx);
        await flush();
        const previewCalls = meetingPreviewRequests(ctx);
        const last = previewCalls[previewCalls.length - 1];
        assert.ok(last.url.startsWith("/api/mail/unmatched-inbound/101/meeting-confirmation/preview"));
        const parsed = JSON.parse(last.body);
        assert.strictEqual(parsed.contactId, 1);
        assert.strictEqual(parsed.meeting.zoneId, "Europe/Istanbul");
        assert.strictEqual(parsed.meeting.startLocal, "2026-09-11T10:00");
        assert.strictEqual(parsed.meeting.endLocal, "2026-09-11T10:30");
        assert.ok(parsed.meeting.generatedAt, "generatedAt 沿用 options 冻结值");
        assert.match(meetingField(ctx, "meetingFilename").textContent, /^meeting-2026-09-11-/);
        assert.match(meetingField(ctx, "meetingFileMeta").textContent, /日历事件 · 30 分钟 · 0\.[0-9] KB/);
        assert.match(meetingField(ctx, "meetingBody").textContent, /Dear Professor Basdogan/);
        const china = meetingField(ctx, "meetingClock").querySelector('[data-role="china-time"]');
        assert.ok(china.textContent.includes("15:00"));
        const duration = meetingField(ctx, "meetingClock").querySelector('[data-role="meeting-duration"]');
        assert.strictEqual(duration.textContent, "会议时长 30 分钟");
        const download = meetingField(ctx, "downloadMeeting");
        assert.ok(download.getAttribute("href"), "ready 下载链接有 blob URL");
        assert.strictEqual(download.getAttribute("aria-disabled"), "false");
    });

    it("本地 URL 非法：不发 preview、aria-invalid 标记、下载不可点", async () => {
        const ctx = await bootMeetingA();
        await openMeetingLoaded(ctx);
        await fillCompleteMeetingForm(ctx, { meetingUrl: "not-a-url" });
        await flush();
        assert.strictEqual(meetingPreviewRequests(ctx).length, 0, "本地校验失败不发 preview");
        assert.strictEqual(meetingField(ctx, "meetingUrl").getAttribute("aria-invalid"), "true");
        assert.strictEqual(meetingField(ctx, "downloadMeeting").getAttribute("aria-disabled"), "true");
    });

    it("服务端 400：message 进错误区、字段值保留；网络错误：状态文案 + 重试出 ready", async () => {
        let mode = "server";
        const ctx = await bootMeetingA({
            route: (url, method, body, entry, next) => {
                if (/\/meeting-confirmation\/preview/.test(url)) {
                    if (mode === "server") {
                        const err = new Error("请输入有效的 Zoom 会议链接");
                        err.data = { message: "请输入有效的 Zoom 会议链接" };
                        return Promise.reject(err);
                    }
                    if (mode === "network") {
                        return Promise.reject(new Error("network down"));
                    }
                }
                return next(url, method, body);
            }
        });
        await openMeetingLoaded(ctx);
        await fillCompleteMeetingForm(ctx);
        await flush();
        // 服务端 400
        assert.match(meetingField(ctx, "meetingError").textContent, /请输入有效的 Zoom 会议链接/);
        assert.strictEqual(meetingField(ctx, "meetingLoadStatus").hidden, true, "400 不显示重试");
        assert.strictEqual(meetingField(ctx, "meetingUrl").value, "https://zoom.us/j/123456789?pwd=AbC123", "左栏值保留");
        assert.strictEqual(meetingField(ctx, "meetingDate").value, "2026-09-11", "左栏日期保留");
        // 网络错误
        mode = "network";
        click(meetingField(ctx, "cancelMeeting"));
        await flush();
        openMeetingDialog(ctx);
        await flush();
        await fillCompleteMeetingForm(ctx);
        await flush();
        assert.match(meetingField(ctx, "meetingLoadStatus").textContent, /预览生成失败，请重试/);
        const retry = meetingField(ctx, "retryMeeting");
        assert.strictEqual(retry.hidden, false);
        mode = "ok";
        click(retry);
        await flush();
        assert.strictEqual(meetingField(ctx, "meetingLoadStatus").hidden, true);
        assert.strictEqual(meetingField(ctx, "applyMeeting").disabled, false, "重试成功后 ready");
    });

    it("陈旧预览响应不覆盖新值（双重序号保护）", async () => {
        const pending = [];
        const ctx = await bootMeetingA({
            route: (url, method, body, entry, next) => {
                if (/\/meeting-confirmation\/preview/.test(url)) {
                    const parsed = JSON.parse(body);
                    return new Promise((resolve, reject) => {
                        pending.push({ meeting: parsed.meeting, resolve, reject });
                    });
                }
                return next(url, method, body);
            }
        });
        await openMeetingLoaded(ctx);
        await fillCompleteMeetingForm(ctx, { meetingUrl: "https://zoom.us/j/1?pwd=first" });
        await fillCompleteMeetingForm(ctx, { meetingUrl: "https://zoom.us/j/2?pwd=second" });
        assert.ok(pending.length >= 2, "两次完整预览必须发生");
        const latestBody = meetingField(ctx, "meetingBody");
        assert.ok(!latestBody.textContent.includes("Second Name") || latestBody.textContent.includes("请填写"), "尚未返回前不展示旧值");
        // 旧响应先返回：不得覆盖
        pending[pending.length - 2].resolve(defaultPreviewResponse(
            { startLocal: "2026-09-11T10:00", endLocal: "2026-09-11T10:30", zoneId: "Europe/Istanbul" },
            { addressee: "First Name" }
        ));
        await flush();
        assert.ok(!meetingField(ctx, "meetingBody").textContent.includes("First Name"), "旧响应不得覆盖新表单");
        // 新响应返回：展示新值
        pending[pending.length - 1].resolve(defaultPreviewResponse(
            { startLocal: "2026-09-11T10:00", endLocal: "2026-09-11T10:30", zoneId: "Europe/Istanbul" },
            { addressee: "Second Name" }
        ));
        await flush();
        assert.match(meetingField(ctx, "meetingBody").textContent, /Dear Second Name/);
    });
});

describe("fast-p 04: 确认填入草稿（I-1/I-3/T3/S-3 块与卡）", () => {
    it("空正文首填：1 块 1 卡 ready；只写草稿不调用发送/旧确认接口", async () => {
        const ctx = await bootMeetingA();
        await openMeetingLoaded(ctx);
        await confirmReadyMeeting(ctx);
        const editor = ctx.host.querySelector('[aria-label="人工回复正文"]');
        const blocks = editor.querySelectorAll('[data-meeting-block="true"]');
        assert.strictEqual(blocks.length, 1);
        assert.match(meetingBodyText(editor), /Dear Professor Basdogan/);
        const container = ctx.host.querySelector('[data-role="meeting-attachment"]');
        assert.ok(container.querySelector(".meeting-file"), "确认后附件卡出现");
        assert.strictEqual(container.getAttribute("data-state"), "ready");
        assert.ok(container.querySelector('[data-role="filename"]').textContent.startsWith("meeting-2026-09-11-"));
        assert.match(container.querySelector('[data-role="file-meta"]').textContent, /Europe\/Istanbul · 30 分钟/);
        assert.strictEqual(meetingDialog(ctx).hasAttribute("open"), false, "apply 成功后关闭弹窗");
        // I-1：确认只写草稿
        assert.strictEqual(ctx.calls.sendRich.length, 0, "确认不发送");
        assert.ok(!ctx.calls.api.some((e) => /manual-rich-reply/.test(e.url)), "确认不调用发送接口");
        assert.ok(!ctx.calls.api.some((e) => /meeting-schedule/.test(e.url)), "确认不调用旧会议接口");
    });

    it("草稿恢复：切专家再回，会议块+富文本+卡恢复且不产生无意义 stale", async () => {
        const ctx = await bootMeetingA();
        const editor = ctx.host.querySelector('[aria-label="人工回复正文"]');
        editor.innerHTML = "<p>original <b>bold</b> text</p>";
        inputEvent(editor);
        await openMeetingLoaded(ctx);
        await confirmReadyMeeting(ctx);
        const subject = ctx.host.querySelector('input[aria-label="回复主题"]');
        subject.value = "Re: Question 1";
        inputEvent(subject);
        // 切 B 再回 A
        const b = ctx.host.querySelectorAll(".mc-person").find((p) => p.dataset.contactId === "2");
        click(b.querySelector(".mc-person-main"));
        await flush();
        const a = ctx.host.querySelectorAll(".mc-person").find((p) => p.dataset.contactId === "1");
        click(a.querySelector(".mc-person-main"));
        await flush();
        const editor2 = ctx.host.querySelector('[aria-label="人工回复正文"]');
        assert.ok(editor2.innerHTML.includes("<b>bold</b>"), "富文本恢复");
        assert.ok(editor2.innerHTML.includes("data-meeting-block=\"true\""), "会议块恢复");
        assert.strictEqual(editor2.querySelectorAll('[data-meeting-block="true"]').length, 1);
        const container = ctx.host.querySelector('[data-role="meeting-attachment"]');
        assert.strictEqual(container.getAttribute("data-state"), "ready", "恢复不产生无意义 stale");
        assert.strictEqual(ctx.host.querySelector('input[aria-label="回复主题"]').value, "Re: Question 1");
    });

    it("已有正文 + 编辑会议：原内容保留；未手改块原地更新仍 1 块 1 卡", async () => {
        const ctx = await bootMeetingA();
        const editor = ctx.host.querySelector('[aria-label="人工回复正文"]');
        editor.innerHTML = "<p><b>Please also review the agenda.</b></p>";
        inputEvent(editor);
        await openMeetingLoaded(ctx);
        const insertLabel = meetingField(ctx, "insertModeLabel");
        assert.strictEqual(insertLabel.hidden, false, "已有正文 → append/replace 选择可见");
        await confirmReadyMeeting(ctx);
        assert.ok(editor.innerHTML.includes("<b>Please also review the agenda.</b>"), "原加粗保留");
        assert.strictEqual(editor.querySelectorAll('[data-meeting-block="true"]').length, 1);
        // 编辑（未手改 → 原位更新）
        click(ctx.host.querySelector('[data-action="mc-edit-meeting"]'));
        await flush();
        assert.strictEqual(meetingField(ctx, "meetingTitle").textContent, "编辑会议确认");
        assert.strictEqual(meetingField(ctx, "applyMeeting").textContent, "更新并填入回复");
        assert.strictEqual(meetingField(ctx, "meetingDate").value, "2026-09-11");
        setMeetingFieldValue(ctx, "meetingStart", "10:30");
        setMeetingFieldValue(ctx, "meetingEnd", "11:00");
        await flush();
        click(meetingField(ctx, "applyMeeting"));
        await flush();
        assert.strictEqual(editor.querySelectorAll('[data-meeting-block="true"]').length, 1, "更新不产生第二块");
        assert.ok(meetingBodyText(editor).includes("10:30-11:00"), "原位更新替换为新会议时间");
        assert.ok(editor.innerHTML.includes("<b>Please also review the agenda.</b>"), "更新保留外部正文");
    });

    it("手改会议块 → stale 卡、禁止带附件发送；replace 恢复 ready", async () => {
        const ctx = await bootMeetingA();
        const editor = ctx.host.querySelector('[aria-label="人工回复正文"]');
        await openMeetingLoaded(ctx);
        await confirmReadyMeeting(ctx);
        const block = editor.querySelector('[data-meeting-block="true"]');
        block.textContent = String(block.textContent) + " hand edited";
        inputEvent(editor);
        const container = ctx.host.querySelector('[data-role="meeting-attachment"]');
        assert.strictEqual(container.getAttribute("data-state"), "stale", "手改后卡 stale");
        assert.match(container.textContent, /待重新确认/);
        assert.ok(container.querySelector('[data-state="stale"]'));
        click(ctx.host.querySelector('[data-action="mc-send-manual"]'));
        await flush();
        assert.strictEqual(ctx.calls.sendRich.length, 0, "stale 会议禁止带附件发送");
        // 编辑弹窗 conflict：默认 append 时确认禁用；replace 后恢复
        click(ctx.host.querySelector('[data-action="mc-edit-meeting"]'));
        await flush();
        ctx.runTimers(); // 编辑打开自动预览（沿用保存 input）
        await flush();
        assert.strictEqual(meetingField(ctx, "insertMode").value, "append");
        assert.strictEqual(meetingField(ctx, "applyMeeting").disabled, true, "手改块 + append 确认禁用");
        const mode = meetingField(ctx, "insertMode");
        mode.value = "replace";
        changeEvent(mode);
        assert.strictEqual(meetingField(ctx, "applyMeeting").disabled, false);
        click(meetingField(ctx, "applyMeeting"));
        await flush();
        assert.strictEqual(editor.querySelectorAll('[data-meeting-block="true"]').length, 1);
        assert.ok(!editor.innerHTML.includes("hand edited"), "replace 覆盖手改");
        assert.strictEqual(ctx.host.querySelector('[data-role="meeting-attachment"]').getAttribute("data-state"), "ready");
    });

    it("移除附件：0 卡但正文保留；再发送走旧 payload", async () => {
        const ctx = await bootMeetingA();
        const editor = ctx.host.querySelector('[aria-label="人工回复正文"]');
        await openMeetingLoaded(ctx);
        await confirmReadyMeeting(ctx);
        click(ctx.host.querySelector('[data-action="mc-remove-meeting"]'));
        await flush();
        const container = ctx.host.querySelector('[data-role="meeting-attachment"]');
        assert.strictEqual(container.innerHTML.trim(), "", "移除后容器清空");
        assert.ok(meetingBodyText(editor).includes("Dear Professor Basdogan"), "正文保留");
        const subject = ctx.host.querySelector('input[aria-label="回复主题"]');
        subject.value = "Re: Question 1";
        inputEvent(subject);
        click(ctx.host.querySelector('[data-action="mc-send-manual"]'));
        await flush();
        const payload = ctx.calls.sendRich[ctx.calls.sendRich.length - 1].body;
        assert.strictEqual(payload.meeting, undefined, "移除附件后不携带 meeting");
        assert.strictEqual(payload.previewAttachmentSha256, undefined);
    });

    it("QA：append 保留 QA —— meeting 与 ragFactCodes 同 payload", async () => {
        const ctx = await bootMeetingA();
        const wb = ctx.host.querySelector('.mc-section[data-section="workbench"]');
        toggleOpen(wb);
        await flush();
        await ctx.calls.workbenchMounts[0].callbacks.onComplete({
            renderedDraftText: "Please review the attached agenda.",
            text: "Please review the attached agenda.",
            usedFactCodes: ["KB-COMM-044"],
            ragCorpusFingerprint: "fp-2026"
        });
        await flush();
        const editor = ctx.host.querySelector('[aria-label="人工回复正文"]');
        // 已有正文 → append 保留 QA
        await openMeetingLoaded(ctx);
        await confirmReadyMeeting(ctx);
        assert.ok(meetingBodyText(editor).includes("Dear Professor Basdogan"));
        const subject = ctx.host.querySelector('input[aria-label="回复主题"]');
        subject.value = "My QA Subject";
        inputEvent(subject);
        click(ctx.host.querySelector('[data-action="mc-send-manual"]'));
        await flush();
        const payload = ctx.calls.sendRich[ctx.calls.sendRich.length - 1].body;
        assert.deepStrictEqual(payload.ragFactCodes, ["KB-COMM-044"], "append 保留 QA");
        assert.ok(payload.meeting, "append 后携带 meeting");
        assert.strictEqual(payload.previewAttachmentSha256, "a".repeat(64));
    });

    it("QA：replace 清旧 QA；采用工作台先移除旧日历并提示", async () => {
        const ctx = await bootMeetingA();
        const wb = ctx.host.querySelector('.mc-section[data-section="workbench"]');
        toggleOpen(wb);
        await flush();
        await ctx.calls.workbenchMounts[0].callbacks.onComplete({
            renderedDraftText: "Please review the attached agenda.",
            text: "Please review the attached agenda.",
            usedFactCodes: ["KB-COMM-044"],
            ragCorpusFingerprint: "fp-2026"
        });
        await flush();
        const editor = ctx.host.querySelector('[aria-label="人工回复正文"]');
        await openMeetingLoaded(ctx);
        await confirmReadyMeeting(ctx);
        assert.ok(meetingBodyText(editor).includes("Dear Professor Basdogan"));
        // 编辑弹窗 → replace 整篇（清旧 QA）
        click(ctx.host.querySelector('[data-action="mc-edit-meeting"]'));
        await flush();
        ctx.runTimers();
        await flush();
        const mode = meetingField(ctx, "insertMode");
        mode.value = "replace";
        changeEvent(mode);
        click(meetingField(ctx, "applyMeeting"));
        await flush();
        assert.strictEqual(editor.querySelectorAll('[data-meeting-block="true"]').length, 1);
        // 再采用新工作台草稿：原日历附件已移除提示 + 卡清除 + QA 只含新草稿
        const statusStart = ctx.calls.status.length;
        await ctx.calls.workbenchMounts[0].callbacks.onComplete({
            renderedDraftText: "New adopted body",
            text: "New adopted body",
            usedFactCodes: ["KB-COMM-099"],
            ragCorpusFingerprint: "fp-2026"
        });
        await flush();
        assert.strictEqual(editor.innerText, "New adopted body");
        assert.strictEqual(ctx.host.querySelector('[data-role="meeting-attachment"]').innerHTML.trim(), "");
        assert.ok(ctx.calls.status.slice(statusStart).some((s) => /原日历附件已移除/.test(s.message)));
        assert.strictEqual(editor.querySelectorAll('[data-meeting-block="true"]').length, 0);
        const subject = ctx.host.querySelector('input[aria-label="回复主题"]');
        subject.value = "My QA Replace Subject";
        inputEvent(subject);
        click(ctx.host.querySelector('[data-action="mc-send-manual"]'));
        await flush();
        const payload = ctx.calls.sendRich[ctx.calls.sendRich.length - 1].body;
        assert.deepStrictEqual(payload.ragFactCodes, ["KB-COMM-099"], "replace 清旧 QA、采用后只含新草稿 QA");
        assert.strictEqual(payload.meeting, undefined, "采用清除日历附件");
    });
});

describe("fast-p 04: retarget 迁移与新来信目标（I-2/T3）", () => {
    it("切换新来信：meeting stale、正文保留、禁止带附件发送；重新编辑后 ready", async () => {
        let current = expertA();
        const ctx = await bootMeetingA({
            route: (url, method, body, entry, next) => {
                if (url.startsWith("/api/mail/mailbox/conversations?")) {
                    return Promise.resolve({ items: [current, expertB()], total: 2 });
                }
                if (/\/api\/mail\/mailbox\/conversations\/\d+\/messages/.test(url)) {
                    if (current.latestInbound && current.latestInbound.processingId === 102) {
                        const older = messagesA();
                        older.items = older.items.concat([{
                            source: "INBOUND_PROCESSING", id: 102, contactId: 1, direction: "INBOUND", accountCode: "acc1",
                            subject: "Brand new question", body: "raw 102", cleanedBody: "clean 102", eventAt: "2026-09-08T10:00:00",
                            sendStatus: null, processStatus: "MANUAL_REVIEW", attachmentCount: 0, firstAttachmentNames: [], messageId: "m102", inReplyTo: "m88", tags: []
                        }]);
                        return Promise.resolve(older);
                    }
                    return Promise.resolve(messagesA());
                }
                return next(url, method, body);
            },
            dialogResult: true
        });
        const editor = ctx.host.querySelector('[aria-label="人工回复正文"]');
        await openMeetingLoaded(ctx);
        await confirmReadyMeeting(ctx);
        const subject = ctx.host.querySelector('input[aria-label="回复主题"]');
        subject.value = "Re: Question 1";
        inputEvent(subject);
        // 收到新来信 → 提示后确认切换目标
        current = Object.assign({}, expertA(), {
            latestInbound: { processingId: 102, accountCode: "acc1", messageId: "m102", receivedAt: "2026-09-08T10:00:00" },
            pendingCount: 2
        });
        ctx.sandbox.MailboxChat.mount(ctx.host, { filters: {} });
        await flush();
        const card = ctx.host.querySelector('[data-role="meeting-attachment"]');
        assert.strictEqual(card.getAttribute("data-state"), "stale", "retarget 后会议标 stale");
        assert.match(card.textContent, /待重新确认/);
        assert.ok(meetingBodyText(editor).includes("Dear Professor Basdogan"), "正文保留跨目标");
        click(ctx.host.querySelector('[data-action="mc-send-manual"]'));
        await flush();
        assert.strictEqual(ctx.calls.sendRich.length, 0, "retarget stale 禁止发送");
        // 编辑会议重新确认 → ready
        click(ctx.host.querySelector('[data-action="mc-edit-meeting"]'));
        await flush();
        assert.strictEqual(meetingField(ctx, "meetingDate").value, "2026-09-11", "保留旧 input 供改");
        setMeetingFieldValue(ctx, "meetingStart", "11:30");
        setMeetingFieldValue(ctx, "meetingEnd", "12:00");
        await flush();
        click(meetingField(ctx, "applyMeeting"));
        await flush();
        assert.strictEqual(ctx.host.querySelector('[data-role="meeting-attachment"]').getAttribute("data-state"), "ready");
        assert.ok(meetingBodyText(editor).includes("11:30-12:00"), "重新确认后正文为新时间");
    });
});

describe("fast-p 04: 发送锁与异步隔离（T4/I-2）", () => {
    async function meetingReadyCtx(serverOverrides) {
        const ctx = await bootMeetingA(serverOverrides);
        await openMeetingLoaded(ctx);
        await confirmReadyMeeting(ctx);
        return ctx;
    }

    it("带 meeting 发送：payload 带 meeting.input + sha；成功后该份草稿清除", async () => {
        const ctx = await meetingReadyCtx();
        const subject = ctx.host.querySelector('input[aria-label="回复主题"]');
        subject.value = "My Subject A";
        inputEvent(subject);
        click(ctx.host.querySelector('[data-action="mc-send-manual"]'));
        await flush();
        const call = ctx.calls.sendRich[ctx.calls.sendRich.length - 1];
        const payload = call.body;
        assert.ok(payload.meeting, "payload 携带 meeting");
        assert.strictEqual(payload.meeting.zoneId, "Europe/Istanbul");
        assert.strictEqual(payload.previewAttachmentSha256, "a".repeat(64));
        assert.strictEqual(payload.senderAccountCode, null, "不改变 senderAccountCode 适配");
        // 切走再回：草稿已清（主题回默认预填、卡清空）
        const b = ctx.host.querySelectorAll(".mc-person").find((p) => p.dataset.contactId === "2");
        click(b.querySelector(".mc-person-main"));
        await flush();
        const a = ctx.host.querySelectorAll(".mc-person").find((p) => p.dataset.contactId === "1");
        click(a.querySelector(".mc-person-main"));
        await flush();
        assert.notStrictEqual(ctx.host.querySelector('input[aria-label="回复主题"]').value, "My Subject A", "发送成功后该份草稿已删除");
        assert.strictEqual(ctx.host.querySelector('[data-role="meeting-attachment"]').innerHTML.trim(), "");
    });

    it("发送中锁 UI；失败恢复且草稿保留", async () => {
        const ctx = await meetingReadyCtx({ sendRichDeferred: true });
        const editor = ctx.host.querySelector('[aria-label="人工回复正文"]');
        const compose = ctx.host.querySelector('[data-role="manual-compose"]');
        const subject = ctx.host.querySelector('input[aria-label="回复主题"]');
        subject.value = "Re: Question 1";
        inputEvent(subject);
        click(ctx.host.querySelector('[data-action="mc-send-manual"]'));
        await flush();
        assert.strictEqual(compose.getAttribute("data-meeting-sending"), "true");
        assert.strictEqual(editor.getAttribute("contenteditable"), "false");
        assert.strictEqual(subject.disabled, true, "subject 发送中禁用");
        assert.strictEqual(ctx.host.querySelector('[data-action="mc-send-manual"]').disabled, true);
        assert.strictEqual(ctx.host.querySelector('[data-action="mc-remove-meeting"]').disabled, true, "会议操作禁用");
        const deferred = ctx.calls.sendRichDeferred[ctx.calls.sendRichDeferred.length - 1];
        deferred.reject(new Error("smtp down"));
        await flush();
        assert.strictEqual(compose.hasAttribute("data-meeting-sending"), false);
        assert.strictEqual(editor.getAttribute("contenteditable"), "true");
        assert.strictEqual(ctx.host.querySelector('[data-action="mc-send-manual"]').disabled, false);
        assert.strictEqual(ctx.host.querySelector('[data-role="meeting-attachment"]').getAttribute("data-state"), "ready", "失败草稿保留");
    });

    it("A 发送中切 B：A 完成后只清 A 快照，B 目标不受影响", async () => {
        const ctx = await meetingReadyCtx({ sendRichDeferred: true });
        const subject = ctx.host.querySelector('input[aria-label="回复主题"]');
        subject.value = "My Subject A";
        inputEvent(subject);
        click(ctx.host.querySelector('[data-action="mc-send-manual"]'));
        await flush();
        const aDeferred = ctx.calls.sendRichDeferred[ctx.calls.sendRichDeferred.length - 1];
        const b = ctx.host.querySelectorAll(".mc-person").find((p) => p.dataset.contactId === "2");
        click(b.querySelector(".mc-person-main"));
        await flush();
        aDeferred.resolve(true);
        await flush();
        // B 无来信 → 无 manual 区，不抛错即通过；回 A 草稿已清
        const a2 = ctx.host.querySelectorAll(".mc-person").find((p) => p.dataset.contactId === "1");
        click(a2.querySelector(".mc-person-main"));
        await flush();
        assert.notStrictEqual(ctx.host.querySelector('input[aria-label="回复主题"]').value, "My Subject A");
    });

    it("发送失败不删草稿不自动重试；再点成功", async () => {
        const ctx = await bootMeetingA();
        await openMeetingLoaded(ctx);
        await confirmReadyMeeting(ctx);
        const subject = ctx.host.querySelector('input[aria-label="回复主题"]');
        subject.value = "Re: Question 1";
        inputEvent(subject);
        let failNext = true;
        ctx.sandbox.mcHostSendRichReply = (processingId, body) => {
            ctx.calls.sendRich.push({ processingId: Number(processingId), body });
            if (failNext) return Promise.reject(new Error("smtp down"));
            return Promise.resolve(true);
        };
        click(ctx.host.querySelector('[data-action="mc-send-manual"]'));
        await flush();
        const b = ctx.host.querySelectorAll(".mc-person").find((p) => p.dataset.contactId === "2");
        click(b.querySelector(".mc-person-main"));
        await flush();
        const a = ctx.host.querySelectorAll(".mc-person").find((p) => p.dataset.contactId === "1");
        click(a.querySelector(".mc-person-main"));
        await flush();
        assert.strictEqual(ctx.host.querySelector('input[aria-label="回复主题"]').value, "Re: Question 1", "失败草稿保留");
        assert.strictEqual(ctx.host.querySelector('[data-role="meeting-attachment"]').getAttribute("data-state"), "ready");
        failNext = false;
        click(ctx.host.querySelector('[data-action="mc-send-manual"]'));
        await flush();
        assert.ok(ctx.calls.sendRich[ctx.calls.sendRich.length - 1].body.meeting, "重试成功携带 meeting");
    });
});

describe("fast-p 04: 历史下载错误经宿主状态提示", () => {
    it("下载适配错误 → hostShowStatus error；下载参数原样传递", async () => {
        const withCalendar = messagesA();
        withCalendar.items = withCalendar.items.map((message) => {
            if (message.source === "MAIL_RECORD" && message.id === 88) {
                return Object.assign({}, message, {
                    calendarAttachment: {
                        filename: "meeting-2026-09-06-Expert.ics",
                        byteLength: 512,
                        downloadUrl: "/api/mail/conversations/1/messages/88/calendar-attachment"
                    }
                });
            }
            return message;
        });
        const ctx = await bootChat({
            conversations: { items: [expertA()], total: 1 },
            messages: withCalendar,
            contact: contactA(),
            downloadError: "日历附件不可用"
        });
        const a = ctx.host.querySelectorAll(".mc-person")[0];
        click(a.querySelector(".mc-person-main"));
        await flush();
        const article = ctx.host.querySelector('[data-message-key="MAIL_RECORD:88"]');
        click(article.querySelector('[data-action="mc-download-sent-meeting"]'));
        await flush();
        assert.ok(ctx.calls.status.some((s) => /日历附件不可用/.test(s.message)));
    });
});


