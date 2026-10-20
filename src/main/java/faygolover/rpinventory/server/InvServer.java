package faygolover.rpinventory.server;

import faygolover.rpcore.api.event.InventoryCapacityChangedEvent;
import faygolover.rpinventory.RpInventory;
import faygolover.rpinventory.compat.CuriosCompat;
import faygolover.rpinventory.limits.InvState;
import faygolover.rpinventory.limits.Limits;
import faygolover.rpinventory.limits.LimitsCalc;
import faygolover.rpinventory.network.InvNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервер: пересчёт ёмкости раз в секунду и по просьбе ({@link #refresh}), рассылка, раскладка вещей из снятых ячеек,
 * страховка (вещь в закрытой ячейке → переложить), выбранная ячейка хотбара.
 */
@Mod.EventBusSubscriber(modid = RpInventory.MODID)
public final class InvServer {
    private InvServer() {}

    private static final Set<UUID> PENDING = ConcurrentHashMap.newKeySet();

    /** Пересчитать игрока в ближайший тик. */
    public static void refresh(Player player) {
        PENDING.add(player.getUUID());
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) return;
        boolean due = PENDING.remove(player.getUUID()) || (player.tickCount + player.getId()) % 20 == 0;
        if (due) update(player);
    }

    /** Пересчитать, разослать при изменении, разложить вещи, поправить хотбар и Curios. */
    public static void update(ServerPlayer player) {
        Limits old = InvState.get(player);
        Limits now = LimitsCalc.compute(player);
        boolean changed = !now.equals(old) || !InvState.serverAll().containsKey(player.getUUID());
        if (changed) {
            InvState.putServer(player.getUUID(), now);
            InvNetwork.sendToAll(player.getUUID(), now);
        }
        CuriosCompat.apply(player, now);
        Redistribute.run(player, now, changed);
        Redistribute.fixSelected(player, now);
        if (changed) MinecraftForge.EVENT_BUS.post(new InventoryCapacityChangedEvent(player));
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        for (Map.Entry<UUID, Limits> e : InvState.serverAll().entrySet()) InvNetwork.send(player, e.getKey(), e.getValue());
        refresh(player);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        InvState.removeServer(id);
        PENDING.remove(id);
        InvNetwork.sendToAll(id, null);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        refresh(event.getEntity());
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        refresh(event.getEntity());
    }

    @SubscribeEvent
    public static void onGameMode(PlayerEvent.PlayerChangeGameModeEvent event) {
        refresh(event.getEntity());
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        InvState.clearServer();
        PENDING.clear();
    }
}
