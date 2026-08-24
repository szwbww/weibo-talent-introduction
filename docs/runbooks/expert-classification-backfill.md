# 专家研发类型分类回填线上执行手册

> 对应计划：`docs/plans/2026-08-24/02-expert-rnd-classification-backfill.md`（子计划 02）
> 政策版本：`rnd-v1-2026`（分类只由 `ExpertClassificationService` 计算，M-2）
> 任务类型：`EXPERT_CLASSIFICATION_BACKFILL`
> 前置：子计划 01（分类领域对象 + ES mapping）已发布。

本手册仅供**已登录管理员**通过 API 手动执行。**发布应用不会自动启动任何回填**；
只有本手册第 4/6/7/9 步的显式请求会启动任务（M-6）。本手册不包含自动增量调度内容。

> 危险操作停止条件统一写在每一步；任何一步出现停止条件，**立即停止**并联系开发确认，
> 不要跳过检查继续下一步。

---

## 0. 变量与通用命令

执行前先把以下变量按实际环境填好（**不要提交真实密码**，`<ES_PASSWORD>` 只在本会话导出）：

```bash
export BASE_URL='https://<APP_HOST>/api'          # 应用 API 基地址
export ES_BASE_URL='https://<ES_HOST>:9200'       # ES 基地址（生产只读账号即可）
export ES_USER='<ES_USERNAME>'
export ES_PASSWORD='<ES_PASSWORD>'
export RAW_INDEX='orcid_info'                     # 以实际索引名为准
export CANDIDATE_INDEX='orcid_info_candidate'
export APPLICATION_INDEX='orcid_info_application'
export COOKIE_JAR='/tmp/ecb_cookies.txt'          # 仅存 /tmp，权限 600，结束后删除
```

> 变量命名禁止使用 `HOME` / `CODEX_HOME` 等可能被工具链覆盖的名字；统一用上面的 `COOKIE_JAR`。

登录（第 3 步完成后 `COOKIE_JAR` 有效）；`ES_USER`/`ES_PASSWORD` 仅用于只读查询。

---

## 1. 发布前：备份与发信暂停确认

**目标：回填期间没有任何 INTRODUCTION 邮件发出。**

1. 备份/快照确认：在 ES 侧对三个索引各打一次快照（或确认已启用自动快照且最近一次成功），
   记录快照名与时间。命令示例（由 ES 管理员执行，具体以运维平台为准）：

   ```bash
   curl -sS -u "$ES_USER:$ES_PASSWORD" -X PUT "$ES_BASE_URL/_snapshot/<BACKUP_REPO>/rnd-classification-$(date +%Y%m%d%H%M%S)?wait_for_completion=true"
   ```

2. 记录三层精确 doc count（作为回滚/验收基线）：

   ```bash
   for idx in "$RAW_INDEX" "$CANDIDATE_INDEX" "$APPLICATION_INDEX"; do
     curl -sS -u "$ES_USER:$ES_PASSWORD" -H 'Content-Type: application/json' \
       -d '{"query":{"match_all":{}}}' "$ES_BASE_URL/$idx/_count" \
       | python3 -c 'import json,sys; print("'"$idx"'", json.load(sys.stdin)["count"])'
   done
   ```

3. 列出所有启用的 INTRODUCTION 批量配置，逐个确认或暂停：

   ```bash
   curl -sS -b "$COOKIE_JAR" "$BASE_URL/mail/batch-send/configs" | python3 -m json.tool
   # 对每个 enabled=true 的 INTRODUCTION 配置：
   curl -sS -b "$COOKIE_JAR" -X PATCH -H 'Content-Type: application/json' \
     -d '{"enabled":false}' "$BASE_URL/mail/batch-send/configs/<CONFIG_ID>/enabled"
   ```

4. 确认/暂停正在运行的任务（看 `/api/task-progress/MANUAL_INITIAL_OUTREACH` 与
   `/api/task-progress/INITIAL_OUTREACH`；如有 RUNNING 先取消或等其结束）。

5. 检查旧定时/队列首发开关，两者都必须保持不触发：

   ```bash
   # 应用环境变量：MAIL_SCHEDULING_ENABLED 必须为 false 或 '-'，
   # MAIL_SCHEDULING_INITIAL_OUTREACH_CRON 必须为 '-'（不调度）
   ssh <APP_HOST> 'echo "ENABLED=$MAIL_SCHEDULING_ENABLED CRON=$MAIL_SCHEDULING_INITIAL_OUTREACH_CRON"'
   ```

**停止条件**：任一步骤无法确认（快照失败、有配置仍 enabled、环境变量未按预期），**不继续回填**。

---

## 2. 发布后：三层 mapping 检查

对三层分别查询 `expertClassification.type/sendable/version` 的 mapping：

```bash
for idx in "$RAW_INDEX" "$CANDIDATE_INDEX" "$APPLICATION_INDEX"; do
  echo "== $idx =="
  curl -sS -u "$ES_USER:$ES_PASSWORD" "$ES_BASE_URL/$idx/_mapping/field/expertClassification.type" \
    | python3 -m json.tool
  curl -sS -u "$ES_USER:$ES_PASSWORD" "$ES_BASE_URL/$idx/_mapping/field/expertClassification.sendable" \
    | python3 -m json.tool
  curl -sS -u "$ES_USER:$ES_PASSWORD" "$ES_BASE_URL/$idx/_mapping/field/expertClassification.version" \
    | python3 -m json.tool
done
```

预期：三层 `type` 与 `version` 均为 `keyword`，`sendable` 为 `boolean`。

**停止条件**：任一索引任一字段缺失或类型不符 → **立即停止**，联系开发修复 mapping 后重来，
严禁在 mapping 缺失时启动 EXECUTE（会整批 400）。

---

## 3. 登录（cookie jar 仅存 /tmp、权限 600）

```bash
rm -f "$COOKIE_JAR"; umask 077
curl -sS -c "$COOKIE_JAR" -H 'Content-Type: application/json' \
  -d '{"username":"<ADMIN_USERNAME>","password":"<ADMIN_PASSWORD>"}' \
  "$BASE_URL/auth/login" | python3 -m json.tool
chmod 600 "$COOKIE_JAR"
# 校验已登录
curl -sS -b "$COOKIE_JAR" "$BASE_URL/auth/me" | python3 -m json.tool
```

- `<ADMIN_USERNAME>` / `<ADMIN_PASSWORD>`：以内部凭证库实际账号替换；本手册不写真实密码。
- 结束后（本手册全部完成或中途终止）**必须删除** cookie jar：

  ```bash
  rm -f "$COOKIE_JAR"
  ```

**停止条件**：`/api/auth/me` 未返回当前用户 → 不继续（后续全部请求会 401）。

---

## 4. CANDIDATE 全量 DRY_RUN（零写入预览）

> DRY_RUN **只扫描、分类、聚合，不写 ES**（I2-1）。

```bash
curl -sS -b "$COOKIE_JAR" -H 'Content-Type: application/json' \
  -d '{
        "level":"CANDIDATE",
        "mode":"DRY_RUN",
        "version":"rnd-v1-2026",
        "batchSize":500,
        "delayMs":250,
        "onlyPending":false
      }' \
  "$BASE_URL/expert-classification/backfill" | python3 -m json.tool
# 预期：HTTP 202 {"message":"任务已启动","taskType":"EXPERT_CLASSIFICATION_BACKFILL"}
```

轮询进度与日志：

```bash
curl -sS -b "$COOKIE_JAR" "$BASE_URL/task-progress/EXPERT_CLASSIFICATION_BACKFILL" | python3 -m json.tool
curl -sS -b "$COOKIE_JAR" "$BASE_URL/task-progress/EXPERT_CLASSIFICATION_BACKFILL/logs?batchOnly=true" | python3 -m json.tool
# 结束后看执行明细（终态 + resultSummary 统计）
curl -sS -b "$COOKIE_JAR" "$BASE_URL/task-progress/EXPERT_CLASSIFICATION_BACKFILL/executions" | python3 -m json.tool
```

记录（验收 A2-1 依据）：`scanned`、六类计数（`classifiedByType`）、`sendable`、`notSendable`、
`reasonCounts` 的 top reasons、`writeSuccess/writeNoop/writeFailure` 必须全为 0。

**停止条件**：`writeSuccess/writeNoop/writeFailure` 任一非 0 → 立即停止并上报（DRY_RUN 绝不写库）。

---

## 5. 抽样（人工确认后才继续）

用**只读 ES 查询**在 CANDIDATE 已分类文档中每类随机抽至少 100 人核对：

```bash
for t in PRODUCTION_RND ACADEMIC_RND HYBRID_RND SERVICE_ONLY OUT_OF_SCOPE UNKNOWN; do
  curl -sS -u "$ES_USER:$ES_PASSWORD" -H 'Content-Type: application/json' \
    -d "{\"size\":100,\"_source\":[\"orcidId\",\"employment\",\"researchFields\",\"institution\",\"recentWorkTitles\",\"patentTitles\",\"expertClassification\"],\"query\":{\"term\":{\"expertClassification.type\":\"$t\"}},\"sort\":[{\"_doc\":\"asc\"}]}" \
    "$ES_BASE_URL/$CANDIDATE_INDEX/_search" \
    | python3 -c 'import json,sys; d=json.load(sys.stdin); print("'"$t"'", d["hits"]["total"]["value"], "hits", len(d["hits"]["hits"]))'
done
```

人工核对标准：

- **医生漏网 = 0/100**：`SERVICE_ONLY`/`OUT_OF_SCOPE` 外的样本中不得出现明确临床职业（医生/外科/牙医等）。
- 科研/生产**误杀率**由业务用户人工确认（`sendable=false` 的样本是否真的不可发信）。

**停止条件**：医生漏网 > 0，或误杀率未获用户确认 → **不得进入 EXECUTE**。

---

## 6. CANDIDATE 正式回填（EXECUTE）

确认串必须**精确**等于 `EXECUTE_CANDIDATE:rnd-v1-2026`（I2-3），只回填仍缺分类/版本不符的文档
（`onlyPending=true`，I2-4）：

```bash
curl -sS -b "$COOKIE_JAR" -H 'Content-Type: application/json' \
  -d '{
        "level":"CANDIDATE",
        "mode":"EXECUTE",
        "version":"rnd-v1-2026",
        "batchSize":500,
        "delayMs":250,
        "onlyPending":true,
        "confirmation":"EXECUTE_CANDIDATE:rnd-v1-2026"
      }' \
  "$BASE_URL/expert-classification/backfill" | python3 -m json.tool
```

执行期间持续监控（并行开一个终端）：

```bash
# 任务进度/日志
watch -n 5 'curl -sS -b "'"$COOKIE_JAR"'" "'"$BASE_URL"'/task-progress/EXPERT_CLASSIFICATION_BACKFILL" | python3 -m json.tool'
# ES cluster health（red 即停止）
watch -n 10 'curl -sS -u "'"$ES_USER"'":"'"$ES_PASSWORD"'" "'"$ES_BASE_URL"'/_cluster/health" | python3 -m json.tool'
# 应用 CPU / JVM（SSH 到应用节点）
ssh <APP_HOST> 'top -b -n1 | head -20; jstat -gcutil <PID> 1000 3'
```

**停止条件（任一即取消）**：
- `writeFailure > 0` → 取消任务（`POST /api/task-progress/EXPERT_CLASSIFICATION_BACKFILL/cancel`），
  记录失败样本（`/logs` 与 executions 的 `resultSummary`），修复后**用 `onlyPending=true` 重跑**；
- cluster health = `red`、应用 CPU/JVM 异常 → 取消并联系运维；
- 终态为 `PARTIAL_SUCCESS`/`FAILED` → 未完成，**不得视为成功**。

取消命令：

```bash
curl -sS -b "$COOKIE_JAR" -X POST "$BASE_URL/task-progress/EXPERT_CLASSIFICATION_BACKFILL/cancel"
```

---

## 7. APPLICATION 层

数量虽小，仍走与 CANDIDATE 完全相同的步骤：先 DRY_RUN（`level:"APPLICATION"`，`onlyPending:false`），
核对比例后 EXECUTE（`confirmation:"EXECUTE_APPLICATION:rnd-v1-2026"`，`onlyPending:true`）。
停止条件同第 4/6 步。

---

## 8. 恢复 INTRODUCTION 前（发信门禁检查）

> 子计划 03 的发送门禁发布后，`sendable != true` 的专家不会进入任何 INTRODUCTION 目标。
> 在恢复任何批量配置前必须确认回填结果。

```bash
# CANDIDATE 中 sendable=true / false / 缺失分类 的精确数量
for cond in \
  '{"term":{"expertClassification.sendable":true}}' \
  '{"term":{"expertClassification.sendable":false}}' \
  '{"bool":{"must_not":[{"exists":{"field":"expertClassification.version"}}]}}'; do
  curl -sS -u "$ES_USER:$ES_PASSWORD" -H 'Content-Type: application/json' \
    -d "{\"query\":$cond}" "$ES_BASE_URL/$CANDIDATE_INDEX/_count" \
    | python3 -c 'import json,sys; print(json.load(sys.stdin)["count"])'
done
```

然后执行子计划 03 的 A3 验收场景（重试路径不绕过门禁），全部通过后才恢复：
- 把第 1 步暂停的 INTRODUCTION 批量配置逐个重新 `PATCH /configs/{id}/enabled` 为 `true`；
- 恢复旧调度：确认业务需要后把 `MAIL_SCHEDULING_ENABLED` 与 `MAIL_SCHEDULING_INITIAL_OUTREACH_CRON` 调回原值。

**停止条件**：`sendable=true` 精确数与你预期不符、或 A3 场景未通过 → 保持发信暂停，联系开发。

---

## 9. RAW 层（独立维护窗口，最后执行）

RAW（428 万量级）只在整个候选/应用链路稳定后，在**独立维护窗口**执行，且**不可与
discovery / enrichment 并跑**（会互相放大 ES 压力）。

分三步，默认 `delayMs>=250`：

```bash
# ① 小样本 DRY_RUN：maxDocs=10000
curl -sS -b "$COOKIE_JAR" -H 'Content-Type: application/json' \
  -d '{"level":"RAW","mode":"DRY_RUN","version":"rnd-v1-2026","batchSize":500,"delayMs":250,"maxDocs":10000,"onlyPending":true}' \
  "$BASE_URL/expert-classification/backfill" | python3 -m json.tool
# 轮询结束后核对统计（同第 4 步）
# ② 全量 DRY_RUN（onlyPending=true 即可，无需 force）
curl -sS -b "$COOKIE_JAR" -H 'Content-Type: application/json' \
  -d '{"level":"RAW","mode":"DRY_RUN","version":"rnd-v1-2026","batchSize":500,"delayMs":250,"onlyPending":true}' \
  "$BASE_URL/expert-classification/backfill" | python3 -m json.tool
# ③ EXECUTE（确认串精确：EXECUTE_RAW:rnd-v1-2026）
curl -sS -b "$COOKIE_JAR" -H 'Content-Type: application/json' \
  -d '{"level":"RAW","mode":"EXECUTE","version":"rnd-v1-2026","batchSize":500,"delayMs":250,"onlyPending":true,"confirmation":"EXECUTE_RAW:rnd-v1-2026"}' \
  "$BASE_URL/expert-classification/backfill" | python3 -m json.tool
```

停止条件：同第 6 步（failure>0 或 health red 即取消），且第 ② 步六类比例与抽样（第 5 步逻辑对 RAW 复做）
未获人工确认时**不得**进入第 ③ 步。

---

## 10. 回滚

- **立即停止**：取消运行中的任务（第 6 步取消命令）；暂停/保持暂停全部 INTRODUCTION 配置与调度。
- **不删除分类字段**：正常情况下不需要也不允许删除 `expertClassification`；字段缺失只会让
  发信门禁安全失败（目标为 0），不会误发。
- **确需数据回滚**：只允许按执行前的快照（第 1 步）恢复整索引；
  **禁止**手写 `_update_by_query` 删除分类对象（M-4/I2-2）。
- 回滚后重跑流程：重新走第 4→6 步。

---

## 11. 完成记录

每层完成后在运维记录（工单/文档）中登记：

| 项 | 值 |
|---|---|
| task execution id | `/executions` 返回的 `executionId` |
| policy version | `rnd-v1-2026` |
| 各层 doc count（回填前后） | RAW / CANDIDATE / APPLICATION |
| 各层 `sendable=true` 计数 | 回填完成后 |
| 失败/重跑次数 | 每次 `PARTIAL_SUCCESS`/`FAILED` 及重跑记录 |
| 人工抽样人与时间 | 第 5 步的执行人、日期、抽样类型与结论 |

并在删除 cookie jar（第 3 步）后结束本次回填。

---

## 12. 增量调度（自动任务）启用

> 自动增量任务由子计划 04 提供，**默认关闭**：`EXPERT_CLASSIFICATION_INCREMENTAL_ENABLED` 未设置或
> 为 `false` 时应用不创建调度 bean，发布/启动零副作用（I4-1）。**只有**第 4/5/8 步（CANDIDATE 全量
> 回填、抽样、发送门禁验收）全部通过后，才在**下一次人工发布**时开启；不要与回填同一次发布启用。

1. 开启前：查询任务历史并记录基线（应只有手动 `triggerType=MANUAL` 的 execution）：

   ```bash
   curl -sS -b "$COOKIE_JAR" "$BASE_URL/task-progress/EXPERT_CLASSIFICATION_BACKFILL/executions" | python3 -m json.tool
   ```

2. 在应用环境变量中配置并发布（`EXPERT_CLASSIFICATION_INCREMENTAL_ENABLED` 必设，其余可选、取默认值）：

   ```bash
   EXPERT_CLASSIFICATION_INCREMENTAL_ENABLED=true     # 必设：true 才创建调度 bean
   # EXPERT_CLASSIFICATION_INCREMENTAL_CRON=0 0 4 * * ?   # 默认每天 04:00
   # EXPERT_CLASSIFICATION_BATCH_SIZE=500                 # 100..1000
   # EXPERT_CLASSIFICATION_DELAY_MS=250                   # 0..5000
   # EXPERT_CLASSIFICATION_MAX_DOCS_PER_RUN=50000         # 1..200000
   ```

3. 自动任务固定行为（I4-2/I4-4）：只处理 **CANDIDATE** 中分类版本缺失/不符（`onlyPending=true`）
   的文档，**不处理 RAW/APPLICATION、不强制重算**；每轮最多处理 `MAX_DOCS_PER_RUN` 条，
   达到上限以 SUCCESS + remaining 结束，次日继续，不视为失败。

4. 启用后：等待一次自动任务完成后再次查询任务历史，核对新增一条
   `triggerType=SCHEDULED`、level=CANDIDATE 的 execution，并抽查 ES 中 CANDIDATE 的
   `expertClassification.version` 写入情况。

5. 明确边界（I4-5）：自动任务**不会**因 `updatedAt`/`enrichedAt` 变化重算同版本已分类文档；
   新增正向证据可能延后到人工 force 或新 policy version，但现有 false/null 永不被自动放行；
   分类结果仍为 **UNKNOWN 的候选保持不可发送**（子计划 03 门禁不变）。

**停止条件**：第 4/5/8 步验收未通过 → 不设置 `EXPERT_CLASSIFICATION_INCREMENTAL_ENABLED=true`；
任务历史出现非 CANDIDATE level 或非 SCHEDULED trigger 的自动 execution → 立即联系开发。

---

## 变更记录

| 日期 | 变更 |
|---|---|
| 2026-08-24 | 初版：子计划 02 交付（11 节；自动增量调度章节由子计划 04 追加） |
| 2026-08-24 | 追加第 12 节：增量调度（自动任务）启用（子计划 04） |
