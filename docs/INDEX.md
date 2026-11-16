# RP Inventory — указатель кода

Создано `python _family/tools/mapgen.py`, версия мода 0.1.1-1.20.1-forge.
**Руками не править**: пересоздаётся скриптом. Смысл, потоки работы и рецепты — в `docs/MAP.md`.
Разбор по шаблонам, без компиляции: необычно оформленная регистрация может отсутствовать в списках.

Всего: 26 файлов, 2159 строк Java.

## 1. Пакеты

| Пакет | Файлов | Строк |
|---|---:|---:|
| `faygolover.rpinventory` | 4 | 318 |
| `faygolover.rpinventory.client` | 3 | 262 |
| `faygolover.rpinventory.compat` | 2 | 116 |
| `faygolover.rpinventory.gametest` | 2 | 277 |
| `faygolover.rpinventory.limits` | 5 | 591 |
| `faygolover.rpinventory.mixin` | 6 | 295 |
| `faygolover.rpinventory.network` | 1 | 62 |
| `faygolover.rpinventory.server` | 3 | 238 |

## 2. Классы

### `faygolover.rpinventory`
- `InvCommand`, 142 — /rpinv: show — итог и из чего сложился; set — личная поправка ГМа; reset; curios — типы слотов Curios.
- `InvConfig`, 85 — Настройки сервера (serverconfig/rpinventory-server.toml мира; клиентам приходят сами).
- `InvProvider`, 40 — Розетка Core: ответы по итоговой ёмкости (InvState) — на сервере и на клиенте.
- `RpInventory`, 51 — RP Inventory — ограничение инвентаря семейства RP вместо InventoryFree.

### `faygolover.rpinventory.client`
- `ClientHotbar`, 106 — HUD-хотбар по открытым ячейкам: полоска короче и по центру, вторая рука и индикатор удара — у её краёв.
- `InvClient`, 33 — Клиент: хотбар по открытым ячейкам, закраска скрытых ячеек в окнах, сброс данных при выходе с сервера.
- `SlotHider`, 123 — Закрытые ячейки не видны: неактивный слот экран и так не рисует, а рамку из текстуры окна закрашиваем фоном окна.

### `faygolover.rpinventory.compat`
- `CuriosCompat`, 42 — Curios — необязателен: классы Curios трогает только CuriosLimits, и только если Curios стоит.
- `CuriosLimits`, 74 — Ограничение типов Curios собственным временным модификатором слотов на каждый тип (один UUID на тип).

### `faygolover.rpinventory.gametest`
- `CuriosTestHook`, 21 — Тест Curios: фальшивого игрока никто не тикает — применяем отложенные изменения размеров слотов сами.
- `InvGameTests`, 256 — Проверка логики без клиента: ./gradlew runGameTestServer (с Curios: -Pwith_curios=true).

### `faygolover.rpinventory.limits`
- `InvState`, 88 — Где лежит итоговая ёмкость игроков.
- `ItemModifiers`, 148 — Предметы-расширители из датапака: data/ /rpinventory/modifiers/*.json.
- `Limits` (record), 128 — Итоговая ёмкость инвентаря игрока: сколько ячеек каждой части открыто, какие части заморожены, поправки к типам Curios.
- `LimitsCalc`, 159 — Расчёт ёмкости (сервер): база (конфиг или личная поправка ГМа) + прибавки источников Core → пределы (меньшее) → заморозка.
- `Personal`, 68 — Личная поправка ГМа (/rpinv set): своя база части вместо конфига, свой предел типа Curios.

### `faygolover.rpinventory.mixin`
- `AbstractContainerMenuMixin`, 48 — Клики в любом меню: по закрытой ячейке — ничего, по своей замороженной — тоже (проверка и на сервере); цифра/F над слотом — не в закрытую/замороженную ячейку своего хотбара или второй руки.
- `InvWrapperMixin`, 33 — Обёртки Forge над инвентарём игрока (PlayerMainInvWrapper, PlayerArmorInvWrapper, ItemHandlerHelper.giveItemToPlayer, возврат вещей из Curios) идут через InvWrapper: в закрытые и замороженные ячейки не кладём, из закрытых не берём…
- `InventoryMixin`, 134 — Поиск места в инвентаре пропускает закрытые и замороженные ячейки: подбор, крафт, выдача модами, возврат вещей.
- `MinecraftMixin`, 20 — Клиент: цифры хотбара не выбирают закрытую или замороженную ячейку.
- `ServerGamePacketListenerImplMixin`, 26 — Сервер: клиент выбрал закрытую/замороженную ячейку хотбара — не принимаем и возвращаем ему прежнюю.
- `SlotMixin`, 34 — Слот любого меню над инвентарём игрока: закрытая у владельца ячейка — неактивна (экран её не рисует и не даёт навести), замороженная — видна, в неё не положить, а владелец не может и взять (другой — может).

### `faygolover.rpinventory.network`
- `InvNetwork`, 62 — Канал rpinventory:main: итоговая ёмкость игроков.

### `faygolover.rpinventory.server`
- `InvEvents`, 46 — Запреты без миксинов: смена рук (F) и надевание по ПКМ.
- `InvServer`, 97 — Сервер: пересчёт ёмкости раз в секунду и по просьбе (#refresh), рассылка, раскладка вещей из снятых ячеек, страховка (вещь в закрытой ячейке → переложить), выбранная ячейка хотбара.
- `Redistribute`, 95 — Вещи из снятых ячеек — в открытые: досыпать в такие же стопки → в пустые (сначала хранилище, потом хотбар) → у ног.

Классов без описания: 0 из 26.

## 3. Подписки на события

| Событие | Кто слушает |
|---|---|
| `ClientPlayerNetworkEvent.LoggingOut` | `InvClient.onLogout` |
| `ContainerScreenEvent.Render.Background` | `InvClient.onBackground`, `SlotHider.onBackground` |
| `FMLCommonSetupEvent` | `RpInventory.setup` |
| `LivingSwapItemsEvent.Hands` | `InvEvents.onSwapHands` |
| `PlayerEvent.PlayerChangeGameModeEvent` | `InvServer.onGameMode` |
| `PlayerEvent.PlayerChangedDimensionEvent` | `InvServer.onDimension` |
| `PlayerEvent.PlayerLoggedInEvent` | `InvServer.onLogin` |
| `PlayerEvent.PlayerLoggedOutEvent` | `InvServer.onLogout` |
| `PlayerEvent.PlayerRespawnEvent` | `InvServer.onRespawn` |
| `PlayerInteractEvent.RightClickItem` | `InvEvents.onRightClickItem` |
| `RenderGuiOverlayEvent.Pre` | `ClientHotbar.onOverlay`, `InvClient.onOverlay` |
| `ServerStoppedEvent` | `InvServer.onStopped` |
| `TickEvent.PlayerTickEvent` | `InvServer.onPlayerTick` |

## 4. Сеть

Канал: `faygolover/rpinventory/network/InvNetwork.java`, имя `main`, версия протокола `не объявлена константой`.
- `Sync` (направление не указано в регистрации)

Классы с обработчиками пакетов (1): `InvNetwork`.

## 5. Команды

- `faygolover/rpinventory/InvCommand.java`: `set` `curios` `rpinv` `show` `reset`

## 6. Данные

- getPersistentData: `Personal` ×4, теги: `rpinventory`

## 7. Конфиги

- server: `InvConfig`, вызовов define 10

## 8. Что берёт из RP Core и что ему даёт

Методы `RpCoreAPI` (сколько вызовов): `inventoryModifiers` 1, `registerCommand` 5, `registerInventory` 1, `registerInventoryModifier` 1, `registerModule` 1, `reportCompat` 1.

Реализует розетки: InventoryModifier ← `ItemModifiers`; InventoryProvider ← `InvProvider`.

События Core: `InventoryCapacityChangedEvent`.

## 9. Связи с другими модами

Проверки `isLoaded`: `curios` (CuriosCompat).

Пакеты других модов семейства в коде: `rpcore` — 9 файлов.

Миксины (6):
- `AbstractContainerMenuMixin` (mixins) → AbstractContainerMenu.class
- `InventoryMixin` (mixins) → Inventory.class
- `InvWrapperMixin` (mixins) → InvWrapper.class, remap = false
- `ServerGamePacketListenerImplMixin` (mixins) → ServerGamePacketListenerImpl.class
- `SlotMixin` (mixins) → Slot.class
- `MinecraftMixin` (client) → Minecraft.class

## 10. Зарегистрированные id

Нет или регистрируются не строковыми литералами.
