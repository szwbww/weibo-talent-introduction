// ════════════════════════════════════════════════════════════════════════
// fast-p 03 收发件箱 × 会议日历集成测试（I-1/I-2/I-3/I-4 · S-3）
// 1) S-3 源文本契约：列表卡片 calendar-summary 骨架、头部三个操作骨架、
//    宿主 adapter 三函数、页面内变更广播的订阅与 unmount 解绑；
// 2) 真实挂载 mailbox-chat.js（最小 DOM）验证：批量摘要落卡片与头部、
//    失败显示“排期暂不可用”而非 0 场、0 场隐藏改期/取消、陈旧 epoch 不覆盖、
//    头部三个操作按 mode 调宿主、广播只刷新当前 owner、unmount 后不再回读；
// 3) 草稿卡时间：preview UTC 经唯一中文北京 formatter（app.js）渲染，
//    不出现英文周/月与原 IANA zone 串。
// 载入方式：真实组件 + app.js 顶层 formatter 切片进 vm 沙箱；无 npm 依赖、无真实网络。

"use strict";

const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const ROOT = path.join(__dirname, "..", "..", "main", "resources", "static");
const chatSource = fs.readFileSync(path.join(ROOT, "mailbox-chat.js"), "utf-8");
const appSource = fs.readFileSync(path.join(ROOT, "app.js"), "utf-8");
const stylesSource = fs.readFileSync(path.join(ROOT, "styles.css"), "utf-8");
const chatCssSource = fs.readFileSync(path.join(ROOT, "mailbox-chat.css"), "utf-8");
const briefSource = fs.readFileSync(
    path.join(__dirname, "..", "..", "..", "docs", "plans", "fast", "meeting-mail-master", "children", "03-calendar-ui", "brief.md"),
    "utf-8"
);
const briefHtml = Array.from(briefSource.matchAll(/```html\n([\s\S]*?)```/g)).map((match) => match[1]);
const BRIEF_SUMMARY_SPAN = briefHtml[3].trim();
const BRIEF_HEADER_BUTTONS = briefHtml[4].trim().split("\n").map((line) => line.trim());

function countOccurrences(text, needle) {
    let count = 0;
    let index = text.indexOf(needle);
    while (index !== -1) {
        count += 1;
        index = text.indexOf(needle, index + needle.length);
    }
    return count;
}

// app.js 顶层 formatter（唯一中文北京时间口径）切片
function extractAppFn(name) {
    const regex = new RegExp("(?:async\\s+)?function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}");
    const match = appSource.match(regex);
    if (!match) throw new Error("Could not find " + name + " in app.js");
    return match[0];
}

function extractAppRegion(startMarker, endMarker) {
    const start = appSource.indexOf(startMarker);
    const end = appSource.indexOf(endMarker, start);
    if (start < 0 || end < 0) throw new Error("app.js region not found: " + startMarker);
    return appSource.slice(start, end);
}

// mailbox-chat.js 模块级 helper（IIFE 内 4 空格缩进，按起止标记切片）
function extractChatModuleFn(name) {
    const start = chatSource.indexOf("    function " + name + "(");
    if (start < 0) throw new Error("Could not find " + name + " in mailbox-chat.js");
    const end = chatSource.indexOf("\n    }\n", start);
    if (end < 0) throw new Error("Could not find end of " + name);
    return chatSource.slice(start, end + "\n    }".length);
}

// ── 源文本契约 ───────────────────────────────────────────────────────────

describe("S-3: 收发件箱排期源文本契约", () => {
    it("列表卡片追加 calendar-summary 骨架（属性逐字、内容由 textContent 填充）", () => {
        const skeleton = BRIEF_SUMMARY_SPAN.replace(/>.*<\/span>$/, "></span>");
        assert.ok(chatSource.includes(skeleton), `列表卡片摘要骨架必须逐字：${skeleton}`);
        assert.strictEqual(countOccurrences(chatSource, skeleton), 2, "列表卡片与头部各一处骨架");
        assert.ok(chatSource.includes('<span class="calendar-summary" data-role="meeting-summary"></span>'));
        assert.ok(stylesSource.includes(".calendar-summary{"), "calendar-summary 必须由 styles.css 声明");
        assert.ok(!chatCssSource.includes(".calendar-summary"), "mailbox-chat.css 不得吸收新 class");
    });

    it("头部操作区追加三个骨架按钮（逐字，含 danger）", () => {
        BRIEF_HEADER_BUTTONS.forEach((line) => {
            assert.ok(chatSource.includes(line), `头部操作骨架必须逐字：${line}`);
        });
        ["mc-add-schedule", "mc-edit-schedule", "mc-cancel-schedule"].forEach((action) => {
            assert.strictEqual(countOccurrences(chatSource, 'data-action="' + action + '"'), 1, action + " 骨架唯一");
            assert.ok(chatSource.includes('action === "' + action + '"'), action + " 必须接入事件分派");
        });
    });

    it("全部经宿主 adapter 走同一套表单/API（组件不自建排期真值）", () => {
        ["mcHostGetMeetingSummaries", "mcHostOpenMeetingSchedule", "mcHostMeetingScheduleChanged"]
            .forEach((fn) => assert.ok(chatSource.includes(`"${fn}"`), `必须经宿主 adapter ${fn}`));
        assert.ok(!/\/api\/meeting-calendar/.test(chatSource), "组件不得绕过宿主直连排期 API");
        assert.ok(!/localStorage[\s\S]{0,80}meeting/i.test(chatSource), "组件不得把排期写入 localStorage");
    });

    it("页面内变更广播的订阅与 unmount 解绑对称", () => {
        assert.strictEqual(countOccurrences(chatSource, 'addEventListener("meeting-calendar-changed"'), 1);
        assert.strictEqual(countOccurrences(chatSource, 'removeEventListener("meeting-calendar-changed"'), 1);
        assert.strictEqual(countOccurrences(chatSource, "notifyMeetingScheduleChanged("), 2, "声明 1 处 + 发送成功 1 处");
    });

    it("草稿卡时间不再回显原 IANA zone 串/英文本地串", () => {
        assert.ok(chatSource.includes('hostFn("formatBeijingMeetingRange")'), "草稿卡必须走统一中文北京 formatter");
        const codeOnly = chatSource
            .split("\n")
            .filter((line) => !/^\s*(\/\/|\*|\/\*)/.test(line))
            .join("\n");
        assert.ok(!codeOnly.includes("zoneId"), "不得回显 zoneId");
        assert.ok(!codeOnly.includes("startLocal"), "不得改用本地字符串兜底");
    });
});

// ── 草稿卡时间（真实 formatter 组合） ────────────────────────────────────

describe("I-2: 会议草稿卡时间文案", () => {
    function createMetaSandbox() {
        const sandbox = { console, URLSearchParams };
        vm.createContext(sandbox);
        vm.runInContext(extractAppRegion("const MEETING_CALENDAR_ZONE = ", "// ── API adapter"), sandbox);
        ["meetingCalendarBeijingParts", "formatBeijingMeetingRange"].forEach((name) => {
            vm.runInContext(extractAppFn(name), sandbox);
        });
        vm.runInContext(extractChatModuleFn("meetingCardMetaTextFor"), sandbox);
        return sandbox;
    }

    it("同日与跨日均输出中文北京串，无英文周/月与 IANA zone", () => {
        const sandbox = createMetaSandbox();
        const sameDay = vm.runInContext(
            "meetingCardMetaTextFor({ startUtc: '2026-09-11T07:00:00Z', endUtc: '2026-09-11T07:30:00Z', durationMinutes: 30 }, formatBeijingMeetingRange)",
            sandbox
        );
        assert.strictEqual(sameDay, "2026年9月11日 周五 15:00–15:30 · 30 分钟");
        const crossDay = vm.runInContext(
            "meetingCardMetaTextFor({ startUtc: '2026-09-30T15:30:00Z', endUtc: '2026-09-30T16:30:00Z', durationMinutes: 60 }, formatBeijingMeetingRange)",
            sandbox
        );
        assert.strictEqual(crossDay, "2026年9月30日 周三 23:30 – 2026年10月1日 周四 00:30 · 60 分钟");
        [sameDay, crossDay].forEach((text) => {
            assert.ok(!/Mon|Tue|Wed|Thu|Fri|Sat|Sun|Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec/.test(text));
            assert.ok(!/UTC|GMT|Asia\/|Europe\//.test(text));
        });
    });

    it("缺少 UTC 值或 formatter 缺席时返回空串（不伪造时间）", () => {
        const sandbox = createMetaSandbox();
        assert.strictEqual(vm.runInContext("meetingCardMetaTextFor({ durationMinutes: 30 }, formatBeijingMeetingRange)", sandbox), "");
        assert.strictEqual(vm.runInContext("meetingCardMetaTextFor({ startUtc: '2026-09-11T07:00:00Z' }, formatBeijingMeetingRange)", sandbox), "");
        assert.strictEqual(vm.runInContext("meetingCardMetaTextFor({ startUtc: '2026-09-11T07:00:00Z', endUtc: '2026-09-11T07:30:00Z' }, null)", sandbox), "");
        assert.strictEqual(vm.runInContext("meetingCardMetaTextFor({ startUtc: '2026-09-11T07:00:00Z', endUtc: '2026-09-11T07:30:00Z', durationMinutes: 30 }, () => '')", sandbox), "");
    });
});

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


// ── 挂载夹具：真实 mailbox-chat.js + 排期宿主 adapter stub ────────────────

const OPERATOR_STATUS_CATALOG = [["REPLIED", "已回复"], ["INVITED", "已邀约"], ["NOT_CONTACTED", "未联系"]];
const INDEX_LEVEL_CATALOG = [["RAW", "原始"], ["CANDIDATE", "筛选"], ["APPLICATION", "有效"]];

function expertItem(contactId, extra) {
    return Object.assign({
        contactId,
        name: contactId === 1 ? "专家A" : "专家B",
        email: "expert" + contactId + "@example.edu",
        accountCodes: ["acc1"],
        receivedCount: 2,
        sentCount: 1,
        latestMessage: { direction: "INBOUND", subject: "问题" + contactId },
        followed: false,
        expertTags: []
    }, extra || {});
}

function createSandbox(options) {
    const opts = options || {};
    const requests = [];
    const calls = { summaries: [], openSchedule: [], scheduleChanged: [], status: [] };
    const sandbox = {
        console,
        URLSearchParams,
        setTimeout: () => 0,
        clearTimeout: () => {},
        escapeHtml: escapeHtmlLike,
        showStatus: (message, type) => calls.status.push({ message, type }),
        api: (url, requestOptions) => {
            requests.push({ url, method: (requestOptions && requestOptions.method) || "GET" });
            if (url.startsWith("/api/mail/mailbox/conversations?")) {
                const conversations = opts.conversations;
                return Promise.resolve(typeof conversations === "function" ? conversations(url) : (conversations || { items: [], total: 0 }));
            }
            if (/\/api\/mail\/mailbox\/conversations\/\d+\/messages/.test(url)) {
                return Promise.resolve({ items: [], nextBefore: null, hasMore: false });
            }
            if (/\/api\/expert-contacts\/\d+$/.test(url)) {
                return Promise.resolve({ contact: { id: 1, operatorStatus: "REPLIED", currentIndexLevel: "APPLICATION" }, mails: [] });
            }
            return Promise.resolve({});
        },
        mcHostGetMeetingSummaries: (contactIds) => {
            calls.summaries.push((contactIds || []).slice());
            if (opts.summariesFn) return opts.summariesFn(contactIds);
            const rows = (contactIds || []).map((id) => ({
                contactId: id,
                activeCount: Number((opts.summaryCounts || {})[id] || 0),
                next: (opts.summaryNext || {})[id] || null
            }));
            return Promise.resolve(rows);
        },
        mcHostOpenMeetingSchedule: (contactId, scheduleOptions) => {
            calls.openSchedule.push({ contactId: Number(contactId), options: scheduleOptions || {} });
            return Promise.resolve();
        },
        mcHostMeetingScheduleChanged: (contactId) => { calls.scheduleChanged.push(Number(contactId)); },
        formatBeijingMeetingShort: (value) => "SHORT(" + value + ")",
        formatBeijingMeetingRange: (startUtc, endUtc) => "RANGE(" + startUtc + "~" + endUtc + ")",
        expertTagLabels: {}
    };
    sandbox.operatorStatusOptions = OPERATOR_STATUS_CATALOG;
    sandbox.indexLevelOptions = INDEX_LEVEL_CATALOG;
    vm.createContext(sandbox);
    vm.runInContext(chatSource, sandbox, { filename: "mailbox-chat.js" });
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

function personFor(ctx, contactId) {
    return ctx.host.querySelectorAll(".mc-person").find((person) => String(person.dataset.contactId) === String(contactId)) || null;
}

function summaryCard(ctx, contactId) {
    const person = personFor(ctx, contactId);
    return person ? person.querySelector('[data-role="meeting-summary"]') : null;
}

function headerSummary(ctx) {
    const identity = ctx.host.querySelector(".mc-identity");
    return identity ? identity.querySelector('[data-role="meeting-summary"]') : null;
}

function headerButton(ctx, action) {
    const actions = ctx.host.querySelector(".mc-actions");
    return actions ? actions.querySelector('[data-action="' + action + '"]') : null;
}

function click(el) {
    el.dispatchEvent(new MiniEvent("click", { bubbles: true }));
}

function emitCalendarChanged(ctx, contactId) {
    const event = new MiniEvent("meeting-calendar-changed", { bubbles: true });
    event.detail = { contactId };
    ctx.doc.root.dispatchEvent(event);
}

async function openExpert(ctx, contactId) {
    click(personFor(ctx, contactId).querySelector(".mc-person-main"));
    await flush();
}

describe("I-3/I-4: 列表页排期摘要", () => {
    it("一次批量传当前页专家 id，并落到卡片", async () => {
        const ctx = mountChat({
            conversations: { items: [expertItem(1), expertItem(2)], total: 2 },
            summaryCounts: { 1: 2, 2: 0 },
            summaryNext: { 1: { startUtc: "2026-09-18T02:00:00Z" } }
        });
        await flush();
        assert.deepStrictEqual(JSON.parse(JSON.stringify(ctx.calls.summaries[0])), [1, 2], "摘要必须批量传当前页全部专家 id");
        assert.strictEqual(summaryCard(ctx, 1).textContent, "已有排期 · 2场 · SHORT(2026-09-18T02:00:00Z)");
        assert.strictEqual(summaryCard(ctx, 2).textContent, "暂无排期");
    });

    it("摘要读取失败显示“排期暂不可用”，不冒充 0 场", async () => {
        const ctx = mountChat({
            conversations: { items: [expertItem(1)], total: 1 },
            summariesFn: () => Promise.reject(new Error("503"))
        });
        await flush();
        assert.strictEqual(summaryCard(ctx, 1).textContent, "排期暂不可用");
        assert.ok(!summaryCard(ctx, 1).textContent.includes("暂无排期"));
    });

    it("陈旧列表 epoch 的慢回包不得覆盖新筛选结果", async () => {
        const deferred = [];
        const ctx = mountChat({
            conversations: { items: [expertItem(1)], total: 1 },
            summariesFn: () => new Promise((resolve, reject) => deferred.push({ resolve, reject }))
        });
        await flush();
        assert.strictEqual(deferred.length, 1);
        ctx.sandbox.MailboxChat.mount(ctx.host, {});
        await flush();
        assert.strictEqual(deferred.length, 2, "第二次列表刷新必须重新读摘要");
        deferred[1].resolve([{ contactId: 1, activeCount: 1, next: null }]);
        await flush();
        assert.strictEqual(summaryCard(ctx, 1).textContent, "已有排期 · 1场");
        deferred[0].resolve([{ contactId: 1, activeCount: 9, next: null }]);
        await flush();
        assert.strictEqual(summaryCard(ctx, 1).textContent, "已有排期 · 1场", "旧 epoch 摘要不得覆盖");
    });
});

describe("I-1/I-4: 头部排期操作（S-3）", () => {
    it("头部显示摘要；0 场隐藏改期/取消，保留新增", async () => {
        const ctx = mountChat({ conversations: { items: [expertItem(1)], total: 1 }, summaryCounts: { 1: 0 } });
        await flush();
        await openExpert(ctx, 1);
        assert.strictEqual(headerSummary(ctx).textContent, "暂无排期");
        assert.strictEqual(headerButton(ctx, "mc-edit-schedule").hidden, true);
        assert.strictEqual(headerButton(ctx, "mc-cancel-schedule").hidden, true);
        assert.strictEqual(headerButton(ctx, "mc-add-schedule").hidden, false, "新增排期必须保留");
    });

    it("有排期时三个操作可用，并按 mode 调同一宿主表单 adapter", async () => {
        const ctx = mountChat({ conversations: { items: [expertItem(1)], total: 1 }, summaryCounts: { 1: 2 } });
        await flush();
        await openExpert(ctx, 1);
        assert.strictEqual(headerSummary(ctx).textContent, "已有排期 · 2场");
        assert.strictEqual(headerButton(ctx, "mc-edit-schedule").hidden, false);
        assert.strictEqual(headerButton(ctx, "mc-cancel-schedule").hidden, false);
        click(headerButton(ctx, "mc-add-schedule"));
        click(headerButton(ctx, "mc-edit-schedule"));
        click(headerButton(ctx, "mc-cancel-schedule"));
        await flush();
        assert.deepStrictEqual(ctx.calls.openSchedule.map((call) => call.options.mode), ["create", "edit", "cancel"]);
        assert.ok(ctx.calls.openSchedule.every((call) => call.contactId === 1 && call.options.expertLabel === "专家A"));
    });

    it("宿主 adapter 缺席时降级提示，不伪造成功", async () => {
        const ctx = mountChat({ conversations: { items: [expertItem(1)], total: 1 }, summaryCounts: { 1: 1 } });
        await flush();
        await openExpert(ctx, 1);
        delete ctx.sandbox.mcHostOpenMeetingSchedule;
        click(headerButton(ctx, "mc-cancel-schedule"));
        await flush();
        assert.strictEqual(ctx.calls.openSchedule.length, 0);
        assert.ok(ctx.calls.status.some((entry) => entry.message === "排期功能暂不可用"));
    });
});

describe("I-1/I-3: 页面内变更广播", () => {
    it("广播只刷新当前 owner：摘要失效回读，草稿/时间线区域不重建", async () => {
        const ctx = mountChat({ conversations: { items: [expertItem(1)], total: 1 }, summaryCounts: { 1: 1 } });
        await flush();
        await openExpert(ctx, 1);
        const scrollBefore = ctx.host.querySelector(".mc-scroll");
        const before = ctx.calls.summaries.length;
        emitCalendarChanged(ctx, 1);
        await flush();
        assert.strictEqual(ctx.calls.summaries.length, before + 1, "广播必须回读一次摘要");
        assert.strictEqual(summaryCard(ctx, 1).textContent, "已有排期 · 1场");
        assert.strictEqual(ctx.host.querySelector(".mc-scroll"), scrollBefore, "长会话滚动区（含草稿）不得被重建");
    });

    it("unmount 后解绑广播，不再回读", async () => {
        const ctx = mountChat({ conversations: { items: [expertItem(1)], total: 1 }, summaryCounts: { 1: 1 } });
        await flush();
        const before = ctx.calls.summaries.length;
        ctx.sandbox.MailboxChat.unmount(ctx.host);
        await flush();
        emitCalendarChanged(ctx, 1);
        await flush();
        assert.strictEqual(ctx.calls.summaries.length, before, "unmount 后必须解绑 meeting-calendar-changed");
    });
});
