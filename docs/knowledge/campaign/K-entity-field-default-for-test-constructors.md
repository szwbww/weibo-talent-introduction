---
id: K-entity-field-default-for-test-constructors
domain: campaign
created: 2026-08-12
last_used: 2026-08-16
hit_count: 1
source: create-p:batch-send-rhythm-01-rounds-per-run
severity: P2
---

给 `batch_send_task_config` 这类被广泛构造的实体新增字段时，**Kotlin data class 的新字段必须带默认值**，否则会打断其全部具名参数构造点，把一批范围外的测试类拖进变更清单，突破计划的文件数约束。

`BatchSendTaskConfig(` 全仓 **11 个构造点**（2026-08-12 grep 实测），生产侧只有 1 个：

| 位置 | 侧 |
|---|---|
| `BatchSendTaskConfigService.kt:51` | 生产 |
| `BatchSendConfigControllerTest.kt:52,73` | 测试 |
| `BatchSendSchedulerTest.kt:26` | 测试 |
| `BatchSendTaskRuntimeIntegrationTest.kt:630` | 测试 |
| `BatchSendControlServiceTest.kt:78,204` | 测试 |
| `BatchSendTaskConfigServiceTest.kt:117,428,467` | 测试 |
| `ManualInitialOutreachServiceTest.kt:2240` | 测试 |

`BatchExecutionSnapshot(` 同理有 3 个测试构造点（`BatchSendControlServiceTest.kt:45,122`、`BatchSendTaskRuntimeIntegrationTest.kt:645`）+ 3 个生产点。

默认值应取**安全侧**（少发不多发、不限制而非全限制），因为它只在两种场景生效：测试构造缺参、Jackson 反序列化缺字段。真实取值一律由服务层显式写入或由迁移回填。

**反向推论**：删除字段时没有等价规避手段——删字段必然打断全部具名构造点。因此「删列」类需求的文件数天然比「加列」高一倍，规划时必须按删除侧估算，且不可再拆（拆了会留下无法编译的中间提交）。

关联：[[K-batch-config-legacy-adapter-field-preservation]]、[[K-batch-task-config-snapshot-log-identity]]
