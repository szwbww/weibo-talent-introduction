# fix-1: ai-reply-01 接缝局部折叠

## 原计划 / 子计划引用

- 原计划：`docs/plans/2026-07-12/ai-reply-01-format-preservation.md`
- 复验对象：phase 1 format preservation（T1/T2/T3）

## 约束摘录

- **I-1**: 无删除 → 返回原始 `text`（含 `\n`/`\r\n`/尾换行）；禁止全局 whitespace normalization
- **I-2**: 按原文 span 删除；未命中区与分隔符原样保留；**仅将删除接缝处**超过 3 个连续空行收敛为 2 个
- **I-3**: `findViolations` / `sanitize` 共用同一 tokenizer + `detectDirectRequest`
- **I-4**: 授权语义不变
- 范围：仅 `AiReplyActionPolicy.kt` + 对应测试；不改 DraftService 生产代码

## 修正记录表

| ID | 严重度 | 触发频率 | 问题 | 期望 |
|---|---|---|---|---|
| P1-1 | P1 | 每次有删除且正文别处已有 `\n{3,}` | `collapseDeletionSeamNewlines` 对整串 `replace`，非接缝局部 | 只在删除接缝处把 `\n{3,}`→`\n\n`；非接缝预存空洞字节不变 |
| P1-2 | P1 | CRLF 草稿删除中间段 | `\n{3,}` 匹配不到 `\r\n\r\n\r\n\r\n`，接缝空洞不收敛 | 接缝折叠识别 `\r?\n` 连续空行（或等价），删后最多保留 2 个空行 |

## 修复规格

### P1-1 / P1-2 — 接缝局部折叠

**文件**: `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyActionPolicy.kt`

**改什么**:
1. 删除整串 `collapseDeletionSeamNewlines(sb.toString())`。
2. 在 splice 时，仅当两个 kept 区间在原文中被删除 unit 隔开时，对拼接点两侧相遇的换行串做空行收敛：连续空行（LF 或 CRLF）最多保留 2 个（即最多两个“空行分隔”对应 `\n\n` / `\r\n\r\n`）。
3. 未被删除打断的原文区间（含其内部预存 `\n\n\n`）必须原样拷贝。
4. 不 `trim()`、不做全局 `\s+` 压缩；句内空格不动。

**测试**（`AiReplyActionPolicyTest.kt`）:
1. 正文前部已有 `\n\n\n`（3+ 空行），后部删一句 CTA → 前部 `\n\n\n` 仍在；接缝处不超过 `\n\n`。
2. CRLF：删中间段后接缝不留下超过两个空行的 `\r\n` 空洞。

### 不做（观察）

- 同行删除后双空格：计划允许保留原分隔符；不强制 `\s+` 压缩。
- `\r` 粘在 unit 尾：若接缝折叠覆盖 CRLF 后可接受；不为改 delimiter 单独扩 scope，除非测试失败。
- I-3 显式 span 对等测试：P2，本轮不做。

## 当前状态（修前）

- Build/定向测：`mvn -Dtest=AiReplyActionPolicyTest,AiReplyDraftServiceTest test` → exit 0（修前功能测绿，但 I-2 接缝局部语义未覆盖）
- 问题点：`AiReplyActionPolicy.kt:176-177` 整串 `replace(Regex("""\n{3,}"""), "\n\n")`

## 合规审计（修前）

| Constraint | Verdict | Evidence |
|---|---|---|
| I-1 | ✅ | early `return text to false` @116-117 |
| I-2 span splice | ✅ | offset splice @119-125 |
| I-2 seam-local collapse | ❌ | whole-string @176-177 |
| I-2 CRLF blank collapse | ❌ | `\n{3,}` only |
| I-3 | ✅ | shared `tokenizeUnits` + `detectDirectRequest` |
| I-4 | ✅ | auth regex/enums untouched |
| T1/T3 tests | ✅ | present; seam locality untested |
| Scope | ✅ | 3 files only |
