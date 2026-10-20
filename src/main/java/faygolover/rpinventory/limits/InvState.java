package faygolover.rpinventory.limits;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Где лежит итоговая ёмкость игроков. Сервер и клиент — отдельно (в одиночной игре оба в одной JVM): сторону
 * определяет мир игрока. Нет данных (фейковые игроки модов, до первого расчёта) — всё открыто.
 */
public final class InvState {
    private InvState() {}

    private static final Map<UUID, Limits> SERVER = new ConcurrentHashMap<>();
    private static final Map<UUID, Limits> CLIENT = new ConcurrentHashMap<>();

    public static Limits get(Player player) {
        if (player == null) return Limits.NONE;
        Limits l = (player.level().isClientSide ? CLIENT : SERVER).get(player.getUUID());
        return l == null ? Limits.NONE : l;
    }

    /** Ячейка инвентаря-контейнера: открыта ли для владельца этого инвентаря. Чужой контейнер — открыт. */
    public static boolean open(Container container, int index) {
        return !(container instanceof Inventory inv) || get(inv.player).open(index);
    }

    public static boolean usable(Container container, int index) {
        return !(container instanceof Inventory inv) || get(inv.player).usable(index);
    }

    /** Слот меню скрыт: его ячейки нет у владельца инвентаря. */
    public static boolean hidden(Slot slot) {
        return !open(slot.container, slot.getContainerSlot());
    }

    /**
     * Слот меню нельзя трогать вообще: скрыт или заморожен. Так — когда неизвестно, кто действует (перенос
     * shift-кликом, mayPlace): класть в замороженную ячейку нельзя никому.
     */
    public static boolean blocked(Slot slot) {
        return !usable(slot.container, slot.getContainerSlot());
    }

    /**
     * Слот меню нельзя трогать игроку {@code actor}: скрытый — никому; замороженный — только самому владельцу (рука не
     * работает), а другой (медик при обыске) из неё взять может.
     */
    public static boolean blockedFor(Slot slot, Player actor) {
        if (!(slot.container instanceof Inventory inv)) return false;
        Limits l = get(inv.player);
        int i = slot.getContainerSlot();
        return !l.open(i) || (l.frozen(i) && (actor == null || actor == inv.player || actor.getUUID().equals(inv.player.getUUID())));
    }

    // ------------------------------------------------------------------ запись

    public static void putServer(UUID id, Limits limits) {
        SERVER.put(id, limits);
    }

    public static void removeServer(UUID id) {
        SERVER.remove(id);
    }

    public static Map<UUID, Limits> serverAll() {
        return Map.copyOf(SERVER);
    }

    public static void putClient(UUID id, Limits limits) {
        if (limits == null) CLIENT.remove(id);
        else CLIENT.put(id, limits);
    }

    public static void clearClient() {
        CLIENT.clear();
    }

    public static void clearServer() {
        SERVER.clear();
    }
}
