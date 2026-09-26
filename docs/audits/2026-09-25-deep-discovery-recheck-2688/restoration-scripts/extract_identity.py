import pathlib,json,re,sys,xml.etree.ElementTree as ET,collections,importlib.util
sys.path.insert(0,'/tmp/deep-discovery-recheck-2688');from extract_supplemental import assess,norm,text
from extract_base_corrected import extract
p=pathlib.Path('/tmp/expert-identity-restore-193');cache=pathlib.Path('/tmp/deep-discovery-name-audit/public_xml')
rows=json.load(open('docs/audits/2026-09-25-deep-discovery-recheck-2688/confirmed-wrong.json'));out=[];parsed={}
def afftext(n):
 v=n.text or ''
 for c in n:
  if c.tag not in ('label','email','sup','institution-id'):v+=afftext(c)
  v+=c.tail or ''
 return ' '.join(v.split())
for r in rows:
 pmc=r['pmcId']
 path=next(folder/(pmc+'.xml') for folder in [pathlib.Path('/tmp/deep-discovery-recheck-2688/public_xml'),cache] if (folder/(pmc+'.xml')).exists())
 if pmc not in parsed:
  doc=ET.parse(path).getroot();meta=doc.find('./front/article-meta');parsed[pmc]=(meta,extract(path))
 meta,(mappings,doi,authors)=parsed[pmc];result=assess(path,r['email'],r['oldName']);assert norm(result.get('expectedName',''))==norm(r['expectedName']) and result['verdict'] in ('CONFIRMED_OTHER_AUTHOR','NAME_VARIANT_REVIEW')
 nodes=[c for c in meta.findall('./contrib-group/contrib') if c.find('name') is not None and norm(text(c.find('name/given-names'))+' '+text(c.find('name/surname')))==norm(r['expectedName'])];assert len(nodes)==1,(r['email'],len(nodes));c=nodes[0]
 byid={n.get('id'):n for n in meta.iter() if n.get('id')};affs=[];refs=[]
 for x in c.findall('xref[@ref-type="aff"]'):refs.extend(x.get('rid','').split())
 affnodes=[byid[rid] for rid in dict.fromkeys(refs) if rid in byid]+c.findall('./aff')
 if not affnodes and not refs:
  group=next(g for g in meta.findall('./contrib-group') if c in list(g))
  shared=group.findall('./aff') or meta.findall('./aff')
  if len(shared)==1:affnodes=shared
 for a in affnodes:
  txt=afftext(a).split(';')[0].strip(' ;,')
  if '@' in txt:
   glued=re.search(r'(China|Kazakhstan|Chile)(?=[A-Za-z0-9_.+%-]+@)',txt)
   if glued:txt=txt[:glued.end()]
   else:txt=re.sub(r'[A-Za-z0-9_.+%-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}.*$','',txt).strip(' ;,')
  countries=[{'text':text(n).strip(),'code':n.get('country')} for n in a.findall('.//country')]
  if txt:affs.append({'text':txt,'countries':countries})
 oids=sorted({match.group(0).upper() for n in c.findall('./contrib-id') if 'orcid' in ' '.join(n.attrib.values()).lower() for match in re.finditer(r'\d{4}-\d{4}-\d{4}-\d{3}[\dXx]',text(n))})
 years=[int(text(n)) for n in meta.findall('./pub-date/year') if text(n).strip().isdigit()]
 out.append({'id':r['id'],'email':r['email'],'oldName':r['oldName'],'expectedName':r['expectedName'],'givenNames':text(c.find('name/given-names')).strip(),'familyNames':text(c.find('name/surname')).strip(),'affiliations':affs,'verifiedOrcids':oids,'doi':doi,'paperYear':min(years) if years else None,'pmcId':pmc,'evidenceUrl':r['evidenceUrl']})
(p/'verified-identities.json').write_text(json.dumps(out,ensure_ascii=False,indent=2))
print(json.dumps({'records':len(out),'noAffiliations':sum(not r['affiliations'] for r in out),'withOrcid':sum(bool(r['verifiedOrcids']) for r in out),'noPaperYear':sum(not r['paperYear'] for r in out)}))
print('pycountry',bool(importlib.util.find_spec('pycountry')))
for r in out:
 if not r['id'].startswith('EMAIL-'):print(r['email'],r['expectedName'],'oldID',r['id'],'verifiedOrcid',r['verifiedOrcids'],'doi',r['doi'])
