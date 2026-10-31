"use strict";
const fs = require("fs"), path = require("path"), vm = require("vm");
const assert = require("node:assert/strict"), { test } = require("node:test");
const harnessSource = fs.readFileSync(path.join(__dirname, "mailboxChatBehavior.test.js"), "utf8");
const sandbox = { require, __dirname, console, URL, URLSearchParams, setImmediate };
vm.createContext(sandbox);
vm.runInContext(harnessSource.slice(0, harnessSource.indexOf('describe("mailbox chat mount')) +
    '\nglobalThis.H = { bootChat, flush, click, changeEvent, inputEvent, setEditorContent, expertA, expertB, messagesA, contactA, createDraftServer, chipButton, MiniEvent };', sandbox);
const H = sandbox.H, ROOT = path.join(__dirname, "../../main/resources/static");
const BASE = "/api/mail/mailbox/review-drafts";
const clone = x => JSON.parse(JSON.stringify(x));
const role = (h, name) => h.host.querySelector('[data-role="' + name + '"]');
const action = (h, name) => h.host.querySelector('[data-review-action="' + name + '"]');
const editor = h => role(h, "mc-editor");
const subject = h => h.host.querySelector('input[aria-label="回复主题"]');
function draft(id, contactId = 1, extra = {}) { return Object.assign({ id, contactId, version: 2, ownerUsername: "user1", state: "SUBMITTED", subject: "Review " + id, html: "<p>User body " + id + "</p>", text: "User body " + id, deleted: false, sendStatus: "NOT_SENT", commentCount: 0, currentContextToken: "initial", approvedContextToken: null }, extra); }
function deferred() { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b; }); return { promise, resolve, reject }; }
async function setup(extra = {}, mount = {}) {
    const rows = extra.rows || [draft(11), draft(12), draft(21, 2)];
    const state = { rows, token: "initial", hook: null, summaryFail: false, confirmations: [], sends: [], events: [], ...extra };
    const h = await H.bootChat({ draftServer: state.draftServer, authMe: state.authMe, contact: H.contactA(), messages: H.messagesA(), route(url, method, raw, entry, next) {
        const body = raw ? JSON.parse(raw) : null;
        if (state.hook) { const value = state.hook(url, method, body); if (value !== undefined) return value; }
        if (url.startsWith("/api/mail/mailbox/conversations?")) {
            const q = new URL(url, "http://test").searchParams;
            if (state.summaryFail) return Promise.reject(Error("summary down"));
            const counts = id => rows.filter(x => x.contactId === id && !x.deleted && x.state === "SUBMITTED").length;
            let items = [H.expertA({ reviewPendingCount: counts(1) }), H.expertB({ reviewPendingCount: counts(2) })];
            if (q.get("expertScope") === "PENDING_REVIEW") items = items.filter(x => x.reviewPendingCount > 0);
            return Promise.resolve({ items, total: items.length, reviewPendingTotal: counts(1) + counts(2),
                filterCounts: { user1FullExpertCount: 0, pendingReviewDraftCount: counts(1) + counts(2) } });
        }
        if (url.startsWith(BASE + "?")) {
            const q = new URL(url, "http://test").searchParams, page = Number(q.get("page")), size = Number(q.get("size"));
            const items = rows.filter(x => x.contactId === Number(q.get("contactId")));
            return Promise.resolve({ items: clone(items.slice(page * size, (page + 1) * size)), total: items.length });
        }
        const m = url.match(/^\/api\/mail\/mailbox\/review-drafts\/(\d+)(?:\/([^?]+))?/);
        if (!m) return next(url, method, raw, entry);
        const row = rows.find(x => x.id === Number(m[1])), suffix = m[2];
        if (!row) return Promise.reject(Error("missing draft"));
        if (!suffix) return Promise.resolve(clone(row));
        if (suffix === "context") return Promise.resolve({ currentToken: state.token, approvedToken: row.approvedContextToken, changed: row.approvedContextToken !== state.token });
        if (suffix === "approve") { row.state = "APPROVED"; row.version++; row.approvedContextToken = state.token; return Promise.resolve(clone(row)); }
        if (suffix === "return") { row.state = "RETURNED"; row.version++; row.returnReason = body.reason; return Promise.resolve(clone(row)); }
        if (suffix === "confirm-context") { state.confirmations.push(body); return Promise.resolve({ confirmationEventId: 101, currentToken: state.token }); }
        if (suffix === "send") { state.sends.push(body); row.sendStatus = "SENT"; return Promise.resolve({ sent: true }); }
        if (suffix === "translate") return Promise.resolve({ ok: true, translatedText: "译文 " + row.id });
        if (suffix === "comments") { row.commentCount++; state.events.unshift({ id: 31, actorUsername: "admin", eventType: "COMMENT", createdAt: "now", payload: { text: body.text } }); return Promise.resolve(state.events[0]); }
        if (suffix === "events") return Promise.resolve(clone(state.events));
        throw Error("unexpected " + url);
    } }, { filters: {}, ...mount });
    h.state = state;
    H.click(h.host.querySelector('[data-action="mc-select-expert"][data-contact-id="1"]')); await H.flush();
    return h;
}
async function select(h, value) { role(h, "review-selection").value = value; H.changeEvent(role(h, "review-selection")); await H.flush(); }
async function click(h, name) { assert.ok(action(h, name), name + " exists"); H.click(action(h, name)); await H.flush(); }
function reviewRequests(h, suffix) { return h.calls.api.filter(x => x.url.startsWith(BASE) && (!suffix || x.url.includes(suffix))); }
function finish(h) { h.sandbox.MailboxChat.unmount(h.host); }

test("pending scope keeps server counts and existing account/text filters", async () => {
    const h = await setup({}, { filters: { accountCode: "acc1", keyword: "retained", expertScope: "PENDING_REVIEW" } });
    assert.ok(h.calls.api.some(x => /expertScope=PENDING_REVIEW/.test(x.url) && /keyword=retained/.test(x.url) && /accountCode=acc1/.test(x.url)));
    assert.equal(h.host.querySelectorAll('.mc-person[data-contact-id]').length, 2);
    assert.equal(h.host.querySelectorAll('[data-role="review-pending-count"]').map(x => x.textContent).join(","), "待审核 2,待审核 1");
    finish(h);
});

test("I-20 single original editor, immutable review cannot autosave/send, original body and attachment restore", async () => {
    const server = H.createDraftServer(), target = { contactId: 1, kind: "INBOUND", processingId: 101, accountScope: "acc1" };
    server.seed("admin", target, { subject: "Admin A", html: "<b>Admin A</b>", text: "Admin A", context: { schemaVersion: 1, outboundAttachmentDraft: { revision: 1, items: [{ id: "attachment-1", state: "ready", filename: "one.txt", attachmentId: 77 }] } } });
    const h = await setup({ draftServer: server }); const original = editor(h);
    await select(h, "review:11");
    assert.equal(editor(h), original); assert.equal(h.host.querySelectorAll('[data-role="mc-editor"]').length, 1);
    assert.equal(editor(h).getAttribute("contenteditable"), "false"); assert.equal(subject(h).disabled, true);
    assert.equal(h.host.querySelector('[data-action="mc-send-manual"]'), null); assert.equal(action(h, "send"), null);
    const before = server.requests.filter(x => x.method === "PUT").length;
    H.inputEvent(editor(h)); h.runTimers(); await H.flush();
    assert.equal(server.requests.filter(x => x.method === "PUT").length, before);
    await select(h, "manual"); assert.equal(subject(h).value, "Admin A"); assert.match(editor(h).innerHTML, /Admin A/);
    assert.equal(server.get("admin", target).content.context.outboundAttachmentDraft.items.length, 1); finish(h);
});

test("I-20 failed flush blocks review switch, retained admin bytes never become review data", async () => {
    const server = H.createDraftServer(), h = await setup({ draftServer: server });
    H.setEditorContent(editor(h), "<p>dirty admin</p>", "dirty admin"); H.inputEvent(editor(h));
    server.intercept = (entry, next) => entry.method === "PUT" ? Promise.reject(Error("offline")) : next();
    await select(h, "review:11"); assert.match(editor(h).innerHTML, /dirty admin/);
    assert.ok(h.host.querySelector('[data-action="mc-send-manual"]')); assert.equal(reviewRequests(h, "/11").length, 0);
    server.intercept = null; await h.api.flushDrafts(); finish(h);
});

test("I-21 approval only updates state/counts and explicit send uses only the dedicated frozen version route", async () => {
    const h = await setup({ rows: [draft(11)] }, { filters: { expertScope: "PENDING_REVIEW" } });
    await select(h, "review:11"); await click(h, "approve");
    assert.equal(h.state.sends.length, 0);
    assert.match(editor(h).textContent, /User body 11/); assert.match(role(h, "review-context").textContent, /暂无待审核草稿/);
    assert.ok(action(h, "send")); await click(h, "send"); assert.equal(h.state.sends.length, 1);
    assert.deepEqual(Object.keys(h.state.sends[0]).sort(), ["confirmationEventId", "expectedVersion"]);
    assert.equal(h.state.sends[0].expectedVersion, 3); assert.equal(action(h, "send"), null);
    assert.match(role(h, "review-selection").textContent, /已发送/);
    assert.doesNotMatch(role(h, "review-context").textContent, /再次手动点击发送/);
    assert.equal(h.calls.sendRich.length + h.calls.sendConversation.length, 0); assert.match(role(h, "review-state").textContent, /已发送/); finish(h);
});

for (const status of ["SENT", "DELIVERY_UNKNOWN", "DELIVERY_IN_PROGRESS"]) test("I-21 terminal/pending status " + status + " has no send button", async () => {
    const h = await setup({ rows: [draft(11, 1, { state: "APPROVED", sendStatus: status, approvedContextToken: "initial" })] });
    await select(h, "review:11"); assert.equal(action(h, "send"), null); assert.equal(h.host.querySelector('[data-action="mc-send-manual"]'), null);
    assert.equal(reviewRequests(h, "/context").length, 0); finish(h);
});

test("I-22 native context cancel reopens, confirm alone never sends, next click sends, subsequent context invalidates event", async () => {
    const h = await setup({ rows: [draft(11, 1, { state: "APPROVED", approvedContextToken: "old" })] });
    await select(h, "review:11"); assert.ok(role(h, "review-context-confirm"));
    await click(h, "context-cancel"); assert.equal(role(h, "review-context-confirm"), null);
    await click(h, "send"); assert.ok(role(h, "review-context-confirm"));
    await click(h, "context-confirm"); assert.equal(h.state.confirmations.length, 1); assert.equal(h.state.sends.length, 0);
    h.state.token = "later"; await click(h, "send"); assert.equal(h.state.sends.length, 0); assert.ok(role(h, "review-context-confirm"));
    await click(h, "context-confirm"); assert.equal(h.state.sends.length, 0);
    await click(h, "send"); assert.equal(h.state.sends.length, 1); assert.equal(h.state.sends[0].confirmationEventId, 101); finish(h);
});

test("I-22 backend 409 clears stale confirmation and opens fresh context dialog", async () => {
    const h = await setup({ rows: [draft(11, 1, { state: "APPROVED", approvedContextToken: "initial" })] }); await select(h, "review:11");
    h.state.hook = (url, method) => { if (url.endsWith("/send")) { h.state.token = "race"; return Promise.reject(Object.assign(Error("changed"), { status: 409, data: { code: "REVIEW_CONTEXT_CHANGED" } })); } };
    await click(h, "send"); assert.ok(role(h, "review-context-confirm")); assert.equal(h.state.sends.length, 0); finish(h);
});

test("I-22 translation stays beneath body; late translation and comments cannot cross drafts", async () => {
    const h = await setup(); await select(h, "review:11"); const html = editor(h).innerHTML, subj = subject(h).value;
    await click(h, "translate"); assert.match(role(h, "review-translation").textContent, /译文/); assert.equal(editor(h).innerHTML, html); assert.equal(subject(h).value, subj);
    await select(h, "review:12"); const hold = deferred(); h.state.hook = url => url.endsWith("/translate") ? hold.promise : undefined;
    H.click(action(h, "translate")); await H.flush(); await select(h, "review:11");
    hold.resolve({ ok: true, translatedText: "late secret" }); await H.flush(); assert.doesNotMatch(h.host.textContent, /late secret/);
    await click(h, "comments"); const input = role(h, "review-discussion").querySelector("textarea"); input.value = "review comment"; await click(h, "comment");
    assert.match(role(h, "review-discussion").textContent, /review comment/); finish(h);
});

test("return requires inline reason; history includes deleted versions without loading them into editor", async () => {
    const h = await setup({ rows: [draft(11), draft(12, 1, { deleted: true })], events: [{ id: 22, actorUsername: "user1", eventType: "SAVE", createdAt: "then", payload: { html: '<b>Version</b><script>bad()</script>', text: "Version" } }] });
    await select(h, "review:11"); await click(h, "return"); await click(h, "confirm-return"); assert.equal(h.state.rows[0].state, "SUBMITTED");
    role(h, "review-return").querySelector("textarea").value = "Please revise"; await click(h, "confirm-return"); assert.equal(h.state.rows[0].returnReason, "Please revise");
    await click(h, "history"); const history = role(h, "review-history"); assert.match(history.textContent, /已删除/);
    assert.equal(history.querySelector('[data-review-action="select"][data-draft-id="12"]'), null);
    H.click(history.querySelector('[data-review-action="events"][data-draft-id="12"]')); await H.flush();
    assert.ok(role(h, "review-version-body")); assert.equal(role(h, "review-version-body").querySelector("script"), null); finish(h);
});

test("draft selector paginates beyond first page and stale detail cannot cross expert/session/unmount", async () => {
    const rows = Array.from({ length: 45 }, (_, i) => draft(100 + i)); const h = await setup({ rows });
    assert.equal(role(h, "review-selection").querySelectorAll("option").length, 21);
    await click(h, "more-drafts"); await click(h, "more-drafts"); assert.equal(role(h, "review-selection").querySelectorAll("option").length, 46);
    const hold = deferred(); h.state.hook = url => url === BASE + "/100" ? hold.promise : undefined;
    H.click(action(h, "history")); await select(h, "review:100");
    H.click(h.host.querySelector('[data-action="mc-select-expert"][data-contact-id="2"]')); await H.flush();
    hold.resolve(draft(100)); await H.flush(); assert.doesNotMatch(editor(h).textContent, /User body 100/);
    finish(h); assert.equal(h.host.innerHTML, "");
});

test("review uses the final popover entry and synchronized resources", () => {
    const index = fs.readFileSync(path.join(ROOT, "index.html"), "utf8");
    assert.doesNotMatch(index, /id="mailboxReviewPendingBtn"/);
    const versions = [...index.matchAll(/\?v=([^" ]+)/g)].map(x => x[1]);
    assert.ok(versions.length && versions.every(x => x === versions[0]));
});

test("safety confirmation retries only the frozen review route and cannot send after draft switch", async () => {
    const h = await setup({ rows: [draft(11, 1, { state: "APPROVED", approvedContextToken: "initial" }), draft(12)] });
    await select(h, "review:11");
    let attempts = 0;
    const hold = deferred();
    h.sandbox.confirmManualReplySafety = () => hold.promise;
    h.state.hook = (url, method, body) => {
        if (url.endsWith("/11/send")) {
            attempts++;
            return Promise.reject(Object.assign(Error("safety"), { status: 422, data: { code: "MANUAL_SEND_SAFETY_BLOCKED", findings: [{ code: "REPLY_SIGNATURE_MISSING" }] } }));
        }
    };
    H.click(action(h, "send")); await H.flush();
    assert.equal(attempts, 1);
    await select(h, "review:12"); hold.resolve({ safetyWarningConfirmed: true }); await H.flush();
    assert.equal(attempts, 1); assert.equal(h.calls.sendRich.length + h.calls.sendConversation.length, 0);
    assert.match(subject(h).value, /Review 12/); finish(h);
});

test("Escape cancellation clears credentials and stale comment response cannot populate another draft", async () => {
    const h = await setup({ rows: [draft(11, 1, { state: "APPROVED", approvedContextToken: "old" }), draft(12)] });
    await select(h, "review:11");
    role(h, "review-context-confirm").dispatchEvent(new H.MiniEvent("cancel", { bubbles: false }));
    assert.equal(role(h, "review-context-confirm"), null);
    await click(h, "send"); assert.ok(role(h, "review-context-confirm"));
    await click(h, "context-cancel"); await click(h, "comments");
    role(h, "review-discussion").querySelector("textarea").value = "A private comment";
    const hold = deferred(); h.state.hook = (url, method) => method === "POST" && url.endsWith("/comments") ? hold.promise : undefined;
    H.click(action(h, "comment")); await H.flush(); await select(h, "review:12");
    hold.resolve({ id: 40 }); await H.flush();
    assert.equal(role(h, "review-discussion").hidden, true); assert.doesNotMatch(h.host.textContent, /A private comment/); finish(h);
});

test("native context dialog cycles keyboard focus in both directions without sending", async () => {
    const h = await setup({ rows: [draft(11, 1, { state: "APPROVED", approvedContextToken: "old" })] });
    await select(h, "review:11");
    const dialog = role(h, "review-context-confirm");
    const cancel = action(h, "context-cancel"), confirm = action(h, "context-confirm");
    const tab = (target, shiftKey) => {
        const event = new H.MiniEvent("keydown", { bubbles: true });
        event.key = "Tab"; event.shiftKey = shiftKey;
        let prevented = false; event.preventDefault = () => { prevented = true; };
        target.dispatchEvent(event); assert.equal(prevented, true);
    };
    confirm.focus(); tab(confirm, false); assert.equal(dialog.ownerDocument.activeElement, cancel);
    tab(cancel, true); assert.equal(dialog.ownerDocument.activeElement, confirm);
    assert.equal(h.state.sends.length, 0);
    finish(h);
});

test("review list waits for authenticated ownership and loads after delayed identity", async () => {
    const identity = deferred();
    const h = await setup({ authMe: identity.promise });
    assert.equal(reviewRequests(h).length, 0);
    identity.resolve({ authenticated: true, username: "admin", mustChangePassword: false });
    await H.flush();
    assert.ok(reviewRequests(h).some(x => x.url.startsWith(BASE + "?")));
    finish(h);
});

{
const assert = require("node:assert/strict");
const { test } = require("node:test");
const source = fs.readFileSync(path.join(__dirname, "mailboxChatBehavior.test.js"), "utf8");
const sandbox = { require, __dirname, console, URL, URLSearchParams, setImmediate };
vm.createContext(sandbox);
vm.runInContext(source.slice(0, source.indexOf('describe("mailbox chat mount')) +
    '\nglobalThis.H = { bootChat, flush, click, inputEvent, changeEvent, MiniEvent, expertA, expertB, messagesA, contactA, createDraftServer, setEditorContent };', sandbox);
const H = sandbox.H;
const clone = value => JSON.parse(JSON.stringify(value));
const deferred = () => { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b; }); return { promise, resolve, reject }; };
const node = (ctx, role) => ctx.host.querySelector(`[data-role="${role}"]`);
const action = (ctx, name) => ctx.host.querySelector(`[data-review-action="${name}"]`);
const editor = ctx => ctx.host.querySelector('[aria-label="人工回复正文"]');
const reviews = ctx => ctx.calls.api.filter(r => r.url.includes("/review-drafts"));
const sends = ctx => reviews(ctx).filter(r => r.url.endsWith("/send"));
const posts = ctx => reviews(ctx).filter(r => r.method === "POST");
function detail(id = 11, contactId = 1, extra = {}) {
    return Object.assign({ id, contactId, ownerUsername: "user1", version: 2, state: "SUBMITTED", subject: "Review subject", html: "<p>User review body</p>", text: "User review body", submittedAt: "2026-10-30T10:00:00", updatedAt: "2026-10-30T10:00:00", deleted: false, commentCount: 0, currentContextToken: "context-A", approvedContextToken: null, sendStatus: "NOT_SENT" }, extra);
}
async function boot(options = {}) {
    const rows = options.rows || [detail(), detail(12), detail(21, 2)];
    const state = { rows, token: "context-A", events: [], sendCount: 0, intercept: options.intercept, listError: false, confirmations: 0 };
    const route = (url, method, raw, entry, next) => {
        const body = raw && JSON.parse(raw);
        const respond = () => {
            if (url.startsWith("/api/mail/mailbox/conversations?")) {
                if (state.listError) return Promise.reject(new Error("list offline"));
                const params = new URL(url, "http://test").searchParams;
                const items = [H.expertA(), H.expertB()].map(item => Object.assign(item, { reviewPendingCount: rows.filter(r => r.contactId === item.contactId && !r.deleted && r.state === "SUBMITTED").length }));
                const selected = params.get("expertScope") === "PENDING_REVIEW" ? items.filter(item => item.reviewPendingCount) : items;
                const pendingReviewDraftCount = rows.filter(r => !r.deleted && r.state === "SUBMITTED").length;
                return Promise.resolve({ items: selected, total: selected.length, reviewPendingTotal: pendingReviewDraftCount,
                    filterCounts: { user1FullExpertCount: 0, pendingReviewDraftCount } });
            }
            if (!url.includes("/review-drafts")) return next(url, method, raw);
            const parsed = new URL(url, "http://test");
            const match = parsed.pathname.match(/review-drafts\/(\d+)(?:\/(.*))?$/);
            if (!match) {
                const all = rows.filter(r => r.contactId === Number(parsed.searchParams.get("contactId")));
                const page = Number(parsed.searchParams.get("page")), size = Number(parsed.searchParams.get("size"));
                return Promise.resolve(clone({ items: all.slice(page * size, (page + 1) * size), total: all.length }));
            }
            const row = rows.find(r => r.id === Number(match[1]));
            const endpoint = match[2];
            if (!endpoint) return Promise.resolve(clone(row));
            if (endpoint === "context") return Promise.resolve({ currentToken: state.token, changed: state.token !== row.approvedContextToken });
            if (endpoint === "confirm-context") { state.confirmations++; return Promise.resolve({ currentToken: state.token, confirmationEventId: 901 }); }
            if (endpoint === "translate") return Promise.resolve({ ok: true, translatedText: "Translated " + row.id });
            if (endpoint === "events") return Promise.resolve(clone(state.events.slice(parsed.searchParams.has("beforeId") ? 20 : 0, parsed.searchParams.has("beforeId") ? 40 : 20)));
            if (endpoint === "comments") { state.events.unshift({ id: 1000, actorUsername: "admin", eventType: "COMMENT", createdAt: "now", payload: { text: body.text } }); return Promise.resolve(state.events[0]); }
            if (endpoint === "approve") { assert.equal(body.observedContextToken, row.currentContextToken); row.state = "APPROVED"; row.approvedContextToken = state.token; row.version++; }
            if (endpoint === "return") { row.state = "RETURNED"; row.returnReason = body.reason; row.version++; }
            if (endpoint === "send") { row.sendStatus = "SENT"; state.sendCount++; }
            return Promise.resolve(clone(row));
        };
        const intercepted = state.intercept && state.intercept({ url, method, body, state, respond });
        return intercepted === undefined ? respond() : intercepted;
    };
    const ctx = await H.bootChat({ route, draftServer: options.draftServer, messages: H.messagesA(), contact: H.contactA() }, { filters: options.filters || {} });
    H.click(ctx.host.querySelector('[data-action="mc-select-expert"][data-contact-id="1"]')); await H.flush();
    return Object.assign(ctx, { reviewState: state });
}
async function select(ctx, value = "review:11") {
    const selector = node(ctx, "review-selection"); assert.ok(selector, "selector survives real async original-draft load");
    selector.value = value; H.changeEvent(selector); await H.flush();
}
async function press(ctx, name) { const button = action(ctx, name); assert.ok(button, name); H.click(button); await H.flush(); }

async function applyExpertScope(ctx, scope) {
    H.click(ctx.host.querySelector('[data-action="mc-more-filters"]')); await H.flush();
    const select = ctx.host.querySelector("#mailboxFilterExpertScope");
    assert.ok(select);
    assert.equal(ctx.host.querySelectorAll("#mailboxSearchBtn").length, 1);
    select.value = scope; H.changeEvent(select);
    H.click(ctx.host.querySelector("#mailboxSearchBtn")); await H.flush();
}
test("popover pending scope uses server counts, retains conditions, expert cards and current approved editor", async () => {
    const ctx = await boot({ filters: { accountCode: "acc1", country: "Japan" } });
    await applyExpertScope(ctx, "PENDING_REVIEW");
    assert.match(ctx.host.querySelector("#mailboxFilterExpertScope").textContent, /待审核（3条）/);
    assert.equal(ctx.host.querySelectorAll(".mc-person").length, 2);
    assert.equal(ctx.host.querySelectorAll('.mc-person [data-role="review-pending-count"]').length, 2);
    assert.ok(node(ctx, "review-context"));
    const params = new URL(ctx.calls.api.filter(r => r.url.includes("conversations?") && r.url.includes("expertScope=PENDING_REVIEW")).at(-1).url, "http://test").searchParams;
    assert.equal(params.get("accountCode"), "acc1"); assert.equal(params.get("page"), "0");
    await press(ctx, "approve");
    await select(ctx, "review:12"); await press(ctx, "approve");
    assert.ok(node(ctx, "review-selection").querySelector('option[value="review:12"][selected]'), "last approval cannot change reviewed recipient/draft");
    assert.ok(action(ctx, "send")); assert.equal(ctx.host.querySelectorAll(".mc-person").length, 1);
    await applyExpertScope(ctx, "ALL");
    assert.equal(ctx.host.querySelector("#mailboxFilterExpertScope").value, "ALL");
    ctx.reviewState.listError = true; await ctx.api.refresh(); await H.flush();
    assert.doesNotMatch(ctx.host.querySelector("#mailboxFilterExpertScope").textContent, /待审核（0条）/);
});

test("single editor isolates review body from original persisted rich body and attachments", async () => {
    const server = H.createDraftServer();
    const target = { contactId: 1, kind: "INBOUND", processingId: 101, accountScope: "acc1" };
    server.seed("admin", target, { subject: "Admin A", html: "<b>Admin original</b>", text: "Admin original", context: { schemaVersion: 1, outboundAttachmentDraft: { revision: 1, items: [{ key: "of-1", id: "7", state: "ready", filename: "original.txt", byteLength: 1 }] } } });
    const ctx = await boot({ draftServer: server });
    assert.match(editor(ctx).textContent, /Admin original/);
    await select(ctx);
    assert.equal(ctx.host.querySelectorAll('[data-role="mc-editor"]').length, 1);
    assert.equal(editor(ctx).getAttribute("contenteditable"), "false");
    assert.equal(ctx.host.querySelector('[data-action="mc-send-manual"]'), null);
    assert.equal(ctx.host.querySelector('[data-role="outbound-file-input"]'), null);
    H.inputEvent(editor(ctx)); await ctx.api.flushDrafts();
    assert.equal(server.get("admin", target).content.text, "Admin original");
    await select(ctx, "manual");
    assert.match(editor(ctx).textContent, /Admin original/); assert.match(ctx.host.textContent, /original.txt/);
    assert.ok(ctx.host.querySelector('[data-action="mc-send-manual"]'));
    assert.equal(server.requests.filter(r => r.method === "PUT").length, 0);
});

test("dirty save failure prevents review entry; successful flush completes before reading review", async () => {
    const server = H.createDraftServer(), ctx = await boot({ draftServer: server });
    H.setEditorContent(editor(ctx), "<p>unsaved A</p>", "unsaved A"); H.inputEvent(editor(ctx));
    server.intercept = (entry, next) => entry.method === "PUT" ? Promise.reject(new Error("save offline")) : next();
    await select(ctx); assert.equal(node(ctx, "review-context").hidden, true); assert.match(editor(ctx).textContent, /unsaved A/);
    server.intercept = null; await select(ctx);
    assert.ok(node(ctx, "review-context"));
    assert.equal(server.get("admin", { contactId: 1, kind: "INBOUND", processingId: 101, accountScope: "acc1" }).content.text, "unsaved A");
});

for (const state of ["DRAFT", "SUBMITTED", "RETURNED"]) test(state + " has no review or ordinary send node", async () => {
    const ctx = await boot({ rows: [detail(11, 1, { state })] }); await select(ctx);
    assert.equal(action(ctx, "send"), null); assert.equal(ctx.host.querySelector('[data-action="mc-send-manual"]'), null);
    assert.equal(posts(ctx).length, 0);
});

for (const status of ["SENT", "DELIVERY_UNKNOWN", "DELIVERY_IN_PROGRESS"]) test(status + " cannot resend or demand context confirmation", async () => {
    const ctx = await boot({ rows: [detail(11, 1, { state: "APPROVED", sendStatus: status })] }); await select(ctx);
    assert.equal(action(ctx, "send"), null); assert.equal(node(ctx, "review-context-confirm"), null);
    assert.equal(reviews(ctx).filter(r => r.url.endsWith("/context")).length, 0);
});

test("approval and context confirmation never send; cancel, Escape, new context and explicit send sequence", async () => {
    const ctx = await boot(); await select(ctx); await press(ctx, "approve");
    assert.equal(sends(ctx).length, 0); assert.ok(action(ctx, "send"));
    ctx.reviewState.token = "context-B";
    await select(ctx); assert.equal(node(ctx, "review-context-confirm").open, true);
    assert.equal(ctx.doc.activeElement, action(ctx, "context-cancel"));
    await press(ctx, "context-cancel"); await press(ctx, "send");
    assert.equal(node(ctx, "review-context-confirm").open, true);
    const cancel = new H.MiniEvent("cancel"); node(ctx, "review-context-confirm").dispatchEvent(cancel); await H.flush();
    assert.equal(node(ctx, "review-context-confirm"), null);
    await press(ctx, "send"); await press(ctx, "context-confirm");
    assert.equal(sends(ctx).length, 0); assert.equal(ctx.reviewState.confirmations, 1);
    ctx.reviewState.token = "context-C";
    await press(ctx, "send"); assert.equal(node(ctx, "review-context-confirm").open, true);
    await press(ctx, "context-confirm"); assert.equal(sends(ctx).length, 0);
    await press(ctx, "send");
    assert.equal(sends(ctx).length, 1); assert.equal(ctx.reviewState.sendCount, 1); assert.equal(action(ctx, "send"), null);
    const payload = JSON.parse(sends(ctx)[0].body);
    assert.deepEqual(Object.keys(payload).sort(), ["confirmationEventId", "expectedVersion"]);
    assert.equal(payload.confirmationEventId, 901);
});

test("late context confirmation after cancel cannot restore a send credential", async () => {
    const hold = deferred();
    const ctx = await boot({ rows: [detail(11, 1, { state: "APPROVED", approvedContextToken: "old" })], intercept: r => r.url.endsWith("/confirm-context") ? hold.promise : undefined });
    await select(ctx); H.click(action(ctx, "context-confirm")); await H.flush();
    assert.equal(action(ctx, "context-confirm").disabled, true);
    node(ctx, "review-context-confirm").dispatchEvent(new H.MiniEvent("cancel"));
    hold.resolve({ confirmationEventId: 77, currentToken: "context-A" }); await H.flush();
    assert.equal(node(ctx, "review-context-confirm"), null);
    await press(ctx, "send"); assert.equal(node(ctx, "review-context-confirm").open, true); assert.equal(sends(ctx).length, 0);
});

test("translation, comments, escaped version history, cursor and deleted history do not mutate review content", async () => {
    const ctx = await boot({ rows: [detail(), { id: 99, contactId: 1, deleted: true }] }); await select(ctx);
    const html = editor(ctx).innerHTML, subject = ctx.host.querySelector('input[aria-label="回复主题"]').value;
    await press(ctx, "translate"); assert.equal(node(ctx, "review-translation").textContent, "Translated 11");
    assert.equal(editor(ctx).innerHTML, html); assert.equal(ctx.host.querySelector('input[aria-label="回复主题"]').value, subject);
    ctx.reviewState.events = Array.from({ length: 21 }, (_, i) => ({ id: 100 - i, actorUsername: "user1", createdAt: "now", eventType: "SAVE", payload: { text: "<script>snapshot</script> " + i } }));
    await press(ctx, "events"); assert.equal(node(ctx, "review-discussion").querySelectorAll("script").length, 0);
    await press(ctx, "more-events"); assert.equal(node(ctx, "review-discussion").querySelectorAll("article").length, 21);
    assert.match(reviews(ctx).at(-1).url, /beforeId=81/);
    const comment = ctx.host.querySelector('[aria-label="评论内容"]'); comment.value = "Reviewed";
    await press(ctx, "comment"); assert.match(action(ctx, "comments").textContent, /1/);
    await press(ctx, "history");
    const deleted = node(ctx, "review-history").querySelector('[data-draft-id="99"]');
    assert.equal(deleted.dataset.reviewAction, "events");
    assert.equal(node(ctx, "review-history").querySelector('[data-review-action="select"][data-draft-id="99"]'), null);
});

test("stale translation and event replies after changing draft, expert or session are discarded", async () => {
    for (const endpoint of ["translate", "events"]) {
        const hold = deferred();
        const ctx = await boot({ intercept: r => new URL(r.url, "http://test").pathname.endsWith("/" + endpoint) ? hold.promise : undefined });
        await select(ctx); H.click(action(ctx, endpoint)); await H.flush();
        await select(ctx, "review:12");
        hold.resolve(endpoint === "translate" ? { ok: true, translatedText: "STALE A" } : [{ id: 2, actorUsername: "STALE A", payload: { text: "STALE A" } }]); await H.flush();
        assert.doesNotMatch(ctx.host.textContent, /STALE A/); assert.equal(node(ctx, "review-translation").hidden, true);
        H.click(ctx.host.querySelector('[data-action="mc-select-expert"][data-contact-id="2"]')); await H.flush();
        assert.equal(node(ctx, "review-context").hidden, true);
        ctx.api.applyOptions({ sessionUser: "user1" }); await H.flush();
        assert.equal(node(ctx, "review-selection"), null);
    }
});

test("pagination can find submitted draft beyond the first page and preserves all selectable history", async () => {
    const rows = Array.from({ length: 23 }, (_, i) => detail(i + 11, 1, { state: i === 22 ? "SUBMITTED" : "RETURNED" }));
    const ctx = await boot({ rows }); await applyExpertScope(ctx, "PENDING_REVIEW");
    assert.ok(node(ctx, "review-selection").querySelector('option[value="review:33"][selected]')); assert.equal(node(ctx, "review-selection").querySelectorAll("option").length, 24);
    assert.ok(reviews(ctx).some(r => r.url.includes("page=1")));
});

test("return requires a reason; server context conflict refreshes submitted snapshot without approving or sending", async () => {
    let conflict = true;
    const ctx = await boot({ intercept: r => {
        if (r.url.endsWith("/approve") && conflict) { conflict = false; r.state.rows[0].currentContextToken = "fresh"; return Promise.reject(Object.assign(new Error("Review latest conversation"), { status: 409, data: { code: "REVIEW_CONTEXT_CHANGED" } })); }
    } }); await select(ctx); await press(ctx, "approve");
    assert.equal(action(ctx, "send"), null); assert.equal(sends(ctx).length, 0);
    await select(ctx);
    await press(ctx, "approve");
    assert.equal(JSON.parse(posts(ctx).filter(r => r.url.endsWith("/approve")).at(-1).body).observedContextToken, "fresh");
    await select(ctx, "review:12");
    await press(ctx, "return"); await press(ctx, "confirm-return"); assert.equal(ctx.reviewState.rows[1].state, "SUBMITTED");
    ctx.host.querySelector('[aria-label="退回原因"]').value = "Please revise"; await press(ctx, "confirm-return");
    assert.equal(ctx.reviewState.rows[1].state, "RETURNED"); assert.match(ctx.host.textContent, /Please revise/);
});

test("safe retry keeps the dedicated endpoint, safety protocol and server send identity; unknown hides resend", async () => {
    let attempts = 0;
    const ctx = await boot({ rows: [detail(11, 1, { state: "APPROVED", approvedContextToken: "context-A" })], intercept: r => {
        if (r.url.endsWith("/send") && ++attempts === 1) return Promise.reject(Object.assign(new Error("safety"), { data: { code: "MANUAL_SEND_SAFETY_BLOCKED", findings: [{ code: "REPLY_IDENTITY" }] } }));
    } });
    ctx.sandbox.confirmManualReplySafety = async () => ({ safetyWarningConfirmed: true });
    await select(ctx); await press(ctx, "send"); assert.equal(sends(ctx).length, 2); assert.equal(JSON.parse(sends(ctx)[1].body).safetyWarningConfirmed, true); assert.equal(ctx.reviewState.sendCount, 1);
    const unknown = await boot({ rows: [detail(11, 1, { state: "APPROVED", approvedContextToken: "context-A" })], intercept: r => {
        if (r.url.endsWith("/send")) { r.state.rows[0].sendStatus = "DELIVERY_UNKNOWN"; return Promise.reject(new Error("unknown")); }
    } }); await select(unknown); await press(unknown, "send"); assert.equal(action(unknown, "send"), null); assert.match(unknown.host.textContent, /待确认/);
});

test("final composition has no obsolete top review button and keeps literal opaque native dialog CSS", () => {
    const root = path.join(__dirname, "../../main/resources/static");
    const index = fs.readFileSync(path.join(root, "index.html"), "utf8");
    assert.doesNotMatch(index, /id="mailboxReviewPendingBtn"/);
    const versions = [...index.matchAll(/(?:src|href)="[^"?]+\?v=([^"&]+)/g)].map(m => m[1]);
    assert.ok(versions.length > 0 && versions.every(v => v === versions[0]));
    const plan = fs.readFileSync(path.join(__dirname, "../../..", "docs/plans/2026-10-29/draft-review-05-admin.md"), "utf8");
    const css = plan.slice(plan.indexOf(".mail-chat .mail-review-confirm{width:"), plan.indexOf("\n```", plan.indexOf(".mail-chat .mail-review-confirm{width:")));
    assert.ok(fs.readFileSync(path.join(root, "styles.css"), "utf8").includes(css));
});
}
