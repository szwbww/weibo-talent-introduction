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
            state: { aiTraining: { activeTab: "simulate", unsupportedAnswers: [], unsupportedAnswersTotal: 0, unsupportedAnswersPage: 0, unsupportedAnswersSize: 20, unsupportedAnswersSourceMode: "", unsupportedAnswersLoaded: false, unsupportedAnswersLoading: false, unsupportedAnswersError: "", unsupportedAnswersRequestToken: 0 } },
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
        await sandbox.loadAiTrainingUnsupportedAnswers(true);
        assert.match(calls[1], /page=1&size=20&sourceMode=TRAINING/);
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
        assert.match(renderer, /escapeHtml\(item\.operatorInstruction/);
        assert.match(renderer, /translatableBody\(item\.answerText/);
        assert.match(renderer, /暂无已确认的无依据回答/);
        assert.doesNotMatch(renderer, /data-action|采用|复用|推荐|相似|编辑|删除|晋升/);
        assert.match(app, /reloadAiTrainingUnsupportedAnswersBtn/);
        assert.match(app, /aiTrainingUnsupportedAnswerSourceFilter/);
    });
});
