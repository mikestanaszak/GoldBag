import assert from 'node:assert/strict';
import { test } from 'node:test';
import { analyzeCommits } from '@semantic-release/commit-analyzer';

const logger = { log() {} };
for (const [message, expected] of [
  ['fix: preserve account balance', 'patch'],
  ['feat: support Minecraft 26.3', 'minor'],
  ['feat: replace legacy storage\n\nBREAKING CHANGE: legacy databases require migration', 'major'],
  ['docs: clarify installation', null],
]) {
  test(`${message.split('\n')[0]} selects ${expected ?? 'no release'}`, async () => {
    assert.equal(await analyzeCommits({}, { commits: [{ hash: 'test', message }], logger }), expected);
  });
}
test('breaking rebuild takes precedence over later features and fixes', async () => {
  const commits = [
    'feat: replace legacy storage\n\nBREAKING CHANGE: legacy databases require migration',
    'feat: support Minecraft 26.3',
    'fix: preserve account balance',
  ].map(message => ({ hash: 'test', message }));
  assert.equal(await analyzeCommits({}, { commits, logger }), 'major');
});
