# ItemFixer

[![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)](https://papermc.io/)
[![Platform](https://img.shields.io/badge/Paper-1.21--26.3-purple.svg)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-21%2B-orange.svg)](https://adoptium.net/)

Replaces old items with the new ones in case updates are needed. Built for FFA where you need to update items but you dont want to use a clanky fix villager NPC.

<p>
  <img src="https://github.com/vanillaxtra/itemfixer/blob/main/assets/main.png?raw=true" width="48%" alt="Rule list">
  <img src="https://github.com/vanillaxtra/itemfixer/blob/main/assets/editrule.png?raw=true" width="48%" alt="Edit rule">
</p>

---

# 🔄 Item Replacement

* **Pair old and new** - Each fix is one old item and the item it becomes.
* **Keeps the stack** - A stack of 12 stays a stack of 12. Only the item changes.
* **Exact match** - Name, enchants, and item data stay so it purely becomes the item you want it to.
* **Chat notice** - Players with `itemfixer.notify` get notified when something of theirs changed.

---

# 📖 Rule Menu

`/itemfixer` opens the rule menu.

* **One icon per rule** - Each item except the emerald represents a rule which you can click on and eidt.
* **Add** - A enchanted emerald sits right after the last rule. If you click it, you can create a new rule.

Click a rule to edit it. Click the emerald to add one.

---

# ✏️ Editor

A 4-row menu.

* **Left slot** - The old item.
* **Right slot** - What it becomes.
* **Click or shift-click** - Takes an item back out of a slot.
* **Save** - The emerald glows once both slots are filled.
* **Delete** - Bottom right, only while editing an existing rule.

---

# 🎒 What Gets Scanned

Checked on the player:

- Inventory
- Armor
- Offhand
- Cursor
- Ender chest, only when `scan.ender-chest` in the config is true (off by default)

Not checked:

- Chests, barrels, and other physical world storage
- Items nested inside shulkers or bundles. I lowkey might add support for this in the future.

---

# 🌍 Worlds And Bypass

* **Bypass** - Anyone with a permission listed under `bypass-permissions` is left alone. Default is `itemfixer.bypass` (default false).
* **Blacklist** - Skip the worlds in the list.
* **Whitelist** - Repair only the worlds in the list.
* World names ignore case.

Offline players cannot be checked for bypass. A bypass player who is offline during `/itemfixer fixall` is still repaired.

---

# ⏱️ How Often It Runs

Repairs stay on the main thread, a few players at a time, as to not spike mspt and lag the server.

* **`cycle-seconds`** - How often online players are checked again. Default 20.
* **`players-per-tick`** - How many players items are repaired on one tick. Default 1.
* Join, pickup, and closing a chest also mark that player to be checked.
* Players standing in a skipped world, or with bypass, are not checked.
* `/itemfixer reload` applies `config.yml`, `rules.yml`, and `messages.yml` without a restart. A broken config keeps the previous settings.

---

# 👥 Fix Everyone

`/itemfixer fixall` repairs people who are online and people who are offline.

---

# 💬 Messages

All text lives in `plugins/ItemFixer/messages.yml`. Minimessage support (basically a human right)!

```yaml
gui:
  save: "<green>Save Rule"
  delete: "<red>Delete Rule"
```

---

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/itemfixer` | `itemfixer.admin` | Opens the rule list. Players only. |
| `/itemfixer reload` | `itemfixer.admin` | Reloads config, rules, and messages. |
| `/itemfixer fixall` | `itemfixer.admin` | Repairs online players, then offline player files. |

Console can run `reload` and `fixall`. Opening the menu requires a player.

---

## Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `itemfixer.admin` | OP | Open the menu, reload, and run fixall |
| `itemfixer.notify` | true | Hear when your items are updated |
| `itemfixer.bypass` | false | Keep old items. Also the default entry in `bypass-permissions` |

---

## Configuration

Main config: `plugins/ItemFixer/config.yml`

| Key | Default | What it does |
|-----|---------|----------------|
| `bypass-permissions` | `itemfixer.bypass` | Permissions that skip the repair |
| `worlds.mode` | `blacklist` | `blacklist` skips the list, `whitelist` repairs only the list |
| `worlds.list` | empty | World names, case ignored |
| `scan.ender-chest` | `false` | Also check ender chests |
| `optimisations.skip-armor-without-equipable` | `true` | Skip armor slots when no old item is wearable armor |
| `cycle-seconds` | `20` | Seconds between full online checks |
| `players-per-tick` | `1` | Players repaired on each tick |

Rules are stored in `plugins/ItemFixer/rules.yml`. Overflow from offline repairs is stored in `plugins/ItemFixer/pending.yml`.

---

## Requirements

- **Minecraft:** Paper 1.21 through 26.3
- **Java:** 21 or newer
- **Build:** Maven, `mvn package`, output `target/itemfixer.jar`

Folia is not supported. (sorry jules)
