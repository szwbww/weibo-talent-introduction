const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const root = path.join(__dirname, "..", "..", "main", "resources", "static");
const html = fs.readFileSync(path.join(root, "index.html"), "utf-8");
const app = fs.readFileSync(path.join(root, "app.js"), "utf-8");
const css = fs.readFileSync(path.join(root, "styles.css"), "utf-8");

function extractFunction(name) {
    const start = app.indexOf(`function ${name}(`);
    const asyncStart = app.indexOf(`async function ${name}(`);
    const index = asyncStart >= 0 && (start < 0 || asyncStart < start) ? asyncStart : start;
    if (index < 0) throw new Error(`missing ${name}`);
    let depth = 0;
    let opened = false;
    for (let cursor = index; cursor < app.length; cursor += 1) {
        if (app[cursor] === "{") { depth += 1; opened = true; }
        if (app[cursor] === "}") {
            depth -= 1;
            if (opened && depth === 0) return app.slice(index, cursor + 1);
        }
    }
    throw new Error(`unterminated ${name}`);
}

describe("AI training unsupported answer index", () => {
    it("uses the approved six-column read-only tab skeleton without new CSS", () => {
        assert.match(html, /class="ai-tab" data-tab="unsupportedAnswers"/);
        assert.match(html, /id="aiTabUnsupportedAnswers"/);
        assert.match(html, /id="aiTrainingUnsupportedAnswerSourceFilter"/);
        assert.match(html, /id="reloadAiTrainingUnsupportedAnswersBtn"/);
        assert.match(html, /id="aiTrainingUnsupportedAnswerTable"/);
        assert.match(html, /id="aiTrainingUnsupportedAnswerPager" class="list-pager" hidden/);
        ["状态/来源", "原问题", "操作员描述", "AI 回答", "模型", "创建时间"].forEach((title) => assert.ok(html.includes(`<th>${title}</th>`)));
        assert.doesNotMatch(html, /unsupported-answer[^\n]*(?:采用|复用|推荐|搜索|编辑|删除|晋升)/);
        assert.doesNotMatch(css, /unsupported-answer|unsupportedAnswers/);
    });

    it("whitelists the panel and keeps optional ES loading out of the main loader", () => {
        const switchTab = extractFunction("switchAiTrainingTab");
        const mainLoad = extractFunction("loadAiTraining");
        assert.match(switchTab, /unsupportedAnswers.*aiTabUnsupportedAnswers/);
        assert.match(switchTab, /loadAiTrainingUnsupportedAnswers/);
        assert.doesNotMatch(mainLoad, /unsupported-answers|loadAiTrainingUnsupportedAnswers/);
    });

    it("loads only after activation and sends bounded filter and page parameters", async () => {
        const calls = [];
        const panels = [
            { id: "aiTabQa", classList: { toggle: () => {} } },
            { id: "aiTabUnsupportedAnswers", classList: { toggle: () => {} } }
        ];
        const sandbox = {
            state: { aiTraining: { activeTab: "simulate", unsupportedAnswers: [], unsupportedAnswersTotal: 0, unsupportedAnswersPage: 0, unsupportedAnswersSize: 20, unsupportedAnswersSourceMode: "", unsupportedAnswersTopic: "", unsupportedAnswersLoaded: false, unsupportedAnswersLoading: false, unsupportedAnswersError: "", unsupportedAnswersRequestToken: 0 } },
            document: {
                querySelectorAll: (selector) => selector.includes("ai-tab-content") ? panels : [{ dataset: { tab: "unsupportedAnswers" }, classList: { toggle: () => {} } }]
            },
            URLSearchParams,
            api: async (url) => { calls.push(url); return { items: [], total: 0, page: 0, size: 20 }; },
            renderAiTrainingUnsupportedAnswers: () => {},
            showStatus: () => {}
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFunction("loadAiTrainingUnsupportedAnswers"), sandbox);
        vm.runInContext("this.loadAiTrainingUnsupportedAnswers = loadAiTrainingUnsupportedAnswers;", sandbox);
        assert.strictEqual(calls.length, 0);
        await sandbox.loadAiTrainingUnsupportedAnswers();
        assert.strictEqual(calls.length, 1);
        assert.match(calls[0], /\/api\/ai-training\/unsupported-answers\?page=0&size=20/);
        sandbox.state.aiTraining.unsupportedAnswersSourceMode = "TRAINING";
        sandbox.state.aiTraining.unsupportedAnswersPage = 1;
        // c6 (T-3 / I-3): topic keyword 精确过滤随参数发送。
        sandbox.state.aiTraining.unsupportedAnswersTopic = "company.followup";
        await sandbox.loadAiTrainingUnsupportedAnswers(true);
        assert.match(calls[1], /page=1&size=20&sourceMode=TRAINING&topic=company\.followup/);
        sandbox.api = async () => { throw new Error("UNSUPPORTED_ANSWER_INDEX_UNAVAILABLE"); };
        await sandbox.loadAiTrainingUnsupportedAnswers(true);
        assert.strictEqual(sandbox.state.aiTraining.unsupportedAnswersError, "UNSUPPORTED_ANSWER_INDEX_UNAVAILABLE");
        assert.doesNotMatch(extractFunction("loadAiTrainingUnsupportedAnswers"), /showStatus/);
    });

    it("lets a forced filter request supersede an in-flight request", async () => {
        const deferred = () => {
            let resolve;
            const promise = new Promise((resolver) => { resolve = resolver; });
            return { promise, resolve };
        };
        const first = deferred();
        const second = deferred();
        const calls = [];
        const renders = [];
        const sandbox = {
            state: { aiTraining: { unsupportedAnswers: [], unsupportedAnswersTotal: 0, unsupportedAnswersPage: 0, unsupportedAnswersSize: 20, unsupportedAnswersSourceMode: "", unsupportedAnswersLoaded: false, unsupportedAnswersLoading: false, unsupportedAnswersError: "", unsupportedAnswersRequestToken: 0 } },
            URLSearchParams,
            api: (url) => {
                calls.push(url);
                return calls.length === 1 ? first.promise : second.promise;
            },
            renderAiTrainingUnsupportedAnswers: () => {
                const training = sandbox.state.aiTraining;
                renders.push({ items: training.unsupportedAnswers.slice(), total: training.unsupportedAnswersTotal });
            }
        };
        vm.createContext(sandbox);
        vm.runInContext(extractFunction("loadAiTrainingUnsupportedAnswers"), sandbox);
        vm.runInContext("this.loadAiTrainingUnsupportedAnswers = loadAiTrainingUnsupportedAnswers;", sandbox);

        const firstLoad = sandbox.loadAiTrainingUnsupportedAnswers();
        sandbox.state.aiTraining.unsupportedAnswersSourceMode = "TRAINING";
        const secondLoad = sandbox.loadAiTrainingUnsupportedAnswers(true);
        assert.strictEqual(calls.length, 2);
        assert.match(calls[1], /page=0&size=20&sourceMode=TRAINING/);

        const newest = { requestText: "newest" };
        second.resolve({ items: [newest], total: 1 });
        await secondLoad;
        first.resolve({ items: [{ requestText: "stale" }], total: 99 });
        await firstLoad;

        assert.deepStrictEqual(sandbox.state.aiTraining.unsupportedAnswers, [newest]);
        assert.strictEqual(sandbox.state.aiTraining.unsupportedAnswersTotal, 1);
        assert.strictEqual(sandbox.state.aiTraining.unsupportedAnswersLoading, false);
        assert.deepStrictEqual(renders.at(-1).items, [newest]);
        assert.strictEqual(renders.at(-1).total, 1);
    });

    it("escapes content, delegates translation per cell, and exposes no mutation actions", () => {
        const renderer = extractFunction("renderAiTrainingUnsupportedAnswers");
        assert.match(renderer, /translatableBody\(item\.requestText/);
        // c6 (I-6 / T-3): 空 operatorInstruction 渲染为 —（不再整行丢弃）。
        assert.match(renderer, /escapeHtml\(item\.operatorInstruction \|\| "—"\)/);
        assert.match(renderer, /translatableBody\(item\.answerText/);
        assert.match(renderer, /暂无已确认的无依据回答/);
        // c6 (I-5 / A-2): 状态列按 status × sourceMode 实际值渲染，带「运营已编辑」标记；
        // 列表仍只读——无 data-action 或任何操作按钮。「编辑」仅出现在标记文案中，
        // 故从禁用词中去掉，其余禁用词（采用/复用/推荐/相似/删除/晋升）保留。
        assert.match(renderer, /item\.status === "CANDIDATE" \? "CANDIDATE \/ 待转事实" : "ACTIVE \/ 已转化"/);
        assert.match(renderer, /运营已编辑/);
        assert.doesNotMatch(renderer, /data-action|采用|复用|推荐|相似|删除|晋升/);
        assert.match(app, /reloadAiTrainingUnsupportedAnswersBtn/);
        assert.match(app, /aiTrainingUnsupportedAnswerSourceFilter/);
    });

    // c6 (T-3 / T-5): topic 下拉与「待转事实」视图由 app.js 运行时注入（index.html
    // 不在本计划变更清单），发送 topic 过滤参数并维护待转事实激活链。
    it("wires the topic filter and pending-topics view without new CSS", () => {
        assert.match(app, /aiTrainingUnsupportedAnswerTopicFilter/);
        assert.match(app, /unsupportedAnswersTopic/);
        assert.match(app, /ensureAiTrainingPendingTopicsSection/);
        assert.match(app, /pending-topics\?threshold=3/);
        assert.match(app, /pending-topics\/\$\{encodeURIComponent\(topic\)\}\/activate/);
        assert.match(app, /pendingActivationTopic/);
        assert.doesNotMatch(css, /unsupported-answer|unsupportedAnswers/);
    });

    it("keeps evaluation saved while showing independent archive status", async () => {
        const save = extractFunction("saveAiTrainingEvaluation");
        const cases = [
            ["SAVED", 2, 0, "已保存评估 #123 · 已归档 2 条无依据回答", "训练评估已保存", "ok"],
            ["PARTIAL", 1, 1, "评估已保存 #123；无依据回答仅归档 1/2 条", "训练评估已保存；请前往无依据回答索引 Tab 检查归档结果", "warn"],
            ["FAILED", 0, 2, "评估已保存 #123；无依据回答索引写入失败", "训练评估已保存；请前往无依据回答索引 Tab 检查归档结果", "warn"],
            ["NOT_APPLICABLE", 0, 0, "已保存评估 #123 · 2026-07-30T10:00:00Z", "训练评估已保存", "ok"]
        ];
        for (const [archiveStatus, archivedCount, failedCount, expectedStatus, expectedToastMessage, expectedToastLevel] of cases) {
            const token = {};
            const assembly = {
                source: { sourceType: "TRAINING_MAIL", sourceId: 11 },
                sourceVersion: "source-v1",
                evidenceSetVersion: "evidence-v1",
                itemVersions: []
            };
            const status = { textContent: "" };
            const button = { disabled: false, textContent: "保存评估" };
            const panel = {
                querySelector: (selector) => {
                    if (selector.includes("training-evaluation-status")) return status;
                    if (selector.includes("save-training-evaluation")) return button;
                    if (selector.includes("training-evaluation-note")) return { value: "" };
                    if (selector.includes("aiTrainingEvaluationRating")) return { value: "MEETS_EXPECTATION" };
                    return null;
                }
            };
            const calls = [];
            const toasts = [];
            const sandbox = {
                aiTrainingEvaluationContext: { token, assembly, saved: false },
                $: () => panel,
                api: async (...args) => {
                    calls.push(args);
                    return {
                        evaluationId: 123,
                        createdAt: "2026-07-30T10:00:00Z",
                        unsupportedAnswerArchiveStatus: archiveStatus,
                        unsupportedAnswerArchivedCount: archivedCount,
                        unsupportedAnswerArchiveFailedCount: failedCount
                    };
                },
                showStatus: (message, level) => toasts.push({ message, level }),
                window: { localStorage: { getItem: () => "operator" } }
            };
            vm.createContext(sandbox);
            vm.runInContext(save, sandbox);
            vm.runInContext("this.saveAiTrainingEvaluation = saveAiTrainingEvaluation;", sandbox);
            await sandbox.saveAiTrainingEvaluation(token);
            await sandbox.saveAiTrainingEvaluation(token);
            assert.strictEqual(calls.length, 1);
            assert.strictEqual(sandbox.aiTrainingEvaluationContext.saved, true);
            assert.strictEqual(button.disabled, true);
            assert.strictEqual(button.textContent, "已保存");
            assert.strictEqual(status.textContent, expectedStatus);
            assert.deepStrictEqual(toasts, [{ message: expectedToastMessage, level: expectedToastLevel }]);
        }
    });
});
