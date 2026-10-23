// The two rule stages must differ only in the legacy v1 paths, and a plain `firebase deploy` must never ship the
// phase-1 file again (it would reopen the v1 paths to every signed-in account). No emulator needed.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';

const load = (file) => JSON.parse(readFileSync(new URL(`../${file}`, import.meta.url), 'utf8'));
const phase1 = load('database.rules.json').rules;
const phase2 = load('database.rules.phase2.json').rules;
const LEGACY = ['chat', 'tracking', 'alerts', 'buzz', 'users', 'notifications'];

test('v2 paths are identical in both stages, so the pair/role suites cover phase 2 too', () => {
  for (const path of ['pairs', 'invites', 'userPairs', 'app_config']) {
    assert.deepEqual(phase2[path], phase1[path], `${path} differs between the stages`);
  }
});

test('phase 2 has no legacy path and no root grant', () => {
  for (const path of LEGACY) assert.equal(phase2[path], undefined, `${path} still present in phase 2`);
  assert.equal(phase2['.read'], undefined);
  assert.equal(phase2['.write'], undefined);
  assert.deepEqual(Object.keys(phase2).sort(), ['app_config', 'invites', 'pairs', 'userPairs']);
});

test('firebase.json deploys the phase-2 rules', () => {
  assert.equal(load('firebase.json').database.rules, 'database.rules.phase2.json');
});
