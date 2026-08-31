const fs = require("fs");
const path = require("path");
const assert = require("assert");
const { describe, it } = require("node:test");

const root = path.join(__dirname, "..", "..", "main", "resources", "static");
const html = fs.readFileSync(path.join(root, "index.html"), "utf-8");
const app = fs.readFileSync(path.join(root, "app.js"), "utf-8");
const css = fs.readFileSync(path.join(root, "styles.css"), "utf-8");

const CACHE_KEY = "20260831-expert-material-tags";
const CHECK_REPLIES_TAG = '<button class="button" id="checkRepliesBtn" onclick="handleCheckReplies()">检查回复</button>';

describe("check replies relocation (p1)", () => {
    it("I-1: id=\"checkRepliesBtn\" appears exactly once in index.html, inside the mailbox view", () => {
        const idMatches = html.match(/id="checkRepliesBtn"/g) || [];
        assert.strictEqual(idMatches.length, 1, "id=\"checkRepliesBtn\" must appear exactly once in index.html");
        const contactsIdx = html.indexOf('id="view-contacts"');
        const mailboxIdx = html.indexOf('id="view-mailbox"');
        const btnIdx = html.indexOf('id="checkRepliesBtn"');
        assert.ok(contactsIdx >= 0 && mailboxIdx >= 0, "view-contacts and view-mailbox must exist");
        assert.ok(btnIdx > mailboxIdx, "checkRepliesBtn must live in the mailbox view, not the contacts view");
        assert.ok(!html.slice(contactsIdx, mailboxIdx).includes("checkRepliesBtn"),
            "checkRepliesBtn must not remain in view-contacts");
    });

    it("I-1: the button tag is preserved verbatim", () => {
        assert.ok(html.includes(CHECK_REPLIES_TAG), "button tag must be preserved verbatim");
    });

    it("I-1: all five app.js reference sites stay verbatim", () => {
        assert.strictEqual((app.match(/checkRepliesBtn/g) || []).length, 5,
            "checkRepliesBtn must appear exactly 5 times in app.js");
        assert.ok(app.includes('checkRepliesBtn: "检查回复"'),
            "taskButtonOriginalTexts entry must stay");
        assert.ok(app.includes('CHECK_REPLIES: { label: "检查回复", btnId: "checkRepliesBtn" }'),
            "taskButtonMapping entry must stay");
        assert.ok(app.includes('openTaskModal(taskType, "检查回复", "checkRepliesBtn", { knownActiveAtOpen: true });'),
            "handleCheckReplies call must stay");
        assert.ok(app.includes('openTaskModal(taskType, "检查回复", "checkRepliesBtn", { launchRequested: true });'),
            "executeCheckReplies call must stay");
        assert.ok(app.includes('btnId: "checkRepliesBtn"'),
            "taskLaunchConfigs entry must stay");
    });

    it("I-2: cross-view selection and view switching are untouched", () => {
        assert.strictEqual((app.match(/\$\$\(["']\.expert-select-cb:checked["']\)/g) || []).length, 2,
            "both .expert-select-cb:checked reads (executeCheckReplies + preload) must remain");
        assert.match(app, /function setView\([\s\S]*?classList\.toggle\("active",/,
            "setView must keep toggling .active on .view sections");
        assert.ok(app.includes("async function resumeProgressPollingIfNeeded()"),
            "resumeProgressPollingIfNeeded must remain unchanged");
        assert.ok(html.includes('id="view-contacts"') && html.includes('id="view-mailbox"'),
            ".view sections must stay in the DOM");
    });

    it("I-3: the cache-key triad uses one current value everywhere", () => {
        const keys = (html.match(/\?v=[^"]+/g) || []).map((k) => k.slice(3));
        assert.strictEqual(keys.length, 3, "index.html must carry exactly three cache-busted asset URLs");
        for (const key of keys) {
            assert.strictEqual(key, CACHE_KEY, "every cache key must equal " + CACHE_KEY);
        }
    });

    it("S-1: exactly one .panel-head-actions in index.html, 检查回复 to the left of 批量发送", () => {
        assert.strictEqual((html.match(/class="panel-head-actions"/g) || []).length, 1,
            ".panel-head-actions must be used exactly once");
        const checkIdx = html.indexOf('id="checkRepliesBtn"');
        const bulkIdx = html.indexOf('id="bulkOutreachBtn"');
        assert.ok(checkIdx >= 0 && bulkIdx >= 0);
        assert.ok(checkIdx < bulkIdx, "检查回复 must render to the left of 批量发送");
        assert.ok(html.slice(checkIdx, bulkIdx).includes('onclick="handleCheckReplies()"'),
            "检查回复 must sit inside the actions container right next to 批量发送");
    });

    it("S-1: .panel-head-actions rule block is inserted verbatim in styles.css", () => {
        assert.ok(css.includes(".panel-head-actions {\n    display: inline-flex;\n    align-items: center;\n    gap: 8px;\n    flex-shrink: 0;\n}"),
            "contract rule block must be present verbatim");
    });

    it("S-2: contacts toolbar keeps 自动回复 and 回刷 ES in order, without 检查回复", () => {
        const contactsIdx = html.indexOf('id="view-contacts"');
        const mailboxIdx = html.indexOf('id="view-mailbox"');
        const fragment = html.slice(contactsIdx, mailboxIdx);
        assert.ok(!fragment.includes("checkRepliesBtn"),
            "checkRepliesBtn must be gone from the contacts toolbar");
        const bulkAutoIdx = fragment.indexOf('id="bulkAutoReplyBtn"');
        const backfillIdx = fragment.indexOf('id="backfillOperatorStatusBtn"');
        assert.ok(bulkAutoIdx >= 0 && backfillIdx > bulkAutoIdx,
            "自动回复：加载中... must stay before 回刷 ES");
    });
});
