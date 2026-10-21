# 称呼与签名补全、全局默认设置及发送确认：开发总计划

状态：详细计划已编写，尚未实施。日期：2026-10-21。仓库基线：`b0d2f278a9dddc0be0dcf6fbd2e9b5a947c8a135`；工作区已有其他任务改动，实施不得覆盖。[代码证据回执](reply-identity-evidence.md)保存逐行代码、检索命令/输出和源码SHA256。此文区分既有事实与拟新增方案，不把交互预览当生产功能证据。

## 需求描述

1. 人工回复工具栏「↗ 跟进邮件」旁加「补全称呼与签名」。读取全局默认回复片段，按当前专家及真实发件账号渲染。缺项补全，已有内容不覆盖。
2. 提供「默认设置」：选择并编辑默认SALUTATION/CLOSING，预览当前邮件效果；所有专家共用，现有草稿不被自动改写。
3. 发送前比较本次正文首尾；发现缺失、不一致或无法判断，弹出确认。**点击“确认发送”继续按本次提交正文发送；“返回修改”或ESC保留草稿。身份提醒不要求输入确认文字。**

必须保持：原账号/线程锚点规则、附件/会议/QA/RAG、富文本、持久草稿及发送幂等；原有STRONG风险确认仍独立有效。已有占位符/附件/认证等业务失败不是本轮新增身份提醒，不改变其校验语义。

范围外：批量首封、自动回复、模板直接发送入口的新增身份检查；旧邮件修复/撤回；个人/专家级默认；LLM姓名推断；全站改版。修改共享片段正文会自然影响引用该ID的模板、修改默认会影响已有默认frame消费者；这项共享数据影响需在设置说明中明确。

## 关键不变量

### I-1: 默认设置单一来源
- Rule：使用reply_snippet.is_default和主正文，不创建第二份配置表、浏览器永久默认或固定片段ID。
- Applies to：03写、01读、02展示/编辑。
- Violation consequence：跨专家默认漂移。
- 来源：用户要求；E01–E05。

### I-2: 补全不覆盖、比较不改稿
- Rule：仅明确MISSING且canInsert=true才插入；其他状态保留正文。历史引文不作为当前称呼/签名，但排除引文必须有服务端真实源依据。
- Applies to：01分析、02插入；最终发送使用原链final bodies。
- Violation consequence：错名叠加或悄悄重写邮件。
- 来源：用户要求；K-reply-identity-followup-quote-boundary。

### I-3: 可确认的提醒
- Rule：新增身份finding全部NORMAL；取消无SMTP，明确确认后可继续。不得把不确定身份做成不能绕过的新门槛。
- Applies to：01最终发送；02两个发送入口。
- Violation consequence：违背用户明确修正。
- 来源：用户明确要求；E06:690–696。

### I-4: 当前上下文和保存边界
- Rule：账号/专家/锚点由服务端真实发送规则决定；异步回包不能应用到另一个草稿；配置保存、正文补全、确认发送是三个不同动作，不相互隐式执行。
- Applies to：03事务；01解析；02owner/revision/seq及草稿链。
- Violation consequence：姓名串用、跨专家草稿覆盖或误发。
- 来源：K-mailbox-draft-cache-owner-capture；K-manual-send-fingerprint-complete-identity。

## 现状审计

|已核实事实|代码证据|方案结果|
|---|---|---|
|已有两类默认片段，未有每类型唯一默认约束|E01、E02:115–120|复用现有默认；新设置原子修正所选两类型的默认|
|update会replace变体；setEnabled原无事务|E02:221–278、E04|新保存窄写，原写入口统一有序锁；不调用默认空variants更新|
|服务端已有NORMAL确认、STRONG文字确认|E06:673–696|复用safetyWarningConfirmed，身份提醒不用STRONG|
|最终text/HTML各自渲染|E06:575–605、E08|分别检查，不能只检查text；不声称跨ES读取原子性|
|跟进首行直接复用历史Dear/Hi|E12:6714–6726|历史称呼不能作为当前正确身份依据|
|引文是On…wrote纯文本，不一定有blockquote|E12/E12b|核实真实源尾部；只靠HTML标签不足|
|app.js有两处同名发送函数|E13|两个入口共享同一确认展示语义|
|原dialog缺native cancel处理，取消发送还alert失败|E13:20423–20466、:18210–18212|实现ESC收敛及正常取消返回|
|通用异常handler catch-all|E07:82–84|新409通过controller局部handler及HTTP测试证明，不能仅假定ResponseStatusException生效|
|邮箱按钮局部padding覆盖为11px、非primary无shadow|E15/E16|样式契约按最终级联，不能仅抄全局12px|

存储审计：03负责reply_snippet/content_variant；01记录发送副作用与mailbox_reply_draft链；02记录前端草稿Map与replySnippets缓存。完整schema、写读路径、交互点均在对应子计划，原始检索回执E03/E05/E19/E20/E24/E25/E27。跨阶段交互：全局配置→默认/按ID读者；实际锚点→签名；inspect→编辑器→原草稿保存→最终发送；两个旧发送入口→统一确认。

## 实现方案

按依赖顺序执行；编号沿用原文件名，**03 → 01 → 02**。每阶段单独实现和验证，后阶段不得反向成为前阶段上线依赖。

|阶段|详细计划|实际清单（含已批准修订）|独立交付|
|---|---|---:|---|
|1|[03 全局默认设置](reply-identity-03-global-settings.md)|5|认证GET/PUT、双项原子保存、旧写路径协调；旧UI仍可用|
|2|[01 后端校验与只读预览](reply-identity-01-backend.md)|11|inspect/defaults-preview；既有UI可通过原安全确认继续发送；只读精确SENT重放|
|3|[02 前端按钮与设置/确认](reply-identity-02-frontend.md)|6|缺项补全、全局编辑与预览、软确认文案/取消语义；退役冲突旧工具栏断言|

每阶段不超过2个独立子系统，新增持久字段0。若执行发现清单外文件确实必改，先根据新代码证据修订对应子计划，不借本计划扩大到邮箱整体重构。

落地步骤关联：03遵守I-1/I-4；01遵守I-1–I-4；02遵守I-1–I-4及其S-1–S-4样式契约。仅03新增配置写路径，读者见03 P1–P4；02补全文本通过原草稿链写入，由恢复/发送读取，见02 P2。

验证/发布：先测试环境03、01、02逐阶段验收，再生产发布；本次只写计划，不部署业务。未完成02时，01仍可用旧通用确认界面，显示可能较通用但可确认继续。回退代码不回写历史邮件；若需回退全局默认/正文，先保存新旧设置快照，再由设置API明确恢复，不能部署脚本覆盖运营期间改动。

## 变更文件清单

本总计划是调度文档，不额外授权修改业务文件；实现白名单是各子计划`## 变更文件清单`。沿用分阶段执行；01阶段因已批准的非草稿INBOUND精确SENT重放修订，窄范围增加既有幂等服务文件，不合并邮箱整体重构。

|计划文档|实施范围|
|---|---|
|reply-identity-03-global-settings.md|reply repository/service/controller及两个测试|
|reply-identity-01-backend.md|identity service/controller、PendingMailOperationService、ManualReplySendAttemptService及七个测试文件|
|reply-identity-02-frontend.md|mailbox-chat.js/app.js/index.html/styles.css、mailboxReplyIdentity.test.js及mailboxChatBehavior.test.js最小过时断言移除|

## 验收标准

- I-1：03事务/并发/GET指纹测试、跨用户同默认；01下一次分析读取新默认；02保存不改草稿。
- I-2：缺项/匹配/错名/裸签名/重复/引用/text与HTML差异；第二次补全无重复；真实浏览器验证保留链接/列表/附件/会议块。
- I-3：两个发送入口确认后成功一次；取消/ESC不发送、不alert失败、不关闭草稿；原STRONG流程回归。
- I-4：账号/锚点交叉验证、切专家/范围/登录人/编辑器版本迟到结果丢弃、持久草稿恢复、SENT重试不再投递。
- 机器验证命令在子计划内。MySQL事务验证必须启用mysqlIt并实际运行；普通mvn test不证明被禁用的IT通过。JS stub不替代真实浏览器样式与DOM验收。
- [计划审查记录](reply-identity-plan-review.md)只验证计划结构和证据引用，不能算新功能测试通过。实施未开始，所以不生成“实现通过”结论。

## 人工验收清单

本节为总体验收，子计划的A-n是各阶段权威清单。人工验收开始时再导出同目录`*-acceptance.md`，当前不提前打勾。测试均用虚构联系人和自有邮箱。

### A-1: 全局默认到两名专家
- 前置条件：测试联系人Daniel Morgan、Elena Martin；发件人Alex Lee；两个登录会话。后台有称呼`Dear Dr ${expertFamilyName},`、签名`Best regards,`换行`${senderName}`。
- 操作步骤：1. 甲打开默认设置，将称呼改为`Hi ${expertName},`并保存。2. 乙依次打开两专家，正文写`Thank you for your reply.`，点击补全。3. 再点击一次。
- 预期结果：分别为`Hi Daniel Morgan,`和`Hi Elena Martin,`；签名均Alex Lee；重复点击字符数不变；保存设置前已有正文未被自动替换。
- 覆盖：需求1/2、I-1/I-2/I-4、配置→补全→草稿路径。

### A-2: 错名软确认与保持原文
- 前置条件：两入口各准备`Dear Alice,`开头、`Best regards,`换行`Bob`结尾的测试草稿。
- 操作步骤：1. 点击补全。2. 点击发送并ESC。3. 再次发送点“返回修改”。4. 第三次发送点“确认发送”。
- 预期结果：补全不覆盖Alice/Bob；前两次取消0封、无失败alert；确认后自有邮箱收到1封，仍为Alice/Bob；不输入确认文字。
- 覆盖：需求3、I-2/I-3；两个入口一致。

### A-3: 原功能与跨专家隔离
- 前置条件：准备粗体/链接/列表/附件/真实跟进引文草稿，另备会议及QA/RAG草稿；开启网络节流。
- 操作步骤：1. 发起补全立即切另一个专家。2. 返回原专家补全、保存、刷新。3. 向自有邮箱发送各测试草稿并重提成功请求。4. 触发原STRONG场景后取消。
- 预期结果：迟到结果不进入其他专家正文；原富文本/锚点/附件/会议内容保留；刷新可恢复；每份草稿只收到1封；STRONG取消不发。
- 覆盖：I-4及必须保持项；对应子计划细化验收。

### A-4: 设置及确认UI
- 前置条件：1200px与390px宽浏览器。
- 操作步骤：1. 查看工具栏、设置弹窗、发送确认弹窗。2. 用键盘Tab/ESC。3. 对照02样式契约逐项核对。
- 预期结果：补全紧随跟进按钮；12px/32px按钮、6px间距、邮箱水平padding11px；设置窄屏单列且不透明白底；取消能恢复操作；旧共享弹窗仍显示原文案。
- 覆盖：02 S-1–S-4。

## 修正记录

- A2（2026-10-21，用户「批准 继续」）：依据本计划 I-4 的发送幂等保持项、实现方案的清单外必改先修订规则，以及 01 的 I-6，批准 01 增加 ManualReplySendAttemptService.kt 的只读精确完成查询、附件完整payload与新增身份提醒顺序修订；详见子计划 T6/A1 和暂停证据 `docs/plans/fast/reply-identity-plan/children/01/execution.md:24-57`。03→01→02 顺序、持久字段及其他范围均不变。

- A4（2026-10-21，用户「批准 继续」，记录时间10:49:28+08:00）：依据实现方案“清单外必改先修订”规则和02 S-1，批准02增加既有 mailboxChatBehavior.test.js 的最小过时断言移除授权，保持跟进行为回归与新按钮顺序；对应子计划A3及暂停证据 `docs/plans/fast/reply-identity-plan/children/02/execution.md:31-53`。不改变产品范围或阶段顺序。
