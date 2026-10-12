# GoldBag gold theme

User requested `[GoldBag]` on all player messages, full gold coloring, and a more refined/fun experience. Shared formatting strips previous color codes and adds a gold prefix on each line; already prefixed custom text does not duplicate the prefix. Conversation prompts explicitly use the same formatter. Existing messages.yml wording remains user owned on installed servers; the theme applies automatically. New installs receive refreshed defaults.

Menus retain sizes, staging slots, navigation and transaction dispatch. Titles use `[GoldBag]`, item names gold and lore yellow. Deposit footer panes are yellow, instructions reflect batch staging, banknotes retain identity metadata and gain redemption instructions. Success messages add brief treasure/adventure wording while retaining amount/count diagnostics. Economy, persistence, rates, permissions and existing notes are unchanged.

Verification: focused formatter tests2; `mvn -B clean verify` and final `mvn -B verify` PASS (86 Java tests); packaged-JAR verifier PASS (716 base classes, major60,20 SQLite natives); harness13 and release-analysis5 PASS. Node/npm discovered from bundled runtime; plain npm was unavailable. Independent Luna review cleared the corrected conversation prompt bypass. Harness title expectations updated to branded titles.

Native Paper1.21.11 build132 / Java25 theme probe: pending. Publication and existing maya-funtimes activation: pending.
