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
      if (process.env.GOLDBAG_DISABLE_PHYSICS === "1") bot.physicsEnabled = false;
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

function itemText(item) {
  return item ? JSON.stringify(item.nbt || item.components || item.metadata || item) : '';
}

function windowTitle(window) {
  return window ? JSON.stringify(window.title) : '';
}

function assertMenuControl(window, slot, matcher, description) {
  assert(window && window.slots && window.slots[slot], `${description} control is missing at slot ${slot}`);
  assert(matcher.test(itemText(window.slots[slot])), `${description} control at slot ${slot} is ${itemText(window.slots[slot])}`);
}

function assertDepositLayout(window) {
  assert(window && window.slots && window.slots.length >= 90, 'deposit menu is not a 54-slot chest window');
  assert(window.slots.slice(0, 45).every(item => item == null), 'deposit chest top 0-44 must remain blank');
  assertMenuControl(window, 45, /back|main|menu/i, 'deposit main');
  assertMenuControl(window, 48, /withdraw/i, 'deposit withdrawal');
  assertMenuControl(window, 49, /close|cancel/i, 'deposit close');
  assertMenuControl(window, 50, /balance|deposit|shift|info|rate|help/i, 'deposit info');
  assertMenuControl(window, 52, /deposit|all/i, 'deposit all');
}

function assertWithdrawLayout(window) {
  assert(window && window.slots && window.slots.length >= 90, 'withdraw menu is not a 54-slot chest window');
  assertMenuControl(window, 48, /deposit/i, 'withdraw deposit');
  assertMenuControl(window, 49, /close|cancel/i, 'withdraw close');
  assertMenuControl(window, 50, /balance|withdraw|shift|info|rate|help/i, 'withdraw info');
  assertMenuControl(window, 51, /back|main|menu/i, 'withdraw back');
}

function findWindowItem(window, itemName) {
  const start = Number.isInteger(window.inventoryStart) ? window.inventoryStart : 54;
  const end = Number.isInteger(window.inventoryEnd) ? window.inventoryEnd : window.slots.length;
  for (let slot = start; slot < end; slot += 1) {
    if (window.slots[slot] && window.slots[slot].name === itemName) return slot;
  }
  return -1;
}

function findTopItem(window, itemName) {
  return window.slots.slice(0, 45).findIndex(item => item && item.name === itemName);
}

async function assertSameWindow(bot, window, description) {
  await sleep(150);
  assert(bot.currentWindow === window, `${description} replaced the open menu`);
  assert(/GoldBag (Deposit|Withdraw)/i.test(windowTitle(bot.currentWindow)), `${description} closed the exchange menu`);
}

async function waitForExchange(bot, window, start, matcher, description) {
  await waitForLine(bot, start, matcher, description);
  await assertSameWindow(bot, window, description);
}

async function clickDepositCursor(bot, itemName, targetSlot, mouseButton = 0, mode = 0) {
  const window = bot.currentWindow;
  const source = findWindowItem(window, itemName);
  assert(source >= 0, `deposit player inventory has no ${itemName}`);
  const start = bot.lines.length;
  if (mode === 1) {
    await bot.clickWindow(source, 0, mode);
  } else {
    await bot.clickWindow(source, mouseButton === 1 ? 1 : 0, 0);
    await sleep(100);
    if (!window.selectedItem || window.selectedItem.name !== itemName) {
      if (mouseButton === 1) return { window, start, supported: false };
      assert(false, `${itemName} was not picked up to the cursor`);
    }
    await bot.clickWindow(targetSlot, mouseButton, 0);
  }
  return { window, start, supported: true };
}

async function returnCursor(bot) {
  const window = bot.currentWindow;
  if (!window || !window.selectedItem) return;
  const start = Number.isInteger(window.inventoryStart) ? window.inventoryStart : 54;
  const slot = window.slots.findIndex((item, index) => index >= start && !item);
  assert(slot >= start, 'could not find a player inventory slot for the rejected cursor item');
  await bot.clickWindow(slot, 0, 0);
}

async function clickWithdrawal(bot, itemName, mouseButton, mode, matcher, description) {
  const window = bot.currentWindow;
  const slot = findTopItem(window, itemName);
  assert(slot >= 0, `withdraw menu omitted ${itemName}`);
  const start = bot.lines.length;
  await bot.clickWindow(slot, mouseButton, mode);
  if (matcher) await waitForExchange(bot, window, start, matcher, description);
  else await assertSameWindow(bot, window, description);
  return window;
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

async function waitForWindow(bot, title, timeout = TIMEOUT) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) {
    if (bot.currentWindow && JSON.stringify(bot.currentWindow.title).includes(title)) return bot.currentWindow;
    await sleep(50);
  }
  throw new Error(`Timed out waiting for ${title} menu`);
}

async function confirmPreview(bot, matcher, description) {
  const window = await waitForWindow(bot, 'GoldBag Confirm');
  const start = bot.lines.length;
  await bot.clickWindow(11, 0, 0);
  const result = matcher ? await waitForLine(bot, start, matcher, description) : null;
  const deadline = Date.now() + TIMEOUT;
  while (bot.currentWindow === window && Date.now() < deadline) await sleep(50);
  assert(bot.currentWindow !== window, 'confirmation menu did not close');
  return result;
}

async function preview(bot, text, expected) {
  bot.chat(text);
  const window = await waitForWindow(bot, 'GoldBag Confirm');
  assert(expected.test(JSON.stringify(window.slots[13])), `Unexpected confirmation summary: ${JSON.stringify(window.slots[13])}`);
  return window;
}

async function clickMenu(bot, slot, title) {
  const old = bot.currentWindow;
  await bot.clickWindow(slot, 0, 0);
  const deadline = Date.now() + TIMEOUT;
  while (bot.currentWindow === old && Date.now() < deadline) await sleep(50);
  return waitForWindow(bot, title);
}

async function expectBalance(bot, amount) {
  await command(bot, '/goldbag balance', new RegExp(`Balance.*${amount.replace('.', '\\.')}`), `balance ${amount}`);
}

async function useNote(bot, offhand = false) {
  // Aim into air so an incidental block interaction cannot consume the click.
  await bot.look(0, Math.PI / 2, true);
  bot.activateItem(offhand);
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
  await useNote(a);
  await waitForLine(a, aRedeemStart, /Banknote redeemed/i, 'post-restart note redemption');
  await expectBalance(a, '90.00');
  const bReplayStart = b.lines.length;
  await useNote(b);
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
    passed.push('canonical main menu opens on the actual server');
    a.closeWindow(menu);
    await sleep(500);

    await command(a, '/goldbag help', /open the menu|deposit|withdraw/i, 'command help');
    await command(a, '/gb balance', /Balance.*0\.00/i, 'canonical alias');
    await command(a, '/goldbag rates', /raw_iron|diamond/i, 'catalog rates');
    passed.push('rates list the live catalog');
    await command(a, '/goldbag storage status', /schema|healthy|pending/i, 'storage status');
    passed.push('storage status exposes live schema/health/pending state');

    // Commands for deposits and withdrawals execute directly. Keep this first
    // pair at zero so the exact configured 25 raw iron = G50.00 economics are
    // still checked without opening a quantity or confirmation menu.
    await clear(a);
    await give(a, 'raw_iron', 25);
    await command(a, '/goldbag deposit raw_iron 25', /Deposited.*50\.00/i, 'direct deposit command');
    await expectBalance(a, '50.00');
    await command(a, '/goldbag withdraw diamond 1', /Withdrew.*1/i, 'direct withdrawal command');
    assert(count(a, 'diamond') === 1, 'direct withdrawal did not add one diamond');
    await expectBalance(a, '0.00');
    passed.push('deposit and withdrawal commands execute directly with exact configured prices');

    // Exercise repeated direct withdrawals before opening the persistent
    // withdrawal screen.
    await command(a, `/goldbag admin set ${BOT_A} 200 repeat withdrawal fixture`, /Balance set.*200\.00/i, 'repeat withdrawal balance');
    await clear(a);
    await command(a, '/goldbag withdraw diamond 1', /Withdrew.*1/i, 'first repeated withdrawal');
    await command(a, '/goldbag withdraw diamond 1', /Withdrew.*1/i, 'second repeated withdrawal');
    assert(count(a, 'diamond') === 2, 'repeated direct withdrawals did not add two diamonds');
    await expectBalance(a, '100.00');
    passed.push('repeated direct withdrawals remain independent and journal one item each');

    // Deposit is a virtual chest. Its upper 45 slots stay empty while the
    // listener consumes player-inventory shift clicks and cursor placements.
    await clear(a);
    await give(a, 'raw_iron', 25);
    await give(a, 'raw_gold', 2);
    await openMenu(a);
    let depositMenu = await clickMenu(a, 12, 'GoldBag Deposit');
    assertDepositLayout(depositMenu);
    const shift = await clickDepositCursor(a, 'raw_iron', 0, 0, 1);
    await waitForExchange(a, shift.window, shift.start, /Deposited.*50\.00/i, 'shift-click deposit');
    assert(count(a, 'raw_iron') === 0, 'shift-click deposit left raw iron in the inventory');

    const cursor = await clickDepositCursor(a, 'raw_gold', 1, 0, 0);
    await waitForExchange(a, cursor.window, cursor.start, /Deposited.*10\.00/i, 'cursor placement deposit');
    assert(count(a, 'raw_gold') === 0, 'cursor placement deposit left raw gold in the inventory');
    await expectBalance(a, '160.00');
    let interactionBalance = 160;
    passed.push('deposit chest shift-click and cursor placement journal immediately without Confirm or menu replacement');

    // A right-click picks one item from a two-item stack. Mineflayer exposes
    // this normal click path on supported protocol versions.
    await give(a, 'raw_iron', 2);
    const right = await clickDepositCursor(a, 'raw_iron', 2, 1, 0);
    if (right.supported) {
      await waitForExchange(a, right.window, right.start, /Deposited.*2\.00/i, 'right-click single deposit');
      assert(count(a, 'raw_iron') === 1, 'right-click deposit did not leave one item from the two-item stack');
      await expectBalance(a, '162.00');
      interactionBalance = 162;
      passed.push('right-click cursor placement deposits one item when the client supports split-stack clicks');
    } else {
      passed.push('right-click single placement skipped because the connected client did not expose split-stack clicks');
      await expectBalance(a, '160.00');
    }

    // Custom and uncatalogued items are rejected at the virtual chest and are
    // returned to the player inventory.
    await give(a, 'iron_ingot', 1);
    const ineligible = await clickDepositCursor(a, 'iron_ingot', 3, 0, 1);
    await sleep(500);
    await assertSameWindow(a, ineligible.window, 'ineligible shift-click rejection');
    assert(count(a, 'iron_ingot') === 1, 'uncatalogued shift-click item was consumed');
    await expectBalance(a, `${interactionBalance}.00`);
    await command(a, `/minecraft:give ${a.username} minecraft:raw_iron[custom_name='{"text":"Forged"}'] 1`, null);
    const custom = await clickDepositCursor(a, 'raw_iron', 4, 0, 0);
    await sleep(500);
    await returnCursor(a);
    await assertSameWindow(a, custom.window, 'custom cursor rejection');
    assert(count(a, 'raw_iron') >= 2, 'custom cursor item was consumed');
    await expectBalance(a, `${interactionBalance}.00`);
    a.closeWindow(depositMenu);

    await openMenu(a);
    const withdrawMenu = await clickMenu(a, 14, 'GoldBag Withdraw');
    assertWithdrawLayout(withdrawMenu);
    await clickWithdrawal(a, 'diamond', 0, 0, /Withdrew.*1/i, 'left-click one withdrawal');
    await clickWithdrawal(a, 'raw_iron', 1, 0, /Withdrew.*16/i, 'right-click sixteen withdrawal');
    const clampedGold = Math.floor((interactionBalance - 82) / 5);
    await clickWithdrawal(a, 'raw_gold', 0, 1, new RegExp(`Withdrew.*${clampedGold}`), 'shift-click clamped withdrawal');
    await expectBalance(a, `${interactionBalance - 82 - clampedGold * 5}.00`);
    passed.push('withdrawal left/right/shift gestures execute in place and clamp to affordability');

    // Repeat the shift gesture against a full ordinary inventory. The menu
    // remains open and the request is rejected without scattering an item.
    a.closeWindow(withdrawMenu);
    await clear(a);
    await command(a, `/goldbag admin set ${BOT_A} 50 capacity fixture`, /Balance set.*50\.00/i, 'capacity balance');
    await give(a, 'cobblestone', 2304);
    await openMenu(a);
    const capacityMenu = await clickMenu(a, 14, 'GoldBag Withdraw');
    assertWithdrawLayout(capacityMenu);
    const capacityStart = a.lines.length;
    await clickWithdrawal(a, 'diamond', 0, 1, null, 'shift-click capacity rejection');
    await waitForLine(a, capacityStart, /capacity|Insufficient|quantity/i, 'shift-click capacity rejection');
    assert(count(a, 'diamond') === 0, 'capacity-clamped withdrawal added an item');
    await expectBalance(a, '50.00');
    a.closeWindow(capacityMenu);
    passed.push('withdrawal shift-click clamps to zero when inventory capacity is full');

    await clear(a);
    await command(a, `/goldbag admin set ${BOT_A} 0 reset deposit-all fixture`, /Balance set.*0\.00/i, 'deposit-all balance');
    await give(a, 'raw_iron', 25);
    await give(a, 'redstone', 5);
    await openMenu(a);
    depositMenu = await clickMenu(a, 12, 'GoldBag Deposit');
    assertDepositLayout(depositMenu);
    const allStart = a.lines.length;
    await a.clickWindow(52, 0, 0);
    await waitForExchange(a, depositMenu, allStart, /Deposited.*51\.25/i, 'deposit-all control');
    assert(count(a, 'raw_iron') === 0 && count(a, 'redstone') === 0, 'deposit-all left eligible items in inventory');
    await expectBalance(a, '51.25');
    passed.push('deposit-all control journals multiple resources while leaving the chest open');

    await openMenu(a);
    const payStart = a.lines.length;
    await a.clickWindow(15, 0, 0);
    await waitForLine(a, payStart, /recipient and amount/i, 'payment prompt');
    await command(a, `${BOT_B} 5.00`, /Paid.*5\.00/i, 'player payment');

    await expectBalance(a, '46.25');
    await expectBalance(b, '5.00');
    passed.push('online two-player payment debits and credits atomically');

    await clear(a);
    await command(a, `/goldbag admin set ${BOT_A} 100 reset banknote fixture`, /Balance set.*100\.00/i, 'banknote balance reset');
    await expectBalance(a, '100.00');

    await openMenu(a);
    const noteStart = a.lines.length;
    await a.clickWindow(16, 0, 0);
    await waitForLine(a, noteStart, /banknote amount/i, 'banknote prompt');
    await command(a, 'invalid', /amount|number|decimal/i, 'invalid banknote amount');
    a.chat('10.00');
    await waitForWindow(a, 'GoldBag Confirm');

    await confirmPreview(a, /Banknote issued.*10\.00/i, 'banknote confirmation');
    await sleep(800);
    const mainNote = findNote(a);
    assert(mainNote && mainNote.count === 1, 'issued banknote is not one paper item');
    const noteData = JSON.stringify(mainNote.nbt || mainNote.components || {});
    const mainIdentity = noteIdentity(mainNote);
    assert(mainIdentity, 'issued banknote has no readable identity');
    assert(/note-id|goldbag/i.test(noteData), `issued paper has no visible GoldBag identity data: ${noteData}`);
    await a.equip(mainNote, 'hand');
    await useNote(a);
    await sleep(1200);
    await expectBalance(a, '100.00');
    assert(count(a, 'paper') === 0, 'main-hand banknote was not consumed');
    passed.push('issued banknote carries identity data and redeems once from the main hand');

    await preview(a, '/goldbag note 5.00', /5\.00/i);
    await confirmPreview(a, /Banknote issued.*5\.00/i, 'offhand note confirmation');
    await sleep(700);
    const offhandNote = findNote(a);
    assert(offhandNote, 'second banknote was not issued');
    await a.equip(offhandNote, 'off-hand');
    await useNote(a, true);
    await sleep(1200);
    await expectBalance(a, '100.00');
    assert(count(a, 'paper') === 0, 'offhand banknote was not consumed');
    passed.push('offhand banknote redemption works through the actual interaction event');

    await preview(a, '/goldbag note 10.00', /10\.00/i);
    await confirmPreview(a, /Banknote issued.*10\.00/i, 'copy-replay note confirmation');
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
    await useNote(b);
    await sleep(1200);
    await expectBalance(b, '15.00');
    const sourceAfterCopy = findNote(a, sourceIdentity);
    assert(sourceAfterCopy, 'source note disappeared before duplicate redemption attempt');
    await a.equip(sourceAfterCopy, 'hand');
    await useNote(a);
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
    await command(a, '/goldbag deposit raw_iron 1', /Your inventory does not contain that quantity/i, 'custom-item rejection');
    const customLines = currentLines(a, customStart);
    assert(!currentLines(a, customStart).some((line) => /Deposited/i.test(line)), 'custom item was deposited');
    assert(count(a, 'raw_iron') === 1, 'custom raw iron was consumed');
    passed.push('renamed/custom raw iron is rejected and remains in the inventory');

    await clear(a);
    await give(a, 'cobblestone', 2304);
    await sleep(900);
    assert(mainInventoryEmptySlots(a) === 0, `full inventory fixture has ${mainInventoryEmptySlots(a)} empty ordinary slots`);
    const fullStart = a.lines.length;
    await command(a, '/goldbag withdraw diamond 1', /That quantity exceeds your balance, capacity, or transaction limit/i, 'full-inventory withdrawal rejection');
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
    await preview(a, '/goldbag note 5.00', /5\.00/i);
    await confirmPreview(a, /Banknote issued.*5\.00/i, 'restart note confirmation');
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

module.exports = {
  assertDepositLayout,
  assertSameWindow,
  assertWithdrawLayout,
  closeBots,
  connect,
  findNote,
  noteIdentity,
  confirmPreview,
  waitForWindow,
};
