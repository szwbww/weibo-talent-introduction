# 01b：ORCID 机构唯一性与任职语义

## 需求描述

ORCID 公开记录有且仅有一家非空机构时才自动写机构；多个不同机构不任选第一家。机构名不直接写成当前任职。不得改变 ORCID 邮箱/姓名/主键绑定和论文、SBIR 路径。范围外：历史回填、当前职位判定、ORCID 国籍推断。

## 关键不变量

### Invariant I-1: ORCID 多机构不猜
- Rule: `expanded-result.institution-name` 的非空去重结果恰为一项时取该项；零项或多项写 null。不得使用 `firstOrNull()` 代表主机构。
- Applies to: `OrcidDataSource.parseOrcidRecords`、`ExpertDiscoveryService.buildOrcidProfile`。
- Violation consequence: 随数组顺序变化把另一家机构赋给专家。
- 来源: original；`OrcidDataSource.kt:146-180`。

### Invariant I-2: 任职、国家、身份保守
- Rule: ORCID `institution-name` 只表达来源关联，不证明当前雇主/职位；`employment=null`，`country` 继续 null。邮箱、ORCID ID、姓名以及已有 `identityVerification` 生成路径不改变。
- Applies to: `ExpertDiscoveryService.buildOrcidProfile`。
- Violation consequence: 无职务证据却显示为当前任职或猜国籍。
- 来源: original；`ExpertDiscoveryService.kt:1177-1193`。

## 现状审计

### ORCID 来源与三层 ES
- Schema/mapping: 三层已有 `institution`、`employment`、`country`；本步不新增 ES 字段。
- Write paths: `OrcidDataSource.parseOrcidRecords` 当前取 `institution-name.firstOrNull()`、国家固定 null；`ExpertDiscoveryService.buildOrcidProfile` 把同一个机构名写入 `institution` 与 `employment`，再由 `toIndexMap` 写 RAW、晋升复制候选；`ExpertIndexWriterService` 晋升 `_source` 透传。（来源: K-promotion-source-passthrough）
- Read paths: `ExpertSearchService` 详情/发送、`ExpertClassificationService` 分类、`MailVariableService` 模板；本步不改这些读取，输出保守空值。
- Interaction points: ORCID 数组→RAW/候选机构→详情/模板/分类；ORCID 机构→任职展示。

## 实现方案

1. `OrcidDataSource` 对 `institution-name` 数组 trim、去空、去重；只在恰一项时返回机构。原邮箱/姓名/ORCID 解析不变。（I-1、I-2）
2. `ExpertDiscoveryService.buildOrcidProfile` 保留可证唯一机构，`employment=null`、`country=null`；多机构空机构参与现有分类/资格评估，不复制原文。（I-1、I-2）

## 变更文件清单

| 文件 | 改动 |
|---|---|
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/OrcidDataSource.kt` | 唯一机构提取 |
| `src/main/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryService.kt` | ORCID 任职保守写入 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/OrcidDataSourceTest.kt` | 单/多/空机构 |
| `src/test/kotlin/com/weibo/talentintroduction/discovery/service/ExpertDiscoveryServiceTest.kt` | RAW/候选字段与身份回归 |

## 验收标准

- I-1：单机构输出原名；两个不同机构输出 null；重复同名只算一家；数组为空输出 null。
- I-2：ORCID 建档 `employment=null`、`country=null`；原邮箱/姓名/ORCID ID 与身份验证断言通过；RAW/候选同值。

## 人工验收清单

### A-1: 单家与多家
- 前置条件: 测试 ORCID 源一条仅有 `Seoul National University`，另一条含 `Seoul National University` 和 `Korea University`；两条都有公开邮箱。
- 操作步骤: 运行 ORCID 发现，打开 RAW 与候选详情。
- 预期结果: 第一人两层机构为 `Seoul National University`；第二人两层机构为空；两人的任职和国家均为空。
- 覆盖: I-1、I-2、来源→ES→详情。

### A-2: 身份/其他来源回归
- 前置条件: 上述两人及一名论文来源、一名 SBIR 导入专家。
- 操作步骤: 对比发现前后邮箱、姓名、ORCID ID/专家主键，查看论文与 SBIR 档案。
- 预期结果: ORCID 身份字段相同；论文与 SBIR 原有来源字段及行为不因本步改变。
- 覆盖: I-2、不得改变其他来源。
