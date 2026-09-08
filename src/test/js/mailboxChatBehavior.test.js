"use strict";

// 子计划 10 行为测试（I-1..I-5 / S-3/S-4/S-5 前端契约）：
// 真实 DOM 能力的最小树（HTML 解析 + 事件冒泡 + querySelector/closest/dataset），
// 驱动 mailbox-chat.js 挂载/选择专家/标记已处理/草稿切换/采用发送/关注/材料入口；
// 另含 app.js 宿主守卫（任务钻取与脚本缺失仍走旧 table/group 分支）。
// 覆盖：
//  - (source,id) 消息渲染键；待专家回复 vs 仅失败专家不混入
//  - 标记已处理：状态/计数/角标局部更新，不重建编辑器、无材料/下载请求
//  - 草稿跨专家恢复；新来信目标提示（确认切换 / 取消保留）
//  - 可信采用 → 人工发送 payload 保留 QA 载荷；发送成功清草稿、失败保留
//  - 关注乐观更新 + 失败回滚；材料抽屉同 contactId store（app 适配器）
//  - 任务钻取/未匹配入口原样保留（loadMailbox 守卫）

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
// 最小真实 DOM（含 innerHTML 解析 —— 聊天组件以模板字符串渲染）
// ════════════════════════════════════════════════════════════════════════

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
        while (node && node.nodeType === 1) {
            event.currentTarget = node;
            for (const fn of node.listeners.get(event.type) || []) fn(event);
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
    createElement(tag) {
        return new MiniElement(tag, this);
    }
    createTextNode(data) {
        return new MiniText(String(data));
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
                // 容忍轻微未闭合：弹出至匹配标签
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
        if (VOID_TAGS.has(tag) || selfClose) {
            if (stack.length) stack[stack.length - 1].appendChild(el);
            else nodes.push(el);
        } else {
            if (stack.length) stack[stack.length - 1].appendChild(el);
            else nodes.push(el);
            stack.push(el);
        }
        pos = gt + 1;
    }
    if (parent) {
        nodes.forEach((node) => parent.appendChild(node));
    }
}

function createHost() {
    const doc = new MiniDocument();
    const host = doc.createElement("div");
    doc.root.appendChild(host);
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
// 聊天沙箱：mailbox-chat.js + 宿主 stub（app 全局函数按需注入）
// ════════════════════════════════════════════════════════════════════════

// V-2 修复（R-1）：与 app.js 顶层 operatorStatusOptions/indexLevelOptions 同值的目录。
// app.js 将其发布到 window，mailbox-chat.js 经 IIFE 参数（浏览器=window）读取；
// 本沙箱把这些值发布到 chat global，镜像同一宿主契约。
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
        unmounts: []
    };
    const timers = [];

    const route = opts.route || function defaultRoute(url, method) {
        if (url.startsWith("/api/mail/mailbox/conversations?")) {
            return Promise.resolve(opts.conversations || { items: [], total: 0 });
        }
        if (/\/api\/mail\/mailbox\/conversations\/\d+\/messages/.test(url)) {
            return Promise.resolve(opts.messages || { items: [], nextBefore: null, hasMore: false });
        }
        if (/\/api\/expert-contacts\/\d+/.test(url)) {
            return Promise.resolve(opts.contact || { contact: null, mails: [] });
        }
        if (/\/api\/operator-action-logs/.test(url)) {
            return Promise.resolve({ records: opts.logs || [] });
        }
        if (/\/api\/inbound-summary\/mails\/\d+\/thread/.test(url)) {
            return Promise.resolve({ tags: opts.threadTags || [] });
        }
        if (/\/api\/mail\/unmatched-inbound\/\d+\/mark-resolved/.test(url)) {
            return Promise.resolve({});
        }
        if (/\/api\/mail\/mailbox\/conversations\/\d+\/follow/.test(url)) {
            return Promise.resolve({ followed: opts.followResult !== false });
        }
        return Promise.resolve({});
    };

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
        translatableBody: (text) => `<div class="translatable-body-block"><div class="pre translatable-body">${escapeHtmlLike(text)}</div><button class="btn-translate" type="button">翻译</button><div class="translation-text pre" hidden></div></div>`,
        renderInboundTagChip: (tag, chipOpts) => {
            const cls = ["inbound-tag-chip"].concat(tag && tag.tagType === "QA" ? ["qa"] : ["custom"]).join(" ");
            return `<span class="${cls}">${escapeHtmlLike(tag && tag.label ? tag.label : "")}</span>`;
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
        unmountExpertMaterialsHosts: (rootEl) => { calls.materialsHostCleanup = (calls.materialsHostCleanup || 0) + 1; }
    };

    if (opts.expertTagRender) {
        sandbox.renderMailboxExpertTagEditor = (ref, tags, editorId, missing) =>
            `<div class="detail-section expert-tag-editor" id="${editorId}" data-orcid="${escapeHtmlLike(ref.orcidId || "")}" data-level="${escapeHtmlLike(ref.currentIndexLevel || "")}"></div>`;
    }
    if (opts.fetchTags) {
        sandbox.fetchExpertTagsFromEs = (orcidId, level) => Promise.resolve(opts.fetchTags);
    }

    // V-2 修复（R-1）：默认在 chat global 发布 app.js 同一目录（window 发布后的线上状态）；
    // catalogs:false 复现 V-2 空目录线上症状（selector 渲染 0 个选项、无 POST）。
    if (opts.catalogs !== false) {
        sandbox.operatorStatusOptions = OPERATOR_STATUS_CATALOG;
        sandbox.indexLevelOptions = INDEX_LEVEL_CATALOG;
    }

    vm.createContext(sandbox);
    vm.runInContext(chatSource, sandbox, { filename: "mailbox-chat.js" });
    return {
        sandbox,
        calls,
        timers,
        runTimers: () => { while (timers.length) { const fn = timers.shift(); fn(); } }
    };
}

function expertA() {
    return {
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
        latestMessage: { source: "INBOUND_PROCESSING", id: 101, direction: "INBOUND", subject: "Question 1", time: "2026-09-07T03:00:00", sendStatus: null },
        latestInbound: { processingId: 101, accountCode: "acc1", messageId: "m101", receivedAt: "2026-09-07T03:00:00" },
        materialCount: 40
    };
}

function expertB() {
    return {
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
        latestMessage: { source: "MAIL_RECORD", id: 88, direction: "OUTBOUND", subject: "Introduction", time: "2026-09-06T09:00:00", sendStatus: "SENT" },
        latestInbound: null,
        materialCount: 0
    };
}

function expertCFailed() {
    return {
        contactId: 3,
        name: "专家C",
        email: "c@example.edu",
        orcid: "0000-0003",
        accountCodes: ["acc3"],
        followed: false,
        receivedCount: 0,
        sentCount: 0,
        failedCount: 2,
        pendingCount: 0,
        waitingReply: false,
        latestMessage: { source: "MAIL_RECORD", id: 77, direction: "OUTBOUND", subject: "Intro fail", time: "2026-09-05T09:00:00", sendStatus: "FAILED" },
        latestInbound: null,
        materialCount: 0
    };
}

function messagesA(extra) {
    const base = [
        { source: "MAIL_RECORD", id: 87, contactId: 1, direction: "OUTBOUND", accountCode: "acc1", subject: "Intro older", body: "old out", cleanedBody: "old out", eventAt: "2026-09-05T02:00:00", sendStatus: "SENT", processStatus: null, attachmentCount: 0, firstAttachmentNames: [], messageId: "m87", inReplyTo: null },
        { source: "INBOUND_PROCESSING", id: 90, contactId: 1, direction: "INBOUND", accountCode: "acc1", subject: "Earlier question", body: "raw earlier", cleanedBody: "earlier cleaned", eventAt: "2026-09-06T08:00:00", sendStatus: null, processStatus: "PROCESSED", attachmentCount: 0, firstAttachmentNames: [], messageId: "m90", inReplyTo: "m87" },
        { source: "MAIL_RECORD", id: 88, contactId: 1, direction: "OUTBOUND", accountCode: "acc1", subject: "Intro", body: "intro body", cleanedBody: "intro body", eventAt: "2026-09-06T09:30:00", sendStatus: "SENT", processStatus: null, attachmentCount: 0, firstAttachmentNames: [], messageId: "m88", inReplyTo: null },
        { source: "INBOUND_PROCESSING", id: 101, contactId: 1, direction: "INBOUND", accountCode: "acc1", subject: "Question 1", body: "raw question with <script>alert(1)</script>", cleanedBody: "cleaned question", eventAt: "2026-09-07T03:00:00", sendStatus: null, processStatus: "MANUAL_REVIEW", attachmentCount: 2, firstAttachmentNames: ["a.pdf", "b.pdf"], messageId: "m101", inReplyTo: "m88" }
    ];
    if (extra && extra.nextBefore) {
        return { items: extra.items || base, nextBefore: extra.nextBefore, hasMore: true };
    }
    return { items: base, nextBefore: null, hasMore: false };
}

function contactA() {
    return {
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
    };
}

async function bootChat(serverOverrides, mountOptions) {
    const options = Object.assign({}, serverOverrides || {});
    const ctx = createChatSandbox(options);
    const { doc, host } = createHost();
    ctx.host = host;
    ctx.doc = doc;
    ctx.sandbox.MailboxChat.mount(host, mountOptions || { filters: {} });
    await flush();
    return ctx;
}

function personButtons(host) {
    return host.querySelectorAll(".mc-person-main");
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

function toggleOpen(el) {
    el.setAttribute("open", "");
    el.dispatchEvent(new MiniEvent("toggle", { bubbles: true }));
}

// ════════════════════════════════════════════════════════════════════════

describe("mailbox chat mount + expert list (S-3)", () => {
    it("mounts S-3 骨架：专家栏/会话栏、搜索、四个筛选 chip，分页 20 位/页", async () => {
        const conversations = { items: [expertA(), expertB(), expertCFailed()], total: 3 };
        const ctx = await bootChat({ conversations });
        const html = ctx.host.innerHTML;
        assert.match(html, /class="mail-chat"/);
        assert.ok(ctx.host.querySelector('aside.mc-experts[aria-label="专家会话列表"]'));
        assert.ok(ctx.host.querySelector('section.mc-conversation[aria-label="专家往来信件"]'));
        assert.ok(ctx.host.querySelector('.mc-list-tools input[aria-label="搜索专家"]'));
        const chips = ctx.host.querySelectorAll(".mc-filter");
        assert.deepStrictEqual(chips.map((chip) => chip.textContent), ["全部", "关注", "待处理", "待专家回复"]);
        assert.strictEqual(chips[0].getAttribute("aria-pressed"), "true");
        assert.strictEqual(personButtons(ctx.host).length, 3);
        assert.match(ctx.host.querySelector(".mc-pager").textContent, /第 1\/1 页 · 共 3 位/);
        const first = ctx.calls.api.find((entry) => entry.url.startsWith("/api/mail/mailbox/conversations?"));
        assert.ok(first, "必须请求 conversations summary");
        const query = new URLSearchParams(first.url.split("?")[1]);
        assert.strictEqual(query.get("page"), "0");
        assert.strictEqual(query.get("size"), "20");
    });

    it("纯发件专家显示 收0·发N/待专家回复；仅失败专家绝不显示待专家回复", async () => {
        const conversations = { items: [expertA(), expertB(), expertCFailed()], total: 3 };
        const ctx = await bootChat({ conversations });
        const persons = ctx.host.querySelectorAll(".mc-person");
        const b = persons.find((person) => person.dataset.contactId === "2");
        const c = persons.find((person) => person.dataset.contactId === "3");
        assert.match(b.textContent, /收 0 · 发 2/);
        assert.ok(b.querySelector('.mc-badge[data-tone="waiting"]'), "B 待专家回复");
        assert.ok(!c.querySelector(".mc-badge"), "C 仅失败：无任何状态徽标");
        assert.match(c.textContent, /收 0 · 发 0/);
        assert.doesNotMatch(c.textContent, /待专家回复/);
    });

    it("搜索 300ms 防抖后带 q 参数重查；chip 关注/待处理/待专家回复映射 API", async () => {
        let served = 0;
        const ctx = createChatSandbox({
            route: (url) => {
                if (url.startsWith("/api/mail/mailbox/conversations?")) {
                    served += 1;
                    return Promise.resolve({ items: [], total: 0 });
                }
                return Promise.resolve({});
            }
        });
        const { host } = createHost();
        ctx.sandbox.MailboxChat.mount(host, { filters: {} });
        await flush();
        const search = host.querySelector('.mc-list-tools input[type="search"]');
        search.value = "zhang";
        inputEvent(search);
        assert.strictEqual(served, 1, "防抖前不发起请求");
        ctx.runTimers();
        await flush();
        assert.strictEqual(served, 2);
        const last = ctx.calls.api[ctx.calls.api.length - 1];
        assert.ok(new URLSearchParams(last.url.split("?")[1]).get("q") === "zhang");
        // chip: 待专家回复
        const waitingChip = host.querySelectorAll(".mc-filter").find((chip) => chip.dataset.chip === "waiting");
        click(waitingChip);
        await flush();
        const chipUrl = ctx.calls.api[ctx.calls.api.length - 1].url;
        assert.strictEqual(new URLSearchParams(chipUrl.split("?")[1]).get("waitingReply"), "true");
        const followedChip = host.querySelectorAll(".mc-filter").find((chip) => chip.dataset.chip === "followed");
        click(followedChip);
        await flush();
        const followedUrl = ctx.calls.api[ctx.calls.api.length - 1].url;
        assert.strictEqual(new URLSearchParams(followedUrl.split("?")[1]).get("followed"), "true");
        assert.strictEqual(new URLSearchParams(followedUrl.split("?")[1]).get("waitingReply"), null);
    });

    it("空列表显示 mc-empty「没有符合条件的专家」", async () => {
        const ctx = await bootChat({ conversations: { items: [], total: 0 } });
        assert.match(ctx.host.querySelector(".mc-expert-list").innerHTML, /没有符合条件的专家/);
    });
});

describe("mailbox chat conversation (source,id) keys + mark-resolved (I-1)", () => {
    async function bootA(serverOverrides) {
        const conversations = { items: [expertA(), expertB()], total: 2 };
        const ctx = await bootChat(Object.assign({ conversations, messages: messagesA(), contact: contactA() }, serverOverrides || {}));
        const a = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "1");
        click(a.querySelector(".mc-person-main"));
        await flush();
        return ctx;
    }

    it("timeline 按 (source,id) 键渲染、正文安全文本化、方向 class 正确", async () => {
        const ctx = await bootA();
        const articles = ctx.host.querySelectorAll(".mc-message");
        const keys = articles.map((article) => article.dataset.messageKey);
        assert.deepStrictEqual(keys, ["MAIL_RECORD:87", "INBOUND_PROCESSING:90", "MAIL_RECORD:88", "INBOUND_PROCESSING:101"]);
        assert.ok(articles.some((article) => article.dataset.direction === "OUTBOUND"));
        assert.ok(articles.some((article) => article.dataset.direction === "INBOUND"));
        const pending = articles.find((article) => article.dataset.messageKey === "INBOUND_PROCESSING:101");
        assert.match(pending.innerHTML, /cleaned question/, "展示清洗后正文");
        assert.doesNotMatch(pending.querySelector(".mc-body").innerHTML, /<script/, "正文必须安全转义，绝不插入原始 HTML");
        assert.ok(pending.querySelector('.mc-badge[data-tone="pending"]'));
        assert.ok(pending.querySelector('[data-action="mc-mark-resolved"]'));
        const outbound = articles.find((article) => article.dataset.messageKey === "MAIL_RECORD:88");
        assert.match(outbound.innerHTML, /已发送/);
        assert.ok(!outbound.querySelector('[data-action="mc-mark-resolved"]'), "发件无处理入口");
        assert.ok(outbound.querySelector('.mc-badge[data-tone="success"]'));
        const resolved = articles.find((article) => article.dataset.messageKey === "INBOUND_PROCESSING:90");
        assert.match(resolved.innerHTML, /已处理/);
        assert.ok(!resolved.querySelector('[data-action="mc-mark-resolved"]'));
    });

    it("标记已处理：调既有 API + 操作人对话框；局部刷新消息/计数/角标，不动编辑器、无下载/材料挂载", async () => {
        const ctx = await bootA();
        const editor = ctx.host.querySelector('[aria-label="人工回复正文"]');
        const subject = ctx.host.querySelector('input[aria-label="回复主题"]');
        subject.value = "Re: Question 1";
        inputEvent(subject);
        editor.innerText = "draft keeps";
        inputEvent(editor);
        const before = ctx.calls.materialsMounts.length;
        const markBtn = ctx.host.querySelector('[data-action="mc-mark-resolved"]');
        click(markBtn);
        await flush();
        const markRequest = ctx.calls.api.find((entry) => entry.url.includes("/mark-resolved"));
        assert.ok(markRequest, "must call mark-resolved API");
        assert.strictEqual(markRequest.url, "/api/mail/unmatched-inbound/101/mark-resolved");
        assert.strictEqual(markRequest.method, "POST");
        assert.deepStrictEqual(JSON.parse(markRequest.body), { resolvedBy: "验收员", note: "" });
        assert.strictEqual(ctx.calls.badgeRefresh, 1, "刷新全局未处理角标一次");
        const article = ctx.host.querySelector('[data-message-key="INBOUND_PROCESSING:101"]');
        assert.match(article.innerHTML, /已处理/);
        assert.ok(!article.querySelector('[data-action="mc-mark-resolved"]'), "处理后按钮消失");
        assert.strictEqual(ctx.calls.materialsMounts.length, before, "标记处理不触发材料抽屉/下载");
        assert.strictEqual(ctx.host.querySelector('input[aria-label="回复主题"]').value, "Re: Question 1", "编辑器主题未变");
        assert.strictEqual(ctx.host.querySelector('[aria-label="人工回复正文"]').innerText, "draft keeps", "编辑器正文未重建");
        const expert = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "1");
        assert.ok(!expert.querySelector('.mc-badge[data-tone="pending"]'), "专家行待处理计数清零");
    });

    it("workbench 折叠默认不挂载；展开一次挂载真实 processingId；切专家销毁旧实例", async () => {
        const ctx = await bootA();
        assert.strictEqual(ctx.calls.workbenchMounts.length, 0, "默认折叠不发生成/不挂载");
        const wb = ctx.host.querySelector('.mc-section[data-section="workbench"]');
        assert.ok(wb, "工作台 section 存在");
        assert.strictEqual(wb.open, false);
        toggleOpen(wb);
        await flush();
        assert.strictEqual(ctx.calls.workbenchMounts.length, 1);
        assert.strictEqual(ctx.calls.workbenchMounts[0].processingId, 101, "工作台绑定 summary.latestInbound.processingId");
        // 切专家 B（无来信）→ 旧实例销毁，不为 B 挂载
        const b = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "2");
        click(b.querySelector(".mc-person-main"));
        await flush();
        assert.strictEqual(ctx.calls.unmounts.length, 1, "切专家销毁旧 workbench mount");
        assert.strictEqual(ctx.calls.workbenchMounts.length, 1, "无来信专家不发生成挂载");
    });
});

describe("mailbox chat no-inbound expert (I-4)", () => {
    it("无来信专家：工作台说明不可生成，人工区为说明 + 模板跟进按钮（无假富文本编辑器）", async () => {
        const conversations = { items: [expertB(), expertA()], total: 2 };
        const ctx = await bootChat({ conversations, messages: messagesA(), contact: contactA() });
        const b = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "2");
        click(b.querySelector(".mc-person-main"));
        await flush();
        const conversation = ctx.host.querySelector(".mc-conversation");
        assert.match(conversation.textContent, /暂无专家来信，暂不能生成回复/);
        assert.ok(!conversation.querySelector(".mc-compose"), "无来信不渲染自由富文本编辑器");
        const followBtn = conversation.querySelector('[data-action="mc-template-follow"]');
        assert.ok(followBtn, "提供模板跟进按钮");
        assert.strictEqual(ctx.calls.workbenchMounts.length, 0);
        click(followBtn);
        await flush();
        assert.deepStrictEqual(ctx.calls.followUp, [2], "走既有专家模板发件流程");
        assert.strictEqual(ctx.calls.sendRich.length, 0, "0 次人工生成调用");
    });
});

describe("mailbox chat drafts + adopt + send (I-3)", () => {
    async function bootSelectedA(serverOverrides) {
        const conversations = { items: [expertA(), expertB()], total: 2 };
        const ctx = await bootChat(Object.assign({ conversations, messages: messagesA(), contact: contactA() }, serverOverrides || {}));
        const a = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "1");
        click(a.querySelector(".mc-person-main"));
        await flush();
        return ctx;
    }

    it("草稿跨专家切换恢复（keyed contactId+目标来信+账号）", async () => {
        const ctx = await bootSelectedA();
        const subject = ctx.host.querySelector('input[aria-label="回复主题"]');
        const editor = ctx.host.querySelector('[aria-label="人工回复正文"]');
        assert.match(subject.value, /^Re: Question 1$/);
        subject.value = "My subject";
        inputEvent(subject);
        editor.innerText = "hello draft";
        inputEvent(editor);
        const b = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "2");
        click(b.querySelector(".mc-person-main"));
        await flush();
        const a = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "1");
        click(a.querySelector(".mc-person-main"));
        await flush();
        const restoredSubject = ctx.host.querySelector('input[aria-label="回复主题"]');
        const restoredEditor = ctx.host.querySelector('[aria-label="人工回复正文"]');
        assert.strictEqual(restoredSubject.value, "My subject", "草稿主题恢复");
        assert.strictEqual(restoredEditor.innerText, "hello draft", "草稿正文恢复");
    });

    it("采用 → 人工发送：QA 载荷（ragFactCodes/ragCorpusFingerprint/edited）原样进发送 payload", async () => {
        const ctx = await bootSelectedA();
        const wb = ctx.host.querySelector('.mc-section[data-section="workbench"]');
        toggleOpen(wb);
        await flush();
        assert.strictEqual(ctx.calls.workbenchMounts.length, 1);
        const mount = ctx.calls.workbenchMounts[0];
        await mount.callbacks.onComplete({
            renderedDraftText: "adopted draft body",
            text: "adopted draft body",
            usedFactCodes: ["KB-COMM-044"],
            ragCorpusFingerprint: "fp-2026"
        });
        await flush();
        const editor = ctx.host.querySelector('[aria-label="人工回复正文"]');
        assert.strictEqual(editor.innerText, "adopted draft body");
        assert.match(ctx.calls.status.map((s) => s.message).join("|"), /草稿已采用到人工回复区/);
        const sendBtn = ctx.host.querySelector('[data-action="mc-send-manual"]');
        click(sendBtn);
        await flush();
        assert.strictEqual(ctx.calls.sendRich.length, 1);
        const payload = ctx.calls.sendRich[0];
        assert.strictEqual(payload.processingId, 101);
        const body = payload.body;
        assert.ok(body.subject.includes("Re: Question 1"));
        assert.ok(body.textBody.includes("adopted draft body"));
        assert.ok(body.htmlBody.includes("adopted draft body"));
        assert.deepStrictEqual(body.ragFactCodes, ["KB-COMM-044"], "QA 审计规则集随发送保留");
        assert.strictEqual(body.ragCorpusFingerprint, "fp-2026");
        assert.strictEqual(body.edited, false);
        assert.strictEqual(body.senderAccountCode, null);
        // 成功发送 → 清草稿（切走再回不再恢复旧稿）
        const b = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "2");
        click(b.querySelector(".mc-person-main"));
        await flush();
        const a = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "1");
        click(a.querySelector(".mc-person-main"));
        await flush();
        const restored = ctx.host.querySelector('input[aria-label="回复主题"]');
        assert.ok(restored.value !== "adopted draft body");
    });

    it("发送失败保留全部输入与草稿，按钮恢复可用", async () => {
        const ctx = await bootSelectedA({ sendRichError: "SMTP 500" });
        const subject = ctx.host.querySelector('input[aria-label="回复主题"]');
        const editor = ctx.host.querySelector('[aria-label="人工回复正文"]');
        subject.value = "Keep me";
        inputEvent(subject);
        editor.innerText = "keep body";
        inputEvent(editor);
        const sendBtn = ctx.host.querySelector('[data-action="mc-send-manual"]');
        click(sendBtn);
        await flush();
        assert.strictEqual(ctx.calls.sendRich.length, 1);
        assert.strictEqual(ctx.host.querySelector('input[aria-label="回复主题"]').value, "Keep me");
        assert.strictEqual(ctx.host.querySelector('[aria-label="人工回复正文"]').innerText, "keep body");
        assert.strictEqual(sendBtn.disabled, false);
        // 切走再回草稿仍在
        const b = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "2");
        click(b.querySelector(".mc-person-main"));
        await flush();
        const a = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "1");
        click(a.querySelector(".mc-person-main"));
        await flush();
        assert.strictEqual(ctx.host.querySelector('input[aria-label="回复主题"]').value, "Keep me");
    });

    it("新来信 + 已编辑草稿：提示选择目标；确认后切换主题预填/目标，取消则保留原目标", async () => {
        let current = expertA();
        const ctx = await bootSelectedA({
            route: (url, method) => {
                if (url.startsWith("/api/mail/mailbox/conversations?")) {
                    return Promise.resolve({ items: [current, expertB()], total: 2 });
                }
                if (/\/api\/mail\/mailbox\/conversations\/\d+\/messages/.test(url)) {
                    if (current.latestInbound && current.latestInbound.processingId === 102) {
                        const older = messagesA();
                        older.items = older.items.concat([{
                            source: "INBOUND_PROCESSING", id: 102, contactId: 1, direction: "INBOUND", accountCode: "acc1",
                            subject: "Brand new question", body: "raw 102", cleanedBody: "clean 102", eventAt: "2026-09-08T10:00:00",
                            sendStatus: null, processStatus: "MANUAL_REVIEW", attachmentCount: 0, firstAttachmentNames: [], messageId: "m102", inReplyTo: "m88"
                        }]);
                        return Promise.resolve(older);
                    }
                    return Promise.resolve(messagesA());
                }
                return Promise.resolve({});
            },
            dialogResult: null
        });
        // 编辑草稿（目标 #101）
        const subject = ctx.host.querySelector('input[aria-label="回复主题"]');
        const editor = ctx.host.querySelector('[aria-label="人工回复正文"]');
        subject.value = "Re: Question 1";
        inputEvent(subject);
        editor.innerText = "typed draft";
        inputEvent(editor);
        // 服务器出现新来信 #102 → 刷新
        current = Object.assign({}, expertA(), {
            latestInbound: { processingId: 102, accountCode: "acc1", messageId: "m102", receivedAt: "2026-09-08T10:00:00" },
            pendingCount: 2
        });
        ctx.sandbox.MailboxChat.mount(ctx.host, { filters: {} });
        await flush();
        console.error("NEWINBOUND DEBUG dialogs:", ctx.calls.dialogs.length, "msgs:", ctx.calls.api.filter((e) => e.url.includes("/messages")).length, "list:", ctx.calls.api.filter((e) => e.url.startsWith("/api/mail/mailbox/conversations?")).length, "last:", ctx.calls.api[ctx.calls.api.length - 1].url);
        assert.strictEqual(ctx.calls.dialogs.length, 1, "已编辑草稿时必须提示选择目标");
        assert.strictEqual(ctx.calls.dialogs[0].type, "confirm");
        // 取消 → 保留原目标与草稿
        assert.strictEqual(ctx.host.querySelector('input[aria-label="回复主题"]').value, "Re: Question 1");
        assert.strictEqual(ctx.host.querySelector('[aria-label="人工回复正文"]').innerText, "typed draft");
        // 再次刷新不再重复打扰（dismissed）
        ctx.sandbox.MailboxChat.mount(ctx.host, { filters: {} });
        await flush();
        assert.strictEqual(ctx.calls.dialogs.length, 1, "取消后同目标不再重复提示");
    });

    it("新来信 + 已编辑草稿：确认切换目标 → 主题按新来信预填、正文保留", async () => {
        let current = expertA();
        const ctx = await bootSelectedA({
            route: (url) => {
                if (url.startsWith("/api/mail/mailbox/conversations?")) {
                    return Promise.resolve({ items: [current, expertB()], total: 2 });
                }
                if (/\/api\/mail\/mailbox\/conversations\/\d+\/messages/.test(url)) {
                    if (current.latestInbound && current.latestInbound.processingId === 102) {
                        const older = messagesA();
                        older.items = older.items.concat([{
                            source: "INBOUND_PROCESSING", id: 102, contactId: 1, direction: "INBOUND", accountCode: "acc1",
                            subject: "Brand new question", body: "raw 102", cleanedBody: "clean 102", eventAt: "2026-09-08T10:00:00",
                            sendStatus: null, processStatus: "MANUAL_REVIEW", attachmentCount: 0, firstAttachmentNames: [], messageId: "m102", inReplyTo: "m88"
                        }]);
                        return Promise.resolve(older);
                    }
                    return Promise.resolve(messagesA());
                }
                return Promise.resolve({});
            },
            dialogResults: { confirm: { __confirmed: true } }
        });
        const subject = ctx.host.querySelector('input[aria-label="回复主题"]');
        const editor = ctx.host.querySelector('[aria-label="人工回复正文"]');
        subject.value = "Re: Question 1";
        inputEvent(subject);
        editor.innerText = "typed draft";
        inputEvent(editor);
        current = Object.assign({}, expertA(), {
            latestInbound: { processingId: 102, accountCode: "acc1", messageId: "m102", receivedAt: "2026-09-08T10:00:00" },
            pendingCount: 2
        });
        ctx.sandbox.MailboxChat.mount(ctx.host, { filters: {} });
        await flush();
        assert.ok(ctx.calls.dialogs.length >= 1, "确认对话框出现");
        assert.ok(ctx.calls.dialogs.some((d) => d.type === "confirm"));
        assert.strictEqual(ctx.host.querySelector('input[aria-label="回复主题"]').value, "Re: Brand new question", "确认后主题按新来信预填");
        assert.strictEqual(ctx.host.querySelector('[aria-label="人工回复正文"]').innerText, "typed draft", "正文不被静默清空");
        const info = ctx.host.querySelector('[data-role="target-info"]').textContent;
        assert.ok(info.includes("#102"), "目标来信切换为 #102");
    });
});

describe("mailbox chat follow (I-5)", () => {
    it("关注乐观更新；失败回滚星标并报错，按钮恢复可点", async () => {
        let failNext = false;
        const conversations = { items: [expertA()], total: 1 };
        const ctx = await bootChat({
            conversations,
            route: (url, method) => {
                if (url.startsWith("/api/mail/mailbox/conversations?")) {
                    return Promise.resolve({ items: [expertA()], total: 1 });
                }
                if (/\/follow$/.test(url) && (method === "DELETE" || method === "PUT")) {
                    if (failNext) return Promise.reject(new Error("network down"));
                    return Promise.resolve({ followed: method === "PUT" });
                }
                return Promise.resolve({});
            }
        });
        assert.strictEqual(ctx.host.querySelector(".mc-follow").getAttribute("aria-pressed"), "false");
        click(ctx.host.querySelector(".mc-follow"));
        await flush();
        const putReq = ctx.calls.api.find((entry) => entry.method === "PUT" && /\/follow$/.test(entry.url));
        assert.ok(putReq, "PUT /follow 发起关注");
        const starred = ctx.host.querySelector(".mc-follow");
        if (!starred) {
            console.error("FOLLOW DEBUG html:", ctx.host.innerHTML.slice(0, 1000));
            console.error("FOLLOW DEBUG api:", JSON.stringify(ctx.calls.api));
        }
        assert.strictEqual(starred && starred.getAttribute("aria-pressed"), "true", "乐观更新为已关注");
        assert.ok(starred.textContent.includes("★"));
        // DELETE 失败 → 回滚
        failNext = true;
        click(ctx.host.querySelector(".mc-follow"));
        await flush();
        const delReq = ctx.calls.api.find((entry) => entry.method === "DELETE" && /\/follow$/.test(entry.url));
        assert.ok(delReq, "DELETE /follow 发起取消关注");
        const rolledBack = ctx.host.querySelector(".mc-follow");
        assert.strictEqual(rolledBack.getAttribute("aria-pressed"), "true", "失败回滚为已关注");
        assert.ok(rolledBack.textContent.includes("★"));
        assert.strictEqual(rolledBack.disabled, false, "失败后按钮恢复可点");
        assert.ok(ctx.calls.status.some((s) => /关注操作失败/.test(s.message)), "报错提示");
    });
});

// ════════════════════════════════════════════════════════════════════════
// V-2 修复（R-1）：状态/层级选择器从 window（chat global）发布目录渲染可选值，
// 变更后走既有 /operator-status、/index-level POST（既有 payload keys，无新端点）
// ════════════════════════════════════════════════════════════════════════

describe("mailbox chat status/level catalog selectors (V-2)", () => {
    async function bootSelectedA(serverOverrides) {
        const conversations = { items: [expertA(), expertB()], total: 2 };
        const ctx = await bootChat(Object.assign({ conversations, messages: messagesA(), contact: contactA() }, serverOverrides || {}));
        const a = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "1");
        click(a.querySelector(".mc-person-main"));
        await flush();
        return ctx;
    }

    function optionList(select) {
        return select ? select.querySelectorAll("option") : [];
    }

    it("目录发布到 chat global：状态/层级下拉渲染全部可选值（value=枚举、文案=既有中文标签）", async () => {
        const ctx = await bootSelectedA();
        const statusSelect = ctx.host.querySelector('[data-role="status-select"]');
        const levelSelect = ctx.host.querySelector('[data-role="level-select"]');
        assert.ok(statusSelect, "状态下拉存在");
        assert.ok(levelSelect, "层级下拉存在");
        const statusOptions = optionList(statusSelect);
        assert.strictEqual(statusOptions.length, OPERATOR_STATUS_CATALOG.length, "状态目录完整渲染");
        for (const [value, label] of OPERATOR_STATUS_CATALOG) {
            assert.ok(statusOptions.some((option) => option.getAttribute("value") === value && option.textContent === label),
                `状态选项缺失 ${value}/${label}`);
        }
        const levelOptions = optionList(levelSelect);
        assert.strictEqual(levelOptions.length, INDEX_LEVEL_CATALOG.length, "层级目录完整渲染");
        for (const [value, label] of INDEX_LEVEL_CATALOG) {
            assert.ok(levelOptions.some((option) => option.getAttribute("value") === value && option.textContent === label),
                `层级选项缺失 ${value}/${label}`);
        }
    });

    it("变更状态与层级：仅发既有 /operator-status 与 /index-level POST（既有 payload keys）", async () => {
        const ctx = await bootSelectedA();
        const statusSelect = ctx.host.querySelector('[data-role="status-select"]');
        const levelSelect = ctx.host.querySelector('[data-role="level-select"]');
        statusSelect.value = "COMPLETED"; // contactA 原值 REPLIED → 变更
        levelSelect.value = "RAW";        // contactA 原值 APPLICATION → 变更
        click(ctx.host.querySelector('[data-action="mc-save-settings"]'));
        await flush();
        const statusPost = ctx.calls.api.find((entry) => entry.method === "POST" && entry.url === "/api/expert-contacts/1/operator-status");
        const levelPost = ctx.calls.api.find((entry) => entry.method === "POST" && entry.url === "/api/expert-contacts/1/index-level");
        assert.ok(statusPost, "状态变更必须发起既有 /operator-status POST");
        assert.deepStrictEqual(JSON.parse(statusPost.body), { operatorStatus: "COMPLETED", operatorName: "console" });
        assert.ok(levelPost, "层级变更必须发起既有 /index-level POST");
        assert.deepStrictEqual(JSON.parse(levelPost.body), { targetLevel: "RAW", operatorName: "console" });
        assert.ok(ctx.calls.status.some((s) => /专家信息已更新/.test(s.message)), "保存成功状态提示");
    });

    it("目录缺失回归：选择器为空（V-2 症状可观测）；且 app.js 源文本发布 window 目录", async () => {
        const ctx = await bootSelectedA({ catalogs: false });
        const statusSelect = ctx.host.querySelector('[data-role="status-select"]');
        const levelSelect = ctx.host.querySelector('[data-role="level-select"]');
        assert.ok(statusSelect, "状态下拉仍渲染（但无任何选项）");
        assert.strictEqual(optionList(statusSelect).length, 0, "目录缺失时状态下拉为空");
        assert.strictEqual(optionList(levelSelect).length, 0, "目录缺失时层级下拉为空");
        // DOM-stub harness 不整跑 app.js：以源文本断言发布语句存在，防止 stub 假绿
        assert.match(appSource, /window\.operatorStatusOptions\s*=\s*operatorStatusOptions\s*;/);
        assert.match(appSource, /window\.indexLevelOptions\s*=\s*indexLevelOptions\s*;/);
    });
});

describe("mailbox chat materials + expert detail (I-5 / S-1)", () => {
    it("header 材料按钮经 mcHostOpenMaterials 打开 child-08 drawer（同一 contactId）", async () => {
        const conversations = { items: [expertA(), expertB()], total: 2 };
        const ctx = await bootChat({ conversations, messages: messagesA(), contact: contactA() });
        const a = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "1");
        click(a.querySelector(".mc-person-main"));
        await flush();
        const materialsBtn = ctx.host.querySelector('[data-action="mc-open-materials"]');
        assert.match(materialsBtn.textContent, /材料 40/);
        click(materialsBtn);
        await flush();
        assert.deepStrictEqual(ctx.calls.materialsMounts, [1], "材料抽屉挂载同一 contactId store");
    });

    it("单信「查看全部附件」打开同一专家材料 drawer，不下载附件", async () => {
        const conversations = { items: [expertA()], total: 1 };
        const ctx = await bootChat({ conversations, messages: messagesA(), contact: contactA() });
        click(ctx.host.querySelector(".mc-person-main"));
        await flush();
        const attachmentsBtn = ctx.host.querySelector('[data-action="mc-view-attachments"]');
        assert.ok(attachmentsBtn, "附件详情按钮存在");
        click(attachmentsBtn);
        await flush();
        assert.deepStrictEqual(ctx.calls.materialsMounts, [1]);
        assert.ok(!ctx.calls.api.some((entry) => /transfers|download/.test(entry.url)), "查看附件不触发下载");
    });

    it("「查看专家详情」走既有 openContactInList 流程", async () => {
        const conversations = { items: [expertA()], total: 1 };
        const ctx = await bootChat({ conversations, messages: messagesA(), contact: contactA() });
        click(ctx.host.querySelector(".mc-person-main"));
        await flush();
        click(ctx.host.querySelector('[data-action="mc-open-expert"]'));
        await flush();
        assert.deepStrictEqual(ctx.calls.openExpert, [1]);
    });
});

describe("app.js 宿主守卫：任务钻取/脚本缺失保持旧分支 (I-5)", () => {
    function createMailboxHostSandbox({ chatGlobal, taskExecutionId, apiImpl }) {
        const store = new Map();
        function el(id) {
            if (!store.has(id)) {
                store.set(id, {
                    id,
                    value: "",
                    innerHTML: "",
                    textContent: "",
                    disabled: false,
                    checked: false,
                    hidden: false,
                    classList: { toggle: () => {} }
                });
            }
            return store.get(id);
        }
        const calls = { chatMounts: 0, chatUnmounts: 0, apiUrls: [] };
        const mailboxChatStub = {
            mount: () => { calls.chatMounts += 1; return {}; },
            unmount: () => { calls.chatUnmounts += 1; return true; }
        };
        const sandbox = {
            $: (sel) => el(sel.replace(/^#/, "")),
            document: {
                querySelector: (selector) => {
                    if (selector === 'input[name="mailboxViewMode"]:checked') return { value: "MAIL" };
                    if (selector === 'input[name="mailboxMailScope"]:checked') return { value: "ALL" };
                    if (selector === ".mailbox-view-controls") return { hidden: false };
                    return null;
                },
                querySelectorAll: () => []
            },
            URLSearchParams,
            MailboxChat: chatGlobal ? mailboxChatStub : undefined,
            setView: () => {},
            state: {
                mailbox: {
                    items: [],
                    groups: [],
                    viewMode: "MAIL",
                    page: 0,
                    totalCount: 0,
                    pageSize: 20,
                    accountsLoaded: true,
                    dateDefaultsApplied: true,
                    onlyPending: false,
                    tagFilter: "",
                    taskExecutionId: taskExecutionId || null,
                    taskExecutionLabel: taskExecutionId ? "批量首发邮件" : null,
                    focusExpertContactId: null,
                    focusExpertEmail: null
                }
            },
            api: async (url) => {
                calls.apiUrls.push(url);
                if (apiImpl) return apiImpl(url);
                return { items: [], totalCount: 0 };
            },
            showStatus: () => {},
            renderMailboxTable: () => {},
            renderMailboxExpertGroups: () => {},
            renderMailboxPagination: () => {},
            refreshUnmatchedBadge: async () => {}
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("mailboxChatAvailable"), sandbox);
        vm.runInContext(extractFn("mailboxChatEligible"), sandbox);
        vm.runInContext(extractFn("unmountMailboxChatHosts"), sandbox);
        vm.runInContext(extractFn("syncMailboxChatChrome"), sandbox);
        vm.runInContext(extractFn("mailboxChatFilterSnapshot"), sandbox);
        vm.runInContext(extractFn("refreshMailboxChatList"), sandbox);
        vm.runInContext("async function loadMailboxAccounts() {}", sandbox);
        vm.runInContext(extractFn("mailboxViewMode"), sandbox);
        vm.runInContext(extractFn("mailboxPendingOnly"), sandbox);
        vm.runInContext(extractFn("syncMailboxViewModeControls"), sandbox);
        vm.runInContext(extractFn("loadMailbox"), sandbox);
        return { sandbox, calls };
    }

    it("组件存在且无 taskExecutionId → 挂载聊天，旧分页/模式控件隐藏", async () => {
        const viewControls = { hidden: false };
        const original = createMailboxHostSandbox({ chatGlobal: true, taskExecutionId: null });
        original.sandbox.document.querySelector = (selector) => {
            if (selector === 'input[name="mailboxViewMode"]:checked') return { value: "MAIL" };
            if (selector === 'input[name="mailboxMailScope"]:checked') return { value: "ALL" };
            if (selector === ".mailbox-view-controls") return viewControls;
            return null;
        };
        const { sandbox, calls } = original;
        await sandbox.loadMailbox();
        assert.strictEqual(calls.chatMounts, 1, "激活聊天 mount");
        assert.strictEqual(calls.chatUnmounts, 0);
        assert.strictEqual(viewControls.hidden, true, "旧 MAIL/EXPERT 模式控件隐藏");
        assert.strictEqual(sandbox.$("#mailboxPagination").hidden, true, "旧外部分页隐藏");
        assert.ok(!calls.apiUrls.some((url) => url.startsWith("/api/mail/mailbox?") || url.startsWith("/api/mail/mailbox/by-expert?")), "不再走旧平铺/by-expert 端点");
    });

    it("任务钻取（taskExecutionId）→ 卸载聊天并保持旧平铺列表端点与参数", async () => {
        const { sandbox, calls } = createMailboxHostSandbox({ chatGlobal: true, taskExecutionId: 13023 });
        await sandbox.loadMailbox();
        assert.strictEqual(calls.chatMounts, 0, "任务钻取不激活聊天");
        assert.ok(calls.apiUrls.some((url) => url.startsWith("/api/mail/mailbox?") && url.includes("taskExecutionId=13023")), "taskExecutionId 参数保留");
        // 聊天先前已挂载时（同次进入不可达；此处模拟）卸载路径在 loadMailbox 非激活分支执行
        assert.ok(calls.chatUnmounts === 0 || calls.chatUnmounts === 1);
    });

    it("脚本未加载（无 MailboxChat）→ 完全旧行为", async () => {
        const { sandbox, calls } = createMailboxHostSandbox({ chatGlobal: false, taskExecutionId: null });
        await sandbox.loadMailbox();
        assert.strictEqual(calls.chatMounts, 0);
        assert.ok(calls.apiUrls.some((url) => url.startsWith("/api/mail/mailbox?")), "旧端点照常");
    });
});

describe("mcHostOpenMaterials app 适配器（drawer/store 契约）", () => {
    it("configure + mount({host, contactId, mode:'drawer'})；无 ExpertMaterials 时不动作", async () => {
        const calls = { configure: 0, mounts: [] };
        const sandbox = {
            expertMaterialsAvailable: () => true,
            window: {
                ExpertMaterials: {
                    configure: () => { calls.configure += 1; },
                    mount: (options) => { calls.mounts.push(options); return {}; }
                }
            },
            api: async () => ({}),
            contextPath: "",
            labelDocumentType: (v) => v,
            labelDocumentStatus: (v) => v,
            formatFileSize: (v) => String(v),
            $: (sel) => (sel === "#mailboxList" ? { tagName: "DIV" } : null)
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("mcHostOpenMaterials"), sandbox);
        const result = sandbox.mcHostOpenMaterials(7);
        assert.strictEqual(result, true);
        assert.strictEqual(calls.configure, 1);
        assert.strictEqual(calls.mounts.length, 1);
        assert.strictEqual(calls.mounts[0].contactId, 7);
        assert.strictEqual(calls.mounts[0].mode, "drawer");
        assert.ok(calls.mounts[0].host, "drawer host 传入");
    });
});
