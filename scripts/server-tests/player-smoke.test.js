'use strict';

const assert = require('node:assert/strict');
const { EventEmitter } = require('node:events');
const test = require('node:test');
const { closeBots, connect, findNote, noteIdentity } = require('./player-smoke');

function fakeBot() {
  const bot = new EventEmitter();
  bot.quitCalls = 0;
  bot.endCalls = 0;
  bot.quit = () => { bot.quitCalls += 1; };
  bot.end = () => { bot.endCalls += 1; };
  return bot;
}

test('connect cleans a client that times out before spawn', async () => {
  const bot = fakeBot();
  await assert.rejects(connect('timeout-bot', () => bot, 10), /Timed out/);
  assert.equal(bot.quitCalls, 1);
});

test('connect cleans a client kicked before spawn', async () => {
  const bot = fakeBot();
  setTimeout(() => bot.emit('kicked', 'test kick'), 2);
  await assert.rejects(connect('kicked-bot', () => bot, 100), /kicked/);
  assert.equal(bot.quitCalls, 1);
});

test('closeBots isolates one client cleanup exception', () => {
  const first = fakeBot();
  const second = fakeBot();
  first.quit = () => { throw new Error('synthetic quit failure'); };
  closeBots([first, second]);
  assert.equal(first.endCalls, 1);
  assert.equal(second.quitCalls, 1);
});

test('findNote selects the exact copied identity over stale paper', () => {
  const identity = '123e4567-e89b-12d3-a456-426614174000';
  const stale = { name: 'paper', count: 1, components: [{ type: 'custom_name', data: { text: 'stale' } }] };
  const copied = { name: 'paper', count: 1, components: [{ type: 'custom_data', data: { noteId: identity } }] };
  const bot = { inventory: { items: () => [stale, copied] } };
  assert.equal(noteIdentity(copied), identity);
  assert.equal(findNote(bot, identity), copied);
});

test('connect resolves spawned clients for normal cleanup', async () => {
  const bot = fakeBot();
  setTimeout(() => bot.emit('spawn'), 2);
  const connected = await connect('spawned-bot', () => bot, 100);
  assert.equal(connected, bot);
  closeBots([connected]);
  assert.equal(bot.quitCalls, 1);
});
