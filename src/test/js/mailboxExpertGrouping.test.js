const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const appJsPath = path.join(__dirname, "..", "..", "main", "resources", "static", "app.js");
const indexPath = path.join(__dirname, "..", "..", "main", "resources", "static", "index.html");
const stylesPath = path.join(__dirname, "..", "..", "main", "resources", "static", "styles.css");
const appJsSource = fs.readFileSync(appJsPath, "utf-8");
const indexSource = fs.readFileSync(indexPath, "utf-8");
const stylesSource = fs.readFileSync(stylesPath, "utf-8");

function extractFn(name) {
    const regex = new RegExp("(?:async\\s+)?function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}");
    const match = appJsSource.match(regex);
    if (!match) throw new Error("Could not find " + name + " in app.js");
    return match[0];
}

function createMailboxSandbox(options = {}) {
    const store = new Map();
    const viewMode = options.viewMode || "MAIL";
    const mailScope = options.mailScope || "ALL";

    function el(id) {
        if (!store.has(id)) {
            store.set(id, {
                id,
                value: "",
                innerHTML: "",
                textContent: "",
                disabled: false,
                checked: false,
                classList: {
                    toggle: () => {}
                }
            });
        }
        return store.get(id);
    }

    const sandbox = {
        $: (sel) => el(sel.replace(/^#/, "")),
        document: {
            querySelector: (selector) => {
                if (selector === 'input[name="mailboxViewMode"]:checked') {
                    return { value: viewMode };
                }
                if (selector === 'input[name="mailboxMailScope"]:checked') {
                    return { value: mailScope };
                }
                return null;
            },
            querySelectorAll: () => []
        },
        URLSearchParams,
        state: {
            mailbox: {
                items: [],
                groups: [],
                viewMode: viewMode,
                page: 0,
                totalCount: 0,
                pageSize: 20,
                accountsLoaded: true,
                dateDefaultsApplied: false,
                onlyPending: false,
                tagFilter: ""
            }
        },
        operatorStatusLabels: { REPLIED: "已回复" },
        indexLevelLabels: { APPLICATION: "有效" },
        MAILBOX_TAG_BADGE_CLASS: {},
        api: async () => ({ items: [], totalCount: 0, groups: [] }),
        showStatus: () => {},
        renderMailboxTable: () => {},
        renderMailboxExpertGroups: () => {},
        renderMailboxPagination: () => {},
        refreshUnmatchedBadge: async () => {},
        escapeHtml: (v) => String(v == null ? "" : v),
        badge: (text) => `<span class="badge">${text}</span>`,
        renderMailboxTagBadges: () => "",
        renderMailboxActions: (row) => `<button data-action="open-pending" data-id="${row.inboundProcessingId}">处理</button>`
    };

    vm.createContext(sandbox);
    vm.runInContext(extractFn("monitoringToday"), sandbox);
    vm.runInContext(extractFn("mailboxViewMode"), sandbox);
    vm.runInContext(extractFn("mailboxPendingOnly"), sandbox);
    vm.runInContext(extractFn("syncMailboxViewModeControls"), sandbox);
    vm.runInContext("async function loadMailboxAccounts() {}", sandbox);
    vm.runInContext(extractFn("renderMailboxCard"), sandbox);
    vm.runInContext(extractFn("renderMailboxExpertGroups"), sandbox);
    vm.runInContext(extractFn("renderMailboxPagination"), sandbox);
    vm.runInContext(extractFn("loadMailbox"), sandbox);
    sandbox.__store = store;
    return sandbox;
}

describe("mailbox expert grouping", () => {
    it("index.html places view and scope segmented controls together", () => {
        assert.match(indexSource, /class="mailbox-view-controls"/);
        assert.match(indexSource, /name="mailboxViewMode" value="MAIL"/);
        assert.match(indexSource, /name="mailboxViewMode" value="EXPERT"/);
        assert.match(indexSource, /name="mailboxMailScope" value="ALL"/);
        assert.match(indexSource, /name="mailboxMailScope" value="PENDING"/);
        assert.doesNotMatch(indexSource, /id="mailboxFilterOnlyPending"/);
    });

    it("styles.css renders both controls as one compact cluster", () => {
        assert.match(stylesSource, /\.mailbox-view-controls\s*\{[\s\S]*?gap:\s*8px/);
        assert.match(stylesSource, /\.mailbox-segmented-control label:has\(input:checked\)/);
        assert.match(stylesSource, /outline: 2px solid rgba\(var\(--primary-rgb\), 0\.35\)/);
    });

    it("EXPERT plus ALL calls by-expert with normal filters and no pending flag", async () => {
        const sb = createMailboxSandbox({ viewMode: "EXPERT" });
        let requestUrl = "";

        sb.api = async (url) => {
            requestUrl = url;
            return { groups: [], totalCount: 0 };
        };

        sb.$("#mailboxFilterDirection").value = "INBOUND";
        sb.$("#mailboxFilterTag").value = "收件";
        sb.$("#mailboxFilterStartDate").value = "2026-07-01";
        sb.$("#mailboxFilterEndDate").value = "2026-07-18";
        sb.$("#mailboxFilterAccountCode").value = "acc1";
        sb.$("#mailboxFilterRecipient").value = "expert@example.com";
        sb.$("#mailboxFilterKeyword").value = "材料";

        await sb.loadMailbox();

        assert.ok(requestUrl.startsWith("/api/mail/mailbox/by-expert?"));
        const query = new URLSearchParams(requestUrl.split("?")[1] || "");
        assert.equal(query.get("accountCode"), "acc1");
        assert.equal(query.get("recipientEmail"), "expert@example.com");
        assert.equal(query.get("keyword"), "材料");
        assert.equal(query.get("direction"), "INBOUND");
        assert.equal(query.get("pending"), null);
        assert.equal(query.get("tag"), "收件");
        assert.equal(query.get("startDate"), "2026-07-01");
        assert.equal(query.get("endDate"), "2026-07-18");
        assert.equal(sb.state.mailbox.viewMode, "EXPERT");
    });

    it("EXPERT plus PENDING keeps the pending scope independent", async () => {
        const sb = createMailboxSandbox({ viewMode: "EXPERT", mailScope: "PENDING" });
        let requestUrl = "";
        sb.api = async (url) => {
            requestUrl = url;
            return { groups: [], totalCount: 0 };
        };
        sb.$("#mailboxFilterStartDate").value = "2026-07-01";
        sb.$("#mailboxFilterEndDate").value = "2026-07-18";

        await sb.loadMailbox();

        const query = new URLSearchParams(requestUrl.split("?")[1] || "");
        assert.equal(query.get("pending"), "true");
        assert.equal(query.get("startDate"), null);
        assert.equal(query.get("endDate"), null);
        assert.equal(sb.$("#mailboxFilterDirection").disabled, false);
        assert.equal(sb.$("#mailboxFilterTag").disabled, false);
        assert.equal(sb.$("#mailboxFilterStartDate").disabled, true);
    });

    it("renderMailboxExpertGroups outputs nested expert group and mailbox card actions", () => {
        const sb = createMailboxSandbox();
        sb.state.mailbox.groups = [{
            expertContactId: 100,
            expertName: "张三",
            expertEmail: "zhang@example.com",
            expertOrcidId: "0000-0001-0002-0003",
            operatorStatus: "REPLIED",
            expertIndexLevel: "APPLICATION",
            mailCount: 3,
            pendingCount: 1,
            mails: [{
                id: 10,
                source: "INBOUND_PROCESSING",
                expertContactId: 100,
                direction: "INBOUND",
                processStatus: "MANUAL_REVIEW",
                inboundProcessingId: 10,
                subject: "Question",
                senderAccountCode: "acc1",
                expertEmail: "zhang@example.com",
                expertName: "张三",
                tags: ["专家", "收件", "待处理"],
                timestamp: "2026-07-18T10:00:00"
            }]
        }];

        sb.renderMailboxExpertGroups();
        const html = sb.$("#mailboxList").innerHTML;

        assert.match(html, /class="inbound-expert-group"/);
        assert.match(html, /data-action="open-monitoring-contact" data-id="100"/);
        assert.match(html, /共 3 封 · 待处理 1 封/);
        assert.match(html, /class="mailbox-card" data-source="INBOUND_PROCESSING" data-id="10"/);
        assert.match(html, /data-action="open-pending" data-id="10"/);
    });

    it("renderMailboxPagination uses expert unit label", () => {
        const sb = createMailboxSandbox();
        sb.state.mailbox.viewMode = "EXPERT";
        sb.state.mailbox.totalCount = 2;
        sb.state.mailbox.page = 0;

        sb.renderMailboxPagination();
        assert.match(sb.$("#mailboxPagination").innerHTML, /共 2 位专家/);
    });
});
