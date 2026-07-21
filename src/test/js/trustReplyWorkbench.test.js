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

function loadWorkbenchFns(sandbox, ...names) {
    names.forEach((name) => vm.runInContext(extractFn(name), sandbox));
}

describe("trust reply workbench (from app.js)", () => {
    it("does not expose legacy segment merge helpers", () => {
        assert.ok(!appJsSource.includes("function buildComposedSegments"));
        assert.ok(!appJsSource.includes("function mergeSegmentsToText"));
        assert.ok(!appJsSource.includes("function buildDeterministicComposedPreview"));
        assert.ok(!appJsSource.includes("function setupComposeDragDrop"));
    });

    it("renders workbench title and operator instruction field", () => {
        assert.ok(appJsSource.includes("可信回复工作台"));
        assert.ok(appJsSource.includes('id="composedOperatorInstruction"'));
        assert.ok(appJsSource.includes("data-action=\"trust-generate-draft\""));
        assert.ok(appJsSource.includes("data-action=\"trust-adopt-draft\""));
    });

    it("evaluateComposedFacts posts selected fact ids to server", () => {
        const evaluation = {
            canonicalFactIds: [10, 20],
            suggestedFactIds: [10],
            draftReadiness: "READY",
            requestCoverage: [],
            gapDetected: false
        };
        const sandbox = {
            composedReplyState: {
                recordId: 42,
                selectedFactIds: [10, 20],
                evaluateSeq: 0
            },
            api: async (url, options) => {
                sandbox.lastEvaluateUrl = url;
                sandbox.lastEvaluateBody = JSON.parse(options.body);
                return evaluation;
            },
            syncComposeFactCheckboxes: () => {},
            refreshComposedWorkbenchUI: () => {}
        };
        vm.createContext(sandbox);
        loadWorkbenchFns(sandbox, "sameFactIdSet", "evaluateComposedFacts");
        return sandbox.evaluateComposedFacts().then(() => {
            assert.strictEqual(sandbox.lastEvaluateUrl, "/api/mail/unmatched-inbound/42/composed-reply/evaluate");
            assert.deepStrictEqual(sandbox.lastEvaluateBody, { factRuleIds: [10, 20] });
            assert.strictEqual(JSON.stringify(sandbox.composedReplyState.selectedFactIds), JSON.stringify([10, 20]));
            assert.strictEqual(sandbox.composedReplyState.evaluationPending, false);
            assert.deepStrictEqual(sandbox.composedReplyState.confirmedEvaluation, evaluation);
        });
    });

    it("requestCoverageBadgeClass maps server statuses to badge classes", () => {
        const sandbox = {};
        vm.createContext(sandbox);
        loadWorkbenchFns(sandbox, "requestCoverageBadgeClass");
        assert.strictEqual(sandbox.requestCoverageBadgeClass("GROUNDED"), "ok");
        assert.strictEqual(sandbox.requestCoverageBadgeClass("NEEDS_REVIEW"), "warn");
        assert.strictEqual(sandbox.requestCoverageBadgeClass("BLOCKED"), "error");
    });

    it("clearComposedDraftSession resets draft and locked facts", () => {
        const sandbox = {
            composedReplyState: {
                draft: { rendered: "x" },
                lockedFactIds: [10],
                recordId: 1
            },
            aiReplyState: { requestSeq: 0 },
            resetAiReplyState: (recordId) => {
                sandbox.resetCalledWith = recordId;
            },
            renderComposedDraftPreview: () => {},
            updateTrustWorkbenchButtons: () => {}
        };
        vm.createContext(sandbox);
        loadWorkbenchFns(sandbox, "clearComposedDraftSession");
        sandbox.clearComposedDraftSession();
        assert.strictEqual(sandbox.composedReplyState.draft, null);
        assert.strictEqual(sandbox.composedReplyState.lockedFactIds, null);
        assert.strictEqual(sandbox.resetCalledWith, 1);
    });

    it("confirmedCanonicalFactIds rejects pending or stale evaluation", () => {
        const sandbox = {
            composedReplyState: {
                evaluationPending: true,
                confirmedEvaluation: { canonicalFactIds: [10] },
                selectedFactIds: [10]
            }
        };
        vm.createContext(sandbox);
        loadWorkbenchFns(sandbox, "sameFactIdSet", "confirmedCanonicalFactIds");
        assert.strictEqual(sandbox.confirmedCanonicalFactIds(), null);

        sandbox.composedReplyState.evaluationPending = false;
        sandbox.composedReplyState.selectedFactIds = [];
        assert.strictEqual(sandbox.confirmedCanonicalFactIds(), null);

        sandbox.composedReplyState.selectedFactIds = [10];
        assert.strictEqual(
            JSON.stringify(sandbox.confirmedCanonicalFactIds()),
            JSON.stringify([10])
        );
    });

    it("markComposedEvaluationPending clears confirmed evaluation before debounced evaluate returns", () => {
        const sandbox = {
            composedReplyState: {
                evaluationPending: false,
                confirmedEvaluation: { canonicalFactIds: [10] },
                selectedFactIds: [],
                draft: { rendered: "draft" },
                lockedFactIds: [10],
                recordId: 42
            },
            aiReplyState: { requestSeq: 0 },
            resetAiReplyState: () => {},
            renderComposedDraftPreview: () => {},
            updateTrustWorkbenchButtons: () => {}
        };
        vm.createContext(sandbox);
        loadWorkbenchFns(sandbox, "clearComposedDraftSession", "markComposedEvaluationPending");
        sandbox.markComposedEvaluationPending();
        assert.strictEqual(sandbox.composedReplyState.evaluationPending, true);
        assert.strictEqual(sandbox.composedReplyState.confirmedEvaluation, null);
        assert.strictEqual(sandbox.composedReplyState.draft, null);
        assert.strictEqual(sandbox.composedReplyState.lockedFactIds, null);
    });

    it("updateTrustWorkbenchButtons disables generate while evaluation is pending", () => {
        const generateBtn = { textContent: "", disabled: false };
        const adoptBtn = {
            textContent: "", disabled: false,
            setAttribute: () => {}, removeAttribute: () => {}
        };
        const headingEl = { textContent: "" };
        const sandbox = {
            composedReplyState: {
                suggest: { llmEnabled: true },
                evaluationPending: true,
                confirmedEvaluation: null,
                selectedFactIds: [],
                draft: null,
                lockedFactIds: null
            },
            aiReplyState: { firstTurnDone: false, inFlight: false },
            document: {
                getElementById: (id) => {
                    if (id === "trustDraftHeading") return headingEl;
                    return null;
                }
            },
            isAiReplyGenerationSuccess: () => false,
            resolveAiReplyFailureReason: () => null,
            $: (selector) => {
                if (selector === "#trustGenerateDraftBtn") return generateBtn;
                if (selector === "#trustAdoptDraftBtn") return adoptBtn;
                return null;
            }
        };
        vm.createContext(sandbox);
        loadWorkbenchFns(sandbox, "sameFactIdSet", "confirmedCanonicalFactIds", "updateTrustWorkbenchButtons");
        sandbox.updateTrustWorkbenchButtons();
        assert.strictEqual(generateBtn.disabled, true);
    });

    it("fact labels fall back to displayName/sectionTitle/replySubject/事实名称缺失 instead of rule ids", () => {
        assert.ok(!appJsSource.includes('`规则 #${ruleId}`'));
        assert.ok(!appJsSource.includes('`规则 #${rule.id}`'));
        assert.ok(appJsSource.includes("事实名称缺失"));
        assert.ok(!appJsSource.includes('const UNNAMED_FACT_LABEL'));

        const selectedList = { innerHTML: "" };
        const sandbox = {
            composedReplyState: {
                evaluationPending: false,
                confirmedEvaluation: { canonicalFactIds: [42] },
                selectedFactIds: [42],
                suggest: {
                    suggestedRules: [],
                    rulesByCategory: [{ rules: [{ id: 42, displayName: "", sectionTitle: "", replySubject: "" }] }]
                },
                draft: { result: { evidenceSources: [] } }
            },
            findSuggestRule: (suggest, ruleId) => {
                for (const category of suggest.rulesByCategory || []) {
                    const rule = (category.rules || []).find((item) => item.id === ruleId);
                    if (rule) return rule;
                }
                return (suggest.suggestedRules || []).find((item) => item.id === ruleId) || null;
            },
            resolveFactDisplayName: (ruleId, evidenceSources, suggest) => {
                if (suggest) {
                    const rule = sandbox.findSuggestRule(suggest, ruleId);
                    if (rule) {
                        const name = (rule.displayName || "").trim();
                        if (name && name !== "未命名事实") return name;
                        const section = (rule.sectionTitle || "").trim();
                        if (section && section !== "未命名事实") return section;
                        const subject = (rule.replySubject || "").trim();
                        if (subject && subject !== "未命名事实") return subject;
                    }
                }
                return "事实名称缺失";
            },
            escapeHtml: (value) => String(value),
            $: (selector) => {
                if (selector === "#composedSelectedList") {
                    return selectedList;
                }
                return null;
            }
        };
        vm.createContext(sandbox);
        loadWorkbenchFns(sandbox, "sameFactIdSet", "confirmedCanonicalFactIds", "renderComposedSelectedList");
        sandbox.renderComposedSelectedList();
        assert.ok(selectedList.innerHTML.includes("事实名称缺失"));
        assert.ok(!selectedList.innerHTML.includes("#42"));
        assert.ok(!selectedList.innerHTML.includes("规则 #"));
        assert.ok(!selectedList.innerHTML.includes("未命名事实"));
    });

    // ── Button attribute / input retention / send independence (Phase 08 I-4/I-5) ──

    it("adopt button is disabled with aria and title on generation failure", () => {
        const generateBtn = { textContent: "", disabled: false };
        const adoptBtn = {
            textContent: "", disabled: false, _title: "", _aria: null,
            setAttribute: (k, v) => { if (k === "aria-disabled") adoptBtn._aria = v; },
            removeAttribute: () => { adoptBtn._aria = null; adoptBtn._title = ""; },
            getAttribute: (k) => k === "aria-disabled" ? adoptBtn._aria : null,
            get title() { return adoptBtn._title; },
            set title(v) { adoptBtn._title = v; }
        };
        const headingEl = { textContent: "" };
        const sandbox = {
            composedReplyState: {
                suggest: { llmEnabled: true },
                evaluationPending: false,
                selectedFactIds: [1],
                confirmedEvaluation: { canonicalFactIds: [1] },
                lockedFactIds: [1],
                draft: {
                    rendered: "ref text",
                    result: { usedLlm: false, generationState: "FALLBACK_NO_RESPONSE", contextWarnings: [] }
                }
            },
            aiReplyState: { firstTurnDone: true, inFlight: false },
            document: { getElementById: (id) => id === "trustDraftHeading" ? headingEl : null },
            isAiReplyGenerationSuccess: () => false,
            resolveAiReplyFailureReasonFromResult: () => "FALLBACK_NO_RESPONSE",
            confirmedCanonicalFactIds: () => [1],
            $: (sel) => {
                if (sel === "#trustGenerateDraftBtn") return generateBtn;
                if (sel === "#trustAdoptDraftBtn") return adoptBtn;
                return null;
            }
        };
        vm.createContext(sandbox);
        loadWorkbenchFns(sandbox, "sameFactIdSet", "confirmedCanonicalFactIds", "updateTrustWorkbenchButtons");
        sandbox.updateTrustWorkbenchButtons();
        assert.strictEqual(adoptBtn.disabled, true);
        assert.strictEqual(adoptBtn._aria, "true");
        assert.match(adoptBtn.title, /LLM 生成失败/);
        assert.strictEqual(generateBtn.textContent, "重试生成");
        assert.strictEqual(headingEl.textContent, "QA 规则参考内容");
    });

    it("adopt button fails closed when rendered draft has no result", () => {
        const generateBtn = { textContent: "", disabled: false };
        const adoptBtn = {
            disabled: false, _title: "", _aria: null,
            setAttribute: (k, v) => { if (k === "aria-disabled") adoptBtn._aria = v; },
            removeAttribute: () => { adoptBtn._aria = null; adoptBtn._title = ""; },
            get title() { return adoptBtn._title; },
            set title(v) { adoptBtn._title = v; }
        };
        const headingEl = { textContent: "" };
        const sandbox = {
            composedReplyState: {
                suggest: { llmEnabled: true }, evaluationPending: false,
                selectedFactIds: [1], confirmedEvaluation: { canonicalFactIds: [1] },
                lockedFactIds: [1], draft: { rendered: "ref text", result: null }
            },
            aiReplyState: { firstTurnDone: true, inFlight: false },
            document: { getElementById: () => headingEl },
            isAiReplyGenerationSuccess: () => false,
            resolveAiReplyFailureReasonFromResult: () => "FALLBACK_NO_RESPONSE",
            confirmedCanonicalFactIds: () => [1],
            $: (sel) => sel === "#trustGenerateDraftBtn" ? generateBtn
                : sel === "#trustAdoptDraftBtn" ? adoptBtn : null
        };
        vm.createContext(sandbox);
        loadWorkbenchFns(sandbox, "sameFactIdSet", "confirmedCanonicalFactIds", "updateTrustWorkbenchButtons");
        sandbox.updateTrustWorkbenchButtons();
        assert.strictEqual(adoptBtn.disabled, true);
        assert.strictEqual(adoptBtn._aria, "true");
        assert.match(adoptBtn.title, /LLM 生成失败/);
        assert.strictEqual(headingEl.textContent, "QA 规则参考内容");
    });

    it("adopt button enabled and heading restored on LLM success", () => {
        const generateBtn = { textContent: "", disabled: false };
        const adoptBtn = {
            textContent: "", disabled: true, _title: "old", _aria: "true",
            setAttribute: (k, v) => { if (k === "aria-disabled") adoptBtn._aria = v; },
            removeAttribute: () => { adoptBtn._aria = null; adoptBtn._title = ""; },
            getAttribute: (k) => k === "aria-disabled" ? adoptBtn._aria : null,
            get title() { return adoptBtn._title; },
            set title(v) { adoptBtn._title = v; }
        };
        const headingEl = { textContent: "QA 规则参考内容" };
        const sandbox = {
            composedReplyState: {
                suggest: { llmEnabled: true },
                evaluationPending: false,
                selectedFactIds: [1],
                confirmedEvaluation: { canonicalFactIds: [1] },
                lockedFactIds: [1],
                draft: {
                    rendered: "draft",
                    result: { usedLlm: true, generationState: "LLM_USED", contextWarnings: [] }
                }
            },
            aiReplyState: { firstTurnDone: false, inFlight: false },
            document: { getElementById: () => headingEl },
            isAiReplyGenerationSuccess: () => true,
            resolveAiReplyFailureReasonFromResult: () => null,
            confirmedCanonicalFactIds: () => [1],
            $: (sel) => {
                if (sel === "#trustGenerateDraftBtn") return generateBtn;
                if (sel === "#trustAdoptDraftBtn") return adoptBtn;
                return null;
            }
        };
        vm.createContext(sandbox);
        loadWorkbenchFns(sandbox, "sameFactIdSet", "confirmedCanonicalFactIds", "updateTrustWorkbenchButtons");
        sandbox.updateTrustWorkbenchButtons();
        assert.strictEqual(adoptBtn.disabled, false);
        assert.strictEqual(adoptBtn._aria, null);
        assert.strictEqual(adoptBtn.title, "");
        assert.strictEqual(headingEl.textContent, "可信草稿");
    });
});
