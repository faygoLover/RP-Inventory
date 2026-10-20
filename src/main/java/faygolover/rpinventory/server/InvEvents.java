package faygolover.rpinventory.server;

import faygolover.rpinventory.RpInventory;
import faygolover.rpinventory.limits.InvState;
import faygolover.rpinventory.limits.Limits;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingSwapItemsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Запреты без миксинов: смена рук (F) и надевание по ПКМ. Работают на обеих сторонах, чтобы клиент не мигал. */
@Mod.EventBusSubscriber(modid = RpInventory.MODID)
public final class InvEvents {
    private InvEvents() {}

    /** F: обе ячейки (выбранная и вторая рука) должны быть рабочими. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onSwapHands(LivingSwapItemsEvent.Hands event) {
        if (!(event.getEntity() instanceof Player player)) return;
        Limits l = InvState.get(player);
        if (!l.usable(Inventory.SLOT_OFFHAND) || !l.usable(player.getInventory().selected)) event.setCanceled(true);
    }

    /** ПКМ броней/элитрой/тыквой: нельзя надеть в закрытую или замороженную ячейку брони. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        if (Equipable.get(stack) == null) return;
        EquipmentSlot slot = LivingEntity.getEquipmentSlotForItem(stack);
        if (slot.getType() != EquipmentSlot.Type.ARMOR) return;
        int index = 36 + slot.getIndex();
        if (!InvState.get(event.getEntity()).usable(index)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }
}
