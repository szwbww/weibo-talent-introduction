#!/usr/bin/env python3
"""Prepare a local review report from exported CSV. No network, ES writes or mail."""
import argparse
import csv
import hashlib
import json
import re
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

PERSONAL_PROVIDERS = {'gmail.com', 'googlemail.com', 'yahoo.com', 'yahoo.co.uk', 'hotmail.com', 'outlook.com', 'live.com', 'msn.com', 'icloud.com', 'me.com', 'mac.com', 'aol.com', 'proton.me', 'protonmail.com', 'qq.com', '163.com', '126.com'}
COMPANY_DOMAINS = {'applied materials': {'amat.com', 'appliedmaterials.com'}, 'nvidia': {'nvidia.com'}}
EMAIL = re.compile(r'^[A-Za-z0-9.!#$%&\x27+/=?^_`{|}~-]+@(?:[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?\.)+[A-Za-z]{2,63}$')

def classify_email(email, company):
    if not EMAIL.fullmatch(email) or any(c in email for c in '*•●…') or '..' in email.split('@')[0]:
        return 'INVALID'
    domain = email.rsplit('@', 1)[1].lower()
    if domain in PERSONAL_PROVIDERS:
        return 'PERSONAL_PROVIDER'
    if domain in COMPANY_DOMAINS.get(company.lower(), set()):
        return 'CURRENT_COMPANY'
    if domain.endswith('.edu') or '.edu.' in domain or '.ac.' in domain:
        return 'ACADEMIC_DOMAIN'
    return 'UNKNOWN_DOMAIN'

def review_row(row, benchmarks, online):
    emails = list(dict.fromkeys(value.strip().lower() for value in row.get('emails', '').split(';') if value.strip()))
    email_options = [{'email': email, 'category': classify_email(email, row.get('company', '')), 'deliverability': 'NOT_CHECKED', 'ownership': 'NOT_INDEPENDENTLY_CHECKED'} for email in emails]
    personal = [item['email'] for item in email_options if item['category'] == 'PERSONAL_PROVIDER']
    missing = [key for key in ('name', 'company', 'jobTitle', 'location', 'linkedin', 'profileText') if not row.get(key, '').strip()]
    company_matches = [item for item in benchmarks if item.get('benchmark_name', '').casefold() == row.get('company', '').casefold()]
    blockers = ['SENIOR_ROLE_EVIDENCE_REQUIRED', 'RND_TYPE_AND_TECHNICAL_FIT_REQUIRED', 'RESEARCH_FIELD_REVIEW_REQUIRED', 'EMAIL_VERIFICATION_REQUIRED']
    if missing:
        blockers.append('MISSING_PROFILE_FIELDS')
    if not company_matches:
        blockers.append('BENCHMARK_MATCH_REQUIRED')
    if not personal:
        blockers.append('PERSONAL_EMAIL_NOT_IDENTIFIED')
    elif len(personal) > 1:
        blockers.append('PRIMARY_EMAIL_SELECTION_REQUIRED')
    title = row.get('jobTitle', '')
    domain_hint = 'IT_CLOUD_REVIEW_REQUIRED' if re.search(r'cloud|network|\bit\b|enterprise architect|solutions architect', title, re.I) else 'REVIEW_REQUIRED'
    research_hint = 'Cloud Architecture and Cloud Networking' if re.search(r'cloud', title, re.I) else None
    checked_emails = {value.lower() for value in online.get('checkedEmails', [])}
    audit_covers_person = set(emails).issubset(checked_emails) and bool(emails) and row.get('name', '').casefold() == online.get('checkedName', '').casefold()
    duplicates = 'NOT_CHECKED'
    if audit_covers_person:
        total = online.get('sampleEsMatches', {}).get('total', {})
        if 'value' in total and 'sampleContacts' in online:
            duplicates = 'FOUND' if total['value'] or online['sampleContacts'] else 'NOT_FOUND_AT_AUDIT_TIME'
    if duplicates != 'NOT_FOUND_AT_AUDIT_TIME':
        blockers.append('DUPLICATE_OR_CONTACT_HISTORY_REVIEW_REQUIRED')
    tasks = [task for task in online.get('tasks', []) if task['mail_type'] == 'INTRODUCTION']
    target_tag = 'ContactOut个人邮箱待发送'
    accepts_tag = any(target_tag in json.loads(task['tags_json']) for task in tasks)
    if not accepts_tag:
        blockers.append('CONTACTOUT_TASK_NOT_CONFIGURED')
    key = row.get('linkedin') or row.get('name', '') + '|' + row.get('company', '')
    return {
        'recordId': 'CONTACTOUT-' + hashlib.sha256(key.encode()).hexdigest()[:24],
        'sourceRecord': row,
        'emailOptions': email_options,
        'selectedEmail': None,
        'missingFields': missing,
        'benchmarkMatches': [{'id': item.get('benchmark_id'), 'name': item.get('benchmark_name'), 'verificationStatus': item.get('verification_status')} for item in company_matches],
        'domainReview': domain_hint,
        'suggestedResearchField': research_hint,
        'researchFieldEvidence': 'TITLE_ONLY_NOT_REVIEWED' if research_hint else 'MISSING',
        'expertType': None,
        'seniorRoleConfirmed': None,
        'duplicateCheck': duplicates,
        'duplicateCheckedAt': online.get('checkedAt') if audit_covers_person else None,
        'proposedSourceTag': target_tag,
        'status': 'REVIEW_REQUIRED',
        'blockers': blockers,
        'sendReady': False,
    }

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--csv', type=Path, required=True)
    parser.add_argument('--benchmarks', type=Path, required=True)
    parser.add_argument('--online-audit', type=Path)
    parser.add_argument('--output', type=Path, required=True)
    options = parser.parse_args()
    with options.csv.open(encoding='utf-8-sig', newline='') as stream:
        reader = csv.DictReader(stream)
        required = {'name', 'company', 'jobTitle', 'emails', 'profileText'}
        if not required.issubset(reader.fieldnames or []):
            raise SystemExit('CSV 缺少必需列：' + ', '.join(sorted(required - set(reader.fieldnames or []))))
        raw_rows = list(reader)
    benchmarks = json.loads(options.benchmarks.read_text(encoding='utf-8')).get('merged_benchmark_enterprises', [])
    online = json.loads(options.online_audit.read_text(encoding='utf-8')) if options.online_audit else {}
    rows = [review_row(row, benchmarks, online) for row in raw_rows]
    counts = Counter(item['category'] for row in rows for item in row['emailOptions'])
    report = {'schemaVersion': 1, 'preparedAt': datetime.now(timezone.utc).isoformat(), 'inputFile': str(options.csv.resolve()), 'summary': {'people': len(rows), 'emailAddresses': sum(counts.values()), 'emailCategories': dict(counts), 'reviewRequired': len(rows), 'sendReady': 0, 'esWrites': 0, 'emailsSent': 0}, 'rows': rows}
    options.output.parent.mkdir(parents=True, exist_ok=True)
    options.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(report['summary'], ensure_ascii=False))

if __name__ == '__main__':
    main()
