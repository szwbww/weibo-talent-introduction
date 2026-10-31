"use strict";
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const assert = require("node:assert/strict");
const { test } = require("node:test");
// Reuse the finite node/tree model, not a querySelector that invents elements.
const original = fs.readFileSync(path.join(__dirname, "mailboxReadOnly.test.js"), "utf8");
const context = { require, __dirname, console, setTimeout, clearTimeout, AbortController };
vm.createContext(context);
vm.runInContext(original.slice(0, original.indexOf('describe("S-1')) + '\nglobalThis.tools = {createHarness, allNodes, clickAction, flush, deferred, listResponse, detailResponse, EXPERT_A, moduleSource};', context);
const { createHarness, allNodes, clickAction, flush, deferred, listResponse, detailResponse, EXPERT_A, moduleSource } = context.tools;
const BASE = "/api/mail/mailbox/review-drafts";
const nodes = h => allNodes(h.root);
const role = (h, name) => nodes(h).find(n => n.dataset.role === name);
const action = (h, name, id) => nodes(h).find(n => n.dataset.reviewAction === name && (id === undefined || n.dataset.draftId === String(id)));
const click = (h, name, id) => { const node = action(h, name, id); assert.ok(node, name); h.root.dispatch("click", { target: node, preventDefault() {} }); };
const draftValue = (id = 1, version = 1, state = "DRAFT") => ({ id, contactId: 7, version, state, subject: "A", html: "<p>body</p>", text: "body", deleted: false, commentCount: 0, updatedAt: "2026-10-29T12:00:00" });
async function setup({ sanitizer = true } = {}) {
    const h = createHarness();
    if (sanitizer) h.runtime.MailboxMeeting = { sanitizeDraftHtml(html) { h.sanitized.push(html); return html.replace(/<script[^>]*>[^]*?<\/script>/gi, "").replace(/ on\w+="[^"]*"/g, ""); } };
    h.sanitized = [];
    h.rows = new Map(); h.events = []; h.nextId = 1;
    h.custom = null;
    h.responder = async (url, options) => {
        if (h.custom) { const result = h.custom(url, options); if (result !== undefined) return result; }
        const method = options.method;
        if (url.startsWith("/api/mail/mailbox/conversations?")) return listResponse([EXPERT_A, { contactId: 8, name: "乙" }]);
        if (url.includes("/messages")) return detailResponse([{ source: "MAIL_RECORD", id: 1, direction: "INBOUND", body: "<b>literal</b>", subject: "mail" }]);
        if (url.startsWith(BASE + "?")) return { items: [...h.rows.values()], total: h.rows.size };
        if (url === BASE && method === "POST") { const row = draftValue(h.nextId++); h.rows.set(row.id, row); return row; }
        const id = Number(url.slice(BASE.length + 1).split(/[/?]/)[0]);
        const row = h.rows.get(id);
        if (url.includes("/events")) return h.events;
        if (method === "GET") return { ...row };
        if (method === "DELETE") { h.rows.set(id, { ...row, deleted: true }); return { id, deleted: true }; }
        const body = JSON.parse(options.body);
        assert.equal(body.expectedVersion, row.version, "all mutations use the current server version");
        if (url.endsWith("/comments")) return { id: 1 };
        const next = { ...row, version: row.version + 1 };
        if (method === "PUT") Object.assign(next, { subject: body.subject, html: body.html, text: body.html.replace(/<[^>]*>/g, "") });
        if (url.endsWith("/submit")) next.state = "SUBMITTED";
        if (url.endsWith("/withdraw")) next.state = "DRAFT";
        h.rows.set(id, next); return next;
    };
    const metadata = h.metadataResponder;
    h.metadataResponder = (url, options) => url.startsWith(BASE) ? h.responder(url, options) : metadata(url, options);
    h.mount(); await flush(); clickAction(h, "select-expert"); await flush();
    return h;
}
async function create(h) { click(h, "new"); await flush(); }
function edit(h, text = "edited") {
    role(h, "review-editor").innerHTML = "<p>" + text + "</p>";
    role(h, "review-editor").dispatch("input", {});
}

test("review mutations refresh the draft tab without replacing the active editor", async () => {
    const h = await setup();
    h.custom = (url) => url.includes("draftsOnly=true")
        ? { items: [], total: [...h.rows.values()].some(row => !row.deleted) ? 1 : 0 } : undefined;
    const editor = role(h, "review-editor");
    await create(h);
    assert.equal(role(h, "reader-draft-experts").textContent, "1");
    edit(h, "keep this input"); click(h, "save"); await flush();
    assert.equal(role(h, "review-editor"), editor);
    assert.equal(role(h, "review-subject").value, "A");
    click(h, "delete"); await flush();
    assert.equal(role(h, "reader-draft-experts").textContent, "0");
    h.handle.unmount();
});

test("S-1 persistent exact DOM and empty state, no extra classes/capabilities/storage", async () => {
    const h = await setup();
    assert.equal(role(h, "review-state").textContent, "暂无草稿");
    assert.equal(role(h, "review-editor").hidden, true);
    assert.equal(role(h, "review-drafts").parentNode.className, "mc-scroll");
    assert.equal(role(h, "review-compose").parentNode.className, "mc-section-content");
    assert.equal(role(h, "review-history").hidden, true);
    assert.equal(role(h, "review-discussion").hidden, true);
    assert.equal(nodes(h).filter(n => n.dataset.role === "review-editor").length, 1);
    assert.ok(!/(localStorage|sessionStorage)\s*[.[]/.test(moduleSource));
    assert.ok(!/sender-accounts|\/translate|\/approve|\/send|attachment/i.test(moduleSource.replace(/\/\*[\s\S]*?\*\//g, "")));
    const allowed = new Set(["mail-review-controls", "mail-review-panel", "mail-review-entry", "mail-review-input", "mail-review-footer", "mail-review-actions", "mail-review-select"]);
    for (const n of nodes(h)) {
        assert.equal(n.getAttribute("style"), null);
        for (const c of n.className.split(/\s+/)) if (c.startsWith("mail-review-")) assert.ok(allowed.has(c), c);
    }
    h.handle.unmount();
});

test("explicit save, latest-version submit, withdraw, two independent drafts and detail selection", async () => {
    const h = await setup(); await create(h); edit(h);
    assert.equal(h.handle.hasPendingDrafts(), true);
    assert.equal(h.calls.filter(c => c.options.method === "PUT").length, 0);
    click(h, "submit"); await flush();
    const mutations = h.calls.filter(c => ["PUT", "POST"].includes(c.options.method));
    assert.equal(mutations.at(-2).options.method, "PUT");
    assert.equal(JSON.parse(mutations.at(-1).options.body).expectedVersion, 2);
    assert.equal(role(h, "review-state").textContent, "已提交");
    assert.equal(role(h, "review-editor").getAttribute("contenteditable"), "false");
    assert.equal(action(h, "save").hidden, true); assert.equal(action(h, "withdraw").hidden, false);
    click(h, "withdraw"); await flush(); await create(h); edit(h, "B"); click(h, "save"); await flush();
    assert.equal(h.rows.size, 2); click(h, "history"); await flush(); click(h, "select", 1); await flush();
    assert.equal(role(h, "review-subject").value, "A");
    assert.equal(role(h, "review-history").hidden, true);
    assert.ok(h.calls.some(c => c.path === BASE + "/1" && c.options.method === "GET")); h.handle.unmount();
});

test("late save canonical baseline preserves newer input and dirty unload prompt", async () => {
    const h = await setup(); await create(h); edit(h, "first"); const gate = deferred();
    h.custom = (url, opt) => opt.method === "PUT" ? gate.promise : undefined;
    click(h, "save"); await flush(); edit(h, "newer");
    gate.resolve({ ...draftValue(1, 2), subject: "canonical", html: "<p>canonical</p>" }); await flush();
    assert.equal(role(h, "review-subject").value, "A");
    assert.equal(role(h, "review-editor").innerHTML, "<p>newer</p>");
    assert.equal(h.handle.hasPendingDrafts(), true);
    let prevented = false; h.runtime.dispatchWindow("beforeunload", { preventDefault() { prevented = true; } }); assert.equal(prevented, true);
    h.custom = null; h.rows.set(1, { ...h.rows.get(1), version: 2 }); await h.handle.flushDrafts();
    prevented = false; h.runtime.dispatchWindow("beforeunload", { preventDefault() { prevented = true; } }); assert.equal(prevented, false);
    h.handle.unmount();
});

for (const status of [0, 409]) test("failed save " + status + " preserves input and blocks navigation/refresh/create/submit", async () => {
    const h = await setup(); await create(h); edit(h, "local");
    h.custom = (url, opt) => opt.method === "PUT" ? Promise.reject(Object.assign(Error("failed"), { status })) : undefined;
    const beta = nodes(h).find(n => n.dataset.action === "select-expert" && n.dataset.contactId === "8");
    h.root.dispatch("click", { target: beta }); await flush();
    assert.equal(role(h, "review-subject").value, "A");
    assert.equal(nodes(h).find(n => n.className === "mc-identity").children[0].textContent, "张教授");
    await assert.rejects(h.handle.flushDrafts()); await assert.rejects(h.handle.refresh());
    click(h, "new"); await flush(); click(h, "submit"); await flush();
    assert.equal(h.rows.size, 1); assert.ok(!h.calls.some(c => c.path.endsWith("/submit")));
    assert.equal(role(h, "review-error").textContent, status === 409 ? "草稿已被更新，请重新加载" : "保存草稿失败：failed"); h.handle.unmount();
});

for (const status of [401, 403]) test("auth " + status + " clears timeline/editor/comments/cache and aborts pending requests", async () => {
    const h = await setup(); await create(h); edit(h, "secret");
    h.custom = (url, opt) => opt.method === "PUT" ? Promise.reject(Object.assign(Error("denied"), { status })) : undefined;
    click(h, "save"); await flush();
    assert.equal(role(h, "review-subject").value, ""); assert.equal(role(h, "review-editor").textContent, "");
    assert.equal(role(h, "review-discussion").textContent, ""); assert.equal(role(h, "review-history").textContent, "");
    assert.equal(nodes(h).filter(n => n.className === "mc-message").length, 0);
    assert.equal(h.handle.hasPendingDrafts(), false); const count = h.calls.length;
    h.runtime.dispatchWindow("focus", {}); await flush(); assert.equal(h.calls.length, count); h.handle.unmount();
});

test("focus preserves draft input; stale version is rejected on save without overwrite", async () => {
    const h = await setup(); await create(h); edit(h, "local"); const editor = role(h, "review-editor");
    const start = h.calls.length;
    h.runtime.dispatchWindow("focus", {}); await flush();
    assert.equal(role(h, "review-editor"), editor); assert.equal(editor.innerHTML, "<p>local</p>");
    assert.deepEqual(Array.from(h.calls.slice(start),c=>c.path),["/api/mail/mailbox/conversations/7/access"]);
    h.custom = (url, opt) => opt.method === "PUT" ? Promise.reject(Object.assign(Error("version conflict"),{status:409})) : undefined;
    click(h,"save"); await flush();
    assert.equal(action(h, "submit").disabled, true); assert.equal(action(h, "reload").hidden, false);
    assert.equal(editor.innerHTML,"<p>local</p>");
    assert.equal(role(h, "review-error").textContent, "草稿已被更新，请重新加载"); h.handle.unmount();
});

test("late detail/events/create responses cannot enter a different expert or unmounted owner", async () => {
    for (const endpoint of ["detail", "events", "create"]) {
        const h = await setup(); await create(h); const gate = deferred();
        h.custom = (url, opt) => endpoint === "detail" && url === BASE + "/1" && opt.method === "GET" ? gate.promise
            : endpoint === "events" && url.includes("/events") ? gate.promise
            : endpoint === "create" && url === BASE && opt.method === "POST" ? gate.promise : undefined;
        if (endpoint === "detail") h.runtime.dispatchWindow("focus", {});
        if (endpoint === "events") click(h, "events");
        if (endpoint === "create") click(h, "new");
        await flush();
        const beta = nodes(h).find(n => n.dataset.action === "select-expert" && n.dataset.contactId === "8");
        h.root.dispatch("click", { target: beta }); await flush();
        gate.resolve(endpoint === "events" ? [{ id: 99, eventType: "COMMENT", payload: { text: "OLD SECRET" } }] : { ...draftValue(1), subject: "OLD SECRET" }); await flush();
        assert.equal(role(h, "review-subject").value, ""); assert.ok(!h.root.textContent.includes("OLD SECRET"));
        h.handle.unmount(); assert.equal(h.host.children.length, 0);
    }
});

for (const state of ["DRAFT", "RETURNED", "SUBMITTED", "APPROVED"]) test("server state matrix " + state, async () => {
    const h = await setup(); await create(h);
    h.rows.set(1, { ...h.rows.get(1), state, returnReason: state === "RETURNED" ? "补充时间" : null });
    await h.handle.refresh(); click(h, "comments"); await flush();
    const editable = ["DRAFT", "RETURNED"].includes(state);
    assert.equal(role(h, "review-editor").getAttribute("contenteditable"), String(editable));
    assert.equal(action(h, "save").hidden, !editable); assert.equal(action(h, "submit").hidden, !editable);
    assert.equal(action(h, "withdraw").hidden, state !== "SUBMITTED"); assert.equal(action(h, "delete").hidden, state === "APPROVED");
    assert.equal(nodes(h).some(n => n.tagName === "TEXTAREA"), state !== "APPROVED");
    if (state === "RETURNED") assert.equal(role(h, "review-return-reason").textContent, "补充时间");
    if (state === "APPROVED") assert.equal(role(h, "review-save-state").textContent, "已审核，不能修改"); h.handle.unmount();
});

test("deleted history remains event-readable but cannot open editor; event cursor is an id", async () => {
    const h = await setup(); await create(h); edit(h); click(h, "save"); await flush(); click(h, "delete"); await flush();
    h.events = Array.from({ length: 20 }, (_, i) => ({ id: 30 - i, actorUsername: "admin", eventType: "SAVE", payload: { html: "<p>version</p>", text: "version" } }));
    click(h, "history"); await flush(); assert.equal(action(h, "select", 1), undefined);
    assert.ok(role(h, "review-history").textContent.includes("已删除")); click(h, "events", 1); await flush();
    assert.equal(role(h, "review-editor").hidden, true); assert.equal(nodes(h).filter(n => n.dataset.role === "review-version-body").length, 20);
    click(h, "more-events"); await flush(); assert.ok(h.calls.some(c => c.path.includes("beforeId=11"))); h.handle.unmount();
});

test("missing sanitizer is text-only read-only, all HTML passes the shared sanitizer", async () => {
    const h = await setup({ sanitizer: false }); await create(h);
    assert.equal(role(h, "review-editor").getAttribute("contenteditable"), "false");
    assert.equal(role(h, "review-editor").textContent, "body"); assert.equal(role(h, "review-error").textContent, "编辑组件加载失败"); h.handle.unmount();
    const safe = await setup(); await create(safe); assert.ok(safe.sanitized.includes("<p>body</p>")); safe.handle.unmount();
});

test("S-1 literal CSS contract and S-2 production resource/shell contracts", () => {
    const root = path.join(__dirname, "../../main/resources/static");
    const plan = fs.readFileSync(path.join(__dirname, "../../../docs/plans/2026-10-29/draft-review-04-reader.md"), "utf8");
    const css = plan.match(/```css\n([^]*?)```/)[1];
    assert.ok(fs.readFileSync(path.join(root, "styles.css"), "utf8").includes(css));
    const index = fs.readFileSync(path.join(root, "index.html"), "utf8");
    assert.ok(index.includes("id=\"mailboxList\""));
    const versions = [...index.matchAll(/\?v=([^\"]+)/g)].map(m => m[1]);
    assert.ok(versions.length > 0 && versions.every(v => v === versions[0]));
});

test("after revoke, flush permits logout and explicit refresh starts from the authorized list only", async () => {
    const h = await setup(); await create(h); edit(h);
    h.custom = (url, opt) => opt.method === "PUT" ? Promise.reject(Object.assign(Error("revoked"), { status: 403 })) : undefined;
    click(h, "save"); await flush(); await h.handle.flushDrafts();
    h.custom = null; const start = h.calls.length;
    await h.handle.refresh(); await flush();
    assert.ok(h.calls.slice(start).every(c => c.path.startsWith("/api/mail/mailbox/conversations?") || c.path.startsWith("/api/meeting-calendar/summaries?")));
    assert.equal(role(h, "review-drafts").hidden, true);
    h.handle.unmount();
});

test("submit freezes native editing while awaiting server state, but explicit save permits later typing", async () => {
    const h = await setup(); await create(h); edit(h);
    const gate = deferred();
    h.custom = (url) => url.endsWith("/submit") ? gate.promise : undefined;
    click(h, "submit"); await flush();
    assert.equal(role(h, "review-subject").disabled, true);
    assert.equal(role(h, "review-editor").getAttribute("contenteditable"), "false");
    assert.equal(action(h, "submit").disabled, true);
    gate.resolve(draftValue(1, 3, "SUBMITTED")); await flush();
    assert.equal(role(h, "review-state").textContent, "已提交");
    h.handle.unmount();
});

test("initial history detail network failure retries the requested draft only after confirmation", async () => {
    const h = await setup();
    h.rows.set(17, { ...draftValue(17), subject: "Recovered draft" });
    click(h, "history"); await flush();
    let failed = false;
    h.custom = (url, opt) => {
        if (url === BASE + "/17" && opt.method === "GET" && !failed) {
            failed = true; return Promise.reject(Error("network unavailable"));
        }
    };
    click(h, "select", 17); await flush();
    assert.equal(role(h, "review-editor").hidden, true);
    assert.equal(action(h, "reload").hidden, false);
    const before = h.calls.length;
    h.runtime.confirm = () => false;
    click(h, "reload"); await flush();
    assert.equal(h.calls.length, before, "cancel never discards or requests detail");
    h.runtime.confirm = () => true;
    click(h, "reload"); await flush();
    assert.equal(h.calls.length, before + 1);
    assert.equal(h.calls.at(-1).path, BASE + "/17");
    assert.equal(role(h, "review-subject").value, "Recovered draft");
    assert.equal(role(h, "review-editor").getAttribute("contenteditable"), "true");
    assert.equal(action(h, "reload").hidden, true);
    h.handle.unmount();
});

test("detail recovery keeps conflict input until confirmation and clears it on revoked authorization", async () => {
    const h = await setup(); await create(h); edit(h, "Local text to copy");
    h.custom = (url, opt) => opt.method === "PUT" ? Promise.reject(Object.assign(Error("conflict"), { status: 409 })) : undefined;
    click(h, "save"); await flush();
    h.runtime.confirm = () => false;
    click(h, "reload"); await flush();
    assert.equal(role(h, "review-subject").value, "A");
    assert.equal(role(h, "review-editor").innerHTML, "<p>Local text to copy</p>");
    h.custom = (url, opt) => url === BASE + "/1" && opt.method === "GET"
        ? Promise.reject(Object.assign(Error("revoked"), { status: 403 })) : undefined;
    h.runtime.confirm = () => true;
    click(h, "reload"); await flush();
    assert.equal(role(h, "review-drafts").hidden, true);
    assert.equal(role(h, "review-subject").value, "");
    assert.equal(role(h, "review-editor").textContent, "");
    assert.equal(action(h, "reload").hidden, true);
    const before = h.calls.length;
    click(h, "reload"); await flush();
    assert.equal(h.calls.length, before, "revocation removes the requested recovery identity");
    h.handle.unmount();
});

for (const crypto of [undefined, {}]) test("creation without randomUUID uses a canonical retry-stable UUID with crypto " + (crypto ? "present" : "absent"), async () => {
    const h = await setup();
    h.runtime.crypto = crypto;
    let random = 0;
    h.runtime.Math = { floor: Math.floor, random: () => ((random++ % 16) + 0.5) / 16 };
    const requests = [], persisted = new Map();
    h.custom = (url, opt) => {
        if (url !== BASE || opt.method !== "POST") return;
        const body = JSON.parse(opt.body);
        assert.match(body.clientRequestId, /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
        requests.push(body.clientRequestId);
        if (!persisted.has(body.clientRequestId)) {
            const row = draftValue(h.nextId++);
            persisted.set(body.clientRequestId, row); h.rows.set(row.id, row);
        }
        if (requests.length === 1) return Promise.reject(Error("response lost after create"));
        return persisted.get(body.clientRequestId);
    };
    await create(h);
    assert.equal(role(h, "review-editor").hidden, true);
    await create(h);
    assert.equal(requests[0], requests[1], "ambiguous network failure retries the same identity");
    assert.equal(h.rows.size, 1);
    assert.equal(role(h, "review-editor").getAttribute("contenteditable"), "true");
    await create(h);
    assert.notEqual(requests[1], requests[2], "a separate new draft gets a new identity");
    assert.equal(h.rows.size, 2);
    h.handle.unmount();
});

test("主题不可编辑；提交失败保持已保存状态并显示真实提交错误", async () => {
    const h = await setup(); await create(h);
    assert.equal(role(h,"review-subject").disabled,true);
    assert.equal(role(h,"review-subject").getAttribute("readonly"),"");
    h.custom = url => url.endsWith("/submit") ? Promise.reject(Object.assign(Error("validation"),{status:422,data:{message:"请填写回复正文后提交审核"}})) : undefined;
    click(h,"submit"); await flush();
    assert.equal(role(h,"review-save-state").textContent,"已保存");
    assert.equal(role(h,"review-error").textContent,"提交审核失败：请填写回复正文后提交审核");
    assert.equal(role(h,"review-editor").innerHTML,"<p>body</p>");
    h.handle.unmount();
});

test("discussion renders text safely, author/time separately and empty comments cannot submit", async () => {
    const h = await setup(); await create(h);
    h.events = [{id:2, actorUsername:"user1", eventType:"COMMENT", createdAt:"2026-10-31T16:45:00",payload:{text:"回复意见"}},
        {id:1, actorUsername:"admin", eventType:"COMMENT", createdAt:"2026-10-31T16:42:00",payload:{text:"<img src=x>补充时间"}}];
    click(h,"comments"); await flush();
    const panel = role(h,"review-discussion");
    assert.ok(panel.classList.contains('mc-discussion'));
    assert.ok(panel.textContent.includes('仅内部可见'));
    assert.ok(!panel.textContent.includes('COMMENT'));
    assert.ok(!nodes(h).some(n => n.tagName === 'IMG'));
    const bodies = nodes(h).filter(n => n.classList.contains('mc-comment-body'));
    assert.equal(bodies[0].textContent,'<img src=x>补充时间');
    const input = nodes(h).find(n => n.tagName === 'TEXTAREA');
    assert.equal(action(h,'comment').disabled,true);
    input.value='  ';input.dispatch('input',{});assert.equal(action(h,'comment').disabled,true);
    input.value='已补充';input.dispatch('input',{});assert.equal(action(h,'comment').disabled,false);
    assert.ok(panel.textContent.includes('3 / 2000'));
    click(h,'comment');await flush();
    assert.ok(h.calls.some(c=>c.path.endsWith('/comments') && JSON.parse(c.options.body).text==='已补充'));
    h.handle.unmount();
});
