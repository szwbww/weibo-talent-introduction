"use strict";

// fast-p 2026-10-27 · user1 只读邮件视图（03 I-2/I-3/I-4 · S-1）。
// 模块行为/网络测试：用真实实现加载 mailbox-readonly.js，配一个最小 DOM 模型
// （createElement/appendChild/textContent/classList/dataset/事件），断言渲染结果、
// 请求白名单、失权与竞态语义。S-1 DOM 骨架逐层校验，不用万能 querySelector stub。

const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const ROOT = path.join(__dirname, "..", "..", "main", "resources", "static");
const modulePath = path.join(ROOT, "mailbox-readonly.js");
const moduleSource = fs.readFileSync(modulePath, "utf-8");

// ---------------------------------------------------------------------------
// 最小 DOM 模型（只实现模块实际使用的 API）
// ---------------------------------------------------------------------------
class FakeClassList {
    constructor(node) {
        Object.defineProperty(this, "node", { value: node });
    }
    names() {
        return new Set(String(this.node.className || "").split(/\s+/).filter(Boolean));
    }
    write(set) {
        this.node.className = Array.from(set).join(" ");
    }
    add(...values) {
        const set = this.names();
        values.forEach((value) => set.add(value));
        this.write(set);
    }
    remove(...values) {
        const set = this.names();
        values.forEach((value) => set.delete(value));
        this.write(set);
    }
    contains(value) {
        return this.names().has(value);
    }
    toggle(value, force) {
        const set = this.names();
        const next = force === undefined ? !set.has(value) : Boolean(force);
        if (next) set.add(value); else set.delete(value);
        this.write(set);
        return next;
    }
}

class FakeElement {
    constructor(doc, tagName) {
        this.ownerDocument = doc;
        this.tagName = String(tagName || "").toUpperCase();
        this.children = [];
        this.parentNode = null;
        this._text = "";
        this.dataset = {};
        this.attributes = {};
        this.listeners = {};
        this.hidden = false;
        this.disabled = false;
        this.value = "";
        this.className = "";
        this.classList = new FakeClassList(this);
        this._scrollTop = 0;
    }
    get textContent() {
        if (!this.children.length) return this._text;
        return this.children.map((child) => child.textContent).join("");
    }
    set textContent(value) {
        this.children.forEach((child) => { child.parentNode = null; });
        this.children = [];
        this._text = value === null || value === undefined ? "" : String(value);
    }
    get scrollTop() {
        return this._scrollTop;
    }
    set scrollTop(value) {
        this._scrollTop = Number(value) || 0;
    }
    get scrollHeight() {
        return this._text ? 40 : this.children.length * 40;
    }
    appendChild(child) {
        if (child.parentNode) child.parentNode.removeChild(child);
        child.parentNode = this;
        this.children.push(child);
        return child;
    }
    replaceChildren(...nodes) {
        this.children.forEach((child) => { child.parentNode = null; });
        this.children = [];
        this._text = "";
        nodes.forEach((node) => this.appendChild(node));
    }
    removeChild(child) {
        const index = this.children.indexOf(child);
        if (index >= 0) {
            this.children.splice(index, 1);
            child.parentNode = null;
        }
        return child;
    }
    setAttribute(name, value) {
        this.attributes[name] = String(value);
        if (name.startsWith("data-")) {
            const key = name.slice(5).replace(/-([a-z])/g, (_, char) => char.toUpperCase());
            this.dataset[key] = String(value);
        }
    }
    getAttribute(name) {
        return Object.prototype.hasOwnProperty.call(this.attributes, name) ? this.attributes[name] : null;
    }
    addEventListener(type, handler) {
        (this.listeners[type] = this.listeners[type] || []).push(handler);
    }
    removeEventListener(type, handler) {
        const list = this.listeners[type] || [];
        const index = list.indexOf(handler);
        if (index >= 0) list.splice(index, 1);
    }
    dispatch(type, event) {
        (this.listeners[type] || []).slice().forEach((handler) => handler(event));
    }
    closest(selector) {
        let node = this;
        while (node) {
            if (selector === "[data-action]" && node.dataset && node.dataset.action !== undefined) return node;
            if (selector.startsWith(".") && node.classList && node.classList.contains(selector.slice(1))) return node;
            node = node.parentNode;
        }
        return null;
    }
    querySelectorAll() {
        return [];
    }
}

function createDocument() {
    const doc = {
        hidden: false,
        listeners: {},
        createElement(tag) { return new FakeElement(doc, tag); },
        addEventListener(type, handler) { (doc.listeners[type] = doc.listeners[type] || []).push(handler); },
        removeEventListener(type, handler) {
            const list = doc.listeners[type] || [];
            const index = list.indexOf(handler);
            if (index >= 0) list.splice(index, 1);
        },
        dispatch(type, event) { (doc.listeners[type] || []).slice().forEach((handler) => handler(event)); }
    };
    return doc;
}

function loadRuntime(doc) {
    const sandbox = {
        document: doc,
        AbortController,
        setTimeout,
        clearTimeout,
        console
    };
    sandbox.window = sandbox;
    sandbox.listeners = {};
    sandbox.addEventListener = (type, handler) => {
        (sandbox.listeners[type] = sandbox.listeners[type] || []).push(handler);
    };
    sandbox.removeEventListener = (type, handler) => {
        const list = sandbox.listeners[type] || [];
        const index = list.indexOf(handler);
        if (index >= 0) list.splice(index, 1);
    };
    sandbox.dispatchWindow = (type, event) => {
        (sandbox.listeners[type] || []).slice().forEach((handler) => handler(event));
    };
    vm.createContext(sandbox);
    vm.runInContext(moduleSource, sandbox, { filename: modulePath });
    return sandbox;
}

function allNodes(node) {
    const result = [node];
    node.children.forEach((child) => result.push(...allNodes(child)));
    return result;
}

function classes(node) {
    return String(node.className || "").split(/\s+/).filter(Boolean);
}

function findAll(node, className) {
    return allNodes(node).filter((candidate) => classes(candidate).includes(className));
}

function findOne(node, className) {
    return findAll(node, className)[0] || null;
}

function byAction(root, action) {
    return allNodes(root).find((node) => node.dataset && node.dataset.action === action) || null;
}

function deferred() {
    let resolve;
    let reject;
    const promise = new Promise((res, rej) => { resolve = res; reject = rej; });
    return { promise, resolve, reject };
}

const flush = async () => {
    for (let index = 0; index < 6; index += 1) {
        await new Promise((resolve) => setTimeout(resolve, 0));
    }
};

const LIST_PATH = "/api/mail/mailbox/conversations";

function createHarness() {
    const document = createDocument();
    const runtime = loadRuntime(document);
    const host = document.createElement("div");
    const calls = [];
    const harness = {
        document,
        runtime,
        host,
        calls,
        responder: async () => ({ items: [], total: 0, page: 0, size: 20 })
    };
    harness.api = (requestPath, options = {}) => {
        calls.push({ path: requestPath, options });
        return harness.responder(requestPath, options);
    };
    harness.mount = () => {
        harness.handle = runtime.window.MailboxReadOnly.mount(host, {
            api: harness.api,
            sessionUser: { username: "user1" }
        });
        harness.root = host.children[0];
        return harness.handle;
    };
    return harness;
}

function listResponse(items, total) {
    return { items, total: total === undefined ? items.length : total, page: 0, size: 20 };
}

const EXPERT_A = {
    contactId: 7,
    name: "张教授",
    email: "zhang@example.edu",
    latestMessage: { subject: "Re: 合作邀请", time: "2026-10-20T09:00:00" }
};

function detailResponse(items, extra = {}) {
    return {
        items,
        nextBefore: extra.nextBefore === undefined ? null : extra.nextBefore,
        hasMore: extra.hasMore === true
    };
}

function clickAction(harness, action) {
    const node = byAction(harness.root, action);
    assert.ok(node, `action ${action} must be rendered`);
    harness.root.dispatch("click", { target: node, preventDefault() {} });
    return node;
}

// ---------------------------------------------------------------------------
describe("S-1 DOM 骨架（真实模块渲染）", () => {
    it("列表加载后按契约层级渲染搜索/列表/分页/会话容器", async () => {
        const harness = createHarness();
        harness.responder = async (requestPath) => {
            assert.ok(requestPath.startsWith(`${LIST_PATH}?`), requestPath);
            return listResponse([EXPERT_A]);
        };
        harness.mount();
        await flush();

        const root = harness.root;
        assert.strictEqual(root.tagName, "DIV");
        assert.ok(classes(root).includes("mail-chat"));
        assert.strictEqual(root.dataset.role, "readonly-mailbox");

        const aside = findOne(root, "mc-experts");
        assert.strictEqual(aside.tagName, "ASIDE");
        assert.strictEqual(aside.getAttribute("aria-label"), "已授权专家");

        const tools = findOne(aside, "mc-list-tools");
        const searchRow = findOne(tools, "mc-search-row");
        const input = searchRow.children.find((child) => child.tagName === "INPUT");
        assert.strictEqual(input.getAttribute("type"), "search");
        assert.strictEqual(input.getAttribute("aria-label"), "搜索已授权专家");
        assert.strictEqual(input.getAttribute("placeholder"), "搜索姓名或邮箱");
        assert.ok(byAction(tools, "search"));
        assert.ok(byAction(tools, "refresh"));
        assert.strictEqual(findOne(tools, "mc-note").textContent, "只读：仅显示管理员授权的专家邮件");

        const list = findOne(aside, "mc-expert-list");
        const person = findOne(list, "mc-person");
        assert.strictEqual(person.dataset.active, "false");
        const main = findOne(person, "mc-person-main");
        assert.strictEqual(main.tagName, "BUTTON");
        assert.strictEqual(main.getAttribute("type"), "button");
        assert.strictEqual(main.dataset.action, "select-expert");
        assert.strictEqual(main.dataset.contactId, "7");
        const smalls = main.children.filter((child) => child.tagName === "SMALL");
        assert.strictEqual(main.children[0].tagName, "STRONG");
        assert.strictEqual(main.children[0].textContent, "张教授");
        assert.strictEqual(smalls[0].textContent, "zhang@example.edu");
        assert.strictEqual(smalls[1].textContent, "Re: 合作邀请");

        const pager = findOne(aside, "mc-pager");
        assert.strictEqual(byAction(pager, "previous").textContent, "上一页");
        assert.strictEqual(byAction(pager, "next").textContent, "下一页");
        const live = pager.children.find((child) => child.tagName === "SPAN");
        assert.strictEqual(live.getAttribute("aria-live"), "polite");
        assert.strictEqual(live.textContent, "第 1 页，共 1 位专家");

        const conversation = findOne(root, "mc-conversation");
        assert.strictEqual(conversation.tagName, "SECTION");
        assert.strictEqual(conversation.getAttribute("aria-label"), "往来邮件");
        const header = findOne(conversation, "mc-header");
        const identity = findOne(header, "mc-identity");
        assert.strictEqual(identity.children[0].tagName, "H2");
        assert.strictEqual(identity.children[1].tagName, "P");
        assert.strictEqual(findOne(header, "mc-badge").textContent, "只读");
        const scroll = findOne(conversation, "mc-scroll");
        assert.strictEqual(findOne(scroll, "mc-empty").textContent, "请选择左侧专家");

        assert.strictEqual(harness.calls.length, 1);
        assert.strictEqual(harness.calls[0].path, `${LIST_PATH}?page=0&size=20`);
        assert.strictEqual(harness.calls[0].options.method, "GET");
    });

    it("选中专家后渲染时间线按钮与消息 article（方向/时间/主题/正文）", async () => {
        const harness = createHarness();
        harness.responder = async (requestPath) => {
            if (requestPath.startsWith(`${LIST_PATH}?`)) return listResponse([EXPERT_A]);
            return detailResponse([
                {
                    source: "INBOUND_PROCESSING",
                    id: 11,
                    direction: "INBOUND",
                    subject: "咨询合作",
                    cleanedBody: "清理后正文",
                    body: "原始正文",
                    eventAt: "2026-10-01T08:30:00"
                },
                {
                    source: "MAIL_RECORD",
                    id: 12,
                    direction: "OUTBOUND",
                    subject: "回复",
                    cleanedBody: "   ",
                    body: "原始发件正文",
                    eventAt: "2026-10-02T09:45:12"
                }
            ], { hasMore: true, nextBefore: "cursor-1" });
        };
        harness.mount();
        await flush();
        clickAction(harness, "select-expert");
        await flush();

        const detailCall = harness.calls.find((call) => call.path.includes("/messages"));
        assert.strictEqual(detailCall.path, `${LIST_PATH}/7/messages?limit=50`);

        const scroll = findOne(harness.root, "mc-scroll");
        const timeline = findOne(scroll, "mc-timeline");
        assert.ok(timeline, "timeline must be inside .mc-scroll");
        const older = findOne(timeline, "mc-load-older");
        assert.strictEqual(older.tagName, "BUTTON");
        assert.strictEqual(older.textContent, "加载更早邮件");
        assert.ok(classes(older).includes("mc-text-button"));

        const messages = findAll(timeline, "mc-message");
        assert.strictEqual(messages.length, 2);
        assert.strictEqual(messages[0].tagName, "ARTICLE");
        assert.strictEqual(messages[0].dataset.direction, "INBOUND");
        const firstHeader = messages[0].children.find((child) => child.tagName === "HEADER");
        assert.strictEqual(firstHeader.children[0].textContent, "收件");
        assert.strictEqual(firstHeader.children[1].tagName, "TIME");
        assert.strictEqual(firstHeader.children[1].textContent, "2026-10-01 08:30");
        assert.strictEqual(messages[0].children.find((child) => child.tagName === "H3").textContent, "咨询合作");
        assert.strictEqual(findOne(messages[0], "mc-body").textContent, "清理后正文");

        assert.strictEqual(messages[1].dataset.direction, "OUTBOUND");
        assert.strictEqual(messages[1].children[0].children[0].textContent, "发件");
        assert.strictEqual(findOne(messages[1], "mc-body").textContent, "原始发件正文",
            "cleanedBody 空白时回退 body（不是 undefined）");
    });
});

describe("I-4 文本渲染安全", () => {
    it("含 img/script/链接的样本只作为文本，不产生子元素或属性", async () => {
        const harness = createHarness();
        const evil = '<img src="https://evil.example/x.png" onerror="alert(1)"><script>alert(2)</script> https://evil.example/link';
        harness.responder = async (requestPath) => {
            if (requestPath.startsWith(`${LIST_PATH}?`)) {
                return listResponse([{ ...EXPERT_A, name: "<b>张</b>", latestMessage: { subject: evil } }]);
            }
            return detailResponse([{
                source: "INBOUND_PROCESSING",
                id: 21,
                direction: "INBOUND",
                subject: evil,
                cleanedBody: evil,
                body: evil,
                eventAt: "2026-10-03T10:00:00"
            }]);
        };
        harness.mount();
        await flush();
        clickAction(harness, "select-expert");
        await flush();

        const root = harness.root;
        assert.ok(!allNodes(root).some((node) => node.tagName === "IMG"), "no img element");
        assert.ok(!allNodes(root).some((node) => node.tagName === "SCRIPT"), "no script element");
        assert.ok(!allNodes(root).some((node) => node.tagName === "A"), "no anchor element");
        assert.strictEqual(allNodes(root).filter((node) => node.getAttribute("src") !== null).length, 0);

        const message = findOne(root, "mc-message");
        assert.strictEqual(message.children.find((child) => child.tagName === "H3").textContent, evil);
        assert.strictEqual(findOne(message, "mc-body").textContent, evil);
        assert.strictEqual(findOne(root, "mc-person-main").children[0].textContent, "<b>张</b>");
    });

    it("正文缺失显示空文本而不是 undefined", async () => {
        const harness = createHarness();
        harness.responder = async (requestPath) => {
            if (requestPath.startsWith(`${LIST_PATH}?`)) return listResponse([EXPERT_A]);
            return detailResponse([{
                source: "INBOUND_PROCESSING",
                id: 31,
                direction: "INBOUND",
                subject: null,
                cleanedBody: null,
                body: null,
                eventAt: "2026-10-04T10:00:00"
            }]);
        };
        harness.mount();
        await flush();
        clickAction(harness, "select-expert");
        await flush();
        const message = findOne(harness.root, "mc-message");
        assert.strictEqual(findOne(message, "mc-body").textContent, "");
        assert.strictEqual(message.children.find((child) => child.tagName === "H3").textContent, "");
    });
});

describe("I-3 失权/失败/竞态", () => {
    it("详情 403 立即清空正文并显示固定提示，cursor 同时清空", async () => {
        const harness = createHarness();
        harness.responder = async (requestPath) => {
            if (requestPath.startsWith(`${LIST_PATH}?`)) return listResponse([EXPERT_A]);
            const error = new Error("forbidden");
            error.status = 403;
            error.data = { code: "MAIL_READ_FORBIDDEN" };
            throw error;
        };
        harness.mount();
        await flush();
        clickAction(harness, "select-expert");
        await flush();

        const alertNode = findOne(harness.root, "mc-error");
        assert.ok(alertNode, "error node must exist");
        assert.strictEqual(alertNode.getAttribute("role"), "alert");
        assert.strictEqual(alertNode.textContent, "暂无权限查看该专家邮件");
        assert.strictEqual(findAll(harness.root, "mc-message").length, 0);
        assert.strictEqual(findOne(harness.root, "mc-load-older"), null);
    });

    it("更早页 403 也清空已加载正文", async () => {
        const harness = createHarness();
        let phase = 0;
        harness.responder = async (requestPath) => {
            if (requestPath.startsWith(`${LIST_PATH}?`)) return listResponse([EXPERT_A]);
            phase += 1;
            if (phase === 1) {
                return detailResponse([{
                    source: "MAIL_RECORD", id: 41, direction: "OUTBOUND",
                    subject: "第一封", cleanedBody: "正文", body: "正文", eventAt: "2026-10-02T10:00:00"
                }], { hasMore: true, nextBefore: "cursor-a" });
            }
            const error = new Error("forbidden");
            error.status = 403;
            throw error;
        };
        harness.mount();
        await flush();
        clickAction(harness, "select-expert");
        await flush();
        assert.strictEqual(findAll(harness.root, "mc-message").length, 1);

        clickAction(harness, "older");
        await flush();
        assert.strictEqual(findAll(harness.root, "mc-message").length, 0);
        assert.strictEqual(findOne(harness.root, "mc-error").textContent, "暂无权限查看该专家邮件");
    });

    it("网络错误显示固定提示且不恢复旧正文", async () => {
        const harness = createHarness();
        let phase = 0;
        harness.responder = async (requestPath) => {
            if (requestPath.startsWith(`${LIST_PATH}?`)) return listResponse([EXPERT_A]);
            phase += 1;
            if (phase === 1) {
                return detailResponse([{
                    source: "INBOUND_PROCESSING", id: 51, direction: "INBOUND",
                    subject: "旧正文", cleanedBody: "旧正文", body: "旧正文", eventAt: "2026-10-02T11:00:00"
                }]);
            }
            throw new Error("socket hang up");
        };
        harness.mount();
        await flush();
        clickAction(harness, "select-expert");
        await flush();
        assert.strictEqual(findAll(harness.root, "mc-message").length, 1);

        clickAction(harness, "refresh");
        await flush();
        assert.strictEqual(findAll(harness.root, "mc-message").length, 0);
        assert.strictEqual(findOne(harness.root, "mc-error").textContent, "加载失败，请刷新重试");
    });

    it("慢 A 响应不能覆盖已切换到 B 的正文", async () => {
        const harness = createHarness();
        const slow = deferred();
        const expertB = { contactId: 8, name: "李教授", email: "li@example.edu", latestMessage: { subject: "B" } };
        harness.responder = async (requestPath) => {
            if (requestPath.startsWith(`${LIST_PATH}?`)) return listResponse([EXPERT_A, expertB]);
            if (requestPath.includes("/7/messages")) return slow.promise;
            return detailResponse([{
                source: "INBOUND_PROCESSING", id: 61, direction: "INBOUND",
                subject: "B 的邮件", cleanedBody: "B 的正文", body: "B 的正文", eventAt: "2026-10-05T10:00:00"
            }]);
        };
        harness.mount();
        await flush();

        const mainButtons = findAll(harness.root, "mc-person-main");
        harness.root.dispatch("click", { target: mainButtons[0] });
        await flush();
        harness.root.dispatch("click", { target: findAll(harness.root, "mc-person-main")[1] });
        await flush();

        assert.strictEqual(findOne(harness.root, "mc-identity").children[0].textContent, "李教授");
        assert.strictEqual(findOne(harness.root, "mc-body").textContent, "B 的正文");

        slow.resolve(detailResponse([{
            source: "INBOUND_PROCESSING", id: 71, direction: "INBOUND",
            subject: "A 的迟到邮件", cleanedBody: "A 的正文", body: "A 的正文", eventAt: "2026-10-01T10:00:00"
        }]));
        await flush();

        assert.strictEqual(findOne(harness.root, "mc-identity").children[0].textContent, "李教授");
        assert.strictEqual(findOne(harness.root, "mc-body").textContent, "B 的正文");
        assert.strictEqual(findAll(harness.root, "mc-message").length, 1);
    });

    it("unmount 中止在途请求、清空 DOM，迟到响应不再渲染", async () => {
        const harness = createHarness();
        const slow = deferred();
        harness.responder = async (requestPath) => {
            if (requestPath.startsWith(`${LIST_PATH}?`)) return listResponse([EXPERT_A]);
            return slow.promise;
        };
        harness.mount();
        await flush();
        clickAction(harness, "select-expert");
        await flush();
        const detailCall = harness.calls.find((call) => call.path.includes("/messages"));
        assert.ok(detailCall.options.signal, "detail request must carry an abort signal");
        assert.strictEqual(detailCall.options.signal.aborted, false);

        harness.handle.unmount();
        assert.strictEqual(harness.host.children.length, 0, "unmount clears host content");
        assert.strictEqual(detailCall.options.signal.aborted, true, "unmount aborts in-flight request");

        slow.resolve(detailResponse([{
            source: "INBOUND_PROCESSING", id: 81, direction: "INBOUND",
            subject: "after", cleanedBody: "after", body: "after", eventAt: "2026-10-06T10:00:00"
        }]));
        await flush();
        assert.strictEqual(harness.host.children.length, 0);
    });

    it("重复 focus/visibility 事件合并为一组在途请求", async () => {
        const harness = createHarness();
        const gate = deferred();
        let listCalls = 0;
        harness.responder = async (requestPath) => {
            if (requestPath.startsWith(`${LIST_PATH}?`)) {
                listCalls += 1;
                if (listCalls > 1) return gate.promise;
                return listResponse([EXPERT_A]);
            }
            return detailResponse([]);
        };
        harness.mount();
        await flush();
        assert.strictEqual(listCalls, 1);

        harness.runtime.dispatchWindow("focus", {});
        harness.document.dispatch("visibilitychange", {});
        harness.runtime.dispatchWindow("focus", {});
        await flush();
        assert.strictEqual(listCalls, 2, "同时刻重复事件只产生一组请求");

        gate.resolve(listResponse([EXPERT_A]));
        await flush();
        assert.strictEqual(harness.host.children.length, 1);

        harness.handle.unmount();
        harness.runtime.dispatchWindow("focus", {});
        harness.document.dispatch("visibilitychange", {});
        await flush();
        assert.strictEqual(listCalls, 2, "unmount 后不再响应 focus/visible");
    });
});

describe("I-2 请求范围与状态隔离", () => {
    it("只发起列表/详情两条 GET；不触碰任何存储", async () => {
        const harness = createHarness();
        harness.responder = async (requestPath) => {
            if (requestPath.startsWith(`${LIST_PATH}?`)) return listResponse([EXPERT_A]);
            return detailResponse([], { hasMore: true, nextBefore: "c" });
        };
        harness.mount();
        await flush();
        clickAction(harness, "select-expert");
        await flush();
        clickAction(harness, "older");
        await flush();

        harness.calls.forEach((call) => {
            assert.strictEqual(call.options.method, "GET");
            const normalized = call.path.split("?")[0];
            assert.ok(
                normalized === LIST_PATH || /^\/api\/mail\/mailbox\/conversations\/[^/]+\/messages$/.test(normalized),
                `unexpected request ${call.path}`
            );
            assert.ok(call.options.signal, "every request carries an abort signal");
        });
        assert.ok(!/(localStorage|sessionStorage)\s*[.[]/.test(moduleSource), "module must not touch web storage");
        assert.ok(!/document\.cookie/.test(moduleSource));
    });

    it("搜索只带 q，无结果文案区分有无授权", async () => {
        const harness = createHarness();
        const requested = [];
        harness.responder = async (requestPath) => {
            requested.push(requestPath);
            if (requestPath.includes("q=")) return listResponse([]);
            return listResponse([]);
        };
        harness.mount();
        await flush();
        assert.strictEqual(findOne(harness.root, "mc-expert-list").children[0].textContent, "暂无已授权的专家邮件");

        const input = allNodes(harness.root).find((node) => node.tagName === "INPUT");
        input.value = "张 三";
        clickAction(harness, "search");
        await flush();

        const searchCall = requested[requested.length - 1];
        assert.strictEqual(searchCall, `${LIST_PATH}?page=0&size=20&q=${encodeURIComponent("张 三")}`);
        assert.strictEqual(findOne(harness.root, "mc-expert-list").children[0].textContent, "没有匹配的已授权专家");
        assert.strictEqual(findOne(harness.root, "mc-scroll").children[0].textContent, "请选择左侧专家");
    });

    it("搜索框 Enter 触发同一搜索路径", async () => {
        const harness = createHarness();
        const requested = [];
        harness.responder = async (requestPath) => {
            requested.push(requestPath);
            return listResponse([]);
        };
        harness.mount();
        await flush();
        const input = allNodes(harness.root).find((node) => node.tagName === "INPUT");
        input.value = "li";
        input.dispatch("keydown", { key: "Enter", preventDefault() {} });
        await flush();
        assert.strictEqual(requested[requested.length - 1], `${LIST_PATH}?page=0&size=20&q=li`);
    });

    it("刷新保留选中专家并重新读取列表与正文（正文先清后取）", async () => {
        const harness = createHarness();
        const gate = deferred();
        let detailCalls = 0;
        harness.responder = async (requestPath) => {
            if (requestPath.startsWith(`${LIST_PATH}?`)) return listResponse([EXPERT_A]);
            detailCalls += 1;
            if (detailCalls === 1) {
                return detailResponse([{
                    source: "INBOUND_PROCESSING", id: 91, direction: "INBOUND",
                    subject: "旧", cleanedBody: "旧正文", body: "旧正文", eventAt: "2026-10-01T10:00:00"
                }]);
            }
            return gate.promise;
        };
        harness.mount();
        await flush();
        clickAction(harness, "select-expert");
        await flush();
        assert.strictEqual(findOne(harness.root, "mc-body").textContent, "旧正文");

        clickAction(harness, "refresh");
        await flush();
        assert.strictEqual(findAll(harness.root, "mc-message").length, 0, "刷新前清正文");
        assert.strictEqual(findOne(harness.root, "mc-scroll").children[0].textContent, "加载中…");
        assert.strictEqual(findOne(harness.root, "mc-identity").children[0].textContent, "张教授");

        gate.resolve(detailResponse([{
            source: "INBOUND_PROCESSING", id: 92, direction: "INBOUND",
            subject: "新", cleanedBody: "新正文", body: "新正文", eventAt: "2026-10-07T10:00:00"
        }]));
        await flush();
        assert.strictEqual(findOne(harness.root, "mc-body").textContent, "新正文");
    });

    it("分页按钮按 total 禁用并携带 page", async () => {
        const harness = createHarness();
        const items = Array.from({ length: 20 }, (_, index) => ({
            contactId: index + 1, name: `专家${index}`, email: `e${index}@example.edu`, latestMessage: { subject: "s" }
        }));
        const requested = [];
        harness.responder = async (requestPath) => {
            requested.push(requestPath);
            const page = Number(new URL(`https://x${requestPath}`).searchParams.get("page"));
            return { items, total: 45, page, size: 20 };
        };
        harness.mount();
        await flush();

        const pager = findOne(harness.root, "mc-pager");
        assert.strictEqual(byAction(pager, "previous").disabled, true);
        assert.strictEqual(byAction(pager, "next").disabled, false);
        clickAction(harness, "next");
        await flush();
        assert.strictEqual(requested[requested.length - 1], `${LIST_PATH}?page=1&size=20`);
        assert.strictEqual(findOne(harness.root, "mc-pager").children[1].textContent, "第 2 页，共 45 位专家");
    });

    it("更早页按 source:id 去重并保持时间正序", async () => {
        const harness = createHarness();
        harness.responder = async (requestPath) => {
            if (requestPath.startsWith(`${LIST_PATH}?`)) return listResponse([EXPERT_A]);
            if (requestPath.includes("before=")) {
                assert.ok(requestPath.includes("before=cursor-1"), requestPath);
                return detailResponse([
                    { source: "MAIL_RECORD", id: 102, direction: "INBOUND", subject: "重复", cleanedBody: "重复", eventAt: "2026-10-02T10:00:00" },
                    { source: "INBOUND_PROCESSING", id: 101, direction: "INBOUND", subject: "最早", cleanedBody: "最早", eventAt: "2026-09-30T08:00:00" }
                ], { hasMore: false, nextBefore: null });
            }
            return detailResponse([
                { source: "MAIL_RECORD", id: 102, direction: "INBOUND", subject: "较新", cleanedBody: "较新", eventAt: "2026-10-02T10:00:00" },
                { source: "INBOUND_PROCESSING", id: 103, direction: "INBOUND", subject: "中间", cleanedBody: "中间", eventAt: "2026-10-01T09:00:00" }
            ], { hasMore: true, nextBefore: "cursor-1" });
        };
        harness.mount();
        await flush();
        clickAction(harness, "select-expert");
        await flush();
        clickAction(harness, "older");
        await flush();

        const times = findAll(harness.root, "mc-message").map((message) => message.children[0].children[1].textContent);
        assert.deepStrictEqual(times, ["2026-09-30 08:00", "2026-10-01 09:00", "2026-10-02 10:00"]);
        assert.strictEqual(findOne(harness.root, "mc-load-older"), null, "hasMore=false 不再显示加载更早");
    });

    it("详情无邮件显示暂无往来邮件", async () => {
        const harness = createHarness();
        harness.responder = async (requestPath) => {
            if (requestPath.startsWith(`${LIST_PATH}?`)) return listResponse([EXPERT_A]);
            return detailResponse([]);
        };
        harness.mount();
        await flush();
        clickAction(harness, "select-expert");
        await flush();
        assert.strictEqual(findOne(harness.root, "mc-timeline").children[0].textContent, "暂无往来邮件");
    });
});
