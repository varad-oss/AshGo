import { test, before, after, beforeEach } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { ref, get, set, push } from 'firebase/database';
import { makeEnv, user, anon, seed } from './helpers.mjs';

let env;
before(async () => { env = await makeEnv(); });
after(async () => { await env.cleanup(); });
beforeEach(async () => { await env.clearDatabase(); });

test('legacy chat readable and writable by a signed-in v1 client', async () => {
  const db = user(env, 'v1phone');
  await assertSucceeds(push(ref(db, 'chat'), { text: 'hi', isFromTraveler: true, timestamp: 1 }));
  await assertSucceeds(get(ref(db, 'chat')));
  for (const path of ['tracking/current_trip', 'alerts', 'buzz/traveler', 'users/traveler']) {
    await assertSucceeds(get(ref(db, path)));
  }
  await assertSucceeds(set(ref(db, 'tracking/current_trip'), { latitude: 1, longitude: 2, timestamp: 3 }));
});

test('unauthenticated client cannot read or write legacy paths', async () => {
  const db = anon(env);
  for (const path of ['chat', 'tracking', 'alerts', 'buzz', 'users', 'app_config', 'notifications']) {
    await assertFails(get(ref(db, path)));
  }
  await assertFails(set(ref(db, 'chat/x'), { text: 'spam' }));
});

test('nobody can write notifications', async () => {
  await assertFails(set(ref(anon(env), 'notifications/traveler'), { title: 'x', message: 'y', timestamp: 1 }));
  await assertFails(set(ref(user(env, 'v1phone'), 'notifications/traveler'), { title: 'x', message: 'y', timestamp: 1 }));
  await assertSucceeds(get(ref(user(env, 'v1phone'), 'notifications/traveler')));
});

test('clients cannot write app_config but can read it', async () => {
  await seed(env, 'app_config', { latest_version_code: 45 });
  const db = user(env, 'v1phone');
  await assertSucceeds(get(ref(db, 'app_config')));
  await assertFails(set(ref(db, 'app_config/latest_version_code'), 999));
});

test('root read is denied so pair data never leaks through a cascade', async () => {
  await assertFails(get(ref(user(env, 'v1phone'), '/')));
  await assertFails(get(ref(user(env, 'v1phone'), 'pairs')));
});
