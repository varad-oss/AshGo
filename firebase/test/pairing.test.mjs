import { test, before, after, beforeEach } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { ref, get, set, remove, serverTimestamp } from 'firebase/database';
import { makeEnv, user, seed, emailKey, DAY } from './helpers.mjs';

let env;
before(async () => { env = await makeEnv(); });
after(async () => { await env.cleanup(); });
beforeEach(async () => { await env.clearDatabase(); });

const P = 'pair1';
const A = 'alice', B = 'bob', C = 'carol';
const B_EMAIL = 'Bob.Smith@example.com';

const meta = (uid) => ({ memberA: uid, relationship: 'PARTNER', createdAt: serverTimestamp() });
const createPair = (db, uid = A, pairId = P) => set(ref(db, `pairs/${pairId}/meta`), meta(uid));
const invite = (db, email = B_EMAIL, { pairId = P, from = A, expiresIn = DAY - 60_000 } = {}) =>
  set(ref(db, `invites/${emailKey(email)}`), {
    pairId, fromUid: from, fromName: 'Alice', createdAt: serverTimestamp(), expiresAt: Date.now() + expiresIn,
  });
const join = (db, pairId = P, uid = B) => set(ref(db, `pairs/${pairId}/meta/memberB`), uid);

async function invitedPair() {
  const a = user(env, A);
  await assertSucceeds(createPair(a));
  await assertSucceeds(invite(a));
  return a;
}

async function fullPair() {
  const a = await invitedPair();
  const b = user(env, B, B_EMAIL);
  await assertSucceeds(join(b));
  return { a, b };
}

test('create pair as memberA', async () => {
  await assertSucceeds(createPair(user(env, A)));
});

test('cannot create a pair naming someone else as memberA', async () => {
  await assertFails(createPair(user(env, A), C));
});

test('cannot create a pair that already exists', async () => {
  await assertSucceeds(createPair(user(env, A)));
  await assertFails(createPair(user(env, C), C));
});

test('inviter writes an invite that expires within 24 h', async () => {
  await invitedPair();
});

test('invite with expiry beyond 24 h is rejected', async () => {
  const a = user(env, A);
  await assertSucceeds(createPair(a));
  await assertFails(invite(a, B_EMAIL, { expiresIn: DAY + 60_000 }));
});

test('only memberA of the pair can invite for it', async () => {
  const a = user(env, A);
  await assertSucceeds(createPair(a));
  await assertFails(invite(user(env, C), B_EMAIL, { from: C }));
});

test('invitee with matching verified email reads the invite; a different email cannot', async () => {
  await invitedPair();
  await assertSucceeds(get(ref(user(env, B, B_EMAIL), `invites/${emailKey(B_EMAIL)}`)));
  await assertFails(get(ref(user(env, C, 'carol@example.com'), `invites/${emailKey(B_EMAIL)}`)));
});

test('unverified email cannot join', async () => {
  await invitedPair();
  await assertFails(join(user(env, B, B_EMAIL, false)));
});

test('expired invite cannot join', async () => {
  const a = user(env, A);
  await assertSucceeds(createPair(a));
  await seed(env, `invites/${emailKey(B_EMAIL)}`, {
    pairId: P, fromUid: A, fromName: 'Alice', createdAt: Date.now() - 2 * DAY, expiresAt: Date.now() - DAY,
  });
  await assertFails(join(user(env, B, B_EMAIL)));
});

test('matching invite joins as memberB', async () => {
  await fullPair();
});

test('third account cannot join a full pair', async () => {
  await fullPair();
  await assertFails(join(user(env, C, B_EMAIL), P, C));
});

test('memberA cannot join their own pair as memberB', async () => {
  const a = user(env, A, B_EMAIL);
  await assertSucceeds(createPair(a));
  await assertSucceeds(invite(a));
  await assertFails(join(a, P, A));
});

test('cannot join a pair the invite does not point to', async () => {
  await invitedPair();
  await assertSucceeds(createPair(user(env, C), C, 'pair2'));
  await assertFails(join(user(env, B, B_EMAIL), 'pair2'));
});

test('userPairs can only point to a pair you belong to', async () => {
  const { a, b } = await fullPair();
  await assertSucceeds(set(ref(a, `userPairs/${A}`), P));
  await assertSucceeds(set(ref(b, `userPairs/${B}`), P));
  await assertFails(set(ref(user(env, C), `userPairs/${C}`), P));
  await assertFails(set(ref(a, `userPairs/${B}`), P));
  await assertFails(get(ref(a, `userPairs/${B}`)));
});

test('outsider cannot read the pair; members can', async () => {
  const { a, b } = await fullPair();
  await assertSucceeds(get(ref(a, `pairs/${P}`)));
  await assertSucceeds(get(ref(b, `pairs/${P}`)));
  await assertFails(get(ref(user(env, C), `pairs/${P}`)));
});

test('memberA confirms the pair; memberB cannot', async () => {
  const { a, b } = await fullPair();
  await assertFails(set(ref(b, `pairs/${P}/meta/confirmedAt`), serverTimestamp()));
  await assertSucceeds(set(ref(a, `pairs/${P}/meta/confirmedAt`), serverTimestamp()));
  await assertFails(set(ref(a, `pairs/${P}/meta/confirmedAt`), serverTimestamp()));
});

test('members cannot rewrite who the members are', async () => {
  const { a, b } = await fullPair();
  await assertFails(set(ref(a, `pairs/${P}/meta/memberB`), C));
  await assertFails(set(ref(b, `pairs/${P}/meta/memberA`), B));
});

test('either member can delete the whole pair (Leave); an outsider cannot', async () => {
  const { a } = await fullPair();
  await assertFails(remove(ref(user(env, C), `pairs/${P}`)));
  await assertSucceeds(remove(ref(a, `pairs/${P}`)));
});

test('invitee or inviter can delete the invite; others cannot', async () => {
  await invitedPair();
  await assertFails(remove(ref(user(env, C, 'carol@example.com'), `invites/${emailKey(B_EMAIL)}`)));
  await assertSucceeds(remove(ref(user(env, B, B_EMAIL), `invites/${emailKey(B_EMAIL)}`)));
});
