# Child 10 执行报告 — 收发件箱专家聊天布局

- 执行结果：READY_FOR_VERIFICATION
- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-materials/docs/plans/2026-09-07/10-mailbox-chat-frontend.md`
- Plan SHA-256: `548f1bee55dc589c26a4a7ab2120639adcb40d5a57819c5b292586afc3ffb02a`
- Execution ID: 上述路径@sha256
- Execution epoch: NEW（无同身份历史执行证据）
- Executor: Impl10
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-materials`
- Worktree ID: `…/weibo-talent-introduction-fast-mailbox-materials@fast/mailbox-materials@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/weibo-talent-introduction-fast-mailbox-materials`
- 分支: `fast/mailbox-materials`
- Pre-execution code SHA: `020e8392f89ebbf5ed38b95060605055a67167c5`（child 09 Code head；HEAD 当时 4675007 = 09 evidence）
- Post-execution code SHA（本提交）: 见文末 commit SHA
- 身份复核：开始与结束时 plan/worktree identity 一致（plan 未变、worktree 未变）。

## 授权文件（10）与实际变更

| # | 文件 | 操作 | 说明 |
|---|---|---|---|
| 1 | `src/main/resources/static/mailbox-chat.js` | 新增 | IIFE 组件，`window.MailboxChat = {mount, unmount, version}` |
| 2 | `src/main/resources/static/mailbox-chat.css` | 新增 | S-3 CSS 逐字复制（8454 字节） |
| 3 | `src/main/resources/static/app.js` | 修改 | loadMailbox 聊天守卫分支 + setView 卸载 + 宿主适配器块（+234 行） |
| 4 | `src/test/js/mailboxChatBehavior.test.js` | 新增 | 21 用例（I-1..I-5 行为 + 宿主守卫 + 材料抽屉适配器） |
| 5 | `src/test/js/mailboxChatStyle.test.js` | 新增 | 5 用例（CSS 逐字字节比对 + class 白名单 + 无 inline style + 命名空间） |
| 6 | `src/test/js/mailboxExpertGrouping.test.js` | 未改动 | 兼容分支断言全部继续通过，无行为变化（见下） |
| 7 | `src/test/js/mailboxDateDefault.test.js` | 未改动 | 同上 |
| 8 | `src/test/js/taskDrilldown.test.js` | 未改动 | 同上 |
| 9 | `src/test/js/unmatchedDetailResolvedAction.test.js` | 未改动 | 同上 |
| 10 | `src/test/js/unmatchedQaReplySource.test.js` | 未改动 | 同上 |

**为什么 #6–#10 未改动**：brief 与计划均要求「只在聊天激活分支改变行为时调整、旧兼容分支断言保留」。这些测试的沙箱均不含
`window.MailboxChat`，loadMailbox 的守卫（`typeof mailboxChatEligible === "function" && mailboxChatEligible()`）使它们精确落在兼容分支；
全新激活分支由 mailboxChatBehavior.test.js 单独覆盖（组件存在+无 taskExecutionId→挂载；taskExecutionId→旧平铺端点；脚本缺失→旧行为）。
5 个既有文件 51 条断言 + 全量 730 条 JS 断言在改动后原样全绿，证明旧断言无需改写、也未删除任何发送校验。新激活分支的覆盖测试并入新增文件，
未产生「为通过测试而删校验」类改动。

## 命令证据（全部在工作树根目录、最终状态新鲜执行）

| 命令 | 结果 | 证据 |
|---|---|---|
| `node --check src/main/resources/static/mailbox-chat.js && node --check src/main/resources/static/app.js` | PASS | exit 0，SYNTAX_OK |
| `node --test src/test/js/mailboxChatBehavior.test.js src/test/js/mailboxChatStyle.test.js` | PASS | tests 26, pass 26, fail 0 |
| `node --test src/test/js/*.test.js` | PASS | tests 730, suites 136, pass 730, fail 0 |
| `JAVA_HOME=…/zulu-11.jdk/Contents/Home mvn test` | PASS | EXIT=0，BUILD SUCCESS；Surefire `Tests run: 3218, Failures: 0, Errors: 0, Skipped: 9`；exec-plugin Node 阶段 `tests 730 / pass 730`（日志 /tmp/mvn10-final.log） |

说明：首个后台 mvn 运行发生在最后一批 JS 重构之前，故最终提交状态又新鲜执行了一次完整 `mvn test`（上表），结果以本次为准。

## 关键不变量核查

- **I-1（单信操作）**：消息卡片仅 INBOUND_PROCESSING+MANUAL_REVIEW 渲染「标记已处理」；已处理渲染只读「已处理」徽标；外发只渲染真实
  sendStatus（SENT=已发送 / FAILED=发送失败）；原文/清洗/翻译/邮件标签/技术信息原位 `<details class="mc-mail-extras">` 展开（translatableBody
  全局委托即用；Message-ID/账号进 mail-technical-detail/grid）；附件默认折叠计数、展开前 ≤3 名称 +「查看全部附件」走 child-08 材料抽屉；
  标记成功走既有 `POST /api/mail/unmatched-inbound/{processingId}/mark-resolved`（openActionDialog("mark-unmatched-resolved")
  收集 resolvedBy/note，服务端写 operator_action_log MARK_INBOUND_RESOLVED），成功后仅局部刷新该消息状态、该专家 pending 计数与全局
  角标（refreshUnmatchedBadge），**不重建编辑器、零下载请求**（测试断言）。行为测试 21 项全绿。
- **I-2（固定上下文/顺序）**：mc-scroll 内固定 设置→mc-timeline→workbench(默认折叠,无 open)→manual(默认 open)→logs(折叠)（mailboxChatStyle/behavior 断言）；
  工作台经 `mcHostMountWorkbench(hostEl, processingId, …)` 绑定 `summary.latestInbound.processingId`（真实处理 id，不是最后可见消息）；
  `autoBootstrap:false` ⇒ 默认折叠 0 生成请求、展开不自动生成；测试断言展开前 mount 数=0、展开一次 mount=1、切专家旧实例 unmount=1。
  宿主显式传 host 元素，不复用 `[data-trust-reply-live-host]` 全局 querySelector。
- **I-3（发送与草稿）**：人工区默认展开；草稿按 `contactId:processingId:accountCode` 存内存 Map，跨专家切换恢复（测试断言主题/正文还原）；
  新来信 + 已编辑草稿 → `openActionDialog("confirm")` 提示选择目标：确认→主题按新来信预填（正文/QA 保留）、取消→保留原目标且同目标不重复打扰；
  发送 payload 复用 mcHostSendRichReply（app.js 镜像 submitManualRichReply 的 MANUAL_SEND_SAFETY_BLOCKED 两轮确认 + strong 键入 + 文案），
  服务端校验/QA 审计 authority 不变；采用→发送保留 ragFactCodes/ragCorpusFingerprint/edited=false 载荷（测试断言请求体）；
  成功才清草稿并刷新；失败保留全部输入、按钮恢复可用；无历史 readiness 审批门。
- **I-4（纯发件专家）**：received=0/sent>0 行显示「收 0 · 发 N」+ 待专家回复徽标且出现在列表；failed-only（收0发0失败>0）无徽标、绝不混入
  待专家回复；无来信 ⇒ 工作台区 mc-note「暂无专家来信，暂不能生成回复」（0 mount/0 生成），人工区为说明 +「选择模板发送跟进邮件」
  （`mcHostOpenFollowUp`→openContactInList 既有 COMPOSE_TEMPLATE 流程，无自由 subject/body 伪造，无 processingId 捏造）。
- **I-5（范围与共用）**：仅专家入列表、每页 20（conversations?page&size=20）、pager「第 X/Y 页 · 共 N 位」；chat 列表/消息全部 child-07 API；
  关注 PUT/DELETE `/api/mail/mailbox/conversations/{id}/follow` 乐观更新+失败回滚+在途禁用（测试断言含失败回滚星标）；
  材料按钮与「查看全部附件」均调 app.js `mcHostOpenMaterials` → `ExpertMaterials.configure + mount({host, contactId, mode:"drawer"})`
  同一 contactId 共享 store（不复制材料 DOM）；taskExecutionId 钻取与脚本缺失分支保持原 table/group 行为（守卫测试断言旧端点与参数）；
  未匹配入口（monitoring/标签/面板）不受影响（legacy 委托与端点未动）。
- **S-3 CSS 逐字**：落地 `mailbox-chat.css` 与计划 10 S-3 块字节一致，并与 ui-style-contract S-3 块字节一致（双文档比对测试）；
  SHA-256 `90a125bf45bcdbc23cfca5a48d410c044fe6a7e1d30b4e5555d0b0c299a28651`，8454 字节；mailbox-chat.js 模板全部字面量 class 通过白名单
  （mc-* 在 mailbox-chat.css，其余复用类在 styles.css）；无 inline style；规则全部 `.mail-chat` 命名空间内。DOM 层级/aria 由 behavior 测试断言。
- **不变量之外**：未触碰 index.html（注册归 child 11）；未改 trust-reply-workbench.js 内部；未删任何旧处理 API/发送校验。

## 偏差与说明

1. **5 个授权既有测试文件零改动**（见上）——计划要求「仅在新激活分支改变行为处调整」；实测这些文件全部精确处于兼容分支且断言原样通过，
   新增分支覆盖放入了 mailboxChatBehavior.test.js。提交文件数 5 < 10，仍严格 ⊆ 授权清单。
2. **「查看全部附件」按本信 source 预筛**：child-08 现公开 API（mount/unmount/subscribe/getState/setSelection/requestTransfers/configure）
   无程序化 source/sourceId 初始筛选入口，且 expert-materials.js 不在本子计划授权文件内；实现为打开同 contactId 共享 store 的
   ExpertMaterials 抽屉（drawer 内「来源来信」筛选由运营自选），保证 store 同源一致（I-5/G-4 两 host 同 store 契约成立）。
   若要求抽屉首屏即按来信来源过滤，需 child-08 追加 mount 选项，属计划外扩展 → 留给后续修订。
3. 无来信专家的跟进按钮落在收发件箱视图的既有联系人详情模板发件流程（该流程本身位于 contacts 视图；打开即切换视图，为既有
   COMPOSE_TEMPLATE 操作面）。
4. 本项不涉迁移/数据库；无需 MigrationIntegrationTest/MySQL IT（总计划要求仅在涉迁移时启用）。

## 剩余关注

- 浏览器人工验收（A-1/A-2/A-3 四视口/键盘）仍须在 11 统一注册（index.html script/css + 缓存键）后于真实页面执行；本项交付可部署但激活开关在 child 11。
- 草稿为内存态：离开收发件箱视图即随 unmount 清空（产品语义：草稿绑定当前会话目标；防跨会话陈旧目标误发）。如运营要求跨会话草稿持久化，
  属新增持久化契约，另行计划。

## Next Action

- READY_FOR_VERIFICATION → 运行 verify-p。
