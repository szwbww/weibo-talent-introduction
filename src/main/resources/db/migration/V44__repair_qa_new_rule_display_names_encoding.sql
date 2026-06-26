-- Repair QA display names introduced after V18 through a mis-decoded client connection.
-- Keep literals ASCII-only UTF-8 hex to avoid repeating the encoding failure.
UPDATE qa_rule SET display_name = CONVERT(UNHEX('E689BFE8AFBAE8A786E9A29120564352') USING utf8mb4)
 WHERE reply_subject = 'Confirmation video requirement';
UPDATE qa_rule SET display_name = CONVERT(UNHEX('E58D95E4B880E794B3E68AA5E689BFE8AFBA') USING utf8mb4)
 WHERE reply_subject = 'Single application commitment';
UPDATE qa_rule SET display_name = CONVERT(UNHEX('E585A5E98089E5908EE6B581E7A88B') USING utf8mb4)
 WHERE reply_subject = 'After selection process';
UPDATE qa_rule SET display_name = CONVERT(UNHEX('E68890E58A9FE78E872FE69CAAE585A5E98089') USING utf8mb4)
 WHERE reply_subject = 'Success rate and reapplication';
UPDATE qa_rule SET display_name = CONVERT(UNHEX('E8B584E69699E4BF9DE5AF86C2B7E7BB9DE4B88DE694B6E8B4B9') USING utf8mb4)
 WHERE reply_subject = 'Document confidentiality and no fees';
UPDATE qa_rule SET display_name = CONVERT(UNHEX('E4BBA3E79086E8B584E8B4A8C2B7E694BFE5BA9CE59088E4BD9CE8AF81E6988E') USING utf8mb4)
 WHERE reply_subject = 'Agency credentials and government cooperation';
UPDATE qa_rule SET display_name = CONVERT(UNHEX('E5A49AE4BBA3E79086C2B7E69D83E79B8AE4BF9DE99A9C') USING utf8mb4)
 WHERE reply_subject = 'Multi-agency rights protection';
UPDATE qa_rule SET display_name = CONVERT(UNHEX('E9A1B9E79BAEE6958FE6849FE680A7') USING utf8mb4)
 WHERE reply_subject = 'Project sensitivity concerns';
UPDATE qa_rule SET display_name = CONVERT(UNHEX('E4BC9AE8AEAEE5AE89E68E92') USING utf8mb4)
 WHERE reply_subject = 'Meeting arrangement';
UPDATE qa_rule SET display_name = CONVERT(UNHEX('E58FAAE982AEE4BBB6C2B7E4B88DE794A84C696E6B6564496E') USING utf8mb4)
 WHERE reply_subject = 'Email-only communication preference';
UPDATE qa_rule SET display_name = CONVERT(UNHEX('E59088E4BD9CE4BC81E4B89AE4BFA1E681AF') USING utf8mb4)
 WHERE reply_subject = 'Partner company information';
UPDATE qa_rule SET display_name = CONVERT(UNHEX('E9A1B9E79BAEE680BBE8A788') USING utf8mb4)
 WHERE reply_subject = 'Program overview';
