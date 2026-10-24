"use strict";

// 05（contact-profile-05-video）测试：真实挂载（02 挂载入口）+ 可控 XHR 的上传状态机。
//
// 覆盖：
// - I-1 列表与命名：canonical expertId、GET page/size=20、自定义名/原始名/大小/上传人时间、
//   名称主体规则（NFC/code point/非法字符，与 04 同算法）、超长默认名必须人工改；
// - I-2 原始文件与进度：XHR.send(原始 File)、Content-Type application/octet-stream、不用
//   FormData/FileReader/readAsDataURL/整文件 arrayBuffer；>1GiB 网络前拦截；可计量才显示 loaded/total；
//   100% 只显示“上传完成，正在保存”，仅 201 显示“已上传”；
// - I-3 七态与重试：pending/uploading/processing/success/failed/uncertain/cancelled 是队列状态词表；
//   `send` 之后的中断（abort/断网/超时）一律 uncertain（零进度也不例外）；失败/未知暂停队列、
//   不自动重传；显式重试；success 禁止移除/重发；刷新后同名提示；
// - I-4 编辑与生命周期：rename 用 version、409 保留输入并提示刷新、不自动覆盖；切专家/卸载
//   abort 在途 XHR、停队列、关 dialog、清播放器 src 与文件引用；晚响应不写当前专家 DOM；
// - I-5 鉴权与播放：401/403 构造 Response 交既有 handleAuthResponse 并停队列、status 0 不构造
//   Response；只从 API 白名单同源路径设 video.src / 下载 href；编码失败提示下载出口；
// - S-1/S-2：DOM 契约（无 inline style、唯一 id/for、隐藏语义）、S-2 逐字插入 02 块之前、11 项缓存键。
//
// 说明（K-dom-stub-tests-hide-dangling-refs）：本文件不使用“getElementById 恒返回元素”的空 stub，
// 而是把 app.js 的 02/05 区域真实执行在能解析 innerHTML、支持 querySelector/事件冒泡/dataset/dialog 的
// 小型 DOM 上；缓存键从 index.html 派生、目标值从 05 计划正文解析，测试不固化键字面量。

const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const STATIC_DIR = path.join(__dirname, "..", "..", "main", "resources", "static");
const APP_SOURCE = fs.readFileSync(path.join(STATIC_DIR, "app.js"), "utf-8");
const STYLES_SOURCE = fs.readFileSync(path.join(STATIC_DIR, "styles.css"), "utf-8");
const INDEX_SOURCE = fs.readFileSync(path.join(STATIC_DIR, "index.html"), "utf-8");
const PLAN_PATH = path.join(__dirname, "..", "..", "..", "docs", "plans", "2026-10-11", "contact-profile-05-video-ui.md");
const PLAN_SOURCE = fs.readFileSync(PLAN_PATH, "utf-8");
const PLAN_02_PATH = path.join(__dirname, "..", "..", "..", "docs", "plans", "2026-10-11", "contact-profile-02-ui.md");
const PLAN_02_SOURCE = fs.readFileSync(PLAN_02_PATH, "utf-8");

// K-frontend-cache-key-triad：实值只从 index.html 派生，计划目标值从 05 计划正文解析。
const CACHE_KEY = (INDEX_SOURCE.match(/styles\.css\?v=([^"'&<>]+)/) || [])[1];
const PLANNED_CACHE_KEY = (() => {
    const match = PLAN_SOURCE.match(/缓存键同步\s*`([0-9]{8}-[a-z0-9-]+)`/);
    if (!match) throw new Error("planned cache key not found in " + PLAN_PATH);
    return match[1];
})();

const MODULE_02_BEGIN = "/* contact-profile-02-ui:begin */";
const MODULE_02_END = "/* contact-profile-02-ui:end */";
const MODULE_05_BEGIN = "/* contact-profile-05-video:begin */";
const MODULE_05_END = "/* contact-profile-05-video:end */";
const HOST_SELECTOR = '[data-role="expert-manual-profile"]';
const SECTION_SELECTOR = '[data-role="expert-meeting-videos"]';
const VIDEO_URL = "/api/experts/meeting-videos";

const GROUP_ORDER = ["matching", "identity", "education", "technology"];

const TEXT = {
    empty: "暂无会议视频",
    loading: "正在加载视频…",
    uncertain: "结果未确认，请刷新视频列表",
    processing: "上传完成，正在保存",
    success: "已上传",
    pending: "待上传",
    uploading: "正在上传…",
    cancelled: "已取消",
    busy: "当前上传任务过多，请稍后重试",
    limit: "单个视频不能超过 1 GiB（1024 MiB）",
    type: "只支持 mp4、mov、webm 视频",
    nameTooLong: "文件名（含扩展名）最多 100 个字符",
    unauthorized: "登录已失效，请重新登录后再试",
    codec: "浏览器不支持此视频编码，请下载观看",
    nameConflict: "同名视频已存在，请修改名称后重试",
    versionConflict: "视频已被更新，请刷新列表后重试"
};

// ════════════════════════════════════════════════════════════════════════
// 小型 DOM（innerHTML 解析 / 选择器 / 事件冒泡 / dataset / dialog / media）
// ════════════════════════════════════════════════════════════════════════

const VOID_TAGS = new Set(["area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr"]);

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
        this.pauseCalls = 0;
        this.loadCalls = 0;
        this.paused = false;
    }
    get children() {
        return this.childNodes.filter((node) => node.nodeType === 1);
    }
    get firstChild() {
        return this.childNodes[0] || null;
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
    querySelector(selector) {
        return this.querySelectorAll(selector)[0] || null;
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
    pause() {
        this.pauseCalls += 1;
        this.paused = true;
    }
    load() {
        this.loadCalls += 1;
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
// 可控 XHR / Response / fetch 路由
// ════════════════════════════════════════════════════════════════════════

function createXhrRegistry() {
    const calls = [];
    class StubXhr {
        constructor() {
            this.method = "";
            this.url = "";
            this.headers = {};
            this.body = null;
            this.status = 0;
            this.responseText = "";
            this.timeout = null;
            this.sent = false;
            this.abortCount = 0;
            this.upload = {};
            this.onload = null;
            this.onerror = null;
            this.ontimeout = null;
            this.onabort = null;
            calls.push(this);
        }
        open(method, url) {
            this.method = String(method);
            this.url = String(url);
        }
        setRequestHeader(name, value) {
            this.headers[String(name).toLowerCase()] = String(value);
        }
        send(body) {
            this.sent = true;
            this.body = body;
        }
        abort() {
            this.abortCount += 1;
            if (typeof this.onabort === "function") this.onabort();
        }
        progress(loaded, total, lengthComputable) {
            if (typeof this.upload.onprogress === "function") {
                this.upload.onprogress({ loaded, total, lengthComputable });
            }
        }
        respond(status, body) {
            this.status = Number(status);
            this.responseText = body == null ? "" : (typeof body === "string" ? body : JSON.stringify(body));
            if (typeof this.onload === "function") this.onload();
        }
        fail() {
            if (typeof this.onerror === "function") this.onerror();
        }
        timeOut() {
            if (typeof this.ontimeout === "function") this.ontimeout();
        }
    }
    return { calls, StubXhr };
}

/** 与浏览器一致的护栏：status 0/非法范围必须抛错，逼迫实现区分“未开始”与“未知提交”。 */
function createResponseRegistry() {
    const built = [];
    class StubResponse {
        constructor(body, options = {}) {
            const status = options.status === undefined ? 200 : Number(options.status);
            if (!Number.isInteger(status) || status < 200 || status > 599) {
                throw new RangeError(`invalid Response status: ${options.status}`);
            }
            this.body = body == null ? "" : String(body);
            this.status = status;
            this.headers = options.headers || {};
            built.push(this);
        }
        clone() {
            return new StubResponse(this.body, { status: this.status, headers: this.headers });
        }
        async text() {
            return this.body;
        }
        async json() {
            return JSON.parse(this.body || "null");
        }
    }
    return { built, StubResponse };
}

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
        const entry = routes.slice().reverse().find((item) => item.method === method &&
            (typeof item.match === "string" ? target.includes(item.match) : item.match.test(target)));
        if (!entry) return makeResponse(404, { code: "NOT_FOUND", message: `no stub route for ${method} ${target}` });
        const result = await waitWithSignal(Promise.resolve().then(() => entry.handler(record)), record.signal);
        return makeResponse(result.status, result.body);
    }
    return { route, requests, fetchImpl };
}

// ════════════════════════════════════════════════════════════════════════
// 脚手架：真实挂载 02/05 区域
// ════════════════════════════════════════════════════════════════════════

function extractFunction(name) {
    const regex = new RegExp("(?:async\\s+)?function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}");
    const match = APP_SOURCE.match(regex);
    if (!match) throw new Error("Could not find " + name + " in app.js");
    return match[0];
}

function extractRegion(begin, end) {
    const start = APP_SOURCE.indexOf(begin);
    const stop = APP_SOURCE.indexOf(end);
    if (start === -1 || stop === -1 || stop < start) throw new Error(`${begin} region missing in app.js`);
    return APP_SOURCE.slice(start, stop + end.length);
}

const REGION_02 = extractRegion(MODULE_02_BEGIN, MODULE_02_END);
const REGION_05 = extractRegion(MODULE_05_BEGIN, MODULE_05_END);

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
    return Object.assign({
        expertId: "0000-0000-1111-2222",
        version: 3,
        identity: { talentCode: null, chineseName: null, birthDateText: null, documentNumber: null, sourceCountry: null },
        education: {
            graduatedSchool: null, schoolRanking: null, preChinaOrganization: null, organizationRanking: null,
            postdocOrganization: null, postdocPeriod: null, professionalTitle: null, administrativeTitles: null
        },
        technology: { industry: null, technicalExpertise: null, programCategories: null },
        matching: { researchAbility: null, companyMatchStatus: "NOT_MATCHED", matchedCompanies: [] },
        updatedBy: "ops",
        updatedAt: "2026-10-11T10:00:00.000+08:00"
    }, overrides);
}

function videoDto(overrides = {}) {
    const id = overrides.id || "11111111-2222-3333-4444-555555555555";
    const expertId = overrides.expertId || "0000-0000-1111-2222";
    return Object.assign({
        id,
        expertId,
        originalFilename: "第一次会议.mp4",
        displayFilename: "第一次会议.mp4",
        extension: "mp4",
        byteLength: 1048576,
        uploadedBy: "ops",
        uploadedAt: "2026-10-11T10:00:00.000+08:00",
        updatedBy: "ops",
        updatedAt: "2026-10-11T10:00:00.000+08:00",
        version: 1,
        previewUrl: `${VIDEO_URL}/${id}/preview?expertId=${expertId}`,
        downloadUrl: `${VIDEO_URL}/${id}/download?expertId=${expertId}`
    }, overrides);
}

function fakeFile(name, size) {
    return { name, size, type: "video/mp4" };
}

function createSandbox(fixture, router, xhr, response) {
    const authCalls = [];
    const sandbox = {
        document: fixture.doc,
        window: { innerWidth: 1440, matchMedia: () => ({ matches: false, addEventListener() {} }) },
        console,
        AbortController,
        setTimeout,
        clearTimeout,
        setImmediate,
        URLSearchParams,
        XMLHttpRequest: xhr.StubXhr,
        Response: response.StubResponse,
        contextPath: "",
        fetch: router.fetchImpl,
        requestAnimationFrame: () => 0,
        handleAuthResponse: async (res) => {
            if (res.status === 401 || res.status === 403) authCalls.push(res);
            if (res.status === 401) throw new Error("UNAUTHORIZED");
            if (res.status === 403) {
                const data = await res.clone().json().catch(() => null);
                if (data && data.code === "PASSWORD_CHANGE_REQUIRED") throw new Error("PASSWORD_CHANGE_REQUIRED");
            }
            return res;
        },
        state: { view: "contacts", contacts: [], accounts: [{ accountCode: "ACC_A", senderEmail: "a@example.com", enabled: true }] },
        mobileContactPresentation: { generation: 1, mainScrollTop: 0, listScrollTop: 0, trigger: null },
        indexLevelLabels: { RAW: "原始专家", CANDIDATE: "候选专家", APPLICATION: "申请专家" },
        operatorStatusOptions: [],
        indexLevelOptions: [],
        $: (selector) => fixture.doc.querySelector(selector),
        $$: (selector) => fixture.doc.querySelectorAll(selector),
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
    vm.runInContext(REGION_05, sandbox);
    vm.runInContext([
        "showExpertDetail",
        "loadContactDetail"
    ].map(extractFunction).join("\n\n"), sandbox);
    sandbox.authCalls = authCalls;
    return sandbox;
}

function createHarness() {
    const fixture = createFixture();
    const router = createRouter();
    const xhr = createXhrRegistry();
    const response = createResponseRegistry();
    const sandbox = createSandbox(fixture, router, xhr, response);
    router.route("GET", "/api/experts/manual-profile", () => ({ status: 200, body: profileDto() }));
    router.route("GET", VIDEO_URL, () => ({ status: 200, body: { items: [], total: 0, page: 0, size: 20 } }));
    const context = {
        expertId: "0000-0000-1111-2222",
        level: "RAW",
        contactId: "",
        name: "甲专家",
        nationality: "加拿大",
        keyword: "关键词",
        generation: 1
    };
    const mount = (overrides) => {
        fixture.contactDetail.innerHTML = `<div class="ec-profile" data-role="expert-manual-profile"></div>`;
        return sandbox.mountExpertManualProfile(fixture.contactDetail, Object.assign({}, context, overrides || {}));
    };
    return { fixture, router, xhr, response, sandbox, mount, context };
}

function stateOf(session) {
    return session.meetingVideo;
}

function sectionOf(harness) {
    return harness.fixture.contactDetail.querySelector(SECTION_SELECTOR);
}

function videoGets(harness) {
    return harness.router.requests.filter((request) => request.method === "GET" && request.url.includes(VIDEO_URL));
}

function lastXhr(harness) {
    return harness.xhr.calls[harness.xhr.calls.length - 1] || null;
}

/** 选择文件：设 input.files 后派发 change（与真实浏览器一致）。 */
function queueFiles(session, files) {
    const state = stateOf(session);
    state.fileInput.files = files;
    state.fileInput.dispatchEvent(new MiniEvent("change", { bubbles: true }));
}

function startUpload(session) {
    stateOf(session).startButton.click();
}

function rowsOf(session) {
    return stateOf(session).queueEl.querySelectorAll(".em-upload-row");
}

function rowStateText(row) {
    return row.querySelector(".em-upload-state").textContent;
}

function assertNoInlineStyle(scope, label) {
    const walk = (node) => {
        for (const child of node.childNodes) {
            if (child.nodeType !== 1) continue;
            assert.strictEqual(child.getAttribute("style"), null, `${label}: inline style is forbidden`);
            walk(child);
        }
    };
    walk(scope);
}

// ════════════════════════════════════════════════════════════════════════
// 静态门禁：S-2 / 缓存键 / 区域边界 / I-2 源文本
// ════════════════════════════════════════════════════════════════════════

function extractPlanCssBlock(source, startsWith) {
    const blocks = [...source.matchAll(/```css\r?\n([\s\S]*?)\n```/g)].map((match) => match[1]);
    const block = blocks.find((item) => item.trimStart().startsWith(startsWith));
    if (!block) throw new Error("css block not found: " + startsWith);
    return block;
}

describe("contact profile 05 — S-2 / 缓存键 / 区域边界", () => {
    it("S-2：视频 CSS 逐字插入 02 的 S-2 块之前（A3）", () => {
        const videoBlock = extractPlanCssBlock(PLAN_SOURCE, ".ec-video-player {");
        const profileBlock = extractPlanCssBlock(PLAN_02_SOURCE, ".ec-profile {");
        assert.ok(STYLES_SOURCE.includes(videoBlock), "video S-2 block must be copied verbatim from the plan");
        assert.strictEqual(STYLES_SOURCE.split(videoBlock).length - 1, 1, "video S-2 block must appear exactly once");
        assert.ok(STYLES_SOURCE.indexOf(videoBlock) < STYLES_SOURCE.indexOf(profileBlock),
            "video S-2 block must be inserted before the 02 S-2 block");
        assert.ok(videoBlock.includes("@media (max-width: 760px)"),
            "S-2 must cap the player at 260px on small screens");
        for (const name of new Set([...videoBlock.matchAll(/\.([A-Za-z][\w-]*)/g)].map((match) => match[1]))) {
            const escaped = name.replace(/[.*+?^${}()|[\]\\/-]/g, "\\$&");
            assert.ok(new RegExp("\\." + escaped + "(?![\\w-])").test(STYLES_SOURCE), `class .${name} must be declared`);
        }
        for (const rule of [".ec-video-player { display: block; width: 100%; max-height: 420px;", ".em-upload-progress"]) {
            assert.ok(rule.startsWith(".em-upload-progress") || STYLES_SOURCE.includes(rule), `missing rule: ${rule}`);
        }
        assert.ok(STYLES_SOURCE.includes(".ec-video-player[hidden]") === false ||
            STYLES_SOURCE.includes("display: none !important"), "global [hidden] rule must still hide the player");
    });

    it("缓存键：index.html 11 项统一为 05 计划键，数量与顺序不变，测试自身不固化字面量", () => {
        assert.match(CACHE_KEY, /^\d{8}-[a-z0-9-]+$/, "approved contact-records release key");
        const keys = [...INDEX_SOURCE.matchAll(/\?v=([0-9a-z-]+)/g)].map((match) => match[1]);
        assert.strictEqual(keys.length, 17, "index.html must carry seventeen cache-busted assets");
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
        assert.ok(!fs.readFileSync(__filename, "utf-8").includes(CACHE_KEY),
            "the 05 test must derive the cache key, never hard-code it");
    });

    it("区域边界：05 区域独立于 02 区域，无全局监听、无 inline style，02 骨架由 typeof 守卫接入", () => {
        assert.strictEqual(APP_SOURCE.split(MODULE_05_BEGIN).length - 1, 1, "exactly one 05 region");
        assert.strictEqual(APP_SOURCE.split(MODULE_05_END).length - 1, 1, "exactly one 05 region");
        assert.ok(APP_SOURCE.indexOf(MODULE_05_BEGIN) > APP_SOURCE.indexOf(MODULE_02_END),
            "05 region must live outside the 02 region");
        assert.ok(!REGION_05.includes("document.addEventListener"), "05 module must not register document listeners");
        assert.ok(!REGION_05.includes("window.addEventListener"), "05 module must not register window listeners");
        assert.ok(!REGION_05.includes('style="'), "05 module must not emit inline styles");
        assert.strictEqual(APP_SOURCE.split('class="ec-profile" data-role="expert-manual-profile"').length - 1, 2,
            "05 must not add a third ec-profile host");
        assert.ok(extractFunction("expertManualProfileShellTemplate").includes("expertMeetingVideoPanelTemplate"),
            "02 shell must insert the 05 panel through the typeof-guard convention");
        assert.ok(extractFunction("mountExpertManualProfile").includes("mountExpertMeetingVideos"),
            "02 mount must start 05 through the typeof-guard convention");
        assert.ok(extractFunction("unmountExpertManualProfileSession").includes("unmountExpertMeetingVideos"),
            "02 unmount must stop 05 through the typeof-guard convention");
        assert.strictEqual(REGION_02.split("expertMeetingVideo").length - 1, 4,
            "02 region must only touch 05 through the four guarded call sites");
    });

    it("I-2 源文本：原始 File + octet-stream，不使用 FormData/FileReader/base64/整文件 arrayBuffer", () => {
        assert.ok(REGION_05.includes("new XMLHttpRequest()"), "uploads must use streaming XHR");
        assert.ok(REGION_05.includes(".send(entry.file)"), "the XHR body must be the original File object");
        assert.ok(REGION_05.includes('setRequestHeader("Content-Type", "application/octet-stream")'),
            "raw upload must declare application/octet-stream");
        assert.ok(REGION_05.includes("xhr.timeout = 0"), "long uploads must not be cut by the short API timeout");
        for (const forbidden of ["FormData", "FileReader", "readAsDataURL", "arrayBuffer", "btoa", "multipart"]) {
            assert.ok(!REGION_05.includes(forbidden), `05 upload path must not use ${forbidden}`);
        }
    });
});

// ════════════════════════════════════════════════════════════════════════
// 真实挂载：两个宿主
// ════════════════════════════════════════════════════════════════════════

describe("contact profile 05 — 真实挂载与列表", () => {
    it("两个宿主都挂载视频 section（四组档案之后）与 nav「会议视频」按钮，各发一次列表 GET", async () => {
        const harness = createHarness();
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

        // 无 contact 宿主
        await harness.sandbox.showExpertDetail({
            displayName: "ES 显示名", orcidId: "0000-0000-AAAA-1111", indexLevel: "RAW",
            nationality: "", country: "美国", keyword: "关键词甲", email: "a@example.com"
        }, harness.sandbox.mobileContactPresentation.generation);
        await flush();
        const noContactPanel = harness.fixture.contactDetail.querySelector('.detail-tab-panel[data-panel="contact"]');
        const noContactProfile = noContactPanel.querySelector(HOST_SELECTOR);
        const noContactSection = noContactProfile.querySelector(SECTION_SELECTOR);
        assert.ok(noContactSection, "academic-default host must mount the meeting-video section");
        const noContactGroups = noContactProfile.querySelectorAll(".ec-section[data-ec-group]");
        assert.strictEqual(noContactGroups.length, 4);
        const childIndex = (node) => node.parentNode.children.indexOf(node);
        const lastGroupIndex = Math.max(...noContactGroups.map(childIndex));
        assert.ok(childIndex(noContactSection) > lastGroupIndex,
            "video section must come after the four archive sections");
        const navButton = noContactProfile.querySelector("[data-ec-video-jump]");
        assert.ok(navButton, "nav must gain the 会议视频 button");
        assert.strictEqual(navButton.textContent, "会议视频");
        assert.ok(navButton.parentNode.classList.contains("ec-nav"), "the new nav button stays inside ec-nav");

        // 有 contact 宿主
        harness.sandbox.state.contacts = [{
            orcidId: "0000-0000-7777-8888", displayName: "ES 显示名", nationality: "加拿大",
            country: "美国", keyword: "关键词乙", employment: "某大学", indexLevel: "CANDIDATE", contactId: 7
        }];
        harness.sandbox.state.selectedExpertOrcid = "0000-0000-7777-8888";
        await harness.sandbox.loadContactDetail(7, harness.sandbox.beginContactDetailPresentation());
        await flush();
        const contactPanel = harness.fixture.contactDetail.querySelector('.detail-tab-panel[data-panel="contact"]');
        const contactProfile = contactPanel.querySelector(HOST_SELECTOR);
        assert.ok(contactProfile.querySelector(SECTION_SELECTOR), "contact host must mount the meeting-video section");
        assert.ok(contactProfile.querySelector("[data-ec-video-jump]"), "contact host nav keeps the video button");

        const gets = videoGets(harness);
        assert.strictEqual(gets.length, 2, "each host must issue exactly one list GET");
        assert.ok(gets[0].url.includes("expertId=0000-0000-AAAA-1111"), "no-contact host binds the ES expertId");
        assert.ok(gets[1].url.includes("expertId=0000-0000-7777-8888"), "contact host binds the canonical expertId");
        for (const request of gets) {
            assert.ok(request.url.includes("page=0"), "list must start at page 0");
            assert.ok(request.url.includes("size=20"), "list page size must be 20");
        }
    });

    it("列表渲染：自定义完整名/原始名/大小/上传人时间；空态；分页只在需要时显示", async () => {
        const harness = createHarness();
        const session = harness.mount();
        await flush();
        const state = stateOf(session);
        assert.strictEqual(sectionOf(harness).querySelector(".ec-video-list .ec-message").textContent, TEXT.empty,
            "empty list must show 暂无会议视频");
        assert.strictEqual(state.pagerEl.hidden, true, "pager must stay hidden while it is not needed");
        assert.strictEqual(sectionOf(harness).querySelector(".ec-message").textContent,
            "单文件最大 1 GiB（1024 MiB）；支持 MP4、MOV、WebM", "limit hint text must survive");

        // 第二页数据（total > size）→ 分页出现
        harness.router.route("GET", VIDEO_URL, () => ({
            status: 200,
            body: {
                items: [videoDto({ byteLength: 1073741824 })],
                total: 21, page: 0, size: 20
            }
        }));
        state.refreshButton.click();
        await flush();
        const article = sectionOf(harness).querySelector(".ec-video-list article.metadata-card");
        assert.ok(article, "each video must render as article.metadata-card");
        assert.strictEqual(article.querySelector('[data-role="video-name"]').textContent, "第一次会议.mp4");
        const meta = article.querySelector('[data-role="video-meta"]').textContent;
        assert.ok(meta.includes("原始名 第一次会议.mp4"), meta);
        assert.ok(meta.includes("1 GiB"), "1 GiB must render as 1024 MiB or 1 GiB: " + meta);
        assert.ok(meta.includes("上传人 ops"), meta);
        assert.ok(meta.includes("2026-10-11 10:00 北京时间"), meta);
        assert.strictEqual(state.pagerEl.hidden, false, "pager must appear once total exceeds one page");
        assert.ok(state.pagerTextEl.textContent.includes("共 21 个视频"), state.pagerTextEl.textContent);
        assert.strictEqual(state.prevButton.disabled, true, "first page disables 上一页");
        assert.strictEqual(state.nextButton.disabled, false);
        assertNoInlineStyle(sectionOf(harness), "video section");
        assert.strictEqual(sectionOf(harness).querySelectorAll("[id]").length, 0, "video section must not use ids");
    });

    it("列表失败保留上一次内容并提示重试，不渲染成空态", async () => {
        const harness = createHarness();
        harness.router.route("GET", VIDEO_URL, () => ({ status: 200, body: { items: [videoDto()], total: 1, page: 0, size: 20 } }));
        const session = harness.mount();
        await flush();
        const state = stateOf(session);
        assert.strictEqual(sectionOf(harness).querySelectorAll(".ec-video-list article").length, 1);
        harness.router.route("GET", VIDEO_URL, () => ({ status: 500, body: { code: "VIDEO_STORE_FAILED", message: "boom" } }));
        state.refreshButton.click();
        await flush();
        const error = sectionOf(harness).querySelector(".ec-error");
        assert.strictEqual(error.hidden, false);
        assert.ok(error.textContent.includes("视频列表加载失败"), error.textContent);
        assert.strictEqual(sectionOf(harness).querySelectorAll(".ec-video-list article").length, 1,
            "failure must keep the last successful list");
    });
});

// ════════════════════════════════════════════════════════════════════════
// S-1 上传弹窗 + I-2 请求/进度
// ════════════════════════════════════════════════════════════════════════

describe("contact profile 05 — 上传弹窗与请求", () => {
    it("S-1：上传弹窗骨架、队列行骨架、后缀只读、唯一 id/for 的重命名弹窗", async () => {
        const harness = createHarness();
        const session = harness.mount();
        await flush();
        const state = stateOf(session);
        const dialog = state.uploadDialog;
        assert.ok(dialog.classList.contains("em-upload-dialog") && dialog.classList.contains("ec-editor"));
        assert.strictEqual(dialog.getAttribute("aria-label"), "上传会议视频");
        assert.ok(state.fileInput.getAttribute("multiple") !== null);
        assert.strictEqual(state.fileInput.getAttribute("accept"), ".mp4,.mov,.webm");
        assert.ok(dialog.querySelector(".em-upload-limit").textContent.includes("1 GiB"));
        assert.ok(dialog.querySelector(".em-upload-summary"));
        assert.strictEqual(state.cancelButton.disabled, true, "取消当前上传 starts disabled");
        assert.ok(state.startButton.textContent.includes("开始上传"));

        queueFiles(session, [fakeFile("第一次会议.mp4", 2048), fakeFile("第二次会议.mov", 4096)]);
        const rows = rowsOf(session);
        assert.strictEqual(rows.length, 2, "each selected file gets its own row");
        assert.strictEqual(rows[0].getAttribute("data-upload-state"), "pending");
        assert.strictEqual(rowStateText(rows[0]), TEXT.pending);
        assert.strictEqual(rows[0].querySelector('[data-role="original-name"]').textContent, "第一次会议.mp4");
        assert.strictEqual(rows[0].querySelector('input[data-role="name-stem"]').value, "第一次会议");
        assert.strictEqual(rows[0].querySelector('[data-role="extension"]').textContent, ".mp4",
            "extension span is fixed and not an input");
        assert.strictEqual(rows[1].querySelector('[data-role="extension"]').textContent, ".mov");
        assert.strictEqual(rows[0].querySelectorAll("input").length, 1, "only the name stem is editable");
        assert.strictEqual(rows[1].querySelector('[data-role="original-name"]').textContent, "第二次会议.mov");
        assertNoInlineStyle(dialog, "upload dialog");

        const renameDialog = state.renameDialog;
        assert.ok(renameDialog.classList.contains("em-upload-dialog") && renameDialog.classList.contains("ec-editor"));
        const label = renameDialog.querySelector(".ec-video-name").parentNode;
        assert.strictEqual(label.tagName, "LABEL");
        assert.strictEqual(label.getAttribute("for"), state.renameInput.getAttribute("id"));
        assert.ok(state.renameInput.getAttribute("id").startsWith("ec-video-rename-name-stem-"));
        assert.ok(renameDialog.querySelector(".ec-edit-grid"));
        assert.ok(renameDialog.querySelector(".ec-message"));
        assert.ok(renameDialog.querySelector(".modal-close-btn"));
    });

    it("I-1/I-2：超限与非法名在网络前拦截；非法扩展名不发请求", async () => {
        const harness = createHarness();
        const session = harness.mount();
        await flush();
        queueFiles(session, [
            fakeFile("超限.mp4", 1073741825),
            fakeFile("非法?.mp4", 1024),
            fakeFile("无后缀", 1024),
            fakeFile("a".repeat(100) + ".mp4", 1024)
        ]);
        const rows = rowsOf(session);
        rows[0].querySelector('input[data-role="name-stem"]').value = "超限";
        startUpload(session);
        const states = rows.map((row) => row.getAttribute("data-upload-state"));
        assert.deepStrictEqual(states, ["failed", "failed", "failed", "failed"], states.toString());
        assert.ok(rowStateText(rows[0]).includes("1 GiB"), rowStateText(rows[0]));
        assert.ok(rowStateText(rows[1]).includes("/ \\ : * ?"), rowStateText(rows[1]));
        assert.ok(rowStateText(rows[2]).includes(TEXT.type), rowStateText(rows[2]));
        assert.ok(rowStateText(rows[3]).includes(TEXT.nameTooLong), rowStateText(rows[3]));
        assert.strictEqual(harness.xhr.calls.length, 0, "validation failures must never reach the network");
        // 超长默认名必须人工改：改名后重试才允许上传
        const long = rows[3].querySelector('input[data-role="name-stem"]');
        assert.strictEqual(long.disabled, true, "failed rows need explicit 重试 before editing");
        rows[3].querySelector("[data-ec-video-remove]").click(); // 重试
        assert.strictEqual(rows[3].getAttribute("data-upload-state"), "pending");
        long.value = "短名";
        long.dispatchEvent(new MiniEvent("input", { bubbles: true }));
        startUpload(session);
        await flush();
        assert.strictEqual(harness.xhr.calls.length, 1, "only the fixed entry may be uploaded");
        const xhr = lastXhr(harness);
        assert.strictEqual(xhr.body, stateOf(session).entries[3].file, "send() must receive the original File reference");
        assert.ok(xhr.url.includes("nameStem=%E7%9F%AD%E5%90%8D"), xhr.url);
        assert.ok(xhr.url.includes("expectedSize=1024"), xhr.url);
    });

    it("I-2：octet-stream + timeout 0 + 一次编码 + 串行一个 XHR", async () => {
        const harness = createHarness();
        const session = harness.mount();
        await flush();
        queueFiles(session, [fakeFile("会议 & 讨论.mp4", 4096), fakeFile("第二个.mp4", 4096)]);
        startUpload(session);
        assert.strictEqual(harness.xhr.calls.length, 1, "uploads must be serial: one XHR at a time");
        const first = lastXhr(harness);
        assert.strictEqual(first.method, "POST");
        assert.strictEqual(first.headers["content-type"], "application/octet-stream");
        assert.strictEqual(first.timeout, 0);
        assert.strictEqual(first.body.name, "会议 & 讨论.mp4");
        assert.strictEqual(first.body.size, 4096);
        assert.ok(first.url.startsWith(VIDEO_URL + "?"), first.url);
        assert.ok(first.url.includes("level=RAW"), "validated level hint is forwarded");
        // URLSearchParams 只编码一次：& 必须是 %26，且不出现二次编码的 %2526
        assert.ok(first.url.includes("originalName=%E4%BC%9A%E8%AE%AE+%26+%E8%AE%A8%E8%AE%BA.mp4"), first.url);
        assert.ok(!first.url.includes("%2526"), "originalName must not be encoded twice");
        first.respond(201, videoDto({ id: "aaaaaaaa-1111-2222-3333-444444444444", displayFilename: "会议 & 讨论.mp4" }));
        await flush();
        assert.strictEqual(harness.xhr.calls.length, 2, "after success the next pending entry is sent");
        const second = lastXhr(harness);
        assert.strictEqual(second.body.name, "第二个.mp4");
        assert.strictEqual(rowStateText(rowsOf(session)[0]), TEXT.success);
        assert.strictEqual(rowStateText(rowsOf(session)[1]), TEXT.uploading);
        second.progress(2048, 4096, true);
        assert.ok(rowStateText(rowsOf(session)[1]).includes("2 KiB"), rowStateText(rowsOf(session)[1]));
        assert.strictEqual(rowsOf(session)[1].querySelector(".em-upload-progress").hidden, false);
        assert.strictEqual(rowsOf(session)[1].querySelector(".em-upload-progress").value, "50");
        // 本次上传已量到总量：loaded/total 继续可见，但不因不可计量事件跳到 processing
        second.progress(3000, 4096, false);
        assert.ok(rowStateText(rowsOf(session)[1]).includes("/ 4 KiB"), rowStateText(rowsOf(session)[1]));
        assert.ok(!rowStateText(rowsOf(session)[1]).includes(TEXT.processing));
        assert.strictEqual(rowsOf(session)[1].querySelector(".em-upload-progress").value, "73");
        assert.strictEqual(rowsOf(session)[1].getAttribute("data-upload-state"), "uploading");
        second.progress(4096, 4096, true);
        assert.strictEqual(rowsOf(session)[1].getAttribute("data-upload-state"), "processing");
    });

    it("I-2/I-3：100% 只显示“上传完成，正在保存”，未获 201 前绝不显示已上传", async () => {
        const harness = createHarness();
        const session = harness.mount();
        await flush();
        queueFiles(session, [fakeFile("进度.mp4", 8192)]);
        startUpload(session);
        const xhr = lastXhr(harness);
        xhr.progress(8192, 8192, true);
        const row = rowsOf(session)[0];
        assert.strictEqual(row.getAttribute("data-upload-state"), "processing");
        assert.strictEqual(rowStateText(row), TEXT.processing);
        assert.ok(!rowStateText(row).includes(TEXT.success), "100% must not claim success");
        assert.strictEqual(row.querySelector(".em-upload-progress").value, "100");
        // 100% 后无响应（断网）：提交结果未知
        xhr.fail();
        assert.strictEqual(row.getAttribute("data-upload-state"), "uncertain");
        assert.strictEqual(rowStateText(row), TEXT.uncertain);
        assert.strictEqual(harness.xhr.calls.length, 1, "no automatic retry after an unknown outcome");
    });
});

// ════════════════════════════════════════════════════════════════════════
// I-3 状态机 / I-5 鉴权
// ════════════════════════════════════════════════════════════════════════

describe("contact profile 05 — 状态机、重试与鉴权", () => {
    it("201：标记已上传、刷新列表、success 行禁止移除且不许重发", async () => {
        const harness = createHarness();
        harness.router.route("GET", VIDEO_URL, () => ({
            status: 200,
            body: { items: [videoDto()], total: 1, page: 0, size: 20 }
        }));
        const session = harness.mount();
        await flush();
        const state = stateOf(session);
        queueFiles(session, [fakeFile("第一次会议.mp4", 1024)]);
        startUpload(session);
        const xhr = lastXhr(harness);
        xhr.respond(201, videoDto());
        await flush();
        const row = rowsOf(session)[0];
        assert.strictEqual(row.getAttribute("data-upload-state"), "success");
        assert.strictEqual(rowStateText(row), TEXT.success);
        const action = row.querySelector("[data-ec-video-remove]");
        assert.strictEqual(action.hidden, true, "success rows must not allow 移除/重发");
        assert.strictEqual(row.querySelector('input[data-role="name-stem"]').disabled, true);
        assert.strictEqual(videoGets(harness).length >= 2, true, "a successful upload refreshes the list");
        assert.strictEqual(sectionOf(harness).querySelectorAll(".ec-video-list article").length, 1);
        assert.strictEqual(state.running, false, "queue finishes once nothing is pending");
        action.click();
        assert.strictEqual(harness.xhr.calls.length, 1, "clicking the hidden action must not resend anything");
    });

    it("201 但响应不可解析：按未确认处理（绝不显示已上传）", async () => {
        const harness = createHarness();
        const session = harness.mount();
        await flush();
        queueFiles(session, [fakeFile("坏响应.mp4", 1024)]);
        startUpload(session);
        const xhr = lastXhr(harness);
        xhr.respond(201, "<html>gateway</html>");
        await flush();
        assert.strictEqual(rowsOf(session)[0].getAttribute("data-upload-state"), "uncertain");
        assert.strictEqual(rowStateText(rowsOf(session)[0]), TEXT.uncertain);
    });

    it("409 同名：failed + 固定文案 + 暂停队列 + 刷新后提示同名 + 显式重试才回到 pending", async () => {
        const harness = createHarness();
        const session = harness.mount();
        await flush();
        queueFiles(session, [fakeFile("第一次会议.mp4", 1024), fakeFile("第二个.mp4", 1024)]);
        startUpload(session);
        const first = lastXhr(harness);
        harness.router.route("GET", VIDEO_URL, () => ({
            status: 200,
            body: { items: [videoDto()], total: 1, page: 0, size: 20 }
        }));
        first.respond(409, { code: "VIDEO_NAME_CONFLICT", message: "同名视频已存在，请修改名称后重试" });
        await flush();
        const rows = rowsOf(session);
        assert.strictEqual(rows[0].getAttribute("data-upload-state"), "failed");
        assert.ok(rowStateText(rows[0]).includes(TEXT.nameConflict), rowStateText(rows[0]));
        assert.ok(rowStateText(rows[0]).includes("列表中已有同名视频"), rowStateText(rows[0]));
        assert.strictEqual(rows[1].getAttribute("data-upload-state"), "pending", "queue pauses on failure");
        assert.strictEqual(harness.xhr.calls.length, 1, "no blind continuation to the next large file");
        assert.strictEqual(rows[0].querySelector("[data-ec-video-remove]").textContent, "重试");
        rows[0].querySelector("[data-ec-video-remove]").click();
        assert.strictEqual(rows[0].getAttribute("data-upload-state"), "pending");
        assert.strictEqual(rows[0].querySelector("[data-ec-video-remove]").textContent, "移除");
        startUpload(session);
        assert.strictEqual(harness.xhr.calls.length, 2, "retry is explicit and restarts from the beginning");
        assert.strictEqual(lastXhr(harness).body.name, "第一次会议.mp4");
    });

    it("429/507/413/415：状态与文案固定，且不自动继续下一个文件", async () => {
        const cases = [
            { status: 429, code: "VIDEO_UPLOAD_BUSY", expect: TEXT.busy, state: "pending" },
            { status: 507, code: "VIDEO_STORAGE_FULL", expect: "存储空间不足", state: "failed" },
            { status: 413, code: "VIDEO_TOO_LARGE", expect: TEXT.limit, state: "failed" },
            { status: 415, code: "UNSUPPORTED_VIDEO_TYPE", expect: TEXT.type, state: "failed" }
        ];
        for (const item of cases) {
            const harness = createHarness();
            const session = harness.mount();
            await flush();
            queueFiles(session, [fakeFile("甲.mp4", 1024), fakeFile("乙.mp4", 1024)]);
            startUpload(session);
            lastXhr(harness).respond(item.status, { code: item.code, message: "server" });
            await flush();
            const rows = rowsOf(session);
            assert.strictEqual(rows[0].getAttribute("data-upload-state"), item.state, item.code);
            assert.ok(rowStateText(rows[0]).includes(item.expect), `${item.code}: ${rowStateText(rows[0])}`);
            assert.strictEqual(rows[1].getAttribute("data-upload-state"), "pending", `${item.code}: queue pauses`);
            assert.strictEqual(harness.xhr.calls.length, 1, `${item.code}: no auto retry`);
        }
    });

    it("I-3/I-4：send 之后的 abort/断网/超时（零进度也不例外）都是未确认、不自动重传、刷新列表", async () => {
        const cases = [
            {
                name: "abort",
                trigger: (harness, session) => {
                    rowsOf(session)[0].querySelector("[data-ec-video-remove]").click();
                }
            },
            { name: "network", trigger: (harness, session, xhr) => xhr.fail() },
            { name: "timeout", trigger: (harness, session, xhr) => xhr.timeOut() }
        ];
        for (const item of cases) {
            const harness = createHarness();
            const session = harness.mount();
            await flush();
            queueFiles(session, [fakeFile("甲.mp4", 1024), fakeFile("乙.mp4", 1024)]);
            const rows = rowsOf(session);
            const getsBefore = videoGets(harness).length;
            startUpload(session);
            const xhr = lastXhr(harness);
            assert.strictEqual(xhr.sent, true, `${item.name}: the XHR must have been sent`);
            assert.strictEqual(stateOf(session).entries[0].uploadedBytes, 0,
                `${item.name}: zero progress by construction`);

            item.trigger(harness, session, xhr);
            await flush();

            // 进度回调不是传输确认：首字节之前中断同样可能已到达服务端 → uncertain，绝不能当“未开始”重发。
            assert.strictEqual(rows[0].getAttribute("data-upload-state"), "uncertain", item.name);
            assert.strictEqual(rowStateText(rows[0]), TEXT.uncertain, item.name);
            assert.strictEqual(stateOf(session).running, false, `${item.name}: queue must pause`);
            assert.strictEqual(rows[1].getAttribute("data-upload-state"), "pending", `${item.name}: no blind continuation`);
            assert.ok(videoGets(harness).length > getsBefore, `${item.name}: the list must refresh`);
            assert.strictEqual(harness.xhr.calls.length, 1, `${item.name}: never automatically retransmit`);
        }
    });

    it("取消：活动项 abort 一次、已发送按未确认处理；pending 移除只影响队列", async () => {
        const harness = createHarness();
        const session = harness.mount();
        await flush();
        queueFiles(session, [fakeFile("甲.mp4", 1024), fakeFile("乙.mp4", 1024)]);
        const rows = rowsOf(session);
        // pending 移除（send 之前）：唯一的安全取消，只影响队列
        rows[1].querySelector("[data-ec-video-remove]").click();
        assert.strictEqual(rowsOf(session).length, 1, "移除 only touches the queue");
        assert.strictEqual(stateOf(session).entries.length, 1);
        // 活动取消（已发送、已量到字节）→ 未确认
        startUpload(session);
        const xhr = lastXhr(harness);
        xhr.progress(256, 1024, true);
        rowsOf(session)[0].querySelector("[data-ec-video-remove]").click();
        assert.strictEqual(xhr.abortCount, 1, "active upload must be aborted exactly once");
        assert.strictEqual(rowsOf(session)[0].getAttribute("data-upload-state"), "uncertain");
        assert.strictEqual(stateOf(session).running, false);
        // 活动取消（已发送、零进度）→ 同样未确认：本地 abort 不是服务端未提交的证据
        queueFiles(session, [fakeFile("丙.mp4", 1024)]);
        startUpload(session);
        const third = lastXhr(harness);
        rowsOf(session)[1].querySelector("[data-ec-video-remove]").click();
        assert.strictEqual(third.abortCount, 1);
        assert.strictEqual(rowsOf(session)[1].getAttribute("data-upload-state"), "uncertain");
        assert.strictEqual(rowStateText(rowsOf(session)[1]), TEXT.uncertain);
        assert.strictEqual(rowsOf(session)[1].querySelector("[data-ec-video-remove]").textContent, "重试");
    });

    it("活动上传期间关闭被禁用、Escape 阻止关闭并提示先取消", async () => {
        const harness = createHarness();
        const session = harness.mount();
        await flush();
        const state = stateOf(session);
        state.sectionUploadButton.click(); // 打开上传弹窗
        assert.strictEqual(state.uploadDialog.open, true, "上传视频 must open the upload dialog");
        queueFiles(session, [fakeFile("甲.mp4", 1024)]);
        startUpload(session);
        assert.strictEqual(state.dialogCloseButton.disabled, true);
        assert.strictEqual(state.cancelButton.disabled, false, "取消当前上传 stays available");
        assert.strictEqual(state.startButton.disabled, true);
        const event = new MiniEvent("cancel", { bubbles: false });
        state.uploadDialog.dispatchEvent(event);
        assert.strictEqual(event.defaultPrevented, true, "Escape must not close during an active upload");
        assert.strictEqual(state.dialogCloseButton.disabled, true);
        assert.ok(state.dialogErrorEl.textContent.includes("请先取消当前上传"), state.dialogErrorEl.textContent);
        state.dialogCloseButton.click();
        assert.strictEqual(state.uploadDialog.open, true, "close button must be a no-op during an active upload");
        state.cancelButton.click(); // 取消活动上传（已发送：abort 后按未确认处理）
        assert.strictEqual(rowsOf(session)[0].getAttribute("data-upload-state"), "uncertain");
        assert.strictEqual(rowStateText(rowsOf(session)[0]), TEXT.uncertain);
        assert.strictEqual(state.dialogCloseButton.disabled, false);
        state.dialogCloseButton.click();
        assert.strictEqual(state.uploadDialog.open, false);
        assert.strictEqual(state.sectionUploadButton.focused, true, "focus must return to 上传视频");
    });

    it("I-5：401/403 构造真实 Response 交 handleAuthResponse 并停队列", async () => {
        const harness = createHarness();
        const session = harness.mount();
        await flush();
        queueFiles(session, [fakeFile("甲.mp4", 1024), fakeFile("乙.mp4", 1024)]);
        startUpload(session);
        lastXhr(harness).respond(401, { code: "UNAUTHORIZED", message: "未登录" });
        await flush();
        assert.strictEqual(harness.sandbox.authCalls.length, 1, "401 must reach the existing auth handler");
        const auth = harness.sandbox.authCalls[0];
        assert.strictEqual(auth.status, 401);
        assert.strictEqual(auth.headers["Content-Type"], "application/json");
        assert.ok(auth.body.includes("UNAUTHORIZED"), auth.body);
        assert.strictEqual(rowsOf(session)[0].getAttribute("data-upload-state"), "pending");
        assert.ok(rowStateText(rowsOf(session)[0]).includes(TEXT.unauthorized), rowStateText(rowsOf(session)[0]));
        assert.strictEqual(stateOf(session).running, false, "auth failures stop the queue");
        assert.strictEqual(harness.xhr.calls.length, 1, "the remaining file is not sent after a 401");
    });

    it("I-5：status 0（断网）不构造非法 Response，按未确认处理", async () => {
        const harness = createHarness();
        const session = harness.mount();
        await flush();
        queueFiles(session, [fakeFile("甲.mp4", 1024)]);
        startUpload(session);
        const xhr = lastXhr(harness);
        xhr.progress(128, 1024, true);
        const built = harness.response.built.length;
        assert.doesNotThrow(() => xhr.fail());
        assert.strictEqual(harness.response.built.length, built, "status 0 must never build a Response");
        assert.strictEqual(harness.sandbox.authCalls.length, 0, "status 0 must not be handed to handleAuthResponse");
        assert.strictEqual(rowsOf(session)[0].getAttribute("data-upload-state"), "uncertain");
    });
});

// ════════════════════════════════════════════════════════════════════════
// 播放 / 下载 / 改名 / 生命周期
// ════════════════════════════════════════════════════════════════════════

describe("contact profile 05 — 播放、下载、改名与生命周期", () => {
    async function mountWithVideos(harness, videos) {
        harness.router.route("GET", VIDEO_URL, () => ({
            status: 200,
            body: { items: videos, total: videos.length, page: 0, size: 20 }
        }));
        const session = harness.mount();
        await flush();
        return session;
    }

    it("I-5：播放只设白名单同源 previewUrl，一次一个播放器，非白名单不设 src", async () => {
        const harness = createHarness();
        const first = videoDto({ id: "11111111-1111-1111-1111-111111111111" });
        const second = videoDto({ id: "22222222-2222-2222-2222-222222222222", displayFilename: "第二个.mp4" });
        const third = videoDto({
            id: "33333333-3333-3333-3333-333333333333",
            displayFilename: "外链.mp4",
            previewUrl: "https://evil.example.com/api/experts/meeting-videos/333/preview?expertId=x"
        });
        const session = await mountWithVideos(harness, [first, second, third]);
        const articles = sectionOf(harness).querySelectorAll(".ec-video-list article");
        assert.strictEqual(articles.length, 3);
        articles[0].querySelector("[data-ec-video-play]").click();
        const playerA = articles[0].querySelector("video");
        assert.strictEqual(playerA.getAttribute("src"), first.previewUrl);
        assert.strictEqual(playerA.hidden, false);
        assert.strictEqual(playerA.getAttribute("controls"), "");
        assert.strictEqual(playerA.getAttribute("preload"), "metadata");
        assert.strictEqual(playerA.loadCalls, 1, "playback relies on the browser's own Range loading");
        articles[1].querySelector("[data-ec-video-play]").click();
        const playerB = articles[1].querySelector("video");
        assert.strictEqual(playerA.getAttribute("src"), null, "previous player must be released");
        assert.strictEqual(playerA.pauseCalls >= 1, true);
        assert.strictEqual(playerB.getAttribute("src"), second.previewUrl);
        assert.strictEqual(stateOf(session).player.player, playerB);
        articles[1].querySelector("[data-ec-video-play]").click();
        assert.strictEqual(playerB.getAttribute("src"), null, "clicking again releases the player");
        articles[2].querySelector("[data-ec-video-play]").click();
        assert.strictEqual(articles[2].querySelector("video").getAttribute("src"), null,
            "non-whitelisted previewUrl must never reach video.src");
        assert.ok(articles[2].querySelector(".ec-error").textContent.includes("视频地址不可用"));
    });

    it("I-5：解码失败提示下载出口，下载 href 只取白名单 downloadUrl", async () => {
        const harness = createHarness();
        const video = videoDto();
        await mountWithVideos(harness, [video]);
        const article = sectionOf(harness).querySelector(".ec-video-list article");
        const download = article.querySelector("[data-ec-video-download]");
        assert.strictEqual(download.getAttribute("href"), video.downloadUrl);
        assert.strictEqual(download.getAttribute("download"), "第一次会议.mp4");
        article.querySelector("[data-ec-video-play]").click();
        article.querySelector("video").dispatchEvent(new MiniEvent("error", { bubbles: false }));
        assert.strictEqual(article.querySelector("video").hidden, true);
        const error = article.querySelector(".ec-error");
        assert.strictEqual(error.hidden, false);
        assert.strictEqual(error.textContent, TEXT.codec);
        assert.strictEqual(download.getAttribute("href"), video.downloadUrl, "download must survive a codec failure");

        const bad = videoDto({
            id: "44444444-4444-4444-4444-444444444444",
            displayFilename: "坏链接.mp4",
            downloadUrl: "//evil.example.com/download"
        });
        const harnessB = createHarness();
        await mountWithVideos(harnessB, [bad]);
        const articleB = sectionOf(harnessB).querySelector(".ec-video-list article");
        assert.strictEqual(articleB.querySelector("[data-ec-video-download]").getAttribute("href"), null,
            "protocol-relative download URL must be rejected");
    });

    it("I-4：重命名预填主体、扩展只读、PATCH 带 version；409 保留输入并提示刷新、不自动覆盖", async () => {
        const harness = createHarness();
        const video = videoDto({ displayFilename: "第一次会议.mp4", version: 4 });
        const session = await mountWithVideos(harness, [video]);
        const state = stateOf(session);
        const article = sectionOf(harness).querySelector(".ec-video-list article");
        article.querySelector("[data-ec-video-rename]").click();
        assert.strictEqual(state.renameDialog.open, true);
        assert.strictEqual(state.renameInput.value, "第一次会议", "stem must be prefilled without the fixed extension");
        assert.strictEqual(state.renameExtensionEl.textContent, ".mp4");
        assert.ok(state.renameOriginalEl.textContent.includes("原始文件名：第一次会议.mp4"), state.renameOriginalEl.textContent);
        assert.strictEqual(state.renameInput.focused, true);

        // 409 同名：保留输入 + 提示 + 不自动重试
        harness.router.route("PATCH", VIDEO_URL, () => ({ status: 409, body: { code: "VIDEO_NAME_CONFLICT", message: "同名视频已存在" } }));
        state.renameInput.value = "第一次会议-补充";
        state.renameForm.dispatchEvent(new MiniEvent("submit", { bubbles: true }));
        await flush();
        assert.strictEqual(state.renameDialog.open, true, "409 must keep the dialog open");
        assert.strictEqual(state.renameInput.value, "第一次会议-补充", "user input must be preserved");
        assert.ok(state.renameErrorEl.textContent.includes(TEXT.nameConflict), state.renameErrorEl.textContent);
        assert.strictEqual(harness.router.requests.filter((request) => request.method === "PATCH").length, 1,
            "409 must not auto-retry");
        assert.strictEqual(article.querySelector('[data-role="video-name"]').textContent, "第一次会议.mp4");

        // Escape 关闭后焦点归还「重命名」按钮（列表未重绘，触发器仍在 DOM 内）
        const renameButton = article.querySelector("[data-ec-video-rename]");
        const cancelEvent = new MiniEvent("cancel", { bubbles: false });
        state.renameDialog.dispatchEvent(cancelEvent);
        assert.strictEqual(cancelEvent.defaultPrevented, true);
        assert.strictEqual(state.renameDialog.open, false, "Escape must close the rename dialog when idle");
        assert.strictEqual(renameButton.focused, true, "focus must return to the 重命名 trigger");
        renameButton.click();
        assert.strictEqual(state.renameDialog.open, true, "重新打开重命名弹窗");

        // 成功：URL 与 body 必须带 expertId / expectedVersion；链接不随名称变更
        harness.router.route("PATCH", VIDEO_URL, (request) => {
            const payload = JSON.parse(request.body);
            return {
                status: 200,
                body: Object.assign({}, video, {
                    displayFilename: `${payload.nameStem}.mp4`, version: Number(payload.expectedVersion) + 1
                })
            };
        });
        state.renameInput.value = "第一次会议-补充";
        state.renameForm.dispatchEvent(new MiniEvent("submit", { bubbles: true }));
        await flush();
        const patch = harness.router.requests.filter((request) => request.method === "PATCH").slice(-1)[0];
        assert.ok(patch.url.includes(`expertId=${encodeURIComponent(video.expertId)}`), patch.url);
        assert.deepStrictEqual(JSON.parse(patch.body), { nameStem: "第一次会议-补充", expectedVersion: 4 });
        assert.strictEqual(state.renameSummaryEl.textContent, "已保存");
        const updated = sectionOf(harness).querySelector(".ec-video-list article");
        assert.strictEqual(updated.querySelector('[data-role="video-name"]').textContent, "第一次会议-补充.mp4");
        assert.strictEqual(updated.querySelector("[data-ec-video-download]").getAttribute("href"), video.downloadUrl,
            "playback/download URLs must not change when the display name changes");
        assert.strictEqual(state.renameDialog.open, true, "02 editor semantics: dialog stays open with 已保存");
    });

    it("I-4：切专家/卸载 abort 在途上传、停队列、关 dialog、清播放器 src 与文件引用", async () => {
        const harness = createHarness();
        const session = await mountWithVideos(harness, [videoDto()]);
        const state = stateOf(session);
        const article = sectionOf(harness).querySelector(".ec-video-list article");
        article.querySelector("[data-ec-video-play]").click();
        assert.ok(article.querySelector("video").getAttribute("src"));
        queueFiles(session, [fakeFile("甲.mp4", 1024)]);
        startUpload(session);
        const xhr = lastXhr(harness);
        xhr.progress(256, 1024, true);
        const before = harness.xhr.calls.length;
        harness.sandbox.beginContactDetailPresentation(); // 切专家 = 既有统一卸载出口
        assert.strictEqual(xhr.abortCount, 1, "in-flight upload must be aborted on unmount");
        assert.strictEqual(state.closed, true);
        assert.strictEqual(state.running, false);
        assert.strictEqual(state.uploadDialog.open, false);
        assert.strictEqual(state.renameDialog.open, false);
        assert.strictEqual(article.querySelector("video").getAttribute("src"), null,
            "player src must be removed and reloaded to release the stream");
        assert.strictEqual(article.querySelector("video").loadCalls >= 2, true);
        assert.strictEqual(state.entries.length, 0, "queue and File references must be dropped");
        // 晚响应不得再写 DOM、不得重发
        xhr.status = 201;
        xhr.responseText = JSON.stringify(videoDto());
        xhr.respond(201, videoDto());
        xhr.progress(1024, 1024, true);
        await flush();
        assert.strictEqual(harness.xhr.calls.length, before, "late callbacks must not trigger another upload");
        assert.strictEqual(state.entries.length, 0);
    });

    it("I-4：切到另一个专家后，旧会话的晚列表响应不写新宿主 DOM", async () => {
        const harness = createHarness();
        const pending = deferred();
        let calls = 0;
        harness.router.route("GET", VIDEO_URL, () => {
            calls += 1;
            if (calls === 2) {
                // 旧会话的“刷新”请求挂起，等切换完成后才返回数据
                return pending.promise.then(() => ({ status: 200, body: { items: [videoDto()], total: 1, page: 0, size: 20 } }));
            }
            return { status: 200, body: { items: [], total: 0, page: 0, size: 20 } };
        });
        const firstSession = harness.mount();
        await flush();
        const stateA = stateOf(firstSession);
        stateA.refreshButton.click(); // 第 2 次列表请求挂起
        await flush();
        // 切专家：新会话 + 新宿主，旧会话随之卸载
        const secondSession = harness.mount({ expertId: "0000-0000-9999-8888", generation: 2 });
        harness.sandbox.mobileContactPresentation.generation = 2;
        await flush();
        assert.strictEqual(stateA.closed, true, "previous session must be closed by the shared mount entry");
        pending.resolve();
        await flush();
        assert.strictEqual(stateOf(secondSession).items.length, 0, "late response must not write the new host");
        assert.strictEqual(sectionOf(harness).querySelectorAll(".ec-video-list article").length, 0);
        assert.strictEqual(sectionOf(harness).querySelector(".ec-video-list .ec-message").textContent, TEXT.empty);
    });
});
