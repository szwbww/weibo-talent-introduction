"""Read-only server-side diagnosis. Inject query(sql), TRUTH before execution.

No business writes or external requests. Returned targets contain hashes and IDs,
never email addresses, full profiles or original documents.
"""
import collections
import hashlib
import json
import re
import unicodedata

def fp(s):
    return hashlib.sha256(s.encode('utf-8')).hexdigest()[:24]

def norm(s):
    s = unicodedata.normalize('NFKD', s).lower()
    return ' '.join(re.sub(r'[^\w]+', ' ', ''.join(c for c in s if not unicodedata.combining(c))).split())

TARGETS = []
for row in query("SELECT id,input_json,result_json FROM discovery_paper_reprocess_item WHERE execution_id=31982 AND kind='TARGET' AND JSON_UNQUOTE(JSON_EXTRACT(result_json,'$.reasonCode')) IN ('IDENTITY_NOT_BOUND','SOURCE_IDENTITY_CONFLICT','EXISTING_IDENTITY_CONFLICT')"):
    TARGETS.append({'id':int(row[0]), 'input':json.loads(row[1]), 'result':json.loads(row[2])})
record_ids = sorted({r['recordId'] for t in TARGETS for r in t['input']['paperRefs']})
PAPERS = {}
for start in range(0, len(record_ids), 250):
    ids = ','.join("'%s'" % n for n in record_ids[start:start+250])
    for row in query("SELECT item_key,input_json,result_json FROM discovery_paper_reprocess_item WHERE execution_id=31982 AND kind='PAPER' AND item_key IN ("+ids+")"):
        PAPERS[int(row[0])] = {'input':json.loads(row[1]), 'result':json.loads(row[2])}

truth = collections.defaultdict(list)
for x in TRUTH:
    truth[x['emailHash']].append(x)
counts = collections.Counter()
out = []
for t in TARGETS:
    i,r=t['input'],t['result']; refs=i['paperRefs']; reason=r['reasonCode']
    named=[x for x in refs if x.get('givenNames') and x.get('familyNames')]
    names=set(' '.join((x['givenNames'],x['familyNames'])).strip().lower() for x in named)
    item={'id':t['id'],'emailHash':fp(i['email'].strip().lower()),'reason':reason,
          'records':sorted(set(x['recordId'] for x in refs))}
    exact=[]
    for tr in truth[item['emailHash']]:
        if any(x['recordId']==tr['recordId'] and x['bodyHash']==tr['bodyHash'] for x in refs):
            exact.append({k:tr[k] for k in ('recordId','page','rule','nameHash')})
    item['confirmedPriorEvidence']=exact
    if reason=='IDENTITY_NOT_BOUND':
        counts['missing_total']+=1
        item['allNamesNull']=all(x.get('givenNames') is None and x.get('familyNames') is None for x in refs)
        counts['missing_all_names_null']+=int(item['allNamesNull'])
        if exact: counts['missing_confirmed_prior_targets']+=1
        counts['missing_confirmed_prior_combinations']+=len(exact)
        formats=set(); authors=[]; reasons=set()
        for ref in refs:
            p=PAPERS[ref['recordId']]
            authors.append(len(p['input']['metadata'].get('authors') or []))
            for a in p['result'].get('attempts',[]):
                if a.get('hash')==ref['bodyHash']:
                    formats.add(a.get('parsed',{}).get('format','UNKNOWN'))
                    reasons.update(a.get('parsed',{}).get('reasons',[]))
        item['formats']=sorted(formats); item['parserReasons']=sorted(reasons)
        item['metadataAuthorCounts']=sorted(set(authors))
        counts['missing_format_'+'+'.join(sorted(formats))]+=1
        counts['missing_metadata_'+('all_empty' if not any(authors) else 'single_author_only' if max(authors)==1 else 'multi_author')]+=1
        for code in reasons: counts['missing_parser_reason_'+code]+=1
    elif reason=='SOURCE_IDENTITY_CONFLICT':
        # Mirrors Kotlin's inconsistent APIs: asText() passes JSON null as "null"
        # through the blank filter; name() then uses asText("") and maps it to "".
        raw_names=set(' '.join(x.get(k) or '' for k in ('givenNames','familyNames')).strip().lower()
                      for x in refs if all(('null' if x.get(k) is None else x[k]).strip()
                                          for k in ('givenNames','familyNames')))
        real_ids={k:len(set(x[k] for x in refs if x.get(k))) for k in ('orcidId','openAlexAuthorId')}
        phantom=(len(names)==1 and '' in raw_names and len(raw_names)==2 and max(real_ids.values())<=1)
        item['nullNamePhantom']=phantom
        counts['source_null_name_phantom' if phantom else 'source_other']+=1
        counts['source_confirmed_prior_targets']+=int(bool(exact))
    else:
        b=(r.get('before') or {}).get('source')
        item['rawPresent']=b is not None
        if b is None:
            item['category']='RAW_MISSING'
        else:
            item['rawDiscoveryEligible']=(b.get('identityVerification') is not None or b.get('emailSource') in ('PAPER_FULLTEXT','ORCID_PUBLIC') or 'discovered' in (b.get('tags') or []))
            old=(b.get('givenNames') or '', b.get('familyNames') or '')
            new=(named[0]['givenNames'],named[0]['familyNames'])
            if tuple(x.lower() for x in old)==tuple(x.lower() for x in new): category='SAME_FIELDS'
            elif norm(' '.join(old))==norm(' '.join(new)): category='SAME_COMBINED_NAME'
            elif sorted(norm(' '.join(old)).split())==sorted(norm(' '.join(new)).split()): category='REORDERED_NAME'
            elif norm(old[1])==norm(new[1]) or norm(old[0])==norm(new[0]): category='PARTIAL_NAME_VARIANT'
            else: category='DIFFERENT_NAME'
            item['category']=category
            oldnames={norm(' '.join(old))}
            meta_names=set()
            for ref in refs:
                for a in PAPERS[ref['recordId']]['input']['metadata'].get('authors') or []:
                    meta_names.add(norm(' '.join((a.get('givenNames') or '',a.get('familyNames') or ''))))
            item['rawNameIsCoauthor']=norm(' '.join(old)) in meta_names
            item['rawProofPresent']=b.get('identityVerification') is not None
            ids={k:sorted(set(x[k] for x in refs if x.get(k))) for k in ('orcidId','openAlexAuthorId')}
            ext=b.get('externalIds') or {}
            if isinstance(ext,str):
                try: ext=json.loads(ext)
                except Exception: ext={}
            item['sourceOrcidMatchesRaw']=bool(ids['orcidId']) and (b.get('orcidId') in ids['orcidId'] or ext.get('orcid') in ids['orcidId'])
            counts['existing_raw_not_discovery']+=int(not item['rawDiscoveryEligible'])
            counts['existing_raw_name_coauthor']+=int(item['rawNameIsCoauthor'])
        counts['existing_'+item['category']]+=1
    out.append(item)

REPORT={'runId':31982,'sourceExecutionId':30711,'targetCount':len(TARGETS),
        'paperRecords':len(PAPERS),'counts':dict(counts),'targets':out}
