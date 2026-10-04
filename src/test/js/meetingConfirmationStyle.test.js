"use strict";

// fast-p 04 样式契约测试（S-1/I-6）：
// 1) 落地 meeting-confirmation.css 与 evidence/meeting-confirmation.target.css
//    逐字（字节）一致 —— 不增一行、不减一行、不重排。
// 2) DOM class 白名单：meeting-confirmation.js / mailbox-chat.js（会议模板）中
//    的字面量 class 必须全部命中 S-1 CSS / styles.css / mailbox-chat.css；
//    未列出的新 class、inline style、全局 p/button 污染一律禁止。
// 3) 独立资源文件：组件/CSS 均为独立文件；index.html 的注册（统一键/顺序/无重复）
//    由 meetingConfirmationAssets.test.js 覆盖（A2：退役本文件未注册断言）。
// 4) 不引入 rev 类/导航/预览 mock/fetch 拦截痕迹；无 element.style 写入。

const fs = require("fs");
const path = require("path");
const assert = require("assert");
const { describe, it } = require("node:test");

const ROOT = path.join(__dirname, "..", "..", "main", "resources", "static");
const cssPath = path.join(ROOT, "meeting-confirmation.css");
const cssSource = fs.readFileSync(cssPath, "utf-8");
const componentSource = fs.readFileSync(path.join(ROOT, "meeting-confirmation.js"), "utf-8");
const chatSource = fs.readFileSync(path.join(ROOT, "mailbox-chat.js"), "utf-8");
const mailboxChatCss = fs.readFileSync(path.join(ROOT, "mailbox-chat.css"), "utf-8");
const stylesSource = fs.readFileSync(path.join(ROOT, "styles.css"), "utf-8");

const TARGET_CSS = path.join(__dirname, "..", "..", "..", "docs", "plans", "2026-09-09", "meeting-confirmation-evidence", "meeting-confirmation.target.css");

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

function declaredIn(source, cls) {
    return new RegExp(`\\.${cls}(?=[\\s,{.:\\[])`).test(source);
}

describe("S-1: 落地 CSS 与 04 evidence 逐字（字节）一致", () => {
    it("meeting-confirmation.css 与 evidence/meeting-confirmation.target.css 字节一致", () => {
        const target = fs.readFileSync(TARGET_CSS, "utf-8");
        assert.strictEqual(cssSource, target);
    });

    it("文件是独立资源（index.html 注册检查见 meetingConfirmationAssets.test.js）", () => {
        assert.ok(fs.existsSync(cssPath), "CSS 文件独立存在");
        assert.ok(fs.existsSync(path.join(ROOT, "meeting-confirmation.js")), "组件文件独立存在");
    });
});

describe("S-1/I-6: DOM class 白名单与模板卫生", () => {
    it("meeting-confirmation.js 模板字面量 class 全部命中 S-1/既有样式", () => {
        const classes = literalClasses(componentSource);
        assert.ok(classes.size > 0, "must find template classes to audit");
        const unknown = [];
        classes.forEach((cls) => {
            if (/\$/.test(cls)) return;
            if (declaredIn(cssSource, cls)) return;
            if (declaredIn(stylesSource, cls)) return;
            if (declaredIn(mailboxChatCss, cls)) return;
            unknown.push(cls);
        });
        assert.deepStrictEqual(unknown, [], "组件模板 class 必须已在样式契约中声明");
    });

    it("mailbox-chat.js 会议模板的字面量 class 不越过旧样式白名单（meeting-* 只经运行时拼接）", () => {
        const classes = literalClasses(chatSource);
        classes.forEach((cls) => {
            if (/\$/.test(cls)) return;
            assert.ok(!String(cls).startsWith("meeting-"), `mailbox-chat.js 不得出现字面量 meeting- class：${cls}`);
        });
    });

    it("新文件模板不写 inline style；宿主不写 element.style", () => {
        assert.ok(!/"style="/.test(componentSource), "meeting-confirmation.js 模板不得出现 style 属性");
        assert.ok(!/'style='/.test(componentSource), "meeting-confirmation.js 模板不得出现 style 属性");
        assert.ok(!/\.style\s*=[^=]/.test(componentSource), "组件不得写 element.style");
        assert.ok(!/\.style\s*=[^=]/.test(chatSource), "mailbox-chat.js 宿主不得写 element.style");
        assert.ok(!/"style="/.test(chatSource), "mailbox-chat.js 模板不得出现 style 属性");
    });

    it("S-1 不复制预览导航/rev 类/mock 数据/fetch 拦截", () => {
        assert.ok(!cssSource.includes(".rev-"), "CSS 不含预览 rev 类");
        assert.ok(!cssSource.includes("#manual"), "CSS 不含预览 #manual 范围");
        assert.ok(!componentSource.includes("fetch("), "组件不直接 fetch/拦截");
    });

    it("既有 mailbox-chat.css 未被会议规则污染（独立 CSS 边界）", () => {
        assert.ok(!mailboxChatCss.includes(".meeting-"), "mailbox-chat.css 不含会议 class");
        const mobilePlan = fs.readFileSync(path.join(__dirname, "../../../docs/plans/2026-10-03/mobile-core-02-mailbox.md"), "utf8");
        const mobileBlock = mobilePlan.match(/```css\n([\s\S]*?)```/)[1];
        assert.ok(stylesSource.includes(mobileBlock), "手机覆盖必须逐字等于批准的 02 CSS");
        assert.ok(!stylesSource.replace(mobileBlock, "").includes(".meeting-dialog"), "批准的手机块外 styles.css 不含会议弹窗规则");
        // 会议标记 class 全部经 ${mcCls(...)} 动态拼接（模板字面量含 $，被旧白名单跳过）
        assert.ok(/class="\$\{mcCls\(/.test(chatSource), "会议标记必须以 ${mcCls(...)} 模板字面量出现");
        assert.ok(!chatSource.includes('class="meeting-'), "不得出现字面量 meeting- class");
    });
});

describe("S-2/S-3/S-4/S-5: 关键 id 与 data-role 源文本存在性（DOM stub 防悬空）", () => {
    const COMPONENT_IDS = [
        "id=\"meetingForm\"", "id=\"meetingTitle\"", "id=\"meetingContext\"",
        "id=\"closeMeeting\"", "id=\"meetingLoadStatus\"", "id=\"retryMeeting\"",
        "id=\"meetingCountry\"", "id=\"meetingCountrySummary\"", "id=\"meetingZoneField\"",
        "id=\"meetingZoneLabel\"", "id=\"meetingZoneSearch\"", "id=\"toggleZone\"", "id=\"meetingZoneOptions\"",
        "id=\"meetingZoneHint\"", "id=\"meetingDate\"", "id=\"meetingStart\"", "id=\"meetingEndDate\"",
        "id=\"meetingEnd\"", "id=\"meetingClock\"", "id=\"meetingUrl\"",
        "id=\"insertModeLabel\"", "id=\"insertMode\"", "id=\"meetingError\"", "id=\"meetingBody\"",
        "id=\"meetingFilename\"", "id=\"meetingFileMeta\"", "id=\"downloadMeeting\"", "id=\"inspectIcs\"",
        "id=\"meetingRaw\"", "id=\"cancelMeeting\"", "id=\"applyMeeting\""
    ];
    // I-5：已废弃的模板/称呼/签名节点必须整块消失（含模板 CSS 类与 id）。
    const REMOVED_IDS = [
        "meetingTemplate", "templateDetails", "templateText", "resetTemplate",
        "meetingName", "meetingSignature"
    ];
    const HOST_ROLES = [
        "data-action=\"mc-open-meeting\"", "data-role=\"meeting-attachment\"", "data-action=\"mc-edit-meeting\"",
        "data-action=\"mc-remove-meeting\"", "data-action=\"mc-download-meeting\"", "data-action=\"mc-download-sent-meeting\"",
        "data-role=\"sent-meeting-attachment\"", "data-meeting-block=\"true\"", "data-role=\"calendar-download\""
    ];

    it("S-2 组件关键 id 在源文本唯一存在", () => {
        assert.ok(/setAttribute\("id",\s*"meetingDialog"\)/.test(componentSource), "dialog id 经 setAttribute 唯一设置");
        COMPONENT_IDS.forEach((needle) => {
            const count = componentSource.split(needle).length - 1;
            assert.ok(count >= 1, `${needle} 必须在 meeting-confirmation.js 中存在`);
        });
    });

    it("I-5 废弃节点与模板专用语法不在组件源文本出现", () => {
        REMOVED_IDS.forEach((id) => {
            assert.ok(!componentSource.includes(`id="${id}"`), `${id} 必须已从弹窗移除`);
        });
        assert.ok(!componentSource.includes("meeting-template"), "模板详情 class 必须移除");
        assert.ok(!componentSource.includes("{{expert_salutation}}"), "专用 {{...}} 变量文案必须移除");
        assert.ok(!componentSource.includes("{{meeting_time}}"), "专用 {{...}} 变量文案必须移除");
        assert.ok(!componentSource.includes("meetingNameValue"), "称呼读取函数必须移除");
        assert.ok(!componentSource.includes("signatureValue"), "签名读取函数必须移除");
        assert.ok(!componentSource.includes("state.templates"), "模板目录状态必须移除");
        assert.ok(!componentSource.includes("baseTemplateId"), "模板选择状态必须移除");
    });

    it("S-3/S-5 宿主 data-action/data-role 在 mailbox-chat.js 源文本存在", () => {
        HOST_ROLES.forEach((needle) => {
            assert.ok(chatSource.includes(needle), `${needle} 必须在 mailbox-chat.js 中存在`);
        });
    });

    it("S-4 时区候选结构与文案在组件源文本存在", () => {
        assert.ok(componentSource.includes("role=\"option\""), "候选项 role=option");
        assert.ok(componentSource.includes("meeting-zone-option-"), "候选 id 前缀");
        assert.ok(componentSource.includes("没有匹配的时区，请尝试英文城市名或 UTC+3。"), "空结果固定文案");
        assert.ok(componentSource.includes("aria-activedescendant"), "键盘 active 跟随 input");
        assert.ok(componentSource.includes('data-role="zone-label"'), "候选国家标签 span");
        assert.ok(componentSource.includes('data-role="zone-offset"'), "候选 UTC 偏移 span");
        assert.strictEqual(componentSource.includes("</span><small>"), false,
            "候选不再内嵌原始 IANA small");
    });

    it("S-1/S-2 国家控件与分组契约：aria-describedby、无新 class/inline style、时区块位置", () => {
        const countryInput = componentSource.match(/<input id="meetingCountrySearch"[^>]*>/)[0];
        assert.ok(countryInput.includes('aria-describedby="meetingCountrySummary"'),
            "国家搜索框关联摘要");
        assert.ok(countryInput.includes('role="combobox"'));
        assert.ok(countryInput.includes('aria-controls="meetingCountryOptions"'));
        assert.ok(componentSource.includes('id="meetingCountryOptions" class="meeting-zone-options" role="listbox"'));
        assert.ok(componentSource.includes("\u8be5\u56fd\u5bb6/\u5730\u533a\u6709\u591a\u4e2a\u65f6\u533a\uff0c\u8bf7\u9009\u62e9\u3002"), "多组未选摘要文案");
        assert.ok(componentSource.includes("\u8be5\u65f6\u95f4\u6ca1\u6709\u53ef\u7528\u65f6\u533a\uff0c\u8bf7\u8c03\u6574\u65e5\u671f\u6216\u65f6\u95f4\u3002"), "0 组提示文案");
        assert.ok(componentSource.includes("\u586b\u5199\u5b8c\u6574\u4f1a\u8bae\u65e5\u671f\u548c\u65f6\u95f4\u540e\u663e\u793a\u65f6\u533a\u3002"), "未填完整时间提示");
        assert.ok(componentSource.includes("\u4f1a\u8bae\u65f6\u533a\u914d\u7f6e\u7248\u672c\u4e0d\u5339\u914d\uff0c\u8bf7\u5237\u65b0\u540e\u91cd\u8bd5"), "缺元信息文案");
        assert.ok(componentSource.includes("\u4f1a\u8bae\u65f6\u533a\u52a0\u8f7d\u5931\u8d25\uff0c\u8bf7\u91cd\u8bd5"), "目录失败文案");
        assert.ok(componentSource.includes("\u4f1a\u8bae\u65e5\u671f\u6216\u65f6\u95f4\u53d8\u5316\u540e\uff0c\u539f\u65f6\u533a\u9009\u9879\u5df2\u5206\u5f00\uff0c\u8bf7\u91cd\u65b0\u9009\u62e9\u3002"),
            "改期分拆提示文案");
        // country 在 loadStatus 之后、start/end fields 之前；zone field 在两个 fields 之后、clock 之前
        const loadAt = componentSource.indexOf("id=\"meetingLoadStatus\"");
        const countryAt = componentSource.indexOf("id=\"meetingCountry\"");
        const fieldsAt = componentSource.indexOf("class=\"meeting-fields\"");
        const zoneFieldAt = componentSource.indexOf("id=\"meetingZoneField\"");
        const clockAt = componentSource.indexOf("id=\"meetingClock\"");
        assert.ok(loadAt > -1 && countryAt > loadAt, "country 在 loadStatus 之后");
        assert.ok(fieldsAt > countryAt, "country 在起止字段之前");
        assert.ok(zoneFieldAt > fieldsAt, "时区选择移到最后一段起止字段之后");
        assert.ok(clockAt > zoneFieldAt, "时区块在 meetingClock 之前");
        assert.ok(componentSource.includes("<div id=\"meetingZoneField\" class=\"meeting-zone-field\" hidden>"),
            "时区块整块 hidden 契约");
        assert.strictEqual(componentSource.includes("placeholder=\"搜索国家、城市、时区或 UTC 偏移\""), false,
            "旧搜索候选文案退役");
    });

    it("空结果/加载/错误/ready/stale/sending 均有确定文案源", () => {
        assert.ok(componentSource.includes("正在生成邮件与日历…"));
        assert.ok(componentSource.includes("请填写左侧配置后预览邮件。"));
        assert.ok(componentSource.includes("请修正左侧配置后预览邮件。"));
        assert.ok(componentSource.includes("会议配置加载失败，请重试"));
        assert.ok(componentSource.includes("预览生成失败，请重试"));
        assert.ok(componentSource.includes("会议时间已过去，请核对"));
        assert.ok(chatSource.includes("待重新确认"));
        assert.ok(chatSource.includes("已发送日历"));
        assert.ok(chatSource.includes("data-meeting-sending"));
    });
});
