// Test-only runner: no npm/app dependency, no real credentials in argv or exports.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const net = require('node:net');
const { spawn } = require('node:child_process');

const root = path.resolve(__dirname, '..');
const output = path.join(root, 'target', 'hardening');
const envPath = path.join(output, 'newman-environment.json');
const rawReport = path.join(output, 'newman-raw.json');
let application;
let passed = false;
const normalPath = value => value.replace(/\{\{baseUrl\}\}/g, '').split('?')[0]
  .replace(/\{\{\w+\}\}|\{\w+\}/g, '{id}');
function requests(collection) {
  return collection.item.flatMap(item => item.request ? [item] : requests(item));
}
function operations(collection) {
  return new Set(requests(collection).map(item => {
    const url = typeof item.request.url === 'string' ? item.request.url : item.request.url.raw;
    return `${item.request.method} ${normalPath(url)}`;
  }));
}
function checkCoverage() {
  const canonical = new Set(fs.readFileSync(path.join(root, '..', '14_API_CONTRACT.md'), 'utf8')
    .split(/\r?\n/).filter(line => /^\|\s*\d+\s*\|/.test(line)).map(line => line.split('|'))
    .filter(cells => cells.length >= 7 && Number(cells[1].trim()) <= 51)
    .map(cells => cells[2].replaceAll('`', '').trim()).map(operation => {
      const index = operation.indexOf(' ');
      return operation.slice(0, index) + ' ' + normalPath(operation.slice(index + 1));
    }));
  if (canonical.size !== 51) throw new Error('Canonical operation count mismatch.');
  const baseline = new Set();
  for (const file of fs.readdirSync(path.join(root, 'postman')).filter(file => file.endsWith('.json') && !file.includes('Hardening'))) {
    operations(JSON.parse(fs.readFileSync(path.join(root, 'postman', file), 'utf8'))).forEach(op => baseline.add(op));
  }
  const live = operations(JSON.parse(fs.readFileSync(path.join(root, 'postman', 'Rockey-Hardening.postman_collection.json'), 'utf8')));
  for (const op of canonical) if (!baseline.has(op) || !live.has(op)) throw new Error(`Postman operation missing: ${op}`);
  console.log('Static Postman coverage: existing collections 51/51; hardening collection 51/51.');
}
function expiredToken(key) {
  const encode = value => Buffer.from(JSON.stringify(value)).toString('base64url');
  const now = Math.floor(Date.now() / 1000);
  const payload = `${encode({alg:'HS256',typ:'JWT'})}.${encode({sub:'hardening10001@example.test',userId:10001,role:'ADMIN',iat:now-3600,exp:now-1800})}`;
  return payload + '.' + crypto.createHmac('sha256', key).update(payload).digest('base64url');
}
async function portIsFree() {
  return new Promise(resolve => {
    const socket = net.connect(18081, '127.0.0.1');
    socket.once('connect', () => { socket.destroy(); resolve(false); });
    socket.once('error', () => resolve(true));
  });
}
async function ready() {
  const deadline = Date.now() + 45000;
  while (Date.now() < deadline) {
    if (application.exitCode !== null) throw new Error('Application exited before readiness; inspect sanitized startup evidence.');
    try {
      const result = await fetch('http://127.0.0.1:18081/api/auth/me', {signal:AbortSignal.timeout(1000)});
      if (result.status === 401) return;
    } catch { /* bounded startup readiness polling, not a concurrency-test sleep */ }
    await new Promise(resolve => setTimeout(resolve, 100));
  }
  throw new Error('Application readiness timed out.');
}
async function liveSecurityHeaders() {
  const url = 'http://127.0.0.1:18081/api/analytics/dashboard';
  const approved = await fetch(url,{method:'OPTIONS',headers:{Origin:'http://localhost:5173','Access-Control-Request-Method':'GET','Access-Control-Request-Headers':'Authorization'}});
  const denied = await fetch(url,{method:'OPTIONS',headers:{Origin:'https://unapproved.example.test','Access-Control-Request-Method':'GET'}});
  const anonymous = await fetch(url);
  if (approved.status !== 200 || approved.headers.get('access-control-allow-origin') !== 'http://localhost:5173'
      || approved.headers.get('access-control-allow-credentials') !== 'true' || denied.status !== 403
      || denied.headers.has('access-control-allow-origin') || anonymous.status !== 401
      || anonymous.headers.get('x-content-type-options') !== 'nosniff'
      || anonymous.headers.get('x-frame-options') !== 'DENY' || !anonymous.headers.has('cache-control')) {
    throw new Error('Live CORS/security-header check failed.');
  }
  console.log('Live CORS allow/deny and security-header checks passed. HTTPS deployment is not claimed.');
}
async function run() {
  checkCoverage();
  if (process.argv.includes('--audit-only')) return;
  if (!/^jdbc:mysql:\/\/(127\.0\.0\.1|localhost):3306\/rockey_hospitality_hardening(?:\?.*)?$/.test(process.env.ROCKEY_DB_URL || '')) {
    throw new Error('Refusing non-disposable database configuration.');
  }
  if (!process.env.ROCKEY_DB_USERNAME || !process.env.ROCKEY_DB_PASSWORD || !process.env.JAVA_HOME) throw new Error('Required private environment configuration is missing.');
  if (!await portIsFree()) throw new Error('Hardening port already in use; no existing process was changed.');
  const fixture = JSON.parse(fs.readFileSync(path.join(output, 'fixtures.json'), 'utf8'));
  const key = crypto.randomBytes(32);
  const values = Object.entries({...fixture,expiredAccessToken:expiredToken(key)})
    .map(([name,value]) => ({key:name,value:String(value),enabled:true,type:'secret'}));
  fs.writeFileSync(envPath, JSON.stringify({name:'Disposable hardening only',values}));
  const jar = path.join(root, 'target', 'rockey-hospitality-0.0.1-SNAPSHOT.jar');
  const startup = fs.createWriteStream(path.join(output, 'startup.log'));
  application = spawn(path.join(process.env.JAVA_HOME, 'bin', 'java.exe'),
    ['-jar',jar,'--server.port=18081','--server.address=127.0.0.1','--rockey.alerts.scan-delay-ms=3600000'],
    {cwd:root,windowsHide:true,env:{...process.env,ROCKEY_JWT_SECRET_BASE64:key.toString('base64')}});
  application.stdout.pipe(startup); application.stderr.pipe(startup);
  await ready();
  await liveSecurityHeaders();
  console.log('Packaged Java17 application ready against disposable MySQL.');
  const cli = fs.createWriteStream(path.join(output, 'newman-cli.log'));
  const args = ['--yes','newman','run','postman/Rockey-Hardening.postman_collection.json','-e','target/hardening/newman-environment.json',
    '--reporters','cli,json','--reporter-cli-no-console','--reporter-cli-no-assertions','--reporter-cli-no-failures',
    '--reporter-json-export','target/hardening/newman-raw.json'];
  const newman = spawn('npx.cmd',args,{cwd:root,windowsHide:true,shell:true});
  newman.stdout.pipe(cli); newman.stderr.pipe(cli);
  const code = await new Promise((resolve,reject) => {newman.once('exit',resolve);newman.once('error',reject);});
  const report = JSON.parse(fs.readFileSync(rawReport,'utf8'));
  const summary = {stats:report.run.stats,failures:report.run.failures.map(failure => ({
    test:failure.error.test,kind:failure.error.name,request:failure.source?.name})),
    executions:report.run.executions.map(execution => ({name:execution.item.name,
      method:execution.request.method,status:execution.response?.code}))};
  fs.writeFileSync(path.join(output,'api-results.json'),JSON.stringify(summary,null,2));
  console.log(JSON.stringify({requests:summary.stats.requests,assertions:summary.stats.assertions,failures:summary.failures}));
  if (code !== 0 || summary.failures.length) throw new Error('Live API hardening failed; sanitized request/test names are in api-results.json.');
  passed = true;
}
run().catch(error => {console.error(error.message);process.exitCode=1;}).finally(async () => {
  if (application && application.exitCode === null) {
    application.kill();
    await new Promise(resolve => {application.once('exit',resolve);setTimeout(resolve,5000).unref();});
  }
  for (const file of [envPath,rawReport,...(passed ? [path.join(output,'fixtures.json')] : [])]) {
    if (fs.existsSync(file)) fs.unlinkSync(file);
  }
});
