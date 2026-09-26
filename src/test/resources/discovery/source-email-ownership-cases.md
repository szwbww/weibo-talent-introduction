# 原文邮箱归属夹具

2026-09-26 人工核对既有公开原文存档。JSON 中 html 是原文作者区的完整结构片段（Edward 仅截取其独立作者节点），authors 保留该论文作者名单，sourceSha256 是完整 HTML 文件摘要。无网络依赖；测试预期不由解析器推导。

用户已批准修正原方案验收：Edward Raff 两条明确入库；两篇 Hang Zhao 的共享说明保留为归属不明，不要求入库。旧 reviews.json 的四条“已确认”结论不作为真值。

| 版本 / 来源 | 人工观察 | 预期 |
|---|---|---|
| [2609.21306v1](https://arxiv.org/html/2609.21306v1) | Edward Raff 独立 ltx_role_author 节点内，两机构下各有一个邮箱 | edward.raff@crowdstrike.com、raff.edward@umbc.edu 均属于 Edward Raff |
| [2609.22795v1](https://arxiv.org/html/2609.22795v1) | 三位作者共用机构说明列出三个邮箱，附在 Hang 节点下 | 三个邮箱均不从拼写或顺序猜归属 |
| [2609.21983v1](https://arxiv.org/html/2609.21983v1) | Ke Liu 节点内混有共同通讯作者说明及两个邮箱 | 两个邮箱均不广播给 Ke Liu，也不按邮箱拼写绑 Hang Zhao |

合成反例和正例在测试中标明：完整姓名—邮箱联系条目、同名作者节点、重复邮箱不同作者、跨段邻近、邮箱拼写猜名、单作者第三方邮箱、多邮箱、结构化共享说明。PDF 用 PDFBox 将明确标注的文本样本排版成 PDF 后走真实解析入口；不将合成 PDF 冒充下载原文。
