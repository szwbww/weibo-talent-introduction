"use strict";

const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const assert = require("node:assert/strict");
const { describe, it } = require("node:test");
const ROOT = path.join(__dirname, "../../main/resources/static");
const chat = fs.readFileSync(path.join(ROOT, "mailbox-chat.js"), "utf8");
const app = fs.readFileSync(path.join(ROOT, "app.js"), "utf8");
const index = fs.readFileSync(path.join(ROOT, "index.html"), "utf8");
const harnessSource = fs.readFileSync(path.join(__dirname, "mailboxChatBehavior.test.js"), "utf8");
const harness = { require, __dirname, console, URL, URLSearchParams, setImmediate };
vm.createContext(harness);
vm.runInContext(harnessSource.slice(0, harnessSource.indexOf('describe("mailbox chat mount')) +
    '\nglobalThis.H = { createDraftServer, createDom, bootChat, flush, click, inputEvent, expertA, expertB, messagesA, contactA, MiniElement, MiniText, MiniDocument, MiniEvent };', harness);
const H = harness.H;
const json = (value) => JSON.parse(JSON.stringify(value));
function fn(source, name, indent = "") {
    const match = source.match(new RegExp("^" + indent + "(?:async\\s+)?function " + name + "\\([^\\n]*\\) \\{[\\s\\S]*?\\n" + indent + "\\}", "m"));
    assert.ok(match, name);
    return match[0];
}
function deferred() { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b; }); return { promise, resolve, reject }; }

// Add the real operations used by this feature to the shared parsed-DOM harness.
// Range.insertNode splits a text node and fragment insertion moves children, not clones.
Object.defineProperty(H.MiniText.prototype, "textContent", { configurable: true, get() { return this.data; }, set(value) { this.data = String(value); } });
const append = H.MiniElement.prototype.appendChild;
const insert = H.MiniElement.prototype.insertBefore;
H.MiniElement.prototype.appendChild = function (node) {
    if (node.nodeType === 11) { [...node.childNodes].forEach((child) => append.call(this, child)); return node; }
    return append.call(this, node);
};
H.MiniElement.prototype.insertBefore = function (node, before) {
    if (node.nodeType === 11) { [...node.childNodes].forEach((child) => insert.call(this, child, before)); return node; }
    return insert.call(this, node, before);
};
H.MiniElement.prototype.replaceChildren = function (...nodes) { [...this.childNodes].forEach((node) => this.removeChild(node)); nodes.forEach((node) => this.appendChild(node)); };
H.MiniDocument.prototype.createDocumentFragment = function () { const node = this.createElement("fragment"); node.nodeType = 11; return node; };
H.MiniDocument.prototype.createRange = function () {
    let parent, before, text, offset;
    return {
        setStartBefore(node) { parent = node.parentNode; before = node; },
        setStart(node, index) { text = node; offset = index; parent = node.parentNode; },
        collapse() {},
        insertNode(node) {
            if (text) {
                before = parent.ownerDocument.createTextNode(text.data.slice(offset));
                const next = parent.childNodes[parent.childNodes.indexOf(text) + 1] || null;
                text.data = text.data.slice(0, offset);
                parent.insertBefore(before, next);
            }
            parent.insertBefore(node, before);
        }
    };
};
Object.defineProperty(H.MiniElement.prototype, "isConnected", { get() { let node = this; while (node.parentNode) node = node.parentNode; return node.nodeType === 9; } });
function visible(node) {
    if (node.nodeType === 3) return node.data;
    if (node.tagName === "BR") return "\n";
    const text = node.childNodes.map(visible).join("");
    return ["P", "DIV", "BLOCKQUOTE", "LI", "UL", "OL"].includes(node.tagName) ? "\n\n" + text + "\n\n" : text;
}
Object.defineProperty(H.MiniElement.prototype, "innerText", { configurable: true,
    get() { return this.childNodes.map(visible).join("").replace(/\n{3,}/g, "\n\n").trim(); },
    set(value) { this.replaceChildren(this.ownerDocument.createTextNode(String(value))); }
});
const slot = (status = "MISSING", text = "Dear Dr Morgan,") => ({ status, canInsert: status === "MISSING", expectedText: text, actualText: null, reasonCode: status });
function inspection(payload, extra = {}) {
    return Object.assign({ context: { contactId: payload.contactId, inboundProcessingId: payload.inboundProcessingId, anchorMailRecordId: payload.anchorMailRecordId, accountCode: payload.senderAccountCode || "acc1" },
        salutation: slot(), signature: slot("MISSING", "Best regards,\nAlex Lee"), quoteBoundary: { kind: "NONE", canonicalQuotedTail: null }, warnings: [] }, extra);
}
const action = (ctx, name) => ctx.host.querySelector(`[data-action="${name}"]`);
const editor = (ctx) => ctx.host.querySelector('[data-role="mc-editor"]');
async function boot(options = {}) {
    const ctx = await H.bootChat(Object.assign({ conversations: { items: [H.expertA(), H.expertB()], total: 2 }, messages: H.messagesA(), contact: H.contactA() }, options));
    H.click(ctx.host.querySelector('[data-action="mc-select-expert"][data-contact-id="1"]'));
    await H.flush();
    ctx.inspectCalls = [];
    ctx.sandbox.mcHostInspectReplyIdentity = async (payload) => { ctx.inspectCalls.push(json(payload)); return inspection(payload); };
    return ctx;
}
function type(ctx, html) { editor(ctx).innerHTML = html; H.inputEvent(editor(ctx)); }
async function complete(ctx) { H.click(action(ctx, "mc-complete-reply-identity")); await H.flush(); }
function statusText(ctx) { return ctx.calls.status.map((entry) => typeof entry === "string" ? entry : entry.message).join("\n"); }
function utils() {
    const sandbox = { Set, console };
    vm.createContext(sandbox);
    ["replyIdentityNormalize", "replyIdentityResultMatches", "replyIdentityQuoteRange", "replyIdentityFragment", "insertReplyIdentitySlots"].forEach((name) => vm.runInContext(fn(chat, name, "    "), sandbox));
    return sandbox;
}

describe("reply identity I-1/I-4 real component completion and draft ownership", () => {
    it("places completion immediately after follow-up, and last when follow-up is unavailable", async () => {
        for (const sentCount of [2, 0]) {
            const ctx = await boot({ conversations: { items: [H.expertA({ sentCount })], total: 1 } });
            const tools = ctx.host.querySelector(".mc-editor-tools");
            const completeButton = action(ctx, "mc-complete-reply-identity");
            assert.equal(tools.children[tools.children.length - 1], completeButton);
            if (sentCount) assert.equal(tools.children[tools.children.indexOf(completeButton) - 1], action(ctx, "mc-open-followup"));
            else assert.equal(action(ctx, "mc-open-followup"), null);
            ctx.api.unmount();
        }
    });
    it("binds the actual toolbar button, preserves rich nodes, saves canonical content once, and is idempotent", async () => {
        const ctx = await boot();
        type(ctx, '<p>Thank you <b>very much</b> for your reply.</p><ul><li>Point</li></ul><p><a href="https://example.invalid/">Link</a></p>');
        const bold = editor(ctx).querySelector("b"), link = editor(ctx).querySelector("a"), list = editor(ctx).querySelector("ul");
        await complete(ctx);
        assert.match(editor(ctx).innerText, /^Dear Dr Morgan,/);
        assert.match(editor(ctx).innerText, /Best regards,\nAlex Lee$/);
        assert.equal(editor(ctx).querySelector("b"), bold);
        assert.equal(editor(ctx).querySelector("a"), link);
        assert.equal(editor(ctx).querySelector("ul"), list);
        const snapshot = editor(ctx).innerHTML;
        ctx.sandbox.mcHostInspectReplyIdentity = async (payload) => inspection(payload, { salutation: slot("MATCH"), signature: slot("MATCH") });
        await complete(ctx);
        assert.equal(editor(ctx).innerHTML, snapshot);
        await ctx.sandbox.MailboxChat.flushDrafts(ctx.host);
        const stored = ctx.draftServer.get("admin", { contactId: 1, kind: "INBOUND", processingId: 101, accountScope: "acc1" });
        assert.equal(stored.content.html, snapshot);
        assert.equal(ctx.inspectCalls[0].sourceKind, "INBOUND");
        assert.equal(ctx.inspectCalls[0].inboundProcessingId, 101);
        assert.equal(ctx.inspectCalls[0].senderAccountCode, "acc1");
        ctx.api.unmount();
    });
    for (const name of ["salutation", "signature"]) {
        it(`inserts only missing ${name}; existing mismatch and all non-MISSING states are untouched`, async () => {
            const ctx = await boot(); type(ctx, "<p>Existing body.</p>");
            ctx.sandbox.mcHostInspectReplyIdentity = async (payload) => inspection(payload, { salutation: slot(name === "salutation" ? "MISSING" : "MISMATCH"), signature: slot(name === "signature" ? "MISSING" : "MISMATCH", "Best regards,\nAlex Lee") });
            await complete(ctx);
            assert.equal(editor(ctx).querySelectorAll("p").length, 2);
            assert.match(statusText(ctx), /存在不一致，请核对/);
            ctx.api.unmount();
        });
    }
    it("all existing/uncertain/duplicate values produce no mutation or extra draft revision", async () => {
        const ctx = await boot(); type(ctx, "<p>Dear Alice,</p><p>Body.</p><p>Bob</p>");
        await ctx.sandbox.MailboxChat.flushDrafts(ctx.host);
        const before = editor(ctx).innerHTML;
        for (const status of ["MATCH", "MISMATCH", "UNCERTAIN", "MULTIPLE_OR_MISPLACED"]) {
            ctx.sandbox.mcHostInspectReplyIdentity = async (payload) => inspection(payload, { salutation: slot(status), signature: slot(status) });
            await complete(ctx); assert.equal(editor(ctx).innerHTML, before);
        }
        ctx.api.unmount();
    });
    it("does not issue two inspect calls while busy and restores the original button", async () => {
        const ctx = await boot(); type(ctx, "<p>Body.</p>"); const hold = deferred(); let count = 0;
        ctx.sandbox.mcHostInspectReplyIdentity = (payload) => { count++; return hold.promise.then(() => inspection(payload)); };
        H.click(action(ctx, "mc-complete-reply-identity")); H.click(action(ctx, "mc-complete-reply-identity"));
        assert.equal(count, 1); assert.equal(action(ctx, "mc-complete-reply-identity").textContent, "检查中…");
        hold.resolve(); await H.flush(); assert.equal(action(ctx, "mc-complete-reply-identity").disabled, false); ctx.api.unmount();
    });
    for (const scenario of ["edit", "contact", "unmount", "logout"]) {
        it(`rejects delayed inspection after ${scenario}`, async () => {
            const ctx = await boot(); type(ctx, "<p>Original.</p>"); const hold = deferred(); let payload;
            ctx.sandbox.mcHostInspectReplyIdentity = (body) => { payload = body; return hold.promise; };
            H.click(action(ctx, "mc-complete-reply-identity"));
            if (scenario === "edit") type(ctx, "<p>New text.</p>");
            if (scenario === "contact") { H.click(ctx.host.querySelector('[data-action="mc-select-expert"][data-contact-id="2"]')); await H.flush(); }
            if (scenario === "unmount") ctx.api.unmount();
            if (scenario === "logout") await ctx.api.applyOptions({ sessionUser: "other-user" });
            hold.resolve(inspection(payload)); await H.flush();
            assert.ok(!ctx.host.textContent.includes("Dear Dr Morgan,"));
            if (scenario !== "unmount") ctx.api.unmount();
        });
    }
});

describe("reply identity I-1/I-2 quote Range and plain-node matrix", () => {
    for (const html of ["", "   ", "<p>Body.</p>", "<p><b>Body.</b> <a href='https://example.invalid/'>link</a></p>"]) {
        it(`safe text construction preserves input ${JSON.stringify(html)}`, () => {
            const { doc } = H.createDom(), el = doc.createElement("div"); el.innerHTML = html;
            const before = [...el.childNodes];
            const result = utils().insertReplyIdentitySlots(el, inspection({}), el.innerText);
            assert.equal(result.salutation, true); assert.equal(result.signature, true);
            before.forEach((node) => assert.ok(el.childNodes.includes(node)));
            assert.equal(el.querySelectorAll("br").length, 1);
        });
    }
    it("inserts before a verified blockquote and preserves the original rich/quote nodes", () => {
        const { doc } = H.createDom(), el = doc.createElement("div");
        el.innerHTML = "<p><b>Current.</b></p><blockquote><p>Historical reply.</p></blockquote>";
        const quote = el.querySelector("blockquote"), bold = el.querySelector("b");
        const result = inspection({}, { salutation: slot("MATCH"), quoteBoundary: { kind: "VERIFIED_TAIL", canonicalQuotedTail: "Historical reply." } });
        assert.equal(utils().insertReplyIdentitySlots(el, result, el.innerText).signature, true);
        assert.equal(el.querySelector("blockquote"), quote); assert.equal(el.querySelector("b"), bold);
        assert.equal(el.childNodes.at(-1), quote);
        assert.match(el.childNodes.at(-2).innerText, /Alex Lee/);
    });
    it("maps browser-rendered list and div line breaks before a verified quote without replacing rich nodes", () => {
        const { doc } = H.createDom(), el = doc.createElement("div");
        el.innerHTML = "<p>Dear Dr Morgan,</p><p><b>Current</b> link</p><ul><li>One</li><li>Two</li></ul><div>Line one</div><div>Line two</div><p>History retained.</p>";
        const bold = el.querySelector("b"), list = el.querySelector("ul"), quote = el.childNodes.at(-1);
        const text = "Dear Dr Morgan,\n\nCurrent link\n\nOne\nTwo\n\nLine one\nLine two\n\nHistory retained.";
        const result = inspection({}, { salutation: slot("MATCH"), quoteBoundary: { kind: "VERIFIED_TAIL", canonicalQuotedTail: "History retained." } });
        assert.equal(utils().insertReplyIdentitySlots(el, result, text).signature, true);
        assert.equal(el.querySelector("b"), bold); assert.equal(el.querySelector("ul"), list);
        assert.equal(el.childNodes.at(-1), quote);
        assert.match(el.childNodes.at(-2).innerText, /Alex Lee/);
    });
    it("splits a text node with Range for the real follow-up plaintext quote form", () => {
        const { doc } = H.createDom(), el = doc.createElement("div");
        el.appendChild(doc.createTextNode("Current.\n\nOn 2026-10-21 acc1 wrote:\n\nOld body."));
        const text = el.firstChild;
        const result = inspection({}, { salutation: slot("MATCH"), quoteBoundary: { kind: "VERIFIED_TAIL", canonicalQuotedTail: "On 2026-10-21 acc1 wrote:\n\nOld body." } });
        assert.equal(utils().insertReplyIdentitySlots(el, result, el.innerText).signature, true);
        assert.equal(el.firstChild, text); assert.equal(text.data, "Current.\n\n");
        assert.equal(el.childNodes.at(-1).data, result.quoteBoundary.canonicalQuotedTail);
    });
    for (const boundary of [{ kind: "UNCERTAIN" }, { kind: "VERIFIED_TAIL", canonicalQuotedTail: "wrong" }, { kind: "VERIFIED_TAIL", canonicalQuotedTail: "repeat" }]) {
        it(`fails closed for ambiguous tail ${JSON.stringify(boundary)}`, () => {
            const { doc } = H.createDom(), el = doc.createElement("div"); el.innerHTML = "<p>repeat</p><p>repeat</p>";
            const output = utils().insertReplyIdentitySlots(el, inspection({}, { quoteBoundary: boundary }), el.innerText);
            assert.equal(output.signature, false); assert.equal(output.unsafeQuote, true); assert.equal(output.salutation, true);
        });
    }
    it("treats server text as text, never executable HTML", () => {
        const { doc } = H.createDom(), el = doc.createElement("div");
        utils().insertReplyIdentitySlots(el, inspection({}, { salutation: slot("MISSING", "<img src=x onerror=alert(1)>\n\nsecond") }), "");
        assert.equal(el.querySelector("img"), null); assert.match(el.innerText, /<img/); assert.equal(el.querySelectorAll("p").length, 3);
    });
});

describe("reply identity complete owner/revision/context snapshot guards", () => {
    for (const field of ["user", "sessionEpoch", "draftsMap", "ownerKey", "targetKey", "convEpoch", "revision", "editor",
        "contactId", "inboundProcessingId", "senderAccountCode", "accountScope", "anchorMailRecordId", "subject", "html", "text"]) {
        it(`invalidates a response after ${field} changes`, () => {
            const original = { user: "admin", sessionEpoch: 1, draftsMap: {}, ownerKey: "admin:acc1:1", targetKey: "1:101:acc1",
                convEpoch: 1, revision: 1, editor: {}, context: { contactId: 1, inboundProcessingId: 101, senderAccountCode: "acc1",
                    accountScope: "acc1", anchorMailRecordId: 88 }, values: { subject: "Re: Subject", html: "<p>Body</p>", text: "Body" } };
            const now = { ...original, context: { ...original.context }, values: { ...original.values } };
            if (Object.hasOwn(now.context, field)) now.context[field] = "changed";
            else if (Object.hasOwn(now.values, field)) now.values[field] = "changed";
            else now[field] = {};
            const s = { instance: { disposed: false }, draftSessionUser: field === "user" ? "other" : "admin",
                draftSessionEpoch: field === "sessionEpoch" ? 2 : 1, replyIdentityCapture: () => now };
            vm.createContext(s);
            vm.runInContext(fn(chat, "replyIdentityMatches", "        "), s);
            assert.equal(s.replyIdentityMatches(original), false);
        });
    }
});

describe("reply identity source-specific backend request contract (F-1)", () => {
    function validate(body) {
        // ReplyIdentityContextRequest.validate(): retain the backend's strict predicates.
        assert.ok(body.contactId > 0);
        assert.ok(body.inboundProcessingId == null || body.inboundProcessingId > 0);
        assert.ok(body.anchorMailRecordId == null || body.anchorMailRecordId > 0);
        if (body.sourceKind === "INBOUND") {
            assert.ok(body.inboundProcessingId != null && body.anchorMailRecordId == null && body.accountScope == null);
        } else {
            assert.equal(body.sourceKind, "CONVERSATION");
            assert.ok(body.inboundProcessingId == null && body.senderAccountCode == null);
        }
    }
    for (const scenario of [
        { name: "filtered inbound", mode: "inbound", anchor: null, sourceKind: "INBOUND" },
        { name: "ordinary conversation", mode: "outbound", anchor: null, sourceKind: "CONVERSATION" },
        { name: "followup-anchor conversation", mode: "inbound", anchor: 88, sourceKind: "CONVERSATION" }
    ]) {
        it(`projects inspect and defaults-preview for ${scenario.name} without losing internal guards`, async () => {
            const requests = [], values = { subject: "Subject", text: "Body", html: "<p>Body</p>" }, draftMap = {};
            const s = {
                instance: { user: "admin", manual: { mode: scenario.mode, targetProcessingId: 101 },
                    selectedContactId: 1, conversation: { accountScope: "acc1" }, convEpoch: 3,
                    meeting: { editorRevision: 4 }, replyIdentity: { seq: 0 }, disposed: false },
                draftSessionEpoch: 2, draftSessionUser: "admin",
                readManualValues: () => values, currentTargetKey: () => "target",
                getDraft: () => ({ followUpAnchorMailRecordId: scenario.anchor }),
                templateReferenceSenderAccount: () => ({ ok: true, code: "acc1" }),
                currentDraftsMap: () => draftMap, conversationCacheKey: (...parts) => parts.join(":"),
                manualInputs: () => ({ editor: values }), replyIdentityEditable: () => true,
                saveDraftFromInputs() {}, refreshReplyIdentityControls() {}, hostShowStatus() {},
                replyIdentityResultMatches: () => true, insertReplyIdentitySlots: () => ({}),
                hostFn: (name) => {
                    if (name === "mcHostInspectReplyIdentity") return async (body) => {
                        validate(body); requests.push({ endpoint: "inspect", body: json(body) });
                        return { salutation: slot("MATCH"), signature: slot("MATCH") };
                    };
                    if (name === "mcHostOpenReplyIdentitySettings") return async (context) => {
                        assert.equal(context.isCurrent(), true);
                        const h = settingsHost({ preview: async (body) => {
                            validate(body); requests.push({ endpoint: "defaults-preview", body: json(body) });
                            return { context: {}, renderedTexts: {}, warnings: [], missingVariables: [] };
                        } });
                        await h.open(context);
                    };
                }
            };
            vm.createContext(s);
            ["replyIdentityCapture", "replyIdentityRequestContext", "replyIdentityMatches", "completeReplyIdentity", "openReplyIdentitySettings"]
                .forEach((name) => vm.runInContext(fn(chat, name, "        "), s));
            const captured = s.replyIdentityCapture();
            assert.equal(captured.context.senderAccountCode, "acc1");
            assert.equal(captured.context.accountScope, "acc1");
            await s.completeReplyIdentity();
            s.openReplyIdentitySettings(); await H.flush();
            assert.deepEqual(requests.map((request) => request.endpoint), ["inspect", "defaults-preview"]);
            for (const { body } of requests) {
                assert.equal(body.sourceKind, scenario.sourceKind);
                if (scenario.sourceKind === "INBOUND") {
                    assert.equal(body.senderAccountCode, "acc1"); assert.equal(body.inboundProcessingId, 101);
                    assert.equal(Object.hasOwn(body, "accountScope"), false);
                } else {
                    assert.equal(body.accountScope, "acc1"); assert.equal(body.anchorMailRecordId, scenario.anchor);
                    assert.equal(Object.hasOwn(body, "senderAccountCode"), false);
                    assert.equal(Object.hasOwn(body, "inboundProcessingId"), false);
                }
            }
            s.instance.conversation.accountScope = "acc2";
            assert.equal(s.replyIdentityMatches(captured), false);
            assert.throws(() => validate({ ...requests[0].body,
                ...(scenario.sourceKind === "INBOUND" ? { accountScope: "acc1" } : { senderAccountCode: "acc1" }) }));
        });
    }
});

function sending(overrides = {}) {
    const sandbox = { console, Set, JSON, Object, String, Array, Number, alerts: [], dialogs: [], requests: [],
        AI_REPLY_WARNING_LABELS: {}, escapeHtml: (s) => String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;"),
        alert(message) { sandbox.alerts.push(message); }, showStatus() {},
        openActionDialog: async (type, options) => { sandbox.dialogs.push({ type, options }); return {}; },
        api: async (url, options) => { sandbox.requests.push(JSON.parse(options.body)); if (sandbox.requests.length % 2) throw { data: { code: "MANUAL_SEND_SAFETY_BLOCKED", findings: [{ code: "REPLY_SALUTATION_MISMATCH", severity: "NORMAL", sentence: "当前：<Alice>；建议：Morgan" }] } }; return {}; }, ...overrides };
    vm.createContext(sandbox);
    vm.runInContext("const manualReplySubmissions = new Set();" + ["freezeManualReplySnapshot", "renderManualReplyFindings", "confirmManualReplySafety", "postManualRichReplySnapshot", "submitManualRichReply"].map((name) => fn(app, name)).join("\n"), sandbox);
    return sandbox;
}
const frozenBody = () => ({ requestId: "same-id", draftRef: { id: 9, version: 7 }, senderAccountCode: "acc1", subject: "Subject", textBody: "Dear Alice\nBody\nBob", htmlBody: "<p>Dear Alice</p><b>Body</b><p>Bob</p>", templateTextBody: "${expertName}", templateHtmlBody: "<p>${expertName}</p>", attachments: [{ id: 8 }, { id: 2 }], qaRuleIds: [3, 1], ragFactCodes: ["fact-a"], meeting: { place: "Zoom" } });
describe("reply identity I-3 both send paths and immutable confirmation", () => {
    it("deep-freezes the complete snapshot and retries exactly once without changing any nested payload", async () => {
        const s = sending(), body = frozenBody(), before = json(body);
        s.openActionDialog = async (type, options) => { s.dialogs.push({ type, options }); body.attachments.reverse(); body.draftRef.version = 100; return {}; };
        assert.equal(await s.submitManualRichReply("/fixture/send", body), true);
        assert.equal(s.requests.length, 2);
        assert.deepEqual(s.requests[0], before);
        assert.deepEqual(s.requests[1], { ...before, safetyWarningConfirmed: true });
        assert.equal(s.dialogs[0].type, "reply-identity-confirm");
        assert.ok(s.dialogs[0].options.message.includes("&lt;Alice&gt;"));
        assert.ok(!s.dialogs[0].options.message.includes("命中原句"));
        const frozen = s.freezeManualReplySnapshot(before); assert.equal(Object.isFrozen(frozen.attachments[0]), true);
    });
    for (const confirmed of [null, false]) {
        it(`cancel ${confirmed} stops without a failure alert and prompts again on next independent send`, async () => {
            const s = sending({ openActionDialog: async () => confirmed });
            assert.equal(await s.submitManualRichReply("/fixture/send", frozenBody()), false);
            assert.equal(s.requests.length, 1); assert.equal(s.alerts.length, 0);
            s.requests.length = 0;
            assert.equal(await s.submitManualRichReply("/fixture/send", frozenBody()), false);
            assert.equal(s.requests.length, 1);
        });
    }
    it("retains a separate typed STRONG confirmation, including cancellation", async () => {
        const s = sending(); const originalApi = s.api;
        s.api = async (...args) => { try { return await originalApi(...args); } catch (error) { error.data.requiresStrongConfirmation = true; error.data.findings.push({ code: "SENSITIVE_DOCUMENT_REQUEST", severity: "STRONG", sentence: "passport" }); throw error; } };
        s.openActionDialog = async (type) => { s.dialogs.push(type); return type === "confirm-typed" ? null : {}; };
        assert.equal(await s.submitManualRichReply("/fixture/send", frozenBody()), false); assert.deepEqual(s.dialogs, ["reply-identity-confirm", "confirm-typed"]); assert.equal(s.requests.length, 1); assert.equal(s.alerts.length, 0);
        s.requests.length = 0; s.openActionDialog = async () => ({});
        await s.submitManualRichReply("/fixture/send", frozenBody()); assert.equal(s.requests[1].strongConfirmationText, "确认发送");
    });
    it("does not suppress real validation/network failures", async () => {
        const s = sending({ api: async () => { throw new Error("offline"); } });
        assert.equal(await s.submitManualRichReply("/fixture/send", frozenBody()), false); assert.match(s.alerts[0], /offline/);
    });
    it("serializes duplicate sends while the original prompt owns the lock", async () => {
        const hold = deferred(), s = sending({ openActionDialog: () => hold.promise });
        const first = s.submitManualRichReply("/fixture/send", frozenBody()); await H.flush();
        assert.equal(await s.submitManualRichReply("/fixture/send", frozenBody()), false); assert.equal(s.requests.length, 1);
        hold.resolve({}); await first; assert.equal(s.requests.length, 2);
    });
    it("legacy unmatched cancel returns false without clearing QA, refreshing detail, alerting failure, or leaking its lock", async () => {
        const { doc } = H.createDom(); const el = doc.createElement("button"), edit = doc.createElement("div"), subject = doc.createElement("input");
        edit.setAttribute("contenteditable", "true"); edit.innerHTML = "<b>Dear Alice</b>";
        const s = sending({ element: el, $: (id) => id.includes("Editor") ? edit : subject, manualReplyQaContext: { qaRuleIds: [1] }, aiReplyState: { adoptContext: {} }, resetPreflightState() { throw new Error("must not clear"); }, showUnmatchedDetail() { throw new Error("must not refresh"); }, refreshMailboxAfterPendingAction() { throw new Error("must not refresh"); }, postManualRichReplySnapshot: async () => null });
        s.postManualRichReplySnapshot = async () => null;
        vm.runInContext(fn(app, "submitManualRichReply", "    "), s);
        assert.equal(await s.submitManualRichReply(101, frozenBody()), false); assert.equal(el.disabled, false); assert.equal(edit.getAttribute("contenteditable"), "true"); assert.equal(s.manualReplyQaContext.qaRuleIds[0], 1); assert.equal(s.alerts.length, 0);
    });
});

function dialogHost() {
    const { doc } = H.createDom();
    const dialog = doc.createElement("dialog"); dialog.setAttribute("id", "actionDialog");
    dialog.innerHTML = '<form id="actionDialogForm"><h3 id="actionDialogTitle"></h3><div id="actionDialogBody"></div><button type="button" data-action="action-dialog-cancel">取消</button><button type="submit">确认执行</button></form>';
    doc.body.appendChild(dialog);
    const s = { document: doc, console, escapeHtml: (value) => String(value) };
    vm.createContext(s);
    vm.runInContext(app.slice(app.indexOf("const ACTION_DIALOG_SCHEMAS ="), app.indexOf("function openActionDialog")) + fn(app, "openActionDialog"), s);
    return { s, doc, dialog };
}
describe("reply identity S-2 native dialog exit lifecycle", () => {
    it("ignores a queued previous close event after the next confirmation opens", async () => {
        const { s, dialog } = dialogHost();
        const first = s.openActionDialog("reply-identity-confirm", { message: "identity" });
        dialog.querySelector("form").fire("submit", false);
        await first;
        const next = s.openActionDialog("confirm-typed", { message: "strong" });
        dialog.fire("close", false);
        assert.equal(dialog.open, true);
        dialog.fire("cancel", false);
        assert.equal(await next, null);
    });
    for (const exit of ["cancel", "close", "click"]) {
        it(`${exit} settles once and restores the shared button labels`, async () => {
            const { s, doc, dialog } = dialogHost();
            const promise = s.openActionDialog("reply-identity-confirm", { message: "fixture" });
            assert.equal(doc.getElementById("actionDialogTitle").textContent, "称呼与签名需确认");
            assert.equal(dialog.querySelector('[type="submit"]').textContent, "确认发送");
            if (exit === "click") H.click(dialog.querySelector('[data-action="action-dialog-cancel"]'));
            else { if (exit === "close") dialog.close(); dialog.fire(exit, false); }
            assert.equal(await promise, null); assert.equal(dialog.open, false);
            assert.equal(dialog.querySelector('[type="submit"]').textContent, "确认执行");
            const next = s.openActionDialog("confirm", { message: "other" }); assert.equal(dialog.querySelector('[data-action="action-dialog-cancel"]').textContent, "取消");
            dialog.fire("cancel", false); await next;
            assert.equal((dialog.listeners.get("cancel") || []).length, 0);
        });
    }
});

function defaultsData() {
    const salutationOptions = [{ id: 1, name: "称呼", content: "Dear ${expertName}," }, { id: 2, name: "称呼-企业", content: "Hello ${expertName}," }];
    const signatureOptions = [{ id: 3, name: "致敬-签名", content: "Best regards,\n${senderName}" }];
    return { salutation: salutationOptions[0], signature: signatureOptions[0], salutationOptions, signatureOptions, baseFingerprint: "base" };
}
function settingsHost(options = {}) {
    const { doc } = H.createDom(); const requests = [], previews = [], timers = new Map(); let timerId = 0;
    const s = { document: doc, console, Map, Object, String, Number, state: { replySnippetsLoaded: true },
        setTimeout: (fn) => { timers.set(++timerId, fn); return timerId; }, clearTimeout: (id) => timers.delete(id),
        showStatus() {}, loadReplySnippets: async () => { if (options.cacheFailure) throw new Error("cache"); },
        api: async (url, request = {}) => {
            requests.push({ url, method: request.method || "GET", body: request.body ? JSON.parse(request.body) : null });
            if (url.endsWith("defaults-preview")) { const body = JSON.parse(request.body); previews.push(body); if (options.preview) return options.preview(body); return { context: { recipientEmail: "fixture@example.invalid", accountCode: "acc1" }, renderedTexts: { salutation: body.salutation.content, signature: body.signature.content }, warnings: [], missingVariables: [] }; }
            if (request.method === "PUT") { if (options.put) return options.put(JSON.parse(request.body)); const data = defaultsData(); data.salutation = { ...JSON.parse(request.body).salutation, name: "称呼-企业" }; data.signature = { ...JSON.parse(request.body).signature, name: "致敬-签名" }; data.salutationOptions = data.salutationOptions.map((option) => option.id === data.salutation.id ? { ...option, content: data.salutation.content } : option); return data; }
            return options.data || defaultsData();
        }
    };
    vm.createContext(s);
    vm.runInContext("let replyIdentitySettingsDialog = null; let replyIdentityDefaultsRevision = 0;" + ["mcHostPreviewReplyIdentityDefaults", "replyIdentitySummary", "mcHostOpenReplyIdentitySettings"].map((name) => fn(app, name)).join("\n"), s);
    return { s, doc, requests, previews, async open(context = { payload: { sourceKind: "INBOUND", contactId: 1, inboundProcessingId: 101 }, isCurrent: () => true }) { await s.mcHostOpenReplyIdentitySettings(context); return doc.querySelector("dialog"); }, tick() { const work = [...timers.values()]; timers.clear(); work.forEach((run) => run()); } };
}
const setting = (dialog, name) => dialog.querySelector(`[name="${name}"]`);
describe("reply identity I-6 atomic defaults, per-selection drafts and isolated previews", () => {
    it("retains unselected edits locally, submits only the final two selections, and never mutates the editor", async () => {
        const h = settingsHost({ cacheFailure: true }), dialog = await h.open();
        const salutation = setting(dialog, "salutationContent"), select = setting(dialog, "salutationSnippetId");
        salutation.value = "unsaved one"; salutation.fire("input"); select.value = "2"; select.fire("change"); salutation.value = "selected two"; salutation.fire("input");
        select.value = "1"; select.fire("change"); assert.equal(salutation.value, "unsaved one"); select.value = "2"; select.fire("change"); assert.equal(salutation.value, "selected two");
        H.click(dialog.querySelector('[data-action="save-identity-settings"]')); await H.flush();
        const put = h.requests.find((request) => request.method === "PUT");
        assert.deepEqual(put.body, { baseFingerprint: "base", salutation: { id: 2, content: "selected two" }, signature: { id: 3, content: "Best regards,\n${senderName}" } });
        assert.equal(h.s.state.replySnippetsLoaded, false); assert.match(dialog.textContent, /默认设置已保存，已有草稿未改变/);
    });
    it("cancel/ESC never PUT and reopening performs a fresh GET", async () => {
        const h = settingsHost(), dialog = await h.open(); setting(dialog, "salutationContent").value = "discard";
        dialog.fire("cancel", false); assert.equal(h.doc.querySelector("dialog"), null); await h.open();
        assert.equal(h.requests.filter((request) => request.method === "GET").length, 2); assert.equal(h.requests.filter((request) => request.method === "PUT").length, 0);
        assert.equal(setting(h.doc.querySelector("dialog"), "salutationContent").value, "Dear ${expertName},");
    });
    for (const kind of ["conflict", "network"]) {
        it(`${kind} preserves input, reports no success and requires a new GET before retry`, async () => {
            const h = settingsHost({ put: async () => { if (kind === "conflict") throw { data: { code: "REPLY_IDENTITY_DEFAULTS_CHANGED" }, status: 409 }; throw new Error("network"); } }), dialog = await h.open();
            const input = setting(dialog, "signatureContent"); input.value = "My edit"; input.fire("input"); H.click(dialog.querySelector('[data-action="save-identity-settings"]')); await H.flush();
            assert.equal(input.value, "My edit"); assert.equal(dialog.querySelector('[data-action="save-identity-settings"]').disabled, true);
            assert.match(dialog.textContent, kind === "conflict" ? /默认设置已被修改，请重新加载后核对/ : /保存结果未确认，请重新加载核对/);
        });
    }
    it("blocks cancel/ESC after PUT dispatch until its actual result", async () => {
        const hold = deferred(), h = settingsHost({ put: () => hold.promise }), dialog = await h.open();
        H.click(dialog.querySelector('[data-action="save-identity-settings"]')); dialog.fire("cancel", false);
        assert.equal(dialog.open, true); assert.equal(dialog.querySelector('[data-action="close-identity-settings"]').disabled, true);
        hold.resolve(defaultsData()); await H.flush(); assert.equal(dialog.querySelector('[data-action="close-identity-settings"]').disabled, false);
    });
    it("never silently selects the first candidate when a default is absent", async () => {
        const data = defaultsData(); data.salutation = null;
        const h = settingsHost({ data }), dialog = await h.open();
        assert.equal(setting(dialog, "salutationSnippetId").value, ""); assert.equal(dialog.querySelector('[data-action="save-identity-settings"]').disabled, true);
    });
    it("debounces edits and ignores out-of-order or closed-dialog previews", async () => {
        const holds = [], h = settingsHost({ preview: (body) => { const hold = deferred(); holds.push({ body, hold }); return hold.promise; } });
        const opening = h.s.mcHostOpenReplyIdentitySettings({ payload: { sourceKind: "INBOUND", contactId: 1 }, isCurrent: () => true }); await H.flush();
        const dialog = h.doc.querySelector("dialog"), input = setting(dialog, "salutationContent"); input.value = "newest"; input.fire("input"); h.tick(); await H.flush();
        const response = (value) => ({ context: {}, renderedTexts: { salutation: value, signature: "signature" }, warnings: [], missingVariables: [] });
        holds[1].hold.resolve(response("newest")); await H.flush(); holds[0].hold.resolve(response("old")); await opening;
        assert.equal(dialog.querySelector('[data-role="identity-salutation"]').textContent, "newest");
        input.value = "closed"; input.fire("input"); h.tick(); await H.flush(); dialog.fire("cancel", false); holds[2].hold.resolve(response("closed")); await H.flush(); assert.equal(h.doc.querySelector("dialog"), null);
    });
    it("does not render a preview after its captured owner or revision becomes stale", async () => {
        const hold = deferred();
        let current = true;
        const h = settingsHost({ preview: () => hold.promise });
        const opening = h.s.mcHostOpenReplyIdentitySettings({ payload: { contactId: 1 }, isCurrent: () => current });
        await H.flush();
        current = false;
        hold.resolve({ context: {}, renderedTexts: { salutation: "wrong owner", signature: "wrong owner" } });
        await opening;
        assert.equal(h.doc.querySelector('[data-role="identity-salutation"]').textContent, "");
        assert.match(h.doc.querySelector("dialog").textContent, /正文或会话已变化/);
    });
});

describe("reply identity frozen presentation and registration contract", () => {
    it("registers toolbar/default summary DOM and exact approved CSS without changing the shared dialog", () => {
        assert.ok(chat.includes('${followUpButton}\n                        <button class="button" type="button" data-action="mc-complete-reply-identity">补全称呼与签名</button>'));
        for (const role of ["reply-identity-default-summary", "mc-complete-reply-identity", "mc-reply-identity-settings"]) assert.ok(chat.includes(role));
        for (const id of ["actionDialog", "actionDialogForm", "actionDialogTitle", "actionDialogBody"]) assert.ok(index.includes(`id="${id}"`));
        const plan = fs.readFileSync(path.join(__dirname, "../../../docs/plans/2026-10-21/reply-identity-02-frontend.md"), "utf8");
        const css = [...plan.matchAll(/```css\n([\s\S]*?)```/g)].find((match) => match[1].startsWith(".reply-identity-defaults"))[1];
        assert.ok(fs.readFileSync(path.join(ROOT, "styles.css"), "utf8").includes(css));
        const keys = [...index.matchAll(/(?:src|href)="[^"\n]+\?v=([^"&]+)/g)].map((match) => match[1]);
        assert.equal(new Set(keys).size, 1);
    });
});
