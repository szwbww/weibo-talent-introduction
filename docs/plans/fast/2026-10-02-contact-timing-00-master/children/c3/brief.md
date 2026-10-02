# Child Brief — c3（子计划 03：收发信箱紧凑所在地与推荐时间）

本 brief 是 c3 的唯一执行契约入口；计划文件是逐字实现契约（含 S-1/S-2/S-3 逐字样式与骨架），本 brief 只补充已验证后端接口、环境事实与流程约束。

## 任务与身份

- 工作区（唯一允许改动的位置）：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-02-contact-timing-00-master`
- 分支：`fast/2026-10-02-contact-timing-00-master`
- child_base_sha：`ab8e4cb82bace355c06412d260a42b5fbe6cce74`（c2 已验证终态；证据 commit `f912511`）
- 批准计划：`docs/plans/2026-10-02/contact-timing-03-compact-ui.md`
- 只读证据（按需查阅）：`docs/plans/2026-10-02/contact-timing-evidence/`
- 执行报告（你写，**不要提交**）：`docs/plans/fast/2026-10-02-contact-timing-00-master/children/c3/execution.md`
- 必须使用 `execute-p` 流程；只允许一个本地实现提交，提交信息：`feat(fast-p): implement c3`

## 授权文件（5，见计划「变更文件清单」）

1. `src/main/resources/static/mailbox-chat.js`
2. `src/main/resources/static/styles.css`（尾部逐字追加 S-1/S-2/S-3）
3. `src/main/resources/static/index.html`（11 个资源版本键统一）
4. `src/test/js/mailboxChatBehavior.test.js`（扩展行为用例与 harness）
5. `src/test/js/contactTimingStyle.test.js`（新增）

`mailbox-chat.css` 必须逐字节不变；不得改 `app.js`、`mailbox-chat.css` 或计划外文件。

## 已验证后端接口（c1+c2 四门 PASS，c3 只读消费，逐字冻结）

- `GET /api/mail/contact-locations/countries` → `{sourceVersion, sourceUrl, defaultPolicy, countries:[{code,labelZh,defaultZoneId,zones:[{id,labelZh}]}]}`（目录顺序即资源顺序）。
- `GET /api/mail/contact-locations/{contactId}` → `ContactLocationView{contactId, configured, countryCode?, countryLabel?, zoneId?, effectiveZoneId?, zoneLabel?, usingDefaultZone}`；未配置 200 `configured:false` 且后三个 zone 字段为 null、`usingDefaultZone=false`；contact 不存在 404。
- `PUT /api/mail/contact-locations/{contactId}`，body `{countryCode, zoneId:null|string}` → 200 已持久化配置；401/400/404。
- `GET /api/mail/contact-locations/{contactId}/timing` → `{location: ContactLocationView, recommendation: null | {mode:"WORK_HOURS"|"REPLY_PATTERN", localStart, localEnd, beijingStart, beijingEnd（ISO_OFFSET_DATE_TIME 含完整日期）, sampleCount, replyDayCount, historyDays:180, historyTruncated, recentSamples:[{receivedAtBeijing, receivedAtLocal}]（≤8）, calculatedAt（ISO_INSTANT）}}`。

## 环境/代码事实（2026-10-02 基线）

- 传输 seam：`mailbox-chat.js` 内的 `hostApi()`（= `hostFn("api")`，`(url, options?) => Promise`），既有调用均用相对 `/api/...` 路径；沿用该约定，不新增全局 fetch 抽象。
- `index.html` 当前 11 个 `?v=` 键为 `20261001-email-verification-allowlist`（计划文本里的 `20260930-manual-template-reference` 是陈旧描述；以实际值为准）。全部统一改为 `20261002-contact-timing`，数量与位置不变。
- 已确认 `src/test` 中没有任何测试钉住旧键字面量（测试从 index 提取键），因此换键不会额外破坏既有测试。
- 插入点：`.mc-header-meta`（`renderHeaderMeta`，计划引用的 1819–1822 赋值点）末尾追加 `.contact-timing`；不得用 MutationObserver 补丁，不得新增卡片/标题区。
- 左列表“演示国家标签”不属本轮范围（总计划明确：只落实右侧紧凑状态行）。
- Node v25.7.0。本 child 不需要 Maven/MySQL；**不要运行全量 Maven**（run 级 Maven 回归由 c3 验证者执行）。

## 必需命令（逐字执行；本 child 无需 JAVA_HOME）

1. `node --check src/main/resources/static/mailbox-chat.js`
2. `node --test src/test/js/mailboxChatBehavior.test.js src/test/js/contactTimingStyle.test.js src/test/js/mailboxChatStyle.test.js src/test/js/mailboxTemplateReferenceStyle.test.js src/test/js/mailboxCalendarIntegration.test.js`
3. `node --test src/test/js/*.test.js`

基线：`node --check` 通过；全量 JS 1273 pass / 250 suites / 0 fail（`baseline/js-full.txt`）。新用例应使数量增加且 0 fail；既有数量不得减少。

## 必须保持的不变量（计划为准，摘要）

- I-1：`renderHeaderMeta` 保留原有节点语义，只在末尾追加一组 `.contact-timing`；标签/状态更新触发的重新渲染后仍只出现一次、入口不丢。
- I-2：真值在后台——前端不推断国家、不重算习惯、不写 localStorage/storage；所有日期按显式时区（Asia/Shanghai / effectiveZoneId）格式化，不依赖设备时区；跨北京日期的结束显示“次日”。
- I-3：GET/PUT/目录加载都捕获 `contactId`、请求序号与实例身份；`timing.seq` 管 GET、独立 `dialogSeq` 管表单加载/保存；切专家/账号上下文/unmount 使旧响应失效；A 的保存迟到不得写 B 界面或重开 A 弹窗。
- I-4：取消/Escape 零 PUT；换国家清空 zone 草稿；空值传 null；保存中禁重复提交；失败保留草稿与错误；PUT 成功后才 GET timing；GET 失败显示指定文案且不保留旧时间。
- I-5：原生 `<dialog>`（showModal）直接挂 `document.body`；最多一个本功能弹窗；关闭只移除自有节点，不清共享 portal；焦点行为按计划。
- I-6：`mailbox-chat.css` 字节不变；新样式只在 `styles.css` 尾部逐字追加 S-1/S-2/S-3；无 inline style；11 个资源键一致；不新增 bundle/app.js 入口。

## 返回

`READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`、commit SHA、命令摘要（命令→退出码/计数）、报告路径。不要 push、merge、rebase、amend、reset；不要修改主工作区 `/Users/lukai/IdeaProjects/weibo-talent-introduction`；不要提交 `docs/plans/fast/**`。
