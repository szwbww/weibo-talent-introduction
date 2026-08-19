---
id: K-intent-keyword-two-sided-normalization
domain: qa
created: 2026-08-19
last_used: 2026-08-19
hit_count: 0
source: create-p:01-fact-and-catalog
severity: P1
---

经验：一条 QA 规则要成为某 intent 的证据，它的**同一个** keyword 必须同时满足两个条件，而两侧的规范化函数**不对称**：

- (a) 是来信文本经 `QaFactKeywordMatcher.normalize`（`QaFactSelectionService.kt:381-386`）后的子串 —— 否则不进 `candidateRules`。该函数只做 lowercase + 空白折叠 + `details`→`information`，**不做 programme→program**。
- (b) 是该 intent 的 `title` 或某条 `requestAliases` 经 `AiReplyIntentCatalog.canonicalize`（`AiReplyIntentCatalog.kt:305-312`）后的子串 —— 否则 `scoreRuleIntentAlignment`（:431-447）得 0 分，`selectIntentKeyForRule`（:483-500）不分配。该函数**会做 `\bprogramme\b` → `program`**。

后果：任何含 `programme` 的 rule keyword 永远无法满足 (b)（alias 侧已被改写成 `program`）；而改写成 `program xxx` 又常常无法满足 (a)（英式来信原文写的是 `programme`）。

正确做法：新增/追加 keyword 时逐条验证两个条件同时成立，并写成机械断言（对逐字来信 fixture，断言存在 keyword `k` 使 `normalize(mail).contains(k)` 且 `(title+aliases).any { canonicalize(it).contains(k) }`）。intent 识别成功 ≠ 有证据：只满足 (a) 会让事实明明存在却判 MISSING，只满足 (b) 则规则根本进不了候选池。

关联：[[K-company-identity-keyword-intent-parity]]（parity 的现象层描述，本条给出可判定的机械形式）、[[K-ai-reply-intent-alias-fixture-fidelity]]。
