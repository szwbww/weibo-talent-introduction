# Execution Log — 01-region

（由实施者写入；append-only）

---

## 2026-10-14 · Impl01Region · 实施提交

- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-14-batch-filter-main`
- Branch: `fast/2026-10-14-batch-filter-main`
- child_base_sha: `b02eeb6405b27d5d4f30a32d17019e19c855483d`
- 实施提交：`feat(fast-p): implement 01-region`（仅 4 个授权文件，见下）
- 结果：**READY_FOR_VERIFICATION**（无 PLAN_CONFLICT、无 BLOCKED）

### 1. 变更文件（提交内唯一路径集合）

| 文件 | insertions | deletions |
|---|---:|---:|
| `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/BatchExecutionModels.kt` | 6 | 8 |
| `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachService.kt` | 40 | 1 |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/service/BatchRecipientSelectionServiceTest.kt` | 104 | 2 |
| `src/test/kotlin/com/weibo/talentintroduction/campaign/service/ManualInitialOutreachServiceTest.kt` | 68 | 2 |

无新增产品文件、无新增持久字段、无其他文件改动（`ExpertSearchService` / `CountryContinentMapping` / ES mapping / migration 均未触碰）。
`docs/plans/fast/**` 下的 fast-p 证据文件未进入该提交（工作树里 `ledger.md` 的既有改动由控制器维护，未被 add）。

### 2. 实现摘要

**I-1（`BatchExecutionModels.kt` → `RecipientScope.matchesRegion`）**

发现/待确认档案分支删除了「country 为空即拒绝」「region == REGION_OTHER 即拒绝」两条无条件拒绝，改为：

```kotlin
return CountryContinentMapping.toRegion(profile.country) in regions
```

`toRegion` 对 null / 空白 / 字面 `Other` / 未识别值一律返回 `REGION_OTHER`，因此：

- `regions` 含 `Other` → 未知国家通过；
- `regions` 不含 `Other` → 未知国家拒绝（Japan 等明确国家不会因选中 `Other` 而放行）；
- `regions` 为空 → 方法不被调用（`mismatchKeys` 的 `regions.isNotEmpty()` 守卫），维持不限地区；
- 非发现分支 `regions.any { regionOf(country) == it || regionOf(nationality) == it }` 逐字未改。

**I-2（`ManualInitialOutreachService.kt` → `buildEsFiltersForLevel`）**

```kotlin
ExpertSearchService.regionsFilter(scope.regions)?.let { regionFilter ->
    filters.add(withDiscoveryUnmappedCountryBranch(scope.regions, regionFilter))
}
```

新增私有函数 `withDiscoveryUnmappedCountryBranch(regions, regionFilter)`：

- 仅当 `CountryContinentMapping.REGION_OTHER in regions` 时包裹，否则逐字返回原 `regionsFilter` 结果；
- 包裹形态为 `bool.should = [ 既有地区查询, { must: [term tags=discovered], must_not: [exists country] } ]` + `minimum_should_match = 1`；
- 纯并集扩展（只增加成员、不新增 must 条件），因此不会提前丢弃非地区原因的目标；空白/未识别**非空**国家由既有 `Other` 查询（`exists country AND NOT terms known`）覆盖，此分支只补「字段不存在」这一类；
- 新增文件级常量 `DISCOVERED_TAG = "discovered"`（写入侧 `ExpertDiscoveryService.toIndexMap` 同值）与 `CountryContinentMapping` import。

调用路径全部经 `buildEsFiltersForLevel`：`countEsEstimate`、`countEsTargets`（预估 scroll）、`fetchEsPage`（执行取页）。NEW 重试路径的最终判定走同一个 `RecipientScope`（经 `BatchRecipientSelectionService`），因此三处口径一致。

**02 可复用接口（下游约束）**：`RecipientScope.matchesRegion` 的 `toRegion(...) in regions` 判定是唯一实现；`withDiscoveryUnmappedCountryBranch` 作为 `buildEsFiltersForLevel` 内的私有 seam，02 可在同文件内复用来构造母集查询，无需复制旧逻辑。

### 3. 命令与结果（JDK11，全新执行）

```bash
cd /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-14-batch-filter-main
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn -B test -Dtest='BatchRecipientSelectionServiceTest,ManualInitialOutreachServiceTest,CountryContinentMappingTest,ExpertSearchServiceTest' -DfailIfNoTests=false
```

- 结果：`BUILD SUCCESS`，`Total time: 02:56 min`（2026-10-14T09:39:07+08:00）。
- surefire（`target/surefire-reports/`）：

| 测试类 | Tests run | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| `BatchRecipientSelectionServiceTest` | 13 | 0 | 0 | 0 |
| `ManualInitialOutreachServiceTest` | 185 | 0 | 0 | 0 |
| `CountryContinentMappingTest` | 9 | 0 | 0 | 0 |
| `ExpertSearchServiceTest` | 75 | 0 | 0 | 0 |
| **合计** | **282** | **0** | **0** | **0** |

- 同一 `mvn test` 生命周期内绑定的 JS 门禁（exec-maven-plugin）也通过：`node --test src/test/js/*.test.js` → `tests 1657 / pass 1657 / fail 0`。本阶段无前端改动。
- 未跳过、未禁用任何用例；无 `-DmigrationIt` 相关 IT（本阶段无迁移）。

### 4. 新增/修改用例与不变量证据映射

**`BatchRecipientSelectionServiceTest`（+5 用例，8 → 13）**

| 用例 | 覆盖 |
|---|---|
| `region Other admits every unknown country of a discovered expert (I-1)` | null / `""` / `"   "` / `"Other"` / `"Atlantis"` 五种未知值在 `regions=[Other]` 下全部 included，且无 `REGION` 原因命中 |
| `region Other never admits a recognised country (I-1)` | Japan / China 在 `regions=[Other]` 下全排除，`reasonHits[REGION]==2`，`filterKeys == [REGION]` |
| `empty regions stay unrestricted for every discovered country value (I-1)` | 7 种国家值 + `regions=[]` 全部 included，无 `REGION` 原因 |
| `recognised region judges a discovered expert by country alone (I-1)` | `Asia (Japan & Korea)`：Japan 通过；null/空白/未识别/France 全排除（4 次原因命中） |
| `non-discovery region rule is unchanged by the Other bucket (I-1 regression)` | 非发现档案仍 country OR nationality：nationality=Japan 与 country=Germany 都不属于 `Other`；Japan/Korea、Europe 既有语义不变 |

fixture 助手新增 `country` / `nationality` 命名参数（默认值 `"China"` / `null`），既有调用逐字不受影响。

**`ManualInitialOutreachServiceTest`（+2 用例、改 1 断言）**

| 用例 | 覆盖 |
|---|---|
| `ES region sieve widens with the discovered missing-country branch only when Other is selected (I-2)` | 断言包裹后 `bool.should == [ExpertSearchService.regionsFilter(["Other"]), discoveredBranch]` 且 `minimum_should_match == 1`；`discoveredBranch == {must:[term tags=discovered], must_not:[exists country]}`；`regions=[Europe]` 时 filters 逐字包含未包裹的 `regionsFilter(["Europe"])` 且完全不含 `discovered` |
| `discovered experts without a country pass the Other sieve in preview and run (I-1 I-2 X1)` | 同一快照下 `countBySnapshot` 与 `run` 同口径（`preview.totalSendable == result.total` = 2）；null 国家与 `Atlantis` 两人 sent；`Japan` 不因 `Other` 放行；captor 断言发送所用 profile 的 `country` 仍是原值 `[null, "Atlantis"]`（I-3 只读，无档案改写） |
| `discovery region uses country only and non-discovery uses country or nationality (I-1 I-2)`（既有，更新断言） | `regions=[Other]` 由 0 → 2（C001 null / C002 未识别），Japan 与非发现档案仍不通过；不限地区 = 4、Japan/Korea = 2 逐字不变 |

M-4/M-5：新增用例全部为内存 fixture + Mockito 读取面（`searchExpertsFiltered` / `scrollExpertsFiltered` / `countExperts` / `resolveAdmissionBatch`），无 SMTP stub 调用、无 Emailable、无生产/线上访问，无新增业务写入。

M-1 共享文件接续：本次只在 `ManualInitialOutreachService.kt` 新增 seam 并对 `buildEsFiltersForLevel` 做并集扩展，未复制旧函数、未覆盖 01 之前的语义，02 可直接在其上构建母集查询。`ManualInitialOutreachServiceTest` / `BatchRecipientSelectionServiceTest` 的 01 用例保留在文件中。

### 5. 未决风险 / 说明

1. **ES 补充分支的判据是 `tags=discovered`**（逐字按子计划 I-2 描述实现）。发现收录路径（`ExpertDiscoveryService.toIndexMap`）在写 `identityVerification` 的同一次 doc 里固定写 `tags=["discovered"]`，故正常路径一致。理论风险：若历史运维脚本直接写 ES、留下「有 `identityVerification` 但无 `discovered` 标签且 `country` 字段缺失」的候选文档，这类档案仍会被粗筛漏掉。子计划未授权为此新增第二分支（例如 `identityVerification exists`），未擅自扩宽；留待 02 的诊断母集或人工审计确认是否需要第二种标签表达。
2. **空白/未识别非空国家依赖既有 `Other` 查询**（`exists country AND NOT terms known`），本阶段未改动该查询；内存侧对空白国家同样落到 `REGION_OTHER`，两侧一致。
3. **非发现档案的 `Other` 语义未变**（country OR nationality 映射到大区）。因此非发现档案若 `country="Atlantis"`，在选中 `Other` 时仍会在内存/ES 两侧通过 —— 这是既有语义，不在 I-1 范围内，本次未改。
4. 2026-10-14 审计快照里的 4,878 人只是冻结证据，本报告不引用其为实时断言。
5. 本阶段未运行全量 `mvn test`（含 `OperatorStatusWriteSeamGuardTest`）。已复核：该守卫只按 `path + 行号 + context` 排除 `EXCLUDED_NOISE_SITES` 中登记的文件（`ExpertSearchService.kt` / `ExpertContactRepository.kt` / `MailRecordRepository.kt` / `UnmatchedInboundMailController.kt` / `MailboxService.kt` / `ExpertIndexController.kt`），`ManualInitialOutreachService.kt` 只出现在 `ALLOWED_WRITE_SITES` 名称集合中、无行号钉；本次新增行不含 `operatorStatus = ` / `operator_status` 文本，故不会触红该守卫。
