"use strict";

const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("node:assert/strict");
const { test } = require("node:test");
const ROOT = path.join(__dirname, "../../main/resources/static");
const fixtureSource = fs.readFileSync(path.join(__dirname, "mailboxChatBehavior.test.js"), "utf8");
const fixture = { require, __dirname, console, setImmediate, URL, URLSearchParams };
vm.createContext(fixture);
vm.runInContext(fixtureSource.slice(0, fixtureSource.indexOf('describe("mailbox chat mount')) + `
MiniElement.prototype.replaceChildren = function(...nodes) {
    [...this.childNodes].forEach(node => this.removeChild(node));
    nodes.forEach(node => this.appendChild(node));
};
MiniElement.prototype.replaceChild = function(node, old) {
    this.insertBefore(node, old); this.removeChild(old); return old;
};
globalThis.tools = { createChatSandbox, createDom, expertA, expertB, messagesA, contactA,
    MiniEvent, click, inputEvent, changeEvent, flush, extractFn };
`, fixture);
const tools = fixture.tools;
const readerSource = fs.readFileSync(path.join(ROOT, "mailbox-readonly.js"), "utf8");
const appSource = fs.readFileSync(path.join(ROOT, "app.js"), "utf8");

async function workspace(options = {}) {
    const levels = { 1: "FULL", 2: "READ" };
    const rows = [tools.expertA(), tools.expertB()];
    const review = { id: 41, contactId: 1, ownerUsername: "user1", state: "APPROVED", version: 2,
        subject: "Approved immutable review", html: "<p>Approved review body</p>", text: "Approved review body", commentCount: 0,
        sendStatus: "NOT_SENT", deleted: false };
    const ctx = tools.createChatSandbox({
        authMe: { authenticated: true, username: "user1" }, contact: tools.contactA(),
        route: (url, method, body, entry, next) => {
            if (options.route) {
                const result = options.route(url, method, body);
                if (result !== undefined) return result;
            }
            if (url.startsWith("/api/mail/mailbox/conversations?")) {
                const q = new URLSearchParams(url.split("?")[1]);
                const pending = q.get("pendingOnly") === "true";
                return { items: (pending ? rows.slice(0, 1) : rows).map(row => ({ ...row, accessLevel: levels[row.contactId] })),
                    total: pending ? 1 : 2 };
            }
            const messages = url.match(/conversations\/([12])\/messages/);
            if (messages) return { ...tools.messagesA(), accessLevel: levels[messages[1]] };
            if (url.startsWith("/api/mail/mailbox/review-drafts?")) return { items: [review], total: 1 };
            if (url === "/api/mail/mailbox/review-drafts/41") return review;
            if (url.startsWith("/api/mail/mailbox/review-drafts/41/events")) return [];
            return next(url, method, body);
        }
    });
    const dom = tools.createDom();
    ctx.sandbox.document = dom.doc;
    ctx.sandbox.AbortController = AbortController;
    vm.runInContext(tools.extractFn("isMailReadOnlyRequestAllowed", appSource), ctx.sandbox);
    const transport = ctx.sandbox.api;
    const rejected = [];
    ctx.sandbox.api = (url, opts) => {
        if (!ctx.sandbox.isMailReadOnlyRequestAllowed(url, opts)) {
            rejected.push(url);
            return Promise.reject(Object.assign(new Error("frontend forbidden"), { status: 403 }));
        }
        return transport(url, opts);
    };
    vm.runInContext(readerSource, ctx.sandbox);
    const handle = ctx.sandbox.MailboxReadOnly.mount(dom.host, { api: ctx.sandbox.api, sessionUser: { username: "user1" } });
    await tools.flush();
    const choose = async id => {
        tools.click(dom.host.querySelector(`[data-action="select-expert"][data-contact-id="${id}"]`));
        await tools.flush();
    };
    return { ...ctx, ...dom, handle, levels, rejected, choose };
}

const writable = h => h.host.querySelector('[data-role="mc-editor"][contenteditable="true"]');

test("READ/FULL switches use the real shared editor and exact request gate without admin bootstrap", async () => {
    const h = await workspace();
    await h.choose(1);
    assert.ok(writable(h));
    assert.equal(h.host.querySelectorAll('[data-role="manual-compose"]').length, 1);
    assert.equal(h.host.querySelector('[data-role="review-compose"]'), null);
    assert.ok(!h.host.querySelector('[data-role="delegated-conversation"] .mc-identity').textContent.includes("a@example.edu"));
    writable(h).innerText = "Only A manual content";
    tools.inputEvent(writable(h));
    await h.handle.flushDrafts();
    assert.ok(h.calls.api.some(call => call.method === "PUT" && call.url.includes("/drafts/target?contactId=1")));
    await h.choose(2);
    assert.equal(writable(h), null);
    assert.equal(h.host.querySelector('[data-role="delegated-conversation"]'), null);
    assert.ok(h.host.querySelector('[data-role="review-compose"]'));
    assert.ok(!h.host.textContent.includes("Only A manual content"));
    assert.deepEqual(h.rejected, []);
    assert.ok(!h.calls.api.some(call => /sender-accounts|task-executions|compose-templates|operator-action-logs|read-access|drafts\/summaries/.test(call.url)));
    h.handle.unmount();
});

test("focus and visibility downgrade before restoring controls, logout clears memory", async () => {
    const h = await workspace();
    await h.choose(1);
    assert.ok(writable(h));
    h.levels[1] = "READ";
    h.dispatchGlobal("focus", {});
    assert.equal(writable(h), null, "writable controls are removed synchronously before the permission request");
    await tools.flush();
    assert.equal(writable(h), null);
    h.doc.root.dispatchEvent(new tools.MiniEvent("visibilitychange"));
    await tools.flush();
    assert.equal(writable(h), null);
    assert.deepEqual(h.rejected, []);
    h.handle.unmount();
    assert.equal(h.host.textContent, "");
});

test("delayed A FULL authority after selecting B cannot mount a writable conversation", async () => {
    let resolveA;
    const slow = new Promise(resolve => { resolveA = resolve; });
    const h = await workspace({ route: url => url.includes("/conversations/1/messages?") ? slow : undefined });
    await h.choose(1);
    await h.choose(2);
    resolveA({ ...tools.messagesA(), accessLevel: "FULL" });
    await tools.flush();
    assert.equal(writable(h), null);
    assert.equal(h.host.querySelector('[data-role="delegated-conversation"]'), null);
    assert.equal(h.host.querySelector(".mc-identity h2").textContent, "专家B");
    h.handle.unmount();
});

test("FULL review history remains immutable and separate from the new manual draft", async () => {
    const h = await workspace();
    await h.choose(1);
    const selector = h.host.querySelector('[data-role="review-selection"]');
    assert.ok(selector);
    selector.value = "review:41";
    tools.changeEvent(selector);
    await tools.flush();
    assert.equal(writable(h), null);
    assert.ok(h.host.textContent.includes("Approved review body"));
    for (const action of ["send", "approve", "return", "confirm-return", "context-confirm"]) {
        assert.equal(h.host.querySelector(`[data-review-action="${action}"]`), null);
    }
    assert.equal(h.calls.sendRich.length, 0);
    assert.equal(h.calls.sendConversation.length, 0);
    const back = h.host.querySelector('[data-role="review-selection"]');
    back.value = "manual";
    tools.changeEvent(back);
    await tools.flush();
    assert.ok(writable(h));
    assert.ok(!writable(h).textContent.includes("Approved review body"));
    h.handle.unmount();
});

test("personal pending chip is a server expert count, not per-page mail count", async () => {
    const h = await workspace();
    assert.equal(h.host.querySelector('[data-role="reader-pending-experts"]').textContent, "1");
    tools.click(h.host.querySelector('[data-action="reader-pending"]'));
    await tools.flush();
    assert.equal(h.host.querySelector('[data-action="reader-pending"]').getAttribute("aria-pressed"), "true");
    assert.ok(h.calls.api.some(call => call.url === "/api/mail/mailbox/conversations?page=0&size=20&pendingOnly=true"));
    assert.ok(h.host.textContent.includes("1条待处理"));
    tools.click(h.host.querySelector('[data-action="reader-all"]'));
    await tools.flush();
    assert.equal(h.host.querySelector('[data-action="reader-all"]').getAttribute("aria-pressed"), "true");
    h.handle.unmount();
});

test("delegated expert tag host uses the real mutation helper without global admin reloads", async () => {
    const h = await workspace({ route: url => {
        if (url === "/api/experts/tags/add") return { success: true };
        if (url.startsWith("/api/experts/profile?")) return { found: true, tags: ["Workspace tag"] };
    } });
    h.sandbox.loadContacts = () => { throw new Error("global contact preload"); };
    h.sandbox.loadExpertTagOptions = () => { throw new Error("global tag preload"); };
    h.sandbox.openExpertTagAddDialog = () => Promise.resolve("Workspace tag");
    for (const name of ["fetchExpertTagsFromEs", "refreshExpertTagsFromEs", "mutateExpertTag"]) {
        vm.runInContext(tools.extractFn(name, appSource), h.sandbox);
    }
    await h.choose(1);
    tools.click(h.host.querySelector('[data-action="mc-open-expert"]'));
    await tools.flush();
    tools.click(h.doc.root.querySelector('[data-action="expert-add-tag-open"]'));
    await tools.flush();
    assert.ok(h.calls.api.some(call => call.url === "/api/experts/tags/add" && call.method === "POST"));
    assert.ok(h.calls.tagEditorUpdates.some(update => update.tags.includes("Workspace tag")));
    assert.equal(h.calls.tagEditorLoadings.at(-1).loading, false);
    assert.deepEqual(h.rejected, []);
    h.handle.unmount();
});

test("tag dialog completing after delegated unmount never mutates the old expert", async () => {
    const h = await workspace();
    let finish;
    h.sandbox.openExpertTagAddDialog = () => new Promise(resolve => { finish = resolve; });
    await h.choose(1);
    tools.click(h.host.querySelector('[data-action="mc-open-expert"]'));
    await tools.flush();
    tools.click(h.doc.root.querySelector('[data-action="expert-add-tag-open"]'));
    await tools.flush();
    assert.equal(typeof finish, "function");
    h.handle.unmount();
    finish("Too late");
    await tools.flush();
    assert.equal(h.calls.tagMutations.length, 0);
    assert.equal(h.host.textContent, "");
});

test("admin tag mutation retains the existing global refresh behavior", async () => {
    const calls = [];
    const context = { URLSearchParams, $: selector => ({ value: selector === "#contactStatusFilter" ? "CONTACTED" : "" }),
        api: async url => url.startsWith("/api/experts/profile?") ? { tags: ["Admin tag"] } : { success: true },
        loadContacts: async () => { calls.push("contacts"); },
        loadExpertTagOptions: async (level, options) => { calls.push({ level, filters: options.filters }); } };
    vm.createContext(context);
    for (const name of ["fetchExpertTagsFromEs", "refreshExpertTagsFromEs", "mutateExpertTag"]) {
        vm.runInContext(tools.extractFn(name, appSource), context);
    }
    assert.deepEqual(Array.from(await context.mutateExpertTag("admin-expert", "CANDIDATE", "Admin tag", "add")), ["Admin tag"]);
    assert.equal(calls[0], "contacts");
    assert.deepEqual(JSON.parse(JSON.stringify(calls[1])), { level: "CANDIDATE", filters: { operatorStatus: "CONTACTED", emailDomain: "", region: "" } });
});

test("precise frontend method/path pairs do not permit global, malformed, or approval endpoints", () => {
    const context = {};
    vm.createContext(context);
    vm.runInContext(tools.extractFn("isMailReadOnlyRequestAllowed", appSource), context);
    const allowed = context.isMailReadOnlyRequestAllowed;
    for (const [method, url] of [
        ["PUT", "/api/mail/mailbox/drafts/target?contactId=1"],
        ["POST", "/api/mail/mailbox/conversations/1/manual-rich-reply"],
        ["PATCH", "/api/experts/manual-profile"],
        ["POST", "/api/meeting-calendar/events"],
        ["POST", "/api/expert-contacts/1/materials/uploads"],
        ["GET", "/api/mail/mailbox/attachments/1/download"]
    ]) assert.equal(allowed(url, { method }), true, method + " " + url);
    for (const url of ["/api/mail/sender-accounts", "/api/task-executions", "/api/experts", "/api/expert-contacts",
        "/api/mail/mailbox/reply-drafts/1", "/api/mail/mailbox/conversations/01/manual-rich-reply",
        "/api/mail/mailbox/conversations/1/manual-rich-reply/", "/api/mail/mailbox/conversations/1/read-access/user1",
        "/api/mail/mailbox/review-drafts/41/approve", "/api/mail/mailbox/review-drafts/41/send"]) {
        for (const method of ["GET", "POST", "PUT", "PATCH", "DELETE"]) assert.equal(allowed(url, { method }), false, method + " " + url);
    }
    assert.equal(allowed("/api/mail/mailbox/conversations/1/manual-rich-reply", { method: "GET" }), false);
    assert.equal(allowed("/api/experts/manual-profile", { method: "POST" }), false);
});

function calendarFixture() {
    const calls = { contacts: 0, create: [], cancel: [], changed: [], close: 0, bind: 0 };
    const fields = Object.fromEntries(Object.entries({
        contactId: "999", startBeijing: "2026-11-01T10:00", endBeijing: "2026-11-01T11:00",
        meetingLink: "", note: "", cancelReason: ""
    }).map(([key, value]) => [key, { value, focus() {} }]));
    const dialog = { showModal() {}, close() { calls.close++; } };
    const context = {
        Promise, Number, Object, String, document: { activeElement: null },
        meetingCalendarState: { dialog: {} }, meetingCalendarDialogEl: () => dialog,
        meetingCalendarFormEl: () => ({ elements: fields }), meetingCalendarField: key => fields[key],
        ensureMeetingCalendarBound: () => { calls.bind++; },
        fillMeetingCalendarDialogFields() {}, setMeetingCalendarDialogError() {}, applyMeetingCalendarDialogMode() {},
        loadMeetingCalendarContacts: () => { calls.contacts++; return Promise.resolve([]); },
        renderMeetingCalendarContactOptions() {}, showStatus() {},
        meetingCalendarEventExpertLabel: () => "Expert A", meetingCalendarIsCancelled: () => false,
        meetingCalendarBeijingTextToInstant: value => new Date(value + "+08:00"), meetingCalendarSafeLink: () => true,
        meetingCalendarCreateEvent: body => { calls.create.push(body); return Promise.resolve({ id: 8 }); },
        meetingCalendarUpdateEvent: () => Promise.resolve({}),
        mcHostMeetingScheduleChanged: id => calls.changed.push(id),
        meetingCalendarFetchExpertEvents: () => Promise.resolve([]),
        meetingCalendarFetchEvent: () => Promise.resolve({ updatedAt: "v2" }),
        meetingCalendarCancelEvent: (id, body) => { calls.cancel.push({ id, body }); return Promise.resolve({}); }
    };
    vm.createContext(context);
    for (const name of ["openMeetingCalendarDialog", "closeMeetingCalendarDialog", "meetingCalendarDialogReadPayload",
        "submitMeetingCalendarDialog", "confirmMeetingCalendarCancel", "mcHostOpenMeetingSchedule"]) {
        vm.runInContext(tools.extractFn(name, appSource), context);
    }
    return { context, calls, fields };
}

test("delegated calendar binds the existing form and ignores the global contact picker on submit", async () => {
    const h = calendarFixture();
    await h.context.mcHostOpenMeetingSchedule(1, { delegated: true, isCurrent: () => true });
    assert.equal(h.calls.bind, 1);
    assert.equal(h.calls.contacts, 0);
    h.context.submitMeetingCalendarDialog();
    await tools.flush();
    assert.equal(h.calls.create.length, 1);
    assert.equal(h.calls.create[0].contactId, 1, "hidden stale global select cannot choose another expert");
    assert.deepEqual(h.calls.changed, [1]);
    assert.equal(h.calls.close, 1);
    const html = fs.readFileSync(path.join(ROOT, "index.html"), "utf8");
    assert.ok(html.includes('id="meetingCalendarForm"'));
    assert.ok(html.includes('data-role="expert-picker"'));
});

test("delegated calendar disables the hidden required contact select and restores it for admin", () => {
    const h = calendarFixture();
    const nodes = new Map();
    const node = selector => {
        if (!nodes.has(selector)) nodes.set(selector, {});
        return nodes.get(selector);
    };
    const dialog = { querySelector: node };
    h.context.meetingCalendarDialogEl = () => dialog;
    h.context.meetingCalendarFormEl = () => ({ querySelector: node });
    h.context.meetingCalendarFieldLabel = node;
    h.context.meetingCalendarSetHidden = (element, hidden) => { element.hidden = hidden; };
    h.context.meetingCalendarDialogTitle = () => "Schedule";
    h.context.refreshMeetingCalendarDialogTimeSummary = () => {};
    h.context.renderMeetingCalendarDialogSourceSummary = () => {};
    vm.runInContext(tools.extractFn("applyMeetingCalendarDialogMode", appSource), h.context);
    h.fields.contactId.value = "";
    h.fields.contactId.required = true;
    h.context.meetingCalendarState.dialog = { mode: "create", delegated: true };
    h.context.applyMeetingCalendarDialogMode();
    assert.equal(h.fields.contactId.disabled, true, "hidden required select must not participate in native form validation");
    assert.equal(node('[data-role="expert-picker"]').hidden, true);
    h.context.meetingCalendarState.dialog.delegated = false;
    h.context.applyMeetingCalendarDialogMode();
    assert.equal(h.fields.contactId.disabled, false);
    assert.equal(node('[data-role="expert-picker"]').hidden, false);
    const html = fs.readFileSync(path.join(ROOT, "index.html"), "utf8");
    assert.match(html, /<select name="contactId" required>/);
});

test("delegated calendar late save cannot close a replacement context or refresh its expert", async () => {
    const h = calendarFixture();
    let finish;
    h.context.meetingCalendarCreateEvent = () => new Promise(resolve => { finish = resolve; });
    let current = true;
    await h.context.mcHostOpenMeetingSchedule(1, { delegated: true, isCurrent: () => current });
    h.context.submitMeetingCalendarDialog();
    current = false;
    h.context.closeMeetingCalendarDialog({ restoreFocus: false });
    await h.context.mcHostOpenMeetingSchedule(2, { delegated: true, isCurrent: () => true });
    finish({});
    await tools.flush();
    assert.equal(h.context.meetingCalendarState.dialog.contactId, 2);
    assert.equal(h.context.meetingCalendarState.dialog.open, true);
    assert.equal(h.calls.close, 1);
    assert.deepEqual(h.calls.changed, []);
});

test("delegated calendar revoke during cancel version fetch prevents the subsequent write", async () => {
    const h = calendarFixture();
    let finish;
    let current = true;
    h.context.meetingCalendarFetchEvent = () => new Promise(resolve => { finish = resolve; });
    h.context.openMeetingCalendarDialog({ mode: "cancel", contactId: 1, delegated: true, isCurrent: () => current,
        event: { id: 8, contactId: 1, updatedAt: "v1" } });
    h.context.confirmMeetingCalendarCancel();
    current = false;
    h.context.closeMeetingCalendarDialog({ restoreFocus: false });
    finish({ updatedAt: "v2" });
    await tools.flush();
    assert.deepEqual(h.calls.cancel, []);
    assert.deepEqual(h.calls.changed, []);
});
