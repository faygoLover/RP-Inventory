package faygolover.rpinventory.mixin;

import faygolover.rpinventory.limits.InvState;
import faygolover.rpinventory.limits.Limits;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Поиск места в инвентаре пропускает закрытые и замороженные ячейки: подбор, крафт, выдача модами, возврат вещей.
 * Колесо и выбор блока (средняя кнопка) — тоже только по рабочим ячейкам хотбара.
 */
@Mixin(Inventory.class)
public abstract class InventoryMixin {
    @Shadow @Final public NonNullList<ItemStack> items;
    @Shadow public int selected;
    @Shadow @Final public Player player;

    @Shadow public abstract ItemStack getItem(int index);

    @Shadow private boolean hasRemainingSpaceForItem(ItemStack destination, ItemStack origin) {
        throw new AssertionError();
    }

    @Shadow public abstract boolean add(int slot, ItemStack stack);

    @Inject(method = "getFreeSlot", at = @At("HEAD"), cancellable = true)
    private void rpinventory$getFreeSlot(CallbackInfoReturnable<Integer> cir) {
        Limits l = InvState.get(player);
        if (l.exempt()) return;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).isEmpty() && l.usable(i)) {
                cir.setReturnValue(i);
                return;
            }
        }
        cir.setReturnValue(-1);
    }

    @Inject(method = "getSlotWithRemainingSpace", at = @At("HEAD"), cancellable = true)
    private void rpinventory$getSlotWithRemainingSpace(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        Limits l = InvState.get(player);
        if (l.exempt()) return;
        if (l.usable(selected) && hasRemainingSpaceForItem(getItem(selected), stack)) {
            cir.setReturnValue(selected);
            return;
        }
        if (l.usable(Inventory.SLOT_OFFHAND) && hasRemainingSpaceForItem(getItem(Inventory.SLOT_OFFHAND), stack)) {
            cir.setReturnValue(Inventory.SLOT_OFFHAND);
            return;
        }
        for (int i = 0; i < items.size(); i++) {
            if (l.usable(i) && hasRemainingSpaceForItem(items.get(i), stack)) {
                cir.setReturnValue(i);
                return;
            }
        }
        cir.setReturnValue(-1);
    }

    /** Добавить в конкретную ячейку, а она закрыта — как в любую свободную. */
    @Inject(method = "add(ILnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void rpinventory$add(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (slot >= 0 && !InvState.get(player).usable(slot)) cir.setReturnValue(add(-1, stack));
    }

    @Inject(method = "findSlotMatchingItem", at = @At("HEAD"), cancellable = true)
    private void rpinventory$findSlotMatchingItem(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        Limits l = InvState.get(player);
        if (l.exempt()) return;
        for (int i = 0; i < items.size(); i++) {
            ItemStack there = items.get(i);
            if (l.usable(i) && !there.isEmpty() && ItemStack.isSameItemSameTags(stack, there)) {
                cir.setReturnValue(i);
                return;
            }
        }
        cir.setReturnValue(-1);
    }

    @Inject(method = "getSuitableHotbarSlot", at = @At("HEAD"), cancellable = true)
    private void rpinventory$getSuitableHotbarSlot(CallbackInfoReturnable<Integer> cir) {
        Limits l = InvState.get(player);
        if (l.exempt()) return;
        for (int i = 0; i < 9; i++) {
            int j = (selected + i) % 9;
            if (l.usable(j) && items.get(j).isEmpty()) {
                cir.setReturnValue(j);
                return;
            }
        }
        for (int i = 0; i < 9; i++) {
            int j = (selected + i) % 9;
            if (l.usable(j) && !items.get(j).isNotReplaceableByPickAction(player, j)) {
                cir.setReturnValue(j);
                return;
            }
        }
        cir.setReturnValue(selected);
    }

    /** Взять блок из ячейки в хотбар (средняя кнопка): из закрытой/замороженной — нельзя. */
    @Inject(method = "pickSlot", at = @At("HEAD"), cancellable = true)
    private void rpinventory$pickSlot(int index, CallbackInfo ci) {
        Limits l = InvState.get(player);
        if (!l.exempt() && !l.usable(index)) ci.cancel();
    }

    @Inject(method = "swapPaint", at = @At("HEAD"), cancellable = true)
    private void rpinventory$swapPaint(double direction, CallbackInfo ci) {
        Limits l = InvState.get(player);
        if (l.exempt()) return;
        ci.cancel();
        int dir = (int) Math.signum(direction);
        if (dir == 0) return;
        int s = selected;
        for (int k = 0; k < 9; k++) {
            s = Math.floorMod(s - dir, 9);
            if (l.usable(s)) {
                selected = s;
                return;
            }
        }
    }
}
