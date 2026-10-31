"use strict";

// fast-p 2026-10-27 · user1 只读授权入口（04 I-1..I-4 / S-1）。
// 用真实 mailbox-chat.js + 最小真实 DOM（含 innerHTML 解析/事件冒泡）驱动：
//   - S-1：admin 选中专家后 .mc-actions 末尾固定两个节点、文案/disabled 矩阵、复用既有 class；
//   - I-1：仅 instance.user === "admin" 渲染；目标 contactId 捕获于点击时；伪造按钮无请求；
//   - I-2：GET→PUT→DELETE 闭环、非乐观翻转、异常回包形状、失败后只 GET 重试、双击单笔在途；
//   - I-3：晚到读/写回包不写新专家、回旧专家重新 GET、unmount 后不新建节点；
//   - I-4：原 header 动作保留、授权不触发草稿/发信、index 版本一致、reader 无 grant 请求。

const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const ROOT = path.join(__dirname, "..", "..", "main", "resources", "static");
const chatSource = fs.readFileSync(path.join(ROOT, "mailbox-chat.js"), "utf-8");
const readerSource = fs.readFileSync(path.join(ROOT, "mailbox-readonly.js"), "utf-8");
const indexSource = fs.readFileSync(path.join(ROOT, "index.html"), "utf-8");
const chatCssSource = fs.readFileSync(path.join(ROOT, "mailbox-chat.css"), "utf-8");

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
        if (this.tagName === "SELECT" && !this._value) {
            const selected = this.querySelectorAll("option").find(option => option.selected || option.hasAttribute("selected"));
            return selected ? selected.value : "";
        }
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
    focus() {
        this.ownerDocument._activeElement = this;
    }
    showModal() {
        this.setAttribute("open", "");
    }
    close() {
        this.removeAttribute("open");
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
        this._activeElement = null;
    }
    get body() {
        return this.root;
    }
    get activeElement() {
        return this._activeElement;
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
// 04 沙箱：真实 mailbox-chat.js + 授权接口桩
// ════════════════════════════════════════════════════════════════════════

function grantUrl(contactId) {
    return `/api/mail/mailbox/conversations/${contactId}/read-access/user1`;
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
        expertTags: ["学术科研"],
        latestMessage: { source: "INBOUND_PROCESSING", id: 101, direction: "INBOUND", subject: "Question 1", time: "2026-09-07T03:00:00", sendStatus: null },
        latestInbound: { processingId: 101, accountCode: "acc1", messageId: "m101", receivedAt: "2026-09-07T03:00:00" },
        materialCount: 3
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
        receivedCount: 1,
        sentCount: 1,
        failedCount: 0,
        pendingCount: 1,
        waitingReply: false,
        expertTags: [],
        latestMessage: { source: "MAIL_RECORD", id: 88, direction: "OUTBOUND", subject: "Introduction", time: "2026-09-06T09:00:00", sendStatus: "SENT" },
        latestInbound: null,
        materialCount: 0
    }, extra || {});
}

function conversationFixtures() {
    return { items: [expertA(), expertB()], total: 2, page: 0, size: 20 };
}

function deferred() {
    let resolveFn = null;
    let rejectFn = null;
    const promise = new Promise((resolve, reject) => { resolveFn = resolve; rejectFn = reject; });
    return { promise, resolve: resolveFn, reject: rejectFn };
}

function createGrantSandbox(options) {
    const opts = options || {};
    const requests = [];
    const calls = { statuses: [], materials: [], dialogs: [] };
    const access = Object.assign({}, opts.access || {});
    const timers = [];
    const frames = [];
    let timerSeq = 0;

    const grantResponse = (contactId) => ({
        username: "user1",
        contactId: Number(contactId),
        accessLevel: access[contactId] || "NONE",
        canRead: ["READ", "FULL"].includes(access[contactId])
    });

    function defaultRoute(url, method, body) {
        if (url === "/api/auth/me") {
            return Promise.resolve(opts.authMe !== undefined
                ? opts.authMe
                : { authenticated: true, username: "admin", mustChangePassword: false });
        }
        const grant = url.match(/^\/api\/mail\/mailbox\/conversations\/(\d+)\/read-access\/user1$/);
        if (grant) {
            const id = Number(grant[1]);
            if (opts.readAccessError) return Promise.reject(new Error(opts.readAccessError));
            if (method === "PUT") access[id] = JSON.parse(body).accessLevel;
            if (method === "DELETE") access[id] = "NONE";
            return Promise.resolve(grantResponse(id));
        }
        if (url.startsWith("/api/mail/mailbox/conversations?")) {
            return Promise.resolve(opts.conversations || conversationFixtures());
        }
        let match = url.match(/^\/api\/mail\/mailbox\/conversations\/(\d+)\/messages/);
        if (match) return Promise.resolve(opts.messages || { items: [], nextBefore: null, hasMore: false });
        match = url.match(/^\/api\/mail\/mailbox\/conversations\/(\d+)\/suspension/);
        if (match) {
            return Promise.resolve({
                contactId: Number(match[1]), suspended: false, suspendReason: null,
                suspensionPendingCount: 0, progressStatus: "NONE", followed: false
            });
        }
        match = url.match(/^\/api\/expert-contacts\/(\d+)/);
        if (match) {
            const id = Number(match[1]);
            return Promise.resolve({
                contact: {
                    id, expertName: `专家${id}`, expertEmail: `e${id}@example.edu`,
                    currentIndexLevel: "APPLICATION", operatorStatus: "REPLIED", currentStatus: "WAITING_REPLY"
                },
                mails: []
            });
        }
        if (url.startsWith("/api/mail/mailbox/drafts")) return Promise.resolve({ total: 0, page: 0, size: 20, items: [] });
        if (url.startsWith("/api/mail/unmatched-inbound?")) return Promise.resolve({ records: [], totalCount: 0, manualReviewTotal: 0, countsByReasonType: {} });
        if (url.startsWith("/api/operator-action-logs")) return Promise.resolve({ records: [] });
        if (url.startsWith("/api/inbound-summary/tags/options")) return Promise.resolve({ items: [] });
        return Promise.resolve({});
    }

    const route = opts.route
        ? (url, method, body) => opts.route(url, method, body, defaultRoute)
        : defaultRoute;

    const sandbox = {
        console,
        URLSearchParams,
        setTimeout: (fn) => { const id = ++timerSeq; timers.push({ id, fn }); return id; },
        clearTimeout: (id) => { const idx = timers.findIndex((item) => item.id === id); if (idx >= 0) timers.splice(idx, 1); },
        escapeHtml: escapeHtmlLike,
        alert: () => {},
        confirm: () => true,
        showStatus: (message, type) => { calls.statuses.push({ message, type }); },
        api: (url, requestOpts) => {
            const entry = { url, method: (requestOpts && requestOpts.method) || "GET", body: requestOpts && requestOpts.body };
            requests.push(entry);
            return Promise.resolve().then(() => route(url, entry.method, entry.body));
        },
        openActionDialog: () => Promise.resolve(null),
        unmountExpertMaterialsHosts: () => {},
        mcHostOpenMaterials: (contactId) => { calls.materials.push(Number(contactId)); return true; },
        mcHostOpenExpertDetail: () => Promise.resolve(),
        mcHostOpenFollowUp: () => Promise.resolve(),
        refreshUnmatchedBadge: () => Promise.resolve(),
        operatorStatusOptions: [
            ["NOT_CONTACTED", "未联系"], ["REPLIED", "已回复"], ["COMPLETED", "已完成"]
        ],
        indexLevelOptions: [["RAW", "原始"], ["CANDIDATE", "筛选"], ["APPLICATION", "有效"]],
        expertTagLabels: {},
        matchMedia: () => ({ matches: false, addEventListener: () => {}, removeEventListener: () => {} }),
        requestAnimationFrame: (fn) => { frames.push(fn); return frames.length; },
        addEventListener: () => {},
        removeEventListener: () => {}
    };
    vm.createContext(sandbox);
    vm.runInContext(chatSource, sandbox, { filename: "mailbox-chat.js" });

    return {
        sandbox,
        requests,
        calls,
        access,
        timers,
        frames,
        runFrames: () => { while (frames.length) frames.shift()(); },
        runTimers: () => { while (timers.length) timers.shift().fn(); }
    };
}

function mountChat(options, mountOptions) {
    const ctx = createGrantSandbox(options);
    const { doc, host } = createDom();
    ctx.doc = doc;
    ctx.host = host;
    ctx.sandbox.document = doc;
    ctx.api = ctx.sandbox.MailboxChat.mount(host, Object.assign({ filters: {}, sessionUser: "admin" }, mountOptions || {}));
    return ctx;
}

async function settle(ctx) {
    for (let i = 0; i < 10; i += 1) {
        await flush();
        ctx.runFrames();
        ctx.runTimers();
    }
    await flush();
}

async function boot(options, mountOptions) {
    const ctx = mountChat(options, mountOptions);
    await settle(ctx);
    return ctx;
}

function click(el) {
    el.dispatchEvent(new MiniEvent("click", { bubbles: true }));
}

function personButton(host, contactId) {
    return host.querySelectorAll('[data-action="mc-select-expert"]')
        .find((button) => Number(button.dataset.contactId) === Number(contactId)) || null;
}

function selectPerson(host, contactId) {
    const button = personButton(host, contactId);
    assert.ok(button, `找不到专家 ${contactId} 的列表按钮`);
    click(button);
}

function actionsEl(host) {
    return host.querySelector(".mc-actions");
}

function grantSelect(host) {
    return host.querySelector('[data-action="mc-set-user1-access"]');
}

function grantError(host) {
    return host.querySelector('[data-role="mc-access-error"]');
}

function setLevel(ctx, level) {
    const select = grantSelect(ctx.host);
    select.value = level;
    select.dispatchEvent(new MiniEvent("change", { bubbles: true }));
}

function accessPayload(contactId, accessLevel) {
    return { username: "user1", contactId, accessLevel, canRead: accessLevel !== "NONE" };
}

function grantRequests(ctx) {
    return ctx.requests.filter((entry) => /\/read-access\/user1$/.test(entry.url));
}

function grantMethods(ctx) {
    return grantRequests(ctx).map((entry) => entry.method);
}

const ORIGINAL_ACTIONS = [
    "mc-open-materials",
    "mc-manage-expert",
    "mc-add-schedule",
    "mc-edit-schedule",
    "mc-cancel-schedule"
];

// ════════════════════════════════════════════════════════════════════════
// S-1：追加节点、文案矩阵、复用既有 class
// ════════════════════════════════════════════════════════════════════════

describe("08 S-2：admin 三态直接授权", () => {
    it("加载中禁用，服务端确认后启用；原头部动作保留在权限入口之前", async () => {
        const gate = deferred();
        const ctx = await boot({ route: (url, method, body, next) =>
            url === grantUrl(1) && method === "GET" ? gate.promise : next(url, method, body) });
        selectPerson(ctx.host, 1);
        await flush();
        const select = grantSelect(ctx.host);
        assert.equal(select.tagName, "SELECT");
        assert.equal(select.className, "mc-access-select");
        assert.equal(select.getAttribute("aria-label"), "user1专家权限");
        assert.equal(select.disabled, true);
        assert.deepEqual(select.querySelectorAll("option").map(option => option.value), ["NONE", "READ", "FULL"]);
        const actions = actionsEl(ctx.host);
        ORIGINAL_ACTIONS.forEach(action => {
            const node = actions.querySelector(`[data-action="${action}"]`);
            assert.ok(node);
            assert.ok(actions.children.indexOf(node) < actions.children.indexOf(select));
        });
        assert.doesNotMatch(actions.innerHTML, /style=/);
        gate.resolve(accessPayload(1, "READ"));
        await settle(ctx);
        assert.equal(select.value, "READ");
        assert.equal(select.disabled, false);
        assert.equal(grantError(ctx.host).hidden, true);
    });

    it("NONE → READ → FULL → NONE：固定目标、明确body，只有撤权使用DELETE", async () => {
        const ctx = await boot();
        selectPerson(ctx.host, 1); await settle(ctx);
        for (const level of ["READ", "FULL", "NONE"]) {
            setLevel(ctx, level); await settle(ctx);
            assert.equal(grantSelect(ctx.host).value, level);
            assert.equal(grantSelect(ctx.host).disabled, false);
        }
        const writes = grantRequests(ctx).filter(entry => entry.method !== "GET");
        assert.deepEqual(writes.map(entry => [entry.url, entry.method]), [
            [grantUrl(1), "PUT"], [grantUrl(1), "PUT"], [grantUrl(1), "DELETE"]
        ]);
        assert.deepEqual(writes.slice(0, 2).map(entry => JSON.parse(entry.body)), [
            { accessLevel: "READ" }, { accessLevel: "FULL" }
        ]);
        assert.equal(writes[2].body, undefined);
        assert.equal(ctx.access[1], "NONE");
    });

    it("保存期间禁用并保持确认等级；重复change只产生一次写入", async () => {
        const gate = deferred();
        const ctx = await boot({ access: { 1: "READ" }, route: (url, method, body, next) =>
            url === grantUrl(1) && method === "PUT" ? gate.promise : next(url, method, body) });
        selectPerson(ctx.host, 1); await settle(ctx);
        setLevel(ctx, "FULL"); setLevel(ctx, "FULL"); await flush();
        assert.equal(grantSelect(ctx.host).disabled, true);
        assert.equal(grantSelect(ctx.host).value, "READ");
        assert.equal(grantRequests(ctx).filter(entry => entry.method === "PUT").length, 1);
        gate.resolve(accessPayload(1, "FULL")); await settle(ctx);
        assert.equal(grantSelect(ctx.host).value, "FULL");
        assert.equal(grantSelect(ctx.host).disabled, false);
    });

    it("保存失败回滚已确认等级并行内报错，不自动重试或发送邮件", async () => {
        const ctx = await boot({ access: { 1: "READ" }, route: (url, method, body, next) =>
            url === grantUrl(1) && method === "PUT" ? Promise.reject(new Error("offline")) : next(url, method, body) });
        selectPerson(ctx.host, 1); await settle(ctx);
        setLevel(ctx, "FULL"); await settle(ctx);
        assert.equal(grantSelect(ctx.host).value, "READ");
        assert.equal(grantSelect(ctx.host).disabled, false);
        assert.equal(grantError(ctx.host).hidden, false);
        assert.match(grantError(ctx.host).textContent, /保存失败/);
        assert.deepEqual(grantMethods(ctx), ["GET", "PUT"]);
        assert.equal(ctx.access[1], "READ");
        assert.ok(!ctx.requests.some(entry => /rich-reply|\/send$/.test(entry.url)));
    });

    it("加载失败保持不可写，刷新只重新GET而非重发写入", async () => {
        let count = 0;
        const ctx = await boot({ route: (url, method, body, next) => {
            if (url === grantUrl(1) && method === "GET" && ++count === 1) return Promise.reject(new Error("offline"));
            return next(url, method, body);
        } });
        selectPerson(ctx.host, 1); await settle(ctx);
        assert.equal(grantSelect(ctx.host).disabled, true);
        assert.match(grantError(ctx.host).textContent, /加载失败/);
        await ctx.api.refreshFromHost(); await settle(ctx);
        assert.equal(grantSelect(ctx.host).disabled, false);
        assert.equal(grantSelect(ctx.host).value, "NONE");
        assert.ok(grantMethods(ctx).every(method => method === "GET"));
    });

    it("非法、不一致或错目标响应不能升级权限", async () => {
        const valid = accessPayload(1, "FULL");
        for (const payload of [
            Object.assign({}, valid, { username: "admin" }),
            Object.assign({}, valid, { contactId: 2 }),
            Object.assign({}, valid, { canRead: "yes" }),
            Object.assign({}, valid, { accessLevel: "ADMIN" }),
            Object.assign({}, valid, { canRead: false })
        ]) {
            const ctx = await boot({ route: (url, method, body, next) =>
                url === grantUrl(1) && method === "GET" ? Promise.resolve(payload) : next(url, method, body) });
            selectPerson(ctx.host, 1); await settle(ctx);
            assert.equal(grantSelect(ctx.host).disabled, true, JSON.stringify(payload));
            assert.equal(grantError(ctx.host).hidden, false);
        }
    });

    it("PUT不一致回包按失败回滚，不画成成功", async () => {
        const ctx = await boot({ access: { 1: "READ" }, route: (url, method, body, next) =>
            url === grantUrl(1) && method === "PUT" ? Promise.resolve(accessPayload(1, "NONE")) : next(url, method, body) });
        selectPerson(ctx.host, 1); await settle(ctx);
        setLevel(ctx, "FULL"); await settle(ctx);
        assert.equal(grantSelect(ctx.host).value, "READ");
        assert.equal(grantError(ctx.host).hidden, false);
    });

    it("非admin不渲染授权入口；伪造select不产生授权请求", async () => {
        for (const username of ["user1", "console"]) {
            const ctx = await boot({ authMe: { authenticated: true, username } }, { sessionUser: username });
            selectPerson(ctx.host, 1); await settle(ctx);
            assert.equal(grantSelect(ctx.host), null);
            assert.equal(grantRequests(ctx).length, 0);
            actionsEl(ctx.host).insertAdjacentHTML("beforeend", '<select data-action="mc-set-user1-access" data-contact-id="1"><option value="FULL">伪造</option></select>');
            setLevel(ctx, "FULL"); await settle(ctx);
            assert.equal(grantRequests(ctx).length, 0);
        }
    });

    it("A慢GET切B，迟到A不能覆盖B；回A必须重新读取", async () => {
        const gate = deferred();
        let reads = 0;
        const ctx = await boot({ access: { 1: "READ", 2: "FULL" }, route: (url, method, body, next) =>
            url === grantUrl(1) && method === "GET" && ++reads === 1 ? gate.promise : next(url, method, body) });
        selectPerson(ctx.host, 1); await flush();
        selectPerson(ctx.host, 2); await settle(ctx);
        assert.equal(grantSelect(ctx.host).value, "FULL");
        gate.resolve(accessPayload(1, "NONE")); await settle(ctx);
        assert.equal(grantSelect(ctx.host).dataset.contactId, "2");
        assert.equal(grantSelect(ctx.host).value, "FULL");
        selectPerson(ctx.host, 1); await settle(ctx);
        assert.equal(grantSelect(ctx.host).value, "READ");
        assert.equal(reads, 2);
    });

    it("A写入在途切B，保存仍落A且回包不画到B", async () => {
        const gate = deferred();
        const ctx = await boot({ access: { 1: "READ", 2: "NONE" }, route: (url, method, body, next) =>
            url === grantUrl(1) && method === "PUT" ? gate.promise.then(() => next(url, method, body)) : next(url, method, body) });
        selectPerson(ctx.host, 1); await settle(ctx);
        setLevel(ctx, "FULL"); await flush();
        selectPerson(ctx.host, 2); await settle(ctx);
        gate.resolve(); await settle(ctx);
        assert.equal(ctx.access[1], "FULL");
        assert.equal(grantSelect(ctx.host).dataset.contactId, "2");
        assert.equal(grantSelect(ctx.host).value, "NONE");
        selectPerson(ctx.host, 1); await settle(ctx);
        assert.equal(grantSelect(ctx.host).value, "FULL");
    });

    it("unmount后GET/PUT迟到都不能恢复节点", async () => {
        for (const pending of ["GET", "PUT"]) {
            const gate = deferred();
            const ctx = await boot({ access: { 1: "READ" }, route: (url, method, body, next) =>
                url === grantUrl(1) && method === pending ? gate.promise : next(url, method, body) });
            selectPerson(ctx.host, 1); await settle(ctx);
            if (pending === "PUT") setLevel(ctx, "FULL");
            await flush(); ctx.api.unmount();
            gate.resolve(accessPayload(1, "FULL")); await settle(ctx);
            assert.equal(ctx.host.innerHTML, "");
            assert.equal(grantSelect(ctx.host), null);
        }
    });
});

describe("04 I-4：原管理行为与资源版本", () => {
    it("原材料入口与授权后的原动作保留，授权本身不发信", async () => {
        const ctx = await boot();
        selectPerson(ctx.host, 1); await settle(ctx);
        click(actionsEl(ctx.host).querySelector('[data-action="mc-open-materials"]')); await settle(ctx);
        assert.deepEqual(ctx.calls.materials, [1]);
        setLevel(ctx, "FULL"); await settle(ctx);
        ORIGINAL_ACTIONS.forEach(action => assert.ok(actionsEl(ctx.host).querySelector(`[data-action="${action}"]`)));
        assert.ok(!ctx.requests.some(entry => /rich-reply|\/send$/.test(entry.url)));
    });

    it("index.html 本地资源统一缓存键且 mailbox-readonly.js 仍紧邻 app.js", () => {
        // 键字面量只允许存在于 index.html（taskActivityCenter I-8 守卫），故从源文本派生。
        const match = indexSource.match(/styles\.css\?v=([^"'&<>]+)/);
        assert.ok(match, "index.html 必须注册 styles.css?v=");
        const cacheKey = match[1];
        const keys = Array.from(indexSource.matchAll(/\?v=([^"'&<>]+)/g)).map((item) => item[1]);
        assert.ok(keys.length > 0, "index.html 必须存在 ?v= 资源键");
        keys.forEach((key) => assert.strictEqual(key, cacheKey));
        assert.ok(!/user1-readonly/.test(indexSource), "不得残留旧缓存键");
        const escaped = cacheKey.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
        assert.match(indexSource, new RegExp(
            `<script src="mailbox-readonly\\.js\\?v=${escaped}"></script>\\s*<script src="app\\.js\\?v=${escaped}"></script>`));
    });

    it("reader 模块仍无授权入口/请求", () => {
        assert.doesNotMatch(readerSource, /read-access/);
        assert.doesNotMatch(readerSource, /mc-toggle-user1-read-access/);
        assert.doesNotMatch(readerSource, /canRead/);
    });
});
