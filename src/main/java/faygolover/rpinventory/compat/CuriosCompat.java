package faygolover.rpinventory.compat;

import faygolover.rpinventory.limits.Limits;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;

import java.util.List;

/** Curios — необязателен: классы Curios трогает только {@link CuriosLimits}, и только если Curios стоит. */
public final class CuriosCompat {
    private CuriosCompat() {}

    /** Тип Curios у игрока: сколько слотов сейчас и поправка мода. */
    public record TypeInfo(String type, int slots, Limits.CuriosRule rule) {}

    /** Надетая в слот Curios вещь. */
    public record Worn(String type, net.minecraft.world.item.ItemStack stack) {}

    private static Boolean loaded;

    public static boolean loaded() {
        if (loaded == null) loaded = ModList.get().isLoaded("curios");
        return loaded;
    }

    /** Привести слоты Curios игрока к его ёмкости (сервер). */
    public static void apply(ServerPlayer player, Limits limits) {
        if (loaded()) CuriosLimits.apply(player, limits);
    }

    /** Строки для {@code /rpinv curios}: тип, сколько слотов у игрока сейчас, поправка мода. Пусто — Curios нет. */
    public static List<TypeInfo> types(Player player) {
        return loaded() ? CuriosLimits.types(player) : List.of();
    }

    /** Все надетые вещи Curios игрока (для предметов-расширителей). */
    public static List<Worn> worn(Player player) {
        return loaded() ? CuriosLimits.worn(player) : List.of();
    }
}
