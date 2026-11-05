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
            if (method === "POST") { state.posts.push(JSON.parse(raw)); if (state.conflict) return Promise.reject(Object.assign(Error("代次已变化"),{status:409})); state.job={...state.job,status:"CANCELLED",revision:2,canCancel:false}; }
            return Promise.resolve({...state.job,serverNowMs:100000+time.now()});
        }
        if (url.includes("/conversations/1/messages")) return Promise.resolve({...H.messagesA(),items:[...H.messagesA().items,{source:"DELAYED_MANUAL_REPLY",id:77,direction:"OUTBOUND",accountCode:"acc1",subject:"Queued",body:"Frozen",eventAt:"2026-11-05T00:00:00",sendStatus:state.job.status,outboundAttachments:state.attachments || [],delayedReply:{...state.job,serverNowMs:100000+time.now()}}]});
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
test("deadline uses monotonic server offset and never claims SENT at zero",async()=>{
    const h=await boot(); H.click(button(h,"mc-delay-cancel-open")); await h.time.advance(61000);
    assert.equal(h.state.posts.length,0); assert.equal(button(h,"mc-delay-confirm-submit"),null);
    const card = h.host.querySelector('[data-message-key="DELAYED_MANUAL_REPLY:77"]');
    assert.match(card.textContent,/等待发送/); assert.doesNotMatch(card.textContent,/已发送/); finish(h);
});
test("409 rereads exactly once without forced retry",async()=>{
    const h=await boot(); h.state.conflict=true; H.click(button(h,"mc-delay-cancel-open")); H.click(button(h,"mc-delay-confirm-submit")); await H.flush();
    assert.equal(h.state.posts.length,1); assert.ok(h.calls.api.some(x=>x.method==="GET"&&x.url.endsWith("/delayed-replies/77"))); assert.match(h.host.textContent,/代次已变化/); finish(h);
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
