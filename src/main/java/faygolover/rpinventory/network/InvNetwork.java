package faygolover.rpinventory.network;

import faygolover.rpinventory.RpInventory;
import faygolover.rpinventory.limits.InvState;
import faygolover.rpinventory.limits.Limits;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Канал {@code rpinventory:main}: итоговая ёмкость игроков. Рассылается всем — клиенту нужна своя (скрыть ячейки,
 * хотбар) и чужая (обыск: ячейки пациента в меню медика).
 */
public final class InvNetwork {
    private InvNetwork() {}

    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(RpInventory.MODID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    public static void register() {
        CHANNEL.registerMessage(0, Sync.class, Sync::encode, Sync::decode, Sync::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void sendToAll(UUID player, Limits limits) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), new Sync(player, limits));
    }

    public static void send(ServerPlayer to, UUID player, Limits limits) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> to), new Sync(player, limits));
    }

    /** Ёмкость игрока {@code player}; null — забыть (игрок вышел). */
    public record Sync(UUID player, Limits limits) {
        static void encode(Sync m, FriendlyByteBuf buf) {
            buf.writeUUID(m.player);
            buf.writeBoolean(m.limits != null);
            if (m.limits != null) m.limits.write(buf);
        }

        static Sync decode(FriendlyByteBuf buf) {
            UUID id = buf.readUUID();
            return new Sync(id, buf.readBoolean() ? Limits.read(buf) : null);
        }

        static void handle(Sync m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> InvState.putClient(m.player, m.limits));
            ctx.get().setPacketHandled(true);
        }
    }
}
