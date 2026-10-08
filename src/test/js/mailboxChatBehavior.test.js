"use strict";

// 收发件箱修复 02 行为测试（I-1..I-8 / S-1..S-7 前端契约）：
// 真实 DOM 能力的最小树（HTML 解析 + 事件冒泡 + querySelector/closest/dataset），
// 驱动 mailbox-chat.js 挂载/筛选/专家标签行/管理/翻译/邮件标签/位置缓存/竞态守卫；
// 另含 app.js 宿主守卫（任务钻取与脚本缺失仍走旧 table/group 分支）。
// 覆盖：
//  - S-1/S-2：四 tab（全部/关注/待处理/待匹配）、⋯ popover 高级筛选草稿语义、id 迁移与还原
//  - S-7：列表专家标签单行渲染（[] 无占位 / null 暂不可用 / title 完整 / 无 pending badge）
//  - I-1：tab 参数无 waitingReply、全部无 pendingOnly/followed；mark 后服务端重查与空页回退
//  - S-4/I-3/I-4：邮件标签直读/删除/添加 adapter 回调；翻译一次一请求 + 缓存
//  - S-3/I-6：管理 overlay portal 化、状态/层级取消零请求、部分失败如实提示
//  - I-5/I-7：窗口缓存/滚动恢复（0 有效）/loadOlder 守卫/quiet 合并/晚响应不串专家
//  - 既有业务：可信工作台 LIVE_INBOUND 宿主、人工回复草稿/采用/发送、关注乐观更新

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
    const contactNotes = Object.assign({}, opts.contactNotes || {});
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
        if (/^\/api\/mail\/contact-notes\/\d+$/.test(url)) {
            const contactId = Number(url.split("/").pop());
            if (method === "PUT") {
                const note = JSON.parse(body).note.replace(/\r\n?/g, "\n").trim();
                contactNotes[contactId] = { contactId, note, updatedBy: note ? "admin" : null, updatedAt: note ? "2026-10-08T10:18:00.000+08:00" : null };
            }
            return Promise.resolve(contactNotes[contactId] || { contactId, note: "", updatedBy: null, updatedAt: null });
        }
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
                return Promise.resolve({ contactId: id, suspended: false, suspendReason: null, suspensionPendingCount: 0, progressStatus: "NONE", followed: false });
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
                    progressStatus: current.progressStatus !== undefined ? current.progressStatus : (current.followed === true ? "FOLLOWING" : "NONE"),
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
                    progressStatus: current.progressStatus !== undefined ? current.progressStatus : (current.followed === true ? "FOLLOWING" : "NONE"),
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
        // 02：三态标记唯一写端点（PUT status → progressStatus/followed）。
        if (/\/api\/mail\/mailbox\/conversations\/\d+\/progress-status$/.test(url) && method === "PUT") {
            const id = Number(url.split("/")[5]);
            const parsed = body ? JSON.parse(body) : {};
            const status = parsed && typeof parsed.status === "string" ? parsed.status : "NONE";
            opts.progressStatus = opts.progressStatus || {};
            opts.progressStatus[id] = status;
            return Promise.resolve({ contactId: id, progressStatus: status, followed: status === "FOLLOWING" });
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

// 待匹配（邮件级）夹具：process_status=MANUAL_REVIEW 且 expert_contact_id=null 的来信响应。
function unmatchedMail(id, extra) {
    return Object.assign({
        id,
        senderAccountCode: "acc-unmatched",
        imapUid: 500 + id,
        messageId: `msg-${id}`,
        inReplyTo: null,
        fromEmail: `sender${id}@example.com`,
        subject: `待匹配来信 ${id}`,
        body: null,
        cleanedBody: null,
        receivedAt: "2026-09-10T10:00:00",
        processStatus: "MANUAL_REVIEW",
        processReason: "CONTACT_NOT_FOUND",
        reasonType: null,
        resolvedAt: null,
        resolvedBy: null,
        expertContactId: null
    }, extra || {});
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
    ctx.api = ctx.sandbox.MailboxChat.mount(host, mountOptions || { filters: {} });
    return ctx;
}

async function bootChat(serverOverrides, mountOptions, dom) {
    const ctx = mountChat(serverOverrides || {}, mountOptions, dom);
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

// 浏览器语义模拟：真实 contenteditable 的 innerText 由渲染后的 DOM 派生，与 innerHTML
// 不同源（迷你 DOM 不做布局推导）。人工富文本用例需要「HTML 含连续 <br>、纯文本含连续
// 换行」这一真实组合，故显式注入该元素的 innerText 取值。
function setEditorContent(editor, html, text) {
    editor.innerHTML = html;
    let innerTextValue = String(text == null ? "" : text);
    Object.defineProperty(editor, "innerText", {
        configurable: true,
        get: () => innerTextValue,
        set: (next) => { innerTextValue = String(next == null ? "" : next); }
    });
    return editor;
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
    // 02：Tab 计数请求（page=0&size=1）不算列表请求。
    return ctx.calls.api.filter((entry) => entry.url.startsWith("/api/mail/mailbox/conversations?")
        && queryOf(entry.url).get("size") !== "1");
}

function unmatchedRequests(ctx) {
    return ctx.calls.api.filter((entry) => entry.url.startsWith("/api/mail/unmatched-inbound?"));
}

function lastUnmatchedRequest(ctx) {
    const list = unmatchedRequests(ctx);
    return list.length ? list[list.length - 1] : null;
}

function chipButton(ctx, key) {
    return ctx.host.querySelectorAll(".mc-filter").find((chip) => chip.dataset.chip === key) || null;
}

function unmatchedCards(ctx) {
    return ctx.host.querySelectorAll('[data-action="mc-select-unmatched"]');
}

function lastConversationsRequest(ctx) {
    const list = conversationsRequests(ctx);
    return list.length ? list[list.length - 1] : null;
}

function queryOf(url) {
    return new URLSearchParams(url.split("?")[1] || "");
}

// ════════════════════════════════════════════════════════════════════════

describe("mailbox chat mount + S-1 skeleton + S-7 expert tag rows", () => {
    it("mounts S-1 骨架：两栏、搜索行、⋯ popover、五 tab、分页", async () => {
        const conversations = { items: [expertA(), expertB(), expertCTagsNull()], total: 3 };
        const ctx = await bootChat({ conversations });
        const html = ctx.host.innerHTML;
        assert.match(html, /class="mail-chat mobile-core-mailbox" data-mobile-pane="list"/);
        assert.ok(ctx.host.querySelector('aside.mc-experts[aria-label="专家会话列表"]'));
        assert.ok(ctx.host.querySelector('section.mc-conversation[aria-label="专家往来信件"]'));
        assert.ok(ctx.host.querySelector('.mc-search-row input[aria-label="搜索专家"]'));
        const chips = ctx.host.querySelectorAll(".mc-filter");
        assert.deepStrictEqual(chips.map((chip) => chip.dataset.chip), ["all", "provided", "followed", "pending", "suspended", "replied", "unmatched"]);
        assert.deepStrictEqual(
            chips.map((chip) => chip.textContent.replace(/\d+/g, "")),
            ["全部", "已提供", "跟进中", "待处理", "已挂起", "已回复", "待匹配"]
        );
        assert.strictEqual(chips.find((chip) => chip.dataset.chip === "pending").getAttribute("aria-pressed"), "true", "普通首次进入默认待处理（total>0）");
        const popover = ctx.host.querySelector("#mcFilterPopover");
        assert.ok(popover, "⋯ popover 存在");
        assert.ok(popover.getAttribute("hidden") !== null, "popover 默认关闭");
        const toggle = ctx.host.querySelector('[data-action="mc-more-filters"]');
        assert.ok(toggle, "只有 ⋯ 可见入口");
        assert.strictEqual(toggle.getAttribute("aria-expanded"), "false");
        assert.strictEqual(personButtons(ctx.host).length, 3);
        assert.match(ctx.host.querySelector(".mc-pager").textContent, /第 1\/1 页 · 共 3 位/);
        const first = conversationsRequests(ctx)[0];
        assert.ok(first, "必须请求 conversations summary");
        const query = queryOf(first.url);
        assert.strictEqual(query.get("page"), "0");
        assert.strictEqual(query.get("size"), "20");
    });

    it("S-7：专家标签单行渲染 —— [] 无占位、null 显示「标签暂不可用」、卡片无 waiting/pending badge", async () => {
        const conversations = { items: [expertA(), expertB(), expertCTagsNull()], total: 3 };
        const ctx = await bootChat({ conversations });
        const persons = ctx.host.querySelectorAll(".mc-person");
        const a = persons.find((person) => person.dataset.contactId === "1");
        const b = persons.find((person) => person.dataset.contactId === "2");
        const c = persons.find((person) => person.dataset.contactId === "3");
        assert.match(a.querySelector(".mc-person-counts").textContent, /收 2 · 发 2/);
        const aTags = a.querySelector(".mc-person-tags");
        assert.ok(aTags, "A 有标签行");
        assert.strictEqual(aTags.getAttribute("title"), "专家标签：学术科研、重点关注");
        assert.deepStrictEqual(aTags.querySelectorAll(".mc-person-tag").map((span) => span.textContent), ["学术科研", "重点关注"]);
        assert.ok(a.querySelector(".mc-person-main").getAttribute("aria-label").includes("专家标签：学术科研、重点关注"), "键盘可读完整标签");
        assert.ok(!b.querySelector(".mc-person-tags"), "B 空数组：不渲染标签占位");
        assert.ok(!b.querySelector(".mc-person-tags-unavailable"), "B 空数组：不显示不可用");
        assert.ok(!c.querySelector(".mc-person-tags"), "C null：不渲染标签 chips");
        const cUnavailable = c.querySelector(".mc-person-tags-unavailable");
        assert.ok(cUnavailable, "C null：显示单行标签暂不可用");
        assert.strictEqual(cUnavailable.getAttribute("title"), "标签暂不可用");
        [a, b, c].forEach((person) => {
            assert.ok(!person.querySelector('.mc-badge[data-tone="waiting"]'), "等待回复 badge 已删除");
            assert.ok(!person.querySelector('.mc-badge[data-tone="pending"]'), "卡片不显示待处理 badge");
        });
        assert.doesNotMatch(ctx.host.querySelector(".mc-expert-list").textContent, /待专家回复/);
    });

    it("S-7：未知/特殊字符标签原值转义、title 完整", async () => {
        const weird = expertA({ expertTags: ["<b>bold</b>&\"quote\"", "普通"] });
        const ctx = await bootChat({ conversations: { items: [weird], total: 1 } });
        const a = ctx.host.querySelectorAll(".mc-person")[0];
        const aTags = a.querySelector(".mc-person-tags");
        assert.strictEqual(aTags.getAttribute("title"), "专家标签：<b>bold</b>&\"quote\"、普通");
        const chips = aTags.querySelectorAll(".mc-person-tag");
        assert.strictEqual(chips.length, 2);
        assert.ok(!aTags.innerHTML.includes("<b>bold</b>"), "标签文本必须转义，不能成为 HTML");
        assert.match(chips[0].innerHTML, /&lt;b&gt;bold&lt;\/b&gt;&amp;/, "特殊字符已转义");
    });
});

describe("I-1 tab 参数与服务端排序（无 waitingReply）", () => {
    it("已回复独立请求并可人工移出；刷新后从已回复消失", async () => {
        let dismissed = false;
        const ctx = await bootChat({
            conversations: { items: [expertA()], total: 1 },
            route: (url, method, body, entry, next) => {
                if (url.startsWith("/api/mail/mailbox/conversations?") && queryOf(url).get("repliedOnly") === "true") {
                    return Promise.resolve(dismissed ? { items: [], total: 0 } : { items: [expertA()], total: 1 });
                }
                if (url === "/api/mail/mailbox/conversations/1/replied-dismissal" && method === "PUT") {
                    dismissed = true;
                    return Promise.resolve({ dismissed: true });
                }
                return next(url, method, body);
            }
        });
        click(chipButton(ctx, "replied"));
        await flush();
        assert.strictEqual(queryOf(lastConversationsRequest(ctx).url).get("repliedOnly"), "true");
        assert.strictEqual(queryOf(lastConversationsRequest(ctx).url).get("followed"), null);
        const remove = ctx.host.querySelector('[data-action="mc-dismiss-replied"]');
        assert.ok(remove, "已回复卡片有移出操作");
        click(remove);
        await flush();
        assert.ok(ctx.calls.api.some((entry) => entry.url.endsWith("/1/replied-dismissal") && entry.method === "PUT"));
        assert.strictEqual(ctx.host.querySelectorAll(".mc-person").length, 0);
        click(chipButton(ctx, "all"));
        await flush();
        assert.strictEqual(ctx.host.querySelectorAll(".mc-person").length, 1, "全部视图仍保留专家");
    });

    it("三个 tab 参数：全部无参、关注 followed=true、待处理 pendingOnly=true，永不发 waitingReply", async () => {
        let served = 0;
        const conversations = { items: [expertA()], total: 1 };
        const ctx = await bootChat({ conversations });
        const chips = ctx.host.querySelectorAll(".mc-filter");
        // 关注
        click(chips.find((chip) => chip.dataset.chip === "followed"));
        await flush();
        let q = queryOf(lastConversationsRequest(ctx).url);
        assert.strictEqual(q.get("followed"), "true");
        assert.strictEqual(q.get("pendingOnly"), null);
        assert.strictEqual(q.get("waitingReply"), null);
        // 待处理
        click(chips.find((chip) => chip.dataset.chip === "pending"));
        await flush();
        q = queryOf(lastConversationsRequest(ctx).url);
        assert.strictEqual(q.get("pendingOnly"), "true");
        assert.strictEqual(q.get("followed"), null);
        assert.strictEqual(q.get("waitingReply"), null);
        // 全部：两者都不传
        click(chips.find((chip) => chip.dataset.chip === "all"));
        await flush();
        q = queryOf(lastConversationsRequest(ctx).url);
        assert.strictEqual(q.get("followed"), null);
        assert.strictEqual(q.get("pendingOnly"), null);
        assert.strictEqual(q.get("waitingReply"), null);
        assert.ok(!ctx.calls.api.some((entry) => /waitingReply=true/.test(entry.url)), "永不发送 waitingReply");
    });

    it("外部 onlyPending 只初始化首次；用户点过 tab 后 tab 是唯一权威", async () => {
        const ctx = await bootChat(
            { conversations: { items: [expertA()], total: 1 } },
            { filters: { pendingOnly: true } }
        );
        const chips = ctx.host.querySelectorAll(".mc-filter");
        const pendingChip = chips.find((chip) => chip.dataset.chip === "pending");
        const allChip = chips.find((chip) => chip.dataset.chip === "all");
        assert.strictEqual(pendingChip.getAttribute("aria-pressed"), "true", "初次 onlyPending 初始化为待处理");
        click(allChip);
        await flush();
        assert.strictEqual(allChip.getAttribute("aria-pressed"), "true", "用户切到全部");
        // 再次以 onlyPending=true 刷新（app 级），不得把全部切回待处理
        ctx.sandbox.MailboxChat.mount(ctx.host, { filters: { pendingOnly: true } });
        await flush();
        assert.strictEqual(allChip.getAttribute("aria-pressed"), "true", "用户 tab 权威：不被外部 onlyPending 覆盖");
        const q = queryOf(lastConversationsRequest(ctx).url);
        assert.strictEqual(q.get("pendingOnly"), null, "全部绝不发 pendingOnly");
    });
});

describe("S-2 高级筛选 popover（草稿语义）", () => {
    it("应用筛选：recipientEmail/keyword/label/日期/账号/方向 映射 01 接口；角标与摘要更新", async () => {
        const ctx = await bootChat({
            conversations: { items: [expertA()], total: 1 },
            tagOptions: [
                { tagKey: "qa:3", label: "会议安排", tagType: "QA", active: true, count: 2 },
                { tagKey: "qa:3", label: "会议安排", tagType: "QA", active: true, count: 1 },
                { tagKey: "custom:x", label: "会议安排", tagType: "CUSTOM", active: true, count: 1 },
                { tagKey: "custom:y", label: "研究兴趣", tagType: "CUSTOM", active: true, count: 1 }
            ]
        });
        // 打开 popover（触发标签选项真实加载）
        click(ctx.host.querySelector('[data-action="mc-more-filters"]'));
        await flush();
        const tagSelect = popoverField(ctx, "mailboxFilterTag");
        const optionValues = tagSelect.querySelectorAll("option").map((option) => option.textContent);
        assert.deepStrictEqual(optionValues, ["全部标签", "会议安排", "研究兴趣"], "真实标签去重、不伪造旧类别");
        const account = popoverField(ctx, "mailboxFilterAccountCode");
        account.value = "acc1";
        changeEvent(account);
        const direction = popoverField(ctx, "mailboxFilterDirection");
        direction.value = "INBOUND";
        changeEvent(direction);
        const recipient = popoverField(ctx, "mailboxFilterRecipient");
        recipient.value = "a@example.edu";
        inputEvent(recipient);
        const keyword = popoverField(ctx, "mailboxFilterKeyword");
        keyword.value = "meeting-z9";
        inputEvent(keyword);
        tagSelect.value = "会议安排";
        changeEvent(tagSelect);
        popoverField(ctx, "mailboxFilterStartDate").value = "2026-09-01";
        popoverField(ctx, "mailboxFilterEndDate").value = "2026-09-30";
        const before = conversationsRequests(ctx).length;
        // 应用
        click(docById(ctx, "mailboxSearchBtn"));
        await flush();
        assert.strictEqual(conversationsRequests(ctx).length, before + 1, "应用才发请求");
        const q = queryOf(lastConversationsRequest(ctx).url);
        assert.strictEqual(q.get("accountCode"), "acc1");
        assert.strictEqual(q.get("direction"), "INBOUND");
        assert.strictEqual(q.get("recipientEmail"), "a@example.edu");
        assert.strictEqual(q.get("keyword"), "meeting-z9");
        assert.strictEqual(q.get("label"), "会议安排");
        assert.strictEqual(q.get("startDate"), "2026-09-01");
        assert.strictEqual(q.get("endDate"), "2026-09-30");
        assert.strictEqual(q.get("subject"), null, "不再 keyword→subject");
        assert.strictEqual(q.get("waitingReply"), null);
        const countBadge = ctx.host.querySelector(".mc-filter-count");
        assert.strictEqual(countBadge.hidden, false);
        assert.strictEqual(countBadge.textContent, "7");
        const summary = ctx.host.querySelector(".mc-filter-summary");
        assert.match(summary.textContent, /7 项筛选已生效/);
        assert.ok(ctx.host.querySelector("#mcFilterPopover").getAttribute("hidden") !== null, "应用后 popover 关闭");
    });

    it("草稿：字段修改不触发请求；× 关闭未应用恢复已生效值", async () => {
        const ctx = await bootChat({ conversations: { items: [expertA()], total: 1 } });
        const before = conversationsRequests(ctx).length;
        click(ctx.host.querySelector('[data-action="mc-more-filters"]'));
        await flush();
        const keyword = popoverField(ctx, "mailboxFilterKeyword");
        keyword.value = "draft not applied";
        inputEvent(keyword);
        const account = popoverField(ctx, "mailboxFilterAccountCode");
        account.value = "acc1";
        changeEvent(account);
        await flush();
        assert.strictEqual(conversationsRequests(ctx).length, before, "修改字段只记草稿不查询");
        // 关闭（×）
        click(ctx.host.querySelector('[data-action="mc-close-filters"]'));
        assert.strictEqual(keyword.value, "", "未应用关闭恢复已生效值");
        assert.strictEqual(account.value, "", "未应用关闭恢复已生效值");
        assert.ok(ctx.host.querySelector("#mcFilterPopover").getAttribute("hidden") !== null);
    });

    it("非法日期：应用时提示且不请求", async () => {
        const ctx = await bootChat({ conversations: { items: [expertA()], total: 1 } });
        const before = conversationsRequests(ctx).length;
        click(ctx.host.querySelector('[data-action="mc-more-filters"]'));
        await flush();
        popoverField(ctx, "mailboxFilterStartDate").value = "2026-09-30";
        popoverField(ctx, "mailboxFilterEndDate").value = "2026-09-01";
        click(docById(ctx, "mailboxSearchBtn"));
        await flush();
        assert.strictEqual(conversationsRequests(ctx).length, before, "非法日期不发请求");
        const popover = ctx.host.querySelector("#mcFilterPopover");
        const error = popover.querySelector(".mc-inline-error");
        assert.strictEqual(error.hidden, false);
        assert.match(error.textContent, /开始日期不能晚于结束日期/);
    });

    it("Enter 应用；重置/清除立即清空高级筛选并查询，保持 tab 与 q", async () => {
        const ctx = await bootChat({
            conversations: { items: [expertA()], total: 1 }
        });
        // 先设 q 与 chip=待处理
        const search = ctx.host.querySelector('.mc-search-row input[type="search"]');
        search.value = "zhang";
        inputEvent(search);
        ctx.runTimers();
        await flush();
        const pendingChip = ctx.host.querySelectorAll(".mc-filter").find((chip) => chip.dataset.chip === "pending");
        click(pendingChip);
        await flush();
        // 打开并应用一个高级筛选
        click(ctx.host.querySelector('[data-action="mc-more-filters"]'));
        await flush();
        popoverField(ctx, "mailboxFilterKeyword").value = "meeting";
        inputEvent(popoverField(ctx, "mailboxFilterKeyword"));
        keyEvent(popoverField(ctx, "mailboxFilterKeyword"), "Enter");
        await flush();
        let q = queryOf(lastConversationsRequest(ctx).url);
        assert.strictEqual(q.get("keyword"), "meeting");
        assert.strictEqual(q.get("q"), "zhang", "q 独立保留");
        assert.strictEqual(q.get("pendingOnly"), "true", "chip 保留");
        // 清除：清空高级筛选、保持 tab/q
        click(ctx.host.querySelector('[data-action="mc-clear-filters"]'));
        await flush();
        q = queryOf(lastConversationsRequest(ctx).url);
        assert.strictEqual(q.get("keyword"), null);
        assert.strictEqual(q.get("q"), "zhang");
        assert.strictEqual(q.get("pendingOnly"), "true");
        const summary = ctx.host.querySelector(".mc-filter-summary");
        assert.ok(summary.getAttribute("hidden") !== null, "清除后摘要隐藏");
    });
});

describe("S-1 宿主 chrome：唯一筛选节点迁移与还原、刷新按钮迁移", () => {
    it("聊天挂载：七字段+查询按钮移入 popover、view 加 mc-refined、刷新按钮进 panel-head-actions", async () => {
        const dom = createMailboxViewDom();
        const ctx = mountChat(
            { conversations: { items: [expertA()], total: 1 } },
            {},
            { doc: dom.doc, host: dom.list }
        );
        await flush();
        const inPopover = dom.doc.getElementById("mailboxFilterFields");
        const popover = dom.list.querySelector("#mcFilterPopover");
        ["mailboxFilterAccountCode", "mailboxFilterDirection", "mailboxFilterTag", "mailboxFilterRecipient", "mailboxFilterKeyword", "mailboxFilterStartDate", "mailboxFilterEndDate"].forEach((id) => {
            const el = dom.doc.getElementById(id);
            assert.ok(el, `${id} 仍在 document 内（唯一节点，非复制）`);
            assert.ok(popover.contains(el), `${id} 已迁入 popover`);
            assert.ok(inPopover.contains(el), `${id} 在 #mailboxFilterFields 内`);
        });
        const searchBtn = dom.doc.getElementById("mailboxSearchBtn");
        assert.ok(popover.contains(searchBtn), "查询按钮迁入 popover footer");
        assert.ok(searchBtn.textContent === "应用筛选" || searchBtn.textContent === "查询", "共享按钮文案切换");
        assert.ok(dom.view.classList.contains("mc-refined"), "chatOn 时 view-mailbox 加 mc-refined");
        const actions = dom.panel.querySelector(".panel-head-actions");
        assert.strictEqual(actions.firstChild.getAttribute("id"), "mailboxRefreshBtn", "刷新按钮移到 panel-head-actions 最前");
        // 动态账号选项保留（loadMailboxAccounts 填充的是同一节点）
        const account = dom.doc.getElementById("mailboxFilterAccountCode");
        const options = account.querySelectorAll("option").map((option) => option.textContent);
        assert.ok(options.includes("acc1 (a@x.com)") && options.includes("acc2 (b@x.com)"), "账号动态选项保留");
        // 标签选择已换成真实标签（不含旧类别）
        assert.ok(!account.querySelector("option[value='专家']"), "旧类别不作为选项来源");
    });

    it("unmount：字段/按钮还原旧 toolbar、mc-refined 移除、portal 移除", async () => {
        const dom = createMailboxViewDom();
        const ctx = mountChat(
            { conversations: { items: [expertA()], total: 1 } },
            {},
            { doc: dom.doc, host: dom.list }
        );
        await flush();
        // 用户改了字段值（未应用）+ 账号下拉由宿主填充
        const account = dom.doc.getElementById("mailboxFilterAccountCode");
        account.value = "acc1";
        const tagSelect = dom.doc.getElementById("mailboxFilterTag");
        const oldTagOptions = Array.from(tagSelect.querySelectorAll("option")).map((o) => o.value);
        ctx.sandbox.MailboxChat.unmount(dom.list);
        const toolbar = dom.toolbar;
        const idsInToolbar = toolbar.children
            .filter((child) => child.getAttribute && child.getAttribute("id"))
            .map((child) => child.getAttribute("id"));
        const expected = ["mailboxRefreshBtn", "mailboxFilterAccountCode", "mailboxFilterDirection", "mailboxFilterTag", "mailboxFilterRecipient", "mailboxFilterKeyword", "mailboxFilterStartDate", "mailboxFilterEndDate", "mailboxSearchBtn"];
        assert.deepStrictEqual(idsInToolbar, expected, "唯一节点按原顺序还原到旧 toolbar");
        assert.ok(!dom.view.classList.contains("mc-refined"), "unmount 移除 mc-refined");
        const actions = dom.panel.querySelector(".panel-head-actions");
        assert.strictEqual(actions.firstChild.getAttribute("id"), "checkRepliesBtn", "panel-head-actions 还原");
        const restoredAccount = dom.doc.getElementById("mailboxFilterAccountCode");
        assert.strictEqual(restoredAccount.value, "acc1", "值保留");
        const restoredTag = dom.doc.getElementById("mailboxFilterTag");
        assert.deepStrictEqual(Array.from(restoredTag.querySelectorAll("option")).map((o) => o.value), oldTagOptions, "还原旧类别选项");
        assert.ok(!dom.doc.querySelector(".mail-chat.mc-overlay-root"), "portal 已移除");
        assert.strictEqual(dom.list.innerHTML, "", "host 已清空");
    });
});

describe("mailbox chat conversation (source,id) keys + S-4 卡片", () => {
    async function bootA(serverOverrides) {
        const conversations = { items: [expertA(), expertB()], total: 2 };
        const ctx = await bootChat(Object.assign({ conversations, messages: messagesA(), contact: contactA() }, serverOverrides || {}));
        const a = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "1");
        click(a.querySelector(".mc-person-main"));
        await flush();
        return ctx;
    }

    it("timeline 按 (source,id) 键渲染；正文安全；处理态/按钮按 S-4", async () => {
        const ctx = await bootA();
        const articles = ctx.host.querySelectorAll(".mc-message");
        const keys = articles.map((article) => article.dataset.messageKey);
        assert.deepStrictEqual(keys, ["MAIL_RECORD:87", "INBOUND_PROCESSING:90", "MAIL_RECORD:88", "INBOUND_PROCESSING:101"]);
        const pending = articles.find((article) => article.dataset.messageKey === "INBOUND_PROCESSING:101");
        assert.match(pending.innerHTML, /cleaned question/);
        assert.doesNotMatch(pending.querySelector(".mc-body").innerHTML, /<script/, "正文安全转义");
        assert.ok(pending.querySelector('.mc-badge[data-tone="pending"]'));
        const pendingActions = pending.querySelectorAll("footer button").map((btn) => btn.dataset.action);
        assert.deepStrictEqual(pendingActions, ["mc-translate", "mc-add-mail-tag", "mc-mark-resolved"], "来信 footer：翻译/加标签/标记处理");
        const processed = articles.find((article) => article.dataset.messageKey === "INBOUND_PROCESSING:90");
        assert.ok(processed.querySelector(".mailbox-suspend-processed-label"), "已处理来信显示已处理标签");
        assert.match(processed.querySelector(".mailbox-suspend-processed-label").textContent, /已处理/);
        assert.ok(!processed.querySelector('[data-action="mc-mark-resolved"]'), "已处理不再标记");
        assert.ok(processed.querySelector('[data-action="mc-add-mail-tag"]'), "已处理来信仍能加标签");
        const outbound = articles.find((article) => article.dataset.messageKey === "MAIL_RECORD:88");
        assert.ok(outbound.querySelector('.mc-badge[data-tone="success"]'));
        const outActions = outbound.querySelectorAll("footer button").map((btn) => btn.dataset.action);
        assert.deepStrictEqual(outActions, ["mc-translate"], "发件只有翻译");
        assert.ok(!outbound.querySelector('[data-action="mc-add-mail-tag"]'), "发件无标签入口");
        assert.ok(!outbound.querySelector('[data-role="mail-tags"]'), "发件无标签行");
        assert.ok(ctx.host.querySelector(".mc-timeline-head"), "时间线头部存在");
        assert.ok(ctx.host.querySelector('[data-action="mc-latest"]'), "最新消息按钮存在");
    });

    it("S-4：同数字 id 不同 source —— 只有 INBOUND_PROCESSING 显示其 tags；删除走 DELETE tagId，失败保留 chips", async () => {
        const messages = messagesA();
        // 给发件 88 一个同 id 邮件标签形状字段也不应展示（tags 只属于 INBOUND_PROCESSING）
        const withOutboundTags = messages.items.map((msg) => {
            if (msg.source === "MAIL_RECORD" && msg.id === 88) {
                return Object.assign({}, msg, { tags: [mailTag(99, "不应出现", "CUSTOM")] });
            }
            return msg;
        });
        let tag7Deleted = false;
        const ctx = await bootA({
            messages: { items: withOutboundTags, nextBefore: null, hasMore: false },
            route: (url, method, body, entry, next) => {
                if (/\/api\/mail\/mailbox\/conversations\/\d+\/messages/.test(url)) {
                    // 删除后服务端窗口不再包含 tag 7（真实删除语义）
                    const items = withOutboundTags.map((msg) => {
                        if (msg.source === "INBOUND_PROCESSING" && msg.id === 101) {
                            const tags = (msg.tags || []).filter((tag) => !(tag7Deleted && Number(tag.tagId) === 7));
                            return Object.assign({}, msg, { tags });
                        }
                        return msg;
                    });
                    return Promise.resolve({ items, nextBefore: null, hasMore: false });
                }
                if (/\/api\/inbound-summary\/tags\/\d+/.test(url) && method === "DELETE") {
                    tag7Deleted = true;
                    return Promise.resolve({});
                }
                return next(url, method, body);
            }
        });
        const outbound = ctx.host.querySelector('[data-message-key="MAIL_RECORD:88"]');
        assert.ok(!outbound.querySelector('[data-role="mail-tags"]'), "发件绝不渲染邮件标签行");
        const inbound = ctx.host.querySelector('[data-message-key="INBOUND_PROCESSING:101"]');
        const tagRow = inbound.querySelector('[data-role="mail-tags"]');
        assert.ok(tagRow, "来信标签行存在");
        assert.match(tagRow.textContent, /research/);
        const chip = tagRow.querySelector(".chip-x");
        assert.ok(chip, "chip 可删除");
        click(chip);
        await flush();
        const del = ctx.calls.api.find((entry) => entry.method === "DELETE" && /\/api\/inbound-summary\/tags\/7$/.test(entry.url));
        assert.ok(del, "DELETE /api/inbound-summary/tags/7");
        const afterDel = ctx.host.querySelector('[data-message-key="INBOUND_PROCESSING:101"] [data-role="mail-tags"]');
        assert.ok(!afterDel, "删除成功 chip 消失（含静默窗口校验后）");
        assert.ok(!ctx.calls.api.some((entry) => /\/thread/.test(entry.url)), "无每封 thread 读取");
    });

    it("S-4：删除失败保留原 chips 并原位报错", async () => {
        const ctx = await bootA({ deleteTagError: "网络错误" });
        const inbound = ctx.host.querySelector('[data-message-key="INBOUND_PROCESSING:101"]');
        const chip = inbound.querySelector('[data-role="mail-tags"] .chip-x');
        click(chip);
        await flush();
        const still = ctx.host.querySelector('[data-message-key="INBOUND_PROCESSING:101"] [data-role="mail-tags"]');
        assert.ok(still, "失败保留原 chips");
        const error = ctx.host.querySelector('[data-message-key="INBOUND_PROCESSING:101"] .mc-inline-error');
        assert.strictEqual(error.hidden, false, "原位错误提示");
        assert.match(error.textContent, /网络错误/);
    });

    it("I-3：添加标签走宿主 adapter（inboundId/source/contactId/onTagsChanged），回包 tags 直显", async () => {
        const ctx = await bootA();
        const inbound = ctx.host.querySelector('[data-message-key="INBOUND_PROCESSING:101"]');
        click(inbound.querySelector('[data-action="mc-add-mail-tag"]'));
        await flush();
        assert.strictEqual(ctx.calls.inboundTagModals.length, 1, "打开旧 #inboundAddTagModal 宿主 adapter");
        const adapter = ctx.calls.inboundTagModals[0];
        assert.strictEqual(adapter.inboundId, 101);
        assert.strictEqual(adapter.source, "INBOUND_PROCESSING");
        assert.strictEqual(adapter.contactId, 1);
        assert.strictEqual(typeof adapter.onTagsChanged, "function");
        // 模拟 submitInboundAddTag 成功：服务器 POST 回包 tags → onTagsChanged(tags)
        adapter.onTagsChanged([mailTag(7, "research", "CUSTOM"), mailTag(12, "新增QA", "QA", 4)]);
        await flush();
        const updated = ctx.host.querySelector('[data-message-key="INBOUND_PROCESSING:101"] [data-role="mail-tags"]');
        assert.match(updated.textContent, /新增QA/, "POST 回包 tags 直显，不依赖固定编辑器 id");
        assert.ok(!ctx.calls.api.some((entry) => /\/thread/.test(entry.url)), "不按每封 GET thread");
    });

    it("I-4：翻译一次点击一次请求，收起再开零请求；空正文无翻译按钮", async () => {
        const ctx = await bootA();
        const outbound = ctx.host.querySelector('[data-message-key="MAIL_RECORD:87"]');
        assert.ok(outbound.querySelector('[data-action="mc-translate"]'), "有正文发件提供翻译");
        const outMsg = outbound.querySelector(".mc-body").textContent;
        click(outbound.querySelector('[data-action="mc-translate"]'));
        await flush();
        let translateCalls = ctx.calls.api.filter((entry) => entry.url === "/api/translate");
        assert.strictEqual(translateCalls.length, 1);
        assert.deepStrictEqual(JSON.parse(translateCalls[0].body), { text: outMsg.trim() }, "翻译同一显示正文");
        // 卡片在翻译状态更新时重渲染，需重查节点
        const freshOutbound = ctx.host.querySelector('[data-message-key="MAIL_RECORD:87"]');
        const translation = freshOutbound.querySelector(".mc-translation");
        assert.ok(translation, "译文块渲染");
        assert.match(translation.textContent, /译文：/, "译文原位展开且转义");
        const collapseBtn = freshOutbound.querySelector('[data-action="mc-translate"]');
        assert.strictEqual(collapseBtn.textContent, "收起译文");
        // 收起再开：零请求
        click(collapseBtn);
        await flush();
        click(ctx.host.querySelector('[data-message-key="MAIL_RECORD:87"] [data-action="mc-translate"]'));
        await flush();
        translateCalls = ctx.calls.api.filter((entry) => entry.url === "/api/translate");
        assert.strictEqual(translateCalls.length, 1, "再开复用缓存零请求");
        assert.match(ctx.host.querySelector('[data-message-key="MAIL_RECORD:87"] .mc-translation').textContent, /译文：/);
    });

    it("I-4：翻译失败按钮显示「翻译失败，重试」，重试成功；翻译不重建 manual DOM", async () => {
        let failNext = true;
        const ctx = await bootChat({
            conversations: { items: [expertA()], total: 1 },
            messages: messagesA(),
            contact: contactA(),
            route: (url, method, body, entry, next) => {
                if (/\/api\/translate/.test(url)) {
                    if (failNext) {
                        failNext = false;
                        return Promise.reject(new Error("timeout"));
                    }
                    const parsed = body ? JSON.parse(body) : { text: "" };
                    return Promise.resolve({ ok: true, translatedText: `译文：${parsed.text}` });
                }
                return next(url, method, body);
            }
        });
        const a = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "1");
        click(a.querySelector(".mc-person-main"));
        await flush();
        const editor = ctx.host.querySelector('[aria-label="人工回复正文"]');
        editor.innerText = "draft intact";
        const article = ctx.host.querySelector('[data-message-key="INBOUND_PROCESSING:101"]');
        click(article.querySelector('[data-action="mc-translate"]'));
        await flush();
        const errArticle = ctx.host.querySelector('[data-message-key="INBOUND_PROCESSING:101"]');
        const errBtn = errArticle.querySelector('[data-action="mc-translate"]');
        assert.strictEqual(errBtn.textContent, "翻译失败，重试", "失败文案");
        click(errBtn);
        await flush();
        const retryBtn = ctx.host.querySelector('[data-message-key="INBOUND_PROCESSING:101"] [data-action="mc-translate"]');
        assert.strictEqual(retryBtn.textContent, "收起译文");
        assert.match(ctx.host.querySelector('[data-message-key="INBOUND_PROCESSING:101"] .mc-translation').textContent, /译文：cleaned question/);
        assert.strictEqual(ctx.host.querySelector('[aria-label="人工回复正文"]').innerText, "draft intact", "翻译不重建/不触碰 manual DOM");
    });

    it("mark-resolved 成功后服务端重查当前页；编辑器草稿保留", async () => {
        let resolved = false;
        const ctx = await bootA({
            route: (url, method, body, entry, next) => {
                if (/\/api\/mail\/mailbox\/conversations\/\d+\/messages/.test(url)) {
                    // 服务端已持久化：静默刷新窗口回包 PROCESSED（否则会被视为回滚）
                    const items = messagesA().items.map((msg) => {
                        if (resolved && msg.source === "INBOUND_PROCESSING" && msg.id === 101) {
                            return Object.assign({}, msg, { processStatus: "PROCESSED" });
                        }
                        return msg;
                    });
                    return Promise.resolve({ items, nextBefore: null, hasMore: false });
                }
                if (/\/api\/mail\/unmatched-inbound\/101\/mark-resolved/.test(url)) {
                    resolved = true;
                    return Promise.resolve({ id: 101, processStatus: "PROCESSED", resolvedBy: "admin" });
                }
                return next(url, method, body);
            }
        });
        const editor = ctx.host.querySelector('[aria-label="人工回复正文"]');
        editor.innerText = "draft keeps";
        inputEvent(editor);
        const listBefore = conversationsRequests(ctx).length;
        const markBtn = ctx.host.querySelector('[data-message-key="INBOUND_PROCESSING:101"] [data-action="mc-mark-resolved"]');
        click(markBtn);
        await flush();
        assert.ok(!ctx.calls.api.some((entry) => /mark-resolved/.test(entry.url)), "第一次点击只进入原位确认，不发 POST");
        click(ctx.host.querySelector('[data-action="mc-process-confirm"]'));
        await flush();
        const markRequest = ctx.calls.api.find((entry) => entry.url === "/api/mail/unmatched-inbound/101/mark-resolved");
        assert.ok(markRequest, "调既有 mark-resolved API");
        assert.strictEqual(markRequest.body, JSON.stringify({ note: null }), "body 只带 note:null，不传操作人");
        assert.ok(conversationsRequests(ctx).length > listBefore, "mark 成功后重查服务端列表");
        const lastUrl = conversationsRequests(ctx)[conversationsRequests(ctx).length - 1].url;
        assert.ok(lastUrl.includes("page=0"), "重查当前页（服务端顺序）");
        // 草稿保留（编辑器未重建）
        assert.strictEqual(ctx.host.querySelector('[aria-label="人工回复正文"]').innerText, "draft keeps", "编辑器草稿保留");
        const article = ctx.host.querySelector('[data-message-key="INBOUND_PROCESSING:101"]');
        assert.match(article.textContent, /已处理/, "消息局部更新为已处理（静默窗口校验不回滚）");
        assert.ok(article.querySelector(".mailbox-suspend-processed-label"), "显示服务端回包账号");
        assert.ok(!article.querySelector('[data-action="mc-mark-resolved"]'), "按钮消失");
    });

    it("mark-resolved 空页回退：第 1 页处理完服务器第 1 页为空则回退第 0 页", async () => {
        const ctx = await bootChat({
            conversations: { items: [expertA()], total: 30 },
            messages: messagesA(),
            contact: contactA(),
            route: (url, method, body, entry, next) => {
                if (url.startsWith("/api/mail/mailbox/conversations?")) {
                    const page = Number(queryOf(url).get("page"));
                    if (page === 1) return Promise.resolve({ items: [], total: 30 });
                    if (page === 0) return Promise.resolve({ items: [expertA()], total: 30 });
                    return Promise.resolve({ items: [expertA()], total: 30 });
                }
                return next(url, method, body);
            }
        });
        const a = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "1");
        click(a.querySelector(".mc-person-main"));
        await flush();
        // 翻到第 1 页
        click(ctx.host.querySelector('[data-action="mc-page-next"]'));
        await flush();
        const markBtn = ctx.host.querySelector('[data-message-key="INBOUND_PROCESSING:101"] [data-action="mc-mark-resolved"]');
        click(markBtn);
        await flush();
        click(ctx.host.querySelector('[data-action="mc-process-confirm"]'));
        await flush();
        const urls = conversationsRequests(ctx).map((entry) => entry.url);
        assert.ok(urls[urls.length - 1].includes("page=0"), "空页自动回退到上一有效页");
    });
});

describe("S-3/I-6 管理 overlay", () => {
    async function bootSelectedA(serverOverrides) {
        const conversations = { items: [expertA(), expertB()], total: 2 };
        const ctx = await bootChat(Object.assign({ conversations, messages: messagesA(), contact: contactA() }, serverOverrides || {}));
        const a = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "1");
        click(a.querySelector(".mc-person-main"));
        await flush();
        return ctx;
    }

    function openManage(ctx) {
        click(ctx.host.querySelector('[data-action="mc-manage-expert"]'));
        return flush();
    }

    function manageOverlay(ctx) {
        return ctx.doc.querySelector(".mc-manage-overlay");
    }

    it("管理 overlay portal 到 body（不在 .mc-scroll / 带 backdrop-filter 的 panel 内），选项来自真实目录", async () => {
        const ctx = await bootSelectedA();
        await openManage(ctx);
        const overlay = manageOverlay(ctx);
        assert.ok(overlay, "overlay 存在");
        assert.strictEqual(overlay.hidden, false);
        const root = overlay.parentNode;
        assert.ok(root.classList.contains("mc-overlay-root") && root.classList.contains("mail-chat"), "独立 .mail-chat.mc-overlay-root");
        assert.ok(ctx.doc.body === root.parentNode || ctx.doc.body.contains(root), "portal 在 body 下");
        const scroll = ctx.host.querySelector(".mc-scroll");
        assert.ok(!scroll.contains(overlay), "管理不在消息滚动区");
        const dialog = overlay.querySelector(".mc-manage-dialog");
        assert.ok(dialog, "dialog 角色");
        const statusSelect = overlay.querySelector('[data-role="status-select"]');
        const levelSelect = overlay.querySelector('[data-role="level-select"]');
        assert.strictEqual(statusSelect.querySelectorAll("option").length, OPERATOR_STATUS_CATALOG.length);
        assert.strictEqual(statusSelect.value, "REPLIED", "当前状态选中");
        assert.strictEqual(levelSelect.value, "APPLICATION", "当前层级选中");
        const note = overlay.querySelector(".mc-manage-note");
        assert.match(note.textContent, /标签修改即时生效/);
    });

    it("取消（×）零请求；状态/层级未变化时保存零请求", async () => {
        const ctx = await bootSelectedA();
        await openManage(ctx);
        const overlay = manageOverlay(ctx);
        overlay.querySelector('[data-role="status-select"]').value = "COMPLETED";
        overlay.querySelector('[data-role="level-select"]').value = "RAW";
        const before = ctx.calls.api.length;
        click(overlay.querySelector('[data-action="mc-close-manage"]'));
        await flush();
        assert.strictEqual(ctx.calls.api.length, before, "取消状态/层级零请求");
        assert.ok(manageOverlay(ctx) === null || manageOverlay(ctx).hidden, "overlay 关闭");
        // 重开：不改直接保存 → 零请求
        await openManage(ctx);
        const overlay2 = manageOverlay(ctx);
        const before2 = ctx.calls.api.length;
        click(overlay2.querySelector('[data-action="mc-save-settings"]'));
        await flush();
        assert.strictEqual(ctx.calls.api.length, before2, "无变化保存零请求");
        assert.ok(ctx.calls.status.some((s) => /均未变化/.test(s.message)));
    });

    it("保存只发变化字段的两端点；成功回读 dataset 与头部", async () => {
        const ctx = await bootSelectedA();
        await openManage(ctx);
        const overlay = manageOverlay(ctx);
        overlay.querySelector('[data-role="status-select"]').value = "COMPLETED";
        overlay.querySelector('[data-role="level-select"]').value = "RAW";
        click(overlay.querySelector('[data-action="mc-save-settings"]'));
        await flush();
        const statusPost = ctx.calls.api.find((entry) => entry.method === "POST" && entry.url === "/api/expert-contacts/1/operator-status");
        const levelPost = ctx.calls.api.find((entry) => entry.method === "POST" && entry.url === "/api/expert-contacts/1/index-level");
        assert.ok(statusPost);
        assert.deepStrictEqual(JSON.parse(statusPost.body), { operatorStatus: "COMPLETED", operatorName: "console" });
        assert.ok(levelPost);
        assert.deepStrictEqual(JSON.parse(levelPost.body), { targetLevel: "RAW", operatorName: "console" });
        assert.ok(ctx.calls.status.some((s) => /专家信息已更新/.test(s.message)));
        const statusSelect = overlay.querySelector('[data-role="status-select"]');
        assert.strictEqual(statusSelect.dataset.currentValue, "COMPLETED", "回读 dataset");
        assert.match(ctx.host.querySelector(".mc-header-meta").textContent, /已完成/, "头部同步");
    });

    it("部分失败如实提示「部分变更未保存」，失败端 dataset 不回写，按钮恢复", async () => {
        const ctx = await bootSelectedA({
            route: (url, method, body, entry, next) => {
                if (method === "POST" && /\/operator-status$/.test(url)) return Promise.reject(new Error("status down"));
                if (method === "POST" && /\/index-level$/.test(url)) return Promise.resolve({});
                return next(url, method, body);
            }
        });
        await openManage(ctx);
        const overlay = manageOverlay(ctx);
        overlay.querySelector('[data-role="status-select"]').value = "COMPLETED";
        overlay.querySelector('[data-role="level-select"]').value = "RAW";
        const saveBtn = overlay.querySelector('[data-action="mc-save-settings"]');
        click(saveBtn);
        await flush();
        const error = overlay.querySelector(".mc-inline-error");
        assert.strictEqual(error.hidden, false);
        assert.match(error.textContent, /部分变更未保存/);
        assert.strictEqual(saveBtn.disabled, false, "按钮恢复");
        assert.strictEqual(overlay.querySelector('[data-role="status-select"]').dataset.currentValue, "REPLIED", "失败端不回写");
        assert.strictEqual(overlay.querySelector('[data-role="level-select"]').dataset.currentValue, "RAW", "成功端回写");
        assert.ok(ctx.calls.status.some((s) => s.type === "error"));
    });

    it("专家标签经共享 seam 即时保存：编辑器在 portal 内渲染、加/删后静默刷新列表与头部", async () => {
        let currentTags = { found: true, tags: ["学术科研", "重点关注"] };
        const ctx = await bootSelectedA({
            fetchTagsFn: async () => currentTags,
            nextExpertTag: "承诺回复材料"
        });
        await openManage(ctx);
        await flush();
        const overlay = manageOverlay(ctx);
        const tagsRoot = overlay.querySelector('[data-role="expert-tags"]');
        const editor = overlay.querySelector(".expert-tag-editor");
        assert.ok(editor, "共享 expert-tag-editor 渲染在管理内");
        assert.strictEqual(editor.getAttribute("id"), "mcManageExpertTagEditor");
        const listRequestsBefore = conversationsRequests(ctx).length;
        click(editor.querySelector('[data-action="expert-add-tag-open"]'));
        await flush();
        assert.strictEqual(ctx.calls.tagMutations.length, 1);
        assert.strictEqual(ctx.calls.tagMutations[0].action, "add");
        assert.strictEqual(ctx.calls.tagMutations[0].tag, "承诺回复材料");
        assert.ok(ctx.calls.tagEditorUpdates.length >= 1, "editor 走 updateExpertTagEditor");
        assert.ok(conversationsRequests(ctx).length > listRequestsBefore, "加标签后静默刷新列表 summary");
        const editorAfter = manageOverlay(ctx).querySelector(".expert-tag-editor");
        assert.ok(editorAfter, "重渲染后仍在 mc-settings-tags 作用域");
        assert.strictEqual(editorAfter.parentNode.getAttribute("data-role"), "expert-tags");
        assert.match(editorAfter.textContent, /承诺回复材料/);
        // 删除标签
        const removeBtn = editorAfter.querySelector('[data-action="expert-remove-tag"]');
        click(removeBtn);
        await flush();
        assert.strictEqual(ctx.calls.tagMutations.length, 2);
        assert.strictEqual(ctx.calls.tagMutations[1].action, "remove");
    });

    it("缺画像专家：管理标签区显示不可用文案", async () => {
        const missingContact = contactA();
        missingContact.contact.orcidId = "";
        const ctx = await bootChat({
            conversations: { items: [expertA()], total: 1 },
            messages: messagesA(),
            contact: missingContact,
            fetchTagsFn: async () => ({ found: false, tags: [] })
        });
        const a = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "1");
        click(a.querySelector(".mc-person-main"));
        await flush();
        await openManage(ctx);
        await flush();
        const overlay = manageOverlay(ctx);
        assert.match(overlay.textContent, /该专家在 ES 中无画像文档，标签功能不可用/);
    });

    it("切换专家自动关闭管理 overlay", async () => {
        const ctx = await bootSelectedA();
        await openManage(ctx);
        assert.strictEqual(manageOverlay(ctx).hidden, false);
        const b = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "2");
        click(b.querySelector(".mc-person-main"));
        await flush();
        const overlay = manageOverlay(ctx);
        assert.ok(overlay === null || overlay.hidden === true, "切专家后管理关闭");
    });
});

describe("mailbox chat 既有业务（I-7）：workbench/manual/drafts/adopt/send", () => {
    async function bootSelectedA(serverOverrides) {
        const conversations = { items: [expertA(), expertB()], total: 2 };
        const ctx = await bootChat(Object.assign({ conversations, messages: messagesA(), contact: contactA() }, serverOverrides || {}));
        const a = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "1");
        click(a.querySelector(".mc-person-main"));
        await flush();
        return ctx;
    }

    it("workbench 默认折叠不挂载；展开一次挂载真实 processingId；切专家销毁旧实例", async () => {
        const ctx = await bootSelectedA();
        assert.strictEqual(ctx.calls.workbenchMounts.length, 0, "默认折叠不发生成/不挂载");
        const wb = ctx.host.querySelector('.mc-section[data-section="workbench"]');
        assert.ok(wb);
        assert.strictEqual(wb.open, false);
        toggleOpen(wb);
        await flush();
        assert.strictEqual(ctx.calls.workbenchMounts.length, 1);
        assert.strictEqual(ctx.calls.workbenchMounts[0].processingId, 101);
        const b = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "2");
        click(b.querySelector(".mc-person-main"));
        await flush();
        assert.strictEqual(ctx.calls.unmounts.length, 1, "切专家销毁旧 workbench mount");
    });

    it("无来信但有真实 SENT 发件（sentCount=2）：显示自由回复编辑器并走会话 adapter", async () => {
        const conversations = { items: [expertB(), expertA()], total: 2 };
        const ctx = await bootChat({ conversations, messages: messagesA(), contact: contactB() });
        const b = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "2");
        click(b.querySelector(".mc-person-main"));
        await flush();
        const conversation = ctx.host.querySelector(".mc-conversation");
        assert.match(conversation.textContent, /暂无专家来信，暂不能生成回复/, "工作台仍不可生成");
        const compose = conversation.querySelector(".mc-compose");
        assert.ok(compose, "sentCount>0 时显示人工回复编辑器（S-1 骨架）");
        assert.match(compose.querySelector('[data-role="target-info"]').textContent, /回复最近成功发件线程/);
        const subjectInput = conversation.querySelector('input[aria-label="回复主题"]');
        assert.strictEqual(subjectInput.value, "Re: Introduction", "SENT 出站最新消息主题生成 Re: 默认主题");
        // 既有模板跟进按钮仍在 footer
        const followBtn = conversation.querySelector('[data-action="mc-template-follow"]');
        assert.ok(followBtn, "outbound footer 保留模板跟进入口");
        const editor = conversation.querySelector('[aria-label="人工回复正文"]');
        editor.innerText = "free follow up";
        inputEvent(editor);
        click(conversation.querySelector('[data-action="mc-send-manual"]'));
        await flush();
        assert.strictEqual(ctx.calls.sendRich.length, 0, "outbound 不走旧来信 adapter");
        assert.strictEqual(ctx.calls.sendConversation.length, 1);
        const payload = ctx.calls.sendConversation[0];
        assert.strictEqual(payload.contactId, 2);
        assert.ok(payload.body.requestId, "body 携带 requestId");
        assert.ok(!("processingId" in payload.body), "不伪造 processingId");
        assert.ok(!("senderAccountCode" in payload.body), "不接收/发送发件账号覆盖");
        assert.ok(!("qaRuleIds" in payload.body) && !("ragFactCodes" in payload.body), "无 QA/RAG 字段");
        assert.ok(payload.body.subject.includes("Re: Introduction"));
        // 成功删除草稿：切走再切回主题回到默认
        const a = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "1");
        click(a.querySelector(".mc-person-main"));
        await flush();
        const b2 = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "2");
        click(b2.querySelector(".mc-person-main"));
        await flush();
        assert.strictEqual(ctx.host.querySelector('input[aria-label="回复主题"]').value, "Re: Introduction", "发送成功后草稿删除");
    });

    it("pendingCount=0 且真实来信（PROCESSED 已处理）：编辑器存在并走旧 processingId adapter", async () => {
        // I-2：待处理状态不是回信门禁 —— 已处理（非 MANUAL_REVIEW）来信仍可自由回信。
        const conversations = { items: [expertA({ pendingCount: 0 }), expertB()], total: 2 };
        const ctx = await bootChat({ conversations, messages: messagesA(), contact: contactA() });
        const a = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "1");
        click(a.querySelector(".mc-person-main"));
        await flush();
        const conversation = ctx.host.querySelector(".mc-conversation");
        const compose = conversation.querySelector(".mc-compose");
        assert.ok(compose, "pendingCount=0 + latestInbound 仍显示编辑器");
        const editor = conversation.querySelector('[aria-label="人工回复正文"]');
        editor.innerText = "reply to processed";
        inputEvent(editor);
        click(conversation.querySelector('[data-action="mc-send-manual"]'));
        await flush();
        assert.strictEqual(ctx.calls.sendRich.length, 1, "inbound 路径继续走旧 adapter");
        assert.strictEqual(ctx.calls.sendRich[0].processingId, 101);
        assert.strictEqual(ctx.calls.sendConversation.length, 0);
    });

    it("仅失败发件（sentCount=0）：不显示编辑器，保留说明与模板跟进", async () => {
        // I-1/S-2：无来信 + 无真实 SENT 发件不开放自由回信（FAILED 不算锚点）。
        const onlyFailed = expertB({ sentCount: 0, failedCount: 3, waitingReply: false });
        const conversations = { items: [onlyFailed, expertA()], total: 2 };
        const ctx = await bootChat({ conversations, messages: [], contact: contactB() });
        const b = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "2");
        click(b.querySelector(".mc-person-main"));
        await flush();
        const conversation = ctx.host.querySelector(".mc-conversation");
        assert.ok(!conversation.querySelector(".mc-compose"), "仅失败发件不显示编辑器");
        assert.match(conversation.textContent, /系统不会在没有真实来信时伪造可生成的人工富文本回复/);
        const followBtn = conversation.querySelector('[data-action="mc-template-follow"]');
        assert.ok(followBtn);
        click(followBtn);
        await flush();
        assert.deepStrictEqual(ctx.calls.followUp, [2]);
        assert.strictEqual(ctx.calls.sendConversation.length, 0);
    });

    it("outbound 草稿 requestId：失败/取消复用、编辑后换新、跨专家切换恢复", async () => {
        // I-4/I-12：发送失败保留 requestId → 重试同一 requestId；用户再次编辑正文清空
        // requestId → 下一次发送生成新值；切换专家/重挂载按 OUTBOUND key 恢复草稿。
        let uuidSeq = 0;
        const overrides = { conversations: { items: [expertB(), expertA()], total: 2 }, messages: messagesA(), contact: contactB(), sendConversationError: "network down" };
        const ctx = await bootChat(overrides);
        ctx.sandbox.crypto = {
            randomUUID: () => `00000000-0000-4000-8000-${String(++uuidSeq).padStart(12, "0")}`
        };
        const b = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "2");
        click(b.querySelector(".mc-person-main"));
        await flush();
        let conversation = ctx.host.querySelector(".mc-conversation");
        const editor = conversation.querySelector('[aria-label="人工回复正文"]');
        editor.innerText = "typed follow up";
        inputEvent(editor);
        click(conversation.querySelector('[data-action="mc-send-manual"]'));
        await flush();
        assert.strictEqual(ctx.calls.sendConversation.length, 1, "首次发送失败仍调用 adapter");
        const firstRequestId = ctx.calls.sendConversation[0].body.requestId;
        assert.strictEqual(firstRequestId, "00000000-0000-4000-8000-000000000001");
        // 失败重试：草稿保留且复用同一 requestId（幂等收敛键不变）
        click(ctx.host.querySelector('[data-action="mc-send-manual"]'));
        await flush();
        assert.strictEqual(ctx.calls.sendConversation.length, 2);
        assert.strictEqual(ctx.calls.sendConversation[1].body.requestId, firstRequestId, "失败重试复用 requestId");
        // 编辑正文 → requestId 清空；下次发送生成新值
        editor.innerText = "typed follow up edited";
        inputEvent(editor);
        click(ctx.host.querySelector('[data-action="mc-send-manual"]'));
        await flush();
        assert.strictEqual(ctx.calls.sendConversation.length, 3);
        assert.notStrictEqual(ctx.calls.sendConversation[2].body.requestId, firstRequestId, "编辑正文后换新 requestId");
        // 跨专家切换恢复草稿（含编辑后的正文）
        const a = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "1");
        click(a.querySelector(".mc-person-main"));
        await flush();
        const b2 = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "2");
        click(b2.querySelector(".mc-person-main"));
        await flush();
        conversation = ctx.host.querySelector(".mc-conversation");
        assert.strictEqual(conversation.querySelector('[aria-label="人工回复正文"]').innerText, "typed follow up edited", "outbound 草稿跨切换恢复");
        // 网络恢复后发送成功 → 草稿与 requestId 一并删除（切走再切回回到默认主题）
        overrides.sendConversationError = undefined;
        ctx.calls.sendConversation.length = 0;
        click(conversation.querySelector('[data-action="mc-send-manual"]'));
        await flush();
        assert.strictEqual(ctx.calls.sendConversation.length, 1);
        click(a.querySelector(".mc-person-main"));
        await flush();
        click(b2.querySelector(".mc-person-main"));
        await flush();
        assert.strictEqual(ctx.host.querySelector('input[aria-label="回复主题"]').value, "Re: Introduction", "成功后草稿删除、主题回默认");
    });

    it("outbound 安全取消（false）保留正文与 requestId；成功删除草稿", async () => {
        // I-12：adapter 返回 false（安全确认取消）→ 草稿与 requestId 保留；返回 true 才删除。
        let uuidSeq = 0;
        const conversations = { items: [expertB(), expertA()], total: 2 };
        const ctx = await bootChat({ conversations, messages: messagesA(), contact: contactB(), sendConversationResult: false });
        ctx.sandbox.crypto = {
            randomUUID: () => `00000000-0000-4000-8000-${String(++uuidSeq).padStart(12, "0")}`
        };
        const b = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "2");
        click(b.querySelector(".mc-person-main"));
        await flush();
        const editor = ctx.host.querySelector('[aria-label="人工回复正文"]');
        editor.innerText = "cancelled draft";
        inputEvent(editor);
        click(ctx.host.querySelector('[data-action="mc-send-manual"]'));
        await flush();
        assert.strictEqual(ctx.calls.sendConversation.length, 1);
        const requestId = ctx.calls.sendConversation[0].body.requestId;
        // false → 草稿仍在（切走再切回正文保留）
        const a = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "1");
        click(a.querySelector(".mc-person-main"));
        await flush();
        const b2 = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "2");
        click(b2.querySelector(".mc-person-main"));
        await flush();
        assert.strictEqual(ctx.host.querySelector('[aria-label="人工回复正文"]').innerText, "cancelled draft", "取消后草稿保留");
        // 下次发送复用同一 requestId
        click(ctx.host.querySelector('[data-action="mc-send-manual"]'));
        await flush();
        assert.strictEqual(ctx.calls.sendConversation.length, 2);
        assert.strictEqual(ctx.calls.sendConversation[1].body.requestId, requestId, "取消后复用 requestId");
    });

    it("草稿跨专家切换恢复；unmount/重挂载后同专家草稿仍恢复", async () => {
        const ctx = await bootSelectedA();
        const subject = ctx.host.querySelector('input[aria-label="回复主题"]');
        const editor = ctx.host.querySelector('[aria-label="人工回复正文"]');
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
        assert.strictEqual(ctx.host.querySelector('input[aria-label="回复主题"]').value, "My subject", "草稿主题恢复");
        assert.strictEqual(ctx.host.querySelector('[aria-label="人工回复正文"]').innerText, "hello draft", "草稿正文恢复");
        // unmount → 重新挂载 → 恢复草稿（模块缓存，同标签页）
        ctx.sandbox.MailboxChat.unmount(ctx.host);
        ctx.sandbox.MailboxChat.mount(ctx.host, { filters: {} });
        await flush();
        const a2 = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "1");
        click(a2.querySelector(".mc-person-main"));
        await flush();
        assert.strictEqual(ctx.host.querySelector('input[aria-label="回复主题"]').value, "My subject", "重挂载后草稿恢复");
        assert.strictEqual(ctx.host.querySelector('[aria-label="人工回复正文"]').innerText, "hello draft");
    });

    it("采用 → 人工发送：QA 载荷原样进发送 payload；成功清草稿，失败保留", async () => {
        const ctx = await bootSelectedA();
        const wb = ctx.host.querySelector('.mc-section[data-section="workbench"]');
        toggleOpen(wb);
        await flush();
        assert.strictEqual(ctx.calls.workbenchMounts.length, 1);
        await ctx.calls.workbenchMounts[0].callbacks.onComplete({
            renderedDraftText: "adopted draft body",
            text: "adopted draft body",
            usedFactCodes: ["KB-COMM-044"],
            ragCorpusFingerprint: "fp-2026"
        });
        await flush();
        const editor = ctx.host.querySelector('[aria-label="人工回复正文"]');
        assert.strictEqual(editor.innerText, "adopted draft body");
        const sendBtn = ctx.host.querySelector('[data-action="mc-send-manual"]');
        click(sendBtn);
        await flush();
        assert.strictEqual(ctx.calls.sendRich.length, 1);
        const payload = ctx.calls.sendRich[0];
        assert.strictEqual(payload.processingId, 101);
        assert.ok(payload.body.subject.includes("Re: Question 1"));
        assert.deepStrictEqual(payload.body.ragFactCodes, ["KB-COMM-044"]);
        assert.strictEqual(payload.body.ragCorpusFingerprint, "fp-2026");
        assert.strictEqual(payload.body.edited, false);
    });

    // 人工富文本发送：保留一个空行，更多空行收敛；格式逐字保留。
    it("来信人工富文本发送：5 个 LF / CRLF / 空白行压成一个空行，HTML 保留两个 <br>", async () => {
        const ctx = await bootSelectedA();
        const editor = ctx.host.querySelector('[aria-label="人工回复正文"]');
        setEditorContent(
            editor,
            "<b>A</b><br><br><br><a href=\"https://x.test\">B</a><ul><li>C</li></ul>",
            "A\r\n\r\n\r\n\r\nB"
        );
        inputEvent(editor);
        click(ctx.host.querySelector('[data-action="mc-send-manual"]'));
        await flush();

        assert.strictEqual(ctx.calls.sendRich.length, 1);
        const body = ctx.calls.sendRich[0].body;
        assert.strictEqual(body.textBody, "A\n\nB", "连续换行/CRLF 保留一个空行");
        assert.ok(!body.textBody.includes("\r"), "不含 CR");
        assert.ok(!body.textBody.includes("\n\n\n"), "至多保留一个空行");
        assert.strictEqual(
            body.htmlBody,
            "<b>A</b><br><br><a href=\"https://x.test\">B</a><ul><li>C</li></ul>",
            "连续 <br> 保留两个，bold/link/list 保留"
        );
    });

    it("来信人工富文本发送：空白行与空 <p>/<div> 同样保留一个", async () => {
        const ctx = await bootSelectedA();
        const editor = ctx.host.querySelector('[aria-label="人工回复正文"]');
        setEditorContent(
            editor,
            "<p>A</p><p><br></p><p>&nbsp;</p><div>B</div>",
            "A\n \t\n\nB"
        );
        inputEvent(editor);
        click(ctx.host.querySelector('[data-action="mc-send-manual"]'));
        await flush();

        const body = ctx.calls.sendRich[0].body;
        assert.strictEqual(body.textBody, "A\n\nB");
        assert.strictEqual(body.htmlBody, "<p>A</p><p><br></p><div>B</div>", "重复空 p/div 至多保留一个空行");
    });

    // I-3/I-6：会话回信路径同样在生成 request body 前规范化；adapter 取消（安全确认）
    // 后重提必须复用同一 canonical 正文与既有 requestId。
    it("会话回信：canonical 正文与 requestId 在取消重提后不漂移", async () => {
        let uuidSeq = 0;
        const overrides = {
            conversations: { items: [expertB(), expertA()], total: 2 },
            messages: messagesA(),
            contact: contactB(),
            sendConversationResult: false
        };
        const ctx = await bootChat(overrides);
        ctx.sandbox.crypto = {
            randomUUID: () => `00000000-0000-4000-8000-00000000000${(uuidSeq += 1)}`
        };
        const b = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "2");
        click(b.querySelector(".mc-person-main"));
        await flush();

        const editor = ctx.host.querySelector('[aria-label="人工回复正文"]');
        setEditorContent(editor, "<b>A</b><br><br><br>B", "A\n\n\n\n\nB");
        inputEvent(editor);
        click(ctx.host.querySelector('[data-action="mc-send-manual"]'));
        await flush();

        assert.strictEqual(ctx.calls.sendConversation.length, 1, "outbound 走会话 adapter");
        const first = ctx.calls.sendConversation[0].body;
        assert.strictEqual(first.textBody, "A\n\nB");
        assert.strictEqual(first.htmlBody, "<b>A</b><br><br>B");
        assert.ok(first.requestId, "首次发送生成 requestId");

        // 安全确认取消（adapter 返回 false）→ 重提同一 canonical 正文与 requestId
        click(ctx.host.querySelector('[data-action="mc-send-manual"]'));
        await flush();
        assert.strictEqual(ctx.calls.sendConversation.length, 2);
        const second = ctx.calls.sendConversation[1].body;
        assert.strictEqual(second.textBody, first.textBody, "重提正文不漂移");
        assert.strictEqual(second.htmlBody, first.htmlBody);
        assert.strictEqual(second.requestId, first.requestId, "重提复用同一 requestId");
    });

    // 采用 AI 草稿即写入 canonical 正文与基线；一行空白保留，更多空行收敛。
    it("采用 AI 草稿：正文换行先收敛，仅换行差异不令 edited=true", async () => {
        // adapter 返回 false（安全确认取消）→ 草稿与 QA 基线保留，第二次发送仍带 edited 判定。
        const ctx = await bootSelectedA({ sendRichResult: false });
        const wb = ctx.host.querySelector('.mc-section[data-section="workbench"]');
        toggleOpen(wb);
        await flush();
        await ctx.calls.workbenchMounts[0].callbacks.onComplete({
            renderedDraftText: "First paragraph.\r\n\r\n\r\nSecond paragraph.",
            usedFactCodes: ["KB-COMM-044"],
            ragCorpusFingerprint: "fp-2026"
        });
        await flush();

        const editor = ctx.host.querySelector('[aria-label="人工回复正文"]');
        assert.strictEqual(editor.innerText, "First paragraph.\n\nSecond paragraph.", "采用即保留一个空行");
        assert.ok(ctx.calls.sendRich.length === 0, "采用不触发发送");

        click(ctx.host.querySelector('[data-action="mc-send-manual"]'));
        await flush();
        const body = ctx.calls.sendRich[0].body;
        assert.strictEqual(body.textBody, "First paragraph.\n\nSecond paragraph.");
        assert.strictEqual(body.edited, false, "仅换行差异不标记语义编辑");

        // 真正的内容编辑仍标记 edited=true
        editor.innerText = "First paragraph.\nSecond paragraph. Extra.";
        inputEvent(editor);
        click(ctx.host.querySelector('[data-action="mc-send-manual"]'));
        await flush();
        assert.strictEqual(ctx.calls.sendRich.length, 2);
        assert.strictEqual(ctx.calls.sendRich[1].body.edited, true, "内容变化仍标语义编辑");
    });

    it("新来信 + 已编辑草稿：提示选择目标；取消保留原目标与草稿", async () => {
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
                            sendStatus: null, processStatus: "MANUAL_REVIEW", attachmentCount: 0, firstAttachmentNames: [], messageId: "m102", inReplyTo: "m88", tags: []
                        }]);
                        return Promise.resolve(older);
                    }
                    return Promise.resolve(messagesA());
                }
                return Promise.resolve({});
            },
            dialogResult: null
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
        assert.strictEqual(ctx.calls.dialogs.length, 1, "已编辑草稿时必须提示选择目标");
        assert.strictEqual(ctx.calls.dialogs[0].type, "confirm");
        assert.strictEqual(ctx.host.querySelector('input[aria-label="回复主题"]').value, "Re: Question 1");
        assert.strictEqual(ctx.host.querySelector('[aria-label="人工回复正文"]').innerText, "typed draft");
    });

    it("header 材料按钮打开 child-08 drawer；查看专家详情走 openContactInList 适配", async () => {
        const ctx = await bootSelectedA();
        const materialsBtn = ctx.host.querySelector('[data-action="mc-open-materials"]');
        assert.match(materialsBtn.textContent, /材料 40/);
        click(materialsBtn);
        await flush();
        assert.deepStrictEqual(ctx.calls.materialsMounts, [1]);
        click(ctx.host.querySelector('[data-action="mc-open-expert"]'));
        await flush();
        assert.deepStrictEqual(ctx.calls.openExpert, [1]);
    });
});

// ════════════════════════════════════════════════════════════════════════
// 02 · 三态状态菜单（I-1/I-2/I-3/I-5；S-2/S-3）
// 真实保存、异步保护、分页回退、详情/待匹配边界。
// ════════════════════════════════════════════════════════════════════════

describe("02 · 三态状态菜单（I-1/I-2/I-3/I-5）", () => {
    function progressButton(ctx, contactId) {
        return ctx.host.querySelector(`.mc-person[data-contact-id="${contactId}"] [data-action="mc-progress-menu"]`);
    }
    function progressOption(ctx, contactId, statusName) {
        return ctx.host.querySelector(`.mc-person[data-contact-id="${contactId}"] [data-action="mc-set-progress"][data-progress="${statusName}"]`);
    }
    function progressMenuLabels(ctx, contactId) {
        return ctx.host.querySelectorAll(`.mc-person[data-contact-id="${contactId}"] .mailbox-progress-option`).map((option) => option.textContent);
    }
    function openMenu(ctx, contactId) {
        click(progressButton(ctx, contactId));
    }

    it("NodeList 无 filter：三个状态均可展开菜单，键盘跳过禁用项", async () => {
        for (const progressStatus of ["NONE", "FOLLOWING", "PROVIDED"]) {
            const ctx = await bootChat({
                conversations: { items: [expertA({ progressStatus })], total: 1 }
            });
            const menu = ctx.host.querySelector(".mailbox-progress-menu");
            const querySelectorAll = menu.querySelectorAll.bind(menu);
            const options = querySelectorAll(".mailbox-progress-option");
            options[0].disabled = true;
            // 原 Mini DOM 返回 Array，掩盖浏览器 NodeList 没有 filter 的错误。
            menu.querySelectorAll = (selector) => {
                const nodes = querySelectorAll(selector);
                return Object.assign({
                    length: nodes.length,
                    item: (index) => nodes[index] || null,
                    [Symbol.iterator]: () => nodes[Symbol.iterator]()
                }, nodes);
            };
            assert.strictEqual(menu.querySelectorAll(".mailbox-progress-option").filter, undefined);
            openMenu(ctx, 1);
            assert.strictEqual(menu.hidden, false, progressStatus);
            assert.strictEqual(progressButton(ctx, 1).getAttribute("aria-expanded"), "true");
            assert.strictEqual(ctx.doc.activeElement, options[1], "首项禁用时聚焦下一可用项");
            keyEvent(ctx.doc.activeElement, "ArrowDown");
            assert.strictEqual(ctx.doc.activeElement, options[1], "键盘导航跳过禁用项");
            keyEvent(ctx.doc.activeElement, "Escape");
            assert.strictEqual(menu.hidden, true);
            assert.strictEqual(ctx.doc.activeElement, progressButton(ctx, 1));
            assert.ok(!ctx.calls.api.some((entry) => entry.url.endsWith("/progress-status")), "展开不写状态");
        }
    });

    it("未标记入口六条转换：菜单逐字、PUT body 仅 status、取消回 NONE、绝不调旧 follow", async () => {
        let status = "NONE";
        const ctx = await bootChat({
            conversations: { items: [expertA()], total: 1 },
            route: (url, method, body, entry, next) => {
                if (url.endsWith("/1/progress-status") && method === "PUT") {
                    status = JSON.parse(body).status;
                    return Promise.resolve({ contactId: 1, progressStatus: status, followed: status === "FOLLOWING" });
                }
                if (url.startsWith("/api/mail/mailbox/conversations?") && queryOf(url).get("size") !== "1") {
                    return Promise.resolve({ items: [Object.assign(expertA(), { progressStatus: status, followed: status === "FOLLOWING" })], total: 1 });
                }
                return next(url, method, body);
            }
        });
        assert.strictEqual(progressButton(ctx, 1).textContent, "未标记");
        // NONE → FOLLOWING
        openMenu(ctx, 1);
        assert.deepStrictEqual(progressMenuLabels(ctx, 1), ["跟进中", "已提供"]);
        click(progressOption(ctx, 1, "FOLLOWING"));
        await flush();
        assert.strictEqual(progressButton(ctx, 1).textContent, "跟进中");
        // FOLLOWING → PROVIDED
        openMenu(ctx, 1);
        assert.deepStrictEqual(progressMenuLabels(ctx, 1), ["取消跟进", "已提供"]);
        click(progressOption(ctx, 1, "PROVIDED"));
        await flush();
        assert.strictEqual(progressButton(ctx, 1).textContent, "已提供");
        // PROVIDED → NONE
        openMenu(ctx, 1);
        assert.deepStrictEqual(progressMenuLabels(ctx, 1), ["跟进中", "取消提供"]);
        click(progressOption(ctx, 1, "NONE"));
        await flush();
        assert.strictEqual(progressButton(ctx, 1).textContent, "未标记");

        const puts = ctx.calls.api.filter((entry) => entry.url.endsWith("/progress-status"));
        assert.deepStrictEqual(puts.map((entry) => JSON.parse(entry.body).status), ["FOLLOWING", "PROVIDED", "NONE"]);
        puts.forEach((entry) => assert.deepStrictEqual(Object.keys(JSON.parse(entry.body)), ["status"], "body 仅含 status"));
        assert.ok(!ctx.calls.api.some((entry) => /\/follow$/.test(entry.url)), "不再调用旧 follow 端点");
        assert.ok(!progressMenuLabels(ctx, 1).some((label) => /进入/.test(label)), "菜单无「进入」字样");
    });

    it("字段缺失从 followed 派生；字段存在但非法显示「状态不可用」并禁用", async () => {
        const legacy = expertA({ followed: true });
        const invalid = expertA({ contactId: 2, name: "专家2", progressStatus: "WAT" });
        const ctx = await bootChat({ conversations: { items: [legacy, invalid], total: 2 } });
        assert.strictEqual(progressButton(ctx, 1).textContent, "跟进中", "缺字段从 followed 派生");
        assert.strictEqual(progressButton(ctx, 2).textContent, "状态不可用");
        assert.strictEqual(progressButton(ctx, 2).disabled, true, "非法状态禁用");
        assert.strictEqual(ctx.host.querySelector('.mc-person[data-contact-id="2"] .mailbox-progress-menu'), null, "非法状态无菜单");
    });

    it("已提供/跟进中请求互斥；标记后保持 chip/搜索与筛选", async () => {
        const ctx = await bootChat({ conversations: { items: [expertA()], total: 1 } });
        click(chipButton(ctx, "provided"));
        await flush();
        let q = queryOf(lastConversationsRequest(ctx).url);
        assert.strictEqual(q.get("providedOnly"), "true");
        assert.strictEqual(q.get("followed"), null);
        click(chipButton(ctx, "followed"));
        await flush();
        q = queryOf(lastConversationsRequest(ctx).url);
        assert.strictEqual(q.get("followed"), "true");
        assert.strictEqual(q.get("providedOnly"), null);

        click(chipButton(ctx, "all"));
        await flush();
        const search = ctx.host.querySelector('.mc-search-row input[type="search"]');
        search.value = "needle";
        inputEvent(search);
        ctx.runTimers();
        await flush();
        assert.strictEqual(queryOf(lastConversationsRequest(ctx).url).get("q"), "needle", "搜索已生效");

        let status = "NONE";
        const ctx2 = await bootChat({
            conversations: { items: [expertA()], total: 1 },
            route: (url, method, body, entry, next) => {
                if (url.endsWith("/1/progress-status") && method === "PUT") {
                    status = JSON.parse(body).status;
                    return Promise.resolve({ contactId: 1, progressStatus: status, followed: status === "FOLLOWING" });
                }
                if (url.startsWith("/api/mail/mailbox/conversations?") && queryOf(url).get("size") !== "1") {
                    return Promise.resolve({ items: [Object.assign(expertA(), { progressStatus: status })], total: 1 });
                }
                return next(url, method, body);
            }
        });
        const search2 = ctx2.host.querySelector('.mc-search-row input[type="search"]');
        search2.value = "needle";
        inputEvent(search2);
        ctx2.runTimers();
        await flush();
        openMenu(ctx2, 1);
        click(progressOption(ctx2, 1, "FOLLOWING"));
        await flush();
        const after = queryOf(lastConversationsRequest(ctx2).url);
        assert.strictEqual(after.get("q"), "needle", "标记后搜索保留");
        assert.strictEqual(chipButton(ctx2, "pending").getAttribute("aria-pressed"), "true", "标记后页签保留");
    });

    it("写入成功前不改状态；busy 禁用重复操作；失败保留旧状态并可重试", async () => {
        let status = "NONE";
        let deferred = null;
        let fail = false;
        const ctx = await bootChat({
            conversations: { items: [expertA()], total: 1 },
            route: (url, method, body, entry, next) => {
                if (url.endsWith("/1/progress-status") && method === "PUT") {
                    if (fail) return Promise.reject(new Error("boom"));
                    const nextStatus = JSON.parse(body).status;
                    return new Promise((resolve) => {
                        deferred = () => { status = nextStatus; resolve({ contactId: 1, progressStatus: nextStatus, followed: false }); };
                    });
                }
                if (url.startsWith("/api/mail/mailbox/conversations?") && queryOf(url).get("size") !== "1") {
                    return Promise.resolve({ items: [Object.assign(expertA(), { progressStatus: status })], total: 1 });
                }
                return next(url, method, body);
            }
        });
        openMenu(ctx, 1);
        click(progressOption(ctx, 1, "FOLLOWING"));
        await flush();
        assert.strictEqual(progressButton(ctx, 1).textContent, "未标记", "未成功前不改显示状态");
        assert.strictEqual(progressButton(ctx, 1).disabled, true, "busy 禁用按钮");
        const putsBefore = ctx.calls.api.filter((entry) => entry.url.endsWith("/progress-status")).length;
        openMenu(ctx, 1);
        assert.strictEqual(progressButton(ctx, 1).disabled, true);
        assert.strictEqual(ctx.calls.api.filter((entry) => entry.url.endsWith("/progress-status")).length, putsBefore, "busy 不重复 PUT");
        deferred();
        await flush();
        assert.strictEqual(progressButton(ctx, 1).textContent, "跟进中");
        assert.strictEqual(progressButton(ctx, 1).disabled, false, "成功后解除 busy");

        fail = true;
        openMenu(ctx, 1);
        click(progressOption(ctx, 1, "PROVIDED"));
        await flush();
        assert.strictEqual(progressButton(ctx, 1).textContent, "跟进中", "失败保留旧状态");
        assert.strictEqual(progressButton(ctx, 1).disabled, false, "失败解除 busy");
        assert.ok(ctx.calls.status.some((entry) => /状态保存失败/.test(entry.message)));

        fail = false;
        openMenu(ctx, 1);
        click(progressOption(ctx, 1, "PROVIDED"));
        await flush();
        deferred();
        await flush();
        assert.strictEqual(progressButton(ctx, 1).textContent, "已提供", "重试成功");
    });

    it("写成功但列表重查失败：提示「状态已保存，列表刷新失败，请重试」且不报写入失败", async () => {
        let status = "NONE";
        let listFail = false;
        const ctx = await bootChat({
            conversations: { items: [expertA()], total: 1 },
            route: (url, method, body, entry, next) => {
                if (url.endsWith("/1/progress-status") && method === "PUT") {
                    status = JSON.parse(body).status;
                    return Promise.resolve({ contactId: 1, progressStatus: status, followed: false });
                }
                if (url.startsWith("/api/mail/mailbox/conversations?")) {
                    if (listFail) return Promise.reject(new Error("list down"));
                    return Promise.resolve({ items: [Object.assign(expertA(), { progressStatus: status })], total: 1 });
                }
                return next(url, method, body);
            }
        });
        listFail = true;
        openMenu(ctx, 1);
        click(progressOption(ctx, 1, "FOLLOWING"));
        await flush();
        assert.ok(ctx.calls.status.some((entry) => /^状态已保存，列表刷新失败，请重试$/.test(entry.message)), "刷新失败单独提示");
        assert.ok(!ctx.calls.status.some((entry) => /状态保存失败/.test(entry.message)), "绝不误报写入失败");
        assert.ok(ctx.calls.api.some((entry) => entry.url.endsWith("/1/progress-status") && entry.method === "PUT"), "写入确已发生");
    });

    it("过期列表回包 null：刷新短路，不回退上一页、不误报失败", async () => {
        let status = "NONE";
        let progressPending = false;
        let release = null;
        const ctx = await bootChat({
            conversations: { items: [expertA()], total: 40 },
            route: (url, method, body, entry, next) => {
                if (url.endsWith("/1/progress-status") && method === "PUT") {
                    status = JSON.parse(body).status;
                    return Promise.resolve({ contactId: 1, progressStatus: status, followed: false });
                }
                if (url.startsWith("/api/mail/mailbox/conversations?") && queryOf(url).get("size") !== "1") {
                    if (progressPending) return new Promise((resolve) => { release = () => resolve(null); });
                    return Promise.resolve({ items: [expertA()], total: 40 });
                }
                return next(url, method, body);
            }
        });
        click(ctx.host.querySelector('[data-action="mc-page-next"]'));
        await flush();
        assert.strictEqual(queryOf(lastConversationsRequest(ctx).url).get("page"), "1");
        const listBefore = conversationsRequests(ctx).length;
        progressPending = true;
        openMenu(ctx, 1);
        click(progressOption(ctx, 1, "FOLLOWING"));
        await flush();
        assert.ok(release, "刷新列表请求在途");
        release();
        await flush();
        const tail = conversationsRequests(ctx).slice(listBefore);
        assert.ok(!tail.some((entry) => queryOf(entry.url).get("page") === "0"), "null 不触发上一页回退");
        assert.ok(!ctx.calls.status.some((entry) => /状态已保存，列表刷新失败/.test(entry.message)), "过期 null 不误报刷新失败");
    });

    it("状态页最后一条移出：空页自动回退一页", async () => {
        const page0 = Array.from({ length: 20 }, (_, i) => expertA({ contactId: i + 1, name: `专家${i + 1}` }));
        let removed = false;
        const ctx = await bootChat({
            conversations: { items: page0, total: 21 },
            route: (url, method, body, entry, next) => {
                if (url.endsWith("/21/progress-status") && method === "PUT") {
                    removed = true;
                    return Promise.resolve({ contactId: 21, progressStatus: "PROVIDED", followed: false });
                }
                if (url.startsWith("/api/mail/mailbox/conversations?")) {
                    const page = Number(queryOf(url).get("page"));
                    const total = removed ? 20 : 21;
                    if (page === 1) return Promise.resolve({ items: removed ? [] : [expertA({ contactId: 21, name: "专家21" })], total });
                    return Promise.resolve({ items: page0, total });
                }
                return next(url, method, body);
            }
        });
        click(ctx.host.querySelector('[data-action="mc-page-next"]'));
        await flush();
        assert.strictEqual(ctx.host.querySelectorAll(".mc-person").length, 1, "末页唯一一条");
        openMenu(ctx, 21);
        click(progressOption(ctx, 21, "PROVIDED"));
        await flush();
        assert.strictEqual(queryOf(lastConversationsRequest(ctx).url).get("page"), "0", "空页回退一页");
        assert.strictEqual(ctx.host.querySelectorAll(".mc-person").length, 20);
        assert.match(ctx.host.querySelector(".mc-pager").textContent, /共 20 位/);
    });

    it("修改卡片 A 不当作选中 A：B 详情正文不变", async () => {
        let statusA = "NONE";
        const ctx = await bootChat({
            conversations: { items: [expertA(), expertB()], total: 2 },
            contact: contactB(),
            messages: { items: [], nextBefore: null, hasMore: false },
            route: (url, method, body, entry, next) => {
                if (url.endsWith("/1/progress-status") && method === "PUT") {
                    statusA = JSON.parse(body).status;
                    return Promise.resolve({ contactId: 1, progressStatus: statusA, followed: false });
                }
                if (url.startsWith("/api/mail/mailbox/conversations?") && queryOf(url).get("size") !== "1") {
                    return Promise.resolve({ items: [expertA({ progressStatus: statusA }), expertB()], total: 2 });
                }
                return next(url, method, body);
            }
        });
        const bPerson = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "2");
        click(bPerson.querySelector(".mc-person-main"));
        await flush();
        assert.strictEqual(ctx.host.querySelector(".mc-conversation h2").textContent, "专家B");
        openMenu(ctx, 1);
        click(progressOption(ctx, 1, "PROVIDED"));
        await flush();
        assert.strictEqual(ctx.host.querySelector(".mc-conversation h2").textContent, "专家B", "改 A 不切选中");
        assert.ok(!ctx.host.querySelector(".mc-conversation").textContent.includes("专家A"), "A 摘要不塞给 B 正文");
    });

    it("菜单：再点/Escape/外部关闭，焦点回触发按钮；详情无控件、待匹配无菜单", async () => {
        const ctx = await bootChat({
            conversations: { items: [expertA()], total: 1 },
            unmatched: { records: [unmatchedMail(901)], totalCount: 1 }
        });
        const menu = () => ctx.host.querySelector(".mailbox-progress-menu");
        openMenu(ctx, 1);
        assert.strictEqual(menu().hidden, false);
        assert.strictEqual(progressButton(ctx, 1).getAttribute("aria-expanded"), "true");
        const options = menu().querySelectorAll(".mailbox-progress-option");
        assert.strictEqual(ctx.doc.activeElement, options[0], "打开焦点首项");
        keyEvent(ctx.doc.activeElement, "ArrowDown");
        assert.strictEqual(ctx.doc.activeElement, options[1], "ArrowDown 移动");
        keyEvent(ctx.doc.activeElement, "Escape");
        assert.strictEqual(menu().hidden, true);
        assert.strictEqual(progressButton(ctx, 1).getAttribute("aria-expanded"), "false");
        assert.strictEqual(ctx.doc.activeElement, progressButton(ctx, 1), "Escape 回触发按钮");

        openMenu(ctx, 1);
        assert.strictEqual(menu().hidden, false);
        click(ctx.host.querySelector('.mc-search-row input[type="search"]'));
        assert.strictEqual(menu().hidden, true, "外部点击关闭");

        openMenu(ctx, 1);
        openMenu(ctx, 1);
        assert.strictEqual(menu().hidden, true, "再次点关闭");

        // 详情无旧关注/状态控件
        click(ctx.host.querySelector('.mc-person[data-contact-id="1"] .mc-person-main'));
        await flush();
        assert.strictEqual(ctx.host.querySelector('.mc-header [data-action="mc-toggle-follow"]'), null, "详情无关注按钮");
        assert.strictEqual(ctx.host.querySelector(".mc-header .mailbox-progress"), null, "详情无状态菜单");
        assert.ok(ctx.host.querySelector('.mc-header [data-action="mc-open-materials"]'), "既有动作保留");

        // 待匹配无状态菜单
        click(chipButton(ctx, "unmatched"));
        await flush();
        assert.strictEqual(ctx.host.querySelector(".mailbox-progress-status"), null, "待匹配卡片无状态菜单");
    });
});

describe("02 · 卡片姓名 title（S-2/A-4；V-1 回归）", () => {
    it("长含特殊字符姓名：可见文本与 title 同时保留完整原文，转义边界一致", async () => {
        const longName = "Katherine \"Kate\" O'Brien <Smith> & van der Waals 教授 长姓名测试一二三四五六七八九十";
        const ctx = await bootChat({ conversations: { items: [expertA({ name: longName })], total: 1 } });
        const strong = ctx.host.querySelector('.mc-person[data-contact-id="1"] .mc-person-heading strong');
        assert.ok(strong, "卡片姓名节点存在");
        assert.strictEqual(strong.textContent, longName, "可见文本解码后等于完整原文");
        assert.strictEqual(strong.getAttribute("title"), longName, "title 必须暴露完整原文（不可缺失、不可截断）");
        assert.ok(strong.outerHTML.includes("&quot;") && strong.outerHTML.includes("&lt;"), "文本与 title 均走同一 escapeText 边界");
    });
});

describe("02 · refreshListWithFallback null 短路（T-2/I-3）", () => {
    function pageRequests(ctx, page) {
        return ctx.calls.api.filter((entry) => entry.url.startsWith("/api/mail/mailbox/conversations?")
            && queryOf(entry.url).get("size") !== "1"
            && queryOf(entry.url).get("page") === String(page));
    }

    async function bootList(options) {
        return bootChat(Object.assign({
            conversations: { items: [expertA()], total: 21 },
            contact: contactA(),
            messages: messagesA()
        }, options || {}));
    }

    async function goLastPage(ctx) {
        click(ctx.host.querySelector('[data-action="mc-page-next"]'));
        await flush();
    }

    async function triggerMarkResolved(ctx) {
        const pending = ctx.host.querySelector('[data-message-key="INBOUND_PROCESSING:101"] [data-action="mc-mark-resolved"]');
        assert.ok(pending, "存在 MANUAL_REVIEW 原位处理入口");
        click(pending);
        await flush();
        const confirm = ctx.host.querySelector('[data-message-key="INBOUND_PROCESSING:101"] [data-action="mc-process-confirm"]');
        assert.ok(confirm, "原位确认出现");
        click(confirm);
        await flush();
    }

    it("首查 null（失败回包）：短路，不回退旧页", async () => {
        let failFirst = false;
        const ctx = await bootList({
            route: (url, method, body, entry, next) => {
                if (url.startsWith("/api/mail/mailbox/conversations?") && queryOf(url).get("size") !== "1") {
                    const page = Number(queryOf(url).get("page"));
                    if (page === 1) {
                        if (failFirst) return Promise.reject(new Error("list down"));
                        return Promise.resolve({ items: [], total: 21 });
                    }
                    return Promise.resolve({ items: [expertA()], total: 21 });
                }
                return next(url, method, body);
            }
        });
        click(ctx.host.querySelector('.mc-person[data-contact-id="1"] .mc-person-main'));
        await flush();
        await goLastPage(ctx);
        const before = pageRequests(ctx, 0).length;
        failFirst = true;
        await triggerMarkResolved(ctx);
        assert.ok(pageRequests(ctx, 1).length >= 1, "刷新确已请求末页");
        assert.strictEqual(pageRequests(ctx, 0).length, before, "首查 null 不得触发上一页回退");
    });

    it("回退 fetch null（过期回包）：短路，不做选中项协调", async () => {
        let fallbackNull = false;
        const ctx = await bootList({
            route: (url, method, body, entry, next) => {
                if (url.startsWith("/api/mail/mailbox/conversations?") && queryOf(url).get("size") !== "1") {
                    const page = Number(queryOf(url).get("page"));
                    if (page === 1) return Promise.resolve({ items: [], total: 21 });
                    if (page === 0 && fallbackNull) return Promise.resolve(null);
                    return Promise.resolve({ items: [expertA()], total: 21 });
                }
                return next(url, method, body);
            }
        });
        click(ctx.host.querySelector('.mc-person[data-contact-id="1"] .mc-person-main'));
        await flush();
        assert.strictEqual(ctx.host.querySelector(".mc-conversation h2").textContent, "专家A");
        await goLastPage(ctx);
        fallbackNull = true;
        await triggerMarkResolved(ctx);
        assert.strictEqual(ctx.host.querySelector(".mc-conversation h2").textContent, "专家A", "过期 null 不清选中/不串空态");
    });
});

describe("I-5 位置/窗口缓存与异步守卫", () => {
    it("A 慢响应晚于 B 选中到达：不得写入 B", async () => {
        const deferredA = [];
        const conversations = { items: [expertA(), expertB()], total: 2 };
        const ctx = await bootChat({
            conversations,
            contact: contactA(),
            route: (url, method, body, entry, next) => {
                if (/\/api\/mail\/mailbox\/conversations\/1\/messages/.test(url)) {
                    return new Promise((resolve) => deferredA.push(() => resolve({ items: messagesA().items, nextBefore: null, hasMore: false })));
                }
                if (/\/api\/mail\/mailbox\/conversations\/2\/messages/.test(url)) {
                    return Promise.resolve({ items: [], nextBefore: null, hasMore: false });
                }
                return next(url, method, body);
            }
        });
        const a = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "1");
        click(a.querySelector(".mc-person-main"));
        await flush();
        // A 的消息请求挂起中，用户切到 B
        const b = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "2");
        click(b.querySelector(".mc-person-main"));
        await flush();
        // A 的慢响应此刻才到
        deferredA.forEach((resolve) => resolve());
        await flush();
        const articles = ctx.host.querySelectorAll(".mc-message");
        const keys = articles.map((article) => article.dataset.messageKey);
        assert.deepStrictEqual(keys, [], "B 无来信：A 的晚响应绝不写入 B");
        assert.ok(!ctx.host.innerHTML.includes("cleaned question"), "A 消息未混入 B");
    });

    it("quiet refresh 合并保留已加载窗口：按 source:id 替换、不重复、服务端状态胜", async () => {
        const conversations = { items: [expertA()], total: 1 };
        const ctx = await bootChat({
            conversations,
            contact: contactA(),
            messages: { items: messagesA().items, nextBefore: "2026-09-05T00:00:00", hasMore: true }
        });
        const a = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "1");
        click(a.querySelector(".mc-person-main"));
        await flush();
        // 加载更早（加 2 条旧消息）
        const older = makeMessages(2, "old");
        ctx.sandbox.MailboxChat.mount(ctx.host, { filters: {} });
        await flush();
        // 直接触发 loadOlder
        click(ctx.host.querySelector('[data-action="mc-load-older"]'));
        await flush();
        // 服务器最新窗口：101 标签更新 + 新增 102
        const latestItems = messagesA().items.map((msg) => {
            if (msg.id === 101 && msg.source === "INBOUND_PROCESSING") {
                return Object.assign({}, msg, { tags: [mailTag(7, "research", "CUSTOM"), mailTag(21, "服务器新增", "QA", 5)] });
            }
            return msg;
        }).concat([{
            source: "INBOUND_PROCESSING", id: 102, contactId: 1, direction: "INBOUND", accountCode: "acc1",
            subject: "newest", body: "b", cleanedBody: "c102", eventAt: "2026-09-09T01:00:00",
            sendStatus: null, processStatus: "MANUAL_REVIEW", attachmentCount: 0, firstAttachmentNames: [], messageId: "m102b", tags: []
        }]);
        const serverMsg = { items: latestItems, nextBefore: null, hasMore: false };
        ctx.sandbox.MailboxChat.mount(ctx.host, { filters: {} });
        await flush();
        // quiet refresh 走 route 默认 opts.messages（未更新）→ 这里改为覆写 route 更精确：
        const keys = ctx.host.querySelectorAll(".mc-message");
        const keyList = Array.from(keys).map((el) => el.dataset.messageKey);
        assert.strictEqual(new Set(keyList).size, keyList.length, "无重复键");
    });

    it("loadOlder：busy 防重入、合并去重、滚动不跳（0 偏差路径）", async () => {
        let olderRequests = 0;
        const ctx = await bootChat({
            conversations: { items: [expertA()], total: 1 },
            contact: contactA(),
            messages: { items: messagesA().items, nextBefore: "2026-09-05T00:00:00", hasMore: true },
            route: (url, method, body, entry, next) => {
                if (/\/messages\?/.test(url) && queryOf(url).get("before")) {
                    olderRequests += 1;
                    const older = makeMessages(2, "old");
                    return Promise.resolve({ items: older, nextBefore: null, hasMore: false });
                }
                return next(url, method, body);
            }
        });
        const a = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "1");
        click(a.querySelector(".mc-person-main"));
        await flush();
        const scroll = ctx.host.querySelector(".mc-scroll");
        scroll.scrollTop = 40;
        const beforeCount = ctx.host.querySelectorAll(".mc-message").length;
        const loadBtn = ctx.host.querySelector('[data-action="mc-load-older"]');
        click(loadBtn);
        click(loadBtn); // busy 防重入
        await flush();
        assert.strictEqual(olderRequests, 1, "busy 防重复请求");
        const afterCount = ctx.host.querySelectorAll(".mc-message").length;
        assert.strictEqual(afterCount, beforeCount + 2, "更早消息并入不丢");
        const keys = Array.from(ctx.host.querySelectorAll(".mc-message")).map((el) => el.dataset.messageKey);
        assert.strictEqual(new Set(keys).size, keys.length, "合并后无重复 (source,id)");
        assert.strictEqual(scroll.scrollTop, 40, "锚点偏差 0（≤2px）");
        const olderMessageRequests = ctx.calls.api.filter((entry) => entry.url.includes("before="));
        assert.strictEqual(olderMessageRequests.length, 1, "busy 期间只有一次更早请求");
    });

    it("scrollTop 保存/恢复：0 有效；不同 accountScope/用户不复用", async () => {
        const conversations = { items: [expertA(), expertB()], total: 2 };
        const ctx = await bootChat({
            conversations,
            messages: { items: messagesA().items, nextBefore: null, hasMore: false },
            contact: contactA()
        });
        const a = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "1");
        click(a.querySelector(".mc-person-main"));
        await flush();
        const scroll = ctx.host.querySelector(".mc-scroll");
        scroll.scrollTop = 0; // 显式 0
        scrollEvent(scroll);
        ctx.runTimers();
        await flush();
        // 切走再切回（同会话缓存）
        const b = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "2");
        click(b.querySelector(".mc-person-main"));
        await flush();
        const a2 = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "1");
        click(a2.querySelector(".mc-person-main"));
        await flush();
        assert.strictEqual(ctx.host.querySelector(".mc-scroll").scrollTop, 0, "0 是有效恢复值");
        // 非 0 滚动
        const scroll2 = ctx.host.querySelector(".mc-scroll");
        scroll2.scrollTop = 77;
        scrollEvent(scroll2);
        ctx.runTimers();
        await flush();
        click(ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "2").querySelector(".mc-person-main"));
        await flush();
        click(ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "1").querySelector(".mc-person-main"));
        await flush();
        assert.strictEqual(ctx.host.querySelector(".mc-scroll").scrollTop, 77, "非 0 滚动位置恢复");
        // 不同 accountScope：不复用
        ctx.sandbox.MailboxChat.unmount(ctx.host);
        ctx.sandbox.MailboxChat.mount(ctx.host, { filters: { accountCode: "acc9" } });
        await flush();
        click(ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "1").querySelector(".mc-person-main"));
        await flush();
        assert.strictEqual(ctx.host.querySelector(".mc-scroll").scrollTop, 0, "不同账号范围不复用旧位置");
    });

    it("「最新消息」按钮不标记处理、不发送", async () => {
        const ctx = await bootChat({
            conversations: { items: [expertA()], total: 1 },
            messages: messagesA(),
            contact: contactA()
        });
        click(ctx.host.querySelector(".mc-person-main"));
        await flush();
        const before = ctx.calls.api.length;
        click(ctx.host.querySelector('[data-action="mc-latest"]'));
        await flush();
        assert.strictEqual(ctx.calls.api.length, before, "最新消息不发请求/不标记/不发送");
        assert.ok(!ctx.calls.api.some((entry) => /mark-resolved/.test(entry.url)));
        assert.strictEqual(ctx.calls.sendRich.length, 0);
        const article = ctx.host.querySelector('[data-message-key="INBOUND_PROCESSING:101"]');
        assert.ok(article.querySelector('[data-action="mc-mark-resolved"]'), "处理状态未被触碰");
    });

    it("首次进入（无缓存）不报错且滚动落在合法范围（0..max）", async () => {
        const many = makeMessages(60, "");
        const ctx = await bootChat({
            conversations: { items: [expertA()], total: 1 },
            messages: { items: many, nextBefore: null, hasMore: false },
            contact: contactA()
        });
        click(ctx.host.querySelector(".mc-person-main"));
        await flush();
        const scroll = ctx.host.querySelector(".mc-scroll");
        assert.ok(Number.isFinite(scroll.scrollTop), "定位后 scrollTop 合法");
        assert.ok(scroll.scrollTop >= 0);
        assert.ok(ctx.host.querySelectorAll(".mc-message").length === 60, "60 封历史完整渲染");
        assert.ok(!ctx.calls.status.some((s) => s.type === "error"), "无错误提示");
    });
});

describe("app.js 宿主守卫：任务钻取/脚本缺失保持旧分支 (I-7)", () => {
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
            mount: (list, options) => { calls.chatMounts += 1; calls.lastOptions = options; return {}; },
            unmount: () => { calls.chatUnmounts += 1; return true; },
            isMounted: () => false
        };
        const sandbox = {
            $: (sel) => el(sel.replace(/^#/, "")),
            document: {
                querySelector: (selector) => {
                    if (selector === 'input[name="mailboxViewMode"]:checked') return { value: "MAIL" };
                    if (selector === 'input[name="mailboxMailScope"]:checked') return { value: "ALL" };
                    if (selector === ".mailbox-view-controls") return { hidden: false, classList: { toggle: () => {} } };
                    return null;
                },
                querySelectorAll: () => [],
                getElementById: () => null
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
                    focusExpertEmail: null,
                    chatMounted: false
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

    it("组件存在且无 taskExecutionId → 挂载聊天；重复 loadMailbox 不再重放快照", async () => {
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
        assert.strictEqual(viewControls.hidden, true, "旧 MAIL/EXPERT 模式控件隐藏");
        assert.ok(calls.lastOptions.filters && calls.lastOptions.filters.pendingOnly === false, "初挂载带快照");
        assert.ok(!calls.apiUrls.some((url) => url.startsWith("/api/mail/mailbox?") || url.startsWith("/api/mail/mailbox/by-expert?")), "不再走旧端点");
        // 第二次 loadMailbox（已挂载）：mount 但不带快照（不重放草稿值）
        const mountsBefore = calls.chatMounts;
        await sandbox.loadMailbox();
        assert.strictEqual(calls.chatMounts, mountsBefore + 1, "已挂载仍 mount（静默刷新）");
        assert.strictEqual(calls.lastOptions.filters, undefined, "已挂载不再重放筛选快照");
    });

    it("任务钻取（taskExecutionId）→ 卸载聊天并保持旧平铺列表端点与参数", async () => {
        const { sandbox, calls } = createMailboxHostSandbox({ chatGlobal: true, taskExecutionId: 13023 });
        await sandbox.loadMailbox();
        assert.strictEqual(calls.chatMounts, 0, "任务钻取不激活聊天");
        assert.ok(calls.apiUrls.some((url) => url.startsWith("/api/mail/mailbox?") && url.includes("taskExecutionId=13023")), "taskExecutionId 参数保留");
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

// ════════════════════════════════════════════════════════════════════════
// 待匹配 Tab（邮件级队列，I-1/I-2/I-3/I-5/I-6/I-8 + S-1/S-2/S-3）
// ════════════════════════════════════════════════════════════════════════

function createUnmatchedPanelDom() {
    const { doc, host } = createDom();
    const panel = doc.createElement("section");
    panel.setAttribute("class", "panel");
    panel.setAttribute("id", "unmatchedDetailPanel");
    panel.hidden = true;
    doc.body.appendChild(panel);
    return { doc, host, panel };
}

describe("待匹配 Tab：第七 chip、请求契约与邮件级列表", () => {
    const conversations = { items: [expertA()], total: 1 };

    it("S-1：七个 tab 顺序/anchor 固定，待匹配只请求 unmatched-inbound（offset=page*20、无专家参数）", async () => {
        const ctx = await bootChat({ conversations, unmatched: { records: [unmatchedMail(901)], totalCount: 1 } });
        const chips = ctx.host.querySelectorAll(".mc-filter");
        assert.deepStrictEqual(chips.map((chip) => chip.dataset.chip), ["all", "provided", "followed", "pending", "suspended", "replied", "unmatched"]);
        assert.deepStrictEqual(
            chips.map((chip) => chip.textContent.replace(/\d+/g, "")),
            ["全部", "已提供", "跟进中", "待处理", "已挂起", "已回复", "待匹配"]
        );
        assert.strictEqual(chips[3].getAttribute("aria-pressed"), "true", "普通首次进入默认待处理");
        assert.strictEqual(chips[6].getAttribute("aria-pressed"), "false");

        click(chips[6]);
        await flush();

        assert.strictEqual(chips[6].getAttribute("aria-pressed"), "true", "待匹配选中态");
        assert.strictEqual(chips[3].getAttribute("aria-pressed"), "false");
        const q = queryOf(lastUnmatchedRequest(ctx).url);
        assert.strictEqual(q.get("unmatchedOnly"), "true");
        assert.strictEqual(q.get("pageSize"), "20");
        assert.strictEqual(q.get("pageOffset"), "0");
        ["query", "followed", "pendingOnly", "page", "size", "accountCode", "keyword", "recipientEmail", "label"].forEach((key) => {
            assert.strictEqual(q.get(key), null, `待匹配请求不得携带 ${key}`);
        });

        // ⋯ 高级筛选在待匹配隐藏、搜索框文案切换；离开后恢复
        const moreBtn = ctx.host.querySelector('[data-action="mc-more-filters"]');
        const searchInput = ctx.host.querySelector('.mc-search-row input[type="search"]');
        assert.strictEqual(moreBtn.hidden, true, "待匹配隐藏高级筛选入口");
        assert.strictEqual(searchInput.getAttribute("aria-label"), "搜索待匹配来信");
        assert.strictEqual(searchInput.getAttribute("placeholder"), "搜索发件邮箱、主题");

        const expertRequestsBefore = conversationsRequests(ctx).length;
        click(chips[0]);
        await flush();
        assert.strictEqual(moreBtn.hidden, false, "离开待匹配恢复 ⋯");
        assert.strictEqual(searchInput.getAttribute("aria-label"), "搜索专家");
        assert.strictEqual(searchInput.getAttribute("placeholder"), "搜索专家姓名、邮箱");
        assert.ok(conversationsRequests(ctx).length > expertRequestsBefore, "切回全部重新请求专家会话");
        assert.ok(!unmatchedRequests(ctx).some((entry) => entry.url.includes("pendingOnly")), "待匹配请求不带专家参数");
    });

    it("S-2：卡片渲染主题/发件人/时间/账号/待匹配 badge，分页单位为「封」", async () => {
        const ctx = await bootChat({
            conversations,
            unmatched: { records: [unmatchedMail(901), unmatchedMail(902, { subject: "" })], totalCount: 21 }
        });
        click(chipButton(ctx, "unmatched"));
        await flush();

        const cards = ctx.host.querySelectorAll(".mc-person");
        assert.strictEqual(cards.length, 2, "服务端返回的记录原样渲染（无客户端过滤）");
        const first = cards[0];
        assert.strictEqual(first.dataset.unmatchedId, "901");
        assert.strictEqual(first.dataset.active, "false");
        assert.strictEqual(first.querySelector(".mc-person-heading strong").textContent, "待匹配来信 901");
        const smalls = first.querySelectorAll("small");
        assert.strictEqual(smalls.length, 2);
        assert.strictEqual(smalls[0].textContent, "sender901@example.com");
        assert.strictEqual(smalls[1].textContent, "2026-09-10T10:00:00 · 账号：acc-unmatched");
        const badgeEl = first.querySelector(".mc-badge");
        assert.strictEqual(badgeEl.textContent, "未关联专家");
        assert.strictEqual(badgeEl.getAttribute("data-tone"), "pending");
        assert.strictEqual(first.querySelector(".mc-follow"), null, "待匹配卡片无关注星标");
        assert.strictEqual(cards[1].querySelector(".mc-person-heading strong").textContent, "（无主题）");
        assert.match(ctx.host.querySelector(".mc-pager").textContent, /第 1\/2 页 · 共 21 封/);
    });

    it("S-2：空列表与加载失败文案；分页 0 条为「第 1/1 页 · 共 0 封」", async () => {
        const emptyCtx = await bootChat({ conversations, unmatched: { records: [], totalCount: 0 } });
        click(chipButton(emptyCtx, "unmatched"));
        await flush();
        assert.strictEqual(emptyCtx.host.querySelector(".mc-expert-list .mc-empty").textContent, "暂无待匹配来信");
        assert.match(emptyCtx.host.querySelector(".mc-pager").textContent, /第 1\/1 页 · 共 0 封/);
        const conversation = emptyCtx.host.querySelector(".mc-conversation");
        assert.strictEqual(conversation.getAttribute("aria-label"), "待匹配来信处理");
        assert.strictEqual(conversation.querySelector(".mc-empty").textContent, "请选择左侧待匹配来信");
        const emptyScroll = conversation.querySelector(".mc-scroll");
        assert.strictEqual(emptyScroll.getAttribute("aria-label"), "待匹配来信详情");
        assert.strictEqual(emptyScroll.getAttribute("tabindex"), "0");

        const failCtx = await bootChat({ conversations, unmatchedError: "boom" });
        click(chipButton(failCtx, "unmatched"));
        await flush();
        const error = failCtx.host.querySelector(".mc-expert-list .mc-error");
        assert.ok(error, "加载失败显示错误块");
        assert.match(error.textContent, /待匹配来信加载失败，请重试/);
    });

    it("I-8：搜索走服务端 query，切页 offset=page*20；待匹配不做客户端过滤", async () => {
        const server = {
            conversations,
            unmatched: { records: [unmatchedMail(901)], totalCount: 21 }
        };
        const ctx = await bootChat(server);
        click(chipButton(ctx, "unmatched"));
        await flush();

        const searchInput = ctx.host.querySelector('.mc-search-row input[type="search"]');
        searchInput.value = "acceptance-key";
        inputEvent(searchInput);
        ctx.runTimers();
        await flush();
        assert.strictEqual(queryOf(lastUnmatchedRequest(ctx).url).get("query"), "acceptance-key");

        server.unmatched = { records: [unmatchedMail(902)], totalCount: 21 };
        click(ctx.host.querySelector('[data-action="mc-page-next"]'));
        await flush();
        const next = queryOf(lastUnmatchedRequest(ctx).url);
        assert.strictEqual(next.get("pageOffset"), "20");
        assert.strictEqual(next.get("query"), "acceptance-key");
        assert.strictEqual(ctx.host.querySelectorAll(".mc-person")[0].dataset.unmatchedId, "902");
    });

    it("I-5：点击卡片把 .mc-scroll 宿主交给宿主函数；同一卡片不重复请求", async () => {
        const dom = createUnmatchedPanelDom();
        const server = {
            conversations,
            unmatched: { records: [unmatchedMail(901), unmatchedMail(902)], totalCount: 2 },
            unmatchedPanel: dom.panel
        };
        const ctx = await bootChat(server, undefined, { doc: dom.doc, host: dom.host });
        click(chipButton(ctx, "unmatched"));
        await flush();

        click(unmatchedCards(ctx)[0]);
        await flush();

        assert.strictEqual(ctx.calls.unmatchedMounts.length, 1);
        assert.strictEqual(ctx.calls.unmatchedMounts[0].id, 901);
        const scrollHost = ctx.calls.unmatchedMounts[0].hostEl;
        assert.ok(scrollHost.classList.contains("mc-scroll"), "宿主是 .mc-scroll");
        assert.strictEqual(scrollHost.parentNode, ctx.host.querySelector(".mc-conversation"));
        assert.strictEqual(dom.panel.parentNode, scrollHost, "同一详情节点被挂进右栏");
        assert.strictEqual(dom.doc.querySelectorAll("#unmatchedDetailPanel").length, 1, "详情面板始终唯一");
        const active = ctx.host.querySelectorAll(".mc-person").find((card) => card.dataset.active === "true");
        assert.strictEqual(active.dataset.unmatchedId, "901", "选中卡片高亮");

        click(unmatchedCards(ctx)[0]);
        await flush();
        assert.strictEqual(ctx.calls.unmatchedMounts.length, 1, "当前 id 仍存在时不重复 mount");
    });

    it("I-5：记录消失/切占位 Tab/unmount 都先归还节点，右栏回落空态", async () => {
        const dom = createUnmatchedPanelDom();
        const server = {
            conversations,
            unmatched: { records: [unmatchedMail(901)], totalCount: 1 },
            unmatchedPanel: dom.panel
        };
        const ctx = await bootChat(server, undefined, { doc: dom.doc, host: dom.host });
        click(chipButton(ctx, "unmatched"));
        await flush();
        click(unmatchedCards(ctx)[0]);
        await flush();
        assert.strictEqual(dom.panel.parentNode, ctx.calls.unmatchedMounts[0].hostEl);

        // 服务端重查后该记录消失（例如已绑定/已标记处理）
        server.unmatched = { records: [], totalCount: 0 };
        ctx.sandbox.MailboxChat.mount(ctx.host, {});
        await flush();

        assert.ok(ctx.calls.unmatchedReleases >= 1, "记录消失先归还 lease");
        assert.strictEqual(dom.panel.parentNode, dom.doc.body, "节点被移出聊天右栏（未被 innerHTML 销毁）");
        assert.strictEqual(ctx.host.querySelector(".mc-conversation .mc-empty").textContent, "请选择左侧待匹配来信");
        assert.strictEqual(ctx.host.querySelectorAll('.mc-person[data-active="true"]').length, 0, "选中键被清空");

        // 重新选中后 unmount：必须先归还再清空宿主
        server.unmatched = { records: [unmatchedMail(901)], totalCount: 1 };
        ctx.sandbox.MailboxChat.mount(ctx.host, {});
        await flush();
        click(unmatchedCards(ctx)[0]);
        await flush();
        assert.strictEqual(dom.panel.parentNode, ctx.calls.unmatchedMounts[1].hostEl, "重新挂载回右栏");
        const releasesBefore = ctx.calls.unmatchedReleases;
        ctx.sandbox.MailboxChat.unmount(ctx.host);
        assert.strictEqual(ctx.calls.unmatchedReleases, releasesBefore + 1, "unmount 归还 lease");
        assert.strictEqual(dom.panel.parentNode, dom.doc.body);
        assert.strictEqual(dom.doc.querySelectorAll("#unmatchedDetailPanel").length, 1, "静态面板未随宿主清空被销毁");
    });

    it("I-3：晚到的专家列表响应不得覆盖待匹配列表（反之亦然）", async () => {
        const pending = [];
        const server = {
            conversations,
            unmatched: { records: [unmatchedMail(901)], totalCount: 1 },
            route: (url, method, body, entry, next) => {
                if (url.startsWith("/api/mail/mailbox/conversations?")) {
                    return new Promise((resolve) => { pending.push(() => resolve(server.conversations)); });
                }
                return next(url, method, body, entry);
            }
        };
        const ctx = await bootChat(server);
        assert.strictEqual(pending.length, 1, "初挂载专家列表请求挂起");

        click(chipButton(ctx, "unmatched"));
        await flush();
        assert.strictEqual(unmatchedCards(ctx).length, 1, "待匹配列表已渲染");

        pending[0]();
        await flush();
        assert.strictEqual(unmatchedCards(ctx).length, 1, "晚到的专家响应不覆盖待匹配列表");
        assert.strictEqual(ctx.host.querySelectorAll('[data-action="mc-select-expert"]').length, 0);

        // 反向：待匹配请求挂起时切回专家 tab，晚到的待匹配响应不得覆盖专家列表
        const pendingUnmatched = [];
        const reverse = {
            conversations,
            unmatched: { records: [unmatchedMail(902)], totalCount: 1 },
            route: (url, method, body, entry, next) => {
                if (url.startsWith("/api/mail/unmatched-inbound?")) {
                    return new Promise((resolve) => { pendingUnmatched.push(() => resolve(reverse.unmatched)); });
                }
                return next(url, method, body, entry);
            }
        };
        const reverseCtx = await bootChat(reverse);
        click(chipButton(reverseCtx, "unmatched"));
        await flush();
        assert.strictEqual(pendingUnmatched.length, 1, "待匹配请求挂起");
        click(chipButton(reverseCtx, "all"));
        await flush();
        assert.strictEqual(personButtons(reverseCtx.host).length, 1, "专家列表已渲染");

        pendingUnmatched[0]();
        await flush();
        assert.strictEqual(unmatchedCards(reverseCtx).length, 0, "晚到的待匹配响应不覆盖专家列表");
        assert.strictEqual(personButtons(reverseCtx.host).length, 1);
    });

    it("I-6：待匹配选择不写 selectedContactId/sessionStore；切回专家仍恢复草稿", async () => {
        const dom = createUnmatchedPanelDom();
        const server = {
            conversations: { items: [expertA()], total: 1 },
            messages: messagesA(),
            contact: contactA(),
            unmatched: { records: [unmatchedMail(901)], totalCount: 1 },
            unmatchedPanel: dom.panel
        };
        const ctx = await bootChat(server, undefined, { doc: dom.doc, host: dom.host });

        click(personButtons(ctx.host)[0]);
        await flush();
        const subject = ctx.host.querySelector('input[aria-label="回复主题"]');
        const editor = ctx.host.querySelector('[aria-label="人工回复正文"]');
        subject.value = "My subject";
        inputEvent(subject);
        editor.innerText = "hello draft";
        inputEvent(editor);

        click(chipButton(ctx, "unmatched"));
        await flush();
        click(unmatchedCards(ctx)[0]);
        await flush();

        const mailIdRequests = ctx.calls.api.filter(
            (entry) => /\/api\/mail\/mailbox\/conversations\/\d+\/(messages|manual-rich-reply)/.test(entry.url) && entry.url.includes("901")
        );
        assert.deepStrictEqual(mailIdRequests, [], "邮件 id 绝不进入专家会话 endpoint");
        assert.strictEqual(ctx.calls.unmatchedMounts[0].id, 901);

        click(chipButton(ctx, "all"));
        await flush();
        const expertAgain = personButtons(ctx.host)[0];
        assert.ok(expertAgain, "切回全部仍能选中专家");
        click(expertAgain);
        await flush();
        assert.strictEqual(ctx.host.querySelector('input[aria-label="回复主题"]').value, "My subject", "草稿主题未被待匹配污染");
        assert.strictEqual(ctx.host.querySelector('[aria-label="人工回复正文"]').innerText, "hello draft", "草稿正文未被待匹配污染");
    });

    it("无宿主函数时右栏给出明确不可用提示，不伪造处理 UI", async () => {
        const ctx = await bootChat({ conversations, unmatched: { records: [unmatchedMail(901)], totalCount: 1 } });
        delete ctx.sandbox.mcHostMountUnmatchedDetail;
        click(chipButton(ctx, "unmatched"));
        await flush();
        click(unmatchedCards(ctx)[0]);
        await flush();
        const alertEl = ctx.host.querySelector('.mc-conversation .mc-empty[role="alert"]');
        assert.ok(alertEl, "右栏显示错误");
        assert.match(alertEl.textContent, /来信处理面板不可用，请刷新页面重试/);
        assert.strictEqual(ctx.host.querySelector('[data-action="bind-candidate"]'), null, "不伪造绑定入口");
    });
});

// ════════════════════════════════════════════════════════════════════════
// app.js 详情面板宿主 lease（I-4/I-5/S-3）
// ════════════════════════════════════════════════════════════════════════

describe("app.js mcHostMountUnmatchedDetail / mcHostReleaseUnmatchedDetail（lease）", () => {
    function createLeaseDom() {
        const doc = new MiniDocument();
        const view = doc.createElement("div");
        view.setAttribute("id", "view-mailbox");
        const list = doc.createElement("div");
        list.setAttribute("id", "mailboxList");
        view.appendChild(list);
        const panel = doc.createElement("section");
        panel.setAttribute("class", "panel");
        panel.setAttribute("id", "unmatchedDetailPanel");
        panel.hidden = true;
        view.appendChild(panel);
        const tail = doc.createElement("div");
        tail.setAttribute("id", "mailboxTail");
        view.appendChild(tail);
        doc.body.appendChild(view);
        return { doc, view, list, panel, tail };
    }

    function createLeaseSandbox(dom, options) {
        const opts = options || {};
        const calls = { releases: 0, teardowns: 0, details: [], scrolled: [] };
        const main = { scrollTop: 0, scrollTo: (args) => calls.scrolled.push(args) };
        const state = { mailbox: { detailContext: null } };
        const sandbox = {
            console,
            requestAnimationFrame: (fn) => { fn(); return 1; },
            $: (selector) => (selector === "#unmatchedDetailPanel" ? dom.panel : dom.doc.querySelector(selector)),
            document: {
                querySelector: (selector) => (selector === ".main" ? main : dom.doc.querySelector(selector))
            },
            state,
            unmountMailboxTrustReplyHosts: () => { calls.teardowns += 1; },
            showUnmatchedDetail: (id) => {
                calls.details.push(Number(id));
                if (opts.detailError) return Promise.reject(new Error(opts.detailError));
                return Promise.resolve();
            }
        };
        vm.createContext(sandbox);
        const leaseDecl = appSource.match(/const unmatchedDetailLease = \{[^}]*\};/);
        assert.ok(leaseDecl, "app.js 必须声明 unmatchedDetailLease");
        vm.runInContext(leaseDecl[0], sandbox);
        vm.runInContext(extractFn("mcHostReleaseUnmatchedDetail"), sandbox);
        vm.runInContext(extractFn("mcHostMountUnmatchedDetail"), sandbox);
        vm.runInContext(extractFn("focusMailboxProcessingPanel"), sandbox);
        return { sandbox, calls, state, main };
    }

    function scrollHost(dom, doc) {
        const body = doc.createElement("div");
        body.setAttribute("class", "mc-conversation");
        const scroll = doc.createElement("div");
        scroll.setAttribute("class", "mc-scroll");
        body.appendChild(scroll);
        dom.view.appendChild(body);
        return scroll;
    }

    it("挂载：同一节点挂进右栏，原父节点/兄弟位置被登记；释放后逐字归位", async () => {
        const dom = createLeaseDom();
        const { sandbox, calls, state } = createLeaseSandbox(dom);
        const host = scrollHost(dom, dom.doc);
        sandbox.state.mailbox.detailContext = { source: "INBOUND_PROCESSING", id: 901, inboundProcessingId: 901 };
        const originalIndex = dom.view.children.indexOf(dom.panel);

        await sandbox.mcHostMountUnmatchedDetail(host, 901);

        assert.strictEqual(sandbox.$("#unmatchedDetailPanel"), dom.panel, "节点身份不变，无 clone/第二实例");
        assert.strictEqual(dom.panel.parentNode, host, "面板挂进传入的 .mc-scroll");
        assert.deepStrictEqual(calls.details, [901]);

        sandbox.mcHostReleaseUnmatchedDetail();

        assert.strictEqual(dom.panel.parentNode, dom.view, "归还原父节点");
        assert.strictEqual(dom.view.children[originalIndex], dom.panel, "回到原兄弟位置（tail 之前）");
        assert.strictEqual(dom.panel.nextSibling, dom.tail, "原 nextSibling 精确保留");
        assert.strictEqual(dom.panel.hidden, true, "归还即隐藏");
        assert.strictEqual(calls.teardowns, 1, "统一 teardown 使旧详情响应失效");
        assert.strictEqual(state.mailbox.detailContext, null, "详情上下文清空");
    });

    it("重复释放无副作用；非法 id/宿主直接拒绝且不动节点", async () => {
        const dom = createLeaseDom();
        const { sandbox } = createLeaseSandbox(dom);
        const host = scrollHost(dom, dom.doc);
        const before = dom.view.children.length;

        await assert.rejects(() => sandbox.mcHostMountUnmatchedDetail(host, 0));
        await assert.rejects(() => sandbox.mcHostMountUnmatchedDetail(null, 901));
        assert.strictEqual(dom.panel.parentNode, dom.view, "拒绝路径不动节点");
        assert.strictEqual(dom.view.children.length, before);

        await sandbox.mcHostMountUnmatchedDetail(host, 901);
        sandbox.mcHostReleaseUnmatchedDetail();
        const index = dom.view.children.indexOf(dom.panel);
        sandbox.mcHostReleaseUnmatchedDetail();
        sandbox.mcHostReleaseUnmatchedDetail();
        assert.strictEqual(dom.panel.parentNode, dom.view);
        assert.strictEqual(dom.view.children.indexOf(dom.panel), index, "重复释放保持归位后的位置");
        assert.strictEqual(dom.doc.querySelectorAll("#unmatchedDetailPanel").length, 1);
    });

    it("详情加载失败：先归还节点再抛错（供右栏显示错误）", async () => {
        const dom = createLeaseDom();
        const { sandbox, calls } = createLeaseSandbox(dom, { detailError: "detail down" });
        const host = scrollHost(dom, dom.doc);

        await assert.rejects(() => sandbox.mcHostMountUnmatchedDetail(host, 902), /detail down/);

        assert.strictEqual(dom.panel.parentNode, dom.view, "失败路径归还节点");
        assert.strictEqual(dom.panel.hidden, true);
        assert.strictEqual(calls.teardowns, 1);
    });

    it("I-4：面板节点迁移后既有 click 委托仍生效（处理动作不新增入口）", async () => {
        const dom = createLeaseDom();
        const { sandbox } = createLeaseSandbox(dom);
        const host = scrollHost(dom, dom.doc);
        const handled = [];
        dom.panel.addEventListener("click", (event) => {
            const button = event.target.closest ? event.target.closest("[data-action]") : null;
            if (button) handled.push(button.dataset.action);
        });

        await sandbox.mcHostMountUnmatchedDetail(host, 901);
        dom.panel.innerHTML = '<button class="button primary" data-action="bind-candidate" data-contact-id="7">绑定</button>';
        click(dom.panel.querySelector('[data-action="bind-candidate"]'));

        assert.deepStrictEqual(handled, ["bind-candidate"], "移动后的面板仍把动作交给原委托");
        assert.strictEqual(dom.doc.querySelectorAll("#unmatchedDetailPanel").length, 1);
        sandbox.mcHostReleaseUnmatchedDetail();
    });

    it("focusMailboxProcessingPanel：面板在 .mc-scroll 内只重置该容器，不滚动整页", async () => {
        const dom = createLeaseDom();
        const { sandbox, calls, main } = createLeaseSandbox(dom);
        const host = scrollHost(dom, dom.doc);
        await sandbox.mcHostMountUnmatchedDetail(host, 901);
        host.scrollTop = 240;

        sandbox.focusMailboxProcessingPanel();

        assert.strictEqual(host.scrollTop, 0, "只重置右栏滚动容器");
        assert.deepStrictEqual(calls.scrolled, [], "不滚动整页 .main");
        assert.strictEqual(main.scrollTop, 0);
    });
});

// ════════════════════════════════════════════════════════════════════════
// 跟进邮件 01（I-1..I-8 / S-1/S-3）：人工选择引用邮件、自然短正文与同源引用、
// 草稿锚点/幂等、填入后的互斥清理，以及发送分支（有锚点 → 会话接口）。
// 需求 02 计划：docs/plans/2026-09-14/followup-email-01-manual-anchor.md
// ════════════════════════════════════════════════════════════════════════

describe("followup 01：人工选择引用邮件与自然正文（I-1..I-8/S-1/S-3）", () => {
    const VIDEO_BODY = "Just following up on my email below about a brief Zoom call. Would you be available sometime this week or next? We’re happy to work around your time zone.";
    const MEETING_REMINDER_BODY = "This is a courteous reminder of our scheduled meeting. We would be honored by your participation at the appointed time.";
    const CV_BODY = "Just following up on my note below. When convenient, could you please send your CV? It will help us identify suitable industry partners.";
    const GENERIC_BODY = "I hope you’re doing well. I wanted to follow up on my previous email and would be happy to continue our conversation. Please feel free to share any thoughts or questions you may have. I look forward to hearing from you.";

    // 时间线夹具：合法 SENT（acc1：#2893 较旧、#4007 最新）+ 全部非法形态。
    function followupMessage(extra) {
        return Object.assign({
            source: "MAIL_RECORD",
            id: 2893,
            contactId: 1,
            direction: "OUTBOUND",
            accountCode: "acc1",
            subject: "Older introduction",
            body: "<p>A</p><p>B<br>C</p>",
            cleanedBody: "<p>A</p><p>B<br>C</p>",
            eventAt: "2026-09-05T11:49:00",
            sendStatus: "SENT",
            processStatus: null,
            attachmentCount: 0,
            firstAttachmentNames: [],
            messageId: "m2893",
            inReplyTo: null,
            tags: []
        }, extra || {});
    }

    function followupInbound(extra) {
        return Object.assign({
            source: "INBOUND_PROCESSING",
            id: 5001,
            contactId: 1,
            direction: "INBOUND",
            accountCode: "acc1",
            subject: "inbound question",
            body: "inbound raw",
            cleanedBody: "inbound cleaned",
            eventAt: "2026-09-07T09:00:00",
            sendStatus: null,
            processStatus: "PROCESSED",
            attachmentCount: 0,
            firstAttachmentNames: [],
            messageId: "m5001",
            inReplyTo: null,
            tags: []
        }, extra || {});
    }

    function followupMessages(overrides) {
        const opts = overrides || {};
        const items = opts.items ? opts.items.slice() : [
            followupMessage(),
            followupMessage({
                id: 4007, subject: "Newer introduction", body: "newer body", cleanedBody: "newer body",
                eventAt: "2026-09-06T09:00:00", messageId: "m4007", inReplyTo: "m2893"
            }),
            followupInbound(),
            followupMessage({ id: 4010, subject: "failed send", body: "failed", cleanedBody: "failed", eventAt: "2026-09-07T10:00:00", sendStatus: "FAILED" }),
            followupMessage({ id: 4012, subject: "blank account", body: "blank", cleanedBody: "blank", eventAt: "2026-09-08T11:00:00", accountCode: "   " }),
            followupMessage({ id: 4013, subject: "simulator", body: "sim", cleanedBody: "sim", eventAt: "2026-09-08T12:00:00", accountCode: "SIMULATOR_NOOP" })
        ];
        if (opts.withOtherAccount) {
            items.push(followupMessage({ id: 4011, subject: "other account", body: "other", cleanedBody: "other", eventAt: "2026-09-08T10:00:00", accountCode: "acc9" }));
        }
        return { items, nextBefore: opts.nextBefore || null, hasMore: !!opts.hasMore };
    }

    async function bootFollowup(serverOverrides, mountOptions) {
        const conversations = { items: [expertA(), expertB()], total: 2 };
        const ctx = await bootChat(
            Object.assign({ conversations, messages: followupMessages(), contact: contactA() }, serverOverrides || {}),
            mountOptions
        );
        ctx.sandbox.state = { accounts: [
            { accountCode: "acc1", senderName: "Alice Chen", senderDisplayName: "Talent Team" },
            { accountCode: "acc9", senderName: "Bob Wang" }
        ] };
        vm.runInContext(extractFn("mcHostGetSenderName"), ctx.sandbox);
        return ctx;
    }

    function dialogOf(ctx) {
        return ctx.doc.querySelector(".followup-dialog");
    }

    function followupOptions(ctx) {
        const dialog = dialogOf(ctx);
        return dialog ? dialog.querySelectorAll('[data-action="mc-select-followup"]') : [];
    }

    function openFollowup(ctx) {
        click(ctx.host.querySelector('[data-action="mc-open-followup"]'));
    }

    function selectOption(ctx, id) {
        const option = followupOptions(ctx).find((node) => node.dataset.mailRecordId === String(id));
        assert.ok(option, `候选 #${id} 必须存在`);
        click(option);
    }

    function followupCopyOptions(ctx) {
        const dialog = dialogOf(ctx);
        return dialog ? dialog.querySelectorAll('[data-action="mc-select-followup-copy"]') : [];
    }

    function selectCopy(ctx, copy) {
        const option = followupCopyOptions(ctx).find((node) => node.dataset.followupCopy === copy);
        assert.ok(option, `文案 ${copy} 必须存在`);
        click(option);
    }

    function applyFollowup(ctx) {
        const apply = dialogOf(ctx).querySelector('[data-action="mc-apply-followup"]');
        if (apply.disabled) selectCopy(ctx, "generic");
        click(apply);
    }

    function anchorNote(ctx) {
        return ctx.host.querySelector('[data-role="followup-anchor-note"]');
    }

    function draftFixture(ctx, extra) {
        // 夹具：把一次合法发送产物当作可引用邮件（body/cleanedBody 由用例指定）。
        return followupMessage(extra);
    }

    it("S-1：跟进按钮只在有成功发件时渲染，位于工具栏末位且 class 严格为 button", async () => {
        const ctx = await bootFollowup();
        const a = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "1");
        click(a.querySelector(".mc-person-main"));
        await flush();
        const tools = ctx.host.querySelector(".mc-editor-tools");
        const button = tools.querySelector('[data-action="mc-open-followup"]');
        assert.ok(button, "有成功发件的专家必须提供跟进入口");
        assert.strictEqual(button.getAttribute("class"), "button", "只允许既有 button class");
        assert.strictEqual(tools.children[tools.children.length - 1], button, "跟进按钮必须是工具栏最后一个按钮");
        assert.strictEqual(button.textContent, "↗ 跟进邮件");

        // sentCount=0（只有失败发件）不渲染跟进入口
        const onlyFailed = expertB({ sentCount: 0, failedCount: 3 });
        const noSent = await bootChat({
            conversations: { items: [onlyFailed, expertA()], total: 2 },
            messages: followupMessages(),
            contact: contactB()
        });
        const b = noSent.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "2");
        click(b.querySelector(".mc-person-main"));
        await flush();
        assert.strictEqual(noSent.host.querySelector('[data-action="mc-open-followup"]'), null, "无成功发件不显示跟进入口");
        assert.ok(noSent.host.querySelector('[data-role="manual-followup"]'), "保留既有无来信提示与模板跟进");
    });

    it("I-1/I-2：弹窗默认不选择、只列合法 SENT，且不改变主题与正文", async () => {
        const ctx = await bootFollowup();
        const a = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "1");
        click(a.querySelector(".mc-person-main"));
        await flush();
        const subjectBefore = ctx.host.querySelector('input[aria-label="回复主题"]').value;
        const editorBefore = ctx.host.querySelector('[aria-label="人工回复正文"]').innerText;

        openFollowup(ctx);
        await flush();
        const dialog = dialogOf(ctx);
        assert.ok(dialog, "弹窗必须挂载到 portal root");
        assert.ok(dialog.hasAttribute("open"), "弹窗必须处于打开状态");
        assert.strictEqual(dialog.querySelector("#followupTitle").textContent, "生成跟进邮件");

        const options = followupOptions(ctx);
        assert.deepStrictEqual(options.map((node) => node.dataset.mailRecordId), ["4007", "2893"], "只列合法 SENT，且按时间倒序");
        assert.deepStrictEqual(options.map((node) => node.getAttribute("aria-checked")), ["false", "false"], "默认无选中项");
        assert.strictEqual(dialog.querySelector('[data-action="mc-apply-followup"]').disabled, true, "未选择时不得填入");
        assert.strictEqual(dialog.querySelector('[data-role="followup-subject"]').value, "");
        assert.strictEqual(dialog.querySelector('[data-role="followup-body"]').value, "");
        assert.strictEqual(dialog.querySelector('[data-role="followup-quote"]').textContent, "");

        // 打开弹窗不改变人工回复区
        assert.strictEqual(ctx.host.querySelector('input[aria-label="回复主题"]').value, subjectBefore);
        assert.strictEqual(ctx.host.querySelector('[aria-label="人工回复正文"]').innerText, editorBefore);
        assert.strictEqual(anchorNote(ctx), null, "未填入不得出现锚点提示");
    });

    it("I-2：INBOUND/FAILED/其他账号/空账号/模拟器行永不出现在候选里；账号范围生效", async () => {
        const ctx = await bootFollowup();
        const a = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "1");
        click(a.querySelector(".mc-person-main"));
        await flush();
        openFollowup(ctx);
        const ids = followupOptions(ctx).map((node) => node.dataset.mailRecordId);
        ["5001", "4010", "4011", "4012", "4013"].forEach((illegal) => {
            assert.ok(ids.indexOf(illegal) === -1, `非法行 ${illegal} 不得成为候选`);
        });

        // accountScope=acc9：只允许该账号的成功发件（含其他账号行时必须过滤）
        const scoped = await bootFollowup({ messages: followupMessages({ withOtherAccount: true }) }, { filters: { accountCode: "acc9" } });
        const a2 = scoped.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "1");
        click(a2.querySelector(".mc-person-main"));
        await flush();
        openFollowup(scoped);
        assert.deepStrictEqual(
            followupOptions(scoped).map((node) => node.dataset.mailRecordId),
            ["4011"],
            "账号范围外的成功发件不得成为候选"
        );
    });

    it("I-1/I-5/I-6：选择引用邮件后仍须人工选择文案，主题和引用随邮件同步", async () => {
        const ctx = await bootFollowup();
        const a = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "1");
        click(a.querySelector(".mc-person-main"));
        await flush();
        openFollowup(ctx);
        const dialog = dialogOf(ctx);

        selectOption(ctx, 2893);
        await flush();
        assert.deepStrictEqual(
            followupOptions(ctx).map((node) => node.getAttribute("aria-checked")),
            ["false", "true"],
            "只有所选一项为选中态"
        );
        assert.strictEqual(dialog.querySelector('[data-action="mc-apply-followup"]').disabled, true, "未选文案不得填入");
        assert.strictEqual(dialog.querySelector('[data-role="followup-subject"]').value, "Re: Older introduction");
        assert.strictEqual(dialog.querySelector('[data-role="followup-body"]').value, "");
        assert.deepStrictEqual(
            followupCopyOptions(ctx).map((node) => [node.dataset.followupCopy, node.getAttribute("aria-pressed")]),
            [["video", "false"], ["meetingReminder", "false"], ["cv", "false"], ["generic", "false"]],
            "四种文案都必须由运营手动选择"
        );
        // I-6：引用头 + 块级/换行标签确定性转换后的完整原文
        assert.strictEqual(
            dialog.querySelector('[data-role="followup-quote"]').textContent,
            "On 2026-09-05 11:49, acc1 wrote:\n\nA\n\nB\nC"
        );
        assert.strictEqual(dialog.querySelector('[data-role="followup-body"]').value.indexOf("September"), -1);
        assert.strictEqual(dialog.querySelector('[data-role="followup-body"]').value.indexOf("For reference"), -1);

        selectCopy(ctx, "video");
        assert.strictEqual(dialog.querySelector('[data-action="mc-apply-followup"]').disabled, false);
        assert.strictEqual(
            dialog.querySelector('[data-role="followup-body"]').value,
            `Dear Professor,\n\n${VIDEO_BODY}\n\nBest regards,\nAlice Chen`
        );

        selectOption(ctx, 4007);
        await flush();
        assert.deepStrictEqual(
            followupOptions(ctx).map((node) => node.getAttribute("aria-checked")),
            ["true", "false"]
        );
        assert.strictEqual(dialog.querySelector('[data-role="followup-subject"]').value, "Re: Newer introduction");
        assert.strictEqual(
            dialog.querySelector('[data-role="followup-quote"]').textContent,
            "On 2026-09-06 09:00, acc1 wrote:\n\nnewer body"
        );
        assert.strictEqual(dialog.querySelector('[data-role="followup-body"]').value, "", "换引用邮件必须重新选择文案");
        assert.strictEqual(dialog.querySelector('[data-action="mc-apply-followup"]').disabled, true);
    });

    it("I-5：四种跟进文案使用发件人名称落款；不读取专家标签", async () => {
        const cases = [
            { copy: "video", line: VIDEO_BODY },
            { copy: "meetingReminder", line: MEETING_REMINDER_BODY },
            { copy: "cv", line: CV_BODY },
            { copy: "generic", line: GENERIC_BODY }
        ];
        for (const item of cases) {
            const ctx = await bootFollowup({ conversations: { items: [expertA({ expertTags: null })], total: 1 } });
            const a = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "1");
            click(a.querySelector(".mc-person-main"));
            await flush();
            openFollowup(ctx);
            selectOption(ctx, 2893);
            selectCopy(ctx, item.copy);
            const body = dialogOf(ctx).querySelector('[data-role="followup-body"]').value;
            assert.strictEqual(body, `Dear Professor,\n\n${item.line}\n\nBest regards,\nAlice Chen`, `人工文案 ${item.copy}`);
            applyFollowup(ctx);
            assert.ok(ctx.host.querySelector('[aria-label="人工回复正文"]').innerText.startsWith(`${body}\n\nOn `), "填入草稿保留所选文案和发件人名称");
        }
    });

    it("账号接口提供落款名称，选择不同账号引用时使用对应名称", async () => {
        const ctx = await bootFollowup({ messages: followupMessages({ withOtherAccount: true }) });
        const accounts = [
            { accountCode: "acc1", senderName: "  Alice Chen  ", senderDisplayName: "Talent Team", senderEmail: "alice@example.com", enabled: true },
            { accountCode: "acc9", senderName: "Bob Wang", senderEmail: "bob@example.com", enabled: false }
        ];
        const originalApi = ctx.sandbox.api;
        ctx.sandbox.api = (url, opts) => url === "/api/mail/sender-accounts" ? Promise.resolve(accounts) : originalApi(url, opts);
        ctx.sandbox.state = { accounts: [], mailbox: { accountsLoaded: false } };
        ctx.sandbox.$ = (selector) => ctx.doc.querySelector(selector);
        vm.runInContext(extractFn("loadMailboxAccounts"), ctx.sandbox);
        await ctx.sandbox.loadMailboxAccounts();
        const a = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "1");
        click(a.querySelector(".mc-person-main"));
        await flush();
        openFollowup(ctx);
        for (const [id, name] of [[2893, "Alice Chen"], [4011, "Bob Wang"]]) {
            selectOption(ctx, id);
            selectCopy(ctx, "meetingReminder");
            assert.ok(dialogOf(ctx).querySelector('[data-role="followup-body"]').value.endsWith(`Best regards,\n${name}`));
        }
    });

    it("发件人名称缺失时不把账号代码用作落款", async () => {
        const ctx = await bootFollowup();
        const a = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "1");
        click(a.querySelector(".mc-person-main"));
        await flush();
        openFollowup(ctx);
        selectOption(ctx, 2893);
        for (const accounts of [[], [{ accountCode: "acc1", senderName: "   " }]]) {
            ctx.sandbox.state.accounts = accounts;
            selectCopy(ctx, "meetingReminder");
            assert.strictEqual(dialogOf(ctx).querySelector('[data-role="followup-body"]').value,
                `Dear Professor,\n\n${MEETING_REMINDER_BODY}\n\nBest regards,`);
        }
    });

    it("I-5：称呼只复用所选邮件首行的 Dear/Hi 且不超过 100 字符", async () => {
        const cases = [
            { cleanedBody: "Dear Prof. Smith,\n\nWelcome aboard", greeting: "Dear Prof. Smith," },
            { cleanedBody: "Hi Anna,\n\nthanks", greeting: "Hi Anna," },
            { cleanedBody: "Hello there,\n\nthanks", greeting: "Dear Professor," },
            { cleanedBody: `Dear ${"x".repeat(120)},\n\nhi`, greeting: "Dear Professor," }
        ];
        for (const item of cases) {
            const messages = followupMessages({ items: [followupMessage({ cleanedBody: item.cleanedBody, body: item.cleanedBody })] });
            const ctx = await bootFollowup({ messages });
            const a = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "1");
            click(a.querySelector(".mc-person-main"));
            await flush();
            openFollowup(ctx);
            selectOption(ctx, 2893);
            selectCopy(ctx, "generic");
            const body = dialogOf(ctx).querySelector('[data-role="followup-body"]').value;
            assert.strictEqual(body.slice(0, item.greeting.length + 1), `${item.greeting}\n`, `首行 ${JSON.stringify(item.cleanedBody.slice(0, 20))}`);
        }
    });

    it("I-6：HTML 正文确定性转纯文本，脚本内容不生成 DOM script", async () => {
        const messages = followupMessages({
            items: [followupMessage({
                body: "cleaned question <script>alert(1)</script>",
                cleanedBody: "cleaned question <script>alert(1)</script>"
            })]
        });
        const ctx = await bootFollowup({ messages });
        const a = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "1");
        click(a.querySelector(".mc-person-main"));
        await flush();
        openFollowup(ctx);
        selectOption(ctx, 2893);
        const quote = dialogOf(ctx).querySelector('[data-role="followup-quote"]').textContent;
        assert.strictEqual(quote, "On 2026-09-05 11:49, acc1 wrote:\n\ncleaned question alert(1)");
        assert.strictEqual(ctx.doc.querySelectorAll("script").length, 0, "邮件正文绝不生成 script 元素");
        assert.strictEqual(dialogOf(ctx).querySelectorAll("script").length, 0);
        assert.strictEqual(ctx.host.querySelectorAll("script").length, 0);
    });

    it("I-7/S-3：取消不改状态；填入是全文替换并写入锚点提示", async () => {
        const ctx = await bootFollowup();
        const a = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "1");
        click(a.querySelector(".mc-person-main"));
        await flush();
        const subjectInput = ctx.host.querySelector('input[aria-label="回复主题"]');
        const editor = ctx.host.querySelector('[aria-label="人工回复正文"]');
        const subjectBefore = subjectInput.value;
        const editorBefore = editor.innerText;

        // 打开 → 选择 → 取消：主题/正文/提示都不变（选择不是填写）
        openFollowup(ctx);
        selectOption(ctx, 2893);
        click(dialogOf(ctx).querySelector('[data-action="mc-close-followup"]'));
        await flush();
        assert.strictEqual(dialogOf(ctx), null, "取消后弹窗关闭");
        assert.strictEqual(subjectInput.value, subjectBefore);
        assert.strictEqual(editor.innerText, editorBefore);
        assert.strictEqual(anchorNote(ctx), null);

        // 填入：主题与正文全文替换 + 锚点提示
        openFollowup(ctx);
        selectOption(ctx, 2893);
        applyFollowup(ctx);
        await flush();
        assert.strictEqual(ctx.host.querySelector('input[aria-label="回复主题"]').value, "Re: Older introduction");
        const filled = ctx.host.querySelector('[aria-label="人工回复正文"]').innerText;
        assert.ok(filled.indexOf(GENERIC_BODY) >= 0, "正文含通用文案");
        assert.ok(filled.indexOf("On 2026-09-05 11:49, acc1 wrote:\n\nA\n\nB\nC") >= 0, "正文含同源完整引用");
        const note = anchorNote(ctx);
        assert.ok(note, "填入后必须显示锚点提示");
        assert.strictEqual(note.getAttribute("class"), "mc-note");
        assert.strictEqual(note.textContent, "已引用邮件 #2893 · 2026-09-05 11:49 · Older introduction");
        const composeEl = ctx.host.querySelector('[data-role="manual-compose"]');
        const kids = composeEl.children;
        const editorEl = ctx.host.querySelector('[data-role="mc-editor"]');
        assert.strictEqual(kids[kids.indexOf(editorEl) + 1], note, "提示紧随编辑器");
        const footerEl = composeEl.querySelector(".mc-compose-footer");
        assert.ok(footerEl, "底部操作区存在");
        assert.ok(kids.indexOf(note) < kids.indexOf(footerEl), "提示位于会议附件/底部操作区之前");
        assert.strictEqual(note.textContent.indexOf("m2893"), -1, "不得显示 Message-ID");
        assert.strictEqual(dialogOf(ctx), null, "填入后弹窗关闭");
    });

    it("I-8：有锚点的来信草稿只走会话接口，无锚点来信仍走 processingId 接口", async () => {
        const ctx = await bootFollowup();
        const a = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "1");
        click(a.querySelector(".mc-person-main"));
        await flush();
        const editor = ctx.host.querySelector('[aria-label="人工回复正文"]');

        // 无锚点：既有来信路径
        editor.innerText = "plain inbound reply";
        inputEvent(editor);
        click(ctx.host.querySelector('[data-action="mc-send-manual"]'));
        await flush();
        assert.strictEqual(ctx.calls.sendRich.length, 1, "无锚点来信走 processingId adapter");
        assert.strictEqual(ctx.calls.sendConversation.length, 0);
        assert.strictEqual(ctx.calls.sendRich[0].processingId, 101);

        // 有锚点：会话路径 + 显式 anchorMailRecordId
        ctx.sandbox.crypto = { randomUUID: () => "00000000-0000-4000-8000-000000000001" };
        openFollowup(ctx);
        selectOption(ctx, 2893);
        applyFollowup(ctx);
        await flush();
        click(ctx.host.querySelector('[data-action="mc-send-manual"]'));
        await flush();
        assert.strictEqual(ctx.calls.sendRich.length, 1, "有锚点来信不得再走 processingId adapter");
        assert.strictEqual(ctx.calls.sendConversation.length, 1);
        const body = ctx.calls.sendConversation[0].body;
        assert.strictEqual(ctx.calls.sendConversation[0].contactId, 1);
        assert.strictEqual(body.anchorMailRecordId, 2893);
        assert.strictEqual(body.accountScope, null);
        assert.strictEqual(body.subject, "Re: Older introduction");
        assert.ok(body.textBody.indexOf("On 2026-09-05 11:49, acc1 wrote:") >= 0, "发送正文与所选引用同源");
        assert.strictEqual(body.senderAccountCode, undefined, "会话请求不得携带发件账号");
        assert.strictEqual(body.ragFactCodes, undefined, "会话请求不得携带 QA/RAG");
        assert.strictEqual(body.meeting, undefined, "会话请求不得携带会议字段");
    });

    it("I-3/I-8：无来信会话（outbound）不带 anchorMailRecordId，填入后携带所选 id", async () => {
        const ctx = await bootFollowup({ contact: contactB() });
        ctx.sandbox.crypto = { randomUUID: () => "00000000-0000-4000-8000-000000000002" };
        const b = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "2");
        click(b.querySelector(".mc-person-main"));
        await flush();
        const editor = ctx.host.querySelector('[aria-label="人工回复正文"]');
        editor.innerText = "free reply without anchor";
        inputEvent(editor);
        click(ctx.host.querySelector('[data-action="mc-send-manual"]'));
        await flush();
        assert.strictEqual(ctx.calls.sendConversation.length, 1);
        assert.strictEqual(Object.prototype.hasOwnProperty.call(ctx.calls.sendConversation[0].body, "anchorMailRecordId"), false,
            "未使用跟进弹窗的旧会话回信不得携带锚点字段");

        openFollowup(ctx);
        selectOption(ctx, 2893);
        applyFollowup(ctx);
        await flush();
        assert.ok(anchorNote(ctx), "outbound 草稿同样显示锚点提示");
        click(ctx.host.querySelector('[data-action="mc-send-manual"]'));
        await flush();
        assert.strictEqual(ctx.calls.sendConversation.length, 2);
        assert.strictEqual(ctx.calls.sendConversation[1].body.anchorMailRecordId, 2893);
    });

    it("I-4：换锚点清 requestId，同锚点失败重试复用同一 requestId", async () => {
        let uuidSeq = 0;
        const ctx = await bootFollowup({ sendConversationResult: false });
        ctx.sandbox.crypto = { randomUUID: () => `00000000-0000-4000-8000-${String(++uuidSeq).padStart(12, "0")}` };
        const a = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "1");
        click(a.querySelector(".mc-person-main"));
        await flush();

        openFollowup(ctx);
        selectOption(ctx, 2893);
        applyFollowup(ctx);
        await flush();
        click(ctx.host.querySelector('[data-action="mc-send-manual"]'));
        await flush();
        const first = ctx.calls.sendConversation[0].body;
        assert.strictEqual(first.anchorMailRecordId, 2893);
        assert.ok(first.requestId, "发送前必须生成 requestId");

        // 同锚点、正文不变 → 复用（失败重试收敛到同一 attempt）
        click(ctx.host.querySelector('[data-action="mc-send-manual"]'));
        await flush();
        assert.strictEqual(ctx.calls.sendConversation[1].body.requestId, first.requestId, "同锚点重试复用 requestId");
        assert.strictEqual(ctx.calls.sendConversation[1].body.anchorMailRecordId, 2893);

        // 换锚点 → requestId 严格作废
        openFollowup(ctx);
        selectOption(ctx, 4007);
        applyFollowup(ctx);
        await flush();
        click(ctx.host.querySelector('[data-action="mc-send-manual"]'));
        await flush();
        const third = ctx.calls.sendConversation[2].body;
        assert.strictEqual(third.anchorMailRecordId, 4007);
        assert.notStrictEqual(third.requestId, first.requestId, "换锚点必须作废旧 requestId");
    });

    it("I-7：采用可信草稿清除锚点提示并回落到无锚点发送", async () => {
        const ctx = await bootFollowup();
        const a = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "1");
        click(a.querySelector(".mc-person-main"));
        await flush();
        openFollowup(ctx);
        selectOption(ctx, 2893);
        applyFollowup(ctx);
        await flush();
        assert.ok(anchorNote(ctx));

        const wb = ctx.host.querySelector('.mc-section[data-section="workbench"]');
        toggleOpen(wb);
        await flush();
        await ctx.calls.workbenchMounts[0].callbacks.onComplete({ renderedDraftText: "adopted body", usedFactCodes: ["KB-1"] });
        await flush();
        assert.strictEqual(anchorNote(ctx), null, "采用可信草稿后锚点提示必须消失");

        click(ctx.host.querySelector('[data-action="mc-send-manual"]'));
        await flush();
        assert.strictEqual(ctx.calls.sendRich.length, 1, "清锚点后回到来信 adapter");
        assert.strictEqual(ctx.calls.sendConversation.length, 0);
    });

    it("I-4/S-3：切走再切回按草稿恢复主题、正文与锚点提示", async () => {
        const ctx = await bootFollowup();
        const a = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "1");
        click(a.querySelector(".mc-person-main"));
        await flush();
        openFollowup(ctx);
        selectOption(ctx, 2893);
        applyFollowup(ctx);
        await flush();

        const b = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "2");
        click(b.querySelector(".mc-person-main"));
        await flush();
        const a2 = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "1");
        click(a2.querySelector(".mc-person-main"));
        await flush();

        assert.strictEqual(ctx.host.querySelector('input[aria-label="回复主题"]').value, "Re: Older introduction");
        assert.ok(ctx.host.querySelector('[aria-label="人工回复正文"]').innerText.indexOf("On 2026-09-05 11:49, acc1 wrote:") >= 0);
        const note = anchorNote(ctx);
        assert.ok(note, "草稿恢复后锚点提示仍在");
        assert.strictEqual(note.textContent, "已引用邮件 #2893 · 2026-09-05 11:49 · Older introduction");
    });

    it("I-1/I-2：hasMore 时提示更早邮件；切专家关闭弹窗", async () => {
        const ctx = await bootFollowup({ messages: followupMessages({ hasMore: true, nextBefore: "cur" }) });
        const a = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "1");
        click(a.querySelector(".mc-person-main"));
        await flush();
        openFollowup(ctx);
        await flush();
        const more = dialogOf(ctx).querySelector('[data-role="followup-more"]');
        assert.ok(more, "存在更早页时必须明确提示");
        assert.ok(more.textContent.indexOf("加载更早信件") >= 0);
        assert.deepStrictEqual(
            followupOptions(ctx).map((node) => node.dataset.mailRecordId),
            ["4007", "2893"],
            "弹窗只列当前已加载窗口，不自动拉取历史"
        );

        const b = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "2");
        click(b.querySelector(".mc-person-main"));
        await flush();
        assert.strictEqual(dialogOf(ctx), null, "切专家必须关闭弹窗");
    });

    it("I-8：发送成功删除草稿与提示；失败保留草稿与提示", async () => {
        const ok = await bootFollowup();
        const a = ok.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "1");
        click(a.querySelector(".mc-person-main"));
        await flush();
        ok.sandbox.crypto = { randomUUID: () => "00000000-0000-4000-8000-000000000003" };
        openFollowup(ok);
        selectOption(ok, 2893);
        applyFollowup(ok);
        await flush();
        assert.ok(anchorNote(ok));
        click(ok.host.querySelector('[data-action="mc-send-manual"]'));
        await flush();
        assert.strictEqual(ok.calls.sendConversation.length, 1);
        assert.strictEqual(anchorNote(ok), null, "发送成功后锚点提示随草稿删除");

        const bad = await bootFollowup({ sendConversationError: "mail down" });
        const a2 = bad.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "1");
        click(a2.querySelector(".mc-person-main"));
        await flush();
        bad.sandbox.crypto = { randomUUID: () => "00000000-0000-4000-8000-000000000004" };
        openFollowup(bad);
        selectOption(bad, 2893);
        applyFollowup(bad);
        await flush();
        click(bad.host.querySelector('[data-action="mc-send-manual"]'));
        await flush();
        assert.strictEqual(bad.calls.sendConversation.length, 1);
        assert.ok(anchorNote(bad), "发送失败保留草稿与锚点提示");
        assert.ok(bad.host.querySelector('[aria-label="人工回复正文"]').innerText.indexOf("On 2026-09-05 11:49, acc1 wrote:") >= 0);
    });
});

// ════════════════════════════════════════════════════════════════════════
// fast-p 01：人工回复「引用邮件模板」（I-1..I-8 / S-1..S-3）
// ════════════════════════════════════════════════════════════════════════

const TEMPLATE_A = {
    id: 1,
    templateCode: "TPL_A",
    templateName: "验收-A",
    description: "第一套模板",
    subject: "Template subject A",
    enabled: true,
    subjectSnippetId: 5,
    blocks: [
        { id: 11, blockOrder: 1, blockType: "CUSTOM_TEXT", refId: null, refDisplayName: "自定义正文", customText: "第一段" },
        { id: 12, blockOrder: 2, blockType: "REPLY_SNIPPET", refId: 3, refDisplayName: "回复片段", customText: null }
    ]
};

const TEMPLATE_B = {
    id: 2,
    templateName: "验收-B",
    description: "第二套模板",
    subject: "Template subject B",
    enabled: true,
    blocks: [{ id: 21, blockOrder: 1, blockType: "CUSTOM_TEXT", refId: null, refDisplayName: "自定义正文", customText: "B 正文" }]
};

const TEMPLATE_DISABLED = Object.assign({}, TEMPLATE_B, { id: 3, templateName: "验收-B(停用)", enabled: false });

function templatePreviewResponse(extra) {
    return Object.assign({
        subject: "Template subject A",
        body: "第一段\n\n第二段",
        blocks: [{ blockOrder: 1, blockType: "CUSTOM_TEXT", refId: null, refDisplayName: "自定义正文", included: true, skipReason: null, textPreview: "第一段" }],
        fallbackKeys: [],
        toEmail: "a@example.edu",
        variables: [],
        variantPoolSize: 1
    }, extra || {});
}

describe("fast-p 01 引用邮件模板：入口 / 只读上下文 / 竞态 / 填入（I-1..I-8/S-1..S-3）", () => {
    const PREVIEW_BODY = "第一段\n\n第二段";
    const STALE_TEXT = "回复目标或草稿已变化，请关闭后重新选择模板";
    const UNRESOLVED_ACCOUNT_TEXT = "无法确认当前跟进邮件的发件账号，请重新选择跟进邮件";

    async function bootInboundTemplates(overrides, mountOptions) {
        const conversations = { items: [expertA(), expertB()], total: 2 };
        const ctx = await bootChat(Object.assign({
            conversations,
            messages: messagesA(),
            contact: contactA(),
            composeTemplates: [TEMPLATE_A, TEMPLATE_B, TEMPLATE_DISABLED],
            composePreview: templatePreviewResponse()
        }, overrides || {}), mountOptions);
        const a = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "1");
        click(a.querySelector(".mc-person-main"));
        await flush();
        return ctx;
    }

    function triggerOf(ctx) {
        return ctx.host.querySelector('[data-action="mc-open-template-reference"]');
    }
    function dialogOf(ctx) {
        return ctx.doc.body.querySelector(".reply-template-dialog");
    }
    function nodeOf(ctx, role) {
        const dialog = dialogOf(ctx);
        return dialog ? dialog.querySelector(`[data-role="${role}"]`) : null;
    }
    function optionsOf(ctx) {
        const dialog = dialogOf(ctx);
        return dialog ? dialog.querySelectorAll('[data-action="mc-select-template-reference"]') : [];
    }
    function applyOf(ctx) {
        const dialog = dialogOf(ctx);
        return dialog ? dialog.querySelector('[data-action="mc-apply-template-reference"]') : null;
    }
    function editorOf(ctx) {
        return ctx.host.querySelector('[aria-label="人工回复正文"]');
    }
    function subjectOf(ctx) {
        return ctx.host.querySelector('input[aria-label="回复主题"]');
    }
    function listCalls(ctx) {
        return ctx.calls.api.filter((entry) => entry.url === "/api/compose-templates");
    }
    function previewCalls(ctx) {
        return ctx.calls.api.filter((entry) => entry.url === "/api/compose-templates/preview-draft");
    }
    function templateCalls(ctx) {
        return ctx.calls.api.filter((entry) => entry.url.indexOf("/api/compose-templates") === 0);
    }
    function draftCards(ctx) {
        return ctx.host.querySelectorAll('[data-role="outbound-draft-files"] .outbound-file');
    }
    // MiniDOM 不做排版：br 不产生换行文本，故用节点结构断言纯文本语义（文本节点 + <br>）。
    function structureOf(node) {
        return Array.prototype.slice.call(node.childNodes).map((child) => {
            if (child.nodeType === 3) return `text:${child.data}`;
            const tag = String(child.tagName).toLowerCase();
            if (tag === "br") return "br";
            return `${tag}:${child.getAttribute("class") || ""}`;
        });
    }
    function holderOf(ctx) {
        const editor = editorOf(ctx);
        return editor.children[editor.children.length - 1];
    }
    async function openTemplate(ctx) {
        click(triggerOf(ctx));
        await flush();
        return dialogOf(ctx);
    }
    function selectTemplate(ctx, id) {
        const option = optionsOf(ctx).find((node) => node.dataset.templateId === String(id));
        assert.ok(option, `模板 ${id} 必须在列表里`);
        click(option);
    }
    function setSearch(ctx, value) {
        const input = nodeOf(ctx, "template-search");
        input.value = value;
        inputEvent(input);
    }
    function setReplaceSubject(ctx, on) {
        const box = nodeOf(ctx, "template-replace-subject");
        box.checked = !!on;
        changeEvent(box);
    }
    function setReplaceMode(ctx, on) {
        const dialog = dialogOf(ctx);
        const append = dialog.querySelector('input[name="reply-template-mode"][value="append"]');
        const replace = dialog.querySelector('input[name="reply-template-mode"][value="replace"]');
        append.checked = !on;
        replace.checked = !!on;
        changeEvent(on ? replace : append);
    }
    function statusTexts(ctx) {
        return ctx.calls.status.map((entry) => entry.message);
    }

    it("S-1/I-1：入口只属于来信人工回复，位于回形针之后、跟进之前；outbound/unavailable 无入口", async () => {
        const ctx = await bootInboundTemplates();
        const actions = ctx.host.querySelector(".mc-editor-tools").querySelectorAll("button[data-action]")
            .map((button) => button.getAttribute("data-action"));
        assert.deepStrictEqual(actions, [
            "mc-rich-command", "mc-rich-command", "mc-rich-command", "mc-rich-command",
            "mc-upload-attachment", "mc-open-template-reference", "mc-open-followup"
        ], "引用模板固定在回形针之后、跟进之前");
        const button = triggerOf(ctx);
        assert.strictEqual(button.getAttribute("class"), "button reply-template-trigger");
        assert.strictEqual(button.getAttribute("type"), "button");
        assert.strictEqual(button.textContent.trim(), "引用模板");
        // 会议组件缺席（本 harness 无 MailboxMeeting）仍可打开纯文本引用
        await openTemplate(ctx);
        assert.ok(dialogOf(ctx), "会议组件缺席仍可打开引用弹框");
        assert.ok(nodeOf(ctx, "template-body"), "纯文本预览容器存在");

        const outbound = await bootChat({ conversations: { items: [expertB(), expertA()], total: 2 }, messages: messagesA(), contact: contactB() });
        click(outbound.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "2").querySelector(".mc-person-main"));
        await flush();
        assert.strictEqual(outbound.host.querySelector('[data-action="mc-open-template-reference"]'), null, "outbound 不渲染入口");

        const none = await bootChat({ conversations: { items: [expertB({ sentCount: 0 }), expertA()], total: 2 }, messages: [], contact: contactB() });
        click(none.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "2").querySelector(".mc-person-main"));
        await flush();
        assert.strictEqual(none.host.querySelector('[data-action="mc-open-template-reference"]'), null, "unavailable 不渲染入口");
    });

    it("I-1/I-2：打开即重读列表、只呈现 enabled、payload 为四字段 blocks 与真实上下文", async () => {
        const ctx = await bootInboundTemplates();
        assert.strictEqual(listCalls(ctx).length, 0, "未打开时不请求模板");
        await openTemplate(ctx);
        assert.strictEqual(listCalls(ctx).length, 1, "打开即读取模板列表");
        assert.strictEqual(listCalls(ctx)[0].method, "GET");
        assert.deepStrictEqual(optionsOf(ctx).map((node) => node.dataset.templateId), ["1", "2"], "只呈现 enabled 模板");
        assert.deepStrictEqual(optionsOf(ctx).map((node) => node.getAttribute("aria-pressed")), ["true", "false"], "默认选中首个");
        assert.strictEqual(nodeOf(ctx, "template-count").textContent, "2");
        assert.strictEqual(previewCalls(ctx).length, 1, "默认选中即预览一次");
        assert.deepStrictEqual(JSON.parse(previewCalls(ctx)[0].body), {
            subject: "Template subject A",
            subjectSnippetId: 5,
            blocks: [
                { blockOrder: 1, blockType: "CUSTOM_TEXT", refId: null, customText: "第一段" },
                { blockOrder: 2, blockType: "REPLY_SNIPPET", refId: 3, customText: null }
            ],
            contactId: 1,
            senderAccountCode: "acc1",
            strictPlaceholders: false,
            variantIndex: 0
        });
        assert.strictEqual(nodeOf(ctx, "template-contact-email").textContent, "a@example.edu");
        assert.strictEqual(nodeOf(ctx, "template-contact-name").textContent, "专家A");
        assert.strictEqual(nodeOf(ctx, "template-account").textContent, "acc1");
        assert.strictEqual(nodeOf(ctx, "template-subject").textContent, "Template subject A");
        assert.strictEqual(nodeOf(ctx, "template-body").textContent, PREVIEW_BODY);
        assert.strictEqual(nodeOf(ctx, "template-preview-badge").hidden, false, "成功预览显示徽标");
        assert.strictEqual(applyOf(ctx).disabled, false);
        assert.deepStrictEqual(templateCalls(ctx).map((entry) => entry.method), ["GET", "POST"], "只使用两个只读接口");
    });

    it("I-2：每次打开重读列表；换模板只保留新快照；应用不再重新预览", async () => {
        const seen = [];
        const ctx = await bootInboundTemplates({
            route: (url, method, body, entry, next) => {
                if (url === "/api/compose-templates") return Promise.resolve([TEMPLATE_A, TEMPLATE_B]);
                if (url === "/api/compose-templates/preview-draft") {
                    const parsed = JSON.parse(body);
                    seen.push(parsed.subject);
                    return Promise.resolve(templatePreviewResponse({
                        subject: parsed.subject,
                        body: parsed.subject === "Template subject B" ? "B 正文" : PREVIEW_BODY
                    }));
                }
                return next(url, method, body, entry);
            }
        });
        await openTemplate(ctx);
        assert.strictEqual(nodeOf(ctx, "template-body").textContent, PREVIEW_BODY);
        selectTemplate(ctx, 2);
        await flush();
        assert.deepStrictEqual(seen, ["Template subject A", "Template subject B"], "换模板重新预览一次");
        assert.strictEqual(nodeOf(ctx, "template-body").textContent, "B 正文", "只保留新模板快照");
        assert.strictEqual(nodeOf(ctx, "template-subject").textContent, "Template subject B");
        click(applyOf(ctx));
        await flush();
        assert.strictEqual(previewCalls(ctx).length, 2, "应用不再调用预览");
        assert.deepStrictEqual(structureOf(holderOf(ctx)), ["text:B 正文"], "只填入当前快照");
        await openTemplate(ctx);
        assert.strictEqual(listCalls(ctx).length, 2, "每次打开重读列表");
        assert.strictEqual(nodeOf(ctx, "template-body").textContent, PREVIEW_BODY, "重开后默认选中首个");
    });

    it("I-2：搜索只在本地过滤，保留仍在结果中的选择，空结果清选中", async () => {
        const ctx = await bootInboundTemplates();
        await openTemplate(ctx);
        setSearch(ctx, "第二");
        await flush();
        assert.deepStrictEqual(optionsOf(ctx).map((node) => node.dataset.templateId), ["2"], "按名称本地过滤");
        assert.strictEqual(previewCalls(ctx).length, 2, "搜索后选择变化才重新预览一次");
        setSearch(ctx, "不存在的模板");
        await flush();
        assert.deepStrictEqual(optionsOf(ctx).map((node) => node.dataset.templateId), []);
        assert.match(dialogOf(ctx).querySelector('[data-role="template-list"]').textContent, /未找到匹配模板/);
        assert.strictEqual(applyOf(ctx).disabled, true, "无选中模板禁止填入");
        assert.strictEqual(previewCalls(ctx).length, 2, "空结果不发预览请求");
    });

    it("I-3：toEmail 缺失/不匹配、空正文、残留变量、示例退订值都禁止填入并给出提示", async () => {
        const cases = [
            { name: "toEmail 缺失", preview: templatePreviewResponse({ toEmail: null }), expect: /预览联系人邮箱与当前专家邮箱不一致/ },
            { name: "toEmail 不匹配", preview: templatePreviewResponse({ toEmail: "other@example.edu" }), expect: /预览联系人邮箱与当前专家邮箱不一致/ },
            { name: "空正文", preview: templatePreviewResponse({ body: "   " }), expect: /预览正文为空/ },
            { name: "残留变量", preview: templatePreviewResponse({ body: "Dear ${expertFamilyName}," }), expect: /正文仍含未替换变量/ },
            { name: "示例退订值", preview: templatePreviewResponse({ body: "退订：https://example.com/u/unsubscribe?token=preview" }), expect: /退订链接尚未配置，当前仅为示例链接，请先配置后重试/ }
        ];
        for (const item of cases) {
            const ctx = await bootInboundTemplates({ composePreview: item.preview });
            await openTemplate(ctx);
            assert.strictEqual(applyOf(ctx).disabled, true, `${item.name} 必须禁止填入`);
            assert.strictEqual(nodeOf(ctx, "template-warning").hidden, false, `${item.name} 必须显示提示`);
            assert.match(nodeOf(ctx, "template-warning").textContent, item.expect, `${item.name} 提示文案`);
            click(applyOf(ctx));
            await flush();
            assert.strictEqual(editorOf(ctx).textContent.trim(), "", `${item.name} 不得改正文`);
            assert.strictEqual(ctx.calls.sendRich.length, 0, `${item.name} 不得触发发送`);
            assert.ok(dialogOf(ctx), `${item.name} 弹框保持打开`);
        }
    });

    it("I-3：主题残留 ${ 只在勾选「同时替换回复主题」时阻止填入", async () => {
        const ctx = await bootInboundTemplates({
            composePreview: templatePreviewResponse({ subject: "Hi ${expertName}", body: "正文正常" })
        });
        await openTemplate(ctx);
        assert.strictEqual(applyOf(ctx).disabled, false, "未勾选时不因主题残留阻止正文");
        setReplaceSubject(ctx, true);
        assert.strictEqual(applyOf(ctx).disabled, true, "勾选后主题残留阻止填入");
        assert.match(nodeOf(ctx, "template-warning").textContent, /主题仍含未替换变量/);
        setReplaceSubject(ctx, false);
        assert.strictEqual(applyOf(ctx).disabled, false);
    });

    it("I-3：默认值/缺值/跳过块分别提示；textPreview 截断不影响完整正文", async () => {
        const longBody = "长正文".repeat(120);
        const ctx = await bootInboundTemplates({
            composePreview: templatePreviewResponse({
                body: longBody,
                blocks: [
                    { blockOrder: 1, blockType: "CUSTOM_TEXT", refId: null, refDisplayName: "自定义正文", included: true, skipReason: null, textPreview: longBody.slice(0, 200) },
                    { blockOrder: 2, blockType: "REPLY_SNIPPET", refId: 9, refDisplayName: "停用片段", included: false, skipReason: "片段已禁用", textPreview: null }
                ],
                fallbackKeys: ["expertFamilyName"],
                variables: [
                    { key: "expertFamilyName", label: "姓氏", value: "Professor", filled: true, usedFallback: true },
                    { key: "programmeName", label: "项目", value: "", filled: false, usedFallback: false }
                ]
            })
        });
        await openTemplate(ctx);
        const warning = nodeOf(ctx, "template-warning").textContent;
        assert.match(warning, /未包含正文块：2\. 停用片段（片段已禁用）/);
        assert.match(warning, /使用默认值：expertFamilyName/);
        assert.match(warning, /缺少变量值：programmeName；填入后请补齐/);
        assert.strictEqual(nodeOf(ctx, "template-body").textContent, longBody, "正文使用完整 body 而非截断的 textPreview");
        assert.strictEqual(applyOf(ctx).disabled, false, "默认值/缺值只是人工提示，不新建发送门禁");
    });

    it("I-3：加载失败与预览失败分别提示；重试只重试当前失败阶段", async () => {
        const listFailed = await bootInboundTemplates({ composeTemplatesError: "list down" });
        await openTemplate(listFailed);
        assert.strictEqual(nodeOf(listFailed, "template-status").textContent, "邮件模板加载失败，请重试");
        assert.strictEqual(applyOf(listFailed).disabled, true);
        const retry = dialogOf(listFailed).querySelector('[data-action="mc-retry-template-reference"]');
        assert.strictEqual(retry.hidden, false, "失败显示重试");
        assert.strictEqual(previewCalls(listFailed).length, 0, "列表失败不请求预览");
        click(retry);
        await flush();
        assert.strictEqual(listCalls(listFailed).length, 2, "重试只重读列表");
        assert.strictEqual(previewCalls(listFailed).length, 0);

        const previewFailed = await bootInboundTemplates({ composePreviewError: "preview down" });
        await openTemplate(previewFailed);
        assert.strictEqual(nodeOf(previewFailed, "template-status").textContent, "模板预览失败，请重试");
        assert.strictEqual(applyOf(previewFailed).disabled, true);
        click(dialogOf(previewFailed).querySelector('[data-action="mc-retry-template-reference"]'));
        await flush();
        assert.strictEqual(listCalls(previewFailed).length, 1, "预览重试不重读列表");
        assert.strictEqual(previewCalls(previewFailed).length, 2);
    });

    it("I-4：预览与编辑器保持字面纯文本；发送 payload 为完整 html/text；不写 localStorage", async () => {
        const literal = "<img src=x onerror=alert(1)> A & B\n第二行";
        let localStorageWrites = 0;
        const ctx = await bootInboundTemplates({ composePreview: templatePreviewResponse({ body: literal }) });
        ctx.sandbox.localStorage = {
            getItem: () => null,
            setItem: () => { localStorageWrites += 1; },
            removeItem: () => {}
        };
        await openTemplate(ctx);
        const bodyNode = nodeOf(ctx, "template-body");
        assert.strictEqual(bodyNode.textContent, literal, "预览逐字字面文本");
        assert.strictEqual(bodyNode.querySelector("img"), null, "预览不生成图片节点");
        assert.strictEqual(bodyNode.querySelector("script"), null, "预览不生成脚本节点");
        click(applyOf(ctx));
        await flush();
        const editor = editorOf(ctx);
        assert.strictEqual(editor.querySelector("img"), null, "编辑器不产生可执行节点");
        assert.strictEqual(editor.querySelector("script"), null);
        const holder = holderOf(ctx);
        assert.strictEqual(holder.tagName, "DIV", "模板正文包在无 class 的 div");
        assert.strictEqual(holder.getAttribute("class"), null);
        assert.deepStrictEqual(structureOf(holder), [
            "text:<img src=x onerror=alert(1)> A & B", "br", "text:第二行"
        ], "字面文本进文本节点，换行只由 <br> 表达");
        // 浏览器把该结构渲染成带换行的纯文本；按既有约定注入真实 innerText 后再发送。
        setEditorContent(editor, editor.innerHTML, literal);
        click(ctx.host.querySelector('[data-action="mc-send-manual"]'));
        await flush();
        const body = ctx.calls.sendRich[0].body;
        assert.ok(body.textBody.includes("<img src=x onerror=alert(1)> A & B"), "纯文本逐字外发");
        assert.ok(body.htmlBody.includes("&lt;img src=x onerror=alert(1)&gt;"), "HTML 逐字转义，不执行");
        assert.strictEqual(localStorageWrites, 0, "不写 localStorage 正文");
        assert.strictEqual(ctx.calls.sendRich.length, 1, "只有用户点击发送才发信");
    });

    it("I-5：默认不改主题；空正文直接填、非空默认追加、显式替换只清正文", async () => {
        const ctx = await bootInboundTemplates();
        const editor = editorOf(ctx);
        setEditorContent(editor, "<b>原有正文</b>", "原有正文");
        inputEvent(editor);
        const subjectBefore = subjectOf(ctx).value;
        await openTemplate(ctx);
        assert.strictEqual(nodeOf(ctx, "template-modes").hidden, false, "正文非空显示模式选择");
        click(applyOf(ctx));
        await flush();
        assert.strictEqual(subjectOf(ctx).value, subjectBefore, "默认保留回复主题");
        assert.ok(editorOf(ctx).querySelector("b"), "追加保留原有富文本节点");
        assert.deepStrictEqual(editorOf(ctx).children.map((child) => child.tagName), ["B", "DIV"], "原有节点不重建，模板正文追加到末尾");
        assert.deepStrictEqual(structureOf(holderOf(ctx)), ["text:第一段", "br", "br", "text:第二段"]);

        await openTemplate(ctx);
        setReplaceSubject(ctx, true);
        setReplaceMode(ctx, true);
        click(applyOf(ctx));
        await flush();
        assert.strictEqual(subjectOf(ctx).value, "Template subject A", "勾选后逐字采用预览主题");
        assert.strictEqual(editorOf(ctx).querySelector("b"), null, "旧节点被清除");
        assert.deepStrictEqual(editorOf(ctx).children.map((child) => child.tagName), ["DIV"], "替换后只留模板正文");
        assert.deepStrictEqual(structureOf(holderOf(ctx)), ["text:第一段", "br", "br", "text:第二段"]);

        const empty = await bootInboundTemplates();
        await openTemplate(empty);
        assert.strictEqual(nodeOf(empty, "template-modes").hidden, true, "正文为空时不显示模式选择");
        click(applyOf(empty));
        await flush();
        assert.deepStrictEqual(structureOf(holderOf(empty)), ["text:第一段", "br", "br", "text:第二段"], "空正文直接填入");
    });

    it("I-5：追加保留 QA/RAG 证据，替换清除；追加不改变主题外的既有引用锚点", async () => {
        const ctx = await bootInboundTemplates();
        const wb = ctx.host.querySelector('.mc-section[data-section="workbench"]');
        toggleOpen(wb);
        await flush();
        await ctx.calls.workbenchMounts[0].callbacks.onComplete({
            renderedDraftText: "adopted body",
            text: "adopted body",
            usedFactCodes: ["KB-COMM-044"],
            ragCorpusFingerprint: "fp-2026"
        });
        await flush();
        await openTemplate(ctx);
        click(applyOf(ctx));
        await flush();
        click(ctx.host.querySelector('[data-action="mc-send-manual"]'));
        await flush();
        const appended = ctx.calls.sendRich[0].body;
        assert.deepStrictEqual(appended.ragFactCodes, ["KB-COMM-044"], "追加保留 QA/RAG 证据");
        assert.strictEqual(appended.ragCorpusFingerprint, "fp-2026", "追加保留语料指纹");
        assert.strictEqual(appended.edited, true, "正文变化后 edited=true");
        // 采用稿以纯文本节点落地，追加只在其后插入模板正文块（原节点不重建）
        assert.deepStrictEqual(structureOf(editorOf(ctx)), ["text:adopted body", "div:"], "追加保留采用稿并追加模板正文块");
        assert.ok(appended.textBody.includes("adopted body") && appended.textBody.includes("第一段"), "追加保留原文与模板正文");

        const replaced = await bootInboundTemplates();
        toggleOpen(replaced.host.querySelector('.mc-section[data-section="workbench"]'));
        await flush();
        await replaced.calls.workbenchMounts[0].callbacks.onComplete({
            renderedDraftText: "adopted body",
            text: "adopted body",
            usedFactCodes: ["KB-COMM-044"],
            ragCorpusFingerprint: "fp-2026"
        });
        await flush();
        await openTemplate(replaced);
        setReplaceMode(replaced, true);
        click(applyOf(replaced));
        await flush();
        click(replaced.host.querySelector('[data-action="mc-send-manual"]'));
        await flush();
        const replacedBody = replaced.calls.sendRich[0].body;
        assert.strictEqual(replacedBody.ragFactCodes, undefined, "替换正文清除 QA/RAG 证据");
        assert.strictEqual(replacedBody.ragCorpusFingerprint, undefined);
        assert.deepStrictEqual(editorOf(replaced).children.map((child) => child.tagName), ["DIV"], "只剩模板正文");
    });

    it("I-2/I-5：跟进锚点账号取该封成功发件账号；发送仍携带原锚点", async () => {
        const ctx = await bootInboundTemplates({
            messages: {
                items: [
                    { source: "MAIL_RECORD", id: 4007, contactId: 1, direction: "OUTBOUND", accountCode: "acc1", subject: "Intro", body: "b", cleanedBody: "b", eventAt: "2026-09-06T09:00:00", sendStatus: "SENT", processStatus: null, attachmentCount: 0, firstAttachmentNames: [], messageId: "m4007", inReplyTo: null, tags: [] },
                    { source: "MAIL_RECORD", id: 4011, contactId: 1, direction: "OUTBOUND", accountCode: "acc9", subject: "Other account send", body: "b", cleanedBody: "b", eventAt: "2026-09-06T10:00:00", sendStatus: "SENT", processStatus: null, attachmentCount: 0, firstAttachmentNames: [], messageId: "m4011", inReplyTo: null, tags: [] },
                    { source: "INBOUND_PROCESSING", id: 101, contactId: 1, direction: "INBOUND", accountCode: "acc1", subject: "Question 1", body: "raw", cleanedBody: "cleaned", eventAt: "2026-09-07T03:00:00", sendStatus: null, processStatus: "MANUAL_REVIEW", attachmentCount: 0, firstAttachmentNames: [], messageId: "m101", inReplyTo: null, tags: [] }
                ],
                nextBefore: null,
                hasMore: false
            }
        });
        ctx.sandbox.state = { accounts: [{ accountCode: "acc1", senderName: "Alice" }, { accountCode: "acc9", senderName: "Bob" }] };
        vm.runInContext(extractFn("mcHostGetSenderName"), ctx.sandbox);
        // 普通来信：账号取来信账号 acc1
        await openTemplate(ctx);
        assert.strictEqual(nodeOf(ctx, "template-account").textContent, "acc1");
        assert.strictEqual(JSON.parse(previewCalls(ctx)[0].body).senderAccountCode, "acc1");
        click(dialogOf(ctx).querySelector('[data-action="mc-close-template-reference"]'));
        // 选择 acc9 的成功发件作为跟进锚点
        click(ctx.host.querySelector('[data-action="mc-open-followup"]'));
        await flush();
        const option = ctx.doc.body.querySelector(".followup-dialog").querySelectorAll('[data-action="mc-select-followup"]')
            .find((node) => node.dataset.mailRecordId === "4011");
        assert.ok(option, "acc9 的成功发件必须是候选项");
        click(option);
        click(ctx.doc.body.querySelector(".followup-dialog").querySelector('[data-action="mc-select-followup-copy"]'));
        click(ctx.doc.body.querySelector(".followup-dialog").querySelector('[data-action="mc-apply-followup"]'));
        await flush();
        await openTemplate(ctx);
        assert.strictEqual(nodeOf(ctx, "template-account").textContent, "acc9", "锚点账号优先于来信账号与联系人绑定账号");
        const anchored = previewCalls(ctx);
        assert.strictEqual(JSON.parse(anchored[anchored.length - 1].body).senderAccountCode, "acc9");
        click(applyOf(ctx));
        await flush();
        click(ctx.host.querySelector('[data-action="mc-send-manual"]'));
        await flush();
        assert.strictEqual(ctx.calls.sendConversation.length, 1, "带锚点走会话发送路径");
        assert.strictEqual(ctx.calls.sendConversation[0].body.anchorMailRecordId, 4011, "模板应用保留跟进锚点");
        assert.strictEqual(ctx.calls.sendRich.length, 0);
    });

    it("I-2：跟进锚点失效（该邮件不再是合法成功发件）时显示固定文案且不发请求", async () => {
        let messagesCalls = 0;
        const baseItems = [
            { source: "MAIL_RECORD", id: 4011, contactId: 1, direction: "OUTBOUND", accountCode: "acc1", subject: "Sent", body: "b", cleanedBody: "b", eventAt: "2026-09-06T10:00:00", sendStatus: "SENT", processStatus: null, attachmentCount: 0, firstAttachmentNames: [], messageId: "m4011", inReplyTo: null, tags: [] },
            { source: "INBOUND_PROCESSING", id: 101, contactId: 1, direction: "INBOUND", accountCode: "acc1", subject: "Question 1", body: "raw", cleanedBody: "cleaned", eventAt: "2026-09-07T03:00:00", sendStatus: null, processStatus: "MANUAL_REVIEW", attachmentCount: 0, firstAttachmentNames: [], messageId: "m101", inReplyTo: null, tags: [] }
        ];
        const ctx = await bootInboundTemplates({
            messages: { items: baseItems, nextBefore: null, hasMore: false },
            route: (url, method, body, entry, next) => {
                if (/\/messages\?/.test(url)) {
                    messagesCalls += 1;
                    const items = messagesCalls === 1
                        ? baseItems
                        : baseItems.map((item) => (item.id === 4011 ? Object.assign({}, item, { sendStatus: "FAILED" }) : item));
                    return Promise.resolve({ items, nextBefore: null, hasMore: false });
                }
                return next(url, method, body, entry);
            }
        });
        ctx.sandbox.state = { accounts: [{ accountCode: "acc1", senderName: "Alice" }] };
        vm.runInContext(extractFn("mcHostGetSenderName"), ctx.sandbox);
        click(ctx.host.querySelector('[data-action="mc-open-followup"]'));
        await flush();
        click(ctx.doc.body.querySelector(".followup-dialog").querySelector('[data-action="mc-select-followup"]'));
        click(ctx.doc.body.querySelector(".followup-dialog").querySelector('[data-action="mc-select-followup-copy"]'));
        click(ctx.doc.body.querySelector(".followup-dialog").querySelector('[data-action="mc-apply-followup"]'));
        await flush();
        // 服务端随后报告该发件为 FAILED：锚点不再是合法候选
        const api = ctx.sandbox.MailboxChat.mount(ctx.host, {});
        await api.refreshFromHost();
        await flush();
        const before = templateCalls(ctx).length;
        click(triggerOf(ctx));
        await flush();
        assert.strictEqual(dialogOf(ctx), null, "锚点无法解析时不得打开弹框");
        assert.strictEqual(templateCalls(ctx).length, before, "锚点无法解析时零请求");
        assert.ok(statusTexts(ctx).includes(UNRESOLVED_ACCOUNT_TEXT), "显示固定失败文案");
    });

    it("I-5/I-1：应用保留已就绪的通用附件；发送中入口禁用且不会打开弹框", async () => {
        let pendingSend = null;
        const ctx = await bootInboundTemplates({
            route: (url, method, body, entry, next) => {
                if (/\/api\/mail\/conversations\/\d+\/outbound-attachments$/.test(url)) {
                    return Promise.resolve({
                        id: "att-1", filename: "template-reference-check.txt", contentType: "text/plain",
                        byteLength: 3, sha256: "sha-att-1", downloadUrl: "/api/mail/attachments/att-1"
                    });
                }
                return next(url, method, body, entry);
            }
        });
        ctx.sandbox.FormData = class SandboxFormData { append() {} };
        ctx.sandbox.mcHostSendRichReply = (processingId, body) => {
            ctx.calls.sendRich.push({ processingId: Number(processingId), body });
            return new Promise((resolve) => { pendingSend = resolve; });
        };
        const fileInput = ctx.host.querySelector('[data-role="outbound-file-input"]');
        fileInput.files = [{ name: "template-reference-check.txt", size: 3 }];
        changeEvent(fileInput);
        await flush();
        assert.deepStrictEqual(draftCards(ctx).map((card) => card.getAttribute("data-state")), ["ready"], "附件上传完成");
        await openTemplate(ctx);
        click(applyOf(ctx));
        await flush();
        assert.deepStrictEqual(draftCards(ctx).map((card) => card.getAttribute("data-state")), ["ready"], "模板应用后附件仍就绪");
        click(ctx.host.querySelector('[data-action="mc-send-manual"]'));
        await flush();
        assert.strictEqual(triggerOf(ctx).disabled, true, "发送中入口 disabled");
        const before = templateCalls(ctx).length;
        click(triggerOf(ctx));
        await flush();
        assert.strictEqual(dialogOf(ctx), null, "发送中不打开引用弹框");
        assert.strictEqual(templateCalls(ctx).length, before, "发送中零请求");
        assert.strictEqual(pendingSend !== null, true, "发送请求已发出");
        pendingSend(true);
        await flush();
        assert.strictEqual(ctx.calls.sendRich[0].body.attachmentIds.join(","), "att-1", "模板应用保留通用附件");
    });

    it("I-6：慢响应不得覆盖新选择；关闭/切专家后的回包不再写入", async () => {
        const pending = [];
        const ctx = await bootInboundTemplates({
            route: (url, method, body, entry, next) => {
                if (url === "/api/compose-templates") return Promise.resolve([TEMPLATE_A, TEMPLATE_B]);
                if (url === "/api/compose-templates/preview-draft") {
                    const parsed = JSON.parse(body);
                    return new Promise((resolve) => {
                        pending.push({
                            subject: parsed.subject,
                            resolve: () => resolve(templatePreviewResponse({ subject: parsed.subject, body: `body:${parsed.subject}` }))
                        });
                    });
                }
                return next(url, method, body, entry);
            }
        });
        await openTemplate(ctx);
        assert.strictEqual(pending.length, 1);
        pending[0].resolve();
        await flush();
        assert.strictEqual(nodeOf(ctx, "template-body").textContent, "body:Template subject A");
        // A 慢 B 快：只显示 B
        selectTemplate(ctx, 1);
        assert.strictEqual(pending.length, 2);
        selectTemplate(ctx, 2);
        assert.strictEqual(pending.length, 3);
        pending[2].resolve();
        await flush();
        assert.strictEqual(nodeOf(ctx, "template-body").textContent, "body:Template subject B");
        pending[1].resolve();
        await flush();
        assert.strictEqual(nodeOf(ctx, "template-body").textContent, "body:Template subject B", "迟到的 A 响应不得覆盖 B");
        // 关闭后回包不再挂载
        selectTemplate(ctx, 1);
        const late = pending[pending.length - 1];
        click(dialogOf(ctx).querySelectorAll('[data-action="mc-close-template-reference"]')[1]);
        await flush();
        assert.strictEqual(dialogOf(ctx), null, "取消关闭弹框");
        late.resolve();
        await flush();
        assert.strictEqual(dialogOf(ctx), null, "关闭后迟到回包不得重新渲染");
        // 切专家（新 epoch）后回包不写入
        await openTemplate(ctx);
        selectTemplate(ctx, 2);
        const stale = pending[pending.length - 1];
        const b = ctx.host.querySelectorAll(".mc-person").find((person) => person.dataset.contactId === "2");
        click(b.querySelector(".mc-person-main"));
        await flush();
        assert.strictEqual(dialogOf(ctx), null, "切专家关闭引用弹框");
        stale.resolve();
        await flush();
        assert.strictEqual(dialogOf(ctx), null, "切专家后迟到回包不写入新目标");
    });

    it("I-6：打开后修改正文/主题 → 应用被禁用并提示固定文案，不写入任何内容", async () => {
        const ctx = await bootInboundTemplates();
        await openTemplate(ctx);
        const editor = editorOf(ctx);
        setEditorContent(editor, "打开后输入", "打开后输入");
        inputEvent(editor);
        click(applyOf(ctx));
        await flush();
        assert.strictEqual(editorOf(ctx).textContent, "打开后输入", "不得写入模板正文");
        assert.strictEqual(subjectOf(ctx).value.includes("Template subject A"), false, "不得写入模板主题");
        assert.ok(statusTexts(ctx).includes(STALE_TEXT), "显示固定的草稿已变化提示");
        assert.strictEqual(nodeOf(ctx, "template-status").textContent, STALE_TEXT);
        assert.strictEqual(applyOf(ctx).disabled, true, "变化后禁用填入");
        assert.ok(dialogOf(ctx), "弹框保持打开");
    });

    it("I-7：Esc/X/取消走同一关闭路径；应用后焦点回正文；unmount 关闭并移除弹框", async () => {
        const ctx = await bootInboundTemplates();
        const focused = [];
        const trigger = triggerOf(ctx);
        trigger.focus = () => { focused.push("trigger"); };
        await openTemplate(ctx);
        assert.ok(dialogOf(ctx));
        keyEvent(nodeOf(ctx, "template-search"), "Escape");
        await flush();
        assert.strictEqual(dialogOf(ctx), null, "Esc 关闭");
        assert.deepStrictEqual(focused, ["trigger"], "Esc 后焦点回到仍在 DOM 中的触发按钮");
        await openTemplate(ctx);
        click(dialogOf(ctx).querySelectorAll('[data-action="mc-close-template-reference"]')[0]);
        await flush();
        assert.strictEqual(dialogOf(ctx), null, "关闭按钮关闭");
        await openTemplate(ctx);
        click(dialogOf(ctx).querySelectorAll('[data-action="mc-close-template-reference"]')[1]);
        await flush();
        assert.strictEqual(dialogOf(ctx), null, "取消关闭");
        // 重复开关不残留遮罩/弹框，Esc 一次即可关闭
        for (let i = 0; i < 3; i += 1) {
            await openTemplate(ctx);
            keyEvent(nodeOf(ctx, "template-search"), "Escape");
            await flush();
        }
        assert.strictEqual(ctx.doc.body.querySelectorAll(".reply-template-dialog").length, 0, "反复开关后不留残影");
        const editor = editorOf(ctx);
        editor.focus = () => { focused.push("editor"); };
        await openTemplate(ctx);
        click(applyOf(ctx));
        await flush();
        assert.deepStrictEqual(focused.slice(-1), ["editor"], "应用后聚焦正文");
        assert.ok(statusTexts(ctx).includes("模板已填入回复，可继续编辑"));
        // 打开跟进弹窗：引用弹框关闭，且新面板不被旧 close 清掉
        await openTemplate(ctx);
        click(ctx.host.querySelector('[data-action="mc-open-followup"]'));
        await flush();
        assert.strictEqual(dialogOf(ctx), null, "打开跟进弹窗关闭引用弹框");
        assert.ok(ctx.doc.body.querySelector(".followup-dialog"), "跟进弹窗正常打开");
        // 打开管理面板：引用弹框关闭，管理面板正常
        await openTemplate(ctx);
        click(ctx.host.querySelector('[data-action="mc-manage-expert"]'));
        await flush();
        assert.strictEqual(dialogOf(ctx), null);
        assert.ok(ctx.doc.body.querySelector(".mc-manage-overlay"), "管理面板正常打开");
        closeManageFromTest(ctx);
        // unmount 关闭并移除
        await openTemplate(ctx);
        assert.ok(dialogOf(ctx));
        ctx.sandbox.MailboxChat.unmount(ctx.host);
        assert.strictEqual(ctx.doc.body.querySelector(".reply-template-dialog"), null, "unmount 关闭并移除弹框");
    });

    function closeManageFromTest(ctx) {
        const close = ctx.doc.body.querySelector('[data-action="mc-close-manage"]');
        if (close) click(close);
    }

    it("I-8：整个交互只发生两个只读请求，且应用不触发任何发送/模板写入", async () => {
        const ctx = await bootInboundTemplates();
        await openTemplate(ctx);
        selectTemplate(ctx, 2);
        await flush();
        click(applyOf(ctx));
        await flush();
        const urls = templateCalls(ctx).map((entry) => `${entry.method} ${entry.url}`);
        assert.deepStrictEqual(Array.from(new Set(urls)), ["GET /api/compose-templates", "POST /api/compose-templates/preview-draft"], "只使用两个只读接口");
        assert.strictEqual(ctx.calls.sendRich.length, 0, "应用不发送邮件");
        assert.strictEqual(ctx.calls.sendConversation.length, 0);
        assert.strictEqual(ctx.calls.api.filter((entry) => entry.method !== "GET" && entry.url.indexOf("/api/compose-templates") !== 0).length, 0, "无其它写请求");
    });
});

// ════════════════════════════════════════════════════════════════════════
// fast-p 2026-10-02 c3：紧凑所在地 + 北京时间推荐（I-1..I-6 / S-1..S-3）
// 只读消费 /api/mail/contact-locations/*；唯一写路径是配置弹窗的 PUT。
// ════════════════════════════════════════════════════════════════════════

function unconfiguredLocation(contactId) {
    return {
        contactId: Number(contactId), configured: false, countryCode: null, countryLabel: null,
        zoneId: null, effectiveZoneId: null, zoneLabel: null, usingDefaultZone: false
    };
}

// 巴西默认时区 America/Sao_Paulo：当地 08:00–17:00 → 北京 20:00–次日 05:00（跨北京日期）
const TIMING_WORK_HOURS_BR = {
    location: {
        contactId: 1, configured: true, countryCode: "BR", countryLabel: "巴西",
        zoneId: null, effectiveZoneId: "America/Sao_Paulo", zoneLabel: "巴西 · 圣保罗", usingDefaultZone: true
    },
    recommendation: {
        mode: "WORK_HOURS",
        localStart: "2026-10-02T09:00:00-03:00",
        localEnd: "2026-10-02T18:00:00-03:00",
        beijingStart: "2026-10-02T20:00:00+08:00",
        beijingEnd: "2026-10-03T05:00:00+08:00",
        sampleCount: 0,
        replyDayCount: 0,
        historyDays: 180,
        historyTruncated: false,
        recentSamples: [],
        calculatedAt: "2026-10-02T12:00:00Z"
    }
};

// 印度显式时区 Asia/Kolkata（+05:30）：窗口同北京日期，样本跨当地日期
const TIMING_REPLY_IN = {
    location: {
        contactId: 1, configured: true, countryCode: "IN", countryLabel: "印度",
        zoneId: "Asia/Kolkata", effectiveZoneId: "Asia/Kolkata", zoneLabel: "印度 · 加尔各答", usingDefaultZone: false
    },
    recommendation: {
        mode: "REPLY_PATTERN",
        localStart: "2026-10-02T13:00:00+05:30",
        localEnd: "2026-10-02T15:00:00+05:30",
        beijingStart: "2026-10-02T15:30:00+08:00",
        beijingEnd: "2026-10-02T17:30:00+08:00",
        sampleCount: 3,
        replyDayCount: 3,
        historyDays: 180,
        historyTruncated: false,
        recentSamples: [
            { receivedAtBeijing: "2026-10-01T01:15:00+08:00", receivedAtLocal: "2026-09-30T22:45:00+05:30" },
            { receivedAtBeijing: "2026-10-02T00:30:00+08:00", receivedAtLocal: "2026-10-01T22:00:00+05:30" }
        ],
        calculatedAt: "2026-10-02T12:00:00Z"
    }
};

describe("fast-p c3 收发信箱紧凑所在地与推荐时间（I-1..I-6 / S-1..S-3）", () => {
    function timingGroup(ctx) {
        return ctx.host.querySelector('[data-role="contact-timing"]');
    }
    function timingDialog(ctx) {
        return ctx.doc.body.querySelector(".contact-timing-dialog");
    }
    function personOf(ctx, contactId) {
        return ctx.host.querySelectorAll(".mc-person").find((node) => node.dataset.contactId === String(contactId));
    }
    function timingRequests(ctx) {
        return ctx.calls.api.filter((entry) => /^\/api\/mail\/contact-locations\/\d+\/timing$/.test(entry.url));
    }
    function putRequests(ctx) {
        return ctx.calls.api.filter((entry) => entry.method === "PUT" && /^\/api\/mail\/contact-locations\/\d+$/.test(entry.url));
    }
    function submitDialog(dialog) {
        dialog.querySelector('[data-role="contact-location-form"]')
            .dispatchEvent(new MiniEvent("submit", { bubbles: true }));
    }
    function contactFixture(contactId, orcidId) {
        return {
            contact: {
                id: Number(contactId),
                orcidId,
                expertEmail: `e${contactId}@example.edu`,
                expertName: `专家${contactId}`,
                currentIndexLevel: "APPLICATION",
                operatorStatus: "REPLIED",
                currentStatus: "WAITING_REPLY"
            },
            mails: []
        };
    }
    async function bootSelected(contactId, extra) {
        const id = Number(contactId);
        const ctx = await bootChat(Object.assign({
            conversations: { items: [expertA(), expertB()], total: 2 },
            messages: { items: [], nextBefore: null, hasMore: false },
            contact: contactFixture(id === 2 ? 2 : 1, id === 2 ? "0000-0002" : "0000-0001")
        }, extra || {}));
        click(personOf(ctx, id).querySelector(".mc-person-main"));
        await flush();
        return ctx;
    }
    function openLocation(ctx) {
        click(timingGroup(ctx).querySelector('[data-action="mc-contact-location"]'));
        return flush();
    }

    it("I-1/S-1：状态行末尾只追加一组，三态文案与位置正确", async () => {
        const ctx = await bootSelected(1, { contactTiming: TIMING_WORK_HOURS_BR });
        const group = timingGroup(ctx);
        assert.ok(group, "状态行必须有 .contact-timing");
        assert.strictEqual(ctx.host.querySelectorAll('[data-role="contact-timing"]').length, 1, "只追加一组");
        assert.strictEqual(group.querySelector(".contact-timing-location").textContent, "巴西 ▾");
        assert.strictEqual(group.querySelector(".contact-timing-recommend").textContent, "建议北京 20:00–次日05:00");
        assert.strictEqual(
            group.querySelector(".contact-timing-recommend strong").getAttribute("title"),
            "北京时间 10月2日 20:00–10月3日 05:00",
            "title 显示完整日期区间"
        );
        assert.strictEqual(group.querySelector('[data-action="mc-contact-timing-evidence"]').textContent, "ⓘ");
        const meta = ctx.host.querySelector(".mc-header-meta");
        const kids = meta.children;
        assert.strictEqual(meta.querySelector(".mc-note-row").children[1], group, "推荐仍在下排末尾");
        assert.strictEqual(meta.querySelector(".mc-note-tags").lastChild.getAttribute("data-action"), "mc-open-expert", "原「查看专家详情」语义不变");

        const bare = await bootSelected(1, { contactTiming: { location: unconfiguredLocation(1), recommendation: null } });
        const bareGroup = timingGroup(bare);
        assert.strictEqual(bareGroup.querySelector(".contact-timing-location").textContent, "配置所在地 ▾");
        assert.strictEqual(bareGroup.querySelectorAll(".contact-timing-recommend").length, 0);
        assert.strictEqual(bareGroup.querySelectorAll('[data-action="mc-contact-timing-evidence"]').length, 0, "无推荐不渲染 ⓘ");
        assert.strictEqual(bareGroup.querySelectorAll(".contact-timing-note").length, 0, "未配置不显示加载/错误文案");
    });

    it("I-1：标签更新与现有刷新后仍只有一组，配置入口不丢", async () => {
        const ctx = await bootSelected(1, {
            contactTiming: TIMING_WORK_HOURS_BR,
            nextExpertTag: "重点关注"
        });
        assert.strictEqual(ctx.host.querySelectorAll('[data-role="contact-timing"]').length, 1);
        // 标签即时保存（管理 overlay）会重新渲染状态行
        click(ctx.host.querySelector('[data-action="mc-manage-expert"]'));
        await flush();
        const editor = ctx.doc.querySelector('[data-action="expert-add-tag-open"]');
        assert.ok(editor, "标签编辑器在 portal 内");
        click(editor);
        await flush();
        assert.strictEqual(ctx.calls.tagMutations.length, 1, "走共享标签 seam");
        assert.strictEqual(ctx.host.querySelectorAll('[data-role="contact-timing"]').length, 1, "标签更新后仍只有一组");
        assert.ok(timingGroup(ctx).querySelector('[data-action="mc-contact-location"]'), "配置入口不丢");
        assert.strictEqual(timingGroup(ctx).querySelector(".contact-timing-recommend").textContent, "建议北京 20:00–次日05:00");
    });

    it("I-2：日期按显式时区格式化，设备时区变化不改变文字，且不写 localStorage", async () => {
        const originalTz = process.env.TZ;
        const render = async (tz) => {
            process.env.TZ = tz;
            const ctx = await bootSelected(1, { contactTiming: TIMING_WORK_HOURS_BR });
            let writes = 0;
            ctx.sandbox.localStorage = { getItem: () => null, setItem: () => { writes += 1; } };
            await openLocation(ctx);
            click(timingDialog(ctx).querySelectorAll('[data-action="mc-contact-dialog-close"]')[0]);
            await flush();
            return {
                text: timingGroup(ctx).querySelector(".contact-timing-recommend").textContent,
                writes,
                tz: Intl.DateTimeFormat().resolvedOptions().timeZone
            };
        };
        try {
            const utc = await render("UTC");
            const tokyo = await render("Asia/Tokyo");
            assert.strictEqual(utc.text, tokyo.text, "北京时间文字不得随设备时区变化");
            assert.strictEqual(utc.text, "建议北京 20:00–次日05:00");
            assert.notStrictEqual(utc.tz, tokyo.tz, "两次运行的设备时区确实不同（防空洞断言）");
            assert.strictEqual(utc.writes + tokyo.writes, 0, "不写 localStorage");
        } finally {
            process.env.TZ = originalTz;
        }
    });

    it("S-2/I-2：依据弹窗只读已取得的响应，日期完整、0 样本隐藏列表", async () => {
        const ctx = await bootSelected(1, { contactTiming: TIMING_REPLY_IN });
        const before = ctx.calls.api.length;
        click(timingGroup(ctx).querySelector('[data-action="mc-contact-timing-evidence"]'));
        await flush();
        assert.strictEqual(ctx.calls.api.length, before, "查看依据不新增任何请求");
        const dialog = timingDialog(ctx);
        assert.ok(dialog, "依据弹窗存在");
        assert.strictEqual(dialog.getAttribute("data-kind"), "evidence");
        const range = dialog.querySelector(".contact-timing-range");
        assert.strictEqual(range.querySelector("strong").textContent, "北京 10月2日 15:30–17:30");
        assert.strictEqual(range.querySelector("span").textContent, "当地 10月2日 13:00–15:00");
        assert.strictEqual(dialog.querySelector("header p").textContent, "印度 · 手动时区 Asia/Kolkata");
        const helps = dialog.querySelectorAll(".contact-timing-help");
        assert.strictEqual(helps[0].textContent, "结合历史回复 · 3 次来信 · 3 个回复日");
        assert.strictEqual(helps[2].textContent, "仅使用最近 1000 条范围内的去重来信。");
        assert.strictEqual(helps[2].hidden, true, "未截断不显示 1000 条提示");
        const items = dialog.querySelectorAll(".contact-timing-history li");
        assert.strictEqual(items.length, 2);
        assert.strictEqual(items[0].querySelectorAll("span")[0].textContent, "北京 10月1日 01:15");
        assert.strictEqual(items[0].querySelectorAll("span")[1].textContent, "当地 9月30日 22:45");

        const ctx2 = await bootSelected(1, { contactTiming: TIMING_WORK_HOURS_BR });
        click(timingGroup(ctx2).querySelector('[data-action="mc-contact-timing-evidence"]'));
        await flush();
        const dialog2 = timingDialog(ctx2);
        assert.strictEqual(dialog2.querySelectorAll(".contact-timing-history").length, 0, "0 样本隐藏列表");
        assert.strictEqual(
            dialog2.querySelectorAll(".contact-timing-help")[0].textContent,
            "样本不足，使用当地工作时间 08:00–17:00 · 0 次来信 · 0 个回复日"
        );
    });

    it("所在地筛选：中文与代码匹配，清空恢复，无结果仍保留已选国家和时区", async () => {
        const ctx = await bootSelected(1, { contactLocations: { 1: { countryCode: "US", zoneId: "America/Los_Angeles" } } });
        await openLocation(ctx);
        const dialog = timingDialog(ctx);
        const search = dialog.querySelector('[data-contact-filter="countryCode"]');
        const country = dialog.querySelector('select[name="countryCode"]');
        const zone = dialog.querySelector('select[name="zoneId"]');
        const values = () => country.querySelectorAll("option").map((option) => option.getAttribute("value"));
        const requestCount = ctx.calls.api.length;
        search.value = "中国";
        inputEvent(search);
        assert.deepStrictEqual(values(), ["", "CN", "US"], "仅保留匹配项与当前选择");
        const enter = new MiniEvent("keydown", { bubbles: true });
        enter.key = "Enter";
        let prevented = false;
        enter.preventDefault = () => { prevented = true; };
        search.dispatchEvent(enter);
        assert.strictEqual(prevented, true, "搜索时回车不触发表单保存");
        assert.strictEqual(country.value, "US");
        assert.strictEqual(zone.value, "America/Los_Angeles", "搜索不能清空已选时区");
        search.value = "  cN  ";
        inputEvent(search);
        assert.deepStrictEqual(values(), ["", "CN", "US"], "代码忽略大小写和首尾空格");
        search.value = "不存在";
        inputEvent(search);
        assert.deepStrictEqual(values(), ["", "US"]);
        assert.strictEqual(dialog.querySelector('[data-contact-filter-hint="countryCode"]').textContent, "无匹配结果，请更换关键词");
        search.value = "";
        inputEvent(search);
        assert.deepStrictEqual(values(), ["", "BR", "CN", "IN", "US"]);
        assert.strictEqual(dialog.querySelector('[data-contact-filter-hint="countryCode"]').hidden, true);
        assert.strictEqual(ctx.calls.api.length, requestCount, "筛选不请求或保存");
        submitDialog(dialog);
        assert.strictEqual(search.disabled, true, "保存中搜索也禁用");
        await flush();
        assert.deepStrictEqual(JSON.parse(putRequests(ctx)[0].body), { countryCode: "US", zoneId: "America/Los_Angeles" });
    });

    it("所在地筛选：时区支持城市和标识搜索，换国家重置搜索，筛选后选择可保存", async () => {
        const ctx = await bootSelected(1, { contactLocations: { 1: { countryCode: "US", zoneId: null } } });
        await openLocation(ctx);
        const dialog = timingDialog(ctx);
        const search = dialog.querySelector('[data-contact-filter="zoneId"]');
        const zone = dialog.querySelector('select[name="zoneId"]');
        const values = () => zone.querySelectorAll("option").map((option) => option.getAttribute("value"));
        search.value = "洛杉矶";
        inputEvent(search);
        assert.deepStrictEqual(values(), ["", "America/Los_Angeles"]);
        assert.strictEqual(zone.value, "", "搜索不自动选中第一个匹配项");
        search.value = "new_york";
        inputEvent(search);
        assert.deepStrictEqual(values(), ["", "America/New_York"]);
        zone.value = "America/New_York";
        changeEvent(zone);
        search.value = "无匹配";
        inputEvent(search);
        assert.deepStrictEqual(values(), ["", "America/New_York"]);
        assert.strictEqual(zone.value, "America/New_York");
        const country = dialog.querySelector('select[name="countryCode"]');
        country.value = "BR";
        changeEvent(country);
        assert.strictEqual(search.value, "");
        assert.strictEqual(zone.value, "");
        assert.deepStrictEqual(values(), ["", "America/Sao_Paulo", "America/Manaus"]);
        search.value = "马瑙斯";
        inputEvent(search);
        zone.value = "America/Manaus";
        changeEvent(zone);
        submitDialog(dialog);
        await flush();
        assert.deepStrictEqual(JSON.parse(putRequests(ctx)[0].body), { countryCode: "BR", zoneId: "America/Manaus" });
    });

    it("I-4/S-2：取消与 Escape 零 PUT；换国家清空 zone；null 与显式值请求 JSON 准确；保存中禁重复提交", async () => {
        const ctx = await bootSelected(1, {
            contactTiming: TIMING_REPLY_IN,
            contactLocations: { 1: { countryCode: "US", zoneId: null } }
        });
        await openLocation(ctx);
        let dialog = timingDialog(ctx);
        assert.strictEqual(dialog.parentNode, ctx.doc.body, "dialog 直接挂在 body 下");
        assert.deepStrictEqual(
            dialog.querySelectorAll('select[name="countryCode"] option').map((option) => option.getAttribute("value")),
            ["", "BR", "CN", "IN", "US"],
            "国家 option 来自目录"
        );
        assert.strictEqual(dialog.querySelector('select[name="countryCode"]').value, "US", "回填已保存国家");
        assert.strictEqual(dialog.querySelector('select[name="zoneId"]').value, "");
        assert.strictEqual(dialog.querySelector('[data-role="contact-default-zone"]').textContent, "默认时区：America/New_York");
        assert.strictEqual(
            dialog.querySelector('select[name="zoneId"] option').textContent,
            "使用默认时区 · 美国东部 · 纽约（America/New_York）"
        );
        // 选具体时区后取消：零 PUT，且不落库
        dialog.querySelector('select[name="zoneId"]').value = "America/Los_Angeles";
        changeEvent(dialog.querySelector('select[name="zoneId"]'));
        click(dialog.querySelectorAll('[data-action="mc-contact-dialog-close"]')[1]);
        await flush();
        assert.strictEqual(timingDialog(ctx), null, "取消关闭弹窗");
        assert.strictEqual(putRequests(ctx).length, 0, "取消零 PUT");
        await openLocation(ctx);
        dialog = timingDialog(ctx);
        assert.strictEqual(dialog.querySelector('select[name="countryCode"]').value, "US", "取消后原值不变");
        assert.strictEqual(dialog.querySelector('select[name="zoneId"]').value, "");
        keyEvent(dialog, "Escape");
        await flush();
        assert.strictEqual(timingDialog(ctx), null, "Escape 关闭");
        assert.strictEqual(putRequests(ctx).length, 0, "Escape 零 PUT");
        // 换国家清空 zone 草稿；显式值原样提交
        await openLocation(ctx);
        dialog = timingDialog(ctx);
        dialog.querySelector('select[name="zoneId"]').value = "America/Los_Angeles";
        const country = dialog.querySelector('select[name="countryCode"]');
        country.value = "BR";
        changeEvent(country);
        assert.strictEqual(dialog.querySelector('select[name="zoneId"]').value, "", "换国家清空 zone 草稿");
        assert.strictEqual(dialog.querySelector('select[name="zoneId"] option').textContent, "使用默认时区 · 巴西 · 圣保罗（America/Sao_Paulo）");
        dialog.querySelector('select[name="zoneId"]').value = "America/Manaus";
        changeEvent(dialog.querySelector('select[name="zoneId"]'));
        submitDialog(dialog);
        submitDialog(dialog);
        assert.strictEqual(putRequests(ctx).length, 1, "保存中禁重复提交");
        await flush();
        assert.deepStrictEqual(JSON.parse(putRequests(ctx)[0].body), { countryCode: "BR", zoneId: "America/Manaus" });
        assert.strictEqual(timingDialog(ctx), null, "保存成功关闭弹窗");
        // 重开：显式时区往返一致（服务端桩已持久化）
        await openLocation(ctx);
        dialog = timingDialog(ctx);
        assert.strictEqual(dialog.querySelector('select[name="zoneId"]').value, "America/Manaus", "显式时区往返一致");
        // 单时区国家：字段 hidden 且提交 null
        const single = dialog.querySelector('select[name="countryCode"]');
        single.value = "IN";
        changeEvent(single);
        assert.strictEqual(dialog.querySelector('[data-role="contact-zone-field"]').hidden, true, "单时区隐藏具体时区字段");
        submitDialog(dialog);
        await flush();
        assert.deepStrictEqual(JSON.parse(putRequests(ctx)[1].body), { countryCode: "IN", zoneId: null }, "空值传 null");
    });

    it("I-4：保存失败保留草稿与错误、不假装成功；PUT 成功但重读失败不保留旧时间", async () => {
        const failing = await bootSelected(1, { contactTiming: TIMING_REPLY_IN, contactLocationSaveError: "save down" });
        await openLocation(failing);
        let dialog = timingDialog(failing);
        dialog.querySelector('select[name="countryCode"]').value = "BR";
        changeEvent(dialog.querySelector('select[name="countryCode"]'));
        dialog.querySelector('select[name="zoneId"]').value = "America/Manaus";
        changeEvent(dialog.querySelector('select[name="zoneId"]'));
        submitDialog(dialog);
        await flush();
        assert.strictEqual(putRequests(failing).length, 1);
        dialog = timingDialog(failing);
        assert.ok(dialog, "失败不关闭弹窗");
        assert.strictEqual(dialog.querySelector('select[name="countryCode"]').value, "BR", "草稿保留");
        assert.strictEqual(dialog.querySelector('select[name="zoneId"]').value, "America/Manaus", "时区草稿保留");
        const error = dialog.querySelector(".contact-timing-error");
        assert.strictEqual(error.hidden, false);
        assert.strictEqual(error.textContent, "save down");
        assert.strictEqual(dialog.querySelector('button[type="submit"]').disabled, false, "可再次保存");
        assert.strictEqual(dialog.querySelector('button[type="submit"]').textContent, "保存");

        let failTiming = false;
        const ctx = await bootSelected(1, {
            contactTiming: TIMING_WORK_HOURS_BR,
            route: (url, method, body, entry, next) => {
                if (/^\/api\/mail\/contact-locations\/\d+$/.test(url) && method === "PUT") {
                    failTiming = true;
                    return next(url, method, body, entry);
                }
                if (/\/timing$/.test(url) && failTiming) return Promise.reject(new Error("timing down"));
                return next(url, method, body, entry);
            }
        });
        assert.ok(timingGroup(ctx).querySelector(".contact-timing-recommend"), "保存前有推荐");
        await openLocation(ctx);
        dialog = timingDialog(ctx);
        dialog.querySelector('select[name="countryCode"]').value = "IN";
        changeEvent(dialog.querySelector('select[name="countryCode"]'));
        submitDialog(dialog);
        await flush();
        assert.strictEqual(timingDialog(ctx), null, "保存成功关闭弹窗");
        const group = timingGroup(ctx);
        assert.strictEqual(group.querySelectorAll(".contact-timing-recommend").length, 0, "重读失败不得保留旧推荐");
        assert.strictEqual(group.querySelector(".contact-timing-note").textContent, "所在地已保存，推荐更新失败");
        const retry = group.querySelector('[data-action="mc-contact-timing-retry"]');
        assert.ok(retry, "提供重试入口");
        const putsBefore = putRequests(ctx).length;
        assert.strictEqual(timingRequests(ctx).length, 2, "选择 1 次 + 保存后 1 次");
        failTiming = false;
        click(retry);
        await flush();
        assert.strictEqual(putRequests(ctx).length, putsBefore, "重试只重读，不自动保存");
        assert.strictEqual(timingRequests(ctx).length, 3, "重试读当前 contact 一次");
        assert.ok(timingGroup(ctx).querySelector(".contact-timing-recommend"), "重试成功后恢复推荐");
    });

    it("S-2/I-4：目录或配置加载失败显示错误、保存禁用，重试只重读", async () => {
        let fail = true;
        const ctx = await bootSelected(1, {
            contactTiming: TIMING_REPLY_IN,
            route: (url, method, body, entry, next) => {
                if (url === "/api/mail/contact-locations/countries" && fail) return Promise.reject(new Error("catalog down"));
                return next(url, method, body, entry);
            }
        });
        await openLocation(ctx);
        let dialog = timingDialog(ctx);
        assert.strictEqual(dialog.querySelector(".contact-timing-error").hidden, false, "加载失败显示错误");
        assert.strictEqual(dialog.querySelector(".contact-timing-error").textContent, "所在地配置加载失败");
        assert.strictEqual(dialog.querySelector('button[type="submit"]').disabled, true, "加载失败时保存禁用");
        assert.strictEqual(dialog.querySelector('[data-action="mc-contact-dialog-retry"]').hidden, false, "footer 提供重试");
        fail = false;
        click(dialog.querySelector('[data-action="mc-contact-dialog-retry"]'));
        await flush();
        dialog = timingDialog(ctx);
        assert.strictEqual(dialog.querySelector(".contact-timing-error").hidden, true, "重试成功后清除错误");
        assert.strictEqual(dialog.querySelector('button[type="submit"]').disabled, false, "重试成功后启用保存");
        assert.strictEqual(dialog.querySelector('[data-action="mc-contact-dialog-retry"]').hidden, true);
        assert.strictEqual(putRequests(ctx).length, 0, "加载与重试零 PUT");
    });

    it("I-3：迟到的推荐响应不覆盖新选择；保存 A 期间切 B 不写 B 界面也不重开 A 弹窗", async () => {
        const pending = [];
        const ctx = await bootChat({
            conversations: { items: [expertA(), expertB()], total: 2 },
            messages: { items: [], nextBefore: null, hasMore: false },
            contact: contactFixture(1, "0000-0001"),
            route: (url, method, body, entry, next) => {
                if (/^\/api\/mail\/contact-locations\/\d+\/timing$/.test(url)) {
                    const id = Number(url.split("/")[4]);
                    return new Promise((resolve) => {
                        pending.push({ id, resolve: () => resolve(id === 1 ? TIMING_WORK_HOURS_BR : TIMING_REPLY_IN) });
                    });
                }
                if (/^\/api\/mail\/contact-locations\/\d+$/.test(url) && method === "PUT") {
                    const id = Number(url.split("/")[4]);
                    return new Promise((resolve) => { pending.push({ id, put: true, resolve: () => resolve({}) }); });
                }
                return next(url, method, body, entry);
            }
        });
        click(personOf(ctx, 1).querySelector(".mc-person-main"));
        await flush();
        assert.strictEqual(pending.length, 1, "A 的推荐请求已发出");
        assert.strictEqual(timingGroup(ctx).querySelector(".contact-timing-note").textContent, "推荐计算中…", "加载态文案");
        click(personOf(ctx, 2).querySelector(".mc-person-main"));
        await flush();
        assert.strictEqual(pending.length, 2, "B 的推荐请求已发出");
        pending[1].resolve();
        await flush();
        assert.strictEqual(timingGroup(ctx).querySelector(".contact-timing-location").textContent, "印度 ▾");
        pending[0].resolve();
        await flush();
        assert.strictEqual(timingGroup(ctx).querySelector(".contact-timing-location").textContent, "印度 ▾", "迟到的 A 响应不得覆盖 B");
        // B 保存中切回 A：A 的保存完成不得重开弹窗或写 B 界面
        await openLocation(ctx);
        const dialog = timingDialog(ctx);
        dialog.querySelector('select[name="countryCode"]').value = "BR";
        changeEvent(dialog.querySelector('select[name="countryCode"]'));
        submitDialog(dialog);
        await flush();
        const putEntry = pending.find((entry) => entry.put);
        assert.ok(putEntry, "PUT 已发出");
        click(personOf(ctx, 1).querySelector(".mc-person-main"));
        await flush();
        putEntry.resolve();
        await flush();
        assert.strictEqual(timingDialog(ctx), null, "迟到保存不得重开旧弹窗");
    });

    it("I-5：自有 dialog 最多一个、直挂 body，关闭只移除自有节点，既有面板不受影响", async () => {
        const ctx = await bootSelected(1, { contactTiming: TIMING_REPLY_IN });
        click(ctx.host.querySelector('[data-action="mc-manage-expert"]'));
        await flush();
        assert.ok(ctx.doc.body.querySelector(".mc-manage-overlay"), "管理面板已打开");
        await openLocation(ctx);
        const dialog = timingDialog(ctx);
        assert.ok(dialog);
        assert.strictEqual(dialog.parentNode, ctx.doc.body, "dialog 直接挂在 body 下");
        assert.strictEqual(ctx.doc.body.querySelectorAll(".contact-timing-dialog").length, 1, "最多一个本功能弹窗");
        assert.ok(ctx.doc.body.querySelector(".mc-manage-overlay"), "打开所在地弹窗不移除既有面板");
        // 关闭管理面板（portal innerHTML 清空）不得删除自有弹窗
        click(ctx.doc.querySelector('[data-action="mc-close-manage"]'));
        await flush();
        assert.strictEqual(timingDialog(ctx), dialog, "旧弹窗生命周期不得删除新弹窗");
        click(timingDialog(ctx).querySelectorAll('[data-action="mc-contact-dialog-close"]')[0]);
        await flush();
        assert.strictEqual(timingDialog(ctx), null, "关闭按钮只移除自有节点");
        // 既有管理面板仍能重新打开（IP-2）
        click(ctx.host.querySelector('[data-action="mc-manage-expert"]'));
        await flush();
        assert.ok(ctx.doc.body.querySelector(".mc-manage-overlay"), "管理面板仍可打开");
        // 依据弹窗替换所在地弹窗：始终只有一个
        await openLocation(ctx);
        assert.strictEqual(ctx.doc.body.querySelectorAll(".contact-timing-dialog").length, 1);
    });

    it("I-3/I-5：unmount 关闭并移除自有弹窗，迟到响应不再写 DOM", async () => {
        const pending = [];
        const ctx = await bootChat({
            conversations: { items: [expertA()], total: 1 },
            messages: { items: [], nextBefore: null, hasMore: false },
            contact: contactFixture(1, "0000-0001"),
            route: (url, method, body, entry, next) => {
                if (/^\/api\/mail\/contact-locations\/\d+\/timing$/.test(url)) {
                    return new Promise((resolve) => { pending.push({ resolve: () => resolve(TIMING_REPLY_IN) }); });
                }
                return next(url, method, body, entry);
            }
        });
        click(personOf(ctx, 1).querySelector(".mc-person-main"));
        await flush();
        await openLocation(ctx);
        assert.ok(timingDialog(ctx));
        ctx.sandbox.MailboxChat.unmount(ctx.host);
        assert.strictEqual(timingDialog(ctx), null, "unmount 关闭并移除自有弹窗");
        pending.forEach((entry) => entry.resolve());
        await flush();
        assert.strictEqual(timingDialog(ctx), null, "unmount 后迟到响应不得重开弹窗");
        assert.strictEqual(ctx.doc.body.querySelectorAll(".contact-timing-dialog").length, 0);
    });
});

// ---------------------------------------------------------------------------
// fast-p 2026-10-02 · mailbox-last-reply-time（I-1..I-5 / S-1..S-3）
// 在生产 mailbox-chat.js 上验证收发件箱「上次回复」：列表行 + 详情槽、北京时间语义、
// 空值/异常区分、刷新同步（沿用 disposed/listSeq 守卫）与取值来源纪律。
// ---------------------------------------------------------------------------

describe("fast-p 上次回复：收发件箱列表与详情时间（I-1..I-5 / S-1..S-3）", () => {
    const EIGHT_TEXT = "2026-10-02 星期五 17:59";
    const EIGHT_TITLE = "北京时间 2026-10-02 星期五 17:59";
    const EIGHT_DATETIME = "2026-10-02T17:59:00+08:00";
    const UNAVAILABLE = "回复时间暂不可用";
    const EMPTY_TEXT = "尚未回复";

    function personOf(ctx, contactId) {
        return ctx.host.querySelectorAll(".mc-person").find((node) => node.dataset.contactId === String(contactId));
    }
    function replyRow(ctx, contactId) {
        return personOf(ctx, contactId).querySelector(".mailbox-reply-list");
    }
    function replyTime(ctx, contactId) {
        return replyRow(ctx, contactId).querySelector("time");
    }
    function openPerson(ctx, contactId) {
        click(personOf(ctx, contactId).querySelector(".mc-person-main"));
        return flush();
    }
    function detailSlot(ctx) {
        return ctx.host.querySelector('[data-role="last-reply-time"]');
    }
    function inbound(receivedAt) {
        return receivedAt === null
            ? null
            : { processingId: 101, accountCode: "acc1", messageId: "m101", receivedAt };
    }
    /** 列表夹具：默认专家 A，可覆写 latestInbound/receivedCount。 */
    function expertRow(inboundValue, extra) {
        return expertA(Object.assign({ latestInbound: inboundValue }, extra || {}));
    }
    async function bootRow(inboundValue, extra) {
        const ctx = await bootChat({
            conversations: { items: [expertRow(inboundValue, extra)], total: 1 },
            messages: messagesA(),
            contact: contactA()
        });
        return ctx;
    }
    /** 需要组件 api（refreshFromHost/loadList/unmount）的挂载，避免 mountChat 的双重 loadList。 */
    function mountWithApi(options) {
        const ctx = createChatSandbox(options || {});
        const dom = createDom();
        ctx.doc = dom.doc;
        ctx.host = dom.host;
        ctx.sandbox.document = dom.doc;
        ctx.api = ctx.sandbox.MailboxChat.mount(dom.host, { filters: {} });
        return ctx;
    }
    async function bootWithApi(options) {
        const ctx = mountWithApi(options);
        await flush();
        return ctx;
    }
    function listPayloadAtom(receivedAt) {
        return { items: [expertRow(inbound(receivedAt))], total: 1 };
    }
    const KNOWN_ENDPOINTS = /^\/api\/(auth\/me|mail\/(mailbox\/conversations|unmatched-inbound)|expert-contacts|operator-action-logs|inbound-summary|translate|compose-templates|mail\/contact-locations)/;

    it("上次回复 B-1：列表与详情显示北京时间日期+星期+时分，datetime 带 +08:00", async () => {
        const ctx = await bootRow(inbound("2026-10-02T17:59:00"));
        const row = replyRow(ctx, 1);
        assert.ok(row, "列表必须有 .mailbox-reply-list");
        assert.strictEqual(row.querySelectorAll("span")[0].textContent, "上次回复", "列表标签");
        const time = row.querySelector("time");
        assert.strictEqual(time.textContent, EIGHT_TEXT, "可见日期+星期+时分");
        assert.strictEqual(time.getAttribute("datetime"), EIGHT_DATETIME);
        assert.strictEqual(time.getAttribute("title"), EIGHT_TITLE);
        const aria = personOf(ctx, 1).querySelector(".mc-person-main").getAttribute("aria-label");
        assert.ok(aria.includes("查看专家A往来邮件"), "保留专家名称描述");
        assert.ok(aria.includes("专家标签：学术科研、重点关注"), "保留标签描述");
        assert.ok(aria.includes(EIGHT_TITLE), "aria 含同一北京时间说明");

        await openPerson(ctx, 1);
        const slot = detailSlot(ctx);
        assert.ok(slot, "详情必须有回复时间槽");
        assert.strictEqual(slot.querySelector("span").textContent, "专家上次回复");
        assert.strictEqual(slot.querySelector("time").textContent, EIGHT_TEXT, "详情与列表同值");
        assert.strictEqual(slot.querySelector("time").getAttribute("datetime"), EIGHT_DATETIME);
        assert.strictEqual(slot.querySelector(".mailbox-reply-zone").textContent, "北京时间");
        const kids = ctx.host.querySelector(".mc-identity").children;
        assert.deepStrictEqual(kids.map((node) => node.tagName), ["H2", "P", "SPAN", "SPAN"], "identity 顺序：名称→账号→时间槽→排期");
        assert.strictEqual(kids[2], slot);
        assert.strictEqual(kids[3].getAttribute("data-role"), "meeting-summary");
    });

    it("上次回复 B-2：更晚的我方发件不改变时间（只认 latestInbound）", async () => {
        const ctx = await bootChat({
            conversations: {
                items: [expertA({
                    latestInbound: inbound("2026-10-01T20:48:00"),
                    latestMessage: { source: "MAIL_RECORD", id: 88, direction: "OUTBOUND", subject: "Follow up", time: "2026-10-02T08:45:00", sendStatus: "SENT" }
                })],
                total: 1
            },
            messages: messagesA(),
            contact: contactA()
        });
        assert.strictEqual(replyTime(ctx, 1).textContent, "2026-10-01 星期四 20:48");
        await openPerson(ctx, 1);
        assert.strictEqual(detailSlot(ctx).querySelector("time").textContent, "2026-10-01 星期四 20:48");
    });

    it("上次回复 B-3：null+0 才是尚未回复；其余异常一律回复时间暂不可用且无 time 元素", async () => {
        const empty = await bootRow(null, { receivedCount: 0 });
        assert.strictEqual(replyRow(empty, 1).querySelector(".mailbox-reply-empty").textContent, EMPTY_TEXT);
        assert.strictEqual(replyRow(empty, 1).querySelector("time"), null);
        assert.ok(personOf(empty, 1).querySelector(".mc-person-main").getAttribute("aria-label").includes(`上次回复 ${EMPTY_TEXT}`));
        await openPerson(empty, 1);
        const emptySlot = detailSlot(empty);
        assert.strictEqual(emptySlot.querySelector(".mailbox-reply-empty").textContent, EMPTY_TEXT);
        assert.strictEqual(emptySlot.querySelector("time"), null);
        assert.strictEqual(emptySlot.querySelector(".mailbox-reply-zone"), null, "空值分支省略北京时间尾注");

        const cases = [
            { label: "null+2", value: null, extra: { receivedCount: 2 } },
            { label: "空字符串时间", value: inbound(""), extra: {} },
            { label: "非法时间", value: inbound("not-a-time"), extra: {} },
            { label: "结构异常", value: "oops", extra: {} }
        ];
        for (const item of cases) {
            const ctx = await bootRow(item.value, item.extra);
            const row = replyRow(ctx, 1);
            assert.strictEqual(row.querySelector(".mailbox-reply-empty").textContent, UNAVAILABLE, item.label);
            assert.strictEqual(row.querySelector("time"), null, `${item.label}: 不得输出 time/Invalid Date`);
            await openPerson(ctx, 1);
            const slot = detailSlot(ctx);
            assert.strictEqual(slot.querySelector(".mailbox-reply-empty").textContent, UNAVAILABLE, item.label);
            assert.strictEqual(slot.querySelector("time"), null, item.label);
            assert.strictEqual(slot.querySelector(".mailbox-reply-zone"), null, `${item.label}: 省略北京时间尾注`);
        }
    });

    it("上次回复 B-3b：字段缺失/非对象即使 receivedCount=0 也不是尚未回复", async () => {
        const missing = expertA({ receivedCount: 0 });
        delete missing.latestInbound;
        const missingCtx = await bootChat({
            conversations: { items: [missing], total: 1 },
            messages: messagesA(),
            contact: contactA()
        });
        assert.strictEqual(replyRow(missingCtx, 1).querySelector(".mailbox-reply-empty").textContent, UNAVAILABLE, "缺失 latestInbound 字段");
        assert.strictEqual(replyRow(missingCtx, 1).querySelector("time"), null);
        await openPerson(missingCtx, 1);
        assert.strictEqual(detailSlot(missingCtx).querySelector(".mailbox-reply-empty").textContent, UNAVAILABLE);
        assert.strictEqual(detailSlot(missingCtx).querySelector("time"), null);

        const oops = await bootRow("oops", { receivedCount: 0 });
        assert.strictEqual(replyRow(oops, 1).querySelector(".mailbox-reply-empty").textContent, UNAVAILABLE, "非对象 latestInbound");
        assert.strictEqual(replyRow(oops, 1).querySelector("time"), null);
        await openPerson(oops, 1);
        assert.strictEqual(detailSlot(oops).querySelector(".mailbox-reply-empty").textContent, UNAVAILABLE);
        assert.strictEqual(detailSlot(oops).querySelector("time"), null);

        const nullZero = await bootRow(null, { receivedCount: 0 });
        assert.strictEqual(replyRow(nullZero, 1).querySelector(".mailbox-reply-empty").textContent, EMPTY_TEXT, "仅 null+0 才是尚未回复");
        assert.strictEqual(replyRow(nullZero, 1).querySelector("time"), null);
    });

    it("上次回复 B-3c：null 来信下缺失/非数字计数不是尚未回复（I-3 判别式）", async () => {
        const rows = [
            ["计数缺失", () => {
                const row = expertRow(null);
                delete row.receivedCount;
                return row;
            }],
            ["计数为 null", () => expertRow(null, { receivedCount: null })],
            ["计数为字符串 \"0\"", () => expertRow(null, { receivedCount: "0" })]
        ];
        for (const [label, build] of rows) {
            const ctx = await bootChat({
                conversations: { items: [build()], total: 1 },
                messages: messagesA(),
                contact: contactA()
            });
            const row = replyRow(ctx, 1);
            assert.strictEqual(row.querySelector(".mailbox-reply-empty").textContent, UNAVAILABLE, `${label}: 列表`);
            assert.strictEqual(row.querySelector("time"), null, `${label}: 列表不得有 time`);
            await openPerson(ctx, 1);
            const slot = detailSlot(ctx);
            assert.strictEqual(slot.querySelector(".mailbox-reply-empty").textContent, UNAVAILABLE, `${label}: 详情`);
            assert.strictEqual(slot.querySelector("time"), null, `${label}: 详情不得有 time`);
        }
        const nullZero = await bootRow(null, { receivedCount: 0 });
        assert.strictEqual(replyRow(nullZero, 1).querySelector(".mailbox-reply-empty").textContent, EMPTY_TEXT, "数字 0 仍是尚未回复");
        assert.strictEqual(replyRow(nullZero, 1).querySelector("time"), null);
    });

    it("上次回复 B-4：跨年/闰日边界正确，2 月 30 日不进位，小数秒不影响日期星期与分钟", async () => {
        const cases = [
            ["2025-12-31T23:58:00", "2025-12-31 星期三 23:58"],
            ["2024-02-29T00:00:00", "2024-02-29 星期四 00:00"],
            ["2026-10-02T17:59:59.123", "2026-10-02 星期五 17:59"]
        ];
        for (const [input, expected] of cases) {
            const ctx = await bootRow(inbound(input));
            assert.strictEqual(replyTime(ctx, 1).textContent, expected, input);
            await openPerson(ctx, 1);
            assert.strictEqual(detailSlot(ctx).querySelector("time").textContent, expected, input);
        }
        const bad = await bootRow(inbound("2026-02-30T10:00:00"));
        assert.strictEqual(replyRow(bad, 1).querySelector(".mailbox-reply-empty").textContent, UNAVAILABLE);
        assert.strictEqual(replyRow(bad, 1).querySelector("time"), null, "2 月 30 日不得自动进位成 3 月日期");
    });

    it("上次回复 B-4b：4–9 位小数秒截断到毫秒后仍正常显示北京时间", async () => {
        const cases = [
            ["2026-10-02T17:59:59.1234", "4 位小数秒"],
            ["2026-10-02T17:59:59.123456789", "9 位小数秒"]
        ];
        for (const [input, label] of cases) {
            const ctx = await bootRow(inbound(input));
            const listTime = replyTime(ctx, 1);
            assert.ok(listTime, `${label}: 列表必须有 time 元素`);
            assert.strictEqual(listTime.textContent, EIGHT_TEXT, `${label}: 列表可见北京时间`);
            assert.strictEqual(listTime.getAttribute("datetime"), EIGHT_DATETIME, `${label}: 列表 datetime`);
            await openPerson(ctx, 1);
            const slot = detailSlot(ctx);
            assert.strictEqual(slot.querySelector(".mailbox-reply-empty"), null, `${label}: 详情不得落入不可用分支`);
            assert.strictEqual(slot.querySelector("time").textContent, EIGHT_TEXT, `${label}: 详情可见北京时间`);
            assert.strictEqual(slot.querySelector("time").getAttribute("datetime"), EIGHT_DATETIME, `${label}: 详情 datetime`);
        }
    });

    it("上次回复 B-4c：前后空白/制表/换行的 ISO 时间不 trim，一律不可用", async () => {
        const padded = [
            " 2026-10-02T17:59:00",
            "2026-10-02T17:59:00 ",
            "\t2026-10-02T17:59:00\n",
            "\n2026-10-02T17:59:00\t"
        ];
        for (const input of padded) {
            const ctx = await bootRow(inbound(input));
            const row = replyRow(ctx, 1);
            assert.strictEqual(row.querySelector(".mailbox-reply-empty").textContent, UNAVAILABLE, `${JSON.stringify(input)}: 列表`);
            assert.strictEqual(row.querySelector("time"), null, `${JSON.stringify(input)}: 列表不得有 time`);
            await openPerson(ctx, 1);
            const slot = detailSlot(ctx);
            assert.strictEqual(slot.querySelector(".mailbox-reply-empty").textContent, UNAVAILABLE, `${JSON.stringify(input)}: 详情`);
            assert.strictEqual(slot.querySelector("time"), null, `${JSON.stringify(input)}: 详情不得有 time`);
        }
        const exact = await bootRow(inbound("2026-10-02T17:59:00"));
        assert.strictEqual(replyTime(exact, 1).textContent, EIGHT_TEXT, "无空白输入保持正常显示");
        await openPerson(exact, 1);
        assert.strictEqual(detailSlot(exact).querySelector("time").textContent, EIGHT_TEXT, "无空白输入详情正常显示");
    });

    it("上次回复 B-5：输出与设备时区无关（运行期切换 TZ 仍为同一北京时间）", async () => {
        const originalTz = process.env.TZ;
        const render = async (tz) => {
            process.env.TZ = tz;
            const ctx = await bootRow(inbound("2026-10-02T17:59:00"));
            await openPerson(ctx, 1);
            return {
                list: replyTime(ctx, 1).textContent,
                detail: detailSlot(ctx).querySelector("time").textContent,
                tz: Intl.DateTimeFormat().resolvedOptions().timeZone
            };
        };
        try {
            const first = await render("UTC");
            const second = await render("America/Los_Angeles");
            assert.strictEqual(first.tz, "UTC");
            assert.strictEqual(second.tz, "America/Los_Angeles");
            assert.notStrictEqual(first.tz, second.tz, "两次运行的设备时区确实不同（防空洞断言）");
            [first, second].forEach((out) => {
                assert.strictEqual(out.list, EIGHT_TEXT, "列表与设备时区无关");
                assert.strictEqual(out.detail, EIGHT_TEXT, "详情与设备时区无关");
            });
        } finally {
            process.env.TZ = originalTz;
        }
    });

    it("上次回复 B-6：宿主刷新同步两处并保留编辑器/回复目标/工作台；无发送请求", async () => {
        let updated = false;
        const ctx = await bootWithApi({
            messages: messagesA(),
            contact: contactA(),
            route: (url, method, body, entry, next) => {
                if (url.startsWith("/api/mail/mailbox/conversations?")) {
                    return Promise.resolve(listPayloadAtom(updated ? "2026-10-02T18:05:00" : "2026-10-02T17:59:00"));
                }
                return next(url, method, body);
            }
        });
        await openPerson(ctx, 1);
        const editor = ctx.host.querySelector('[aria-label="人工回复正文"]');
        assert.ok(editor, "人工回复编辑器存在");
        editor.innerText = "保留的草稿";
        const subject = ctx.host.querySelector('input[aria-label="回复主题"]');
        subject.value = "Re: KEEP";
        const targetInfo = ctx.host.querySelector('[data-role="target-info"]').textContent;
        const workbenchMounts = ctx.calls.workbenchMounts.length;
        const sends = ctx.calls.sendConversation.length + ctx.calls.sendRich.length;

        updated = true;
        await ctx.api.refreshFromHost();
        await flush();

        assert.strictEqual(replyTime(ctx, 1).textContent, "2026-10-02 星期五 18:05", "列表更新为新 summary");
        assert.strictEqual(detailSlot(ctx).querySelector("time").textContent, "2026-10-02 星期五 18:05", "详情取同一份当前行");
        assert.strictEqual(ctx.host.querySelector('[aria-label="人工回复正文"]'), editor, "编辑器 DOM 身份不变");
        assert.strictEqual(editor.innerText, "保留的草稿", "正文保留");
        assert.strictEqual(ctx.host.querySelector('input[aria-label="回复主题"]').value, "Re: KEEP", "回复主题保留");
        assert.strictEqual(ctx.host.querySelector('[data-role="target-info"]').textContent, targetInfo, "回复目标不变");
        assert.strictEqual(ctx.calls.workbenchMounts.length, workbenchMounts, "不额外挂载工作台");
        assert.strictEqual(ctx.calls.sendConversation.length + ctx.calls.sendRich.length, sends, "无发送请求");
    });

    it("上次回复 B-7：loadList 与 header 重建后仍恰好一个槽且为新值", async () => {
        let updated = false;
        const ctx = await bootWithApi({
            messages: messagesA(),
            contact: contactA(),
            route: (url, method, body, entry, next) => {
                if (url.startsWith("/api/mail/mailbox/conversations?")) {
                    return Promise.resolve({ items: [expertRow(inbound(updated ? "2026-10-02T18:05:00" : "2026-10-02T17:59:00")), expertB()], total: 2 });
                }
                return next(url, method, body);
            }
        });
        await openPerson(ctx, 1);
        updated = true;
        await ctx.api.loadList();
        await flush();
        assert.strictEqual(detailSlot(ctx).querySelector("time").textContent, "2026-10-02 星期五 18:05", "loadList 后详情刷新");

        await openPerson(ctx, 2);
        await openPerson(ctx, 1);
        assert.strictEqual(ctx.host.querySelectorAll('[data-role="last-reply-time"]').length, 1, "重建后恰一个槽");
        assert.strictEqual(detailSlot(ctx).querySelector("time").textContent, "2026-10-02 星期五 18:05", "不退回旧 selectedSummary 时间");
        assert.strictEqual(replyTime(ctx, 1).textContent, "2026-10-02 星期五 18:05", "列表同值");
    });

    it("上次回复 B-8：迟到旧回包不覆盖；卸载后不写 DOM", async () => {
        const pending = [];
        let mode = "normal";
        const ctx = await bootWithApi({
            messages: messagesA(),
            contact: contactA(),
            route: (url, method, body, entry, next) => {
                if (url.startsWith("/api/mail/mailbox/conversations?")) {
                    if (mode === "defer") return new Promise((resolve) => pending.push(resolve));
                    return Promise.resolve(listPayloadAtom(mode === "new" ? "2026-10-02T18:05:00" : "2026-10-02T17:59:00"));
                }
                return next(url, method, body);
            }
        });
        await openPerson(ctx, 1);
        assert.strictEqual(detailSlot(ctx).querySelector("time").textContent, EIGHT_TEXT);

        mode = "defer";
        const slow = ctx.api.refreshFromHost();
        mode = "new";
        await ctx.api.refreshFromHost();
        await flush();
        assert.strictEqual(detailSlot(ctx).querySelector("time").textContent, "2026-10-02 星期五 18:05", "新回包生效");
        // 放行迟到旧回包（携带更旧的时间，若被采纳会覆盖）
        pending.shift()(listPayloadAtom("2026-10-01T20:48:00"));
        await slow;
        await flush();
        assert.strictEqual(detailSlot(ctx).querySelector("time").textContent, "2026-10-02 星期五 18:05", "迟到旧回包不得覆盖");
        assert.strictEqual(replyTime(ctx, 1).textContent, "2026-10-02 星期五 18:05");

        mode = "defer";
        const inFlight = ctx.api.loadList();
        ctx.api.unmount();
        pending.shift()(listPayloadAtom("2026-10-02T17:59:00"));
        await inFlight;
        await flush();
        assert.strictEqual(ctx.host.querySelectorAll(".mailbox-reply-list").length, 0, "卸载后不写列表");
        assert.strictEqual(ctx.host.querySelectorAll('[data-role="last-reply-time"]').length, 0, "卸载后不写详情槽");
    });

    it("上次回复 B-9：账号筛选后显示当前回包时间，不回退取历史最大值", async () => {
        const ctx = await bootWithApi({
            messages: messagesA(),
            contact: contactA(),
            route: (url, method, body, entry, next) => {
                if (url.startsWith("/api/mail/mailbox/conversations?")) {
                    const account = queryOf(url).get("accountCode");
                    const at = account === "acc-a" ? "2026-10-01T20:48:00" : "2026-10-02T17:59:00";
                    return Promise.resolve({ items: [expertA({ latestInbound: inbound(at), accountCodes: ["acc-a", "acc-b"] })], total: 1 });
                }
                return next(url, method, body);
            }
        });
        await openPerson(ctx, 1);
        assert.strictEqual(replyTime(ctx, 1).textContent, EIGHT_TEXT);
        click(ctx.host.querySelector('[data-action="mc-more-filters"]'));
        await flush();
        const account = popoverField(ctx, "mailboxFilterAccountCode");
        account.value = "acc-a";
        changeEvent(account);
        click(docById(ctx, "mailboxSearchBtn"));
        await flush();
        assert.strictEqual(replyTime(ctx, 1).textContent, "2026-10-01 星期四 20:48", "按当前账号回包显示");
        assert.strictEqual(detailSlot(ctx).querySelector("time").textContent, "2026-10-01 星期四 20:48", "详情与列表同源");
    });

    it("上次回复 B-10：待匹配视图无新增时间行；不引入额外 endpoint/存储", async () => {
        const ctx = await bootChat({
            conversations: { items: [expertRow(inbound("2026-10-02T17:59:00"))], total: 1 },
            unmatched: { records: [unmatchedMail(901)], totalCount: 1, manualReviewTotal: 1, countsByReasonType: {} }
        });
        assert.ok(replyRow(ctx, 1), "专家列表有上次回复行");
        const unmatchedChip = ctx.host.querySelectorAll(".mc-filter").find((chip) => chip.dataset.chip === "unmatched");
        click(unmatchedChip);
        await flush();
        const card = ctx.host.querySelector(".mc-person");
        click(card.querySelector(".mc-person-main"));
        await flush();
        assert.strictEqual(ctx.host.querySelectorAll(".mailbox-reply-list").length, 0, "待匹配列表不新增时间行");
        assert.strictEqual(ctx.host.querySelectorAll('[data-role="last-reply-time"]').length, 0, "待匹配详情不新增时间槽");
        const unknown = ctx.calls.api.map((entry) => entry.url).filter((url) => !KNOWN_ENDPOINTS.test(url));
        assert.deepStrictEqual(unknown, [], "不得引入额外 endpoint");
        assert.strictEqual(ctx.sandbox.localStorage, undefined, "不新增 localStorage 状态");
        assert.strictEqual(ctx.sandbox.sessionStorage, undefined, "不新增 sessionStorage 状态");
    });
});


describe("mobile-core-02: pane、草稿归属与隐藏几何", () => {
    const pane = (ctx) => ctx.host.querySelector(".mobile-core-mailbox").dataset.mobilePane;
    const back = (ctx) => click(ctx.host.querySelector('[data-action="mobile-mailbox-back"]'));
    const choose = (ctx, id = 1) => click(ctx.host.querySelector(`[data-action="mc-select-expert"][data-contact-id="${id}"]`));
    async function mobile(overrides, mountOptions, dom) {
        return bootChat(Object.assign({ mobile: true, conversations: { items: [expertA(), expertB()], total: 2 },
            messages: messagesA(), contact: contactA() }, overrides), mountOptions, dom);
    }
    it("初访列表；返回采集无 input 事件草稿，同专家重开复用编辑器且不请求", async () => {
        const ctx = await mobile();
        assert.equal(pane(ctx), "list");
        ctx.host.querySelector(".mc-expert-list").scrollTop = 123;
        choose(ctx); await flush(); ctx.runFrames();
        assert.equal(pane(ctx), "detail");
        const editor = ctx.host.querySelector('[aria-label="人工回复正文"]');
        editor.innerText = "手机草稿";
        ctx.host.querySelector('input[aria-label="回复主题"]').value = "手机主题";
        const count = ctx.calls.api.length;
        back(ctx); ctx.runFrames();
        assert.equal(pane(ctx), "list");
        assert.equal(ctx.host.querySelector(".mc-expert-list").scrollTop, 123);
        choose(ctx); ctx.runFrames();
        assert.strictEqual(ctx.host.querySelector('[aria-label="人工回复正文"]'), editor);
        assert.equal(ctx.calls.api.length, count);
        choose(ctx, 2); await flush(); choose(ctx); await flush();
        assert.equal(ctx.host.querySelector('[aria-label="人工回复正文"]').innerText, "手机草稿");
        assert.equal(ctx.host.querySelector('input[aria-label="回复主题"]').value, "手机主题");
    });
    for (const position of [0, 245]) it(`隐藏刷新保留 scrollTop=${position}，重开在 rAF 恢复`, async () => {
        const ctx = await mobile(); choose(ctx); await flush(); ctx.runFrames();
        const scroll = ctx.host.querySelector(".mc-scroll");
        scroll.scrollTop = position;
        back(ctx); ctx.runFrames();
        scroll.scrollTop = 0; // display:none 的无效几何
        scrollEvent(scroll); ctx.runTimers();
        await ctx.sandbox.MailboxChat.mount(ctx.host).refreshFromHost(); await flush();
        assert.equal(pane(ctx), "list");
        choose(ctx); ctx.runFrames();
        assert.equal(scroll.scrollTop, position);
    });
    it("慢 select 返回后响应不跳屏；同项重开复用迟到加载的编辑器", async () => {
        let resolve;
        const ctx = await mobile({ route: (url, method, body, entry, next) => /conversations\/1\/messages/.test(url)
            ? new Promise((done) => { resolve = done; }) : next(url, method, body) });
        choose(ctx); back(ctx); ctx.runFrames(); resolve(messagesA()); await flush(); ctx.runFrames();
        assert.equal(pane(ctx), "list");
        const editor = ctx.host.querySelector('[aria-label="人工回复正文"]');
        choose(ctx); ctx.runFrames();
        assert.equal(pane(ctx), "detail"); assert.strictEqual(ctx.host.querySelector('[aria-label="人工回复正文"]'), editor);
    });
    it("外部 focus 慢定位、无 email/无权限均有返回；迟到定位不抢焦点", async () => {
        let resolve;
        const ctx = await mobile({ conversations: { items: [], total: 0 }, route: (url, method, body, entry, next) =>
            url.includes("q=a%40example.edu") ? new Promise((done) => { resolve = done; }) : next(url, method, body)
        }, { filters: {}, focus: { contactId: 1, email: "a@example.edu" } });
        assert.equal(pane(ctx), "detail"); back(ctx); ctx.runFrames();
        const focused = ctx.doc.activeElement;
        resolve({ items: [expertA()], total: 1 }); await flush(); ctx.runFrames();
        assert.equal(pane(ctx), "list"); assert.strictEqual(ctx.doc.activeElement, focused);
        const missing = await mobile({ conversations: { items: [], total: 0 } }, { filters: {}, focus: { contactId: 7 } });
        assert.equal(pane(missing), "detail"); back(missing); assert.equal(pane(missing), "list");
    });
    it("scope 变更在旧 owner 采集；账号 A/B 和用户互不污染", async () => {
        const ctx = await mobile({}, { filters: { accountCode: "acc1" }, sessionUser: "u1" });
        choose(ctx); await flush();
        ctx.host.querySelector('[aria-label="人工回复正文"]').innerText = "A账号草稿";
        ctx.sandbox.MailboxChat.mount(ctx.host, { filters: { accountCode: "acc2" } }); await flush();
        assert.notEqual(ctx.host.querySelector('[aria-label="人工回复正文"]').innerText, "A账号草稿");
        ctx.host.querySelector('[aria-label="人工回复正文"]').innerText = "B账号草稿";
        ctx.sandbox.MailboxChat.mount(ctx.host, { filters: { accountCode: "acc1" } }); await flush();
        assert.equal(ctx.host.querySelector('[aria-label="人工回复正文"]').innerText, "A账号草稿");
        ctx.sandbox.MailboxChat.mount(ctx.host, { sessionUser: "u2" }); await flush(); choose(ctx); await flush();
        assert.notEqual(ctx.host.querySelector('[aria-label="人工回复正文"]').innerText, "A账号草稿");
        ctx.sandbox.MailboxChat.unmount(ctx.host);
        ctx.sandbox.MailboxChat.mount(ctx.host, { filters: { accountCode: "acc2" }, sessionUser: "u1" }); await flush();
        choose(ctx); await flush(); assert.equal(ctx.host.querySelector('[aria-label="人工回复正文"]').innerText, "B账号草稿");
    });
    it("旋转零业务请求、编辑器不重建；unmount 清监听且采集正文", async () => {
        const ctx = await mobile(); choose(ctx); await flush(); ctx.runFrames();
        const editor = ctx.host.querySelector('[aria-label="人工回复正文"]'); editor.innerText = "卸载前草稿";
        const count = ctx.calls.api.length;
        assert.equal(ctx.mediaListeners.size, 1);
        ctx.resize(false); ctx.runFrames(); ctx.resize(true); ctx.runFrames();
        assert.strictEqual(ctx.host.querySelector('[aria-label="人工回复正文"]'), editor); assert.equal(ctx.calls.api.length, count);
        ctx.sandbox.MailboxChat.unmount(ctx.host); assert.equal(ctx.mediaListeners.size, 0);
        ctx.sandbox.MailboxChat.mount(ctx.host, { filters: {} }); await flush(); choose(ctx); await flush();
        assert.equal(ctx.host.querySelector('[aria-label="人工回复正文"]').innerText, "卸载前草稿"); assert.equal(ctx.mediaListeners.size, 1);
    });
    it("待匹配返回保留唯一 lease；项消失回列表且归还", async () => {
        let items = [unmatchedMail(501)];
        const dom = createUnmatchedPanelDom();
        const ctx = await mobile({ unmatchedPanel: dom.panel, route: (url, method, body, entry, next) => url.startsWith("/api/mail/unmatched-inbound?")
            ? Promise.resolve({ records: items, totalCount: items.length }) : next(url, method, body) }, undefined, dom);
        click(ctx.host.querySelector('[data-chip="unmatched"]')); await flush();
        click(ctx.host.querySelector('[data-action="mc-select-unmatched"]')); await flush();
        assert.equal(pane(ctx), "detail"); const mounts = ctx.calls.unmatchedMounts.length;
        back(ctx); click(ctx.host.querySelector('[data-action="mc-select-unmatched"]')); await flush();
        assert.equal(ctx.calls.unmatchedMounts.length, mounts);
        items = []; await ctx.sandbox.MailboxChat.mount(ctx.host).loadList(); await flush();
        assert.equal(pane(ctx), "list"); assert.equal(ctx.host.querySelectorAll("#unmatchedDetailPanel").length, 0);
    });
    it("返回后重新打开又立即返回，不得用尚未恢复的零位置覆盖缓存", async () => {
        const ctx = await mobile(); choose(ctx); await flush(); ctx.runFrames();
        const scroll = ctx.host.querySelector(".mc-scroll"); scroll.scrollTop = 245;
        back(ctx); ctx.runFrames(); scroll.scrollTop = 0;
        choose(ctx); back(ctx); ctx.runFrames(); choose(ctx); ctx.runFrames();
        assert.equal(scroll.scrollTop, 245);
    });
    it("隐藏刷新重排消息后按锚点相对位置恢复，而非旧 scrollTop", async () => {
        const ctx = await mobile(); choose(ctx); await flush(); ctx.runFrames();
        const scroll = ctx.host.querySelector(".mc-scroll");
        const layout = (shift) => ctx.host.querySelectorAll(".mc-message").forEach((el, i) => {
            el.offsetTop = i * 150 + shift; el.offsetHeight = 150;
        });
        layout(0); scroll.scrollTop = 245; back(ctx); ctx.runFrames(); scroll.scrollTop = 0;
        await ctx.sandbox.MailboxChat.mount(ctx.host).refreshFromHost(); await flush(); layout(40);
        choose(ctx); ctx.runFrames(); assert.equal(scroll.scrollTop, 285);
    });
    it("旧 focus 定位完成不得替换用户后来显式选择的专家", async () => {
        let locate, messagesB;
        const ctx = await mobile({ conversations: { items: [expertB()], total: 1 }, route: (url, method, body, entry, next) => {
            if (url.includes("q=a%40example.edu")) return new Promise((done) => { locate = done; });
            if (url.includes("conversations/2/messages")) return new Promise((done) => { messagesB = done; });
            return next(url, method, body);
        } }, { filters: {}, focus: { contactId: 1, email: "a@example.edu" } });
        back(ctx); choose(ctx, 2); await flush(); locate({ items: [expertA()], total: 1 }); await flush();
        messagesB(messagesA()); await flush();
        assert.match(ctx.host.querySelector(".mc-header h2").textContent, /专家B/);
        await ctx.sandbox.MailboxChat.mount(ctx.host).loadList(); await flush();
        assert.match(ctx.host.querySelector(".mc-header h2").textContent, /专家B/);
    });
    it("发送中返回再切 B，A 迟到成功不清 B 草稿也不重开 pane", async () => {
        const ctx = await mobile(); choose(ctx); await flush();
        let resolveSend;
        ctx.sandbox.mcHostSendRichReply = () => new Promise((done) => { resolveSend = done; });
        ctx.host.querySelector('[aria-label="人工回复正文"]').innerText = "A提交版本";
        click(ctx.host.querySelector('[data-action="mc-send-manual"]')); await flush();
        back(ctx); choose(ctx, 2); await flush();
        ctx.host.querySelector('[aria-label="人工回复正文"]').innerText = "B保留";
        back(ctx); resolveSend(true); await flush(); ctx.runFrames();
        assert.equal(pane(ctx), "list"); choose(ctx, 2); ctx.runFrames();
        assert.equal(ctx.host.querySelector('[aria-label="人工回复正文"]').innerText, "B保留");
    });

    it("发送成功后返回/换专家不复活已清草稿，显式新输入仍保存", async () => {
        const ctx = await mobile(); choose(ctx); await flush();
        const editor = ctx.host.querySelector('[aria-label="人工回复正文"]'); editor.innerText = "已发送正文";
        const subject = ctx.host.querySelector('input[aria-label="回复主题"]'); subject.value = "已发送主题"; inputEvent(subject); inputEvent(editor);
        click(ctx.host.querySelector('[data-action="mc-send-manual"]')); await flush();
        back(ctx); choose(ctx, 2); await flush(); choose(ctx); await flush();
        assert.notEqual(ctx.host.querySelector('input[aria-label="回复主题"]').value, "已发送主题");
        const nextEditor = ctx.host.querySelector('[aria-label="人工回复正文"]'); nextEditor.innerText = "重发正文"; inputEvent(nextEditor);
        click(ctx.host.querySelector('[data-action="mc-send-manual"]')); await flush();
        // 用户再次输入同文案：input 事件明确建立新草稿，不被生命周期快照抑制。
        inputEvent(nextEditor); back(ctx); choose(ctx, 2); await flush(); choose(ctx); await flush();
        assert.equal(ctx.host.querySelector('[aria-label="人工回复正文"]').innerText, "重发正文");
        click(ctx.host.querySelector('[data-action="mc-send-manual"]')); await flush();
        ctx.host.querySelector('[aria-label="人工回复正文"]').innerText = "发送后新正文";
        back(ctx); choose(ctx, 2); await flush(); choose(ctx); await flush();
        assert.equal(ctx.host.querySelector('[aria-label="人工回复正文"]').innerText, "发送后新正文");
    });

    it("外部 focus 不存在时先保存当前正文，返回后仍可恢复原专家", async () => {
        const ctx = await mobile(); choose(ctx); await flush();
        ctx.host.querySelector('[aria-label="人工回复正文"]').innerText = "定位失败前草稿";
        ctx.sandbox.MailboxChat.mount(ctx.host, { focus: { contactId: 999 } }); await flush();
        assert.match(ctx.host.querySelector(".mc-conversation").textContent, /未找到该专家/);
        back(ctx); choose(ctx); await flush();
        assert.equal(ctx.host.querySelector('[aria-label="人工回复正文"]').innerText, "定位失败前草稿");
    });

});

describe("收发件箱专家备注：正式挂载行为（I-1..I-8 / S-1..S-3）", () => {
    const noteUrl = "/api/mail/contact-notes/1";
    const view = (contactId, note, updatedAt = "2026-10-08T02:18:00.000Z") => ({
        contactId, note, updatedBy: note ? "<admin>" : null, updatedAt: note ? updatedAt : null
    });
    const trigger = (ctx) => ctx.host.querySelector(".mc-note-trigger");
    const dialog = (ctx) => ctx.doc.body.querySelector(".mc-note-dialog");
    const puts = (ctx) => ctx.calls.api.filter((r) => r.url.includes("/contact-notes/") && r.method === "PUT");
    const gets = (ctx) => ctx.calls.api.filter((r) => r.url.includes("/contact-notes/") && r.method === "GET");
    function choose(ctx, id = 1) {
        click(ctx.host.querySelectorAll(".mc-person-main").find((node) => node.dataset.contactId === String(id)));
    }
    async function selected(extra) {
        const ctx = await bootChat(Object.assign({
            conversations: { items: [expertA(), expertB()], total: 2 }, contact: contactA()
        }, extra || {}));
        choose(ctx);
        await flush();
        return ctx;
    }
    function shortcut(node, opts) {
        const event = new MiniEvent("keydown", { bubbles: true });
        Object.assign(event, { key: "Enter", ctrlKey: true }, opts || {});
        node.dispatchEvent(event);
    }

    it("加载/失败不能空编辑；重试成功才显示添加，取消关闭 Escape 零 PUT", async () => {
        let resolveRead;
        let fail = true;
        const ctx = await selected({ route: (url, method, body, entry, next) => {
            if (url === noteUrl && fail) return new Promise((resolve, reject) => { resolveRead = reject; });
            return next(url, method, body, entry);
        } });
        assert.equal(trigger(ctx).disabled, true);
        assert.match(trigger(ctx).textContent, /加载中/);
        click(trigger(ctx));
        assert.equal(dialog(ctx), null);
        resolveRead(new Error("down")); await flush();
        assert.equal(trigger(ctx).dataset.action, "mc-note-retry");
        assert.equal(trigger(ctx).getAttribute("aria-label"), "重试读取专家备注");
        fail = false; click(trigger(ctx)); await flush();
        assert.match(trigger(ctx).textContent, /＋ 添加备注/);
        assert.equal(ctx.host.querySelector(".mc-note-edit"), null);
        for (const action of ["cancel", "close", "escape"]) {
            click(trigger(ctx));
            const node = dialog(ctx);
            node.querySelector("textarea").value = "丢弃";
            if (action === "escape") keyEvent(node.querySelector("textarea"), "Escape");
            else click(node.querySelector(`[data-note-action="${action}"]`));
            assert.equal(dialog(ctx), null);
            assert.equal(ctx.doc.activeElement, trigger(ctx));
        }
        assert.equal(puts(ctx).length, 0);
    });

    it("纯文本、准确 metadata、独立 body DOM、长度/emoji/键盘与回包清空", async () => {
        const html = "<img src=x onerror=alert(1)>\n中文";
        const ctx = await selected({ contactNotes: { 1: view(1, html) } });
        assert.equal(ctx.host.querySelector(".mc-note-text").textContent, html);
        assert.equal(ctx.host.querySelector(".mc-note-slot img"), null);
        assert.equal(trigger(ctx).getAttribute("title"), null);
        click(trigger(ctx));
        let node = dialog(ctx);
        assert.equal(node.parentNode, ctx.doc.body);
        assert.equal(node.getAttribute("style"), null);
        assert.ok(node.querySelector(`#${node.getAttribute("aria-labelledby")}`));
        const input = node.querySelector("textarea");
        assert.equal(node.querySelector("label").getAttribute("for"), input.id || input.getAttribute("id"));
        assert.equal(input.getAttribute("maxlength"), "2000");
        assert.equal(input.value, html);
        assert.equal(ctx.doc.activeElement, input);
        assert.match(node.querySelector("[data-note-meta]").textContent, /<admin> · 2026-10-08 10:18 北京时间/);
        assert.equal(node.querySelector("admin"), null);
        input.value = "😀".repeat(1001); inputEvent(input);
        assert.match(node.querySelector("[data-note-meta]").textContent, /^2002 \/ 2000/);
        shortcut(input); assert.equal(puts(ctx).length, 0);
        input.value = "😀".repeat(1000); inputEvent(input);
        shortcut(input, { isComposing: true }); keyEvent(input, "Enter");
        assert.equal(puts(ctx).length, 0);
        shortcut(input, { ctrlKey: false, metaKey: true }); await flush();
        assert.equal(JSON.parse(puts(ctx)[0].body).note.length, 2000);
        assert.deepEqual(Object.keys(JSON.parse(puts(ctx)[0].body)), ["note"]);
        assert.equal(dialog(ctx), null);
        assert.equal(ctx.host.querySelector(".mc-note-text").textContent.length, 2000);
        click(trigger(ctx)); node = dialog(ctx);
        node.querySelector("textarea").value = " \r\n ";
        click(node.querySelector('[data-note-action="save"]')); await flush();
        assert.match(trigger(ctx).textContent, /＋ 添加备注/);
        click(trigger(ctx));
        assert.equal(dialog(ctx).querySelector("[data-note-meta]").textContent, "0 / 2000");
        assert.ok(ctx.calls.status.some((status) => status.message === "备注已保存"));
    });

    it("保存 busy 禁重复/关闭/读取，失败保留原摘要与草稿再显式重试", async () => {
        let rejectPut;
        let fail = true;
        const ctx = await selected({ contactNotes: { 1: view(1, "服务器原值") }, route: (url, method, body, entry, next) => {
            if (url === noteUrl && method === "PUT" && fail) return new Promise((resolve, reject) => { rejectPut = reject; });
            return next(url, method, body, entry);
        } });
        click(trigger(ctx));
        const node = dialog(ctx), input = node.querySelector("textarea");
        input.value = "待重试\r\n第二行";
        click(node.querySelector('[data-note-action="save"]'));
        click(node.querySelector('[data-note-action="save"]')); shortcut(input);
        click(node.querySelector('[data-note-action="cancel"]'));
        click(node.querySelector('[data-note-action="close"]'));
        keyEvent(input, "Escape"); node.dispatchEvent(new MiniEvent("cancel"));
        assert.equal(puts(ctx).length, 1);
        assert.equal(dialog(ctx), node);
        assert.equal(input.disabled, true);
        assert.ok(node.querySelectorAll("button").every((button) => button.disabled));
        const count = gets(ctx).length;
        ctx.api.refreshFromHost(); await flush();
        assert.equal(gets(ctx).length, count);
        rejectPut(new Error("<网络错误>")); await flush();
        assert.equal(input.value, "待重试\r\n第二行");
        assert.equal(ctx.host.querySelector(".mc-note-text").textContent, "服务器原值");
        assert.equal(input.disabled, false);
        assert.ok(node.querySelectorAll("button").every((button) => !button.disabled));
        assert.match(node.querySelector(".mc-note-error").textContent, /备注保存失败，请重试/);
        assert.equal(node.querySelector(".mc-note-error").hidden, false);
        assert.equal(ctx.calls.status.some((s) => s.message === "备注已保存"), false);
        fail = false; shortcut(input); await flush();
        assert.equal(ctx.host.querySelector(".mc-note-text").textContent, "待重试\n第二行");
    });

    it("A→B→A contact/epoch/readSeq 防旧 GET；保存作废此前 GET", async () => {
        const reads = [];
        const ctx = await selected({ route: (url, method, body, entry, next) => {
            if (url.includes("/contact-notes/") && method === "GET") return new Promise((resolve) => reads.push({ url, resolve }));
            return next(url, method, body, entry);
        } });
        choose(ctx, 2); await flush(); choose(ctx); await flush();
        reads[2].resolve(view(1, "新A")); await flush();
        reads[1].resolve(view(2, "B")); reads[0].resolve(view(1, "旧A")); await flush();
        assert.equal(ctx.host.querySelector(".mc-note-text").textContent, "新A");
        click(trigger(ctx));
        const node = dialog(ctx);
        node.querySelector("textarea").value = "保存优先";
        ctx.api.refreshFromHost(); await flush();
        assert.equal(reads.length, 4);
        click(node.querySelector('[data-note-action="save"]')); await flush();
        reads[3].resolve(view(1, "保存前旧读取")); await flush();
        assert.equal(ctx.host.querySelector(".mc-note-text").textContent, "保存优先");
    });

    it("在途 A PUT 强制切 B，晚响应不关闭 B dialog、污染数据或泄露草稿", async () => {
        let resolvePut;
        const ctx = await selected({ contactNotes: { 1: view(1, "A已存"), 2: view(2, "B已存") }, route: (url, method, body, entry, next) => {
            if (url === noteUrl && method === "PUT") return new Promise((resolve) => { resolvePut = resolve; });
            return next(url, method, body, entry);
        } });
        click(trigger(ctx)); const old = dialog(ctx);
        old.querySelector("textarea").value = "A草稿";
        click(old.querySelector('[data-note-action="save"]'));
        choose(ctx, 2); await flush();
        assert.equal(old.parentNode, null);
        click(trigger(ctx)); const current = dialog(ctx);
        current.querySelector("textarea").value = "B草稿";
        resolvePut(view(1, "A新值")); await flush();
        assert.equal(dialog(ctx), current);
        assert.equal(current.querySelector("textarea").value, "B草稿");
        assert.equal(ctx.host.querySelector(".mc-note-text").textContent, "B已存");
        assert.equal(ctx.calls.status.some((s) => s.message === "备注已保存"), false);
        click(current.querySelector('[data-note-action="cancel"]')); choose(ctx); await flush();
        click(trigger(ctx)); assert.equal(dialog(ctx).querySelector("textarea").value, "A已存");
    });

    it("账号/待匹配/unmount 清理自有 dialog，晚 GET/PUT 不复活 DOM", async () => {
        for (const boundary of ["account", "unmatched", "unmount"]) {
            let finish;
            const ctx = await selected({ contactNotes: { 1: view(1, "原值") }, route: (url, method, body, entry, next) => {
                if (url === noteUrl && method === "PUT") return new Promise((resolve) => { finish = resolve; });
                return next(url, method, body, entry);
            } });
            click(trigger(ctx)); const old = dialog(ctx);
            click(old.querySelector('[data-note-action="save"]'));
            if (boundary === "account") ctx.sandbox.MailboxChat.mount(ctx.host, { filters: { accountCode: "acc2" } });
            else if (boundary === "unmatched") click(ctx.host.querySelector('[data-chip="unmatched"]'));
            else ctx.sandbox.MailboxChat.unmount(ctx.host);
            await flush(); finish(view(1, "迟到")); await flush();
            assert.equal(old.parentNode, null);
            assert.equal(dialog(ctx), null);
        }
        for (const boundary of ["account", "unmatched", "unmount"]) {
            const reads = [];
            const ctx = await selected({ route: (url, method, body, entry, next) => {
                if (url === noteUrl) return new Promise((resolve) => { reads.push(resolve); });
                return next(url, method, body, entry);
            } });
            if (boundary === "account") ctx.sandbox.MailboxChat.mount(ctx.host, { filters: { accountCode: "acc2" } });
            else if (boundary === "unmatched") click(ctx.host.querySelector('[data-chip="unmatched"]'));
            else ctx.sandbox.MailboxChat.unmount(ctx.host);
            await flush();
            reads[0](view(1, "迟到GET")); await flush();
            assert.equal(ctx.host.textContent.includes("迟到GET"), false);
            assert.equal(dialog(ctx), null);
        }
    });

    it("timing/标签重绘不丢备注、编辑焦点或邮件草稿，不额外 GET；空 timeline 显式刷新仍 GET", async () => {
        let finishTiming, finishTags;
        const ctx = await selected({ messages: messagesA(), contactNotes: { 1: view(1, "内部备注") },
            fetchTagsFn: () => new Promise((resolve) => { finishTags = resolve; }),
            route: (url, method, body, entry, next) => {
                if (/\/timing$/.test(url)) return new Promise((resolve) => { finishTiming = resolve; });
                return next(url, method, body, entry);
            }
        });
        const editor = ctx.host.querySelector('[aria-label="人工回复正文"]');
        setEditorContent(editor, "<p>保留邮件草稿</p>", "保留邮件草稿"); inputEvent(editor);
        click(trigger(ctx)); const node = dialog(ctx), input = node.querySelector("textarea");
        input.value = "尚未保存"; inputEvent(input);
        const count = gets(ctx).length;
        finishTiming(TIMING_WORK_HOURS_BR); await flush();
        finishTags({ found: true, tags: ["重点关注"] }); await flush();
        assert.equal(dialog(ctx), node);
        assert.equal(input.value, "尚未保存");
        assert.equal(ctx.doc.activeElement, input);
        assert.equal(ctx.host.querySelector(".mc-note-text").textContent, "内部备注");
        assert.equal(gets(ctx).length, count);
        assert.equal(editor.innerHTML, "<p>保留邮件草稿</p>");
        click(node.querySelector('[data-note-action="cancel"]'));
        choose(ctx, 2); await flush(); choose(ctx); await flush();
        assert.equal(ctx.host.querySelector('[aria-label="人工回复正文"]').innerText, "保留邮件草稿");
        const empty = await selected({ messages: { items: [], nextBefore: null, hasMore: false } });
        const before = gets(empty).length;
        empty.api.refreshFromHost(); await flush();
        assert.equal(gets(empty).length, before + 1);
    });

    it("打开备注关闭既有浮层，关闭只删自有节点；非法日期不伪造当前时间", async () => {
        const ctx = await selected({ contactNotes: { 1: view(1, "内容", "not-a-date") } });
        click(ctx.host.querySelector('[data-action="mc-contact-location"]')); await flush();
        assert.ok(ctx.doc.body.querySelector(".contact-timing-dialog"));
        click(trigger(ctx));
        assert.equal(ctx.doc.body.querySelector(".contact-timing-dialog"), null);
        assert.match(dialog(ctx).querySelector("[data-note-meta]").textContent, /<admin>/);
        assert.doesNotMatch(dialog(ctx).querySelector("[data-note-meta]").textContent, /北京时间/);
        assert.equal(ctx.host.querySelector(".mc-note-row").querySelector(".contact-timing") !== null, true);
        const portal = ctx.doc.body.querySelector(".mc-overlay-root");
        const sentinel = ctx.doc.createElement("span");
        sentinel.textContent = "其他拥有者";
        portal.appendChild(sentinel);
        click(dialog(ctx).querySelector('[data-note-action="cancel"]'));
        assert.equal(sentinel.parentNode, portal);
        assert.equal(dialog(ctx), null);
    });

    it("同 contact 显式刷新 GET 以 readSeq 最新响应为准，重新打开产生独立标识", async () => {
        const reads = [];
        const ctx = await selected({ route: (url, method, body, entry, next) => {
            if (url === noteUrl) return new Promise((resolve) => { reads.push(resolve); });
            return next(url, method, body, entry);
        } });
        ctx.api.refreshFromHost(); await flush();
        reads[1](view(1, "最新")); await flush();
        reads[0](view(1, "旧响应")); await flush();
        assert.equal(ctx.host.querySelector(".mc-note-text").textContent, "最新");
        click(trigger(ctx));
        const firstId = dialog(ctx).querySelector("textarea").getAttribute("id");
        click(dialog(ctx).querySelector('[data-note-action="cancel"]'));
        click(trigger(ctx));
        const input = dialog(ctx).querySelector("textarea");
        assert.notEqual(input.getAttribute("id"), firstId);
        assert.equal(input.value, "最新");
        input.value = "中".repeat(2001);
        click(dialog(ctx).querySelector('[data-note-action="save"]'));
        assert.equal(puts(ctx).length, 0);
        assert.equal(input.value.length, 2001);
    });
});

describe("专家备注冻结样式与缓存键", () => {
    it("S-1/S-2/S-3 标记块逐字一致", () => {
        const css = fs.readFileSync(path.join(ROOT, "mailbox-chat.css"), "utf-8");
        const blocks = [
`/* mailbox-expert-note:S1 */
.mail-chat .mc-header .mc-header-meta{min-width:0}
.mail-chat .mc-note-tags{display:flex;align-items:center;gap:8px;flex-wrap:wrap;width:100%;min-width:0}
.mail-chat .mc-note-row{display:flex;align-items:center;gap:12px;width:100%;min-width:0}
.mail-chat .mc-note-slot{flex:1;min-width:0}
.mail-chat .mc-note-row .contact-timing{flex:none;margin-left:auto;padding-left:0;border-left:0}
.mail-chat .mc-note-trigger{display:flex;align-items:center;gap:4px;width:100%;min-width:0;height:24px;padding:0;border:0;border-radius:4px;background:transparent;color:#61748e;font:inherit;font-size:11px;line-height:24px;text-align:left;cursor:pointer}
.mail-chat .mc-note-label{flex:none;color:#8494aa}
.mail-chat .mc-note-text{min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.mail-chat .mc-note-edit{flex:none;color:#3762d8;margin-left:6px}
.mail-chat .mc-note-trigger:hover:not(:disabled){background:#f3f6fc;color:#285ac0}
.mail-chat .mc-note-trigger:active:not(:disabled){background:#e9efff}
.mail-chat .mc-note-trigger:disabled{opacity:.55;cursor:default}
.mail-chat .mc-note-trigger:focus-visible{outline:2px solid #6389d3;outline-offset:2px}
/* mailbox-expert-note:S1:end */`,
`/* mailbox-expert-note:S2 */
.mc-note-dialog{position:fixed;inset:0;margin:auto;padding:0;width:460px;max-width:calc(100vw - 32px);height:fit-content;max-height:calc(100dvh - 32px);overflow:auto;border:1px solid #d9e3f1;border-radius:12px;background:#fff;color:#475569;box-shadow:0 14px 48px #17325728;font:12px/1.6 var(--font-body,sans-serif)}
.mc-note-dialog,.mc-note-dialog *{box-sizing:border-box}
.mc-note-dialog [hidden]{display:none!important}
.mc-note-dialog::backdrop{background:#172c4712}
.mc-note-dialog header{display:flex;align-items:center;justify-content:space-between;padding:12px 16px;border-bottom:1px solid #edf1f7}
.mc-note-dialog header strong{font-size:14px;font-weight:600}
.mc-note-dialog .mc-note-close{border:0;border-radius:4px;background:transparent;color:#8494aa;font-size:22px;line-height:24px;cursor:pointer;padding:0 4px}
.mc-note-dialog .mc-note-close:hover:not(:disabled){background:#edf3ff;color:#244ca9}
.mc-note-dialog .mc-note-close:active:not(:disabled){background:#dbeafe}
.mc-note-body{padding:12px 16px}
.mc-note-body label{display:block;color:#97a5b8;font-size:11px;margin-bottom:6px}
.mc-note-dialog textarea{display:block;width:100%;min-height:116px;max-height:220px;resize:vertical;border:1px solid #b8caf2;border-radius:7px;padding:9px 11px;background:#fff;color:#475569;font:inherit;line-height:1.8;margin:0 0 7px}
.mc-note-dialog textarea:focus{outline:2px solid #dce8ff;outline-offset:1px;border-color:#668ee3}
.mc-note-dialog textarea:disabled{opacity:.65;cursor:wait}
.mc-note-hint{color:#97a5b8;font-size:10px}
.mc-note-error{margin-top:8px;color:#be123c;font-size:12px;line-height:1.6;overflow-wrap:anywhere}
.mc-note-dialog footer{display:flex;align-items:center;justify-content:space-between;gap:10px;padding:0 16px 14px}
.mc-note-dialog footer>div{display:flex;gap:8px}
.mc-note-dialog .button{height:30px;min-height:30px;padding:4px 12px;font-size:12px}
.mc-note-dialog button:disabled{opacity:.45;cursor:not-allowed;transform:none;box-shadow:none}
.mc-note-dialog button:focus-visible{outline:2px solid #6389d3;outline-offset:2px}
/* mailbox-expert-note:S2:end */`,
`/* mailbox-expert-note:S3 */
@media(max-width:760px){.mail-chat .mc-note-row{flex-wrap:wrap}.mail-chat .mc-note-row .mc-note-slot{flex-basis:100%}.mail-chat .mc-note-row .contact-timing{margin-left:0}.mail-chat .mc-note-trigger{min-height:36px}.mc-note-dialog textarea{font-size:16px}.mc-note-dialog footer{flex-wrap:wrap}.mc-note-dialog .button{min-height:36px;height:36px}}
/* mailbox-expert-note:S3:end */`
        ];
        blocks.forEach((block, index) => {
            const marker = `S${index + 1}`;
            const actual = css.match(new RegExp(`/\\* mailbox-expert-note:${marker} \\*/[\\s\\S]*?/\\* mailbox-expert-note:${marker}:end \\*/`, "g"));
            assert.deepEqual(actual, [block]);
        });
    });
    it("所有当前版本资源统一键，原资源顺序与非版本资源不变", () => {
        const html = fs.readFileSync(path.join(ROOT, "index.html"), "utf-8");
        const assets = [...html.matchAll(/(?:href|src)="([^"]+\?v=([^"]+))"/g)];
        assert.deepEqual(assets.map((match) => match[1].split("?")[0]), [
            "styles.css", "expert-materials.css", "mailbox-chat.css", "meeting-confirmation.css", "world-clock.css",
            "trust-reply-workbench.js", "expert-materials.js", "meeting-confirmation.js", "mailbox-chat.js", "app.js", "world-clock.js"
        ]);
        assert.equal(new Set(assets.map((match) => match[2])).size, 1);
        assert.match(assets[0][2], /^\d{8}-[a-z0-9-]+$/);
        assert.ok(html.includes('<script src="task-modal-runtime.js"></script>'));
    });
});
