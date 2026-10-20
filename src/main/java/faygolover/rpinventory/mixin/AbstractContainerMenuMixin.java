package faygolover.rpinventory.mixin;

import faygolover.rpinventory.limits.InvState;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Клики в любом меню: по закрытой ячейке — ничего, по своей замороженной — тоже (проверка и на сервере); цифра/F над
 * слотом — не в закрытую/замороженную ячейку своего хотбара или второй руки. Shift-клик не досыпает и не кладёт в такие
 * ячейки (ванильный перенос не спрашивает mayPlace при досыпании, а слоты брони переопределяют mayPlace без super).
 */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {
    @Shadow @Final public NonNullList<Slot> slots;

    @Inject(method = "doClick", at = @At("HEAD"), cancellable = true)
    private void rpinventory$doClick(int slotId, int button, ClickType type, Player player, CallbackInfo ci) {
        if (slotId >= 0 && slotId < slots.size() && InvState.blockedFor(slots.get(slotId), player)) {
            ci.cancel();
            return;
        }
        if (type == ClickType.SWAP && !InvState.get(player).usable(button)) ci.cancel();
    }

    @Redirect(method = "moveItemStackTo", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/Slot;getItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack rpinventory$moveGetItem(Slot slot) {
        return InvState.blocked(slot) ? ItemStack.EMPTY : slot.getItem();
    }

    @Redirect(method = "moveItemStackTo", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/Slot;mayPlace(Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean rpinventory$moveMayPlace(Slot slot, ItemStack stack) {
        return !InvState.blocked(slot) && slot.mayPlace(stack);
    }
}
