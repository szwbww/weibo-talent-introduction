# 总览：投递率 / 反风控 计划索引

> 目标：提升有效投递、规避收件方风控。本文件是所有相关 plan 的执行顺序与依赖索引。
> 日期：2026-06-20。所有子 plan 位于 `docs/plans/`。

## 全部计划一览

| 计划 | 文件 | 代码文件数 | 依赖 | 价值 |
|---|---|---|---|---|
| 0. 域名认证 | （无 plan，DNS 运营，用户处理中） | 0 | — | 投递命门：SPF✅ / DKIM❌ / DMARC❌ / PTR(腾讯负责) |
| 1. 退订-主计划 | `2026-06-20-unsubscribe-suppression-00-master.md` | — | — | 共享上下文与不变量 |
| 2. 退订-抑制核心 | `…-unsubscribe-suppression-01-suppression-core.md` | 9 | 无 | 退订被记录 + 批量外发跳过 |
| 3. 退订-一键退订 | `…-unsubscribe-suppression-02-list-unsubscribe-oneclick.md` | 7 | #2 | `List-Unsubscribe` 头 + 一键端点（Gmail/Yahoo 强制） |
| 4. 退订-回复拦截 | `…-unsubscribe-suppression-03-reply-path-guard.md` | 2 | #2 | 自动回复对退订者不再发 |
| 5. 退订-管理前端 | `…-unsubscribe-suppression-04-admin-frontend.md` | 7 | #2 | 抑制名单后台管理 |
| 6. 多部分邮件 | `2026-06-20-deliverability-multipart-text-html-plan.md` | 5 | 无 | HTML 邮件补纯文本,提反垃圾分 |
| 7. 服务商节流 | `2026-06-20-deliverability-per-provider-throttle-plan.md` | 5 | 无 | 单服务商限流不误伤其它 |
| 8. 发件预热 | `2026-06-20-deliverability-sender-warmup-plan.md` | 7 | 无 | 新邮箱按天爬坡,避免冷启动判垃圾 |
| 9. 外部信誉监控 | `2026-06-20-deliverability-external-reputation-monitoring-plan.md` | 0（本期） | #0、(可选)#2 | Postmaster/SNDS/FBL 看板 + 投诉回流 |

## 依赖关系图

```
#0 认证(DNS) ──────────────► #9 外部信誉监控(数据有效性依赖认证)
                                   ▲ (可选任务C 投诉回流) 依赖 #2

#1 退订主计划
   └─ #2 抑制核心 ─┬─► #3 回复拦截
                   ├─► #4 管理前端
                   └─► #5 一键退订(#3 中编号 02)

#6 多部分邮件  ─ 独立
#7 服务商节流  ─ 独立
#8 发件预热    ─ 独立
```

（说明：上表「3. 退订-一键退订」即依赖图里 #2 的分支,文件名后缀 `02`;#3/#4 为后缀 `03/04`。）

## 推荐执行顺序（按收益 / 成本 / 依赖）

**第 0 步｜认证（最高优先,用户处理中,非代码）**
补齐 DKIM + DMARC（`p=none` 起步）。这是所有投递改进的前提,也是 #9 出数据的前提。

**第 1 阶段｜合规硬门槛 + 退订闭环**
1. 退订-抑制核心（#2）
2. 退订-一键退订（#3 / 文件 `02`）
3. 多部分邮件（#6）

→ 完成后即满足 Gmail/Yahoo 批量发件人的「一键退订 + 退订处理」要求,且邮件结构合规。

**第 2 阶段｜降风控(发件侧节奏)**
4. 服务商节流（#7）
5. 发件预热（#8）
6. 退订-回复拦截（#4 / 文件 `03`）

**第 3 阶段｜可视化与运营**
7. 外部信誉监控 A/B（#9,运营 + 运行手册,认证就绪后开始攒数据）
8. 退订-管理前端（#5 / 文件 `04`）
9.（可选）#9 任务 C：FBL 投诉自动抑制

## 并行性
- #6、#7、#8 相互独立,可并行排期。
- 退订系列 02/03/04 都依赖 01,需在 01 之后,彼此可顺序或并行（注意 03/04 都改各自文件,冲突小）。

## 每个计划的落地循环（项目既有流程）
对每个代码计划:`superpowers:subagent-driven-development` 执行 → `fix-v` 验证（≤3 轮）→ `superpowers:finishing-a-development-branch` 合并。

## 一句话优先级
认证 → 退订核心+一键退订+多部分 → 服务商节流+预热 → 监控+管理页。
