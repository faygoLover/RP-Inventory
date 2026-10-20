package faygolover.rpinventory.client;

import faygolover.rpinventory.RpInventory;
import faygolover.rpinventory.limits.InvState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ContainerScreenEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Клиент: хотбар по открытым ячейкам, закраска скрытых ячеек в окнах, сброс данных при выходе с сервера. */
@Mod.EventBusSubscriber(modid = RpInventory.MODID, value = Dist.CLIENT)
public final class InvClient {
    private InvClient() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        ClientHotbar.onOverlay(event);
    }

    @SubscribeEvent
    public static void onBackground(ContainerScreenEvent.Render.Background event) {
        SlotHider.onBackground(event);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        InvState.clearClient();
    }
}
