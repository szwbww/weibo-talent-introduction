# 采集提前停止修复验证

授权与边界：[scope.md](scope.md)。main 基线 `b15bb069f43f89c45ee6859dba04a0baff5d774d`；本次未提交、未发布。

## 实现

- 内部邮箱查重对瞬态故障最多 3 次短尝试，失败作者可进入原有有限延迟恢复。
- 本轮恢复只消费尚未完成作者，不重新获取原文，不重复累计论文、作者及有效邮箱；全部完成才推进该页游标。ORCID 同样处理。
- Crossref / arXiv 搜索复用同页短尝试与有限延迟恢复；保留 OpenAlex 429 的既有预算延期行为。
- 各来源遵守自身计划重试时间；取消、时间预算和不可重试错误保留原检查点并停止。
- 最近失败仅留存阶段、异常类、HTTP 状态、耗时、批次、尝试次数、可重试标记。服务端逐次记录脱敏错误日志；进度与最终统计保存最近失败。没有添加完整身份或原始异常消息。
- 恢复上下文保存源内批次号，避免恢复后第 8 批被误记为第 1 批。
- 运行／历史来源表显示等待、重试耗尽、数据耗尽；专属采集日志展示非批次恢复事件。共享日志缓存的全部发现任务读取入口统一包含这些事件，其他任务仍按批读取。

## 验证记录

- 初次完整 Maven 生产编译、测试编译：退出 0。
- 首轮 Kotlin：263 项，261 通过；2 项本机 HTTP 测试因沙箱不允许绑定 `127.0.0.1` 临时端口失败，未发现断言失败。已在允许本机测试端口的执行环境重跑最终完整构建。
- 最终完整 Maven `test`（包含生产与测试重编）：退出 0。Kotlin 265 项全部通过，0 跳过：`ExpertDiscoveryServiceTest` 201、`DiscoveryPipelineServiceTest` 52、`DiscoveryPromotionProgressServiceTest` 12。包含新增 7 项故障恢复/诊断测试。
- Node：356 项全部通过，0 跳过。命令：`node --test src/test/js/task*.test.js src/test/js/discovery*.test.js src/test/js/meetingConfirmationAssets.test.js src/test/js/trustReplyWorkbenchSharedMount.test.js`。
- JavaScript 语法检查、限定文件 `git diff --check`：通过。
- 既有 `application.yml`、`docs/releases.json`、`CLAUDE.md` 修改保持原哈希，本次未改。
- 构建前后 9 个本次代码／测试文件哈希一致；[测试汇总及源文件哈希](test-results.json)。

日志：`/tmp/discovery-retry-kotlin-final.log`、`/tmp/discovery-retry-node-final.log`。最终执行使用 JDK 11：

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home /opt/homebrew/bin/mvn -q test -Dtest=ExpertDiscoveryServiceTest,DiscoveryPipelineServiceTest,DiscoveryPromotionProgressServiceTest -DskipNodeTests=true
```

## 独立源码审核

按仓库 fix-v 流程做只读审核；依据为本次用户直接批准的修复范围。实现阶段已修正三个有代码证据的问题：多来源恢复提前执行、共享日志缓存覆盖恢复事件、恢复后的诊断批次号归零。

独立审核 `/root/discovery_retry_review` 结论：**PASS（AUDIT_ONLY，机器验证）**。逐路径核对失败回调／游标推进（`ExpertDiscoveryService.kt:689`）、独立重试时刻（`:1209`）、批次基线（`:798`、`:1430`）、脱敏诊断（`:2823`）和共享日志读取（`task-modal-runtime.js:129`）；核对最终测试回执及冻结哈希，无剩余明确阻断。人工线上效果验收仍为 PENDING。

## 限制

本次不处理无开放全文地址、下载方拒绝访问或资格规则；不保证外部服务恢复成功或具体文件增量。人工线上验收待发布后执行，本轮没有启动线上采集或重审任务。
