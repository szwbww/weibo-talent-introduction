-- V107: Program overview (id=24) is an overview fact, not the authority for the
-- fee / material-confidentiality commitments. V76 backfilled both controlled keys
-- onto it; V82 later made controlled keys exclusive, which left id=24 unsavable
-- and un-enableable. Strip the two controlled keys, keep everything else.
-- answer_body / reply_body are deliberately untouched (I-6).
UPDATE qa_rule
   SET coverage_keys = 'programme.purpose,programme.structure,programme.tracks,programme.scope,finance.government_funding,finance.enterprise_compensation,work.remote_arrangement,work.travel_arrangement,work.relocation',
       updated_at = updated_at
 WHERE id = 24
   AND coverage_keys = 'programme.purpose,programme.structure,programme.tracks,programme.scope,finance.government_funding,finance.enterprise_compensation,work.remote_arrangement,work.travel_arrangement,work.relocation,fees.policy,confidentiality.materials';
