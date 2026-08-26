# institutionType 真实分布盘点执行手册

> 对应计划：`docs/plans/2026-08-25/05a-institution-type-collection.md` 的验收项 **A5a-7**
> 产出：一张真实分布与交叉表，作为 **05B（分类规则改造）评分数值的唯一依据**
> 前置：05A 已发布到线上（`institutionType` 字段已在三层 mapping 中声明并开始写入）
> 任务类型：`EXPERT_ENRICHMENT`

**本手册是只读盘点 + 一次既有任务的手动触发，不包含任何代码改动、不写入新字段、不改配置。**

> **执行边界（越界即停止）**
> - 不得修改任何源码，尤其**不得**为了让更多文档进入 enrichment 范围而改动
>   `ExpertDiscoveryService.kt:856` 的 `minusDays(30)`——那是改生产行为，不是盘点。
> - 不得执行 `_update_by_query`、`_reindex`、`_delete_by_query` 等任何 ES 写操作。
> - 不得改动 `application.yml` 或任何环境变量。
> - 任何一步出现「停止条件」，**立即停止并把已有输出交回**，不要自行绕过。

---

## 0. 变量

```bash
export BASE_URL='https://<APP_HOST>/api'
export ES_BASE_URL='https://<ES_HOST>:9200'
export ES_AUTH='<ES_USERNAME>:<ES_PASSWORD>'      # 只读账号即可
export CANDIDATE_INDEX='orcid_info_candidate'
export COOKIE_JAR='/tmp/itd_cookies.txt'          # 仅存 /tmp，结束后删除
```

> 不要把真实密码写进任何文件或提交记录；只在本会话 `export`。
> 变量名不要用 `HOME` 等可能被工具链覆盖的名字。

管理 API 需要已登录会话，登录方式沿用
`docs/runbooks/expert-classification-backfill.md` 第 3 步。

---

## 1. 前置确认：字段是否真的已声明并生效

```bash
curl -sS -u "$ES_AUTH" "$ES_BASE_URL/$CANDIDATE_INDEX/_mapping" \
 | python3 -c "
import sys,json
d=json.load(sys.stdin)
props=list(d.values())[0]['mappings']['properties']
it=props.get('institutionType')
print('institutionType 声明 =', it)
print('institution      声明 =', props.get('institution'))
"
```

**预期**：`institutionType` 为 `{'type': 'keyword'}`。

**停止条件**：
- 输出 `None` → 05A 的 mapping 未推送到线上。停止，报告「mapping 未生效」。
- 类型不是 `keyword`（例如是 `text`）→ 停止，报告实际类型。**不要**尝试自行修改 mapping。

三层索引都查一遍（把 `$CANDIDATE_INDEX` 换成 RAW 与 APPLICATION 索引名各跑一次），
三份必须都是 `keyword` 且逐字相同。

---

## 2. 摸底：现在有多少数据

### 2.1 enrichment 待处理量

```bash
curl -sS -b "$COOKIE_JAR" "$BASE_URL/expert-discovery/enrich/stats" | python3 -m json.tool
```

返回三个字段：`pending`（本轮能覆盖的量）、`enrichedLast30d`、`total`。

### 2.2 字段当前覆盖率

```bash
curl -sS -u "$ES_AUTH" -H 'Content-Type: application/json' \
  "$ES_BASE_URL/$CANDIDATE_INDEX/_search" -d '{
    "size":0,
    "aggs":{
      "has":{"filter":{"exists":{"field":"institutionType"}}},
      "missing":{"missing":{"field":"institutionType"}},
      "has_email":{"filter":{"bool":{"filter":[
        {"exists":{"field":"institutionType"}},{"exists":{"field":"email"}}]}}}
    }}' | python3 -m json.tool
```

**记录**：`total` / `has` / `missing` / `has_email` 四个数字。

> **预期在此刻是接近全缺失的** —— 05A 只在写入时产生该字段，
> 存量文档要等 enrichment 跑过才有值。这不是缺陷。

---

## 3. 触发一次 enrichment

> **只有在第 2.1 步的 `pending > 0` 时才执行本步。** `pending == 0` 时跳到第 5 步并如实记录。

```bash
curl -sS -b "$COOKIE_JAR" -X POST "$BASE_URL/expert-discovery/enrich" | python3 -m json.tool
```

任务类型 `EXPERT_ENRICHMENT`，与其他任务**互斥**（`tryStartWithToken`）。
若返回 409「任务正在执行中」，说明有别的任务在跑，**等待，不要强行重试**。

### 监控进度

```bash
watch -n 30 "curl -sS -b '$COOKIE_JAR' '$BASE_URL/task-progress/EXPERT_ENRICHMENT' | python3 -m json.tool"
```

### 耗时预期

批量补全按 `enrichmentBatchSize`（默认 50，`application.yml`）分块，
每块间 `enrichmentDelayMs`（默认 300ms）等待；
`enrichmentRateLimitMode=WAIT` 时命中 OpenAlex 限流会退避重试，最长退避
`enrichmentMaxBackoffMs`（默认 1800000ms = 30 分钟）。

因此 **`pending` 上万时耗时以小时计**。可以让它跑，中途用上面的命令看进度。

**停止条件**：
- 进度 `status` 变为 `FAILED` → 记录 `message` 与 `failureReasons`，停止。
- 连续 30 分钟 `processedCount` 无变化 → 可能卡在限流退避，记录当前进度后停止并报告。

### 允许中途停止

不需要跑完全量。**`has` 达到 5000 条以上即可进入盘点**，
样本量足够反映分布。需要提前结束时用任务取消接口，不要直接杀进程。

---

## 4. 等待 ES 刷新

enrichment 写入后 ES 需要短暂刷新才能被聚合到。等待 60 秒，或显式刷新：

```bash
sleep 60
curl -sS -u "$ES_AUTH" -X POST "$ES_BASE_URL/$CANDIDATE_INDEX/_refresh"
```

`_refresh` 是只读盘点的例外许可，它不修改任何文档。

---

## 5. 盘点

```bash
export ES_URL="$ES_BASE_URL"
export IDX="$CANDIDATE_INDEX"
bash docs/plans/2026-08-25/scripts/institution-type-distribution.sh
```

脚本产出六组输出，**全部原样保留**：

| 组 | 内容 |
|---|---|
| ① | 覆盖率（总数 / 有值 / 缺失 / 有值且有邮箱） |
| ② | `institutionType` 取值分布 |
| ③ | **机构类型 × 当前分类结果 交叉表** |
| ④ | 同 ③，但只统计有邮箱的文档 |
| ⑤ | `company` 的 15 个样本机构名 + 其当前分类 |
| ⑥ | `healthcare` 的 15 个样本机构名 + 其当前分类 |

**停止条件**：① 中 `has < 500` → 样本不足，分布不可信。停止，报告覆盖率，
不要基于不足 500 条的样本下任何结论。

---

## 6. 结果记录（执行方填写，原样交回）

```
### 第 1 步 mapping 确认
RAW / CANDIDATE / APPLICATION 的 institutionType 声明：

### 第 2 步 摸底
enrich/stats:  pending=____  enrichedLast30d=____  total=____
覆盖率(执行 enrichment 前):  total=____  has=____  missing=____  has_email=____

### 第 3 步 enrichment
启动时间____  结束时间____  最终 status=____
enriched=____  failed=____  failureReasons=____
是否中途停止：是/否，原因____

### 第 5 步 盘点
（此处原样粘贴脚本 ①②③④⑤⑥ 六组完整输出）

### 异常与偏差
（任何与本手册预期不符的现象，如实记录；没有则写「无」）
```

---

## 7. 判读（供交回后决策用，执行方不需要下结论）

| 观察 | 含义 |
|---|---|
| ③ 中 `company` 的 `UNKNOWN` 占比高（>70%） | 证实企业研发人员判不出可发信，05B 的评分调整成立 |
| ③ 中 `company` 的 `UNKNOWN` 占比低（<30%） | 有其他路径在救他们，05B 方案需重新设计 |
| ④ 中 `company` 占比远低于 ② | 企业研发人员本就难抽到邮箱，05B 的收益上限有限 |
| ③ 中 `healthcare` 出现 `ACADEMIC_RND` / `PRODUCTION_RND` | 医疗机构漏进发信池，`OUT_OF_SCOPE` 词表有缺口 |
| ⑤⑥ 中出现明显错判的机构名 | OpenAlex 的 `type` 本身不可靠，需重新评估整个方案 |

**执行方只负责如实产出数据，不做判读、不改计划、不改代码。**

---

## 8. 收尾

```bash
rm -f "$COOKIE_JAR"
unset ES_AUTH ES_PASSWORD
```

确认未产生任何源码改动：

```bash
git status --short -- src/ && echo "(以上为空即正确)"
```
