# CV-3 复验修复计划 fix-2：收紧 I-5 frame 变体断言

## 结论

本轮 fix-v 复验确认 `fix-1` 的大部分缺口已经补上：

- I-4：已新增旧请求 JSON 省略 `useVariants` 时默认 `false` 的测试。
- I-5：已新增 `resolveManualFrameForInbound(..., useVariants=false/true)` 与 `sendManualComposedReply(..., useVariants=true)` 测试。
- 验证命令通过：`PendingMailOperationServiceTest`、全量 `mvn test`、Node tests。

但 I-5 仍不能放行：当前测试名与验收语义要求“frame 片段在 `useVariants=true` 输出变体”，而测试夹具里 salutation/closing 的 id 会在当前 seed 下落到主体槽，因此用例没有硬断言这两个 frame 片段确实输出变体。

## 证据

当前测试 contact 使用 `orcidId = "orcid-1"`，`variantSeedFor` 返回 `"orcid-1".hashCode()`。`ContentVariantService.resolveBody` 对主文 + 1 条变体的池子使用：

```kotlin
Math.floorMod(seed + ownerId, 2)
```

在 `PendingMailOperationServiceTest.stubFrameSnippetVariants` 当前默认 id 下：

- `ackId = 100` -> index 1，输出变体。
- `salutationId = 201` -> index 0，输出主体。
- `greetingId = 202` -> index 1，输出变体。
- `closingId = 203` -> index 0，输出主体。

因此：

- `resolveManualFrameForInbound returns snippet variant bodies when useVariants is true` 用 `resolvedSnippetBody(...)` 计算期望值，会把 salutation/closing 的主体也当作通过结果。
- `sendManualComposedReply includes snippet variants in skeleton order when useVariants is true` 同样用 `resolvedSnippetBody(...)`，没有字面断言 `VARIANT salutation` / `VARIANT closing` 出现在外发正文。

这不影响当前生产实现判断，但不满足计划验收标准中 “frame 片段在 useVariants=true 用例中输出变体、false 输出主体” 的测试强度。

## 修复范围

只改测试：

- `src/test/kotlin/com/weibo/talentintroduction/mail/service/PendingMailOperationServiceTest.kt`

不改生产代码。

## 修复步骤

1. 调整 `stubFrameSnippetVariants` 在 I-5 变体测试中的 snippet id，使 salutation/greeting/closing/ack 全部在当前 seed 下命中变体槽。对 `orcid-1` 和 2 项池，使用偶数 id 即可命中 index 1。
2. 在 `useVariants=true` 测试中直接断言字面变体文本：
   - `VARIANT salutation`
   - `VARIANT greeting`
   - `VARIANT closing`
   - `VARIANT ack`
3. 在 `useVariants=false` 测试中继续断言字面主体文本：
   - `MAIN salutation`
   - `MAIN greeting`
   - `MAIN closing`
   - `MAIN ack`
4. 外发顺序测试也用字面变体文本做 `contains` 与 `indexOf`，避免 `resolvedSnippetBody(...)` 把主体选择伪装成变体通过。

## 验证命令

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=PendingMailOperationServiceTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
git diff --check
git diff --cached --check
```

## 放行标准

- I-5 true 路径对 salutation/greeting/closing/ack 四类 snippet 均字面断言变体文本。
- I-5 false 路径对 salutation/greeting/closing/ack 四类 snippet 均字面断言主体文本。
- 全量测试与 diff hygiene 通过。
