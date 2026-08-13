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

function createContactsSandbox(options = {}) {
    const store = new Map();
    function el(id) {
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
    }

    const sandbox = {
        $: (sel) => el(sel.replace(/^#/, "")),
        document: {
            createElement(tag) {
                return {
                    tagName: tag.toUpperCase(),
                    value: "",
                    textContent: "",
                    outerHTML: ""
                };
            }
        },
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
        loadEmailProviders: options.includeEmailProviders ? undefined : () => {},
        loadRegions: options.includeRegions ? undefined : () => {},
        loadExpertTagOptions: options.includeExpertTagOptions ? undefined : () => {},
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
    if (options.includeEmailProviders) {
        vm.runInContext(extractFn("loadEmailProviders"), sandbox);
    }
    if (options.includeRegions) {
        vm.runInContext(extractFn("loadRegions"), sandbox);
    }
    if (options.includeExpertTagOptions) {
        vm.runInContext(extractFn("loadExpertTagOptions"), sandbox);
    }
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

describe("loadEmailProviders batch config dropdown full results (I-5)", () => {
    function createEmailProvidersSandbox() {
        const store = new Map();
        function el(id) {
            if (!store.has(id)) {
                store.set(id, {
                    id,
                    value: "",
                    innerHTML: "",
                    appendChild(child) {
                        this.innerHTML += child.outerHTML || String(child);
                    }
                });
            }
            return store.get(id);
        }

        const sandbox = {
            $: (sel) => el(sel.replace(/^#/, "")),
            document: {
                createElement(tag) {
                    return { tagName: tag.toUpperCase(), value: "", textContent: "", outerHTML: "" };
                }
            },
            api: async () => [],
            URLSearchParams
        };

        vm.createContext(sandbox);
        vm.runInContext(extractFn("loadEmailProviders"), sandbox);
        return sandbox;
    }

    it("uses level-only URL for batchSendEmailDomain when refreshConfigDropdown is true", async () => {
        const sb = createEmailProvidersSandbox();
        const urls = [];
        sb.api = async (url) => {
            urls.push(url);
            return [{ domain: "gmail.com", count: 10 }, { domain: "outlook.com", count: 5 }];
        };

        await sb.loadEmailProviders("APPLICATION", {
            filters: { operatorStatus: "NOT_CONTACTED", region: "Europe" },
            refreshConfigDropdown: true
        });

        assert.strictEqual(urls.length, 2);
        const fullUrl = urls.find((u) => u.endsWith("/api/experts/email-providers?level=APPLICATION"));
        assert.ok(fullUrl, "expected full-level-only email-providers request");
        const filterUrl = urls.find((u) => u !== fullUrl);
        assert.ok(filterUrl.includes("operatorStatus=NOT_CONTACTED"));
        assert.ok(filterUrl.includes("region=Europe"));
        assert.ok(!filterUrl.includes("emailDomain"));
    });

    it("does not request full providers when refreshConfigDropdown is false", async () => {
        const sb = createEmailProvidersSandbox();
        const urls = [];
        sb.api = async (url) => {
            urls.push(url);
            return [{ domain: "gmail.com", count: 3 }];
        };

        await sb.loadEmailProviders("CANDIDATE", {
            filters: { region: "Asia (Other)" },
            refreshConfigDropdown: false
        });

        assert.strictEqual(urls.length, 1);
        assert.ok(urls[0].includes("region=Asia"));
        assert.ok(!urls[0].includes("emailDomain"));
    });
});

describe("loadContacts level change keeps batchSendEmailDomain full (I-5)", () => {
    it("requests filtered and full email-providers when level changes with active filters", async () => {
        const sb = createContactsSandbox({ includeEmailProviders: true, includeRegions: true, includeExpertTagOptions: true });
        const urls = [];

        sb.$("#expertIndexLevel").value = "APPLICATION";
        sb.$("#expertIndexSize").value = "50";
        sb.$("#contactStatusFilter").value = "NOT_CONTACTED";
        sb.$("#contactNeedsAttentionFilter").value = "";
        sb.$("#expertRegionFilter").value = "Europe";
        sb.$("#expertTagFilter").value = "";
        sb.$("#expertEmailDomainFilter").value = "";
        sb.$("#batchSendEmailDomain").value = "gmail.com";
        sb.state.lastEmailProvidersLevel = "CANDIDATE";

        sb.api = async (url) => {
            urls.push(url);
            if (url.includes("/api/experts/email-providers")) {
                return [{ domain: "gmail.com", count: 10 }];
            }
            if (url.includes("/api/experts/regions")) {
                return [{ region: "Europe", count: 10 }];
            }
            if (url.includes("/api/experts/tags/aggregation")) {
                return [{ tag: "verified", count: 2 }];
            }
            return { experts: [], totalHits: 0 };
        };

        await sb.loadContacts();

        const providerUrls = urls.filter((u) => u.includes("/api/experts/email-providers"));
        // After material-reminder unified batch-send, config dropdown providers are
        // loaded per sendType in the modal — loadContacts no longer refreshes them.
        assert.strictEqual(providerUrls.length, 1);
        const filteredProviderUrl = providerUrls[0];
        assert.ok(filteredProviderUrl.includes("operatorStatus=NOT_CONTACTED"));
        assert.ok(filteredProviderUrl.includes("region=Europe"));
        assert.ok(!filteredProviderUrl.includes("emailDomain"));
    });
});

describe("loadRegions localizes option labels but keeps English values (child 05)", () => {
    function createRegionLoadSandbox() {
        const options = [];
        const filterDropdown = {
            value: "",
            innerHTML: "",
            appendChild(opt) { options.push(opt); }
        };
        const sandbox = {
            $: (sel) => sel === "#expertRegionFilter" ? filterDropdown : null,
            document: {
                createElement(tag) {
                    return { tagName: tag.toUpperCase(), value: "", textContent: "" };
                }
            },
            api: async () => [{ region: "Europe", count: 10 }],
            URLSearchParams,
            console: { error() {} }
        };
        vm.createContext(sandbox);
        const regionLabelsSrc = appJsSource.match(/var REGION_LABELS = \{[\s\S]*?\};/);
        assert.ok(regionLabelsSrc, "REGION_LABELS must be defined");
        vm.runInContext(regionLabelsSrc[0], sandbox);
        vm.runInContext(extractFn("regionLabel"), sandbox);
        vm.runInContext(extractFn("loadRegions"), sandbox);
        sandbox.__options = options;
        return sandbox;
    }

    it("loadRegions renders Chinese labels while option values stay English (I-1/S-1)", async () => {
        const sb = createRegionLoadSandbox();
        await sb.loadRegions("CANDIDATE", { filters: {} });
        assert.strictEqual(sb.__options.length, 1);
        assert.strictEqual(sb.__options[0].value, "Europe", "option value must be the English constant");
        assert.match(sb.__options[0].textContent, /^欧洲/, "option text must start with the Chinese label");
    });

    it("selecting the Chinese-labeled option sends the English region constant to /api/experts (I-1)", async () => {
        const sb = createContactsSandbox();
        const urls = [];
        sb.$("#expertIndexLevel").value = "CANDIDATE";
        sb.$("#expertIndexSize").value = "50";
        sb.$("#contactStatusFilter").value = "";
        sb.$("#contactNeedsAttentionFilter").value = "";
        sb.$("#expertRegionFilter").value = "Europe"; // UI shows 欧洲, select value stays English
        sb.$("#expertTagFilter").value = "";
        sb.$("#expertEmailDomainFilter").value = "";
        sb.api = async (url) => {
            urls.push(url);
            if (url.includes("/api/experts/regions")) return [{ region: "Europe", count: 10 }];
            return { experts: [], totalHits: 0 };
        };
        await sb.loadContacts();
        const expertsUrl = urls.find((u) => u.includes("/api/experts?"));
        assert.ok(expertsUrl, "expected an /api/experts call");
        assert.strictEqual(
            new URLSearchParams(expertsUrl.split("?")[1]).get("region"),
            "Europe",
            "query param must be the English constant, not the Chinese label"
        );
    });
});
