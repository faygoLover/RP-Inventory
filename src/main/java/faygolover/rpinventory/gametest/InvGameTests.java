package faygolover.rpinventory.gametest;

import faygolover.rpcore.api.InventoryProvider.Part;
import faygolover.rpcore.api.RpCoreAPI;
import faygolover.rpinventory.RpInventory;
import faygolover.rpinventory.compat.CuriosCompat;
import faygolover.rpinventory.limits.InvState;
import faygolover.rpinventory.limits.Limits;
import faygolover.rpinventory.limits.Personal;
import faygolover.rpinventory.server.InvServer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.wrapper.PlayerMainInvWrapper;

/**
 * Проверка логики без клиента: {@code ./gradlew runGameTestServer} (с Curios: {@code -Pwith_curios=true}).
 * Игроки — фальшивые; пересчёт ёмкости зовём вручную ({@link InvServer#update}), их никто не тикает.
 * Конфиг — по умолчанию: хотбар 9, хранилище 9, броня и вторая рука открыты.
 */
@GameTestHolder(RpInventory.MODID)
@PrefixGameTestTemplate(false)
public final class InvGameTests {
    private InvGameTests() {}

    static final String T = "platform";
    private static int counter;

    static ServerPlayer player(GameTestHelper h, String prefix) {
        var server = h.getLevel().getServer();
        var profile = new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), prefix + (counter++));
        ServerPlayer p = new ServerPlayer(server, h.getLevel(), profile);
        net.minecraft.network.Connection conn = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(conn);
        server.getPlayerList().placeNewPlayer(conn, p);
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        p.getInventory().clearContent();
        Vec3 pos = h.absoluteVec(new Vec3(1.5, 1, 1.5));
        p.moveTo(pos.x, pos.y, pos.z, 0, 0);
        InvServer.update(p);
        return p;
    }

    static void remove(GameTestHelper h, ServerPlayer p) {
        Personal.reset(p);
        if (!p.isRemoved()) h.getLevel().getServer().getPlayerList().remove(p);
    }

    static void check(GameTestHelper h, boolean ok, String what) {
        if (!ok) h.fail(what);
    }

    /** Подбор/выдача: только в открытые ячейки (хотбар 0–8, хранилище 9–17), остальное не влезает. */
    @GameTest(template = T)
    public static void addSkipsClosedSlots(GameTestHelper h) {
        ServerPlayer p = player(h, "rpi-add-");
        Inventory inv = p.getInventory();
        ItemStack big = new ItemStack(Items.COBBLESTONE, 64 * 30);
        inv.add(big);
        for (int i = 0; i < 18; i++) check(h, inv.getItem(i).getCount() == 64, "ячейка " + i + " должна быть полной");
        for (int i = 18; i < 36; i++) check(h, inv.getItem(i).isEmpty(), "закрытая ячейка " + i + " заполнена");
        check(h, big.getCount() == 64 * 12, "остаток " + big.getCount() + " вместо " + 64 * 12);
        remove(h, p);
        h.succeed();
    }

    /** Вещь в закрытой ячейке переезжает в первую свободную ячейку хранилища (хранилище раньше хотбара). */
    @GameTest(template = T)
    public static void redistributeMovesToStorage(GameTestHelper h) {
        ServerPlayer p = player(h, "rpi-move-");
        Inventory inv = p.getInventory();
        inv.setItem(30, new ItemStack(Items.DIAMOND, 5));
        InvServer.update(p);
        check(h, inv.getItem(30).isEmpty(), "закрытая ячейка 30 не освобождена");
        check(h, inv.getItem(9).is(Items.DIAMOND) && inv.getItem(9).getCount() == 5, "алмазы не в ячейке 9: " + inv.getItem(9));
        remove(h, p);
        h.succeed();
    }

    /** Места нет — выпадает у ног. */
    @GameTest(template = T)
    public static void redistributeDropsWhenFull(GameTestHelper h) {
        ServerPlayer p = player(h, "rpi-drop-");
        Inventory inv = p.getInventory();
        for (int i = 0; i < 18; i++) inv.setItem(i, new ItemStack(Items.DIRT, 64));
        inv.setItem(30, new ItemStack(Items.EMERALD, 3));
        InvServer.update(p);
        check(h, inv.getItem(30).isEmpty(), "закрытая ячейка 30 не освобождена");
        boolean dropped = !h.getLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(4),
                e -> e.getItem().is(Items.EMERALD) && e.getItem().getCount() == 3).isEmpty();
        check(h, dropped, "изумруды не выпали");
        remove(h, p);
        h.succeed();
    }

    /** Источник Core: +3 хранилища, вторая рука снята, хотбар заморожен. */
    @GameTest(template = T)
    public static void coreModifierApplies(GameTestHelper h) {
        RpCoreAPI.registerInventoryModifier(new ResourceLocation(RpInventory.MODID, "gametest"), (player, ch) -> {
            if (!player.getGameProfile().getName().startsWith("rpi-mod-")) return;
            ch.add(Part.STORAGE, 3);
            ch.close(Part.OFFHAND);
            ch.freeze(Part.HOTBAR);
        });
        ServerPlayer p = player(h, "rpi-mod-");
        Inventory inv = p.getInventory();
        inv.setItem(0, new ItemStack(Items.STICK));
        Limits l = InvState.get(p);
        check(h, l.capacity(Part.STORAGE) == 12, "хранилище " + l.capacity(Part.STORAGE) + " вместо 12");
        check(h, !l.open(Inventory.SLOT_OFFHAND), "вторая рука открыта");
        check(h, l.frozen(0) && l.open(0), "хотбар не заморожен");
        // Заморожен: клик по ячейке хотбара в меню инвентаря (слот 36 = ячейка 0) ничего не берёт.
        InventoryMenu menu = p.inventoryMenu;
        menu.clicked(36, 0, ClickType.PICKUP, p);
        check(h, menu.getCarried().isEmpty() && inv.getItem(0).is(Items.STICK), "взял вещь из замороженной ячейки");
        check(h, !menu.slots.get(36).mayPickup(p), "mayPickup у замороженной ячейки");
        // Другой игрок (медик при обыске) из замороженной ячейки взять может — меню над тем же инвентарём.
        ServerPlayer medic = player(h, "rpi-medic-");
        InventoryMenu foreign = new InventoryMenu(inv, false, medic);
        foreign.clicked(36, 0, ClickType.PICKUP, medic);
        check(h, foreign.getCarried().is(Items.STICK) && inv.getItem(0).isEmpty(), "медик не взял вещь из замороженной ячейки");
        inv.setItem(0, foreign.getCarried());
        foreign.setCarried(ItemStack.EMPTY);
        remove(h, medic);
        // Подбор не идёт в замороженный хотбар — сразу в хранилище.
        inv.add(new ItemStack(Items.APPLE));
        check(h, inv.getItem(9).is(Items.APPLE), "яблоко не в ячейке 9: " + inv.getItem(9));
        RpCoreAPI.registerInventoryModifier(new ResourceLocation(RpInventory.MODID, "gametest"), (player, ch) -> {});
        remove(h, p);
        h.succeed();
    }

    /** Shift-клик шлема при закрытой ячейке шлема — в шлем не попадает. Цифра над слотом — не в закрытый хотбар. */
    @GameTest(template = T)
    public static void menuRespectsClosedSlots(GameTestHelper h) {
        ServerPlayer p = player(h, "rpi-menu-");
        Personal.setPart(p, Part.HEAD, 0);
        Personal.setPart(p, Part.HOTBAR, 2);
        InvServer.update(p);
        Inventory inv = p.getInventory();
        InventoryMenu menu = p.inventoryMenu;
        inv.setItem(9, new ItemStack(Items.IRON_HELMET));
        menu.quickMoveStack(p, 9);
        check(h, inv.getItem(39).isEmpty(), "шлем надет в закрытую ячейку");
        // Цифра 5 над слотом хранилища 10: ячейка хотбара 4 закрыта.
        inv.setItem(10, new ItemStack(Items.BREAD));
        menu.clicked(10, 4, ClickType.SWAP, p);
        check(h, inv.getItem(4).isEmpty() && inv.getItem(10).is(Items.BREAD), "цифра переложила в закрытый хотбар");
        // Скрытый слот меню неактивен (экран его не рисует).
        check(h, !menu.slots.get(5).isActive(), "слот шлема активен");
        check(h, !menu.slots.get(40).isActive() && menu.slots.get(37).isActive(), "активность слотов хотбара");
        remove(h, p);
        h.succeed();
    }

    /** Обёртка Forge (выдача модами, возврат из Curios) не кладёт в закрытые ячейки. */
    @GameTest(template = T)
    public static void forgeWrapperRespectsClosedSlots(GameTestHelper h) {
        ServerPlayer p = player(h, "rpi-wrap-");
        PlayerMainInvWrapper w = new PlayerMainInvWrapper(p.getInventory());
        ItemStack rest = w.insertItem(30, new ItemStack(Items.GOLD_INGOT, 4), false);
        check(h, rest.getCount() == 4 && p.getInventory().getItem(30).isEmpty(), "положил в закрытую ячейку 30");
        rest = w.insertItem(12, new ItemStack(Items.GOLD_INGOT, 4), false);
        check(h, rest.isEmpty() && p.getInventory().getItem(12).getCount() == 4, "не положил в открытую ячейку 12");
        remove(h, p);
        h.succeed();
    }

    /** Ёмкость уменьшилась (личная поправка ГМа) — выбранная закрытая ячейка хотбара сменилась на открытую. */
    @GameTest(template = T)
    public static void selectedMovesOffClosedSlot(GameTestHelper h) {
        ServerPlayer p = player(h, "rpi-sel-");
        p.getInventory().selected = 7;
        Personal.setPart(p, Part.HOTBAR, 3);
        InvServer.update(p);
        check(h, p.getInventory().selected == 2, "выбрана " + p.getInventory().selected + " вместо 2");
        remove(h, p);
        h.succeed();
    }

    /** Curios (если стоит): предел типа 0 убирает его слоты модификатором; сброс возвращает. */
    @GameTest(template = T)
    public static void curiosLimit(GameTestHelper h) {
        if (!CuriosCompat.loaded()) {
            h.succeed();
            return;
        }
        ServerPlayer p = player(h, "rpi-curios-");
        String type = null;
        for (CuriosCompat.TypeInfo t : CuriosCompat.types(p)) {
            if (t.slots() > 0) {
                type = t.type();
                break;
            }
        }
        RpInventory.LOGGER.info("RP Inventory gametest: типы Curios {}, проверяю {}", CuriosCompat.types(p).size(), type);
        if (type == null) {
            remove(h, p);
            h.succeed();
            return;
        }
        String fType = type;
        int before = slots(p, fType);
        Personal.setCurios(p, fType, 0);
        InvServer.update(p);
        CuriosTestHook.flush(p);
        check(h, slots(p, fType) == 0, "у типа " + fType + " осталось " + slots(p, fType) + " слотов");
        Personal.reset(p);
        InvServer.update(p);
        CuriosTestHook.flush(p);
        check(h, slots(p, fType) == before, "у типа " + fType + " " + slots(p, fType) + " слотов вместо " + before);
        remove(h, p);
        h.succeed();
    }

    /**
     * Предмет-расширитель из датапака (тестовый пакет в {@code run/world/datapacks}: кожаный нагрудник на груди —
     * хранилище +3, пояс Curios +1). Без пакета — пропуск.
     */
    @GameTest(template = T)
    public static void itemModifierFromDatapack(GameTestHelper h) {
        if (faygolover.rpinventory.limits.ItemModifiers.INSTANCE.size() == 0) {
            h.succeed();
            return;
        }
        ServerPlayer p = player(h, "rpi-item-");
        p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(Items.LEATHER_CHESTPLATE));
        InvServer.update(p);
        Limits l = InvState.get(p);
        check(h, l.capacity(Part.STORAGE) == 12, "хранилище " + l.capacity(Part.STORAGE) + " вместо 12");
        check(h, l.curiosRule("belt").add() == 1, "пояс Curios: " + l.curiosRule("belt"));
        // В руке, а не на груди — не считается.
        p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, ItemStack.EMPTY);
        p.getInventory().setItem(0, new ItemStack(Items.LEATHER_CHESTPLATE));
        InvServer.update(p);
        check(h, InvState.get(p).capacity(Part.STORAGE) == 9, "нагрудник в руке дал слоты");
        remove(h, p);
        h.succeed();
    }

    private static int slots(ServerPlayer p, String type) {
        for (CuriosCompat.TypeInfo t : CuriosCompat.types(p)) if (t.type().equals(type)) return t.slots();
        return -1;
    }
}
