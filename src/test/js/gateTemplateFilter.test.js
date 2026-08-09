const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const appJsPath = path.join(__dirname, "..", "..", "main", "resources", "static", "app.js");
const appJsSource = fs.readFileSync(appJsPath, "utf-8");

function extractGateFn() {
    const regex = /function\s+initExpertGateFilter\s*\([^)]*\)\s*\{[\s\S]*?\n\}/;
    const match = appJsSource.match(regex);
    if (!match) throw new Error("Could not find initExpertGateFilter function in app.js");
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

function createGateSandbox() {
    const store = new Map();
    const chips = {};
    const gateFieldValues = ["employment", "degree", "institution", "researchFields", "recentWorkTitles", "patentTitles"];

    function makeEl(id) {
        if (!store.has(id)) {
            const el = {
                id,
                value: "",
                innerHTML: "",
                textContent: "",
                hidden: true,
                disabled: false,
                classList: makeClassList(),
                options: [],
                children: [],
                addEventListener(type, fn) {
                    el._listeners = el._listeners || {};
                    (el._listeners[type] = el._listeners[type] || []).push(fn);
                },
                appendChild(child) { el.children.push(child); }
            };
            store.set(id, el);
        }
        return store.get(id);
    }

    gateFieldValues.forEach((v) => {
        chips[v] = {
            dataset: { value: v },
            classList: makeClassList()
        };
    });

    const hasFieldTagSelect = makeEl("hasFieldTagSelect");
    hasFieldTagSelect.querySelectorAll = () => gateFieldValues.map((v) => chips[v]);

    const sandbox = {
        $: (sel) => makeEl(sel.replace(/^#/, "")),
        document: {
            createElement: () => makeEl("__option__")
        },
        URLSearchParams,
        api: async () => ({ totalHits: 0 }),
        showStatus: (message, type) => {
            sandbox.__status = sandbox.__status || [];
            sandbox.__status.push({ message, type });
        },
        reloadContactsFromStart: () => { sandbox.__reloads = (sandbox.__reloads || 0) + 1; },
        populateExpertGateTemplateFilter: () => {},
        console: { log: () => {}, warn: () => {}, error: () => {} }
    };
    sandbox.__store = store;
    sandbox.__chips = chips;

    vm.createContext(sandbox);
    vm.runInContext(extractGateFn(), sandbox);
    vm.runInContext("initExpertGateFilter(reloadContactsFromStart);", sandbox);

    sandbox.__triggerChange = async () => {
        const select = sandbox.$("#expertGateTemplateFilter");
        const listeners = (select._listeners && select._listeners.change) || [];
        for (const fn of listeners) await fn();
    };
    return sandbox;
}

function expertQueryParams(url) {
    return new URLSearchParams(url.split("?")[1] || "");
}

describe("expert gate template filter", () => {
    it("app.js gate code has no hardcoded required-field fallback and no 可发送 wording", () => {
        const gateSource = extractGateFn();
        assert.doesNotMatch(gateSource, /recentWorkTitles/, "gate must not hardcode es fields");
        assert.doesNotMatch(gateSource, /researchFields/, "gate must not hardcode es fields");
        assert.doesNotMatch(gateSource, /可发送|可发/, "count text must not imply exact sendable count (I-9)");
    });

    it("gate-fields 500 applies no filter: no chip selection, no hasField param, summary hidden, one notice", async () => {
        const sb = createGateSandbox();
        const requested = [];
        sb.api = async (url) => {
            requested.push(url);
            if (url.includes("/gate-fields")) {
                throw new Error("500 Internal Server Error");
            }
            return { totalHits: 0 };
        };

        sb.$("#expertGateTemplateFilter").value = "5";
        await sb.__triggerChange();

        Object.values(sb.__chips).forEach((chip) => {
            assert.equal(chip.classList.contains("active"), false, "no chip may be selected on failure");
        });
        assert.ok(!requested.some((u) => u.includes("/api/experts")), "no /api/experts query on gate-fields failure");
        assert.equal(sb.$("#expertGateSummary").hidden, true, "summary stays hidden on failure");
        assert.equal(sb.__status.length, 1, "exactly one failure notice");
        assert.equal(sb.__status[0].type, "error");
        assert.match(sb.__status[0].message, /按模板门禁筛选失败/);
    });

    it("selecting a template applies esFields chips and shows 符合 N / M from totalHits", async () => {
        const sb = createGateSandbox();
        const expertUrls = [];
        sb.api = async (url) => {
            if (url.includes("/gate-fields")) {
                return {
                    templateId: 5,
                    requiredKeys: ["recentWorkTitle", "primaryResearchField"],
                    esFields: ["recentWorkTitles", "researchFields"]
                };
            }
            if (url.includes("/api/experts")) {
                expertUrls.push(url);
                const q = expertQueryParams(url);
                return { totalHits: q.getAll("hasField").length > 0 ? 30 : 100 };
            }
            return { totalHits: 0 };
        };

        sb.$("#expertGateTemplateFilter").value = "5";
        await sb.__triggerChange();

        assert.equal(sb.__chips.recentWorkTitles.classList.contains("active"), true);
        assert.equal(sb.__chips.researchFields.classList.contains("active"), true);
        assert.equal(sb.__chips.employment.classList.contains("active"), false);
        assert.equal(sb.$("#expertGateSummary").hidden, false, "summary visible after success");
        assert.equal(sb.$("#expertGateSummary").classList.contains("has-value"), true);
        assert.equal(sb.$("#expertGateMatchCount").textContent, "30");
        assert.equal(sb.$("#expertGateTotalCount").textContent, "100");

        const filtered = expertUrls.find((u) => expertQueryParams(u).getAll("hasField").length > 0);
        const total = expertUrls.find((u) => expertQueryParams(u).getAll("hasField").length === 0);
        assert.ok(filtered, "filtered /api/experts query present");
        assert.ok(total, "unfiltered /api/experts query present");
        assert.deepEqual(
            expertQueryParams(filtered).getAll("hasField").sort(),
            ["recentWorkTitles", "researchFields"].sort()
        );
    });

    it("switching back to 不限 restores the manual chip snapshot and hides the summary", async () => {
        const sb = createGateSandbox();
        sb.__chips.institution.classList.add("active"); // manual selection
        sb.api = async (url) => {
            if (url.includes("/gate-fields")) {
                return {
                    templateId: 5,
                    requiredKeys: ["recentWorkTitle", "primaryResearchField"],
                    esFields: ["recentWorkTitles", "researchFields"]
                };
            }
            if (url.includes("/api/experts")) {
                const q = expertQueryParams(url);
                return { totalHits: q.getAll("hasField").length > 0 ? 30 : 100 };
            }
            return { totalHits: 0 };
        };

        // apply the gate: manual chip is suspended, gate chips take over
        sb.$("#expertGateTemplateFilter").value = "5";
        await sb.__triggerChange();
        assert.equal(sb.__chips.institution.classList.contains("active"), false, "manual chip suspended while gate active");
        assert.equal(sb.__chips.recentWorkTitles.classList.contains("active"), true);
        assert.equal(sb.__chips.researchFields.classList.contains("active"), true);

        // back to 不限: gate chips cleared, manual snapshot restored, summary hidden
        sb.$("#expertGateTemplateFilter").value = "";
        await sb.__triggerChange();
        assert.equal(sb.__chips.recentWorkTitles.classList.contains("active"), false);
        assert.equal(sb.__chips.researchFields.classList.contains("active"), false);
        assert.equal(sb.__chips.institution.classList.contains("active"), true, "manual snapshot restored");
        assert.equal(sb.$("#expertGateSummary").hidden, true, "summary hidden on 不限");
    });
});
