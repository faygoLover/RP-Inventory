package faygolover.rpinventory.mixin;

import faygolover.rpinventory.limits.InvState;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Inventory;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Сервер: клиент выбрал закрытую/замороженную ячейку хотбара — не принимаем и возвращаем ему прежнюю. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
    @Shadow public ServerPlayer player;

    @Redirect(method = "handleSetCarriedItem", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/entity/player/Inventory;selected:I", opcode = Opcodes.PUTFIELD))
    private void rpinventory$select(Inventory inv, int value) {
        if (InvState.get(inv.player).usable(value)) inv.selected = value;
        else player.connection.send(new ClientboundSetCarriedItemPacket(inv.selected));
    }
}
