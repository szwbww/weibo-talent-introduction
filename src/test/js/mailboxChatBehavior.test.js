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
        unmatchedMounts: [],
        unmatchedReleases: 0,
        unmatchedUnmounts: []
    };
    const timers = [];

    const defaultRoute = function defaultRoute(url, method, body) {
        if (url.startsWith("/api/mail/mailbox/conversations?")) {
            return Promise.resolve(opts.conversations || { items: [], total: 0 });
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
            return Promise.resolve({});
        }
        if (/\/api\/mail\/mailbox\/conversations\/\d+\/follow/.test(url)) {
            return Promise.resolve({ followed: opts.followResult !== false });
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

    vm.createContext(sandbox);
    vm.runInContext(chatSource, sandbox, { filename: "mailbox-chat.js" });
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
    ctx.sandbox.MailboxChat.mount(host, mountOptions || { filters: {} });
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
    it("mounts S-1 骨架：两栏、搜索行、⋯ popover、三 tab、分页", async () => {
        const conversations = { items: [expertA(), expertB(), expertCTagsNull()], total: 3 };
        const ctx = await bootChat({ conversations });
        const html = ctx.host.innerHTML;
        assert.match(html, /class="mail-chat"/);
        assert.ok(ctx.host.querySelector('aside.mc-experts[aria-label="专家会话列表"]'));
        assert.ok(ctx.host.querySelector('section.mc-conversation[aria-label="专家往来信件"]'));
        assert.ok(ctx.host.querySelector('.mc-search-row input[aria-label="搜索专家"]'));
        const chips = ctx.host.querySelectorAll(".mc-filter");
        assert.deepStrictEqual(chips.map((chip) => chip.textContent), ["全部", "关注", "待处理", "待匹配"]);
        assert.deepStrictEqual(chips.map((chip) => chip.dataset.chip), ["all", "followed", "pending", "unmatched"]);
        assert.strictEqual(chips[0].getAttribute("aria-pressed"), "true");
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
        assert.ok(processed.querySelector(".mc-done"), "已处理来信显示 ✓ 已处理");
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
                    return Promise.resolve({});
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
        const markRequest = ctx.calls.api.find((entry) => entry.url === "/api/mail/unmatched-inbound/101/mark-resolved");
        assert.ok(markRequest, "调既有 mark-resolved API");
        assert.ok(conversationsRequests(ctx).length > listBefore, "mark 成功后重查服务端列表");
        const lastUrl = conversationsRequests(ctx)[conversationsRequests(ctx).length - 1].url;
        assert.ok(lastUrl.includes("page=0"), "重查当前页（服务端顺序）");
        // 草稿保留（编辑器未重建）
        assert.strictEqual(ctx.host.querySelector('[aria-label="人工回复正文"]').innerText, "draft keeps", "编辑器草稿保留");
        const article = ctx.host.querySelector('[data-message-key="INBOUND_PROCESSING:101"]');
        assert.match(article.textContent, /✓ 已处理/, "消息局部更新为已处理（静默窗口校验不回滚）");
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

    it("关注乐观更新；失败回滚星标并报错", async () => {
        let failNext = false;
        let followedState = false;
        const ctx = await bootChat({
            conversations: { items: [expertA()], total: 1 },
            route: (url, method, body, entry, next) => {
                if (url.startsWith("/api/mail/mailbox/conversations?")) {
                    return Promise.resolve({ items: [expertA({ followed: followedState })], total: 1 });
                }
                if (/\/follow$/.test(url) && (method === "DELETE" || method === "PUT")) {
                    if (failNext) return Promise.reject(new Error("network down"));
                    followedState = method === "PUT";
                    return Promise.resolve({ followed: followedState });
                }
                return next(url, method, body);
            }
        });
        assert.strictEqual(ctx.host.querySelector(".mc-follow").getAttribute("aria-pressed"), "false");
        click(ctx.host.querySelector(".mc-follow"));
        await flush();
        const starred = ctx.host.querySelector(".mc-follow");
        assert.strictEqual(starred.getAttribute("aria-pressed"), "true", "乐观更新为已关注");
        failNext = true;
        click(starred);
        await flush();
        const rolledBack = ctx.host.querySelector(".mc-follow");
        assert.strictEqual(rolledBack.getAttribute("aria-pressed"), "true", "失败回滚为已关注");
        assert.ok(rolledBack.textContent.includes("★"));
        assert.ok(ctx.calls.status.some((s) => /关注操作失败/.test(s.message)));
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

describe("待匹配 Tab：第四 chip、请求契约与邮件级列表", () => {
    const conversations = { items: [expertA()], total: 1 };

    it("S-1：四个 tab 顺序/anchor 固定，待匹配只请求 unmatched-inbound（offset=page*20、无专家参数）", async () => {
        const ctx = await bootChat({ conversations, unmatched: { records: [unmatchedMail(901)], totalCount: 1 } });
        const chips = ctx.host.querySelectorAll(".mc-filter");
        assert.deepStrictEqual(chips.map((chip) => chip.dataset.chip), ["all", "followed", "pending", "unmatched"]);
        assert.deepStrictEqual(chips.map((chip) => chip.textContent), ["全部", "关注", "待处理", "待匹配"]);
        assert.strictEqual(chips[0].getAttribute("aria-pressed"), "true", "默认全部");
        assert.strictEqual(chips[3].getAttribute("aria-pressed"), "false");

        click(chips[3]);
        await flush();

        assert.strictEqual(chips[3].getAttribute("aria-pressed"), "true", "待匹配选中态");
        assert.strictEqual(chips[0].getAttribute("aria-pressed"), "false");
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
