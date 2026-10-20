package faygolover.rpinventory.limits;

import faygolover.rpcore.api.InventoryProvider.Part;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Личная поправка ГМа ({@code /rpinv set}): своя база части вместо конфига, свой предел типа Curios. Лежит в
 * сохраняемых данных игрока ({@code PlayerPersisted}, переживает смерть).
 */
public final class Personal {
    private Personal() {}

    private static final String KEY = "rpinventory";
    private static final String CURIOS = "curios";

    private static CompoundTag root(Player player, boolean create) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (create && !player.getPersistentData().contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
        }
        CompoundTag tag = persisted.getCompound(KEY);
        if (create && !persisted.contains(KEY, Tag.TAG_COMPOUND)) persisted.put(KEY, tag);
        return tag;
    }

    public static String key(Part part) {
        return part.name().toLowerCase(Locale.ROOT);
    }

    /** Своя база частей (только заданные). */
    public static Map<Part, Integer> parts(Player player) {
        CompoundTag tag = root(player, false);
        Map<Part, Integer> out = new EnumMap<>(Part.class);
        for (Part p : Part.values()) if (tag.contains(key(p), Tag.TAG_INT)) out.put(p, tag.getInt(key(p)));
        return out;
    }

    /** Свои пределы типов Curios. */
    public static Map<String, Integer> curios(Player player) {
        CompoundTag tag = root(player, false).getCompound(CURIOS);
        Map<String, Integer> out = new TreeMap<>();
        for (String k : tag.getAllKeys()) out.put(k, tag.getInt(k));
        return out;
    }

    public static void setPart(Player player, Part part, int value) {
        root(player, true).putInt(key(part), Math.max(0, Math.min(part.size, value)));
    }

    public static void setCurios(Player player, String type, int limit) {
        CompoundTag root = root(player, true);
        CompoundTag c = root.getCompound(CURIOS);
        c.putInt(type, Math.max(0, limit));
        root.put(CURIOS, c);
    }

    public static void reset(Player player) {
        player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).remove(KEY);
    }
}
