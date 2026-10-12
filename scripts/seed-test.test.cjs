const { test } = require('node:test');
const assert = require('node:assert/strict');
const { validate } = require('./seed-test.cjs');
// Shape-only strings, never executed or used as actual credentials.
const inputs = () => ({ ROCKEY_DB_URL: 'jdbc:mysql://127.0.0.1:3306/rockey_hospitality_hardening?useSSL=false',
  ROCKEY_SEED_WORKER_HASH: '$2b$12$' + '.'.repeat(53), ROCKEY_SEED_MANAGER_HASH: '$2b$12$' + '.'.repeat(53),
  ROCKEY_DB_USERNAME: 'synthetic', ROCKEY_DB_PASSWORD: 'non-executable-test-value' });
test('rejects demo, non-loopback and lookalike targets before any SQL can execute', () => {
  for (const url of ['jdbc:mysql://127.0.0.1:3306/rockey_hospitality_demo?x=1',
    'jdbc:mysql://remote:3306/rockey_hospitality_hardening?x=1',
    'jdbc:mysql://127.0.0.1:3306/rockey_hospitality_hardening_extra?x=1'])
    assert.throws(() => validate({ ...inputs(), ROCKEY_DB_URL: url }));
});
test('requires private hashes and credentials and rejects injection-shaped input', () => {
  for (const key of ['ROCKEY_SEED_WORKER_HASH', 'ROCKEY_SEED_MANAGER_HASH', 'ROCKEY_DB_USERNAME', 'ROCKEY_DB_PASSWORD'])
    assert.throws(() => validate({ ...inputs(), [key]: '' }));
  assert.throws(() => validate({ ...inputs(), ROCKEY_SEED_WORKER_HASH: "';COMMIT;" }));
});
test('validates input shape without opening a database connection', () => { assert.doesNotThrow(() => validate(inputs())); });
