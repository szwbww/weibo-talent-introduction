const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const appJsPath = path.join(__dirname, "..", "..", "main", "resources", "static", "app.js");
const indexHtmlPath = path.join(__dirname, "..", "..", "main", "resources", "static", "index.html");
const appJsSource = fs.readFileSync(appJsPath, "utf-8");
const indexHtml = fs.readFileSync(indexHtmlPath, "utf-8");

function extractFn(name) {
    const regex = new RegExp("(?:async\\s+)?function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}");
    const match = appJsSource.match(regex);
    if (!match) throw new Error("Could not find " + name + " in app.js");
    return match[0];
}

const escapeHtmlImpl = (value) => String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");

function createLabelSandbox() {
    const sandbox = {
        state: { qaCoverageKeys: [] },
        escapeHtml: escapeHtmlImpl,
        badge: (label, type) => `<span class="badge ${type || "primary"}">${label}</span>`
    };
    vm.createContext(sandbox);
    vm.runInContext(extractFn("renderQaCoverageKeyLabels"), sandbox);
    return sandbox;
}

const DEFAULT_KEYS = [
    { key: "general.answer", label: "通用回答", group: "通用", controlled: false },
    { key: "company.legal_name", label: "公司法定名称", group: "公司信息", controlled: false },
    { key: "fees.policy", label: "费用政策", group: "费用与保密", controlled: true, groupId: "G2", groupName: "费用政策" },
    { key: "confidentiality.materials", label: "材料保密", group: "费用与保密", controlled: true, groupId: "G1", groupName: "材料保密" }
];

const DEFAULT_GROUPS = [
    { id: "G1", name: "材料保密", keys: ["confidentiality.materials"], canonicalBody: "Your materials are kept strictly confidential." },
    { id: "G2", name: "费用政策", keys: ["fees.policy"], canonicalBody: "We never charge any fees throughout the entire process." }
];

function createGateSandbox(overrides = {}) {
    const inputs = (overrides.inputs || []).map((item) => {
        const fakeItem = { classList: { remove() {}, toggle() {} } };
        return {
            checked: !!item.checked,
            dataset: { coverageKey: item.key },
            closest: () => fakeItem
        };
    });
    const container = {
        querySelectorAll: () => inputs,
        querySelector: (selector) => {
            const match = /data-coverage-key="([^"]+)"/.exec(selector);
            if (!match) return null;
            return inputs.find((input) => input.dataset.coverageKey === match[1]) || null;
        }
    };
    const gate = {
        className: "",
        innerHTML: "",
        insertAdjacentHTML: function (position, html) {
            this.innerHTML += html;
        }
    };
    const saveBtn = { disabled: false };
    const saveBlock = { hidden: true, textContent: "" };
    const badge = { hidden: true };
    const textarea = { value: overrides.body || "" };
    const countEl = { textContent: "" };
    const chips = { innerHTML: "" };
    const sandbox = {
        state: {
            qaCoverageKeys: overrides.keys || DEFAULT_KEYS,
            qaControlledGroups: overrides.controlledGroups || DEFAULT_GROUPS,
            qaCoverageAuthorities: overrides.authorities || {},
            selectedRuleId: overrides.selectedRuleId || null
        },
        $: (selector) => {
            if (selector === "#qaCoverageKeyOptions") return container;
            if (selector === "#qaRuleAnswerBody") return textarea;
            if (selector === "#qaRuleSaveBtn") return saveBtn;
            if (selector === "#qaCoverageSaveBlock") return saveBlock;
            if (selector === "#qaCoverageBodyBadge") return badge;
            if (selector === "#qaCoverageGate") return gate;
            if (selector === "#qaCoverageKeyCount") return countEl;
            if (selector === "#qaCoverageKeyChips") return chips;
            return null;
        },
        escapeHtml: escapeHtmlImpl
    };
    vm.createContext(sandbox);
    [
        "qaCoverageEntry",
        "qaCoverageKeyControlled",
        "collectQaCoverageKeys",
        "evaluateQaCoverageGate",
        "renderQaCoverageGate",
        "renderGateRevokeCard",
        "renderGateCanonCard",
        "diffWordsForGate",
        "updateCoverageKeyCount",
        "renderQaCoverageKeyChips",
        "uncheckCoverageKey",
        "doGateRevoke"
    ].forEach((name) => vm.runInContext(extractFn(name), sandbox));
    return { sandbox, gate, saveBtn, saveBlock, badge, textarea, inputs };
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

describe("qa coverage gate UI (restored)", () => {
    it("index.html hosts the coverage editor containers", () => {
        assert.match(indexHtml, /id="qaCoverageKeyOptions"/);
        assert.match(indexHtml, /id="qaCoverageKeyChips"/);
        assert.match(indexHtml, /id="qaCoverageGate"/);
        assert.match(indexHtml, /id="qaCoverageSaveBlock"/);
        assert.match(indexHtml, /id="qaCoverageKeyCount"/);
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

    it("loadQa fetches coverage-keys metadata plus gate endpoints", () => {
        const loadFn = extractFn("loadQa");
        assert.match(loadFn, /\/api\/qa\/coverage-keys/);
        assert.match(loadFn, /\/api\/qa\/coverage-keys\/controlled-groups/);
        assert.match(loadFn, /\/api\/qa\/coverage-keys\/authorities/);
    });

    it("saveQaRule sends coverageKeys collected from the DOM", () => {
        const saveFn = extractFn("saveQaRule");
        assert.match(saveFn, /coverageKeys:\s*collectQaCoverageKeys\(\)/);
        assert.match(saveFn, /answerBody/);
    });

    it("fillQaRuleForm renders coverage options and the gate", () => {
        const fillFn = extractFn("fillQaRuleForm");
        assert.match(fillFn, /renderQaCoverageKeyOptions/);
        assert.match(fillFn, /renderQaCoverageGate/);
        assert.match(fillFn, /answerBody/);
    });

    it("table colspan matches nine-column fact-card layout", () => {
        assert.match(appJsSource, /colspan="9"/);
    });

    it("no coverage key constants hardcoded in app.js", () => {
        assert.doesNotMatch(appJsSource, /"company\.legal_name"/);
        assert.doesNotMatch(appJsSource, /"programme\.purpose"/);
    });

    it("collectQaCoverageKeys returns empty array when nothing is checked", () => {
        const { sandbox } = createGateSandbox({ inputs: [{ key: "fees.policy" }, { key: "general.answer" }] });
        assert.deepStrictEqual(Array.from(sandbox.collectQaCoverageKeys()), []);
    });

    it("collectQaCoverageKeys walks every .qa-cov-input and returns checked keys in DOM order", () => {
        const { sandbox } = createGateSandbox({
            inputs: [
                { key: "fees.policy", checked: true },
                { key: "general.answer", checked: true },
                { key: "company.legal_name", checked: false }
            ]
        });
        assert.deepStrictEqual(Array.from(sandbox.collectQaCoverageKeys()), ["fees.policy", "general.answer"]);
    });

    it("evaluateQaCoverageGate returns none for non-controlled selections", () => {
        const { sandbox } = createGateSandbox({
            inputs: [{ key: "general.answer", checked: true }],
            body: "Any body text."
        });
        assert.strictEqual(sandbox.evaluateQaCoverageGate().status, "none");
    });

    it("evaluateQaCoverageGate returns aligned for an exact group with matching body", () => {
        const { sandbox } = createGateSandbox({
            inputs: [{ key: "fees.policy", checked: true }],
            body: "We never charge any fees throughout the entire process."
        });
        assert.strictEqual(sandbox.evaluateQaCoverageGate().status, "aligned");
    });

    it("evaluateQaCoverageGate returns drift for an exact group with drifted body", () => {
        const { sandbox } = createGateSandbox({
            inputs: [{ key: "fees.policy", checked: true }],
            body: "We never charge any fees during the application stage."
        });
        assert.strictEqual(sandbox.evaluateQaCoverageGate().status, "drift");
    });

    it("evaluateQaCoverageGate returns partial for a controlled key outside a full group", () => {
        const { sandbox } = createGateSandbox({
            inputs: [
                { key: "fees.policy", checked: true },
                { key: "company.legal_name", checked: true }
            ],
            body: "Some body."
        });
        assert.strictEqual(sandbox.evaluateQaCoverageGate().status, "partial");
    });

    it("drift disables the save button and shows the block message", () => {
        const { sandbox, gate, saveBtn, saveBlock, badge } = createGateSandbox({
            inputs: [{ key: "fees.policy", checked: true }],
            body: "We never charge any fees during the application stage."
        });
        sandbox.renderQaCoverageGate();
        assert.strictEqual(saveBtn.disabled, true);
        assert.strictEqual(saveBlock.hidden, false);
        assert.match(saveBlock.textContent, /保存已被门禁拦截/);
        assert.strictEqual(badge.hidden, false);
        assert.match(gate.innerHTML, /恢复标准正文/);
        assert.match(gate.innerHTML, /解除本规则对「费用政策」的授权/);
    });

    it("partial keeps the save button enabled (I-1 parity)", () => {
        const { sandbox, gate, saveBtn, saveBlock } = createGateSandbox({
            inputs: [
                { key: "fees.policy", checked: true },
                { key: "company.legal_name", checked: true }
            ],
            body: "Some body."
        });
        sandbox.renderQaCoverageGate();
        assert.strictEqual(saveBtn.disabled, false);
        assert.strictEqual(saveBlock.hidden, true);
        assert.match(gate.innerHTML, /不构成对外承诺的权威出处/);
    });

    it("revoke card shows green impact-ok when another authority exists and never touches save state", () => {
        const { sandbox, gate, saveBtn } = createGateSandbox({
            inputs: [{ key: "fees.policy", checked: true }],
            body: "We never charge any fees throughout the entire process.",
            authorities: { "fees.policy": [{ id: 5, displayName: "Participant fee policy" }] },
            selectedRuleId: 3
        });
        sandbox.renderGateRevokeCard("G2");
        assert.match(gate.innerHTML, /impact-ok/);
        assert.match(gate.innerHTML, /仍有其它权威出处/);
        assert.match(gate.innerHTML, /Participant fee policy/);
        assert.strictEqual(saveBtn.disabled, false);
    });

    it("revoke card shows red impact-bad with handoff copy when it is the last authority", () => {
        const { sandbox, gate, saveBtn } = createGateSandbox({
            inputs: [{ key: "fees.policy", checked: true }],
            body: "We never charge any fees throughout the entire process.",
            authorities: {},
            selectedRuleId: 3
        });
        sandbox.renderGateRevokeCard("G2");
        assert.match(gate.innerHTML, /impact-bad/);
        assert.match(gate.innerHTML, /转人工/);
        assert.match(gate.innerHTML, /确认解除授权/);
        assert.strictEqual(saveBtn.disabled, false);
    });

    it("revoke-do removes every key of the group together (contract.party + contract.terms)", () => {
        const g3 = {
            id: "G3",
            name: "合同安排",
            keys: ["contract.party", "contract.terms"],
            canonicalBody: "After selection, you will sign a labor contract directly with the matched enterprise."
        };
        const { sandbox, inputs } = createGateSandbox({
            inputs: [
                { key: "contract.party", checked: true },
                { key: "contract.terms", checked: true }
            ],
            controlledGroups: [g3],
            body: g3.canonicalBody
        });
        sandbox.doGateRevoke(g3);
        assert.strictEqual(inputs[0].checked, false, "contract.party must be unchecked");
        assert.strictEqual(inputs[1].checked, false, "contract.terms must be unchecked");
        assert.deepStrictEqual(Array.from(sandbox.collectQaCoverageKeys()), []);
    });
});
