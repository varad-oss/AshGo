import { test, before, after, beforeEach } from 'node:test';
import { assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { ref, get, set, update, remove, serverTimestamp } from 'firebase/database';
import { makeEnv, user, seed, DAY } from './helpers.mjs';

let env, a, b, c;
const P = 'pair1', A = 'alice', B = 'bob', C = 'carol';
const pp = (path) => `pairs/${P}/${path}`;

before(async () => { env = await makeEnv(); });
after(async () => { await env.cleanup(); });
beforeEach(async () => {
  await env.clearDatabase();
  await seed(env, pp('meta'), { memberA: A, memberB: B, relationship: 'PARTNER', createdAt: Date.now() - DAY, confirmedAt: Date.now() - DAY });
  a = user(env, A); b = user(env, B); c = user(env, C);
});

const now = serverTimestamp;

// ---- profiles / presence / location / trips: own node only ----
test('member writes own profile, presence and location; not the partner\'s; outsider nothing', async () => {
  await assertSucceeds(set(ref(a, pp(`profiles/${A}`)), { name: 'Alice', greeting: true }));
  await assertSucceeds(set(ref(a, pp(`presence/${A}`)), { online: true, lastSeen: now(), battery: 64, charging: false, sharing: 'live' }));
  await assertSucceeds(set(ref(a, pp(`location/${A}`)), { lat: 18.52, lng: 73.85, acc: 12, at: now() }));
  await assertFails(set(ref(a, pp(`profiles/${B}`)), { name: 'Bob' }));
  await assertFails(set(ref(a, pp(`location/${B}`)), { lat: 1, lng: 1, at: now() }));
  await assertFails(set(ref(c, pp(`profiles/${C}`)), { name: 'Carol' }));
  await assertSucceeds(get(ref(b, pp(`location/${A}`))));
});

test('profile email must be the member\'s own verified email', async () => {
  await assertFails(set(ref(a, pp(`profiles/${A}`)), { name: 'Alice', email: 'bob@example.com' }));
  await assertSucceeds(set(ref(a, pp(`profiles/${A}`)), { name: 'Alice', email: 'alice@example.com' }));
});

test('objects cannot be replaced by strings (presence, status, dest)', async () => {
  await assertFails(set(ref(a, pp(`presence/${A}`)), 'x'.repeat(1000)));
  await assertFails(set(ref(a, pp(`presence/${A}/status`)), 'x'.repeat(1000)));
  await assertFails(set(ref(a, pp(`trips/${A}`)), { state: 'active', startedAt: now(), notifyPartner: true, dest: 'x' }));
});

test('receipt maps must be maps, on events and SOS', async () => {
  await assertFails(set(ref(a, pp('events/e1')), { from: A, type: 'checkin', kind: 'k', at: now(), deliveredTo: 'x' }));
  await assertFails(set(ref(a, pp('events/e2')), { from: A, type: 'checkin', kind: 'k', at: now(), seenBy: true }));
  await assertFails(set(ref(a, pp('sos/s1')), { from: A, state: 'active', at: now(), ackBy: true }));
  await assertFails(set(ref(a, pp('sos/s2')), { from: A, state: 'active', at: now(), deliveredTo: 'x' }));
});

test('profile limits: name 1–40 chars, avatar ≤ 41 000 chars', async () => {
  await assertFails(set(ref(a, pp(`profiles/${A}`)), { name: '' }));
  await assertFails(set(ref(a, pp(`profiles/${A}`)), { name: 'x'.repeat(41) }));
  await assertFails(set(ref(a, pp(`profiles/${A}`)), { name: 'Alice', avatar: 'x'.repeat(41_001) }));
  await assertSucceeds(set(ref(a, pp(`profiles/${A}`)), { name: 'Alice', avatar: 'x'.repeat(41_000) }));
});

test('location out of range rejected', async () => {
  await assertFails(set(ref(a, pp(`location/${A}`)), { lat: 91, lng: 0, at: now() }));
  await assertFails(set(ref(a, pp(`location/${A}`)), { lat: 0, lng: -181, at: now() }));
  await assertFails(set(ref(a, pp(`location/${A}`)), { lat: 0, lng: 0, acc: -1, at: now() }));
});

test('client-chosen timestamp rejected', async () => {
  await assertFails(set(ref(a, pp(`location/${A}`)), { lat: 0, lng: 0, at: Date.now() - 60_000 }));
  await assertFails(set(ref(a, pp('chat/m1')), { from: A, text: 'hi', at: Date.now() + 60_000 }));
  await assertFails(set(ref(a, pp(`presence/${A}`)), { sharing: 'live', lastSeen: Date.now() + DAY }));
});

test('presence sharing must be live, paused or off', async () => {
  await assertFails(set(ref(a, pp(`presence/${A}`)), { sharing: 'spying' }));
});

test('trip: own only, state enum, note ≤ 80', async () => {
  const trip = { state: 'active', startedAt: now(), notifyPartner: true, expectBy: Date.now() + 3_600_000, note: 'Cab KA-01' };
  await assertSucceeds(set(ref(a, pp(`trips/${A}`)), trip));
  await assertFails(set(ref(b, pp(`trips/${A}`)), trip));
  await assertFails(set(ref(a, pp(`trips/${A}`)), { ...trip, state: 'teleported' }));
  await assertFails(set(ref(a, pp(`trips/${A}`)), { ...trip, note: 'x'.repeat(81) }));
});

// ---- chat / media ----
test('chat with forged sender rejected; own message accepted', async () => {
  await assertFails(set(ref(a, pp('chat/m1')), { from: B, text: 'hi', at: now() }));
  await assertSucceeds(set(ref(a, pp('chat/m1')), { from: A, text: 'hi', at: now() }));
});

test('chat is immutable once written', async () => {
  await assertSucceeds(set(ref(a, pp('chat/m1')), { from: A, text: 'hi', at: now() }));
  await assertFails(set(ref(a, pp('chat/m1/text')), 'edited'));
  await assertFails(set(ref(b, pp('chat/m1')), { from: B, text: 'overwrite', at: now() }));
});

test('chat text ≤ 2000 chars', async () => {
  await assertFails(set(ref(a, pp('chat/m1')), { from: A, text: 'x'.repeat(2001), at: now() }));
});

test('sender may delete own chat; partner only after 7 days', async () => {
  await assertSucceeds(set(ref(a, pp('chat/fresh')), { from: A, text: 'hi', at: now() }));
  await seed(env, pp('chat/old'), { from: A, text: 'old', at: Date.now() - 8 * DAY });
  await assertFails(remove(ref(b, pp('chat/fresh'))));
  await assertSucceeds(remove(ref(b, pp('chat/old'))));
  await assertSucceeds(remove(ref(a, pp('chat/fresh'))));
});

test('media over 205 000 chars rejected; within limit accepted', async () => {
  await assertFails(set(ref(a, pp('media/x1')), { from: A, data: 'x'.repeat(205_001), w: 10, h: 10, at: now() }));
  await assertSucceeds(set(ref(a, pp('media/x2')), { from: A, data: 'x'.repeat(205_000), w: 10, h: 10, at: now() }));
});

// ---- events (check-ins, nudges) ----
test('partner sets own delivered/seen receipts; sender cannot set its own', async () => {
  await assertSucceeds(set(ref(a, pp('events/e1')), { from: A, type: 'checkin', kind: 'home_safe', at: now() }));
  await assertSucceeds(set(ref(b, pp(`events/e1/deliveredTo/${B}`)), now()));
  await assertSucceeds(set(ref(b, pp(`events/e1/seenBy/${B}`)), now()));
  await assertFails(set(ref(a, pp(`events/e1/seenBy/${A}`)), now()));
  await assertFails(set(ref(b, pp(`events/e1/seenBy/${A}`)), now()));
  await assertFails(set(ref(a, pp('events/e2')), { from: A, type: 'checkin', kind: 'x', at: now(), seenBy: { [B]: now() } }));
  await assertFails(set(ref(a, pp('events/e3')), { from: A, type: 'checkin', kind: 'x', at: now(), deliveredTo: { [B]: now() } }));
});

test('event type must be checkin or nudge', async () => {
  await assertFails(set(ref(a, pp('events/e1')), { from: A, type: 'location_request', kind: 'x', at: now() }));
});

// ---- SOS ----
const raise = (db, uid, id = 's1') => set(ref(db, pp(`sos/${id}`)), { from: uid, state: 'active', at: now(), lat: 18.5, lng: 73.8, locAt: Date.now() - 60_000 });

test('member raises SOS; outsider cannot', async () => {
  await assertSucceeds(raise(a, A));
  await assertFails(raise(c, C, 's2'));
});

test('SOS cannot be created already cancelled or pre-acknowledged', async () => {
  await assertFails(set(ref(a, pp('sos/s2')), { from: A, state: 'active', at: now(), deliveredTo: { [B]: now() } }));
  await assertFails(set(ref(a, pp('sos/s1')), { from: A, state: 'cancelled', at: now(), cancelledAt: now() }));
  await assertFails(set(ref(a, pp('sos/s1')), { from: A, state: 'active', at: now(), ackBy: { [B]: now() } }));
});

test('partner cannot cancel; sender cancels with server time', async () => {
  await assertSucceeds(raise(a, A));
  await assertFails(update(ref(b, pp('sos/s1')), { state: 'cancelled', cancelledAt: now() }));
  await assertFails(update(ref(a, pp('sos/s1')), { state: 'cancelled', cancelledAt: Date.now() - 1000 }));
  await assertSucceeds(update(ref(a, pp('sos/s1')), { state: 'cancelled', cancelledAt: now() }));
  await assertFails(update(ref(a, pp('sos/s1')), { state: 'active' }));
});

test('partner acks with server time; sender cannot ack own SOS', async () => {
  await assertSucceeds(raise(a, A));
  await assertSucceeds(set(ref(b, pp(`sos/s1/deliveredTo/${B}`)), now()));
  await assertSucceeds(set(ref(b, pp(`sos/s1/ackBy/${B}`)), now()));
  await assertFails(set(ref(a, pp(`sos/s1/ackBy/${A}`)), now()));
  await assertFails(set(ref(b, pp(`sos/s1/ackBy/${A}`)), now()));
});

test('SOS location time tolerates a few minutes of phone-clock skew, not more', async () => {
  await assertSucceeds(raise(a, A));
  await assertSucceeds(update(ref(a, pp('sos/s1')), { lat: 1, lng: 2, locAt: Date.now() + 60_000 }));
  await assertFails(update(ref(a, pp('sos/s1')), { lat: 1, lng: 2, locAt: Date.now() + 10 * 60_000 }));
});

test('only the sender updates SMS counts and location', async () => {
  await assertSucceeds(raise(a, A));
  await assertSucceeds(update(ref(a, pp('sos/s1')), { smsSent: 2, smsFailed: 1 }));
  await assertFails(set(ref(b, pp('sos/s1/smsSent')), 0));
  await assertFails(set(ref(b, pp('sos/s1/lat')), 0));
});

test('active SOS cannot be deleted, even by its sender', async () => {
  await assertSucceeds(raise(a, A));
  await assertFails(remove(ref(a, pp('sos/s1'))));
  await assertFails(remove(ref(b, pp('sos/s1'))));
});

test('cancelled SOS deletable by sender only after 30 days', async () => {
  await seed(env, pp('sos/old'), { from: A, state: 'cancelled', at: Date.now() - 40 * DAY, cancelledAt: Date.now() - 31 * DAY });
  await seed(env, pp('sos/recent'), { from: A, state: 'cancelled', at: Date.now() - 10 * DAY, cancelledAt: Date.now() - 9 * DAY });
  await assertFails(remove(ref(a, pp('sos/recent'))));
  await assertFails(remove(ref(b, pp('sos/old'))));
  await assertSucceeds(remove(ref(a, pp('sos/old'))));
});

test('unknown child fields rejected', async () => {
  await assertFails(set(ref(a, pp('chat/m1')), { from: A, text: 'hi', at: now(), extra: 1 }));
  await assertFails(set(ref(a, pp(`location/${A}`)), { lat: 0, lng: 0, at: now(), speed: 3 }));
  await assertFails(set(ref(a, pp('sos/s9')), { from: A, state: 'active', at: now(), note: 'x' }));
  await assertFails(set(ref(a, pp('unknownNode/x')), 1));
});
