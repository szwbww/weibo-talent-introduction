# fast-p 子计划 01 执行报告

- 子计划：`docs/plans/2026-09-17/global-world-clock-01-component.md`（身份 `commit:96a0a411c88eaa447c2c6512f9c70fa8d8aaffb1`）
- 主计划：`docs/plans/2026-09-17/global-world-clock-master.md`
- child_base_sha：`24f5c8205a304d3682e09e02458960bc2caa0463`
- 工作区：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-global-world-clock-master`
- 分支：`fast/global-world-clock-master`
- 执行环境：Node `v25.7.0`（macOS arm64）
- 实现提交：`4c4c85c3ae236307a4435ca382929dc88c74eaa0` — `feat(fast-p): implement 01`
- 提交内容（`git show --name-only`）：仅下列三个文件；`docs/plans/fast/**` 未进入提交（仍为未跟踪工作区文件）

## 交付文件

| 文件 | 状态 | 行数 | sha256 |
|---|---|---|---|
| `src/main/resources/static/world-clock.js` | 新增 | 1089 | `ceb6ccd4b796953b4344ca35d4f9ad5acc8c2d0f0828000ece4388ae053721c7` |
| `src/main/resources/static/world-clock.css` | 新增（S-4 标记块逐字复制） | 152 | `81519e9b7cb3589769e63acf24d72745ce28182126765f12e2443b38d12c2055` |
| `src/test/js/worldClock.test.js` | 新增 | 1847 | `a90213a56bb4abe5d89c67a06403a14d2a9d15307b909ff641200aaf5bb2fde8` |

`git status --porcelain` 在提交前只列出三个新实现文件（另有被排除的 `docs/plans/fast/…`）：

```
?? docs/plans/fast/global-world-clock-master/
?? src/main/resources/static/world-clock.css
?? src/main/resources/static/world-clock.js
?? src/test/js/worldClock.test.js
```

提交后 `git status --porcelain` 仅剩 `?? docs/plans/fast/global-world-clock-master/`。

## 必跑命令与结果（均在工作区根目录执行）

| # | 命令 | 退出码 | tests | pass | fail |
|---|---|---|---|---|---|
| 1 | `node --check src/main/resources/static/world-clock.js` | 0 | — | — | — |
| 2 | `node --test src/test/js/worldClock.test.js` | 0 | 41 | 41 | 0 |
| 3 | `TZ=UTC node --test src/test/js/worldClock.test.js` | 0 | 41 | 41 | 0 |
| 4 | `TZ=America/Los_Angeles node --test src/test/js/worldClock.test.js` | 0 | 41 | 41 | 0 |
| 5 | `node --test src/test/js/meetingConfirmation.test.js src/test/js/meetingConfirmationAssets.test.js` | 0 | 33 | 33 | 0 |

未运行 Maven 构建、全量 JS 套件、格式化或 lint（按 brief 限定；全量门禁属 02）。

## 覆盖映射（计划验收标准 → 测试用例）

| 不变量 | 断言位置（`worldClock.test.js` describe） |
|---|---|
| I-1 | T3：挂载位置/单例/DOM 唯一、原九个导航身份与顺序不变、组件未在原导航挂监听、切 view 无请求无新计时器、destroy 后可重挂载 |
| I-2 | T3 `fitHeader`：required=1390；available=1389→图标态（accessible name 保留）、1390→完整态（1px 容差）、1391→完整态、同宽三次重算不抖动、`nav.clientWidthOverride=40` 不参与判据 |
| I-3 | I-3：fake now +60s 只改 `#worldClockNow`/`#worldClockHeaderTime`（`2026-09-17 15:01:30`/`09月17日 15:01`），输入与六区结果不变；use-now 更新输入与结果、关闭重开保留选择 |
| I-4 | T1：计划「确定性换算样例」14 行逐格硬编码（含伦敦/柏林/纽约/洛杉矶/东京/首尔/加尔各答/加德满都、跨日、DST 跳时与回拨、+05:30/+05:45），`epoch === Date.UTC(2026,8,17,7,0)`；`TZ=UTC` 与 `TZ=America/Los_Angeles` 两次运行同结果 |
| I-5 | T2：单次 GET + 成功缓存；常用六区计划顺序与缺失条目降级；全球目录顺序分页（6 行/页、`第 X / Y 页`、边界 disabled、clamp）；中文/别名/IANA/UTC 偏移搜索；`UTC+5:30` 不含 `+05:45`；全角 `UTC＋05:30` 归一；区域与搜索取交集；切 common/global 清空并回第 1 页；空态互斥 |
| I-6 | I-6：loading 中重复 open 不重复请求；失败文案与 `.world-clock-error`、显式重试（失败后仅按钮可再请求）；retry 进行中 disabled 且不重复；非数组/无合法条目 → error；认证隐藏 abort + 零计时器 + 晚到回包不写 DOM + 恢复只重启一个计时器且新 open 重新请求；visibilitychange 暂停/立即刷新；destroy 解绑全部监听/observer/timer/请求与新增 DOM |
| I-7 | I-7：transport 只有目录相对 GET（无 `//`、无 method）、期间交互不产生新请求；恶意 label 只产出文本节点、无元素、无 inline style；生产源码无静态目录数据（除六个常用 IANA id）、无 localStorage/fetch/ES module 语法 |
| I-8 | I-7 末例 + T4：`index.html` 不含 `worldClockTrigger`/`worldClockPanel`（01 未激活页面） |
| I-9 | T4：CSS 与计划 `WORLD_CLOCK_CSS` 标记块逐字一致（LF、单末尾换行、无 CR）；规则全部限定 `world-clock` 作用域；`.topnav.world-clock-header` 含 `flex-wrap: nowrap` 与 640/440/coarse/dark/reduced-motion/icon-only/focus-visible/disabled 分支；三份模板常量与计划标记块逐字一致；初始隐藏态挂载后 trigger/panel 与计划模板结构一致（无缺节点/额外 class/inline style）；结果行与 ROW 模板结构一致且文本只走 textContent；Escape/关闭按钮回焦 trigger、外部点击与焦点移出关层但不抢焦点、内部 pointerdown/focusin 不关层 |

## 测试敏感度验证（mutation 抽查，均临时改后还原）

对被测实现逐一注入偏差后运行 `node --test src/test/js/worldClock.test.js`，确认失败（即测试不是空断言）：

| 注入 | 结果 |
|---|---|
| CSS 改 `min(490px, calc(100vw - 24px))` → `520px` | 1 fail（T4 CSS 字节） |
| 投影改用后端 `raw.offsetLabel` | 4 fail（T1/T2） |
| `parseBeijingInput` 去掉 −8h | 22 fail |
| 移除 `loadCatalog` 的 loading 守卫 | 1 fail（I-6 重试防重） |
| 移除 `open()` 的 `idle` 守卫（失败后自动重试） | 1 fail（I-6 显式重试） |
| `fitHeader` 阈值改 998（永不降级） | 1 fail（T3 图标态） |
| 面板模板 `aria-labelledby` 改名 | 2 fail（T4） |
| 移除认证隐藏清理 | 1 fail（I-6） |
| 移除 `requestSeq` 递增（晚到回包隔离） | 1 fail（I-6） |
| `applySelection` 改为读当前时钟（覆盖用户选择） | 1 fail（I-3/I-5） |
| `destroy` 不移除 trigger/panel | 3 fail |
| `tick` 不再刷新钟面 | 1 fail（I-3） |
| 跳过组件自身 UTC 偏移数值筛选 | 1 fail（T2 新增用例） |

首轮 mutation 暴露两处“测试未咬住实现”的缺口，已收口：
1. `loadCatalog` 的 loading 守卫与 `onRetryClick` 的守卫互为冗余 → 删除 `onRetryClick` 守卫，重试按钮直接绑定 `loadCatalog`，防重只有一处权威，并由 I-6 用例咬住。
2. 组件自身的 UTC 偏移数值筛选与宿主 `filterZones` 的数值分支结果重合 → 新增一条注入“仅子串匹配”的 filterZones 的用例，使组件自带的数值筛选可观测。

## 偏差与实现说明

1. **模板常量末尾换行**：计划 fenced block 末尾的单个换行属于 Markdown fence 产物。模块常量按 `<html>` 原文逐行 `join("\n")`（不含末尾空行），测试比对时对标记块剥掉恰好一个末尾换行；标签、class、id、属性、层级、顺序完全一致（其它字符零改动）。
2. **模板即唯一 DOM 来源**：模块用 `document.createElement("template")` + `innerHTML` 解析 S-4 常量并 `cloneNode(true)`（行模板每行克隆一次），DOM 由契约字符串生成而非二次手写结构；动态内容一律 `textContent`，无外部 HTML 注入。
3. **失败后不自动重试**：`open()` 仅在 `catalogStatus === "idle"` 时请求目录；失败后重开只显示错误与「重新加载时区」，由 I-6「失败显式重试，不无限重试」推出。
4. **焦点移出关层**：按 T3 要求新增文档级 `focusin` 处理：焦点移到 panel/trigger 之外时关闭浮层且不 blur 外部焦点、不回焦 trigger。
5. **公开 API 多一个只读 `templates`**：`Object.freeze({trigger, panel, row})`，供契约测试与 02 比对使用；`mount/parseBeijingInput/projectZones` 之外未引入任何框架式接口。
6. **文案**：计划明确给出的三条状态文案（`正在加载时区…`、`时区加载失败，请重试。`、`当前浏览器有 N 个目录时区暂不可用。`）与空态文案逐字采用；计划未固定的计数/页码文案实现为 `共 N 个时区`、`第 X / Y 页`。
7. **测试 DOM stub**：为真实事件路径与布局读数自建最小 DOM（innerHTML 解析、template、事件冒泡、`getBoundingClientRect`/`clientWidth`、computed style、observer/timer/rAF 假体）；缺失 id 返回 `null`。stub 类型额外实现了 `util.inspect.custom`：调试期发现“断言失败时 node:test 序列化环形 stub 图会退化到分钟级”，故元素身份断言改为布尔比较并给 stub 加紧凑打印（仅测试基础设施，不影响生产实现）。
8. **未执行**：未运行 Maven/全量 JS 套件/格式化/lint；未做浏览器人工验收（A-1～A-8 属 02 之后的阶段），DOM stub 绿测不构成布局通过结论。
9. 未修改 `index.html`、`app.js`、`styles.css`、`meeting-confirmation.*` 及任何后端/Maven 文件；未 push/merge/rebase/amend。
