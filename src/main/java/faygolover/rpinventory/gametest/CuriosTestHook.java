package faygolover.rpinventory.gametest;

import net.minecraft.server.level.ServerPlayer;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.ArrayList;

/** Тест Curios: фальшивого игрока никто не тикает — применяем отложенные изменения размеров слотов сами. */
final class CuriosTestHook {
    private CuriosTestHook() {}

    static void flush(ServerPlayer p) {
        CuriosApi.getCuriosInventory(p).ifPresent(handler -> {
            for (ICurioStacksHandler s : new ArrayList<>(handler.getUpdatingInventories())) s.update();
            handler.getUpdatingInventories().clear();
            handler.handleInvalidStacks();
        });
    }
}
