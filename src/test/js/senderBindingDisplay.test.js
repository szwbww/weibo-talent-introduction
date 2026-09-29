const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const appJsPath = path.join(__dirname, "..", "..", "main", "resources", "static", "app.js");
const appJsSource = fs.readFileSync(appJsPath, "utf-8");

function extractFn(name) {
    const regex = new RegExp("(?:async\\s+)?function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}");
    const match = appJsSource.match(regex);
    if (!match) throw new Error("Could not find " + name + " in app.js");
    return match[0];
}

// DOM stub: Map-backed #id lookup so any id (incl. #senderBindingSelect) is covered.
function createElStub(store) {
    return (sel) => {
        const id = sel.replace(/^#/, "");
        if (!store.has(id)) {
            store.set(id, {
                id,
                value: "",
                disabled: false,
                innerHTML: "",
                hidden: false,
                parentElement: { style: {}, title: "" },
                appendChild(child) {
                    this.innerHTML += child.outerHTML || String(child);
                }
            });
        }
        return store.get(id);
    };
}

function createListSandbox() {
    const store = new Map();
    const sandbox = {
        $: createElStub(store),
        state: {
            contacts: []
        },
        operatorStatusLabels: {},
        expertTagLabels: {},
        indexLevelLabels: {
            RAW: "原始",
            CANDIDATE: "筛选",
            APPLICATION: "有效"
        },
        contactBadgeType: () => "",
        labelStatus: (v) => v || "",
        badge: (v, t) => `<span class="badge ${t || ""}">${v}</span>`,
        staggerListItems: () => {}
    };
    vm.createContext(sandbox);
    vm.runInContext(extractFn("escapeHtml"), sandbox);
    vm.runInContext(extractFn("renderContactListItems"), sandbox);
    sandbox.__store = store;
    return sandbox;
}

function bareContact(overrides = {}) {
    return Object.assign({
        orcidId: "0001",
        operatorStatus: "NOT_CONTACTED",
        contactId: null,
        contactStatus: "NEW",
        needsManualAttention: false,
        tags: [],
        hIndex: null,
        enrichedAt: null,
        displayName: "A",
        email: "a@example.com",
        country: "",
        indexLevel: "CANDIDATE",
        indexLevelName: "筛选",
        employment: null,
        boundSenderAccountCode: null,
        senderAccountChanged: false
    }, overrides);
}

describe("senderBindingDisplay list rendering", () => {
    it("renders binding account text for bound contact", () => {
        const sb = createListSandbox();
        sb.state.contacts = [bareContact({ boundSenderAccountCode: "ACC_A" })];
        sb.renderContactListItems();
        assert.ok(sb.$("#contactList").innerHTML.includes("账号：ACC_A"));

        // DOM stub 陷阱 (K-dom-stub-tests-hide-dangling-refs): stub must cover
        // #senderBindingSelect, and the T3.3 action branches must reference
        // defined loadContactDetail / loadContacts functions.
        sb.$("#senderBindingSelect"); // force stub coverage of the select id
        const actionSandbox = {
            $: createElStub(new Map()),
            state: { contacts: [] }
        };
        vm.createContext(actionSandbox);
        vm.runInContext(extractFn("loadContactDetail"), actionSandbox);
        vm.runInContext(extractFn("loadContacts"), actionSandbox);
        vm.runInContext(extractFn("handleContactAction"), actionSandbox);
        assert.strictEqual(typeof actionSandbox.loadContactDetail, "function");
        assert.strictEqual(typeof actionSandbox.loadContacts, "function");
        assert.ok(actionSandbox.$("#senderBindingSelect"));
    });

    it("renders 未绑定 when no binding", () => {
        const sb = createListSandbox();
        sb.state.contacts = [bareContact({ boundSenderAccountCode: null })];
        sb.renderContactListItems();
        assert.ok(sb.$("#contactList").innerHTML.includes("账号：未绑定"));
    });

    it("renders sender-changed tag only when flag is true", () => {
        const flagged = createListSandbox();
        flagged.state.contacts = [bareContact({ senderAccountChanged: true })];
        flagged.renderContactListItems();
        const flaggedHtml = flagged.$("#contactList").innerHTML;
        assert.ok(flaggedHtml.includes("expert-tag tag-sender-changed"));
        assert.ok(flaggedHtml.includes("发送账号已变更"));

        const unflagged = createListSandbox();
        unflagged.state.contacts = [bareContact({ senderAccountChanged: false })];
        unflagged.renderContactListItems();
        const unflaggedHtml = unflagged.$("#contactList").innerHTML;
        assert.ok(!unflaggedHtml.includes("expert-tag tag-sender-changed"));
        assert.ok(!unflaggedHtml.includes("发送账号已变更"));
    });

    it("escapes account code", () => {
        const sb = createListSandbox();
        sb.state.contacts = [bareContact({ boundSenderAccountCode: "<img src=x>" })];
        sb.renderContactListItems();
        const html = sb.$("#contactList").innerHTML;
        assert.ok(html.includes("&lt;img"));
        assert.ok(!html.includes("<img"));
    });

    it("expert-row-sub renders even when only binding exists", () => {
        const sb = createListSandbox();
        sb.state.contacts = [bareContact({
            employment: null,
            tags: [],
            hIndex: null,
            enrichedAt: null,
            boundSenderAccountCode: "ACC_E"
        })];
        sb.renderContactListItems();
        assert.ok(sb.$("#contactList").innerHTML.includes("expert-row-sub"));
        assert.ok(sb.$("#contactList").innerHTML.includes("账号：ACC_E"));
    });
});

describe("senderBindingDisplay accounts table", () => {
    it("account row renders bound expert count", async () => {
        const store = new Map();
        const sandbox = {
            $: createElStub(store),
            state: { accounts: [] },
            badge: (v, t) => `<span class="badge ${t || ""}">${v}</span>`,
            api: async () => []
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("escapeHtml"), sandbox);
        vm.runInContext(extractFn("loadAccounts"), sandbox);
        sandbox.api = async () => [
            {
                accountCode: "ACC_X",
                senderEmail: "x@example.com",
                strategyWeight: 100,
                todaySentCount: 1,
                effectiveDailyLimit: 100,
                dailySendLimit: 100,
                enabled: true,
                autoSendPaused: false,
                boundExpertCount: 12
            },
            {
                accountCode: "ACC_Y",
                senderEmail: "y@example.com",
                strategyWeight: 100,
                todaySentCount: 1,
                effectiveDailyLimit: 100,
                dailySendLimit: 100,
                enabled: true,
                autoSendPaused: false
            }
        ];
        await sandbox.loadAccounts();
        const html = sandbox.$("#accountsTable").innerHTML;
        assert.ok(html.includes("<td>12</td>"));
        assert.ok(html.includes("<td>0</td>"));
    });

    it("renders 硬退率过高 warn badge without resume action when only warning", async () => {
        // I-4：旧 API 只给 hardBounceRateHigh 而不带统计字段时的回退分支（原徽标文案逐字保留）。
        const store = new Map();
        const sandbox = {
            $: createElStub(store),
            state: { accounts: [] },
            badge: (v, t) => `<span class="badge ${t || ""}">${v}</span>`,
            api: async () => []
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("escapeHtml"), sandbox);
        vm.runInContext(extractFn("loadAccounts"), sandbox);
        sandbox.api = async () => [
            {
                accountCode: "WARN_ONLY",
                senderEmail: "warn@example.com",
                strategyWeight: 100,
                todaySentCount: 1,
                effectiveDailyLimit: 100,
                dailySendLimit: 100,
                enabled: true,
                autoSendPaused: false,
                hardBounceRateHigh: true,
                boundExpertCount: 12
            }
        ];
        await sandbox.loadAccounts();
        const html = sandbox.$("#accountsTable").innerHTML;
        assert.ok(html.includes("启用"));
        assert.ok(html.includes("badge warn"));
        assert.ok(html.includes("硬退率过高"));
        assert.ok(html.includes('title="近7天硬退率超过5%（已发至少20封）；仅提示，不影响自动发送"'));
        assert.ok(!html.includes("永久退信偏高"));
        assert.ok(!html.includes("NaN"));
        assert.ok(!html.includes('data-action="resume-auto-send"'));
    });

    it("keeps 自动暂停 and resume action orthogonal to 硬退率过高 warning", async () => {
        const store = new Map();
        const sandbox = {
            $: createElStub(store),
            state: { accounts: [] },
            badge: (v, t) => `<span class="badge ${t || ""}">${v}</span>`,
            api: async () => []
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("escapeHtml"), sandbox);
        vm.runInContext(extractFn("loadAccounts"), sandbox);
        sandbox.api = async () => [
            {
                accountCode: "FAULT",
                senderEmail: "fault@example.com",
                strategyWeight: 100,
                todaySentCount: 1,
                effectiveDailyLimit: 100,
                dailySendLimit: 100,
                enabled: true,
                autoSendPaused: true,
                autoSendPausedReason: "SELF_CHECK_FAILED:timeout",
                hardBounceRateHigh: true,
                boundExpertCount: 12
            }
        ];
        await sandbox.loadAccounts();
        const html = sandbox.$("#accountsTable").innerHTML;
        assert.ok(html.includes("自动暂停"));
        assert.ok(html.includes("SELF_CHECK_FAILED:timeout"));
        assert.ok(html.includes("硬退率过高"));
        assert.ok(html.includes('data-action="resume-auto-send"'));
    });
});

function createAccountsSandbox(accounts) {
    const store = new Map();
    const sandbox = {
        $: createElStub(store),
        state: { accounts: [] },
        badge: (v, t) => `<span class="badge ${t || ""}">${v}</span>`,
        api: async () => accounts
    };
    vm.createContext(sandbox);
    vm.runInContext(extractFn("escapeHtml"), sandbox);
    vm.runInContext(extractFn("loadAccounts"), sandbox);
    return sandbox;
}

function bounceAccount(overrides = {}) {
    return Object.assign({
        accountCode: "ACC_B",
        senderEmail: "b@example.com",
        strategyWeight: 100,
        todaySentCount: 1,
        effectiveDailyLimit: 100,
        dailySendLimit: 100,
        enabled: true,
        autoSendPaused: false,
        boundExpertCount: 3
    }, overrides);
}

const staticDir = path.join(__dirname, "..", "..", "main", "resources", "static");
const indexHtmlPath = path.join(staticDir, "index.html");
const indexHtmlSource = fs.readFileSync(indexHtmlPath, "utf-8");

// K-frontend-cache-key-triad：缓存键不写死在测试里——从随包发布的 index.html 派生，
// 键字面量只允许出现在 index.html（taskActivityCenter.test.js 对 src 与 test 两侧做落单扫描）。
const CACHE_KEY = (() => {
    const match = indexHtmlSource.match(/styles\.css\?v=([^"'&<>]+)/);
    if (!match) throw new Error("index.html must register styles.css with a ?v= cache key");
    return match[1];
})();

describe("bounce alert badge (O-1/I-4/S-1)", () => {
    it("renders 永久退信偏高 with counts, percent and the two-window tooltip", async () => {
        const sb = createAccountsSandbox([bounceAccount({
            hardBounceRateHigh: true,
            hardBounceCount: 9,
            sentCount: 160,
            hardBounceRate: 0.05625,
            hardBounceSampleSufficient: true,
            hardBounceWindowDays: 7
        })]);
        await sb.loadAccounts();
        const html = sb.$("#accountsTable").innerHTML;

        assert.ok(html.includes("永久退信偏高 9/160（5.63%）"), html);
        assert.ok(html.includes('<span class="badge warn" title='), "原位复用 span.badge.warn");
        assert.ok(html.includes("近7天退信事件9条 / 近7天成功发信160封＝5.63%"), html);
        assert.ok(html.includes("两者可能不是同一批邮件"), html);
        // S-1 要求 title 走 escapeHtml，故 `>` 在源码里是 `&gt;`，浏览器属性值仍是「阈值>5%，至少20封」。
        assert.ok(html.includes("阈值&gt;5%，至少20封"), html);
        assert.ok(html.includes("仅提示，不影响自动发送"), html);
        assert.ok(!html.includes("style="), "S-1：不得新增 inline style");
        assert.ok(!html.includes("NaN"));
        assert.strictEqual((html.match(/<td[ >]/g) || []).length, 7, "S-1：不新增表格列");
        assert.ok(!html.includes('data-action="resume-auto-send"'));
    });

    it("renders no third segment for an insufficient sample instead of a fake 0/0", async () => {
        const sb = createAccountsSandbox([bounceAccount({
            hardBounceRateHigh: false,
            hardBounceCount: 2,
            sentCount: 19,
            hardBounceRate: null,
            hardBounceSampleSufficient: false,
            hardBounceWindowDays: 7
        })]);
        await sb.loadAccounts();
        const html = sb.$("#accountsTable").innerHTML;

        assert.ok(html.includes("启用"));
        assert.ok(!html.includes("badge warn"), html);
        assert.ok(!html.includes("永久退信偏高"));
        assert.ok(!html.includes("硬退率过高"));
        assert.ok(!html.includes("NaN"));
        assert.ok(!html.includes("null/"));
    });

    it("falls back to the original badge when statistics fields are missing or not numbers", async () => {
        const missing = createAccountsSandbox([bounceAccount({ hardBounceRateHigh: true })]);
        await missing.loadAccounts();
        const missingHtml = missing.$("#accountsTable").innerHTML;
        assert.ok(missingHtml.includes("硬退率过高"), missingHtml);
        assert.ok(missingHtml.includes('title="近7天硬退率超过5%（已发至少20封）；仅提示，不影响自动发送"'));
        assert.ok(!missingHtml.includes("永久退信偏高"));
        assert.ok(!missingHtml.includes("NaN"));

        const notNumeric = createAccountsSandbox([bounceAccount({
            hardBounceRateHigh: true,
            hardBounceCount: "<img src=x>",
            sentCount: 160,
            hardBounceRate: 0.05625,
            hardBounceSampleSufficient: true
        })]);
        await notNumeric.loadAccounts();
        const notNumericHtml = notNumeric.$("#accountsTable").innerHTML;
        assert.ok(notNumericHtml.includes("硬退率过高"), notNumericHtml);
        assert.ok(!notNumericHtml.includes("<img"));
        assert.ok(!notNumericHtml.includes("永久退信偏高"));

        const nullRate = createAccountsSandbox([bounceAccount({
            hardBounceRateHigh: true,
            hardBounceCount: 9,
            sentCount: 160,
            hardBounceRate: null,
            hardBounceSampleSufficient: true
        })]);
        await nullRate.loadAccounts();
        const nullRateHtml = nullRate.$("#accountsTable").innerHTML;
        assert.ok(nullRateHtml.includes("硬退率过高"), nullRateHtml);
        assert.ok(!nullRateHtml.includes("NaN"));
        assert.ok(!nullRateHtml.includes("永久退信偏高"));
    });
});

describe("static resource cache keys (I-5)", () => {
    it("switches every already-versioned resource to the one current key without adding or retiring one", () => {
        const keys = indexHtmlSource.match(/\?v=[^"']+/g) || [];
        assert.strictEqual(keys.length, 11, `expected 11 versioned assets, found ${keys.length}`);
        keys.forEach((key) => assert.strictEqual(key, `?v=${CACHE_KEY}`));
        assert.ok(/^[0-9]{8}-[a-z0-9-]+$/.test(CACHE_KEY), `cache key must be <yyyymmdd>-<slug>, got: ${CACHE_KEY}`);
    });

    it("retires the previous key from index.html", () => {
        assert.ok(!indexHtmlSource.includes("20260929-discovery-schedule"));
    });

    it("leaves the unversioned task-modal-runtime script untouched", () => {
        assert.ok(indexHtmlSource.includes('<script src="task-modal-runtime.js"></script>'));
        assert.ok(!indexHtmlSource.includes("task-modal-runtime.js?v="));
    });
});
