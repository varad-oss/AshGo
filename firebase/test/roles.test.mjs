// Roles (owner's design): whoever creates the orbit (memberA) only watches; whoever joins (memberB) shares
// location, takes trips and raises SOS. Plus the shared photo wall, which both can add to and take down.
import { test, before, after, beforeEach } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { ref, get, set, update, remove, serverTimestamp } from 'firebase/database';
import { makeEnv, user, seed, DAY } from './helpers.mjs';

let env, guardian, traveller, outsider;
const P = 'pair1', A = 'alice', B = 'bob', C = 'carol';
const pp = (path) => `pairs/${P}/${path}`;
const now = serverTimestamp;

before(async () => { env = await makeEnv(); });
after(async () => { await env.cleanup(); });
beforeEach(async () => {
  await env.clearDatabase();
  await seed(env, pp('meta'), { memberA: A, memberB: B, relationship: 'PARTNER', createdAt: Date.now() - DAY, confirmedAt: Date.now() - DAY });
  guardian = user(env, A); traveller = user(env, B); outsider = user(env, C);
});

test('only the one who joined shares location; the creator may still clear their own', async () => {
  await assertSucceeds(set(ref(traveller, pp(`location/${B}`)), { lat: 18.5, lng: 73.8, at: now() }));
  await assertFails(set(ref(guardian, pp(`location/${A}`)), { lat: 18.5, lng: 73.8, at: now() }));
  await seed(env, pp(`location/${A}`), { lat: 1, lng: 2, at: 1 });
  await assertSucceeds(remove(ref(guardian, pp(`location/${A}`))));
});

test('only the one who joined starts trips', async () => {
  const trip = { state: 'active', startedAt: now(), notifyPartner: true };
  await assertSucceeds(set(ref(traveller, pp(`trips/${B}`)), trip));
  await assertFails(set(ref(guardian, pp(`trips/${A}`)), trip));
});

test('only the one who joined raises SOS; the creator can still acknowledge it', async () => {
  await assertFails(set(ref(guardian, pp('sos/s0')), { from: A, state: 'active', at: now() }));
  await assertSucceeds(set(ref(traveller, pp('sos/s1')), { from: B, state: 'active', at: now() }));
  await assertSucceeds(set(ref(guardian, pp(`sos/s1/ackBy/${A}`)), now()));
});

test('the wall: either member adds a photo; the data and its entry go together; outsiders see nothing', async () => {
  const add = (db, who, id) => update(ref(db, pp('')), {
    [`wall/${id}`]: { from: who, at: now(), w: 800, h: 600 },
    [`wallData/${id}`]: 'aGVsbG8=',
  });
  await assertSucceeds(add(guardian, A, 'w1'));
  await assertSucceeds(add(traveller, B, 'w2'));
  await assertSucceeds(get(ref(traveller, pp('wall'))));
  await assertSucceeds(get(ref(guardian, pp('wallData/w2'))));
  await assertFails(get(ref(outsider, pp('wall'))));
  await assertFails(get(ref(outsider, pp('wallData/w1'))));
  await assertFails(add(outsider, C, 'w3'));
  await assertFails(add(guardian, B, 'w4')); // forged sender
});

test('the wall: either member takes any photo down; nobody edits one in place; size is capped', async () => {
  await seed(env, pp('wall/w1'), { from: B, at: 1, w: 1, h: 1 });
  await seed(env, pp('wallData/w1'), 'aGVsbG8=');
  await assertFails(set(ref(guardian, pp('wallData/w1')), 'b3RoZXI='));
  await assertSucceeds(update(ref(guardian, pp('')), { 'wall/w1': null, 'wallData/w1': null }));
  await assertFails(update(ref(traveller, pp('')), {
    'wall/w9': { from: B, at: now(), w: 1, h: 1 },
    'wallData/w9': 'x'.repeat(210_001),
  }));
});
