package faygolover.rpinventory;

import com.mojang.logging.LogUtils;
import faygolover.rpcore.api.RpCoreAPI;
import faygolover.rpinventory.limits.ItemModifiers;
import faygolover.rpinventory.network.InvNetwork;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * RP Inventory — ограничение инвентаря семейства RP вместо InventoryFree. Закрытые ячейки скрыты в любом меню и для
 * любого владельца инвентаря (миксины на ванильные {@code Slot}, {@code Inventory}, {@code AbstractContainerMenu});
 * ёмкость — конфиг + личная поправка ГМа + источники Core ({@link faygolover.rpcore.api.InventoryModifier}).
 */
@Mod(RpInventory.MODID)
public final class RpInventory {
    public static final String MODID = "rpinventory";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RpInventory() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, InvConfig.SPEC);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> InvCommand.register(e.getDispatcher()));
        MinecraftForge.EVENT_BUS.addListener((AddReloadListenerEvent e) -> e.addListener(ItemModifiers.INSTANCE));
    }

    private void setup(FMLCommonSetupEvent event) {
        InvNetwork.register();
        event.enqueueWork(() -> {
            RpCoreAPI.registerInventory(new InvProvider());
            // Предметы-расширители из датапака (data/<ns>/rpinventory/modifiers) — такой же источник, как особенности.
            RpCoreAPI.registerInventoryModifier(ItemModifiers.SOURCE, ItemModifiers.INSTANCE);
            RpCoreAPI.registerModule(MODID, "Ограничение инвентаря: хотбар, хранилище, броня, вторая рука, Curios");
            // Необязательные интеграции — в общий отчёт RP Core (/rpcore compat).
            faygolover.rpcore.api.RpCoreAPI.reportCompat(MODID, "curios");
            RpCoreAPI.registerCommand(MODID, "/rpinv show <игрок>", "rpinventory.command.show", 2, false);
            RpCoreAPI.registerCommand(MODID, "/rpinv set <игроки> <часть> <число>", "rpinventory.command.set", 2, false);
            RpCoreAPI.registerCommand(MODID, "/rpinv set <игроки> curios <тип> <число>", "rpinventory.command.set_curios", 2, false);
            RpCoreAPI.registerCommand(MODID, "/rpinv reset <игроки>", "rpinventory.command.reset", 2, false);
            RpCoreAPI.registerCommand(MODID, "/rpinv curios [игрок]", "rpinventory.command.curios", 2, false);
        });
    }
}
