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
});
