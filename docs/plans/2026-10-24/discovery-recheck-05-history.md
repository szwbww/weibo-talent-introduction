# 05 · 独立重审轮次与轨迹存储

日期：2026-10-24。状态：待执行；本文件不证明修复已上线。前置及发布门禁见 [总计划](discovery-recheck-master.md)。基线 HEAD `b161e87464d42b9fa2cd076ef9255b0e3b2b29ab`，须保留工作区已有修改。

## 需求描述

结果：同一人可多次重审，每轮独立保留名单、证据和8步记录；旧发现批次不被重置。仅建立兼容存储/读取合同，尚不暴露生产重审启动入口。保持普通发现去重、已准入Gate、CLI历史工具和请求预算。

## 关键不变量

### I-1：轮次独立且普通路径兼容
- 规则：item.review_epoch BIGINT默认0；普通发现恒0；重审恒该轮owner_execution_id且>0。唯一键改(docId,identityHash,policyVersion,review_epoch)。不得伪造policyVersion、搬迁旧item、清空旧request_count或覆盖证据。
- 适用：reserve普通/重审、迁移、CLI
- 违反后果：新一轮覆盖原发现统计或重复消费旧项。
- 来源：K-discovery-recheck-history-not-item-reset

### I-2：新项不抢占现有准入
- 规则：供业务Gate/managed查询保留全部epoch=0普通项（包括AUDIT），epoch>0仅返回admission_revision非空的ENFORCE重审项；AUDIT/准备中/排队/未通过的新项不变更当前业务状态。commit内部可显式提供staged项供Gate复算；提交事务完成后才成为业务读者可用事实。
- 适用：Repository.findItemsByDocIds/findManagedContexts、Gate、Commit交互
- 违反后果：点击对照就让已准入专家变待审核。
- 来源：需求及下列源码审计

### I-3：冻结配置和可变控制分离
- 规则：run只增recheck_json一个列，null表示普通发现；对象schemaVersion=1，spec写一次不可改，control只能CAS run.revision。spec固定actor/requestKey/scope/evidenceMode/evaluatedAt/代码与解析版本；control阶段PREPARING→READY→RUNNING→COMPLETED，异常FAILED/INTERRUPTED，取消CANCELLING→CANCELLED。只有READY且名单hash已固定能启动。
- 适用：新run保存/查询/领取
- 违反后果：漂移名单、刷新配置混入旧任务。
- 来源：需求及下列源码审计

### I-4：八步完整历史与取消
- 规则：trace新表唯一(item_id,step_no,attempt_no)，step_no1..8；状态NOT_STARTED/RUNNING/SUCCESS/INSUFFICIENT/SKIPPED/ERROR/CANCELLED。终态记录不可改，重试append新attempt；item新增CANCELLED state且outcome=null、completedAt有值、admissionRevision=null。已经提交者不能取消成未提交。
- 适用：trace迁移/domain、查询报表
- 违反后果：错误/取消/不合格混淆或已准入回滚。
- 来源：需求及下列源码审计

### I-5：领取隔离
- 规则：普通listReserved仅epoch=0；重审必须input_closed=true且control.phase=RUNNING才能claimDue/renew/reserveAttempt。取消/中断不能继续提交新准入；投影已提交结果可继续。旧CLI重排队只接受epoch=0。
- 适用：Repository所有领取/续租/尝试/重排队路径
- 违反后果：准备名单时提前外调或旧恢复器执行了重审。
- 来源：需求及下列源码审计

## 现状审计

V158唯一键当前只有docId/identity/policy；Repository.reserveItem:371–388、identity:705三列lookup，不能仅改DDL。findItemsByDocIds:436返回所有项；AdmissionGate:60按max id取ENFORCE，因此未完成新项会抢占旧结论，必须同时改读语义。listReserved:610、claimDue:447、requeue:43/102是遗漏风险。QueryService.records/summary读取reporting*，需独立取消数量。
本子计划触及V158三表，所有写者/读者及CLI全集见共享审计。每个已有共享store只增一个列；新trace表完整schema不是给旧表塞多列。task_execution仅引用真实id，不新增外键以免普通任务清理连带删核验史。

完整检索回执、共享存储全集及交互矩阵：[证据目录](discovery-recheck-evidence/README.md)。以下列出的写入边界为准，不因引用共享审计而扩大文件范围。

## 实现方案

1. 新迁移V161：run加nullable MEDIUMTEXT recheck_json；item加review_epoch BIGINT NOT NULL DEFAULT0，替换uk_dvi_identity为四列，保留原索引；新增discovery_verification_step表：id BIGINT主键、item_id BIGINT、step_no INT、attempt_no INT、state VARCHAR(24)、input_hash CHAR(64)、input_json MEDIUMTEXT、output_json MEDIUMTEXT、reason_json TEXT、version_json TEXT、started_at/finished_at DATETIME(3)、created_at；唯一(item_id,step_no,attempt_no)、索引(item_id,id)，无删除级联。字段允许NOT_STARTED时input_hash/start/finish为空，其余按状态校验；step1..8约束。部署前确认161未被占用，只允许无语义变化顺延版本号。（I-1/3/4）
2. Domain追加reviewEpoch=0/recheckJson=null的默认参数，旧序列化可读；新增RecheckSpec/Control与Step模型放同领域文件。不修改VerificationMode的AUDIT/ENFORCE及其policyVersion。CANCELLED状态约束须覆盖构造函数/结果校验，取消不能伪装ERROR。（I-1/3/4）
3. Repository普通reserve两次identity查询与INSERT均带epoch0；新增reserveRecheckItem只接受真实重审run，并检查spec/control/epoch。findItemsByDocIds及findManagedContexts实施I-2过滤；提供单独历史查询给审计，不把业务过滤用于历史页。所有reporting*明确cancelled与queued互斥；旧ordinary汇总不变。（I-1/2/4）
4. 更新claimDue/renewLease/reserveAttempt/saveOutcome/readReserved与CLI两requeue的边界，数据库锁序继续run→item，不在网络期间持SQL事务。新重审API尚未接入，因此本阶段普通发现行为不变。（I-5）
5. Gate/QueryService读取新列并保留旧payload兼容；Gate不能仅按最新id读未提交重审。相同doc/identity的新已提交epoch取最新id，与旧项全部可在历史看到。（I-2）

## 变更文件清单

仅下列 9 个文件。新增文件明确标注；其余文件不得顺带修改。

| 文件 | 变更 |
|---|---|
| `src/main/resources/db/migration/V161__discovery_recheck_history.sql` | 新增迁移 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/domain/DiscoveryVerification.kt` | 新列模型/重审控制/步骤/取消状态 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepository.kt` | 轮次去重、读隔离、领取、报表 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationAdmissionGate.kt` | 业务上下文兼容 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryService.kt` | 历史/取消报表 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/repository/DiscoveryVerificationRepositoryIT.kt` | 迁移/并发/普通去重/重审独立 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/domain/DiscoveryVerificationContractTest.kt` | 新旧JSON与取消约束 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationAdmissionGateTest.kt` | 未提交新项不抢占 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/DiscoveryVerificationQueryServiceTest.kt` | 取消与历史核账 |

## 验收标准

- I-1：旧V158数据迁移所有id/request_count/owner/evidence完全不变；普通同输入仍同id；两轮重审产生两个新id；每轮重试仍同id。
- I-2：已有AUTO_PASSED时新增AUDIT、QUEUED ENFORCE、PENDING ENFORCE均不改变业务Gate；新真正提交后仅新结论生效；历史可查全部。
- I-3：spec二次改写409；run revision过期CAS失败；READY前不能claim；scopeHash可重复重算。
- I-4：8个step槽齐全；终态更新拒绝；CANCELLED不入errors/autoAdmitted；已有admission_revision不能被cancel清除。
- I-5：普通恢复器不碰重审RESERVED；旧CLI拒绝重审项；RUNNING取消后新reserveAttempt/commit禁止，已存在投影可单独重试。真实MySQL测试必须执行，不能只mock SQL。

## 人工验收清单

### A-1：历史独立
- 前置条件：测试库有一条普通发现item及一次准入；通过测试fixture建立两个同人重审轮次。
- 操作步骤：查询原批次和两个重审详情。
- 预期结果：三个item id不同；原owner/request_count/evidence不变；普通再次发现仍复用原item。
- 覆盖：I-1/3

### A-2：对照不改变准入
- 前置条件：测试库已有AUTO_PASSED，创建AUDIT及未完成ENFORCE重审项。
- 操作步骤：分别查询审核列表、发送选择前的准入读取和历史。
- 预期结果：当前仍AUTO_PASSED；三轮均能在历史读取；没有新增候选副本。
- 覆盖：I-2

### A-3：取消与恢复隔离
- 前置条件：重审有1项已提交、1项运行、1项排队，配置测试HTTP阻塞。
- 操作步骤：取消run后解除阻塞，再运行普通恢复器和CLI。
- 预期结果：已提交项保留；未提交两项CANCELLED或停止待收敛；CLI拒绝重审项；普通恢复器不启动其RESERVED。
- 覆盖：I-4/5

人工验收开始后，才从本节导出同名前缀 `-acceptance.md`；机器通过不代表人工验收完成。
