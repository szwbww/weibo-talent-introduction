"use strict";
const fs = require("fs"), path = require("path"), vm = require("vm");
const assert = require("node:assert/strict"), { test } = require("node:test");
const source = fs.readFileSync(path.join(__dirname, "mailboxChatBehavior.test.js"), "utf8");
const scope = { require, __dirname, console, URL, URLSearchParams, setImmediate };
vm.createContext(scope);
vm.runInContext(source.slice(0, source.indexOf('describe("mailbox chat mount')) + '\nglobalThis.H={bootChat,flush,click,expertA,expertB,messagesA,contactA,MiniEvent,extractFn};', scope);
const H = scope.H;
const app = fs.readFileSync(path.join(__dirname, "../../main/resources/static/app.js"), "utf8");
function clock() {
    let now = 0, id = 0; const timers = new Map();
    return { now: () => now, setTimeout(fn, ms) { timers.set(++id, { fn, at: now + ms }); return id; }, clearTimeout(key) { timers.delete(key); },
        async advance(ms) { const end = now + ms; while (true) { const next = [...timers].filter(([,t])=>t.at<=end).sort((a,b)=>a[1].at-b[1].at)[0]; if (!next) break; now=next[1].at; timers.delete(next[0]); await next[1].fn(); await H.flush(); } now=end; }, size: () => timers.size };
}
function job(extra={}) { return { id: 77, revision: 1, status: "WAITING", serverNowMs: 100000, dueAtMs: 160000, originKind: "LEGACY", originId: null, canCancel: true, canRetry: false, ...extra }; }
async function boot(extra={}, selected=true) {
    const time=clock(), state={ job:job(extra), posts:[], conflict:false };
    const h=await H.bootChat({ clock:time, performance:{now:time.now}, conversations:{items:[H.expertA({failedCount:2}),H.expertB()],total:2}, contact:H.contactA(), route(url,method,raw,entry,next) {
        if (url.includes("/delayed-replies?")) return Promise.resolve([]);
        if (/\/delayed-replies\/77/.test(url)) {
            if (url.endsWith("/restore") && state.restore) return typeof state.restore === "function" ? state.restore() : Promise.resolve(state.restore);
            if (method === "POST") {
                state.posts.push(JSON.parse(raw));
                if (state.postError) return Promise.reject(state.postError);
                if (state.conflict) { state.job = { ...state.job, ...state.afterConflict }; return Promise.reject(Object.assign(Error("已超过撤回期限"), { status: 409 })); }
                state.job = { ...state.job, status: "CANCELLED", revision: 2, canCancel: false };
            }
            return Promise.resolve({...state.job,serverNowMs:100000+time.now()});
        }
        if (url.includes("/conversations/1/messages")) return Promise.resolve({...H.messagesA(),items:[...H.messagesA().items,{source:"DELAYED_MANUAL_REPLY",id:77,direction:"OUTBOUND",accountCode:"acc1",subject:"Queued",body:"Frozen",eventAt:"2026-11-05T00:00:00",sendStatus:state.job.status,outboundAttachments:state.attachments || [],delayedReply:state.missing ? null : {...state.job,serverNowMs:100000+time.now()}}]});
        return next(url,method,raw,entry);
    }});
    if (selected) { H.click(h.host.querySelector('[data-action="mc-select-expert"][data-contact-id="1"]')); await H.flush(); }
    return Object.assign(h,{time,state});
}
const button=(h,action)=>h.host.querySelector('[data-action="'+action+'"]');
const finish=h=>h.api.unmount();
test("two-stage footer cancel opens and dismisses without POST, repeated confirm posts once",async()=>{
    const h=await boot(); const card=h.host.querySelector('[data-message-key="DELAYED_MANUAL_REPLY:77"]');
    H.click(button(h,"mc-delay-cancel-open")); await H.flush(); assert.equal(h.state.posts.length,0);
    assert.equal(h.host.querySelector('[data-message-key="DELAYED_MANUAL_REPLY:77"]'),card,"opening only replaces footer actions");
    H.click(button(h,"mc-delay-confirm-dismiss")); await H.flush(); assert.equal(h.state.posts.length,0);
    H.click(button(h,"mc-delay-cancel-open")); const confirm=button(h,"mc-delay-confirm-submit"); H.click(confirm); H.click(confirm); await H.flush();
    assert.equal(h.state.posts.length,1); assert.equal(h.state.posts[0].expectedRevision,1); assert.equal(h.state.job.status,"CANCELLED"); finish(h);
});
for (const fresh of [false, true]) {
    for (const actualStatus of ["WAITING", "SENDING", "SENT"]) {
        test(`59s admitted confirmation survives timer and redraw at 61s; fresh=${fresh}, authoritative=${actualStatus}`, async () => {
            const h = await boot({ originKind: fresh ? "DRAFT" : "LEGACY" });
            h.state.attachments = [{ id: "file-1", filename: "retained.txt", byteLength: 4 }];
            await h.time.advance(59000);
            H.click(button(h, "mc-delay-cancel-open")); await H.flush();
            if (fresh) h.state.job.canCancel = false;
            await h.time.advance(2000);
            assert.ok(button(h, "mc-delay-confirm-submit"));
            const card = h.host.querySelector('[data-message-key="DELAYED_MANUAL_REPLY:77"]');
            assert.match(card.textContent, /等待发送/);
            assert.doesNotMatch(card.textContent, /已发送/);
            assert.match(card.textContent, /Frozen/);
            assert.match(card.textContent, /retained.txt/);
            assert.equal(h.state.posts.length, 0);
            h.state.conflict = true;
            h.state.afterConflict = { status: actualStatus, canCancel: false };
            const start = h.calls.api.length, submit = button(h, "mc-delay-confirm-submit");
            H.click(submit); H.click(submit); await H.flush();
            assert.deepEqual(h.state.posts, [{ expectedRevision: 1 }]);
            const actions = h.calls.api.slice(start).filter(x => /\/delayed-replies\/77/.test(x.url));
            assert.deepEqual(Array.from(actions, x => [x.method, x.url.split("/").pop()]), [["POST", "cancel"], ["GET", "77"]]);
            assert.equal(button(h, "mc-delay-confirm-submit"), null);
            assert.match(h.host.textContent, /已超过撤回期限/);
            assert.match(h.host.textContent, new RegExp({ WAITING: "等待发送", SENDING: "正在发送", SENT: "已发送" }[actualStatus]));
            assert.doesNotMatch(h.host.textContent, /已撤回/);
            await h.time.advance(5000);
            assert.equal(h.state.posts.length, 1);
            finish(h);
        });
    }
}
test("expired new open and dismissed confirmation cannot reopen, even with stale capability", async () => {
    for (const admitted of [false, true]) {
        const h = await boot(), open = button(h, "mc-delay-cancel-open");
        if (admitted) H.click(open);
        await h.time.advance(61000);
        if (admitted) H.click(button(h, "mc-delay-confirm-dismiss"));
        H.click(open); await H.flush();
        assert.equal(button(h, "mc-delay-confirm-submit"), null);
        assert.equal(h.state.posts.length, 0);
        finish(h);
    }
});
for (const invalidation of ["SENDING", "SENT", "CANCELLED", "FAILED", "UNKNOWN", "revision", "missing", "error", "capability"]) {
    test(`observed ${invalidation} invalidates admitted cancel without deadline bypass`, async () => {
        const h = await boot();
        H.click(button(h, "mc-delay-cancel-open"));
        const stale = button(h, "mc-delay-confirm-submit");
        if (invalidation === "revision") h.state.job.revision++;
        else if (invalidation === "missing") h.state.missing = true; // Same-key authoritative row replaces its missing job projection; pagination omission does not.
        else if (invalidation === "error") h.state.job.publicErrorMessage = "来源已失效";
        else if (invalidation === "capability") h.state.job.canCancel = false;
        else h.state.job.status = invalidation;
        await h.time.advance(5000);
        assert.equal(!!button(h, "mc-delay-confirm-submit"), false);
        await h.time.advance(56000);
        H.click(stale); await H.flush();
        assert.equal(h.state.posts.length, 0);
        finish(h);
    });
}
for (const status of [0, 401, 403]) {
    test(`late submit ${status || "network"} failure never retries or announces success`, async () => {
        const h = await boot(); await h.time.advance(59000);
        H.click(button(h, "mc-delay-cancel-open")); await h.time.advance(2000);
        h.state.postError = Object.assign(Error("offline or denied"), { status });
        H.click(button(h, "mc-delay-confirm-submit")); await H.flush();
        assert.equal(h.state.posts.length, 1);
        assert.equal(button(h, "mc-delay-confirm-submit"), null);
        assert.doesNotMatch(h.host.textContent, /已撤回/);
        if (status) assert.equal(h.host.textContent, "");
        else assert.match(h.host.textContent, /offline or denied/);
        finish(h);
    });
}
for (const cleanup of ["hidden", "unmount", "expert"]) {
    test(`late admitted confirmation is cleared on ${cleanup}`, async () => {
        const h = await boot(); await h.time.advance(59000);
        H.click(button(h, "mc-delay-cancel-open")); await h.time.advance(2000);
        const stale = button(h, "mc-delay-confirm-submit");
        if (cleanup === "hidden") { h.doc.hidden = true; h.doc.root.dispatchEvent(new H.MiniEvent("visibilitychange")); }
        if (cleanup === "unmount") finish(h);
        if (cleanup === "expert") H.click(h.host.querySelector('[data-action="mc-select-expert"][data-contact-id="2"]'));
        await H.flush(); H.click(stale); await H.flush();
        assert.equal(h.state.posts.length, 0);
        finish(h);
    });
}
test("hidden confirmation DOM is removed and unchanged resume permits one fresh explicit action", async () => {
    for (const action of ["cancel", "retry"]) {
        const h = await boot(action === "cancel" ? { revision: 7 } : {
            revision: 7, status: "FAILED", canCancel: false, canRetry: true
        });
        try {
            const reads = [], api = h.sandbox.api;
            h.sandbox.api = async (url, options) => {
                const response = await api(url, options);
                if (url.includes("/conversations/1/messages") && (!options || !options.method || options.method === "GET")) {
                    reads.push(JSON.parse(JSON.stringify(response)));
                }
                return response;
            };
            await h.time.advance(5000);
            assert.ok(reads.length > 0, "capture an actual conversation GET before hiding");
            const before = reads[reads.length - 1];
            const cardSelector = '[data-message-key="DELAYED_MANUAL_REPLY:77"]';
            const card = h.host.querySelector(cardSelector), body = card.querySelector(".mc-body");
            assert.ok(body);
            const actions = card.querySelector(".mc-delay-actions");
            const innerHTML = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(actions), "innerHTML");
            // Match native replacement: detached nodes must no longer bubble to the host.
            Object.defineProperty(actions, "innerHTML", {
                get() { return innerHTML.get.call(this); },
                set(value) {
                    this.childNodes.slice().forEach(child => this.removeChild(child));
                    innerHTML.set.call(this, value);
                }
            });
            H.click(button(h, `mc-delay-${action}-open`)); await H.flush();
            const confirmation = card.querySelector(".mc-delay-confirm");
            const submit = button(h, "mc-delay-confirm-submit"), dismiss = button(h, "mc-delay-confirm-dismiss");
            assert.ok(confirmation); assert.ok(submit); assert.ok(dismiss);
            assert.equal(h.state.posts.length, 0);

            h.doc.hidden = true;
            h.doc.root.dispatchEvent(new H.MiniEvent("visibilitychange"));
            await H.flush();
            assert.equal(!!h.host.querySelector(".mc-delay-confirm"), false, `${action}: hidden must remove visible confirmation DOM`);
            assert.equal(h.host.contains(confirmation), false);
            assert.equal(h.host.contains(submit), false);
            assert.equal(h.host.contains(dismiss), false);
            assert.equal(button(h, "mc-delay-confirm-submit"), null);
            assert.equal(button(h, "mc-delay-confirm-dismiss"), null);
            assert.equal(h.host.querySelector(cardSelector), card, "hidden cleanup preserves the message card");
            assert.equal(card.querySelector(".mc-body"), body, "hidden cleanup preserves the message body");
            const conversationCalls = () => h.calls.api.filter(entry => entry.url.startsWith("/api/mail/mailbox/conversations")).length;
            const hiddenCalls = conversationCalls(), readCount = reads.length;
            await h.time.advance(10000);
            assert.equal(conversationCalls(), hiddenCalls, "hidden time must not poll conversations");
            assert.equal(reads.length, readCount);

            h.doc.hidden = false;
            h.doc.root.dispatchEvent(new H.MiniEvent("visibilitychange"));
            await H.flush();
            assert.ok(reads.length > readCount, "visibility resume performs an actual conversation GET");
            const resumed = reads[reads.length - 1];
            const delayedJob = response => response.items.find(item => item.source === "DELAYED_MANUAL_REPLY").delayedReply;
            assert.ok(delayedJob(resumed).serverNowMs > delayedJob(before).serverNowMs);
            const withoutServerNow = response => JSON.stringify(response, (key, value) => key === "serverNowMs" ? undefined : value);
            assert.equal(withoutServerNow(resumed), withoutServerNow(before), "resume changes only serverNowMs, not job/revision/status/capabilities");
            assert.equal(h.host.querySelector(".mc-delay-confirm"), null);
            assert.equal(button(h, "mc-delay-confirm-submit"), null);
            assert.equal(button(h, "mc-delay-confirm-dismiss"), null);
            assert.equal(h.host.contains(submit), false);
            assert.equal(h.host.contains(dismiss), false);
            assert.equal(h.host.querySelector(cardSelector), card);
            assert.equal(card.querySelector(".mc-body"), body);
            H.click(submit); H.click(dismiss); await H.flush();
            assert.equal(h.state.posts.length, 0, "detached confirmation references cannot submit");

            const reopen = button(h, `mc-delay-${action}-open`);
            assert.ok(reopen, "the current legal action remains available");
            const revision = delayedJob(resumed).revision;
            H.click(reopen); await H.flush();
            const freshSubmit = button(h, "mc-delay-confirm-submit");
            assert.ok(freshSubmit); assert.notEqual(freshSubmit, submit);
            assert.equal(h.state.posts.length, 0, "reopening still requires explicit confirmation");
            H.click(freshSubmit); H.click(freshSubmit); await H.flush();
            assert.deepEqual(h.state.posts, [{ expectedRevision: revision }]);
            const posts = h.calls.api.filter(entry => entry.method === "POST" && /\/delayed-replies\/77\//.test(entry.url));
            assert.equal(posts.length, 1);
            assert.ok(posts[0].url.endsWith(`/${action}`));
        } finally {
            finish(h);
        }
    }
});
test("UNKNOWN, permanent failures, and historical errors have no retry capability",async()=>{
    for (const status of ["UNKNOWN","FAILED"]) { const h=await boot({status,canCancel:false,publicErrorMessage:'<img src=x onerror=alert(1)>'});
        assert.equal(button(h,"mc-delay-retry-open"),null); assert.equal(h.host.querySelector('.mc-send-error img'),null); assert.match(h.host.textContent,/img src=x/); finish(h); }
});
test("list failure count is independent of latest and list-only polling stops on disposal",async()=>{
    const h=await boot({},false); assert.match(h.host.textContent,/有 2 封邮件发送异常/);
    const list=()=>h.calls.api.filter(x=>x.url.startsWith('/api/mail/mailbox/conversations?')).length;
    const before=list(); await h.time.advance(5000); assert.ok(list()>before); finish(h); const stopped=list(); await h.time.advance(10000); assert.equal(list(),stopped);
});
test("background polling redraws an unchanged list after a network failure", async () => {
    const time = clock();
    let failList = false;
    const h = await H.bootChat({ clock: time, performance: { now: time.now },
        conversations: { items: [H.expertA(), H.expertB()], total: 2 },
        route(url, method, raw, entry, next) {
            if (failList && url.startsWith("/api/mail/mailbox/conversations?")) return Promise.reject(new Error("offline"));
            return next(url, method, raw, entry);
        }
    });
    failList = true;
    await time.advance(5000);
    assert.ok(button(h, "mc-retry-list"));
    failList = false;
    await time.advance(5000);
    assert.equal(button(h, "mc-retry-list"), null);
    assert.ok(h.host.querySelector('[data-action="mc-select-expert"][data-contact-id="1"]'));
    finish(h);
});
test("delayed attachment cards never label unsent files as sent or expose draft removal", async () => {
    for (const [status, text] of [["WAITING", "等待发送"], ["CANCELLED", "已撤回"], ["UNKNOWN", "发送结果待确认"]]) {
        const h = await boot({ status });
        h.state.attachments = [{ id: "file-1", filename: "actual.txt", byteLength: 91, downloadUrl: "/download/file-1" }];
        await h.time.advance(5000);
        const card = h.host.querySelector('[data-message-key="DELAYED_MANUAL_REPLY:77"]');
        assert.match(card.querySelector(".outbound-file-meta").textContent, new RegExp(text));
        assert.doesNotMatch(card.querySelector(".outbound-file-meta").textContent, /已发送/);
        assert.equal(card.querySelector('[data-action="mc-remove-attachment"]'), null);
        assert.ok(card.querySelector('[data-role="outbound-download"]'));
        finish(h);
    }
});
test("switching expert invalidates an open confirmation",async()=>{
    const h=await boot(); H.click(button(h,"mc-delay-cancel-open")); const stale=button(h,"mc-delay-confirm-submit");
    H.click(h.host.querySelector('[data-action="mc-select-expert"][data-contact-id="2"]')); await H.flush(); H.click(stale); await H.flush(); assert.equal(h.state.posts.length,0); finish(h);
});
test("global submit preserves boolean and optional response callback for WAITING and SENT",async()=>{
    for (const sendStatus of ["WAITING","SENT"]) {
        const statuses=[],alerts=[],results=[]; const box={postManualRichReplySnapshot:async()=>({sendStatus}),showStatus:(m)=>statuses.push(m),alert:m=>alerts.push(m)};
        vm.createContext(box); vm.runInContext(H.extractFn("submitManualRichReply",app.slice(app.indexOf('async function submitManualRichReply(url'))),box);
        assert.equal(await box.submitManualRichReply('/test',{},r=>results.push(r)),true); assert.equal(results[0].sendStatus,sendStatus);
        if(sendStatus==="WAITING") { assert.deepEqual(alerts,[]); assert.deepEqual(statuses,["已加入发送队列，60 秒内可撤回"]); } else assert.match(alerts[0],/发送成功/);
    }
});
test("both host adapters forward response callback unchanged",async()=>{
    const seen=[],box={submitManualRichReply:async(...args)=>{seen.push(args);return true;}}; vm.createContext(box);
    for(const name of ["mcHostSendRichReply","mcHostSendConversationRichReply"]) vm.runInContext(H.extractFn(name,app),box);
    const callback=()=>{}; await box.mcHostSendRichReply(100,{},callback); await box.mcHostSendConversationRichReply(1,{},callback);
    assert.equal(seen.length,2); assert.ok(seen.every(x=>x[2]===callback));
});
test("reader routes admit only numeric exact method pairs",()=>{
    const box={};vm.createContext(box);vm.runInContext(H.extractFn("isMailReadOnlyRequestAllowed",app),box);
    const base='/api/mail/mailbox/conversations/1/delayed-replies/77';
    for(const [suffix,method] of [['','GET'],['/restore','GET'],['/cancel','POST'],['/retry','POST']]) assert.equal(box.isMailReadOnlyRequestAllowed(base+suffix,{method}),true);
    for(const [suffix,method] of [['/cancel','GET'],['/restore','POST'],['/retry','DELETE'],['/anything','GET']]) assert.equal(box.isMailReadOnlyRequestAllowed(base+suffix,{method}),false);
    assert.equal(box.isMailReadOnlyRequestAllowed(base.replace('/77','/077'),{method:'GET'}),false);
});

test("legacy local WAITING keeps QA and adoption context and never rebuilds detail", async () => {
    const start = app.indexOf("    async function submitManualRichReply(recordId");
    const end = app.indexOf('\n    if (action === "send-manual-rich-reply")', start);
    const editor = { getAttribute: () => "true", setAttribute() {}, removeAttribute() {} }, subject = {};
    const qa = { qaRuleIds: [9] }, adopt = { trustReplyAssembly: { source: 100 } };
    let rebuilt = 0;
    const box = { element: {}, $: id => id === "#manualRichReplyEditor" ? editor : subject,
        state: { authIdentity: "admin:admin" }, liveDetailLoadSeq: 1, legacyDelayedReplies: new Map(),
        manualReplyQaContext: qa, aiReplyState: { adoptContext: adopt },
        postManualRichReplySnapshot: async () => ({ sendStatus: "WAITING", delayedReply: job() }),
        showStatus() {}, refreshMailboxAfterPendingAction: async () => {}, showUnmatchedDetail: async () => { rebuilt++; },
        resetPreflightState() { throw Error("must not clear"); }, alert() { throw Error("must not announce SENT"); } };
    vm.createContext(box); vm.runInContext(app.slice(start, end), box);
    assert.equal(await box.submitManualRichReply(100, { htmlBody: "<p>raw</p>" }), true);
    assert.equal(box.manualReplyQaContext, qa); assert.equal(box.aiReplyState.adoptContext, adopt);
    assert.equal(rebuilt, 0); assert.equal(box.element.disabled, true);
});

test("legacy reload restore uses server processing id, rejects missing id and preserves new input", async () => {
    const start = app.indexOf("async function mcHostRestoreDelayedReply("), end = app.indexOf("\nconst preflightState", start);
    const input = { subject: "Original", htmlBody: "<b>Original</b>", textBody: "Original", qaRuleIds: [9], attachmentIds: ["att"] };
    for (const conflict of [false, true]) {
        const editor = { innerText: conflict ? "new unsaved" : "", innerHTML: conflict ? "<p>new unsaved</p>" : "" }, subject = {};
        const opened = [], calls = [];
        const box = { state: { authIdentity: "admin:admin", mailbox: { detailContext: { id: 100 } } }, document: {},
            $: id => id === "#manualRichReplyEditor" ? editor : subject, legacyDelayedReplies: new Map(), manualReplyQaContext: null,
            api: async url => { calls.push(url); return url.endsWith("/restore")
                ? { originKind: "LEGACY", inboundProcessingId: 100, expectedCancelledRevision: 2, originalInput: input }
                : { record: { expertContactId: 1, senderAccountCode: "acc1" } }; },
            showUnmatchedDetail: async id => opened.push(id), openActionDialog: async () => true,
            window: { MailboxMeeting: { sanitizeDraftHtml: html => html } } };
        vm.createContext(box); vm.runInContext(app.slice(start, end), box);
        assert.equal(await box.mcHostRestoreDelayedReply(1, 77, "acc1"), !conflict);
        assert.ok(calls.includes("/api/mail/unmatched-inbound/100"));
        assert.equal(editor.innerHTML, conflict ? "<p>new unsaved</p>" : input.htmlBody);
        assert.deepEqual(opened, conflict ? [] : [100]);
        if (!conflict) assert.deepEqual(box.legacyDelayedReplies.get("admin:admin|100").input.attachmentIds, ["att"]);
        box.api = async () => ({ originKind: "LEGACY", originalInput: input });
        await assert.rejects(box.mcHostRestoreDelayedReply(1, 77, "acc1"), /无法恢复/);
    }
});

for (const evidence of ["QA", "RAG"]) {
    for (const edit of ["unchanged", "text", "format", "new-adoption"]) {
        test(`restored ${evidence} ${edit} send preserves current body authority and original context`, async () => {
            const original = {
                subject: "Original", textBody: "Rendered original", htmlBody: "<p>Rendered original</p>",
                templateTextBody: "Raw ${expertName}", trustReplyAssembly: { source: "LIVE_INBOUND", requests: [{ factRuleIds: [9] }] },
                templateHtmlBody: "<p>Raw ${expertName}</p>",
                attachmentIds: ["attachment-1"], meetingInput: { title: "Original meeting" },
                ...(evidence === "QA" ? { qaRuleIds: [9, 4], suggestedRuleIds: [4, 9] }
                    : { ragFactCodes: ["fact-b", "fact-a"], ragCorpusFingerprint: "corpus" })
            };
            let html = "", text = "";
            const disclosure = { open: false };
            const editor = {
                get innerHTML() { return html; },
                set innerHTML(value) { html = value; text = value.replace(/<[^>]*>/g, ""); },
                get innerText() { return disclosure.open ? text : ""; },
                closest: () => disclosure,
                getAttribute: () => "true", setAttribute() {}, removeAttribute() {}
            };
            const subject = {}, posts = [];
            const box = {
                id: 100, action: "send-manual-rich-reply", element: {},
                state: { authIdentity: "admin:admin", mailbox: { detailContext: { id: 100 } } },
                liveDetailLoadSeq: 1, legacyDelayedReplies: new Map(), aiReplyState: { adoptContext: null },
                manualReplyQaContext: null, document: {},
                $: id => id === "#manualRichReplyEditor" ? editor : subject,
                api: async url => url.endsWith("/restore")
                    ? { originKind: "LEGACY", inboundProcessingId: 100, expectedCancelledRevision: 7, originalInput: original }
                    : { record: { expertContactId: 1, senderAccountCode: "acc1" } },
                window: { MailboxMeeting: { sanitizeDraftHtml: value => value }, localStorage: { getItem: () => "operator" } },
                showUnmatchedDetail: async () => {}, refreshMailboxAfterPendingAction: async () => {},
                showStatus() {}, resetPreflightState() {}, alert() {},
                postManualRichReplySnapshot: async (url, body) => {
                    posts.push({ url, body: JSON.parse(JSON.stringify(body)) });
                    return { sendStatus: "SENT" };
                }
            };
            vm.createContext(box);
            const restoreStart = app.indexOf("async function mcHostRestoreDelayedReply(");
            vm.runInContext(app.slice(restoreStart, app.indexOf("\nconst preflightState", restoreStart)), box);
            const start = app.indexOf("    async function submitManualRichReply(recordId");
            const builder = app.indexOf('\n    if (action === "send-manual-rich-reply")', start);
            const end = app.indexOf('\n    if (action === "rich-command")', builder);
            vm.runInContext(app.slice(start, builder) + '\nasync function sendCurrentEditor() {' + app.slice(builder, end) + '\n}', box);
            assert.equal(await box.mcHostRestoreDelayedReply(1, 77, "acc1"), true);
            if (edit === "text") editor.innerHTML = "<p>Current operator edited text</p>";
            if (edit === "format") editor.innerHTML = "<p><b>Rendered original</b></p>";
            if (edit === "new-adoption") {
                editor.innerHTML = "<p>New adopted body</p>";
                box.aiReplyState.adoptContext = { recordId: 100, rawTemplate: "New ${expertName}",
                    renderedBaseline: editor.innerText, renderedBaselineHtml: editor.innerHTML };
            }
            const visible = { text: editor.innerText, html: editor.innerHTML };
            assert.equal(await box.sendCurrentEditor(), true);
            assert.equal(posts.length, 1);
            assert.equal(posts[0].url, "/api/mail/unmatched-inbound/100/manual-rich-reply");
            const sent = posts[0].body;
            assert.equal(sent.delayExpectedRevision, 7);
            assert.equal(sent.textBody, visible.text);
            assert.equal(sent.htmlBody, visible.html);
            assert.deepEqual(sent.attachmentIds, original.attachmentIds);
            assert.deepEqual(sent.meetingInput, original.meetingInput);
            for (const field of evidence === "QA" ? ["qaRuleIds", "suggestedRuleIds"] : ["ragFactCodes", "ragCorpusFingerprint"]) {
                assert.deepEqual(sent[field], original[field]);
            }
            if (edit === "unchanged") {
                assert.equal(sent.templateTextBody, original.templateTextBody);
                assert.equal(sent.templateHtmlBody, original.templateHtmlBody);
                assert.deepEqual(sent.trustReplyAssembly, original.trustReplyAssembly);
            } else {
                assert.equal(sent.templateTextBody, edit === "new-adoption" ? "New ${expertName}" : undefined);
                assert.equal(sent.templateHtmlBody, undefined, "old raw HTML must not override current formatting");
                assert.equal(sent.trustReplyAssembly, undefined, "old assembly must not override current editor output");
            }
            // The server consumes nonblank templateTextBody before text/html, so inspect the actual POST, not only the editor.
            const consumedText = sent.templateTextBody || sent.textBody;
            assert.equal(consumedText, edit === "unchanged" ? original.templateTextBody
                : edit === "new-adoption" ? "New ${expertName}" : visible.text);
            assert.equal(original.templateTextBody, "Raw ${expertName}", "restored snapshot remains intact");
        });
    }
}
test("missing persisted draft remains editable when the real target API returns null", async () => {
    const h = await H.bootChat({ conversations: { items: [H.expertA()], total: 1 }, contact: H.contactA(), messages: H.messagesA(), route(url, method, raw, entry, next) {
        if (url.includes("/drafts/target?")) return Promise.resolve(null);
        return next(url, method, raw, entry);
    } });
    H.click(h.host.querySelector('[data-action="mc-select-expert"][data-contact-id="1"]'));
    await H.flush();
    assert.equal(h.host.querySelector('[data-role="mc-editor"]').getAttribute("contenteditable"), "true");
    assert.doesNotMatch(h.host.textContent, /Cannot read properties of null/);
    finish(h);
});

function outboundRestore() {
    const ids = ["11111111-1111-4111-8111-111111111111", "22222222-2222-4222-8222-222222222222"];
    return { id: 77, expectedCancelledRevision: 2, originKind: "LEGACY", sourceKind: "OUTBOUND", anchorMailRecordId: 900,
        originalConversationInput: { requestId: "1b4e28ba-2fa1-11d2-883f-0016d3cca427", accountScope: " ",
            anchorMailRecordId: null, subject: "Original subject", textBody: "Raw ${expertName}", htmlBody: "<p>Raw ${expertName}</p>",
            attachmentIds: ids, draftRef: null },
        outboundAttachments: ids.map((id, i) => ({ id, filename: `${i + 1}.txt`, contentType: "text/plain", byteLength: 3,
            sha256: String(i).repeat(64) })),
        attachmentDownloads: Object.fromEntries(ids.map(id => [id, `/api/mail/conversations/1/outbound-attachments/${id}/download`])) };
}
function editorOf(h) { return h.host.querySelector('[data-role="mc-editor"]'); }
function inputEvent(h) { editorOf(h).dispatchEvent(new H.MiniEvent("input", { bubbles: true })); }
for (const edit of ["unchanged", "text", "format", "attachment"]) {
    test(`outbound cancelled source restores existing renderer and sends ${edit} without draft rebinding`, async () => {
        const h = await boot({ status: "CANCELLED", revision: 2, canCancel: false });
        h.state.restore = outboundRestore();
        // Exercise the real conversation adapter as well as the mailbox builder.
        const sent = [];
        h.sandbox.submitManualRichReply = async (url, body) => { sent.push({ url, body: JSON.parse(JSON.stringify(body)) }); return false; };
        vm.runInContext(H.extractFn("mcHostSendConversationRichReply", app), h.sandbox);
        H.click(button(h, "mc-delay-restore")); await H.flush();
        assert.match(editorOf(h).textContent, /Raw \$\{expertName\}/);
        assert.deepEqual(Array.from(h.host.querySelectorAll('[data-role="outbound-draft-files"] .outbound-file-name'), n => n.textContent), ["1.txt", "2.txt"]);
        const draftRequests = h.calls.api.filter(x => x.url.includes("/drafts") && x.method !== "GET").length;
        if (edit === "text") editorOf(h).innerHTML = "<p>Current edited text</p>";
        if (edit === "format") editorOf(h).innerHTML = "<p><b>Raw ${expertName}</b></p>";
        if (edit === "text" || edit === "format") inputEvent(h);
        if (edit === "attachment") H.click(h.host.querySelector('[data-role="outbound-draft-files"] [data-action="mc-remove-attachment"]'));
        await h.time.advance(1000);
        const beforeSend = h.calls.api.length;
        H.click(button(h, "mc-send-manual")); await H.flush();
        assert.equal(sent.length, 1, JSON.stringify(h.calls.status));
        const body = sent[0].body;
        assert.match(sent[0].url, /conversations\/1\/manual-rich-reply$/);
        assert.equal(body.draftRef, undefined);
        assert.equal(body.anchorMailRecordId, 900, "newer timeline anchors do not replace frozen actual source");
        assert.equal(body.accountScope, " ");
        assert.equal(body.delayExpectedRevision, 2);
        if (edit === "unchanged") {
            assert.equal(body.requestId, h.state.restore.originalConversationInput.requestId);
            assert.equal(body.htmlBody, h.state.restore.originalConversationInput.htmlBody);
            assert.equal(body.textBody, h.state.restore.originalConversationInput.textBody);
        } else {
            assert.notEqual(body.requestId, h.state.restore.originalConversationInput.requestId);
            assert.equal(body.htmlBody, editorOf(h).innerHTML);
        }
        assert.deepEqual(body.attachmentIds, h.state.restore.originalConversationInput.attachmentIds.slice(edit === "attachment" ? 1 : 0));
        assert.equal(h.calls.api.filter(x => x.url.includes("/drafts") && x.method !== "GET").length, draftRequests);
        assert.equal(h.calls.api.slice(beforeSend).filter(x => /\/drafts\/\d+/.test(x.url)).length, 0);
        assert.equal(h.state.restore.originalConversationInput.anchorMailRecordId, null);
        finish(h);
    });
}
test("restored local source never overwrites ordinary draft or unsaved input", async () => {
    const h = await boot({ status: "CANCELLED", revision: 2, canCancel: false });
    h.state.restore = outboundRestore();
    editorOf(h).innerHTML = "<p>New operator input</p>"; inputEvent(h);
    await h.time.advance(1000);
    H.click(button(h, "mc-delay-restore")); await H.flush();
    assert.match(editorOf(h).textContent, /New operator input/);
    assert.ok(h.calls.status.some(x => /未覆盖/.test(x.message)));
    assert.equal(h.state.job.status, "CANCELLED");
    assert.equal(h.state.restore.originalConversationInput.textBody, "Raw ${expertName}");
    finish(h);
});
for (const change of ["expert", "unmount", "editor", "revision"]) {
    test(`late outbound restore cannot overwrite after ${change}`, async () => {
        const h = await boot({ status: "CANCELLED", revision: 2, canCancel: false });
        let resolve;
        h.state.restore = () => new Promise(r => { resolve = r; });
        H.click(button(h, "mc-delay-restore")); await H.flush();
        assert.ok(resolve);
        if (change === "expert") H.click(h.host.querySelector('[data-action="mc-select-expert"][data-contact-id="2"]'));
        if (change === "unmount") finish(h);
        if (change === "editor") { editorOf(h).innerHTML = "<p>Late local input</p>"; inputEvent(h); }
        if (change === "revision") { h.state.job = { ...h.state.job, revision: 3, status: "WAITING" }; await h.time.advance(5000); }
        resolve(outboundRestore()); await H.flush();
        assert.doesNotMatch(h.host.textContent, /Raw \$\{expertName\}/);
        if (change === "editor") assert.match(editorOf(h).textContent, /Late local input/);
        if (change !== "unmount") finish(h);
    });
}

for (const editedDuringMount of [false, true]) {
    test(`inbound restore accepts its own editor mount but rejects intervening input: ${editedDuringMount}`, async () => {
        const h = await boot({ status: "CANCELLED", revision: 2, canCancel: false });
        h.state.restore = { id: 77, originKind: "LEGACY", sourceKind: "INBOUND",
            inboundProcessingId: 101, expectedCancelledRevision: 2, originalInput: { htmlBody: "<p>Original</p>" } };
        let finishMount, accepted;
        h.sandbox.mcHostMountUnmatchedDetail = (host) => {
            host.innerHTML = '<div id="unmatchedDetailPanel"><div id="manualRichReplyEditor" contenteditable="true"></div></div>';
            return new Promise(resolve => { finishMount = resolve; });
        };
        h.sandbox.mcHostRestoreDelayedReply = async (contact, id, account, current, openEditor) => {
            assert.equal(current(), true);
            await openEditor(101);
            accepted = current();
        };
        H.click(button(h, "mc-delay-restore")); await H.flush();
        assert.ok(finishMount, "real mailbox mount invoked the existing inbound host");
        if (editedDuringMount) {
            const editor = h.host.querySelector("#manualRichReplyEditor");
            editor.innerHTML = "<p>New legacy input</p>";
            editor.dispatchEvent(new H.MiniEvent("input", { bubbles: true }));
        }
        finishMount(); await H.flush();
        assert.equal(accepted, !editedDuringMount);
        if (editedDuringMount) assert.match(h.host.textContent, /New legacy input/);
        finish(h);
    });
}

function inboundRestore() {
    return { id: 77, originKind: "LEGACY", sourceKind: "INBOUND", inboundProcessingId: 101,
        expectedCancelledRevision: 2, originalInput: { subject: "Withdrawn subject",
            htmlBody: "<p>Withdrawn inbound original</p>", textBody: "Withdrawn inbound original",
            qaRuleIds: [9], attachmentIds: ["cancelled-attachment"] } };
}

function installInboundRestoreAdapter(h) {
    const api = h.sandbox.api, mounts = [];
    h.sandbox.state = { authIdentity: "admin:admin", mailbox: {} };
    h.sandbox.legacyDelayedReplies = new Map();
    h.sandbox.manualReplyQaContext = null;
    h.sandbox.$ = selector => h.host.querySelector(selector);
    h.sandbox.window = { MailboxMeeting: h.sandbox.MailboxMeeting };
    h.sandbox.api = (url, options) => url === "/api/mail/unmatched-inbound/101"
        ? Promise.resolve({ record: { expertContactId: 1, senderAccountCode: "acc1" } }) : api(url, options);
    h.sandbox.mcHostMountUnmatchedDetail = async (host, id) => {
        mounts.push(id);
        h.sandbox.state.mailbox.detailContext = { id };
        host.innerHTML = '<div id="unmatchedDetailPanel"><input id="manualReplySubject"><div id="manualRichReplyEditor" contenteditable="true"></div></div>';
    };
    const start = app.indexOf("async function mcHostRestoreDelayedReply(");
    const end = app.indexOf("\nconst preflightState", start);
    assert.ok(start >= 0 && end > start, "real restore adapter source is present");
    vm.runInContext(app.slice(start, end), h.sandbox);
    return mounts;
}

function ordinaryTarget(h) {
    const request = h.draftServer.requests.find(entry => entry.method === "GET" && entry.url.includes("/target?"));
    assert.ok(request, "the real consumer loaded its ordinary target");
    const params = new URL(request.url, "https://test.invalid").searchParams;
    return { contactId: Number(params.get("contactId")), kind: params.get("kind"),
        processingId: Number(params.get("processingId")) || 0, accountScope: params.get("accountScope") || "" };
}

for (const conflict of ["latest server draft", "saved local draft", "unsaved input"]) {
    test(`inbound restore consumer protects ${conflict} before legacy adapter mount`, async () => {
        const h = await boot({ status: "CANCELLED", revision: 2, canCancel: false });
        const snapshot = inboundRestore();
        h.state.restore = snapshot;
        const mounts = installInboundRestoreAdapter(h), target = ordinaryTarget(h);
        if (conflict === "latest server draft") {
            // Another browser saves after this consumer has loaded an empty ordinary draft.
            h.draftServer.seed("admin", target, { subject: "New ordinary subject",
                html: "<p>New ordinary body</p>", text: "New ordinary body", context: {
                    outboundAttachmentDraft: { revision: 4, items: [
                        { id: "new-file-1", key: "file-1", filename: "first.txt", state: "ready" },
                        { id: "new-file-2", key: "file-2", filename: "second.txt", state: "ready" }
                    ] }
                } }, { version: 7 });
        } else {
            editorOf(h).innerHTML = "<p>New ordinary local body</p>";
            if (conflict === "saved local draft") {
                inputEvent(h);
                await h.time.advance(1000);
                await h.api.flushDrafts();
            }
        }
        const serverBefore = h.draftServer.get("admin", target), editorBefore = editorOf(h).innerHTML;
        const writesBefore = h.draftServer.requests.filter(entry => entry.method !== "GET").length;
        const frozenBefore = JSON.stringify(snapshot);
        if (conflict !== "unsaved input") assert.equal(serverBefore.state, "ACTIVE");
        H.click(button(h, "mc-delay-restore")); await H.flush();
        assert.ok(h.calls.status.some(entry => /当前已有新草稿或未保存输入.*未覆盖.*撤回内容仍保留/.test(entry.message)),
            "warn before entering the legacy editor; retaining only the server row is insufficient");
        assert.deepEqual(mounts, [], "conflict must not mount or fill the legacy editor");
        assert.equal(h.host.querySelector("#manualRichReplyEditor"), null);
        assert.equal(editorOf(h).innerHTML, editorBefore);
        assert.deepEqual(h.draftServer.get("admin", target), serverBefore, "body, version and ordered attachments remain intact");
        assert.equal(h.draftServer.requests.filter(entry => entry.method !== "GET").length, writesBefore);
        assert.equal(h.state.job.status, "CANCELLED");
        assert.equal(JSON.stringify(await h.sandbox.api("/api/mail/mailbox/conversations/1/delayed-replies/77/restore")), frozenBefore,
            "cancelled original remains readable, independently of the ordinary draft");
        finish(h);
    });
}

test("inbound restore consumer without ordinary conflict reaches real adapter and preserves cancelled snapshot", async () => {
    const h = await boot({ status: "CANCELLED", revision: 2, canCancel: false });
    h.state.restore = inboundRestore();
    const mounts = installInboundRestoreAdapter(h), target = ordinaryTarget(h);
    const frozenBefore = JSON.stringify(h.state.restore);
    H.click(button(h, "mc-delay-restore")); await H.flush();
    assert.deepEqual(mounts, [101]);
    assert.equal(h.host.querySelector("#manualRichReplyEditor").innerHTML, h.state.restore.originalInput.htmlBody);
    assert.equal(h.host.querySelector("#manualReplySubject").value, "Withdrawn subject");
    assert.equal(h.draftServer.get("admin", target).state, null, "legacy restoration never manufactures an ordinary draft");
    assert.equal(h.draftServer.requests.filter(entry => entry.method !== "GET").length, 0);
    assert.equal(h.state.job.status, "CANCELLED");
    assert.equal(JSON.stringify(h.state.restore), frozenBefore);
    finish(h);
});

for (const stage of ["snapshot", "ordinary lookup"]) {
    for (const change of ["expert", "unmount", "editor", "revision"]) {
        test(`late inbound restore consumer rejects ${change} during ${stage}`, async () => {
            const h = await boot({ status: "CANCELLED", revision: 2, canCancel: false });
            const snapshot = inboundRestore(), mounts = installInboundRestoreAdapter(h);
            let resolve;
            h.state.restore = stage === "snapshot" ? () => new Promise(r => { resolve = () => r(snapshot); }) : snapshot;
            if (stage === "ordinary lookup") {
                h.draftServer.intercept = (entry, execute) => entry.method === "GET" && entry.url.includes("/target?")
                    ? new Promise(r => { resolve = () => r(execute()); }) : execute();
            }
            H.click(button(h, "mc-delay-restore")); await H.flush();
            assert.ok(resolve, "restore must await the current ordinary server draft before mounting");
            h.draftServer.intercept = null;
            if (change === "expert") H.click(h.host.querySelector('[data-action="mc-select-expert"][data-contact-id="2"]'));
            if (change === "unmount") finish(h);
            if (change === "editor") { editorOf(h).innerHTML = "<p>Late ordinary input</p>"; inputEvent(h); }
            if (change === "revision") { h.state.job = { ...h.state.job, revision: 3, status: "WAITING" }; await h.time.advance(5000); }
            resolve(); await H.flush();
            assert.deepEqual(mounts, []);
            assert.doesNotMatch(h.host.textContent, /Withdrawn inbound original/);
            if (change === "editor") assert.match(editorOf(h).textContent, /Late ordinary input/);
            if (change !== "unmount") finish(h);
        });
    }
}
