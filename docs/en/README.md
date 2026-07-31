# SoulAuction

> [Русская версия](../../README.md)

**One auction house for the whole network: multiple storefronts, any economy, Folia, and trade protection — no fat-jar, no click dupes.**

SoulAuction is an auction plugin for Paper **1.21+** and **Folia**, built for when economy logic must survive region threading and double-purchase on a proxy is unacceptable.

Single JAR: dependencies via Paper `libraries`, no shade. Fewer conflicts with other plugins and easier updates.

## Why admins choose it

**Multiple auction houses in one plugin** — regular, VIP, event, donation-currency, and more. Each has its own economy (Vault, PlayerPoints, experience, CoinsEngine, items), permissions, and rules. Not three separate AH plugins and not a sync zoo.

**Folia first-class** — region-aware threading, no legacy scheduler on hot paths. For networks already on Folia or migrating, this is baseline, not a nice extra.

**Multi-server** — shared **MySQL** + **Redis**: one catalog across the network, distributed locks for buy/sell, atomic listing state transitions in the database. Pub/sub refreshes the storefront; SQL is the source of truth, not “whoever wrote the file last”.

**Players get a proper AH:** GUI, categories, sorting, search and filters, favorite sellers, hub for “my listings / claim / history”. Messages use MiniMessage, HEX, gradients; texts live in `lang/messages_*.yml`.

**Economy under control:** seller and buyer taxes, bypass/discount via permissions, slot limits by rank, cooldowns, blacklist, world bans, item whitelist/blacklist, rules for ItemsAdder / Oraxen / MMOItems. Listing TTL; if inventory is full, items go to claim, not the void.

**Operations:** deal history, audit log, admin GUI, purge, claim recovery, Discord/Telegram deal notifications (async HTTP, no bot on the Minecraft server). `/ah reload` reloads config and messages.

**Built for:** Paper/Folia networks with multiple game modes and currencies, a shared auction across proxies, where anti-dupe matters more than “one more GUI button”.

## Network and anti-dupe (short)

| Mode | Purpose |
|------|---------|
| `JSON` / `YAML` | single server, quick start |
| `SQLITE` / `MYSQL` | persistence; **MYSQL** is the proxy foundation |
| Redis + pub/sub | cross-instance locks + catalog cache invalidation |

On a proxy: **MYSQL + redis.enabled**. Buy/cancel/expiry go through listing claim (lock + Redis NX + `UPDATE … WHERE status='ACTIVE'`), rollback on payment failure or full inventory. **Sell:** item held in escrow in GUI; inventory listing uses strict removal before DB write (`/ah sell`, no clone-without-consume).

## Features

- Multi-auction support, separate `open` / `buy` / `sell` permissions.
- Currencies: `VAULT`, `PLAYER_POINTS`, `EXPERIENCE`, `COINS_ENGINE`, `ITEM`.
- Storage: `JSON`, `YAML`, `SQLITE`, `MYSQL`.
- Listing TTL (timed or unlimited), claim, seller notification on expiry, per-auction fees, sold/cancelled/expired history.
- Listing limits: `soulauction.<auctionId>.<N>`, `soulauction.all.<N>`.
- Sort priority: `soulauction.priority.<N>`.
- Large-sale broadcast, external notifications (Discord/Telegram).
- **Sell GUI:** amount ±1, pull **matching** items from inventory (up to max stack); comparison by full serialization (NBT, name, enchants), not “similar” stacks. Escrow until confirm — returned on cancel/close.
- **Per-auction price display:** custom currency symbol, before/after number, MiniMessage and optional PlaceholderAPI in the symbol (resource pack / ItemsAdder icons, etc.).

## Price display (`auctions/*.yml`)

Each auction has its own fields (defaults in Java; after first start — `plugins/SoulAuction/auctions/global.yml` and others):

| Field | Purpose |
|-------|---------|
| `currencySymbol` | Symbol or icon in GUI and chat. Empty — economy default (Vault `$`, PlayerPoints `PP`, items `Nx MATERIAL`). |
| `currencySymbolPosition` | `BEFORE` or `AFTER` the amount (`$100` or `100 ₽`). |
| `currencySymbolPlaceholderApi` | `true` — resolve `%...%` from PlaceholderAPI **for the viewer** (requires PlaceholderAPI). |
| `listingTtlSeconds` | Listing lifetime in seconds; **0 or less — no expiry**. |

### Listing TTL and claim

- While a listing is **ACTIVE**, the browser and “My listings” (if TTL enabled) show **time left / expires at** in lore; the open browser refreshes the timer every second.
- **Expired, unsold:** listing removed (`EXPIRED`), item goes to **claim** once (`claims.json`), not the void.
- **Seller:** chat message (online immediately, offline on join) with expiry notice and **[Claim in menu]** → `/ah expired [auctionId]`; item pickup — **`/ah claim`** (expired menu is view-only, slots are not editable).
- **Listing success:** multi-line chat — item, amount, price, auction; separate block for timed or unlimited expiry (`success-listed`, `success-listed-expiry-timed` / `success-listed-expiry-unlimited` in `lang/messages_*.yml`).
- Single process: lock on `listingId` + on SQL, `ACTIVE` → `EXPIRED`/`SOLD`/`CANCELLED` is atomic; claim is removed from buffer before inventory delivery.

`currencySymbol` examples:

- Text: `₽`, `coins`, `gems`
- MiniMessage / glyph: `<glyph:coin>` (ItemsAdder, Nexo, etc. — per pack docs)
- PAPI (with `currencySymbolPlaceholderApi: true`): `%img_economy%`

Custom symbol prices appear everywhere: GUI listings, sell menu, price filter, purchase, history, chat (`success-bought`, announce, etc.).

## Messages (`plugins/SoulAuction/lang/`)

- `prefix` — prefix for all `{prefix}` strings.
- Keys use MiniMessage; `{price}` is already formatted for the auction the player is viewing.
- **Multi-line chat:** key as a **YAML list** of strings — plugin sends each line separately (`MessageService.send`). Single string — as before. Chat search, sell success, expiry, command usage — in this format.
- Listing lore placeholders in GUI: `{seller}`, `{price}`, `{id}`, `{auction}`, `{expires_in}`, `{expires_at}` (last two when TTL enabled); optional `listingLoreTemplate` in `auctions/*.yml`.

## PlaceholderAPI

Expansion registers automatically when PlaceholderAPI is present. Identifier — `soulauction`, i.e. `%soulauction_<param>%`. Numbers are formatted for the player's locale (thousands separators). Any placeholder also has a `_raw` suffix variant — unformatted value (`1500000` instead of `1,500,000`, enum key instead of lang name) for scoreboards and plugins that need raw numbers.

### Counters

| Placeholder | Value |
|---|---|
| `%soulauction_active_count%` | Player's active listings (aliases: `sell_count`, `purchasable_count`) |
| `%soulauction_expired_count%` | Player's expired items (claims with reason EXPIRED) |
| `%soulauction_total_active_count%` | All listings on the auction house |
| `%soulauction_sell_limit%` | Player's global listing limit (permissions + override) |
| `%soulauction_claims%` | All player items in claim |
| `%soulauction_listings_all%` / `%soulauction_listings_<auctionId>%` | Player's active listings (all / specific auction) |
| `%soulauction_listings_total%` | All listings (unformatted, legacy) |

### Player settings

| Placeholder | Value |
|---|---|
| `%soulauction_selected_sorting%` | Selected sort order |
| `%soulauction_selected_category%` | Selected category |
| `%soulauction_selected_currency%` | Currency of the last opened auction (`currency-*` from lang) |
| `%soulauction_categories_enabled%`, `%soulauction_expired_items_enabled%`, `%soulauction_auction_listing_confirmation_enabled%` | Always `true` — these systems cannot be disabled in SoulAuction |

### Deal statistics

Persistent (`data/stats.json`); seeded once from existing history on first run. `<currency>` is economy type: `vault`, `player_points`, `experience`, `coins_engine`, `item`.

| Placeholder | Value |
|---|---|
| `%soulauction_items_sold%` | Items sold by the player |
| `%soulauction_items_purchased%` | Items purchased by the player |
| `%soulauction_money_made%` | Money earned by the player (after tax) |
| `%soulauction_money_spent%` | Money spent by the player (with buyer tax) |
| `%soulauction_items_sold_<currency>%` and similar | Same, per currency |
| `%soulauction_total_items_sold%`, `%soulauction_total_money_made%`, etc. | Server-wide, including `_<currency>` |

## Commands

### Players

- `/ah` — open the default auction.
- `/ah <auctionId>` — open a specific auction.
- `/ah sell <price>` — list the item in hand on the default auction.
- `/ah sell <auctionId> <price>` — list on the chosen auction.
- `/ah sell <price> <auctionId>` — alternative argument order.
- `/ah my [auctionId]` — show your active listings.
- `/ah selling [auctionId]` — active listings GUI.
- `/ah expired [auctionId]` — expired items GUI (claim).
- `/ah purchased [auctionId]` — purchase history GUI.
- `/ah history [auctionId]` — sales history GUI.
- `/ah search <text>` — search: closes GUI, prompt **in chat** (cancel button in message); after input — refreshed browser and result in chat.
- `/ah search <auctionId> <text>` — same for a specific auction.
- `/ah search cancel` — cancel search and return to the auction.
- `/ah page <number> [auctionId]` — open a specific auction page.
- `/ah claim [all]` — claim expired/cancelled items.
- `/ah cancel <id>` — remove your listing and return the item.
- Command aliases are configured in `config.yml` via `commandAliases` (e.g. `ax`, `auction`).

### Admins

- `/ah reload` — reload configs and messages.
- `/ah purge <days>` — purge deal history older than N days.
- `/ah admin history <player> [limit]` — player history in chat.
- `/ah admin selling <player> [auctionId]` — player's active listings GUI.
- `/ah admin blacklist add|remove <player>` — runtime sell blacklist.
- `/ah admin recover <claimId>` — deliver claim to inventory.
- `/ah admin audit [limit]` — recent audit entries.
- `/ah admin cache stats|rebuild|invalidate` — catalog cache.
- `/ah admin sellfor <player> <auctionId> <price>` — list on behalf of a player (item in hand).
- `/ah admin parse tags|nbt` — parse NBT/tags of item in hand (custom items).
- `/ah view <player> [auctionId]` — player's listings GUI.

## Permissions

### Basic

- `soulauction.command.ah` — use `/ah`.
- `soulauction.command.sell` — use `/ah sell`.
- `soulauction.command.reload` — use `/ah reload`.
- `soulauction.command.admin` — admin commands and purge.
- `soulauction.priority.10` — higher listing sort priority (any N).
- `soulauction.command.my` — use `/ah my`.
- `soulauction.command.claim` — use `/ah claim`.
- `soulauction.command.cancel.any` — remove others' listings via `/ah cancel`.

### Per-auction (set in `config.yml`)

Each auction has separate nodes:
- `openPermission` — open this auction.
- `buyPermission` — buy listings in this auction.
- `sellPermission` — sell in this auction.

Example:
- `soulauction.open.vip`
- `soulauction.buy.vip`
- `soulauction.sell.vip`

### Dynamic listing limits

- `soulauction.<auctionId>.1` — limit 1 active listing in this auction.
- `soulauction.<auctionId>.5` — limit 5 active listings in this auction.
- `soulauction.all.1` — global limit 1 across all auctions.
- `soulauction.all.5` — global limit 5 across all auctions.

If multiple nodes are granted, the highest limit applies.
If none are granted, defaults from `limits` are used.

### Taxes

- `soulauction.tax.bypass` — no sale or purchase tax.
- `soulauction.tax.discount.<percent>` — sale tax discount (highest wins).

In `auctions/*.yml`: `saleTaxPercent` (from seller), `buyTaxPercent` (from buyer).

### Price bounds

- Global: `limits.minPrice`, `limits.maxPrice` in `config.yml`.
- Per-auction: `minPrice`, `maxPrice` (0 = use global).
- `soulauction.price.min.<price>` — minimum listing price for a player.
- `soulauction.price.max.<price>` — maximum listing price for a player.

### Discord and Telegram

In `config.yml` → `notifications` section:

- **Discord:** `notifications.discord.enabled`, `webhookUrl` (channel Incoming Webhook).
- **Telegram:** `notifications.telegram.enabled`, `botToken`, `chatId`.
- Events: `notify-sold`, `notify-listed`, `notify-expired`, `min-price` filter.
- Delivery is **async** (Java HTTP), no shade, no bot on the Minecraft server.
- **Player avatars in Discord:** `notifications.discord.show-player-avatars` (seller head in author, buyer thumbnail on deals). CDN: `avatar-provider` = `MINOTAR` (default) or `CRAFATAR`.

After `/ah reload`, new URLs and flags are picked up.
