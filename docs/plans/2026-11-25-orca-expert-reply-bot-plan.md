# Orca Expert Reply Bot Implementation Plan

> **For Antigravity:** REQUIRED WORKFLOW: Use `.agent/workflows/execute-plan.md` to execute this plan in single-flow mode.

**Goal:** 在项目内落地可复用的青飞专家邮件回复 Skill、30 条已确认邮件 QA 知识库、后台邮箱只读/草稿安全写入脚本，并提供与 Orca 左上角 `Bots` 联动的桥接配置。

**Architecture:** 以 `.agents/skills/qingfei-expert-reply/` 作为唯一规则与知识源，`SKILL.md` 定义先中文提炼来信、中文优先润色、QA/Skill 沉淀建议、人工确认写草稿的四步协议；`scripts/mailbox_bot.py` 通过现有 Spring Boot Session API 安全读写线程与草稿；Orca `Bots` 通过轻量 `SOUL.md` 指向项目内绝对路径实现同源共享。

**Tech Stack:** Markdown (Skill / SOP / QA), Python 3 标准库 (`urllib`, `http.cookiejar`, `json`, `argparse`, `unittest`).

---

### Task 1: 创建项目内 Skill、完整邮件 QA 库与示例库

**Files:**
- Create: `.agents/skills/qingfei-expert-reply/SKILL.md`
- Create: `.agents/skills/qingfei-expert-reply/references/confirmed-email-qa.md`
- Create: `.agents/skills/qingfei-expert-reply/references/examples-and-pitfalls.md`
- Create: `.agents/skills/qingfei-expert-reply/references/orca-bot-soul-template.md`
- Modify: `docs/qa提炼-完整版.md:9-31`

**Steps:**
1. 写入 `confirmed-email-qa.md`：完整收录 30 条近期业务邮件 QA（第 30 条采用最新官方政策文件统一口径）、公司英文全称与注册地、边界区分红线与特例处理规则。
2. 写入 `examples-and-pitfalls.md`：收录阶段标准英文范例（首回配 PDF、答疑、跟进、代理沟通）与 Fabiola / Amir 纠偏对照案例。
3. 写入 `SKILL.md`：固化四步工作流（拉取线程并先输出最新未回复邮件中文提炼 → 默认按 QA 拟稿 / 用户给中文则优先润色并输出 QA & Skill 沉淀建议 → 经用户确认后写回系统收发件箱草稿 → 绝不自动发信）及格式铁律。
4. 写入 `orca-bot-soul-template.md` 并在 `docs/qa提炼-完整版.md` 顶部补充指向 `confirmed-email-qa.md` 的单源说明。

---

### Task 2: TDD 实现后台线程拉取与收发件箱草稿安全写入脚本

**Files:**
- Create: `.agents/skills/qingfei-expert-reply/scripts/test_mailbox_bot.py`
- Create: `.agents/skills/qingfei-expert-reply/scripts/mailbox_bot.py`
- Create: `.agents/skills/qingfei-expert-reply/scripts/.env.mailbot.example`
- Modify: `.gitignore`

**Steps:**
1. 先编写 `test_mailbox_bot.py`：覆盖纯文本转段落 HTML（保留裸 URL 文本）、`INBOUND`/`OUTBOUND` 草稿目标推导、新建/更新 ACTIVE/重开 SENT 草稿时的 `expectedVersion` + `reopen` 计算、已有附件与会议 `context` 合并保留、409 冲突报错。
2. 运行 `python3 -m unittest .agents/skills/qingfei-expert-reply/scripts/test_mailbox_bot.py` 确认初始失败（RED）。
3. 实现 `mailbox_bot.py`（支持 `fetch-thread` 与 `save-draft` 两个 CLI 子命令）及 `.env.mailbot.example`，并在 `.gitignore` 忽略 `.env.mailbot`。
4. 重新运行 `unittest` 确认全部通过（GREEN），并校验 CLI `--help` 输出。
