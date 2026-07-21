---
id: K-due-diligence-intent-fact-parity
domain: qa
created: 2026-07-21
last_used: 2026-07-21
hit_count: 0
source: create-p:ai-reply-09-fallback-reference-intent-parity
severity: P1
---
经验：新增 intent alias 只代表系统识别了问题，不代表已有答案；把同一宽泛事实扩成所有相邻关键词会制造假完整。
正确做法：把“是否有报酬/报酬结构”“时间投入/项目周期”等拆成原子 intent；数据库只给正文确实支持的问法追加关键词。一个子 intent 有据、另一个缺据时整项必须 PARTIAL，无依据 alias 继续 MISSING。
反例：用“存在企业薪酬”同时回答具体 remuneration structure，或用“项目 2–3 年”同时回答每周 time commitment。
