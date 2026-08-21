const fs = require("fs");
const path = require("path");
const assert = require("assert");
const { describe, it } = require("node:test");

const root = path.join(__dirname, "..", "..", "main", "resources");
const html = fs.readFileSync(path.join(root, "static", "index.html"), "utf-8");
const app = fs.readFileSync(path.join(root, "static", "app.js"), "utf-8");

const BUTTON_TAG = '<button class="button primary" id="bulkOutreachBtn" onclick="handleBulkOutreach()">批量发送</button>';
const PANEL_HEADER = "已激活账号收发邮件记录";

describe("batch entry relocation (a3)", () => {
    it("M-4: id=bulkOutreachBtn appears exactly once in index.html", () => {
        const matches = html.match(/id="bulkOutreachBtn"/g);
        assert.ok(matches, "bulkOutreachBtn must exist in index.html");
        assert.strictEqual(matches.length, 1, "bulkOutreachBtn must appear exactly once");
    });

    it("I3-3: button sits inside the mailbox panel, between view-mailbox and view-inbound-summary", () => {
        const mailboxIdx = html.indexOf('id="view-mailbox"');
        const inboundIdx = html.indexOf('id="view-inbound-summary"');
        const btnIdx = html.indexOf('id="bulkOutreachBtn"');
        assert.ok(mailboxIdx >= 0, "view-mailbox section must exist");
        assert.ok(inboundIdx >= 0, "view-inbound-summary section must exist");
        assert.ok(btnIdx >= 0, "bulkOutreachBtn must exist");
        assert.ok(btnIdx > mailboxIdx, "button must come after view-mailbox");
        assert.ok(btnIdx < inboundIdx, "button must come before view-inbound-summary");
    });

    it("I3-2: both head buttons live in .panel-head-actions, 检查回复 before 批量发送", () => {
        const headPattern = new RegExp(
            `<div class="panel-head">\\s*<h2>${PANEL_HEADER}</h2>\\s*` +
            `<div class="panel-head-actions">\\s*` +
            `<button class="button" id="checkRepliesBtn" onclick="handleCheckReplies\\(\\)">检查回复</button>\\s*` +
            `${BUTTON_TAG.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}\\s*` +
            `</div>`
        );
        assert.match(html, headPattern, "panel-head must wrap both buttons in .panel-head-actions with 检查回复 first");
    });

    it("I3-2: button tag keeps class, onclick, and label verbatim", () => {
        assert.ok(html.includes(BUTTON_TAG), "button tag must be preserved verbatim");
        assert.ok(BUTTON_TAG.includes('class="button primary"'));
        assert.ok(BUTTON_TAG.includes('onclick="handleBulkOutreach()"'));
        assert.ok(BUTTON_TAG.includes(">批量发送</button>"));
    });

    it("I3-1: contacts view region no longer contains the bulk outreach button", () => {
        const contactsIdx = html.indexOf('id="view-contacts"');
        const mailboxIdx = html.indexOf('id="view-mailbox"');
        assert.ok(contactsIdx >= 0 && mailboxIdx >= 0, "contacts and mailbox views must exist");
        const contactsFragment = html.slice(contactsIdx, mailboxIdx);
        assert.ok(!contactsFragment.includes("bulkOutreachBtn"), "bulkOutreachBtn must not remain inside view-contacts");
    });

    it("I3-1: nav tab for contacts shows 专家列表", () => {
        const navPattern = /data-view="contacts"[\s\S]*?<span>专家列表<\/span>/;
        assert.match(html, navPattern, "the contacts nav span must read 专家列表");
    });

    it("I3-1: viewMeta.contacts title is 专家列表 and the quad registration stays contacts", () => {
        assert.ok(app.includes('contacts: ["专家列表", "查看联系状态、邮件时间线和人工处理。"],'),
            "viewMeta.contacts first element must be 专家列表 with subtitle unchanged");
        assert.ok(html.includes('data-view="contacts"'), "data-view registration must stay contacts");
        assert.ok(html.includes('id="view-contacts"'), "view section id must stay view-contacts");
        assert.ok(app.includes("state.view === \"contacts\""), "refreshCurrentView branch must stay contacts");
    });
});
