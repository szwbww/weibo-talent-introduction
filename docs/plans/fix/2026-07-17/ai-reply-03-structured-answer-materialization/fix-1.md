# fix-1：AI 回复结构化正文安全组装

## 原计划 / 子计划引用

- 原计划：`docs/plans/2026-07-13/ai-reply-evidence-safe-point-reply-plan-index.md`
- 子计划：`docs/plans/2026-07-13/ai-reply-03-structured-answer-materialization.md`

## 约束摘录

- I-2：`RequestGroundingStatus`、`STATUS:`、`UNSUPPORTED/PARTIAL/GROUNDED`、`UNSUPPORTED_TEXT/PARTIAL_CONFIRMATION/INSUFFICIENT_SAFE_REPLY` 不得进入 `draftText/renderedDraftText`；UNSUPPORTED 不生成正文 section。
- I-8：fallback 仅输出每项真实 QA body；UNSUPPORTED 省略；全项省略时只返回可用 frame。
- I-10：保留 layout/raw 变量边界，不改变 CTA 最终拦截。

## 修正记录

| ID | P1 问题 | 触发频率 |
|---|---|---|
| P1-1 | 所有请求均为 `UNSUPPORTED` 且 manual frame 无可用内容时，`composeFallback()` 返回空串，`enforceActionPolicy()` 改写为 `INSUFFICIENT_SAFE_REPLY`，内部状态文案进入 `draftText`。 | 有多请求邮件、均无审核依据且 frame 配置为空时；低频但可直接外发。 |

## 修复规格

### P1-1：空 fallback 不得注入内部文案

- 文件：`src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyDraftService.kt`
- 在 `enforceActionPolicy()` 的空文本处理处，区分 QA_GROUNDED 的“无可回答 item/仅空 frame”与普通空草稿。
- QA_GROUNDED 空 fallback 必须保留空串或仅现有 frame，绝不写入 `INSUFFICIENT_SAFE_REPLY`；仍保持现有 warning/coverage 让操作端提示人工处理。
- 不新增状态、DTO、接口或前端逻辑；CTA sanitizer 仍执行。
- 测试：在 `AiReplyDraftServiceTest` 覆盖“全部 UNSUPPORTED + salutation/greeting/ack/closing 均空 + disabled/null client/no response”三路径；断言 `draftText` 不含 `INSUFFICIENT_SAFE_REPLY`、`STATUS:`、`UNSUPPORTED`、`PARTIAL`。

## 当前状态

编译：PASS

测试：PASS — 106 JVM tests, 0 failures；Maven 附带 Node tests 221 passed, 0 failed。

命令：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=AiReplyGroundedDraftMaterializerTest,AiReplyPointByPointComposerTest,AiReplyDraftServiceTest,AiTrainingSimulateTest test
```

## 合规审计

- I-1 固定 JSON：✅ `AiReplyGroundedDraftMaterializer.kt:44-121` 严格校验顶层、answer 字段、index 集合与 unsupported 禁入；`AiReplyDraftService.kt:668-680` 约束模型输出。
- I-2 内部状态隔离：❌ `AiReplyDraftService.kt:385-393` 对空文本写入 `INSUFFICIENT_SAFE_REPLY`，可进入 `draftText`。
- I-3 后端 frame/heading：✅ `AiReplyPointByPointComposer.kt:16-44,82-108` 后端组装 frame、heading、编号。
- I-4 heading 清理：✅ `AiReplyPointByPointComposer.kt:130-157` 清 bullet、尾部标点/and、空白、首字母、160 字符。
- I-5 去重：✅ `AiReplyPointByPointComposer.kt:22-42,50-77,124-128` 相同 answer/fact 输出交叉引用。
- I-6 无效响应回退：✅ `AiReplyDraftService.kt:223-265` 无效 JSON 走 fallback、`usedLlm=false`、`FALLBACK_NO_RESPONSE` 与 warning。
- I-7 动作重试 JSON：✅ `AiReplyDraftService.kt:352-375,429-434` grounded retry 先 materialize；无效 retry 保留首稿。
- I-8 fallback 事实隔离：✅ `AiReplyPointByPointComposer.kt:52-79` unsupported/研究综合项省略，仅读取 item fact rules；但全项省略的最终空文本路径受 P1-1 影响。
- I-9 双入口/模式边界：✅ `AiReplyDraftService.kt:99-110,175-209` `generate()` 集中选 mode；QA_MATCHED/FREE_FORM 不 materialize。
- I-10 布局/变量：✅ `AiReplyPointByPointComposer.kt:87-108` 使用空行组装；`AiReplyDraftService.kt:378-395` 使用既有 sanitizer，未替换 raw 变量。
- I-11 多请求/研究优先 grounded：✅ `AiReplyDraftService.kt:99-110` 多请求或研究请求先入 QA_GROUNDED。
- 删除旧内部文案：✅ `AiReplyPointByPointComposer.kt:47-79` 不含旧 confirmation/unsupported 常量；P1-1 的 `INSUFFICIENT_SAFE_REPLY` 残留见上。
- No extras：✅ 变更落在子计划列出的 materializer/composer/service/测试文件；未见新增持久化、外部抓取或 controller 分支。

### 语义完整性检查

- Accumulation：✅ N/A，无时间窗口累计器。
- State machine：✅ N/A，本子计划未定义状态机转换。
- Cross-plan：✅ request→fact/status 矩阵由前序计划提供，`AiReplyDraftService.kt:98,224,453` 仅消费；已核对正常生成、无效响应回退和重试路径。P1-1 是本子计划内部的空正文边界。
