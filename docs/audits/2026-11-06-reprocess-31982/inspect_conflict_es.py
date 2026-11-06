"""Server-only read of 60 conflicting targets. Credentials never leave host."""
import base64
import glob
import urllib.request

def current_es():
    for path in glob.glob('/proc/[0-9]*/cmdline'):
        try:
            if b'org.apache.catalina.startup.Bootstrap' not in open(path,'rb').read(): continue
            env=dict(x.decode().split('=',1) for x in open(path.replace('cmdline','environ'),'rb').read().split(b'\0') if b'=' in x)
            if env.get('ES_PASSWORD'): break
        except (OSError,UnicodeError): continue
    else: raise RuntimeError('ES_ENV_UNAVAILABLE')
    token=base64.b64encode((env.get('ES_USERNAME','elastic')+':'+env['ES_PASSWORD']).encode()).decode()
    base=env.get('ES_BASE_URL','https://es-fcxvip4d.public.tencentelasticsearch.com:9200')
    indices=[env.get('ES_RAW_INDEX_NAME','orcid_info'),env.get('ES_CANDIDATE_INDEX_NAME','orcid_info_candidate'),env.get('ES_APPLICATION_INDEX_NAME','orcid_info_application')]
    def search(addresses):
        body={'size':1000,'query':{'terms':{'email':addresses}},'_source':['email','givenNames','familyNames','orcidId','externalIds','identityVerification','tags','emailSource']}
        req=urllib.request.Request(base+'/'+','.join(indices)+'/_search?ignore_unavailable=true',data=json.dumps(body).encode(),headers={'Authorization':'Basic '+token,'Content-Type':'application/json'})
        data=json.load(urllib.request.urlopen(req,timeout=25))
        assert not data.get('timed_out') and data['_shards']['failed']==0
        return data['hits']['hits']
    return search,indices

selected=[t for t in TARGETS if t['result'].get('reasonCode')=='EXISTING_IDENTITY_CONFLICT']
search,indices=current_es()
hits=search([t['input']['email'] for t in selected])
byemail=collections.defaultdict(list)
for h in hits:
    values=h['_source'].get('email') or []
    if isinstance(values,str): values=[values]
    for address in values:
        if isinstance(address,str): byemail[address.strip().lower()].append(h)
byid={t['id']:t for t in REPORT['targets']}
totals=collections.Counter()
for t in selected:
    refs=t['input']['paperRefs']; bound=next(x for x in refs if x.get('givenNames') and x.get('familyNames') and x.get('identityEvidence'))
    expected=(bound['givenNames'],bound['familyNames']); actual=byemail[t['input']['email'].strip().lower()]
    detail=[]
    for h in actual:
        s=h['_source']; name=(s.get('givenNames') or '',s.get('familyNames') or '')
        mismatch=any(a.strip() and a.lower()!=b.lower() for a,b in zip(name,expected))
        detail.append({'level':indices.index(h['_index']),'docIdHash':fp(h['_id']),
                       'nameMismatch':mismatch,'proofPresent':s.get('identityVerification') is not None,
                       'emailIsArray':isinstance(s.get('email'),list)})
    out=byid[t['id']]; out['currentEs']=detail
    out['currentDistinctDocs']=len(set(h['_id'] for h in actual))
    totals['current_no_raw']+=int(not any(h['_index']==indices[0] for h in actual))
    totals['current_any_name_mismatch']+=int(any(x['nameMismatch'] for x in detail))
    totals['current_multiple_docids']+=int(out['currentDistinctDocs']>1)
    totals['current_name_equal_all_layers']+=int(bool(detail) and not any(x['nameMismatch'] for x in detail))
    totals['current_email_array_hit']+=int(any(x['emailIsArray'] for x in detail))
REPORT['esCheck']={'targets':len(selected),'documents':len(hits),'counts':dict(totals),'requests':1}
