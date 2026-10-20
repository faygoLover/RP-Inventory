package faygolover.rpinventory.mixin;

import faygolover.rpinventory.limits.InvState;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.wrapper.InvWrapper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Обёртки Forge над инвентарём игрока (PlayerMainInvWrapper, PlayerArmorInvWrapper, ItemHandlerHelper.giveItemToPlayer,
 * возврат вещей из Curios) идут через InvWrapper: в закрытые и замороженные ячейки не кладём, из закрытых не берём.
 */
@Mixin(value = InvWrapper.class, remap = false)
public abstract class InvWrapperMixin {
    @Shadow @Final private Container inv;

    @Inject(method = "insertItem", at = @At("HEAD"), cancellable = true)
    private void rpinventory$insert(int slot, ItemStack stack, boolean simulate, CallbackInfoReturnable<ItemStack> cir) {
        if (!InvState.usable(inv, slot)) cir.setReturnValue(stack);
    }

    @Inject(method = "extractItem", at = @At("HEAD"), cancellable = true)
    private void rpinventory$extract(int slot, int amount, boolean simulate, CallbackInfoReturnable<ItemStack> cir) {
        // Забрать из замороженной (рука не работает) другие моды могут — как медик при обыске.
        if (!InvState.open(inv, slot)) cir.setReturnValue(ItemStack.EMPTY);
    }
}
