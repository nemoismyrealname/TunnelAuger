# Tunnel Auger — изменения (overlay)

## Как применить
1. Распакуйте архив **поверх корня проекта** с заменой файлов (структура путей совпадает).
2. Вручную удалите два файла шаблона (в архив «удаление» не положить):
   - `src/main/java/com/example/tunnelauger/mixin/ExampleMixin.java`
   - `src/client/java/com/example/tunnelauger/client/mixin/ExampleClientMixin.java`
3. `./gradlew runClient`.

## Состав изменений

### Баланс и прогрессия
- **Износ 1:1** — площадная копка тратит ровно столько прочности, сколько блоков вскопано (`AugerMiningHandler`).
- Бур **не ломается** от площадной копки: при 1 прочности область перестаёт копаться (бюджет прочности).
- Прочность по тирам: **500 / 1561 / 2031 / 2500** (`AugerProgress.DURABILITY`, применяется через `AugerUpgrades.applyLevel`).
- Пороги апгрейда: **300 / 700 / 1500** блоков; в dev-окружении (`FabricLoader.isDevelopmentEnvironment()`) — **15** для быстрого теста.
- Стоимость ритуала: 1× Heart of the Sea + 8× золото / алмаз / алмазный блок (тир 1/2/3).
- Ремонт-материалы по тирам: железо / золото / алмаз / алмаз (`REPAIRABLE`).
- Скорости копания — именованные константы 6/8/9/10 (`TunnelAugerItem`).
- Все 4 тира бура — в креативной вкладке Tools & Utilities (`ModItems`).

### Площадная копка (QoL)
- **Shift отключает** площадную копку.
- Геометрия области вынесена в общий класс `AugerAreaShape` (сервер + клиентская обводка).
- **Уведомление о готовности к апгрейду**: actionbar + звук опыта (однократно при пересечении порога).
- В области копаются только блоки `#minecraft:mineable/pickaxe`; дроп с зачарованиями игрока (Fortune/Silk Touch).

### Ритуальный камень
- **Рецепты в JSON**: `data/tunnel_auger/ritual_recipes/*.json`, датапак-совместимо, перезагрузка по `/reload` (`RitualRecipeLoader`).
- Тестовый рецепт `stone_pickaxe_test.json` (3 булыжника → каменная кирка) — **удалить перед релизом**.
- **Плавное притягивание** предметов к камню: мягкое ускорение с демпфированием вместо прерывистых рывков (`BuildersStoneBlockEntity.attractItems`).

### Клиент (QoL)
- **Обводка области копания** — тонкая чёрная полупрозрачная рамка (`AugerAreaOutlineRenderer`), скрывается при Shift и на Tier 0.
- Тултип: материалы ритуала при готовности (Shift), единый символ рамки `┃`, ключи `line_ritual`, `msg_ready` в en/ru.

### Чистка шаблона
- `fabric.mod.json`: описание, авторы, убраны заглушки.
- Example-миксины удалены из кода и из `*.mixins.json`.

---

## Обновление v2 — исправления после первой компиляции

По маппированному jar из `.gradle/loom-cache` проверены реальные сигнатуры API 26.2:

1. **`AugerMiningHandler.java`** — `Player.displayClientMessage(msg, true)` в 26.2 удалён.
   Заменён на `player.sendOverlayMessage(msg)` (actionbar, проверено по jar).
2. **`AugerAreaOutlineRenderer.java`** — переписан полностью. `ShapeRenderer`/`LevelRenderer.renderLineBox`
   и `RenderType.lines()` в 26.2 не существуют (рендер переехал на RenderPipelines/feature-рендеры).
   Обводка теперь через новый ванильный API гизмо: `Minecraft.collectPerTickGizmos()` +
   `Gizmos.cuboid(AABB, GizmoStyle.stroke(0x66000000))`, хук — `ClientTickEvents.END_CLIENT_TICK`.

### Снято с контроля (проверено по jar — существуют)
- `Repairable` — пакет `net.minecraft.world.item.enchantment` ✓
- `AABB.encapsulatingFullBlocks` ✓, `Identifier.parse/fromNamespaceAndPath` ✓
- `BuiltInRegistries.ITEM.getValue/containsKey` ✓, `ResourceManager.listResources` ✓
- `hurtAndBreak(int, LivingEntity, EquipmentSlot)` ✓, `Block.dropResources(state, level, pos, be, entity, stack)` ✓
- `GsonHelper`, `Resource.openAsReader`, `HolderSet.direct`, `Minecraft.hitResult` (public) ✓
- `destroyBlock(pos, drop, entity)` (LevelWriter) ✓, `sendOverlayMessage(Component)` ✓

### Остаточные риски (проверить запуском)
- `ClientTickEvents.END_CLIENT_TICK` (fabric-lifecycle-events) — стабильный старый API, должен быть.
- Рендер гизмо в обычном геймплее: per-tick гизмо рисуются штатным `GizmoFeatureRenderer`;
  если обводка вдруг не видна — сообщите, подберём другой хук.
- `TunnelAugerEnchantmentMixin` (target `Enchantment.canEnchant`) — по-прежнему не проверен запуском.
- Efficiency, скорее всего, применяется автоматически поверх `getDestroySpeed` — проверить на зачарованном буре.

---

## Обновление v3 (ванильность и полировка)

1. **Убрано притягивание предметов к камню** (`BuildersStoneBlockEntity.java`):
   магнит удалён полностью (метод `attractItems` и его константы). Предметы падают
   и лежат как в ванилле; ритуал по-прежнему видит всё в зоне сканирования над камнем.
2. **Тултип без вертикальных полос** (`en_us.json`, `ru_ru.json`): убраны префиксы
   `§8┃` и лишние отступы — строки выглядят как ванильные атрибуты.
3. **Негативный отклик — один раз** (`BuildersStoneBlockEntity.java`):
   - шипение (частицы + звук) срабатывает только в момент, когда на камень падает
     предмет, не участвующий ни в одном рецепте и ни в одном ритуале;
   - пока предмет лежит — камень молчит; убрали и бросили снова — отклик повторится;
   - убрано шипение на «бур ещё не готов» и на лишние количества — теперь просто
     ничего не происходит;
   - вспомогательные методы `isPossibleSubsetOfAnyRecipe` / `hasExcessiveQuantities` /
     `isUpgradePending` удалены за ненадобностью.
4. **Ритуал последнего тира** (`AugerUpgrades.java`): 2→3 теперь
   **8 незеритовых слитков + сердце моря** (вместо 8 алмазных блоков).
   Тултип с материалами подхватывает это автоматически.

---

## Обновление v4

1. **Ремонт тира 3 — незеритовым слитком** (`AugerUpgrades.java`):
   железо → золото → алмаз → незерит. Применяется при апгрейде/создании
   предмета; уже существующие буры тира 3 в мире сохраняют старый
   ремонт-материал до пересоздания.
2. **Починены крафты на камне** (два исправления сразу):
   - `BuildersStoneBlockEntity.java`: зона ритуала была крошечной (±0.15 от центра) —
     рассчитана на магнит, который мы убрали в v3. Теперь зона покрывает
     всю верхнюю грань камня с запасом по краям — достаточно, чтобы предметы
     просто лежали на камне где угодно.
   - `TunnelAugerMod.java`: страховочная загрузка JSON-рецептов через
     `ServerLifecycleEvents.SERVER_STARTED` (напрямую из `server.getResourceManager()`,
     метод проверен по jar 26.2) — на случай, если reload-listener не срабатывает.
     В логе ищите строку «загружено рецептов ритуала: N».
   - В оверлей добавлен `RitualRecipe.java` (в прошлых архивах v2/v3 его не было).
3. **Обводка — только невоздушные блоки** (`AugerAreaOutlineRenderer.java`):
   вместо одного большого бокса на всю область теперь обводится каждый блок
   отдельно, воздух пропускается (`BlockState.isAir()` — проверен по jar).
   Позиции берутся из той же `AugerAreaShape.positions`, что и на сервере.

---

## Обновление v5

1. **Готовность к апгрейду — только звук** (`AugerMiningHandler.java`):
   убрано сообщение в actionbar, оставлен однократный звук
   (EXPERIENCE_ORB_PICKUP, пониженный тон). Ключ `msg_ready` удалён из обоих
   lang-файлов. Строка «▸ Готов!» в Shift-тултипе оставлена (информация
   по запросу, а не уведомление).

---

## Обновление v6 (баланс)

1. **Пороги апгрейда 300 / 1500 / 5000** (`AugerProgress.java`) — учитывают,
   что площадная копка засчитывает все блоки области. Dev-порог 15 без изменений.
2. **Истощение голода за площадную копку** (`AugerMiningHandler.java`):
   `causeFoodExhaustion(0.005f × блоков)` — ванильная ставка за блок
   (метод проверен по jar 26.2). В креативе не начисляется.
3. **Подсказка про Shift в тултипе** (`TunnelAugerTooltipHandler.java` + lang):
   без Shift под строкой тира показывается серая строка
   «Зажмите [Shift] — подробности» (ключ `line_hint`), при Shift она скрывается.
