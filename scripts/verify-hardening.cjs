// Secret-safe local Postman runner; uses an existing Newman installation, not an application dependency.
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const collection = JSON.parse(fs.readFileSync(path.join(root,'postman/Rockey-Housekeeping.postman_collection.json'),'utf8'));
const snapshot = JSON.parse(fs.readFileSync(path.join(root,'docs/rockey-openapi.json'),'utf8'));
const normalize = value => value.replace('{{baseUrl}}','').replace('/api/','/').replace(/\{\{\w+\}\}|\{\w+\}/g,'{id}');
const expected = new Set(Object.entries(snapshot.paths).flatMap(([url,value]) =>
  Object.keys(value).filter(method=>['get','post','put','delete'].includes(method)).map(method=>method.toUpperCase()+' '+normalize(url))));
const covered = new Set(collection.item.map(item=>item.request.method+' '+normalize(item.request.url)));
if (expected.size!==24 || [...expected].some(op=>!covered.has(op))) throw new Error('24-operation Postman coverage mismatch.');
console.log('Static Postman operation coverage: 24/24.');
if (process.argv.includes('--audit-only')) process.exit(0);
if (!/^jdbc:mysql:\/\/127\.0\.0\.1:3306\/rockey_hospitality_hardening\?/.test(process.env.ROCKEY_DB_URL || '')) throw new Error('Disposable schema configuration required.');
const baseUrl = process.env.ROCKEY_HARDENING_API_URL || 'http://127.0.0.1:18080/api';
if (!/^http:\/\/127\.0\.0\.1:\d+\/api$/.test(baseUrl)) throw new Error('Loopback backend URL required.');
const email = process.env.ROCKEY_HARDENING_ADMIN_EMAIL, password = process.env.ROCKEY_HARDENING_ADMIN_PASSWORD;
if (!email || !password) throw new Error('Configure disposable ADMIN credentials privately in process environment.');
const newman = require(process.env.ROCKEY_NEWMAN_PATH || 'newman');
newman.run({ collection, reporters: [], environment: {values:[
  {key:'baseUrl',value:baseUrl},{key:'adminEmail',value:email},{key:'adminPassword',value:password},
]}}, async (error,summary) => {
  if (error || summary.run.failures.length) {
    console.error('Postman verification FAILED. No request/response or environment secrets exported.');
    if (summary) {
      const codes={};
      for (const execution of summary.run.executions) { const code=execution.response?.code || 'NO_RESPONSE'; codes[code]=(codes[code]||0)+1; }
      console.error('HTTP status counts: '+JSON.stringify(codes));
      for (const failure of summary.run.failures.slice(0,5)) console.error((failure.source?.name || 'Unnamed assertion')+' — '+(failure.error?.name || 'request failure'));
    }
    process.exitCode=1; return;
  }
  console.log('Postman requests: '+summary.run.stats.requests.total+'; assertions: '+summary.run.stats.assertions.total+'; failures: 0.');
  try {
    const login = await fetch(baseUrl+'/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email,password})});
    if (login.status!==200) throw new Error('Documentation login failed.');
    const session = await login.json();
    const response = await fetch(baseUrl.replace(/\/api$/,'')+'/v3/api-docs',{headers:{Authorization:'Bearer '+session.accessToken}});
    if (response.status!==200) throw new Error('ADMIN OpenAPI retrieval failed.');
    const document = await response.json();
    const count = Object.values(document.paths).reduce((total,value)=>total+Object.keys(value).filter(method=>['get','post','put','delete'].includes(method)).length,0);
    if (count!==24) throw new Error('Live API operation count mismatch.');
    fs.mkdirSync(path.join(root,'target'),{recursive:true});
    fs.writeFileSync(path.join(root,'target/housekeeping-openapi.json'),JSON.stringify(document,null,2)+'\n');
    await fetch(baseUrl+'/auth/logout',{method:'POST',headers:{Authorization:'Bearer '+session.accessToken}});
    console.log('Live OpenAPI: 24/24 operations; credential-free generated snapshot in ignored target/.');
  } catch { console.error('Live OpenAPI verification failed.'); process.exitCode=1; }
});
