# 深度发现：作者归属、学术补全与页面定时配置

状态：待审阅，未实施。2026-09-29。使用 create-p；本组只修改代码，线上历史数据修正不属于开发任务。

基线：`1cd59e31164d11e962203e31c9f61310f2bc5912`。工作区有其他任务的未提交修改，研究开始时包括 `index.html/styles.css`；期间其他任务可能提交变更，执行前重查当前 HEAD/diff，保留其他任务改动。证据与文件 SHA 见 [baseline.json](discovery-repair-evidence/baseline.json)。

## 需求描述

1. 阻止论文合著者的邮箱、姓名、作者 ID、机构和研究方向被交叉绑定。
2. ORCID 对应多个 OpenAlex 作者时，不再取第一条或最后一条补全；明确记录身份歧义。
3. 深度发现弹窗提供“每隔 N 小时执行一次”和保存按钮。第一版仅整数小时，范围 1～168；保存立即调整后续计划，无需重启。当前同步模式可设置；连续流水线模式维持已有运行/暂停规则，不展示一个不能控制实际频率的输入框。

保持：现有身份明确的一人多邮箱、邮箱验证与资格规则、按邮箱去重、手动执行、运行互斥、限额、查询检查点和现有取消语义。不会增加模糊同人匹配、LLM 判断、自动合并专家、新发送门禁或通用调度平台。

范围外：删除/清空/改名线上专家、修改已发送邮件、重跑全部历史补全、启用 pipeline。部署不是本次计划生成的结果。

## 关键不变量

### Invariant M-1：邮箱归属先于学术身份传播
- Rule：只接受来源内明确、无冲突的单一邮箱归属；共享标记、共享段落不得整体归给首位作者。无法确定时保留邮箱线索，不带作者 ID/机构/身份凭证。
- Applies to：01 的 PDF→解析结果→现有发现消费入口。
- Violation consequence：错误身份继续通过机构凭证和学术补全传播。
- 来源：K-author-identity-needs-email-evidence。

### Invariant M-2：歧义不产生学术事实
- Rule：一个 ORCID 返回多个不同作者 ID，补全为身份歧义；不能按姓名相似、返回顺序、引用量任取一人。查询结果被分页截断时不能假装已确认唯一。
- Applies to：02 的批量/单条 ORCID 补全。
- Violation consequence：研究方向、h-index、引用量、论文标题及分类一起串人。
- 来源：原始生产证据。

### Invariant M-3：只调整后续触发
- Rule：小时配置持久化成功且调度应用成功才显示成功；保存不触发发现、不打断当前发现。小时任务与旧 cron 不得同时启动同步发现。
- Applies to：03 的 DB/API/scheduler 与 04 页面。
- Violation consequence：保存即启动、重复发现或页面显示已生效但后台未应用。
- 来源：K-batch-send-scheduler-reschedule-on-enable。

## 现状审计

最近三次发现任务 20846/20954/21068 的最终快照：2,485 条当天新增档案；560 条有机构，未发现机构内夹带邮箱/网址。确认 7 条邮箱归属错误，其中 4 条机构错误、6 条带着错误作者的研究方向；另 2 条 Nobuhiro Tsuji 档案写入天体物理主题。[确证档案](discovery-repair-evidence/confirmed-profile-snapshots.json)。这些是已确证下限，不是全库错误总数。

已实际发出：`ashutosh@nitkkr.ac.in` 被命名 Jitendra Kumar，contact=6186、mail=7515、task=21095，2026-09-29 11:02:01，SENT。真正的 `jitendra@nitt.edu` 于 11:02:18 另收一封。仅当前第一条为确认错寄。

本轮取得六篇原始公开 PDF 与原始 OpenAlex 作品 JSON，逐文件 URL/SHA/长度在 [原文归档](discovery-repair-evidence/original-source-evidence.zip) 的 manifest。用既有编译类做离线重放，未改产品代码：[程序](discovery-repair-evidence/ReplayDiscoveryContacts.java)、[结果](discovery-repair-evidence/contact-replay.json)。姓名拆分使用当前生产的首个空格分割；为定位归属，重放未携带机构字段。抓取日期晚于生产发现，不能把这份元数据称为发现时的逐字请求快照。

- Crea：元数据 `Jan F. Veneman`/`Danijela Ristić–Durrant` 与论文署名不同，`rosterOwners` 漏掉另外两位共享 `*` 的作者，三个邮箱全被归为 Crea。
- Debie：`‡e.debie@…，§helge.janicke@…` 同行，代码取行首 `‡`，把整行邮箱给 Debie；标记集合还未支持 `§`。
- Kumar：`J.Kumar is with …` 拼接下一行 `A. K. Singh are with …`。边界仅识别 `is with`，而元数据拆名为 given=`Ashutosh`/family=`Kumar Singh`，缩写检查也漏掉 `A. K. Singh`。
- Nguyen/Everett/Lumma：线上错误及论文真值已确认，本次下载版本重放没有产生错误绑定；列为真实负例回归，不能宣称其运行时分支已逐一定位。
- OpenAlex：单条 ORCID 查询取 results[0]；批量使用 `results[identity] = ...` 覆盖。同 ORCID 多作者是实际响应事实，Nobuhiro 的线上主题与错误作者节点 top-5 完全相同。[API 核查摘录](discovery-repair-evidence/openalex-orcid-collision.json)。

修正此前表述：`SourceAuthorEmailResolver.textClaims` 的一人多邮箱循环本身也支持正常场景，不能直接全部禁掉；已重放的主要缺陷在 PDF 联系块边界与标记归属。

定时配置只读核对：生产环境未覆盖 enabled/cron/pipelineEnabled；部署 YAML 为 enabled=true、cron=`0 0 */2 * * ?`、pipelineEnabled=false。当前实际规则是每偶数小时整点尝试启动；任务可能超过两小时，因此执行记录不保证两小时一条。页面目前没有定时设置入口。

各子计划包含对应 schema、读写路径、交互及验收；原始 grep 回执：`discovery-repair-evidence/{identity,enrichment,schedule,store,frontend}-paths.txt`。历史 institution 修复已落地部分直接作为现状，不能重做旧计划或清空新 institutionEvidence。

## 实现方案

依次实施，每份单独验证，不并行改共享文件：

| 顺序 | 子计划 | 实施文件数 | 内容 |
|---|---|---:|---|
| 01 | [邮箱归属](discovery-repair-01-contact-ownership.md) | 7 | PDF 标记与段落边界、真实负例、旧结果版本拒绝 |
| 02 | [ORCID 补全](discovery-repair-02-orcid-enrichment.md) | 7 | 唯一作者确认、截断防护、歧义结果贯穿 worker |
| 03 | [小时调度后端](discovery-repair-03-schedule-backend.md) | 9 | 单例设置表、GET/PUT、动态重排 |
| 04 | [小时调度页面](discovery-repair-04-schedule-ui.md) | 7 | 弹窗输入/保存/生效提示、前端回归 |

03→04 为接口依赖；01→02 顺序用于保护同一发现消费链。每份≤10文件、≤2子系统；不把历史数据操作塞进子计划。03/04 是同一功能的后端和页面两步，拆开是为限定验收范围。

存量处理建议（不在开发执行清单）：保留原始快照，按邮箱主键逐条确定真实主人后再纠正姓名/ID/机构/学术字段；已发或已回人员保留联系记录与历史邮件。无法确定的学术字段不重写成“看起来合理”。仅清机构不能解决已确认的姓名/作者 ID 错绑；重跑未经修复的补全会再次污染。9条确证档案应单独处理，不扩为所有同名或多邮箱档案。历史更正要审计 trustedOrcid 的 profile.orcidId 回退，不能只删 externalIds.orcid 后保留另一人的可补全身份；稳定业务关联键及历史邮件不改。（来源：K-historical-identity-orcid-fallback）

## 变更文件清单

本文件仅编排；实施文件穷尽清单分别见01～04。证据目录是计划材料，不是生产写入程序。新 migration 暂定 V144；若实施前被其他任务占用，只允许改为当时下一个空闲版本并同步本计划，禁止覆盖已应用 migration。

## 验收标准

- M-1：01 的真实原文重放不再出现七条已知错误关系；显式一人两邮箱正例继续保留。
- M-2：Nobuhiro 的三个不同作者节点返回 AMBIGUOUS_IDENTITY，学术更新/再资格核验调用均为0；单条、批量、worker 口径一致。
- M-3：页面保存3小时，DB保持3；保存后3/6/9小时为候选触发点，跨午夜连续；仍在运行的触发跳过，不补排。
- 各子计划定向测试通过后一次完整 Java11 `mvn clean package`；不在生产通过真实发信验证。

## 人工验收清单

### A-1：联合验收
- 前置条件：在隔离环境依次完成01～04；使用子计划给定原文、API响应和固定时钟。
- 操作步骤：1. 执行01、02离线案例；2. 通过04弹窗保存3小时；3. 查看03固定时钟的启动记录；4. 再手动启动一次发现。
- 预期结果：错误邮箱关系0；歧义作者补全写入0；保存动作启动0次；定时按3小时生效；手动执行仍可用且与定时互斥。
- 覆盖：M-1～M-3；全部需求。

## 自查结论

已保存实际代码/原文/线上快照证据；真实与合成案例分开；未把全部多作者 ORCID 都判为错误。已用知识条目计数更新；K-task-launch-config-registration 不适用，因为没有新增任务类型。单页 UI不新增入口框架，调度复用现有 Spring TaskScheduler。当前阶段未执行修复、未变更线上数据。

规划自检记录：[plan-self-check.json](discovery-repair-evidence/plan-self-check.json)。此记录只验证文档结构、链接、证据哈希和范围；不代表修复代码已通过测试。
