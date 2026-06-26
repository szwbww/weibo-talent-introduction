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

function createContactsSandbox() {
    const store = new Map();
    function el(id) {
        if (!store.has(id)) {
            store.set(id, {
                id,
                value: "",
                disabled: false,
                parentElement: { style: {}, title: "" }
            });
        }
        return store.get(id);
    }

    const sandbox = {
        $: (sel) => el(sel.replace(/^#/, "")),
        state: {
            contactsPage: 0,
            contacts: [],
            contactsTotalHits: 0,
            lastEmailProvidersLevel: null,
            selectedExpertOrcid: null
        },
        indexLevelLabels: {
            RAW: "原始",
            CANDIDATE: "筛选",
            APPLICATION: "有效"
        },
        renderContactListSkeleton: () => {},
        loadEmailProviders: () => {},
        api: async () => ({}),
        URLSearchParams,
        escapeHtml: (v) => String(v == null ? "" : v),
        renderContactPager: () => {},
        operatorStatusLabels: {},
        expertTagLabels: {},
        contactBadgeType: () => "",
        labelStatus: (v) => v || "",
        badge: (v, t) => `<span class="badge ${t || ""}">${v}</span>`,
        staggerListItems: () => {},
        refreshAutoReplySummary: async () => {},
        loadOperatorStatusSyncTooltip: async () => {}
    };

    vm.createContext(sandbox);
    vm.runInContext(extractFn("renderContactListItems"), sandbox);
    vm.runInContext(extractFn("loadContacts"), sandbox);
    sandbox.__store = store;
    return sandbox;
}

describe("loadContacts with contactNeedsAttentionFilter + expertEmailDomainFilter", () => {
    it("filters contacts by emailDomain in needsAttention branch and sets correct totalHits", async () => {
        const sb = createContactsSandbox();
        
        // Mock DOM inputs
        sb.$("#expertIndexLevel").value = "CANDIDATE";
        sb.$("#expertIndexSize").value = "2"; // Page size 2
        sb.$("#contactStatusFilter").value = "";
        sb.$("#contactNeedsAttentionFilter").value = "true";
        sb.$("#expertEmailDomainFilter").value = "gmail.com";
        
        // Mock API response returning raw contacts list
        const rawContacts = [
            { id: 1, orcidId: "0001", expertEmail: "a@gmail.com", expertName: "A", currentIndexLevel: "CANDIDATE", currentStatus: "NEW", operatorStatus: "NOT_CONTACTED", needsManualAttention: true },
            { id: 2, orcidId: "0002", expertEmail: "b@yahoo.com", expertName: "B", currentIndexLevel: "CANDIDATE", currentStatus: "NEW", operatorStatus: "NOT_CONTACTED", needsManualAttention: true },
            { id: 3, orcidId: "0003", expertEmail: "c@gmail.com", expertName: "C", currentIndexLevel: "CANDIDATE", currentStatus: "NEW", operatorStatus: "NOT_CONTACTED", needsManualAttention: true },
            { id: 4, orcidId: "0004", expertEmail: "d@gmail.com", expertName: "D", currentIndexLevel: "CANDIDATE", currentStatus: "NEW", operatorStatus: "NOT_CONTACTED", needsManualAttention: true }
        ];
        
        sb.api = async (url) => {
            assert.ok(url.includes("needsAttention=true"));
            return { contacts: rawContacts, totalCount: 4 };
        };

        // Execution page 0
        sb.state.contactsPage = 0;
        await sb.loadContacts();
        
        // Filtered targets should only be gmail.com (a@gmail.com, c@gmail.com, d@gmail.com -> total 3)
        // With size = 2, page 0 should contain a@gmail.com and c@gmail.com
        assert.strictEqual(sb.state.contactsTotalHits, 3);
        assert.strictEqual(sb.state.contacts.length, 2);
        assert.strictEqual(sb.state.contacts[0].email, "a@gmail.com");
        assert.strictEqual(sb.state.contacts[1].email, "c@gmail.com");
        
        // Execution page 1
        sb.state.contactsPage = 1;
        await sb.loadContacts();
        
        // Page 1 should contain d@gmail.com
        assert.strictEqual(sb.state.contactsTotalHits, 3);
        assert.strictEqual(sb.state.contacts.length, 1);
        assert.strictEqual(sb.state.contacts[0].email, "d@gmail.com");
    });

    it("does not filter when emailDomain is empty", async () => {
        const sb = createContactsSandbox();
        
        sb.$("#expertIndexLevel").value = "CANDIDATE";
        sb.$("#expertIndexSize").value = "2";
        sb.$("#contactStatusFilter").value = "";
        sb.$("#contactNeedsAttentionFilter").value = "true";
        sb.$("#expertEmailDomainFilter").value = "";
        
        const rawContacts = [
            { id: 1, orcidId: "0001", expertEmail: "a@gmail.com", expertName: "A", currentIndexLevel: "CANDIDATE", currentStatus: "NEW", operatorStatus: "NOT_CONTACTED", needsManualAttention: true },
            { id: 2, orcidId: "0002", expertEmail: "b@yahoo.com", expertName: "B", currentIndexLevel: "CANDIDATE", currentStatus: "NEW", operatorStatus: "NOT_CONTACTED", needsManualAttention: true }
        ];
        
        sb.api = async () => ({ contacts: rawContacts, totalCount: 2 });
        
        sb.state.contactsPage = 0;
        await sb.loadContacts();
        
        assert.strictEqual(sb.state.contactsTotalHits, 2);
        assert.strictEqual(sb.state.contacts.length, 2);
    });
});
