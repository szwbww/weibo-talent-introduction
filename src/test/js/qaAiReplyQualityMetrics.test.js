"use strict";

const { describe, it } = require("node:test");
const fs = require("fs");
const vm = require("vm");
const path = require("path");
const assert = require("assert");

const appJsPath = path.join(__dirname, "..", "..", "main", "resources", "static", "app.js");
const app = fs.readFileSync(appJsPath, "utf-8");
const qualityMetricsBlock = app.match(/const renderQualityMetrics = \(qm\) => \{[\s\S]*?\n    \};/)?.[0] || "";

function extractFn(name) {
    const regex = new RegExp("(?:async\\s+)?function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}");
    const match = app.match(regex);
    if (!match) throw new Error("Could not find " + name + " in app.js");
    return match[0];
}

function runInSandbox(fnBody, context) {
    const sandbox = { ...context };
    vm.createContext(sandbox);
    vm.runInContext(fnBody, sandbox);
    return sandbox;
}

describe("Phase 10 AI reply quality metrics", function () {

    // -- T3: renderQualityMetrics presence --
    it("has renderQualityMetrics function in app.js", function () {
        if (!app.includes("const renderQualityMetrics =")) {
            throw new Error("missing renderQualityMetrics");
        }
    });

    it("has AI reply quality section label", function () {
        if (!app.includes("AI 回复质量指标")) {
            throw new Error("missing AI reply quality section label");
        }
    });

    it("has only initial-draft quality metric cards", function () {
        if (!app.includes("AI 初稿总数")) throw new Error("missing 初稿总数 card");
        if (!app.includes("完整率 (READY)")) throw new Error("missing 完整率 card");
        if (!app.includes("部分覆盖率 (NEEDS_REVIEW)")) throw new Error("missing 部分覆盖率 card");
        if (!app.includes("遗漏率 (BLOCKED)")) throw new Error("missing 遗漏率 card");
        if (qualityMetricsBlock.includes("直发拦截")) throw new Error("deprecated 直发拦截 card should be absent");
        if (qualityMetricsBlock.includes("人工确认")) throw new Error("deprecated 人工确认 card should be absent");
    });

    it("renders 无数据 when aiReplyQuality is null/missing", function () {
        if (!app.includes("AI 回复质量指标：无数据")) {
            throw new Error("missing null qi fallback message");
        }
    });

    it("uses formatPercent for rate values", function () {
        if (!app.includes("formatPercent(qm.readyRate)")) throw new Error("missing formatPercent for readyRate");
        if (!app.includes("formatPercent(qm.partialRate)")) throw new Error("missing formatPercent for partialRate");
        if (!app.includes("formatPercent(qm.omissionRate)")) throw new Error("missing formatPercent for omissionRate");
    });

    it("uses safeNum fallback for count values", function () {
        if (!app.includes("safeNum =")) throw new Error("missing safeNum helper");
    });

    it("renderQualityMetrics called in renderQaAuditPanel template", function () {
        if (!app.includes("renderQualityMetrics(report.aiReplyQuality)")) {
            throw new Error("missing renderQualityMetrics call in template");
        }
    });

    // -- formatPercent edge cases --
    it("formatPercent returns 0% for null", function () {
        const ctx = runInSandbox(extractFn("formatPercent"), {});
        assert.strictEqual(ctx.formatPercent(null), "0%");
    });

    it("formatPercent returns 0% for NaN", function () {
        const ctx = runInSandbox(extractFn("formatPercent"), {});
        assert.strictEqual(ctx.formatPercent(NaN), "0%");
    });

    it("formatPercent returns 0.0% for zero", function () {
        const ctx = runInSandbox(extractFn("formatPercent"), {});
        assert.strictEqual(ctx.formatPercent(0), "0.0%");
    });

    it("formatPercent returns 50.0% for 0.5", function () {
        const ctx = runInSandbox(extractFn("formatPercent"), {});
        assert.strictEqual(ctx.formatPercent(0.5), "50.0%");
    });

    it("formatPercent returns 33.3% for 0.333", function () {
        const ctx = runInSandbox(extractFn("formatPercent"), {});
        assert.strictEqual(ctx.formatPercent(0.333), "33.3%");
    });

    it("formatPercent returns 100.0% for 1.0", function () {
        const ctx = runInSandbox(extractFn("formatPercent"), {});
        assert.strictEqual(ctx.formatPercent(1.0), "100.0%");
    });

    // -- zero denominator compatibility --
    it("safeNum falls back to 0 for null and undefined", function () {
        if (!app.includes("(v != null ? v : 0)")) {
            throw new Error("missing null coalesce for safeNum");
        }
    });

    // -- metadata grid reuse --
    it("reuses metadata-grid class for quality cards", function () {
        if (!app.includes('<div class="metadata-grid">')) {
            throw new Error("missing metadata-grid in quality section");
        }
    });

});
