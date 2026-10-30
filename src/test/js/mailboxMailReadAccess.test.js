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
        canRead: access[contactId] === true
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
            if (method === "PUT") access[id] = true;
            if (method === "DELETE") access[id] = false;
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

function grantButton(host) {
    return host.querySelector('[data-action="mc-toggle-user1-read-access"]');
}

function grantBadge(host) {
    return host.querySelector('[data-role="mc-user1-read-access-status"]');
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

describe("04 S-1：admin 专家头部授权入口", () => {
    it("加载态：末尾两个节点为固定按钮 + 徽标，且按钮禁用", async () => {
        const gate = deferred();
        const ctx = mountChat({
            route: (url, method, body, next) => (/\/read-access\/user1$/.test(url) && method === "GET")
                ? gate.promise
                : next(url, method, body)
        });
        await settle(ctx);
        selectPerson(ctx.host, 1);
        await flush();

        const actions = actionsEl(ctx.host);
        assert.ok(actions, "header 必须有 .mc-actions");
        const children = actions.children;
        const button = children[children.length - 2];
        const badge = children[children.length - 1];
        assert.strictEqual(button.tagName, "BUTTON");
        assert.strictEqual(button.getAttribute("data-action"), "mc-toggle-user1-read-access");
        assert.strictEqual(button.getAttribute("data-contact-id"), "1");
        assert.strictEqual(button.getAttribute("type"), "button");
        assert.ok(button.classList.contains("button"));
        assert.strictEqual(button.disabled, true, "加载中必须禁用");
        assert.strictEqual(button.textContent, "授权状态加载中…");

        assert.strictEqual(badge.tagName, "SPAN");
        assert.ok(badge.classList.contains("mc-badge"));
        assert.strictEqual(badge.getAttribute("data-role"), "mc-user1-read-access-status");
        assert.strictEqual(badge.getAttribute("aria-live"), "polite");
        assert.strictEqual(badge.textContent, "user1：读取中");

        gate.resolve({ username: "user1", contactId: 1, canRead: true });
        await flush();
        assert.strictEqual(button.textContent, "取消 user1 授权");
        assert.strictEqual(button.disabled, false);
        assert.strictEqual(badge.textContent, "user1：可查看");
    });

    it("canRead=false：授权 user1 查看 / user1：未授权 / 可点击；原动作全部保留且在其之前", async () => {
        const ctx = await boot({ access: { 1: false } });
        selectPerson(ctx.host, 1);
        await settle(ctx);

        const actions = actionsEl(ctx.host);
        const button = grantButton(ctx.host);
        const badge = grantBadge(ctx.host);
        assert.ok(button && badge);
        assert.strictEqual(button.textContent, "授权 user1 查看");
        assert.strictEqual(button.disabled, false);
        assert.strictEqual(badge.textContent, "user1：未授权");

        // 原 header 动作保持存在，且都排在新节点之前（S-1：追加在最后）。
        const children = actions.children;
        const buttonIndex = children.indexOf(button);
        ORIGINAL_ACTIONS.forEach((action) => {
            const node = actions.querySelector(`[data-action="${action}"]`);
            assert.ok(node, `原动作 ${action} 必须保留`);
            assert.ok(children.indexOf(node) < buttonIndex, `${action} 必须位于授权入口之前`);
        });
        assert.strictEqual(children[children.length - 1], badge);

        // 复用既有 class / 无新 CSS / 无 inline style。
        const html = actions.innerHTML;
        assert.doesNotMatch(html, /style=/);
        assert.doesNotMatch(html, /data-tone=/);
        assert.strictEqual(button.className, "button");
        assert.strictEqual(badge.className, "mc-badge");
        assert.ok(chatCssSource.includes(".mail-chat .mc-actions{display:flex"));
        assert.ok(chatCssSource.includes(".mail-chat .mc-badge{"));
        assert.ok(chatCssSource.includes(".mail-chat .button:disabled{opacity:.45"));
        assert.ok(chatCssSource.includes("outline:2px solid #3b82f6"));
    });

    it("container id 与文案矩阵在真实源码中唯一（无第二套文案）", () => {
        assert.ok(chatSource.includes('data-action="mc-toggle-user1-read-access"'));
        assert.ok(chatSource.includes('data-role="mc-user1-read-access-status"'));
        assert.ok(chatSource.includes("授权状态加载中…"));
        assert.ok(chatSource.includes("取消 user1 授权"));
        assert.ok(chatSource.includes("授权 user1 查看"));
        assert.ok(chatSource.includes("user1：可查看"));
        assert.ok(chatSource.includes("user1：未授权"));
        assert.ok(chatSource.includes("user1：授权操作失败，请重试"));
        assert.ok(chatSource.includes("重试授权状态"));
    });
});

// ════════════════════════════════════════════════════════════════════════
// I-1：仅 admin 渲染；目标捕获于点击时
// ════════════════════════════════════════════════════════════════════════

describe("04 I-1：身份与目标不能错位", () => {
    it("非 admin（reader/未知身份）不渲染入口、零 grant 请求，伪造按钮也不发请求", async () => {
        for (const username of ["user1", "console"]) {
            const ctx = await boot({ authMe: { authenticated: true, username } }, { sessionUser: username });
            selectPerson(ctx.host, 1);
            await settle(ctx);

            assert.strictEqual(grantButton(ctx.host), null, `${username} 不得出现授权按钮`);
            assert.strictEqual(grantBadge(ctx.host), null, `${username} 不得出现授权徽标`);
            assert.strictEqual(grantRequests(ctx).length, 0);

            const actions = actionsEl(ctx.host);
            actions.insertAdjacentHTML("beforeend",
                '<button class="button" data-action="mc-toggle-user1-read-access" data-contact-id="1">伪造</button>');
            click(grantButton(ctx.host));
            await flush();
            assert.strictEqual(grantRequests(ctx).length, 0, `${username} 伪造点击不得触发授权请求`);
        }
    });

    it("点击请求的 URL 固定为点击时的专家 + 目标 user1", async () => {
        const ctx = await boot({ access: { 1: false } });
        selectPerson(ctx.host, 1);
        await settle(ctx);
        click(grantButton(ctx.host));
        await settle(ctx);
        const writes = grantRequests(ctx).filter((entry) => entry.method !== "GET");
        assert.strictEqual(writes.length, 1);
        assert.strictEqual(writes[0].url, grantUrl(1));
        assert.strictEqual(writes[0].body, undefined, "不得在请求体里携带 actor/role/user");
    });
});

// ════════════════════════════════════════════════════════════════════════
// I-2：服务端状态为准
// ════════════════════════════════════════════════════════════════════════

describe("04 I-2：只显示服务端返回的 canRead", () => {
    it("GET false → PUT true → DELETE false 闭环", async () => {
        const ctx = await boot({ access: { 1: false } });
        selectPerson(ctx.host, 1);
        await settle(ctx);
        assert.deepStrictEqual(grantMethods(ctx), ["GET"]);
        assert.strictEqual(grantButton(ctx.host).textContent, "授权 user1 查看");

        click(grantButton(ctx.host));
        await settle(ctx);
        assert.deepStrictEqual(grantMethods(ctx), ["GET", "PUT"]);
        let button = grantButton(ctx.host);
        assert.strictEqual(button.textContent, "取消 user1 授权");
        assert.strictEqual(button.disabled, false);
        assert.strictEqual(grantBadge(ctx.host).textContent, "user1：可查看");

        click(button);
        await settle(ctx);
        assert.deepStrictEqual(grantMethods(ctx), ["GET", "PUT", "DELETE"]);
        button = grantButton(ctx.host);
        assert.strictEqual(button.textContent, "授权 user1 查看");
        assert.strictEqual(button.disabled, false);
        assert.strictEqual(grantBadge(ctx.host).textContent, "user1：未授权");
    });

    it("在途写入：保存中… + 保留上次确认徽标 + 禁用；双击只有一笔在途写", async () => {
        const gate = deferred();
        const ctx = mountChat({
            access: { 1: false },
            route: (url, method, body, next) => (method === "PUT" && /\/read-access\/user1$/.test(url))
                ? gate.promise
                : next(url, method, body)
        });
        await settle(ctx);
        selectPerson(ctx.host, 1);
        await settle(ctx);

        const button = grantButton(ctx.host);
        click(button);
        click(button);
        await flush();
        assert.strictEqual(grantRequests(ctx).filter((entry) => entry.method === "PUT").length, 1, "双击只允许一笔在途写");
        assert.strictEqual(button.textContent, "保存中…");
        assert.strictEqual(button.disabled, true);
        assert.strictEqual(grantBadge(ctx.host).textContent, "user1：未授权", "保存期间保留上次已确认文字");

        gate.resolve({ username: "user1", contactId: 1, canRead: true });
        await flush();
        assert.strictEqual(button.textContent, "取消 user1 授权");
        assert.strictEqual(button.disabled, false);
        assert.strictEqual(grantBadge(ctx.host).textContent, "user1：可查看");
    });

    it("读取失败进入重试态；重试只重新 GET，不重发写入", async () => {
        let getCalls = 0;
        const ctx = await boot({
            route: (url, method, body, next) => {
                if (!/\/read-access\/user1$/.test(url)) return next(url, method, body);
                if (method !== "GET") return next(url, method, body);
                getCalls += 1;
                if (getCalls === 1) return Promise.reject(new Error("offline"));
                return next(url, method, body);
            }
        });
        selectPerson(ctx.host, 1);
        await settle(ctx);

        let button = grantButton(ctx.host);
        assert.strictEqual(button.textContent, "重试授权状态");
        assert.strictEqual(button.disabled, false);
        assert.strictEqual(grantBadge(ctx.host).textContent, "user1：授权操作失败，请重试");

        click(button);
        await settle(ctx);
        assert.deepStrictEqual(grantMethods(ctx), ["GET", "GET"], "失败重试只能重新 GET");
        button = grantButton(ctx.host);
        assert.strictEqual(button.textContent, "授权 user1 查看");
        assert.strictEqual(grantBadge(ctx.host).textContent, "user1：未授权");
    });

    it("写入失败不乐观翻转，且只能 GET 重试", async () => {
        const ctx = await boot({
            route: (url, method, body, next) => (method === "PUT" && /\/read-access\/user1$/.test(url))
                ? Promise.reject(new Error("网络不可用"))
                : next(url, method, body)
        });
        selectPerson(ctx.host, 1);
        await settle(ctx);
        click(grantButton(ctx.host));
        await settle(ctx);

        let button = grantButton(ctx.host);
        assert.strictEqual(button.textContent, "重试授权状态");
        assert.strictEqual(grantBadge(ctx.host).textContent, "user1：授权操作失败，请重试");
        assert.strictEqual(grantButton(ctx.host).disabled, false);

        click(button);
        await settle(ctx);
        assert.deepStrictEqual(grantMethods(ctx), ["GET", "PUT", "GET"], "失败后点击只能是 GET 重试");
        assert.strictEqual(grantButton(ctx.host).textContent, "授权 user1 查看", "服务端仍为未授权");
    });

    it("回包形状不匹配（username/contactId/canRead）视为错误，不显示成功", async () => {
        const cases = [
            { username: "admin", contactId: 1, canRead: true },
            { username: "user1", contactId: 2, canRead: true },
            { username: "user1", contactId: 1, canRead: "yes" }
        ];
        for (const payload of cases) {
            const ctx = await boot({
                route: (url, method, body, next) => (/\/read-access\/user1$/.test(url) && method === "GET")
                    ? Promise.resolve(payload)
                    : next(url, method, body)
            });
            selectPerson(ctx.host, 1);
            await settle(ctx);
            assert.strictEqual(grantButton(ctx.host).textContent, "重试授权状态", JSON.stringify(payload));
            assert.strictEqual(grantBadge(ctx.host).textContent, "user1：授权操作失败，请重试");
        }
    });

    it("PUT 回包 canRead 与预期不符视为错误", async () => {
        const ctx = await boot({
            access: { 1: false },
            route: (url, method, body, next) => (method === "PUT" && /\/read-access\/user1$/.test(url))
                ? Promise.resolve({ username: "user1", contactId: 1, canRead: false })
                : next(url, method, body)
        });
        selectPerson(ctx.host, 1);
        await settle(ctx);
        click(grantButton(ctx.host));
        await settle(ctx);
        assert.strictEqual(grantButton(ctx.host).textContent, "重试授权状态");
        assert.strictEqual(grantBadge(ctx.host).textContent, "user1：授权操作失败，请重试");
    });
});

// ════════════════════════════════════════════════════════════════════════
// I-3：跨专家异步结果隔离
// ════════════════════════════════════════════════════════════════════════

describe("04 I-3：快切专家/卸载/身份", () => {
    it("A 慢 GET 后切 B：A 回包不覆盖 B；回 A 重新 GET", async () => {
        const gates = { 1: deferred(), 2: deferred() };
        const ctx = mountChat({
            route: (url, method, body, next) => {
                const match = url.match(/^\/api\/mail\/mailbox\/conversations\/(\d+)\/read-access\/user1$/);
                if (match && method === "GET") return gates[Number(match[1])].promise;
                return next(url, method, body);
            }
        });
        await settle(ctx);
        selectPerson(ctx.host, 1);
        await flush();
        selectPerson(ctx.host, 2);
        await flush();

        gates[2].resolve({ username: "user1", contactId: 2, canRead: true });
        await flush();
        assert.strictEqual(grantButton(ctx.host).getAttribute("data-contact-id"), "2");
        assert.strictEqual(grantButton(ctx.host).textContent, "取消 user1 授权");
        assert.strictEqual(grantBadge(ctx.host).textContent, "user1：可查看");

        gates[1].resolve({ username: "user1", contactId: 1, canRead: false });
        await flush();
        assert.strictEqual(grantButton(ctx.host).getAttribute("data-contact-id"), "2", "迟到的 A 回包不得改写 B");
        assert.strictEqual(grantButton(ctx.host).textContent, "取消 user1 授权");
        assert.strictEqual(grantBadge(ctx.host).textContent, "user1：可查看");

        selectPerson(ctx.host, 1);
        await flush();
        assert.strictEqual(grantButton(ctx.host).getAttribute("data-contact-id"), "1");
        assert.strictEqual(grantButton(ctx.host).textContent, "授权 user1 查看");
        assert.strictEqual(grantRequests(ctx).length, 3, "回到 A 必须重新 GET 最新状态");
    });

    it("A 写入在途时切 B：写入仍落到 A，B 显示自己的状态", async () => {
        const gate = deferred();
        let ctx = null;
        ctx = mountChat({
            access: { 1: false, 2: false },
            route: (url, method, body, next) => (method === "PUT" && /\/read-access\/user1$/.test(url))
                ? gate.promise.then((value) => { ctx.access[1] = value.canRead; return value; })
                : next(url, method, body)
        });
        await settle(ctx);
        selectPerson(ctx.host, 1);
        await settle(ctx);
        click(grantButton(ctx.host));
        await flush();

        selectPerson(ctx.host, 2);
        await settle(ctx);
        assert.strictEqual(grantButton(ctx.host).getAttribute("data-contact-id"), "2");
        assert.strictEqual(grantButton(ctx.host).textContent, "授权 user1 查看");

        gate.resolve({ username: "user1", contactId: 1, canRead: true });
        await settle(ctx);
        const writes = grantRequests(ctx).filter((entry) => entry.method !== "GET");
        assert.strictEqual(writes.length, 1);
        assert.strictEqual(writes[0].url, grantUrl(1), "写入目标仍是点击时的 A");
        assert.strictEqual(grantButton(ctx.host).getAttribute("data-contact-id"), "2");
        assert.strictEqual(grantButton(ctx.host).textContent, "授权 user1 查看", "A 的写入结果不得画到 B");

        selectPerson(ctx.host, 1);
        await settle(ctx);
        assert.strictEqual(grantButton(ctx.host).textContent, "取消 user1 授权", "回 A 重新 GET 得到最新状态");
    });

    it("unmount 后的迟到回包不创建/更新节点", async () => {
        const gate = deferred();
        const ctx = mountChat({
            route: (url, method, body, next) => (/\/read-access\/user1$/.test(url) && method === "GET")
                ? gate.promise
                : next(url, method, body)
        });
        await settle(ctx);
        selectPerson(ctx.host, 1);
        await flush();
        ctx.api.unmount();
        gate.resolve({ username: "user1", contactId: 1, canRead: true });
        await settle(ctx);
        assert.strictEqual(ctx.host.innerHTML, "");
        assert.strictEqual(grantButton(ctx.host), null);
    });
});

// ════════════════════════════════════════════════════════════════════════
// I-4：原 header 行为、资源版本与 reader 隔离
// ════════════════════════════════════════════════════════════════════════

describe("04 I-4：原管理行为与资源版本", () => {
    it("原 header 动作可点击；授权点击不触发草稿/发信/材料等其他请求", async () => {
        const ctx = await boot({ access: { 1: false } });
        selectPerson(ctx.host, 1);
        await settle(ctx);

        click(actionsEl(ctx.host).querySelector('[data-action="mc-open-materials"]'));
        await settle(ctx);
        assert.deepStrictEqual(ctx.calls.materials, [1], "原材料入口必须仍可用");

        const before = ctx.requests.length;
        click(grantButton(ctx.host));
        await settle(ctx);
        const newRequests = ctx.requests.slice(before);
        assert.ok(newRequests.length > 0);
        newRequests.forEach((entry) => {
            assert.match(entry.url, /\/read-access\/user1$/, `授权不得触发 ${entry.url}`);
            assert.doesNotMatch(entry.url, /drafts|send|suspension|follow/);
        });
        // 授权后原动作节点未被替换。
        ORIGINAL_ACTIONS.forEach((action) => {
            assert.ok(actionsEl(ctx.host).querySelector(`[data-action="${action}"]`), `${action} 必须保留`);
        });
    });

    it("index.html 本地资源统一缓存键且 mailbox-readonly.js 仍紧邻 app.js", () => {
        // 键字面量只允许存在于 index.html（taskActivityCenter I-8 守卫），故从源文本派生。
        const match = indexSource.match(/styles\.css\?v=([^"'&<>]+)/);
        assert.ok(match, "index.html 必须注册 styles.css?v=");
        const cacheKey = match[1];
        assert.match(cacheKey, /^[0-9]{8}-discovery-source-retry$/, "本步必须统一到 discovery-source-retry 键");
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
