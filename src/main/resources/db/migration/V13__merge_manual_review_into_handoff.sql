-- Merge MANUAL_REVIEW conversation status into MANUAL_HANDOFF.
-- After this migration, MANUAL_REVIEW is no longer a valid contact status.
-- The MANUAL_REVIEW value used by inbound_mail_processing.process_status is a
-- different concept (mail processing pipeline status) and is intentionally left
-- untouched.

UPDATE expert_contact
SET current_status = 'MANUAL_HANDOFF'
WHERE current_status = 'MANUAL_REVIEW';

UPDATE expert_contact_status_history
SET to_status = 'MANUAL_HANDOFF'
WHERE to_status = 'MANUAL_REVIEW';

UPDATE expert_contact_status_history
SET from_status = 'MANUAL_HANDOFF'
WHERE from_status = 'MANUAL_REVIEW';

-- Every contact currently sitting in MANUAL_HANDOFF should have at least one
-- open manual_handoff ticket so the "完成人工" workflow can close it. Back-fill
-- a synthetic PENDING ticket for contacts that have no open ticket (typically
-- contacts that were auto-routed into MANUAL_REVIEW before the merge and
-- therefore never had a ticket created).
INSERT INTO manual_handoff
    (expert_contact_id, reason, handoff_status, assigned_to, note, created_at, updated_at)
SELECT
    ec.id,
    'MIGRATED_FROM_MANUAL_REVIEW',
    'PENDING',
    NULL,
    'Backfilled by V13 migration when MANUAL_REVIEW was merged into MANUAL_HANDOFF.',
    NOW(),
    NOW()
FROM expert_contact ec
WHERE ec.current_status = 'MANUAL_HANDOFF'
  AND NOT EXISTS (
      SELECT 1
      FROM manual_handoff mh
      WHERE mh.expert_contact_id = ec.id
        AND mh.handoff_status IN ('PENDING', 'ASSIGNED')
  );
