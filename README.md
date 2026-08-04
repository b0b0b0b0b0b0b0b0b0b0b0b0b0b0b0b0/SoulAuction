# SoulAuction

> [English version](docs/en/README.md)

**Один аукцион на всю сеть: несколько витрин, любая экономика, Folia и защита сделок — без fat-jar и без дюпов на клике.**

SoulAuction — аукцион для Paper **1.21+** и **Folia**, когда экономика не должна ломаться от region-threading, а «купил два раза» на прокси — недопустимо.

Один JAR: зависимости через Paper `libraries`, не shade. Меньше конфликтов с другими плагинами и проще обновлять.

## Зачем админу

**Несколько аукционов в одном плагине** — обычный, VIP, ивентовый, «за донат-валюту». У каждого своя валюта (Vault, PlayerPoints, опыт, CoinsEngine, предметы), свои права и свои правила. Не три разных AH и не зоопарк на синхронизацию.

**Folia first-class** — region-aware потоки, без legacy-scheduler на горячих путях. Для сети, которая уже на Folia или переезжает, это не «nice to have», а база.

**Мультисервер** — общая **MySQL** + **Redis**: один каталог по сети, распределённые локи на покупку/продажу, атомарные переходы лота в БД. Pub/sub обновляет витрину; истина в SQL, не в «кто последний записал файл».

**Игрокам — нормальный AH:** GUI, категории, сортировки, поиск и фильтры, избранные продавцы, hub «мои лоты / claim / история». Сообщения — MiniMessage, HEX, градиенты; тексты в `lang/messages_*.yml`.

**Экономика под контролем:** налоги с продавца и покупателя, bypass/discount по пермишенам, лимиты слотов по правам, cooldown, blacklist, запрет миров, whitelist/blacklist предметов, правила под ItemsAdder / Oraxen / MMOItems. TTL лотов; если инвентарь полный — предмет уходит в claim, а не в void.

**Операционка:** история сделок, audit, админ-GUI, purge, recover claim, Discord/Telegram на сделки (async HTTP, без бота на сервере). `/ah reload` подхватывает конфиг и сообщения.

**Для кого:** Paper/Folia-сеть, несколько режимов и валют, общий аукцион между прокси, анти-дюп важнее «ещё одной кнопки в GUI`.

## Сеть и анти-дюп (кратко)

| Режим | Назначение |
|--------|------------|
| `JSON` / `YAML` | один сервер, простой старт |
| `SQLITE` / `MYSQL` | персистентность; **MYSQL** — основа для прокси |
| Redis + pub/sub | локи между инстансами + инвалидация кэша каталога |

На прокси: **MYSQL + redis.enabled**. Покупка/снятие/истечение идут через claim лота (лок + Redis NX + `UPDATE … WHERE status='ACTIVE'`), откат при ошибке оплаты или полного инвентаря. **Sell:** предмет в escrow в GUI; листинг из инвентаря — strict-снятие до записи в БД (`/ah sell`, не дублировать клон без consume).

## Что умеет (список)

- Мульти-аукционы, раздельные `open` / `buy` / `sell` permissions.
- Валюты: `VAULT`, `PLAYER_POINTS`, `EXPERIENCE`, `COINS_ENGINE`, `ITEM`.
- Хранилище: `JSON`, `YAML`, `SQLITE`, `MYSQL`.
- TTL лотов (срок или «без срока»), claim, уведомление продавцу при истечении, комиссии per-auction, история sold/cancelled/expired.
- Лимиты лотов: `soulauction.<auctionId>.<N>`, `soulauction.all.<N>`.
- Приоритет в выдаче: `soulauction.priority.<N>`.
- Broadcast крупных продаж, внешние уведомления (Discord/Telegram).
- **Sell-GUI:** количество ±1, добор **таких же** предметов из инвентаря (до max stack); сравнение по полной сериализации (NBT, имя, чары), не «похожие» стаки. Escrow до подтверждения — возврат при отмене/закрытии.
- **Отображение цены per-auction:** свой знак валюты, до/после числа, MiniMessage и опционально PlaceholderAPI в символе (иконки из ресурспака / ItemsAdder и т.п.).
- **Автофейки (fake activity):** синтетические лоты от пула ников и предметов, включаются **per-auction**; пополнение по таймеру, ручной `/ah admin fake`, toggle в админ-GUI.
- **Скины продавцов:** головы в «Избранные продавцы» через SkinsRestorer / Mojang; fallback и общий скин для всех фейков (логотип сервера).
- **Рынок регионов (WorldGuard):** продажа и покупка WG-регионов за валюту аукциона; отдельные команды, GUI и права — см. раздел ниже.

## Отображение цен (`auctions/*.yml`)

У каждого аукциона свои поля (дефолты в Java, после первого старта — в `plugins/SoulAuction/auctions/global.yml` и др.):

| Поле | Назначение |
|------|------------|
| `currencySymbol` | Знак или иконка в GUI и чате. Пусто — формат экономики (Vault `$`, PlayerPoints `PP`, предметы `Nx MATERIAL`). |
| `currencySymbolPosition` | `BEFORE` или `AFTER` числа (`$100` или `100 ₽`). |
| `currencySymbolPlaceholderApi` | `true` — подставить `%...%` из PlaceholderAPI **для игрока, который видит цену** (нужен PlaceholderAPI). |
| `listingTtlSeconds` | Срок лота в секундах; **0 или меньше — без срока** (не истекает). |
| `world-guard-trade-regions` | **Ограничение по WG для предметов** в этом аукционе: открыть GUI, sell, buy — только стоя в одном из регионов. Пустой список `[]` = везде. **Не** рынок регионов (`region-market` в `config.yml`). Примеры ниже. |

Примеры `world-guard-trade-regions` (в `auctions/<id>.yml`):

```yaml
# без ограничения (по умолчанию)
world-guard-trade-regions: []

# регионы в текущем мире игрока (/rg list — id региона)
world-guard-trade-regions:
  - shop
  - market

# конкретный мир + регион
world-guard-trade-regions:
  - world:mall
  - world_nether:trade_hub
```

Нужен WorldGuard. Это **не** `/ah regions` — там продаются сами регионы WG как лоты.

### Срок лотов (TTL) и claim

- Пока лот **ACTIVE**, в витрине и в «Мои лоты» (если срок включён) в lore: **осталось / до какого времени**; в открытой витрине таймер обновляется раз в секунду.
- **Истёк срок, никто не купил:** лот снимается (`EXPIRED`), предмет один раз попадает в **claim** (`claims.json`), не в void.
- **Продавец:** в чат (онлайн сразу, оффлайн при входе) — что истекло и **[Забрать в меню]** → `/ah expired [auctionId]`; выдача предметов — **`/ah claim`** (меню просроченных только показывает, слоты не редактируются).
- **Выставление:** в чат несколько строк — товар, кол-во, цена, аукцион; отдельно блок про срок или «без срока» (`success-listed`, `success-listed-expiry-timed` / `success-listed-expiry-unlimited` в `lang/messages_*.yml`).
- На одном процессе: lock на `listingId` + при SQL переход `ACTIVE` → `EXPIRED`/`SOLD`/`CANCELLED` атомарный; claim снимается из буфера до выдачи в инвентарь.

Примеры `currencySymbol`:

- Текст: `₽`, `мон.`, `алм.`
- MiniMessage / glyph: `<glyph:coin>` (ItemsAdder, Nexo и аналоги — по доке ресурспака)
- PAPI (с `currencySymbolPlaceholderApi: true`): `%img_economy%`

Цена с кастомным символом показывается везде: лоты в GUI, sell-меню, фильтр цены, покупка, история, чат (`success-bought`, announce и т.д.).

## Сообщения (`plugins/SoulAuction/lang/`)

- `prefix` — префикс всех строк с `{prefix}`.
- Ключи — MiniMessage; `{price}` уже с форматом аукциона, в который смотрит игрок.
- **Несколько строк в чате:** ключ как **YAML-список** строк — плагин шлёт каждую строку отдельным сообщением (`MessageService.send`). Одна строка — как раньше. Поиск в чате, успех продажи, истечение лота, usage-команды — в этом формате.
- Плейсхолдеры lore лота в GUI: `{seller}`, `{price}`, `{id}`, `{auction}`, `{expires_in}`, `{expires_at}` (последние два — если TTL включён); опционально `listingLoreTemplate` в `auctions/*.yml`.

## PlaceholderAPI

Плейсы регистрируются автоматически, если PlaceholderAPI на сервере. Идентификатор — `soulauction`, т.е. `%soulauction_<параметр>%`. Числа форматируются под локаль игрока (разделители тысяч). У любого плейсхолдера есть вариант с суффиксом `_raw` — значение без форматирования (`1500000` вместо `1 500 000`, ключ enum вместо имени из lang) для скорбордов и плагинов, которым нужно голое число.

### Счётчики

| Плейсхолдер | Значение |
|---|---|
| `%soulauction_active_count%` | Активные лоты игрока (алиасы: `sell_count`, `purchasable_count`) |
| `%soulauction_expired_count%` | Просроченные предметы игрока (claim с причиной EXPIRED) |
| `%soulauction_total_active_count%` | Все лоты на аукционе |
| `%soulauction_sell_limit%` | Общий лимит лотов игрока (права + override) |
| `%soulauction_claims%` | Все предметы игрока в claim |
| `%soulauction_listings_all%` / `%soulauction_listings_<auctionId>%` | Активные лоты игрока (все / в конкретном аукционе) |
| `%soulauction_listings_total%` | Все лоты (без форматирования, legacy) |

### Настройки игрока

| Плейсхолдер | Значение |
|---|---|
| `%soulauction_selected_sorting%` | Выбранная сортировка |
| `%soulauction_selected_category%` | Выбранная категория |
| `%soulauction_selected_currency%` | Валюта последнего открытого аукциона (`currency-*` из lang) |
| `%soulauction_categories_enabled%`, `%soulauction_expired_items_enabled%`, `%soulauction_auction_listing_confirmation_enabled%` | Всегда `true` — эти системы в SoulAuction не отключаются |

### Статистика сделок

Персистентная (`data/stats.json`), при первом запуске один раз заполняется из существующей истории. Валюта в `<currency>` — тип экономики: `vault`, `player_points`, `experience`, `coins_engine`, `item`.

| Плейсхолдер | Значение |
|---|---|
| `%soulauction_items_sold%` | Продано предметов игроком |
| `%soulauction_items_purchased%` | Куплено предметов игроком |
| `%soulauction_money_made%` | Заработано игроком (после налога) |
| `%soulauction_money_spent%` | Потрачено игроком (с налогом покупателя) |
| `%soulauction_items_sold_<currency>%` и аналоги | То же, по конкретной валюте |
| `%soulauction_total_items_sold%`, `%soulauction_total_money_made%` и т.д. | Глобально по серверу, включая `_<currency>` |

## Автофейки (fake activity)

Синтетические лоты «оживляют» витрину: случайный ник из пула, случайный предмет, случайная цена в заданном диапазоне. Это **не** реальные игроки — покупка проходит как у обычного лота (деньги уходят «в никуда», предмет выдаётся покупателю). После покупки фейкового лота плагин через паузу может выставить новый.

### Включение per-auction

Глобального «вкл/выкл» в `config.yml` нет — только per-auction:

| Где | Поле / действие |
|-----|-----------------|
| `auctions/<id>.yml` | `fake-activity-enabled: true` / `false` (дефолт `false`) |
| `/ah admin` → **ПКМ** по аукциону | настройки аукциона |
| Меню → **слот 22** | вкл/выкл (`LIME_DYE` / `RED_DYE`) → `auctions/<id>.yml` |
| Меню → **слот 49** | пул, лимиты, тик (read-only) |

Пример в `auctions/global.yml`:

```yaml
fake-activity-enabled: true
```

### Пул (`plugins/SoulAuction/fake-activity/`)

Путь задаётся в `config.yml` → `fake-activity.directory` (дефолт `fake-activity`). После первого старта создаются три файла:

| Файл | Содержимое |
|------|------------|
| `settings.yml` | таймеры, лимиты, цены по умолчанию, `admin-fake` |
| `sellers.yml` | список ников-продавцов (до 16 символов) |
| `items.yml` | пул предметов с весами и диапазонами цен |

На fresh install в пул попадает **~120** ников и **~120** vanilla-предметов (руды, еда, инструменты, редкости 1.21) — править можно сразу в YAML.

#### `settings.yml` (главное)

| Поле | Назначение |
|------|------------|
| `initial-fill-listings` | сколько лотов создать сразу при старте (`0` = до `max-total-listings`) |
| `initial-delay-seconds` | пауза перед первым tick пополнения |
| `tick-interval-seconds` | как часто добивать лоты до лимитов |
| `after-purchase-delay-seconds` | пауза после покупки фейка перед новым лотом того же продавца |
| `min-price` / `max-price` | дефолтный диапазон, если у предмета в `items.yml` min/max = 0 |
| `price-variance-percent` | случайный разброс ±% к выбранной цене |
| `max-listings-per-auction` | потолок фейков на один `auctionId` |
| `max-total-listings` | потолок фейков на весь сервер |
| `listing-age-spread-seconds` | случайный «возраст» лота при создании (разные таймеры TTL) |
| `listings-per-tick` | сколько лотов пытаться создать за один tick |
| `auction-ids` | только эти аукционы (`[]` = все с `fake-activity-enabled`) |
| `admin-fake.register-seller` | после `/ah admin fake` дописать ник в `sellers.yml` |
| `admin-fake.register-item` | после `/ah admin fake` дописать предмет из руки в `items.yml` |

#### `items.yml` — поля записи

- `id` — имя для себя
- `material` / `amount` — vanilla-стек (игнорируется, если задан `item-base64`)
- `item-base64` — полный снимок предмета (NBT, custom items)
- `min-price` / `max-price` — `0` = из `settings.yml`
- `auction-ids` — пусто = любой аукцион; иначе только перечисленные
- `weight` — относительный шанс выбора (больше = чаще)

### Ручной фейк

```
/ah admin fake <ник> <auctionId> <цена>
```

Предмет — в **главной руке**. Лот создаётся сразу как synthetic. При включённых `admin-fake.*` ник и/или предмет дописываются в пул async.

### Поведение

- Фейки участвуют в сортировке, поиске, фильтрах как обычные лоты.
- Снять `/ah cancel` может только админ с `soulauction.command.cancel.any` (продавец — synthetic UUID).
- На прокси с MySQL+Redis фейки синхронизируются как обычные listing records.

## Рынок регионов (WorldGuard)

Продажа **регионов WorldGuard** через ту же экономику, что и предметный аукцион: цена списывается у покупателя, владение региона передаётся через WG. Лоты регионов хранятся в общей БД/файлах листингов.

### Требования

- **WorldGuard** на сервере (softdepend).
- В `config.yml` → `region-market.enabled: true`, затем **`/ah reload`** (или рестарт).
- У продавца регион должен быть **его** (owner в WG); у покупателя — деньги на выбранном аукционе.

### Включение (fresh install)

Дефолт в Java — `enabled: false`. После `true` создаётся папка `plugins/SoulAuction/regions/` (служебная).

```yaml
region-market:
  enabled: true
  hide-world-name: true
  ah-subcommand-aliases: [rg]
  standalone-commands: [regions]
  allowed-auction-ids: []
```

| Поле | Назначение |
|------|------------|
| `hide-world-name` | `true` — только id региона (`shop`), без `world:shop`. |
| `allowed-auction-ids` | Пусто = любой аукцион с sell в `auctions/*.yml`. |
| `ah-subcommand-aliases` | Короткие подкоманды `/ah`, напр. `rg` → `/ah rg sell …`. |
| `standalone-commands` | Верхний уровень: `/regions sell …`. **`rg` не добавляй**, если есть WG — их `/rg`; используй `/ah rg` или своё имя. |
| `max-listings-per-player` | Лимит лотов регионов; `0` = общие лимиты из `limits`. |

`standalone-commands` — только после **рестарта** сервера.

### Команды

| Команда | Действие |
|---------|----------|
| `/ah regions` | Рынок регионов (GUI). |
| `/ah rg` | То же (алиас). |
| `/regions` | То же (standalone). |
| `/rg sell …` | То же, если WorldGuard: перехват до WG (`sell`, `cancel`, `my`, `clear`). |
| `/ah regions sell <region> <auctionId> <price>` | Выставить, напр. `shop global 10000`. |
| `/ah regions sell` | Ввод в чате; отмена — `cancel`. |
| `/ah regions my` | GUI ваших лотов («Мои регионы»). |
| `/ah regions cancel <id>` | Снять лот. |
| `/ah regions clear` | Сброс незавершённого sell в чате. |

### GUI

Layout из `gui/general.yml` (как предметный AH). Регионы **не** в обычной витрине `/ah`. Клик по лоту — подтверждение покупки → смена owner в WG.

### Права (два слоя)

**Рынок регионов:**

| Право | Назначение |
|-------|------------|
| `soulauction.command.regions` | Команды рынка |
| `soulauction.region.sell` | Продать регион |
| `soulauction.region.buy` | Купить регион |

**Валюта аукциона** (`auctions/<id>.yml`): `soulauction.buy.global`, `soulauction.sell.global`, …

Покупка региона = **`region.buy`** + **`buy.<auctionId>`**.  
Продажа = **`region.sell`** + **`sell.<auctionId>`**.

Без LuckPerms — дефолты `true` в `plugin.yml`. С LP — выдавай ноды в группах сам.

## Скины продавцов (`config.yml` → `seller-skins`)

Текстуры **голов продавцов** в GUI «Избранные продавцы» (hub → звезда). Лоты в витрине показывают сам предмет, не голову.

SkinsRestorer — **softdepend**; без него на offline/cracked серверах Mojang по нику часто не сработает.

### `source`

| Значение | Поведение |
|----------|-----------|
| `auto` | SkinsRestorer, если установлен; иначе Mojang API |
| `skins-restorer` | только SkinsRestorer |
| `mojang` | только Mojang (online-mode, реальные ники) |
| `off` | дефолтные головы Steve/Alex, без lookup |

### `fallback-skin` и `fake-seller-skin`

```yaml
seller-skins:
  source: skins-restorer
  fallback-skin: "DefaultHead"
  fake-seller-skin: "ServerLogo"
```

| Поле | Когда |
|------|--------|
| `fallback-skin` | lookup по нику продавца не дал текстуру → SR ищет этот custom skin / nick / URL |
| `fake-seller-skin` | **все** synthetic/fake продавцы получают этот скин; **ник игнорируется** |

Значения — имена скинов из SkinsRestorer (`/sr set skin ServerLogo <url>` и т.п.).

### Цепочка lookup (SkinsRestorer)

1. `findOrCreateSkinData(ник)` — или `fake-seller-skin`, если продавец synthetic и поле задано  
2. `findSkinData(fallback-skin)` — если указан  
3. `getSkinForPlayer(uuid, nick)` — SR `defaultSkins` из конфига SkinsRestorer  

При `source: mojang` fallback тоже работает, но через Mojang API по имени.

### Прогрев при старте

Async prefetch в кэш SkinsRestorer (пауза ~100 ms между запросами):

- если задан `fake-seller-skin` — прогревается он (+ `fallback-skin`, если указан);
- иначе — все ники из пула фейков + `fallback-skin`.

В консоли: `SkinsRestorer — prefetched X/Y fake seller skins`.

Lookup при открытии GUI — async; голова обновляется, когда текстура пришла (без RAM-кеша в SoulAuction).

## Админ-GUI

`/ah admin` или `/ah admin gui [страница]` — нужен `soulauction.command.admin` или OP.

| Действие | Результат |
|----------|-----------|
| **ЛКМ** по аукциону | открыть витрину как игрок |
| **ПКМ** по аукциону | настройки аукциона |
| **Слот 22** | вкл/выкл автофейки (краситель) |
| **Слот 49** | автофейки: пул и лимиты (просмотр) |
| **Слот 45** | назад в список |
| Книга в нижнем ряду | создать новый аукцион |

## Команды

### Игроки
- `/ah` — открыть аукцион по умолчанию.
- `/ah <auctionId>` — открыть конкретный аукцион.
- `/ah sell <price>` — выставить предмет из руки в аукцион по умолчанию.
- `/ah sell <auctionId> <price>` — выставить в выбранный аукцион.
- `/ah sell <price> <auctionId>` — альтернативный порядок аргументов.
- `/ah my [auctionId]` — показать свои активные лоты.
- `/ah selling [auctionId]` — GUI активных лотов.
- `/ah expired [auctionId]` — GUI просроченных предметов (claim).
- `/ah purchased [auctionId]` — GUI истории покупок.
- `/ah history [auctionId]` — GUI истории продаж.
- `/ah search <текст>` — поиск: закрытие GUI, запрос **в чате** (кнопка отмены в сообщении); после ввода — обновлённая витрина и итог в чат.
- `/ah search <auctionId> <текст>` — то же в выбранном аукционе.
- `/ah search cancel` — отмена поиска и возврат в аукцион.
- `/ah page <номер> [auctionId]` — открыть нужную страницу аукциона.
- `/ah claim [all]` — забрать просроченные/снятые предметы.
- `/ah cancel <id>` — снять свой лот и вернуть предмет.
- **Рынок регионов (WorldGuard):** `/ah regions`, `/ah rg`, `/regions` — см. раздел «Рынок регионов».
- Алиасы команды настраиваются в `config.yml` через `commandAliases` (например `ax`, `auction`).

### Админы

- `/ah reload` — перезагрузка конфигов и сообщений.
- `/ah purge <days>` — очистка истории сделок старше N дней.
- `/ah admin history <player> [limit]` — история игрока в чат.
- `/ah admin selling <player> [auctionId]` — GUI активных лотов игрока.
- `/ah admin blacklist add|remove <player>` — runtime blacklist продажи.
- `/ah admin recover <claimId>` — выдать claim в инвентарь.
- `/ah admin audit [limit]` — последние audit-записи.
- `/ah admin cache stats|rebuild|invalidate` — кэш каталога.
- `/ah admin sellfor <player> <auctionId> <price>` — выставить лот от имени игрока (предмет в руке).
- `/ah admin fake <ник> <auctionId> <цена>` — synthetic-лот от указанного ника (предмет в руке; см. автофейки).
- `/ah admin gui [страница]` — GUI списка аукционов (ПКМ по аукциону — настройки).
- `/ah admin parse tags|nbt` — разбор NBT/тегов предмета в руке (custom items).
- `/ah view <player> [auctionId]` — GUI лотов игрока.

## Права

### Базовые

- `soulauction.command.ah` — использовать `/ah`.
- `soulauction.command.sell` — использовать `/ah sell`.
- `soulauction.command.reload` — использовать `/ah reload`.
- `soulauction.command.admin` — админ-команды и purge.
- `soulauction.priority.10` — выше приоритет в сортировке лотах (любое N).
- `soulauction.command.my` — использовать `/ah my`.
- `soulauction.command.claim` — использовать `/ah claim`.
- `soulauction.command.cancel.any` — снимать чужие лоты командой `/ah cancel`.

### Рынок регионов (WorldGuard)

- `soulauction.command.regions` — `/ah regions`, `/regions` и алиасы.
- `soulauction.region.sell` — выставить регион на продажу.
- `soulauction.region.buy` — купить регион на рынке.

Плюс права аукциона-валюты (`soulauction.buy.global`, `soulauction.sell.global`, …) — см. раздел «Рынок регионов».

### Аукцион-специфичные (задаются в `auctions/*.yml`)

Для каждого аукциона есть отдельные узлы:
- `openPermission` — право открыть этот аукцион.
- `buyPermission` — право покупать лоты в этом аукционе.
- `sellPermission` — право выставлять лоты в этот аукцион.

Пример:
- `soulauction.open.vip`
- `soulauction.buy.vip`
- `soulauction.sell.vip`

### Динамические лимиты лотов по правам

- `soulauction.<auctionId>.1` — лимит 1 активный лот в этом аукционе.
- `soulauction.<auctionId>.5` — лимит 5 активных лотов в этом аукционе.
- `soulauction.all.1` — общий лимит 1 на все аукционы.
- `soulauction.all.5` — общий лимит 5 на все аукционы.

Если выдано несколько узлов, берётся максимальный лимит.
Если узлов нет, берутся дефолты из `limits`.

### Налоги

- `soulauction.tax.bypass` — без налога продажи и покупки.
- `soulauction.tax.discount.<процент>` — скидка на налог продажи (берётся максимальная).

В `auctions/*.yml`: `saleTaxPercent` (с продавца), `buyTaxPercent` (с покупателя).

### Границы цены

- Глобально: `limits.minPrice`, `limits.maxPrice` в `config.yml`.
- Per-auction: `minPrice`, `maxPrice` (0 = взять глобальные).
- `soulauction.price.min.<цена>` — минимальная цена лота для игрока.
- `soulauction.price.max.<цена>` — максимальная цена лота для игрока.

### Объявления на весь сервер (чат)

В `config.yml` → `announcements` — **четыре отдельных переключателя** (тексты в `lang/messages_*.yml`):

| Блок | Поле | Событие | Lang-ключ |
|------|------|---------|-----------|
| `items` | `broadcast-purchase` | игрок **купил** предмет | `announce-item-purchase` |
| `items` | `broadcast-listing` | игрок **выставил** предмет | `announce-item-listing` |
| `regions` | `broadcast-purchase` | игрок **купил** регион WG | `region-announce-purchase` |
| `regions` | `broadcast-listing` | игрок **выставил** регион | `region-announce-listing` |

Фильтр по цене: `min-purchase-price` / `min-listing-price` в каждом блоке (`0` = все сделки).  
По умолчанию: покупки включены от 5000, выставление — выключено.

Пример:

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

Старые ключи `announcements.enabled` / `min-price` заменены на `items.broadcast-purchase` / `items.min-purchase-price`.

### Discord и Telegram

В `config.yml` → секция `notifications`:

- **Discord:** `notifications.discord.enabled`, `webhookUrl` (Incoming Webhook канала).
- **Telegram:** `notifications.telegram.enabled`, `botToken`, `chatId`.
- События: `notify-sold`, `notify-listed`, `notify-expired`, фильтр `min-price`.
- Отправка **асинхронная** (Java HTTP), без shade и без бота на сервере Minecraft.
- **Аватарки игроков в Discord:** `notifications.discord.show-player-avatars` (голова продавца в author, покупателя — thumbnail на сделках). CDN: `avatar-provider` = `MINOTAR` (дефолт) или `CRAFATAR`.

После `/ah reload` подхватываются новые URL и флаги.

