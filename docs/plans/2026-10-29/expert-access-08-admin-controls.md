# 专家分级权限 · 管理端最终下拉筛选与待处理角标

状态：DRAFT，待批准实施。基线：`main@b15bb069f43f89c45ee6859dba04a0baff5d774d`。仅计划，不代表实现/测试已通过。

证据：[审计总表](expert-access-evidence/audit.md)、[逐字源码E01–E37](expert-access-evidence/source-excerpts.md)、[schema迁移链](expert-access-evidence/schema-chain.md)、[检索收据](expert-access-evidence/store-usage.txt)、[SHA256](expert-access-evidence/sha256.json)。

前置：07及既有draft-review-05-admin；此步才对外提供FULL授权切换完整入口。本文件是可执行边界，先读[总计划](expert-access-master.md)的协议/最终UI与依赖修正。

## 需求描述
管理员在专家头部直接选权限；原更多筛选最上选择可写/待审专家；⋯显示本人待处理邮件数。

必须保持：原七字段/一个Apply/Reset/搜索/分页/桌面手机布局、原个人草稿tab、同框审核和翻译样式保持。

不做：不新增顶部按钮/大授权panel/独立审核弹窗；不在前端伪造计数、筛当前页代替SQL。

## 关键不变量
### Invariant I-1: 三态单一事实
- Rule: 无授权行=NONE；有行access_level仅READ/FULL，NOT NULL默认READ。旧授权迁移为READ。canRead由等级派生，不增send/attachment/tag等冗余布尔列；既有无body PUT只确保可读，不把FULL降为READ；显式新body {accessLevel:"READ"|"FULL"}才切等级。非法值400。
- Applies to: ExpertMailReadAccessService全部读写、列表批量投影、授权controller
- Violation consequence: 旧授权被意外升级或旧页面把FULL降级
- 来源: original

### Invariant I-7: 计数按处理人、数量单位明确
- Rule: 我的待处理=MANUAL_REVIEW且pending_owner_username=session，排除SIMULATOR_NOOP/不存在账号，保留禁用真实邮箱；user1再受可读/FULL准入约束。图标角标复用当前账号pendingBadge.manualReviewTotal（邮件条数，含既有挂起排除），不受列表搜索/分页影响；0隐藏，失败显示不可用而非0。待处理tab数字仍为专家数，待审核是SUBMITTED草稿数。count/page/explain共用条件，All保留历史邮件。监控、来信汇总不变。
- Applies to: 会话聚合/排序/分页、挂起、旧队列、顶栏及更多图标
- Violation consequence: 列表与角标不一致或user1邮件仍进admin待处理
- 来源: K-mailbox-inbound-source-authority

### Invariant I-9: 最终筛选和授权控件位置
- Rule: 管理员原更多筛选完整保留，第一项新增专家筛选（全部/user1可写/待审核）；原七字段顺序随后、Reset/Apply各一个，与原筛选AND，应用重置页码。header只用直接select切NONE/READ/FULL，保存中禁用，失败回滚。刷新旁不新增筛选。
- Applies to: mailbox-chat原FILTER_FIELDS、节点迁移、授权控件
- Violation consequence: 再次覆盖原面板或出现大型权限卡
- 来源: K-relocated-control-refresh-owner

### Invariant I-10: 上下文隔离与最小宿主
- Rule: 保留user1邮箱壳/姓名显示；READ继续独立只读详情，FULL右栏复用conversation-only MailboxChat。加载权限失败默认不可写；切换专家、focus/visible/刷新、降级/撤权、logout/unmount时作废在途结果及可写编辑器/附件/游标/权限缓存。无正文localStorage，无admin全局启动链。
- Applies to: MailboxReadOnly、MailboxChat受限挂载、app宿主
- Violation consequence: 晚响应恢复旧授权或串专家草稿
- 来源: K-mailbox-readonly-needs-lifecycle-audit

### Invariant I-11: 审核是明确依赖，不伪造已实现
- Rule: READ审核流程沿既有draft-review计划；FULL新人工稿走原mailbox_reply_draft。已提交审核稿不因升级自动发送/自动撤回；已审核稿对user1始终不可改（即使FULL），要发送新内容需新建人工稿。管理员仍在同一富文本框审核/人工回复，审核通过不自动发，批准后对话更新须确认。待审入口只搬进原popover顶部，原admin草稿tab含义不变。
- Applies to: MailReviewDraftService、两端宿主、pendingReviewOnly查询
- Violation consequence: 绕过审批锁或把原型当生产功能
- 来源: original

## 样式契约
### S-2：管理员直接授权select
- 替换E20的按钮+badge，仍在`.mc-actions`末尾。原头部/姓名/排期/材料DOM保留。
```html
<select class="mc-access-select" data-action="mc-set-user1-access" aria-label="user1专家权限">
  <option value="NONE">user1：未授权</option>
  <option value="READ">user1：查看＋草稿</option>
  <option value="FULL">user1：可写</option>
</select>
```
- 新CSS逐字复制；全局既有class不修改：
```css
.mail-chat .mc-access-select{max-width:180px;height:34px;padding:0 9px;border:1px solid #b8cff5;border-radius:7px;background:#fff;color:#3564b9;font:inherit;font-size:12px;cursor:pointer}
.mail-chat .mc-access-select:hover{background:#edf3ff;border-color:#93b4ec}
.mail-chat .mc-access-select:focus-visible{outline:2px solid #3b82f6;outline-offset:2px}
.mail-chat .mc-access-select:disabled{opacity:.45;cursor:not-allowed}
```
- 加载中/保存中disabled；失败用既有mc-inline-error提示并恢复服务器已确认值。无需确认/保存按钮；授权本身不发送邮件。列表FULL标记复用`<span class="mc-badge">user1：可写</span>`。禁止大卡片、额外权限面板、inline style、未列class。
### S-3：原更多筛选第一项与邮件角标
- 完整保留E18/E19：七个原字段及原Reset/Apply/关闭按钮；复用mailbox-chat.css:78–100的mc-icon、mc-filter-popover、mc-filter-fields、mc-field、mc-field-wide规则。430px面板/两列/gap14px/padding18px 20px/桌面select34px/圆角7px均不改，手机沿原媒体规则（既有mobile-core min-height:44px优先），不增加覆盖规则。
- 在`#mailboxFilterFields`第一个子元素插入；选项数字来自服务器，未知不显示伪0：
```html
<label class="mc-field mc-field-wide">专家筛选
  <select id="mailboxFilterExpertScope" aria-label="专家筛选">
    <option value="ALL">全部专家</option>
    <option value="USER1_FULL">user1可写（2位）</option>
    <option value="PENDING_REVIEW">待审核（3条）</option>
  </select>
</label>
```
- 数字2/3仅说明文案格式，不写死。当前view的真实筛选状态仍用原mc-filter-summary呈现，不同时显示原active-filter数字角标。
```html
<button class="mc-icon mc-pending-trigger" type="button" data-action="mc-more-filters" aria-label="更多筛选，3条待处理邮件" aria-expanded="false" aria-controls="mcFilterPopover">⋯<span class="mc-pending-count" aria-hidden="true">3</span></button>
```
- 新CSS逐字复制：
```css
.mail-chat .mc-pending-trigger{position:relative}
.mail-chat .mc-pending-count{position:absolute;top:-5px;right:-5px;min-width:18px;height:18px;box-sizing:border-box;padding:0 4px;border:2px solid #fff;border-radius:9px;background:#ea8a23;color:#fff;font-size:10px;font-weight:600;line-height:14px;text-align:center;pointer-events:none}
.mail-chat .mc-pending-count[hidden]{display:none}
```
- 0设hidden；1–99显示原数，>99显示99+且aria-label/title含精确数。刷新/加载失败不冒充0，不清掉筛选面板。既有mc-filter-count规则保留供旧使用点，不就地改其颜色/意义。禁止inline style、第二Apply、刷新旁下拉、未列class。

## 现状审计
E18–E21、audit§5：现有FILTER_FIELDS单一节点迁移，原mc-filter-count是已用筛选个数，新计数必须独立。E20授权为button+badge，S-2明确替换。原draft-review-05曾计划顶部按钮；本子计划为最终覆盖，审核逻辑本身不改。X1/X3/X10/X11/X12。

全量读写清单是本计划的组成部分：[审计§1–§5](expert-access-evidence/audit.md)。其中每张表列明schema、显式写者、FK/后台写入、读取与X交互点；未列为本步变更的writer保持原逻辑，不能因grant扩权改变后台任务。

### 前端样式盘点
逐字DOM/CSS基线见E17–E27/E33/E34；复用class、实值token与目标DOM在上节S条款中逐项固定。本步不修改任何既有共享CSS规则，不需要扩改其其他使用点；新增规则仅作用声明class。新增/修改DOM均须归属于本步S条款。

## 实现方案
1. mailbox-chat将readAccess Boolean状态改三态level，header按S-2直接select；change立即PUT body（NONE用DELETE），saving禁用。捕获user/contact/generation；成功刷新列表和统计，失败恢复已确认等级并显示错误，晚回包不得改另一专家（I-1/I-9/I-10、S-2）。
2. FILTER_FIELDS最前新增expertScope项，mount时只插入一个节点；原七个字段迁移/还原逻辑保留。ALL清除scope；USER1_FULL/PENDING_REVIEW映射06参数，应用后page=0；Reset将scope回ALL并按原规则保留tab/q；原旧reviewOnly状态与新scope只有一个authority（I-9/I-11、S-3）。
3. 更多图标新增S-3角标，数据复用当前session pendingBadge.manualReviewTotal，由app的既有成功刷新路径通知当前host；首次mount主动读取，mark/reopen/挂起/撤权/刷新后重读；不要先开popover才取数。停止显示原筛选数badge，原filter-summary仍可读已选条件。总数与当前页无关；请求失败角标不可冒充0（I-7/I-9/I-10）。
4. 两个scope选项counts来自06.filterCounts；pending-review仍右侧同专家/同编辑器，不弹草稿列表。逐条待审标记、翻译小文字按钮、批准后确认规则沿原review05。不再生成mailboxReviewPendingBtn或刷新旁下拉（I-11/S-3）。
5. app/index移除旧顶部review按钮生成/绑定路径（若前置计划按本修正已不生成，则不造再删）；保留审核模块加载。更新旧按钮契约测试，不弱化成仅检索新文本；样式只新增S-2/S-3，既有mc-icon/mc-filter-count规则不改（I-9/S-2/S-3）。

仅以下文件可变更；每个任务同时受其引用的不变量/样式契约约束。共享文件按本步前置版本接续，不覆盖上一阶段改动。

## 变更文件清单

| # | 路径 | 工作 |
|---|---|---|
| 1 | `src/main/resources/static/mailbox-chat.js` | header直接select、popover第一项、原筛选保持、邮件角标 |
| 2 | `src/main/resources/static/mailbox-chat.css` | S-2/S-3新增规则 |
| 3 | `src/main/resources/static/app.js` | badge刷新成功发布统一事件/宿主入口，无新增顶部控件 |
| 4 | `src/main/resources/static/index.html` | 移除旧审核顶部按钮占位并统一缓存键 |
| 5 | `src/test/js/mailboxMailReadAccess.test.js` | 旧按钮契约改为select成功/回滚/并发 |
| 6 | `src/test/js/mailboxChatBehavior.test.js` | 原popover字段/AND/角标及审核入口 |
| 7 | `src/test/js/mailboxChatStyle.test.js` | 新增CSS逐字契约及桌面手机布局 |
| 8 | `src/test/js/mailReviewAdmin.test.js` | 既有两条待审入口改真实popover专家范围＋单Apply；fixture/query按expertScope/filterCounts接续，保留同框/历史/审核/翻译/确认/发送/跨页行为 |
| 9 | `src/test/js/authFlow.test.js` | logout闭包实际消费的pending-count seq/producer环境接线；保留flush失败/成功顺序与reader隔离，证明当前session迟到计数失效 |
| 10 | `src/test/js/mailboxServerDrafts.test.js` | 既有真实logout/导航fixture接续producer环境，保留失败不logout/reload、flush顺序及latest-intent行为，不弱化断言 |

合计 **10 文件**（原7+1个待审入口及2个logout直接消费fixture，仍≤10）；最多2个紧耦合子系统。新增共享store字段：0。

## 验收标准
- I-1：真实MySQL升级旧grant，断言均READ；并发重复PUT仅一行；旧PUT不降FULL；非法等级400且无写入；非admin管理403。
- I-7：真实SQL验证count/page/explain同口径；两人混合来信、禁用真实邮箱、SIMULATOR、挂起、跨页；admin/user1角标分别等于各自未挂起邮件数，专家数另算。
- I-9：真浏览器验收七原字段+首项、仅1个Apply，AND组合/Reset/退出重进；select成功/失败/迟到响应；图标邮件计数不变成筛选数。
- I-10：延迟A请求后切B/降级/注销；晚回包不挂载可写框、不回填正文。首次mount/刷新/focus/visible均不请求admin全局API。姓名UI无邮箱字段。
- I-11：审核四态/评论/历史/退回/撤销与旧方案回归；升级FULL不能改APPROVED；新人工稿可发送；审核通过与确认对话更新均不自动SMTP。
- S-2：新增CSS与上节逐字一致；实际DOM符合骨架，复用规则无diff；无新inline style/未声明class；按本步UI人工项核对桌面/手机/disabled。
- S-3：新增CSS与上节逐字一致；实际DOM符合骨架，复用规则无diff；无新inline style/未声明class；按本步UI人工项核对桌面/手机/disabled。

门禁：node --test src/test/js/mailboxMailReadAccess.test.js src/test/js/mailboxChatBehavior.test.js src/test/js/mailboxChatStyle.test.js；新增fixture独立fresh执行node --test src/test/js/mailReviewAdmin.test.js及node --test src/test/js/authFlow.test.js src/test/js/mailboxServerDrafts.test.js；全量node --test src/test/js/*.test.js；1440/390px双账号A-8/9/10/14；最后JDK11 mvn test及全部mysqlIt/migrationIt针对性门禁。

测试环境使用隔离MySQL/测试SMTP与临时附件目录；不得把mysqlIt连生产库。跳过/未运行必须记NOT_RUN，不能写PASS。

## 人工验收清单
### A-2: 等级切换与旧授权兼容
- 前置条件: 升级前已有甲READ授权；管理员与user1各开一浏览器。
- 操作步骤:
  1. admin选甲的权限select为可写。
  2. 刷新user1。
  3. 发送一次旧无body PUT，再刷新。
  4. 非admin请求授权接口。
- 预期结果: 旧授权初始READ；设置后FULL；旧PUT后仍FULL；user1改授权403。加载中select禁用，保存失败恢复上一个确认值。
- 覆盖: I-1/I-2/I-9/S-2/X1

### A-8: 原更多筛选完整保留
- 前置条件: 有不同账号、标签、日期和方向的测试邮件，甲FULL；至少一份SUBMITTED审核草稿。
- 操作步骤:
  1. admin点搜索右侧⋯。
  2. 核对专家筛选位于最上。
  3. 选择user1可写并叠加账号/方向/标签/邮箱/关键词/日期。
  4. 应用、Reset、切tab再返回。
- 预期结果: 原七字段均存在；Apply只有一个；筛选AND并回第1页；Reset恢复全部专家范围并保留原tab/q语义；刷新旁无新下拉。
- 覆盖: I-9/S-2/S-3/X11

### A-9: 角标不是筛选数或草稿数
- 前置条件: admin有3封未挂起待处理邮件、2位待处理专家、5份待审草稿；user1有1封待处理。
- 操作步骤:
  1. admin看⋯角标，user1看待处理入口。
  2. admin设置两个普通筛选，切页。
  3. admin处理完3封后刷新。
- 预期结果: admin图标3；user1待处理显示1位专家；admin待处理tab2、待审选项5条；设置两个筛选后图标仍3；清零后角标隐藏。
- 覆盖: I-7/I-9/S-3/X3/X11

### A-10: 待审核筛选与同框审核
- 前置条件: 已有草稿审核计划落地；甲有2份SUBMITTED、乙1份；admin有未发个人草稿。
- 操作步骤:
  1. admin在更多筛选选待审核并应用。
  2. 选甲、选择一份用户草稿，翻译并批准。
  3. 导入一封甲新来信，再点击发送。
- 预期结果: 左侧甲/乙各标2/1条；右侧对应专家，只有一个编辑框；批准不发送；对话更新先确认且仍需手动发送；原admin个人草稿不被覆盖。
- 覆盖: I-9/I-11/S-3/X12

### A-12: 晚响应与撤权清理
- 前置条件: 甲FULL、乙READ；浏览器网络工具延迟甲消息/权限/保存回包。
- 操作步骤:
  1. user1打开甲后立即切乙。
  2. admin撤销甲授权，释放延迟响应。
  3. user1刷新、切后台再回来、注销重登。
- 预期结果: 乙不出现甲正文/附件/发送按钮；甲在重新校验后消失；注销后无正文；不请求邮箱配置/任务列表；无恢复旧权限。
- 覆盖: I-2/I-10/S-4/X10

### A-14: UI尺寸及空错状态
- 前置条件: 桌面1440px和手机390px；准备长姓名、长标签、无标签和模拟接口失败。
- 操作步骤:
  1. 对照本计划S-1–S-4观察。
  2. 打开popover，切权限，模拟失败与无标签。
- 预期结果: popover桌面430px、手机不溢出；首项select桌面34px、手机沿既有min-height:44px；header权限select最大180px；角标18px；无大权限卡；null标签显示“标签暂不可用”，[]不显示；失败不伪造授权成功。
- 覆盖: I-3/I-9/I-10/S-1/S-2/S-3/S-4

本节为权威清单；进入人工验收时才导出同名前缀-acceptance.md，本次不生成衍生表。涉及尚未到本步的UI，使用同会话API执行等价动作并在最终08再次通过UI验证。

跨阶段人工项引用的其他I/S条款，以总计划同编号定义为准；本步实现仍严格限定本文件清单。

## 修正记录
- 2026-10-30：持续授权下纠正既有pendingBadge总邮件wire字段为manualReviewTotal（不是total），完整DTO/app/grep证据见fast-p children/06/execution.md Epoch2；保留SSOT与已有调用方，不加alias/shim、不改变个人未挂起邮件数、0隐藏、失败不可用和最终S-3样式/交互。
- 2026-10-31：完整src消费者核查证明mailReviewAdmin.test.js的既有两条入口仍直调setReviewPendingOnly/mock旧query，完整grep/稳定行为收据见fast-p children/08/execution.md Epoch1。持续授权下7→8仅该直接消费fixture，保留实际同框/审批/历史/翻译/确认/显式发送/跨页断言；改真实popover expertScope=PENDING_REVIEW＋单Apply/准确filterCounts，前端旧state/setter/export删除、不留alias或第二authority，后端兼容参数不变。新增fixture独立fresh及原全量/浏览器/MySQL门禁不减。
- 2026-10-31：真实full JS1985/1983pass/2fail/0skip证明authFlow/serverDrafts两个实际执行logout闭包的直接consumer缺新增pending计数seq/publisher环境，完整grep/原失败见fast-p children/08/execution.md Epoch2；持续授权下8→10仅两fixture接线，保留flush失败无logout/reload、成功顺序与latest-intent行为，以真实producer/consumer验证当前session迟到计数失效，不加生产缺seq/helper fallback或mock-echo断言。额外mailboxSuspensionFollowup请求已因既有calendar DOM通知惯例撤回，不加该文件；新增fixturefresh独立及原全量/JDK11/精确MySQL/实际1440/390验收不减。
- 2026-10-31：真实1440/390测量scope34/44，原styles.css mobile-core min-height44覆盖基础34，与产品base相同blob及完整grep/测量见fast-p children/08/execution.md Epoch3。持续授权下仅澄清S-3/A-14桌面34/手机沿原44最小触控高度，保留原手机媒体/共享CSS，禁止新增覆盖或修改未授权styles.css；产品范围/字面新增CSS与其余验收不变，旧证据保留、新身份fresh完成全部门禁/实际验收。
