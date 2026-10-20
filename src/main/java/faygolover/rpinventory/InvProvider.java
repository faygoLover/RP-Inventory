package faygolover.rpinventory;

import faygolover.rpcore.api.InventoryProvider;
import faygolover.rpinventory.limits.InvState;
import faygolover.rpinventory.server.InvServer;
import net.minecraft.world.entity.player.Player;

/** Розетка Core: ответы по итоговой ёмкости ({@link InvState}) — на сервере и на клиенте. */
final class InvProvider implements InventoryProvider {
    @Override
    public boolean isSlotOpen(Player player, int index) {
        return InvState.get(player).open(index);
    }

    @Override
    public boolean isSlotFrozen(Player player, int index) {
        return InvState.get(player).frozen(index);
    }

    @Override
    public int capacity(Player player, Part part) {
        return InvState.get(player).capacity(part);
    }

    @Override
    public int curiosLimit(Player player, String type) {
        return InvState.get(player).curiosLimit(type);
    }

    @Override
    public boolean limited(Player player) {
        return !InvState.get(player).exempt();
    }

    @Override
    public void refresh(Player player) {
        if (!player.level().isClientSide) InvServer.refresh(player);
    }
}
