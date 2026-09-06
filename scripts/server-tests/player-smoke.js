'use strict';

const mineflayer = require('mineflayer');

const HOST = process.env.GOLDBAG_HOST || '127.0.0.1';
const PORT = Number(process.env.GOLDBAG_PORT || '25575');
const BOT_A = process.env.GOLDBAG_BOT_A || 'GoldBagSmokeA';
const BOT_B = process.env.GOLDBAG_BOT_B || 'GoldBagSmokeB';
const VERSION = process.env.GOLDBAG_MC_VERSION || false;
const TIMEOUT = Number(process.env.GOLDBAG_TIMEOUT_MS || '10000');
const RESTART_CHECK = process.env.GOLDBAG_RESTART_CHECK === '1';

const passed = [];
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

function disconnect(bot, reason) {
  if (!bot) return;
  try {
    if (typeof bot.quit === 'function') bot.quit(reason);
    else if (typeof bot.end === 'function') bot.end();
  } catch (_) {
    try { if (typeof bot.end === 'function') bot.end(); } catch (_) { /* cleanup is best effort */ }
  }
}

function closeBots(bots, reason = 'GoldBag smoke complete') {
  for (const bot of bots) {
    try { disconnect(bot, reason); } catch (_) { /* one broken client cannot block another cleanup */ }
  }
}

function connect(name, createBot = mineflayer.createBot, timeout = TIMEOUT) {
  return new Promise((resolve, reject) => {
    const bot = createBot({
      host: HOST,
      port: PORT,
      username: name,
      auth: 'offline',
      version: VERSION,
    });
    let settled = false;
    const fail = (error) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      disconnect(bot, `GoldBag ${name} connection failed`);
      reject(error);
    };
    const timer = setTimeout(() => fail(new Error(`Timed out waiting for ${name} to spawn`)), timeout);
    bot.lines = [];
    bot.on('messagestr', (message) => {
      const line = String(message);
      bot.lines.push(line);
      console.log(`[${name}] ${line}`);
    });
    bot.once('spawn', () => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      resolve(bot);
    });
    bot.once('error', (error) => fail(error));
    bot.once('kicked', (reason) => fail(new Error(`${name} kicked: ${reason}`)));
    bot.once('end', () => fail(new Error(`${name} connection ended before spawn`)));
  });
}

async function waitForLine(bot, start, matcher, description) {
  const deadline = Date.now() + TIMEOUT;
  while (Date.now() < deadline) {
    for (let index = start; index < bot.lines.length; index += 1) {
      if (matcher.test(bot.lines[index])) return bot.lines[index];
    }
    await sleep(50);
  }
  const recent = bot.lines.slice(start).join(' | ');
  throw new Error(`Timed out waiting for ${description}; received: ${recent}`);
}

async function command(bot, text, matcher, description = text) {
  const start = bot.lines.length;
  bot.chat(text);
  if (matcher) return waitForLine(bot, start, matcher, description);
  await sleep(800);
  return bot.lines.slice(start);
}

function inventoryItems(bot) {
  return bot.inventory.items();
}

function count(bot, itemName) {
  return inventoryItems(bot)
    .filter((item) => item.name === itemName)
    .reduce((total, item) => total + item.count, 0);
}

function noteIdentity(item) {
  if (!item || item.name !== 'paper') return null;
  const serialized = JSON.stringify(item.nbt || item.components || {});
  const match = serialized.match(/[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}/i);
  return match ? match[0].toLowerCase() : null;
}

function findNote(bot, identity = null) {
  return inventoryItems(bot).find((item) => item.name === 'paper'
    && (identity === null || noteIdentity(item) === identity));
}

function mainInventoryEmptySlots(bot) {
  // Mineflayer uses slots 9..44 for the 36 ordinary player inventory slots.
  return bot.inventory.slots.slice(9, 45).filter((item) => item == null).length;
}

function currentLines(bot, start) {
  return bot.lines.slice(start);
}

async function clear(bot) {
  await command(bot, `/minecraft:clear ${bot.username}`, null);
}

async function give(bot, item, amount) {
  await command(bot, `/minecraft:give ${bot.username} minecraft:${item} ${amount}`, null);
}

async function openMenu(bot) {
  return openMenuCommand(bot, '/goldbag');
}

async function openMenuCommand(bot, text) {
  const opened = new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('Timed out waiting for GoldBag menu')), TIMEOUT);
    bot.once('windowOpen', (window) => {
      clearTimeout(timer);
      resolve(window);
    });
  });
  bot.chat(text);
  return opened;
}

async function expectBalance(bot, amount) {
  await command(bot, '/goldbag balance', new RegExp(`Balance.*${amount.replace('.', '\\.')}`), `balance ${amount}`);
}

async function restartCheck(a, b) {
  await expectBalance(a, '85.00');
  await expectBalance(b, '15.00');
  const sourceNote = findNote(a);
  assert(sourceNote, 'restart fixture did not preserve A\'s issued note');
  const sourceIdentity = noteIdentity(sourceNote);
  assert(sourceIdentity, 'restart fixture note has no readable identity');
  await a.equip(sourceNote, 'hand');
  await command(a, `/minecraft:item replace entity ${BOT_B} weapon.mainhand from entity ${BOT_A} weapon.mainhand`, null);
  await sleep(900);
  const copiedNote = findNote(b, sourceIdentity);
  assert(copiedNote, 'restart fixture note was not copyable to B');
  await b.equip(copiedNote, 'hand');
  const aRedeemStart = a.lines.length;
  a.activateItem();
  await waitForLine(a, aRedeemStart, /Banknote redeemed/i, 'post-restart note redemption');
  await expectBalance(a, '90.00');
  const bReplayStart = b.lines.length;
  b.activateItem();
  await waitForLine(b, bReplayStart, /unresolved|cancelled|already|redeem/i, 'post-restart duplicate-note rejection');
  await expectBalance(b, '15.00');
  passed.push('no-reset restart preserved balances and one issued note; redemption then rejected replay');
  console.log('PASS: no-reset restart check; expected A=G90.00, B=G15.00 after one redemption and rejected replay');
}

async function run() {
  console.log(`Connecting ${BOT_A} and ${BOT_B} to ${HOST}:${PORT}`);
  let a = null;
  let b = null;
  try {
    a = await connect(BOT_A);
    b = await connect(BOT_B);
    if (RESTART_CHECK) {
      await restartCheck(a, b);
      return;
    }
    // Existing local test accounts are reset through the audited admin path so
    // reruns do not depend on state left by a previous smoke attempt.
    await command(a, `/goldbag admin set ${BOT_A} 0 reset smoke account`, /Balance set.*0\.00/i, 'reset SmokeA balance');
    await command(a, `/goldbag admin set ${BOT_B} 0 reset smoke account`, /Balance set.*0\.00/i, 'reset SmokeB balance');
    await clear(a);
    await clear(b);

    const menu = await openMenu(a);
    assert(menu && menu.slots && menu.slots.length >= 27, 'GoldBag did not open a chest menu');
    passed.push('canonical menu opens on the actual server');
    a.closeWindow(menu);
    await sleep(500);

    await command(a, '/goldbag rates', /raw_iron|diamond/i, 'catalog rates');
    passed.push('rates list the live catalog');
    await command(a, '/goldbag storage status', /schema|healthy|pending/i, 'storage status');
    passed.push('storage status exposes live schema/health/pending state');

    await clear(a);
    await give(a, 'raw_iron', 25);
    await command(a, '/goldbag deposit raw_iron 25', /Deposit preview.*50\.00/i, 'deposit preview');
    await command(a, '/goldbag confirm', /Deposited.*50\.00/i, 'deposit confirmation');
    await expectBalance(a, '50.00');
    passed.push('25 raw iron deposits as exactly 50.00 G through preview/confirm');

    await command(a, '/goldbag withdraw diamond 1', /Withdrawal preview.*50\.00/i, 'diamond withdrawal preview');
    await command(a, '/goldbag confirm', /Withdrew.*1/i, 'diamond withdrawal confirmation');
    await sleep(800);
    assert(count(a, 'diamond') === 1, 'diamond withdrawal did not add exactly one diamond');
    await expectBalance(a, '0.00');
    passed.push('one diamond withdrawal consumes 50.00 G and adds one ordinary diamond');

    await clear(a);
    await give(a, 'raw_iron', 25);
    await give(a, 'raw_gold', 1);
    await command(a, '/goldbag deposit all', /Deposit-all preview.*raw_iron.*raw_gold/i, 'deposit-all preview');
    await command(a, '/goldbag confirm', /Deposited.*26/i, 'deposit-all confirmation');
    await expectBalance(a, '55.00');
    passed.push('deposit-all previews and consumes multiple resources with the configured prices');

    await command(a, `/goldbag pay ${BOT_B} 5.00`, /Paid.*5\.00/i, 'player payment');
    await expectBalance(a, '50.00');
    await expectBalance(b, '5.00');
    passed.push('online two-player payment debits and credits atomically');

    await clear(a);
    await give(a, 'raw_iron', 25);
    await command(a, '/goldbag deposit raw_iron 25', /Deposit preview.*50\.00/i, 'second deposit preview');
    await command(a, '/goldbag confirm', /Deposited.*50\.00/i, 'second deposit confirmation');
    await expectBalance(a, '100.00');

    await command(a, '/goldbag note 10.00', /Banknote preview.*10\.00/i, 'banknote preview');
    await command(a, '/goldbag confirm', /Banknote issued.*10\.00/i, 'banknote confirmation');
    await sleep(800);
    const mainNote = findNote(a);
    assert(mainNote && mainNote.count === 1, 'issued banknote is not one paper item');
    const noteData = JSON.stringify(mainNote.nbt || mainNote.components || {});
    const mainIdentity = noteIdentity(mainNote);
    assert(mainIdentity, 'issued banknote has no readable identity');
    assert(/note-id|goldbag/i.test(noteData), `issued paper has no visible GoldBag identity data: ${noteData}`);
    await a.equip(mainNote, 'hand');
    a.activateItem();
    await sleep(1200);
    await expectBalance(a, '100.00');
    assert(count(a, 'paper') === 0, 'main-hand banknote was not consumed');
    passed.push('issued banknote carries identity data and redeems once from the main hand');

    await command(a, '/goldbag note 5.00', /Banknote preview.*5\.00/i, 'offhand note preview');
    await command(a, '/goldbag confirm', /Banknote issued.*5\.00/i, 'offhand note confirmation');
    await sleep(700);
    const offhandNote = findNote(a);
    assert(offhandNote, 'second banknote was not issued');
    await a.equip(offhandNote, 'off-hand');
    a.activateItem(true);
    await sleep(1200);
    await expectBalance(a, '100.00');
    assert(count(a, 'paper') === 0, 'offhand banknote was not consumed');
    passed.push('offhand banknote redemption works through the actual interaction event');

    await command(a, '/goldbag note 10.00', /Banknote preview.*10\.00/i, 'copy-replay note preview');
    await command(a, '/goldbag confirm', /Banknote issued.*10\.00/i, 'copy-replay note confirmation');
    await sleep(700);
    const sourceNote = findNote(a);
    assert(sourceNote, 'copy-replay source banknote was not issued');
    const sourceIdentity = noteIdentity(sourceNote);
    assert(sourceIdentity, 'copy-replay source banknote has no readable identity');
    await a.equip(sourceNote, 'hand');
    await command(a, `/minecraft:item replace entity ${BOT_B} weapon.mainhand from entity ${BOT_A} weapon.mainhand`, null);
    await sleep(900);
    const copiedNote = findNote(b, sourceIdentity);
    assert(copiedNote, 'copy-replay target did not receive the copied note');
    await b.equip(copiedNote, 'hand');
    b.activateItem();
    await sleep(1200);
    await expectBalance(b, '15.00');
    const sourceAfterCopy = findNote(a, sourceIdentity);
    assert(sourceAfterCopy, 'source note disappeared before duplicate redemption attempt');
    await a.equip(sourceAfterCopy, 'hand');
    a.activateItem();
    await sleep(1200);
    await expectBalance(a, '90.00');
    await expectBalance(b, '15.00');
    passed.push('copied banknote identity credits once across two actual players');

    await clear(a);
    await command(a, `/minecraft:give ${a.username} minecraft:raw_iron[custom_name='{"text":"Forged"}'] 1`, null);
    await sleep(700);
    const customBefore = inventoryItems(a).find((item) => item.name === 'raw_iron');
    assert(customBefore, 'custom raw iron fixture was not given');
    const customStart = a.lines.length;
    await command(a, '/goldbag deposit raw_iron 1', /Your inventory does not contain that quantity|Deposit preview/i, 'custom-item rejection');
    const customLines = currentLines(a, customStart);
    if (customLines.some((line) => /Deposit preview/i.test(line))) await command(a, '/goldbag confirm', null);
    assert(!currentLines(a, customStart).some((line) => /Deposited/i.test(line)), 'custom item was deposited');
    assert(count(a, 'raw_iron') === 1, 'custom raw iron was consumed');
    passed.push('renamed/custom raw iron is rejected and remains in the inventory');

    await clear(a);
    await give(a, 'cobblestone', 2304);
    await sleep(900);
    assert(mainInventoryEmptySlots(a) === 0, `full inventory fixture has ${mainInventoryEmptySlots(a)} empty ordinary slots`);
    const fullStart = a.lines.length;
    await command(a, '/goldbag withdraw diamond 1', /Withdrawal preview|That quantity exceeds your balance, capacity, or transaction limit/i, 'full-inventory withdrawal rejection');
    if (currentLines(a, fullStart).some((line) => /Withdrawal preview/i.test(line))) await command(a, '/goldbag confirm', null);
    assert(!currentLines(a, fullStart).some((line) => /Withdrew/i.test(line)), 'withdrawal succeeded into a full inventory');
    assert(count(a, 'diamond') === 0, 'full inventory received an unexpected diamond');
    passed.push('full ordinary inventory rejects withdrawal without scattering an item');

    await clear(a);
    await command(a, '/minecraft:give ' + a.username + ' minecraft:iron_ingot 1', null);
    await command(a, '/goldbag deposit iron_ingot 1', /Unknown resource|unsupported|not.*catalog/i, 'smelted-ingot rejection');
    passed.push('smelted iron ingot is rejected by the configured catalog');

    await command(a, `/minecraft:gamemode creative ${a.username}`, null);
    await give(a, 'raw_iron', 1);
    await command(a, '/goldbag deposit raw_iron 1', /unavailable|creative|spectator/i, 'creative exchange rejection');
    await command(a, `/minecraft:gamemode survival ${a.username}`, null);
    passed.push('creative-mode exchange is rejected and the fixture returns to survival');

    const topMenu = await openMenuCommand(a, '/goldbag top');
    assert(topMenu && topMenu.slots && topMenu.slots.length >= 27, 'GoldBag top did not open a leaderboard menu');
    a.closeWindow(topMenu);
    await sleep(500);
    passed.push('leaderboard command returns live player data');

    await clear(a);
    await command(a, '/goldbag note 5.00', /Banknote preview.*5\.00/i, 'restart note preview');
    await command(a, '/goldbag confirm', /Banknote issued.*5\.00/i, 'restart note confirmation');
    const duplicateConfirmStart = a.lines.length;
    await command(a, '/goldbag confirm', /no active|expired|quote/i, 'duplicate confirmation rejection');
    assert(!currentLines(a, duplicateConfirmStart).some((line) => /Banknote issued/i.test(line)), 'duplicate confirm issued a second banknote');
    await sleep(800);
    assert(count(a, 'paper') === 1, 'restart fixture note was not left in A inventory');
    await expectBalance(a, '85.00');
    await expectBalance(b, '15.00');
    passed.push('final restart fixture leaves A=85.00, B=15.00 and one issued note in A inventory');

    console.log(`PASS: ${passed.length} actual-player checks`);
    for (const item of passed) console.log(`  - ${item}`);
  } finally {
    closeBots([a, b]);
  }
}

if (require.main === module) {
  run().catch((error) => {
    console.error(`FAIL: ${error.stack || error}`);
    process.exitCode = 1;
  });
}

module.exports = { closeBots, connect, findNote, noteIdentity };
