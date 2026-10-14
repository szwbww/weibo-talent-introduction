# 06-query 执行报告（epoch 1）

- Child：`06-query`
- Authoritative plan：`docs/plans/2026-10-13/discovery-person-06-query.md`
- Worktree：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-13-discovery-identity-recovery-master`
- Branch：`fast/2026-10-13-discovery-identity-recovery-master`
- 起始 HEAD：`498ea70244129e8e8819417d6ed7cf77e5759b84`（`docs(fast-p): prepare child 06-query`）
- child_base_sha（05-ingestion code head）：`7a96b5957b75096110e392b25a98bb9ea44990a5`
- 结果：READY_FOR_VERIFICATION

## 命令与计数

| 命令 | 退出码 | 计数 |
|---|---|---|
| C1 `JAVA_HOME=…/zulu-11.jdk/Contents/Home mvn -Dtest=DiscoveryPersonQueryServiceTest,ExpertSearchServiceTest,ExpertIndexControllerTest,ExpertContactManagementControllerTest test` | 0 | Tests run 123, Failures 0, Errors 0, Skipped 0 |
| ├ DiscoveryPersonQueryServiceTest | — | tests 11 / failures 0 / errors 0 |
| ├ ExpertSearchServiceTest | — | tests 79 / failures 0 / errors 0 |
| ├ ExpertIndexControllerTest | — | tests 20 / failures 0 / errors 0 |
| └ ExpertContactManagementControllerTest | — | tests 13 / failures 0 / errors 0 |
| JS 门禁（`mvn test` 内 exec-maven-plugin，`node --test`） | 0 | tests 1657 / suites 316 / pass 1657 / fail 0（基线 1657/1657/0，本计划不改前端） |
| `mvn -o compile`（主源码编译核对） | 0 | BUILD SUCCESS |

## 改动摘要（8 个授权文件）

1. `src/main/kotlin/…/discovery/service/DiscoveryPersonQueryService.kt`（新增）
   - 人物口径只读查询：把混合检索拆成 discovered / 其他两个分区。
   - discovered：`scrollExpertsFilteredWithSort` **完整**流式读取 → 按 500 条批量 `findMembers` 取人物关系 → 以有效 `personId` 精确归组（失效/无关系按独立档案，personKey=docId）；代表成员取原排序最前者。
   - 其他：`countExperts` 精确 count + 仅取归并所需前 `from+size` 条（`searchExpertsWithSort`）。
   - 以同一排序键（ES sort token，含方向）归并前缀 → 最终页；`totalHits = 其他精确数 + discovered 人物数`。
   - 聚合桶：其他 ES `aggregateTerms` + discovered 人物去重桶，分区相加。
   - 关闭 scroll；每批上限 `MAX_DISCOVERED_DOCS=100_000` / `DISCOVERED_BUDGET_MS=10_000`，超限抛 `DiscoveryPersonQueryUnavailableException`（可重试，不返回部分/近似 total）。
   - `personDetail(docId, level)`：跨层读成员（`findByDocumentIds`）、批量联系人、指纹核验（当前标签/证据指纹），只读返回 personId/revision/代表 docId/各成员层级/contactId/状态/证据来源/失效标记/差异列表。
   - 只读：无任何登记/修复/创建联系人写路径。
2. `src/main/kotlin/…/expert/service/ExpertSearchService.kt`（修改，**旧低层方法单位不变**）
   - 新增 `expertFilters`（复用原筛选构造的公开入口）、`searchExpertsWithSort`、`scrollExpertsFilteredWithSort`（保留 ES 排序键、finally 关闭 scroll）、`aggregateTerms`；新增 `ExpertHitWithSort`。发送侧依赖的低层方法逐字未改。
3. `src/main/kotlin/…/expert/controller/ExpertIndexController.kt`（修改）
   - `listExperts` 改走 `searchPersons` 返回人物口径 totalHits；`/email-providers`、`/regions`、`/tags/aggregation` 改走人物聚合（桶内按人去重）。
   - 新增只读 `GET /api/experts/person?docId=…&level=…`（无关系按独立档案，缺失返回 404）。
4. `src/main/kotlin/…/campaign/controller/ExpertContactManagementController.kt`（修改）
   - `listContacts` 新增可选 `groupDiscoveredPersons`（默认 false，旧契约逐字不变）与 `emailDomain`；true 分支在成员级完成既有状态/域名条件后按人物聚合，返回总人数、person 元数据、全部原 contactId。
5. `src/test/kotlin/…/discovery/service/DiscoveryPersonQueryServiceTest.kt`（新增 11）
6. `src/test/kotlin/…/expert/service/ExpertSearchServiceTest.kt`（修改 +4）
7. `src/test/kotlin/…/expert/controller/ExpertIndexControllerTest.kt`（修改，改 5 + 新增 2）
8. `src/test/kotlin/…/campaign/controller/ExpertContactManagementControllerTest.kt`（修改 +3）

## 测试覆盖（四份定向）

- 混合范围/去重/精确 total、跨页归并、RAW 独有邮箱不增候选数。
- 条件不拼接：两个分区共享同一套原筛选，仅差 discovered 标记。
- 跨层只读人物详情、差异展示不覆盖、缺失返回 null/404。
- 精确桶数（其他 + discovered 人物去重）。
- 超预算失败（每批 100,000 条上限 + 10s 预算）→ 可重试 `DiscoveryPersonQueryUnavailableException`，不返回部分数。
- 只读：只读路径不调用任何关系/联系人写方法。
- 滚动关闭（新流式方法 finally DELETE scroll）。

## 未决项（不伪造测量）

- **性能门禁未执行**：计划要求先在 51,829 条真实形状脱敏数据上测量 p95≤2s / 额外堆≤128MiB。本环境无该规模真实数据，**未做任何测量，不填任何数值**。该门禁保留给人工/上线前验证（与总计划「5 万级数据若不达预算先修订查询/投影子计划」一致）。已用单测覆盖其失败语义（上限/预算 → 查询不可用）与 scroll 关闭，但不以单测冒充规模性能实测。

## 边界遵守

- 只读消费人物关系，未新增投影写路径；无迁移/新字段；无前端改动。
- 未把 `personId` 填入旧 `orcidId` 参数；`ExpertSearchService` 发送侧低层方法单位不变。
- `docs/plans/fast/**` 未纳入提交。

## 提交

- `feat(fast-p): implement 06-query`，仅含 8 个授权文件（见下方 commit SHA）。
