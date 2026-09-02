const fs = require("fs");
const path = require("path");
const assert = require("assert");
const { describe, it } = require("node:test");

const root = path.join(__dirname, "..", "..", "main", "resources", "static");
const html = fs.readFileSync(path.join(root, "index.html"), "utf-8");
const app = fs.readFileSync(path.join(root, "app.js"), "utf-8");
const css = fs.readFileSync(path.join(root, "styles.css"), "utf-8");

const CACHE_KEY = "20260902-monitoring-window";
const CHECK_REPLIES_TAG = '<button class="button" id="checkRepliesBtn" onclick="handleCheckReplies()">检查回复</button>';
const BULK_OUTREACH_TAG = '<button class="button primary" id="bulkOutreachBtn" onclick="handleBulkOutreach()">批量发送</button>';
const AUTO_REPLY_TAG = '<button class="button" id="bulkAutoReplyBtn">自动回复：加载中...</button>';

const contactsFragment = () => {
    const contactsIdx = html.indexOf('id="view-contacts"');
    const mailboxIdx = html.indexOf('id="view-mailbox"');
    assert.ok(contactsIdx >= 0 && mailboxIdx >= 0, "view-contacts and view-mailbox must exist");
    return html.slice(contactsIdx, mailboxIdx);
};

describe("check replies relocation (p1)", () => {
    it("I-1: id=\"checkRepliesBtn\" appears exactly once in index.html, inside the mailbox view", () => {
        const idMatches = html.match(/id="checkRepliesBtn"/g) || [];
        assert.strictEqual(idMatches.length, 1, "id=\"checkRepliesBtn\" must appear exactly once in index.html");
        const mailboxIdx = html.indexOf('id="view-mailbox"');
        const btnIdx = html.indexOf('id="checkRepliesBtn"');
        assert.ok(btnIdx > mailboxIdx, "checkRepliesBtn must live in the mailbox view, not the contacts view");
        assert.ok(!contactsFragment().includes("checkRepliesBtn"),
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

    it("S-1: .panel-head-actions rule block is present verbatim in styles.css", () => {
        assert.ok(css.includes(".panel-head-actions {\n    display: inline-flex;\n    align-items: center;\n    gap: 8px;\n    flex-shrink: 0;\n}"),
            "contract rule block must be present verbatim");
    });

    it("I-6/S-4: bulkAutoReplyBtn lives exactly once, in the mailbox actions after 批量发送", () => {
        assert.strictEqual((html.match(/id="bulkAutoReplyBtn"/g) || []).length, 1,
            "bulkAutoReplyBtn must appear exactly once in index.html");
        assert.ok(!contactsFragment().includes("bulkAutoReplyBtn"),
            "bulkAutoReplyBtn must be gone from the contacts toolbar");
        assert.ok(html.includes(BULK_OUTREACH_TAG), "bulkOutreachBtn tag must be preserved verbatim");
        assert.ok(html.includes(AUTO_REPLY_TAG), "bulkAutoReplyBtn tag must be preserved verbatim");
        const checkIdx = html.indexOf('id="checkRepliesBtn"');
        const bulkIdx = html.indexOf('id="bulkOutreachBtn"');
        const autoIdx = html.indexOf('id="bulkAutoReplyBtn"');
        assert.ok(checkIdx < bulkIdx && bulkIdx < autoIdx,
            "mailbox actions order must be 检查回复 → 批量发送 → 自动回复");
    });

    it("I-6: auto-reply summary refresh moved out of loadContacts into the mailbox view refresh", () => {
        const loadContactsBody = app.slice(app.indexOf("async function loadContacts()"), app.indexOf("function renderContactListItems()"));
        assert.ok(!loadContactsBody.includes("refreshAutoReplySummary"),
            "loadContacts must no longer refresh the auto-reply summary");
        const refreshBody = app.slice(app.indexOf("async function refreshCurrentView()"), app.indexOf("async function loadAccounts()"));
        assert.ok(refreshBody.includes('if (state.view === "mailbox") await Promise.all(['),
            "refreshCurrentView mailbox branch must await loadMailbox and refreshAutoReplySummary together");
        assert.ok(refreshBody.includes("refreshAutoReplySummary"),
            "mailbox view refresh must call refreshAutoReplySummary");
        assert.ok(app.includes('async function refreshAutoReplySummary()'),
            "refreshAutoReplySummary must remain defined");
        assert.ok(app.includes('function initBulkAutoReply()'),
            "initBulkAutoReply must remain defined");
    });

    it("I-1/S-1: funnel options are pure Chinese with RAW/CANDIDATE/APPLICATION values", () => {
        const levelIdx = html.indexOf('id="expertIndexLevel"');
        assert.ok(levelIdx >= 0, "expertIndexLevel select must exist");
        const fragment = html.slice(levelIdx, html.indexOf("</select>", levelIdx));
        assert.ok(fragment.includes('<option value="RAW">原始</option>'), "RAW option must read 原始");
        assert.ok(fragment.includes('<option value="CANDIDATE" selected>筛选</option>'), "CANDIDATE must be selected and read 筛选");
        assert.ok(fragment.includes('<option value="APPLICATION">有效</option>'), "APPLICATION option must read 有效");
        assert.ok(!fragment.includes("RAW -") && !fragment.includes("CANDIDATE -") && !fragment.includes("APPLICATION -"),
            "no English enum + dash labels may remain");
    });

    it("I-2/S-1: expertIndexSize appears exactly once, inside contactPager; pager is not statically hidden", () => {
        assert.strictEqual((html.match(/id="expertIndexSize"/g) || []).length, 1,
            "expertIndexSize must appear exactly once in index.html");
        const pagerIdx = html.indexOf('id="contactPager"');
        const sizeIdx = html.indexOf('id="expertIndexSize"');
        assert.ok(pagerIdx >= 0 && sizeIdx > pagerIdx,
            "expertIndexSize must live inside the contactPager");
        const pagerFragment = html.slice(pagerIdx, html.indexOf("</div>", html.indexOf('id="contactNextPage"')));
        assert.ok(pagerFragment.includes('id="contactPrevPage"') && pagerFragment.includes('id="contactPageInfo"')
            && pagerFragment.includes('id="contactNextPage"'),
            "pager must keep prev/info/next controls");
        assert.ok(!pagerFragment.includes("hidden"),
            "contactPager must not carry a static hidden attribute");
        const filterGroupIdx = html.indexOf('id="contactsFilterGroup"');
        const actionsIdx = html.indexOf('class="toolbar-group toolbar-actions"');
        const filterFragment = html.slice(filterGroupIdx, actionsIdx);
        assert.ok(!filterFragment.includes("expertIndexSize"),
            "expertIndexSize must not remain in the filter group");
        const sizeOpts = ["10 条/页", "20 条/页", "50 条/页", "100 条/页"];
        for (const opt of sizeOpts) {
            assert.ok(pagerFragment.includes(opt), `pager must keep option ${opt}`);
        }
    });

    it("I-5/S-2: both chip rows keep all data-value buttons inside .toolbar-chip-row", () => {
        assert.strictEqual((html.match(/class="toolbar-chip-row"/g) || []).length, 2,
            ".toolbar-chip-row must wrap exactly the two chip groups");
        const typeIdx = html.indexOf('id="expertTypeTagSelect"');
        const hasFieldIdx = html.indexOf('id="hasFieldTagSelect"');
        assert.ok(typeIdx >= 0 && hasFieldIdx > typeIdx, "both tag selects must exist in order");
        assert.ok(html.slice(typeIdx, hasFieldIdx).includes("class=\"tag-chip\" data-value=\"PRODUCTION_RND\""),
            "expertTypeTagSelect buttons must be preserved");
        assert.ok(html.slice(hasFieldIdx, html.indexOf("expertGateTemplateFilter")).includes("class=\"tag-chip\" data-value=\"employment\""),
            "hasFieldTagSelect buttons must be preserved");
        for (const chipRow of ["expertTypeTagSelect", "hasFieldTagSelect"]) {
            const rowStart = html.lastIndexOf('<div class="toolbar-chip-row">', html.indexOf(`id="${chipRow}"`));
            assert.ok(rowStart >= 0, `${chipRow} must be wrapped by .toolbar-chip-row`);
        }
    });

    it("I-5: H-Index / citation / recent-years / gate-template filters each appear exactly once", () => {
        for (const id of ["expertHIndexMinFilter", "expertCitationMinFilter", "expertRecentYearsFilter", "expertGateTemplateFilter"]) {
            assert.strictEqual((html.match(new RegExp(`id="${id}"`, "g")) || []).length, 1,
                `${id} must appear exactly once in index.html`);
        }
        assert.ok(html.includes('id="expertGateSummary"'), "gate filter summary must stay");
        assert.ok(html.includes('id="expertGateMatchCount"') && html.includes('id="expertGateTotalCount"'),
            "gate filter match/total counters must stay");
    });

    it("S-2: the .toolbar-chip-row rules are present verbatim in styles.css", () => {
        assert.ok(css.includes(".toolbar-chip-row {\n    display: flex;\n    align-items: flex-start;\n    gap: 8px;\n    flex: 1 0 100%;\n    min-width: 0;\n}"),
            ".toolbar-chip-row rule block must be present verbatim");
        assert.ok(css.includes(".toolbar-chip-row > .toolbar-label {\n    flex: 0 0 72px;\n    padding-top: 7px;\n}"),
            ".toolbar-chip-row > .toolbar-label rule block must be present verbatim");
        assert.ok(css.includes(".toolbar-chip-row > .tag-select {\n    flex: 1 1 auto;\n    min-width: 0;\n}"),
            ".toolbar-chip-row > .tag-select rule block must be present verbatim");
    });

    it("I-3: contact detail title no longer mentions the mail timeline", () => {
        assert.strictEqual((html.match(/引进状态演进与往来邮件时间线/g) || []).length, 0,
            "old timeline title must be gone");
        assert.strictEqual((html.match(/专家引进状态与联系详情/g) || []).length, 1,
            "new contact detail title must appear once");
    });

    it("I-3: loadContactDetail source no longer maps detail.mails into renderMailItem", () => {
        const fnStart = app.indexOf("async function loadContactDetail(");
        const fnEnd = app.indexOf("async function", fnStart + 1);
        const fnBody = app.slice(fnStart, fnEnd);
        assert.ok(!fnBody.includes("detail.mails.slice().reverse().map(renderMailItem)"),
            "contact panel must not render the mail timeline from detail.mails");
        assert.ok(!fnBody.includes("mail-timeline"),
            "contact panel must not use .mail-timeline");
        assert.ok(fnBody.includes("open-contact-mailbox"),
            "contact panel must include the 查看收发邮件 action button");
        assert.ok(fnBody.includes('api(`/api/mail/mailbox/by-expert?expertContactId=${contactId}&page=0&size=1`)'),
            "contact panel must fetch the all-time exact-expert mail summary");
        assert.ok(fnBody.includes("邮件统计加载失败"),
            "summary failure branch must render 邮件统计加载失败");
        assert.ok(fnBody.includes("收到 ${mailStatReceived} 封"),
            "summary must render 收到/成功发出/发送失败 counts");
        assert.ok(app.includes("function renderMailItem("),
            "renderMailItem must remain defined for other mail-body hosts");
    });

    it("I-4: mailbox focus helpers exist and are wired", () => {
        assert.ok(app.includes("function clearMailboxExpertFocus()"),
            "clearMailboxExpertFocus helper must exist");
        assert.ok(app.includes("function openExpertMailbox(contactId, email)"),
            "openExpertMailbox helper must exist");
        assert.ok(app.includes('if (action === "open-contact-mailbox")'),
            "handleContactAction must route open-contact-mailbox");
        assert.ok(app.includes("focusExpertContactId") && app.includes("focusExpertEmail"),
            "mailbox state must carry focus fields");
        assert.ok(app.includes('if (tab.dataset.view === "mailbox") clearMailboxExpertFocus();'),
            "normal nav into mailbox must clear the expert focus");
    });
});
