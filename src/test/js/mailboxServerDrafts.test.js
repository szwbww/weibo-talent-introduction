"use strict";

// Run the real component against the existing parsed DOM, controlled clocks and a
// raw-JSON CAS server. No production internals are exported or replaced.
const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("node:assert/strict");
const { describe, it } = require("node:test");
const harnessPath = path.join(__dirname, "mailboxChatBehavior.test.js");
const harnessSource = fs.readFileSync(harnessPath, "utf8");
const harness = { require, __dirname, console, URL, URLSearchParams, setImmediate };
vm.createContext(harness);
vm.runInContext(harnessSource.slice(0, harnessSource.indexOf('describe("mailbox chat mount')) +
    '\nglobalThis.helpers = { createDraftServer, createDom, mountChat, bootChat, flush, click, inputEvent, setEditorContent, expertA, expertB, messagesA, contactA, chipButton, toggleOpen, extractFn };', harness);
const H = harness.helpers;
const ROOT = path.join(__dirname, "../../main/resources/static");
const appSource = fs.readFileSync(path.join(ROOT, "app.js"), "utf8");
const json = (value) => JSON.parse(JSON.stringify(value));
const inbound = (processingId = 101, contactId = 1) => ({ contactId, kind: "INBOUND", processingId, accountScope: "acc1" });
const outbound = (contactId = 2, accountScope = "") => ({ contactId, kind: "OUTBOUND", processingId: 0, accountScope });
const content = (text = "server body", extra = {}) => Object.assign({ subject: "server subject", text, html: "<b>" + text + "</b>", context: { schemaVersion: 1 } }, extra);
const editor = (ctx) => ctx.host.querySelector('[aria-label="人工回复正文"]');
const subject = (ctx) => ctx.host.querySelector('input[aria-label="回复主题"]');
const status = (ctx) => ctx.host.querySelector('[data-role="draft-save-status"]');
const action = (ctx, name) => {
    const buttons = ctx.host.querySelectorAll('[data-action="' + name + '"]');
    return buttons.find((button) => {
        for (let node = button; node && node !== ctx.host; node = node.parentNode) if (node.hidden) return false;
        return true;
    }) || buttons[0] || null;
};
const puts = (server) => server.requests.filter((entry) => entry.method === "PUT");
const deletes = (server) => server.requests.filter((entry) => entry.method === "DELETE");
const read = (server, target = inbound()) => server.get("admin", target);
function deferred() { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b; }); return { promise, resolve, reject }; }
function clock() {
    let now = 0, sequence = 0;
    const pending = new Map();
    return {
        setTimeout(fn, delay) { const id = ++sequence; pending.set(id, { fn, due: now + (Number(delay) || 0) }); return id; },
        clearTimeout(id) { pending.delete(id); },
        advance(ms) {
            const end = now + ms;
            while (true) {
                const next = [...pending.entries()].filter(([, item]) => item.due <= end).sort((a, b) => a[1].due - b[1].due)[0];
                if (!next) break;
                now = next[1].due; pending.delete(next[0]); next[1].fn();
            }
            now = end;
        }
    };
}
async function boot(server, options = {}, selected = 1) {
    const ctx = await H.bootChat(Object.assign({ draftServer: server, conversations: { items: [H.expertA(), H.expertB()], total: 2 }, messages: H.messagesA(), contact: H.contactA() }, options));
    if (selected) { H.click(ctx.host.querySelector('[data-action="mc-select-expert"][data-contact-id="' + selected + '"]')); await H.flush(); }
    return ctx;
}
function type(ctx, text) { editor(ctx).innerText = text; H.inputEvent(editor(ctx)); }
async function save(ctx) { await ctx.sandbox.MailboxChat.flushDrafts(ctx.host); await H.flush(); }
function firstHold(server, method = "PUT") {
    const hold = deferred();
    let execute;
    server.intercept = (entry, next) => {
        if (entry.method === method && !execute) { execute = next; return hold.promise; }
        return next();
    };
    return { release() { hold.resolve(execute()); }, reject(error) { hold.reject(error); }, committed() { return execute(); } };
}

describe("server drafts I-1/I-2/I-3: authority, sequence and navigation", () => {
    it("pure browsing creates no PUT; GET failure locks editing/send and cannot overwrite a remote draft", async () => {
        const server = H.createDraftServer();
        server.seed("admin", inbound(), content());
        server.intercept = (entry, next) => entry.method === "GET" && entry.url.includes("/target?") ? Promise.reject(new Error("offline")) : next();
        const ctx = await boot(server);
        assert.match(ctx.host.textContent, /草稿加载失败，请重试/);
        assert.equal(action(ctx, "mc-send-manual").disabled, true);
        assert.equal(puts(server).length, 0);
        assert.equal(read(server).content.text, "server body");
        server.intercept = null;
        H.click(action(ctx, "mc-reload-draft")); await H.flush();
        assert.equal(editor(ctx).innerText, "server body");
        assert.equal(puts(server).length, 0);
    });

    it("800ms debounce merges true inputs; ACK of A cannot clear the newer B sequence", async () => {
        const server = H.createDraftServer(), time = clock();
        const ctx = await boot(server, { clock: time });
        time.advance(1000); await H.flush();
        assert.equal(puts(server).length, 0, "default subject alone is not a draft");
        type(ctx, "first"); time.advance(799); await H.flush();
        assert.equal(puts(server).length, 0);
        type(ctx, "A"); time.advance(799); await H.flush();
        assert.equal(puts(server).length, 0);
        const hold = firstHold(server);
        const liveStatus = status(ctx);
        time.advance(1); await H.flush();
        assert.equal(puts(server).length, 1);
        assert.equal(puts(server)[0].body.expectedVersion, 0);
        assert.equal(puts(server)[0].body.content.text, "A");
        assert.notEqual(status(ctx).dataset.state, "saved");
        type(ctx, "B"); time.advance(800); await H.flush();
        assert.equal(puts(server).length, 1, "one PUT per target in flight");
        hold.release(); await H.flush(); await save(ctx);
        assert.equal(editor(ctx).innerText, "B");
        assert.equal(puts(server).length, 2);
        assert.equal(puts(server)[1].body.expectedVersion, 1);
        assert.equal(read(server).content.text, "B");
        assert.equal(status(ctx).textContent, "已保存到服务器");
        assert.equal(status(ctx), liveStatus, "aria-live save updates must not rebuild the compose DOM");
    });

    for (const acknowledgeB of [false, true]) it("captures A→B→A " + (acknowledgeB ? "after B ACK" : "before debounce"), async () => {
        const server = H.createDraftServer(), time = clock();
        server.seed("admin", inbound(), content("A"));
        const ctx = await boot(server, { clock: time });
        const originalHtml = editor(ctx).innerHTML;
        type(ctx, "B");
        if (acknowledgeB) { time.advance(800); await H.flush(); assert.equal(read(server).content.text, "B"); }
        H.setEditorContent(editor(ctx), originalHtml, "A"); H.inputEvent(editor(ctx));
        assert.notEqual(status(ctx).dataset.state, "saved");
        time.advance(800); await H.flush(); await save(ctx);
        assert.equal(puts(server).at(-1).body.content.text, "A");
        assert.equal(read(server).content.text, "A");
        assert.equal(editor(ctx).innerText, "A");
        assert.equal(status(ctx).textContent, "已保存到服务器");
        const count = puts(server).length;
        H.inputEvent(editor(ctx)); await save(ctx);
        assert.equal(puts(server).length, count, "unchanged content remains deduplicated");
        ctx.sandbox.MailboxChat.unmount(ctx.host);
        const restored = await boot(server);
        assert.equal(editor(restored).innerText, "A");
        assert.equal(status(restored).dataset.state, "saved");
    });

    it("clearing an edited initial form captures its empty baseline instead of saving the prior edit", async () => {
        const server = H.createDraftServer(), time = clock(), ctx = await boot(server, { clock: time });
        const initialHtml = editor(ctx).innerHTML, initialText = editor(ctx).innerText;
        type(ctx, "temporary edit");
        H.setEditorContent(editor(ctx), initialHtml, initialText); H.inputEvent(editor(ctx));
        time.advance(800); await H.flush(); await save(ctx);
        assert.equal(puts(server).at(-1).body.content.text, initialText);
        assert.equal(read(server).content.text, initialText);
        assert.equal(read(server).state, "ACTIVE");
        assert.equal(status(ctx).dataset.state, "saved");
    });

    it("clearing an existing draft persists empty ACTIVE content and never DELETEs", async () => {
        const server = H.createDraftServer(); server.seed("admin", inbound(), content());
        const ctx = await boot(server);
        subject(ctx).value = ""; H.inputEvent(subject(ctx)); type(ctx, ""); await save(ctx);
        assert.equal(read(server).state, "ACTIVE");
        assert.equal(read(server).content.subject, ""); assert.equal(read(server).content.text, "");
        assert.equal(deletes(server).length, 0);
    });

    it("selecting another expert waits for the actual last PUT; failure leaves the current editor and pending unload prompt", async () => {
        const server = H.createDraftServer(), ctx = await boot(server), hold = firstHold(server);
        type(ctx, "last bytes");
        H.click(ctx.host.querySelector('[data-action="mc-select-expert"][data-contact-id="2"]')); await H.flush();
        assert.equal(editor(ctx).innerText, "last bytes"); assert.equal(puts(server).length, 1);
        const event = { prevented: false, preventDefault() { this.prevented = true; } };
        ctx.dispatchGlobal("beforeunload", event); assert.equal(event.prevented, true);
        hold.reject(new Error("offline")); await H.flush();
        assert.equal(editor(ctx).innerText, "last bytes");
        assert.match(status(ctx).textContent, /保存失败，内容尚未同步到服务器/);
        assert.equal(ctx.sandbox.MailboxChat.hasPendingDrafts(ctx.host), true);
        server.intercept = null;
        H.click(action(ctx, "mc-retry-draft")); await H.flush(); await save(ctx);
        assert.equal(read(server).content.text, "last bytes");
        assert.equal(deletes(server).length, 0);
    });

    it("fresh browser reads confirmed server content instead of module memory or browser storage", async () => {
        const server = H.createDraftServer(), a = await boot(server);
        H.setEditorContent(editor(a), '<b>cross device</b>', 'cross device'); H.inputEvent(editor(a)); await save(a);
        a.sandbox.MailboxChat.unmount(a.host);
        const b = await boot(server);
        assert.equal(editor(b).innerText, "cross device"); assert.match(editor(b).innerHTML, /<b>cross device<\/b>/);
        assert.equal(puts(server).length, 1);
        const source = fs.readFileSync(path.join(ROOT, "mailbox-chat.js"), "utf8");
        assert.doesNotMatch(source, /localStorage\s*\.\s*(?:setItem|removeItem|clear)\s*\(|indexedDB\s*\.\s*open\s*\(/, "no persistent browser draft fallback writes");
    });
});

describe("server drafts I-4/I-7: CAS, lost ACK and explicit discard", () => {
    it("two windows conflict without blind retry; cancelled reload preserves B and confirmed reload restores A", async () => {
        const server = H.createDraftServer(); server.seed("admin", inbound(), content("base"));
        let confirmed = null;
        const a = await boot(server), b = await boot(server, { dialogResults: { confirm: () => confirmed } });
        type(a, "window A"); await save(a);
        type(b, "window B"); await assert.rejects(b.sandbox.MailboxChat.flushDrafts(b.host)); await H.flush();
        const count = puts(server).length;
        assert.equal(status(b).dataset.state, "conflict"); assert.equal(action(b, "mc-send-manual").disabled, true);
        b.runTimers(); await H.flush(); assert.equal(puts(server).length, count);
        H.click(action(b, "mc-reload-draft")); await H.flush(); assert.equal(editor(b).innerText, "window B");
        confirmed = true; H.click(action(b, "mc-reload-draft")); await H.flush();
        assert.equal(editor(b).innerText, "window A"); assert.equal(read(server).content.text, "window A");
        assert.match(b.calls.dialogs.at(-1).options.message, /重新加载将丢弃本窗口尚未保存的修改/);
    });

    it("lost response recognizes an identical committed snapshot after CAS instead of overwriting it", async () => {
        const server = H.createDraftServer(), ctx = await boot(server);
        let lost = false;
        server.intercept = (entry, execute) => {
            if (entry.method === "PUT" && !lost) { lost = true; execute(); throw new Error("response lost"); }
            return execute();
        };
        type(ctx, "committed despite disconnect"); await assert.rejects(ctx.sandbox.MailboxChat.flushDrafts(ctx.host));
        H.click(action(ctx, "mc-retry-draft")); await H.flush();
        assert.equal(read(server).version, 1, "matching ACK must not create another version");
        assert.equal(status(ctx).dataset.state, "saved"); assert.equal(editor(ctx).innerText, "committed despite disconnect");
        assert.ok(server.requests.some((entry) => entry.method === "GET" && entry.url.includes("/target?")));
    });

    it("cancel discard makes zero DELETE; confirmation sends one versioned DELETE and clears only after terminal ACK", async () => {
        const server = H.createDraftServer(); server.seed("admin", inbound(), content());
        let confirmed = null;
        const ctx = await boot(server, { dialogResults: { confirm: () => confirmed } });
        type(ctx, "unsaved local edit"); H.click(action(ctx, "mc-discard-draft")); await H.flush();
        assert.equal(deletes(server).length, 0); assert.equal(editor(ctx).innerText, "unsaved local edit");
        const hold = firstHold(server, "DELETE"); confirmed = true;
        H.click(action(ctx, "mc-discard-draft")); await H.flush();
        assert.equal(deletes(server).length, 1); assert.match(deletes(server)[0].url, /expectedVersion=1/);
        assert.equal(editor(ctx).innerText, "unsaved local edit");
        assert.match(ctx.calls.dialogs.at(-1).options.message, /确定放弃这份草稿？放弃后无法恢复。/);
        hold.release(); await H.flush(); assert.equal(read(server).state, "DISCARDED");
        assert.notEqual(editor(ctx).innerText, "unsaved local edit");
        assert.equal(puts(server).length, 0, "explicit discard need not persist unconfirmed edits first");
    });

    for (const failure of ["offline", "conflict"]) it("DELETE " + failure + " preserves local content and server draft", async () => {
        const server = H.createDraftServer(); server.seed("admin", inbound(), content());
        const ctx = await boot(server, { dialogResult: true }); type(ctx, "keep this");
        server.intercept = (entry, execute) => {
            if (entry.method === "DELETE") {
                if (failure === "conflict") { const row = [...server.rows.values()][0]; row.version += 1; return execute(); }
                throw new Error("offline");
            }
            return execute();
        };
        H.click(action(ctx, "mc-discard-draft")); await H.flush();
        assert.equal(editor(ctx).innerText, "keep this"); assert.equal(read(server).state, "ACTIVE"); assert.equal(deletes(server).length, 1);
    });
});

describe("server drafts I-5/I-8: complete restoration and server paging", () => {
    for (const kind of ["INBOUND", "OUTBOUND"]) it(kind + " restores sanitized rich HTML, QA, requestId and saved target", async () => {
        const server = H.createDraftServer(), target = kind === "INBOUND" ? inbound(90) : outbound();
        const rich = '<p onclick="evil()"><b>safe</b><a href="https://safe.test">link</a><a href="javascript:evil()">bad</a><script>evil()</script></p>';
        const row = server.seed("admin", target, content("safelinkbad", { html: rich, context: { schemaVersion: 1,
            requestId: "saved-request-123", qa: { ragFactCodes: ["KB-1"], ragCorpusFingerprint: "fp", baselineText: "safelinkbad" },
            followUpAnchorMailRecordId: kind === "OUTBOUND" ? 88 : null } }));
        const ctx = await boot(server, {}, 0); H.click(H.chipButton(ctx, "drafts")); await H.flush();
        H.click(ctx.host.querySelector('[data-action="mc-open-draft"][data-draft-id="' + row.id + '"]')); await H.flush();
        assert.equal(subject(ctx).value, "server subject"); assert.match(editor(ctx).innerHTML, /<b>safe<\/b>/);
        assert.match(editor(ctx).innerHTML, /href="https:\/\/safe.test"/); assert.doesNotMatch(editor(ctx).innerHTML, /script|onclick|javascript:/);
        assert.equal(ctx.host.querySelector('[data-role="manual-compose"]').dataset.targetKey, kind === "INBOUND" ? "1:90:acc1" : "2:OUTBOUND:");
        assert.equal(puts(server).length, 0, "restoration is read-only until actual editing");
        type(ctx, "changed"); await save(ctx);
        assert.equal(read(server, target).content.context.requestId, null, "a real content edit invalidates the restored idempotency key");
        assert.deepEqual(json(read(server, target).content.context.qa.ragFactCodes), ["KB-1"]);
    });

    it("draft list uses server total/page, escapes dynamic text and shows exact empty/orphan card roles", async () => {
        const server = H.createDraftServer();
        for (let i = 1; i <= 22; i++) server.seed("admin", inbound(100 + i, i), content(i === 21 ? "" : "preview " + i, { subject: i === 21 ? "" : '<b>subject ' + i + '</b>' }), { expertName: '<script>name</script>', contactExists: i !== 21 });
        const ctx = await boot(server, {}, 0); H.click(H.chipButton(ctx, "drafts")); await H.flush();
        assert.equal(ctx.host.querySelector('[data-role="draft-total"]').textContent, "22");
        assert.equal(ctx.host.querySelectorAll(".mailbox-draft-card").length, 20);
        assert.equal(ctx.host.querySelector('[data-role="draft-scope-note"]').textContent, "草稿仅按账号和搜索条件筛选");
        assert.equal(ctx.host.querySelector('[data-role="draft-subject"] b'), null);
        H.click(action(ctx, "mc-page-next")); await H.flush();
        assert.equal(ctx.host.querySelectorAll(".mailbox-draft-card").length, 2);
        assert.match(ctx.host.textContent, /无主题/); assert.match(ctx.host.textContent, /空白草稿/); assert.match(ctx.host.textContent, /原专家已不存在/);
        const listRequest = server.requests.filter((entry) => /\/drafts\?/.test(entry.url)).at(-1);
        const q = new URL(listRequest.url, "https://test.invalid").searchParams;
        assert.equal(q.get("page"), "1"); assert.equal(q.get("size"), "20");
        assert.equal(q.get("pendingOnly"), null); assert.equal(q.get("followed"), null);
    });

    it("ordinary paging remains server paging and uses one batch summaries request for current-page contacts", async () => {
        const server = H.createDraftServer(); server.seed("admin", inbound(), content());
        const ctx = await boot(server, {}, 0);
        const summary = server.requests.filter((entry) => entry.url.includes("/summaries?"));
        assert.ok(summary.length > 0);
        const q = new URL(summary.at(-1).url, "https://test.invalid").searchParams;
        assert.deepEqual(q.getAll("contactIds").flatMap((value) => value.split(",")).map(Number).sort(), [1, 2]);
        const cards = ctx.host.querySelectorAll(".mc-person"); assert.equal(cards.length, 2);
        assert.equal(cards.find((card) => card.dataset.contactId === "1").querySelector('[data-role="contact-draft-count"]').textContent, "草稿 1");
        assert.equal(cards.find((card) => card.dataset.contactId === "2").querySelector('[data-role="contact-draft-count"]'), null);
        assert.ok(summary.every((entry) => !/contactIds=1(?:&|$)/.test(entry.url)), "no per-card N+1");
    });

    it("owner switch invalidates late A response, clears visible A bytes and never dispatches queued A work under B", async () => {
        const server = H.createDraftServer(), auth = { authenticated: true, username: "admin" };
        const ctx = await boot(server, { authMe: auth }), hold = firstHold(server);
        type(ctx, "private A"); const pending = ctx.sandbox.MailboxChat.flushDrafts(ctx.host).catch(() => {}); await H.flush();
        type(ctx, "newer private A"); auth.username = "user-B";
        ctx.sandbox.MailboxChat.unmount(ctx.host); ctx.sandbox.MailboxChat.mount(ctx.host, { filters: {} }); await H.flush();
        hold.release(); await pending; await H.flush();
        assert.doesNotMatch(ctx.host.textContent, /private A/);
        assert.ok(puts(server).every((entry) => entry.owner === "admin"), "no A bytes sent with B Session");
        assert.equal(deletes(server).length, 0);
    });

    it("a session change without remount is checked before PUT dispatch and cannot save A bytes under B", async () => {
        const server = H.createDraftServer(), auth = { authenticated: true, username: "admin" };
        const ctx = await boot(server, { authMe: auth });
        type(ctx, "private before session change");
        auth.username = "user-B";
        await assert.rejects(ctx.sandbox.MailboxChat.flushDrafts(ctx.host), /登录用户已变化/);
        await H.flush();
        assert.equal(puts(server).length, 0);
        assert.doesNotMatch(ctx.host.textContent, /private before session change/);
        assert.equal(deletes(server).length, 0);
    });

    for (const seam of ["resolve", "applyOptions"]) it(seam + " clears A cards/counts while B list is delayed and rejects a stale A response", async () => {
        const server = H.createDraftServer(), auth = { authenticated: true, username: "admin" };
        server.seed("admin", inbound(), content("private A preview", { subject: "private A subject" }));
        server.seed("user-B", inbound(), content("B preview", { subject: "B subject" }));
        const ctx = await boot(server, { authMe: auth });
        H.click(H.chipButton(ctx, "drafts")); await H.flush();
        assert.match(ctx.host.textContent, /private A preview/);
        const staleA = deferred(), delayedB = deferred();
        let oldResult, bRequests = 0;
        server.intercept = (entry, execute) => {
            if (/\/drafts\?/.test(entry.url) && entry.owner === "admin") {
                oldResult = execute(); return staleA.promise;
            }
            if (/\/drafts\?/.test(entry.url) && entry.owner === "user-B") {
                bRequests += 1; return delayedB.promise.then(execute);
            }
            return execute();
        };
        const oldRefresh = ctx.api.refresh(); await H.flush();
        auth.username = "user-B";
        if (seam === "applyOptions") {
            ctx.sandbox.MailboxChat.mount(ctx.host, { sessionUser: "user-B", filters: {} });
        } else {
            type(ctx, "private A pending edit");
            await assert.rejects(ctx.sandbox.MailboxChat.flushDrafts(ctx.host), /登录用户已变化/);
        }
        await H.flush();
        assert.ok(bRequests > 0, "B list request is actually pending");
        const cleared = () => {
            assert.doesNotMatch(ctx.host.textContent, /private A (?:preview|subject|pending edit)/);
            assert.equal(ctx.host.querySelectorAll(".mailbox-draft-card").length, 0);
            assert.equal(ctx.host.querySelector('[data-role="draft-total"]').textContent, "0");
        };
        cleared();
        staleA.resolve(oldResult); await oldRefresh; await H.flush(); cleared();
        delayedB.resolve(); await H.flush();
        assert.match(ctx.host.textContent, /B preview/);
        assert.doesNotMatch(ctx.host.textContent, /private A/);
        assert.equal(ctx.host.querySelectorAll(".mailbox-draft-card").length, 1);
        assert.equal(ctx.host.querySelector('[data-role="draft-total"]').textContent, "1");
    });
});

describe("server drafts I-2/I-6: adopted writes and version-aligned send", () => {
    it("QA adoption is persisted and unanchored INBOUND retains its original payload plus version-aligned draftRef", async () => {
        const server = H.createDraftServer(), ctx = await boot(server);
        H.toggleOpen(ctx.host.querySelector('.mc-section[data-section="workbench"]')); await H.flush();
        await ctx.calls.workbenchMounts[0].callbacks.onComplete({ renderedDraftText: "adopted", text: "adopted", usedFactCodes: ["KB-1"], ragCorpusFingerprint: "fp" });
        await save(ctx);
        assert.deepEqual(json(read(server).content.context.qa.ragFactCodes), ["KB-1"]);
        const seen = [];
        ctx.sandbox.mcHostSendRichReply = async (processingId, body) => {
            seen.push({ processingId, body: json(body), row: read(server) });
            return false;
        };
        H.click(action(ctx, "mc-send-manual")); await H.flush();
        assert.equal(seen.length, 1); assert.equal(read(server).state, "ACTIVE");
        const captured = seen[0];
        assert.equal(captured.body.draftRef.id, captured.row.id); assert.equal(captured.body.draftRef.version, captured.row.version);
        assert.equal(captured.body.requestId, undefined, "unanchored INBOUND keeps its original request contract");
        assert.equal(captured.row.content.context.requestId, null);
        assert.equal(captured.body.textBody, captured.row.content.text);
        assert.equal(editor(ctx).innerText, "adopted"); assert.equal(deletes(server).length, 0);
    });

    it("failed flush never calls send; HTTP exception after authoritative SENT never revives old text", async () => {
        const server = H.createDraftServer(), ctx = await boot(server);
        type(ctx, "send me"); server.intercept = (entry, next) => entry.method === "PUT" ? Promise.reject(new Error("offline")) : next();
        H.click(action(ctx, "mc-send-manual")); await H.flush(); assert.equal(ctx.calls.sendRich.length, 0);
        server.intercept = null; await save(ctx);
        ctx.sandbox.mcHostSendRichReply = async (id, body) => { server.sent("admin", body.draftRef); throw new Error("HTTP response lost after commit"); };
        H.click(action(ctx, "mc-send-manual")); await H.flush();
        assert.equal(read(server).state, "SENT"); assert.notEqual(editor(ctx).innerText, "send me");
        const count = puts(server).length; ctx.runTimers(); await H.flush(); assert.equal(puts(server).length, count);
        assert.equal(deletes(server).length, 0);
    });

    it("send completion preserves newer unacknowledged input; only explicit continue reopens the terminal version", async () => {
        const server = H.createDraftServer(), ctx = await boot(server), sending = deferred();
        let ref;
        ctx.sandbox.mcHostSendRichReply = async (id, body) => { ref = json(body.draftRef); return sending.promise; };
        type(ctx, "sent version"); H.click(action(ctx, "mc-send-manual")); await H.flush();
        assert.ok(ref); assert.equal(action(ctx, "mc-discard-draft").disabled, true);
        type(ctx, "new unsaved version"); server.sent("admin", ref); sending.resolve(true); await H.flush();
        assert.equal(editor(ctx).innerText, "new unsaved version"); assert.match(ctx.host.textContent, /邮件已发送，新修改尚未保存/);
        const count = puts(server).length; ctx.runTimers(); await H.flush(); assert.equal(puts(server).length, count);
        H.click(action(ctx, "mc-continue-draft")); await H.flush(); await save(ctx);
        assert.equal(read(server).state, "ACTIVE"); assert.equal(read(server).content.text, "new unsaved version");
        assert.equal(puts(server).at(-1).body.reopen, true); assert.equal(puts(server).at(-1).body.expectedVersion, ref.version + 1);
    });
});

describe("server drafts S-1/S-2/S-3/S-4 frozen DOM and CSS", () => {
    it("copies approved CSS literally without altering mailbox-chat.css or inserting inline styles/dialogs", async () => {
        const css = fs.readFileSync(path.join(ROOT, "styles.css"), "utf8");
        const rules = [
            '.mail-chat .mailbox-draft-count{display:inline-flex;align-items:center;margin-left:4px;padding:0 5px;border:1px solid #bfdbfe;border-radius:5px;background:#eff6ff;color:#1e40af;font-size:10px;line-height:16px;white-space:nowrap}',
            '.mail-chat .mc-person.mailbox-draft-card{grid-template-columns:minmax(0,1fr)}',
            '.mail-chat .mailbox-draft-status-row{display:flex;align-items:center;flex-wrap:wrap;gap:8px;min-height:32px}',
            '.mail-chat .mailbox-draft-status{flex:1;min-width:0;color:#64748b;font-size:11px;line-height:1.6;overflow-wrap:anywhere}',
            '.mail-chat .mailbox-draft-status[data-state=saved]{color:#059669}',
            '.mail-chat .mailbox-draft-status[data-state=error],.mail-chat .mailbox-draft-status[data-state=conflict]{color:#be123c}',
            '.mail-chat .mailbox-draft-status[data-state=saving],.mail-chat .mailbox-draft-status[data-state=dirty]{color:#b45309}'
        ];
        for (const rule of rules) assert.equal(css.split(rule).length - 1, 1, rule);
        const server = H.createDraftServer(), ctx = await boot(server);
        const filters = ctx.host.querySelector(".mc-filters"); assert.equal(filters.className, "mc-filters");
        assert.deepEqual(json(filters.querySelectorAll(".mc-filter").map((el) => el.dataset.chip).slice(0, 3)), ["all", "drafts", "provided"]);
        assert.equal(ctx.host.querySelector('[data-role="draft-total"]').textContent, "0");
        const row = status(ctx).parentNode;
        assert.equal(row.className, "mailbox-draft-status-row"); assert.equal(status(ctx).getAttribute("role"), "status");
        assert.equal(status(ctx).getAttribute("aria-live"), "polite");
        const compose = row.parentNode, children = compose.children;
        assert.ok(children.indexOf(row) > children.indexOf(ctx.host.querySelector('[data-role="outbound-draft-files"]')));
        assert.ok(children.indexOf(row) < children.indexOf(ctx.host.querySelector(".mc-compose-footer")));
        for (const name of ["mc-retry-draft", "mc-reload-draft", "mc-continue-draft", "mc-discard-draft"]) assert.ok(action(ctx, name));
        assert.equal(action(ctx, "mc-discard-draft").className, "button danger");
        assert.equal(row.querySelectorAll("[style]").length, 0); assert.equal(ctx.host.querySelectorAll("dialog").length, 0);
        const index = fs.readFileSync(path.join(ROOT, "index.html"), "utf8");
        assert.match(index, /id="actionDialog"/);
        const assetKeys = [...index.matchAll(/(?:href|src)="([^"]+)\?v=([^"]+)"/g)];
        const styleAsset = assetKeys.find((match) => match[1] === "styles.css");
        const chatAsset = assetKeys.find((match) => match[1] === "mailbox-chat.js");
        const appAsset = assetKeys.find((match) => match[1] === "app.js");
        assert.ok(styleAsset && chatAsset && appAsset, "real page registers draft component and host scripts");
        assert.equal(chatAsset[2], styleAsset[2]); assert.equal(appAsset[2], styleAsset[2]);
    });
});

describe("server drafts I-3: real app navigation and logout closures", () => {
    function lifecycle(flush) {
        const calls = [], { doc, host: container } = H.createDom();
        container.innerHTML = '<div id="mailboxList"></div><select id="mobileCoreView"></select><button id="logoutBtn"></button>' +
            '<h1 id="viewTitle"></h1><p id="viewSubtitle"></p>' +
            '<button class="nav-tab" data-view="mailbox"></button><button class="nav-tab" data-view="contacts"></button><button class="nav-tab" data-view="tasks"></button>' +
            '<section class="view" id="view-mailbox"></section><section class="view" id="view-contacts"></section><section class="view" id="view-tasks"></section>';
        const host = doc.getElementById("mailboxList"), mobile = doc.getElementById("mobileCoreView");
        mobile.value = "mailbox";
        const sandbox = {
            document: doc,
            state: { view: "mailbox", monitoring: { autoRefreshTimer: null, loadSeq: 0, activitySeq: 0, openTracking: { requestSeq: 0, settingsSeq: 0 } } },
            viewMeta: { mailbox: ["Mailbox", "Mail"], contacts: ["Contacts", "Experts"], tasks: ["Tasks", "Jobs"] },
            mobileContactPresentation: { generation: 0 },
            taskActivityState: { listRequestSequence: 0, started: false },
            $: (selector) => doc.querySelector(selector),
            $$: (selector) => doc.querySelectorAll(selector),
            mailboxChatAvailable: () => true,
            MailboxChat: { hasPendingDrafts: () => true, flushDrafts: flush },
            refreshCurrentView: () => calls.push(["view", sandbox.state.view]),
            unmountAiTrainingTrustReply: () => {},
            unmountMailboxTrustReplyHosts: () => {},
            unmountMailboxChatHosts: () => calls.push(["unmount"]),
            closeOpenTrackingDetail: () => {},
            closeTaskActivityDetail: () => {},
            resumeProgressPollingIfNeeded: () => {},
            stopTaskWatcher: () => {},
            clearTimeout: () => {},
            showStatus: (message, type) => calls.push(["status", message, type]),
            api: async (url, options) => calls.push(["api", url, options.method]),
            location: { reload: () => calls.push(["reload"]) }
        };
        vm.createContext(sandbox);
        for (const name of ["flushMailboxChatDrafts", "setView", "bindAuthEvents"]) vm.runInContext(H.extractFn(name, appSource), sandbox);
        sandbox.bindAuthEvents();
        return { sandbox, calls, logout: () => [...doc.getElementById("logoutBtn").listeners.get("click")][0](), host, mobile };
    }

    it("clean navigation stays synchronous; pending navigation awaits flush and applies only the latest requested view", async () => {
        const pending = [];
        const ctx = lifecycle(() => { const hold = deferred(); pending.push(hold); return hold.promise; });
        ctx.sandbox.MailboxChat.hasPendingDrafts = () => false;
        const result = ctx.sandbox.setView("contacts");
        assert.equal(result, undefined); assert.equal(ctx.sandbox.state.view, "contacts"); assert.equal(pending.length, 0);
        ctx.sandbox.state.view = "mailbox"; ctx.sandbox.MailboxChat.hasPendingDrafts = () => true;
        const first = ctx.sandbox.setView("contacts"), second = ctx.sandbox.setView("tasks");
        assert.equal(ctx.sandbox.state.view, "mailbox"); assert.equal(pending.length, 2);
        pending[1].resolve(); await second; assert.equal(ctx.sandbox.state.view, "tasks");
        pending[0].resolve(); await first; assert.equal(ctx.sandbox.state.view, "tasks", "old flush must not jump back");
    });

    it("failed navigation and logout stay on mailbox without API logout/reload; successful logout follows flush", async () => {
        const pending = [];
        const ctx = lifecycle(() => { const hold = deferred(); pending.push(hold); return hold.promise; });
        const navigation = ctx.sandbox.setView("contacts");
        pending[0].reject(new Error("offline")); await navigation;
        assert.equal(ctx.sandbox.state.view, "mailbox"); assert.equal(ctx.mobile.value, "mailbox");
        const failedLogout = ctx.logout(); assert.equal(ctx.calls.filter((entry) => entry[0] === "api").length, 0);
        pending[1].reject(new Error("offline")); await failedLogout;
        assert.equal(ctx.calls.filter((entry) => entry[0] === "api" || entry[0] === "reload").length, 0);
        const logout = ctx.logout(); assert.equal(ctx.calls.filter((entry) => entry[0] === "api").length, 0);
        pending[2].resolve(); await logout;
        assert.deepEqual(ctx.calls.filter((entry) => entry[0] === "api" || entry[0] === "reload"), [["api", "/api/auth/logout", "POST"], ["reload"]]);
    });
});

describe("server drafts I-4/I-5/I-6/I-7: terminal, retarget and unknown boundaries", () => {
    async function openSaved(server, row, options) {
        const ctx = await boot(server, options || {}, 0);
        H.click(H.chipButton(ctx, "drafts")); await H.flush();
        H.click(ctx.host.querySelector('[data-action="mc-open-draft"][data-draft-id="' + row.id + '"]')); await H.flush();
        return ctx;
    }

    it("saved old target is not replaced by latestInbound and an existing new target is opened without overwriting either", async () => {
        const server = H.createDraftServer();
        const old = server.seed("admin", inbound(90), content("original target"));
        const newer = server.seed("admin", inbound(), content("existing new target"));
        const ctx = await openSaved(server, old);
        assert.equal(ctx.host.querySelector('[data-role="manual-compose"]').dataset.targetKey, "1:90:acc1");
        assert.equal(editor(ctx).innerText, "original target");
        assert.equal(ctx.host.querySelector('[data-role="draft-target-warning"]').textContent, "有新来信，当前仍在编辑原来信草稿");
        H.click(action(ctx, "mc-retarget-draft")); await H.flush();
        assert.equal(action(ctx, "mc-open-existing-draft").hidden, false);
        assert.equal(puts(server).length, 0); assert.equal(deletes(server).length, 0);
        H.click(action(ctx, "mc-open-existing-draft")); await H.flush();
        assert.equal(editor(ctx).innerText, "existing new target");
        assert.equal(read(server, inbound(90)).content.text, "original target");
        assert.equal(read(server).id, newer.id); assert.equal(read(server).content.text, "existing new target");
    });

    it("confirmed new-target copy persists a separate draft and clears requestId without deleting the original", async () => {
        const server = H.createDraftServer();
        const old = server.seed("admin", inbound(90), content("copy source", { context: {
            schemaVersion: 1, requestId: "old-id", qa: { ragFactCodes: ["KB-1"], ragCorpusFingerprint: "fp", baselineText: "copy source" }
        } }));
        const ctx = await openSaved(server, old, { dialogResult: true });
        H.click(action(ctx, "mc-retarget-draft")); await H.flush(); await save(ctx);
        assert.match(ctx.calls.dialogs.at(-1).options.message, /为新来信另建草稿，原草稿会保留，是否继续？/);
        assert.equal(read(server, inbound(90)).content.text, "copy source");
        assert.equal(read(server).content.text, "copy source"); assert.notEqual(read(server).id, old.id);
        assert.equal(read(server).content.context.requestId, null);
        assert.equal(deletes(server).length, 0);
    });

    it("UNKNOWN remains visible, blocks repeat send and explains that discard cannot retract a possible delivery", async () => {
        const server = H.createDraftServer(); server.seed("admin", inbound(), content("possibly delivered"), {
            sendAttemptId: 44, sendVersion: 1, sendAttemptStatus: "DELIVERY_UNKNOWN"
        });
        const ctx = await boot(server, { dialogResult: true });
        const note = ctx.host.querySelector('[data-role="draft-send-unknown"]');
        assert.equal(note.hidden, false); assert.equal(note.textContent, "发送结果待确认，草稿已保留，请勿重复发送");
        assert.equal(action(ctx, "mc-send-manual").disabled, true);
        H.click(action(ctx, "mc-send-manual")); await H.flush(); assert.equal(ctx.calls.sendRich.length, 0);
        H.click(action(ctx, "mc-discard-draft")); await H.flush();
        assert.match(ctx.calls.dialogs.at(-1).options.message, /这不会撤回可能已发送的邮件。/);
        assert.equal(deletes(server).length, 1); assert.equal(read(server).state, "DISCARDED");
    });

    it("orphan target remains readable and discardable but does not permit editing or sending", async () => {
        const server = H.createDraftServer(), row = server.seed("admin", inbound(), content("orphan bytes"), { contactExists: false });
        const ctx = await openSaved(server, row, { dialogResult: true });
        assert.equal(subject(ctx).value, "server subject"); assert.equal(subject(ctx).readOnly, true);
        assert.equal(editor(ctx).getAttribute("contenteditable"), "false");
        assert.equal(ctx.host.querySelector('[data-role="draft-target-warning"]').textContent, "原专家已不存在，草稿仍为你保留，可查看或放弃。");
        assert.equal(action(ctx, "mc-send-manual").disabled, true); assert.equal(puts(server).length, 0);
        H.click(action(ctx, "mc-discard-draft")); await H.flush(); assert.equal(read(server).state, "DISCARDED");
    });
});

describe("server drafts I-4/I-5/I-6: terminal load and clean context roundtrip", () => {
    it("loading SENT without an edit never reopens or writes, including pagehide and unmount", async () => {
        const server = H.createDraftServer(); const seeded = server.seed("admin", inbound(), content());
        server.sent("admin", { id: seeded.id, version: seeded.version });
        const ctx = await boot(server);
        ctx.runTimers(); ctx.dispatchGlobal("pagehide", {}); await H.flush();
        ctx.sandbox.MailboxChat.unmount(ctx.host); await H.flush();
        assert.equal(puts(server).length, 0); assert.equal(deletes(server).length, 0);
        assert.equal(read(server).state, "SENT"); assert.equal(read(server).content, null);
    });

    it("restored OUTBOUND requestId and QA survive an unchanged failed send with exact body/ref", async () => {
        const server = H.createDraftServer();
        const target = outbound();
        const row = server.seed("admin", target, content("clean restored", { html: "<b>clean restored</b>", context: {
            schemaVersion: 1, requestId: "restored-id-123", qa: { ragFactCodes: ["KB-RESTORED"], ragCorpusFingerprint: "restored-fp", baselineText: "clean restored" }
        } }));
        const ctx = await boot(server, { sendConversationResult: false }, 2);
        H.click(action(ctx, "mc-send-manual")); await H.flush();
        assert.equal(ctx.calls.sendConversation.length, 1);
        const body = ctx.calls.sendConversation[0].body;
        assert.equal(body.requestId, "restored-id-123");
        assert.equal(body.ragFactCodes, undefined, "原 OUTBOUND 发送合同不带入站 QA 字段");
        assert.equal(body.ragCorpusFingerprint, undefined);
        assert.deepEqual(json(read(server, target).content.context.qa.ragFactCodes), ["KB-RESTORED"], "持久稿仍保留 QA 上下文");
        assert.equal(read(server, target).content.context.qa.ragCorpusFingerprint, "restored-fp");
        assert.equal(body.textBody, "clean restored"); assert.equal(body.htmlBody, "<b>clean restored</b>");
        assert.equal(body.draftRef.id, row.id); assert.equal(body.draftRef.version, read(server, target).version);
        assert.equal(read(server, target).state, "ACTIVE"); assert.equal(deletes(server).length, 0);
    });
});

describe("server drafts I-2/I-6: generated OUTBOUND requestId save ordering", () => {
    it("saves the generated conversation requestId before calling the unchanged boolean host adapter", async () => {
        const server = H.createDraftServer(), ctx = await boot(server, {}, 2), seen = [];
        const target = outbound();
        type(ctx, "outbound request bytes");
        ctx.sandbox.mcHostSendConversationRichReply = async (contactId, body) => {
            const row = read(server, target);
            seen.push({ contactId, body: json(body), row, writes: json(puts(server)) });
            return false;
        };
        H.click(action(ctx, "mc-send-manual")); await H.flush();
        assert.equal(seen.length, 1); assert.equal(seen[0].contactId, 2);
        const captured = seen[0];
        assert.ok(captured.body.requestId);
        assert.equal(captured.body.requestId, captured.row.content.context.requestId);
        assert.equal(captured.body.draftRef.id, captured.row.id); assert.equal(captured.body.draftRef.version, captured.row.version);
        assert.equal(captured.body.textBody, captured.row.content.text);
        assert.ok(captured.writes.some((entry) => entry.body.content.context.requestId === captured.body.requestId), "idempotency id reached server before send");
        assert.equal(read(server, target).state, "ACTIVE"); assert.equal(editor(ctx).innerText, "outbound request bytes");
    });
});
