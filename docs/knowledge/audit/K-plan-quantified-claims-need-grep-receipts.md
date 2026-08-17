---
id: K-plan-quantified-claims-need-grep-receipts
domain: audit
created: 2026-08-12
last_used: 2026-08-16
hit_count: 2
source: create-p:batch-send-rhythm-and-filter-00-master
severity: P1
---

# 计划里的数字和全称判断必须附 grep 回执

2026-08-12 对一批 create-p 计划做二次自查，6 处问题里有 **5 处**属同两类：

**类型一：凭印象写出的计数。**
「`updateProgressWithAccumulator` 在介绍邮件循环有 8 个调用点、材料提醒有 7 个」——实测材料提醒是 **5**，且 `updateProgress()` 还有 1 个调用点完全漏列。
读过文件 ≠ 数过。通读时形成的「大概这么多」会以确定语气写进计划，执行 agent 拿它当清单用，漏改的那几处不会有任何编译错误。

**类型二：「仅 / 唯一 / 全部」这类全称判断。**
「仅测试中的 `service()` 工厂需改」——实测有 2 处手工实例化。
「`countSentByMailTypeSince` 无其他调用方」——生产侧确实唯一，但测试里有 6 处 Mockito stub，删掉生产调用后会抛 `UnnecessaryStubbingException`，报错信息与本次改动主题毫无关联，极易被误判为无关回归。

## 规则

计划中出现以下任一形式时，正文必须**贴出 grep 命令与输出**（或逐行列出命中位置），不接受「实测全集」四个字充当证据：

- 具体计数：「N 个调用点」「N 处残留」「共 N 个构造点」
- 全称判断：「仅」「唯一」「全部」「没有其他」
- 存在性否定：「该方法无调用点」「grep 结果为空」

## 三个反复出现的具体陷阱

1. **同名字符串 key 混入变量清单**。`dailySentTotal` 有 8 处命中，其中 2 处是进度 `details` map 的字符串 key（取值来自 `sent` / `breakdown.success`），与同名局部变量无关。按「grep 结果为空」写验收断言就会诱导执行 agent 删掉展示字段。**清理变量时，验收断言应写成「恰剩 N 行且均为 `"key" to ...` 形式」，不是「为空」。**
2. **只 grep `src/main`，漏掉 `src/test` 的 stub**。删除生产调用点会让对应的 Mockito 打桩变成无用 stub。这类 CI 失败的报错信息与改动主题无关联，排查成本高。**grep 时不要加 `--include` 把测试排除掉。**
3. **框架能力假设无仓库先例**。写 Spring Data JDBC `@Query` 返回 DTO 投影前，先 grep 本仓库全部 `@Query` 的返回类型——实测只有实体、标量、`List<String>` 三类，**零个 DTO 投影**。通行做法 ≠ 本仓库已验证。这类假设必须标注为「执行前先 spike」并给出有先例的降级方案。

关联：[[K-batch-task-config-implementation-evidence]]（绿测不能证明新计划已落地）、[[K-dom-stub-tests-hide-dangling-refs]]、[[K-ui-removal-retires-obsolete-contract-tests]]、[[K-entity-field-default-for-test-constructors]]

---

## 2026-08-16 更正（create-p:task-records-refactor-main）：陷阱 #3 的举例已过期

上文陷阱 #3 写「写 Spring Data JDBC `@Query` 返回 DTO 投影前，先 grep 本仓库全部 `@Query` 的返回类型——实测只有实体、标量、`List<String>` 三类，**零个 DTO 投影**」。

**该举例在 2026-08-16 已不成立。** grep 回执：

```
$ grep -rn -A8 "@Query(" src/main/kotlin --include=*.kt | grep -E "fun .*: (List<)?[A-Z][A-Za-z]+" \
    | grep -vE ": (List<)?(String|Long|Int|Boolean|Double)"
mail/repository/InboundMailProcessingRepository.kt:79    fun countGroupedByReasonType(): List<ReasonTypeCount>
mail/repository/InboundMailProcessingRepository.kt:157   fun findLastReceivedAtPerAccount(): List<SenderAccountLastReceived>
mail/repository/InboundMailTagRepository.kt:40           fun countQaTagsGroupedByRule(): List<QaTagCount>
mail/repository/InboundMailTagRepository.kt:63           fun countCustomTagsGroupedByLabel(): List<CustomTagCount>
task/repository/TaskExecutionRepository.kt:66            fun findLastStartedAtByBatchConfigIds(...): List<BatchConfigLastExecution>
campaign/repository/ExpertContactRepository.kt:122       fun countBindingsByAccount(): List<AccountBindingCount>
```

**6 个 DTO 投影先例**（写法：SELECT 列别名与 DTO 属性名对齐）。因此新计划里的列投影**无需再 spike**。

**陷阱 #3 的规则本身仍然成立**（"通行做法 ≠ 本仓库已验证，框架能力假设须先查先例"），失效的只是它举的那个例子。这条更正本身正是该规则的一次应用：**知识条目里的具体计数与全称判断也会过期，Phase 0 载入后必须重新 grep 验证，不能直接引用。**

反向的新例（2026-08-16 实测，尚未证伪）：`@Modifying` + `@Query` 在本仓库**只有 UPDATE 先例**（`TaskExecutionRepository.updateProgressCounts`、`TaskProgressLogRepository.rebindPendingExecutionId`），**DELETE 零先例** —— 见 [[K-modifying-delete-precedent]]（P3 执行时补 spike 结论）。
