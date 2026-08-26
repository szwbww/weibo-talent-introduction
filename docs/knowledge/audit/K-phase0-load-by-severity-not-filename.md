---
id: K-phase0-load-by-severity-not-filename
domain: audit
created: 2026-08-16
last_used: 2026-08-21
hit_count: 1
source: create-p:task-records-refactor-main
severity: P1
---

# Phase 0 载入知识不能只按文件名关键词筛

2026-08-16 复盘一组 5 份计划，发现 4 类缺陷里有 2 类源自同一个动作失误：**Phase 0 载入知识时，按文件名关键词从 `frontend` 域 46 条里挑了 8 条**，漏掉了两条本该无条件读的：

- `K-frontend-cache-key-triad`（severity P1，hit_count 4）—— 后果：五份计划里改前端的三份都会踩「构建中止」或「改动不生效」。文件名里没有 `pager`/`table`/`view` 之类的当时用的关键词，所以没被捞出来。
- `K-js-test-invocation-surface`（hit_count 3）—— 后果：验证命令漏了 pom 也跑的 `node --check`，也没写明 `verify.sh` 只跑一个文件、不可当门禁。

**规则**：Phase 0 选条目时，除了 domain/关键词匹配，还必须**无条件读完**目标域中满足以下任一条件的条目：

- `severity: P1`（或更高）
- `hit_count >= 3`

域内条目多（本仓库 `frontend` 46 条、`mail` 60+ 条）时这条尤其关键：关键词匹配会系统性漏掉「跨所有前端改动的通用约束」这一类条目 —— 它们的文件名天然不含任何具体功能词。

**自查动作**（写完计划、Phase 4 自查时跑一遍）：

```bash
# 列出目标域中 P1 或高频、但本计划正文未引用的条目
for f in docs/knowledge/<domain>/*.md; do
  id=$(grep -m1 '^id:' "$f" | sed 's/id: //')
  sev=$(grep -m1 '^severity:' "$f" | sed 's/severity: //')
  hit=$(grep -m1 '^hit_count:' "$f" | tr -dc '0-9')
  if [ "$sev" = "P1" ] || [ "${hit:-0}" -ge 3 ]; then
    grep -q "$id" docs/plans/<date>/*.md || echo "UNCITED  $sev  $hit  $id"
  fi
done
```

未引用不等于必须引用，但**每一条都要过一眼并有意识地取舍**，不能默默漏掉。

关联：[[K-plan-quantified-claims-need-grep-receipts]]（知识可以播种研究，不能替代研究；这条讲的是知识连载入都没载入）
