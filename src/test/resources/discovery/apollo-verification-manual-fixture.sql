-- MANUAL ONLY. Never import into a shared or production database.
-- mysql -h 127.0.0.1 -u <test-user> -p <independent-db_verification_fixture> < src/test/resources/discovery/apollo-verification-manual-fixture.sql
-- The routine refuses the wrong database or occupied non-fixture execution IDs before any data writes.
DELIMITER $$
DROP PROCEDURE IF EXISTS APV_FIXTURE_seed_reporting$$
CREATE PROCEDURE APV_FIXTURE_seed_reporting()
BEGIN
    DECLARE n INT DEFAULT 1;
    DECLARE b INT;
    DECLARE pos INT;
    DECLARE direct_n INT;
    DECLARE clear_n INT;
    DECLARE unclear_n INT;
    DECLARE ambiguous_n INT;
    DECLARE no_match_n INT;
    DECLARE error_n INT;
    DECLARE doc VARCHAR(128);
    DECLARE outcome_value VARCHAR(32);
    DECLARE state_value VARCHAR(24);
    DECLARE now_value DATETIME(3) DEFAULT '2026-10-18 10:00:00.123';
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;
    IF DATABASE() IS NULL OR RIGHT(DATABASE(),21) <> '_verification_fixture' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='APV-FIXTURE requires an independent database ending _verification_fixture';
    END IF;
    IF EXISTS (SELECT 1 FROM task_execution WHERE id IN (3001,3002,3003)
        AND (request_payload IS NULL OR request_payload NOT LIKE '%APV-FIXTURE-%')) OR
       EXISTS (SELECT 1 FROM discovery_verification_run WHERE execution_id IN (3001,3002,3003)
        AND settings_json NOT LIKE '%APV-FIXTURE-%') OR
       EXISTS (SELECT 1 FROM discovery_verification_item WHERE owner_execution_id IN (3001,3002,3003)
        AND LEFT(expert_doc_id,12) <> 'APV-FIXTURE-') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='APV-FIXTURE execution IDs occupied by non-fixture data';
    END IF;
    START TRANSACTION;
    INSERT INTO task_execution (id,task_type,trigger_type,status,request_payload,started_at,finished_at)
    VALUES (3001,'EXPERT_DISCOVERY','MANUAL','SUCCESS','{"fixture":"APV-FIXTURE-F123"}',now_value,now_value),
           (3002,'EXPERT_DISCOVERY','MANUAL','SUCCESS','{"fixture":"APV-FIXTURE-LEGACY"}',now_value,now_value),
           (3003,'EXPERT_DISCOVERY','MANUAL','SUCCESS','{"fixture":"APV-FIXTURE-EMPTY"}',now_value,now_value)
    ON DUPLICATE KEY UPDATE request_payload=VALUES(request_payload);
    INSERT INTO discovery_verification_run (execution_id,mode,policy_version,apollo_requested,settings_json,input_closed,revision,created_at,updated_at)
    VALUES (3001,'ENFORCE','APV_20261018_V1_ENFORCE',1,'{"fixture":"APV-FIXTURE-F123"}',1,123,now_value,now_value),
           (3003,'ENFORCE','APV_20261018_V1_ENFORCE',0,'{"fixture":"APV-FIXTURE-EMPTY"}',1,0,now_value,now_value)
    ON DUPLICATE KEY UPDATE revision=VALUES(revision),input_closed=VALUES(input_closed);
    -- Only rows explicitly in the fixture namespace can be removed on re-import.
    DELETE a FROM discovery_verification_attempt a JOIN discovery_verification_item i ON i.id=a.item_id
        WHERE i.owner_execution_id=3001 AND LEFT(i.expert_doc_id,12)='APV-FIXTURE-' AND LEFT(a.request_key,12)='APV-FIXTURE-';
    DELETE FROM discovery_verification_item WHERE owner_execution_id=3001 AND LEFT(expert_doc_id,12)='APV-FIXTURE-';
    WHILE n <= 123 DO
        SET b=CASE WHEN n<=40 THEN 1 WHEN n<=80 THEN 2 ELSE 3 END;
        SET pos=n-CASE b WHEN 1 THEN 0 WHEN 2 THEN 40 ELSE 80 END;
        SET direct_n=CASE b WHEN 1 THEN 6 WHEN 2 THEN 5 ELSE 4 END;
        SET clear_n=CASE b WHEN 1 THEN 8 WHEN 2 THEN 7 ELSE 6 END;
        SET unclear_n=CASE b WHEN 1 THEN 6 WHEN 2 THEN 6 ELSE 7 END;
        SET ambiguous_n=CASE b WHEN 1 THEN 8 WHEN 2 THEN 9 ELSE 9 END;
        SET no_match_n=CASE b WHEN 1 THEN 5 WHEN 2 THEN 6 ELSE 8 END;
        SET error_n=CASE b WHEN 1 THEN 2 WHEN 2 THEN 1 ELSE 2 END;
        SET doc=CONCAT('APV-FIXTURE-',LPAD(n,3,'0'));
        SET outcome_value=CASE
            WHEN pos<=direct_n THEN 'DIRECT_CLEAR'
            WHEN pos<=direct_n+clear_n THEN 'APOLLO_CLEAR'
            WHEN pos<=direct_n+clear_n+unclear_n THEN 'APOLLO_UNCLEAR'
            WHEN pos<=direct_n+clear_n+unclear_n+ambiguous_n+no_match_n THEN 'PENDING'
            ELSE NULL END;
        SET state_value=CASE WHEN outcome_value IS NOT NULL THEN 'COMPLETED'
            WHEN pos<=direct_n+clear_n+unclear_n+ambiguous_n+no_match_n+error_n THEN 'ERROR' ELSE 'QUEUED' END;
        INSERT INTO discovery_verification_item
            (owner_execution_id,expert_doc_id,identity_hash,policy_version,source_name,source_item_key,batch_number,snapshot_json,
             fact_fingerprint,state,outcome,qualification,route,apollo_entered_at,search_completed_at,candidate_found_at,person_matched_at,
             evidence_json,reasons_json,request_count,admission_revision,projection_state,activated_at,completed_at,created_at,updated_at)
        VALUES (3001,doc,REPEAT('a',64),'APV_20261018_V1_ENFORCE','OPENALEX',doc,b,
            JSON_OBJECT('identityHash',REPEAT('a',64),'factFingerprint',REPEAT('b',64),'profile',
                JSON_OBJECT('orcidId',doc,'email',CONCAT(LOWER(doc),'@example.invalid'),'givenNames','Synthetic','familyNames',doc,
                    'country','Fixture','keyword',NULL,'employment','Original institution clue','institution','APV-FIXTURE-University')),
            REPEAT('b',64),state_value,outcome_value,
            CASE WHEN outcome_value IN ('DIRECT_CLEAR','APOLLO_CLEAR') THEN 'MET' WHEN state_value='ERROR' THEN 'NOT_EVALUATED' ELSE 'UNKNOWN' END,
            CASE WHEN outcome_value='DIRECT_CLEAR' THEN 'DIRECT' WHEN outcome_value IN ('APOLLO_CLEAR','APOLLO_UNCLEAR') THEN 'APOLLO' ELSE 'NONE' END,
            IF(pos>direct_n,now_value,NULL),
            IF(outcome_value IS NOT NULL AND pos>direct_n,now_value,NULL),
            IF(pos>direct_n AND pos<=direct_n+clear_n+unclear_n+ambiguous_n,now_value,NULL),
            IF(outcome_value IN ('APOLLO_CLEAR','APOLLO_UNCLEAR'),now_value,NULL),
            JSON_OBJECT('schemaVersion',1,'evidence',JSON_ARRAY(JSON_OBJECT('schemaVersion',1,'identityHash',REPEAT('a',64),
                'factFingerprint',REPEAT('b',64),'provider','ORCID','sourceUrl','https://example.invalid/fixture/evidence',
                'fetchedAt','2026-10-18T10:00:00.123Z','sourceUpdatedAt','2026-10-17T00:00:00Z','responseSha256',REPEAT('c',64),
                'field','name','value',CONCAT('Synthetic ',doc),'assertionSource','ORGANIZATION','temporalScope','CURRENT','conflicts',JSON_ARRAY()))),
            CASE WHEN outcome_value='DIRECT_CLEAR' THEN JSON_ARRAY('QUALIFICATION_MET')
                WHEN outcome_value='APOLLO_CLEAR' THEN JSON_ARRAY('APOLLO_PERSON_MATCHED','QUALIFICATION_MET')
                WHEN outcome_value='APOLLO_UNCLEAR' THEN JSON_ARRAY('APOLLO_PERSON_MATCHED','QUALIFICATION_EVIDENCE_MISSING','APV-FIXTURE-REASON-3','APV-FIXTURE-REASON-4')
                WHEN outcome_value='PENDING' AND pos<=direct_n+clear_n+unclear_n+ambiguous_n THEN JSON_ARRAY('IDENTITY_AMBIGUOUS')
                WHEN outcome_value='PENDING' THEN JSON_ARRAY('APOLLO_NO_MATCH')
                WHEN state_value='ERROR' THEN JSON_ARRAY('CONFIG_AUTH') ELSE JSON_ARRAY() END,
            IF(pos>direct_n,1,0),IF(outcome_value IN ('DIRECT_CLEAR','APOLLO_CLEAR','APOLLO_UNCLEAR'),1,NULL),
            IF(outcome_value IN ('DIRECT_CLEAR','APOLLO_CLEAR','APOLLO_UNCLEAR'),'PROJECTED','NOT_REQUIRED'),now_value,
            IF(state_value IN ('COMPLETED','ERROR'),now_value,NULL),now_value,now_value);
        IF pos>direct_n THEN
            INSERT INTO discovery_verification_attempt
                (item_id,request_key,provider,query_kind,page_number,attempt_number,requested_at,completed_at,http_status,result_code,request_json,response_json)
            VALUES (LAST_INSERT_ID(),CONCAT(doc,'-attempt'),'APOLLO','BROAD',1,1,now_value,
                IF(state_value='QUEUED',NULL,now_value),IF(state_value='ERROR',403,IF(state_value='QUEUED',NULL,200)),
                IF(state_value='ERROR','CONFIG_AUTH',IF(state_value='QUEUED','DISPATCH_RESERVED','OK')),
                '{"q_person_name":"Synthetic Fixture"}','{"fixture":"APV-FIXTURE-filtered-response-only"}');
        END IF;
        IF outcome_value IN ('DIRECT_CLEAR','APOLLO_CLEAR','APOLLO_UNCLEAR') THEN
            INSERT INTO expert_discovery_admission (expert_doc_id,identity_hash,decision,revision,policy_version,checked_at,updated_at)
            VALUES (doc,REPEAT('a',64),IF(n=1,'HOLD','AUTO_PASSED'),IF(n=1,2,1),'APV_20261018_V1_ENFORCE',now_value,now_value)
            ON DUPLICATE KEY UPDATE decision=VALUES(decision),revision=VALUES(revision);
        END IF;
        SET n=n+1;
    END WHILE;
    COMMIT;
END$$
CALL APV_FIXTURE_seed_reporting()$$
DROP PROCEDURE APV_FIXTURE_seed_reporting$$
DELIMITER ;
