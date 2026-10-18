# 01 · 有界样本仓库与采集上下文

待实施；依赖无；部署后默认关闭，不改变发现行为。共6文件、1个独立子系统。规范：[总计划](discovery-samples-master.md)、[contracts C-1～C-6](discovery-samples-contracts.md)、[audit](discovery-samples-code-audit.md)。

## 需求描述

O-1：可用文件包保留完整作者输入/正文和结果，且能区分缺正文、配额跳过、写入失败。
O-2：样本在限额内保存并自动清理，损坏/半写文件不被当作有效包。
必须保持：N-1 默认关闭不产生文件/额外网络/业务写入；N-2 业务正常返回及原异常不被诊断覆盖；N-3 不借用队列、任务详情或专家索引保存样本。
范围外：调用点接入、抽取算法、回放CLI、UI、DB迁移、独立队列和对象存储。

## 关键不变量

### Invariant I-1: 固定结构与原始输入
- Rule：contracts C-2/C-4/C-5列出的每个字段、nullable和枚举按定义实现；stage只能EXTRACTION。paper作者全字段、顺序和原始正文不变；允许的URL脱敏是唯一输入改写，并保存原URL指纹。主桶是配额标签，不能改写outcome。
- Applies to：Models、Capture、Store.commit。
- Violation consequence：回放输入失真、准入口径混乱。
- 来源：总I-1/I-2；K-source-recall-needs-original-layout。

### Invariant I-2: 有界且原子
- Rule：C-1所有配置及范围校验、C-3锁/清理/原子提交、C-5正文状态严格执行。仅SAVED有可读文件/SHA。配置失败禁用采样，写失败不提交残包。跨重启以磁盘目录为准计数，不靠内存配额重置绕过上限。
- Applies to：Properties、Store全部写/删/扫路径。
- Violation consequence：爆磁盘、读取半包、重启突破配额。
- 来源：原始需求；总I-3。

### Invariant I-3: 无损旁路
- Rule：业务delegate恰执行一次，结果或异常原样传回；仅采样逻辑自己的Exception被隔离。ThreadLocal在实际调用线程进入、finally恢复，许可释放；拿不到许可立即跳过。enabled=false无目录/日志/清理；HOST_BUSY不是样本抽取失败。
- Applies to：Capture作用域、Store错误隔离、配置注册。
- Violation consequence：误吞生产故障或串样本。
- 来源：K-task-activity-execution-identity；K-page-commit-must-cover-consumer-failures。

## 现状审计

现无DiscoverySample实现，检索回执sample-store-absence.txt。新store全部读写见audit§6：唯一commit写包、cleanup删包、限额扫描读包，03只读包。外部数据库/ES零读写。
新配置注册可沿用 `task/service/TaskAuditRetentionScheduler.kt` 的独立@EnableConfigurationProperties模式；不修改共享RestTemplateConfig。现有MailAttachmentService文件目录属于邮件模块，不借用。
IP-3：scope收集→Store；IP-4：commit/cleanup→目录扫描及03reader。ThreadLocal仅局部钩子使用，02必须显式传执行context。

## 实现方案

1. **模型与配置（I-1/I-2）**：新增清单1/2。Models仅放不可变context、manifest、attempt、bucket/bodyStatus、commit decision类型及规范序列化辅助。复用PaperMetadata/EmailExtractionOutcome，不复制它们的业务定义，不增加其字段。Properties文件含配置注册；默认值/硬上限逐项照C-1。不得创建新的配置页面。
2. **存储（I-1/I-2）**：新增清单3。Store实现commit、cleanup、内部有界扫描；用可注入Clock和根目录方便测试。启动及@Scheduled每小时清理仅enabled时执行；写前再次清理和校验容量。C-3是唯一落盘格式，reader为03。无索引JSON、数据库、独立配额账本和历史统计文件；目录内容就是留存真值。
3. **scope（I-1/I-3）**：新增清单4。入口`capture(context,paper,delegate)`的业务lambda类型固定返回EmailExtractionOutcome；未启用/无合法context直接delegate。作用域绑定的采集器提供有限attempt开始/完整body/异常结束钩子，所有钩子自己fail-open；delegate错误仍抛原对象。返回或异常后选桶并尝试commit；提交错误不能替代业务结果。捕获HostBusy要先识别现有异常类型并跳过。
4. **日志与测试（I-1～I-3）**：清单3实现C-6的成功事件与限频诊断，不修改任务聚合日志。清单5/6测试原子性、配额、过期、并发、readonly、坏包、路径逃逸、delegate一次/异常对象一致、ThreadLocal复用、关闭零IO。测试支持可选`-Dsample.acceptanceDir=/tmp/discovery-sample-acceptance`，在该测试目录输出case结果JSON和包；不得连接生产服务。不是另建生产自检API。

## 变更文件清单

|#|文件|动作|
|---|---|---|
|1|`src/main/kotlin/com/weibo/talentintroduction/config/DiscoverySampleProperties.kt`|新增|
|2|`src/main/kotlin/com/weibo/talentintroduction/discovery/sample/DiscoverySampleModels.kt`|新增|
|3|`src/main/kotlin/com/weibo/talentintroduction/discovery/sample/DiscoverySampleStore.kt`|新增|
|4|`src/main/kotlin/com/weibo/talentintroduction/discovery/sample/DiscoverySampleCapture.kt`|新增|
|5|`src/test/kotlin/com/weibo/talentintroduction/discovery/sample/DiscoverySampleStoreTest.kt`|新增|
|6|`src/test/kotlin/com/weibo/talentintroduction/discovery/sample/DiscoverySampleCaptureTest.kt`|新增|

## 验收标准

- I-1：完整PaperAuthor往返、原bytes SHA、C-4字段/nullable、所有bucket/bodyStatus与冲突优先级逐项断言；元数据过大不截断；URL凭据不入包/日志。
- I-2：用Clock推进8天清过期；每桶上限2时提交3个不同输入只存2，成功对照上限1时提交2个只存1；重建Store再提交仍受限。将body限额设1024、总容量设足够两个测试包，验证超body为BODY_LIMIT、容量淘汰最旧、包括.tmp在内不超上限。模拟原子rename失败无可见包；锁忙即跳过；符号链接越界拒绝；重启清崩溃.tmp。
- I-3：开关、不可写、存储异常、capture许可满、业务异常、HostBusy、两个执行并行、同线程连续执行；delegate调用1次、返回同一个outcome/异常实例；任务/网络依赖零新增调用。新配置Spring绑定测试包含在StoreTest内，不启动完整应用。
- 命令：`mvn -Dtest=DiscoverySampleStoreTest,DiscoverySampleCaptureTest -DskipNodeTests=true test`。不增加pom依赖。运行耗时报告仅实测值，不设猜测的性能收益目标。

## 人工验收清单

以下命令在独立开发工作树执行。测试按场景输出可读JSON及实际目录供人检查；case名称是必须实现的验收产物接口。

### A-1: 输入与状态
- 前置条件：完成01；本机JDK11/Maven可用；测试自行用内存字节和作者元数据构造样本，无生产数据。
- 操作步骤：1. 执行 `mvn -Dtest=DiscoverySampleStoreTest,DiscoverySampleCaptureTest -DskipNodeTests=true -Dsample.acceptanceDir=/tmp/discovery-sample-acceptance test`。2. 打开该目录下 `store/cases.json`、`store/saved/sample.json` 和正文。
- 预期结果：case `roundtrip` 的stage=EXTRACTION、source=OPENALEX、authorRoundtripEqual=true、bodyHashEqual=true；case `missing-body` 的bodyStatus=NOT_AVAILABLE、bodyFile=null；无AUTO_PASSED/RAW状态。
- 覆盖：O-1、I-1、IP-3。

### A-2: 配额、清理与坏目录
- 前置条件：A-1生成文件；测试使用独立temp根、Clock和故障文件系统操作模拟，不修改系统或生产目录权限。
- 操作步骤：1. 查看 `store/cases.json` 的quota、expiry、capacity、atomic-failure、unwritable。2. 对照各case目录实际文件数。
- 预期结果：quota留存失败包2、成功包1；expiry过期包0；capacity的withinLimit=true；atomic-failure可见完整包0；unwritable decision=IO_ERROR；错误不越界删除其他文件。
- 覆盖：O-2、N-2、I-2/I-3、IP-4。

### A-3: 默认关闭与异常不变
- 前置条件：A-1执行完成；CaptureTest生成 `capture-scope/cases.json`。
- 操作步骤：1. 查看disabled、delegate-error、scope-reuse三个case。2. 检查disabled目录不存在；核对并行执行ID列表。
- 预期结果：disabled createdFiles=0、extraNetworkCalls=0；delegate-error sameException=true、delegateCalls=1；scope-reuse crossExecutionMixups=0；dbWrites=0、esWrites=0。
- 覆盖：N-1/N-2/N-3、I-3、IP-3。
