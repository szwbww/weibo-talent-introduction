const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const appJsPath = path.join(__dirname, "..", "..", "main", "resources", "static", "app.js");
const indexHtmlPath = path.join(__dirname, "..", "..", "main", "resources", "static", "index.html");
const appJsSource = fs.readFileSync(appJsPath, "utf-8");
const indexHtml = fs.readFileSync(indexHtmlPath, "utf-8");

// Brace-balanced extraction: walks the real function body, so it also works for
// functions that contain nested blocks / template literals with inner braces
// (the old regex stopped at the first standalone "}" line).
function extractFn(name) {
    const marker = "function " + name + "(";
    const start = appJsSource.indexOf(marker);
    if (start < 0) throw new Error("Could not find " + marker + " in app.js");
    const openIdx = appJsSource.indexOf("{", start);
    let depth = 0;
    let end = openIdx;
    for (; end < appJsSource.length; end++) {
        const ch = appJsSource[end];
        if (ch === "{") depth++;
        else if (ch === "}") {
            depth--;
            if (depth === 0) break;
        }
    }
    return appJsSource.substring(start, end + 1);
}

function createLabelSandbox() {
    const sandbox = {
        state: { qaCoverageKeys: [] },
        escapeHtml: (value) => String(value ?? "")
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#039;"),
        badge: (label, type) => `<span class="badge ${type || "primary"}">${label}</span>`
    };
    vm.createContext(sandbox);
    vm.runInContext(extractFn("renderQaCoverageKeyLabels"), sandbox);
    return sandbox;
}

describe("qa coverage key labels (legacy helper)", () => {
    it("renders warn badge for empty coverage keys", () => {
        const sandbox = createLabelSandbox();
        const result = sandbox.renderQaCoverageKeyLabels([]);
        assert.match(result, /未配置 AI 覆盖能力/);
        assert.match(result, /class="badge warn"/);
    });

    it("renders up to three labels joined by dot separator", () => {
        const sandbox = createLabelSandbox();
        sandbox.state.qaCoverageKeys = [
            { key: "company.legal_name", label: "公司法定名称", group: "公司信息" },
            { key: "company.registered_location", label: "公司注册地点", group: "公司信息" },
            { key: "finance.government_funding", label: "政府资金", group: "资金" }
        ];
        const result = sandbox.renderQaCoverageKeyLabels([
            "company.legal_name", "company.registered_location", "finance.government_funding"
        ]);
        assert.match(result, /公司法定名称/);
        assert.match(result, /公司注册地点/);
        assert.match(result, /政府资金/);
        assert.doesNotMatch(result, /另/);
    });

    it("shows overflow count for more than three keys", () => {
        const sandbox = createLabelSandbox();
        sandbox.state.qaCoverageKeys = [
            { key: "company.legal_name", label: "公司法定名称", group: "公司信息" },
            { key: "company.registered_location", label: "公司注册地点", group: "公司信息" },
            { key: "finance.government_funding", label: "政府资金", group: "资金" },
            { key: "finance.enterprise_compensation", label: "企业报酬", group: "资金" }
        ];
        const result = sandbox.renderQaCoverageKeyLabels([
            "company.legal_name", "company.registered_location",
            "finance.government_funding", "finance.enterprise_compensation"
        ]);
        assert.match(result, /公司法定名称/);
        assert.match(result, /另 1 项/);
    });

    it("falls back to raw key when label not found", () => {
        const sandbox = createLabelSandbox();
        sandbox.state.qaCoverageKeys = [];
        const result = sandbox.renderQaCoverageKeyLabels(["unknown.key"]);
        assert.match(result, /unknown\.key/);
    });
});

// ── p4 fixture data (mirrors the real catalog shape from /api/qa/coverage-keys) ──
const CATALOG_ENTRIES = [
    { key: "programme.purpose", label: "项目目的", group: "项目概况", controlled: false },
    { key: "application.steps", label: "申请步骤", group: "申请流程", controlled: false },
    { key: "fees.policy", label: "费用政策", group: "费用与保密", controlled: true },
    { key: "confidentiality.materials", label: "材料保密", group: "费用与保密", controlled: true },
    { key: "contract.party", label: "签约主体", group: "合同与IP", controlled: true },
    { key: "contract.terms", label: "合同条款", group: "合同与IP", controlled: true },
    { key: "ip.arrangements", label: "知识产权安排", group: "合同与IP", controlled: true }
];
const GROUPS = [
    { id: "G1", name: "材料保密", keys: ["confidentiality.materials"], canonicalBody: "Your materials are kept strictly confidential." },
    { id: "G2", name: "费用政策", keys: ["fees.policy"], canonicalBody: "We never charge any fees throughout the entire process." },
    { id: "G3", name: "合同安排", keys: ["contract.party", "contract.terms"], canonicalBody: "After selection, you will sign a labor contract directly with the matched enterprise." },
    { id: "G4", name: "签约前 IP 边界", keys: ["ip.arrangements"], canonicalBody: "Until a contract is signed, nothing you share with us transfers any rights." }
];

const ESCAPE_HTML = (value) => String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");

describe("qa coverage key editor (p4)", () => {
    it("index.html contains the coverage key editor elements", () => {
        assert.match(indexHtml, /id="qaCoverageKeyOptions"/);
        assert.match(indexHtml, /id="qaCoverageKeyChips"/);
        assert.match(indexHtml, /id="qaCoverageGate"/);
    });

    it("qa rules table uses fact title column not coverage column", () => {
        const rulesTableIdx = indexHtml.indexOf('id="qaRulesTable"');
        assert.ok(rulesTableIdx > 0);
        const tableStart = indexHtml.lastIndexOf("<table>", rulesTableIdx);
        const theadEnd = indexHtml.indexOf("</thead>", tableStart);
        const thead = indexHtml.substring(tableStart, theadEnd);
        assert.match(thead, /事实标题/);
        assert.doesNotMatch(thead, /AI 覆盖能力/);
    });

    it("loadQa fetches coverage-keys endpoint", () => {
        const loadFn = extractFn("loadQa");
        assert.match(loadFn, /\/api\/qa\/coverage-keys/);
    });

    it("saveQaRule sends coverageKeys collected from the DOM (I-3)", () => {
        const saveFn = extractFn("saveQaRule");
        assert.match(saveFn, /answerBody/);
        assert.match(saveFn, /coverageKeys:\s*collectQaCoverageKeys\(\)/);
    });

    it("fillQaRuleForm renders coverage options", () => {
        const fillFn = extractFn("fillQaRuleForm");
        assert.match(fillFn, /renderQaCoverageKeyOptions/);
        assert.match(fillFn, /answerBody/);
    });

    it("save flow keeps the backend as the authority: no bypass flag, error toast stays (I-2)", () => {
        assert.doesNotMatch(appJsSource, /skipCoverageGate|forceSave/);
        assert.match(appJsSource, /saveQaRule\(event\)\.catch/);
    });

    it("table colspan matches nine-column fact-card layout", () => {
        assert.match(appJsSource, /colspan="9"/);
    });

    it("no coverage key constants hardcoded in app.js", () => {
        assert.doesNotMatch(appJsSource, /"company\.legal_name"/);
        assert.doesNotMatch(appJsSource, /"programme\.purpose"/);
    });

    it("collectQaCoverageKeys returns checked keys in DOM order (I-7)", () => {
        const inputs = [
            { checked: true, getAttribute: () => "fees.policy" },
            { checked: false, getAttribute: () => "application.steps" },
            { checked: true, getAttribute: () => "programme.purpose" }
        ];
        const sandbox = {
            $: (sel) => sel === "#qaCoverageKeyOptions" ? { querySelectorAll: () => inputs } : null
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("collectQaCoverageKeys"), sandbox);
        assert.deepStrictEqual(Array.from(sandbox.collectQaCoverageKeys()), ["fees.policy", "programme.purpose"]);
    });

    it("collectQaCoverageKeys returns [] when nothing is checked (I-3)", () => {
        const sandbox = {
            $: () => ({ querySelectorAll: () => [] })
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("collectQaCoverageKeys"), sandbox);
        assert.deepStrictEqual(Array.from(sandbox.collectQaCoverageKeys()), []);
    });

    it("renderQaCoverageKeyOptions renders every catalog key as a resident checkbox (I-7)", () => {
        const keys = Array.from({ length: 31 }, (_, i) => ({
            key: "key." + i,
            label: "能力" + i,
            group: i % 2 ? "组A" : "组B",
            controlled: false
        }));
        function makeEl() {
            return { className: "", textContent: "", title: "", innerHTML: "", children: [], appendChild(child) { this.children.push(child); } };
        }
        const container = makeEl();
        const sandbox = {
            state: { qaCoverageKeys: keys },
            escapeHtml: ESCAPE_HTML,
            document: { createElement: makeEl },
            $: (sel) => sel === "#qaCoverageKeyOptions" ? container : (sel === "#qaCoverageKeyWarning" ? { style: {} } : null)
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("renderQaCoverageKeyOptions"), sandbox);
        sandbox.renderQaCoverageKeyOptions(["key.0", "key.1"]);
        const items = container.children
            .filter((el) => el.className === "qa-cov-grid")
            .flatMap((grid) => grid.children)
            .filter((label) => label.className.startsWith("qa-cov-item"));
        assert.strictEqual(items.length, 31, "every catalog entry must get a .qa-cov-item");
        const withInput = items.filter((label) =>
            /class="qa-cov-input"/.test(label.innerHTML) && /data-coverage-key="[^"]+"/.test(label.innerHTML));
        assert.strictEqual(withInput.length, 31, "every item must carry .qa-cov-input[data-coverage-key]");
        const checked = items.filter((label) => /checked>/.test(label.innerHTML));
        assert.strictEqual(checked.length, 2, "selected keys render checked");
    });

    function gateSandbox({ selected, body }) {
        const inputs = CATALOG_ENTRIES.map((entry) => ({
            checked: selected.includes(entry.key),
            getAttribute: () => entry.key
        }));
        const sandbox = {
            state: { qaCoverageKeys: CATALOG_ENTRIES, qaControlledGroups: GROUPS, qaCoverageAuthorities: {}, selectedRuleId: null },
            $: (sel) => {
                if (sel === "#qaCoverageKeyOptions") return { querySelectorAll: () => inputs };
                if (sel === "#qaRuleAnswerBody") return { value: body };
                return null;
            }
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("collectQaCoverageKeys"), sandbox);
        vm.runInContext(extractFn("evaluateQaCoverageGate"), sandbox);
        return sandbox;
    }

    it("evaluateQaCoverageGate returns none when no controlled key is selected", () => {
        const sandbox = gateSandbox({ selected: ["application.steps"], body: "anything" });
        const result = sandbox.evaluateQaCoverageGate();
        assert.strictEqual(result.kind, "none");
        assert.strictEqual(result.level, "ok");
    });

    it("evaluateQaCoverageGate returns aligned for an exact controlled group with matching body", () => {
        const sandbox = gateSandbox({ selected: ["fees.policy"], body: "We never charge any fees throughout the entire process." });
        const result = sandbox.evaluateQaCoverageGate();
        assert.strictEqual(result.kind, "aligned");
        assert.strictEqual(result.level, "ok");
        assert.strictEqual(result.group.id, "G2");
    });

    it("evaluateQaCoverageGate returns drift for an exact controlled group with drifted body", () => {
        const sandbox = gateSandbox({ selected: ["fees.policy"], body: "We never charge participants at any stage." });
        const result = sandbox.evaluateQaCoverageGate();
        assert.strictEqual(result.kind, "drift");
        assert.strictEqual(result.level, "warn");
        assert.strictEqual(result.group.id, "G2");
    });

    it("evaluateQaCoverageGate returns partial for controlled keys that do not form a group (I-1)", () => {
        const sandbox = gateSandbox({ selected: ["fees.policy", "confidentiality.materials"], body: "anything" });
        const result = sandbox.evaluateQaCoverageGate();
        assert.strictEqual(result.kind, "partial");
        assert.strictEqual(result.level, "warn");
    });

    function renderGateSandbox({ selected, body, authorities = {} }) {
        const inputs = CATALOG_ENTRIES.map((entry) => ({
            checked: selected.includes(entry.key),
            getAttribute: () => entry.key
        }));
        const gateEl = { className: "", innerHTML: "" };
        const saveBtn = { disabled: false };
        const saveBlock = { hidden: true, textContent: "" };
        const sandbox = {
            state: { qaCoverageKeys: CATALOG_ENTRIES, qaControlledGroups: GROUPS, qaCoverageAuthorities: authorities, selectedRuleId: null },
            qaGateUiState: { canonOpen: false, revokeGroupId: null },
            escapeHtml: ESCAPE_HTML,
            $: (sel) => {
                if (sel === "#qaCoverageGate") return gateEl;
                if (sel === "#qaCoverageKeyOptions") return { querySelectorAll: () => inputs };
                if (sel === "#qaRuleAnswerBody") return { value: body };
                if (sel === "#saveBtn") return saveBtn;
                if (sel === "#qaCoverageSaveBlock") return saveBlock;
                return null;
            },
            document: { querySelector: () => null }
        };
        vm.createContext(sandbox);
        [
            "collectQaCoverageKeys",
            "evaluateQaCoverageGate",
            "diffWordsForGate",
            "renderQaGateRevokeCard",
            "renderQaGateBodyBadge",
            "renderQaCoverageGate"
        ].forEach((fn) => vm.runInContext(extractFn(fn), sandbox));
        sandbox._gateEl = gateEl;
        sandbox._saveBtn = saveBtn;
        sandbox._saveBlock = saveBlock;
        return sandbox;
    }

    it("partial state does not disable the save button (I-1 alignment with backend)", () => {
        const sandbox = renderGateSandbox({ selected: ["fees.policy", "confidentiality.materials"], body: "anything" });
        sandbox.renderQaCoverageGate();
        assert.strictEqual(sandbox._saveBtn.disabled, false, "partial must keep save enabled");
        assert.strictEqual(sandbox._saveBlock.hidden, true);
    });

    it("drift state disables the save button and shows the save-block message (S-6)", () => {
        const sandbox = renderGateSandbox({ selected: ["fees.policy"], body: "We never charge participants." });
        sandbox.renderQaCoverageGate();
        assert.strictEqual(sandbox._saveBtn.disabled, true, "drift must disable save");
        assert.strictEqual(sandbox._saveBlock.hidden, false);
        assert.match(sandbox._saveBlock.textContent, /保存已被门禁拦截/);
        assert.match(sandbox._gateEl.innerHTML, /正文与「<b>费用政策<\/b>」的标准承诺<b>不一致<\/b>，无法保存/);
    });

    it("revoke card flags impact-bad when this rule is the last authority (I-5)", () => {
        const sandbox = renderGateSandbox({
            selected: ["fees.policy"],
            body: "We never charge any fees throughout the entire process.",
            authorities: { "fees.policy": [{ id: 10, displayName: "Participant fee policy" }] }
        });
        sandbox.state.selectedRuleId = 10;
        const group = sandbox.state.qaControlledGroups.find((g) => g.id === "G2");
        const card = sandbox.renderQaGateRevokeCard(group);
        assert.match(card, /impact-bad/);
        assert.match(card, /转人工/);
    });

    it("revoke card flags impact-ok when another enabled rule covers the key (I-5)", () => {
        const sandbox = renderGateSandbox({
            selected: ["fees.policy"],
            body: "We never charge any fees throughout the entire process.",
            authorities: {
                "fees.policy": [
                    { id: 10, displayName: "Participant fee policy" },
                    { id: 41, displayName: "Program overview" }
                ]
            }
        });
        sandbox.state.selectedRuleId = 10;
        const group = sandbox.state.qaControlledGroups.find((g) => g.id === "G2");
        const card = sandbox.renderQaGateRevokeCard(group);
        assert.match(card, /impact-ok/);
        assert.doesNotMatch(card, /impact-bad/);
    });

    it("opening the revoke card does not change the save button state (I-5)", () => {
        const sandbox = renderGateSandbox({ selected: ["fees.policy"], body: "We never charge any fees throughout the entire process." });
        sandbox.renderQaCoverageGate();
        assert.strictEqual(sandbox._saveBtn.disabled, false);
        sandbox.qaGateUiState.revokeGroupId = "G2";
        sandbox.renderQaCoverageGate();
        assert.match(sandbox._gateEl.innerHTML, /qa-gate-revoke/);
        assert.match(sandbox._gateEl.innerHTML, /解除本规则对「费用政策」的授权？/);
        assert.strictEqual(sandbox._saveBtn.disabled, false, "revoke card must not alter save state");
    });

    it("revoke-do unchecks every key of the controlled group together (I-4)", () => {
        const inputs = {
            "contract.party": { checked: true },
            "contract.terms": { checked: true },
            "ip.arrangements": { checked: true }
        };
        const queried = [];
        const container = {
            querySelector: (sel) => {
                queried.push(sel);
                const match = sel.match(/data-coverage-key="([^"]+)"/);
                return match ? inputs[match[1]] || null : null;
            }
        };
        const renderCalls = { options: 0, chips: 0, gate: 0 };
        const sandbox = {
            state: { qaControlledGroups: GROUPS },
            qaGateUiState: { canonOpen: false, revokeGroupId: "G3" },
            $: (sel) => sel === "#qaCoverageKeyOptions" ? container : null,
            collectQaCoverageKeys: () => ["contract.party", "contract.terms", "ip.arrangements"],
            renderQaCoverageKeyOptions: () => { renderCalls.options++; },
            renderQaCoverageKeyChips: () => { renderCalls.chips++; },
            renderQaCoverageGate: () => { renderCalls.gate++; }
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFn("revokeQaCoverageGroup"), sandbox);
        sandbox.revokeQaCoverageGroup("G3");
        assert.ok(queried.includes('.qa-cov-input[data-coverage-key="contract.party"]'), "must query contract.party with a quoted selector");
        assert.ok(queried.includes('.qa-cov-input[data-coverage-key="contract.terms"]'), "must query contract.terms with a quoted selector");
        assert.ok(!queried.includes('.qa-cov-input[data-coverage-key="ip.arrangements"]'), "must NOT uncheck ip.arrangements (other group)");
        assert.strictEqual(inputs["contract.party"].checked, false);
        assert.strictEqual(inputs["contract.terms"].checked, false);
        assert.strictEqual(inputs["ip.arrangements"].checked, true);
        assert.strictEqual(sandbox.qaGateUiState.revokeGroupId, null);
        assert.ok(renderCalls.options >= 1 && renderCalls.chips >= 1 && renderCalls.gate >= 1, "panel, chips and gate must re-render");
    });
});
