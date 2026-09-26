# 2026-09-25 已确认错误记录线上恢复结果

用户明确改为“先恢复线上数据，之后再考虑代码”，并告知已暂停发信。本次仅执行已证实错误的53个邮箱记录；没有执行旧计划中的代码修改、部署或新增身份闸门。

完成时间：2026-09-25T15:23:12.942459（服务器时间）。独立回读结果：PASS，0处差异。

## 实际恢复

- 53个邮箱记录，对应RAW 53份、CANDIDATE 53份，共106份ES文档；没有创建或修改APPLICATION文档。
- 31条expert_contact同步正确姓名、机构所在国家；已有联系人主键、运营状态、发件账号绑定全部保留。
- 按论文中作者与affiliation的明确引用恢复机构；原文没有可用国家证据的Amitai Armon国家设为null。多机构作者保留多个署名机构，单值country采用原文第一署名机构所在国家，不能解释为国籍。
- 53条机构字段完成核验，其中45条employment与修复前不同、34条country与修复前不同。机构类型只保留有证据的分类。
- 全部106份档案用与线上一致的ExpertClassificationService重新计算分类；没有修改分类规则。重算结果为ACADEMIC_RND 32份、OUT_OF_SCOPE 34份、UNKNOWN 40份（按索引文档计）。
- 两条错绑指标清空：Datta Madamwar、Rizgar R. Zebari。hIndex、citationCount、worksCount、researchFields、disciplineCategory、lastPublicationYear、recentWorkTitles、patentTitles、enrichedAt、enrichmentSource清为null，等待正确身份重新补全；未编造指标。
- 修正两人的externalIds.orcid并补上已验证的openAlexAuthorId，当前学术补全逻辑会优先使用正确的OpenAlex ID。另5条按作者自身XML节点补入ORCID。

| 邮箱 | 已核实ORCID | 已核实OpenAlex作者ID |
|---|---|---|
| datta_madamwar@yahoo.com | 0000-0003-3301-1120 | A5136819051 |
| rizgar.ramadhan@dpu.edu.krd | 0000-0003-2353-7428 | A5026408327 |

公开身份核对：[Datta ORCID](https://orcid.org/0000-0003-3301-1120)、[Datta OpenAlex](https://api.openalex.org/authors/A5136819051)、[Rizgar ORCID](https://orcid.org/0000-0003-2353-7428)、[Rizgar OpenAlex](https://api.openalex.org/authors/A5026408327)。姓名/邮箱证据沿用[53条原始证据清单](confirmed-wrong.md)，更正值见[恢复清单](restoration-manifest.json)。

## 未改动及验证

- ES逐字段比较：目标字段等于恢复清单，其余_source字段与备份完全一致。
- 联系人逐字段比较：除姓名、国家、更新时间外，与备份一致。
- 31条联系人的mail_record条数、发信/收信计数及历史邮件内容校验值与备份一致；没有改历史称呼、状态或时间，没有发送邮件。
- 恢复前未发现这些联系人的未发送OUTBOUND记录，也未发现RUNNING任务。
- [独立验证结果](restoration-verification.json)记录全部106份文档的姓名与层级。

## 备份与执行记录

生产服务器目录：`/opt/talent/backups/expert-identity-20260925-restore-01/`，目录权限0700，文件0600。

- `before.json`：106份完整ES前镜像、31条联系人前镜像、历史邮件聚合校验值。
- SHA256：`b366101bf4880d79662c298189365d91daa071c283922ab2d62dd73fff27c20f`。
- `patch.json`：恢复字段清单及分类结果；`applied.json`：最终写入完成标记；`verification.json`：独立回读验证。
- 首次尝试因MySQL字符集比较冲突中止；MySQL事务未提交，106份ES全部条件回滚成功。第二次先逐份确认_source与原备份完全一致，再使用新seq_no执行ES CAS并完成MySQL事务。第一次及第二次日志均在同一目录保留。
- 回滚应从before.json按本批次改动字段恢复，并对当前值/CAS版本核验；不能直接覆盖后续人工修改。

## 尚未完成的范围

1. **5,304是风险筛查条数，不是确定错误数，也不是影响上限。** 本次恢复的53条中，52条在风险名单内，另1条Rizgar单条错绑未被主规则命中。其余风险记录需要逐条证据核验，不能批量猜名覆盖。
2. 两条历史业务ID仍呈现旧错误ORCID形状。为了不破坏ES定位与联系人/邮件关联，本次保留ES_id、_source.orcidId、expert_contact.orcid_id；真实ORCID写在externalIds。当前前端仍用orcidId拼ORCID链接，因此这两条详情链接仍会指向旧身份，需在代码阶段修复“业务ID当作者ID/链接”的问题；不能宣称所有身份展示问题已解决。
3. 这两人的正确学术指标尚未重新补全；目前为空。后续补全必须使用上表正确OpenAlex作者ID。
4. 当前XML解析漏洞仍在；没有扩大到全部45,648份论文来源档案逐篇复核，也未审计400多万非论文来源原始记录。本次恢复不等于整库身份核验完成。
