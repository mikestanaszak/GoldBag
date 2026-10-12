# Resource value review — 2026-10-12

User requested a rarity/ease-of-obtaining review during the direct UI refactor. Proposed values are a starting policy for a small survival economy, not empirically measured market prices. The user approved applying the proposed table on 2026-10-12. Defaults and the existing local configuration are in scope; preserve current balances.

| Item | Existing G | Proposed G |
|---|---:|---:|
| Coal | 0.20 | 0.50 |
| Redstone | 1.00 | 0.25 |
| Lapis | 1.00 | 0.50 |
| Raw copper | 1.00 | 0.50 |
| Raw iron | 2.00 | 2.00 |
| Raw gold | 5.00 | 5.00 |
| Diamond | 50.00 | 50.00 |
| Emerald | 25.00 | 2.00 |
| Quartz | 1.00 | 1.00 |
| Amethyst shard | 0.50 | 0.25 |

Keep deposit/withdraw prices equal for transparent lossless resource conversion, and reversible storage blocks exactly 9× the unit price. Quartz/amethyst blocks remain excluded (not reversible 9:1 storage). No change to account balances.

Reasoning: ore rarity alone is misleading. Copper occurs in large quantities and lapis/redstone produce multiple drops per ore. Emerald trading and renewable amethyst make their supply different from rare mined diamonds. Existing raw-metal-only eligibility already excludes the usual iron/gold farm ingots, so retain raw iron/gold and the diamond anchor. A cheap emerald conversion reduces, but does not eliminate, the route from renewable villager trades to diamonds. Static prices cannot identify whether an item was mined or farmed. Revisit prices after observing actual server deposits rather than inventing dynamic markets, caps or provenance tracking in this UI task.

Primary references consulted:
- https://www.minecraft.net/en-us/article/caves---cliffs--part-ii-out-today-java (distribution and large veins)
- https://www.minecraft.net/en-us/article/lapis-lazuli-ore (multi-item yields)
- https://www.minecraft.net/en-us/article/taking-inventory--emerald (trading supply)
- https://www.minecraft.net/en-us/article/taking-inventory--amethyst-shard (growing crystals)
- https://feedback.minecraft.net/hc/en-us/articles/27547857163917-Minecraft-Java-Edition-1-21-Tricky-Trials (witch redstone farm drops)

Existing installations retain their resources.yml. Changing packaged defaults alone would not apply proposed rates to an existing server; any approved deployment must explicitly back up and update the configured rates while preserving custom enable flags, materials and account data.
