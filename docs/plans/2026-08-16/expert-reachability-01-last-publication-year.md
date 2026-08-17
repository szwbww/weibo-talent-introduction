# 计划 01 — OpenAlex enrichment 补写 lastPublicationYear 【已作废】

> **状态：作废（2026-08-16，需求方决策）。本文件保留为决策日志，勿删、勿重新提出。**
>
> 依据项目 `CLAUDE.md` 的「Decision Log Protocol」：本计划属**已关闭决策**。
> 后续任何验证轮次不得将「计划族缺少 lastPublicationYear 步骤」报告为 P1 或开放问题。

## 作废原因

需求方 2026-08-16 明确：**「可以忽略『近年仍在发论文』」**。该维度从可达性口径中整体移除，
本计划失去唯一服务对象。

促成该决策的实测证据（见主计划 R-2 与知识条目 `K-openalex-fetch-works-gated`）：

`OPENALEX_FETCH_WORKS_ENABLED` **默认 false**：

```
application.yml:159:      fetch-works-enabled: ${OPENALEX_FETCH_WORKS_ENABLED:false}
OpenAlexProperties.kt:20:    val fetchWorksEnabled: Boolean = false,
```

故批量 enrichment 路径（`OpenAlexDataSource.kt:252`）根本不发 works 请求，
本计划在默认配置下对批量场景**零产出**；要覆盖存量必须开启该开关，
代价是每位专家增加 1~2 次 OpenAlex 请求与对应 `requestDelayMs` 等待。
「零额外 API 调用」的初始判断只对单专家路径（`:285`）成立。

## 连带作废的内容

- 主计划原「上线节奏」中「等 enrichment 轮完一圈再开过滤」的 30 天窗口约束 —— 已删除。
  新口径不依赖 enrichment，回填当天即全量覆盖（详见主计划「修正记录」）。
- 原计划 02 口径中的 `lastPublicationYear >= currentYear - RECENT_YEARS` 条件 —— 已删除。
- 原验证命令中的 `-Dtest=OpenAlexLastPublicationYearTest` —— 已删除。

## 若将来重启本计划

重启前必须先回答两个问题，二者均不可从代码得出：

1. `OPENALEX_FETCH_WORKS_ENABLED` 的线上取值；若为 false，是否接受开启后的请求量增长。
2. 生产 `mail_compose_template_block.custom_text` 中是否已有 `${lastPublicationYear}` 占位符
   （`SELECT id, template_code, custom_text FROM mail_compose_template_block WHERE custom_text LIKE '%lastPublicationYear%';`）
   —— 有的话，补齐该字段会直接改变外发邮件正文。

另注：`CandidateEligibilityService:47-50` 的 `enableActivityFilter` 消费该字段，
默认 false（`application.yml:121`）；重启本计划时须重新核对该开关的线上取值。
