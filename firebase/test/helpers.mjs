// Shared emulator setup for the rules tests. Uses the demo-ashgo project id, so the
// emulator never touches the real Firebase project.
import { readFileSync } from 'node:fs';
import { initializeTestEnvironment } from '@firebase/rules-unit-testing';

export const DAY = 86_400_000;

export async function makeEnv() {
  return initializeTestEnvironment({
    projectId: 'demo-ashgo',
    database: {
      host: '127.0.0.1',
      port: 9000,
      rules: readFileSync(new URL('../database.rules.json', import.meta.url), 'utf8'),
    },
  });
}

/** A Google-signed-in user: verified email unless told otherwise. */
export function user(env, uid, email = `${uid}@example.com`, verified = true) {
  return env.authenticatedContext(uid, { email, email_verified: verified, firebase: { sign_in_provider: 'google.com' } }).database();
}

export function anon(env) {
  return env.unauthenticatedContext().database();
}

/** Seed data bypassing rules (e.g. old timestamps the rules would reject). */
export async function seed(env, path, value) {
  await env.withSecurityRulesDisabled(async (ctx) => {
    const { ref, set } = await import('firebase/database');
    await set(ref(ctx.database(), path), value);
  });
}

export const emailKey = (email) => email.toLowerCase().replaceAll('.', ',');
