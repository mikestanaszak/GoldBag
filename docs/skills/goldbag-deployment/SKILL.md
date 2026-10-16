---
name: goldbag-deployment
description: Stage, install, restart and validate GoldBag in Mike's existing maya-funtimes Paper Minecraft Dockge stack, preserving live data and rollback artifacts.
---

# GoldBag local installation

Read [goldbag-workflow](../goldbag-workflow/SKILL.md) for standing authorization. Ordinary requested GoldBag implementation includes appropriate local deployment and Minecraft-service restart without another approval question. Honor explicit stage-only or user-handled restart instructions. Do not restart merely because this skill was loaded.

## Locate and prepare

Known stack is `maya-funtimes` (user called it “maya funtimes”), Minecraft container `minecraft`, with host `/opt/stacks/maya-funtimes/data` mounted at `/data`; verify current Compose labels, mounts, running Paper/JDK and plugin state before writes. Do not assume cached container IDs, ports, server versions or hashes remain current. Consult repo operations backup/restore and compatibility docs.

Prefer the verified published release corresponding to integrated source. Check JAR/checksum and capture installed version/hash before replacement. A specifically requested pre-merge test build may be staged, but identify it as unreleased and trace its source/hash separately. If a docs/skills-only change needs no runtime update, skip packaging and restart.

Preserve config, plugin database/journal, worlds, player inventories, permissions and other plugins. Preserve the old JAR outside `plugins/`, conventionally `/data/plugin-backups/`, so Paper cannot load both versions. Take a consistent recovery backup before schema/data-affecting changes; do not copy a live SQLite file as if it were a consistent backup. Never prune volumes, clear data, reset inventories, run the fixture harness on production, or edit economy rows directly.

## Install and verify

Install atomically via a temporary non-JAR filename, confirm readable ownership/mode for the server UID, then replace the intended GoldBag JAR. Check for duplicate old GoldBag JARs. Avoid unrelated stack services/config changes.

When activation is in scope, perform a graceful Minecraft-service stop/start using the discovered stack/container controls; prefer a full server restart over Bukkit hot reload for replacing a plugin. Bound shutdown/start waits, inspect logs for save completion and plugin startup, and verify the new artifact and running state. Do not claim the new plugin is active from file staging alone.

Use read-only production checks for startup, SQLite/catalog and existing integration errors. User player testing may be needed for native protocol, Geyser, Essentials or LuckPerms flows; state the exact unverified flow rather than calling startup a full experience pass. Run destructive fixture/player-smoke checks only on isolated servers.

On failed startup or GoldBag enablement, preserve diagnostics and recover the previous JAR when compatible with the retained schema. If the previous JAR cannot read the migrated schema, preserve the migrated database and develop a forward-compatible fix through the normal feature-branch/PR workflow. Keep a failing server stopped; if Paper remains healthy with GoldBag disabled, preserve that state rather than causing unrelated downtime. Activate the verified fix with one controlled restart. Do not blindly roll back a migrated database or repeatedly restart into the same failure. Stop after a failed recovery attempt and report evidence and the recovery location. Record source/release, staged hash, backup, restart outcome and manual limitations in repo reports and the final response.
