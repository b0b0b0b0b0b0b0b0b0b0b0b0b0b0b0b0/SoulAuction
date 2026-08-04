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
- Large-sale broadcast (4 toggles: item/region purchase & listing), external notifications (Discord/Telegram).
- **Sell GUI:** amount ±1, pull **matching** items from inventory (up to max stack); comparison by full serialization (NBT, name, enchants), not “similar” stacks. Escrow until confirm — returned on cancel/close.
- **Per-auction price display:** custom currency symbol, before/after number, MiniMessage and optional PlaceholderAPI in the symbol (resource pack / ItemsAdder icons, etc.).
- **Fake activity:** synthetic listings from a nick/item pool, enabled **per auction**; timer refill, manual `/ah admin fake`, toggle in admin GUI.
- **Seller skins:** heads in “Favorite sellers” via SkinsRestorer / Mojang; fallback and one shared skin for all fake sellers (server logo).
- **Region market (WorldGuard):** sell/buy WG regions for auction currency; separate commands, GUI, permissions — see below.

## Price display (`auctions/*.yml`)

Each auction has its own fields (defaults in Java; after first start — `plugins/SoulAuction/auctions/global.yml` and others):

| Field | Purpose |
|-------|---------|
| `currencySymbol` | Symbol or icon in GUI and chat. Empty — economy default (Vault `$`, PlayerPoints `PP`, items `Nx MATERIAL`). |
| `currencySymbolPosition` | `BEFORE` or `AFTER` the amount (`$100` or `100 ₽`). |
| `currencySymbolPlaceholderApi` | `true` — resolve `%...%` from PlaceholderAPI **for the viewer** (requires PlaceholderAPI). |
| `listingTtlSeconds` | Listing lifetime in seconds; **0 or less — no expiry**. |
| `world-guard-trade-regions` | **WG whitelist for item trading** in this auction: open GUI, sell, buy — player must stand in one listed region. Empty `[]` = anywhere. **Not** region market (`region-market` in `config.yml`). Examples below. |

Examples for `world-guard-trade-regions` (in `auctions/<id>.yml`):

```yaml
# no restriction (default)
world-guard-trade-regions: []

# regions in the player's current world (/rg list — region id)
world-guard-trade-regions:
  - shop
  - market

# specific world + region
world-guard-trade-regions:
  - world:mall
  - world_nether:trade_hub
```

Requires WorldGuard. This is **not** `/ah regions` — that feature sells WG regions as lots.

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

## Fake activity

Synthetic listings keep the storefront busy: random nick from the pool, random item, random price in range. These are **not** real players — purchases work like normal listings (money is consumed, item is delivered). After a fake listing is bought, the plugin may list another one after a delay.

### Per-auction enable

There is no global on/off in `config.yml` — only per auction:

| Where | Field / action |
|-------|----------------|
| `auctions/<id>.yml` | `fake-activity-enabled: true` / `false` (default `false`) |
| `/ah admin` → **right-click** an auction | auction settings |
| Menu → **slot 22** | toggle fake activity (`LIME_DYE` / `RED_DYE`) → `auctions/<id>.yml` |
| Menu → **slot 49** | fake activity: pool, limits, tick (read-only) |

Example in `auctions/global.yml`:

```yaml
fake-activity-enabled: true
```

### Pool (`plugins/SoulAuction/fake-activity/`)

Path: `config.yml` → `fake-activity.directory` (default `fake-activity`). On first start three files are created:

| File | Contents |
|------|----------|
| `settings.yml` | timers, limits, default prices, `admin-fake` |
| `sellers.yml` | seller display names (max 16 chars) |
| `items.yml` | item pool with weights and price ranges |

Fresh install ships **~120** nicks and **~120** vanilla items (ores, food, tools, 1.21 rares) — edit in YAML anytime.

#### `settings.yml` (main fields)

| Field | Purpose |
|-------|---------|
| `initial-fill-listings` | listings to create on startup (`0` = up to `max-total-listings`) |
| `initial-delay-seconds` | delay before the first refill tick |
| `tick-interval-seconds` | how often to top up toward limits |
| `after-purchase-delay-seconds` | delay after a fake buy before the same seller lists again |
| `min-price` / `max-price` | default range when an item in `items.yml` has min/max = 0 |
| `price-variance-percent` | random ±% on the chosen price |
| `max-listings-per-auction` | fake cap per `auctionId` |
| `max-total-listings` | fake cap server-wide |
| `listing-age-spread-seconds` | random listing age on create (varied TTL timers) |
| `listings-per-tick` | listings to attempt per tick |
| `auction-ids` | only these auctions (`[]` = all with `fake-activity-enabled`) |
| `admin-fake.register-seller` | after `/ah admin fake`, append nick to `sellers.yml` |
| `admin-fake.register-item` | after `/ah admin fake`, append hand item to `items.yml` |

#### `items.yml` entry fields

- `id` — label for yourself
- `material` / `amount` — vanilla stack (ignored when `item-base64` is set)
- `item-base64` — full item snapshot (NBT, custom items)
- `min-price` / `max-price` — `0` = from `settings.yml`
- `auction-ids` — empty = any auction; otherwise only listed ids
- `weight` — relative pick chance (higher = more often)

### Manual fake listing

```
/ah admin fake <nick> <auctionId> <price>
```

Item must be in the **main hand**. Listing is created as synthetic immediately. With `admin-fake.*` enabled, nick and/or item are appended to the pool async.

### Behaviour

- Fakes appear in sorting, search, and filters like normal listings.
- Only an admin with `soulauction.command.cancel.any` can `/ah cancel` them (seller is a synthetic UUID).
- On a proxy with MySQL+Redis, fakes sync like any other listing.

## Region market (WorldGuard)

Sell **WorldGuard regions** using the same economy as the item auction. Region listings use main storage, not item escrow.

### Requirements

- **WorldGuard** (softdepend).
- `config.yml` → `region-market.enabled: true`, then **`/ah reload`** (or restart).
- Seller must own the region in WG; buyer must afford the chosen auction.

### Enable (fresh install)

Default is `enabled: false`. After `true`, `plugins/SoulAuction/regions/` is created (runtime folder).

```yaml
region-market:
  enabled: true
  hide-world-name: true
  ah-subcommand-aliases: [rg]
  standalone-commands: [regions]
  allowed-auction-ids: []
```

| Field | Purpose |
|-------|---------|
| `hide-world-name` | `true` — region id only (`shop`), not `world:shop`. |
| `allowed-auction-ids` | Empty = any sell-enabled auction in `auctions/*.yml`. |
| `ah-subcommand-aliases` | Short `/ah` subs, e.g. `rg` → `/ah rg sell …`. |
| `standalone-commands` | Top-level `/regions …`. **Don't add `rg`** with WG — use `/ah rg` or custom name. |
| `max-listings-per-player` | Region listing cap; `0` = global `limits`. |

`standalone-commands` need a **server restart**.

### Commands

| Command | Action |
|---------|----------|
| `/ah regions` | Region market GUI. |
| `/ah rg` | Same (alias). |
| `/regions` | Same (standalone). |
| `/rg sell …` | Same when WorldGuard is installed: intercepted before WG (`sell`, `cancel`, `my`, `clear`). |
| `/ah regions sell <region> <auctionId> <price>` | List, e.g. `shop global 10000`. |
| `/ah regions sell` | Chat wizard; cancel with `cancel`. |
| `/ah regions my` | Your listings only (“My regions”). |
| `/ah regions cancel <id>` | Remove listing. |
| `/ah regions clear` | Abort chat sell input. |

### GUI

Layout from `gui/general.yml`. Regions do **not** appear in `/ah` item browser. Click listing → confirm → WG ownership transfer.

### Permissions (two layers)

**Region market:**

| Permission | Purpose |
|------------|---------|
| `soulauction.command.regions` | Market commands |
| `soulauction.region.sell` | List a region |
| `soulauction.region.buy` | Buy a region |

**Auction currency** (`auctions/<id>.yml`): `soulauction.buy.global`, `soulauction.sell.global`, …

Buy region = **`region.buy`** + **`buy.<auctionId>`**.  
Sell = **`region.sell`** + **`sell.<auctionId>`**.

Without LuckPerms — `default: true` in `plugin.yml`. With LP — grant nodes in groups.

## Seller skins (`config.yml` → `seller-skins`)

Textures for **seller heads** in the “Favorite sellers” GUI (hub → star). Browser listings show the item itself, not a head.

SkinsRestorer is a **soft dependency**; on offline/cracked servers Mojang lookup by nick often fails without it.

### `source`

| Value | Behaviour |
|-------|-----------|
| `auto` | SkinsRestorer when installed; otherwise Mojang API |
| `skins-restorer` | SkinsRestorer only |
| `mojang` | Mojang only (online-mode, real nicks) |
| `off` | default Steve/Alex heads, no lookup |

### `fallback-skin` and `fake-seller-skin`

```yaml
seller-skins:
  source: skins-restorer
  fallback-skin: "DefaultHead"
  fake-seller-skin: "ServerLogo"
```

| Field | When |
|-------|------|
| `fallback-skin` | seller nick lookup failed → SR resolves this custom skin / nick / URL |
| `fake-seller-skin` | **all** synthetic/fake sellers use this skin; **nick is ignored** |

Values are SkinsRestorer skin names (`/sr set skin ServerLogo <url>`, etc.).

### Lookup chain (SkinsRestorer)

1. `findOrCreateSkinData(nick)` — or `fake-seller-skin` when the seller is synthetic and the field is set  
2. `findSkinData(fallback-skin)` — when configured  
3. `getSkinForPlayer(uuid, nick)` — SR `defaultSkins` from SkinsRestorer config  

With `source: mojang`, fallback uses the Mojang API by name.

### Startup warmup

Async prefetch into SkinsRestorer cache (~100 ms pause between requests):

- if `fake-seller-skin` is set — warmup that skin (+ `fallback-skin` when set);
- otherwise — all fake pool nicks + `fallback-skin`.

Console: `SkinsRestorer — prefetched X/Y fake seller skins`.

GUI lookup is async; the head updates when the texture arrives (no in-memory cache in SoulAuction).

## Admin GUI

`/ah admin` or `/ah admin gui [page]` — requires `soulauction.command.admin` or OP.

| Action | Result |
|--------|--------|
| **Left-click** an auction | open storefront as a player |
| **Right-click** an auction | auction settings |
| **Slot 22** | toggle fake activity (dye) |
| **Slot 49** | fake activity: pool and limits (read-only) |
| **Slot 45** | back to auction list |
| Book in the bottom row | create a new auction (chat wizard) |

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
- **Region market (WorldGuard):** `/ah regions`, `/ah rg`, `/regions` — see “Region market”.
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
- `/ah admin fake <nick> <auctionId> <price>` — synthetic listing under the given nick (item in hand; see fake activity).
- `/ah admin gui [page]` — auction list GUI (right-click an auction for settings).
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

### Region market (WorldGuard)

- `soulauction.command.regions` — `/ah regions`, `/regions`, aliases.
- `soulauction.region.sell` — list a region.
- `soulauction.region.buy` — buy on region market.

Plus auction currency nodes (`soulauction.buy.global`, …) — see “Region market”.

### Per-auction (set in `auctions/*.yml`)

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

### Server-wide chat announcements

In `config.yml` → `announcements` — **four separate toggles** (texts in `lang/messages_*.yml`):

| Block | Field | Event | Lang key |
|-------|-------|-------|----------|
| `items` | `broadcast-purchase` | player **bought** an item | `announce-item-purchase` |
| `items` | `broadcast-listing` | player **listed** an item | `announce-item-listing` |
| `regions` | `broadcast-purchase` | player **bought** a WG region | `region-announce-purchase` |
| `regions` | `broadcast-listing` | player **listed** a region | `region-announce-listing` |

Price filter: `min-purchase-price` / `min-listing-price` per block (`0` = every deal).  
Defaults: purchases on from 5000, listings off.

```yaml
announcements:
  items:
    broadcast-purchase: true
    broadcast-listing: true
    min-purchase-price: 5000
    min-listing-price: 0
  regions:
    broadcast-purchase: true
    broadcast-listing: false
    min-purchase-price: 10000
    min-listing-price: 0
```

Old keys `announcements.enabled` / `min-price` → use `items.broadcast-purchase` / `items.min-purchase-price`.

### Discord and Telegram

In `config.yml` → `notifications` section:

- **Discord:** `notifications.discord.enabled`, `webhookUrl` (channel Incoming Webhook).
- **Telegram:** `notifications.telegram.enabled`, `botToken`, `chatId`.
- Events: `notify-sold`, `notify-listed`, `notify-expired`, `min-price` filter.
- Delivery is **async** (Java HTTP), no shade, no bot on the Minecraft server.
- **Player avatars in Discord:** `notifications.discord.show-player-avatars` (seller head in author, buyer thumbnail on deals). CDN: `avatar-provider` = `MINOTAR` (default) or `CRAFATAR`.

After `/ah reload`, new URLs and flags are picked up.
