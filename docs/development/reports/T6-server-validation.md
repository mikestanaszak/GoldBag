# T6 local Paper server validation

## Authorization and scope

On 2026-09-06 the user explicitly instructed: "Set EULA to true and also test the server." This authorizes the isolated local test server's `eula=true` and startup, superseding the earlier no-acceptance boundary for this test. No hosting purchase or public deployment is involved.

Server directory: `C:\Users\mfsta\Documents\ChatGPT\GoldBag-local-tests\paper-1.21.11-132` (outside source repository).

- Official Paper stable Minecraft 1.21.11 build 132, commit `c5eb0790f199da6c38d0a650e1e5cd5415b28185`.
- Download metadata: `https://fill.papermc.io/v3/projects/paper/versions/1.21.11/builds`; [official download service documentation](https://docs.papermc.io/misc/downloads-service/).
- Server JAR SHA-256: `5FFEF465EEEB5F2A3C23A24419D97C51AFD7DBB4923FF42DF9A3F58BBA1CCFBA`, verified against official metadata before execution.
- GoldBag source `39c93b7`, JAR SHA-256 `43377E3C0C2E8AAB9F5AB22F4DD65A38B0353ED48363683CEFBC2F0ACBB4AE0E`.
- Windows 11 amd64; Eclipse Temurin Java 21.0.11+10; startup `java -Xms512M -Xmx2G -jar server.jar --nogui`.
- Network bound to `127.0.0.1:25575`, offline-mode solely for local automated protocol clients, query/RCON disabled, max four players, peaceful survival test world.
- `scripts/Prepare-GoldBagTestServer.ps1` prepared the directory. Controller then wrote `eula=true` and local-only server properties per the explicit user request.

## In progress

Initial startup and generated configuration validation are running. Luna worker owns `scripts/server-tests/**` and `T6-player-server-tests.md` for actual protocol-client exchange/menu tests. Controller owns server lifecycle, commands, logs, compatibility/status updates, and commits.

The server is a local validation fixture; do not certify any other Minecraft/Paper/Spigot build from its results. Exact results and remaining checks will be appended here and in the player-test report.

## First startup and console checks

- PASS: Paper startup completed, GoldBag enabled with SQLite and all 18 default resources. `plugins` listed GoldBag as enabled. Listening socket confirmed `127.0.0.1:25575`.
- PASS: `goldbag rates` printed original/default rates including raw iron G2.00 and diamond G50.00; ingots absent.
- PASS: `goldbag reload` with valid configuration; invalid `exchange.allow-creative` text rejected with a precise diagnostic while active rates remained available. Original config restored and successful reload repeated.
- PASS: `goldbag storage status` reported healthy with no pending operations; `goldbag storage export` wrote schema2 JSON; `goldbag recovery list` empty.
- PASS: console deposit rejected because inventory operations require a player.
- Setup-only observations: flat-world generation used default empty generator settings and logged `No key layers in MapLike[{}]` during initial world creation; server continued normally. Paper warns about deliberate offline mode and newer releases, and uses its Java profiler on Windows. These are not GoldBag exceptions. Initial mistyped `goldbag export` correctly returned unknown command; canonical `goldbag storage export` passed.
- Actual clients created GoldBagSmokeA/B accounts. Controller opped only those fixture users. Basic deposit, withdrawal and payment progressed; a banknote air-interaction check then failed. Investigation is scoped to event cancellation semantics versus protocol-client behavior; do not mark the banknote test passing yet. See the player-test and banknote-fix reports.
