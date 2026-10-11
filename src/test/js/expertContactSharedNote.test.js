"use strict";

// 03（contact-profile-03-notes）测试：真实挂载 DOM + 可控延迟请求。
//
// 覆盖：
// - I-1 单一来源：只 GET /api/mail/contact-notes/{contactId}，0 次非 GET 请求；无/非法 contactId 零请求、不建联系人；
// - I-2 五态：加载中 / 有内容 / 空内容（「暂无备注」）/ 失败（「备注加载失败，请重试」）/ 无联系人（「尚无收发件箱备注」）；
//   400/404 显示不可读取而非业务空值；401 走既有 handleAuthResponse；备注失败不阻断四组资料编辑与旧材料；
// - I-3 隔离与编码：generation + readSeq + contactId 守卫（同 expert 不同 contact、迟到响应、卸载中止）；
//   动态文本一律 textContent（HTML 字符串不成元素），换行经 .ec-text 的 pre-wrap 可见；
// - S-1：计划逐字 section 结构、无新增 CSS/class/inline style、ec-profile 首子节点（nav 之前）、11 项缓存键统一。
//
// 说明（K-dom-stub-tests-hide-dangling-refs）：本文件不使用“getElementById 恒返回元素”的空 stub，
// 而是把 app.js 的 02/03 区域真实执行在能解析 innerHTML、支持 querySelector/事件冒泡/dataset 的小型 DOM 上，
// 并对真实 index.html / styles.css / 03 计划源文本做断言。

const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const STATIC_DIR = path.join(__dirname, "..", "..", "main", "resources", "static");
const APP_SOURCE = fs.readFileSync(path.join(STATIC_DIR, "app.js"), "utf-8");
const STYLES_SOURCE = fs.readFileSync(path.join(STATIC_DIR, "styles.css"), "utf-8");
const INDEX_SOURCE = fs.readFileSync(path.join(STATIC_DIR, "index.html"), "utf-8");
const PLAN_PATH = path.join(__dirname, "..", "..", "..", "docs", "plans", "2026-10-11", "contact-profile-03-notes.md");
const PLAN_SOURCE = fs.readFileSync(PLAN_PATH, "utf-8");

// 缓存键（K-frontend-cache-key-triad）：实值只从 index.html 派生，计划目标值从 03 计划正文解析，
// 测试与脚本都不得固化键字面量（index.html 是唯一来源）。
const CACHE_KEY = (INDEX_SOURCE.match(/styles\.css\?v=([^"'&<>]+)/) || [])[1];
const PLANNED_CACHE_KEY = (() => {
    const match = PLAN_SOURCE.match(/(?:统一(?:更新)?|同步)为\s*`?([0-9]{8}-[a-z0-9-]+)`?/);
    if (!match) throw new Error("planned cache key not found in " + PLAN_PATH);
    return match[1];
})();

const MODULE_02_BEGIN = "/* contact-profile-02-ui:begin */";
const MODULE_02_END = "/* contact-profile-02-ui:end */";
const MODULE_03_BEGIN = "/* contact-profile-03-notes:begin */";
const MODULE_03_END = "/* contact-profile-03-notes:end */";
const HOST_SELECTOR = '[data-role="expert-manual-profile"]';
const NOTE_SELECTOR = '[data-role="expert-shared-note"]';
const NOTE_CONTENT_SELECTOR = '[data-role="note-content"]';
const NOTE_META_SELECTOR = '[data-role="note-meta"]';
const NOTE_REFRESH_SELECTOR = "[data-ec-refresh-note]";
const NOTE_URL_PREFIX = "/api/mail/contact-notes";

const LOADING_TEXT = "正在加载备注…";
const EMPTY_TEXT = "暂无备注";
const NO_CONTACT_TEXT = "尚无收发件箱备注";
const UNREADABLE_TEXT = "备注不可读取";
const LOAD_FAILED_TEXT = "备注加载失败，请重试";
const UNAUTHORIZED_TEXT = "登录已失效，请重新登录后再试";

const GROUP_ORDER = ["matching", "identity", "education", "technology"];

// ════════════════════════════════════════════════════════════════════════
// 小型 DOM：innerHTML 解析 / 选择器 / 事件冒泡 / dataset / dialog
// ════════════════════════════════════════════════════════════════════════

const VOID_TAGS = new Set([
    "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr"
]);

function parseSelector(selector) {
    const parsed = { tag: null, id: null, classes: [], attrs: [] };
    const pattern = /([a-zA-Z][\w-]*)|\.([\w-]+)|#([\w-]+)|\[([\w-]+)(?:\s*=\s*"([^"]*)")?\]/g;
    let consumed = 0;
    let match;
    while ((match = pattern.exec(selector)) !== null) {
        if (match.index !== consumed) throw new Error("unsupported selector: " + selector);
        consumed = pattern.lastIndex;
        if (match[1]) parsed.tag = match[1].toLowerCase();
        else if (match[2]) parsed.classes.push(match[2]);
        else if (match[3]) parsed.id = match[3];
        else parsed.attrs.push({ name: match[4], value: match[5] });
    }
    if (consumed !== selector.length) throw new Error("unsupported selector: " + selector);
    return parsed;
}

function elementMatches(element, parsed) {
    if (parsed.tag && element.localName !== parsed.tag) return false;
    if (parsed.id !== null && element.getAttribute("id") !== parsed.id) return false;
    for (const name of parsed.classes) {
        if (!element.classList.contains(name)) return false;
    }
    for (const attr of parsed.attrs) {
        if (!element.hasAttribute(attr.name)) return false;
        if (attr.value !== undefined && element.getAttribute(attr.name) !== attr.value) return false;
    }
    return true;
}

class MiniText {
    constructor(doc, data) {
        this.ownerDocument = doc;
        this.nodeType = 3;
        this.parentNode = null;
        this.data = String(data);
    }
    get textContent() {
        return this.data;
    }
    set textContent(value) {
        this.data = String(value == null ? "" : value);
    }
}

class MiniElement {
    constructor(doc, tagName) {
        this.ownerDocument = doc;
        this.nodeType = 1;
        this.localName = String(tagName).toLowerCase();
        this.tagName = this.localName.toUpperCase();
        this.parentNode = null;
        this.childNodes = [];
        this.attrs = new Map();
        this.listeners = new Map();
        this.open = false;
        this.scrollTop = 0;
    }
    // ── 属性 ──────────────────────────────────────────────────────────────
    setAttribute(name, value) {
        this.attrs.set(String(name), String(value == null ? "" : value));
    }
    getAttribute(name) {
        return this.attrs.has(name) ? this.attrs.get(name) : null;
    }
    hasAttribute(name) {
        return this.attrs.has(name);
    }
    removeAttribute(name) {
        this.attrs.delete(name);
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
    get className() {
        return this.getAttribute("class") || "";
    }
    set className(value) {
        this.setAttribute("class", String(value == null ? "" : value));
    }
    get classList() {
        const element = this;
        const tokens = () => element.className.split(/\s+/).filter(Boolean);
        return {
            contains: (name) => tokens().includes(name),
            add: (name) => {
                const list = tokens();
                if (!list.includes(name)) {
                    list.push(name);
                    element.className = list.join(" ");
                }
            },
            remove: (name) => {
                element.className = tokens().filter((item) => item !== name).join(" ");
            }
        };
    }
    get dataset() {
        const element = this;
        const attrOf = (prop) => "data-" + String(prop).replace(/[A-Z]/g, (char) => "-" + char.toLowerCase());
        return new Proxy({}, {
            get(target, prop) {
                if (typeof prop !== "string") return undefined;
                return element.getAttribute(attrOf(prop));
            },
            set(target, prop, value) {
                element.setAttribute(attrOf(prop), value);
                return true;
            },
            has(target, prop) {
                return typeof prop === "string" && element.hasAttribute(attrOf(prop));
            }
        });
    }
    get value() {
        if (this._value !== undefined) return this._value;
        if (this.localName === "option") {
            return this.hasAttribute("value") ? this.getAttribute("value") : this.textContent;
        }
        return "";
    }
    set value(next) {
        this._value = String(next == null ? "" : next);
    }
    // ── 树 ────────────────────────────────────────────────────────────────
    appendChild(node) {
        if (node.parentNode) node.parentNode.removeChild(node);
        node.parentNode = this;
        this.childNodes.push(node);
        return node;
    }
    insertBefore(node, reference) {
        if (node.parentNode) node.parentNode.removeChild(node);
        const index = reference ? this.childNodes.indexOf(reference) : -1;
        node.parentNode = this;
        if (index < 0) this.childNodes.push(node);
        else this.childNodes.splice(index, 0, node);
        return node;
    }
    removeChild(node) {
        const index = this.childNodes.indexOf(node);
        if (index >= 0) this.childNodes.splice(index, 1);
        node.parentNode = null;
        return node;
    }
    remove() {
        if (this.parentNode) this.parentNode.removeChild(this);
    }
    get children() {
        return this.childNodes.filter((node) => node.nodeType === 1);
    }
    get firstChild() {
        return this.childNodes[0] || null;
    }
    get firstElementChild() {
        return this.children[0] || null;
    }
    contains(node) {
        let current = node;
        while (current) {
            if (current === this) return true;
            current = current.parentNode;
        }
        return false;
    }
    get isConnected() {
        let node = this;
        while (node) {
            if (node === this.ownerDocument.root) return true;
            node = node.parentNode;
        }
        return false;
    }
    matches(selector) {
        return elementMatches(this, parseSelector(selector));
    }
    closest(selector) {
        const parsed = parseSelector(selector);
        let node = this;
        while (node) {
            if (node.nodeType === 1 && elementMatches(node, parsed)) return node;
            node = node.parentNode;
        }
        return null;
    }
    // ── 内容 ──────────────────────────────────────────────────────────────
    get textContent() {
        let text = "";
        for (const child of this.childNodes) text += child.textContent;
        return text;
    }
    set textContent(value) {
        this.childNodes.forEach((child) => {
            child.parentNode = null;
        });
        this.childNodes = [];
        const text = String(value == null ? "" : value);
        if (text !== "") this.appendChild(new MiniText(this.ownerDocument, text));
    }
    get innerHTML() {
        return this.childNodes.map(serializeNode).join("");
    }
    set innerHTML(markup) {
        this.childNodes.forEach((child) => {
            child.parentNode = null;
        });
        this.childNodes = [];
        parseMarkup(this.ownerDocument, String(markup)).forEach((node) => this.appendChild(node));
    }
    querySelectorAll(selector) {
        const parsed = parseSelector(selector);
        const results = [];
        const walk = (node) => {
            for (const child of node.childNodes) {
                if (child.nodeType !== 1) continue;
                if (elementMatches(child, parsed)) results.push(child);
                walk(child);
            }
        };
        walk(this);
        return results;
    }
    querySelector(selector) {
        return this.querySelectorAll(selector)[0] || null;
    }
    // ── 事件 ──────────────────────────────────────────────────────────────
    addEventListener(type, listener) {
        if (typeof listener !== "function") return;
        const list = this.listeners.get(type) || [];
        list.push(listener);
        this.listeners.set(type, list);
    }
    removeEventListener(type, listener) {
        const list = this.listeners.get(type) || [];
        this.listeners.set(type, list.filter((item) => item !== listener));
    }
    dispatchEvent(event) {
        if (!event || !event.type) return true;
        if (!event.target) event.target = this;
        let node = this;
        while (node) {
            const list = node.listeners.get(event.type);
            if (list) {
                for (const listener of [...list]) listener.call(node, event);
            }
            if (event.bubbles === false) break;
            node = node.parentNode;
        }
        return true;
    }
    click() {
        const event = {
            type: "click",
            target: this,
            bubbles: true,
            defaultPrevented: false,
            preventDefault() {
                this.defaultPrevented = true;
            }
        };
        this.dispatchEvent(event);
        return event;
    }
    focus() {
        this.ownerDocument.activeElement = this;
    }
    showModal() {
        this.open = true;
    }
    close() {
        this.open = false;
    }
}

function serializeNode(node) {
    if (node.nodeType === 3) return node.data;
    const attrs = [...node.attrs.entries()].map(([name, value]) => ` ${name}="${value}"`).join("");
    const tag = node.localName;
    if (VOID_TAGS.has(tag)) return `<${tag}${attrs}>`;
    return `<${tag}${attrs}>${node.childNodes.map(serializeNode).join("")}</${tag}>`;
}

function applyAttributes(element, raw) {
    const pattern = /([a-zA-Z_:][-\w:.]*)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'>]+)))?/g;
    let match;
    while ((match = pattern.exec(raw)) !== null) {
        const value = match[2] !== undefined ? match[2]
            : match[3] !== undefined ? match[3]
                : match[4] !== undefined ? match[4] : "";
        element.setAttribute(match[1], value);
    }
}

/** innerHTML 解析：支持闭合标签、自闭合标签、void 标签与布尔属性（本仓模板用到的子集）。 */
function parseMarkup(doc, markup) {
    const roots = [];
    const stack = [];
    const push = (node) => {
        const parent = stack[stack.length - 1];
        if (parent) parent.appendChild(node);
        else roots.push(node);
    };
    const pattern = /<\/?([a-zA-Z][\w-]*)((?:\s+[^\s/>"']+(?:\s*=\s*(?:"[^"]*"|'[^']*'|[^\s"'>]+))?)*)\s*\/?>/g;
    let index = 0;
    let match;
    while ((match = pattern.exec(markup)) !== null) {
        const text = markup.slice(index, match.index);
        if (text !== "") push(new MiniText(doc, text));
        index = pattern.lastIndex;
        const tag = match[1].toLowerCase();
        if (match[0].startsWith("</")) {
            for (let i = stack.length - 1; i >= 0; i -= 1) {
                if (stack[i].localName === tag) {
                    stack.length = i;
                    break;
                }
            }
            continue;
        }
        const element = new MiniElement(doc, tag);
        applyAttributes(element, match[2] || "");
        push(element);
        if (!/\/>$/.test(match[0]) && !VOID_TAGS.has(tag)) stack.push(element);
    }
    const tail = markup.slice(index);
    if (tail !== "") push(new MiniText(doc, tail));
    return roots;
}

class MiniDocument {
    constructor() {
        this.activeElement = null;
        this.root = new MiniElement(this, "root");
    }
    createElement(tagName) {
        return new MiniElement(this, tagName);
    }
    createTextNode(data) {
        return new MiniText(this, data);
    }
    querySelector(selector) {
        return this.root.querySelector(selector);
    }
    querySelectorAll(selector) {
        return this.root.querySelectorAll(selector);
    }
    getElementById(id) {
        return this.querySelector(`#${id}`);
    }
}

// ════════════════════════════════════════════════════════════════════════
// app.js 区域 / 函数抽取
// ════════════════════════════════════════════════════════════════════════

function extractFunction(name) {
    const regex = new RegExp("(?:async\\s+)?function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}");
    const match = APP_SOURCE.match(regex);
    if (!match) throw new Error("Could not find " + name + " in app.js");
    return match[0];
}

function extractRegion(source, begin, end, label) {
    const start = source.indexOf(begin);
    const stop = source.indexOf(end);
    if (start === -1 || stop === -1 || stop < start) throw new Error(label + " region missing in app.js");
    return source.slice(start, stop + end.length);
}

const REGION_02 = extractRegion(APP_SOURCE, MODULE_02_BEGIN, MODULE_02_END, "contact-profile-02-ui");
const REGION_03 = extractRegion(APP_SOURCE, MODULE_03_BEGIN, MODULE_03_END, "contact-profile-03-notes");

function extractPlanSectionBlock() {
    const blocks = [...PLAN_SOURCE.matchAll(/```html\r?\n([\s\S]*?)\n```/g)].map((match) => match[1]);
    const block = blocks.find((item) => item.startsWith('<section class="ec-section" data-role="expert-shared-note"'));
    if (!block) throw new Error("S-1 note section block not found in " + PLAN_PATH);
    return block;
}

// ════════════════════════════════════════════════════════════════════════
// 可控 fetch / 延迟响应（与 02 测试同机制）
// ════════════════════════════════════════════════════════════════════════

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

// ════════════════════════════════════════════════════════════════════════
// 夹具 / 沙箱 / harness
// ════════════════════════════════════════════════════════════════════════

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
    return {
        doc, body, main, view, list, mobileStatus, contactDetail, headActions, levelSelect,
        failures: [], authResponses: []
    };
}

const flush = () => new Promise((resolve) => setImmediate(resolve));

function noteDto(contactId, note, overrides = {}) {
    return Object.assign({
        contactId,
        note,
        updatedBy: note ? "ops" : null,
        updatedAt: note ? "2026-10-11T10:00:00.000+08:00" : null
    }, overrides);
}

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
    GROUP_ORDER.forEach((group) => {
        next[group] = Object.assign({}, base[group], overrides[group] || {});
    });
    return next;
}

function createSandbox(fixture, router, options = {}) {
    const sandbox = {
        document: fixture.doc,
        window: { innerWidth: 1440, matchMedia: () => ({ matches: false, addEventListener() {} }) },
        console,
        Intl,
        AbortController,
        setTimeout,
        clearTimeout,
        setImmediate,
        contextPath: "",
        fetch: router.fetchImpl,
        requestAnimationFrame: () => 0,
        handleAuthResponse: async (response) => {
            // task-modal-runtime.js 的真实语义：401 抛 UNAUTHORIZED、403 强制改密。
            fixture.authResponses.push(response.status);
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
        // 宿主动画依赖（断言只看备注 section 与请求，故为最小 stub）
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
    vm.runInContext(REGION_02, sandbox);
    // 既有切片式测试只执行 02 区域：据此可复现“03 函数不存在”时 02 骨架保持原样的门禁。
    if (options.loadNoteRegion !== false) vm.runInContext(REGION_03, sandbox);
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

function noteSectionOf(harness) {
    const profile = profileOf(harness);
    return profile ? profile.querySelector(NOTE_SELECTOR) : null;
}

function noteContentOf(harness) {
    const section = noteSectionOf(harness);
    return section ? section.querySelector(NOTE_CONTENT_SELECTOR) : null;
}

function noteMetaOf(harness) {
    const section = noteSectionOf(harness);
    return section ? section.querySelector(NOTE_META_SELECTOR) : null;
}

function noteErrorOf(harness) {
    const section = noteSectionOf(harness);
    return section ? section.querySelector(".ec-error") : null;
}

function noteRefreshOf(harness) {
    const section = noteSectionOf(harness);
    return section ? section.querySelector(NOTE_REFRESH_SELECTOR) : null;
}

function noteRequests(harness) {
    return harness.router.requests.filter((request) => request.url.includes(NOTE_URL_PREFIX));
}

function profileRequests(harness) {
    return harness.router.requests.filter((request) => request.url.includes("/api/experts/manual-profile"));
}

/** 默认把资料 GET 与备注 GET 都路由成成功（各用例可覆盖）。 */
function routeHappyPath(harness, noteBody) {
    harness.router.route("GET", "/api/experts/manual-profile", () => ({ status: 200, body: profileDto() }));
    if (noteBody !== undefined) {
        harness.router.route("GET", NOTE_URL_PREFIX, () => ({ status: 200, body: noteBody }));
    }
}

function mountWithNote(harness, contactId, noteBody, generation) {
    routeHappyPath(harness, noteBody);
    return harness.mount({
        expertId: "0000-0000-1111-2222",
        contactId,
        generation: generation === undefined ? harness.sandbox.mobileContactPresentation.generation : generation
    });
}

// ════════════════════════════════════════════════════════════════════════
// 契约与静态门禁
// ════════════════════════════════════════════════════════════════════════

describe("contact profile 03 — S-1 / 缓存键 / 区域边界", () => {
    it("S-1：section 模板与 03 计划逐字一致，且只用已声明的 class（无新增 CSS）", () => {
        const harness = createHarness();
        const expected = extractPlanSectionBlock();
        assert.strictEqual(harness.sandbox.expertContactSharedNoteSectionTemplate(), expected,
            "note section template must match the plan block verbatim");
        assert.ok(!expected.includes('style="'), "no inline style");
        for (const name of new Set([...expected.matchAll(/class="([^"]*)"/g)].flatMap((match) => match[1].split(/\s+/)))) {
            assert.ok(new RegExp("\\." + name + "(?![\\w-])").test(STYLES_SOURCE),
                `.${name} must already be declared in styles.css (no new CSS in 03)`);
        }
    });

    it("缓存键：index.html 11 项统一为 03 计划键，数量与顺序不变，测试自身不固化字面量", () => {
        assert.strictEqual(CACHE_KEY, PLANNED_CACHE_KEY, "index.html must carry the key required by the 03 plan");
        const keys = [...INDEX_SOURCE.matchAll(/\?v=([0-9a-z-]+)/g)].map((match) => match[1]);
        assert.strictEqual(keys.length, 11, "index.html must carry exactly eleven cache-busted assets");
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
        assert.ok(INDEX_SOURCE.includes('<script src="task-modal-runtime.js"></script>'),
            "no asset may be added or re-keyed beyond the eleven");
        // K-frontend-cache-key-triad：键字面量只允许出现在 index.html。
        assert.ok(!fs.readFileSync(__filename, "utf-8").includes(CACHE_KEY),
            "the 03 test must derive the cache key, never hard-code it");
    });

    it("区域边界：03 区域独立于 02 区域，无全局监听 / 无 inline style，且由 02 的 typeof 守卫接入", () => {
        assert.strictEqual(APP_SOURCE.split(MODULE_03_BEGIN).length - 1, 1, "exactly one 03 region");
        assert.strictEqual(APP_SOURCE.split(MODULE_03_END).length - 1, 1, "exactly one 03 region");
        const region02Start = APP_SOURCE.indexOf(MODULE_02_BEGIN);
        const region02Stop = APP_SOURCE.indexOf(MODULE_02_END);
        const region03Start = APP_SOURCE.indexOf(MODULE_03_BEGIN);
        assert.ok(region03Start > region02Stop && region03Start > region02Start,
            "03 region must live outside the 02 region so slice-extracted 02 tests stay untouched");
        assert.ok(!REGION_03.includes("document.addEventListener"), "03 must not register document-level listeners");
        assert.ok(!REGION_03.includes("window.addEventListener"), "03 must not register window-level listeners");
        assert.ok(!REGION_03.includes('style="'), "03 must not emit inline styles");
        assert.ok(!REGION_03.includes("innerHTML"), "03 must write text through textContent, never innerHTML");
        // 02 区域内的三个接入点都用 typeof 守卫（未加载 03 区域时保持原行为）。
        assert.strictEqual(REGION_02.split("typeof expertContactSharedNoteSectionTemplate === \"function\"").length - 1, 1);
        assert.strictEqual(REGION_02.split("typeof mountExpertContactSharedNote === \"function\"").length - 1, 1);
        assert.strictEqual(REGION_02.split("typeof unmountExpertContactSharedNote === \"function\"").length - 1, 1);
    });

    it("停止态：未加载 03 区域的沙箱里，02 骨架仍是四组 section/editor（既有切片式测试不回退）", async () => {
        // 只跑 02 区域（模拟 02 静态契约测试的切片沙箱）：03 函数不存在 → shell 不含备注 section，
        // 且挂载流程不因缺失的 03 接入点报错（typeof 守卫）。
        const fixture = createFixture();
        const router = createRouter();
        const sandbox = createSandbox(fixture, router, { loadNoteRegion: false });
        const shell = sandbox.expertManualProfileShellTemplate();
        assert.ok(!shell.includes('data-role="expert-shared-note"'), "03 section must be absent without the 03 region");
        assert.strictEqual((shell.match(/class="ec-section"/g) || []).length, 4);
        assert.strictEqual((shell.match(/class="em-upload-dialog ec-editor"/g) || []).length, 4);
        assert.strictEqual((shell.match(/data-ec-jump=/g) || []).length, 4);

        router.route("GET", "/api/experts/manual-profile", () => ({ status: 200, body: profileDto() }));
        fixture.contactDetail.innerHTML = `<div class="ec-profile" data-role="expert-manual-profile"></div>`;
        const session = sandbox.mountExpertManualProfile(fixture.contactDetail, {
            expertId: "E", contactId: "7", generation: sandbox.mobileContactPresentation.generation
        });
        await session.ready;
        const profile = fixture.contactDetail.querySelector(HOST_SELECTOR);
        assert.strictEqual(profile.querySelectorAll(".ec-section").length, 4);
        assert.strictEqual(profile.querySelectorAll(".ec-editor").length, 4);
        assert.strictEqual(profile.querySelector(".ec-nav").querySelectorAll("button").length, 4);
        assert.strictEqual(session.sharedNote, undefined, "no note state without the 03 region");
        assert.deepStrictEqual(router.requests.filter((request) => request.url.includes(NOTE_URL_PREFIX)), []);
    });
});

// ════════════════════════════════════════════════════════════════════════
// S-1 宿主与骨架（真实挂载）
// ════════════════════════════════════════════════════════════════════════

describe("contact profile 03 — S-1 宿主结构", () => {
    it("挂载后：备注 section 是 ec-profile 首子节点（nav 之前），结构与计划一致，四组骨架保留", async () => {
        const harness = createHarness();
        const session = mountWithNote(harness, "7", noteDto(7, "首次沟通"));
        await session.ready;
        await session.sharedNote.ready;

        const profile = profileOf(harness);
        const section = noteSectionOf(harness);
        assert.ok(section, "note section must exist inside the ec-profile host");
        assert.strictEqual(profile.firstElementChild, section, "note section must be the first child of ec-profile");
        assert.strictEqual(profile.children[1].className, "ec-nav", "nav must follow the note section");
        assert.ok(section.classList.contains("ec-section"));
        assert.strictEqual(section.getAttribute("data-role"), "expert-shared-note");

        const heading = section.querySelector(".ec-heading");
        assert.strictEqual(heading.querySelector("h3").textContent, "收发件箱备注");
        const refresh = heading.querySelector(NOTE_REFRESH_SELECTOR);
        assert.strictEqual(refresh.tagName, "BUTTON");
        assert.strictEqual(refresh.getAttribute("type"), "button");
        assert.ok(refresh.classList.contains("button"));
        assert.strictEqual(refresh.textContent, "刷新");
        assert.strictEqual(section.querySelector(NOTE_CONTENT_SELECTOR).className, "ec-text");
        const meta = section.querySelector(NOTE_META_SELECTOR);
        assert.strictEqual(meta.className, "ec-message");
        assert.strictEqual(meta.getAttribute("role"), "status");
        assert.strictEqual(meta.getAttribute("aria-live"), "polite");
        const error = section.querySelector(".ec-error");
        assert.strictEqual(error.getAttribute("role"), "alert");
        assert.strictEqual(error.hidden, true);
        assert.ok(!section.innerHTML.includes('style="'), "no inline style in the note section");
        // 插入点在 nav 之前（同一份 shell 文本里的顺序）
        const shell = harness.sandbox.expertManualProfileShellTemplate();
        assert.ok(shell.indexOf('data-role="expert-shared-note"') < shell.indexOf('class="ec-nav"'));
        // 02 的四组骨架仍然存在
        assert.strictEqual(profile.querySelectorAll(".ec-section").length, 5, "4 groups + 1 note section");
        assert.strictEqual(profile.querySelectorAll(".ec-editor").length, 4);
        assert.strictEqual(profile.querySelector(".ec-nav").querySelectorAll("button").length, 4);
        for (const group of GROUP_ORDER) {
            assert.ok(profile.querySelector(`.ec-section[data-ec-group="${group}"]`), `group ${group} must exist`);
        }
    });
});

// ════════════════════════════════════════════════════════════════════════
// I-1 单一来源
// ════════════════════════════════════════════════════════════════════════

describe("contact profile 03 — I-1 单一来源", () => {
    it("有 contact：只发一次 GET /api/mail/contact-notes/{contactId}，全程 0 次写请求", async () => {
        const harness = createHarness();
        const session = mountWithNote(harness, "7", noteDto(7, "首次沟通\n等待企业反馈"));
        await session.ready;
        await session.sharedNote.ready;

        const noteCalls = noteRequests(harness);
        assert.strictEqual(noteCalls.length, 1, "one read per detail entry");
        assert.strictEqual(noteCalls[0].method, "GET");
        assert.ok(noteCalls[0].url.endsWith(`${NOTE_URL_PREFIX}/7`), "URL must carry the real contactId: " + noteCalls[0].url);
        assert.ok(!noteCalls[0].url.includes("expertId"), "the note source is contactId only (I-1)");
        assert.deepStrictEqual(harness.router.requests.filter((request) => request.method !== "GET"), [],
            "the detail page must never write notes");
    });

    it("无 contact：零请求、不新建联系人，显示「尚无收发件箱备注」且隐藏刷新按钮", async () => {
        const harness = createHarness();
        harness.router.route("GET", "/api/experts/manual-profile", () => ({ status: 200, body: profileDto() }));
        const session = harness.mount({ expertId: "0000-0000-1111-2222", contactId: "", generation: 1 });
        await session.ready;
        await session.sharedNote.ready;

        assert.deepStrictEqual(noteRequests(harness), [], "no contact → no note request at all");
        assert.strictEqual(noteContentOf(harness).textContent, NO_CONTACT_TEXT);
        assert.strictEqual(noteRefreshOf(harness).hidden, true, "refresh button hidden without a contact");
        assert.strictEqual(noteErrorOf(harness).hidden, true);
        assert.strictEqual(harness.fixture.failures.length, 0);
    });

    it("非法 contactId（0/null 文本）：同样不发请求", async () => {
        const harness = createHarness();
        harness.router.route("GET", "/api/experts/manual-profile", () => ({ status: 200, body: profileDto() }));
        for (const contactId of ["0", "null", "-3", "abc"]) {
            const session = harness.mount({ expertId: "E", contactId, generation: harness.sandbox.mobileContactPresentation.generation });
            await session.ready;
            await session.sharedNote.ready;
            assert.strictEqual(noteContentOf(harness).textContent, NO_CONTACT_TEXT, "contactId=" + contactId);
            assert.strictEqual(noteRefreshOf(harness).hidden, true, "contactId=" + contactId);
        }
        assert.deepStrictEqual(noteRequests(harness), [], "invalid ids must never reach the note endpoint");
    });

    it("刷新：再发一次同 contactId 的 GET，只更新该 section", async () => {
        const harness = createHarness();
        let body = noteDto(7, "首次沟通\n等待企业反馈");
        harness.router.route("GET", "/api/experts/manual-profile", () => ({ status: 200, body: profileDto() }));
        harness.router.route("GET", "/api/mail/contact-notes/7", () => ({ status: 200, body }));
        const session = harness.mount({ expertId: "E", contactId: "7", generation: 1 });
        await session.ready;
        await session.sharedNote.ready;
        assert.strictEqual(noteContentOf(harness).textContent, "首次沟通\n等待企业反馈");

        const section = noteSectionOf(harness);
        const nav = profileOf(harness).children[1];
        body = noteDto(7, "已约会议", { updatedBy: "ops2" });
        noteRefreshOf(harness).click();
        await session.sharedNote.ready;

        assert.strictEqual(noteRequests(harness).length, 2, "refresh issues exactly one more GET");
        assert.strictEqual(noteContentOf(harness).textContent, "已约会议");
        assert.strictEqual(noteSectionOf(harness), section, "the same section node is updated in place");
        assert.strictEqual(profileOf(harness).children[1], nav, "nav and the four groups are untouched");
        assert.strictEqual(profileOf(harness).querySelectorAll(".ec-section").length, 5);
        assert.strictEqual(nav.querySelectorAll("button").length, 4);
        assert.strictEqual(noteErrorOf(harness).hidden, true);
    });
});

// ════════════════════════════════════════════════════════════════════════
// I-2 五态
// ════════════════════════════════════════════════════════════════════════

describe("contact profile 03 — I-2 五态", () => {
    it("加载中：显示加载文案并禁用刷新；成功后内容与元信息就位且刷新恢复", async () => {
        const harness = createHarness();
        const pending = deferred();
        harness.router.route("GET", "/api/experts/manual-profile", () => ({ status: 200, body: profileDto() }));
        harness.router.route("GET", "/api/mail/contact-notes/7", () => pending.promise);
        const session = harness.mount({ expertId: "E", contactId: "7", generation: 1 });
        await session.ready;

        assert.strictEqual(noteContentOf(harness).textContent, LOADING_TEXT);
        assert.strictEqual(noteRefreshOf(harness).disabled, true, "refresh disabled while loading");
        assert.strictEqual(noteRefreshOf(harness).hidden, false);
        assert.strictEqual(noteErrorOf(harness).hidden, true);

        pending.resolve({
            status: 200,
            body: noteDto(7, "首次沟通\n等待企业反馈", { updatedBy: "ops", updatedAt: "2026-10-11T10:00:00.000+08:00" })
        });
        await session.sharedNote.ready;
        assert.strictEqual(noteContentOf(harness).textContent, "首次沟通\n等待企业反馈");
        assert.strictEqual(noteMetaOf(harness).textContent, "ops · 2026-10-11 10:00 北京时间");
        assert.strictEqual(noteRefreshOf(harness).disabled, false, "refresh re-enabled after load");
    });

    it("成功空内容：显示「暂无备注」，且不虚构修改人/时间", async () => {
        const harness = createHarness();
        const session = mountWithNote(harness, "7", noteDto(7, ""));
        await session.ready;
        await session.sharedNote.ready;

        assert.strictEqual(noteContentOf(harness).textContent, EMPTY_TEXT);
        assert.strictEqual(noteMetaOf(harness).textContent, "");
        assert.strictEqual(noteErrorOf(harness).hidden, true);
        assert.strictEqual(noteRefreshOf(harness).disabled, false);
    });

    it("成功有内容：两行原文与修改人/时间原样显示", async () => {
        const harness = createHarness();
        const session = mountWithNote(harness, "7", noteDto(7, "首次沟通\n等待企业反馈"));
        await session.ready;
        await session.sharedNote.ready;

        assert.strictEqual(noteContentOf(harness).textContent, "首次沟通\n等待企业反馈");
        assert.strictEqual(noteMetaOf(harness).textContent, "ops · 2026-10-11 10:00 北京时间");
    });

    it("失败（500）：显示「备注加载失败，请重试」而非空内容，且不阻断四组资料编辑", async () => {
        const harness = createHarness();
        harness.router.route("GET", "/api/experts/manual-profile", () => ({ status: 200, body: profileDto() }));
        harness.router.route("GET", "/api/mail/contact-notes/7", () => ({
            status: 500, body: { code: "INTERNAL_ERROR", message: "服务暂时不可用" }
        }));
        const session = harness.mount({ expertId: "E", contactId: "7", generation: 1 });
        await session.ready;
        await session.sharedNote.ready;

        assert.strictEqual(noteErrorOf(harness).textContent, LOAD_FAILED_TEXT);
        assert.strictEqual(noteErrorOf(harness).hidden, false);
        assert.notStrictEqual(noteContentOf(harness).textContent, EMPTY_TEXT, "a failed read must never look empty");
        assert.strictEqual(noteRefreshOf(harness).disabled, false, "retry reuses the refresh button");
        // 独立失败：资料已加载 → 四组编辑入口可用，材料/回复等旧区域不受影响
        assert.strictEqual(session.loaded, true);
        for (const group of GROUP_ORDER) {
            assert.strictEqual(session.editors.get(group).editButton.disabled, false, `${group} editor must stay enabled`);
        }
        assert.strictEqual(harness.fixture.failures.length, 0, "note failure must not fail the detail presentation");
        assert.deepStrictEqual(harness.router.requests.filter((request) => request.method !== "GET"), []);
    });

    it("失败（400/404）：显示「备注不可读取」而非业务空值", async () => {
        for (const status of [400, 404]) {
            const harness = createHarness();
            harness.router.route("GET", "/api/experts/manual-profile", () => ({ status: 200, body: profileDto() }));
            harness.router.route("GET", "/api/mail/contact-notes/7", () => ({
                status, body: { code: "NOT_FOUND", message: "联系人不存在" }
            }));
            const session = harness.mount({ expertId: "E", contactId: "7", generation: 1 });
            await session.ready;
            await session.sharedNote.ready;

            assert.strictEqual(noteErrorOf(harness).textContent, UNREADABLE_TEXT, "status " + status);
            assert.notStrictEqual(noteContentOf(harness).textContent, EMPTY_TEXT, "status " + status);
            assert.strictEqual(noteRefreshOf(harness).disabled, false, "status " + status);
        }
    });

    it("失败（网络中断）：显示重试文案且不抛出，刷新后恢复", async () => {
        const harness = createHarness();
        let broken = true;
        harness.router.route("GET", "/api/experts/manual-profile", () => ({ status: 200, body: profileDto() }));
        harness.router.route("GET", "/api/mail/contact-notes/7", () => {
            if (broken) throw new Error("Failed to fetch");
            return { status: 200, body: noteDto(7, "恢复后的备注") };
        });
        const session = harness.mount({ expertId: "E", contactId: "7", generation: 1 });
        await session.ready;
        await session.sharedNote.ready;

        assert.strictEqual(noteErrorOf(harness).textContent, LOAD_FAILED_TEXT);
        assert.notStrictEqual(noteContentOf(harness).textContent, EMPTY_TEXT);

        broken = false;
        noteRefreshOf(harness).click();
        await session.sharedNote.ready;
        assert.strictEqual(noteContentOf(harness).textContent, "恢复后的备注");
        assert.strictEqual(noteErrorOf(harness).hidden, true);
        assert.strictEqual(noteErrorOf(harness).textContent, "");
    });

    it("失败后刷新仍失败：保留上一次成功内容，不清空成「暂无备注」", async () => {
        const harness = createHarness();
        let body = noteDto(7, "已约会议");
        let fail = false;
        harness.router.route("GET", "/api/experts/manual-profile", () => ({ status: 200, body: profileDto() }));
        harness.router.route("GET", "/api/mail/contact-notes/7", () => (fail
            ? { status: 503, body: { code: "UNAVAILABLE", message: "稍后重试" } }
            : { status: 200, body }));
        const session = harness.mount({ expertId: "E", contactId: "7", generation: 1 });
        await session.ready;
        await session.sharedNote.ready;
        assert.strictEqual(noteContentOf(harness).textContent, "已约会议");

        fail = true;
        noteRefreshOf(harness).click();
        await session.sharedNote.ready;
        assert.strictEqual(noteContentOf(harness).textContent, "已约会议", "last known content survives a failed refresh");
        assert.strictEqual(noteErrorOf(harness).textContent, LOAD_FAILED_TEXT);
    });

    it("401：走既有 handleAuthResponse，并给出登录失效提示（不显示空内容、不发写请求）", async () => {
        const harness = createHarness();
        harness.router.route("GET", "/api/experts/manual-profile", () => ({ status: 200, body: profileDto() }));
        harness.router.route("GET", "/api/mail/contact-notes/7", () => ({
            status: 401, body: { code: "UNAUTHORIZED", message: "未登录" }
        }));
        const session = harness.mount({ expertId: "E", contactId: "7", generation: 1 });
        await session.ready;
        await session.sharedNote.ready;

        assert.ok(harness.fixture.authResponses.includes(401), "the shared auth handler must see the 401");
        assert.strictEqual(noteErrorOf(harness).textContent, UNAUTHORIZED_TEXT);
        assert.notStrictEqual(noteContentOf(harness).textContent, EMPTY_TEXT);
        assert.deepStrictEqual(harness.router.requests.filter((request) => request.method !== "GET"), []);
    });
});

// ════════════════════════════════════════════════════════════════════════
// I-3 隔离与编码
// ════════════════════════════════════════════════════════════════════════

describe("contact profile 03 — I-3 隔离与编码", () => {
    it("同一 expertId 不同 contactId：各读各的记录，甲活动不覆盖乙活动", async () => {
        const harness = createHarness();
        const delayed = deferred();
        harness.router.route("GET", "/api/experts/manual-profile", () => ({ status: 200, body: profileDto() }));
        harness.router.route("GET", "/api/mail/contact-notes/7", () => delayed.promise);
        harness.router.route("GET", "/api/mail/contact-notes/8", () => ({ status: 200, body: noteDto(8, "乙活动") }));

        const first = harness.mount({ expertId: "E", contactId: "7", generation: harness.sandbox.beginContactDetailPresentation() });
        await first.ready;
        await flush();
        const second = harness.mount({ expertId: "E", contactId: "8", generation: harness.sandbox.beginContactDetailPresentation() });
        await second.ready;
        await second.sharedNote.ready;
        assert.strictEqual(noteContentOf(harness).textContent, "乙活动");

        delayed.resolve({ status: 200, body: noteDto(7, "甲活动") });
        await flush();
        await flush();
        assert.strictEqual(noteContentOf(harness).textContent, "乙活动", "甲活动的迟到响应不得写入乙");
        assert.ok(noteRequests(harness).some((request) => request.url.endsWith("/8")));
        assert.ok(noteRequests(harness).some((request) => request.url.endsWith("/7")));
    });

    it("卸载/切人：在途备注请求被中止，迟到响应不再写入", async () => {
        const harness = createHarness();
        const delayed = deferred();
        harness.router.route("GET", "/api/experts/manual-profile", () => ({ status: 200, body: profileDto() }));
        harness.router.route("GET", "/api/mail/contact-notes/7", () => delayed.promise);
        const session = harness.mount({ expertId: "E", contactId: "7", generation: harness.sandbox.beginContactDetailPresentation() });
        await session.ready;
        await flush();
        const request = noteRequests(harness)[0];
        assert.strictEqual(noteContentOf(harness).textContent, LOADING_TEXT);

        harness.sandbox.unmountExpertManualProfilePanels();
        assert.strictEqual(session.closed, true);
        assert.strictEqual(request.signal.aborted, true, "the in-flight note read must be aborted");

        delayed.resolve({ status: 200, body: noteDto(7, "迟到的备注") });
        await flush();
        await flush();
        assert.notStrictEqual(noteContentOf(harness).textContent, "迟到的备注");
        assert.strictEqual(noteContentOf(harness).textContent, LOADING_TEXT, "closed session must not repaint");
    });

    it("编码：HTML 字符串只作纯文本，不构造元素；换行原样保留", async () => {
        const harness = createHarness();
        const payload = "<img src=x onerror=\"window.__pwned=1\">\n<script>alert(1)</script>";
        const session = mountWithNote(harness, "7", noteDto(7, payload, { updatedBy: "<b>ops</b>" }));
        await session.ready;
        await session.sharedNote.ready;

        const section = noteSectionOf(harness);
        assert.strictEqual(noteContentOf(harness).textContent, payload, "textContent must round-trip the raw note");
        assert.strictEqual(section.querySelectorAll("img").length, 0);
        assert.strictEqual(section.querySelectorAll("script").length, 0);
        assert.strictEqual(section.querySelectorAll("b").length, 0);
        assert.ok(noteMetaOf(harness).textContent.startsWith("<b>ops</b>"), "updater is text too");
        assert.strictEqual(harness.sandbox.window.__pwned, undefined);
    });

    it("换行可见：.ec-text 声明 white-space: pre-wrap（复用 02 样式，无新增 CSS）", () => {
        assert.match(STYLES_SOURCE, /\.ec-text\s*\{[^}]*white-space:\s*pre-wrap/, ".ec-text must keep pre-wrap");
    });

    it("失败不阻断旧材料：备注失败时 material/doc 区域仍照常渲染（无异常）", async () => {
        const harness = createHarness();
        harness.router.route("GET", "/api/experts/manual-profile", () => ({ status: 200, body: profileDto() }));
        harness.router.route("GET", "/api/mail/contact-notes/7", () => ({ status: 500, body: { code: "X", message: "boom" } }));
        const session = harness.mount({ expertId: "E", contactId: "7", generation: 1 });
        await session.ready;
        await session.sharedNote.ready;
        assert.strictEqual(noteErrorOf(harness).textContent, LOAD_FAILED_TEXT);
        assert.strictEqual(profileOf(harness).querySelectorAll(".ec-editor").length, 4);
        assert.strictEqual(harness.fixture.failures.length, 0);
    });
});

// ════════════════════════════════════════════════════════════════════════
// 宿主入口：loadContactDetail / showExpertDetail
// ════════════════════════════════════════════════════════════════════════

describe("contact profile 03 — 两个宿主入口", () => {
    function routeHost(harness, noteHandler) {
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
        if (noteHandler) harness.router.route("GET", NOTE_URL_PREFIX, noteHandler);
    }

    it("loadContactDetail：按 contact.id 读备注，section 落在 ec-profile 首位", async () => {
        const harness = createHarness();
        routeHost(harness, () => ({ status: 200, body: noteDto(7, "准备对接企业") }));
        harness.sandbox.state.contacts = [{
            orcidId: "0000-0000-7777-8888", displayName: "ES 显示名", nationality: "加拿大",
            country: "美国", keyword: "关键词乙", employment: "某大学", indexLevel: "CANDIDATE", contactId: 7
        }];
        harness.sandbox.state.selectedExpertOrcid = "0000-0000-7777-8888";

        await harness.sandbox.loadContactDetail(7, harness.sandbox.beginContactDetailPresentation());
        await flush();
        await flush();

        const panel = harness.fixture.contactDetail.querySelector('.detail-tab-panel[data-panel="contact"]');
        const host = panel.querySelector(HOST_SELECTOR);
        assert.ok(host, "the ec-profile host must be mounted in the contact panel");
        assert.strictEqual(panel.children[0], host, "ec-profile stays the first child of the contact panel");
        assert.strictEqual(host.firstElementChild.getAttribute("data-role"), "expert-shared-note");
        assert.strictEqual(noteRequests(harness).length, 1);
        assert.ok(noteRequests(harness)[0].url.endsWith("/7"));
        assert.strictEqual(noteContentOf(harness).textContent, "准备对接企业");
        assert.deepStrictEqual(harness.router.requests.filter((request) => request.method !== "GET"), []);
    });

    it("showExpertDetail（无 contact）：零备注请求并显示「尚无收发件箱备注」", async () => {
        const harness = createHarness();
        routeHost(harness, () => ({ status: 200, body: noteDto(1, "不该被读") }));

        await harness.sandbox.showExpertDetail({
            displayName: "ES 显示名", orcidId: "0000-0000-AAAA-1111", indexLevel: "RAW",
            nationality: "", country: "美国", keyword: "关键词甲", email: "a@example.com"
        }, harness.sandbox.beginContactDetailPresentation());
        await flush();
        await flush();

        assert.deepStrictEqual(noteRequests(harness), [], "an expert without a contact must not read notes");
        assert.strictEqual(noteContentOf(harness).textContent, NO_CONTACT_TEXT);
        assert.strictEqual(noteRefreshOf(harness).hidden, true);
        assert.strictEqual(noteErrorOf(harness).hidden, true);
    });
});
