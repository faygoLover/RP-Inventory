# RP Inventory — передача в отдельный чат (2026-10-20)

Начни с этого файла, потом [docs/00_plan.md](docs/00_plan.md) — там решения Алекса, устройство, настройки, порядок
работ, ответы на О1–О3 и **§10 «Состояние»** (20.10: шаги 1–7 сделаны, в игре не проверено; план проверки —
[docs/01_test_plan.md](docs/01_test_plan.md)).

Проверка кодом: `./gradlew runGameTestServer -Pwith_curios=true` (и без флага — без Curios).

## Кто и как работает
- Пользователь — **Алекс** (faygoLover), автор модов и сервера «Департамент S5». Отвечать **по-русски**, всегда.
- Делать **только обсуждённое**; не «улучшать» работающее без спроса.
- **Перед любой проверкой в игре** (особенно визуальной) — спросить Алекса и дать короткий план проверки. Визуально
  проверяет он, ты — кодом и скриптами (сборка, чтение кода, декомпиляция, логи, GameTest). Клиентов для «посмотреть»
  сам не запускай.
- Коммиты — **локально**, в GitHub не пушить без просьбы. В конце сообщения коммита:
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Сначала договориться по открытым вопросам плана (§9), потом код.

## Окружение
- Рабочая папка: `C:\Users\Alex\Desktop\Claude Code\` — соседние репозитории семейства RP: `RP Core`, `RP Perks`,
  `RP Stamina`, `RP Medicine (Health revork)`, `RP Culinary`, `RP PDA and communications`, `RP Stamina Sports`, `RP Fixes`.
- Forge 1.20.1 (47.4.x), официальные маппинги, Java 17:
  `JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.14.7-hotspot" ./gradlew build -x test --console=plain -q`.
  `java` в PATH — 25, не он.
- **Образец сборки** с RP Core: `RP Stamina Sports/build.gradle` + `gradle.properties` — Core лежит jar-ом в `libs/`
  (`flatDir`, `fg.deobf("blank:rpcore:${rpcore_version}")`), там же флаги запуска с семейством (`-Pwith_family`).
  Свежий Core: `RP Core/build/libs/rpcore-0.6.0-1.20.1-forge.jar` (0.6.0 — с «замком» семейства, канал версии 4).
- Грабли ForgeGradle: пересобрал зависимость с той же версией — удалить
  `~/.gradle/caches/forge_gradle/deobf_dependencies/blank/<мод>/`.
- Исходники MC/Forge и декомпилятор: `_mcref/` (`forge-1.20.1-47.4.13/src`, `tools/decompile.ps1 <jar> -Name x` →
  `_mcref/mods/x`). Уже разобраны: `inventoryfree-3.2.1`, `exposure-1.9.21`, `cuffed-1.3.13`, `emf-3.3.7`, `pal-1.0.2`.
  Искать с явным путём (`_mcref` скрыт от общего поиска).
- Чужие моды в dev-запуске: jar из сборки, через `fg.deobf` и `flatDir` (так проверяли заплатки RP Fixes на Custom NPCs
  и InventoryFree). Jar сборки — `D:\ElyPrismLauncher\instances\DEP_S5_admin\minecraft\mods`.

## Что знать про RP Core
- Розетки — интерфейсы в `RP Core/src/main/java/faygolover/rpcore/api/` (образец — `StaminaProvider`), регистрация и
  доступ — `RpCoreAPI` (`registerStamina` / `stamina()`). Сделай `InventoryProvider` так же.
- Сеть Core: `network/CoreNetwork.java`, `VERSION = "4"`. Меняешь протокол — поднимай версию (сервер и все клиенты
  переходят на новый Core вместе).
- Замок семейства (Core 0.6.0): предметы RP-модов скрыты из креатива/JEI без ключа; `RP Core/lock.local.properties` —
  только локально, **не удалять и не коммитить**.
- План/история Core: `RP Core/docs/00_plan.md`.

## Связанные места в других модах
- **RP Perks** — `Config.java`: `inventory_slots_command = "inventory_slots set_available {player} {value}"`,
  `Stat.INVENTORY_SLOTS`, `ADMIN_INVENTORY_SLOTS = 36`; особенности `Perk.ORGANIZED` (+3) / `Perk.DISORGANIZED` (−3).
  Сейчас слоты меняются командой InventoryFree — перевести на Core.
- **RP Medicine** — обыск: `menu/SearchMenu.java`, `server/SearchService.java` (медик кладёт вещи пациента в закрытые
  ячейки — находка 20.10 в `docs/test_findings.md`). Выброс предмета из отсутствующей руки — `server/EffectsApplier.java`
  (конец `apply`, ячейки 9–35 без учёта закрытых). Руки/переломы — `core/GameplayEffects.java` (`armsDisabled`,
  `mainArmBad`, `offArmBad`), ампутации — `core/Limbs.java`, `BodyPartState.missing/prosthesis`.
- **RP Fixes** — заплатка InventoryFree `mixin/inventoryfree/SlotBlockerMixin.java` (`fixes.inventoryfree_keep_slots`) —
  убрать, когда RP Inventory заменит InventoryFree.
- **InventoryFree 3.2.1** (как он работает): `_mcref/mods/inventoryfree-3.2.1/kirderf1/inventoryfree/` — `SlotBlocker`
  (подмена слотов), `InventoryEnforcer` (уборка по тику), `BlockedSlot`. Настройки на сервере: 18 ячеек, без предмета
  открытия.

## Сборка и выдача
- Сборка S5: репозиторий `S5 Packs` (`mods.toml`, слои). Добавить/обновить свой мод: `python tools/add.py <jar>`
  (кладёт в `local-mods/`), убрать: `python tools/add.py --remove <modid>`. Выпуск — Алекс кнопкой в панели.
- Тестовый инстанс Алекса — `DEP_S5_admin` (синхронизация с сервером сейчас **выключена**: `PreLaunchCommand` пуст,
  оригинал — `instance.cfg.bak-sync-2026-10-19`; `instance.cfg` править только при закрытом лаунчере). Старые jar при
  замене — в `minecraft/mods_backup_2026-10-19`.
- Живой сервер — только через выпуск в панели; сервер без спроса не трогать.

## Слоты Curios в сборке (на 20.10, для справки — в коде брать у Curios)
armband, back, back_plate, backpack, belt, body, boots, bracelet, charm, curio, dog_tag, feet, front_plate, hands, head,
heart, iff, legs, mask, mouth, necklace, pda, rigs, ring, tactical_terminal, uniform_pants, uniform_top.
