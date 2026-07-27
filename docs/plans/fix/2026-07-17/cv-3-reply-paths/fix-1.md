# CV-3 复验修复计划 fix-1：补齐 I-4/I-5 验收用例

## 结论

本轮 fix-v 复验未发现 `cv-3-reply-paths` 的主实现路径偏离：自动 QA seed、预览镜像、人工规则正文解析、`overrideTextBody` 优先级、AI 草稿恒主体、composer 零改动均符合计划。

但计划验收标准明确要求 I-4/I-5 有用例覆盖，当前测试缺口如下，故复验不能放行：

1. I-4 缺少“不带 `useVariants` 的旧请求 JSON 反序列化后为 false”的用例。
2. I-5 缺少“frame 片段在 `useVariants=true` 输出变体、false 输出主体”的用例，尤其是 `REPLY_SNIPPET` 的 salutation/greeting/closing/ack 解析。

## 证据

- `PendingQaReplyRequest` / `PendingManualRichReplyRequest` / `ComposedReplyRequest` 已有 `useVariants: Boolean = false`，但 `rg "readValue|PendingQaReplyRequest|PendingManualRichReplyRequest|ComposedReplyRequest"` 未找到对应 JSON 默认值测试。
- `PendingMailOperationService.resolveManualFrame` / `resolveAckContent` 已在消费点调用 `ContentVariantService.resolveBody(REPLY_SNIPPET, ...)`，但 `PendingMailOperationServiceTest` 现有变体测试只覆盖 QA rule body 的 suggest/send 同文与 override 优先级。
- `mvn test` 通过：JVM tests 1317 run / 0 failures / 0 errors / 3 skipped；Node tests 198 pass。
- `git diff --check` 与 `git diff --cached --check` 均通过。

## 修复范围

只改测试：

- `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt`

不改生产代码，除非新增测试暴露真实行为错误。

## 修复步骤

1. 在 `PendingMailOperationServiceTest` 增加 JSON 反序列化默认值测试：
   - 用 `ObjectMapper().registerKotlinModule()` 或现有 mapper。
   - 分别反序列化不含 `useVariants` 的 `PendingQaReplyRequest`、`PendingManualRichReplyRequest`、`ComposedReplyRequest`。
   - 断言三者 `useVariants == false`。

2. 增加 `REPLY_SNIPPET` 变体 fixture：
   - stub `replySnippetService.resolveManualFrame()` 返回 salutation/greeting/closing 与 ack option。
   - stub `replySnippetService.listByType(SALUTATION/GREETING/CLOSING)` 返回对应默认 snippet，带稳定 id。
   - stub `replySnippetService.resolveAck(ackId)` 返回 ack 主体。
   - 在 `contentVariantRepository.findByOwnerTypeAndOwnerIdAndEnabledTrueOrderByVariantOrderAscIdAsc(REPLY_SNIPPET, id)` 返回一条变体。

3. 增加 I-5 双态测试：
   - `resolveManualFrameForInbound(id, useVariants=false)` 返回 salutation/greeting/closing/ack 主体。
   - `resolveManualFrameForInbound(id, useVariants=true)` 返回对应变体。
   - `sendManualComposedReply(... ackSnippetId=ackId, useVariants=true)` 发出的正文包含 ack/snippet 变体，且仍保持 salutation -> ack -> greeting -> rule -> closing 顺序。

4. 保持现有规则正文 suggest/send 同文测试不变；如新增 helper，只服务本测试文件，不抽公共抽象。

## 验证命令

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=PendingMailOperationServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
git diff --check
git diff --cached --check
```

## 放行标准

- I-4 JSON 默认 false 用例存在并通过。
- I-5 frame/ack 主体与变体双态用例存在并通过。
- 全量 Maven + Node 测试通过。
- diff hygiene 通过。
