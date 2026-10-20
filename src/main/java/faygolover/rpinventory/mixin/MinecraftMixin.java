package faygolover.rpinventory.mixin;

import faygolover.rpinventory.limits.InvState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Клиент: цифры хотбара не выбирают закрытую или замороженную ячейку. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Redirect(method = "handleKeybinds", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/entity/player/Inventory;selected:I", opcode = Opcodes.PUTFIELD))
    private void rpinventory$select(Inventory inv, int value) {
        if (InvState.get(inv.player).usable(value)) inv.selected = value;
    }
}
