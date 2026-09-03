const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const staticDir = path.join(__dirname, "..", "..", "main", "resources", "static");
const html = fs.readFileSync(path.join(staticDir, "index.html"), "utf-8");
const css = fs.readFileSync(path.join(staticDir, "styles.css"), "utf-8");
const app = fs.readFileSync(path.join(staticDir, "app.js"), "utf-8");
const planDoc = fs.readFileSync(
    path.join(__dirname, "..", "..", "..", "docs", "plans", "2026-09-02", "06-prompt-console.md"),
    "utf-8"
);

// 逐字 CSS 契约：直接从计划文件抽取 `## 样式契约` 下的 4 个 css 栅格（S-1..S-4）。
const styleSection = planDoc.split("## 样式契约")[1].split("## 实现方案")[0];
const CSS_BLOCKS = [...styleSection.matchAll(/```css\n([\s\S]*?)```/g)].map((m) => m[1].replace(/\n$/, ""));

function extractFunction(name) {
    const start = app.indexOf(`function ${name}(`);
    const asyncStart = app.indexOf(`async function ${name}(`);
    const index = asyncStart >= 0 && (start < 0 || asyncStart < start) ? asyncStart : start;
    if (index < 0) throw new Error("missing " + name);
    let depth = 0;
    let opened = false;
    for (let cursor = index; cursor < app.length; cursor += 1) {
        if (app[cursor] === "{") { depth += 1; opened = true; }
        if (app[cursor] === "}") {
            depth -= 1;
            if (opened && depth === 0) return app.slice(index, cursor + 1);
        }
    }
    throw new Error("unterminated " + name);
}

function makePanels() {
    return {
        ragPromptRetrievalRules: { innerHTML: "" },
        ragPromptGenerationRules: { innerHTML: "" },
        ragPromptRetrievalCount: { textContent: "" },
        ragPromptGenerationCount: { textContent: "" },
        ragPromptSaveBtn: { disabled: true },
        ragPromptSaveBarStatus: {
            textContent: "",
            classList: {
                dirty: false,
                toggle(name, on) { if (name === "dirty") this.dirty = Boolean(on); }
            }
        }
    };
}

function makeSandbox(panels) {
    const sandbox = {
        state: { aiTraining: {
            ragPromptIsCustom: false,
            ragPromptRetrieval: [],
            ragPromptGenerationBase: [],
            ragPromptGenerationDerived: [],
            ragPromptDeleted: 0
        } },
        escapeHtml: (value) => String(value ?? "")
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#039;"),
        $: (selector) => {
            const key = String(selector).replace("#", "");
            return panels[key] || { innerHTML: "", textContent: "", classList: { dirty: false, toggle() {} } };
        }
    };
    vm.createContext(sandbox);
    return sandbox;
}

function runRendererFns(sandbox) {
    ["ragPromptRows", "ragPromptBaseIndexOf", "ragPromptRowList", "ragPromptRowHtml",
        "ragPromptDirtyCount", "ragPromptUndoRow", "ragPromptDeleteRow",
        "renderRagPromptRules", "markRagPromptDirty"].forEach((name) => {
        vm.runInContext(extractFunction(name), sandbox);
    });
}

function defaultGenBase() {
    return Array.from({ length: 19 }, (_, i) => ({
        text: "gen rule " + (i + 1),
        base: "gen rule " + (i + 1),
        derived: false,
        changed: i === 11,
        added: i === 18,
        isNew: false
    }));
}

function defaultDerived() {
    return [
        { text: "DERIVED detail token rule", base: "DERIVED detail token rule", derived: true, changed: false, added: false, isNew: false },
        { text: "DERIVED evidence token rule", base: "DERIVED evidence token rule", derived: true, changed: false, added: false, isNew: false },
        { text: "DERIVED ip token rule", base: "DERIVED ip token rule", derived: true, changed: false, added: false, isNew: false }
    ];
}

function splitRows(markup) {
    const parts = markup.split(/(?=<div class="rag-prompt-rule(?: readonly)?" data-rag-prompt-call=)/);
    return parts.filter((part) => part.startsWith('<div class="rag-prompt-rule'));
}

describe("AI 提示词与约束页 (plan 06)", () => {
    it("G-8 + DOM 骨架：渲染用 id 全部真实存在于 index.html；旧表单两 textarea 原样保留（S-5/D-14）", () => {
        ["ragPromptRetrieval", "ragPromptGeneration", "ragPromptSaveBar",
            "ragPromptRetrievalRules", "ragPromptGenerationRules",
            "ragPromptRetrievalCount", "ragPromptGenerationCount",
            "ragPromptSaveBarStatus", "ragPromptSaveBtn", "ragPromptResetBtn"].forEach((id) => {
            assert.ok(html.includes(`id="${id}"`), `missing ${id} in index.html`);
        });
        assert.ok(html.includes('<section class="panel ai-training-panel rag-prompt-card" id="ragPromptRetrieval">'));
        assert.ok(html.includes('<section class="panel ai-training-panel rag-prompt-card" id="ragPromptGeneration">'));
        assert.ok(html.includes('<div class="rag-prompt-savebar" id="ragPromptSaveBar">'));
        // S-5/D-14: 旧自由回复表单只改标题文案 + 追加一行 .muted；结构原样。
        const freeFormCount = html.split('id="aiTrainingFreeFormPrompt"').length - 1;
        const constraintsCount = html.split('id="aiTrainingConstraints"').length - 1;
        assert.strictEqual(freeFormCount, 1, "aiTrainingFreeFormPrompt must appear exactly once");
        assert.strictEqual(constraintsCount, 1, "aiTrainingConstraints must appear exactly once");
        assert.ok(html.includes('<textarea id="aiTrainingFreeFormPrompt" rows="12">'));
        assert.ok(html.includes('<textarea id="aiTrainingConstraints" rows="12" placeholder="例如：Do not promise fees&#10;Reply in English when inbound is English">'));
        assert.ok(html.includes("自由回复提示词（旧链路 · 兜底路径）"));
        assert.ok(html.includes("本节只作用于未走 RAG 的兜底回复；RAG 链路的约束见上方两张清单"));
    });

    it("T4：loadAiTraining 追加 loadRagPromptConfig；五个新函数就位", () => {
        const mainLoad = extractFunction("loadAiTraining");
        assert.ok(mainLoad.includes("loadRagPromptConfig()"), "main loader must call loadRagPromptConfig");
        ["loadRagPromptConfig", "renderRagPromptRules", "markRagPromptDirty",
            "saveRagPromptConfig", "resetRagPromptConfig"].forEach((name) => {
            assert.ok(app.includes(`function ${name}(`) || app.includes(`async function ${name}(`), `missing ${name}`);
        });
    });

    it("I-31：默认 22 行渲染，第 18/19/21 行 readonly、无 contenteditable、无操作按钮", () => {
        const panels = makePanels();
        const sandbox = makeSandbox(panels);
        sandbox.state.aiTraining.ragPromptRetrieval = Array.from({ length: 5 }, (_, i) => ({
            text: "ret rule " + (i + 1), base: "ret rule " + (i + 1), derived: false, changed: false, added: false, isNew: false
        }));
        sandbox.state.aiTraining.ragPromptGenerationBase = defaultGenBase();
        sandbox.state.aiTraining.ragPromptGenerationDerived = defaultDerived();
        runRendererFns(sandbox);
        sandbox.renderRagPromptRules("retrieval");
        sandbox.renderRagPromptRules("generation");
        sandbox.markRagPromptDirty();

        assert.strictEqual(panels.ragPromptRetrievalCount.textContent, "5 条");
        assert.strictEqual(panels.ragPromptGenerationCount.textContent, "22 条");
        const rows = splitRows(panels.ragPromptGenerationRules.innerHTML);
        assert.strictEqual(rows.length, 22, "generation must render 22 rows");
        // 第 18/19/21 行（merged 0-based 17/18/20）只读。
        [17, 18, 20].forEach((index) => {
            assert.ok(rows[index].includes('class="rag-prompt-rule readonly"'), `row ${index + 1} must be readonly`);
            assert.ok(!rows[index].includes("contenteditable"), `row ${index + 1} must not be editable`);
            assert.ok(!rows[index].includes("data-rag-prompt-act"), `row ${index + 1} must have no actions`);
            assert.ok(rows[index].includes("派生 · 只读"), `row ${index + 1} must carry the derived badge`);
        });
        // 其余 19 行可编辑且带编号（I-32 编号是渲染产物）。
        const editable = rows.filter((row) => row.includes("contenteditable"));
        assert.strictEqual(editable.length, 19);
        assert.strictEqual((rows[17].match(/rag-prompt-rule-no">18\./g) || []).length, 1);
        assert.strictEqual((rows[18].match(/rag-prompt-rule-no">19\./g) || []).length, 1);
        assert.strictEqual((rows[20].match(/rag-prompt-rule-no">21\./g) || []).length, 1);
        // 默认干净态：第 12 条「本次改动」、第 22 条「新增」徽章（A-1）。
        assert.ok(rows[11].includes("rag-badge changed"), "rule 12 must show 本次改动");
        assert.strictEqual((panels.ragPromptGenerationRules.innerHTML.match(/本次改动/g) || []).length, 1);
        assert.ok(rows[21].includes("rag-badge added"), "rule 22 must show 新增");
        assert.strictEqual((panels.ragPromptGenerationRules.innerHTML.match(/新增/g) || []).length, 1);
        // 干净态：保存按钮禁用、状态未修改。
        assert.strictEqual(panels.ragPromptSaveBtn.disabled, true);
        assert.strictEqual(panels.ragPromptSaveBarStatus.textContent, "未修改");
    });

    it("markRagPromptDirty：改 1 处亮起保存栏与「已改」徽章，撤销后复原", () => {
        const panels = makePanels();
        const sandbox = makeSandbox(panels);
        sandbox.state.aiTraining.ragPromptGenerationBase = defaultGenBase();
        sandbox.state.aiTraining.ragPromptGenerationDerived = defaultDerived();
        runRendererFns(sandbox);
        sandbox.renderRagPromptRules("generation");
        sandbox.markRagPromptDirty();
        assert.strictEqual(panels.ragPromptSaveBtn.disabled, true);

        const edited = sandbox.state.aiTraining.ragPromptGenerationBase[5];
        edited.text = edited.base + " EXTRA";
        sandbox.renderRagPromptRules("generation");
        sandbox.markRagPromptDirty();

        assert.strictEqual(panels.ragPromptSaveBtn.disabled, false, "save must enable after one edit");
        assert.strictEqual(panels.ragPromptSaveBarStatus.textContent, "已修改 1 处 · 未保存");
        assert.strictEqual(panels.ragPromptSaveBarStatus.classList.dirty, true);
        const rows = splitRows(panels.ragPromptGenerationRules.innerHTML);
        assert.ok(rows[5].includes("rag-badge dirty"), "edited row must show 已改 badge");
        assert.ok(rows[5].includes(">撤销</button>"), "edited row must offer undo");

        sandbox.ragPromptUndoRow("generation", 5);
        assert.strictEqual(edited.text, edited.base, "undo must restore the base text");
        assert.strictEqual(panels.ragPromptSaveBtn.disabled, true, "save must disable after undo");
        assert.strictEqual(panels.ragPromptSaveBarStatus.textContent, "未修改");
        assert.strictEqual(panels.ragPromptSaveBarStatus.classList.dirty, false);
    });

    it("I-32：删除第 3 条后原第 4 条渲染编号变 3.，删除计入未保存状态", () => {
        const panels = makePanels();
        const sandbox = makeSandbox(panels);
        sandbox.state.aiTraining.ragPromptGenerationBase = defaultGenBase();
        sandbox.state.aiTraining.ragPromptGenerationDerived = defaultDerived();
        runRendererFns(sandbox);
        sandbox.renderRagPromptRules("generation");
        sandbox.markRagPromptDirty();

        sandbox.ragPromptDeleteRow("generation", 2); // 删第 3 条
        assert.strictEqual(sandbox.state.aiTraining.ragPromptGenerationBase.length, 18);
        const rows = splitRows(panels.ragPromptGenerationRules.innerHTML);
        assert.strictEqual(rows.length, 21, "deleting one rule must leave 21 rows");
        const follower = rows.find((row) => row.includes("gen rule 4"));
        assert.ok(follower, "old 4th rule must still exist");
        assert.ok(follower.includes('rag-prompt-rule-no">3.'), "old 4th rule must now render as 3.");
        assert.strictEqual(panels.ragPromptSaveBtn.disabled, false);
        assert.strictEqual(panels.ragPromptSaveBarStatus.textContent, "已修改 1 处 · 未保存");
    });

    it("S-1..S-4：styles.css 追加块与计划 css 栅格逐字一致（含全部新增 class）", () => {
        assert.strictEqual(CSS_BLOCKS.length, 4, "plan must carry exactly S-1..S-4 css fences");
        CSS_BLOCKS.forEach((block) => {
            assert.ok(css.includes(block), "S-block must be byte-identical in styles.css");
        });
        [".rag-prompt-card", ".rag-prompt-card-head", ".rag-prompt-callno", ".rag-prompt-count",
            ".rag-prompt-body", ".rag-prompt-add", ".rag-prompt-foot", ".rag-prompt-rule",
            ".rag-prompt-rule-no", ".rag-prompt-rule-text", ".rag-prompt-rule.readonly",
            ".rag-prompt-rule-actions", ".rag-prompt-savebar", ".rag-prompt-savebar-status",
            ".rag-badge.dirty", ".rag-badge.changed", ".rag-badge.added", ".rag-badge.readonly"].forEach((selector) => {
            assert.ok(css.includes(selector), `missing ${selector}`);
        });
        // S-3：只加 4 个变体，.rag-badge 基类（04 S-4 提供）不得被本计划重定义。
        assert.strictEqual((css.match(/\.rag-badge \{/g) || []).length, 1, "rag-badge base must not be redefined");
    });

    it("S-4：保存栏 background 字面不透明白 + 禁 var(--panel-bg)", () => {
        const start = css.indexOf(".rag-prompt-savebar {");
        assert.ok(start >= 0);
        const end = css.indexOf("}", start);
        const block = css.slice(start, end + 1);
        assert.ok(block.includes("rgba(255, 255, 255, .96)"), "savebar must use literal rgba white");
        assert.ok(block.includes("backdrop-filter: blur(8px)"), "savebar must blur backdrop");
        assert.ok(!block.includes("var(--panel-bg)"), "savebar must not use translucent panel token");
        // S-4 状态类单独成块（.dirty 变主色）。
        assert.ok(css.includes(".rag-prompt-savebar-status.dirty {\n    color: var(--primary);\n}"));
    });
});
