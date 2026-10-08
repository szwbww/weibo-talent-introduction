const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("node:assert/strict");
const { test } = require("node:test");

const root = path.join(__dirname, "../../main/resources/static");
const app = fs.readFileSync(path.join(root, "app.js"), "utf8");
const html = fs.readFileSync(path.join(root, "index.html"), "utf8");
const css = fs.readFileSync(path.join(root, "styles.css"), "utf8");
const plan = fs.readFileSync(path.join(__dirname, "../../../docs/plans/2026-10-06/wecom-inbound-notification-02-ui.md"), "utf8");
const feature = app.slice(app.indexOf("const mailboxGroupPushState ="), app.indexOf("async function refreshAutoReplySummary()"));
const endpoint = "/api/expert-inbound-notifications/settings";

function target() {
    return {
        disabled: false, textContent: "", attrs: {}, listeners: {},
        setAttribute(key, value) { this.attrs[key] = value; },
        addEventListener(event, fn) { (this.listeners[event] ||= []).push(fn); },
        async emit(event) { for (const fn of this.listeners[event] || []) await fn(); }
    };
}
function harness() {
    const nodes = Object.fromEntries(["mailboxGroupPushBtn", "mailboxGroupPushState", "mailboxRefreshBtn", "logoutBtn"].map(id => [id, target()]));
    const document = target();
    document.hidden = false;
    const calls = [], statuses = [], responses = [];
    const context = vm.createContext({
        state: { view: "mailbox", mailbox: { page: 3, selectedExpert: 42, keyword: "keep" } },
        appStarted: true, document,
        $: selector => nodes[selector.slice(1)],
        api: async (url, options = {}) => {
            calls.push({ url, options });
            assert.equal(url, endpoint, "no auto-reply/bulk/contact endpoint may be called");
            assert.ok(responses.length, "every request must have an explicit response");
            const response = responses.shift();
            if (response instanceof Error) throw response;
            return await response;
        },
        showStatus: (message, type) => statuses.push({ message, type }),
        loadContacts: () => assert.fail("switch must not reload contacts"),
        loadMailbox: () => assert.fail("switch must not reload mailbox"),
        localStorage: { getItem: () => assert.fail("no local facts"), setItem: () => assert.fail("no local facts") }
    });
    vm.runInContext(feature, context);
    context.initMailboxGroupPush();
    return {
        context, nodes, document, calls, statuses, responses,
        push: vm.runInContext("mailboxGroupPushState", context),
        btn: nodes.mailboxGroupPushBtn, label: nodes.mailboxGroupPushState,
        async load(enabled = false, configured = true) {
            responses.push({ enabled, configured });
            await context.refreshMailboxGroupPush();
        }
    };
}
function deferred() {
    let resolve, reject;
    const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
    return { promise, resolve, reject };
}
const tick = () => new Promise(resolve => setImmediate(resolve));

test("I-4/S-1: actual HTML declares accessible native switch directly after auto-reply", () => {
    const panel = html.slice(html.indexOf('<section class="panel" id="mailboxConversationPanel">'), html.indexOf('<div class="mailbox-list"'));
    const tag = panel.match(/<button class="button" id="mailboxGroupPushBtn"[\s\S]*?<\/button>/)?.[0];
    assert.ok(tag, "actual HTML must contain mailboxGroupPushBtn, not just a DOM stub");
    assert.match(tag, /id="mailboxGroupPushState">加载中…<\/span>/);
    assert.match(tag, /type="button"/);
    assert.match(tag, /role="switch" aria-checked="false" aria-label="企业微信群消息推送" disabled/);
    assert.match(tag, /<span>群消息<\/span>/);
    assert.match(tag, /class="mailbox-push-track" aria-hidden="true"/);
    assert.doesNotMatch(tag, /style=|onclick=/);
    assert.match(panel, /id="bulkAutoReplyBtn"[^>]*>[^<]*<\/button>\s*<button class="button" id="mailboxGroupPushBtn"/);
    assert.ok(panel.indexOf('id="checkRepliesBtn"') < panel.indexOf('id="bulkOutreachBtn"'));
    assert.ok(panel.indexOf('id="bulkOutreachBtn"') < panel.indexOf('id="bulkAutoReplyBtn"'));
});

test("S-1/S-2: both approved CSS blocks remain byte-for-byte exact", () => {
    const blocks = [...plan.matchAll(/```css\n([\s\S]*?)\n```/g)].map(match => match[1]);
    assert.equal(blocks.length, 3);
    for (const block of blocks.slice(0, 2)) assert.ok(css.includes(block), "approved local CSS must match verbatim");
});

test("I-4: every existing versioned resource shares the approved new key", () => {
    const keys = [...html.matchAll(/\?v=([^"']+)/g)].map(match => match[1]);
    assert.equal(keys.length, 11);
    assert.deepEqual([...new Set(keys)], [plan.match(/统一 bump 为 `([^`]+)`/)[1]]);
    assert.doesNotMatch(html, /20261006-discovery-review-merge/);
    assert.doesNotMatch(feature, /webhook|localStorage/i);
});

test("I-1: initialization registers once without GET or PUT; first load only GETs", async () => {
    const h = harness();
    h.context.initMailboxGroupPush();
    assert.equal(h.calls.length, 0);
    assert.equal(h.label.textContent, "加载中…");
    assert.equal(h.btn.disabled, true);
    assert.equal(h.btn.attrs["aria-checked"], "false");
    for (const [node, event] of [[h.btn, "click"], [h.nodes.mailboxRefreshBtn, "click"], [h.nodes.logoutBtn, "click"], [h.document, "visibilitychange"]]) {
        assert.equal(node.listeners[event].length, 1);
    }
    await h.load(true);
    assert.equal(h.calls.length, 1);
    assert.equal(h.calls[0].options.method, undefined);
    assert.equal(h.calls[0].options.cache, "no-store");
    assert.equal(h.label.textContent, "已开启");
    assert.equal(h.btn.attrs["aria-checked"], "true");
});

test("I-1: refresh disables switch but preserves last confirmed aria value", async () => {
    const h = harness();
    await h.load(true);
    const pending = deferred();
    h.responses.push(pending.promise);
    const request = h.context.refreshMailboxGroupPush();
    assert.equal(h.label.textContent, "加载中…");
    assert.equal(h.btn.disabled, true);
    assert.equal(h.btn.attrs["aria-checked"], "true");
    await h.btn.emit("click");
    assert.equal(h.calls.length, 2);
    pending.resolve({ enabled: false, configured: true });
    await request;
    assert.equal(h.label.textContent, "已关闭");
});

test("I-1: disabled/unconfigured cannot enable, enabled/unconfigured may close", async () => {
    const h = harness();
    await h.load(false, false);
    assert.equal(h.label.textContent, "未配置");
    assert.equal(h.btn.disabled, true);
    await h.context.saveMailboxGroupPush();
    assert.equal(h.calls.length, 1);
    await h.load(true, false);
    assert.equal(h.label.textContent, "已开启");
    assert.equal(h.btn.disabled, false);
    h.responses.push({ enabled: false, configured: false });
    await h.btn.emit("click");
    assert.deepEqual(JSON.parse(h.calls[2].options.body), { enabled: false });
    assert.equal(h.label.textContent, "未配置");
});

test("I-1: GET failure is retry-only, never a toggle", async () => {
    const h = harness();
    h.responses.push(new Error("offline"));
    await h.context.refreshMailboxGroupPush();
    assert.equal(h.label.textContent, "加载失败");
    assert.equal(h.btn.disabled, false);
    h.responses.push({ enabled: true, configured: true });
    await h.btn.emit("click");
    assert.equal(h.calls.length, 2);
    assert.ok(h.calls.every(call => !call.options.method));
    assert.equal(h.label.textContent, "已开启");
});

test("I-2: double-click emits one explicit PUT; save awaits server response", async () => {
    const h = harness();
    await h.load();
    const pending = deferred();
    h.responses.push(pending.promise);
    const save = h.btn.emit("click");
    assert.equal(h.label.textContent, "保存中…");
    assert.equal(h.btn.disabled, true);
    assert.equal(h.btn.attrs["aria-checked"], "false");
    await h.btn.emit("click");
    assert.equal(h.calls.length, 2);
    assert.equal(h.calls[1].options.method, "PUT");
    assert.deepEqual(JSON.parse(h.calls[1].options.body), { enabled: true });
    assert.equal(h.calls[1].options.timeoutMs, 10000);
    pending.resolve({ enabled: true, configured: true });
    await save;
    assert.equal(h.label.textContent, "已开启");
    assert.equal(h.btn.attrs["aria-checked"], "true");
    assert.equal(h.btn.disabled, false);
    assert.equal(h.statuses.at(-1).type, "ok");
});

test("I-2: successful PUT displays response, not intended target", async () => {
    const h = harness();
    await h.load();
    h.responses.push({ enabled: false, configured: true });
    await h.btn.emit("click");
    assert.equal(h.label.textContent, "已关闭");
    assert.equal(h.btn.attrs["aria-checked"], "false");
});

test("I-2: failed PUT restores confirmed value while rechecking, then adopts GET", async () => {
    const h = harness();
    await h.load(true);
    const reconciliation = deferred();
    h.responses.push(new Error("save failed"), reconciliation.promise);
    const save = h.btn.emit("click");
    await tick();
    assert.equal(h.calls.at(-1).options.method, undefined);
    assert.equal(h.label.textContent, "加载中…");
    assert.equal(h.btn.attrs["aria-checked"], "true");
    assert.ok(h.statuses.some(status => status.type === "error" && status.message.includes("保存失败")));
    reconciliation.resolve({ enabled: false, configured: true });
    await save;
    assert.equal(h.label.textContent, "已关闭");
});

test("I-2: timeout plus failed reconciliation is unknown; repeated retries remain GET-only", async () => {
    const h = harness();
    await h.load();
    h.responses.push(new Error("请求超时（10 秒）"), new Error("offline"));
    await h.btn.emit("click");
    assert.equal(h.label.textContent, "状态未知");
    assert.equal(h.btn.attrs["aria-checked"], "false");
    assert.equal(h.btn.disabled, false);
    h.responses.push(new Error("still offline"));
    await h.btn.emit("click");
    assert.equal(h.label.textContent, "状态未知");
    h.responses.push({ enabled: true, configured: true });
    await h.btn.emit("click");
    assert.equal(h.label.textContent, "已开启");
    assert.equal(h.calls.filter(call => call.options.method === "PUT").length, 1);
});

test("I-2: overlapping reconciliation refreshes cannot forget uncertain write", async () => {
    const h = harness();
    await h.load();
    const old = deferred();
    h.responses.push(new Error("timeout"), old.promise);
    const save = h.btn.emit("click");
    await tick();
    h.responses.push(new Error("offline"));
    await h.context.refreshMailboxGroupPush();
    assert.equal(h.label.textContent, "状态未知");
    old.resolve({ enabled: true, configured: true });
    await save;
    assert.equal(h.label.textContent, "状态未知");
});

test("I-2: older GET cannot replace newer GET then PUT", async () => {
    const h = harness();
    const old = deferred();
    h.responses.push(old.promise);
    const oldGet = h.context.refreshMailboxGroupPush();
    await h.load();
    h.responses.push({ enabled: true, configured: true });
    await h.btn.emit("click");
    old.resolve({ enabled: false, configured: false });
    await oldGet;
    assert.equal(h.label.textContent, "已开启");
    assert.equal(h.btn.disabled, false);
});

test("I-2: manual/visibility refresh during PUT queues only one post-save GET", async () => {
    const h = harness();
    await h.load();
    const pending = deferred();
    h.responses.push(pending.promise, { enabled: true, configured: true });
    const save = h.btn.emit("click");
    await h.nodes.mailboxRefreshBtn.emit("click");
    await h.document.emit("visibilitychange");
    assert.equal(h.calls.length, 2);
    assert.equal(h.label.textContent, "保存中…");
    pending.resolve({ enabled: true, configured: true });
    await save;
    assert.equal(h.calls.length, 3);
    assert.equal(h.calls[2].options.method, undefined);
});

test("I-1/I-3: moved refresh node and foreground visibility each GET without altering selection", async () => {
    const h = harness();
    const snapshot = JSON.stringify(h.context.state.mailbox);
    h.context.initMailboxGroupPush(); // component mount/unmount keeps this same node
    h.responses.push({ enabled: false, configured: true });
    await h.nodes.mailboxRefreshBtn.emit("click");
    h.document.hidden = true;
    await h.document.emit("visibilitychange");
    assert.equal(h.calls.length, 1);
    h.document.hidden = false;
    h.responses.push({ enabled: true, configured: true });
    await h.document.emit("visibilitychange");
    await tick();
    assert.equal(h.calls.length, 2);
    assert.equal(h.label.textContent, "已开启");
    assert.equal(JSON.stringify(h.context.state.mailbox), snapshot);
    h.context.state.view = "accounts";
    await h.document.emit("visibilitychange");
    await h.nodes.mailboxRefreshBtn.emit("click");
    assert.equal(h.calls.length, 2);
});

test("I-3: leaving view invalidates late GET; reentry GET is independent", async () => {
    const h = harness();
    const late = deferred();
    h.responses.push(late.promise);
    const request = h.context.refreshMailboxGroupPush();
    h.context.state.view = "accounts";
    h.context.invalidateMailboxGroupPush();
    late.resolve({ enabled: true, configured: true });
    await request;
    assert.equal(h.label.textContent, "加载中…");
    assert.equal(h.push.confirmedEnabled, null);
    h.context.state.view = "mailbox";
    await h.load(false);
    assert.equal(h.label.textContent, "已关闭");
    assert.match(app, /if \(view !== "mailbox" && typeof invalidateMailboxGroupPush === "function"\) invalidateMailboxGroupPush\(\);/);
    assert.match(app, /refreshAutoReplySummary\(\)\.catch\(\(\) => \{\}\),\s*refreshMailboxGroupPush\(\)/);
});

test("I-3: logout invalidates late PUT and blocks updates/statuses/reconciliation", async () => {
    const h = harness();
    await h.load();
    const late = deferred();
    h.responses.push(late.promise);
    const request = h.btn.emit("click");
    await h.nodes.logoutBtn.emit("click");
    h.context.appStarted = false;
    late.reject(new Error("late failure"));
    await request;
    assert.equal(h.push.confirmedEnabled, null);
    assert.equal(h.statuses.length, 0);
    assert.equal(h.calls.length, 2);
    await h.document.emit("visibilitychange");
    assert.equal(h.calls.length, 2);
    assert.match(app.slice(app.indexOf("function stopAuthenticatedApp()"), app.indexOf("window.stopAuthenticatedApp")), /invalidateMailboxGroupPush\(\)/);
    assert.match(app, /initMailboxGroupPush\(\);\s*bootstrap\(\);/);
});
