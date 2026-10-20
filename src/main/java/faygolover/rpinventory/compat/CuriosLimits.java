package faygolover.rpinventory.compat;

import faygolover.rpinventory.limits.Limits;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ограничение типов Curios собственным временным модификатором слотов на каждый тип (один UUID на тип). Список
 * типов не задан заранее — какие есть у игрока, такие и берём. Тип с 0 слотов Curios сам не показывает; вещи из
 * убранных слотов Curios отдаёт игроку (в открытые ячейки) или бросает.
 */
final class CuriosLimits {
    private CuriosLimits() {}

    private static final String NAME = "rpinventory";

    static UUID uuid(String type) {
        return UUID.nameUUIDFromBytes(("rpinventory:curios:" + type).getBytes(StandardCharsets.UTF_8));
    }

    static void apply(ServerPlayer player, Limits limits) {
        ICuriosItemHandler handler = CuriosApi.getCuriosInventory(player).resolve().orElse(null);
        if (handler == null) return;
        for (Map.Entry<String, ICurioStacksHandler> e : handler.getCurios().entrySet()) {
            String type = e.getKey();
            ICurioStacksHandler stacks = e.getValue();
            // Пока Curios не пересчитал размер после чужой правки — getSlots() старый, подождём следующего раза.
            if (handler.getUpdatingInventories().contains(stacks)) continue;
            UUID id = uuid(type);
            AttributeModifier ours = stacks.getModifiers().get(id);
            int oursAmount = ours == null ? 0 : (int) Math.round(ours.getAmount());
            int without = stacks.getSlots() - oursAmount;
            int want = limits.curiosRule(type).apply(without) - without;
            if (want == oursAmount && (want != 0 || ours == null)) continue;
            if (ours != null) stacks.removeModifier(id);
            if (want != 0) stacks.addTransientModifier(new AttributeModifier(id, NAME, want, AttributeModifier.Operation.ADDITION));
        }
    }

    static List<CuriosCompat.Worn> worn(Player player) {
        List<CuriosCompat.Worn> out = new ArrayList<>();
        ICuriosItemHandler handler = CuriosApi.getCuriosInventory(player).resolve().orElse(null);
        if (handler == null) return out;
        handler.getCurios().forEach((type, stacks) -> {
            for (int i = 0; i < stacks.getSlots(); i++) {
                var stack = stacks.getStacks().getStackInSlot(i);
                if (!stack.isEmpty()) out.add(new CuriosCompat.Worn(type, stack));
            }
        });
        return out;
    }

    static List<CuriosCompat.TypeInfo> types(Player player) {
        List<CuriosCompat.TypeInfo> out = new ArrayList<>();
        ICuriosItemHandler handler = CuriosApi.getCuriosInventory(player).resolve().orElse(null);
        Limits limits = faygolover.rpinventory.limits.InvState.get(player);
        for (String type : CuriosApi.getPlayerSlots(player).keySet()) {
            int slots = handler == null ? 0 : handler.getStacksHandler(type).map(ICurioStacksHandler::getSlots).orElse(0);
            out.add(new CuriosCompat.TypeInfo(type, slots, limits.curiosRule(type)));
        }
        return out;
    }
}
