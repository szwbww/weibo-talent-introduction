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

function extractExpertTypeTagsBinding() {
    const regex = /\(function\s+initExpertTypeTags\s*\(\s*\)\s*\{[\s\S]*?\n\s*\}\)\(\);/;
    const match = appJsSource.match(regex);
    if (!match) throw new Error("Could not find initExpertTypeTags binding in app.js");
    return match[0];
}

function extractUpdateFilterBadge() {
    const regex = /const\s+updateFilterBadge\s*=\s*\(\s*\)\s*=>\s*\{[\s\S]*?\n\s*\};/;
    const match = appJsSource.match(regex);
    if (!match) throw new Error("Could not find updateFilterBadge in app.js");
    return match[0];
}

function makeClassList() {
    const set = new Set();
    return {
        contains(cls) { return set.has(cls); },
        add(cls) { set.add(cls); },
        remove(cls) { set.delete(cls); },
        toggle(cls, force) {
            const on = force === undefined ? !set.has(cls) : Boolean(force);
            if (on) set.add(cls); else set.delete(cls);
            return on;
        },
        __snapshot() { return Array.from(set); }
    };
}

function makeChip(value) {
    return {
        dataset: { value },
        classList: makeClassList(),
        _listeners: {},
        addEventListener(type, fn) {
            (this._listeners[type] = this._listeners[type] || []).push(fn);
        }
    };
}

function makeTagSelect(chips) {
    return {
        id: "expertTypeTagSelect",
        querySelectorAll(sel) { return sel === ".tag-chip" ? chips : []; },
        addEventListener() {}
    };
}

function clickChip(chip) {
    (chip._listeners.click || []).forEach((fn) => fn());
}

const TYPE_VALUES = ["PRODUCTION_RND", "ACADEMIC_RND", "HYBRID_RND", "SERVICE_ONLY", "OUT_OF_SCOPE", "UNKNOWN", "UNCLASSIFIED"];

function createChipSandbox() {
    const chips = TYPE_VALUES.map(makeChip);
    const tagSelect = makeTagSelect(chips);
    const store = new Map([["expertTypeTagSelect", tagSelect]]);
    const sandbox = {
        $: (sel) => {
            const id = sel.replace(/^#/, "");
            if (!store.has(id)) {
                store.set(id, {
                    id,
                    value: "",
                    innerHTML: "",
                    textContent: "",
                    hidden: true,
                    disabled: false,
                    classList: makeClassList(),
                    options: [],
                    addEventListener() {}
                });
            }
            return store.get(id);
        },
        reloadContactsFromStart: () => { sandbox.__reloads = (sandbox.__reloads || 0) + 1; }
    };
    sandbox.__chips = chips;
    sandbox.__store = store;
    vm.createContext(sandbox);
    vm.runInContext(extractFn("expertTypeActiveValues"), sandbox);
    vm.runInContext(extractExpertTypeTagsBinding(), sandbox);
    return sandbox;
}

function expertQueryParams(url) {
    return new URLSearchParams(url.split("?")[1] || "");
}

describe("expert type filter (01-expert-list-type-filter)", () => {
    it("chip click toggles .active, expertTypeActiveValues reflects it, and reload is requested", () => {
        const sb = createChipSandbox();
        assert.deepEqual(sb.expertTypeActiveValues(), []);

        clickChip(sb.__chips[0]); // PRODUCTION_RND
        assert.deepEqual(sb.expertTypeActiveValues(), ["PRODUCTION_RND"]);
        assert.equal(sb.__reloads, 1, "chip click must trigger reloadContactsFromStart");

        clickChip(sb.__chips[1]); // ACADEMIC_RND
        assert.deepEqual(sb.expertTypeActiveValues(), ["PRODUCTION_RND", "ACADEMIC_RND"]);
        assert.equal(sb.__reloads, 2);

        clickChip(sb.__chips[0]); // toggle back off
        assert.deepEqual(sb.expertTypeActiveValues(), ["ACADEMIC_RND"]);
    });

    it("loadContacts omits expertType param when no chip is selected", async () => {
        const sb = createLoadContactsSandbox();
        const urls = [];
        sb.api = async (url) => {
            urls.push(url);
            if (url.includes("/api/experts?") && !url.includes("/api/experts/")) return { experts: [], totalHits: 0 };
            return [];
        };

        await sb.loadContacts();

        const listUrl = urls.find((u) => u.includes("/api/experts?") && !u.includes("/api/experts/"));
        assert.ok(listUrl, "expected a list query to /api/experts");
        const params = expertQueryParams(listUrl);
        assert.equal(params.has("expertType"), false, "no expertType key when nothing selected");
        assert.equal(params.toString().includes("expertType"), false);
    });

    it("loadContacts appends one expertType param per active chip (multi-value)", async () => {
        const sb = createLoadContactsSandbox();
        const urls = [];
        sb.api = async (url) => {
            urls.push(url);
            if (url.includes("/api/experts?") && !url.includes("/api/experts/")) return { experts: [], totalHits: 0 };
            return [];
        };

        clickChip(sb.__chips[1]); // ACADEMIC_RND
        clickChip(sb.__chips[4]); // OUT_OF_SCOPE
        await sb.loadContacts();

        const listUrl = urls.find((u) => u.includes("/api/experts?") && !u.includes("/api/experts/"));
        assert.ok(listUrl, "expected a list query to /api/experts");
        const params = expertQueryParams(listUrl);
        const values = params.getAll("expertType");
        assert.equal(values.length, 2, "two active chips must yield two repeated expertType params");
        assert.deepEqual(values.sort(), ["ACADEMIC_RND", "OUT_OF_SCOPE"].sort());
    });

    it("updateFilterBadge counts active expert type chips and hides at zero", () => {
        const chips = TYPE_VALUES.map(makeChip);
        const tagSelect = makeTagSelect(chips);
        const store = new Map([
            ["expertTypeTagSelect", tagSelect],
            ["filterActiveCount", { hidden: false, textContent: "" }]
        ]);
        const sandbox = {
            $: (sel) => {
                const id = sel.replace(/^#/, "");
                if (!store.has(id)) {
                    store.set(id, {
                        id,
                        value: "",
                        selectedOptions: undefined
                    });
                }
                return store.get(id);
            }
        };
        sandbox.$("#expertIndexLevel").value = "CANDIDATE";
        sandbox.$("#expertIndexSize").value = "50";
        vm.createContext(sandbox);
        vm.runInContext(extractFn("expertTypeActiveValues"), sandbox);
        vm.runInContext(extractUpdateFilterBadge(), sandbox);

        sb_call(sandbox, "updateFilterBadge");
        const countEl = sandbox.$("#filterActiveCount");
        assert.equal(countEl.hidden, true, "badge hidden with no active filters");
        assert.equal(countEl.textContent, 0);

        chips[2].classList.add("active"); // HYBRID_RND
        sb_call(sandbox, "updateFilterBadge");
        assert.equal(countEl.hidden, false, "badge visible when a type chip is active");
        assert.equal(countEl.textContent, 1);

        chips[5].classList.add("active"); // UNKNOWN —— 同一筛选组，徽章仍计 1
        sb_call(sandbox, "updateFilterBadge");
        assert.equal(countEl.textContent, 1);

        chips[2].classList.remove("active");
        chips[5].classList.remove("active");
        sb_call(sandbox, "updateFilterBadge");
        assert.equal(countEl.hidden, true, "badge hidden again when all type chips are off");
        assert.equal(countEl.textContent, 0);
    });
});

/* 照 loadContactsFilter.test.js 的 DOM stub 范式：把 renderContactListItems 与 loadContacts
   抽入 vm 沙箱，另注入 expertTypeActiveValues（loadContacts 的 I1-5 取值依赖）。 */
function createLoadContactsSandbox() {
    const chips = TYPE_VALUES.map(makeChip);
    const tagSelect = makeTagSelect(chips);
    const store = new Map([["expertTypeTagSelect", tagSelect]]);

    function el(id) {
        if (!store.has(id)) {
            store.set(id, {
                id,
                value: "",
                disabled: false,
                innerHTML: "",
                hidden: false,
                parentElement: { style: {}, title: "" },
                addEventListener() {},
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
        state: {
            contactsPage: 0,
            contacts: [],
            contactsTotalHits: 0,
            lastEmailProvidersLevel: null,
            selectedExpertOrcid: null
        },
        indexLevelLabels: { RAW: "原始", CANDIDATE: "筛选", APPLICATION: "有效" },
        renderContactListSkeleton: () => {},
        loadEmailProviders: () => {},
        loadRegions: () => {},
        loadExpertTagOptions: () => {},
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
        loadOperatorStatusSyncTooltip: async () => {},
        showStatus: () => {},
        reloadContactsFromStart: () => { sandbox.__reloads = (sandbox.__reloads || 0) + 1; }
    };
    sandbox.__chips = chips;
    sandbox.__store = store;

    vm.createContext(sandbox);
    vm.runInContext(extractFn("expertTypeActiveValues"), sandbox);
    vm.runInContext(extractExpertTypeTagsBinding(), sandbox);
    vm.runInContext(extractFn("renderContactListItems"), sandbox);
    vm.runInContext(extractFn("loadContacts"), sandbox);
    return sandbox;
}

function sb_call(sandbox, name) {
    vm.runInContext(name + "();", sandbox);
}
