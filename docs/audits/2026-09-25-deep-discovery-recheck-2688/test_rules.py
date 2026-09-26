import pathlib,tempfile,xml.etree.ElementTree as E
from extract_supplemental import supplemental

def run(authors,notes):
 with tempfile.TemporaryDirectory() as d:
  p=pathlib.Path(d)/'p.xml';p.write_text('<article><front><article-meta><contrib-group>'+authors+'</contrib-group><author-notes>'+notes+'</author-notes></article-meta></front></article>');return supplemental(p)[0]
def author(g,f,inside=''):return '<contrib contrib-type="author"><name><given-names>'+g+'</given-names><surname>'+f+'</surname></name>'+inside+'</contrib>'
a=author('Jing','Li')+author('Cheng','Zhang')
r=run(a,'<corresp>a@x.edu (J. Li), b@x.edu (C. Zhang).</corresp>');assert r['a@x.edu'][0]['name']=='Jing Li' and r['b@x.edu'][0]['name']=='Cheng Zhang'
r=run(author('Wei','Zhang')+author('Wen','Zhao'),'<corresp>a@x.edu (W.Z.); b@x.edu (W.Z.)</corresp>');assert not r
r=run(author('Alice','Smith')+author('Bob','Jones'),'<corresp>Correspondence: Alice Smith; Bob Jones, University X, Emails a@x.edu; b@x.edu</corresp>');assert not r
r=run(author('Alice','Smith')+author('Bob','Jones'),'<corresp>Alice Smith, University X. Email: a@x.edu. Bob Jones, University Y. Email: b@x.edu.</corresp>');assert {x['name'] for x in r['a@x.edu']}=={'Alice Smith'} and {x['name'] for x in r['b@x.edu']}=={'Bob Jones'}
r=run(author('Alice','Smith','<aff><email>a@x.edu</email></aff>')+author('Bob','Jones'),'');assert not r
r=run(author('Alice','Smith','<address><email>a@x.edu</email></address>')+author('Bob','Jones'),'');assert r['a@x.edu'][0]['name']=='Alice Smith'
r=run(author('Alice','Smith','<xref ref-type="author-notes" rid="c"/>')+author('Bob','Jones','<xref ref-type="author-notes" rid="c"/>'),'<fn id="c"><p>Email a@x.edu</p></fn>');assert not r
r=run(author('Alice','Smith','<xref ref-type="author-notes" rid="c"/>'),'<fn id="c"><p>Email a@x.edu</p></fn>');assert r['a@x.edu'][0]['name']=='Alice Smith'
r=run(author('Alice','Smith')+author('Bob','Jones'),'<corresp>Co-authors: AS: a@x.edu, BJ: b@x.edu</corresp>');assert r['a@x.edu'][0]['name']=='Alice Smith' and r['b@x.edu'][0]['name']=='Bob Jones'
from extract_supplemental import assess
with tempfile.TemporaryDirectory() as d:
 p=pathlib.Path(d)/'p.xml';p.write_text('<article><front><article-meta><contrib-group>'+author('Alice','Smith','<xref ref-type="corresp" rid="c"/>')+author('Bob','Jones','<xref ref-type="corresp" rid="c"/>')+'</contrib-group><author-notes><corresp id="c">a@x.edu (Alice Smith), b@x.edu (Bob Jones)</corresp></author-notes></article-meta></front></article>')
 result=assess(p,'b@x.edu','Alice Smith');assert result['expectedName']=='Bob Jones' and result['verdict']=='CONFIRMED_OTHER_AUTHOR';assert result['originalMapping'] is None
print('PASS: 10 source-evidence boundary cases, including trailing-name-label regression')
