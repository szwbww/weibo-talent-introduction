-- ============================================================================
-- V116 repair rag_fact mojibake from the latin1 deployment channel
--
-- V112 seeded rag_fact with non-ASCII content (Chinese titles, en dash in
-- two answers) through a mysql client whose connection charset negotiated to
-- latin1 on MySQL 5.7 (character_set_server=latin1). The original UTF-8 bytes
-- were stored as latin1-decoded characters, so every Chinese title and the two
-- non-ASCII answers are double-encoded mojibake. rag_kb_meta still claims the
-- true seed fingerprint e62421a42c432cf3, so the RagKnowledgeBase startup
-- guard aborts ("corpus drifted") on any such database.
--
-- Repair: decode utf8mb4 -> latin1 (each mojibake char collapses back to the
-- original UTF-8 byte), take those bytes as BINARY, then decode as utf8mb4.
-- Pure ASCII content round-trips unchanged, so each column is guarded by a
-- byte-level check (HEX contains a C2/C3 lead byte = a U+0080-U+00FF char,
-- which never legitimately occurs in this corpus) and only mojibake rows are
-- rewritten. Operator edits after V112 cannot exist yet (rag_fact had no write
-- path before this release); if any clean CJK ever appears the guard skips it.
--
-- Keep this migration ASCII-only: no non-ASCII literals (deployment channels
-- decode non-ASCII literals with the connection charset before persistence).
-- ============================================================================

UPDATE rag_fact
   SET title = CONVERT(CAST(CONVERT(title USING latin1) AS BINARY) USING utf8mb4)
 WHERE HEX(title) REGEXP '^(..)*C[23]';

UPDATE rag_fact
   SET category = CONVERT(CAST(CONVERT(category USING latin1) AS BINARY) USING utf8mb4)
 WHERE HEX(category) REGEXP '^(..)*C[23]';

UPDATE rag_fact
   SET question_variants = CONVERT(CAST(CONVERT(question_variants USING latin1) AS BINARY) USING utf8mb4)
 WHERE HEX(question_variants) REGEXP '^(..)*C[23]';

UPDATE rag_fact
   SET keywords = CONVERT(CAST(CONVERT(keywords USING latin1) AS BINARY) USING utf8mb4)
 WHERE HEX(keywords) REGEXP '^(..)*C[23]';

UPDATE rag_fact
   SET answer = CONVERT(CAST(CONVERT(answer USING latin1) AS BINARY) USING utf8mb4)
 WHERE HEX(answer) REGEXP '^(..)*C[23]';

UPDATE rag_fact
   SET source_refs = CONVERT(CAST(CONVERT(source_refs USING latin1) AS BINARY) USING utf8mb4)
 WHERE HEX(source_refs) REGEXP '^(..)*C[23]';
