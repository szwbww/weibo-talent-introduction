"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const root = path.join(__dirname, "../../main/resources/static");
const source = fs.readFileSync(path.join(root, "console-shell.js"), "utf8");

function harness(reduced = false) {
    const timers = new Map();
    let id = 0;
    const doc = { querySelector: () => null, getElementById: () => null };
    doc.createElement = () => {
        const attrs = new Map();
        return {
            ownerDocument: doc, children: [], isConnected: true, inert: false,
            classList: { add() {} },
            setAttribute: (k, v) => attrs.set(k, v), getAttribute: k => attrs.get(k) ?? null,
            removeAttribute: k => attrs.delete(k),
            appendChild(child) { this.children.push(child); child.parentNode = this; },
            removeChild(child) { this.children.splice(this.children.indexOf(child), 1); child.parentNode = null; },
            animate() { this.animationCount = (this.animationCount || 0) + 1; return { cancel() {} }; }
        };
    };
    const runtime = { document: doc, matchMedia: () => ({matches: reduced}),
        setTimeout(fn) { timers.set(++id, fn); return id; }, clearTimeout(id) { timers.delete(id); } };
    runtime.window = runtime;
    vm.runInNewContext(source, runtime);
    const container = doc.createElement(), content = doc.createElement();
    container.appendChild(content);
    return { motion: runtime.ConsoleMotion, container, content, tick() { const tasks = [...timers.values()]; timers.clear(); tasks.forEach(fn => fn()); }, timers };
}

test("fast loads unblock immediately and never flash an overlay", () => {
    const h = harness();
    const finish = h.motion.begin(h.container, h.content);
    assert.equal(h.content.inert, true);
    assert.equal(h.container.getAttribute("aria-busy"), "true");
    finish(); h.tick();
    assert.equal(h.container.children.length, 1);
    assert.equal(h.content.inert, false);
    assert.equal(h.container.getAttribute("aria-busy"), null);
    assert.equal(h.content.animationCount, 1);
});

test("a slow load exposes status; stale completion cannot clear a newer load", () => {
    const h = harness();
    const old = h.motion.begin(h.container, h.content, "正在加载会话…");
    h.tick();
    assert.equal(h.container.children[1].getAttribute("role"), "status");
    const current = h.motion.begin(h.container, h.content);
    old(); h.tick();
    assert.equal(h.container.getAttribute("aria-busy"), "true");
    assert.equal(h.content.inert, true);
    assert.equal(h.container.children.length, 2);
    current();
    assert.equal(h.container.children.length, 1);
    assert.equal(h.content.inert, false);
});

test("failed requests release the cover and preserve pre-existing inert state", async () => {
    const h = harness(); h.content.inert = true;
    const finish = h.motion.begin(h.container, h.content); h.tick();
    await assert.rejects(Promise.reject(new Error("offline")).finally(finish), /offline/);
    assert.equal(h.container.children.length, 1);
    assert.equal(h.content.inert, true);
});

test("leaving or logging out cancels pending covers and ignores late responses", () => {
    const h = harness();
    const finish = h.motion.begin(h.container, null); h.tick();
    h.motion.cancelAll(); finish(); h.tick();
    assert.equal(h.container.children.length, 1);
    assert.equal(h.content.inert, false);
    assert.equal(h.timers.size, 0);
    assert.equal(h.content.animationCount, undefined);
});

test("reduced motion keeps loading feedback but disables content movement", () => {
    const h = harness(true); const finish = h.motion.begin(h.container, h.content); h.tick();
    assert.equal(h.container.children.length, 2); finish();
    assert.equal(h.content.animationCount, undefined);
});

test("the initial shell and controller select the mailbox, with a compact header", () => {
    const html = fs.readFileSync(path.join(root, "index.html"), "utf8");
    const app = fs.readFileSync(path.join(root, "app.js"), "utf8");
    assert.match(html, /class="nav-tab active" data-view="mailbox"/);
    assert.match(html, /class="view active" id="view-mailbox"/);
    assert.doesNotMatch(html, /class="view active" id="view-accounts"/);
    assert.match(app, /const state = \{\s*lastEmailProvidersLevel: null,\s*view: "mailbox"/);
    assert.ok(html.indexOf('src="console-shell.js') < html.indexOf('src="app.js'));
    const css = fs.readFileSync(path.join(root, "console-shell.css"), "utf8");
    assert.match(css, /\.mc-header \.mc-identity>\.mailbox-reply-detail[^{]+\.mc-reader-schedule\{display:none!important\}/);
    assert.match(css, /prefers-reduced-motion:reduce/);
});

test("view refresh holds the real loading cover until request completion or error", async () => {
    const app = fs.readFileSync(path.join(root, 'app.js'), 'utf8');
    const fn = app.match(/async function refreshCurrentView\(\) \{[\s\S]*?\n\}/)[0];
    const h = harness();
    let rejectRequest;
    const request = new Promise((resolve, reject) => { rejectRequest = reject; });
    const statuses = [];
    const context = {
        window: {ConsoleMotion: h.motion}, state: {view: 'mailbox'}, $: () => h.container,
        loadMailbox: () => request, refreshAutoReplySummary: async () => {}, refreshMailboxGroupPush: async () => {},
        showStatus: message => statuses.push(message)
    };
    const done = vm.runInNewContext(fn + '\nrefreshCurrentView();', context);
    h.tick();
    assert.equal(h.container.getAttribute('aria-busy'), 'true');
    assert.equal(h.container.children.length, 2);
    rejectRequest(new Error('offline')); await done;
    assert.equal(h.container.children.length, 1);
    assert.equal(h.container.getAttribute('aria-busy'), null);
    assert.deepEqual(statuses, ['offline']);
});
