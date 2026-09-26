import pathlib,json,re,unicodedata,xml.etree.ElementTree as ET,collections
root=pathlib.Path('/tmp/deep-discovery-name-audit')
def text(n):
 if n is None:return ''
 out=n.text or ''
 for c in n:
  value=text(c)
  out+=(' '+value+' ') if c.tag=='email' else value
  out+=c.tail or ''
 return out
def norm(s):return ''.join(c for c in unicodedata.normalize('NFKD',s).casefold() if c.isalnum())
def initials(s):return ''.join(w[0] for w in re.findall(r'[^\W\d_]+',s,re.UNICODE))
mail_re=re.compile(r'[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}')
def extract(p):
 doc=ET.parse(p).getroot();meta=doc.find('./front/article-meta')
 if meta is None:return {},None,[]
 doi=next((text(n).strip().lower() for n in meta.findall('article-id') if n.get('pub-id-type')=='doi'),None)
 authors=[];mapping=collections.defaultdict(set);evidence=collections.defaultdict(set);snippets=collections.defaultdict(list);nodes={n.get('id'):n for n in meta.iter() if n.get('id')}
 def add(email,name,method,snippet):
  mapping[email].add(name);evidence[email].add(method);snippets[email].append(snippet)
 for c in meta.findall('./contrib-group/contrib'):
  if c.get('contrib-type') not in (None,'author'):continue
  name=c.find('name')
  if name is None:continue
  g=text(name.find('given-names')).strip();f=text(name.find('surname')).strip();full=(g+' '+f).strip()
  if not g or not f:continue
  a={'name':full,'initials':norm(initials(g+' '+f)),'refs':set(rid for x in c.findall('xref') if x.get('ref-type') in ('corresp','author-notes') for rid in x.get('rid','').split())};authors.append(a)
  for em in c.findall('./email'):
   email=text(em).strip().lower()
   if mail_re.fullmatch(email):add(email,full,'XML_DIRECT','Author node: '+full+'; email: '+email)
 refs={rid for a in authors for rid in a['refs']}
 corresp_refs={rid for x in meta.findall('.//contrib/xref[@ref-type="corresp"]') for rid in x.get('rid','').split()}
 for rid in refs:
  node=nodes.get(rid)
  if node is None:continue
  claimants=[a for a in authors if rid in a['refs']];txt=text(node);matches=list(mail_re.finditer(txt));emails={m.group(0).lower() for m in matches}
  # Full names are matched independently for EACH email, bounded by the previous email.
  previous_end=0;last_named=None
  for m in matches:
   prefix=txt[previous_end:m.start()];previous_end=m.end()
   prefix=re.sub(r'(?i)\b(?:e-?mail(?:\s+address)?)\s*[:：]?\s*$','',prefix).strip()
   candidates=[a for a in claimants if norm(prefix).endswith(norm(a['name']))]
   if len(candidates)==1:
    last_named=candidates[0]['name'];add(m.group(0).lower(),last_named,'XML_EXPLICIT_FULLNAME',prefix.strip()+' '+m.group(0))
   elif last_named and re.fullmatch(r'[\s,/]*(?:(?:and|or)[\s,/]*)?',prefix,re.IGNORECASE):
    add(m.group(0).lower(),last_named,'XML_EXPLICIT_FULLNAME_EMAIL_LIST','Explicit name: '+last_named+'; continuation: '+prefix.strip()+' '+m.group(0))
   else:last_named=None
  # Initial labels can cover several emails ONLY when no other name/text intervenes.
  for segment in txt.split(';'):
   labels=list(re.finditer(r'\(([^()]*)\)',segment));ems=list(mail_re.finditer(segment))
   if len(labels)!=1 or not ems:continue
   label=labels[0];candidates=[a for a in claimants if a['initials']==norm(label.group(1))]
   if len(candidates)!=1 or label.start()<ems[-1].end() or norm(segment[label.end():]):continue
   residual=segment[:label.start()]+segment[label.end():];residual=mail_re.sub('',residual)
   residual=re.sub(r'(?i)\b(?:correspondence|corresponding\s+authors?|e-?mails?|and|or)\b','',residual)
   if norm(residual) not in ('',norm(candidates[0]['name'])):continue
   for em in ems:add(em.group(0).lower(),candidates[0]['name'],'XML_EXPLICIT_INITIALS',segment.strip())
  # An exclusive cross-reference is usable only for one email, and no conflicting explicit mapping.
  if len(claimants)==1 and len(emails)==1 and (node.tag=='corresp' or rid in corresp_refs or re.search(r'(?i)correspond',txt)):
   email=next(iter(emails));add(email,claimants[0]['name'],'XML_UNIQUE_XREF',txt.strip())
 # Explicit contributor-information paragraphs are independent primary evidence.
 for sec in doc.findall('./back//sec')+doc.findall('./body//sec[@sec-type="contrib-info"]'):
  if norm(text(sec.find('title'))) not in ('contributorinformation','authorinformation'):continue
  for para in sec.findall('./p'):
   txt=text(para);ems=list(mail_re.finditer(txt))
   if len(ems)!=1:continue
   em=ems[0];prefix=re.sub(r'(?i)\b(?:e-?mail(?:\s+address)?)\s*[:：]?\s*$','',txt[:em.start()]).strip()
   candidates=[a for a in authors if norm(prefix)==norm(a['name'])]
   if len(candidates)==1:add(em.group(0).lower(),candidates[0]['name'],'XML_CONTRIBUTOR_INFORMATION',txt.strip())
 return {e:{'names':sorted(v),'method':'+'.join(sorted(evidence[e])),'snippets':list(dict.fromkeys(snippets[e]))} for e,v in mapping.items()},doi,[a['name'] for a in authors]
if __name__=='__main__':
 groups=json.load(open(root/('duplicate-groups-with-pmc.json' if (root/'duplicate-groups-with-pmc.json').exists() else 'duplicate-groups.json')));out=[];counts=collections.Counter();matched_pairs=collections.defaultdict(set);parsed={};manual_reviews={r['email']:r for r in json.load(open(root/'manual-variant-review.json'))}
 for group in groups:
  for r in group['members']:
   pmc=r.get('externalIds',{}).get('pmcId');p=root/'public_xml'/(str(pmc)+'.xml')
   row={**r,'groupKey':group['nameKey'],'samePaperName':bool(r.get('doi') and r['doi'].lower() in group['samePaperDois'])}
   if not pmc:row['verdict']='NO_PMC_XML_REFERENCE'
   elif not p.exists():row['verdict']='XML_UNAVAILABLE'
   else:
    try:
     if pmc not in parsed:parsed[pmc]=extract(p)
     mappings,doi,authors=parsed[pmc];row['pmcId']=pmc;row['evidenceUrl']='https://pmc.ncbi.nlm.nih.gov/articles/'+pmc+'/'
     mapped=mappings.get(r['email']);row['paperAuthorNames']=authors
     if doi and r.get('doi') and doi.lower()!=r['doi'].lower():row['verdict']='PAPER_REFERENCE_CONFLICT'
     elif not mapped or len(mapped['names'])!=1:row['verdict']='AMBIGUOUS_OR_UNMAPPED_EMAIL'
     else:
      expected=mapped['names'][0];row.update(expectedName=expected,evidenceMethod=mapped['method'],evidenceSnippets=mapped.get('snippets',[]))
      if norm(expected)==norm(r['name']):row['verdict']='MATCH'
      elif norm(r['name']) in {norm(a) for a in authors}:row['verdict']='CONFIRMED_OTHER_AUTHOR'
      else:row['verdict']='NAME_VARIANT_OR_WRONG_REVIEW'
    except Exception as e:row['verdict']='XML_PARSE_ERROR';row['error']=str(e)
   if row['verdict']=='NAME_VARIANT_OR_WRONG_REVIEW' and row['email'] in manual_reviews:
    review=manual_reviews[row['email']];assert row['name']==review['storedName'] and row['expectedName']==review['expectedName'] and row['pmcId']==review['pmcId']
    row['verdict']=review['verdict'];row['manualReview']=review['reviewReason']
   counts[row['verdict']]+=1;out.append(row)
 (root/'xml-assessment.json').write_text(json.dumps(out,ensure_ascii=False,indent=2))
 wrong=[r for r in out if r['verdict']=='CONFIRMED_OTHER_AUTHOR'];(root/'new-confirmed-wrong.json').write_text(json.dumps(wrong,ensure_ascii=False,indent=2))
 print(json.dumps({'total':len(out),'counts':counts,'confirmedWrong':len(wrong),'wrongContacted':sum(any(c['sent'] for c in r['contacts']) for r in wrong)},ensure_ascii=False))
 for r in sorted(wrong,key=lambda r:(-any(c['sent'] for c in r['contacts']),r['email']))[:20]:print(r['email'],r['name'],'->',r['expectedName'],r['evidenceMethod'],r['pmcId'])
