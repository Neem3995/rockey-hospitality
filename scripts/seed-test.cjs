const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

function validate(env) {
  if (!/^jdbc:mysql:\/\/127\.0\.0\.1:3306\/rockey_hospitality_hardening\?[^#]*$/.test(env.ROCKEY_DB_URL || ''))
    throw new Error('Exact loopback disposable hardening URL required.');
  for (const key of ['ROCKEY_SEED_WORKER_HASH', 'ROCKEY_SEED_MANAGER_HASH']) {
    if (!/^\$2[aby]\$1[0-6]\$[./A-Za-z0-9]{53}$/.test(env[key] || ''))
      throw new Error('Private runtime BCrypt hashes with cost 10-16 required.');
  }
  if (!env.ROCKEY_DB_USERNAME || !env.ROCKEY_DB_PASSWORD) throw new Error('Private disposable account configuration required.');
}

if (require.main === module) {
  try {
    validate(process.env);
    if (process.argv.includes('--dry-run')) {
      console.log('Seed input safety gates passed. No database connection or writes performed.');
    } else {
      const sql = fs.readFileSync(path.join(__dirname, '../database/seed-test.sql'), 'utf8');
      const input = "SET @seed_worker_hash='" + process.env.ROCKEY_SEED_WORKER_HASH + "';\n"
        + "SET @seed_manager_hash='" + process.env.ROCKEY_SEED_MANAGER_HASH + "';\n" + sql;
      const result = spawnSync(process.env.ROCKEY_MYSQL_PATH || 'mysql',
        ['--batch', '--skip-column-names', '--host=127.0.0.1', '--port=3306',
          '--database=rockey_hospitality_hardening', '--user=' + process.env.ROCKEY_DB_USERNAME],
        { input, env: { ...process.env, MYSQL_PWD: process.env.ROCKEY_DB_PASSWORD }, encoding: 'utf8' });
      // Never export raw diagnostics or inputs; a first SQL error terminates batch/connection and rolls back.
      if (result.status !== 0 || result.error) throw new Error('Seed execution failed; no raw credential-bearing diagnostics exported.');
      console.log('Disposable seed completed: 2 users, 3 rooms, 3 tasks, 2 inspections; no ADMIN.');
    }
  } catch (error) { console.error(error.message); process.exitCode = 1; }
}
module.exports = { validate };
