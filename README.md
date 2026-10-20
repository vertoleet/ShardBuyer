<div align="center">

# ✦ ShardBuyer ✦

**An advanced item buyer for Paper 1.20.4**

Categories · leveling · dynamic market · exclusive items · auto-sell · prices right in item descriptions

![Paper](https://img.shields.io/badge/Paper-1.20.4-blue)
![Java](https://img.shields.io/badge/Java-17%2B-orange)
![License](https://img.shields.io/badge/license-MIT-green)
![Languages](https://img.shields.io/badge/languages-EN%20ES%20RU%20UK%20PT%20FR-purple)

**English** · [Русский](README.ru.md)

</div>

---

## ✨ Features

- **Categories** — every category can have its own fully configurable menu, or switch to a single
  menu that flips through categories page by page (`menu-mode: PAGED`).
- **Smart pricing** — set only raw-material prices, the plugin calculates everything else from the
  server recipes:
  - *packing* (9 dried kelp ⇄ dried kelp block) costs exactly the same as the items inside;
  - *real crafting* (a piston) is worth more than its ingredients (`craft-multiplier`);
  - *smelting* adds value (kelp 2$ → dried kelp 3$);
  - always the cheapest recipe is used, so there is nothing to exploit.
- **Enchantments** — every enchantment and level has its own price; enchanted books, armor and tools
  become more valuable; damaged gear is cheaper.
- **Leveling** — players earn points for selling and level up for a price bonus. One shared level,
  a separate level per category, or disabled. Leveling can be turned off for single categories.
- **Dynamic market** — the more an item is sold by everyone, the lower its price and points.
  Prices recover over time.
- **Exclusive items** — boosted prices but a limited amount; limits and a random rotation refresh on a timer.
- **Sell menu** — a 54-slot menu: drop items in, press the button or just close it. Works with
  enchanted gear, books and shulker boxes (sell the contents, keep or sell the box).
- **Auto-sell** — enable it per item; items are sold the moment you pick them up (even with a full
  inventory). Can be disabled completely.
- **Prices in item descriptions** — shown only to the player, with their own bonuses, updated on
  every inventory change. Items are **not modified** on the server, so they still stack.
- **`/worth`** — every price with pages, sorting, category filter and search.
- **Number formats** — `1,000` · `1 000` · `1.000` · `1000` or short `1k` `1m` `1b`.
- **6 languages** — English, Español, Русский, Українська, Português, Français.
  Item names in chat are shown in each player's own client language.
- **Everything is configurable** — messages, menus, sounds, command names and aliases.
- Global price booster, permission multipliers, PlaceholderAPI placeholders.

## 📦 Requirements

| | |
|---|---|
| Server | **Paper 1.20.4** (or a fork), Java 17+ |
| Required | [Vault](https://www.spigotmc.org/resources/vault.34315/) + any economy plugin (EssentialsX, CMI, …) |
| Recommended | [ProtocolLib](https://github.com/dmulloy2/ProtocolLib/releases) **5.2.0+** — for prices in item descriptions |
| Optional | [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) |

## 🚀 Installation

1. Download `ShardBuyer-x.x.x.jar` from [Releases](../../releases).
2. Put it into `plugins/` together with Vault, an economy plugin and ProtocolLib.
3. Start the server. Choose your language in `plugins/ShardBuyer/config.yml` → `language`.
4. Adjust the files and run `/shardbuyer reload`.

## 💬 Commands

Command names and aliases are set in `config.yml → commands` (default below).

| Command | Description |
|---|---|
| `/buyer [category]` | open the buyer |
| `/buyer lore` · `autosell` · `level` · `exclusive` · `help` | toggle prices in descriptions · toggle auto-sell · your level · exclusive items · help |
| `/sell` | sell menu |
| `/sell hand` · `/sell all` | sell the item in hand · the whole inventory |
| `/worth` | price list |
| `/worth hand` · `/worth lore` · `/worth <category or text>` | price in hand · toggle prices in descriptions · filter / search |
| `/shardbuyer reload` | reload all files |
| `/shardbuyer setprice <price> [item]` · `delprice [item]` | set / remove a manual price |
| `/shardbuyer info` | price breakdown of the item in hand |
| `/shardbuyer points <player> <add\|set\|take> <amount> [category]` | change points |
| `/shardbuyer resetlevel <player>` | reset leveling |
| `/shardbuyer market <reset\|info> [item]` | dynamic market |
| `/shardbuyer exclusive reset` | refresh exclusive items |
| `/shardbuyer booster <multiplier> <time>` · `booster stop` | global price booster |
| `/shardbuyer dump` | export all final prices to `computed-prices.yml` |

## 🔑 Permissions

| Permission | Default | Description |
|---|---|---|
| `shardbuyer.use` | everyone | open the buyer |
| `shardbuyer.sell` | everyone | sell menu, `/sell hand` |
| `shardbuyer.sell.all` | everyone | `/sell all`, "sell whole inventory" |
| `shardbuyer.worth` | everyone | `/worth` |
| `shardbuyer.lore` | everyone | prices in item descriptions |
| `shardbuyer.autosell` | everyone | auto-sell |
| `shardbuyer.admin` | op | admin commands |
| `shardbuyer.multiplier.<name>` | — | price multipliers from `config.yml → multipliers` |

## 📁 Files

| File | Contents |
|---|---|
| `config.yml` | language, commands, menu mode, number format, leveling, market, auto-sell, price lore |
| `lang/<code>/messages.yml` | all messages |
| `lang/<code>/menus.yml` | all menus, category names and descriptions |
| `categories.yml` | categories and their items |
| `prices.yml` | base prices and auto-pricing settings |
| `enchantments.yml` | enchantment prices |
| `exclusive.yml` | exclusive items |
| `item-names.yml` | optional custom item names |

Colors: `&a`, HEX `&#C084FC` (gradients `<gradient:#C084FC:#67E8F9>text</gradient>` are supported too).

**Adding a language:** copy `lang/en` to `lang/<code>` (e.g. `lang/de`), translate it and set
`language: de`. Pull requests with new languages are welcome!

## 🧩 PlaceholderAPI

`%shardbuyer_level%` · `%shardbuyer_level_<category>%` · `%shardbuyer_points%` · `%shardbuyer_next_points%` ·
`%shardbuyer_progress%` · `%shardbuyer_bonus%` · `%shardbuyer_boost%` · `%shardbuyer_earned%` ·
`%shardbuyer_sold%` · `%shardbuyer_booster%` · `%shardbuyer_booster_time%` · `%shardbuyer_exclusive_time%` ·
`%shardbuyer_autosell%` · `%shardbuyer_lore%`

## 🛠 Building

```bash
mvn package
```
The jar will be in `target/`. Every push to GitHub is also built automatically (Actions tab).

## 📄 License

[MIT](LICENSE)
