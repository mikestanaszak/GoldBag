'use strict';

const assert = require('node:assert/strict');
const { EventEmitter } = require('node:events');
const test = require('node:test');
const {
  assertDepositLayout,
  assertSameWindow,
  assertWithdrawLayout,
  closeBots,
  connect,
  count,
  findNote,
  findWindowItem,
  inventoryItems,
  installCursorPacketAdapter,
  mainInventoryEmptySlots,
  noteIdentity,
  queueWithdrawalClicks,
  stagedCount,
  stagedItems,
} = require('./player-smoke');

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

test('open chest inventory state is preferred over stale player inventory state', () => {
  const slots = Array(90).fill(null);
  const windowItem = { name: 'raw_gold', count: 1 };
  slots[54] = windowItem;
  const bot = {
    currentWindow: { slots, inventoryStart: 54, inventoryEnd: 90 },
    inventory: {
      items: () => [{ name: 'raw_gold', count: 2 }],
      slots: Array(45).fill(null),
    },
  };
  assert.deepEqual(inventoryItems(bot), [windowItem]);
  assert.equal(count(bot, 'raw_gold'), 1);
  assert.equal(mainInventoryEmptySlots(bot), 35);
  bot.currentWindow = null;
  assert.equal(count(bot, 'raw_gold'), 2);
});

test('window item lookup can select custom metadata over a plain stack', () => {
  const plain = { name: 'raw_iron', count: 1 };
  const custom = { name: 'raw_iron', count: 1, components: [{ type: 'custom_name', data: { text: 'Forged' } }] };
  const slots = Array(90).fill(null);
  slots[54] = plain;
  slots[55] = custom;
  const window = { slots, inventoryStart: 54, inventoryEnd: 90 };
  assert.equal(findWindowItem(window, 'raw_iron'), 54);
  assert.equal(findWindowItem(window, 'raw_iron', item => /forged/i.test(JSON.stringify(item.components))), 55);
});

test('cursor packet adapter restores and clears the selected item', () => {
  const client = new EventEmitter();
  const window = { selectedItem: { name: 'stale' } };
  const bot = { _client: client, currentWindow: window, inventory: { selectedItem: null } };
  assert.equal(installCursorPacketAdapter(bot, contents => contents && { name: contents.name }), true);
  client.emit('set_cursor_item', { contents: { name: 'raw_iron' } });
  assert.deepEqual(window.selectedItem, { name: 'raw_iron' });
  client.emit('set_cursor_item', { contents: null });
  assert.equal(window.selectedItem, null);
});

test('connect resolves spawned clients for normal cleanup', async () => {
  const bot = fakeBot();
  setTimeout(() => bot.emit('spawn'), 2);
  const connected = await connect('spawned-bot', () => bot, 100);
  assert.equal(connected, bot);
  closeBots([connected]);
  assert.equal(bot.quitCalls, 1);
});

test('deposit layout allows staged items and reserves batch controls', () => {
  const slots = Array(90).fill(null);
  slots[0] = { name: 'coal', count: 64 };
  slots[1] = { name: 'iron_ingot', count: 1, components: [{ type: 'custom_name', data: { text: 'Forged' } }] };
  for (const [slot, name] of [[45, 'Back to Main menu'], [48, 'Withdraw'], [49, 'Close'], [50, 'Balance and deposit instructions'], [52, 'Deposit all']]) {
    slots[slot] = { name: 'paper', components: [{ type: 'custom_name', data: { text: name } }] };
  }
  assert.doesNotThrow(() => assertDepositLayout({ slots, title: 'GoldBag Deposit 1' }));
  assert.equal(stagedCount({ slots }, 'coal'), 64);
  assert.equal(stagedCount({ slots }, 'iron_ingot'), 1);
  assert.equal(stagedItems({ slots }).length, 2);
});

test('withdraw layout reserves deposit, close, info and back controls', () => {
  const slots = Array(90).fill(null);
  for (const [slot, name] of [[48, 'Deposit'], [49, 'Close'], [50, 'Balance and withdraw instructions'], [51, 'Back to Main menu']]) {
    slots[slot] = { name: 'paper', components: [{ type: 'custom_name', data: { text: name } }] };
  }
  assert.doesNotThrow(() => assertWithdrawLayout({ slots, title: 'GoldBag Withdraw 1' }));
});

test('rapid withdrawal helper queues the requested bounded click count', async () => {
  const calls = [];
  const bot = {
    lines: [],
    currentWindow: { slots: [{ name: 'diamond', count: 1 }], title: 'GoldBag Withdraw 1' },
    clickWindow: (slot, mouseButton, mode) => {
      calls.push([slot, mouseButton, mode]);
      return Promise.resolve();
    },
  };
  const queued = queueWithdrawalClicks(bot, 'diamond', 3, 0, 0);
  await Promise.all(queued.pending);
  assert.equal(queued.pending.length, 3);
  assert.deepEqual(calls, [[0, 0, 0], [0, 0, 0], [0, 0, 0]]);
});

test('direct exchange guards require the same open exchange window', async () => {
  const window = { title: 'GoldBag Deposit 1', slots: Array(90).fill(null) };
  const bot = { currentWindow: window };
  await assertSameWindow(bot, window, 'direct deposit');
  bot.currentWindow = { ...window };
  await assert.rejects(assertSameWindow(bot, window, 'replacement'));
});
