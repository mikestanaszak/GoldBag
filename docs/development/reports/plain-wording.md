# Plain GoldBag wording

Owner requested removing the playful treasure wording after2.3.0. Replaced it across command help/payment, deposit/withdraw/banknote results, conversation prompts, banknote lore, menu labels/instructions/leaderboard and default messages.yml with direct wording. Kept gold/yellow styling and `[GoldBag]` prefixes. No transaction, permission, persistence, slot or price logic changed. Existing production messages.yml was already plain and remains preserved.

Verification: reviewed the complete source diff; no treasure/adventure/gold-rush/vault/pocket wording remains in runtime source. `mvn -B verify` PASS (86 tests), packaged verifier PASS (Java16 baseline, SQLite natives, restart/restore). Publication/activation evidence follows.
