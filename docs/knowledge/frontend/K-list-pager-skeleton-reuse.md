---
id: K-list-pager-skeleton-reuse
domain: frontend
created: 2026-08-16
last_used: 2026-08-16
hit_count: 0
source: create-p:p0-task-execution-list-performance
---

经验：静态后台的分页条已有**逐字一致的既有骨架**，新增分页一律复制，**零新增 CSS**。

```html
<div id="<prefix>Pager" class="list-pager" hidden>
    <button class="button small" id="<prefix>PrevPage">上一页</button>
    <span id="<prefix>PageInfo" class="list-pager-info"></span>
    <button class="button small" id="<prefix>NextPage">下一页</button>
</div>
```

既有实例 5 处：`index.html:439`（退订名单）、`:658`（专家联系）、`:822`（AI 训练 QA）、`:891`（模拟邮件）、`:928`（未支持回答）。

样式来源：`.list-pager`（`styles.css:1105-1113`）、`.list-pager-info`（`:1115-1119`，`12px` + `--text-muted` + `--font-mono`）、`.button.small`（`:2316-2321`，`height:26px`）。这三个 class 各有 5+ 处使用点，**禁止就地修改规则块**。

调用范式（`app.js:3742` 的 `loadSuppressions`）：`page` / `size` 作为 query 参数，响应 `{items, total}`，`state.<x>Page` 存页码，筛选变更时先归零再加载。

⚠️ 仓库里另有一套 `pageSize` / `pageOffset` 命名（`InboundMailSummaryController:36`、`BounceController:28`、`UnmatchedInboundMailController:93`）。**扁平表格 + `.list-pager` 的场景取 `page`/`size`**，与退订名单同构；不要混用。

关联：[[K-view-registration-triad]]
