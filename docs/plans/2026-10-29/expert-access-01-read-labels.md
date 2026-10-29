# 专家分级权限 · 只读标签展示

状态：DRAFT，待批准实施。基线：`main@b15bb069f43f89c45ee6859dba04a0baff5d774d`。仅计划，不代表实现/测试已通过。

证据：[审计总表](expert-access-evidence/audit.md)、[逐字源码E01–E37](expert-access-evidence/source-excerpts.md)、[schema迁移链](expert-access-evidence/schema-chain.md)、[检索收据](expert-access-evidence/store-usage.txt)、[SHA256](expert-access-evidence/sha256.json)。

前置：无；先行小切片。本文件是可执行边界，先读[总计划](expert-access-master.md)的协议/最终UI与依赖修正。

## 需求描述
只读专家可见专家标签和邮件标签，不能修改。此步可独立交付。

必须保持：姓名显示、仅邮箱tab、附件禁用、授权列表分页、403清理不变。

不做：不开放写权限，不新增接口/数据库字段，不改管理端标签编辑器。

## 关键不变量
### Invariant I-2: 精确接口与资源权限同时成立
- Rule: user1仍是user1，仅邮箱导航。认证开启时默认拒绝未列出的HandlerMethod+HTTP method；允许handler后仍查目标专家当前权限。READ只能读授权邮件/标签与审核草稿；FULL只操作该专家。不得按URL前缀、按钮可见性或请求体operatorName授权。管理员授权接口永不向user1放行。
- Applies to: AuthInterceptor/Policy、目标守卫、body专用校验、app请求闸门
- Violation consequence: 横向越权或变相全局管理员
- 来源: K-session-auth-does-not-imply-mail-authorization

### Invariant I-3: 标签可见不等于可写
- Rule: READ显示summary.expertTags和真实来信tags；空数组不显示占位，expertTags=null显示“标签暂不可用”。全部标签文字转义。READ无新增/删除/自动打标控件，手工调用写API返回403且DB/ES不变；FULL才可改当前专家标签。
- Applies to: 用户列表、头部、邮件标签、ExpertIndexController、InboundMailSummaryController
- Violation consequence: 只读用户修改数据或标签丢失
- 来源: K-expert-tag-editor-shared-render-contract

### Invariant I-10: 上下文隔离与最小宿主
- Rule: 保留user1邮箱壳/姓名显示；READ继续独立只读详情，FULL右栏复用conversation-only MailboxChat。加载权限失败默认不可写；切换专家、focus/visible/刷新、降级/撤权、logout/unmount时作废在途结果及可写编辑器/附件/游标/权限缓存。无正文localStorage，无admin全局启动链。
- Applies to: MailboxReadOnly、MailboxChat受限挂载、app宿主
- Violation consequence: 晚响应恢复旧授权或串专家草稿
- 来源: K-mailbox-readonly-needs-lifecycle-audit

### Invariant I-13: 不扩大成全局管理
- Rule: FULL范围为当前专家人工邮件/附件资料/人工档案/标签/备注位置/排期/现有单专家状态管理；全局邮箱账号配置、批量发送、任务/AI训练、模板配置、全局专家搜索、人工接管分派、会议视频库不开放。自动收发/晋级/标签流程不受user1交互授权守卫影响。READ附件仍不可查看下载，邮件正文邮箱不做脱敏承诺。
- Applies to: 精确路由表、用户宿主菜单、后台任务
- Violation consequence: 无关功能扩权或后台任务中断
- 来源: original

## 样式契约
### S-1：只读标签
- 复用：`styles.css:3828–3861`的inbound-tag-chip/qa/custom/inactive，`:4766–4800`的expert-tag；`mailbox-chat.css:120–121`的mc-tag-row和标签尺寸；完整原文见E21–E23。复用class，不改任何既有规则，无新CSS。
- DOM（每个label用textContent；列表和头部相同；消息行只用于INBOUND_PROCESSING）：
```html
<div class="mc-tag-row" data-role="readonly-expert-tags"><span class="expert-tag" title="完整标签">标签</span></div>
<div class="mc-tag-row" data-role="readonly-mail-tags"><span class="inbound-tag-chip qa">邮件标签</span></div>
```
- 空数组不生成行，null在相同区域用`<p class="mc-note">标签暂不可用</p>`；未知标签显示原值。无x、input、button、data-action写入口。禁止inline style、未声明新class；不得调用共享可编辑tag renderer再用CSS隐藏删除按钮。

## 现状审计
E04/E17证明数据已有、用户DOM未渲染；标签schema和全部写读路径见审计§1/§4。这里只增加投影消费者，不写ES/邮件标签。前端基线E17/E21–E23，X2/X8/X10。

全量读写清单是本计划的组成部分：[审计§1–§5](expert-access-evidence/audit.md)。其中每张表列明schema、显式写者、FK/后台写入、读取与X交互点；未列为本步变更的writer保持原逻辑，不能因grant扩权改变后台任务。

### 前端样式盘点
逐字DOM/CSS基线见E17–E27/E33/E34；复用class、实值token与目标DOM在上节S条款中逐项固定。本步不修改任何既有共享CSS规则，不需要扩改其其他使用点；新增规则仅作用声明class。新增/修改DOM均须归属于本步S条款。

## 实现方案
1. `mailbox-readonly.js`在renderList/renderIdentity/renderConversation加入S-1节点，用textContent；`expertTags=[]`无行、null固定不可用；消息tags使用现有返回的label/type/active信息，不调用全局tag API。遵守I-2/I-3/I-10/I-13。
2. `mailboxReadOnly.test.js`增加标签数据驱动与无写请求、无删除控件、切专家迟到回包用例（I-3/I-10、S-1）。现有403/注销测试保留。
3. `index.html`将已有全部同值资源键统一改为本次交付唯一键；执行前反查固定键测试。本轮检索没有测试固定20261029-user1-mail-access，若基线漂移新增固定键测试先修订文件清单（I-10、S-1）。

仅以下文件可变更；每个任务同时受其引用的不变量/样式契约约束。共享文件按本步前置版本接续，不覆盖上一阶段改动。

## 变更文件清单

| # | 路径 | 工作 |
|---|---|---|
| 1 | `src/main/resources/static/mailbox-readonly.js` | 列表/头部/来信只读标签，null与转义 |
| 2 | `src/main/resources/static/index.html` | 统一已有静态资源缓存键 |
| 3 | `src/test/js/mailboxReadOnly.test.js` | 标签与只读生命周期回归 |

合计 **3 文件**；最多2个紧耦合子系统。新增共享store字段：0。

## 验收标准
- I-2：真实AuthWebConfig+MVC逐个运行路由矩阵：NONE/READ/FULL/跨专家/admin；403之前SMTP、ES和文件流读取调用为0；未列端点仍403。
- I-3：同一专家含ES标签和QA/CUSTOM邮件标签；断言READ可见且不存在编辑/删除节点，直接POST/DELETE均403；空/null/恶意HTML分别验证。
- I-10：延迟A请求后切B/降级/注销；晚回包不挂载可写框、不回填正文。首次mount/刷新/focus/visible均不请求admin全局API。姓名UI无邮箱字段。
- I-13：管理员全路径回归；user1无法打开其他tab及全局端点。定时收信与自动标签仍执行，READ附件响应仍空且直链403。
- S-1：新增CSS与上节逐字一致；实际DOM符合骨架，复用规则无diff；无新inline style/未声明class；按本步UI人工项核对桌面/手机/disabled。

门禁：node --test src/test/js/mailboxReadOnly.test.js；node --check src/main/resources/static/mailbox-readonly.js；桌面/手机真实浏览器A-1/A-14。

测试环境使用隔离MySQL/测试SMTP与临时附件目录；不得把mysqlIt连生产库。跳过/未运行必须记NOT_RUN，不能写PASS。

## 人工验收清单
### A-1: READ标签可见且不可写
- 前置条件: 测试库有授权READ专家甲，ES标签“新发现”“瑞典”，一封来信带QA标签“薪资”和CUSTOM标签“补充材料”。
- 操作步骤:
  1. user1登录，选甲。
  2. 查看列表/头部与来信。
  3. 用同会话请求新增/删除专家标签和邮件标签。
- 预期结果: 上述四个标签出现在对应区域；无新增/×控件；两类写接口403，admin刷新标签未变；无附件下载按钮。
- 覆盖: I-2/I-3/I-13/S-1/X2/X8

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
- 预期结果: popover桌面430px、手机不溢出；首项select34px；header权限select最大180px；角标18px；无大权限卡；null标签显示“标签暂不可用”，[]不显示；失败不伪造授权成功。
- 覆盖: I-3/I-9/I-10/S-1/S-2/S-3/S-4

本节为权威清单；进入人工验收时才导出同名前缀-acceptance.md，本次不生成衍生表。涉及尚未到本步的UI，使用同会话API执行等价动作并在最终08再次通过UI验证。

跨阶段人工项引用的其他I/S条款，以总计划同编号定义为准；本步实现仍严格限定本文件清单。
