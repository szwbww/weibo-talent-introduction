-- V76: Add coverage_keys column to qa_rule and backfill known rules.
-- coverage_keys records what business facts the rule body actually covers.
-- Keywords control "could be relevant"; coverage keys control "can answer this intent".
-- ASCII-only; no Chinese literals.

ALTER TABLE qa_rule
    ADD COLUMN coverage_keys VARCHAR(2000) NOT NULL DEFAULT '';

-- Backfill by stable reply_subject (NOT by keyword inference).
-- Unknown/weak rules keep empty string.

-- Company registered identity (V75 new rule)
UPDATE qa_rule
   SET coverage_keys = 'company.legal_name,company.registered_location'
 WHERE reply_subject = 'Company registered identity and location' AND coverage_keys = '';

-- Agency credentials
UPDATE qa_rule
   SET coverage_keys = 'company.verification_evidence'
 WHERE reply_subject = 'Agency credentials and government cooperation' AND coverage_keys = '';

-- Program overview
UPDATE qa_rule
   SET coverage_keys = 'programme.purpose,programme.structure,programme.tracks,programme.scope,finance.government_funding,finance.enterprise_compensation,work.remote_arrangement,work.travel_arrangement,work.relocation,fees.policy,confidentiality.materials'
 WHERE reply_subject = 'Program overview' AND coverage_keys = '';

-- About the talent program
UPDATE qa_rule
   SET coverage_keys = 'programme.purpose,programme.tracks,finance.government_funding,finance.enterprise_compensation'
 WHERE reply_subject = 'About the talent program' AND coverage_keys = '';

-- Partner company information
UPDATE qa_rule
   SET coverage_keys = 'enterprise.matching'
 WHERE reply_subject = 'Partner company information' AND coverage_keys = '';

-- Responsibilities and benefits
UPDATE qa_rule
   SET coverage_keys = 'role.responsibilities'
 WHERE reply_subject = 'Responsibilities and benefits' AND coverage_keys = '';

-- Contract and IP arrangements
UPDATE qa_rule
   SET coverage_keys = 'contract.party,contract.terms,ip.arrangements'
 WHERE reply_subject = 'Contract and IP arrangements' AND coverage_keys = '';

-- Funding support
UPDATE qa_rule
   SET coverage_keys = 'finance.government_funding,finance.enterprise_compensation'
 WHERE reply_subject = 'Funding support' AND coverage_keys = '';

-- Application process
UPDATE qa_rule
   SET coverage_keys = 'application.steps,application.timeline'
 WHERE reply_subject = 'Application process' AND coverage_keys = '';

-- Getting started materials
UPDATE qa_rule
   SET coverage_keys = 'application.required_materials'
 WHERE reply_subject = 'Getting started materials' AND coverage_keys = '';

-- Researcher selection (replace in application criteria, NOT Possible role)
UPDATE qa_rule
   SET coverage_keys = 'researcher.selection'
 WHERE reply_subject = 'Application criteria' AND coverage_keys = '';

-- Application deadline
UPDATE qa_rule
   SET coverage_keys = 'application.timeline'
 WHERE reply_subject = 'Application deadline' AND coverage_keys = '';

-- Project sensitivity
UPDATE qa_rule
   SET coverage_keys = 'confidentiality.materials'
 WHERE reply_subject = 'Project sensitivity concerns' AND coverage_keys = '';

-- Workplace arrangement
UPDATE qa_rule
   SET coverage_keys = 'work.travel_arrangement,work.relocation'
 WHERE reply_subject = 'Workplace arrangement' AND coverage_keys = '';

-- Full-time/part-time
UPDATE qa_rule
   SET coverage_keys = 'work.remote_arrangement,work.travel_arrangement'
 WHERE reply_subject = 'Full-time and part-time options' AND coverage_keys = '';
