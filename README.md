# SoulAuction

Современный аукцион-плагин для Paper 1.21+ с GUI, категориями, сортировкой, мульти-аукционами и раздельной экономикой.

## Возможности

- Несколько независимых аукционов с разными ID.
- Отдельная валюта на каждый аукцион: `VAULT` или `PLAYER_POINTS`.
- Выбор хранилища: `JSON`, `YAML`, `SQLITE`, `MYSQL`.
- SQL-режимы работают через асинхронный пул соединений.
- Redis lock перед продажей (для `MYSQL`) для дополнительной anti-dupe защиты.
- Раздельные права на открытие, покупку и выставление в каждом аукционе.
- TTL лотов с автопросрочкой и отправкой предметов в claim-хранилище.
- Комиссия продажи на уровне каждого аукциона.
- История сделок и действий (sold/cancelled/expired).
- Blacklist материалов по каждому аукциону.
- Динамические лимиты лотов по правам:
  - `soulauction.<auctionId>.<N>`
  - `soulauction.all.<N>`
- GUI на `InventoryHolder` без проверки по title.
- Защита от двойной покупки (анти-дюп на уровне удаления лота перед транзакцией).
- MiniMessage + HEX + градиенты в сообщениях.

## Требования

- Java 21
- Paper 1.21+
- Vault (для аукционов с экономикой `VAULT`)
- PlayerPoints (для аукционов с экономикой `PLAYER_POINTS`)

## Сборка

```bash
./gradlew build
```

Готовый jar появится в `build/libs/`.

## Команды

### Игроки

- `/ah` — открыть аукцион по умолчанию.
- `/ah <auctionId>` — открыть конкретный аукцион.
- `/ah sell <price>` — выставить предмет из руки в аукцион по умолчанию.
- `/ah sell <auctionId> <price>` — выставить в выбранный аукцион.
- `/ah sell <price> <auctionId>` — альтернативный порядок аргументов.
- `/ah my [auctionId]` — показать свои активные лоты.
- `/ah claim [all]` — забрать просроченные/снятые предметы.
- `/ah cancel <id>` — снять свой лот и вернуть предмет.
- Алиасы команды настраиваются в `config.yml` через `commandAliases` (например `ax`, `auction`).

### Админы

- `/ah reload` — перезагрузка конфигов и сообщений.

## Права

### Базовые

- `soulauction.command.ah` — использовать `/ah`.
- `soulauction.command.sell` — использовать `/ah sell`.
- `soulauction.command.reload` — использовать `/ah reload`.
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

## Конфиги аукционов

```yaml
defaultAuctionId: global

storage:
  mode: "MYSQL"
  flatDirectory: "data/listings"
  database:
    host: "127.0.0.1"
    port: 3306
    database: "soulauction"
    username: "root"
    password: "password"
    poolSize: 8
    sqliteFile: "data/auction.db"
  redis:
    enabled: true
    host: "127.0.0.1"
    port: 6379
    password: ""
    database: 0
    timeoutMs: 1500
    sellLockMillis: 2500

limits:
  defaultMaxActiveListingsPerAuction: 3
  defaultMaxActiveListingsGlobal: 6
  maxPrice: 500000
  allowSelfBuy: false
  allowSelling: true

auctionsDirectory: "auctions"
```

Каждый аукцион лежит отдельным файлом в папке `auctions/`, например:

`auctions/global.yml`
```yaml
id: "global"
displayName: "Глобальный"
economy: "VAULT"
buyEnabled: true
sellEnabled: true
listingTtlSeconds: 86400
saleTaxPercent: 5.0
blockedMaterials: ["BEDROCK", "BARRIER"]
openPermission: "soulauction.open.global"
buyPermission: "soulauction.buy.global"
sellPermission: "soulauction.sell.global"
```
