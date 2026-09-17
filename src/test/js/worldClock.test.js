"use strict";

// fast-p 01 独立组件测试（T4；I-1～I-9 / S-1～S-4）
//
// 载入方式：Node require 生产模块 world-clock.js（module.exports），真实加载宿主的
// meeting-confirmation.js 复用其 filterZones；注入最小 DOM / observer / timer / 时钟环境，
// 事件真实 dispatch 驱动。CSS 与三份 DOM 模板的期望直接从计划标记块提取（不生成第二份来源），
// 换算表的期望值在本文件硬编码。无 npm 依赖、无真实网络。

const fs = require("fs");
const path = require("path");
const vm = require("vm");
const util = require("util");
const assert = require("assert");
const { describe, it, beforeEach, afterEach } = require("node:test");

// 断言失败时 node:test 会 inspect 实参；DOM stub 是巨型环形对象图，必须自定义打印，
// 否则一次失败会让错误序列化退化成分钟级挂起。
const INSPECT = util.inspect.custom;

const STATIC_DIR = path.join(__dirname, "..", "..", "main", "resources", "static");
const PLAN_PATH = path.join(__dirname, "..", "..", "..", "docs", "plans", "2026-09-17",
    "global-world-clock-01-component.md");

const moduleSource = fs.readFileSync(path.join(STATIC_DIR, "world-clock.js"), "utf-8");
const cssPath = path.join(STATIC_DIR, "world-clock.css");
const cssSource = fs.readFileSync(cssPath, "utf-8");
const planSource = fs.readFileSync(PLAN_PATH, "utf-8");
// 先于任何 document stub 载入，保证模块载入时不触发浏览器自动启动分支。
const WorldClock = require(path.join(STATIC_DIR, "world-clock.js"));
const Meeting = require(path.join(STATIC_DIR, "meeting-confirmation.js"));

function planBlock(name, language) {
    const lines = planSource.split("\n");
    const begin = lines.indexOf("<!-- WORLD_CLOCK_" + name + "_BEGIN -->");
    const end = lines.indexOf("<!-- WORLD_CLOCK_" + name + "_END -->");
    assert.ok(begin >= 0 && end > begin, "计划缺少 WORLD_CLOCK_" + name + " 标记块");
    assert.strictEqual(lines[begin + 1], "```" + language, name + " 标记块语言不是 " + language);
    assert.strictEqual(lines[end - 1], "```", name + " 标记块缺少收尾 fence");
    return lines.slice(begin + 2, end - 1).join("\n") + "\n";
}

// ---------------------------------------------------------------------------
// 最小 DOM / 环境
// ---------------------------------------------------------------------------

const VOID_TAGS = new Set(["input", "br", "img", "hr", "meta", "link", "area", "base", "col",
    "embed", "source", "track", "wbr"]);
const TAG_PATTERN = "<(\\/?)([a-zA-Z][a-zA-Z0-9-]*)((?:\\s+[a-zA-Z_:][-a-zA-Z0-9_:.]*(?:\\s*=\\s*"
    + "(?:\"[^\"]*\"|'[^']*'|[^\\s\"'>]+))?)*)\\s*(\\/?)>";
const ATTR_PATTERN = "([a-zA-Z_:][-a-zA-Z0-9_:.]*)(?:\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+)))?";

function decodeEntities(value) {
    return String(value)
        .replace(/&lt;/g, "<")
        .replace(/&gt;/g, ">")
        .replace(/&quot;/g, "\"")
        .replace(/&#0?39;/g, "'")
        .replace(/&amp;/g, "&");
}

class StubText {
    [INSPECT]() {
        return "#text(" + JSON.stringify(this.data.slice(0, 20)) + ")";
    }

    constructor(ownerDocument, data) {
        this.ownerDocument = ownerDocument;
        this.nodeType = 3;
        this.nodeName = "#text";
        this.parentNode = null;
        this.data = String(data);
    }

    get textContent() {
        return this.data;
    }

    set textContent(value) {
        this.data = String(value);
    }

    cloneNode() {
        return new StubText(this.ownerDocument, this.data);
    }

    remove() {
        if (this.parentNode) this.parentNode.removeChild(this);
    }
}

class StubFragment {
    [INSPECT]() {
        return "#document-fragment(" + this.childNodes.length + ")";
    }

    constructor(ownerDocument) {
        this.ownerDocument = ownerDocument;
        this.nodeType = 11;
        this.nodeName = "#document-fragment";
        this.parentNode = null;
        this.childNodes = [];
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

    appendChild(node) {
        detach(node);
        this.childNodes.push(node);
        node.parentNode = this;
        return node;
    }

    removeChild(node) {
        const index = this.childNodes.indexOf(node);
        if (index >= 0) this.childNodes.splice(index, 1);
        node.parentNode = null;
        return node;
    }
}

function detach(node) {
    if (node.parentNode && typeof node.parentNode.removeChild === "function") {
        node.parentNode.removeChild(node);
    }
}

class StubClassList {
    constructor(element) {
        this.element = element;
    }

    tokens() {
        return (this.element.getAttribute("class") || "").split(/\s+/).filter(Boolean);
    }

    contains(name) {
        return this.tokens().indexOf(name) !== -1;
    }

    add() {
        const tokens = this.tokens();
        Array.prototype.forEach.call(arguments, (name) => {
            if (tokens.indexOf(name) === -1) tokens.push(name);
        });
        this.write(tokens);
    }

    remove() {
        const names = Array.prototype.slice.call(arguments);
        this.write(this.tokens().filter((name) => names.indexOf(name) === -1));
    }

    toggle(name, force) {
        const on = force === undefined ? !this.contains(name) : !!force;
        if (on) this.add(name);
        else this.remove(name);
        return on;
    }

    write(tokens) {
        this.element.setAttribute("class", tokens.join(" "));
    }
}

class StubElement {
    [INSPECT]() {
        const id = this.getAttribute("id");
        return "<" + this.tagName.toLowerCase() + (id ? "#" + id : "") + ">";
    }

    constructor(ownerDocument, tagName) {
        this.ownerDocument = ownerDocument;
        this.nodeType = 1;
        this.tagName = String(tagName).toUpperCase();
        this.nodeName = this.tagName;
        this.parentNode = null;
        this.childNodes = [];
        this.attributes = new Map();
        this.listeners = new Map();
        this.classList = new StubClassList(this);
        this.style = {};
        this.computed = {};
        this.disabled = false;
        this.value = "";
        this.rectWidth = 0;
        this.clientWidthOverride = null;
        this.content = this.tagName === "TEMPLATE" ? new StubFragment(ownerDocument) : null;
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

    get id() {
        return this.getAttribute("id") || "";
    }

    get className() {
        return this.getAttribute("class") || "";
    }

    get hidden() {
        return this.hasAttribute("hidden");
    }

    set hidden(value) {
        if (value) this.setAttribute("hidden", "");
        else this.removeAttribute("hidden");
    }

    get clientWidth() {
        return this.clientWidthOverride === null ? this.rectWidth : this.clientWidthOverride;
    }

    get offsetWidth() {
        return this.rectWidth;
    }

    getBoundingClientRect() {
        return {
            x: 0, y: 0, width: this.rectWidth, height: 0,
            top: 0, left: 0, right: this.rectWidth, bottom: 0
        };
    }

    attributeNames() {
        return Array.from(this.attributes.keys());
    }

    getAttribute(name) {
        return this.attributes.has(name) ? this.attributes.get(name) : null;
    }

    hasAttribute(name) {
        return this.attributes.has(name);
    }

    setAttribute(name, value) {
        const text = String(value);
        if (name === "class") {
            this.attributes.set(name, text.trim().split(/\s+/).filter(Boolean).join(" "));
            return;
        }
        this.attributes.set(name, text);
    }

    removeAttribute(name) {
        this.attributes.delete(name);
    }

    appendChild(node) {
        detach(node);
        this.childNodes.push(node);
        node.parentNode = this;
        return node;
    }

    insertBefore(node, reference) {
        detach(node);
        const index = reference ? this.childNodes.indexOf(reference) : -1;
        if (index < 0) this.childNodes.push(node);
        else this.childNodes.splice(index, 0, node);
        node.parentNode = this;
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

    contains(node) {
        let current = node;
        while (current) {
            if (current === this) return true;
            current = current.parentNode;
        }
        return false;
    }

    cloneNode(deep) {
        const copy = new StubElement(this.ownerDocument, this.tagName);
        this.attributes.forEach((value, name) => copy.attributes.set(name, value));
        copy.style = Object.assign({}, this.style);
        copy.computed = Object.assign({}, this.computed);
        copy.rectWidth = this.rectWidth;
        copy.clientWidthOverride = this.clientWidthOverride;
        copy.disabled = this.disabled;
        copy.value = this.value;
        if (deep) this.childNodes.forEach((child) => copy.appendChild(child.cloneNode(true)));
        return copy;
    }

    get textContent() {
        return this.childNodes.map((child) => child.textContent).join("");
    }

    set textContent(value) {
        const target = this.content || this;
        const text = String(value);
        target.childNodes = [];
        if (text) target.appendChild(new StubText(this.ownerDocument, text));
    }

    get innerHTML() {
        const target = this.content || this;
        return target.childNodes.map((child) => child.textContent).join("");
    }

    set innerHTML(html) {
        const target = this.content || this;
        target.childNodes = [];
        this.ownerDocument.parseFragment(String(html))
            .forEach((node) => target.appendChild(node));
    }

    querySelector(selector) {
        return this.querySelectorAll(selector)[0] || null;
    }

    querySelectorAll(selector) {
        const tokens = parseSelector(selector);
        const found = [];
        collectMatches(this, tokens, found);
        return found;
    }

    addEventListener(type, handler) {
        const list = this.listeners.get(type) || [];
        list.push(handler);
        this.listeners.set(type, list);
    }

    removeEventListener(type, handler) {
        const list = this.listeners.get(type);
        if (!list) return;
        const index = list.indexOf(handler);
        if (index >= 0) list.splice(index, 1);
    }

    dispatchEvent(event) {
        if (!event.target) event.target = this;
        dispatchFrom(this, event);
        return event;
    }

    focus() {
        this.ownerDocument.activeElement = this;
    }

    blur() {
        if (this.ownerDocument.activeElement === this) this.ownerDocument.activeElement = null;
    }

    setWidth(width) {
        this.rectWidth = width;
        return this;
    }
}

function collectMatches(node, tokens, found) {
    node.childNodes.forEach((child) => {
        if (child.nodeType !== 1) return;
        if (nodeMatches(child, tokens)) found.push(child);
        collectMatches(child, tokens, found);
    });
    return found;
}

function parseSelector(selector) {
    return String(selector).trim().split(/\s+/).filter((part) => part && part !== ">").map(parseCompound);
}

function parseCompound(text) {
    const token = { tag: null, id: null, classes: [], attrs: [] };
    const parts = String(text).match(/\[[^\]]*\]|[#.]?[A-Za-z0-9_-]+/g) || [];
    parts.forEach((part) => {
        if (part[0] === "[") {
            const raw = part.slice(1, -1);
            const equal = raw.indexOf("=");
            if (equal === -1) {
                token.attrs.push({ name: raw.trim(), value: null });
            } else {
                token.attrs.push({
                    name: raw.slice(0, equal).trim(),
                    value: raw.slice(equal + 1).trim().replace(/^["']|["']$/g, "")
                });
            }
        } else if (part[0] === "#") {
            token.id = part.slice(1);
        } else if (part[0] === ".") {
            token.classes.push(part.slice(1));
        } else {
            token.tag = part.toLowerCase();
        }
    });
    return token;
}

function matchesCompound(element, token) {
    if (!element || element.nodeType !== 1) return false;
    if (token.tag && element.tagName.toLowerCase() !== token.tag) return false;
    if (token.id && element.getAttribute("id") !== token.id) return false;
    for (let i = 0; i < token.classes.length; i += 1) {
        if (!element.classList.contains(token.classes[i])) return false;
    }
    for (let i = 0; i < token.attrs.length; i += 1) {
        const attribute = token.attrs[i];
        const actual = element.getAttribute(attribute.name);
        if (attribute.value === null) {
            if (actual === null) return false;
        } else if (actual !== attribute.value) {
            return false;
        }
    }
    return true;
}

function nodeMatches(element, tokens) {
    if (!matchesCompound(element, tokens[tokens.length - 1])) return false;
    let ancestor = element.parentNode;
    for (let i = tokens.length - 2; i >= 0; i -= 1) {
        while (ancestor && !matchesCompound(ancestor, tokens[i])) ancestor = ancestor.parentNode;
        if (!ancestor) return false;
        ancestor = ancestor.parentNode;
    }
    return true;
}

function applyAttributes(element, text) {
    const pattern = new RegExp(ATTR_PATTERN, "g");
    let match;
    while ((match = pattern.exec(text)) !== null) {
        const value = match[2] !== undefined ? match[2]
            : (match[3] !== undefined ? match[3] : (match[4] !== undefined ? match[4] : ""));
        element.setAttribute(match[1], decodeEntities(value));
    }
}

function parseFragment(doc, html) {
    const text = String(html);
    const pattern = new RegExp(TAG_PATTERN, "g");
    const roots = [];
    const stack = [];
    let cursor = 0;
    let match;
    const push = (node) => {
        const parent = stack[stack.length - 1];
        if (parent) parent.appendChild(node);
        else roots.push(node);
    };
    while ((match = pattern.exec(text)) !== null) {
        if (match.index > cursor) push(new StubText(doc, decodeEntities(text.slice(cursor, match.index))));
        cursor = pattern.lastIndex;
        const tagName = match[2];
        const lower = tagName.toLowerCase();
        if (match[1] === "/") {
            for (let i = stack.length - 1; i >= 0; i -= 1) {
                if (stack[i].tagName.toLowerCase() === lower) {
                    stack.length = i;
                    break;
                }
            }
            continue;
        }
        const element = new StubElement(doc, tagName);
        applyAttributes(element, match[3] || "");
        push(element);
        if (match[4] !== "/" && !VOID_TAGS.has(lower)) stack.push(element);
    }
    if (cursor < text.length) push(new StubText(doc, decodeEntities(text.slice(cursor))));
    return roots;
}

class StubEvent {
    constructor(type, options) {
        this.type = type;
        this.target = null;
        this.currentTarget = null;
        this.bubbles = !(options && options.bubbles === false);
        this.key = options && options.key !== undefined ? options.key : null;
        this.defaultPrevented = false;
    }

    preventDefault() {
        this.defaultPrevented = true;
    }
}

function dispatchFrom(node, event) {
    let current = node;
    while (current) {
        event.currentTarget = current;
        const list = current.listeners && current.listeners.get(event.type);
        if (list && list.length) list.slice().forEach((handler) => handler.call(current, event));
        if (!event.bubbles) break;
        current = current.parentNode;
    }
}

function dispatch(target, event) {
    if (!event.target) event.target = target;
    dispatchFrom(target, event);
    return event;
}

function click(target) {
    return dispatch(target, new StubEvent("click"));
}

function typeInto(target, value) {
    target.value = value;
    return dispatch(target, new StubEvent("input"));
}

function createDocument() {
    const doc = {
        nodeType: 9,
        nodeName: "#document",
        readyState: "complete",
        hidden: false,
        activeElement: null,
        listeners: new Map(),
        createElement(tagName) {
            return new StubElement(doc, tagName);
        },
        createElementNS(namespace, tagName) {
            return new StubElement(doc, tagName);
        },
        querySelector(selector) {
            return doc.documentElement.querySelector(selector);
        },
        querySelectorAll(selector) {
            return doc.documentElement.querySelectorAll(selector);
        },
        parseFragment(html) {
            return parseFragment(doc, html);
        },
        addEventListener(type, handler) {
            const list = doc.listeners.get(type) || [];
            list.push(handler);
            doc.listeners.set(type, list);
        },
        removeEventListener(type, handler) {
            const list = doc.listeners.get(type);
            if (!list) return;
            const index = list.indexOf(handler);
            if (index >= 0) list.splice(index, 1);
        },
        dispatchEvent(event) {
            return dispatch(doc, event);
        }
    };
    doc[INSPECT] = () => "#document";
    doc.documentElement = new StubElement(doc, "body");
    doc.documentElement.parentNode = doc;
    doc.defaultView = { getComputedStyle: (node) => (node && node.computed) || {} };
    doc.fonts = { ready: Promise.resolve() };
    return doc;
}

const NAV_VIEWS = ["monitoring", "templates", "unsubscribes", "experts", "mailbox", "inbound",
    "training", "poll", "tasks"];

function createEnvironment() {
    const doc = createDocument();
    const shell = doc.createElement("div");
    shell.classList.add("app-shell");
    shell.style.display = "grid";
    doc.documentElement.appendChild(shell);
    const header = doc.createElement("header");
    header.classList.add("topnav");
    header.computed = { columnGap: "12px", paddingLeft: "20px", paddingRight: "20px" };
    header.clientWidthOverride = 1600;
    shell.appendChild(header);
    const brand = doc.createElement("div");
    brand.classList.add("brand");
    brand.setWidth(200);
    header.appendChild(brand);
    const nav = doc.createElement("nav");
    nav.classList.add("nav-tabs");
    nav.computed = { columnGap: "4px" };
    header.appendChild(nav);
    const tabs = NAV_VIEWS.map((view) => {
        const tab = doc.createElement("button");
        tab.classList.add("nav-tab");
        tab.setAttribute("data-view", view);
        tab.setWidth(60);
        nav.appendChild(tab);
        return tab;
    });
    const side = doc.createElement("div");
    side.classList.add("topnav-side");
    side.computed = { columnGap: "8px" };
    header.appendChild(side);
    const userInfo = doc.createElement("div");
    userInfo.classList.add("user-info");
    userInfo.setWidth(120);
    side.appendChild(userInfo);
    const userDisplay = doc.createElement("span");
    userDisplay.setAttribute("id", "currentUserDisplay");
    userDisplay.textContent = "admin";
    userInfo.appendChild(userDisplay);
    const pollButton = doc.createElement("button");
    pollButton.classList.add("nav-tab", "logout-btn");
    pollButton.setAttribute("id", "showPollLogBtn");
    pollButton.setWidth(80);
    side.appendChild(pollButton);
    const logout = doc.createElement("button");
    logout.classList.add("nav-tab", "logout-btn");
    logout.setAttribute("id", "logoutBtn");
    logout.setWidth(100);
    side.appendChild(logout);

    const observers = [];
    const intervals = [];
    const frames = [];
    const previous = {
        document: global.document,
        window: global.window,
        documentValue: Object.prototype.hasOwnProperty.call(global, "document"),
        windowValue: Object.prototype.hasOwnProperty.call(global, "window"),
        ResizeObserver: global.ResizeObserver,
        MutationObserver: global.MutationObserver,
        requestAnimationFrame: global.requestAnimationFrame,
        cancelAnimationFrame: global.cancelAnimationFrame,
        setInterval: global.setInterval,
        clearInterval: global.clearInterval,
        getComputedStyle: global.getComputedStyle,
        now: Date.now
    };
    let nowValue = previous.now();

    class FakeObserver {
        constructor(callback) {
            this.callback = callback;
            this.disconnected = false;
            this.target = null;
            this.options = null;
            observers.push(this);
        }

        observe(target, options) {
            this.target = target;
            this.options = options;
        }

        disconnect() {
            this.disconnected = true;
        }

        fire() {
            if (!this.disconnected) this.callback([], this);
        }
    }

    global.document = doc;
    global.window = global.window || {};
    global.ResizeObserver = FakeObserver;
    global.MutationObserver = FakeObserver;
    global.requestAnimationFrame = (callback) => {
        frames.push(callback);
        return frames.length;
    };
    global.cancelAnimationFrame = (id) => {
        frames[id - 1] = null;
    };
    global.setInterval = (callback, delay) => {
        intervals.push({ callback, delay, cleared: false });
        return intervals.length;
    };
    global.clearInterval = (id) => {
        const entry = intervals[id - 1];
        if (entry) entry.cleared = true;
    };
    global.getComputedStyle = doc.defaultView.getComputedStyle;
    Date.now = () => nowValue;

    return {
        doc, shell, header, nav, brand, side, userInfo, userDisplay, logout, pollButton, tabs,
        observers,
        setNow(value) {
            nowValue = value;
        },
        now() {
            return nowValue;
        },
        pendingIntervals() {
            return intervals.filter((entry) => !entry.cleared);
        },
        fireIntervals() {
            intervals.filter((entry) => !entry.cleared).forEach((entry) => entry.callback());
        },
        flushFrames() {
            const pending = frames.splice(0, frames.length);
            pending.forEach((callback) => {
                if (callback) callback();
            });
        },
        observerFor(target) {
            return observers.find((observer) => observer.target === target) || null;
        },
        fireObserverFor(target) {
            const observer = this.observerFor(target);
            if (observer) observer.fire();
            return !!observer;
        },
        restore() {
            Date.now = previous.now;
            global.document = previous.documentValue ? previous.document : undefined;
            global.window = previous.windowValue ? previous.window : undefined;
            global.ResizeObserver = previous.ResizeObserver;
            global.MutationObserver = previous.MutationObserver;
            global.requestAnimationFrame = previous.requestAnimationFrame;
            global.cancelAnimationFrame = previous.cancelAnimationFrame;
            global.setInterval = previous.setInterval;
            global.clearInterval = previous.clearInterval;
            global.getComputedStyle = previous.getComputedStyle;
        }
    };
}

function createTransport() {
    const calls = [];
    const api = (url, options) => {
        let resolve;
        let reject;
        const promise = new Promise((res, rej) => {
            resolve = res;
            reject = rej;
        });
        calls.push({ url, options, resolve, reject });
        return promise;
    };
    return {
        api,
        calls,
        resolve(index, data) {
            calls[index].resolve(data);
        },
        reject(index, error) {
            calls[index].reject(error || new Error("catalog failed"));
        }
    };
}

const CATALOG = [
    { id: "Europe/London", labelZh: "伦敦", aliases: ["London", "United Kingdom"], offsetLabel: "UTC+00:00", offsetSeconds: 0 },
    { id: "Europe/Berlin", labelZh: "柏林", aliases: ["Berlin", "Germany"], offsetLabel: "UTC+00:00", offsetSeconds: 0 },
    { id: "America/New_York", labelZh: "纽约", aliases: ["New York", "United States"], offsetLabel: "UTC+00:00", offsetSeconds: 0 },
    { id: "America/Los_Angeles", labelZh: "洛杉矶", aliases: ["Los Angeles"], offsetLabel: "UTC+00:00", offsetSeconds: 0 },
    { id: "Asia/Tokyo", labelZh: "东京", aliases: ["Tokyo", "Japan"], offsetLabel: "UTC+00:00", offsetSeconds: 0 },
    { id: "Asia/Seoul", labelZh: "首尔", aliases: ["Seoul", "Korea"], offsetLabel: "UTC+00:00", offsetSeconds: 0 },
    { id: "Asia/Kolkata", labelZh: "加尔各答", aliases: ["Kolkata", "India"], offsetLabel: "UTC+00:00", offsetSeconds: 0 },
    { id: "Asia/Kathmandu", labelZh: "加德满都", aliases: ["Kathmandu", "Nepal"], offsetLabel: "UTC+00:00", offsetSeconds: 0 },
    { id: "Europe/Paris", labelZh: "巴黎", aliases: ["Paris", "France"], offsetLabel: "UTC+00:00", offsetSeconds: 0 },
    { id: "Europe/Madrid", labelZh: "马德里", aliases: ["Madrid", "Spain"], offsetLabel: "UTC+00:00", offsetSeconds: 0 },
    { id: "Pacific/Auckland", labelZh: "奥克兰", aliases: ["Auckland", "New Zealand", "新西兰"], offsetLabel: "UTC+00:00", offsetSeconds: 0 },
    { id: "Africa/Cairo", labelZh: "开罗", aliases: ["Cairo", "Egypt"], offsetLabel: "UTC+00:00", offsetSeconds: 0 },
    { id: "UTC", labelZh: "协调世界时", aliases: ["UTC"], offsetLabel: "UTC+00:00", offsetSeconds: 0 }
];

let environment = null;
let mounted = null;

function settle() {
    return new Promise((resolve) => setImmediate(resolve));
}

function mountComponent(options) {
    const config = options || {};
    const transport = config.transport || createTransport();
    const handle = WorldClock.mount({
        header: config.header || environment.header,
        api: config.api || transport.api,
        filterZones: config.filterZones === undefined ? Meeting.filterZones : config.filterZones
    });
    if (handle) mounted = handle;
    return { handle, transport };
}

function triggerOf() {
    return environment.header.querySelector("#worldClockTrigger");
}

function panelOf() {
    return environment.header.querySelector("#worldClockPanel");
}

function rowsOf() {
    return panelOf().querySelector("#worldClockRows").children;
}

function elementOf(id) {
    return environment.header.querySelector("#" + id);
}

function fixedNow(date, time, extraSeconds) {
    const parsed = WorldClock.parseBeijingInput(date, time);
    assert.strictEqual(parsed.valid, true, "测试基准时间必须合法");
    return parsed.epoch + (extraSeconds || 0) * 1000;
}

function summarizeRows() {
    return rowsOf().map((row) => ({
        name: row.querySelector(".world-clock-name").textContent,
        zone: row.querySelector(".world-clock-zone").textContent,
        time: row.querySelector('[data-field="time"]').textContent,
        date: row.querySelector(".world-clock-date").textContent,
        day: row.querySelector(".world-clock-day").textContent,
        dayHidden: row.querySelector(".world-clock-day").hidden,
        difference: row.querySelector('[data-field="difference"]').textContent,
        offset: row.querySelector('[data-field="offset"]').textContent
    }));
}

function namesOf() {
    return rowsOf().map((row) => row.querySelector(".world-clock-name").textContent);
}

const VOLATILE_ATTRIBUTES = new Set(["hidden", "aria-expanded", "aria-pressed", "aria-invalid",
    "disabled", "datetime"]);

function compareStructure(actual, expected, trail, options) {
    assert.strictEqual(actual.tagName, expected.tagName, trail + " 标签不一致");
    assert.strictEqual(actual.getAttribute("id"), expected.getAttribute("id"), trail + " id 不一致");
    assert.strictEqual(actual.className, expected.className, trail + " class 不一致");
    assert.strictEqual(actual.hasAttribute("style"), false, trail + " 出现 inline style");
    assert.strictEqual(Object.keys(actual.style).length, 0, trail + " 写入 element.style");
    const stable = (element) => element.attributeNames()
        .filter((name) => name !== "class" && name !== "id" && !VOLATILE_ATTRIBUTES.has(name))
        .sort();
    const actualAttributes = stable(actual);
    const expectedAttributes = stable(expected);
    assert.deepStrictEqual(actualAttributes, expectedAttributes, trail + " 属性集合不一致");
    actualAttributes.forEach((name) => {
        assert.strictEqual(actual.getAttribute(name), expected.getAttribute(name),
            trail + " 属性 " + name + " 不一致");
    });
    assert.strictEqual(actual.children.length, expected.children.length,
        trail + " 子元素数量不一致");
    actual.children.forEach((child, index) => {
        compareStructure(child, expected.children[index],
            trail + " > " + child.tagName.toLowerCase() + "[" + index + "]", options);
    });
    if (!actual.children.length && !(options && options.dynamicText)) {
        assert.strictEqual(actual.textContent, expected.textContent, trail + " 文本不一致");
    }
}

/** 顶层逗号切分选择器列表（:is()/:not() 内的逗号不算分隔符）。 */
function splitSelectors(headerText) {
    const parts = [];
    let depth = 0;
    let current = "";
    for (const character of headerText) {
        if (character === "(") depth += 1;
        else if (character === ")") depth = Math.max(0, depth - 1);
        if (character === "," && depth === 0) {
            parts.push(current.trim());
            current = "";
            continue;
        }
        current += character;
    }
    parts.push(current.trim());
    return parts.filter(Boolean);
}

function parsePlanTemplate(name, language) {
    const doc = createDocument();
    return doc.parseFragment(planBlock(name, language)).filter((node) => node.nodeType === 1)[0];
}

beforeEach(() => {
    environment = createEnvironment();
});

afterEach(() => {
    if (mounted && typeof mounted.destroy === "function") mounted.destroy();
    mounted = null;
    if (environment) {
        environment.restore();
        environment = null;
    }
});

// ---------------------------------------------------------------------------
// T1：纯换算
// ---------------------------------------------------------------------------

describe("T1 纯换算：parseBeijingInput / projectZones（I-4）", () => {
    const CATALOG_IDS = ["Europe/London", "Europe/Berlin", "America/New_York",
        "America/Los_Angeles", "Asia/Tokyo", "Asia/Seoul", "Asia/Kolkata", "Asia/Kathmandu"];
    const PROJECTION_CATALOG = CATALOG_IDS.map((id) => ({
        id,
        labelZh: id,
        aliases: [id],
        // 目录 offset 故意给错值，投影必须完全无视它。
        offsetLabel: "UTC+09:99",
        offsetSeconds: 999999
    }));

    // 计划「确定性换算样例」全部精确值；不得由被测函数生成。
    const CONVERSIONS = [
        { date: "2026-09-17", time: "15:00", zone: "Europe/London", localDate: "2026-09-17", local: "08:00", offset: "UTC+01:00", offsetSeconds: 3600, difference: "慢 7 小时", day: "" },
        { date: "2026-09-17", time: "15:00", zone: "Europe/Berlin", localDate: "2026-09-17", local: "09:00", offset: "UTC+02:00", offsetSeconds: 7200, difference: "慢 6 小时", day: "" },
        { date: "2026-09-17", time: "15:00", zone: "America/New_York", localDate: "2026-09-17", local: "03:00", offset: "UTC-04:00", offsetSeconds: -14400, difference: "慢 12 小时", day: "" },
        { date: "2026-09-17", time: "15:00", zone: "America/Los_Angeles", localDate: "2026-09-17", local: "00:00", offset: "UTC-07:00", offsetSeconds: -25200, difference: "慢 15 小时", day: "" },
        { date: "2026-09-17", time: "15:00", zone: "Asia/Tokyo", localDate: "2026-09-17", local: "16:00", offset: "UTC+09:00", offsetSeconds: 32400, difference: "快 1 小时", day: "" },
        { date: "2026-09-17", time: "15:00", zone: "Asia/Seoul", localDate: "2026-09-17", local: "16:00", offset: "UTC+09:00", offsetSeconds: 32400, difference: "快 1 小时", day: "" },
        { date: "2026-01-17", time: "15:00", zone: "America/Los_Angeles", localDate: "2026-01-16", local: "23:00", offset: "UTC-08:00", offsetSeconds: -28800, difference: "慢 16 小时", day: "前一天" },
        { date: "2026-09-17", time: "23:30", zone: "Asia/Tokyo", localDate: "2026-09-18", local: "00:30", offset: "UTC+09:00", offsetSeconds: 32400, difference: "快 1 小时", day: "后一天" },
        { date: "2026-09-17", time: "15:00", zone: "Asia/Kolkata", localDate: "2026-09-17", local: "12:30", offset: "UTC+05:30", offsetSeconds: 19800, difference: "慢 2 小时 30 分", day: "" },
        { date: "2026-09-17", time: "15:00", zone: "Asia/Kathmandu", localDate: "2026-09-17", local: "12:45", offset: "UTC+05:45", offsetSeconds: 20700, difference: "慢 2 小时 15 分", day: "" },
        { date: "2026-03-08", time: "14:30", zone: "America/New_York", localDate: "2026-03-08", local: "01:30", offset: "UTC-05:00", offsetSeconds: -18000, difference: "慢 13 小时", day: "" },
        { date: "2026-03-08", time: "15:30", zone: "America/New_York", localDate: "2026-03-08", local: "03:30", offset: "UTC-04:00", offsetSeconds: -14400, difference: "慢 12 小时", day: "" },
        { date: "2026-11-01", time: "13:30", zone: "America/New_York", localDate: "2026-11-01", local: "01:30", offset: "UTC-04:00", offsetSeconds: -14400, difference: "慢 12 小时", day: "" },
        { date: "2026-11-01", time: "14:30", zone: "America/New_York", localDate: "2026-11-01", local: "01:30", offset: "UTC-05:00", offsetSeconds: -18000, difference: "慢 13 小时", day: "" }
    ];

    it("按计划换算表逐格硬编码断言（含 DST 跳时/回拨/跨日/45 分钟区）", () => {
        CONVERSIONS.forEach((row) => {
            const parsed = WorldClock.parseBeijingInput(row.date, row.time);
            assert.strictEqual(parsed.valid, true, row.date + " " + row.time + " 应合法");
            const projected = WorldClock.projectZones(PROJECTION_CATALOG, parsed.epoch);
            const zone = projected.zones.find((item) => item.id === row.zone);
            assert.ok(zone, row.zone + " 未被投影");
            const trail = row.date + " " + row.time + " → " + row.zone;
            assert.strictEqual(zone.localDate, row.localDate, trail + " 当地日期");
            assert.strictEqual(zone.localTime, row.local, trail + " 当地时间");
            assert.strictEqual(zone.offsetLabel, row.offset, trail + " UTC 偏移");
            assert.strictEqual(zone.offsetSeconds, row.offsetSeconds, trail + " 偏移秒数");
            assert.strictEqual(zone.differenceSeconds, row.offsetSeconds - 28800, trail + " 时差秒数");
            assert.strictEqual(zone.differenceLabel, row.difference, trail + " 时差文案");
            assert.strictEqual(zone.dayLabel, row.day, trail + " 跨日徽标");
        });
    });

    it("同一 epoch 换算：epoch 与北京日历严格互逆", () => {
        const parsed = WorldClock.parseBeijingInput("2026-09-17", "15:00");
        assert.strictEqual(parsed.epoch, Date.UTC(2026, 8, 17, 7, 0));
        assert.strictEqual(parsed.date, "2026-09-17");
        assert.strictEqual(parsed.time, "15:00");
        const sameEpoch = WorldClock.projectZones(PROJECTION_CATALOG, parsed.epoch);
        const tokyo = sameEpoch.zones.find((item) => item.id === "Asia/Tokyo");
        const london = sameEpoch.zones.find((item) => item.id === "Europe/London");
        assert.strictEqual(tokyo.localTime, "16:00");
        assert.strictEqual(london.localTime, "08:00");
    });

    it("拒绝非法输入：日/时/年越界、格式错误、空串一律 invalid", () => {
        const invalid = [
            ["2026-02-30", "12:00"],
            ["2026-02-29", "12:00"],
            ["2026-09-17", "24:00"],
            ["2026-09-17", "23:60"],
            ["1999-12-31", "23:59"],
            ["2101-01-01", "00:00"],
            ["", ""],
            ["2026-9-17", "15:00"],
            ["2026-09-17", "15:00:00"],
            ["2026-13-01", "00:00"],
            [null, null]
        ];
        invalid.forEach(([date, time]) => {
            const parsed = WorldClock.parseBeijingInput(date, time);
            assert.strictEqual(parsed.valid, false, JSON.stringify([date, time]) + " 必须 invalid");
            assert.strictEqual(parsed.epoch, null);
        });
        [
            ["2000-01-01", "00:00"],
            ["2100-12-31", "23:59"],
            ["2024-02-29", "12:00"]
        ].forEach(([date, time]) => {
            assert.strictEqual(WorldClock.parseBeijingInput(date, time).valid, true,
                date + " " + time + " 必须合法");
        });
    });

    it("目录条目不被修改：拒绝不合契约条目、统计 Intl 不支持的 id", () => {
        const raw = [
            { id: "Europe/London", labelZh: "伦敦", aliases: ["London"], offsetLabel: "UTC+09:99", offsetSeconds: 999999 },
            { id: "Custom/Nowhere", labelZh: "未知", aliases: [], offsetLabel: "UTC+00:00", offsetSeconds: 0 },
            null,
            { id: "", labelZh: "空 id", aliases: [] },
            { id: "Europe/Paris", labelZh: 5, aliases: [] },
            { id: "Europe/Madrid", labelZh: "马德里", aliases: "Madrid" }
        ];
        const snapshot = JSON.stringify(raw);
        const projected = WorldClock.projectZones(raw, fixedNow("2026-09-17", "15:00"));
        assert.strictEqual(JSON.stringify(raw), snapshot, "投影不得修改入参");
        assert.deepStrictEqual(projected.zones.map((zone) => zone.id), ["Europe/London"]);
        assert.deepStrictEqual(projected.unsupported, ["Custom/Nowhere"]);
    });

    it("非数组/非法 epoch 输入返回空投影", () => {
        assert.deepStrictEqual(WorldClock.projectZones(null, 0), { zones: [], unsupported: [] });
        assert.deepStrictEqual(WorldClock.projectZones(CATALOG, NaN), { zones: [], unsupported: [] });
    });
});

// ---------------------------------------------------------------------------
// T4：CSS 与 DOM 契约
// ---------------------------------------------------------------------------

describe("T4 契约：world-clock.css 与模板（S-4/I-9）", () => {
    it("CSS 与计划 WORLD_CLOCK_CSS 标记块逐字一致", () => {
        assert.strictEqual(cssSource, planBlock("CSS", "css"));
        assert.ok(cssSource.endsWith("}\n"), "CSS 末尾必须只有一个换行");
        assert.strictEqual(cssSource.indexOf("\r"), -1, "CSS 不得含 CR");
    });

    it("CSS 全部规则限定在组件作用域，且含 1100px 单行覆盖与暗色/窄屏/粗指针分支", () => {
        const headers = [];
        const pattern = /(?:^|}|\*\/)\s*([^{}]+?)\{/g;
        let match;
        while ((match = pattern.exec(cssSource)) !== null) headers.push(match[1].trim());
        assert.ok(headers.length > 20, "CSS 规则数量异常");
        headers.forEach((headerText) => {
            if (headerText.startsWith("@media")) return;
            splitSelectors(headerText).forEach((selector) => {
                assert.ok(selector.indexOf("world-clock") !== -1,
                    "出现组件作用域外的选择器：" + selector);
            });
        });
        const topnavBlock = /\.topnav\.world-clock-header \{([^}]*)\}/.exec(cssSource);
        assert.ok(topnavBlock, "缺少 .topnav.world-clock-header 派生规则");
        assert.ok(/flex-wrap:\s*nowrap/.test(topnavBlock[1]), "1100px 换行必须被 nowrap 覆盖");
        assert.ok(/@media \(max-width: 1100px\)/.test(cssSource) === false,
            "组件自身不重复声明 1100px 媒体查询，靠派生规则覆盖");
        ["@media (max-width: 640px)", "@media (max-width: 440px)", "@media (pointer: coarse)",
            "@media (prefers-color-scheme: dark)", "@media (prefers-reduced-motion: reduce)",
            ".world-clock-trigger.world-clock-icon-only", ":focus-visible", ":disabled"]
            .forEach((fragment) => assert.ok(cssSource.indexOf(fragment) !== -1, "CSS 缺少 " + fragment));
    });

    it("模块模板常量与计划三份标记块逐字一致（仅末尾换行差异）", () => {
        assert.strictEqual(WorldClock.templates.trigger, planBlock("TRIGGER", "html").replace(/\n$/, ""));
        assert.strictEqual(WorldClock.templates.panel, planBlock("PANEL", "html").replace(/\n$/, ""));
        assert.strictEqual(WorldClock.templates.row, planBlock("ROW", "html").replace(/\n$/, ""));
    });

    it("初始隐藏态挂载：trigger 与 panel 与计划模板结构一致（无缺节点/inline style/额外 class）", () => {
        environment.shell.style.display = "none";
        const { handle } = mountComponent();
        assert.ok(handle);
        compareStructure(triggerOf(), parsePlanTemplate("TRIGGER", "html"), "#worldClockTrigger");
        compareStructure(panelOf(), parsePlanTemplate("PANEL", "html"), "#worldClockPanel");
        assert.strictEqual(triggerOf().getAttribute("aria-expanded"), "false");
        assert.strictEqual(elementOf("worldClockHeaderTime").textContent, "");
        assert.strictEqual(elementOf("worldClockNow").textContent, "");
    });

    it("结果行与计划 ROW 模板结构一致，动态内容只走 textContent", async () => {
        environment.setNow(fixedNow("2026-09-17", "15:00", 30));
        const { handle, transport } = mountComponent();
        handle.open();
        transport.resolve(0, CATALOG);
        await settle();
        const row = rowsOf()[0];
        compareStructure(row, parsePlanTemplate("ROW", "html"), "tbody > tr", { dynamicText: true });
        assert.deepStrictEqual(summarizeRows()[0], {
            name: "伦敦",
            zone: "Europe/London",
            time: "08:00",
            date: "2026-09-17",
            day: "",
            dayHidden: true,
            difference: "慢 7 小时",
            offset: "UTC+01:00"
        });
        const dayBadge = row.querySelector(".world-clock-day");
        assert.strictEqual(dayBadge.hidden, true, "无跨日时必须 hidden");
        const crossDay = summarizeRows().find((item) => item.zone === "America/Los_Angeles");
        assert.strictEqual(crossDay.dayHidden, true, "同日不得显示跨日徽标");
    });
});

// ---------------------------------------------------------------------------
// T3：宿主集成、单行布局、单例
// ---------------------------------------------------------------------------

describe("T3 宿主集成与单行布局（I-1/I-2）", () => {
    it("trigger 插在 #logoutBtn 之前、panel 是 .topnav 直接子节点、导航节点不变", async () => {
        const { handle } = mountComponent();
        assert.ok(handle);
        const trigger = triggerOf();
        const panel = panelOf();
        assert.strictEqual(environment.header.querySelectorAll("#worldClockTrigger").length, 1);
        assert.strictEqual(environment.header.querySelectorAll("#worldClockPanel").length, 1);
        assert.strictEqual(trigger.parentNode, environment.side);
        assert.strictEqual(environment.side.children.indexOf(trigger),
            environment.side.children.indexOf(environment.logout) - 1);
        assert.strictEqual(panel.parentNode, environment.header);
        assert.strictEqual(trigger.classList.contains("nav-tab"), false);
        assert.strictEqual(trigger.hasAttribute("data-view"), false);
        assert.strictEqual(environment.header.classList.contains("world-clock-header"), true);
        assert.deepStrictEqual(environment.nav.children.slice(), environment.tabs);
        assert.strictEqual(environment.doc.querySelectorAll(".nav-tab").length, 11,
            "九个 view 按钮 + 轮询日志 + 退出登录，组件不得新增 nav-tab");
        await settle();
        environment.flushFrames();
        assert.strictEqual(environment.pendingIntervals().length, 1, "可见 shell 只有一个计时器");
    });

    it("初始 hidden shell 不启动计时器也不请求目录", async () => {
        environment.shell.style.display = "none";
        const { transport } = mountComponent();
        await settle();
        environment.flushFrames();
        assert.strictEqual(environment.pendingIntervals().length, 0);
        assert.strictEqual(transport.calls.length, 0);
        assert.strictEqual(triggerOf().classList.contains("world-clock-icon-only"), false);
    });

    it("重复 mount 只保留一个实例与一份 DOM；destroy 后可重新挂载", async () => {
        const first = mountComponent();
        const trigger = triggerOf();
        const second = mountComponent();
        assert.strictEqual(second.handle, first.handle, "重复 mount 返回同一实例");
        assert.strictEqual(second.transport.calls.length, 0);
        assert.strictEqual(environment.header.querySelectorAll("#worldClockTrigger").length, 1);
        assert.strictEqual(environment.header.querySelectorAll("#worldClockPanel").length, 1);
        assert.strictEqual(triggerOf() === trigger, true, "不得重建 trigger");
        first.handle.destroy();
        mounted = null;
        assert.strictEqual(triggerOf(), null);
        const third = mountComponent();
        assert.ok(third.handle);
        assert.notStrictEqual(third.handle, first.handle);
        assert.strictEqual(environment.header.querySelectorAll("#worldClockTrigger").length, 1);
        assert.strictEqual(environment.header.classList.contains("world-clock-header"), true);
    });

    it("缺少宿主依赖（api/filterZones/side/logout）时不挂载且不加 header class", () => {
        const noApi = WorldClock.mount({
            header: environment.header, api: null, filterZones: Meeting.filterZones
        });
        assert.strictEqual(noApi, null);
        assert.strictEqual(WorldClock.mount({
            header: environment.header, api: () => {}, filterZones: null
        }), null);
        const bare = environment.doc.createElement("header");
        bare.classList.add("topnav");
        assert.strictEqual(WorldClock.mount({
            header: bare, api: () => {}, filterZones: Meeting.filterZones
        }), null);
        assert.strictEqual(WorldClock.mount({
            header: null, api: () => {}, filterZones: Meeting.filterZones
        }), null);
        assert.strictEqual(environment.header.classList.contains("world-clock-header"), false);
        assert.strictEqual(environment.header.querySelector("#worldClockTrigger"), null);
    });

    it("原导航点击只走宿主监听，组件不绑定原导航也不产生请求/计时器（I-1）", async () => {
        const { transport } = mountComponent();
        const hostClicks = [];
        environment.tabs.forEach((tab) => {
            tab.addEventListener("click", () => hostClicks.push(tab.getAttribute("data-view")));
        });
        environment.tabs.forEach((tab) => click(tab));
        assert.deepStrictEqual(hostClicks, NAV_VIEWS);
        environment.tabs.forEach((tab) => {
            assert.strictEqual(tab.listeners.get("click").length, 1, "组件不得在原导航上挂监听");
        });
        assert.strictEqual(transport.calls.length, 0);
        assert.strictEqual(environment.pendingIntervals().length, 1);
        assert.strictEqual(triggerOf().getAttribute("aria-expanded"), "false");
    });

    it("fitHeader：按所需宽度降级图标态，同宽重算不抖动，且不依赖已压缩的 nav.clientWidth", async () => {
        const { handle } = mountComponent();
        assert.ok(handle);
        const trigger = triggerOf();
        trigger.setWidth(230);
        // 40(padding) + 200(brand) + 12(gap) + (9*60 + 8*4)(nav) + 12(gap) +
        // (120 + 80 + 100 + 230 + 3*8)(side)
        const required = 1390;
        const navLayoutWidth = environment.nav.clientWidth;
        environment.nav.clientWidthOverride = 40;

        const fit = async () => {
            environment.fireObserverFor(environment.header);
            await settle();
            environment.flushFrames();
        };

        environment.header.clientWidthOverride = required - 1;
        await fit();
        assert.strictEqual(trigger.classList.contains("world-clock-icon-only"), true,
            "放不下时降级为图标态");
        assert.strictEqual(trigger.getAttribute("aria-label"), "查看当前北京时间与全球时区",
            "图标态必须保留完整 accessible name");

        environment.header.clientWidthOverride = required + 1;
        await fit();
        assert.strictEqual(trigger.classList.contains("world-clock-icon-only"), false);

        environment.header.clientWidthOverride = required;
        await fit();
        assert.strictEqual(trigger.classList.contains("world-clock-icon-only"), false,
            "容差 1px：恰好相等保持完整态");

        environment.header.clientWidthOverride = navLayoutWidth;
        environment.header.clientWidthOverride = required - 1;
        for (let i = 0; i < 3; i += 1) {
            await fit();
            assert.strictEqual(trigger.classList.contains("world-clock-icon-only"), true,
                "同一宽度重复测量必须稳定");
        }
        environment.header.clientWidthOverride = required;
        await fit();
        assert.strictEqual(trigger.classList.contains("world-clock-icon-only"), false,
            "可用宽度恢复后回到完整态，且不受 nav.clientWidth=40 影响");
    });
});

// ---------------------------------------------------------------------------
// T2：目录、筛选、分页、渲染
// ---------------------------------------------------------------------------

describe("T2 目录加载、筛选与分页（I-3/I-5）", () => {
    it("打开时单次 GET 目录，加载中/成功后重复打开不再请求（复用元信息）", async () => {
        environment.setNow(fixedNow("2026-09-17", "15:00", 30));
        const { handle, transport } = mountComponent();
        handle.open();
        assert.strictEqual(transport.calls.length, 1);
        assert.ok(transport.calls[0].url.startsWith("/api/mail/meeting-confirmation/time-zones?date="));
        assert.strictEqual(transport.calls[0].url.endsWith("2026-09-17"), true,
            "目录 date 参数为发起时的北京日期");
        assert.ok(transport.calls[0].options && transport.calls[0].options.signal);
        assert.strictEqual(transport.calls[0].options.signal.aborted, false);
        assert.strictEqual(elementOf("worldClockLoadStatus").textContent, "正在加载时区…");
        assert.strictEqual(panelOf().hidden, false);
        handle.close();
        handle.open();
        handle.open();
        assert.strictEqual(transport.calls.length, 1, "加载中重复打开不得重复请求");
        transport.resolve(0, CATALOG);
        await settle();
        assert.strictEqual(elementOf("worldClockLoadStatus").hidden, true);
        assert.strictEqual(rowsOf().length, 6);
        handle.close();
        handle.open();
        assert.strictEqual(transport.calls.length, 1, "成功后目录元信息复用");
        assert.strictEqual(rowsOf().length, 6);
    });

    it("常用六区按计划顺序渲染，目录缺失的条目按真实响应显示", async () => {
        environment.setNow(fixedNow("2026-09-17", "15:00", 30));
        const { handle, transport } = mountComponent();
        handle.open();
        transport.resolve(0, CATALOG);
        await settle();
        assert.deepStrictEqual(namesOf(), ["伦敦", "柏林", "纽约", "洛杉矶", "东京", "首尔"]);
        assert.deepStrictEqual(summarizeRows().map((row) => row.time),
            ["08:00", "09:00", "03:00", "00:00", "16:00", "16:00"]);
        assert.strictEqual(elementOf("worldClockCount").textContent, "共 6 个时区");
        assert.strictEqual(elementOf("worldClockPager").hidden, true, "常用六条不显示分页");
        handle.destroy();
        mounted = null;
        const partial = createTransport();
        const second = mountComponent({ transport: partial });
        second.handle.open();
        partial.resolve(0, CATALOG.filter((zone) => zone.id !== "Asia/Seoul" && zone.id !== "Asia/Tokyo"));
        await settle();
        assert.deepStrictEqual(namesOf(), ["伦敦", "柏林", "纽约", "洛杉矶"]);
    });

    it("全球范围按目录顺序分页：每页 6 行、页码与边界按钮", async () => {
        environment.setNow(fixedNow("2026-09-17", "15:00", 30));
        const { handle, transport } = mountComponent();
        handle.open();
        transport.resolve(0, CATALOG);
        await settle();
        click(elementOf("worldClockGlobal"));
        assert.strictEqual(elementOf("worldClockCommon").getAttribute("aria-pressed"), "false");
        assert.strictEqual(elementOf("worldClockGlobal").getAttribute("aria-pressed"), "true");
        assert.deepStrictEqual(namesOf(), CATALOG.slice(0, 6).map((zone) => zone.labelZh));
        assert.strictEqual(elementOf("worldClockPageLabel").textContent, "第 1 / 3 页");
        assert.strictEqual(elementOf("worldClockPrev").disabled, true);
        assert.strictEqual(elementOf("worldClockNext").disabled, false);
        click(elementOf("worldClockNext"));
        assert.deepStrictEqual(namesOf(), CATALOG.slice(6, 12).map((zone) => zone.labelZh));
        assert.strictEqual(elementOf("worldClockPageLabel").textContent, "第 2 / 3 页");
        assert.strictEqual(elementOf("worldClockPrev").disabled, false);
        click(elementOf("worldClockNext"));
        assert.deepStrictEqual(namesOf(), ["协调世界时"]);
        assert.strictEqual(elementOf("worldClockNext").disabled, true);
        click(elementOf("worldClockNext"));
        assert.strictEqual(elementOf("worldClockPageLabel").textContent, "第 3 / 3 页",
            "页码必须在边界 clamp");
        assert.strictEqual(elementOf("worldClockPrev").disabled, false);
    });

    it("搜索自动切全球、命中中文/别名/IANA，清空搜索保留 scope（I-5）", async () => {
        environment.setNow(fixedNow("2026-09-17", "15:00", 30));
        const { handle, transport } = mountComponent();
        handle.open();
        transport.resolve(0, CATALOG);
        await settle();
        const search = elementOf("worldClockSearch");
        search.disabled = false;
        typeInto(search, "柏林");
        assert.strictEqual(elementOf("worldClockGlobal").getAttribute("aria-pressed"), "true");
        assert.deepStrictEqual(namesOf(), ["柏林"]);
        assert.strictEqual(elementOf("worldClockPageLabel").textContent, "第 1 / 1 页");
        typeInto(search, "新西兰");
        assert.deepStrictEqual(namesOf(), ["奥克兰"], "别名文本经宿主 filterZones 命中");
        typeInto(search, "europe/paris");
        assert.deepStrictEqual(namesOf(), ["巴黎"], "IANA id 命中");
        typeInto(search, "");
        assert.strictEqual(elementOf("worldClockGlobal").getAttribute("aria-pressed"), "true",
            "清空搜索保留 global");
        assert.strictEqual(rowsOf().length, 6);
        assert.strictEqual(elementOf("worldClockPageLabel").textContent, "第 1 / 3 页");
    });

    it("UTC 偏移串按投影 offsetSeconds 精确匹配：+5:30 不含 +5:45", async () => {
        environment.setNow(fixedNow("2026-09-17", "15:00", 30));
        const { handle, transport } = mountComponent();
        handle.open();
        transport.resolve(0, CATALOG);
        await settle();
        const search = elementOf("worldClockSearch");
        typeInto(search, "UTC+5:30");
        assert.deepStrictEqual(namesOf(), ["加尔各答"]);
        assert.strictEqual(summarizeRows()[0].offset, "UTC+05:30");
        typeInto(search, "UTC+05:30");
        assert.deepStrictEqual(namesOf(), ["加尔各答"]);
        typeInto(search, "UTC＋05:30");
        assert.deepStrictEqual(namesOf(), ["加尔各答"], "全角加号必须归一");
        typeInto(search, "UTC+5:45");
        assert.deepStrictEqual(namesOf(), ["加德满都"]);
        typeInto(search, "");
        assert.strictEqual(rowsOf().length, 6);
    });

    it("区域分组与搜索是交集，切 common/global 清空搜索与区域并回第 1 页", async () => {
        environment.setNow(fixedNow("2026-09-17", "15:00", 30));
        const { handle, transport } = mountComponent();
        handle.open();
        transport.resolve(0, CATALOG);
        await settle();
        const region = elementOf("worldClockRegion");
        const search = elementOf("worldClockSearch");
        region.value = "Europe";
        dispatch(region, new StubEvent("change"));
        assert.strictEqual(elementOf("worldClockGlobal").getAttribute("aria-pressed"), "true");
        assert.deepStrictEqual(namesOf(), ["伦敦", "柏林", "巴黎", "马德里"]);
        typeInto(search, "马德里");
        assert.deepStrictEqual(namesOf(), ["马德里"], "分组与搜索取交集");
        click(elementOf("worldClockNext"));
        typeInto(search, "UTC+5:30");
        assert.deepStrictEqual(namesOf(), [], "欧洲分组下没有 +05:30");
        assert.strictEqual(elementOf("worldClockEmpty").hidden, false);
        assert.strictEqual(elementOf("worldClockPager").hidden, true);
        assert.strictEqual(elementOf("worldClockCount").textContent, "共 0 个时区");
        click(elementOf("worldClockCommon"));
        assert.strictEqual(search.value, "");
        assert.strictEqual(region.value, "");
        assert.strictEqual(elementOf("worldClockCommon").getAttribute("aria-pressed"), "true");
        assert.deepStrictEqual(namesOf(), ["伦敦", "柏林", "纽约", "洛杉矶", "东京", "首尔"]);
        region.value = "Other";
        dispatch(region, new StubEvent("change"));
        assert.deepStrictEqual(namesOf(), ["协调世界时"], "非标准前缀归入其他/别名");
    });

    it("UTC 偏移串由组件自身精确筛选，不依赖宿主 filterZones 的数值分支（I-5/R-3）", async () => {
        environment.setNow(fixedNow("2026-09-17", "15:00", 30));
        const substringOnly = (zones, query) => {
            const text = String(query == null ? "" : query).trim().toLowerCase();
            if (!text) return zones.slice();
            return zones.filter((zone) => zone.id.toLowerCase().indexOf(text) !== -1
                || zone.labelZh.toLowerCase().indexOf(text) !== -1
                || zone.offsetLabel.toLowerCase().indexOf(text) !== -1);
        };
        const { handle, transport } = mountComponent({ filterZones: substringOnly });
        handle.open();
        transport.resolve(0, CATALOG);
        await settle();
        const search = elementOf("worldClockSearch");
        typeInto(search, "UTC+5:30");
        assert.deepStrictEqual(namesOf(), ["加尔各答"], "组件必须自己做数值精确筛选");
        typeInto(search, "UTC+5:45");
        assert.deepStrictEqual(namesOf(), ["加德满都"]);
        typeInto(search, "UTC-4");
        assert.deepStrictEqual(namesOf(), ["纽约"], "纽约 -04:00 由投影 offsetSeconds 命中");
        typeInto(search, "UTC+2");
        assert.deepStrictEqual(namesOf(), ["柏林", "巴黎", "马德里"], "同偏移多条目按目录顺序返回");
    });

    it("无结果显示显式空态且不残留上次行", async () => {
        environment.setNow(fixedNow("2026-09-17", "15:00", 30));
        const { handle, transport } = mountComponent();
        handle.open();
        transport.resolve(0, CATALOG);
        await settle();
        typeInto(elementOf("worldClockSearch"), "qwertyasdf");
        assert.strictEqual(rowsOf().length, 0);
        assert.strictEqual(elementOf("worldClockEmpty").hidden, false);
        assert.strictEqual(elementOf("worldClockTable").hidden, true);
        assert.strictEqual(elementOf("worldClockPager").hidden, true);
        typeInto(elementOf("worldClockSearch"), "东京");
        assert.deepStrictEqual(namesOf(), ["东京"]);
        assert.strictEqual(elementOf("worldClockEmpty").hidden, true);
        assert.strictEqual(elementOf("worldClockTable").hidden, false);
    });

    it("非法输入隐藏结果并显示输入错误，合法后恢复（I-4）", async () => {
        environment.setNow(fixedNow("2026-09-17", "15:00", 30));
        const { handle, transport } = mountComponent();
        handle.open();
        transport.resolve(0, CATALOG);
        await settle();
        const date = elementOf("worldClockDate");
        const time = elementOf("worldClockTime");
        typeInto(date, "2026-02-30");
        assert.strictEqual(elementOf("worldClockInputError").hidden, false);
        assert.strictEqual(elementOf("worldClockTable").hidden, true);
        assert.strictEqual(rowsOf().length, 0);
        assert.strictEqual(elementOf("worldClockPager").hidden, true);
        assert.strictEqual(date.getAttribute("aria-invalid"), "true");
        assert.strictEqual(elementOf("worldClockNow").textContent.length > 0, true,
            "输入非法时当前钟面仍可用");
        typeInto(date, "2026-01-17");
        typeInto(time, "15:00");
        assert.strictEqual(elementOf("worldClockInputError").hidden, true);
        assert.strictEqual(date.hasAttribute("aria-invalid"), false);
        assert.deepStrictEqual(summarizeRows()[3], {
            name: "洛杉矶",
            zone: "America/Los_Angeles",
            time: "23:00",
            date: "2026-01-16",
            day: "前一天",
            dayHidden: false,
            difference: "慢 16 小时",
            offset: "UTC-08:00"
        });
    });

    it("搜索与分组在 loading/error 中禁用，日期/时间/关闭/使用当前时间可用", async () => {
        const { handle } = mountComponent();
        handle.open();
        assert.strictEqual(elementOf("worldClockSearch").disabled, true);
        assert.strictEqual(elementOf("worldClockRegion").disabled, true);
        assert.strictEqual(elementOf("worldClockRetry").disabled, true);
        assert.strictEqual(elementOf("worldClockDate").disabled, false);
        assert.strictEqual(elementOf("worldClockTime").disabled, false);
        assert.strictEqual(elementOf("worldClockUseNow").disabled, false);
        assert.strictEqual(elementOf("worldClockClose").disabled, false);
    });
});

// ---------------------------------------------------------------------------
// I-3：实时钟与用户选择分离
// ---------------------------------------------------------------------------

describe("I-3 实时钟与所选换算时间分离", () => {
    it("fake now 前进 60 秒只更新当前钟面，选择、输入与换算结果不变", async () => {
        const base = fixedNow("2026-09-17", "15:00", 30);
        environment.setNow(base);
        const { handle, transport } = mountComponent();
        handle.open();
        transport.resolve(0, CATALOG);
        await settle();
        const before = summarizeRows();
        const date = elementOf("worldClockDate");
        const time = elementOf("worldClockTime");
        assert.strictEqual(date.value, "2026-09-17");
        assert.strictEqual(time.value, "15:00");
        const nowBefore = elementOf("worldClockNow").textContent;
        const headerBefore = elementOf("worldClockHeaderTime").textContent;
        environment.setNow(base + 60000);
        environment.fireIntervals();
        assert.notStrictEqual(elementOf("worldClockNow").textContent, nowBefore);
        assert.notStrictEqual(elementOf("worldClockHeaderTime").textContent, headerBefore);
        assert.strictEqual(elementOf("worldClockNow").textContent, "2026-09-17 15:01:30");
        assert.strictEqual(elementOf("worldClockHeaderTime").textContent, "09月17日 15:01");
        assert.strictEqual(date.value, "2026-09-17");
        assert.strictEqual(time.value, "15:00");
        assert.deepStrictEqual(summarizeRows(), before, "tick 不得改变换算结果");
    });

    it("使用当前时间换算更新输入与结果；关闭重开保留选择", async () => {
        const base = fixedNow("2026-09-17", "15:00", 30);
        environment.setNow(base);
        const { handle, transport } = mountComponent();
        handle.open();
        transport.resolve(0, CATALOG);
        await settle();
        typeInto(elementOf("worldClockDate"), "2026-01-17");
        typeInto(elementOf("worldClockTime"), "15:00");
        environment.setNow(base + 61000);
        click(elementOf("worldClockUseNow"));
        assert.strictEqual(elementOf("worldClockDate").value, "2026-09-17");
        assert.strictEqual(elementOf("worldClockTime").value, "15:01");
        assert.deepStrictEqual(summarizeRows()[0].time, "08:01");
        const selection = summarizeRows();
        handle.close();
        assert.strictEqual(panelOf().hidden, true);
        assert.strictEqual(triggerOf().getAttribute("aria-expanded"), "false");
        handle.open();
        assert.strictEqual(elementOf("worldClockDate").value, "2026-09-17");
        assert.strictEqual(elementOf("worldClockTime").value, "15:01");
        assert.deepStrictEqual(summarizeRows(), selection);
        assert.strictEqual(transport.calls.length, 1);
    });

    it("键盘与指针交互：Escape/关闭按钮回焦 trigger，外部点击不抢焦点", async () => {
        const { handle, transport } = mountComponent();
        handle.open();
        transport.resolve(0, CATALOG);
        await settle();
        assert.strictEqual(environment.doc.activeElement === elementOf("worldClockDate"), true,
            "打开后焦点应落在日期输入");
        dispatch(environment.doc, new StubEvent("keydown", { key: "Escape" }));
        assert.strictEqual(panelOf().hidden, true);
        assert.strictEqual(environment.doc.activeElement === triggerOf(), true,
            "Escape 必须回焦 trigger");
        handle.open();
        elementOf("worldClockSearch").focus();
        click(elementOf("worldClockClose"));
        assert.strictEqual(panelOf().hidden, true);
        assert.strictEqual(environment.doc.activeElement === triggerOf(), true,
            "关闭按钮必须回焦 trigger");
        handle.open();
        elementOf("worldClockSearch").focus();
        dispatch(environment.doc, new StubEvent("pointerdown"));
        assert.strictEqual(panelOf().hidden, true);
        assert.strictEqual(environment.doc.activeElement === null, true,
            "外部点击关闭时不得回抢焦点");
        handle.open();
        dispatch(elementOf("worldClockUseNow"), new StubEvent("pointerdown"));
        assert.strictEqual(panelOf().hidden, false, "浮层内部 pointerdown 不关闭");
        dispatch(triggerOf(), new StubEvent("pointerdown"));
        assert.strictEqual(panelOf().hidden, false, "trigger 自身 pointerdown 不关闭");
        const outside = new StubEvent("focusin");
        outside.target = environment.brand;
        environment.doc.activeElement = environment.brand;
        dispatchFrom(environment.doc, outside);
        assert.strictEqual(panelOf().hidden, true, "Tab 焦点移出浮层时关闭");
        assert.strictEqual(environment.doc.activeElement === environment.brand, true,
            "焦点移出关闭不得回抢或清除外部焦点");
        handle.open();
        const inside = new StubEvent("focusin");
        inside.target = elementOf("worldClockSearch");
        environment.doc.activeElement = elementOf("worldClockSearch");
        dispatchFrom(environment.doc, inside);
        assert.strictEqual(panelOf().hidden, false, "浮层内部 focusin 不关闭");
    });
});

// ---------------------------------------------------------------------------
// I-6：失败、重试、晚到回包、认证隐藏、销毁
// ---------------------------------------------------------------------------

describe("I-6 失败/重试/认证/销毁", () => {
    it("加载失败显示错误与显式重试入口，当前钟面仍显示", async () => {
        const { handle, transport } = mountComponent();
        handle.open();
        transport.reject(0);
        await settle();
        const status = elementOf("worldClockLoadStatus");
        assert.strictEqual(status.textContent, "时区加载失败，请重试。");
        assert.strictEqual(status.classList.contains("world-clock-error"), true);
        assert.strictEqual(elementOf("worldClockRetry").hidden, false);
        assert.strictEqual(rowsOf().length, 0);
        assert.strictEqual(elementOf("worldClockTable").hidden, true);
        assert.strictEqual(elementOf("worldClockNow").textContent.length > 0, true);
        assert.strictEqual(elementOf("worldClockSearch").disabled, true);
        handle.close();
        handle.open();
        assert.strictEqual(transport.calls.length, 1, "失败后重开只显示错误，待用户显式重试");
        assert.strictEqual(status.textContent, "时区加载失败，请重试。");
        assert.strictEqual(elementOf("worldClockRetry").hidden, false);
    });

    it("重试成功回到 ready；重试进行中不可重复触发", async () => {
        const { handle, transport } = mountComponent();
        handle.open();
        transport.reject(0);
        await settle();
        const retry = elementOf("worldClockRetry");
        click(retry);
        assert.strictEqual(transport.calls.length, 2);
        click(retry);
        assert.strictEqual(transport.calls.length, 2, "loading 中不得重复请求");
        assert.strictEqual(retry.disabled, true);
        transport.resolve(1, CATALOG);
        await settle();
        assert.strictEqual(elementOf("worldClockLoadStatus").hidden, true);
        assert.strictEqual(elementOf("worldClockLoadStatus").classList.contains("world-clock-error"), false);
        assert.strictEqual(retry.hidden, true);
        assert.strictEqual(rowsOf().length, 6);
        assert.strictEqual(elementOf("worldClockSearch").disabled, false);
    });

    it("非数组或没有合法条目的响应视为 error，不使用常见假数据", async () => {
        const { handle, transport } = mountComponent();
        handle.open();
        transport.resolve(0, { zones: CATALOG });
        await settle();
        assert.strictEqual(elementOf("worldClockLoadStatus").textContent, "时区加载失败，请重试。");
        click(elementOf("worldClockRetry"));
        transport.resolve(1, []);
        await settle();
        assert.strictEqual(elementOf("worldClockLoadStatus").textContent, "时区加载失败，请重试。");
        assert.strictEqual(rowsOf().length, 0);
        click(elementOf("worldClockRetry"));
        transport.resolve(2, [{ id: "", labelZh: "x", aliases: [] }]);
        await settle();
        assert.strictEqual(rowsOf().length, 0);
        assert.strictEqual(elementOf("worldClockRetry").hidden, false);
    });

    it("认证隐藏：关闭浮层、中止请求、清空目录与选择、停表；晚到回包不写 DOM", async () => {
        environment.setNow(fixedNow("2026-09-17", "15:00", 30));
        const { handle, transport } = mountComponent();
        handle.open();
        assert.strictEqual(transport.calls.length, 1);
        const signal = transport.calls[0].options.signal;
        assert.strictEqual(environment.pendingIntervals().length, 1);
        environment.shell.style.display = "none";
        environment.fireObserverFor(environment.shell);
        assert.strictEqual(signal.aborted, true);
        assert.strictEqual(environment.pendingIntervals().length, 0);
        assert.strictEqual(panelOf().hidden, true);
        assert.strictEqual(triggerOf().getAttribute("aria-expanded"), "false");
        transport.resolve(0, CATALOG);
        await settle();
        assert.strictEqual(rowsOf().length, 0, "晚到回包不得写 DOM");
        assert.strictEqual(elementOf("worldClockLoadStatus").textContent, "正在加载时区…");
        assert.strictEqual(elementOf("worldClockDate").value, "");
        environment.shell.style.display = "grid";
        environment.fireObserverFor(environment.shell);
        assert.strictEqual(environment.pendingIntervals().length, 1, "恢复会话只重启一个计时器");
        assert.strictEqual(transport.calls.length, 1, "恢复后目录延迟到点击");
        handle.open();
        assert.strictEqual(transport.calls.length, 2, "旧目录不得跨认证复用");
        assert.strictEqual(elementOf("worldClockDate").value, "2026-09-17");
        assert.strictEqual(environment.pendingIntervals().length, 1);
    });

    it("visibilitychange 暂停后台 tick，回来立即刷新当前时间", async () => {
        const base = fixedNow("2026-09-17", "15:00", 30);
        environment.setNow(base);
        mountComponent();
        assert.strictEqual(environment.pendingIntervals().length, 1);
        environment.doc.hidden = true;
        dispatch(environment.doc, new StubEvent("visibilitychange"));
        assert.strictEqual(environment.pendingIntervals().length, 0);
        environment.setNow(base + 5000);
        environment.doc.hidden = false;
        dispatch(environment.doc, new StubEvent("visibilitychange"));
        assert.strictEqual(environment.pendingIntervals().length, 1);
        assert.strictEqual(elementOf("worldClockNow").textContent, "2026-09-17 15:00:35");
    });

    it("destroy 解绑监听/observer/计时器/请求与新增 DOM，且保留宿主节点与监听", async () => {
        const { handle, transport } = mountComponent();
        const hostListener = () => {};
        environment.tabs[0].addEventListener("click", hostListener);
        handle.open();
        const trigger = triggerOf();
        const signal = transport.calls[0].options.signal;
        assert.strictEqual(environment.observers.length > 0, true);
        handle.destroy();
        mounted = null;
        assert.strictEqual(triggerOf(), null);
        assert.strictEqual(panelOf(), null);
        assert.strictEqual(environment.header.classList.contains("world-clock-header"), false);
        assert.strictEqual(environment.pendingIntervals().length, 0);
        assert.strictEqual(signal.aborted, true);
        environment.observers.forEach((observer) => {
            assert.strictEqual(observer.disconnected, true, "observer 必须 disconnect");
        });
        assert.deepStrictEqual(environment.nav.children.slice(), environment.tabs);
        assert.ok(environment.tabs[0].listeners.get("click").indexOf(hostListener) !== -1,
            "不得解绑宿主监听");
        assert.strictEqual(environment.doc.listeners.get("keydown").length, 0);
        assert.strictEqual(environment.doc.listeners.get("pointerdown").length, 0);
        assert.strictEqual(environment.doc.listeners.get("focusin").length, 0);
        assert.strictEqual(environment.doc.listeners.get("visibilitychange").length, 0);
        click(trigger);
        assert.strictEqual(transport.calls.length, 1, "destroy 后组件监听不得生效");
        transport.resolve(0, CATALOG);
        await settle();
        assert.strictEqual(panelOf(), null, "晚到回包不得重建 DOM");
        const again = mountComponent();
        assert.ok(again.handle);
        assert.strictEqual(environment.header.querySelectorAll("#worldClockTrigger").length, 1);
    });
});

// ---------------------------------------------------------------------------
// I-7：只读边界
// ---------------------------------------------------------------------------

describe("I-7 只读边界", () => {
    it("transport 只收到目录 GET，路径为相对路径且无重复前缀", async () => {
        environment.setNow(fixedNow("2026-09-17", "15:00", 30));
        const { handle, transport } = mountComponent();
        handle.open();
        transport.resolve(0, CATALOG);
        await settle();
        typeInto(elementOf("worldClockSearch"), "巴黎");
        click(elementOf("worldClockGlobal"));
        typeInto(elementOf("worldClockDate"), "2026-11-01");
        typeInto(elementOf("worldClockTime"), "14:30");
        click(elementOf("worldClockUseNow"));
        assert.strictEqual(transport.calls.length, 1, "只有目录 GET");
        const call = transport.calls[0];
        assert.strictEqual(call.url.startsWith("/api/mail/meeting-confirmation/time-zones?date="), true);
        assert.strictEqual(call.url.indexOf("//"), -1, "不得重复前缀或协议");
        assert.strictEqual(call.options.method, undefined, "不得使用 POST 或自定义方法");
    });

    it("目录文本一律 textContent：恶意 label 不产生元素、无 inline style", async () => {
        environment.setNow(fixedNow("2026-09-17", "15:00", 30));
        const evil = {
            id: "Europe/Lisbon",
            labelZh: "<img src=x onerror=alert(1)>",
            aliases: [],
            offsetLabel: "UTC+00:00",
            offsetSeconds: 0
        };
        const { handle, transport } = mountComponent();
        handle.open();
        transport.resolve(0, CATALOG.concat([evil]));
        await settle();
        typeInto(elementOf("worldClockSearch"), "里斯本");
        assert.strictEqual(rowsOf().length, 0, "搜索只命中目录文本");
        typeInto(elementOf("worldClockSearch"), "Europe/Lisbon");
        assert.strictEqual(rowsOf().length, 1);
        const mine = rowsOf()[0];
        assert.strictEqual(mine.querySelector(".world-clock-name").textContent, evil.labelZh);
        assert.strictEqual(mine.querySelectorAll("img").length, 0);
        assert.strictEqual(mine.children.some((cell) => cell.hasAttribute("onerror")), false);
        const walk = [mine];
        while (walk.length) {
            const node = walk.pop();
            assert.strictEqual(node.hasAttribute("style"), false);
            assert.strictEqual(Object.keys(node.style).length, 0);
            node.children.forEach((child) => walk.push(child));
        }
    });

    it("生产模块不含静态目录数据、持久化偏好、fetch 或 ES module 语法", () => {
        assert.strictEqual(/\b(localStorage|sessionStorage)\b/.test(moduleSource), false);
        assert.strictEqual(/\bfetch\s*\(/.test(moduleSource), false);
        assert.strictEqual(/\.style\.[A-Za-z]+\s*=[^=]/.test(moduleSource), false, "不得写 element.style");
        assert.strictEqual(/setAttribute\(\s*["']style["']/.test(moduleSource), false);
        assert.strictEqual(/\.cssText\s*=/.test(moduleSource), false);
        assert.strictEqual(/^\s*(import|export)\s/m.test(moduleSource), false);
        assert.ok(moduleSource.indexOf("module.exports") !== -1);
        const codeSource = moduleSource
            .replace(/\/\*[\s\S]*?\*\//g, "")
            .replace(/^\s*\/\/.*$/gm, "");
        const zoneIds = codeSource.match(/[A-Z][A-Za-z]+\/[A-Za-z_]+/g) || [];
        assert.deepStrictEqual(Array.from(new Set(zoneIds)).sort(), [
            "America/Los_Angeles", "America/New_York", "Asia/Seoul", "Asia/Tokyo",
            "Europe/Berlin", "Europe/London"
        ], "生产模块只允许计划固定的六个常用 IANA id");
    });

    it("页面未被注册（01 不激活）：index.html 不含组件节点", () => {
        const index = fs.readFileSync(path.join(STATIC_DIR, "index.html"), "utf-8");
        assert.strictEqual(index.indexOf("worldClockTrigger"), -1);
        assert.strictEqual(index.indexOf("worldClockPanel"), -1);
    });
});

// ---------------------------------------------------------------------------
// T3/T4：脚本自启动与独立导出
// ---------------------------------------------------------------------------

describe("脚本自启动与独立导出（T1/I-8 边界）", () => {
    it("普通 script：浏览器挂 window.WorldClock，Node 导出 mount/parseBeijingInput/projectZones", () => {
        const browserWindow = {};
        const sandbox = { window: browserWindow };
        vm.runInNewContext(moduleSource, sandbox);
        assert.deepStrictEqual(Object.keys(browserWindow.WorldClock).sort(),
            ["mount", "parseBeijingInput", "projectZones", "templates"]);
        assert.deepStrictEqual(Object.keys(browserWindow.WorldClock.templates).sort(),
            ["panel", "row", "trigger"]);
        assert.strictEqual(typeof WorldClock.mount, "function");
        assert.strictEqual(typeof WorldClock.parseBeijingInput, "function");
        assert.strictEqual(typeof WorldClock.projectZones, "function");
    });

    it("readyState=loading 时注册 DOMContentLoaded；宿主缺失时不挂载也不报错", () => {
        const listeners = [];
        const browserWindow = {};
        vm.runInNewContext(moduleSource, {
            window: browserWindow,
            document: {
                readyState: "loading",
                addEventListener(type, handler) {
                    listeners.push({ type, handler });
                }
            }
        });
        assert.strictEqual(listeners.length, 1);
        assert.strictEqual(listeners[0].type, "DOMContentLoaded");
        const readyListeners = [];
        vm.runInNewContext(moduleSource, {
            window: browserWindow,
            api: () => Promise.resolve([]),
            MailboxMeeting: { filterZones: (zones) => zones },
            document: {
                readyState: "complete",
                querySelector: () => null,
                addEventListener(type, handler) {
                    readyListeners.push({ type, handler });
                }
            }
        });
        assert.strictEqual(readyListeners.length, 0);
    });
});
