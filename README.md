# SoulAuction

Современный аукцион-плагин для **Paper 1.21+ и Folia** с region-aware потоками, GUI, категориями, сортировкой, мульти-аукционами и раздельной экономикой.

## Возможности

- **Folia / region threading:** без `BukkitScheduler`; sync через `PluginSchedulers`. **Hot path (клик GUI)** — только in-memory lock + `ListingLockRunner`, без сетевого I/O на main/region. Redis pub/lock — **async**, best-effort для сети.
- Несколько независимых аукционов с разными ID.
- Отдельная валюта на каждый аукцион: `VAULT`, `PLAYER_POINTS`, `EXPERIENCE`, `COINS_ENGINE`, `ITEM`.
- Выбор хранилища: `JSON`, `YAML`, `SQLITE`, `MYSQL`.
- SQL-режимы работают через асинхронный пул соединений.
- Redis lock перед продажей (для `MYSQL`) для дополнительной anti-dupe защиты.
- Раздельные права на открытие, покупку и выставление в каждом аукционе.
- TTL лотов с автопросрочкой и отправкой предметов в claim-хранилище.
- Комиссия продажи на уровне каждого аукциона.
- История сделок и действий (sold/cancelled/expired).
- Whitelist материалов (глобальный режим) + blacklist по аукциону.
- Cooldown продажи, blacklist игроков, запрет продажи в выбранных мирах.
- Кэш листингов + Redis pub/sub инвалидация между серверами (`storage.redis.pubSubEnabled`).
- GUI: поиск через чат, избранные продавцы, фильтр цены, Shift+ПКМ — в избранное.
- Приоритет сортировки лотов: `soulauction.priority.N`.
- Broadcast крупных продаж (`announcements` в config).
- Админ: history/selling/blacklist/recover/audit, `/ah purge <days>`.
- Поиск учитывает ItemsAdder/Oraxen/MMOItems PDC (best-effort).
- Динамические лимиты лотов по правам:
  - `soulauction.<auctionId>.<N>`
  - `soulauction.all.<N>`
- GUI на `InventoryHolder` без проверки по title.
- Защита от двойной покупки (анти-дюп на уровне удаления лота перед транзакцией).
- MiniMessage + HEX + градиенты в сообщениях.

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

