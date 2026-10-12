import pathlib,json,re,urllib.parse
root=pathlib.Path(__file__).resolve().parents[1]; api=json.loads((root/'docs/rockey-openapi.json').read_text()); failures=[]; refs=[]
def walk(x):
 if isinstance(x,dict):
  for k,v in x.items():
   if k=='$ref':
    refs.append(v); node=api
    try:
     assert v.startswith('#/')
     for part in v[2:].split('/'): node=node[part.replace('~1','/').replace('~0','~')]
    except (KeyError,AssertionError): failures.append('invalid ref '+v)
   else: walk(v)
 elif isinstance(x,list):
  for v in x: walk(v)
walk(api)
ops={method.upper()+' '+p for p,item in api['paths'].items() for method in item if method in ['get','post','put','delete']}; controllers=set()
for f in (root/'src/main/java/com/rockey/hospitality/controller').glob('*.java'):
 t=f.read_text(encoding='utf-8'); t=re.sub(r'/\*.*?\*/','',t,flags=re.S); t=re.sub(r'//[^\n]*','',t)
 base=re.search(r'@RequestMapping\("([^\"]+)"\)',t).group(1)
 for m in re.finditer(r'@(Get|Post|Put|Delete)Mapping(?:\("([^\"]*)"\))?',t): controllers.add(m[1].upper()+' '+base+(m[2] or ''))
assert ops==controllers,(ops-controllers,controllers-ops)
def slug(s):
 s=re.sub(r'[`*_]','',s).lower(); s=re.sub(r'[^\w\- ]','',s); return s.replace(' ','-')
links=0
for f in [root/'README.md',*(root/'docs').glob('*.md'),root/'frontend/README.md',root/'frontend/THEME_SYSTEM.md']:
 text=re.sub(r'```.*?```','',f.read_text(encoding='utf-8'),flags=re.S)
 for target in re.findall(r'(?<!!)\[[^\]]+\]\(([^)]+)\)',text):
  target=target.split(' "')[0]
  if re.match(r'^[a-zA-Z]+://',target): continue
  path,_,anchor=target.partition('#'); dest=f.parent/urllib.parse.unquote(path) if path else f; links+=1
  if not dest.exists(): failures.append(str(f)+' missing '+target)
  elif anchor and dest.suffix=='.md':
   headings=[slug(s) for s in re.findall(r'^#+\s+(.+)$',dest.read_text(encoding='utf-8'),re.M)]
   if urllib.parse.unquote(anchor) not in headings: failures.append(str(f)+' anchor '+target)
result={'openapi_refs':len(refs),'controller_contract_operations':len(ops),'relative_links_anchors':links,'failures':failures}
assert not failures, failures

# Static fixture verification is not MySQL execution proof.
import csv, io
sql=(root/'database/seed-test.sql').read_text()
clean=re.sub(r'--[^\n]*','',sql)
assert not re.search(r'\b(DELETE|UPDATE|DROP|TRUNCATE)\s+(FROM|TABLE|DATABASE|users|rooms|tasks|inspections)\b',clean,re.I)
assert "DATABASE() = 'rockey_hospitality_hardening'" in clean
assert '@seed_worker_hash' in clean and '@seed_manager_hash' in clean
assert 'CHECK (allowed = 1)' in clean and 'START TRANSACTION' in clean and 'COMMIT' in clean
assert clean.index("DATABASE()") < clean.index('INSERT INTO users')
assert clean.index('(SELECT COUNT(*) FROM users)') < clean.index('INSERT INTO users')
rows={}
for table,columns,values in re.findall(r'INSERT INTO (users|rooms|tasks|inspections)\((.*?)\) VALUES\s*(.*?);',clean,re.S):
    cols=columns.split(','); records=[]
    for raw in re.findall(r'\((.*?)\)',values,re.S):
        fields=next(csv.reader(io.StringIO(raw),quotechar="'",skipinitialspace=True))
        assert len(fields)==len(cols)
        records.append(dict(zip(cols,fields)))
    assert len({r['id'] for r in records})==len(records)
    rows[table]={r['id']:r for r in records}
assert set(rows)=={'users','rooms','tasks','inspections'}
assert all(u['role'] in {'USER','MANAGER'} and u['password_hash'].startswith('@seed_') for u in rows['users'].values())
for task in rows['tasks'].values():
    assert task['assigned_user_id'] in rows['users'] and task['room_id'] in rows['rooms']
    assert rows['users'][task['assigned_user_id']]['role']=='USER'
    assert (task['status']=='COMPLETED') == (task['completed_at']!='NULL')
for inspection in rows['inspections'].values():
    task=rows['tasks'][inspection['task_id']]
    assert task['status']=='COMPLETED' and task['room_id']==inspection['room_id']
    assert rows['users'][inspection['inspected_by_user_id']]['role']=='MANAGER'
    assert rows['rooms'][inspection['room_id']]['status']==('READY' if inspection['result']=='PASS' else 'DIRTY')
for route in ['/api/users','/api/rooms','/api/tasks','/api/rooms/{id}/inspections']:
    op=api['paths'][route]['get']
    params={p['name']:p for p in op['parameters']}
    assert params['size']['schema']['maximum']==100 and params['page']['schema']['default']==0
    assert set(op['responses']['200']['content']['application/json']['schema']['required'])=={'items','page','size','totalElements','totalPages'}
pipeline=(root/'Jenkinsfile').read_text()
for stage in ['Checkout','Backend verify and coverage','Frontend checks and build','Current source analysis','Quality gate','Package release','Approved deployment']:
    assert "stage('"+stage+"')" in pipeline
assert "defaultValue: false" in pipeline and 'waitForQualityGate abortPipeline: true' in pipeline
assert "ROCKEY_HARDENING_LIVE = 'false'" in pipeline
assert 'input message:' in pipeline and 'StrictHostKeyChecking=yes' in (root/'scripts/deploy-release.sh').read_text()
# The only privileged argv in activation must equal the single sudoers grant; no bare systemctl calls.
activate=(root/'deployment/activate-release.sh').read_text()
grants=re.findall(r'NOPASSWD: (.+)',(root/'deployment/rockey-deploy.sudoers.template').read_text())
assert grants==['/usr/bin/systemctl restart rockey.service']
assert 'restart=(sudo -n '+grants[0]+')' in activate and 'sudo -n -l '+grants[0]+' ' in activate
assert not re.search(r'^\s*systemctl ',activate,re.M) and activate.count('sudo ')==2 and '\r' not in activate
result.update(seed_static_records={k:len(v) for k,v in rows.items()},pagination_contracts=4,pipeline='static structure only; Jenkins runtime NOT RUN')
print(json.dumps(result))
(root/'target/required-static.json').write_text(json.dumps(result,indent=2)+'\n')
