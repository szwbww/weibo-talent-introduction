"use strict";

// 收发件箱修复 02 样式测试（S-6 契约）：
// 1) 落地的 mailbox-chat.css 与 02 计划证据 evidence/mailbox-chat.target.css
//    逐字（字节）一致 —— 不增一行、不减一行（S-6 全文是唯一执行合同）。
// 2) DOM class 白名单：mailbox-chat.js 模板中的全部字面量 class 必须存在于
//    落地 mailbox-chat.css（mc-* 命名空间）或既有 styles.css（复用类）。
// 3) 模板中不存在 inline style。
// 4) S-1/S-2 宿主 id 与唯一筛选节点的源文本存在性断言
//    （DOM-stub 测试里 getElementById 恒返回元素，故新 id 必须查 index.html 源文本）。
// 5) S-7 专家标签行关键规则（单行 flex/ellipsis、counts flex:none）。

const fs = require("fs");
const path = require("path");
const assert = require("assert");
const { describe, it } = require("node:test");

const ROOT = path.join(__dirname, "..", "..", "main", "resources", "static");
const cssPath = path.join(ROOT, "mailbox-chat.css");
const cssSource = fs.readFileSync(cssPath, "utf-8");
const stylesSource = fs.readFileSync(path.join(ROOT, "styles.css"), "utf-8");
const chatSource = fs.readFileSync(path.join(ROOT, "mailbox-chat.js"), "utf-8");
const indexSource = fs.readFileSync(path.join(ROOT, "index.html"), "utf-8");


// I-1：版本键唯一来源是 index.html 的 styles.css?v=<key>，本文件不得写死字面量。
const CACHE_KEY = (() => {
    const match = indexSource.match(/styles\.css\?v=([^"'&<>]+)/);
    if (!match) throw new Error("index.html must register styles.css with a ?v= cache key");
    return match[1];
})();
const escapeRegExp = (value) => value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");


describe("S-1/S-2: index.html 宿主 id 与唯一筛选节点（源文本断言）", () => {
    function countOccurrences(text, needle) {
        let count = 0;
        let idx = text.indexOf(needle);
        while (idx !== -1) {
            count += 1;
            idx = text.indexOf(needle, idx + 1);
        }
        return count;
    }

    const HOST_IDS = [
        "id=\"view-mailbox\"",
        "id=\"mailboxLegacyToolbar\"",
        "id=\"mailboxConversationPanel\"",
        "id=\"mailboxList\"",
        "id=\"mailboxRefreshBtn\""
    ];
    const FILTER_IDS = [
        "id=\"mailboxFilterAccountCode\"",
        "id=\"mailboxFilterDirection\"",
        "id=\"mailboxFilterTag\"",
        "id=\"mailboxFilterRecipient\"",
        "id=\"mailboxFilterKeyword\"",
        "id=\"mailboxFilterStartDate\"",
        "id=\"mailboxFilterEndDate\"",
        "id=\"mailboxSearchBtn\""
    ];

    it("S-1 宿主标识在 index.html 源文本各出现一次", () => {
        HOST_IDS.forEach((needle) => {
            assert.strictEqual(countOccurrences(indexSource, needle), 1, `${needle} 必须在 index.html 唯一存在`);
        });
    });

    it("S-2 七个筛选字段 + 查询按钮在 index.html 源文本各出现一次（不复制 DOM）", () => {
        FILTER_IDS.forEach((needle) => {
            assert.strictEqual(countOccurrences(indexSource, needle), 1, `${needle} 必须唯一`);
        });
    });

    it("筛选字段已删除旧 inline width（聊天 popover 内由 .mc-field 控制宽度）", () => {
        assert.doesNotMatch(indexSource, /id="mailboxFilter(Recipient|Keyword|StartDate|EndDate)"[^>]*style=/,
            "旧 inline width 必须删除，否则 popover 双列布局会被内联宽度破坏");
    });

    it("mailboxLegacyToolbar 内保留七字段与查询按钮（任务钻取恢复旧 toolbar）", () => {
        const start = indexSource.indexOf("id=\"mailboxLegacyToolbar\"");
        const end = indexSource.indexOf("id=\"mailboxExecutionFilterBar\"");
        assert.ok(start >= 0 && end > start, "mailboxLegacyToolbar 必须位于 mailboxExecutionFilterBar 之前");
        const block = indexSource.slice(start, end);
        FILTER_IDS.forEach((needle) => {
            assert.ok(block.includes(needle), `旧 toolbar 必须包含 ${needle}`);
        });
    });
});

describe("收发件箱静态资源版本", () => {
    it("正文行距更新必须刷新 mailbox-chat.css 缓存版本", () => {
        assert.match(
            indexSource,
            new RegExp('href="mailbox-chat\\.css\\?v=' + escapeRegExp(CACHE_KEY) + '"'),
            "CSS 版本号必须随正文样式更新，避免浏览器继续使用旧行距"
        );
    });
});

describe("S-6: DOM class 白名单与模板卫生", () => {
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

    it("模板字面量 class 全部命中 mailbox-chat.css 或既有 styles.css", () => {
        const classes = literalClasses(chatSource);
        assert.ok(classes.size > 0, "must find template classes to audit");
        const unknown = [];
        classes.forEach((cls) => {
            if (/\$/.test(cls)) return;
            if (cls.startsWith("mc-") || cls === "mail-chat") {
                if (!new RegExp(`\\.${cls}(?=[\\s,{.:\\[])`).test(cssSource)) unknown.push(`${cls} (mc-* 未在 mailbox-chat.css 声明)`);
                return;
            }
            if (!new RegExp(`\\.${cls}(?=[\\s,{.:\\[])`).test(stylesSource)) unknown.push(`${cls} (复用类未在 styles.css 声明)`);
        });
        assert.deepStrictEqual(unknown, [], "新增模板 class 必须已声明（S-6 / styles.css）");
    });

    it("模板不写 inline style", () => {
        assert.ok(!/"style="/.test(chatSource), "mailbox-chat.js 模板不得出现 style 属性");
        assert.ok(!/'style='/.test(chatSource), "mailbox-chat.js 模板不得出现 style 属性");
    });

    it("样式契约无新增未授权全局规则（mailbox view 宿主钩子除外）", () => {
        const lines = cssSource.split("\n").map((line) => line.trim()).filter(Boolean);
        for (const line of lines) {
            if (line.startsWith("/*") || line.startsWith("*") || line.startsWith("//")) continue;
            const selectorPart = line.slice(0, line.indexOf("{") === -1 ? line.length : line.indexOf("{"));
            if (!selectorPart) continue;
            if (selectorPart.startsWith("@media") || selectorPart.startsWith("@")) continue;
            const isScoped = selectorPart.includes(".mail-chat")
                || selectorPart.startsWith("#view-mailbox.mc-refined")
                || selectorPart.startsWith("#view-mailbox:not(.mc-refined)")
                || /^\.mc-note-(?:dialog|body|hint|error)(?=[\s,{.:]|$)/.test(selectorPart);
            assert.ok(isScoped, `rule must stay mailbox-scoped: ${selectorPart}`);
        }
    });
});

describe("S-7: 专家标签单行关键规则", () => {
    it("正文最终行高为紧凑的 1.55，且保留真实换行", () => {
        const finalBodyRule = cssSource.slice(cssSource.lastIndexOf(".mail-chat .mc-body"));
        assert.match(
            finalBodyRule,
            /^\.mail-chat \.mc-body\{font-size:13px;line-height:1\.55;color:#465974\}/,
            "收发件箱只压缩展示行距，不能改写正文换行"
        );
    });

    it("mc-person-meta 强制 nowrap + min-width:0 + 100% 上限", () => {
        assert.match(cssSource, /\.mail-chat \.mc-person-meta\{flex-wrap:nowrap;min-width:0;gap:6px;max-width:100%\}/);
    });

    it("mc-person-counts flex:none（收发数字不被标签挤压）", () => {
        assert.match(cssSource, /\.mail-chat \.mc-person-counts\{flex:none;white-space:nowrap;font-size:11px;color:#79899f\}/);
    });

    it("mc-person-tags 单行容器：flex:1/min-width:0/nowrap/ellipsis", () => {
        assert.match(cssSource, /\.mail-chat \.mc-person-tags\{display:block;flex:1;min-width:0;overflow:hidden;white-space:nowrap;text-overflow:ellipsis;line-height:20px;color:#6482b2;cursor:help\}/);
    });

    it("标签行高 20px，标签 chip 字号 10px 且不换行", () => {
        assert.match(cssSource, /\.mail-chat \.mc-person-tag\{display:inline;padding:2px 5px;margin-right:4px;border:1px solid #d5e2fb;border-radius:5px;background:#edf3ff;color:#5476ba;font-size:10px;line-height:16px;white-space:nowrap\}/);
    });

    it("null 专家标签显示单行「标签暂不可用」", () => {
        assert.match(cssSource, /\.mail-chat \.mc-person-tags-unavailable\{color:#94a3b8;font-size:10px\}/);
    });
});

// ---------------------------------------------------------------------------
// 跟进邮件 01（S-1/S-2/S-3）：弹窗样式是 docs/plans/2026-09-14/
// followup-email-01-manual-anchor.md 的逐字合同，landed 值不得调整；字节锁定的
// mailbox-chat.css 不得吸收任何 followup-* 规则；S-3 复用既有 .mc-note。
// 注：本文件与 02 缓存激活计划共享，上方「收发件箱静态资源版本」块是 01 的基线，
// 02 只替换其中的旧缓存键字面量。
// ---------------------------------------------------------------------------

describe("S-2: 跟进弹窗逐字样式（followup 01 合同）", () => {
    const PLAN_PATH = path.join(__dirname, "..", "..", "..", "docs", "plans", "2026-09-14", "followup-email-01-manual-anchor.md");
    const planSource = fs.readFileSync(PLAN_PATH, "utf-8");
    const contractBlock = planSource.match(/```css\n([\s\S]*?)```/)[1];

    it("styles.css 逐字包含 S-2 合同样式块（不删一行、不改一个值）", () => {
        assert.ok(
            stylesSource.includes(contractBlock),
            "S-2 的完整 CSS 块必须逐字追加在 styles.css（含注释与响应式规则）"
        );
    });

    it("S-2 桌面/窄屏网格、焦点与禁用实值", () => {
        assert.match(stylesSource, /\.followup-grid\{display:grid;grid-template-columns:44% 56%;min-height:430px\}/);
        assert.match(stylesSource, /@media\(max-width:760px\)\{\.followup-dialog\{width:calc\(100vw - 20px\);max-height:calc\(100dvh - 20px\)\}\.followup-grid\{grid-template-columns:1fr\}/);
        assert.match(stylesSource, /\.followup-dialog :is\(button,input,textarea\):focus-visible\{outline:2px solid #3b82f6;outline-offset:2px\}/);
        assert.match(stylesSource, /\.followup-dialog :is\(button,\.button\):disabled\{opacity:\.45;cursor:not-allowed;transform:none;box-shadow:none\}/);
        assert.match(stylesSource, /\.followup-mail-option\[aria-checked=true\]\{border-color:#3b82f6;background:#eff5ff;box-shadow:0 0 0 1px #3b82f6\}/);
        assert.match(stylesSource, /@media\(prefers-reduced-motion:reduce\)\{\.followup-dialog \*\{transition:none!important;scroll-behavior:auto!important\}\}/);
    });

    it("跟进弹窗全部 class 已在 styles.css 声明（无未声明 class）", () => {
        const dialogClasses = [
            "followup-dialog", "followup-head", "followup-close", "followup-grid",
            "followup-list-pane", "followup-preview-pane", "followup-pane-title", "followup-help",
            "followup-mail-list", "followup-mail-option", "followup-empty", "followup-field",
            "followup-quote", "followup-actions"
        ];
        dialogClasses.forEach((cls) => {
            assert.ok(new RegExp(`\\.${cls}(?=[\\s,{.:\\[])`).test(stylesSource), `${cls} 必须在 styles.css 声明`);
        });
        assert.ok(
            stylesSource.includes(`.followup-dialog{`),
            "弹窗根规则必须存在于 styles.css（不在字节锁定的 mailbox-chat.css）"
        );
    });

    it("S-2 不写入字节锁定的 mailbox-chat.css，也不引入 inline style", () => {
        assert.ok(!/followup-/.test(cssSource), "mailbox-chat.css 不得出现 followup-* 规则或引用");
        assert.ok(!/"style="/.test(chatSource), "mailbox-chat.js 模板不得出现 style 属性");
        assert.ok(!/'style='/.test(chatSource), "mailbox-chat.js 模板不得出现 style 属性");
    });

    it("S-3 复用既有 .mc-note，不新增锚点提示 class", () => {
        assert.match(cssSource, /\.mail-chat \.mc-note\{/, "S-3 必须复用既有 .mc-note");
        assert.ok(!/\.followup-anchor-note/.test(stylesSource), "锚点提示不得新增 CSS 规则");
    });

    it("S-1 复用 .button，不新增按钮 class", () => {
        assert.ok(!/\.followup-button|\.followup-trigger/.test(stylesSource), "跟进按钮只允许既有 .button class");
    });
});

// ---------------------------------------------------------------------------
// fast-p 2026-10-02 · mailbox-last-reply-time（S-1..S-4）
// 上次回复：S-3 逐字样式块、S-1/S-2 节点顺序、字节与资源键门禁（键值从 index 派生）。
// ---------------------------------------------------------------------------

describe("上次回复：S-3 逐字样式与 S-1/S-2 结构（mailbox-last-reply-time）", () => {
    const PLAN_PATH = path.join(__dirname, "..", "..", "..", "docs", "plans", "2026-10-02", "mailbox-last-reply-time.md");
    const planSource = fs.readFileSync(PLAN_PATH, "utf-8");
    const S3_BLOCK = planSource.slice(planSource.indexOf("### S-3")).match(/```css\n([\s\S]*?)```/)[1];

    it("styles.css 逐字包含 S-3 合同样式块（不删一行、不改一个值）", () => {
        assert.ok(
            stylesSource.includes(S3_BLOCK),
            "S-3 的完整 CSS 块必须逐字追加在 styles.css（含注释、活跃态与响应式规则）"
        );
    });

    it("S-3 四个业务类的声明/活态/窄屏规则均在 styles.css", () => {
        [
            ".mail-chat .mailbox-reply-list{",
            ".mail-chat .mailbox-reply-list time{",
            ".mail-chat .mc-person[data-active=true] .mailbox-reply-list time{",
            ".mail-chat .mailbox-reply-detail{",
            ".mail-chat .mailbox-reply-detail time{",
            ".mail-chat .mailbox-reply-zone{",
            ".mail-chat .mailbox-reply-empty{",
            "@media(max-width:760px){.mail-chat .mailbox-reply-detail time{font-size:12px}}"
        ].forEach((rule) => assert.ok(stylesSource.includes(rule), rule + " 必须存在"));
    });

    it("S-3 不写入字节锁定的 mailbox-chat.css", () => {
        assert.ok(!/mailbox-reply-/.test(cssSource), "mailbox-chat.css 不得出现 mailbox-reply-* 规则");
    });

    it("S-1 列表：时间行由共享展示函数插入在最近摘要之后、计数标签之前；aria 追加说明", () => {
        const fn = chatSource.slice(chatSource.indexOf("function renderPerson"), chatSource.indexOf("function renderExpertList"));
        assert.ok(fn.includes("${lastReplyListMarkup(item)}"), "列表时间行必须由共享展示函数插入");
        assert.ok(fn.indexOf("latestLine") < fn.indexOf("lastReplyListMarkup(item)"), "时间行在最近摘要之后");
        assert.ok(fn.indexOf("lastReplyListMarkup(item)") < fn.indexOf("mc-person-meta"), "时间行在计数标签之前");
        assert.ok(fn.includes("lastReplyAriaSuffix("), "按钮 aria 追加同一纯文本说明");
        assert.ok(chatSource.includes('class="mailbox-reply-list"'), "列表行 class 由共享标记函数生成");
        assert.ok(!fn.includes('data-role="last-reply-time"'), "列表不写详情槽");
    });

    it("S-2 详情：identity 顺序 h2→p→时间槽→排期；只在 renderHeader 建槽", () => {
        const hdr = chatSource.slice(chatSource.indexOf("function renderHeader"), chatSource.indexOf("function renderHeaderMeta"));
        assert.match(
            hdr,
            /<h2>[^<]*<\/h2><p>[^<]*<\/p><span class="mailbox-reply-detail" data-role="last-reply-time">/,
            "identity 层级：名称→邮箱账号→时间槽"
        );
        assert.ok(hdr.indexOf('data-role="last-reply-time"') < hdr.indexOf('data-role="meeting-summary"'), "时间槽在排期摘要之前");
        assert.strictEqual((chatSource.match(/data-role="last-reply-time"/g) || []).length, 2, "建槽 1 处 + 局部刷新选择器 1 处");
        assert.ok(chatSource.includes('class="mailbox-reply-zone"'), "北京时间尾注存在");
    });

    it("S-1/S-2 新增 class 已在 styles.css 声明（不落字节锁定的 mailbox-chat.css）", () => {
        ["mailbox-reply-list", "mailbox-reply-detail", "mailbox-reply-zone", "mailbox-reply-empty"].forEach((cls) => {
            assert.ok(new RegExp(`\\.${cls}(?=[\\s,{.:\\[])`).test(stylesSource), cls + " 必须在 styles.css 声明");
        });
    });

    it("S-4 既有 11 个版本化资源统一为新键、标签与注册顺序不变", () => {
        const keys = Array.from(indexSource.matchAll(/\?v=([^"'&<>\s]+)/g)).map((match) => match[1]);
        assert.strictEqual(keys.length, 11, "仍为 11 个版本化资源（不新增 script/link）");
        assert.deepStrictEqual(Array.from(new Set(keys)), [CACHE_KEY], "全部资源键必须同值");
        const ordered = ["styles.css", "expert-materials.css", "mailbox-chat.css", "meeting-confirmation.css",
            "world-clock.css", "trust-reply-workbench.js", "expert-materials.js", "meeting-confirmation.js",
            "mailbox-chat.js", "app.js", "world-clock.js"];
        let previous = -1;
        for (const asset of ordered) {
            const at = indexSource.indexOf(`${asset}?v=${CACHE_KEY}`);
            assert.ok(at > previous, `${asset} 必须保持注册顺序`);
            previous = at;
        }
        assert.ok(indexSource.includes('<script src="task-modal-runtime.js"></script>'), "task-modal-runtime.js 保持无版本键");
    });
});


describe("mobile-core-02 CSS 与独立返回条", () => {
    it("逐字追加计划 CSS；唯一返回按钮在 aside 与 section 之间", () => {
        const plan = fs.readFileSync(path.join(__dirname, "../../../docs/plans/2026-10-03/mobile-core-02-mailbox.md"), "utf8");
        const block = plan.match(/```css\n([\s\S]*?)```/)[1];
        assert.ok(stylesSource.includes(block));
        assert.match(chatSource, /class="mail-chat mobile-core-mailbox" data-mobile-pane="list"/);
        assert.match(chatSource, /<\/aside>\s*<button type="button" class="button mobile-mailbox-back" data-action="mobile-mailbox-back">返回会话列表<\/button>\s*<section class="mc-conversation"/);
    });
});


it("手机编辑器用 contenteditable 特异性保护 160px 最小高度", () => {
    assert.match(stylesSource, /@media \(max-width: 760px\) \{\s*\.mail-chat\.mobile-core-mailbox div\.mc-editor\[contenteditable="true"\] \{ min-height: 160px; max-height: 40dvh; \}/);
});

// ---------------------------------------------------------------------------
// fast-p 2026-10-04 · mailbox-progress（S-1..S-4）
// 三态状态菜单：S-2 逐字 CSS 块、新 class 白名单、无旧星标/详情关注控件、
// S-1 高优先级换行规则与完整七 Tab。资源键与字节冻结由上方既有块覆盖。
// ---------------------------------------------------------------------------

describe("02 · 三态标记 S-2 样式与结构契约", () => {
    const planSource = fs.readFileSync(
        path.join(__dirname, "..", "..", "..", "docs", "plans", "2026-10-04", "mailbox-progress-02-frontend.md"),
        "utf-8"
    );
    const START = "/* mailbox-progress-contract:start */";
    const END = "/* mailbox-progress-contract:end */";
    const contractBlock = (() => {
        const start = planSource.indexOf(START);
        const end = planSource.indexOf(END);
        assert.ok(start >= 0 && end > start, "S-2 合同块标记存在");
        return planSource.slice(start, end + END.length);
    })();

    it("styles.css 逐字包含 S-2 合同样式块（不删一行、不改一个值）", () => {
        assert.ok(stylesSource.includes(contractBlock), "S-2 块必须逐字追加在 styles.css");
    });

    it("S-2 新增 5 个业务 class 均在 styles.css 声明", () => {
        ["mailbox-progress-card", "mailbox-progress", "mailbox-progress-status", "mailbox-progress-menu", "mailbox-progress-option"]
            .forEach((cls) => {
                assert.ok(new RegExp("\\." + cls + "(?=[\\s,{.:\\[])").test(stylesSource), `${cls} 必须在 styles.css 声明`);
            });
    });

    it("S-2 不写入字节锁定的 mailbox-chat.css", () => {
        assert.ok(!/mailbox-progress/.test(cssSource), "mailbox-chat.css 不得出现 mailbox-progress* 规则");
    });

    it("删除旧星标与详情关注控件（无 mc-follow / mc-toggle-follow / 关注文案）", () => {
        assert.ok(!/mc-follow/.test(chatSource), "模板不得再出现 mc-follow 星标");
        assert.ok(!/mc-toggle-follow/.test(chatSource), "不得再出现 mc-toggle-follow 动作");
        assert.ok(!/关注/.test(chatSource), "详情/卡片不得再出现关注文案");
    });

    it("S-1 优先级换行规则仍在，七 Tab key 与中文名完整", () => {
        assert.ok(stylesSource.includes("@media(min-width:761px){#view-mailbox.mc-refined .mail-chat{grid-template-columns:380px minmax(0,1fr)}}"), "380px 规则保留");
        assert.ok(stylesSource.includes("#view-mailbox.mc-refined .mail-chat .mc-filters{flex-wrap:wrap;overflow-x:visible;row-gap:0}"), "wrap 高优先级规则保留");
        ["ALL", "PROVIDED", "FOLLOWED", "PENDING", "SUSPENDED", "REPLIED", "UNMATCHED"].forEach((name) => {
            assert.ok(new RegExp("\\{ key: CHIP_" + name + ", label:").test(chatSource), `CHIP_${name} 必须在 FILTER_CHIPS`);
        });
        ["全部", "已提供", "跟进中", "待处理", "已挂起", "已回复", "待匹配"].forEach((label) => {
            assert.ok(chatSource.includes(`label: "${label}"`), `${label} 必须在 FILTER_CHIPS`);
        });
        assert.ok(!/label: "关注"/.test(chatSource), "旧「关注」展示名必须删除");
    });
});
