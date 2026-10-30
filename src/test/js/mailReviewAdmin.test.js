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
    const summaries = [];
    const h = await H.bootChat({ draftServer: state.draftServer, authMe: state.authMe, contact: H.contactA(), messages: H.messagesA(), route(url, method, raw, entry, next) {
        const body = raw ? JSON.parse(raw) : null;
        if (state.hook) { const value = state.hook(url, method, body); if (value !== undefined) return value; }
        if (url.startsWith("/api/mail/mailbox/conversations?")) {
            const q = new URL(url, "http://test").searchParams;
            if (state.summaryFail && q.get("reviewPendingOnly") === "true") return Promise.reject(Error("summary down"));
            const counts = id => rows.filter(x => x.contactId === id && !x.deleted && x.state === "SUBMITTED").length;
            let items = [H.expertA({ reviewPendingCount: counts(1) }), H.expertB({ reviewPendingCount: counts(2) })];
            if (q.get("reviewPendingOnly") === "true") items = items.filter(x => x.reviewPendingCount > 0);
            return Promise.resolve({ items, total: items.length, reviewPendingTotal: counts(1) + counts(2) });
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
    } }, { filters: {}, onReviewPendingSummary: value => summaries.push(clone(value)), ...mount });
    h.state = state; h.summaries = summaries;
    H.click(h.host.querySelector('[data-action="mc-select-expert"][data-contact-id="1"]')); await H.flush();
    return h;
}
async function select(h, value) { role(h, "review-selection").value = value; H.changeEvent(role(h, "review-selection")); await H.flush(); }
async function click(h, name) { assert.ok(action(h, name), name + " exists"); H.click(action(h, name)); await H.flush(); }
function reviewRequests(h, suffix) { return h.calls.api.filter(x => x.url.startsWith(BASE) && (!suffix || x.url.includes(suffix))); }
function finish(h) { h.sandbox.MailboxChat.unmount(h.host); }

test("I-19 pending toolbar summary is server total, cards remain experts, chip exits and filters survive", async () => {
    const h = await setup({}, { filters: { accountCode: "acc1", keyword: "retained" } });
    assert.equal(h.summaries.at(-1).count, 3);
    await h.api.setReviewPendingOnly(true); await H.flush();
    assert.ok(h.calls.api.some(x => /reviewPendingOnly=true/.test(x.url) && /keyword=retained/.test(x.url) && /accountCode=acc1/.test(x.url)));
    assert.equal(h.host.querySelectorAll('.mc-person[data-contact-id]').length, 2);
    assert.equal(h.host.querySelectorAll('[data-role="review-pending-count"]').map(x => x.textContent).join(","), "待审核 2,待审核 1");
    assert.equal(h.host.querySelectorAll("dialog").length, 0);
    H.click(H.chipButton(h, "drafts")); await H.flush();
    assert.equal(h.summaries.at(-1).active, false); finish(h);
});

test("I-19 failed count is null/error rather than a fabricated zero and retry recovers", async () => {
    const h = await setup(); h.state.summaryFail = true; await h.api.refresh(); await H.flush();
    assert.equal(h.summaries.at(-1).count, null); assert.equal(h.summaries.at(-1).error, true);
    h.state.summaryFail = false; await h.api.refresh(); await H.flush(); assert.equal(h.summaries.at(-1).count, 3); finish(h);
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
    const h = await setup({ rows: [draft(11)] }); await h.api.setReviewPendingOnly(true); await H.flush();
    await select(h, "review:11"); await click(h, "approve");
    assert.equal(h.state.sends.length, 0); assert.equal(h.summaries.at(-1).count, 0);
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

test("S-3/S-4/S-5 actual DOM ids, synchronized resources, literal appended dialog CSS", () => {
    const index = fs.readFileSync(path.join(ROOT, "index.html"), "utf8");
    assert.equal((index.match(/id="mailboxReviewPendingBtn"/g) || []).length, 1);
    assert.ok(index.indexOf('id="mailboxReviewPendingBtn"') < index.indexOf('id="checkRepliesBtn"'));
    const versions = [...index.matchAll(/\?v=([^" ]+)/g)].map(x => x[1]); assert.ok(versions.length && versions.every(x => x === versions[0]));
    const plan = fs.readFileSync(path.join(__dirname, "../../../docs/plans/2026-10-29/draft-review-05-admin.md"), "utf8");
    assert.ok(fs.readFileSync(path.join(ROOT, "styles.css"), "utf8").endsWith(plan.match(/```css\n([^]*?)```/)[1]));
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

test("pending summary waits for authenticated ownership and refreshes after delayed identity", async () => {
    const identity = deferred();
    const h = await setup({ authMe: identity.promise });
    assert.equal(h.summaries.at(-1).count, null);
    assert.equal(h.calls.api.filter(x => x.url.includes("reviewPendingOnly=true")).length, 0);
    identity.resolve({ authenticated: true, username: "admin", mustChangePassword: false });
    await H.flush();
    assert.equal(h.summaries.at(-1).count, 3);
    assert.equal(h.summaries.at(-1).loading, false);
    finish(h);
});
