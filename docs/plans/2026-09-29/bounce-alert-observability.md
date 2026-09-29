# 01 发件账号硬退告警可核对

状态：待批准；依赖无。基线/完整存储调用点见 [总计划](bounce-repair-master.md) 与 [代码回执](bounce-repair-code-audit.md)。

## 需求描述

- O-1：告警显示近7天HARD数、成功发信数、百分比，并解释两个时间窗口。
- O-2：低于20封显式返回样本不足；前端无偏高徽标，不能把无告警解释成零退信。
- 必须不变：HARD/SOFT入库、7天/20封/>5%门槛；仅提示不暂停；启停/自动暂停/管理按钮、绑定数、今日额度。
- 不做：退信分类、历史状态修复、调整门槛、统计表、图表、账号页重排、CSS改造。

## 关键不变量

### Invariant I-1: 一次计算、同一cutoff
- Rule: 一个账号的一次toResponse仅调用一次统计方法；其中两次COUNT复用同一since=now-minusDays(7)。分子按HARD+received_at，分母按OUTBOUND+SENT+sent_at。数字和比率来自同一计算结果；不以todaySentCount替代。这里不是承诺两个SQL拥有数据库事务级一致快照，不新增隔离机制。
- Applies to: BounceRateMonitorService、所有复用toResponse的列表/详情/管理响应。
- Violation consequence: 数字不一致、重复查询或口径漂移。
- 来源: original；K-bounce-rate-monitor-denominator-starvation。

### Invariant I-2: 返回值与门槛
- Rule: sentCount<20 → rate=null、sampleSufficient=false、high=false；否则rate=hard/sent、sampleSufficient=true、high=rate>0.05。1/20不告警；2/20告警。比率不封顶100%，因为分子分母非同一批。
- Applies to: 服务快照、新响应字段、UI。
- Violation consequence: 低样本被显示成0%，或阈值偏移。
- 来源: original。

### Invariant I-3: 兼容与无副作用
- Rule: calculateHardBounceRate仍返回Double，低样本仍-1.0；isHardBounceRateHigh、checkAndWarn的公开签名/自定义windowDays与threshold行为不变。统计/GET不得调用任何save/update/pause/resume。管理接口原有写行为不扩张。
- Applies to: BounceRateMonitorService、AutoMailReplyService的checkAndWarn调用、控制器。
- Violation consequence: 自动收信调用失效，或刷新页面暂停发送。
- 来源: K-bounce-rate-monitor-denominator-starvation；V117。

### Invariant I-4: 接口和展示契约
- Rule: 保留hardBounceRateHigh；追加hardBounceCount:Long、sentCount:Long、hardBounceRate:Double?、hardBounceSampleSufficient:Boolean、hardBounceWindowDays:Int。比例是0～非限定上界的小数，不是百分数。9/160返回0.05625，显示5.63%。空/缺字段不得显示NaN、Infinity、伪造0/0；旧API仅有high=true时回退原徽标文案。
- Applies to: DTO、app.js、测试。
- Violation consequence: 百分比乘100两次、兼容失效或虚构数字。
- 来源: original。

### Invariant I-5: 口径与资源版本
- Rule: title明确“近7天退信事件 / 近7天成功发信”“可能不是同一批邮件”；11个已有版本资源统一换为20260929-bounce-alert，未带版本的task-modal-runtime不动。
- Applies to: app.js、index.html。
- Violation consequence: 用户误读指标或浏览器继续显示旧代码。
- 来源: K-frontend-cache-key-triad。

## 样式契约

### S-1: 状态徽标原位替换
- 复用：styles.css:1054的.badge、:1074的.badge.warn；禁止修改CSS。亮色warning=#d97706，背景rgba(217,119,6,0.08)，边框rgba(217,119,6,0.2)；暗色沿用:10030的#fbbf24及背景0.12/边框0.26。padding=2px 8px，font=11px/600，line-height=1，radius=999px，无新增阴影及hover/active/disabled规则。
- 新增class：无。无inline style，无新表格列。
- DOM：现有statusCell第三段条件徽标替换为以下骨架，数字经过数值校验，title用escapeHtml；前两段“启用/禁用”“自动暂停”不动。
```html
<span class="badge warn" title="近7天退信事件9条 / 近7天成功发信160封＝5.63%；两者可能不是同一批邮件；阈值>5%，至少20封；仅提示，不影响自动发送">永久退信偏高 9/160（5.63%）</span>
```
- 高比率但统计字段缺失：原位使用原徽标，不生成数值；低样本：无第三段元素。旧class其它调用点不受影响，不更改全局规则。
- index.html只改资源URL的v参数，不改变任何组件DOM。

## 现状审计

### 存储、读写和交互
- bounce_record：V29/V43，消息ID唯一，无外键；BounceCollectionService.ingest唯一save，在线收信/回填均调用它。BounceRateMonitorService、MailMonitoringService、BounceController、OperatorStatusReconcileService与MailRecordRepository联合监控读取。IP-1：所有HARD事件含无contact事件 → 分子；不能为了关联专家而inner join丢行。（来源: K-bounce-record-has-no-foreign-key）
- mail_record：V1/V15/V23/V24，sent_at可空，sender_account_code可空；ManualOutreachTxHelper、ManualExpertMailService、ManualReplySendAttemptService、AutoMailReplyService、MeetingScheduleService写入。完整调用点见代码回执repository-access。IP-2：各外发路径SENT → 分母；FAILED/INBOUND/窗口外记录不计。
- mail_sender_account：V1/V28/V117；管理、额度、暂停及最后发送时间写入详见总计划。IP-3：listAccounts/getAccount/管理接口toResponse → DTO → loadAccounts；本阶段仅追加内存响应字段，不新增持久化字段。
- BounceRateMonitorService:16-49为权威门槛；MailMonitoringService:267的独立监控接口没有同样的低样本契约，本阶段不改变它，不能把该接口返回值当本页验收值。
- AutoMailReplyService:955调用checkAndWarn；IP-4：统计计算的兼容返回值供日志用，不触发暂停。
- MailSenderAccountController:96-126当前对每账号只取布尔值；DTO:228起无数量字段。
- 完整存储schema/所有grep调用点引用总计划审计，确认本阶段没有新写路径。

### 前端样式盘点
app.js:3279-3290当前片段（逐字）：
```javascript
const hardBounceRateHigh = account.hardBounceRateHigh === true;
// statusCell前两段省略；以下第三段为实际改动区域
+ (hardBounceRateHigh
    ? ` <span class="badge warn" title="近7天硬退率超过5%（已发至少20封）；仅提示，不影响自动发送">硬退率过高</span>`
    : "");
```
styles.css:1054/1074原规则（逐字）：
```css
.badge {
    display: inline-flex;
    align-items: center;
    padding: 2px 8px;
    border-radius: 999px;
    font-size: 11px;
    font-weight: 600;
    font-family: var(--font-body);
    line-height: 1;
    background-color: var(--surface);
    color: var(--text-muted);
    border: 1px solid transparent;
}
.badge.warn {
    background-color: var(--warning-bg);
    color: var(--warning);
    border-color: var(--warning-border);
}
```
font-body实值为'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Helvetica Neue', sans-serif。
index.html:11-15、2323-2328资源旧key为20260929-discovery-schedule；全src/test精确搜索该key无匹配。因此不机械追加旧知识中列出的九个历史测试文件。（来源: K-frontend-cache-key-triad，已以当前代码核实）

## 实现方案

1. I-1/I-2/I-3：在BounceRateMonitorService.kt同文件增加简单统计data class与getStats(accountCode,windowDays=7)；字段hardBounceCount/sentCount/rate/sampleSufficient/windowDays/high。高低比率计算复用；旧方法委托，保留-1兼容。没有缓存、Clock框架或新查询。
2. I-1/I-4：MailSenderAccountController.toResponse改块体，取一次stats，再填原DTO及五个新字段；list/get/create/update/enable/disable/reset/resume均复用。更新所有现有MVC mock，不只更新第一个测试。
3. I-2/I-4/I-5/S-1：app.js仅改loadAccounts徽标组装；校验finite数字和sampleSufficient，toFixed(2)只在展示时使用，告警按API布尔值而非四舍五入后值决定；index.html统一换版本key。
4. I-1～I-5/S-1：三个既有测试文件补边界、字段、DOM、兼容/缓存断言。单测先失败后实现；不改repository、CSS、监控另一接口。

## 变更文件清单

| 文件 | 用途 |
|---|---|
| src/main/kotlin/com/weibo/talentintroduction/mail/service/BounceRateMonitorService.kt | 统计结果与兼容方法 |
| src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailSenderAccountController.kt | 一次统计及DTO |
| src/main/resources/static/app.js | 数字徽标 |
| src/main/resources/static/index.html | 11处版本参数 |
| src/test/kotlin/com/weibo/talentintroduction/mail/service/BounceRateMonitorServiceTest.kt | 门槛/查询/兼容 |
| src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailSenderAccountControllerMvcTest.kt | 完整字段和低样本 |
| src/test/js/senderBindingDisplay.test.js | loadAccounts输出及资源key |
| src/test/js/providerUndeliveredColumn.test.js | 守卫断言收敛为服务商分布链路（修订 A1） |
共8文件（含 A1 追加的守卫测试收敛），后端统计/API与前端两个子系统；无存储新增字段。

## 验收标准

- I-1：9/160断言9、160、0.05625；capture两个repository的since并assertEquals；每次toResponse每个COUNT恰一次。窗口查询沿用原SQL，不加入contact过滤。
- I-2：0/0、2/19 → null/false/false；1/20 → .05/true/false；2/20 → .1/true/true；30/20 →1.5，允许150.00%。
- I-3：旧calculate/checkAndWarn低样本返回-1；自定义阈值/天数仍有效；统计类无writer调用；GET不新增任何写操作。
- I-4：MVC JSON断言字段名/类型/null（不能遗漏null）；VM执行loadAccounts并断言9/160（5.63%）；旧payload、缺失、null与非finite数不生成NaN/伪0。
- I-5：tooltip完整含口径/门槛/仅提示；所有既有带v资源一致换新key，没有新增带版本资源。
- S-1：diff无styles.css；DOM仍同一span.badge.warn，没有inline style、新class/列；原按钮data-action和暂停状态不变。
- IP-1/2/3：测试库插入窗口内HARD/成功SENT/FAILED/INBOUND/窗口外行，SQL计数和GET/API/UI一致；无contact的HARD仍计分子。
- IP-4：checkAndWarn只返回/日志，不调用账号暂停服务。
- 命令（JDK11环境）：`mvn -DskipNodeTests=true -Dtest=BounceRateMonitorServiceTest,MailSenderAccountControllerMvcTest test`；`node --test src/test/js/senderBindingDisplay.test.js`；`node --check src/main/resources/static/app.js`。全阶段完成另跑`mvn test`，mysql-it仅在已配置隔离测试库时运行，不连接生产。

## 人工验收清单

测试数据仅限隔离库；可用SQL在UI新建的专用账号和专用campaign/contact下添加MailRecord/BounceRecord行，必填字段见V1/V29：mail_record填expert_contact_id、direction、mail_type、sender_account_code、send_status、sent_at；bounce_record填sender_account_code、唯一bounce_message_id、bounce_type、received_at。所有时间用固定测试cutoff减1天，额外窗口外用减8天。不得用真实生产账号造数据。

### A-1: 数字与口径
- 前置条件: 专用账号160条OUTBOUND/INTRODUCTION/SENT，9条HARD（其中1条original_expert_contact_id为空），另加1条FAILED、1条INBOUND、1条8天前HARD。
- 操作步骤: 1. GET /api/mail/sender-accounts；2. 打开“邮箱账号”；3. 悬停告警。
- 预期结果: hardBounceCount=9、sentCount=160、hardBounceRate=0.05625；“永久退信偏高 9/160（5.63%）”；title写“两者可能不是同一批邮件”；退信列表仍HARD。
- 覆盖: O-1、I-1/I-2/I-4/I-5、IP-1/2/3。

### A-2: 样本不足与临界值
- 前置条件: 两专用账号分别2条HARD/19封SENT、1条HARD/20封SENT。
- 操作步骤: 1. GET账号列表；2. 刷新页面。
- 预期结果: 第一行hardBounceRate=null、hardBounceSampleSufficient=false；第二行hardBounceRate=.05、hardBounceSampleSufficient=true；两行hardBounceRateHigh=false且均无偏高徽标。
- 覆盖: O-2、I-2/I-4。

### A-3: 原有管理与只读
- 前置条件: 测试库账号enabled=1，自动暂停账号另设auto_send_paused=1；记录今日额度、绑定数及暂停状态。
- 操作步骤: 1. 连续刷新两次；2. 查看按钮；3. 在测试账号上禁用再启用；4. 查看测试收信任务日志。
- 预期结果: 刷新不改启用/暂停/额度/绑定；仍有查看/编辑/测试/启停/重置/删除，暂停行有恢复发送；启停动作仍生效；日志仍可提示hard bounce，告警不新增暂停。
- 覆盖: I-3、IP-3/4、所有原有行为回归。

### A-4: 样式与缓存
- 前置条件: 浏览器先打开旧测试版本并保留缓存；切换新测试版本。
- 操作步骤: 1. 普通刷新；2. 开发者工具检查app.js请求v；3. 亮/暗主题观察告警和其它徽标。
- 预期结果: v=20260929-bounce-alert；新数字可见；亮色#d97706，暗色#fbbf24；徽标padding2px 8px、字号11px、圆角999px；原表格列数和按钮位置不变。
- 覆盖: I-5/S-1、IP-3。

人工验收开始时从本节导出bounce-alert-observability-acceptance.md，保留A编号、验收人、日期、结果/备注；现在不生成空勾选表。

## 修订记录

- A1（2026-09-29，人工批准）：白名单追加 `src/test/js/providerUndeliveredColumn.test.js`。原因：本计划 I-4 冻结账号响应字段名 `hardBounceCount`，`app.js` 的 `loadAccounts` 必须读取该字段；该文件既有的全局源码否定断言（`!appJsSource.includes("hardBounceCount")`）与本条不可同时成立。其来源计划 `docs/plans/2026-09-02/provider-undelivered-column.md` 的 I-6「范围限定（必读）」只约束服务商分布链路，故本次只将该断言收敛回该范围：断言 `renderMonitoringProviderDistribution` 与 `renderMonitoringCards` 的 `worstUndeliveredProvider` 链不再引用 `hardBounceCount`/`softBounceCount`，不放松 `ProviderStatRow` 链路的原有保护，也不禁止账号页消费同名字段。产品实现无需返工。
