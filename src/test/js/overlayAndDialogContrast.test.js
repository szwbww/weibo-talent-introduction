"use strict";

// 计划 05（c6）改写 —— 逐条保留仍有效的 CSS 契约（G-7 / controller ruling）：
// 旧 I-1..I-6（renderMarkup/renderBusyOverlay/busyOverlayState 等旧工作台内部函数切片）
// 与整段「rendered behavior」随旧版按条目工作台一并退役；本文件只保留：
// I-7（.action-dialog 与 .trust-reply-busy-* 不透明白底 + dark-mode 配对）、
// I-8（G-5 缓存键三联）、S-1（.reply-workflow-content 逐字块）、
// S-2（busy 四规则逐字 + spinner/keyframes 唯一）、S-3（dialog-body 对比度作用域）。
// 被断言 CSS 块均不在 05 的 S-5 处置表内 → 保持逐字不动。

const fs = require("fs");
const path = require("path");
const assert = require("assert");
const { describe, it } = require("node:test");

const root = path.join(__dirname, "..", "..", "main", "resources", "static");
const stylesPath = path.join(root, "styles.css");
const indexPath = path.join(root, "index.html");
const styles = fs.readFileSync(stylesPath, "utf-8");
const html = fs.readFileSync(indexPath, "utf-8");

const CACHE_KEY = "20260903-bounce-warning";

function stripWs(text) {
    return text.replace(/\s+/g, " ").trim();
}

function ruleBlock(css, selector) {
    const start = css.indexOf(`${selector} {`);
    assert.ok(start >= 0, `missing rule ${selector}`);
    const end = css.indexOf("\n}", start);
    assert.ok(end > start, `unterminated rule ${selector}`);
    return css.slice(start, end + 2);
}

describe("P2 overlay + dialog contrast (source contract)", () => {
    it("I-7: .action-dialog and the overlay use opaque backgrounds with dark-mode pairs", () => {
        const dialog = ruleBlock(styles, ".action-dialog");
        assert.ok(!stripWs(dialog).includes("var(--panel-bg)"),
            "action-dialog must not use the translucent --panel-bg");
        assert.ok(stripWs(dialog).includes("background: rgba(255, 255, 255, 0.97); backdrop-filter: blur(8px); -webkit-backdrop-filter: blur(8px);"),
            "action-dialog must be opaque with backdrop blur");
        const overlay = ruleBlock(styles, ".trust-reply-busy-overlay");
        assert.ok(stripWs(overlay).includes("background: rgba(255, 255, 255, 0.92);"), "overlay must be near-opaque white");
        const card = ruleBlock(styles, ".trust-reply-busy-card");
        assert.ok(stripWs(card).includes("background: rgba(255, 255, 255, 0.98);"), "card must be near-opaque white");
        const dark = styles.slice(styles.indexOf("@media (prefers-color-scheme: dark)"));
        for (const selector of [".trust-reply-busy-overlay", ".trust-reply-busy-card", ".action-dialog"]) {
            assert.ok(dark.includes(`${selector} {`), `dark media block must override ${selector}`);
        }
        assert.ok(dark.includes("background: rgba(13, 20, 32, 0.92);"), "dark overlay override");
        assert.ok(dark.includes("background: rgba(21, 31, 48, 0.98);")
            && dark.includes("border-color: rgba(148, 163, 184, 0.22);"), "dark card override");
        assert.ok(dark.includes("background: rgba(21, 31, 48, 0.97);"), "dark action-dialog override");
    });

    it("I-8: the cache-key triad is one value, three sites", () => {
        const keys = (html.match(/\?v=[^"]+/g) || []).map((k) => k.slice(3));
        assert.strictEqual(keys.length, 3, "index.html must carry exactly three cache-busted asset URLs");
        assert.strictEqual(new Set(keys).size, 1, "all three keys must share one value");
        assert.strictEqual(keys[0], CACHE_KEY, "the shared key must be " + CACHE_KEY);
    });

    it("S-1: .trust-reply-workbench .reply-workflow-content block matches the contract verbatim", () => {
        assert.strictEqual((styles.match(/\.trust-reply-workbench \.reply-workflow-content/g) || []).length, 1,
            "the selector must stay unique in styles.css");
        const rule = ruleBlock(styles, ".trust-reply-workbench .reply-workflow-content");
        assert.strictEqual(stripWs(rule),
            ".trust-reply-workbench .reply-workflow-content { position: relative; display: flex; flex-direction: column; gap: 12px; padding: 14px; }");
    });

    it("S-2: the four overlay rules are verbatim and no spinner/keyframe is duplicated", () => {
        const overlay = ruleBlock(styles, ".trust-reply-busy-overlay");
        assert.strictEqual(stripWs(overlay),
            ".trust-reply-busy-overlay { position: absolute; inset: 0; z-index: 6; display: flex; align-items: flex-start; justify-content: center; border-radius: var(--radius-md); background: rgba(255, 255, 255, 0.92); backdrop-filter: blur(3px); -webkit-backdrop-filter: blur(3px); }");
        const card = ruleBlock(styles, ".trust-reply-busy-card");
        assert.strictEqual(stripWs(card),
            ".trust-reply-busy-card { position: sticky; top: 96px; display: flex; flex-direction: column; align-items: center; gap: 10px; max-width: 420px; margin: 48px 16px; padding: 18px 22px; border: 1px solid var(--panel-border); border-radius: var(--radius-md); background: rgba(255, 255, 255, 0.98); box-shadow: var(--shadow-lg); text-align: center; }");
        const text = ruleBlock(styles, ".trust-reply-busy-text");
        assert.strictEqual(stripWs(text),
            ".trust-reply-busy-text { color: var(--text-main); font-size: 13px; font-weight: 600; line-height: 1.6; }");
        const hint = ruleBlock(styles, ".trust-reply-busy-hint");
        assert.strictEqual(stripWs(hint),
            ".trust-reply-busy-hint { color: var(--text-secondary); font-size: 12px; font-weight: 500; line-height: 1.6; }");
        assert.strictEqual((styles.match(/ai-reply-loading-spinner/g) || []).length, 1,
            "the spinner style must be reused, not duplicated");
        assert.strictEqual((styles.match(/@keyframes ai-reply-spin/g) || []).length, 1,
            "the keyframes must stay unique");
        const loadingOverlay = ruleBlock(styles, ".ai-reply-loading-overlay");
        assert.ok(stripWs(loadingOverlay).includes("background: rgba(255, 255, 255, 0.84);")
            && stripWs(loadingOverlay).includes("backdrop-filter: blur(2px);"),
            "the mailbox .ai-reply-loading-overlay must stay untouched");
    });

    it("S-3: dialog body paragraphs get scoped contrast overrides; global p untouched", () => {
        const bodyP = ruleBlock(styles, ".action-dialog-body p");
        assert.strictEqual(stripWs(bodyP),
            ".action-dialog-body p { color: var(--text-main); font-size: 13px; line-height: 1.6; }");
        const coverage = ruleBlock(styles, ".action-dialog-body .ai-reply-coverage");
        assert.strictEqual(stripWs(coverage),
            ".action-dialog-body .ai-reply-coverage { color: var(--text-secondary); }");
        const globalP = styles.match(/^p\s*\{[^}]*\}/m)[0];
        assert.strictEqual(stripWs(globalP), "p { font-size: 12px; color: var(--text-muted); margin-top: 2px; }",
            "the global p rule must stay verbatim");
        const backdrop = ruleBlock(styles, ".action-dialog::backdrop");
        assert.ok(stripWs(backdrop).includes("rgba(15, 23, 42, 0.5)") && stripWs(backdrop).includes("blur(6px)"),
            ".action-dialog::backdrop must stay untouched");
        const h3 = ruleBlock(styles, ".action-dialog h3");
        assert.ok(stripWs(h3).includes("font-size: 15px;"), ".action-dialog h3 must stay untouched");
    });
});
