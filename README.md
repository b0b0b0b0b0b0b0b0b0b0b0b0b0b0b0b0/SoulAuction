# SoulAuction

**Один аукцион на всю сеть: несколько витрин, любая экономика, Folia и защита сделок — без fat-jar и без дюпов на клике.**

SoulAuction — аукцион для Paper **1.21+** и **Folia**, когда экономика не должна ломаться от region-threading, а «купил два раза» на прокси — недопустимо.

Один JAR: зависимости через Paper `libraries`, не shade. Меньше конфликтов с другими плагинами и проще обновлять.

## Зачем админу

**Несколько аукционов в одном плагине** — обычный, VIP, ивентовый, «за донат-валюту». У каждого своя валюта (Vault, PlayerPoints, опыт, CoinsEngine, предметы), свои права и свои правила. Не три разных AH и не зоопарк на синхронизацию.

**Folia first-class** — region-aware потоки, без legacy-scheduler на горячих путях. Для сети, которая уже на Folia или переезжает, это не «nice to have», а база.

**Мультисервер** — общая **MySQL** + **Redis**: один каталог по сети, распределённые локи на покупку/продажу, атомарные переходы лота в БД. Pub/sub обновляет витрину; истина в SQL, не в «кто последний записал файл».

**Игрокам — нормальный AH:** GUI, категории, сортировки, поиск и фильтры, избранные продавцы, hub «мои лоты / claim / история». Сообщения — MiniMessage, HEX, градиенты; в чате префикс **«Аукцион»** (настраивается в `messages.yml` → `prefix`), без названия плагина.

**Экономика под контролем:** налоги с продавца и покупателя, bypass/discount по пермишенам, лимиты слотов по правам, cooldown, blacklist, запрет миров, whitelist/blacklist предметов, правила под ItemsAdder / Oraxen / MMOItems. TTL лотов; если инвентарь полный — предмет уходит в claim, а не в void.

**Операционка:** история сделок, audit, админ-GUI, purge, recover claim, Discord/Telegram на сделки (async HTTP, без бота на сервере). `/ah reload` подхватывает конфиг и сообщения.

**Для кого:** Paper/Folia-сеть, несколько режимов и валют, общий аукцион между прокси, анти-дюп важнее «ещё одной кнопки в GUI».

> Не претендует на «всё из zAuctionHouse из коробки» (ставки, аренда, ProtocolLib-витрины — отдельный roadmap). Зато ядро заточено под современный стек, мульти-аукцион, сеть и честные сделки.

## Сеть и анти-дюп (кратко)

| Режим | Назначение |
|--------|------------|
| `JSON` / `YAML` | один сервер, простой старт |
| `SQLITE` / `MYSQL` | персистентность; **MYSQL** — основа для прокси |
| Redis + pub/sub | локи между инстансами + инвалидация кэша каталога |

На прокси: **MYSQL + redis.enabled**. Покупка/снятие/истечение идут через claim лота (лок + Redis NX + `UPDATE … WHERE status='ACTIVE'`), откат при ошибке оплаты или полного инвентаря.

## Что умеет (список)

- Мульти-аукционы, раздельные `open` / `buy` / `sell` permissions.
- Валюты: `VAULT`, `PLAYER_POINTS`, `EXPERIENCE`, `COINS_ENGINE`, `ITEM`.
- Хранилище: `JSON`, `YAML`, `SQLITE`, `MYSQL`.
- TTL лотов, claim, комиссии per-auction, история sold/cancelled/expired.
- Лимиты лотов: `soulauction.<auctionId>.<N>`, `soulauction.all.<N>`.
- Приоритет в выдаче: `soulauction.priority.<N>`.
- Broadcast крупных продаж, внешние уведомления (Discord/Telegram).
- **Отображение цены per-auction:** свой знак валюты, до/после числа, MiniMessage и опционально PlaceholderAPI в символе (иконки из ресурспака / ItemsAdder и т.п.).

## Отображение цен (`auctions/*.yml`)

У каждого аукциона свои поля (дефолты в Java, после первого старта — в `plugins/SoulAuction/auctions/global.yml` и др.):

| Поле | Назначение |
|------|------------|
| `currencySymbol` | Знак или иконка в GUI и чате. Пусто — формат экономики (Vault `$`, PlayerPoints `PP`, предметы `Nx MATERIAL`). |
| `currencySymbolPosition` | `BEFORE` или `AFTER` числа (`$100` или `100 ₽`). |
| `currencySymbolPlaceholderApi` | `true` — подставить `%...%` из PlaceholderAPI **для игрока, который видит цену** (нужен PlaceholderAPI). |

Примеры `currencySymbol`:

- Текст: `₽`, `мон.`, `алм.`
- MiniMessage / glyph: `<glyph:coin>` (ItemsAdder, Nexo и аналоги — по доке ресурспака)
- PAPI (с `currencySymbolPlaceholderApi: true`): `%img_economy%`

Цена с кастомным символом показывается везде: лоты в GUI, sell-меню, фильтр цены, покупка, история, чат (`success-bought`, announce и т.д.).

## Сообщения (`messages.yml`)

- `prefix` — префикс всех строк с `{prefix}` (дефолт: **Аукцион**, не имя плагина).
- Остальные ключи — MiniMessage; `{price}` уже с форматом аукциона, в который смотрит игрок.

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
- `/ah search <текст>` — поиск по продавцу/названию/материалу в аукционе по умолчанию.
- `/ah search <auctionId> <текст>` — поиск в выбранном аукционе.
- `/ah page <номер> [auctionId]` — открыть нужную страницу аукциона.
- `/ah claim [all]` — забрать просроченные/снятые предметы.
- `/ah cancel <id>` — снять свой лот и вернуть предмет.
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

### Аукцион-специфичные (задаются в `config.yml`)

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

### Discord и Telegram

В `config.yml` → секция `notifications`:

- **Discord:** `notifications.discord.enabled`, `webhookUrl` (Incoming Webhook канала).
- **Telegram:** `notifications.telegram.enabled`, `botToken`, `chatId`.
- События: `notify-sold`, `notify-listed`, `notify-expired`, фильтр `min-price`.
- Отправка **асинхронная** (Java HTTP), без shade и без бота на сервере Minecraft.
- **Аватарки игроков в Discord:** `notifications.discord.show-player-avatars` (голова продавца в author, покупателя — thumbnail на сделках). CDN: `avatar-provider` = `MINOTAR` (дефолт) или `CRAFATAR`.

После `/ah reload` подхватываются новые URL и флаги.

