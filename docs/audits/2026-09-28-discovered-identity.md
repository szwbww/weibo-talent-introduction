# 2026-09-28 新发现专家身份核验

只读核验时间：2026-09-28 10:01（Asia/Shanghai）。范围：`discoveredAt` 在 2026-09-28 当天、当前仍带 `discovered` 标签的记录。线上 `orcid_info` 98 条、`orcid_info_candidate` 2 条（与原始层重复）、`orcid_info_application` 0 条；按专家 ID 去重后 98 人。09:37 与 10:01 两次读取的名单一致。来源：arXiv 86 条（66 篇）、OpenAlex 10 条、Crossref 2 条。98 条均被系统标为 `VERIFIED`，本报告不沿用该结论。

核验方法：对 arXiv 的 66 篇逐一读取论文 PDF 前三页，并读取可用的 HTML；对期刊论文读取 6 篇 Europe PMC 全文 XML、1 篇机构库收录的作者稿、1 篇 arXiv 作者稿。核对论文作者、脚注/通讯邮箱，并与专家姓名和已存邮箱逐条比较。未对线上数据做修改。

## 已确认问题：5 条

| 线上 ID | 当前姓名 | 当前邮箱 | 原文证据 | 结论 |
| --- | --- | --- | --- | --- |
| `EMAIL-7c15d51c41baea6e403` | Jieyuan Liu | `zhenwang.work@gmail.com` | [arXiv:2609.15938v1](https://arxiv.org/pdf/2609.15938v1) 列出 Jieyuan Liu、Zhen Wang 为两位通讯作者；[Zhen Wang 本人主页](https://zhenwang9102.github.io/) 明确列该邮箱；同批另一条 Jieyuan Liu 记录为 `jil029@ucsd.edu` | **确认姓名—邮箱错绑**：该邮箱属于同篇论文的 Zhen Wang。 |
| `EMAIL-fc0d6376fe237629219` | Xingwang Li | `bupt@gmail.com` | [arXiv:2609.22309v1](https://arxiv.org/html/2609.22309v1) 的 Xingwang Li 注脚写 `lixingwangbupt@gmail.com` | 原文邮箱被截成后缀；不能据此确认短地址归属。 |
| `EMAIL-2c3f699c1bfd0fa901e` | Yinqiu Liu | `qiu001@e.ntu.edu.sg` | [arXiv:2609.14476v1](https://arxiv.org/html/2609.14476v1) 的 Yinqiu Liu 注脚写 `yinqiu001@e.ntu.edu.sg` | 原文邮箱被截成后缀；不能据此确认短地址归属。 |
| `EMAIL-581aa454d47e432afb9` | Dier Tang | `math@connect.hku.hk` | [arXiv:2609.15825v1](https://arxiv.org/html/2609.15825v1) 注脚写 `tangde_math@connect.hku.hk` | 原文邮箱被截成后缀；不能据此确认短地址归属。 |
| `EMAIL-6f19d554a7074f345cc` | Shaohan Feng | `shaohan@mail.zjgsu.edu.cn` | [arXiv:2609.14952v1](https://arxiv.org/html/2609.14952v1) 的 Shaohan Feng 注脚写 `feng_shaohan@mail.zjgsu.edu.cn` | 原文邮箱被截成后缀；不能据此确认短地址归属。 |

这 4 条截断记录的 PDF 抽取文本在邮箱用户名中插入换行、空格或连字符；子串检索可误判为“原文包含当前邮箱”，应以 HTML 或 PDF 版面中的完整地址为准。

## 另有 1 条待确认

`EMAIL-e181ea399558e01726f`：Luca Larcher，当前 `larcher@amat.com`。[arXiv:2609.15326v1](https://arxiv.org/pdf/2609.15326v1) 论文脚注印作 `Luca Larcher@amat.com`（含空格，非合法邮箱），无法单凭该论文确认线上地址；未计入确认错绑。

其余 92 条经本轮来源比对，未发现可确认的姓名—邮箱错绑；“未发现”不等于每个地址均独立证实归属。学术类型字段中没有发现可由原文证明的跨人错绑。所有问题记录当前均为 `VERIFIED`，因此该状态不能作为发送前的身份保证。

## 原因复现

用当天论文 PDF 重放当前抽取器，5 条问题均可再现。`2609.15938v1` 的首页同时有 Jieyuan Liu 和 Zhen Wang 两位 `*` 通讯作者，抽取器截取的作者标题却只识别到 Jieyuan Liu 的 `*`，随后把同一 `*Correspondence` 行中的两个邮箱都绑定给他。其他 4 篇 PDF 的用户名被换行、连字符或空格拆开；邮箱正则把剩余后缀识别为完整合法地址，联系人版面规则再将该后缀与正确作者关联。来源证据哈希只证明抽取器曾读到相应文本，不独立证明邮箱完整或归属正确，因此这些错误仍获 `VERIFIED`。此前 2026-09-27 的 1,999 条修复是指定旧记录的数据修复，报告明确记载“采集代码未修改”，所以 9 月 28 日新采集数据继续触发这两个边界条件。

## 独立分支修复（未部署）

分支 `codex/discovery-identity-proof`：PDF 版面抽取在把通讯标记绑定给作者前检查完整作者名单；多名作者共用标记时不自动分配邮箱。邮箱解析拒绝被换行、空格、连字符拆断后剩余的用户名后缀，版面证据也使用同一筛选。抽取缓存版本从 `20261001` 递增至 `20261002`；旧队列结果按现有消费门禁拒绝，不会被当作新规则的证据。上线前需评估旧队列失败项的重新抽取，不能把旧缓存直接重新标成新版。

回归包含本批 5 篇原始 PDF，预期错误邮箱不得携带作者身份或生成版面联系人证据。分支未改变线上 5 条记录。上线后另需备份并修复/隔离这 5 条原始层数据，并回读验证；此步骤不随代码分支自动执行。
