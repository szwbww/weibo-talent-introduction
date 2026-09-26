# 专家姓名错绑：生产审计与修复建议

审计日期：2026-09-25；服务器root@150.158.92.103；主筛查快照14:51:38（服务器时间）。初始审计为只读；随后按用户“数据先恢复、代码后修复”的授权，已恢复53条确认错误记录，详见[线上恢复结果](restoration-result.md)。扫描期间生产仍可能新增记录，数值是本轮快照。

结论：存在系统性邮箱→作者身份错绑，且已造成真实邮件叫错名字。不能将未回复全部归因于此；本轮没有因果证据证明回复率受影响的具体幅度。


后续扩大审计：已对全部50,319条发现类记录查重，新增确认2,102条仍错，详见[全量重名排查](../2026-09-25-deep-discovery-duplicate-names/README.md)。本报告原53条恢复记录保持独立。

## 数量与边界

| 口径 | 数量 | 含义 |
|---|---:|---|
| 论文来源三层去重档案 | 45,648 | 仅PAPER_FULLTEXT，按真实ES docID去重 |
| 同论文/同名/不同邮箱风险组 | 2,378 | 包含正常一人多邮箱，不能直接判错 |
| 风险邮箱记录 | 5,304 | 已输出全名单，不代表全部错误 |
| 风险中已有发信的邮箱 | 126 | 对应179封sent_at非空的OUTBOUND记录 |
| 本轮已证实错绑的邮箱记录 | 53 | 35条结构化原文确认并重放；18条原论文/官方资料确认 |
| 已证实错绑中已有发信 | 31 | 其余22条未查到对应联系发信记录 |
| 已证实错绑且实际用了错误姓名称呼 | 15 | 依据风险联系人的真实发信记录称呼，逐一关联证据 |

53条包含一条未命中“同论文同名多邮箱”规则的Rizgar记录，因此不能将5,304当作错误范围的完整上界。未逐篇复核所有45,648条；4百多万非论文来源RAW档案没有纳入此次根因筛查。不能宣称“剩下全部正确”。

索引命中：RAW 45,629，CANDIDATE 45,640，APPLICATION 25；同一docID多层只计一次。主规则风险来源：EUROPE_PMC 4,118；OPENALEX 1,106；CORE 68；CROSSREF 6；ARXIV 6。

72篇公开Europe PMC XML用于优先核验已联系风险及同论文其他风险邮箱；依照直接邮箱、独占引用、原文显式姓名/缩写确认了126条归属，其中35条与系统姓名不同。35条全部通过与生产SHA256一致的JatsXmlEmailParser重放，仍输出了错误身份。这个“126条原文归属”与“126个已联系风险邮箱”恰好同数，集合不同，不可混淆。

## 原始案例

[Comparison Among Cloud Technologies and Cloud Performance原文](https://jastt.org/index.php/jasttpath/article/download/19/8/72)首页明确列出不同作者。

| 邮箱 | 修复前系统名 | 原文作者 | 发信情况 |
|---|---|---|---|
| omar.alzakholi@dpu.edu.krd | Omar Alzakholi | Omar M. Ahmed（期刊页面署名Omar Alzakholi） | 正常别名，保留 |
| shakirdu@yahoo.com | Omar Alzakholi | Shakir M. Abas | 2026-09-25 09:57:31；mail 7162 |
| lailan.haji@uoz.edu.krd | Omar Alzakholi | Lailan M. Haji | 2026-09-25 01:21:50；mail 7128 |
| hanan89md@gmail.com | Omar Alzakholi | Hanan M. Shukur | 2026-09-25 01:20:46；mail 7127 |
| mohammad.abdulrazaq@dpu.edu.krd | Omar Alzakholi | Mohammad A. M. Sadeeq | 未查到已发邮件 |

后三个已联系错绑者实际收到的称呼均为 `Dear Dr. Alzakholi,`。系统不是只在列表显示错：姓名已写进RAW/CANDIDATE及expert_contact。

同论文另一作者rizgar.ramadhan@dpu.edu.krd已在另一篇[原文](https://jastt.org/index.php/jasttpath/article/download/31/14/183)采集中被绑为Hanan M. Shukur；正确为Rizgar R. Zebari，文档ID为0000-0002-8420-5331。此条单独核查，未命中重复风险规则，也没有查到联系发信记录。

## 根因及生产状态

1. **历史PDF/Core邮箱首字母匹配。** 旧PdfEmailExtractor用`knownAuthors.firstOrNull`，只要邮箱local-part含姓或名的首字母就选中。Omar/Alzakholi的`o/a`大量命中共同作者邮箱；姓名、机构与ORCID一起复制。提交3946ab2已有该规则，1ba6852（2026-09-21）改为完整姓名唯一强匹配。生产PdfEmailExtractor/PdfEmailExtractorKt/CoreDataSource字节码均与当前target/classes SHA一致，旧规则已替换；旧记录没有自动清洗。
2. **当前XML共享通讯说明歧义。** JatsXmlEmailParser.parseXrefCorrespondence把共享说明内全部邮箱绑定给每一个引用作者；mergeResults又逐字段firstOrNull，最终大量邮箱落到第一个作者。生产仍可复现。PMC13241006中Ali Akbar Moosavi两个邮箱被归到Sajjad Abbasi；PMC13293455中Latha Thimmappa邮箱被归到Suvarna Hebbar。
3. **身份验证与邮箱验证混淆。** emailVerifiedLevel=3不能证明姓名归属正确。发送链路把已有姓名作为事实，既有模板变量检查只检查缺失/技术ID，没有原文身份归属验证。
4. **补全不能修复错误身份。** updateExpertAcademicFields按既有ID更新指标，不修改姓名。trustedOrcid仍可把历史错误的ORCID形状业务ID用于作者查找。已证实datta_madamwar@yahoo.com绑定Avani Bharatkumar Patel，且有enrichedAt及学术指标；需核验并清除/重采被错误身份污染的字段，而非只改名。尚未将所有这些指标逐项判错。

公开证据例：[Datta官方资料](https://www.spuvvn.edu/team/dr-datta-madamwar/)、[Latha原文](https://pmc.ncbi.nlm.nih.gov/articles/PMC13293455/)、[Ali Akbar原文](https://pmc.ncbi.nlm.nih.gov/articles/PMC13241006/)。

## 原建议修复顺序（已由用户调整）

用户已暂停发送，并指定先恢复确认错误的数据。本次已完成53条恢复；以下为初始建议，不代表已执行，剩余代码和扩大审计工作后续处理。

1. **止损**：对已证实错误及选定待核验名单做可回滚的临时发信隔离；待核验不得继续用猜测姓名发送。复用现有MANUAL抑制机制，不能假标退订/EMAIL_INVALID，不能解除已有退订。此次尚未执行隔离。
2. **堵住仍存漏洞**：修复XML多人共享说明和身份冲突合并；修复后重放本次原文。PDF旧规则已修，不需要重复“修首字母”而忽略XML。
3. **保护作者ID**：增加身份核验标签闸门，阻止隔离记录补全；保留业务docID与邮件关联键，明确阻止错误ORCID主键作为查作者回退。仅删除externalIds.orcid不足以阻止当前回退。
4. **存量分批清洗**：先53条已证实（优先15个实际错称呼），再126个已联系风险邮箱，再扩展5304风险及单条错绑。逐条原文证据、备份、CAS、三层existing文档及MySQL姓名快照同步，机构/身份/指标按证据分别处理。
5. **旧队列处理和验收**：确认在途采集/补全已停止，旧extraction_json重新抽取或隔离；核验列表、详情、模板预览及收信归属后解除本批次隔离。历史已发送邮件不可改写；不自动补发或重置已联系状态。

不能只把所有名字清空/统一称呼后宣布修复：错误机构、作者ID及指标会继续误导筛选和个性化内容。

## 交付文件

- [已证实错误53条，逐条正确姓名/邮箱/证据/称呼](confirmed-wrong.md)
- [已联系风险126个邮箱](contacted-risk.md)
- [全部5304风险记录JSON](risk-records.json)：含ES ID、索引层、DOI、发现时间、联系人ID、发信计数与证据状态。
- [已证实错误JSON](confirmed-wrong.json)
- [审计计数与口径JSON](summary.json)
- [生产同版本解析器重放结果](parser-reproduction.txt)
- [阶段一：XML解析修复](../../plans/2026-09-25/expert-identity-parser-repair.md)
- [阶段二：隔离与数据修复](../../plans/2026-09-25/expert-identity-data-repair.md)

本轮没有修改生产数据、抑制规则、应用代码或发送任务，也没有联系任何专家。生产调查凭据未输出、未落盘。首次大范围导出被自动审批拒绝后，已缩减为服务器内统计、仅返回风险名单与限定称呼片段，完成了该范围核查。
