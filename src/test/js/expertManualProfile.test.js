"use strict";

// 02（contact-profile-02-ui）测试：真实挂载 DOM + 可控延迟请求。
//
// 覆盖：
// - I-1 双宿主同契约：showExpertDetail（无 contact，默认学术档案）与 loadContactDetail
//   （有 contact，默认联系详情）都实际构造，同一个挂载入口、同一 context 键集、19 项可保存；
// - I-2 请求与节点生命周期：挂载捕获 expertId/contactId/generation/宿主；await 后校验；
//   切人中止读请求、关闭弹窗；保存请求不因切换被中止，由下次 GET 确认；
// - I-3 保存与错误：200/400/409/401/503/网络失败、GET 失败重试、取消/Escape、双击保存；
// - I-4 既有字段与凭证：证件末四位、编辑弹窗明文、原文回写、nationality 不拿 country 顶替、
//   恶意 HTML 只作纯文本；
// - S-1 宿主与复用组件：两个 contact panel 最前面插入 ec-profile；契约 class/标签/无 id/无 inline style；
// - S-2 唯一新增 CSS：逐字（字节）追加在 styles.css 末尾。
//
// 说明（K-dom-stub-tests-hide-dangling-refs）：本文件不使用“getElementById 恒返回元素”的空 stub，
// 而是把 app.js 的 02 区域真实执行在能解析 innerHTML、支持 querySelector/事件冒泡/dataset/
// showModal 的小型 DOM 上，并对真实 index.html / styles.css 源文本做断言。

const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const STATIC_DIR = path.join(__dirname, "..", "..", "main", "resources", "static");
const APP_SOURCE = fs.readFileSync(path.join(STATIC_DIR, "app.js"), "utf-8");
const STYLES_SOURCE = fs.readFileSync(path.join(STATIC_DIR, "styles.css"), "utf-8");
const INDEX_SOURCE = fs.readFileSync(path.join(STATIC_DIR, "index.html"), "utf-8");
const PLAN_PATH = path.join(__dirname, "..", "..", "..", "docs", "plans", "2026-10-11", "contact-profile-02-ui.md");
const PLAN_SOURCE = fs.readFileSync(PLAN_PATH, "utf-8");

// I-1（K-frontend-cache-key-triad）：缓存键字面量只允许出现在 index.html，测试从 index.html 动态取值；
// 计划要求的目标值同样从计划正文解析，避免在测试里固化第二份字面量。
const CACHE_KEY = (INDEX_SOURCE.match(/styles\.css\?v=([^"'&<>]+)/) || [])[1];
const PLANNED_CACHE_KEY = (() => {
    const match = PLAN_SOURCE.match(/统一(?:更新)?为\s*`?([0-9]{8}-[a-z0-9-]+)`?/);
    if (!match) throw new Error("planned cache key not found in " + PLAN_PATH);
    return match[1];
})();
const MODULE_BEGIN = "/* contact-profile-02-ui:begin */";
const MODULE_END = "/* contact-profile-02-ui:end */";
const HOST_SELECTOR = '[data-role="expert-manual-profile"]';

// 01 字段契约（docs/plans/2026-10-11/contact-profile-01-storage.md §1 表格）。
const GROUP_FIELDS = {
    matching: ["researchAbility", "companyMatchStatus", "matchedCompanies"],
    identity: ["talentCode", "chineseName", "birthDateText", "documentNumber", "sourceCountry"],
    education: [
        "graduatedSchool", "schoolRanking", "preChinaOrganization", "organizationRanking",
        "postdocOrganization", "postdocPeriod", "professionalTitle", "administrativeTitles"
    ],
    technology: ["industry", "technicalExpertise", "programCategories"]
};
const GROUP_ORDER = ["matching", "identity", "education", "technology"];
const GROUP_LABELS = {
    matching: "科研与企业匹配",
    identity: "身份基本信息",
    education: "教育任职",
    technology: "产业技术"
};
const FIELD_LIMITS = {
    talentCode: 64, chineseName: 200, birthDateText: 10, documentNumber: 100, sourceCountry: 100,
    graduatedSchool: 2000, schoolRanking: 500, preChinaOrganization: 2000, organizationRanking: 500,
    postdocOrganization: 2000, postdocPeriod: 200, professionalTitle: 200, administrativeTitles: 4000,
    industry: 500, technicalExpertise: 4000, programCategories: 500,
    researchAbility: 16, companyMatchStatus: 16, matchedCompanies: 2000
};
const MULTILINE_FIELDS = [
    "graduatedSchool", "preChinaOrganization", "postdocOrganization", "administrativeTitles",
    "technicalExpertise", "matchedCompanies"
];
const SELECT_OPTIONS = {
    researchAbility: { "": "未评估", VERY_STRONG: "很强", STRONG: "强", ABOVE_AVERAGE: "中上", AVERAGE: "中等", FAIR: "一般", LOW: "低" },
    companyMatchStatus: { NOT_MATCHED: "未匹配", MATCHING: "匹配中", MATCHED: "匹配上" }
};
const READONLY_FIELDS = ["expertName", "nationality", "keyword"];
const ALL_FIELDS = GROUP_ORDER.reduce((all, group) => all.concat(GROUP_FIELDS[group]), []);
const CONTRACT_TAGS = new Set([
    "NAV", "SECTION", "DIV", "H3", "SPAN", "BUTTON", "DIALOG", "FORM", "HEADER", "FOOTER",
    "LABEL", "INPUT", "TEXTAREA", "SELECT", "OPTION"
]);
const VOID_TAGS = new Set(["area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr"]);

// ════════════════════════════════════════════════════════════════════════
// 最小真实 DOM（独立副本：innerHTML 解析 + 选择器 + 事件冒泡 + dataset + dialog）
// ════════════════════════════════════════════════════════════════════════

class MiniEvent {
    constructor(type, options) {
        this.type = type;
        this.bubbles = !options || options.bubbles !== false;
        this.target = null;
        this.currentTarget = null;
        this.defaultPrevented = false;
        this.propagationStopped = false;
    }
    preventDefault() {
        this.defaultPrevented = true;
    }
    stopPropagation() {
        this.propagationStopped = true;
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
    get isConnected() {
        let node = this;
        while (node.parentNode) node = node.parentNode;
        return node.nodeType === 9;
    }
}

function decodeEntities(text) {
    return String(text)
        .replace(/&lt;/g, "<")
        .replace(/&gt;/g, ">")
        .replace(/&quot;/g, '"')
        .replace(/&#0?39;/g, "'")
        .replace(/&amp;/g, "&");
}

function parseCompound(token) {
    const out = { tag: null, classes: [], id: null, attrs: [] };
    let index = 0;
    const tagMatch = token.match(/^[a-zA-Z][\w-]*/);
    if (tagMatch) {
        out.tag = tagMatch[0].toUpperCase();
        index = tagMatch[0].length;
    }
    while (index < token.length) {
        const ch = token[index];
        if (ch === "." || ch === "#") {
            const nameMatch = token.slice(index + 1).match(/^[\w-]+/);
            if (!nameMatch) {
                index += 1;
                continue;
            }
            if (ch === ".") out.classes.push(nameMatch[0]);
            else out.id = nameMatch[0];
            index += 1 + nameMatch[0].length;
            continue;
        }
        if (ch === "[") {
            const end = token.indexOf("]", index);
            const raw = token.slice(index + 1, end === -1 ? token.length : end);
            const eq = raw.indexOf("=");
            if (eq === -1) {
                out.attrs.push({ name: raw.trim(), value: null });
            } else {
                let value = raw.slice(eq + 1).trim();
                if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.slice(1, -1);
                }
                out.attrs.push({ name: raw.slice(0, eq).trim(), value });
            }
            index = (end === -1 ? token.length : end) + 1;
            continue;
        }
        index += 1;
    }
    return out;
}

function parseSelector(selector) {
    const steps = [];
    let combinator = " ";
    for (const token of String(selector).trim().split(/\s+/)) {
        if (token === ">") {
            combinator = ">";
            continue;
        }
        steps.push({ combinator, compound: parseCompound(token) });
        combinator = " ";
    }
    return steps;
}

function compoundMatches(element, compound) {
    if (compound.tag && element.tagName !== compound.tag) return false;
    if (compound.id && element.getAttribute("id") !== compound.id) return false;
    for (const cls of compound.classes) {
        if (!element.classList.contains(cls)) return false;
    }
    for (const attr of compound.attrs) {
        const actual = element.getAttribute(attr.name);
        if (attr.value === null) {
            if (actual === null) return false;
        } else if (actual !== attr.value) {
            return false;
        }
    }
    return true;
}

function elementMatches(element, steps) {
    if (!compoundMatches(element, steps[steps.length - 1].compound)) return false;
    let node = element;
    for (let index = steps.length - 1; index > 0; index -= 1) {
        const step = steps[index];
        if (step.combinator === ">") {
            node = node.parentNode;
            if (!node || node.nodeType !== 1 || !compoundMatches(node, steps[index - 1].compound)) return false;
            continue;
        }
        let ancestor = node.parentNode;
        while (ancestor && (ancestor.nodeType !== 1 || !compoundMatches(ancestor, steps[index - 1].compound))) {
            ancestor = ancestor.parentNode;
        }
        if (!ancestor) return false;
        node = ancestor;
    }
    return true;
}

function collectText(node) {
    if (node.nodeType === 3) return node.data;
    return node.childNodes.map((child) => collectText(child)).join("");
}

class MiniElement {
    constructor(tagName, ownerDocument) {
        this.ownerDocument = ownerDocument || null;
        this.tagName = String(tagName).toUpperCase();
        this.nodeType = 1;
        this.parentNode = null;
        this.childNodes = [];
        this.attributes = new Map();
        this.listeners = new Map();
        this._classes = new Set();
        this._dataset = null;
        this._value = "";
        this._dialogOpen = false;
        this.focused = false;
        this.scrollCalls = [];
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
    get isConnected() {
        let node = this;
        while (node.parentNode) node = node.parentNode;
        return node.nodeType === 9;
    }
    get classList() {
        const self = this;
        return {
            add(...names) { names.forEach((name) => self._classes.add(String(name))); },
            remove(...names) { names.forEach((name) => self._classes.delete(String(name))); },
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
        this._classes = new Set(String(value == null ? "" : value).split(/\s+/).filter(Boolean));
    }
    get dataset() {
        if (!this._dataset) {
            const self = this;
            const toAttribute = (key) => "data-" + String(key).replace(/([A-Z])/g, (matched) => "-" + matched.toLowerCase());
            this._dataset = new Proxy({}, {
                get(target, key) {
                    if (typeof key !== "string") return undefined;
                    const attribute = toAttribute(key);
                    return self.attributes.has(attribute) ? self.attributes.get(attribute) : undefined;
                },
                set(target, key, value) {
                    if (typeof key !== "string") return true;
                    const attribute = toAttribute(key);
                    if (value === undefined || value === null) self.attributes.delete(attribute);
                    else self.attributes.set(attribute, String(value));
                    return true;
                },
                deleteProperty(target, key) {
                    self.attributes.delete(toAttribute(key));
                    return true;
                }
            });
        }
        return this._dataset;
    }
    setAttribute(name, value) {
        const text = String(value);
        this.attributes.set(name, text);
        if (name === "class") this.className = text;
        if (name === "value") this._value = text;
        if (name === "type") this._type = text;
        if (name === "hidden") this._hidden = true;
        if (name === "disabled") this._disabled = true;
        if (name === "open") this._dialogOpen = true;
    }
    getAttribute(name) {
        return this.attributes.has(name) ? this.attributes.get(name) : null;
    }
    hasAttribute(name) {
        return this.attributes.has(name);
    }
    removeAttribute(name) {
        this.attributes.delete(name);
        if (name === "value") this._value = "";
        if (name === "hidden") this._hidden = false;
        if (name === "disabled") this._disabled = false;
        if (name === "open") this._dialogOpen = false;
    }
    get hidden() {
        return this.hasAttribute("hidden");
    }
    set hidden(value) {
        if (value) this.setAttribute("hidden", "");
        else this.removeAttribute("hidden");
    }
    get disabled() {
        return this.hasAttribute("disabled");
    }
    set disabled(value) {
        if (value) this.setAttribute("disabled", "");
        else this.removeAttribute("disabled");
    }
    get open() {
        return this._dialogOpen;
    }
    set open(value) {
        this._dialogOpen = !!value;
        if (value) this.setAttribute("open", "");
        else this.removeAttribute("open");
    }
    get type() {
        return this.getAttribute("type") || "";
    }
    set type(value) {
        this.setAttribute("type", value);
    }
    get value() {
        return this._value;
    }
    set value(value) {
        this._value = String(value == null ? "" : value);
        this.attributes.set("value", this._value);
    }
    get textContent() {
        return collectText(this);
    }
    set textContent(value) {
        this.childNodes = [];
        const text = value == null ? "" : String(value);
        if (text !== "") this.appendChild(new MiniText(text));
    }
    get innerHTML() {
        return "";
    }
    set innerHTML(html) {
        const fragment = parseHtml(String(html), this.ownerDocument);
        this.childNodes = [];
        for (const node of fragment.childNodes.slice()) {
            this.appendChild(node);
        }
    }
    appendChild(child) {
        if (child.parentNode) child.parentNode.removeChild(child);
        child.parentNode = this;
        this.childNodes.push(child);
        return child;
    }
    append(...nodes) {
        nodes.forEach((node) => this.appendChild(node));
    }
    removeChild(child) {
        const index = this.childNodes.indexOf(child);
        if (index === -1) return child;
        this.childNodes.splice(index, 1);
        child.parentNode = null;
        return child;
    }
    replaceChildren(...nodes) {
        this.childNodes.slice().forEach((node) => this.removeChild(node));
        nodes.forEach((node) => this.appendChild(node));
    }
    remove() {
        if (this.parentNode) this.parentNode.removeChild(this);
    }
    contains(node) {
        let current = node;
        while (current) {
            if (current === this) return true;
            current = current.parentNode;
        }
        return false;
    }
    matches(selector) {
        return elementMatches(this, parseSelector(selector));
    }
    closest(selector) {
        const steps = parseSelector(selector);
        let node = this;
        while (node && node.nodeType === 1) {
            if (elementMatches(node, steps)) return node;
            node = node.parentNode;
        }
        return null;
    }
    querySelector(selector) {
        return this.querySelectorAll(selector)[0] || null;
    }
    querySelectorAll(selector) {
        const steps = parseSelector(selector);
        const results = [];
        const walk = (node) => {
            for (const child of node.childNodes) {
                if (child.nodeType !== 1) continue;
                if (elementMatches(child, steps)) results.push(child);
                walk(child);
            }
        };
        walk(this);
        return results;
    }
    addEventListener(type, handler) {
        const list = this.listeners.get(type) || [];
        list.push(handler);
        this.listeners.set(type, list);
    }
    removeEventListener(type, handler) {
        const list = this.listeners.get(type) || [];
        this.listeners.set(type, list.filter((item) => item !== handler));
    }
    listenerCount(type) {
        if (type) return (this.listeners.get(type) || []).length;
        let count = 0;
        for (const list of this.listeners.values()) count += list.length;
        return count;
    }
    dispatchEvent(event) {
        event.target = this;
        let node = this;
        while (node && node.nodeType === 1) {
            event.currentTarget = node;
            for (const handler of node.listeners.get(event.type) || []) handler(event);
            if (event.propagationStopped) break;
            node = event.bubbles === false ? null : node.parentNode;
        }
        return true;
    }
    click() {
        this.dispatchEvent(new MiniEvent("click", { bubbles: true }));
    }
    focus() {
        this.focused = true;
        if (this.ownerDocument) this.ownerDocument.activeElement = this;
    }
    scrollIntoView(options) {
        this.scrollCalls.push(options || {});
    }
    showModal() {
        if (this._dialogOpen) throw new Error("dialog already open");
        this.open = true;
    }
    close() {
        if (!this._dialogOpen) return;
        this._dialogOpen = false;
        this.removeAttribute("open");
        this.dispatchEvent(new MiniEvent("close", { bubbles: false }));
    }
}

function applyAttributes(element, raw) {
    const attrRegex = /([^\s=]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'>]+)))?/g;
    let match;
    while ((match = attrRegex.exec(raw)) !== null) {
        element.setAttribute(match[1], match[2] !== undefined ? match[2] : (match[3] !== undefined ? match[3] : (match[4] !== undefined ? match[4] : "")));
    }
}

class MiniFragment {
    constructor() {
        this.nodeType = 11;
        this.childNodes = [];
        this.parentNode = null;
    }
    appendChild(child) {
        if (child.parentNode) child.parentNode.removeChild(child);
        child.parentNode = this;
        this.childNodes.push(child);
        return child;
    }
    removeChild(child) {
        const index = this.childNodes.indexOf(child);
        if (index === -1) return child;
        this.childNodes.splice(index, 1);
        child.parentNode = null;
        return child;
    }
}

function parseHtml(html, ownerDocument) {
    const fragment = new MiniFragment();
    const stack = [fragment];
    const append = (node) => {
        const parent = stack[stack.length - 1];
        parent.childNodes.push(node);
        node.parentNode = parent;
    };
    const tagRegex = /<\/?([a-zA-Z][\w-]*)((?:\s+[^\s/>"']+(?:\s*=\s*(?:"[^"]*"|'[^']*'|[^\s"'>]+))?)*)\s*\/?>/g;
    let lastIndex = 0;
    let match;
    while ((match = tagRegex.exec(html)) !== null) {
        const text = html.slice(lastIndex, match.index);
        if (text) append(new MiniText(decodeEntities(text)));
        lastIndex = tagRegex.lastIndex;
        if (match[0].startsWith("</")) {
            if (stack.length > 1) stack.pop();
            continue;
        }
        const element = ownerDocument.createElement(match[1]);
        applyAttributes(element, match[2] || "");
        append(element);
        if (!match[0].endsWith("/>") && !VOID_TAGS.has(element.tagName.toLowerCase())) stack.push(element);
    }
    const tail = html.slice(lastIndex);
    if (tail) append(new MiniText(decodeEntities(tail)));
    return fragment;
}

class MiniDocument {
    constructor() {
        this.root = new MiniElement("#document", this);
        this.root.nodeType = 9;
        this.root.ownerDocument = this;
        this.activeElement = null;
    }
    createElement(tagName) {
        return new MiniElement(tagName, this);
    }
    createTextNode(data) {
        return new MiniText(data);
    }
    querySelector(selector) {
        return this.querySelectorAll(selector)[0] || null;
    }
    querySelectorAll(selector) {
        const steps = parseSelector(selector);
        const results = [];
        const walk = (node) => {
            for (const child of node.childNodes) {
                if (child.nodeType !== 1) continue;
                if (elementMatches(child, steps)) results.push(child);
                walk(child);
            }
        };
        walk(this.root);
        return results;
    }
    getElementById(id) {
        return this.querySelector(`#${id}`);
    }
}

// ════════════════════════════════════════════════════════════════════════
// 测试脚手架：app.js 区域 / 宿主函数真实执行 + 可控 fetch
// ════════════════════════════════════════════════════════════════════════

function extractFunction(name) {
    const regex = new RegExp("(?:async\\s+)?function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}");
    const match = APP_SOURCE.match(regex);
    if (!match) throw new Error("Could not find " + name + " in app.js");
    return match[0];
}

function extractModuleRegion(source) {
    const start = source.indexOf(MODULE_BEGIN);
    const end = source.indexOf(MODULE_END);
    if (start === -1 || end === -1 || end < start) throw new Error("contact-profile-02-ui region missing in app.js");
    return source.slice(start, end + MODULE_END.length);
}

const MODULE_REGION = extractModuleRegion(APP_SOURCE);

function deferred() {
    let resolve;
    let reject;
    const promise = new Promise((res, rej) => {
        resolve = res;
        reject = rej;
    });
    return { promise, resolve, reject };
}

function abortError() {
    const error = new Error("The operation was aborted.");
    error.name = "AbortError";
    return error;
}

function waitWithSignal(promise, signal) {
    if (!signal) return promise;
    return new Promise((resolve, reject) => {
        const onAbort = () => reject(abortError());
        if (signal.aborted) {
            onAbort();
            return;
        }
        signal.addEventListener("abort", onAbort, { once: true });
        promise.then((value) => {
            signal.removeEventListener("abort", onAbort);
            resolve(value);
        }, (error) => {
            signal.removeEventListener("abort", onAbort);
            reject(error);
        });
    });
}

function makeResponse(status, body) {
    const text = body === undefined || body === null ? "" : JSON.stringify(body);
    return {
        ok: status >= 200 && status < 300,
        status,
        statusText: String(status),
        text: async () => text,
        json: async () => JSON.parse(text),
        clone: () => makeResponse(status, body)
    };
}

function createRouter() {
    const routes = [];
    const requests = [];
    function route(method, match, handler) {
        routes.push({ method: String(method).toUpperCase(), match, handler });
    }
    async function fetchImpl(url, options = {}) {
        const method = String(options.method || "GET").toUpperCase();
        const target = String(url);
        const record = { url: target, method, body: options.body, signal: options.signal };
        requests.push(record);
        const entry = routes.find((item) => item.method === method &&
            (typeof item.match === "string" ? target.includes(item.match) : item.match.test(target)));
        if (!entry) return makeResponse(404, { code: "NOT_FOUND", message: `no stub route for ${method} ${target}` });
        const result = await waitWithSignal(Promise.resolve().then(() => entry.handler(record)), record.signal);
        return makeResponse(result.status, result.body);
    }
    return { route, requests, fetchImpl };
}

function createFixture() {
    const doc = new MiniDocument();
    const html = doc.createElement("html");
    doc.root.appendChild(html);
    const body = doc.createElement("body");
    html.appendChild(body);
    const main = doc.createElement("div");
    main.className = "main";
    body.appendChild(main);
    const view = doc.createElement("section");
    view.setAttribute("id", "view-contacts");
    body.appendChild(view);
    const list = doc.createElement("div");
    list.setAttribute("id", "contactList");
    body.appendChild(list);
    const mobileStatus = doc.createElement("div");
    mobileStatus.setAttribute("id", "mobileContactStatus");
    body.appendChild(mobileStatus);
    const contactDetail = doc.createElement("div");
    contactDetail.setAttribute("id", "contactDetail");
    body.appendChild(contactDetail);
    const headActions = doc.createElement("div");
    headActions.setAttribute("id", "contactHeadActions");
    body.appendChild(headActions);
    const levelSelect = doc.createElement("select");
    levelSelect.setAttribute("id", "expertIndexLevel");
    levelSelect.value = "CANDIDATE";
    body.appendChild(levelSelect);
    return { doc, body, main, view, list, mobileStatus, contactDetail, headActions, levelSelect, failures: [] };
}

const flush = () => new Promise((resolve) => setImmediate(resolve));

function profileDto(overrides = {}) {
    const base = {
        expertId: "0000-0000-1111-2222",
        version: 3,
        identity: {
            talentCode: null, chineseName: null, birthDateText: null, documentNumber: null, sourceCountry: null
        },
        education: {
            graduatedSchool: null, schoolRanking: null, preChinaOrganization: null, organizationRanking: null,
            postdocOrganization: null, postdocPeriod: null, professionalTitle: null, administrativeTitles: null
        },
        technology: { industry: null, technicalExpertise: null, programCategories: null },
        matching: { researchAbility: null, companyMatchStatus: "NOT_MATCHED", matchedCompanies: [] },
        updatedBy: "ops",
        updatedAt: "2026-10-11T10:00:00.000+08:00"
    };
    const next = Object.assign({}, base, overrides);
    ["identity", "education", "technology", "matching"].forEach((group) => {
        next[group] = Object.assign({}, base[group], overrides[group] || {});
    });
    return next;
}

function mergeProfile(profile, patch) {
    const next = JSON.parse(JSON.stringify(profile));
    next[patch.group] = Object.assign({}, next[patch.group], patch.values);
    next.version = Number(profile.version) + 1;
    return next;
}

function createSandbox(fixture, router) {
    const sandbox = {
        document: fixture.doc,
        window: { innerWidth: 1440, matchMedia: () => ({ matches: false, addEventListener() {} }) },
        console,
        AbortController,
        setTimeout,
        clearTimeout,
        setImmediate,
        contextPath: "",
        fetch: router.fetchImpl,
        requestAnimationFrame: () => 0,
        handleAuthResponse: async (response) => {
            // task-modal-runtime.js 的真实语义：401 抛 UNAUTHORIZED、403 强制改密
            if (response.status === 401) throw new Error("UNAUTHORIZED");
            if (response.status === 403) {
                const data = await response.clone().json().catch(() => null);
                if (data && data.code === "PASSWORD_CHANGE_REQUIRED") throw new Error("PASSWORD_CHANGE_REQUIRED");
            }
            return response;
        },
        state: {
            view: "contacts",
            contacts: [],
            accounts: [{ accountCode: "ACC_A", senderEmail: "a@example.com", enabled: true }]
        },
        mobileContactPresentation: { generation: 1, mainScrollTop: 0, listScrollTop: 0, trigger: null },
        indexLevelLabels: { RAW: "原始专家", CANDIDATE: "候选专家", APPLICATION: "申请专家" },
        operatorStatusOptions: [],
        indexLevelOptions: [],
        $: (selector) => fixture.doc.querySelector(selector),
        $$: (selector) => fixture.doc.querySelectorAll(selector),
        // 宿主渲染依赖（断言只看 profile 宿主与请求，故为最小 stub）
        fetchExpertTagsFromEs: async () => ({ found: true, tags: [] }),
        loadMailSendOptions: async () => [],
        loadEmailAliases: () => {},
        mountExpertMaterialsInline: () => {},
        unmountExpertMaterialsHosts: () => {},
        updateSenderBindingDirtyState: () => {},
        showStatus: () => {},
        labelStatus: (value) => String(value == null ? "" : value),
        badge: () => "",
        backToListBtnHtml: () => "",
        renderDetailSubTabs: () => "",
        renderAcademicProfilePanel: () => "",
        renderExpertTagEditor: () => "",
        renderKeywords: () => "",
        renderNoContactMaterialsEmpty: () => "",
        renderMeetingSchedule: () => "",
        renderExpertMaterialRow: () => "",
        renderExpertDocuments: () => "",
        renderOperatorLogs: () => "",
        renderMailSendOptionGroups: () => "",
        renderManualAttentionBanner: () => "",
        optionsFromArray: () => "",
        formatStatusTransition: () => "",
        isMobileCoreViewport: () => false,
        finishContactDetailPresentation: () => {},
        failContactDetailPresentation: (generation, error) => {
            fixture.failures.push({ generation, error });
        }
    };
    vm.createContext(sandbox);
    vm.runInContext([
        "escapeHtml",
        "api",
        "isCurrentContactDetailPresentation",
        "beginContactDetailPresentation",
        "returnToMobileContactsList",
        "finishContactDetailPresentation"
    ].map(extractFunction).join("\n\n"), sandbox);
    vm.runInContext(MODULE_REGION, sandbox);
    vm.runInContext([
        "showExpertDetail",
        "loadContactDetail"
    ].map(extractFunction).join("\n\n"), sandbox);
    return sandbox;
}

function createHarness() {
    const fixture = createFixture();
    const router = createRouter();
    const sandbox = createSandbox(fixture, router);
    const mount = (context) => {
        fixture.contactDetail.innerHTML = `<div class="ec-profile" data-role="expert-manual-profile"></div>`;
        return sandbox.mountExpertManualProfile(fixture.contactDetail, context);
    };
    return { fixture, router, sandbox, mount };
}

function profileOf(harness) {
    return harness.fixture.contactDetail.querySelector(HOST_SELECTOR);
}

function cellOf(harness, name) {
    return profileOf(harness).querySelector(`[data-ec-value="${name}"]`);
}

function sectionOf(harness, group) {
    return profileOf(harness).querySelector(`.ec-section[data-ec-group="${group}"]`);
}

function editorOf(session, group) {
    return session.editors.get(group);
}

function openEditor(session, group) {
    const editor = editorOf(session, group);
    editor.editButton.dispatchEvent(new MiniEvent("click", { bubbles: true }));
    return editor;
}

function setField(editor, name, value) {
    const control = editor.dialog.querySelector(`[name="${name}"]`);
    assert.ok(control, `field ${name} must exist in the ${editor.group.key} editor`);
    control.value = value;
    return control;
}

function submitEditor(editor) {
    editor.form.dispatchEvent(new MiniEvent("submit", { bubbles: true }));
}

function patchRequests(harness) {
    return harness.router.requests.filter((request) => request.method === "PATCH");
}

function manualProfileGets(harness) {
    return harness.router.requests.filter((request) => request.method === "GET" && request.url.includes("/api/experts/manual-profile"));
}

function lastPatchPayload(harness) {
    const request = patchRequests(harness).slice(-1)[0];
    assert.ok(request, "a PATCH request must have been issued");
    return JSON.parse(request.body);
}

function walkElements(root, visit) {
    for (const child of root.childNodes) {
        if (child.nodeType !== 1) continue;
        visit(child);
        walkElements(child, visit);
    }
}

function classDeclared(className) {
    const escaped = className.replace(/[.*+?^${}()|[\]\\/-]/g, "\\$&");
    return new RegExp("\\." + escaped + "(?![\\w-])").test(STYLES_SOURCE);
}

// 供“新增 DOM 类名必须声明”断言使用：class 源文本可能与其它已声明规则无关。
function assertDeclaredClasses(scope, label) {
    const names = new Set();
    walkElements(scope, (element) => {
        for (const name of element.className.split(/\s+/).filter(Boolean)) names.add(name);
    });
    for (const name of names) {
        assert.ok(classDeclared(name), `${label}: class .${name} must be declared in styles.css`);
    }
    return names;
}

function assertContractMarkup(scope, label) {
    walkElements(scope, (element) => {
        assert.ok(CONTRACT_TAGS.has(element.tagName), `${label}: tag <${element.tagName.toLowerCase()}> is outside the S-1 contract`);
        assert.strictEqual(element.getAttribute("style"), null, `${label}: inline style is forbidden`);
        assert.strictEqual(element.getAttribute("id"), null, `${label}: id is forbidden (class + data-ec-* only)`);
    });
}

// ════════════════════════════════════════════════════════════════════════
// 契约与静态门禁
// ════════════════════════════════════════════════════════════════════════

function extractPlanS2Css() {
    const blocks = [...PLAN_SOURCE.matchAll(/```css\r?\n([\s\S]*?)\n```/g)].map((match) => match[1]);
    const block = blocks.find((item) => item.trimStart().startsWith(".ec-profile {"));
    if (!block) throw new Error("S-2 CSS block not found in " + PLAN_PATH);
    return block;
}

describe("contact profile 02 — S-2 / 缓存键 / 模块边界", () => {
    it("S-2：styles.css 保留逐字计划规则", () => {
        const block = extractPlanS2Css();
        assert.ok(STYLES_SOURCE.includes(block), "S-2 rules must be appended verbatim");
        assert.strictEqual(STYLES_SOURCE.split(block).length - 1, 1, "S-2 block must appear exactly once");
        for (const line of block.split("\n")) {
            assert.ok(STYLES_SOURCE.includes(line), `missing S-2 rule line: ${line}`);
        }
        for (const name of new Set([...block.matchAll(/\.([A-Za-z][\w-]*)/g)].map((match) => match[1]))) {
            assert.ok(classDeclared(name), `S-2 class .${name} must be declared in styles.css`);
        }
        // 新增 section/dialog 用实白，不靠 --panel-bg（半透明）；R-1 证据见 K-panel-bg-token-is-translucent。
        assert.ok(block.includes("background: #fff"), "new sections/editor must use opaque white");
    });

    it("缓存键：index.html 11 项统一为计划键，数量与顺序不变", () => {
        assert.match(CACHE_KEY, /^\d{8}-[a-z0-9-]+$/, "approved contact-records release key");
        const keys = [...INDEX_SOURCE.matchAll(/\?v=([0-9a-z-]+)/g)].map((match) => match[1]);
        assert.deepStrictEqual([...new Set(keys)], [CACHE_KEY], "all eleven keys must share the planned value");
        const ordered = [
            "styles.css", "expert-materials.css", "mailbox-chat.css", "meeting-confirmation.css", "world-clock.css",
            "trust-reply-workbench.js", "expert-materials.js", "meeting-confirmation.js", "mailbox-chat.js",
            "app.js", "world-clock.js"
        ];
        let cursor = -1;
        for (const asset of ordered) {
            const at = INDEX_SOURCE.indexOf(`${asset}?v=${CACHE_KEY}`);
            assert.ok(at > cursor, `${asset} must be registered with the planned key and keep its original order`);
            cursor = at;
        }
        assert.ok(INDEX_SOURCE.includes('<script src="task-modal-runtime.js"></script>'), "no asset may be added or re-keyed beyond the eleven");
    });

    it("模块边界：app.js 只有一个 02 区域，无全局监听、无 inline style", () => {
        assert.strictEqual(APP_SOURCE.split(MODULE_BEGIN).length - 1, 1);
        assert.strictEqual(APP_SOURCE.split(MODULE_END).length - 1, 1);
        assert.ok(!MODULE_REGION.includes("document.addEventListener"), "module must not register document-level listeners");
        assert.ok(!MODULE_REGION.includes("window.addEventListener"), "module must not register window-level listeners");
        assert.ok(!MODULE_REGION.includes('style="'), "module must not emit inline styles");
        assert.strictEqual(APP_SOURCE.split('class="ec-profile" data-role="expert-manual-profile"').length - 1, 2,
            "both contact panels must declare the ec-profile host exactly once each");
        // I-1：两条宿主都从同一个挂载入口进入（K-dom-stub-tests-hide-dangling-refs 的源文本回执）
        assert.strictEqual(APP_SOURCE.split("mountExpertManualProfile(contactDetail, {").length - 1, 2,
            "both hosts must mount through the shared entry point");
        assert.ok(extractFunction("beginContactDetailPresentation").includes("unmountExpertManualProfilePanels()"));
        assert.ok(extractFunction("returnToMobileContactsList").includes("unmountExpertManualProfilePanels()"));
    });
});

// ════════════════════════════════════════════════════════════════════════
// S-1 宿主与骨架
// ════════════════════════════════════════════════════════════════════════

describe("contact profile 02 — S-1 宿主与骨架", () => {
    it("无 contact 与有 contact 两条宿主都把 ec-profile 放在 contact panel 最前面且保留其后原节点", async () => {
        const harness = createHarness();
        harness.router.route("GET", "/api/experts/manual-profile", () => ({ status: 200, body: profileDto() }));
        harness.router.route("GET", "/api/expert-contacts/7", () => ({
            status: 200,
            body: {
                contact: {
                    id: 7, orcidId: "0000-0000-7777-8888", expertName: "联系人姓名",
                    expertEmail: "contact@example.com", currentIndexLevel: "CANDIDATE",
                    currentStatus: "INTRO_SENT", expertCountry: "美国"
                },
                statusHistory: []
            }
        }));
        harness.router.route("GET", "/api/expert-contacts/7/documents", () => ({ status: 200, body: [] }));
        harness.router.route("GET", "/api/operator-action-logs", () => ({ status: 200, body: { records: [] } }));
        harness.router.route("GET", "/api/expert-contacts/7/material-requests", () => ({ status: 200, body: [] }));
        harness.router.route("GET", "/api/mail/mailbox/by-expert", () => ({ status: 200, body: { groups: [] } }));

        // 无 contact 宿主（默认学术档案）
        await harness.sandbox.showExpertDetail({
            displayName: "ES 显示名", orcidId: "0000-0000-AAAA-1111", indexLevel: "RAW",
            nationality: "", country: "美国", keyword: "关键词甲", email: "a@example.com"
        }, harness.sandbox.mobileContactPresentation.generation);
        const noContactPanel = harness.fixture.contactDetail.querySelector('.detail-tab-panel[data-panel="contact"]');
        const noContactProfile = noContactPanel.querySelector(HOST_SELECTOR);
        assert.ok(noContactProfile, "academic-default host must mount the profile container");
        assert.strictEqual(noContactPanel.children[0], noContactProfile, "ec-profile must be the first child of the contact panel");
        assert.ok(noContactPanel.querySelector(".metadata-grid"), "原 panel 节点必须保留在 ec-profile 之后");

        // 有 contact 宿主（默认联系详情）
        harness.sandbox.state.contacts = [{
            orcidId: "0000-0000-7777-8888", displayName: "ES 显示名", nationality: "加拿大",
            country: "美国", keyword: "关键词乙", employment: "某大学", indexLevel: "CANDIDATE", contactId: 7
        }];
        harness.sandbox.state.selectedExpertOrcid = "0000-0000-7777-8888";
        await harness.sandbox.loadContactDetail(7, harness.sandbox.beginContactDetailPresentation());
        const contactPanel = harness.fixture.contactDetail.querySelector('.detail-tab-panel[data-panel="contact"]');
        const contactProfile = contactPanel.querySelector(HOST_SELECTOR);
        assert.ok(contactProfile, "contact-default host must mount the profile container");
        assert.strictEqual(contactPanel.children[0], contactProfile, "ec-profile must be the first child of the contact panel");
        assert.ok(contactPanel.querySelector(".metadata-grid"), "原 panel 节点必须保留在 ec-profile 之后");

        assert.deepStrictEqual(harness.fixture.failures, [], "neither host may fail while rendering the panel");
        assert.strictEqual(manualProfileGets(harness).length, 2, "each host must issue exactly one profile GET");
        assert.ok(manualProfileGets(harness)[0].url.includes("expertId=0000-0000-AAAA-1111"));
        assert.ok(manualProfileGets(harness)[1].url.includes("expertId=0000-0000-7777-8888"));
        assert.ok(manualProfileGets(harness)[0].url.includes("level=RAW"), "valid level hint is forwarded");
    });

    it("S-1 骨架：nav 四按钮、四组 section、四组 dialog、19 项 + 3 项只读，且只用契约 class/标签", async () => {
        const harness = createHarness();
        harness.router.route("GET", "/api/experts/manual-profile", () => ({ status: 200, body: profileDto() }));
        const session = harness.mount({ expertId: "0000-0000-1111-2222", generation: 1 });
        await session.ready;

        const profile = profileOf(harness);
        assert.ok(profile.classList.contains("ec-profile"));
        const jumps = profile.querySelectorAll("[data-ec-jump]").map((button) => button.dataset.ecJump);
        assert.deepStrictEqual(jumps, GROUP_ORDER, "nav 四按钮顺序必须为 matching/identity/education/technology");
        for (const button of profile.querySelectorAll("[data-ec-jump]")) {
            assert.strictEqual(button.tagName, "BUTTON");
            assert.strictEqual(button.getAttribute("type"), "button");
            assert.strictEqual(button.textContent, GROUP_LABELS[button.dataset.ecJump]);
        }
        assert.ok(profile.querySelector(".ec-nav"), "nav must carry the ec-nav contract class");
        assert.strictEqual(profile.querySelectorAll(".ec-section").length, 4);
        assert.strictEqual(profile.querySelectorAll(".ec-editor").length, 4);
        assert.ok(profile.querySelectorAll("dialog").every((dialog) => dialog.classList.contains("em-upload-dialog") && dialog.classList.contains("ec-editor")));
        // nav 只承载四个分组按钮，不新增顶层标签
        assert.strictEqual(profile.querySelector(".ec-nav").querySelectorAll("button").length, 4);

        for (const group of GROUP_ORDER) {
            const section = sectionOf(harness, group);
            assert.ok(section, `section ${group} must exist`);
            assert.strictEqual(section.querySelector(".ec-heading h3").textContent, GROUP_LABELS[group]);
            assert.strictEqual(section.querySelector("[data-ec-edit]").dataset.ecEdit, group);
            assert.strictEqual(section.querySelector("[data-ec-edit]").textContent, "编辑");
            assert.ok(section.querySelector(".ec-message").getAttribute("role") === "status");
            assert.ok(section.querySelector(".ec-error").getAttribute("role") === "alert");
            assert.strictEqual(section.querySelector(".ec-error").hidden, true);
        }

        // 19 项人工资料 + 3 项只读复用字段，每一项都有展示位与编辑控件
        for (const name of ALL_FIELDS) {
            assert.ok(cellOf(harness, name), `display cell for ${name} must exist`);
            assert.strictEqual(
                profile.querySelectorAll(`.ec-editor [name="${name}"]`).length, 1,
                `editor control for ${name} must exist exactly once`
            );
        }
        for (const name of READONLY_FIELDS) {
            assert.ok(cellOf(harness, name), `read-only cell for ${name} must exist`);
            assert.strictEqual(
                profile.querySelectorAll(`.ec-editor [name="${name}"]`).length, 0,
                `read-only field ${name} must never be editable`
            );
            const card = profile.querySelector(`[data-ec-readonly="${name}"]`);
            assert.ok(card && card.classList.contains("metadata-card"), `${name} must reuse the metadata-card component read-only`);
        }
        assert.strictEqual(profile.querySelectorAll("[data-ec-value]").length, ALL_FIELDS.length + READONLY_FIELDS.length);

        assertContractMarkup(profile, "profile shell");
        assertDeclaredClasses(profile, "profile shell");
    });

    it("S-1 细节：长文本 span-all/ec-span、maxlength 来自 01、企业每行一家、六档+未评估、进度三选一", async () => {
        const harness = createHarness();
        harness.router.route("GET", "/api/experts/manual-profile", () => ({ status: 200, body: profileDto() }));
        const session = harness.mount({ expertId: "0000-0000-1111-2222", generation: 1 });
        await session.ready;

        for (const name of ALL_FIELDS) {
            const card = cellOf(harness, name).parentNode;
            assert.strictEqual(
                card.classList.contains("span-all"), MULTILINE_FIELDS.includes(name),
                `${name} card span-all must follow the长文本 rule`
            );
            const control = profileOf(harness).querySelector(`.ec-editor [name="${name}"]`);
            const isSelect = Object.prototype.hasOwnProperty.call(SELECT_OPTIONS, name);
            const label = control.parentNode;
            assert.strictEqual(label.tagName, "LABEL", `${name} must use a wrapping label for accessibility`);
            assert.strictEqual(label.classList.contains("ec-span"), MULTILINE_FIELDS.includes(name),
                `${name} label ec-span must follow the长文本 rule`);
            assert.strictEqual(label.firstChild.textContent, cellOf(harness, name).parentNode.querySelector(".metadata-card-header").textContent,
                `${name} editor label and card header must share the 01 field label`);
            if (isSelect) {
                assert.strictEqual(control.tagName, "SELECT");
                continue;
            }
            assert.strictEqual(control.tagName, MULTILINE_FIELDS.includes(name) ? "TEXTAREA" : "INPUT");
            // 01 字段契约按 Unicode code point 计长（F-4 修复）：控件不得输出原生 maxlength——
            // 浏览器按 UTF-16 code unit 执行，会先于契约校验拒绝合法的星平面字符值。
            assert.strictEqual(control.getAttribute("maxlength"), null,
                `${name} must not carry native UTF-16 maxlength`);
        }
        // 上限元数据仍然生效：由 code-point 校验器消费（而不是交给浏览器）。
        const collector = extractFunction("expertManualProfileCollectValues");
        assert.ok(collector.includes("field.maxLength") && collector.includes("expertManualProfileCodePointLength"),
            "field-limit metadata must drive the code-point validator");

        const ability = profileOf(harness).querySelector('.ec-editor [name="researchAbility"]');
        assert.deepStrictEqual(
            ability.children.map((option) => [option.getAttribute("value"), option.textContent]),
            Object.entries(SELECT_OPTIONS.researchAbility),
            "科研能力六档 + 未评估（null）必须逐字符合需求"
        );
        const progress = profileOf(harness).querySelector('.ec-editor [name="companyMatchStatus"]');
        assert.deepStrictEqual(
            progress.children.map((option) => [option.getAttribute("value"), option.textContent]),
            Object.entries(SELECT_OPTIONS.companyMatchStatus),
            "匹配企业进度三选一必须逐字符合需求"
        );
        const companies = profileOf(harness).querySelector('.ec-editor [name="matchedCompanies"]');
        assert.strictEqual(companies.getAttribute("placeholder"), "每行一家企业");
        const birthDate = profileOf(harness).querySelector('.ec-editor [name="birthDateText"]');
        assert.strictEqual(birthDate.getAttribute("type"), null, "birthDateText must stay a text input (no forced complete date)");
        assert.strictEqual(birthDate.getAttribute("placeholder"), "YYYY 或 YYYY-MM-DD");
    });
});

// ════════════════════════════════════════════════════════════════════════
// I-1 双宿主同契约
// ════════════════════════════════════════════════════════════════════════

describe("contact profile 02 — I-1 双宿主同契约", () => {
    it("两条宿主都调用同一挂载入口，context 键集一致且各自身份正确", async () => {
        const harness = createHarness();
        harness.router.route("GET", "/api/experts/manual-profile", () => ({ status: 200, body: profileDto() }));
        harness.router.route("GET", "/api/expert-contacts/9", () => ({
            status: 200,
            body: {
                contact: {
                    id: 9, orcidId: "0000-0000-9999-0001", expertName: "联系人乙",
                    expertEmail: "b@example.com", currentIndexLevel: "APPLICATION", currentStatus: "WAITING_REPLY"
                },
                statusHistory: []
            }
        }));
        harness.router.route("GET", "/api/expert-contacts/9/documents", () => ({ status: 200, body: [] }));
        harness.router.route("GET", "/api/operator-action-logs", () => ({ status: 200, body: { records: [] } }));
        harness.router.route("GET", "/api/expert-contacts/9/material-requests", () => ({ status: 200, body: [] }));
        harness.router.route("GET", "/api/mail/mailbox/by-expert", () => ({ status: 200, body: { groups: [] } }));

        const calls = [];
        const mountReal = harness.sandbox.mountExpertManualProfile;
        harness.sandbox.mountExpertManualProfile = function (rootEl, context) {
            const session = mountReal(rootEl, context);
            calls.push({ rootEl, context, session });
            return session;
        };

        const generationA = harness.sandbox.mobileContactPresentation.generation;
        await harness.sandbox.showExpertDetail({
            displayName: "ES 甲", orcidId: "0000-0000-AAAA-0001", indexLevel: "RAW",
            nationality: "加拿大", country: "美国", keyword: "机器学习, NLP", contactId: ""
        }, generationA);

        harness.sandbox.state.contacts = [{
            orcidId: "0000-0000-9999-0001", displayName: "ES 乙", nationality: "德国",
            country: "德国", keyword: "光学", employment: "某研究所", indexLevel: "APPLICATION", contactId: 9
        }];
        harness.sandbox.state.selectedExpertOrcid = "0000-0000-9999-0001";
        const generationB = harness.sandbox.beginContactDetailPresentation();
        await harness.sandbox.loadContactDetail(9, generationB);

        assert.strictEqual(calls.length, 2, "each host must mount through the shared entry point");
        const contextKeys = Object.keys(calls[0].context).sort();
        assert.deepStrictEqual(Object.keys(calls[1].context).sort(), contextKeys,
            "the two hosts must pass the same context contract");
        assert.deepStrictEqual(contextKeys, ["contactId", "expertId", "generation", "keyword", "level", "name", "nationality"]);
        assert.strictEqual(calls[0].rootEl, harness.fixture.contactDetail);
        assert.strictEqual(calls[1].rootEl, harness.fixture.contactDetail);
        assert.strictEqual(calls[0].context.expertId, "0000-0000-AAAA-0001");
        assert.strictEqual(calls[0].context.contactId, "");
        assert.strictEqual(calls[0].context.level, "RAW");
        assert.strictEqual(calls[0].context.generation, generationA);
        assert.strictEqual(calls[1].context.expertId, "0000-0000-9999-0001");
        assert.strictEqual(calls[1].context.contactId, "9");
        assert.strictEqual(calls[1].context.level, "APPLICATION");
        assert.strictEqual(calls[1].context.name, "联系人乙");
        assert.strictEqual(calls[1].context.nationality, "德国");
        assert.strictEqual(calls[0].session.closed, true, "the contact-host mount must retire the academic-host session");

        await Promise.all(calls.map((call) => call.session.ready));
        assert.strictEqual(manualProfileGets(harness).length, 2);
        assert.strictEqual(manualProfileGets(harness)[0].signal.aborted, true, "the retired academic session read must be aborted");
        assert.strictEqual(manualProfileGets(harness)[1].url.includes("level=APPLICATION"), true);
    });

    it("19 项在两条宿主都能保存：四组 PATCH 键集恰好等于 01 白名单", async () => {
        const harness = createHarness();
        let stored = profileDto({ expertId: "0000-0000-1111-2222" });
        harness.router.route("GET", "/api/experts/manual-profile", () => ({ status: 200, body: JSON.parse(JSON.stringify(stored)) }));
        harness.router.route("PATCH", "/api/experts/manual-profile", (record) => {
            const payload = JSON.parse(record.body);
            stored = mergeProfile(stored, payload);
            return { status: 200, body: JSON.parse(JSON.stringify(stored)) };
        });
        const session = harness.mount({ expertId: "0000-0000-1111-2222", level: "RAW", generation: 1 });
        await session.ready;

        for (const group of GROUP_ORDER) {
            const editor = openEditor(session, group);
            assert.strictEqual(editor.dialog.open, true, `${group} dialog must open with showModal`);
            const groupSaved = {};
            for (const name of GROUP_FIELDS[group]) {
                if (name === "researchAbility") {
                    groupSaved[name] = "STRONG";
                    setField(editor, name, "STRONG");
                } else if (name === "companyMatchStatus") {
                    groupSaved[name] = "MATCHED";
                    setField(editor, name, "MATCHED");
                } else if (name === "matchedCompanies") {
                    groupSaved[name] = ["企业甲", "企业乙"];
                    setField(editor, name, "企业甲\n \n企业乙\n企业甲");
                } else if (name === "birthDateText") {
                    groupSaved[name] = "1980";
                    setField(editor, name, "1980");
                } else {
                    groupSaved[name] = `${name}-样例`;
                    setField(editor, name, `${name}-样例`);
                }
            }
            submitEditor(editor);
            await flush();
            const payload = lastPatchPayload(harness);
            assert.strictEqual(payload.group, group);
            assert.strictEqual(payload.expertId, "0000-0000-1111-2222");
            assert.strictEqual(payload.level, "RAW");
            assert.strictEqual(payload.expectedVersion, 3 + GROUP_ORDER.indexOf(group));
            assert.deepStrictEqual(Object.keys(payload.values).sort(), GROUP_FIELDS[group].slice().sort(),
                `${group} PATCH must carry exactly the 01 whitelist keys`);
            assert.deepStrictEqual(payload.values, groupSaved, `${group} values must round-trip unchanged`);
            assert.strictEqual(editor.summary.textContent, "已保存");
            editor.dialog.close();
            // 只有 200 之后才更新显示
            assert.strictEqual(cellOf(harness, GROUP_FIELDS[group][0]).textContent,
                group === "matching" ? "强" : groupSaved[GROUP_FIELDS[group][0]]);
        }
        assert.strictEqual(patchRequests(harness).length, 4, "one PATCH per group save");
        assert.strictEqual(cellOf(harness, "matchedCompanies").textContent, "企业甲\n企业乙");
        assert.strictEqual(session.version, 7);
    });
});

// ════════════════════════════════════════════════════════════════════════
// I-2 请求与节点生命周期
// ════════════════════════════════════════════════════════════════════════

describe("contact profile 02 — I-2 请求与节点生命周期", () => {
    it("甲响应延迟时切乙：乙不出现甲数据，甲的读请求被中止", async () => {
        const harness = createHarness();
        const slow = deferred();
        harness.router.route("GET", "/api/experts/manual-profile", (record) => {
            if (record.url.includes("AAAA")) return slow.promise;
            return { status: 200, body: profileDto({ expertId: "0000-0000-BBBB", identity: { talentCode: "B-01", chineseName: "乙资料" }, version: 5 }) };
        });

        const sessionA = harness.mount({ expertId: "0000-0000-AAAA", generation: harness.sandbox.mobileContactPresentation.generation });
        await flush();
        assert.strictEqual(cellOf(harness, "talentCode").textContent, "—", "loading state must not claim empty values");

        const generationB = harness.sandbox.beginContactDetailPresentation();
        const sessionB = harness.mount({ expertId: "0000-0000-BBBB", generation: generationB });
        await sessionB.ready;
        assert.strictEqual(sessionA.closed, true, "switching must close the previous session");
        assert.strictEqual(manualProfileGets(harness)[0].signal.aborted, true, "the previous read request must be aborted");

        slow.resolve({ status: 200, body: profileDto({ expertId: "0000-0000-AAAA", identity: { talentCode: "A-99", chineseName: "甲资料" }, version: 9 }) });
        await flush();

        assert.strictEqual(cellOf(harness, "talentCode").textContent, "B-01");
        assert.strictEqual(cellOf(harness, "chineseName").textContent, "乙资料");
        assert.strictEqual(sessionB.version, 5, "甲响应不得改写乙的 version");
        for (const cell of profileOf(harness).querySelectorAll("[data-ec-value]")) {
            assert.ok(!cell.textContent.includes("甲资料") && !cell.textContent.includes("A-99"),
                "slow expert-A response must never reach expert-B DOM");
        }
        for (const section of profileOf(harness).querySelectorAll(".ec-section")) {
            assert.strictEqual(section.querySelector(".ec-error").hidden, true, "no stale error may appear on the new expert");
        }
    });

    it("切专家/返回列表：关闭弹窗并中止读请求；已发出的保存不被中止且由下次 GET 确认", async () => {
        const harness = createHarness();
        let stored = profileDto({ expertId: "0000-0000-1111-2222" });
        harness.router.route("GET", "/api/experts/manual-profile", () => ({ status: 200, body: JSON.parse(JSON.stringify(stored)) }));
        const slowPatch = deferred();
        harness.router.route("PATCH", "/api/experts/manual-profile", async (record) => {
            const payload = JSON.parse(record.body);
            const result = await slowPatch.promise;
            stored = mergeProfile(stored, payload);
            return { status: result.status, body: JSON.parse(JSON.stringify(stored)) };
        });

        const session = harness.mount({ expertId: "0000-0000-1111-2222", generation: harness.sandbox.mobileContactPresentation.generation });
        await session.ready;
        const editor = openEditor(session, "identity");
        setField(editor, "chineseName", "张三");
        submitEditor(editor);
        await flush();
        const patch = patchRequests(harness)[0];
        assert.ok(patch, "PATCH must have been issued");

        harness.sandbox.beginContactDetailPresentation();
        assert.strictEqual(session.closed, true);
        assert.strictEqual(editor.dialog.open, false, "switching experts must close the editor dialog");
        assert.ok(!patch.signal || patch.signal.aborted !== true, "an issued save must not be aborted by navigation");

        slowPatch.resolve({ status: 200 });
        await flush();
        // 不宣称回滚、也不在旧 DOM 上继续写：下一步 GET 才确认服务端事实
        assert.strictEqual(session.closed, true);

        const next = harness.mount({ expertId: "0000-0000-1111-2222", generation: harness.sandbox.mobileContactPresentation.generation });
        await next.ready;
        assert.strictEqual(next.version, 4, "the next GET must confirm the saved version");
        assert.strictEqual(cellOf(harness, "chineseName").textContent, "张三", "the next GET must confirm the saved value");
    });

    it("同一会话内重复读取：旧读响应不得覆盖新读结果", async () => {
        const harness = createHarness();
        const slowFirst = deferred();
        let reads = 0;
        harness.router.route("GET", "/api/experts/manual-profile", () => {
            reads += 1;
            if (reads === 1) return slowFirst.promise;
            return { status: 200, body: profileDto({ identity: { talentCode: "新读结果" }, version: 6 }) };
        });
        const session = harness.mount({ expertId: "0000-0000-1111-2222", generation: 1 });
        await flush();
        const second = harness.sandbox.loadExpertManualProfile(session);
        await second;
        assert.strictEqual(cellOf(harness, "talentCode").textContent, "新读结果");
        assert.strictEqual(manualProfileGets(harness)[0].signal.aborted, true, "the previous read must be aborted");
        slowFirst.resolve({ status: 200, body: profileDto({ identity: { talentCode: "旧读结果" }, version: 1 }) });
        await flush();
        assert.strictEqual(cellOf(harness, "talentCode").textContent, "新读结果");
        assert.strictEqual(session.version, 6);
    });

    it("returnToMobileContactsList 同样收口：中止读请求并关闭弹窗", async () => {
        const harness = createHarness();
        const slow = deferred();
        harness.router.route("GET", "/api/experts/manual-profile", () => slow.promise);
        const session = harness.mount({ expertId: "0000-0000-1111-2222", generation: harness.sandbox.mobileContactPresentation.generation });
        await flush();
        harness.sandbox.returnToMobileContactsList();
        assert.strictEqual(session.closed, true);
        assert.strictEqual(manualProfileGets(harness)[0].signal.aborted, true);
        slow.resolve({ status: 200, body: profileDto() });
        await flush();
        assert.strictEqual(cellOf(harness, "talentCode").textContent, "—", "stale response must not render after returning to the list");
    });

    it("重复挂载与卸载后没有重复事件、悬挂弹窗或额外请求", async () => {
        const harness = createHarness();
        harness.router.route("GET", "/api/experts/manual-profile", () => ({ status: 200, body: profileDto({ technology: { industry: "半导体" } }) }));

        const first = harness.mount({ expertId: "0000-0000-1111-2222", generation: harness.sandbox.mobileContactPresentation.generation });
        await first.ready;
        const staleDialog = editorOf(first, "identity").dialog;
        const staleForm = editorOf(first, "identity").form;
        openEditor(first, "identity");
        assert.strictEqual(staleDialog.open, true);

        const second = harness.mount({ expertId: "0000-0000-1111-2222", generation: harness.sandbox.mobileContactPresentation.generation });
        await second.ready;
        assert.strictEqual(first.closed, true, "re-mounting the same container must retire the previous session");
        assert.strictEqual(staleDialog.open, false, "no dangling dialog may stay open across re-mount");
        assert.strictEqual(manualProfileGets(harness).length, 2, "one GET per mount");

        // 旧宿主的事件不再生效：提交旧表单不会发 PATCH
        staleForm.dispatchEvent(new MiniEvent("submit", { bubbles: true }));
        await flush();
        assert.strictEqual(patchRequests(harness).length, 0, "detached listeners must not issue requests");

        // 新宿主的事件恰好一份：nav 跳转只滚动一次
        const section = sectionOf(harness, "technology");
        profileOf(harness).querySelector('[data-ec-jump="technology"]').click();
        assert.strictEqual(section.scrollCalls.length, 1, "nav jump must scroll exactly once");

        harness.sandbox.unmountExpertManualProfilePanels(harness.fixture.contactDetail);
        assert.strictEqual(second.closed, true);
        section.scrollCalls.length = 0;
        profileOf(harness).querySelector('[data-ec-jump="technology"]').click();
        assert.strictEqual(section.scrollCalls.length, 0, "unmount must remove listeners");
    });
});

// ════════════════════════════════════════════════════════════════════════
// I-3 加载与保存
// ════════════════════════════════════════════════════════════════════════

describe("contact profile 02 — I-3 加载与保存", () => {
    it("GET 失败：显示重试、禁编辑、不把失败当空档案；重试成功后恢复", async () => {
        const harness = createHarness();
        let failing = true;
        harness.router.route("GET", "/api/experts/manual-profile", () => failing
            ? { status: 500, body: { code: "PROFILE_STORE_FAILED", message: "人工档案保存失败，请稍后重试", detail: null } }
            : { status: 200, body: profileDto({ identity: { talentCode: "T-1" } }) });
        const session = harness.mount({ expertId: "0000-0000-1111-2222", generation: harness.sandbox.mobileContactPresentation.generation });
        await session.ready;

        for (const group of GROUP_ORDER) {
            const section = sectionOf(harness, group);
            const error = section.querySelector(".ec-error");
            assert.strictEqual(error.hidden, false, `${group} must show the load failure`);
            assert.strictEqual(error.textContent.trim().startsWith("人工档案保存失败，请稍后重试"), true);
            assert.strictEqual(error.querySelector("[data-ec-retry]").textContent, "重试");
            assert.strictEqual(error.querySelector("[data-ec-retry]").classList.contains("button"), true);
            assert.strictEqual(editorOf(session, group).editButton.disabled, true, "editing must be disabled after a failed GET");
        }
        assert.strictEqual(cellOf(harness, "talentCode").textContent, "—", "a failed GET must not be rendered as an empty profile");
        assert.strictEqual(session.loaded, false);

        // 点击禁用中的编辑按钮不得打开弹窗
        openEditor(session, "identity");
        assert.strictEqual(editorOf(session, "identity").dialog.open, false);

        failing = false;
        sectionOf(harness, "matching").querySelector("[data-ec-retry]").click();
        await flush();
        assert.strictEqual(manualProfileGets(harness).length, 2, "retry must issue a fresh GET");
        assert.strictEqual(cellOf(harness, "talentCode").textContent, "T-1");
        assert.strictEqual(sectionOf(harness, "matching").querySelector(".ec-error").hidden, true);
        assert.strictEqual(sectionOf(harness, "matching").querySelector("[data-ec-retry]"), null, "retry affordance must be removed once loaded");
        assert.strictEqual(editorOf(session, "matching").editButton.disabled, false);
        assert.strictEqual(session.loaded, true);
    });

    it("缺少 expertId：不请求、提示缺少专家标识、禁编辑", async () => {
        const harness = createHarness();
        harness.router.route("GET", "/api/experts/manual-profile", () => ({ status: 200, body: profileDto() }));
        const session = harness.mount({ expertId: "", generation: 1 });
        await session.ready;
        assert.strictEqual(manualProfileGets(harness).length, 0, "no identity means no request");
        assert.strictEqual(sectionOf(harness, "identity").querySelector(".ec-error").textContent, "缺少专家标识，无法保存");
        assert.strictEqual(sectionOf(harness, "identity").querySelector("[data-ec-retry]"), null);
        assert.strictEqual(editorOf(session, "identity").editButton.disabled, true);
    });

    it("保存 400：保留输入并显示服务端消息，不更新显示", async () => {
        const harness = createHarness();
        let stored = profileDto({ expertId: "0000-0000-1111-2222", identity: { chineseName: "旧译名" } });
        harness.router.route("GET", "/api/experts/manual-profile", () => ({ status: 200, body: JSON.parse(JSON.stringify(stored)) }));
        harness.router.route("PATCH", "/api/experts/manual-profile", () => ({
            status: 400, body: { code: "INVALID_REQUEST", message: "出生日期只接受 YYYY 或 YYYY-MM-DD", detail: null }
        }));
        const session = harness.mount({ expertId: "0000-0000-1111-2222", generation: 1 });
        await session.ready;
        const editor = openEditor(session, "identity");
        setField(editor, "chineseName", "新译名");
        submitEditor(editor);
        await flush();

        assert.strictEqual(editor.errorEl.hidden, false);
        assert.strictEqual(editor.errorEl.textContent, "出生日期只接受 YYYY 或 YYYY-MM-DD");
        assert.strictEqual(editor.dialog.open, true, "a failed save must keep the editor open");
        assert.strictEqual(editor.dialog.querySelector('[name="chineseName"]').value, "新译名", "400 must preserve user input");
        assert.strictEqual(cellOf(harness, "chineseName").textContent, "旧译名", "display must only change after a 200");
        assert.strictEqual(session.version, 3);
        assert.strictEqual(patchRequests(harness).length, 1);
    });

    it("保存 409：固定文案、保留输入、不自动覆盖重试", async () => {
        const harness = createHarness();
        const stored = profileDto({ expertId: "0000-0000-1111-2222", identity: { chineseName: "他人改过的译名" } });
        harness.router.route("GET", "/api/experts/manual-profile", () => ({ status: 200, body: JSON.parse(JSON.stringify(stored)) }));
        harness.router.route("PATCH", "/api/experts/manual-profile", () => ({
            status: 409, body: { code: "PROFILE_CONFLICT", message: "资料已被更新，请重新加载后再编辑", detail: null }
        }));
        const session = harness.mount({ expertId: "0000-0000-1111-2222", generation: 1 });
        await session.ready;
        const editor = openEditor(session, "identity");
        setField(editor, "chineseName", "我的译名");
        submitEditor(editor);
        await flush();

        assert.strictEqual(editor.errorEl.textContent, "资料已被更新，请重新加载后再编辑");
        assert.strictEqual(editor.dialog.querySelector('[name="chineseName"]').value, "我的译名");
        assert.strictEqual(sectionOf(harness, "identity").querySelector(".ec-error").textContent, "资料已被更新，请重新加载后再编辑");
        assert.strictEqual(patchRequests(harness).length, 1, "409 must never be retried automatically");
        assert.strictEqual(cellOf(harness, "chineseName").textContent, "他人改过的译名", "409 must not overwrite他人数据");
        assert.strictEqual(editor.summary.textContent, "");

        // 重新加载后可保存（新 version）
        const reload = harness.mount({ expertId: "0000-0000-1111-2222", generation: 1 });
        await reload.ready;
        assert.strictEqual(reload.version, 3);
    });

    it("保存 401：提示登录失效，且不把失败当成功", async () => {
        const harness = createHarness();
        const stored = profileDto({ expertId: "0000-0000-1111-2222", identity: { chineseName: "旧译名" } });
        harness.router.route("GET", "/api/experts/manual-profile", () => ({ status: 200, body: JSON.parse(JSON.stringify(stored)) }));
        harness.router.route("PATCH", "/api/experts/manual-profile", () => ({ status: 401, body: { code: "UNAUTHORIZED", message: "未登录", detail: null } }));
        const session = harness.mount({ expertId: "0000-0000-1111-2222", generation: 1 });
        await session.ready;
        const editor = openEditor(session, "identity");
        setField(editor, "chineseName", "新译名");
        submitEditor(editor);
        await flush();

        assert.strictEqual(editor.errorEl.textContent, "登录已失效，请重新登录后再试");
        assert.strictEqual(editor.summary.textContent, "");
        assert.strictEqual(cellOf(harness, "chineseName").textContent, "旧译名");
        assert.strictEqual(editor.dialog.open, true);
        assert.strictEqual(editor.form.querySelector('button[type="submit"]').disabled, false, "controls must be restored after the response");
    });

    it("保存 503 与网络失败：分别给出可判断的结果提示", async () => {
        const harness = createHarness();
        const stored = profileDto({ expertId: "0000-0000-1111-2222" });
        harness.router.route("GET", "/api/experts/manual-profile", () => ({ status: 200, body: JSON.parse(JSON.stringify(stored)) }));
        let mode = "503";
        harness.router.route("PATCH", "/api/experts/manual-profile", () => {
            if (mode === "503") return { status: 503, body: { code: "EXPERT_LOOKUP_UNAVAILABLE", message: "暂时无法校验专家身份，请稍后重试", detail: null } };
            throw new Error("network down");
        });
        const session = harness.mount({ expertId: "0000-0000-1111-2222", generation: 1 });
        await session.ready;

        const first = openEditor(session, "technology");
        setField(first, "industry", "半导体");
        submitEditor(first);
        await flush();
        assert.strictEqual(first.errorEl.textContent, "暂时无法校验专家身份，请稍后重试");

        mode = "network";
        submitEditor(first);
        await flush();
        assert.strictEqual(first.errorEl.textContent, "保存结果未知，请重新加载确认");
        assert.strictEqual(sectionOf(harness, "technology").querySelector(".ec-error").textContent, "保存结果未知，请重新加载确认");
        assert.strictEqual(first.dialog.open, true, "unknown save result keeps the editor open for inspection");
    });

    it("取消/Escape/关闭都不发 PATCH，焦点归还触发按钮；保存期间禁保存与关闭", async () => {
        const harness = createHarness();
        const stored = profileDto({ expertId: "0000-0000-1111-2222", education: { professionalTitle: "研究员" } });
        harness.router.route("GET", "/api/experts/manual-profile", () => ({ status: 200, body: JSON.parse(JSON.stringify(stored)) }));
        const slowPatch = deferred();
        harness.router.route("PATCH", "/api/experts/manual-profile", async (record) => {
            await slowPatch.promise;
            return { status: 200, body: mergeProfile(JSON.parse(JSON.stringify(stored)), JSON.parse(record.body)) };
        });
        const session = harness.mount({ expertId: "0000-0000-1111-2222", generation: 1 });
        await session.ready;
        const editor = editorOf(session, "education");

        editor.editButton.dispatchEvent(new MiniEvent("click", { bubbles: true }));
        assert.strictEqual(editor.dialog.open, true);
        setField(editor, "professionalTitle", "临时值");
        editor.dialog.dispatchEvent(new MiniEvent("cancel", { bubbles: false }));
        assert.strictEqual(editor.dialog.open, false, "Escape must close without saving");
        assert.strictEqual(patchRequests(harness).length, 0, "Escape must never issue a PATCH");
        assert.strictEqual(editor.editButton.focused, true, "focus must return to the triggering button");

        openEditor(session, "education");
        assert.strictEqual(editor.dialog.querySelector('[name="professionalTitle"]').value, "研究员", "cancelled edits must be discarded");
        editor.dialog.querySelector("[data-ec-cancel]").click();
        assert.strictEqual(editor.dialog.open, false);
        assert.strictEqual(patchRequests(harness).length, 0);

        openEditor(session, "education");
        editor.dialog.querySelector(".modal-close-btn").click();
        assert.strictEqual(editor.dialog.open, false);
        assert.strictEqual(patchRequests(harness).length, 0);

        // 保存期间禁保存与关闭，且双击只发一个 PATCH
        openEditor(session, "education");
        setField(editor, "professionalTitle", "研究员甲");
        submitEditor(editor);
        await flush();
        assert.strictEqual(patchRequests(harness).length, 1);
        assert.strictEqual(editor.form.querySelector('button[type="submit"]').disabled, true);
        assert.strictEqual(editor.dialog.querySelector(".modal-close-btn").disabled, true);
        assert.strictEqual(editor.dialog.querySelector("[data-ec-cancel]").disabled, true);
        submitEditor(editor);
        submitEditor(editor);
        await flush();
        assert.strictEqual(patchRequests(harness).length, 1, "saving must accept at most one PATCH");
        editor.dialog.dispatchEvent(new MiniEvent("cancel", { bubbles: false }));
        assert.strictEqual(editor.dialog.open, true, "a busy editor must not close");

        slowPatch.resolve({ status: 200 });
        await flush();
        assert.strictEqual(editor.dialog.open, true);
        assert.strictEqual(editor.form.querySelector('button[type="submit"]').disabled, false, "controls must be restored after the response");
        assert.strictEqual(editor.summary.textContent, "已保存");
        assert.strictEqual(sectionOf(harness, "education").querySelector(".ec-message").textContent, "已保存");
        assert.strictEqual(cellOf(harness, "professionalTitle").textContent, "研究员甲");
        assert.strictEqual(session.version, 4);
    });
});

// ════════════════════════════════════════════════════════════════════════
// I-4 字段、凭证与输入边界
// ════════════════════════════════════════════════════════════════════════

describe("contact profile 02 — I-4 字段、凭证与输入边界", () => {
    it("证件仅末四位（≤4 位全遮罩），编辑弹窗显明文，PATCH 提交原文", async () => {
        const harness = createHarness();
        let stored = profileDto({ expertId: "0000-0000-1111-2222", identity: { documentNumber: "TEST001234" } });
        harness.router.route("GET", "/api/experts/manual-profile", () => ({ status: 200, body: JSON.parse(JSON.stringify(stored)) }));
        harness.router.route("PATCH", "/api/experts/manual-profile", (record) => {
            const payload = JSON.parse(record.body);
            stored = mergeProfile(stored, payload);
            return { status: 200, body: JSON.parse(JSON.stringify(stored)) };
        });
        const session = harness.mount({ expertId: "0000-0000-1111-2222", generation: 1 });
        await session.ready;
        assert.strictEqual(cellOf(harness, "documentNumber").textContent, "••••••1234", "详情只显示末四位");

        const editor = openEditor(session, "identity");
        const control = editor.dialog.querySelector('[name="documentNumber"]');
        assert.strictEqual(control.value, "TEST001234", "编辑弹窗显示原文");
        submitEditor(editor);
        await flush();
        assert.strictEqual(lastPatchPayload(harness).values.documentNumber, "TEST001234", "PATCH 必须提交原文而不是遮罩值");
        assert.strictEqual(cellOf(harness, "documentNumber").textContent, "••••••1234");

        // ≤4 位全遮罩
        stored = profileDto({ expertId: "0000-0000-1111-2222", identity: { documentNumber: "1234" } });
        const again = harness.mount({ expertId: "0000-0000-1111-2222", generation: 1 });
        await again.ready;
        assert.strictEqual(cellOf(harness, "documentNumber").textContent, "••••");
        stored = profileDto({ expertId: "0000-0000-1111-2222", identity: { documentNumber: "12" } });
        const third = harness.mount({ expertId: "0000-0000-1111-2222", generation: 1 });
        await third.ready;
        assert.strictEqual(cellOf(harness, "documentNumber").textContent, "••");
    });

    it("恶意 HTML 只作为纯文本显示（含只读姓名/关键词），不会构造任何元素", async () => {
        const harness = createHarness();
        const evil = '<img src=x onerror="alert(1)">';
        harness.router.route("GET", "/api/experts/manual-profile", () => ({
            status: 200,
            body: profileDto({
                expertId: "0000-0000-1111-2222",
                identity: { talentCode: evil, chineseName: "<b>加粗</b>" },
                technology: { industry: "</div><script>alert(2)</script>" }
            })
        }));
        const session = harness.mount({
            expertId: "0000-0000-1111-2222",
            name: evil,
            nationality: "<i>x</i>",
            keyword: "<script>alert(3)</script>",
            generation: 1
        });
        await session.ready;

        assert.strictEqual(cellOf(harness, "talentCode").textContent, evil);
        assert.strictEqual(cellOf(harness, "chineseName").textContent, "<b>加粗</b>");
        assert.strictEqual(cellOf(harness, "industry").textContent, "</div><script>alert(2)</script>");
        assert.strictEqual(cellOf(harness, "expertName").textContent, evil);
        assert.strictEqual(cellOf(harness, "nationality").textContent, "<i>x</i>");
        assert.strictEqual(cellOf(harness, "keyword").textContent, "<script>alert(3)</script>");
        assert.strictEqual(profileOf(harness).querySelectorAll("img").length, 0, "no element may be built from user text");
        assert.strictEqual(profileOf(harness).querySelectorAll("script").length, 0);
        assert.strictEqual(profileOf(harness).querySelectorAll("b").length, 0);
        assert.strictEqual(profileOf(harness).querySelectorAll("i").length, 0);
        assert.strictEqual(cellOf(harness, "talentCode").childNodes.length, 1, "the value must be a single text node");
        assertContractMarkup(profileOf(harness), "profile with hostile values");
    });

    it("只读字段不参与 PATCH；country 不顶替缺失的 nationality；空值显示未填写/未评估/未匹配", async () => {
        const harness = createHarness();
        harness.router.route("GET", "/api/experts/manual-profile", () => ({ status: 200, body: profileDto() }));
        harness.router.route("PATCH", "/api/experts/manual-profile", (record) => ({ status: 200, body: mergeProfile(profileDto(), JSON.parse(record.body)) }));
        const session = harness.mount({
            expertId: "0000-0000-1111-2222",
            name: "",
            nationality: "",
            keyword: "",
            generation: 1
        });
        await session.ready;

        assert.strictEqual(cellOf(harness, "expertName").textContent, "未填写");
        assert.strictEqual(cellOf(harness, "nationality").textContent, "未填写");
        assert.strictEqual(cellOf(harness, "keyword").textContent, "未填写");
        assert.strictEqual(cellOf(harness, "talentCode").textContent, "未填写");
        assert.strictEqual(cellOf(harness, "researchAbility").textContent, "未评估");
        assert.strictEqual(cellOf(harness, "companyMatchStatus").textContent, "未匹配");
        assert.strictEqual(cellOf(harness, "matchedCompanies").textContent, "未填写");

        const editor = openEditor(session, "identity");
        submitEditor(editor);
        await flush();
        const payload = lastPatchPayload(harness);
        assert.deepStrictEqual(Object.keys(payload.values).sort(), GROUP_FIELDS.identity.slice().sort());
        for (const name of READONLY_FIELDS) {
            assert.ok(!Object.prototype.hasOwnProperty.call(payload.values, name), `${name} must never be submitted`);
        }
        for (const name of GROUP_FIELDS.identity) {
            assert.strictEqual(payload.values[name], null, `empty ${name} must be submitted as null (清空)`);
        }
        const matching = openEditor(session, "matching");
        submitEditor(matching);
        await flush();
        const matchingPayload = lastPatchPayload(harness);
        assert.strictEqual(matchingPayload.values.companyMatchStatus, "NOT_MATCHED", "空白匹配进度 = 默认未匹配");
        assert.deepStrictEqual(matchingPayload.values.matchedCompanies, [], "空企业数组等价于清空");
    });

    it("01 字段边界在页面输入同样适用：出生日期、MATCHED 需企业、超长提示", async () => {
        const harness = createHarness();
        harness.router.route("GET", "/api/experts/manual-profile", () => ({ status: 200, body: profileDto() }));
        harness.router.route("PATCH", "/api/experts/manual-profile", (record) => ({ status: 200, body: mergeProfile(profileDto(), JSON.parse(record.body)) }));
        const session = harness.mount({ expertId: "0000-0000-1111-2222", generation: 1 });
        await session.ready;

        const identity = openEditor(session, "identity");
        setField(identity, "birthDateText", "1980-02-30");
        submitEditor(identity);
        await flush();
        assert.strictEqual(identity.errorEl.textContent, "出生日期必须是真实存在的日期");
        assert.strictEqual(patchRequests(harness).length, 0, "invalid birth date must not be submitted");

        setField(identity, "birthDateText", "19-80");
        submitEditor(identity);
        await flush();
        assert.strictEqual(identity.errorEl.textContent, "出生日期只接受 YYYY 或 YYYY-MM-DD");
        assert.strictEqual(patchRequests(harness).length, 0);

        setField(identity, "birthDateText", "1980-01-01");
        setField(identity, "talentCode", "X".repeat(65));
        submitEditor(identity);
        await flush();
        assert.strictEqual(identity.errorEl.textContent, "人才编号最多 64 个字符");
        assert.strictEqual(patchRequests(harness).length, 0);

        setField(identity, "talentCode", "T-1");
        submitEditor(identity);
        await flush();
        assert.strictEqual(patchRequests(harness).length, 1);
        assert.strictEqual(lastPatchPayload(harness).values.birthDateText, "1980-01-01");

        const matching = openEditor(session, "matching");
        setField(matching, "companyMatchStatus", "MATCHED");
        setField(matching, "matchedCompanies", "\n  \n");
        submitEditor(matching);
        await flush();
        assert.strictEqual(matching.errorEl.textContent, "匹配上时必须至少填写一家企业");
        assert.strictEqual(patchRequests(harness).length, 1, "MATCHED without companies must not be submitted");

        setField(matching, "matchedCompanies", "企业甲\n企业乙");
        submitEditor(matching);
        await flush();
        assert.strictEqual(patchRequests(harness).length, 2);
        assert.deepStrictEqual(lastPatchPayload(harness).values.matchedCompanies, ["企业甲", "企业乙"]);
    });

    it("01 上限按 Unicode code point 执行：星平面字符到上限接受、超一个拒绝且不发 PATCH", async () => {
        const harness = createHarness();
        harness.router.route("GET", "/api/experts/manual-profile", () => ({ status: 200, body: profileDto() }));
        harness.router.route("PATCH", "/api/experts/manual-profile", (record) => ({ status: 200, body: mergeProfile(profileDto(), JSON.parse(record.body)) }));
        const session = harness.mount({ expertId: "0000-0000-1111-2222", generation: 1 });
        await session.ready;
        const limit = FIELD_LIMITS.talentCode;
        const astral = "𝒜"; // U+1D49C：1 个 code point / 2 个 UTF-16 code unit
        const atLimit = astral.repeat(limit);

        const editor = openEditor(session, "identity");
        const control = setField(editor, "talentCode", atLimit);
        assert.strictEqual(control.value.length, limit * 2, "astral value must be longer than the limit in UTF-16 units");
        assert.strictEqual(control.getAttribute("maxlength"), null, "native UTF-16 length enforcement must not exist");
        submitEditor(editor);
        await flush();
        assert.strictEqual(patchRequests(harness).length, 1, "a value at the code-point limit must be accepted");
        assert.strictEqual(lastPatchPayload(harness).values.talentCode, atLimit);

        setField(editor, "talentCode", astral.repeat(limit + 1));
        submitEditor(editor);
        await flush();
        assert.strictEqual(editor.errorEl.textContent, `人才编号最多 ${limit} 个字符`);
        assert.strictEqual(patchRequests(harness).length, 1, "one code point over must not PATCH");
        assert.strictEqual(lastPatchPayload(harness).values.talentCode, atLimit);
    });
});
