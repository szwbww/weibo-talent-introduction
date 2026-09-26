import json,pathlib,xml.etree.ElementTree as E,sys,re,base64
sys.path.insert(0,'/tmp/deep-discovery-name-audit');from extract_strict import text,norm
p=pathlib.Path('/tmp/deep-discovery-recheck-2688');old=json.load(open('docs/audits/2026-09-25-deep-discovery-duplicate-names/confirmed-wrong.json'));names={'imaginglu@hotmail.com':'Jie Lu','wangwei37@buaa.edu.cn':'Wei Wang','jointwwg@163.com':'Weiguo Wang'};out=[]
for r in old:
 if r['email'] not in names:continue
 meta=E.parse('/tmp/deep-discovery-name-audit/public_xml/'+r['pmcId']+'.xml').find('./front/article-meta');nodes={n.get('id'):n for n in meta.iter() if n.get('id')};c=next(c for c in meta.findall('./contrib-group/contrib') if norm(text(c.find('name/given-names'))+' '+text(c.find('name/surname')))==norm(names[r['email']]))
 aff=[]
 for x in c.findall('./xref[@ref-type="aff"]'):
  for rid in x.get('rid','').split():
   a=nodes[rid];a=E.fromstring(E.tostring(a));
   for l in a.findall('label'):a.remove(l)
   aff.append(' '.join(''.join(a.itertext()).split()))
 ext=dict(r['externalIds']);ext.pop('orcid',None)
 for oid in c.findall('./contrib-id[@contrib-id-type="orcid"]'):
  match=re.search(r'\d{4}-\d{4}-\d{4}-\d{3}[\dX]',text(oid));assert match;ext['orcid']=match.group(0)
 patch={'givenNames':text(c.find('name/given-names')),'familyNames':text(c.find('name/surname')),'institution':'; '.join(aff),'employment':'; '.join(aff),'country':'China','externalIds':ext}
 out.append({'id':r['id'],'email':r['email'],'wrongRestoredName':r['expectedName'],'originalStoredName':r['name'],'expectedName':names[r['email']],'patch':patch,'evidenceUrl':r['evidenceUrl'],'originalAuthorXml':E.tostring(c,encoding='unicode')})
(p/'prior-three-corrections.json').write_text(json.dumps(out,ensure_ascii=False,indent=2))
payload=base64.b64encode(json.dumps(out).encode()).decode();(p/'correct_three.py').write_text(pathlib.Path('/tmp/expert-identity-restore-2102/base.py').read_text()+'\ntargets=json.loads(base64.b64decode("'+payload+'"))\n'+(p/'correct_three_tail.py').read_text())
