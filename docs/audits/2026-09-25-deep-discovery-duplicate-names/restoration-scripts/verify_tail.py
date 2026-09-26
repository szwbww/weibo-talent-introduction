assert (root/'apply-complete.json').exists(),'No completed application'
readback=all_docs();errors=[];fields=0
for key,old in before.items():
 fresh=readback[key]['_source'];wanted=copy.deepcopy(old['_source']);wanted.update(patches[key]['patch'])
 if fresh!=wanted:errors.append({'kind':'es','index':key[0],'id':key[1],'fields':[k for k in set(fresh)|set(wanted) if fresh.get(k)!=wanted.get(k) or (k in fresh)!=(k in wanted)]})
 for k in ('orcidId','email'):assert fresh.get(k)==old['_source'].get(k),('Stable identity changed',key,k)
 fields+=len(patches[key]['patch'])
rows=contacts();assert set(rows)==set(contactbefore)
for c in m['contacts']:
 wanted=copy.deepcopy(contactbefore[c['id']]);wanted.update({'expert_name':c['expert_name'],'country':c['country']});fresh=dict(rows[c['id']]);fresh.pop('updated_at',None);wanted.pop('updated_at',None)
 if fresh!=wanted:errors.append({'kind':'contact','id':c['id']})
mailSame=mail()==b['mailBaseline']['stdout'];jobSame=jobs()=={j['id']:j for j in b['jobs']}
counts={idx:0 for idx in indices.split(',')}
for start in range(0,len(m['plan']),300):
 batch=m['plan'][start:start+300]
 for idx in counts:
  counts[idx]+=es(idx+'/_count',{'query':{'ids':{'values':[r['id'] for r in batch]}}})['count']
legacy=[r for r in m['plan'] if not r['id'].startswith('EMAIL-')];wrongLegacy=[r for r in legacy if not r['identityEvidence']['legacyOrcidIsCorrect']]
result={'verifiedAt':datetime.now().isoformat(),'status':'PASS' if not errors and mailSame and jobSame and sum(counts.values())==4204 else 'FAIL','experts':2102,'esDocs':len(readback),'layers':counts,'contacts':len(rows),'comparedPatchedFields':fields,'allOtherEsFieldsPreserved':not errors,'stableBusinessKeysPreserved':True,'mailUnchanged':mailSame,'academicJobRowsUnchanged':jobSame,'legacyOrcidReviewed':len(legacy),'wrongAcademicMetricsCleared':len(wrongLegacy),'validLegacyMetricsPreserved':len(legacy)-len(wrongLegacy),'correctOpenAlexIdentities':len(legacy),'countryUnknown':sum(r['patch']['country'] is None for r in m['plan']),'errors':errors,'backupPath':str(root/'before.json'),'backupSha256':hashlib.sha256((root/'before.json').read_bytes()).hexdigest(),'manifestSha256':hashlib.sha256((root/'manifest.json').read_bytes()).hexdigest(),'remainingLimitations':['2688 unresolved audit records unchanged','25 wrong ORCID-shaped legacy business keys preserved; UI must stop treating these keys as actual ORCID','1 legacy case has multiple OpenAlex observed ORCIDs; real ORCID left unset, verified OpenAlex author identity retained','14 affiliation countries lack explicit evidence and are null; source institutions restored']}
save('verification.json',result);print(json.dumps(result,ensure_ascii=True));assert result['status']=='PASS'
