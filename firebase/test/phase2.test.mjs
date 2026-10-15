// Rules phase 2 (deploy only after both phones run v2): every legacy v1 path is closed; v2 and app_config stay.
import { test, before, after, beforeEach } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { ref, get, set, push } from 'firebase/database';
import { makeEnv, user } from './helpers.mjs';

let env;
before(async () => { env = await makeEnv('database.rules.phase2.json'); });
after(async () => { await env.cleanup(); });
beforeEach(async () => { await env.clearDatabase(); });

test('phase 2: signed-in clients can neither read nor write any legacy path', async () => {
  const db = user(env, 'v1phone');
  for (const path of ['chat', 'tracking', 'tracking/current_trip', 'alerts', 'buzz/traveler', 'users/traveler', 'notifications']) {
    await assertFails(get(ref(db, path)));
    await assertFails(set(ref(db, path), { x: 1 }));
  }
  await assertFails(push(ref(db, 'chat'), { text: 'hi', isFromTraveler: true, timestamp: 1 }));
});

test('phase 2: app_config stays readable (updater) and never client-writable', async () => {
  const db = user(env, 'alice');
  await assertSucceeds(get(ref(db, 'app_config')));
  await assertFails(set(ref(db, 'app_config/latest_version_code'), 99));
});

test('phase 2: v2 paths are unchanged (own userPairs readable, others not)', async () => {
  await env.withSecurityRulesDisabled(async (ctx) => { await set(ref(ctx.database(), 'userPairs/alice'), 'p1'); });
  await assertSucceeds(get(ref(user(env, 'alice'), 'userPairs/alice')));
  await assertFails(get(ref(user(env, 'mallory'), 'userPairs/alice')));
});
