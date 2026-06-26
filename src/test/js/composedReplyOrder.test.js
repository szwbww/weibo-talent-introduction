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

function createComposeSandbox() {
    const sandbox = {
        composedReplyState: {
            suggest: null,
            selectedRuleIds: [],
            freeText: "",
            previewEdited: false,
            baselinePreview: ""
        },
        QA_COMPOSE_GREETING: "Thank you for your email. Please find our answers below.",
        QA_COMPOSE_CLOSING: "Please let us know if you have any further questions.\n\nBest regards,\nTalent Introduction Team",
        escapeHtml: (v) => String(v == null ? "" : v),
        $: () => null
    };
    vm.createContext(sandbox);
    [
        "findSuggestRule",
        "buildDeterministicComposedPreview",
        "sortCategoryRulesForDisplay",
        "renderComposedSelectedList",
        "setupComposeDragDrop",
        "refreshComposedPreviewFromRules"
    ].forEach((name) => vm.runInContext(extractFn(name), sandbox));
    return sandbox;
}

describe("composed reply order (from app.js)", () => {
    const suggest = {
        rulesByCategory: [
            {
                categoryId: 1,
                categoryName: "Funding",
                composeOrder: 10,
                rules: [
                    { id: 10, displayName: "Rule A", sectionTitle: "A", replyBody: "Body A" },
                    { id: 20, displayName: "Rule B", sectionTitle: "B", replyBody: "Body B" }
                ]
            }
        ]
    };

    it("preview follows selectedRuleIds order instead of composeOrder", () => {
        const sb = createComposeSandbox();
        const preview = sb.buildDeterministicComposedPreview([20, 10], suggest, "");
        assert.ok(preview.indexOf("Body B") < preview.indexOf("Body A"));
    });

    it("move-down updates preview order", () => {
        const sb = createComposeSandbox();
        sb.composedReplyState.suggest = suggest;
        sb.composedReplyState.selectedRuleIds = [10, 20];
        sb.composedReplyState.previewEdited = false;
        sb.refreshComposedPreviewFromRules = function refreshComposedPreviewFromRules() {
            const preview = sb.buildDeterministicComposedPreview(
                sb.composedReplyState.selectedRuleIds,
                sb.composedReplyState.suggest,
                sb.composedReplyState.freeText
            );
            sb.composedReplyState.baselinePreview = preview;
            sb.__preview = preview;
        };

        sb.refreshComposedPreviewFromRules();
        assert.ok(sb.__preview.indexOf("Body A") < sb.__preview.indexOf("Body B"));

        const ids = sb.composedReplyState.selectedRuleIds;
        [ids[1], ids[0]] = [ids[0], ids[1]];
        sb.refreshComposedPreviewFromRules();
        assert.ok(sb.__preview.indexOf("Body B") < sb.__preview.indexOf("Body A"));
    });

    it("suggested rules render before non-suggested rules in category panel sort", () => {
        const sb = createComposeSandbox();
        const suggestedSet = new Set([20]);
        const rules = [
            { id: 10, displayName: "Rule A" },
            { id: 20, displayName: "Rule B" },
            { id: 30, displayName: "Rule C" }
        ];
        const sorted = sb.sortCategoryRulesForDisplay(rules, suggestedSet);
        assert.deepEqual(sorted.map((rule) => rule.id), [20, 10, 30]);
    });

    it("preserves backend stable order within suggested and non-suggested groups", () => {
        const sb = createComposeSandbox();
        const suggestedSet = new Set([20]);
        const rules = [
            { id: 30, priority: 10, displayName: "Rule C" },
            { id: 20, priority: 20, displayName: "Rule B" },
            { id: 10, priority: 30, displayName: "Rule A" }
        ];
        const sorted = sb.sortCategoryRulesForDisplay(rules, suggestedSet);
        assert.deepEqual(sorted.map((rule) => rule.id), [20, 30, 10]);
    });

    it("preserves relative order among multiple suggested rules", () => {
        const sb = createComposeSandbox();
        const suggestedSet = new Set([30, 20]);
        const rules = [
            { id: 10, displayName: "Rule A" },
            { id: 30, displayName: "Rule C" },
            { id: 20, displayName: "Rule B" },
            { id: 40, displayName: "Rule D" }
        ];
        const sorted = sb.sortCategoryRulesForDisplay(rules, suggestedSet);
        assert.deepEqual(sorted.map((rule) => rule.id), [30, 20, 10, 40]);
    });
});
