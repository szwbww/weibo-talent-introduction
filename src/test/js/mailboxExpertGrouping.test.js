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
    it("index.html includes mailbox view mode radio group", () => {
        assert.match(indexSource, /class="mailbox-view-mode"/);
        assert.match(indexSource, /name="mailboxViewMode" value="MAIL"/);
        assert.match(indexSource, /name="mailboxViewMode" value="EXPERT"/);
    });

    it("styles.css includes mailbox-view-mode contract rules", () => {
        assert.match(stylesSource, /\.mailbox-view-mode label:has\(input:checked\)/);
        assert.match(stylesSource, /outline: 2px solid rgba\(var\(--primary-rgb\), 0\.35\)/);
    });

    it("EXPERT mode calls pending-by-expert without direction tag or date params", async () => {
        const sb = createMailboxSandbox({ viewMode: "EXPERT" });
        let requestUrl = "";

        sb.api = async (url) => {
            requestUrl = url;
            return { groups: [], totalCount: 0 };
        };

        sb.$("#mailboxFilterDirection").value = "INBOUND";
        sb.$("#mailboxFilterTag").value = "待处理";
        sb.$("#mailboxFilterStartDate").value = "2026-07-01";
        sb.$("#mailboxFilterEndDate").value = "2026-07-18";
        sb.$("#mailboxFilterAccountCode").value = "acc1";
        sb.$("#mailboxFilterRecipient").value = "expert@example.com";
        sb.$("#mailboxFilterKeyword").value = "材料";

        await sb.loadMailbox();

        assert.ok(requestUrl.startsWith("/api/mail/mailbox/pending-by-expert?"));
        const query = new URLSearchParams(requestUrl.split("?")[1] || "");
        assert.equal(query.get("accountCode"), "acc1");
        assert.equal(query.get("recipientEmail"), "expert@example.com");
        assert.equal(query.get("keyword"), "材料");
        assert.equal(query.get("direction"), null);
        assert.equal(query.get("pending"), null);
        assert.equal(query.get("startDate"), null);
        assert.equal(query.get("endDate"), null);
        assert.equal(sb.state.mailbox.viewMode, "EXPERT");
    });

    it("syncMailboxViewModeControls disables mail-only filters in EXPERT mode", () => {
        const sb = createMailboxSandbox({ viewMode: "EXPERT" });
        sb.syncMailboxViewModeControls();

        assert.equal(sb.$("#mailboxFilterDirection").disabled, true);
        assert.equal(sb.$("#mailboxFilterTag").disabled, true);
        assert.equal(sb.$("#mailboxFilterOnlyPending").disabled, true);
        assert.equal(sb.$("#mailboxFilterStartDate").disabled, true);
        assert.equal(sb.$("#mailboxFilterEndDate").disabled, true);
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
        assert.match(html, /1 封待处理/);
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
