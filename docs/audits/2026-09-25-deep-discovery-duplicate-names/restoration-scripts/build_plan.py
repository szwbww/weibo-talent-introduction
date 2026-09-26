import pathlib,json,re,unicodedata,collections
p=pathlib.Path('/tmp/expert-identity-restore-2102');rows=json.load(open(p/'verified-identities.json'));current={r['id']:r['source'] for r in map(json.loads,open(p/'preflight.jsonl')) if r['kind']=='expert'}
def norm(s):return re.sub('[^a-z0-9]','',unicodedata.normalize('NFKD',s or '').encode('ascii','ignore').decode().lower())
countries={}
for l in open('/usr/share/zoneinfo/iso3166.tab'):
 if not l.startswith('#') and l.strip():
  code,name=l.strip().split('\t',1);countries[code]=name
countries.update(KR='Republic of Korea',KP='Democratic People\'s Republic of Korea',RU='Russia',IR='Iran',TW='Taiwan',TR='Türkiye',VN='Vietnam',US='United States',GB='United Kingdom',BO='Bolivia',VE='Venezuela',TZ='Tanzania',MD='Moldova',SY='Syria')
aliases={name.lower():name for name in countries.values()};aliases['bosnia and herzegovina']='Bosnia and Herzegovina'
for name,aa in {'China':['PR China','P.R. China','P. R. China',"People’s Republic of China","People\'s Republic of China"],'United States':['USA','U.S.A.','US','U.S.','United States of America'],'United Kingdom':['UK','U.K.','England','Scotland','Wales'],'Republic of Korea':['South Korea','Korea, Republic of'],'Türkiye':['Turkey','Turkiye'],'Russia':['Russian Federation'],'Iran':['Islamic Republic of Iran'],'Taiwan':['Taiwan, ROC','Taiwan, R.O.C.']}.items():
 for a in aa:aliases[a.lower()]=name
def country(a):
 for c in a['countries']:
  if c['text'].strip().strip('.').lower() in aliases:return aliases[c['text'].strip().strip('.').lower()]
  if (c['code'] or '').upper() in countries:return countries[c['code'].upper()]
 txt=re.sub(r'\s*\([^()]*\)\s*$','',a['text']).strip().strip(' .')
 txt=re.sub(r',?\s*\d{5,6}$','',txt).strip(' .,')
 for k in sorted(aliases,key=len,reverse=True):
  if re.search(r'(?<![\w])'+re.escape(k.rstrip('.'))+r'\.?$',txt,re.I):return aliases[k]
 return None
plan=[];legacy=[]
for r in rows:
 old=current[r['id']];ext=old.get('externalIds') or {};ext=dict(ext);aff='; '.join(dict.fromkeys(a['text'] for a in r['affiliations']));cs=[country(a) for a in r['affiliations']];ct=next((x for x in cs if x),None)
 patch={'givenNames':r['givenNames'],'familyNames':r['familyNames'],'employment':aff,'institution':aff,'country':ct}
 if old.get('institutionType') is not None:patch['institutionType']=None
 if len(r['verifiedOrcids'])==1:ext['orcid']=r['verifiedOrcids'][0]
 meta={}
 if not r['id'].startswith('EMAIL-'):
  w=json.load(open(p/'public-ids'/('work-'+r['pmcId']+'.json')));o=json.load(open(p/'public-ids'/('orcid-'+r['id']+'.json')))
  matches=[a for a in w['authorships'] if norm(a['author']['display_name'])==norm(r['expectedName']) or norm(a.get('raw_author_name'))==norm(r['expectedName'])];assert len(matches)==1
  author=matches[0]['author'];aid=author['id'].rsplit('/',1)[-1];assert re.fullmatch('A[0-9]+',aid)
  n=o['name'];oname=' '.join((n.get(k) or {}).get('value','') for k in ('given-names','family-name')).strip();correct=norm(oname)==norm(r['expectedName']);ext['openAlexAuthorId']=aid
  oid=(author.get('orcid') or '').rsplit('/',1)[-1];assert re.fullmatch(r'\d{4}-\d{4}-\d{4}-\d{3}[\dX]',oid)
  if r['verifiedOrcids']:assert oid in r['verifiedOrcids']
  # Multiple observed ORCIDs: avoid choosing one without original-author confirmation.
  if len(author.get('observed_orcids',[]))>1 and not r['verifiedOrcids']:ext.pop('orcid',None)
  else:ext['orcid']=oid
  if not correct:
   for k in ('hIndex','citationCount','worksCount','researchFields','disciplineCategory','recentWorkTitles','patentTitles','enrichedAt','enrichmentSource'):patch[k]=None
   patch['lastPublicationYear']=r['paperYear']
  meta={'oldOrcidOwnerName':oname,'legacyOrcidIsCorrect':correct,'verifiedOpenAlexAuthorId':aid,'workUrl':w['id'],'matchedAuthorship':matches[0]};legacy.append({'id':r['id'],'email':r['email'],'expectedName':r['expectedName'],**meta})
 if ext!=old.get('externalIds'):patch['externalIds']=ext
 plan.append({'id':r['id'],'email':r['email'],'oldName':r['oldName'],'expectedName':r['expectedName'],'patch':patch,'evidenceUrl':r['evidenceUrl'],'pmcId':r['pmcId'],'doi':r['doi'],'identityEvidence':meta})
assert len(plan)==2102
(p/'plan.json').write_text(json.dumps(plan,ensure_ascii=False,indent=2));(p/'legacy-identity-evidence.json').write_text(json.dumps(legacy,ensure_ascii=False,indent=2))
print(json.dumps({'targets':len(plan),'legacyIdentityCorrect':sum(x['legacyOrcidIsCorrect'] for x in legacy),'metricsCleared':sum('hIndex' in x['patch'] for x in plan),'countryUnavailable':sum(x['patch']['country'] is None for x in plan),'countries':collections.Counter(x['patch']['country'] for x in plan)},ensure_ascii=False))
print('UNKNOWN_COUNTRIES',json.dumps([r['patch']['institution'] for r in plan if r['patch']['country'] is None][:30],ensure_ascii=False))
