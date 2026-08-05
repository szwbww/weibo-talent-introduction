# 顾问项目时间投入事实：110 直接配置

## 目标

在 110 的 `qa_rule` 表新增一条可审核的“顾问项目时间投入”事实，回答顾问合作的通常参与方式；不更改现有“项目周期 2–3 年”事实。

## 事实内容

- 显示名称：`顾问项目时间投入`
- 主题：`Advisory time commitment`
- 正文：`The time commitment is generally flexible. Most participants provide remote guidance and make 1–2 short visits to China per year, with part-time arrangements possible. The exact workload is agreed with the matched enterprise.`
- 关键词：`time commitment,time requirement,how much time,weekly hours,monthly hours,hours per week,hours per month,level of involvement,how many hours`

不承诺固定每周或每月小时数。

## 数据设计

- 分类：既有 `ROLE_AND_WORKSTYLE`。
- 匹配：`ANY`；优先级 `60`。
- 策略：`REVIEW`，因此 `auto_reply_enabled=0`、`handoff_required=1`。
- `coverage_keys` 保持空。当前受控 key 目录没有 `work.time_commitment`；写入未知 key 会在管理端校验/解析中失效。该 intent 允许无 coverage 的、关键词精确命中的事实。
- 用 `reply_subject='Advisory time commitment'` 做幂等键；已存在时不插入、不覆盖。

## 执行与核验

1. 只读查询确认分类唯一性和同名事实是否已存在。
2. 在单个事务内执行带 `NOT EXISTS` 的 `INSERT ... SELECT`。
3. 提交后只读查询验证恰好一条记录、所有字段与本设计一致。
4. 若分类缺失、同名记录正文冲突或 SQL 失败，回滚并停止，不改写既有记录。

## 边界

- 仅写入一条 110 数据库配置，不改代码、迁移、已有 QA 事实或其他服务器。
- 该操作不会让“项目周期”事实重复出现；既有 `Program overview` 继续回答 2–3 年时长。
