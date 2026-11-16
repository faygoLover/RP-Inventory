# RP Inventory — карта

Версия на момент написания: 0.1.0 (16.11.2026). Факты по классам и миксинам — `docs/INDEX.md`.

## 1. Что делает и чего не делает

Ограничение инвентаря: сколько ячеек хотбара, хранилища, брони, второй руки и слотов Curios открыто игроку.
Итог складывается из базы (конфиг или личная поправка ГМа), прибавок и запретов от других модов (особенности,
состояние тела) и предметов-расширителей из датапака (рюкзак, разгрузка). Закрытая ячейка не видна и не
принимает вещи; замороженная видна, но не работает. Замена мода InventoryFree.

За этим — не сюда:
- **почему** у игрока меньше ячеек (нет руки, особенность) — решает мод-источник через
  `RpCoreAPI.registerInventoryModifier`; здесь только сложение и применение;
- сами слоты Curios и их содержимое — Curios; здесь только временное ограничение числа слотов типа;
- узнать «надето ли» — `Worn` RP Core.

## 2. Как течёт работа

**Пересчёт.** `server/InvServer`, раз в секунду на игрока и сразу по просьбе (`RpCoreAPI.inventoryChanged` →
`refresh`): `limits/LimitsCalc` = база (`InvConfig` или `limits/Personal`) + все источники Core
(`RpCoreAPI.inventoryModifiers()`, в их числе свой `limits/ItemModifiers` — надетые и лежащие расширители из
датапака) → `limits/Limits` (итог по частям) → если изменился: `limits/InvState` (где лежит итог), событие
`InventoryCapacityChangedEvent`, пакет `Sync`, Curios — `compat/CuriosLimits`, вещи из снятых ячеек
раскладываются (`server/Redistribute`: в такие же стопки → в пустые → на землю).

**Применение — миксины.** закрытую и замороженную ячейку пропускает поиск места (`InventoryMixin`: подбор,
крафт, выдача), обёртки Forge (`InvWrapperMixin`), клики в любом меню (`AbstractContainerMenuMixin`), сам слот
неактивен (`SlotMixin`), выбор ячейки хотбара — на клиенте (`MinecraftMixin`) и на сервере
(`ServerGamePacketListenerImplMixin`). Без миксинов — `server/InvEvents` (смена рук, надевание по ПКМ).

**Ответ другим модам.** `InvProvider` — розетка `InventoryProvider` RP Core: открыта ли ячейка, заморожена ли —
на сервере и на клиенте (по присланному итогу).

**Клиент.** `client/ClientHotbar` (хотбар короче и по центру), `client/SlotHider` (закраска скрытых ячеек в окнах).

## 3. Карта пакетов

| Пакет | За что отвечает | С чего начать читать |
|---|---|---|
| `rpinventory` | вход, конфиг, команда, розетка Core | `RpInventory`, `InvConfig`, `InvCommand`, `InvProvider` |
| `limits` | расчёт и хранение итога | `LimitsCalc`, `Limits`, `InvState`, `Personal`, `ItemModifiers` |
| `server` | пересчёт, рассылка, раскладка, запреты | `InvServer`, `Redistribute`, `InvEvents` |
| `mixin` | применение ограничений в ванили и Forge | по имени цели: `InventoryMixin`, `SlotMixin`… |
| `compat` | Curios | `CuriosCompat` (вход, без классов Curios), `CuriosLimits` (классы Curios) |
| `network` | один пакет | `InvNetwork` |
| `client` | хотбар и скрытие ячеек | `InvClient`, `ClientHotbar`, `SlotHider` |
| `gametest` | игровые тесты | `InvGameTests`, `CuriosTestHook` |

## 4. Хочу добавить… — рецепты

| Что | Где | Образец | Не забыть |
|---|---|---|---|
| предмет, расширяющий инвентарь | датапак `data/<ns>/rpinventory/modifiers/*.json` | файлы мода там же | кода не нужно |
| поправку от другого мода (особенность, травма) | в том моде: реализация `InventoryModifier` + `RpCoreAPI.registerInventoryModifier`, при изменении — `RpCoreAPI.inventoryChanged(игрок)` | RP Perks `compat/CoreInventory`; Medicine | здесь править ничего не надо |
| новую часть инвентаря или правило сложения | `limits/Limits` (поле части) + `LimitsCalc` + применение в миксинах | броня | итог уходит клиенту в `Sync` — поле в пакет |
| место, где ячейка всё ещё «протекает» | новый миксин или событие в `server/InvEvents` | `InvWrapperMixin` | проверять и закрытые, и замороженные; чужое меню — через `AbstractContainerMenuMixin` |
| ограничение типа слотов Curios | `compat/CuriosLimits` | — | классы Curios — только в `CuriosLimits`; тесты с Curios: `-Pwith_curios=true` |
| настройку | `InvConfig` | база хотбара | — |
| команду | `InvCommand` | `/rpinv set` | `RpCoreAPI.registerCommand` |

## 5. Данные

| Что | Где хранится | Ключ | Что при смерти | Как попадает клиенту |
|---|---|---|---|---|
| личная поправка ГМа | игрок, ванильный раздел `PlayerPersisted`, внутри — `rpinventory` | `rpinventory` | остаётся (раздел переживает смерть сам) | в составе итога |
| итоговая ёмкость | память (`InvState`), пересчитывается | — | — | пакет `Sync` |

`PersistentTag` RP Core здесь не нужен: `PlayerPersisted` ваниль переносит сама. Ключ не менять.

## 6. Связи

- Берёт из Core: `inventoryModifiers()`; шлёт `InventoryCapacityChangedEvent`.
- Даёт в Core: `InventoryProvider` (`InvProvider`), свой `InventoryModifier` (`ItemModifiers`).
- Curios — необязателен; InventoryFree — мод, который этот заменяет (вместе не ставить).

## 7. Конфиги

`serverconfig/rpinventory-server.toml` — `InvConfig`, 10 ключей: база по частям и поведение. Клиентам приходит сам.

## 8. Сеть

Канал `rpinventory:main`, один пакет сервер → клиент `Sync` (итог). Клиент ничего не присылает; выбор закрытой
ячейки хотбара сервер отклоняет сам.

## 9. Ловушки

- **Закрыть ячейку надо везде сразу:** ваниль кладёт вещи в инвентарь десятком путей (подбор, крафт, shift-клик,
  выдача командой, обёртки Forge). Новый путь — новый миксин; тест `InvGameTests` перебирает известные.
- **Фальшивого игрока в тестах никто не тикает** — отложенные изменения слотов Curios применяет `CuriosTestHook`.
- **Curios меняет число слотов с задержкой** (на своём тике): сразу после пересчёта слот ещё прежний.
- **`InvWrapperMixin` — `remap = false`** (класс Forge): при обновлении Forge проверить, что цель на месте.
- Раскладка вещей из снятых ячеек идёт до закрытия: иначе вещи пропадут.

## 10. Тесты

`python ../_family/family.py verify --tests "RP Inventory"` — 9 игровых тестов (`InvGameTests`): расчёт, закрытые
ячейки не принимают вещи, раскладка, личная поправка. С Curios — `-Pwith_curios=true`. Вид на клиенте — в игре.

## 11. Документы в docs/

| Файл | Состояние |
|---|---|
| `MAP.md`, `INDEX.md`, `test_findings.md` | действуют |
| `00_plan.md` | действует: план и решения |
| `01_test_plan.md` | план проверки в игре (пройден Алексом 20.10) |
| `CLAUDE_old.md` | прежняя памятка: сборка и запуск — верно |
