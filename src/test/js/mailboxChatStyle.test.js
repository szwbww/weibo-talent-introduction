"use strict";

// 子计划 10 样式测试（S-3 契约）：
// 1) 落地的 mailbox-chat.css 与计划 10 / ui-style-contract 的 S-3 CSS 代码块
//    逐字（字节）一致 —— 不增一行、不减一行（两个计划文档互为对照）。
// 2) DOM class 白名单：mailbox-chat.js 模板中的全部字面量 class 必须存在于
//    落地 mailbox-chat.css（mc-* 命名空间）或既有 styles.css（复用类）——
//    不允许新增未声明 class。
// 3) 模板中不存在 inline style；不存在裸 tab 页/其余全局规则修改。
//    （行为/层次断言见 mailboxChatBehavior.test.js。）

const fs = require("fs");
const path = require("path");
const assert = require("assert");
const { describe, it } = require("node:test");

const ROOT = path.join(__dirname, "..", "..", "main", "resources", "static");
const cssPath = path.join(ROOT, "mailbox-chat.css");
const cssSource = fs.readFileSync(cssPath, "utf-8");
const stylesSource = fs.readFileSync(path.join(ROOT, "styles.css"), "utf-8");
const chatSource = fs.readFileSync(path.join(ROOT, "mailbox-chat.js"), "utf-8");

const PLAN_10 = path.join(__dirname, "..", "..", "..", "docs", "plans", "2026-09-07", "10-mailbox-chat-frontend.md");
const STYLE_CONTRACT = path.join(__dirname, "..", "..", "..", "docs", "plans", "2026-09-07", "ui-style-contract.md");

function extractCssBlock(planPath) {
    const text = fs.readFileSync(planPath, "utf-8");
    const start = text.indexOf("```css\n");
    assert.ok(start >= 0, `${planPath} must contain a css fenced block`);
    const after = text.slice(start + "```css\n".length);
    const end = after.indexOf("\n```");
    assert.ok(end > 0, `${planPath} css fence must close`);
    return after.slice(0, end) + "\n";
}

describe("S-3: 落地 CSS 与计划契约逐字一致", () => {
    it("mailbox-chat.css 与 10-mailbox-chat-frontend.md S-3 块字节一致", () => {
        const expected = extractCssBlock(PLAN_10);
        assert.strictEqual(cssSource, expected, "mailbox-chat.css 必须与计划 10 S-3 CSS 逐字一致");
    });

    it("mailbox-chat.css 与 ui-style-contract.md S-3 块字节一致（权威稿对照）", () => {
        const contract = fs.readFileSync(STYLE_CONTRACT, "utf-8");
        // ui-style-contract 含 S-1/S-2 合一块 + S-3 一块：取第二个 css 围栏即 S-3
        const blocks = [];
        let idx = 0;
        while (true) {
            const start = contract.indexOf("```css\n", idx);
            if (start === -1) break;
            const after = contract.slice(start + "```css\n".length);
            const end = after.indexOf("\n```");
            blocks.push(after.slice(0, end) + "\n");
            idx = start + 1;
        }
        assert.ok(blocks.length >= 2, "ui-style-contract must carry S-1..S-3 css blocks");
        const s3 = blocks[blocks.length - 1];
        assert.strictEqual(cssSource, s3, "ui-style-contract S-3 与落地 CSS 逐字一致");
    });
});

describe("S-3: DOM class 白名单与模板卫生", () => {
    function literalClasses(source) {
        const out = new Set();
        const re = /class="([^"]*)"/g;
        let match;
        while ((match = re.exec(source)) !== null) {
            String(match[1]).split(/\s+/).filter(Boolean).forEach((cls) => out.add(cls));
        }
        const singleRe = /class='([^']*)'/g;
        while ((match = singleRe.exec(source)) !== null) {
            String(match[1]).split(/\s+/).filter(Boolean).forEach((cls) => out.add(cls));
        }
        return out;
    }

    it("模板字面量 class 全部命中 S-3 CSS 或既有 styles.css", () => {
        const classes = literalClasses(chatSource);
        assert.ok(classes.size > 0, "must find template classes to audit");
        const unknown = [];
        classes.forEach((cls) => {
            // 动态拼接的复合类（含 ${} 或引号外的模板变量）不在字面量集；此处只审计字面量
            if (/\$/.test(cls)) return;
            if (cls.startsWith("mc-") || cls === "mail-chat") {
                if (!new RegExp(`\\.${cls}(?=[\\s,{.:\\[])`).test(cssSource)) unknown.push(`${cls} (${cls.startsWith("mc-") ? "mc-* 未在" : "mail-chat 未在"} mailbox-chat.css 声明)`);
                return;
            }
            if (!new RegExp(`\\.${cls}(?=[\\s,{.:\\[])`).test(stylesSource)) unknown.push(`${cls} (复用类未在 styles.css 声明)`);
        });
        assert.deepStrictEqual(unknown, [], "新增模板 class 必须已声明（S-3 / styles.css）");
    });

    it("模板不写 inline style", () => {
        assert.ok(!/"style="/.test(chatSource), "mailbox-chat.js 模板不得出现 style 属性");
        assert.ok(!/'style='/.test(chatSource), "mailbox-chat.js 模板不得出现 style 属性");
    });

    it("命名空间规则只作用 .mail-chat，不修改既有全局规则", () => {
        // 落地 CSS 全部规则必须挂在 .mail-chat 命名空间下（唯一例外：无）
        const lines = cssSource.split("\n").map((line) => line.trim()).filter(Boolean);
        for (const line of lines) {
            if (line.startsWith("/*") || line.startsWith("*") || line.startsWith("//")) continue;
            const selectorPart = line.slice(0, line.indexOf("{") === -1 ? line.length : line.indexOf("{"));
            if (!selectorPart) continue;
            if (selectorPart.startsWith("@media") || selectorPart.startsWith("@")) continue;
            assert.ok(selectorPart.includes(".mail-chat"),
                `rule must be namespaced under .mail-chat: ${selectorPart}`);
        }
    });
});
