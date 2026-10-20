package faygolover.rpinventory.mixin;

import faygolover.rpinventory.limits.InvState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Слот любого меню над инвентарём игрока: закрытая у владельца ячейка — неактивна (экран её не рисует и не даёт
 * навести), замороженная — видна, в неё не положить, а владелец не может и взять (другой — может). Объекты слотов не
 * подменяем — меню, узнающие слоты по классу, не ломаются.
 */
@Mixin(Slot.class)
public abstract class SlotMixin {
    @Inject(method = "isActive", at = @At("HEAD"), cancellable = true)
    private void rpinventory$isActive(CallbackInfoReturnable<Boolean> cir) {
        if (InvState.hidden((Slot) (Object) this)) cir.setReturnValue(false);
    }

    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void rpinventory$mayPlace(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (InvState.blocked((Slot) (Object) this)) cir.setReturnValue(false);
    }

    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void rpinventory$mayPickup(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (InvState.blockedFor((Slot) (Object) this, player)) cir.setReturnValue(false);
    }
}
