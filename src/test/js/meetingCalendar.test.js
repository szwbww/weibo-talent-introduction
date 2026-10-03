"use strict";

// fast-p 03 会议日历前端契约测试（I-1/I-2/I-4/I-5 · S-1/S-2）：
// 1) 四点注册（index nav / index section / app viewMeta / app refreshCurrentView）与
//    index.html 的 S-1 骨架、共享 dialog、S-2 取消确认态替换区源文本存在性；
// 2) styles.css 只追加计划 S-2 逐字块，mailbox-chat.css 不吸收 calendar-* 规则；
// 3) 唯一 formatter（zh-CN + Asia/Shanghai + 24 小时）：UTC / America/Los_Angeles /
//    Asia/Shanghai 三个浏览器时区得到同一中文北京串与同一 startBeijing 文本；
// 4) 月历固定周一至周日、42 格、北京月份边界/今天、跨午夜占两个日期格；
// 5) 区间分页（cursor）、摘要 adapter、外链白名单。
// 载入方式：app.js 顶层函数/常量切片进 vm 沙箱（无 DOM 依赖），源文本断言读 index/styles。

const fs = require("fs");
const path = require("path");
const vm = require("vm");
const assert = require("assert");
const { describe, it } = require("node:test");

const ROOT = path.join(__dirname, "..", "..", "main", "resources", "static");
const appSource = fs.readFileSync(path.join(ROOT, "app.js"), "utf-8");
const indexSource = fs.readFileSync(path.join(ROOT, "index.html"), "utf-8");
const stylesSource = fs.readFileSync(path.join(ROOT, "styles.css"), "utf-8");
const chatCssSource = fs.readFileSync(path.join(ROOT, "mailbox-chat.css"), "utf-8");
const briefSource = fs.readFileSync(
    path.join(__dirname, "..", "..", "..", "docs", "plans", "fast", "meeting-mail-master", "children", "03-calendar-ui", "brief.md"),
    "utf-8"
);

const briefCss = /```css\n([\s\S]*?)```/.exec(briefSource)[1];
const briefHtml = Array.from(briefSource.matchAll(/```html\n([\s\S]*?)```/g)).map((match) => match[1]);
const BRIEF_S1_NAV = briefHtml[0].trim().split("\n")[0];
const BRIEF_S1_SECTION = briefHtml[0].trim().split("\n")[1];
const BRIEF_S2_DOM = briefHtml[1].trim().split("\n");
const BRIEF_S2_CANCEL = briefHtml[2].trim().split("\n");

// ── app.js 切片（顶层函数/常量，不复制实现） ────────────────────────────────

function extractRegion(startMarker, endMarker) {
    const start = appSource.indexOf(startMarker);
    const end = start < 0 ? -1 : appSource.indexOf(endMarker, start);
    if (start < 0 || end < 0) throw new Error("app.js region not found: " + startMarker);
    return appSource.slice(start, end);
}

function extractFn(name) {
    const regex = new RegExp("(?:async\\s+)?function\\s+" + name + "\\s*\\([^)]*\\)\\s*\\{[\\s\\S]*?\\n\\}");
    const match = appSource.match(regex);
    if (!match) throw new Error("Could not find " + name + " in app.js");
    return match[0];
}

const CALENDAR_CONSTS = extractRegion("const MEETING_CALENDAR_ZONE = ", "// ── API adapter");
const CALENDAR_CHROME_CONST = extractRegion(
    "const MEETING_CALENDAR_CHROME_HTML =",
    "\nconst MEETING_CALENDAR_DAY_OPEN ="
);
const CALENDAR_SKELETON_CONST = extractRegion(
    "\nconst MEETING_CALENDAR_DAY_OPEN =",
    "\nfunction meetingCalendarRootEl()"
);
const CALENDAR_MODULE_SOURCE = extractRegion("// 会议日历（fast-p 03", "// Auto-init on load: bind events after DOM is ready");

const CALENDAR_FUNCTIONS = [
    "meetingCalendarBeijingParts",
    "meetingCalendarBeijingTextToInstant",
    "meetingCalendarBeijingLocalValue",
    "formatBeijingMeetingRange",
    "formatBeijingMeetingShort",
    "meetingCalendarFetchEvents",
    "meetingCalendarFetchSummaries",
    "meetingCalendarFetchEvent",
    "meetingCalendarMonthAnchor",
    "meetingCalendarShiftMonth",
    "meetingCalendarResetToToday",
    "meetingCalendarMonthLabel",
    "meetingCalendarPad",
    "meetingCalendarGridCells",
    "meetingCalendarGridRange",
    "meetingCalendarEventDateSpan",
    "meetingCalendarEventsByDate",
    "meetingCalendarIsCancelled",
    "meetingCalendarMonthEvents",
    "meetingCalendarOverview",
    "meetingCalendarEventExpertLabel",
    "meetingCalendarEventTimeText",
    "meetingCalendarSafeLink",
    "meetingCalendarDialogErrorMessage"
];

function createSandbox(options) {
    const opts = options || {};
    const requests = [];
    const sandbox = {
        URLSearchParams,
        console,
        escapeHtml: (value) => String(value == null ? "" : value),
        showStatus: () => {},
        api: (url, requestOptions) => {
            const entry = { url, options: requestOptions || null };
            requests.push(entry);
            if (opts.apiResults && opts.apiResults.length) return opts.apiResults.shift();
            return Promise.resolve({ items: [], nextCursor: null });
        }
    };
    vm.createContext(sandbox);
    vm.runInContext(CALENDAR_CONSTS + "\n" + CALENDAR_CHROME_CONST + "\n" + CALENDAR_SKELETON_CONST, sandbox);
    CALENDAR_FUNCTIONS.forEach((name) => vm.runInContext(extractFn(name), sandbox));
    return { sandbox, requests };
}

function countOccurrences(text, needle) {
    let count = 0;
    let index = text.indexOf(needle);
    while (index !== -1) {
        count += 1;
        index = text.indexOf(needle, index + needle.length);
    }
    return count;
}

describe("S-1/S-2: 注册与源文本契约", () => {
    it("index.html 含计划 S-1 的会议日历 Tab 与空 view 骨架（各唯一）", () => {
        assert.ok(indexSource.includes(BRIEF_S1_NAV), "会议日历 nav 按钮必须与 S-1 逐字一致");
        assert.ok(indexSource.includes(BRIEF_S1_SECTION), "view 骨架必须与 S-1 逐字一致（root 内不得预置内容）");
        assert.strictEqual(countOccurrences(indexSource, 'data-view="meeting-calendar"'), 1);
        assert.strictEqual(countOccurrences(indexSource, 'id="view-meeting-calendar"'), 1);
        assert.strictEqual(countOccurrences(indexSource, 'id="meetingCalendarRoot"'), 1);
        assert.strictEqual(countOccurrences(indexSource, 'data-role="calendar-month"'), 0, "月历骨架由 app.js 渲染，index 不复制");
    });

    it("会议日历 Tab 紧跟在收发件箱 Tab 之后（不改变既有 Tab 顺序）", () => {
        const mailbox = indexSource.indexOf('data-view="mailbox"');
        const calendar = indexSource.indexOf('data-view="meeting-calendar"');
        const inboundSummary = indexSource.indexOf('data-view="inbound-summary"');
        assert.ok(mailbox > 0 && calendar > mailbox && inboundSummary > calendar, "插入顺序必须是 邮箱 → 会议日历 → 来信汇总");
    });

    it("index.html 含共享排期 dialog 的全部 S-2 合同片段（含取消确认态替换区）", () => {
        const start = indexSource.indexOf('<dialog id="meetingCalendarDialog"');
        const end = indexSource.indexOf("</dialog>", start);
        assert.ok(start > 0 && end > start, "共享 dialog 必须存在");
        const dialog = indexSource.slice(start, end);
        ["id=\"meetingCalendarDialogTitle\"", "id=\"meetingCalendarForm\"", "data-role=\"expert-picker\""]
            .forEach((needle) => assert.ok(dialog.includes(needle), `dialog 必须包含 ${needle}`));
        BRIEF_S2_DOM.forEach((line) => {
            const fragments = line.split("<label class=\"calendar-field calendar-wide\" data-role=\"cancel-reason-field\">");
            fragments.forEach((fragment) => {
                const parts = fragment.split("<div class=\"calendar-actions\" data-role=\"cancel-confirm-actions\">");
                parts.forEach((part) => {
                    const trimmed = part.trim();
                    if (!trimmed) return;
                    assert.ok(dialog.includes(trimmed) || line.includes(trimmed),
                        `S-2 DOM 合同片段缺失：${trimmed.slice(0, 80)}`);
                });
            });
        });
        BRIEF_S2_CANCEL.forEach((line) => {
            assert.ok(dialog.includes(line), `取消确认态骨架必须在同一 dialog 内逐字存在：${line.slice(0, 60)}`);
        });
        assert.strictEqual(countOccurrences(dialog, "data-role=\"cancel-reason-field\""), 1, "取消原因字段不得重复 id/role");
        assert.strictEqual(countOccurrences(dialog, "data-role=\"cancel-confirm-actions\""), 1);
        assert.strictEqual(countOccurrences(indexSource, 'id="meetingCalendarDialog"'), 1);
        assert.strictEqual(countOccurrences(indexSource, 'id="meetingCalendarForm"'), 1);
        assert.strictEqual(countOccurrences(indexSource, 'id="meetingCalendarDialogTitle"'), 1);
    });

    it("新增 index 片段无 inline style，且未新增静态资源缓存键", () => {
        assert.ok(!indexSource.includes('data-view="meeting-calendar" style='), "不得使用 inline style");
        assert.ok(!/<dialog id="meetingCalendarDialog"[\s\S]*?style="/.test(indexSource.slice(indexSource.indexOf('<dialog id="meetingCalendarDialog"'), indexSource.indexOf("</dialog>", indexSource.indexOf('<dialog id="meetingCalendarDialog"')))), "dialog 不得使用 inline style");
        const keys = indexSource.match(/[?]v=[^"'&]+/g) || [];
        assert.strictEqual(keys.length, 11,
            "静态资源键数量不得因本功能变化（合并 global-world-clock 后基线为 11：7 旧 + meeting-confirmation.css/.js + world-clock.css/.js）");
        assert.ok(!indexSource.includes("meeting-calendar.css"), "本子计划不新增静态资源");
    });

    it("app.js 四点注册齐全：viewMeta + refreshCurrentView 分支", () => {
        assert.ok(appSource.includes('"meeting-calendar": ["会议日历"'), "viewMeta 必须注册会议日历");
        assert.ok(appSource.includes('if (state.view === "meeting-calendar") await loadMeetingCalendar();'),
            "refreshCurrentView 必须回读会议日历");
        const refreshBody = appSource.slice(
            appSource.indexOf("async function refreshCurrentView()"),
            appSource.indexOf("async function loadAccounts()")
        );
        assert.strictEqual(countOccurrences(refreshBody, 'state.view === "meeting-calendar"'), 1,
            "切 Tab 必须且只回读一次会议日历");
    });

    it("styles.css 逐字追加 S-2 CSS 块；mailbox-chat.css 未吸收 calendar-* 规则", () => {
        assert.ok(stylesSource.includes(briefCss), "S-2 CSS 块必须与计划逐字一致");
        assert.ok(stylesSource.includes("/* meeting-mail-03: meeting calendar */"), "追加块必须带计划标题注释");
        assert.ok(!chatCssSource.includes(".calendar-"), "mailbox-chat.css 必须保持字节不变（不得新增 calendar-* 规则）");
        assert.ok(stylesSource.includes(".calendar-summary{"), "calendar-summary 必须由 styles.css 声明");
    });

    it("新增日历模块不含 inline style", () => {
        assert.ok(!/style="/.test(CALENDAR_MODULE_SOURCE), "会议日历模块不得写 inline style");
        assert.ok(!/style=/.test(CALENDAR_MODULE_SOURCE), "会议日历模块不得写 inline style");
    });
});

describe("I-2: 唯一中文北京时间 formatter", () => {
    const iso = "2026-09-18T02:00:00Z";

    function formatUnderTimezone(timezone) {
        const previous = process.env.TZ;
        process.env.TZ = timezone;
        try {
            const { sandbox } = createSandbox();
            vm.runInContext(`const VALUE = ${JSON.stringify(iso)};`, sandbox);
            return {
                local: vm.runInContext("new Date(VALUE).toString()", sandbox),
                range: vm.runInContext("formatBeijingMeetingRange(VALUE, '2026-09-18T02:30:00Z')", sandbox),
                localValue: vm.runInContext("meetingCalendarBeijingLocalValue(VALUE)", sandbox),
                submitted: vm.runInContext("meetingCalendarBeijingTextToInstant(meetingCalendarBeijingLocalValue(VALUE)).toISOString()", sandbox)
            };
        } finally {
            process.env.TZ = previous;
        }
    }

    it("UTC / America/Los_Angeles / Asia/Shanghai 三时区显示与提交完全一致", () => {
        const results = ["UTC", "America/Los_Angeles", "Asia/Shanghai"].map(formatUnderTimezone);
        assert.notStrictEqual(results[0].local, results[1].local, "用例必须真的改变浏览器时区");
        results.forEach((result) => {
            assert.strictEqual(result.range, "2026年9月18日 周五 10:00–10:30");
            assert.strictEqual(result.localValue, "2026-09-18T10:00");
            assert.strictEqual(result.submitted, "2026-09-18T02:00:00.000Z", "提交必须回落到同一 UTC 瞬时");
        });
    });

    it("显示串不含英文周/月与 IANA zone 串", () => {
        const { sandbox } = createSandbox();
        const range = vm.runInContext("formatBeijingMeetingRange('2026-09-30T15:30:00Z', '2026-09-30T16:30:00Z')", sandbox);
        assert.strictEqual(range, "2026年9月30日 周三 23:30 – 2026年10月1日 周四 00:30");
        assert.ok(!/Mon|Tue|Wed|Thu|Fri|Sat|Sun|Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec/.test(range));
        assert.ok(!/Asia\/|Europe\/|UTC|GMT/.test(range));
        const short = vm.runInContext("formatBeijingMeetingShort('2026-09-18T02:00:00Z')", sandbox);
        assert.strictEqual(short, "9月18日 10:00");
    });

    it("datetime-local 文本显式按 +08:00 解析，非法值拒绝而不是被 Date 归一化", () => {
        const { sandbox } = createSandbox();
        assert.strictEqual(vm.runInContext("meetingCalendarBeijingTextToInstant('2026-09-18T10:00').toISOString()", sandbox), "2026-09-18T02:00:00.000Z");
        assert.strictEqual(vm.runInContext("meetingCalendarBeijingTextToInstant('2026-02-30T10:00')", sandbox), null);
        assert.strictEqual(vm.runInContext("meetingCalendarBeijingTextToInstant('2026-09-18 10:00')", sandbox), null);
        assert.strictEqual(vm.runInContext("meetingCalendarBeijingTextToInstant('')", sandbox), null);
    });
});

describe("I-2/I-4: 月历 42 格与北京月份", () => {
    it("固定 7 周 42 格、周一开头、跨月带 outside 标记", () => {
        const { sandbox } = createSandbox();
        vm.runInContext("meetingCalendarState.month = { year: 2026, month: 9 };", sandbox);
        const cells = vm.runInContext("JSON.stringify(meetingCalendarGridCells())", sandbox);
        const parsed = JSON.parse(cells);
        assert.strictEqual(parsed.length, 42);
        assert.strictEqual(parsed[0].key, "2026-08-31", "网格必须从所在周的周一开始");
        assert.strictEqual(parsed[41].key, "2026-10-11");
        assert.strictEqual(parsed[0].outside, true);
        assert.strictEqual(parsed[1].key, "2026-09-01");
        assert.strictEqual(parsed[1].outside, false);
        assert.strictEqual(parsed[1].dayNumber, "1");
        assert.ok(parsed.every((cell) => Number.isFinite(cell.ms)));
        const weekdayRow = vm.runInContext("MEETING_CALENDAR_WEEKDAY_ROW_HTML", sandbox);
        const labels = weekdayRow.match(/>([^<]+)<\/div>/g).map((fragment) => fragment.slice(1, -6));
        assert.deepStrictEqual(labels, ["周一", "周二", "周三", "周四", "周五", "周六", "周日"]);
    });

    it("区间按整格北京日期转 UTC 瞬时（月初/月末以北京日期计算）", () => {
        const { sandbox } = createSandbox();
        vm.runInContext("meetingCalendarState.month = { year: 2026, month: 9 };", sandbox);
        const range = JSON.parse(vm.runInContext("JSON.stringify(meetingCalendarGridRange())", sandbox));
        assert.strictEqual(range.from, "2026-08-30T16:00:00.000Z", "北京 2026-08-31 00:00");
        assert.strictEqual(range.to, "2026-10-11T16:00:00.000Z", "北京 2026-10-12 00:00（右开）");
    });

    it("今天标记由北京日期决定，且只落在当前网格", () => {
        const { sandbox } = createSandbox();
        vm.runInContext("meetingCalendarResetToToday();", sandbox);
        const todayCount = vm.runInContext("meetingCalendarGridCells().filter((cell) => cell.today).length", sandbox);
        assert.strictEqual(todayCount, 1);
        vm.runInContext("meetingCalendarState.month = { year: 2000, month: 1 };", sandbox);
        assert.strictEqual(vm.runInContext("meetingCalendarGridCells().filter((cell) => cell.today).length", sandbox), 0);
    });

    it("月份前后翻页跨年正确", () => {
        const { sandbox } = createSandbox();
        vm.runInContext("meetingCalendarState.month = { year: 2026, month: 12 };", sandbox);
        vm.runInContext("meetingCalendarShiftMonth(1);", sandbox);
        assert.strictEqual(vm.runInContext("JSON.stringify(meetingCalendarState.month)", sandbox), JSON.stringify({ year: 2027, month: 1 }));
        vm.runInContext("meetingCalendarShiftMonth(-2);", sandbox);
        assert.strictEqual(vm.runInContext("JSON.stringify(meetingCalendarState.month)", sandbox), JSON.stringify({ year: 2026, month: 11 }));
    });
});

describe("I-2/I-4: 跨午夜与列表/月历文本", () => {
    const crossMidnight = {
        id: 7,
        contactId: 3,
        expertName: "专家C",
        startUtc: "2026-09-30T15:30:00Z",
        endUtc: "2026-09-30T16:30:00Z",
        status: "ACTIVE"
    };

    it("跨午夜事件在涉及的每个北京日期格都出现，并显示起止完整日期", () => {
        const { sandbox } = createSandbox();
        vm.runInContext(`const EVENT = ${JSON.stringify(crossMidnight)};`, sandbox);
        const span = JSON.parse(vm.runInContext("JSON.stringify(meetingCalendarEventDateSpan(EVENT))", sandbox));
        assert.deepStrictEqual(JSON.parse(JSON.stringify(span)), { startKey: "2026-09-30", endKey: "2026-10-01" });
        vm.runInContext("meetingCalendarState.month = { year: 2026, month: 10 };", sandbox);
        const byDate = JSON.parse(vm.runInContext(
            "JSON.stringify(Array.from(meetingCalendarEventsByDate([EVENT], meetingCalendarGridCells()).entries()).filter((pair) => pair[1].length > 0).map((pair) => pair[0]))",
            sandbox
        ));
        assert.deepStrictEqual(byDate, ["2026-09-30", "2026-10-01"]);
        const monthText = vm.runInContext("meetingCalendarEventTimeText(EVENT, 'month')", sandbox);
        assert.strictEqual(monthText, "2026年9月30日 周三 23:30 – 2026年10月1日 周四 00:30");
        const listText = vm.runInContext("meetingCalendarEventTimeText(EVENT, 'list')", sandbox);
        assert.strictEqual(listText, monthText);
    });

    it("同日事件月历只显示时段、列表显示完整中文日期", () => {
        const { sandbox } = createSandbox();
        vm.runInContext("const EVENT = { id: 8, startUtc: '2026-09-18T02:00:00Z', endUtc: '2026-09-18T02:30:00Z', status: 'ACTIVE', expertName: '专家A' };", sandbox);
        assert.strictEqual(vm.runInContext("meetingCalendarEventTimeText(EVENT, 'month')", sandbox), "10:00–10:30");
        assert.strictEqual(vm.runInContext("meetingCalendarEventTimeText(EVENT, 'list')", sandbox), "2026年9月18日 周五 10:00–10:30");
        assert.strictEqual(vm.runInContext("meetingCalendarEventExpertLabel(EVENT)", sandbox), "专家A");
        assert.strictEqual(vm.runInContext("meetingCalendarIsCancelled({ status: 'CANCELLED' })", sandbox), true);
        assert.strictEqual(vm.runInContext("meetingCalendarIsCancelled(EVENT)", sandbox), false);
    });
});

describe("I-1/I-4: API adapter 分页、摘要与外链白名单", () => {
    it("区间事件按 cursor 翻页取全", async () => {
        const { sandbox, requests } = createSandbox({
            apiResults: [
                Promise.resolve({ items: [{ id: 1 }], nextCursor: "cursor-1" }),
                Promise.resolve({ items: [{ id: 2 }], nextCursor: null })
            ]
        });
        const items = await vm.runInContext(
            "meetingCalendarFetchEvents({ from: '2026-08-30T16:00:00.000Z', to: '2026-10-11T16:00:00.000Z', showCancelled: false })",
            sandbox
        );
        assert.deepStrictEqual(JSON.parse(JSON.stringify(items)).map((item) => item.id), [1, 2]);
        assert.strictEqual(requests.length, 2);
        assert.ok(requests[0].url.includes("from=2026-08-30T16%3A00%3A00.000Z"));
        assert.ok(requests[0].url.includes("showCancelled=false"));
        assert.ok(requests[0].url.includes("limit=200"));
        assert.ok(!requests[0].url.includes("cursor="));
        assert.ok(requests[1].url.includes("cursor=cursor-1"));
    });

    it("服务端持续返回 cursor 时分页有上限（不无限循环）", async () => {
        const apiResults = [];
        for (let index = 0; index < 20; index += 1) apiResults.push(Promise.resolve({ items: [{ id: index }], nextCursor: `c${index}` }));
        const { sandbox, requests } = createSandbox({ apiResults });
        await vm.runInContext(
            "meetingCalendarFetchEvents({ from: '2026-08-30T16:00:00.000Z', to: '2026-10-11T16:00:00.000Z', showCancelled: true })",
            sandbox
        );
        assert.strictEqual(requests.length, 10, "上限必须等于 MEETING_CALENDAR_MAX_PAGES");
        assert.ok(requests[0].url.includes("showCancelled=true"));
    });

    it("摘要 adapter 过滤非法 id 且无 id 时不发请求", async () => {
        const { sandbox, requests } = createSandbox({
            apiResults: [Promise.resolve([{ contactId: 5, activeCount: 2, next: null }])]
        });
        const rows = await vm.runInContext("meetingCalendarFetchSummaries([5, 'x', -1, null])", sandbox);
        assert.deepStrictEqual(JSON.parse(JSON.stringify(rows)), [{ contactId: 5, activeCount: 2, next: null }]);
        assert.strictEqual(requests.length, 1);
        assert.ok(requests[0].url.startsWith("/api/meeting-calendar/summaries?contactIds=5"));
        const empty = await vm.runInContext("meetingCalendarFetchSummaries([])", sandbox);
        assert.deepStrictEqual(JSON.parse(JSON.stringify(empty)), []);
        assert.strictEqual(requests.length, 1, "空 id 集合不得产生请求");
    });

    it("外链仅 http/https，409 冲突给出可重试提示", () => {
        const { sandbox } = createSandbox();
        assert.strictEqual(vm.runInContext("meetingCalendarSafeLink('https://zoom.us/j/1')", sandbox), "https://zoom.us/j/1");
        assert.strictEqual(vm.runInContext("meetingCalendarSafeLink(' http://example.com/a ') ", sandbox), "http://example.com/a");
        assert.strictEqual(vm.runInContext("meetingCalendarSafeLink('javascript:alert(1)')", sandbox), "");
        assert.strictEqual(vm.runInContext("meetingCalendarSafeLink('data:text/html,x')", sandbox), "");
        assert.strictEqual(vm.runInContext("meetingCalendarSafeLink('')", sandbox), "");
        const conflict = vm.runInContext("meetingCalendarDialogErrorMessage({ status: 409, message: 'stale' }, '保存失败')", sandbox);
        assert.match(conflict, /已被其他操作修改/);
        assert.strictEqual(vm.runInContext("meetingCalendarDialogErrorMessage({ message: '结束时间必须晚于开始时间' }, '保存失败')", sandbox), "结束时间必须晚于开始时间");
        assert.strictEqual(vm.runInContext("meetingCalendarDialogErrorMessage(null, '保存失败')", sandbox), "保存失败");
    });
});

describe("S-2: 渲染骨架常量", () => {
    it("预览对齐后的 toolbar 保留全部操作、统计栏及会议侧栏", () => {
        const { sandbox } = createSandbox();
        const chrome = vm.runInContext("MEETING_CALENDAR_CHROME_HTML", sandbox);
        ["previous", "next", "today", "create", "month", "list"].forEach((action) => {
            assert.strictEqual(countOccurrences(chrome, `data-calendar-action="${action}"`), 1);
        });
        ["month-count", "upcoming-count", "today-count", "calendar-agenda", "show-cancelled"].forEach((role) => {
            assert.strictEqual(countOccurrences(chrome, `data-role="${role}"`), 1);
        });
        assert.ok(chrome.includes('class="checkbox-row"'));
        assert.ok(chrome.includes(BRIEF_S2_DOM[1]), "status 骨架必须逐字");
        assert.ok(chrome.includes('<div class="calendar-scroll"><div class="calendar-grid" data-role="calendar-grid"></div></div>'),
            "月历滚动容器必须存在（日期格由渲染期填充）");
        assert.ok(chrome.includes(BRIEF_S2_DOM[3]), "列表容器骨架必须逐字");
    });

    it("日期格与事件按钮骨架逐字、空槽、无样例数据与 inline style", () => {
        const { sandbox } = createSandbox();
        const dayOpen = vm.runInContext("MEETING_CALENDAR_DAY_OPEN", sandbox);
        assert.strictEqual(dayOpen, '<div class="calendar-day" data-outside="false" data-today="false"><span data-role="day-number"></span>');
        assert.strictEqual(vm.runInContext("MEETING_CALENDAR_DAY_CLOSE", sandbox), "</div>");
        const eventSkeleton = vm.runInContext("MEETING_CALENDAR_EVENT_SKELETON", sandbox);
        assert.strictEqual(eventSkeleton,
            '<button class="calendar-event" data-cancelled="false" data-event-id=""><time></time><strong></strong><span data-role="event-status"></span></button>');
        const weekdayRow = vm.runInContext("MEETING_CALENDAR_WEEKDAY_ROW_HTML", sandbox);
        assert.ok(weekdayRow.startsWith('<div class="calendar-weekday">周一</div>'), "月历固定周一起始");
        [dayOpen, eventSkeleton].forEach((skeleton) => {
            assert.ok(!/\d{4}-\d{2}-\d{2}/.test(skeleton), "骨架不得硬编码样例日期");
            assert.ok(!skeleton.includes("style="), "骨架不得使用 inline style");
        });
    });
});

describe("预览对齐：真实排期统计", () => {
    it("北京月份交界只计相交排期，取消项不影响有效统计，午夜结束不多算一天", () => {
        const { sandbox } = createSandbox();
        const result = vm.runInContext(`(() => {
            const events = [
                { id: 1, startUtc: '2026-08-31T15:00:00Z', endUtc: '2026-08-31T16:00:00Z' },
                { id: 2, startUtc: '2026-08-31T15:30:00Z', endUtc: '2026-08-31T16:30:00Z' },
                { id: 3, startUtc: '2026-09-17T01:00:00Z', endUtc: '2026-09-17T02:00:00Z' },
                { id: 4, startUtc: '2026-09-17T03:00:00Z', endUtc: '2026-09-17T04:00:00Z', status: 'CANCELLED' },
                { id: 5, startUtc: '2026-09-30T16:00:00Z', endUtc: '2026-09-30T17:00:00Z' },
                { id: 6, startUtc: '2026-09-16T15:00:00Z', endUtc: '2026-09-16T16:00:00Z' }
            ];
            const month = meetingCalendarMonthEvents(events.reverse(), { year: 2026, month: 9 });
            return { ids: month.map(e => e.id), counts: meetingCalendarOverview(month, new Date('2026-09-17T00:00:00Z')) };
        })()`, sandbox);
        assert.deepStrictEqual(JSON.parse(JSON.stringify(result)), {
            ids: [2, 6, 3, 4], counts: { month: 3, upcoming: 1, today: 1 }
        });
    });
});

// mobile-core-03: exercise production view switching without a second event store.
function createMobileCalendarHarness(width) {
    const harness = createSandbox();
    const { sandbox } = harness;
    const nodes = new Map();
    const root = { dataset: { meetingCalendarSkeleton: "1" }, querySelector(selector) {
        if (!nodes.has(selector)) nodes.set(selector, { hidden: false, attributes: {},
            setAttribute(name, value) { this.attributes[name] = value; } });
        return nodes.get(selector);
    } };
    let viewport = width;
    let listEvents = [];
    Object.assign(sandbox, {
        document: {}, state: { view: "meeting-calendar" },
        window: { matchMedia: () => ({ matches: viewport <= 760 }) },
        $: (selector) => selector === "#meetingCalendarRoot" ? root : null,
        ensureMeetingCalendarBound: () => {}, renderMeetingCalendarOverview: () => {},
        renderMeetingCalendarGrid: () => {},
        renderMeetingCalendarList: (_, events) => { listEvents = events; }
    });
    ["isMobileCoreViewport", "meetingCalendarEffectiveView", "meetingCalendarRootEl", "meetingCalendarSetHidden",
        "meetingCalendarStatusText", "renderMeetingCalendar", "loadMeetingCalendar", "onMeetingCalendarClick",
        "onMobileCoreViewportChange"].forEach(name => vm.runInContext(extractFn(name), sandbox));
    return { ...harness, nodes,
        run: code => vm.runInContext(code, sandbox),
        resize: next => { viewport = next; sandbox.onMobileCoreViewportChange(); },
        click: action => sandbox.onMeetingCalendarClick({ target: { closest: selector =>
            selector === "[data-calendar-action]" ? { getAttribute: () => action } : null } }),
        listEvents: () => JSON.parse(JSON.stringify(listEvents))
    };
}

describe("mobile-core-03: viewport defaults and explicit calendar preference", () => {
    it("fresh 390/760px uses list, 761/1440px uses month, hidden and aria state agree", () => {
        for (const width of [390, 760, 761, 1440]) {
            const h = createMobileCalendarHarness(width);
            h.sandbox.renderMeetingCalendar();
            const mobile = width <= 760;
            assert.strictEqual(h.run("meetingCalendarState.viewPreference"), null);
            assert.strictEqual(h.run("meetingCalendarState.viewMode"), mobile ? "list" : "month");
            assert.strictEqual(h.nodes.get(".calendar-scroll").hidden, mobile);
            assert.strictEqual(h.nodes.get('[data-role="calendar-list"]').hidden, !mobile);
            assert.strictEqual(h.nodes.get('[data-calendar-action="list"]').attributes["aria-pressed"], String(mobile));
            assert.strictEqual(h.nodes.get('[data-calendar-action="month"]').attributes["aria-pressed"], String(!mobile));
            assert.strictEqual(h.requests.length, 0);
        }
    });

    it("unselected mode follows width only on calendar page, preserves open dialog and query state", () => {
        const h = createMobileCalendarHarness(1440);
        h.run("meetingCalendarState.dialog.open = true; meetingCalendarState.dialog.event = {id: 7}; meetingCalendarState.showCancelled = true;");
        const before = h.run("JSON.stringify({seq:meetingCalendarState.seq,dialog:meetingCalendarState.dialog,events:meetingCalendarState.events})");
        h.sandbox.renderMeetingCalendar();
        h.resize(390);
        assert.strictEqual(h.run("meetingCalendarState.viewMode"), "list");
        h.resize(761);
        assert.strictEqual(h.run("meetingCalendarState.viewMode"), "month");
        h.sandbox.state.view = "mailbox";
        h.resize(390);
        assert.strictEqual(h.run("meetingCalendarState.viewMode"), "month");
        assert.strictEqual(h.run("JSON.stringify({seq:meetingCalendarState.seq,dialog:meetingCalendarState.dialog,events:meetingCalendarState.events})"), before);
        assert.strictEqual(h.run("meetingCalendarState.showCancelled"), true);
        assert.strictEqual(h.requests.length, 0);
    });

    it("explicit month/list survives resize, month navigation and refresh with only original fetches", async () => {
        const h = createMobileCalendarHarness(390);
        h.run("meetingCalendarState.month = {year: 2026, month: 10};");
        h.sandbox.renderMeetingCalendar();
        h.click("month");
        h.resize(1440); h.resize(390);
        assert.strictEqual(h.run("meetingCalendarState.viewPreference"), "month");
        assert.strictEqual(h.run("meetingCalendarState.viewMode"), "month");
        assert.strictEqual(h.requests.length, 0);
        h.click("next");
        await new Promise(resolve => setImmediate(resolve));
        assert.strictEqual(h.requests.length, 1, "month navigation retains one original fetch");
        assert.strictEqual(h.run("meetingCalendarState.month.month"), 11);
        assert.strictEqual(h.run("meetingCalendarState.viewMode"), "month");
        await h.sandbox.loadMeetingCalendar();
        assert.strictEqual(h.requests.length, 2, "refresh retains one original fetch");
        assert.strictEqual(h.run("meetingCalendarState.viewMode"), "month");
        h.click("list"); h.resize(1440);
        assert.strictEqual(h.run("meetingCalendarState.viewPreference"), "list");
        assert.strictEqual(h.run("meetingCalendarState.viewMode"), "list");
        assert.strictEqual(h.requests.length, 2);
        const fresh = createMobileCalendarHarness(1440);
        fresh.sandbox.renderMeetingCalendar();
        assert.strictEqual(fresh.run("meetingCalendarState.viewMode"), "month", "reload discards memory preference");
    });

    it("mobile list still filters intersecting Beijing month and sorts original events", () => {
        const h = createMobileCalendarHarness(390);
        h.run(`meetingCalendarState.month = {year:2026,month:10}; meetingCalendarState.events = [
            {id:3,startUtc:'2026-11-01T00:00:00Z',endUtc:'2026-11-01T01:00:00Z'},
            {id:2,startUtc:'2026-10-03T02:00:00Z',endUtc:'2026-10-03T03:00:00Z',status:'CANCELLED'},
            {id:1,startUtc:'2026-09-30T15:30:00Z',endUtc:'2026-09-30T16:30:00Z'}];`);
        h.sandbox.renderMeetingCalendar();
        assert.deepStrictEqual(h.listEvents().map(event => event.id), [1, 2]);
        assert.strictEqual(h.requests.length, 0);
    });

    it("exact approved mobile CSS remains appended and no preference storage or new listener", () => {
        const plan = fs.readFileSync(path.join(__dirname, '../../../docs/plans/2026-10-03/mobile-core-03-calendar-tasks.md'), 'utf8');
        const css = /```css\n([\s\S]*?)```/.exec(plan)[1];
        assert.ok(stylesSource.includes(css));
        assert.strictEqual(countOccurrences(appSource, 'addEventListener("change", onMobileCoreViewportChange)'), 1);
        for (const fn of ['meetingCalendarEffectiveView', 'renderMeetingCalendar', 'onMeetingCalendarClick', 'onMobileCoreViewportChange']) {
            assert.ok(!/localStorage|sessionStorage|addEventListener/.test(extractFn(fn)));
        }
    });
});

describe("mobile-core-03 amendment: real meeting links", () => {
    function setup() {
        const { sandbox } = createSandbox();
        const makeNode = () => ({ attributes: {}, textContent: '', children: [],
            setAttribute(name, value) { this.attributes[name] = value; },
            appendChild(child) { this.children.push(child); },
            querySelector() { return makeNode(); },
            after(node) { this.siblingLink = node; }
        });
        const summary = makeNode();
        Object.assign(sandbox, { URL, document: { createElement: makeNode },
            meetingCalendarDialogEl: () => ({querySelector: () => summary}) });
        ['meetingCalendarLinkLabel', 'meetingCalendarFillEventButton', 'renderMeetingCalendarList',
            'renderMeetingCalendarDialogSourceSummary'].forEach(name => vm.runInContext(extractFn(name), sandbox));
        return { sandbox, makeNode, summary };
    }

    it("list keeps detail buttons and appends safe sibling links, never fabricated links", () => {
        const { sandbox, makeNode } = setup();
        const events = [
            {id:1,meetingLink:'https://us02web.zoom.us/j/123?pwd=actual'},
            {id:2,meetingLink:'https://meet.example.test/abc'},
            {id:3,meetingLink:null}, {id:4,meetingLink:'javascript:alert(1)'},
            {id:5,meetingLink:'https://zoom.us.evil.test/j/123'},
            {id:6,meetingLink:'https://zoom.com/j/789'}
        ];
        const buttons = events.map(makeNode);
        const list = { innerHTML:'', querySelectorAll: () => buttons };
        sandbox.renderMeetingCalendarList(list, events);
        assert.strictEqual(countOccurrences(list.innerHTML, 'class="calendar-event"'), events.length);
        [0,1,4,5].forEach(index => {
            const link = buttons[index].siblingLink;
            assert.strictEqual(link.attributes.href, events[index].meetingLink);
            assert.strictEqual(link.attributes.target, '_blank');
            assert.strictEqual(link.attributes.rel, 'noopener');
            assert.strictEqual(buttons[index].children.length, 0, 'anchor cannot be nested inside button');
        });
        assert.match(buttons[0].siblingLink.textContent, /Zoom/);
        assert.match(buttons[5].siblingLink.textContent, /Zoom/);
        assert.strictEqual(buttons[1].siblingLink.textContent, '会议链接');
        assert.strictEqual(buttons[4].siblingLink.textContent, '会议链接');
        assert.strictEqual(buttons[2].siblingLink, undefined);
        assert.strictEqual(buttons[3].siblingLink, undefined);
    });

    it("shared event details expose the same safe real Zoom link", () => {
        const { sandbox, summary } = setup();
        vm.runInContext("meetingCalendarState.dialog.event = {meetingLink:'https://us02web.zoom.us/j/123',sourceMailRecordId:42};", sandbox);
        sandbox.renderMeetingCalendarDialogSourceSummary();
        assert.strictEqual(summary.children.length, 1);
        assert.strictEqual(summary.children[0].attributes.href, 'https://us02web.zoom.us/j/123');
        assert.strictEqual(summary.children[0].attributes.rel, 'noopener');
        assert.match(summary.children[0].textContent, /Zoom/);
        assert.match(summary.textContent, /来源邮件 #42/);
    });
});
