# 自动回复口径收口与工作台合并 · 主计划索引

日期：2026-08-18
基线 commit：`4583525`（main）
状态：待批准、未执行
性质：索引；实际执行以三个子计划为准，不得压成一次大改

## 背景

`LLM_AUTO_REPLY_ENABLED=false`（`application.yml:110`），自动回复处于预备态。上线前要解决三件事：

1. 自动路与 AI 训练 / 可信回复工作台**口径不一致**，且方向危险（自动比人工宽松）。
2. 自动回复预览与可信回复工作台在**同一个详情页里各有一套渲染器**。
3. 缺少「什么时候可以不看人」的可计算判据。

## 拆分与执行顺序

| 顺序 | 子计划 | 独立产出 | 文件数 | 子系统数 | 依赖 |
|---:|---|---|---:|---:|---|
| 1 | [01 decide 上下文收口](./01-decide-context-closure.md) | 自动路与工作台在「有无依据」上给出相同结论；训练知识对自动路生效 | 7 | 1（mail） | 无 |
| 2 | [02 预览并入工作台](./02-preview-into-workbench.md) | 详情页只剩一个渲染器；预览成为工作台第三宿主 | 10 | 2（frontend + llm） | 01 |
| 3 | [03 CRS 打分与阈值日志](./03-crs-scoring-and-log.md) | 每封信产出可解释置信分并落库，为阈值反解攒样本 | 8 | 2（mail + 迁移） | 01；建议在 02 后执行 |

每个子计划必须先通过自身 `## 验收标准`（fix-v 机器验证）与 `## 人工验收清单`（人执行），才进入下一阶段。

## 跨计划关键不变量

### X-1：`decide()` 始终是自动预览与自动实发的唯一共享决策点

`GroundedAutoReplyDecisionService.decide()` 的生产调用方恒为 2 处，不得增减：

```
$ grep -rn "groundedAutoReplyDecisionService\.decide" src/main --include=*.kt
src/main/kotlin/.../mail/service/AutoReplyPreviewService.kt:111
src/main/kotlin/.../mail/service/AutoMailReplyService.kt:505
```

任何门禁、打分、上下文构造的改动都必须落在 `decide()` 内部，不得在两个调用方各写一份。
来源：K-ai-generate-single-freeform-seam。

**注意该 K 条目正文已过时**：它记录的三个 `generate()` 生产入口在 01 执行后语义变化（自动入口开始携带完整上下文），Phase 6 已同步修订。

### X-2：预览永远是反事实，且永远出稿

预览不因 `autoReplyEnabled=false` / `MANUAL_HANDOFF` / 退订等运行期闸门隐藏正文，只把闸门作为 `wouldBeBlockedBy` 信息标记；预览服务不加 `@Transactional`、无 `save`/`send`。
来源：K-preview-mirrors-pipeline、K-preview-runtime-gates-visible。

03 引入的 CRS 与分档同样只是**标记**，不得在预览侧短路正文。

### X-3：无依据回答的授权边界不变

01/02/03 均不改动 `ANSWER_FROM_OPERATOR_INPUT` 的语义：这类回答保持 `UNSUPPORTED`、空 claims、必须显式采用，不转 QA evidence、不获得自动发送许可。
来源：K-grounding-status-ui-only、`trust-reply-unsupported-answer-v1` 计划 X-3。

### X-4：三个子计划都不改逐项生成结构

把自动路从「一次性全文 `generate()`」改成「逐项 `generateItem()` + assemble」是**第四阶段**的事，**不在本轮任何子计划范围内**。原因见下节。

## 明确不做（Out of scope · 全局）

- **逐项管线改造**：一封信 N 次 LLM 调用，而自动回复跑在 IMAP 拉取循环里
  （`MAIL_SCHEDULING_AUTO_REPLY_MAX_MESSAGES_PER_ACCOUNT` 默认 20，`application.yml:67`；
  `BatchAutoMailReplyService` 再跨账号循环；叠加 attempt 30s / total 300s 预算）。
  同步执行会拖垮收信链路。**该项的前置条件是先完成队列异步化或独立预算，本轮不做。**
- **放开 X-8**：`trust_reply_unsupported_answer_v1` 保持 `index:false`、禁止检索复用。
- **删除 `/api/mail/unmatched-inbound/{id}/auto-reply-preview` 端点**：02 只做前端合并，端点保留供回归对照，删除留待 02 人工验收通过后单独处理。
- **阈值实际启用自动发送**：03 只产出分数与日志，不放行任何自动投递。

## 已知前置缺陷（不在本轮修，但影响 03 的熔断有效性）

硬退信被误判为 SOFT（`dsn_status` 全 NULL），导致既有 5% 自动暂停保护从未真正触发。
03 的熔断条目依赖退信率，该缺陷不修则保护网为假。

**已有计划**：[bounce-dsn-classification-and-email-invalid-writeback.md](./bounce-dsn-classification-and-email-invalid-writeback.md)（同目录，同日）。
本轮 01/02/03 与它**互不阻塞**（无重叠文件），可并行推进；但**在任何自动投递放行之前，它必须先落地**。
