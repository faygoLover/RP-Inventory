package faygolover.rpinventory.server;

import faygolover.rpinventory.RpInventory;
import faygolover.rpinventory.limits.Limits;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Вещи из снятых ячеек — в открытые: досыпать в такие же стопки → в пустые (сначала хранилище, потом хотбар) → у ног.
 * Замороженные ячейки не трогаем: вещь в них остаётся, но и класть туда нельзя.
 */
public final class Redistribute {
    private Redistribute() {}

    /** Обычный порядок поиска места: хранилище 9–35, потом хотбар 0–8. */
    private static final int[] ORDER = new int[36];

    static {
        for (int i = 0; i < 27; i++) ORDER[i] = 9 + i;
        for (int i = 0; i < 9; i++) ORDER[27 + i] = i;
    }

    /**
     * Разложить вещи из закрытых ячеек. {@code expected} — закрытие ожидаемое (ёмкость только что изменилась); иначе
     * вещь попала в закрытую ячейку в обход проверок — пишем в лог.
     *
     * @return сколько предметов выпало
     */
    public static int run(ServerPlayer player, Limits limits, boolean expected) {
        if (limits.exempt()) return 0;
        Inventory inv = player.getInventory();
        int dropped = 0;
        for (int i = 0; i < 41; i++) {
            if (limits.open(i)) continue;
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            if (!expected) {
                RpInventory.LOGGER.warn("RP Inventory: {} — вещь {} в закрытой ячейке {} (попала в обход проверок), переложена",
                        player.getGameProfile().getName(), stack, i);
            }
            inv.setItem(i, ItemStack.EMPTY);
            place(inv, limits, stack);
            if (!stack.isEmpty()) {
                dropped += stack.getCount();
                player.drop(stack, true, false);
            }
        }
        if (dropped > 0) player.displayClientMessage(Component.translatable("rpinventory.dropped", dropped), true);
        return dropped;
    }

    /** Положить стопку в открытые незамороженные ячейки; что не влезло — остаётся в {@code stack}. */
    static void place(Inventory inv, Limits limits, ItemStack stack) {
        if (stack.isStackable()) {
            for (int i : ORDER) {
                if (stack.isEmpty()) return;
                if (!limits.usable(i)) continue;
                ItemStack there = inv.getItem(i);
                if (there.isEmpty() || !ItemStack.isSameItemSameTags(there, stack)) continue;
                int room = Math.min(there.getMaxStackSize(), inv.getMaxStackSize()) - there.getCount();
                if (room <= 0) continue;
                int n = Math.min(room, stack.getCount());
                there.grow(n);
                stack.shrink(n);
                inv.setChanged();
            }
        }
        for (int i : ORDER) {
            if (stack.isEmpty()) return;
            if (!limits.usable(i) || !inv.getItem(i).isEmpty()) continue;
            int n = Math.min(stack.getCount(), Math.min(stack.getMaxStackSize(), inv.getMaxStackSize()));
            inv.setItem(i, stack.split(n));
        }
    }

    /** Выбранная ячейка хотбара закрыта/заморожена — перейти на ближайшую рабочую (если есть). */
    public static void fixSelected(ServerPlayer player, Limits limits) {
        if (limits.exempt()) return;
        Inventory inv = player.getInventory();
        if (limits.usable(inv.selected)) return;
        int best = -1;
        for (int d = 1; d < 9 && best < 0; d++) {
            if (inv.selected - d >= 0 && limits.usable(inv.selected - d)) best = inv.selected - d;
            else if (inv.selected + d < 9 && limits.usable(inv.selected + d)) best = inv.selected + d;
        }
        // Все заморожены (руки не работают) — оставляем как есть; закрытая выбранная при пустом хотбаре — тоже.
        if (best < 0) return;
        inv.selected = best;
        player.connection.send(new ClientboundSetCarriedItemPacket(best));
    }
}
