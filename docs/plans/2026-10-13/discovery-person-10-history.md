# 10 仅新发现历史人物合并与对账

状态：设计，未实施。依赖 04—09；数据应用另属实施阶段，本轮未获应用指令。上级：[总计划](discovery-identity-recovery-master.md)，依据：[审计报告](../../audits/2026-10-13-discovered-person-duplicates/report.md)。

## 需求描述

为当前新发现历史档案给出逐条处置与人物/邮箱映射，确认可靠组后合并展示，并避免后续发现继续产生同人重复。保持原 ES、准入、联系人、别名和邮件；不处理原 10,686 待确认名单，不物理删除，不因合并自动发信或批量批准。

## 关键不变量

### Invariant I-1：冻结范围并实时复核
- Rule：仅处理审计冻结的 51,829 个 discovered 原 docId；实际应用重新读取三层、标签、身份证据、seq_no/primary_term、SQL 关系 revision、人工决定及历史引用。变更、范围外关联或不完整读取均跳过并报告，不使用快照强覆盖。冻结后新增 discovered 由 05 增量接管，另列差额，不混入旧分母。
- Applies to：preview、apply、恢复和报告。
- Violation consequence：误改其他标签或把变化中的记录强行合并。
- 来源：用户范围及顺序三层快照的限制。

### Invariant I-2：候选不等于已核准
- Rule：3,741 组/8,426 档案只是高置信候选，4,685 是最大理论减少展示数；161 组/340 条保持独立待核。既有有效逐邮箱绑定凭证和可信作者 ID 是合并必要条件；有冲突/缺证先回源，不能仅因名字相同批准。应用每组所有成员检查完整后一次提交关系，失败组不部分合并。
- Applies to：候选分类、应用及最终数量。
- Violation consequence：虚报自然人数、误并待核组或部分丢失。
- 来源：本轮名单与审计方法。

### Invariant I-3：历史只关联不重写
- Rule：旧 docId/contactId/mail_record/别名/人工决定均不改；创建人物关系并通过 09 导入已有成功/未知首发保护。多个联系人、发件人绑定或人工资料冲突不得选一覆盖其余；无法确定安全行为的组留待核。回滚只生成关系恢复提案，已有后续成员/发信/版本变化时禁止自动拆组并释放首发保护。
- Applies to：apply、核对、rollback-preview。
- Violation consequence：丢历史或回退造成再次首发。
- 来源：V10/V23/V148、总计划 I-5/I-8。

## 现状审计

- 当前本地审计产物 `output/discovered-person-duplicates-2026-10-13/` 有 summary、tagged-layer-records、unique-records、high-confidence-groups、review-required-groups。生成时为只读顺序快照，无新鲜逐组公开原文复核。
- 原 RAW 51,829，CANDIDATE 44,616，APPLICATION 33；真实 docId 去重 51,829。全库高置信理论减少 4,685；候选层自身 2,574 组/5,830 条、减少 3,256，不可混用。
- 现有修复入口没有人物迁移。新写者只能调用 04 事务登记与 09 历史保护导入；旧 ES/准入/联系人/邮件只读；新读取者为 06/08/09。04 两已有根的自动桥接仍不开放，本批遇到这种情况转待核，不强行吞并。
- 联系人/别名需按所有成员 docId 与邮箱查询，检查多联系人、历史成功、进行中或未知 attempt、手工状态以及范围外引用；不能只查代表 docId 的一个最新联系人。
- 交互：维护窗口与发现/首发竞争、名单标签变化、已有保护、重复 apply、应用中断和后续再发现。

## 实现方案

1. **只读预览（I-1— I-3）**：新增 maintenance service/controller 与本地 CLI。preview 接受冻结 ID/候选组标识，服务端自行读取证据，不接受客户端上传“可信作者结论”。输出每条互斥主类：MERGE_READY、REVIEW_REQUIRED、KEEP_SINGLE、CHANGED_OR_FAILED；并输出每个邮箱当前准入/禁发原因，明确“可以合并”和“可以发送”分别判断。缺绑定证据者可一对一登记单例以保留原行为，但不可创建可信作者键或因此提高准入。
2. **复核材料（I-1/I-2）**：逐组保存成员 ID、邮箱、可信作者键、证据 source/hash/version、来源定位、层级、前后人物关系、三层版本、SQL revision、历史保护判断和原因。已有充分有效凭证可作为合并依据；需要回源者以原 02 能力只处理 discovered 子名单；回源失败留 REVIEW_REQUIRED，不抄旧版本冒充重新核验。
3. **受限应用（I-1— I-3）**：apply 只接冻结 ID、规则版本、预览摘要及 expected 指纹，不接任意目标字段。服务端重复校验后以同库事务登记整组/导入历史保护；ES 不改，所以无三层覆盖或删除。先 50 组试点，对账后再分批；整个首次切换窗口按总计划暂停目标发现/首发及目标身份/标签/准入写入，避免预览提交间竞态。窗口内重新扫描变化清单并核对所有目标，不能只靠旧 snapshot。
4. **幂等与审计（I-1/I-3）**：请求相同组/版本再次 apply 返回 UNCHANGED；CLI 落盘前先原子写临时文件再 rename，保存各组结果和断点。响应未知时先查当前关系，不能再次盲建；若日志落盘失败立即停止，恢复由数据库关系与证据对账。输出目录 0700、个人字段文件 0600；不往仓库公开报告粘贴全邮箱名单。
5. **回滚与增量核对（I-3）**：rollback-preview 生成逐组恢复提案及受影响人物/保护，不提供无条件生产回滚按钮；已发、关系已变或新邮箱加入只报告不可直接恢复。启动 05 后再次发现同组邮箱/新邮箱，确认映射和首发保护继续有效。历史清理的含义是合并重复展示和隔离待核，物理删除 0。
6. **最终交付（I-1— I-3）**：互斥逐条 JSONL/CSV 名单、旧 docId→personId→邮箱映射、处理组数/人物数/成员数/邮件可用状态、实际减少展示数、失败/变化/待核原因、非目标写入审计、前后查询对账、回滚提案。51,829 条每条恰好一个处置结果；未处理不冒称已完成。长期源头核验不到的记录仍保留待核，不擅自删除。

## 变更文件清单

|文件|动作|
|---|---|
|src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonMaintenanceService.kt|新增|
|src/main/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryPersonMaintenanceController.kt|新增，沿用现有管理入口访问控制|
|src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryPersonMaintenanceServiceTest.kt|新增|
|src/test/kotlin/com/weibo/talentintroduction/discovery/controller/DiscoveryPersonMaintenanceControllerTest.kt|新增|
|tools/discovery-person-recovery/reconcile.py|新增|
|tools/discovery-person-recovery/test_reconcile.py|新增|

共 6 文件；维护服务与本地审计工具两个子系统，共享存储增字段 0；逐批输出产物由 CLI 生成，不列为业务代码文件。

## 验收标准

- I-1：输出主类数量和=51,829；标签/版本改变项跳过，范围外写入=0；所有 ES/SQL 读取失败有明确失败结果。
- I-2：161 组没有自动归并；应用减少量按实际确认组计算，分层核对；一次事务失败全组不产生半合并。
- I-3：历史字段逐值不变；已有成功/未知组不能再次首发；重复 apply 0 新增；实际发送=0、物理删除=0。
- 用脱敏固定小样本覆盖每主类及并发/中断；全量预览不是自动执行授权，本轮仅编写此方案。

## 人工验收清单

### A-1：完整名单与试点
- 前置条件：隔离环境载入冻结名单及现有关系/邮件快照，禁 SMTP；含高置信、待核、单例、标签变化各样本。
- 操作步骤：先 preview，核对总数和每类；选 50 个满足所有条件的组 apply；打开新发现列表和旧邮件链接。
- 预期结果：51,829 条各有一个主类；161 待核组没有自动合并；只选中试点组减少重复展示；旧邮件/联系人原值不变；非目标写入、删除、发信均 0。
- 覆盖：I-1— I-3，历史→人物→展示/保护。

### A-2：变化、中断与重复
- 前置条件：预览后移除一个成员 discovered 标签；一组已有成功邮件；另组模拟事务提交后 CLI 断开。
- 操作步骤：apply 后恢复连接，先读当前关系再重复 apply；请求 rollback-preview；再次发现已合并者的新邮箱。
- 预期结果：移标组跳过，历史成功组首发禁止；断开组不会重复新增；回滚仅出提案，新增邮箱/已有发送的组不自动拆除保护；再次发现人数不增、邮箱增加 1。
- 覆盖：I-1/I-3、竞态/断点/后续发现交互。
