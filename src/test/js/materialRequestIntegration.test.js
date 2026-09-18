"use strict";

// fast-p 03 材料索取集成测试（I-2/I-3/I-4 · S-2 前端契约）：
// 真实 mailbox-chat.js（+ meeting-confirmation.js 的清洗器）挂载到最小 DOM，用 stub API
// 驱动：入口门禁与位置、每次打开重读 02 的五项接口、仅 PENDING 默认勾选、
// 已提供/暂不愿提供禁选、逐字英文引言 + 无编号项目符号（按接口目录顺序、只含选中项）、
// 空选禁确认、取消零写、确认只追加到当前草稿（零状态 PUT / 零发送 / 零自动动作）、
// 已有正文与会议块保留、迟到 GET 与 revision 变化不得写入他人草稿、恶意 HTML 只作字面文本。
// 载入方式：真实组件 + 最小 DOM 进 vm 沙箱；无 npm 依赖、无真实网络。

const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const ROOT = path.join(__dirname, "..", "..", "main", "resources", "static");
const chatSource = fs.readFileSync(path.join(ROOT, "mailbox-chat.js"), "utf-8");
const chatCssSource = fs.readFileSync(path.join(ROOT, "mailbox-chat.css"), "utf-8");
const stylesSource = fs.readFileSync(path.join(ROOT, "styles.css"), "utf-8");
const meetingSource = fs.readFileSync(path.join(ROOT, "meeting-confirmation.js"), "utf-8");
const PLAN_PATH = path.join(__dirname, "..", "..", "..", "docs", "plans", "2026-09-17", "03-material-request-ui.md");
const planSource = fs.readFileSync(PLAN_PATH, "utf-8");

// ════════════════════════════════════════════════════════════════════════
// 最小真实 DOM（与 mailboxChatBehavior.test.js 同一实现来源：innerHTML 解析 +
// 事件冒泡 + querySelector/closest/dataset），用于真实挂载 mailbox-chat.js。
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
// 02 记录的真实响应形状（五项固定顺序 + 逐字英文）；前端不得重写这些字符串
// ════════════════════════════════════════════════════════════════════════

const LEAD = "To proceed, please provide the following supporting materials:";

const MATERIAL_ITEMS = [
    { code: "REQ_PUBLICATIONS", label: "代表性论文", status: "PENDING", requestText: "Copies of your representative publications" },
    { code: "REQ_PROJECTS", label: "科研项目", status: "PENDING", requestText: "Supporting documents for research projects" },
    { code: "REQ_PATENTS", label: "专利", status: "PENDING", requestText: "Patent certificates" },
    { code: "REQ_AWARDS", label: "荣誉奖项", status: "PENDING", requestText: "Certificates of honors and awards" },
    { code: "REQ_DEGREES", label: "学位", status: "PENDING", requestText: "Bachelor’s, master’s, and doctoral degree certificates" }
];

function materialItemsWith(overrides) {
    const patch = overrides || {};
    return MATERIAL_ITEMS.map((item) => Object.assign({}, item, patch[item.code] || {}));
}

function expertItem(contactId, extra) {
    return Object.assign({
        contactId,
        name: "专家" + contactId,
        email: "expert" + contactId + "@example.edu",
        accountCodes: ["acc1"],
        followed: false,
        receivedCount: 2,
        sentCount: 2,
        failedCount: 0,
        pendingCount: 1,
        waitingReply: false,
        expertTags: [],
        materialCount: 0,
        latestMessage: {
            source: "INBOUND_PROCESSING", id: 100 + contactId, direction: "INBOUND",
            subject: "Question " + contactId, time: "2026-09-07T03:00:00", sendStatus: null
        },
        latestInbound: {
            processingId: 100 + contactId, accountCode: "acc1",
            messageId: "m" + contactId, receivedAt: "2026-09-07T03:00:00"
        }
    }, extra || {});
}

// ── 挂载夹具：真实 mailbox-chat.js + stub API（无真实网络/发送） ─────────

function createSandbox(options) {
    const opts = options || {};
    const requests = [];
    const calls = { api: requests, status: [], sendRich: [], resolveMaterial: null };
    let materialCalls = 0;
    const sandbox = {
        console,
        URLSearchParams,
        setTimeout: () => 0,
        clearTimeout: () => {},
        escapeHtml: escapeHtmlLike,
        showStatus: (message, type) => calls.status.push({ message, type }),
        api: (url, requestOptions) => {
            const entry = {
                url,
                method: (requestOptions && requestOptions.method) || "GET",
                body: requestOptions && requestOptions.body
            };
            requests.push(entry);
            if (/\/material-requests$/.test(url)) {
                materialCalls += 1;
                if (opts.materialError) return Promise.reject(new Error(opts.materialError));
                if (opts.materialDeferred && materialCalls === 1) {
                    return new Promise((resolve) => { calls.resolveMaterial = resolve; });
                }
                return Promise.resolve(opts.materialItems || MATERIAL_ITEMS);
            }
            if (/\/api\/mail\/mailbox\/conversations\?/.test(url)) {
                return Promise.resolve(opts.conversations || { items: [], total: 0 });
            }
            if (/\/api\/mail\/mailbox\/conversations\/\d+\/messages/.test(url)) {
                return Promise.resolve(opts.messages || { items: [], nextBefore: null, hasMore: false });
            }
            if (/\/api\/expert-contacts\/\d+$/.test(url)) {
                return Promise.resolve({ contact: { id: 1, operatorStatus: "REPLIED", currentIndexLevel: "APPLICATION" }, mails: [] });
            }
            if (/\/api\/operator-action-logs/.test(url)) return Promise.resolve({ records: [] });
            return Promise.resolve({});
        },
        mcHostGetMeetingSummaries: (contactIds) => Promise.resolve((contactIds || []).map((id) => ({ contactId: id, activeCount: 0, next: null }))),
        mcHostDownloadCalendar: () => Promise.resolve(),
        mcHostSendRichReply: (processingId, body) => {
            calls.sendRich.push({ processingId: Number(processingId), body });
            return Promise.resolve(true);
        },
        formatBeijingMeetingShort: (value) => "SHORT(" + value + ")",
        formatBeijingMeetingRange: (startUtc, endUtc) => "RANGE(" + startUtc + "~" + endUtc + ")",
        expertTagLabels: {}
    };
    sandbox.operatorStatusOptions = [["REPLIED", "已回复"], ["INVITED", "已邀约"]];
    sandbox.indexLevelOptions = [["APPLICATION", "有效"]];
    vm.createContext(sandbox);
    vm.runInContext(chatSource, sandbox, { filename: "mailbox-chat.js" });
    if (opts.withMeeting !== false) {
        vm.runInContext(meetingSource, sandbox, { filename: "meeting-confirmation.js" });
    }
    return { sandbox, calls, requests };
}

function mountChat(options) {
    const ctx = createSandbox(options);
    const { doc, host } = createDom();
    ctx.doc = doc;
    ctx.host = host;
    ctx.sandbox.document = doc;
    ctx.sandbox.MailboxChat.mount(host, { filters: {} });
    return ctx;
}

function click(el) {
    el.dispatchEvent(new MiniEvent("click", { bubbles: true }));
}

function inputEvent(el) {
    el.dispatchEvent(new MiniEvent("input", { bubbles: true }));
}

function changeEvent(el) {
    el.dispatchEvent(new MiniEvent("change", { bubbles: true }));
}

function personFor(ctx, contactId) {
    return ctx.host.querySelectorAll(".mc-person").find((person) => String(person.dataset.contactId) === String(contactId)) || null;
}

async function openExpert(ctx, contactId) {
    click(personFor(ctx, contactId).querySelector(".mc-person-main"));
    await flush();
}

/** 挂载 + 打开专家 1 的来信人工回复区（inbound）。 */
async function bootInbound(options) {
    const ctx = mountChat(Object.assign({
        conversations: { items: [expertItem(1), expertItem(2)], total: 2 }
    }, options || {}));
    await flush();
    await openExpert(ctx, 1);
    return ctx;
}

function toolsOf(ctx) {
    return ctx.host.querySelectorAll('[data-role="manual-compose"] .mc-editor-tools button[data-action]');
}

function materialTrigger(ctx) {
    return ctx.host.querySelector('[data-action="mc-open-material-request"]');
}

function materialDialog(ctx) {
    return ctx.doc.body.querySelector(".material-request-dialog");
}

function materialEditor(ctx) {
    return ctx.host.querySelector('[aria-label="人工回复正文"]');
}

function optionInputs(ctx) {
    return ctx.doc.body.querySelectorAll(".material-request-option input");
}

function optionBoxes(ctx) {
    return ctx.doc.body.querySelectorAll(".material-request-option");
}

function previewItems(ctx) {
    const list = ctx.doc.body.querySelector('[data-role="material-request-preview-list"]');
    return list ? list.querySelectorAll("li") : [];
}

function previewLead(ctx) {
    const paper = ctx.doc.body.querySelector(".material-request-paper");
    return paper ? paper.querySelector("p") : null;
}

function countLabel(ctx) {
    const node = ctx.doc.body.querySelector('[data-role="material-request-count"]');
    return node ? node.textContent : "";
}

function applyButton(ctx) {
    return ctx.doc.body.querySelector('[data-action="mc-apply-material-request"]');
}

function cancelButton(ctx) {
    return ctx.doc.body.querySelectorAll('[data-action="mc-close-material-request"]')[1] || null;
}

function materialRequests(ctx) {
    return ctx.calls.api.filter((entry) => /\/material-requests$/.test(entry.url));
}

function materialStatusWrites(ctx) {
    return ctx.calls.api.filter((entry) => entry.method === "PUT" && /\/material-requests\//.test(entry.url));
}

async function openMaterialDialog(ctx) {
    click(materialTrigger(ctx));
    await flush();
    assert.ok(materialDialog(ctx), "材料索取弹窗必须挂载到 portal");
    return materialDialog(ctx);
}

function elementChildren(node) {
    return Array.prototype.slice.call(node.childNodes).filter((child) => child.nodeType === 1);
}

// ════════════════════════════════════════════════════════════════════════

describe("S-2: 工具栏入口与样式合同", () => {
    it("材料索取位于会议确认与跟进之间，复用 .button", async () => {
        const ctx = await bootInbound();
        assert.deepStrictEqual(toolsOf(ctx).map((button) => button.getAttribute("data-action")), [
            "mc-rich-command", "mc-rich-command", "mc-rich-command", "mc-rich-command",
            "mc-upload-attachment", "mc-open-meeting", "mc-open-material-request", "mc-open-followup"
        ], "工具栏顺序必须是 B/I/列表/链接/回形针/会议确认/材料索取/跟进");
        const button = materialTrigger(ctx);
        assert.ok(button.classList.contains("button"), "复用既有 .button");
        assert.ok(button.classList.contains("material-request-trigger"), "S-2 触发按钮 class");
        assert.strictEqual(button.getAttribute("type"), "button");
        assert.strictEqual(button.textContent.trim(), "材料索取");
    });

    it("会议组件缺席：不渲染材料索取入口", async () => {
        const ctx = await bootInbound({ withMeeting: false });
        assert.strictEqual(materialTrigger(ctx), null, "组件缺席时不得渲染材料索取入口");
        assert.strictEqual(ctx.host.querySelector('[data-action="mc-open-meeting"]'), null, "会议入口同样缺席");
    });

    it("styles.css 逐字包含 S-2 块且未追加到文件末尾；mailbox-chat.css 保持字节锁定", () => {
        const blocks = Array.from(planSource.matchAll(/```css\n([\s\S]*?)```/g)).map((match) => match[1]);
        const s2 = blocks.find((block) => block.includes("Manual material request: mailbox composer only."));
        assert.ok(s2, "计划必须带 S-2 的逐字 CSS 块");
        assert.ok(stylesSource.includes(s2), "styles.css 必须逐字包含 S-2 块");
        assert.ok(!stylesSource.endsWith(s2), "S-2 不得追加到 styles.css 末尾（meeting-mail-07 的块必须仍在最后）");
        const withoutBlock = stylesSource.replace(s2, "");
        ["material-request-trigger", "material-request-dialog", "material-request-close",
            "material-request-head", "material-request-body", "material-request-heading",
            "material-request-options", "material-request-option", "material-request-paper",
            "material-request-error", "material-request-actions"].forEach((cls) => {
            assert.ok(s2.includes("." + cls), cls + " 必须在 S-2 块内声明");
            assert.ok(!withoutBlock.includes("." + cls), cls + " 只能在 S-2 块内声明");
        });
        assert.ok(!chatCssSource.includes("material-request"), "mailbox-chat.css 必须保持原样");
        assert.ok(!/"style="/.test(chatSource), "mailbox-chat.js 模板不得内联样式");
    });
});

describe("I-2: 弹窗每次重读五项、只勾待提供、预览逐字", () => {
    it("每次打开重读五项；仅 PENDING 默认勾选，已提供/暂不愿提供禁选并标注状态", async () => {
        const ctx = await bootInbound({
            materialItems: materialItemsWith({ REQ_PATENTS: { status: "PROVIDED" }, REQ_AWARDS: { status: "DECLINED" } })
        });
        await openMaterialDialog(ctx);
        const gets = materialRequests(ctx);
        assert.strictEqual(gets.length, 1, "打开一次只读一次五项接口");
        assert.strictEqual(gets[0].url, "/api/expert-contacts/1/material-requests");
        assert.strictEqual(gets[0].method, "GET");

        const boxes = optionBoxes(ctx);
        assert.strictEqual(boxes.length, 5, "弹窗必须展示五项");
        assert.deepStrictEqual(boxes.map((box) => box.querySelector("span").textContent),
            ["代表性论文", "科研项目", "专利", "荣誉奖项", "学位"], "顺序必须与接口目录一致");
        assert.deepStrictEqual(boxes.map((box) => box.querySelector("small").textContent),
            ["待提供", "待提供", "已提供", "暂不愿提供", "待提供"]);
        const inputs = optionInputs(ctx);
        assert.deepStrictEqual(inputs.map((input) => input.checked), [true, true, false, false, true], "只有 PENDING 默认勾选");
        assert.deepStrictEqual(inputs.map((input) => input.disabled), [false, false, true, true, false], "已提供/暂不愿提供禁选");

        click(cancelButton(ctx));
        await flush();
        assert.strictEqual(materialDialog(ctx), null, "取消必须关闭弹窗");
        await openMaterialDialog(ctx);
        assert.strictEqual(materialRequests(ctx).length, 2, "每次打开都必须重读五项");
    });

    it("预览逐字使用响应 requestText：固定引言 + 无编号项目符号，只含选中项、按目录顺序", async () => {
        const ctx = await bootInbound({
            materialItems: materialItemsWith({ REQ_PATENTS: { status: "PROVIDED" }, REQ_AWARDS: { status: "DECLINED" } })
        });
        await openMaterialDialog(ctx);
        assert.strictEqual(previewLead(ctx).textContent, LEAD, "固定引言必须逐字");
        assert.deepStrictEqual(previewItems(ctx).map((li) => li.textContent), [
            "Copies of your representative publications",
            "Supporting documents for research projects",
            "Bachelor’s, master’s, and doctoral degree certificates"
        ], "项目符号只含选中项且逐字英文");
        assert.strictEqual(ctx.doc.body.querySelector(".material-request-paper ol"), null, "不得使用编号列表");
        assert.strictEqual(countLabel(ctx), "已选 3 项");
        assert.strictEqual(applyButton(ctx).disabled, false);
    });

    it("勾选变化实时重算预览与计数；全部取消后确认禁用", async () => {
        const ctx = await bootInbound();
        await openMaterialDialog(ctx);
        const inputs = optionInputs(ctx);
        assert.strictEqual(countLabel(ctx), "已选 5 项", "五项皆待提供时默认全选");
        inputs[3].checked = false;
        changeEvent(inputs[3]);
        assert.strictEqual(countLabel(ctx), "已选 4 项");
        assert.deepStrictEqual(previewItems(ctx).map((li) => li.textContent), [
            "Copies of your representative publications",
            "Supporting documents for research projects",
            "Patent certificates",
            "Bachelor’s, master’s, and doctoral degree certificates"
        ]);
        inputs.forEach((input) => {
            if (input.disabled) return;
            input.checked = false;
            changeEvent(input);
        });
        assert.strictEqual(countLabel(ctx), "已选 0 项");
        assert.strictEqual(previewItems(ctx).length, 0, "空选时预览没有项目符号");
        assert.strictEqual(applyButton(ctx).disabled, true, "空选必须禁用确认");
    });

    it("五项接口失败：错误留在弹窗内、确认禁用、不写编辑器", async () => {
        const ctx = await bootInbound({ materialError: "503" });
        const before = materialEditor(ctx).innerHTML;
        await openMaterialDialog(ctx);
        const error = ctx.doc.body.querySelector(".material-request-error");
        assert.ok(!error.hidden, "失败必须显示弹窗内错误");
        assert.match(error.textContent, /材料状态加载失败: 503/);
        assert.strictEqual(applyButton(ctx).disabled, true, "加载失败必须禁用确认");
        assert.strictEqual(optionInputs(ctx).length, 0, "失败时不得渲染任何可勾选项");
        assert.strictEqual(materialEditor(ctx).innerHTML, before, "失败不得写编辑器");
        assert.deepStrictEqual(materialStatusWrites(ctx), [], "失败路径不得发 PUT");
    });
});

describe("I-3: 只操作当前草稿", () => {
    it("取消：零请求、零状态写、草稿不变", async () => {
        const ctx = await bootInbound();
        const box = materialEditor(ctx);
        box.innerText = "Dear Professor,";
        inputEvent(box);
        const before = materialEditor(ctx).innerHTML;
        await openMaterialDialog(ctx);
        click(cancelButton(ctx));
        await flush();
        assert.strictEqual(materialDialog(ctx), null, "取消必须关闭弹窗");
        assert.strictEqual(materialEditor(ctx).innerHTML, before, "取消不得改草稿");
        assert.deepStrictEqual(materialStatusWrites(ctx), [], "取消不得改材料状态");
        assert.strictEqual(ctx.calls.sendRich.length, 0, "取消不得发送");
    });

    it("确认：只追加 <p> + <ul><li>，零状态写、零发送，重挂载后仍在", async () => {
        const ctx = await bootInbound({
            materialItems: materialItemsWith({ REQ_PATENTS: { status: "PROVIDED" }, REQ_AWARDS: { status: "DECLINED" } })
        });
        const box = materialEditor(ctx);
        box.innerText = "Dear Professor,";
        inputEvent(box);
        await openMaterialDialog(ctx);
        click(applyButton(ctx));
        await flush();
        assert.strictEqual(materialDialog(ctx), null, "确认后必须关闭弹窗");
        assert.deepStrictEqual(materialStatusWrites(ctx), [], "确认不得改材料状态");
        assert.strictEqual(ctx.calls.sendRich.length, 0, "确认不得发送邮件");
        const children = elementChildren(materialEditor(ctx));
        assert.strictEqual(children.length, 2, "只追加引言段与项目符号列表");
        assert.strictEqual(children[0].tagName, "P");
        assert.strictEqual(children[0].textContent, LEAD);
        assert.strictEqual(children[1].tagName, "UL");
        assert.deepStrictEqual(children[1].childNodes.map((li) => li.textContent), [
            "Copies of your representative publications",
            "Supporting documents for research projects",
            "Bachelor’s, master’s, and doctoral degree certificates"
        ]);
        assert.ok(materialEditor(ctx).innerHTML.includes("Dear Professor,"), "原有正文必须保留");
        await openExpert(ctx, 2);
        await openExpert(ctx, 1);
        const restored = materialEditor(ctx);
        assert.ok(restored.innerHTML.includes("Dear Professor,"), "恢复后原正文仍在");
        assert.ok(restored.innerHTML.includes(LEAD), "恢复后引言段仍在");
        assert.deepStrictEqual(restored.querySelectorAll("ul li").map((li) => li.textContent), [
            "Copies of your representative publications",
            "Supporting documents for research projects",
            "Bachelor’s, master’s, and doctoral degree certificates"
        ], "恢复后项目符号仍在且顺序不变");
    });

    it("确认前编辑器 revision 变化：不得追加", async () => {
        const ctx = await bootInbound();
        await openMaterialDialog(ctx);
        const box = materialEditor(ctx);
        box.innerText = "typed while dialog open";
        inputEvent(box);
        click(applyButton(ctx));
        await flush();
        assert.strictEqual(materialEditor(ctx).innerHTML, "typed while dialog open", "revision 变化后确认不得写入");
    });

    it("迟到 GET：响应前切到另一专家，响应不得写入该专家草稿", async () => {
        const ctx = await bootInbound({ materialDeferred: true });
        click(materialTrigger(ctx));
        await flush();
        assert.ok(materialDialog(ctx), "GET 未返回也应先挂载弹窗");
        await openExpert(ctx, 2);
        assert.strictEqual(materialDialog(ctx), null, "切换专家必须关闭材料弹窗");
        const box = materialEditor(ctx);
        box.innerText = "expert 2 draft";
        inputEvent(box);
        assert.strictEqual(typeof ctx.calls.resolveMaterial, "function", "夹具必须暴露迟到的 GET 决议点");
        ctx.calls.resolveMaterial(MATERIAL_ITEMS);
        await flush();
        assert.strictEqual(materialEditor(ctx).innerHTML, "expert 2 draft", "迟到响应不得写入新专家草稿");
    });

    it("已有会议块：追加材料不改写会议块，且会议块与材料段一起经草稿恢复", async () => {
        const ctx = await bootInbound();
        const box = materialEditor(ctx);
        box.innerHTML = '<div class="meeting-body-block" data-meeting-block="true"><p>Meeting body kept</p></div>';
        inputEvent(box);
        await openMaterialDialog(ctx);
        click(applyButton(ctx));
        await flush();
        const current = materialEditor(ctx);
        const blocks = current.querySelectorAll('[data-meeting-block="true"]');
        assert.strictEqual(blocks.length, 1, "会议块必须保留且唯一");
        assert.ok(blocks[0].textContent.includes("Meeting body kept"), "会议块正文不得被改写");
        assert.strictEqual(elementChildren(current)[0], blocks[0], "材料段只能追加在既有会议块之后");
        await openExpert(ctx, 2);
        await openExpert(ctx, 1);
        const restored = materialEditor(ctx);
        assert.strictEqual(restored.querySelectorAll('[data-meeting-block="true"]').length, 1, "恢复后会议块仍在");
        assert.ok(restored.textContent.includes("Meeting body kept"), "恢复后会议块正文仍在");
        assert.ok(restored.innerHTML.includes(LEAD), "恢复后材料段仍在");
        assert.strictEqual(ctx.calls.sendRich.length, 0, "填入材料不得自动发送");
    });
});

describe("I-4: label/requestText 只作文本（XSS）", () => {
    it("恶意 HTML 在预览与草稿里都是字面文本", async () => {
        const evil = '<img src=x onerror="window.__pwned=1">';
        const items = MATERIAL_ITEMS.map((item, index) => index === 0
            ? Object.assign({}, item, { label: evil, requestText: evil })
            : item);
        const ctx = await bootInbound({ materialItems: items });
        await openMaterialDialog(ctx);
        const first = optionBoxes(ctx)[0];
        assert.strictEqual(first.querySelector("span").textContent, evil, "label 必须按文本渲染");
        assert.strictEqual(first.querySelectorAll("span img").length, 0, "不得生成 img 元素");
        assert.strictEqual(previewItems(ctx)[0].textContent, evil, "预览必须按文本渲染");
        assert.strictEqual(ctx.doc.body.querySelectorAll(".material-request-paper img").length, 0, "预览不得生成可执行标签");
        click(applyButton(ctx));
        await flush();
        const list = materialEditor(ctx).querySelector("ul");
        assert.strictEqual(list.querySelectorAll("li")[0].textContent, evil, "草稿必须按文本渲染");
        assert.strictEqual(list.querySelectorAll("img").length, 0, "草稿不得生成 img 元素");
        assert.ok(materialEditor(ctx).innerHTML.includes("&lt;img"), "草稿里必须是转义后的字面文本");
        assert.strictEqual(ctx.sandbox.__pwned, undefined, "不得执行注入脚本");
    });
});
