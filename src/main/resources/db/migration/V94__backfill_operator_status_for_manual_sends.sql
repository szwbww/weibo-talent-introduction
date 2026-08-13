-- 条件与 ManualInitialOutreachService.hasSentIntroduction():895 逐字一致
-- (direction + mail_type + send_status 三条件缺一不可)
-- 幂等：以 operator_status='NOT_CONTACTED' 为前置，重复执行为 no-op
-- 单调：不触碰任何已推进到 CONTACTED 及之后的行，符合 I-1
-- 无 ${} 占位符，不触发 K-flyway-placeholder-replacement

UPDATE expert_contact ec
   SET ec.operator_status = 'CONTACTED'
 WHERE ec.operator_status = 'NOT_CONTACTED'
   AND EXISTS (
       SELECT 1 FROM mail_record mr
        WHERE mr.expert_contact_id = ec.id
          AND mr.direction   = 'OUTBOUND'
          AND mr.mail_type   = 'INTRODUCTION'
          AND mr.send_status = 'SENT'
   );
