package faygolover.rpinventory.limits;

import faygolover.rpcore.api.InventoryModifier;
import faygolover.rpcore.api.InventoryProvider.Part;
import faygolover.rpcore.api.RpCoreAPI;
import faygolover.rpinventory.InvConfig;
import faygolover.rpinventory.RpInventory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Расчёт ёмкости (сервер): база (конфиг или личная поправка ГМа) + прибавки источников Core → пределы (меньшее) →
 * заморозка. ГМ и творческий режим — без ограничений.
 */
public final class LimitsCalc {
    private LimitsCalc() {}

    private static final Part[] PARTS = Part.values();
    /** Источники, упавшие с ошибкой: в лог один раз. */
    private static final Set<ResourceLocation> FAILED = new HashSet<>();

    /** Строка разбора для {@code /rpinv show}: откуда поправка и что сделала. */
    public record Note(String source, String what) {}

    public record Result(Limits limits, List<Note> notes) {}

    public static Limits compute(Player player) {
        return compute(player, null).limits();
    }

    public static boolean exempt(Player player) {
        if (InvConfig.EXEMPT_CREATIVE.get() && (player.isCreative() || player.isSpectator())) return true;
        int gm = InvConfig.EXEMPT_GM_LEVEL.get();
        return gm >= 0 && player.hasPermissions(gm);
    }

    /** @param notes куда писать разбор (или null — не нужен) */
    public static Result compute(Player player, List<Note> notes) {
        if (!InvConfig.SPEC.isLoaded()) return new Result(Limits.NONE, List.of());
        if (exempt(player)) {
            if (notes != null) notes.add(new Note("rules", "exempt"));
            return new Result(Limits.NONE, notes == null ? List.of() : notes);
        }

        int[] base = new int[PARTS.length];
        base[Part.HOTBAR.ordinal()] = InvConfig.HOTBAR.get();
        base[Part.STORAGE.ordinal()] = InvConfig.STORAGE.get();
        base[Part.HEAD.ordinal()] = InvConfig.ARMOR_HEAD.get() ? 1 : 0;
        base[Part.CHEST.ordinal()] = InvConfig.ARMOR_CHEST.get() ? 1 : 0;
        base[Part.LEGS.ordinal()] = InvConfig.ARMOR_LEGS.get() ? 1 : 0;
        base[Part.FEET.ordinal()] = InvConfig.ARMOR_FEET.get() ? 1 : 0;
        base[Part.OFFHAND.ordinal()] = InvConfig.OFFHAND.get() ? 1 : 0;

        Changes ch = new Changes(notes);
        for (String s : InvConfig.CURIOS.get()) {
            InvConfig.CuriosEntry e = InvConfig.CuriosEntry.parse(s);
            if (e == null) continue;
            ch.source = "config";
            if (e.limit()) ch.limitCurios(e.type(), e.amount());
            else ch.addCurios(e.type(), e.amount());
        }

        for (Map.Entry<Part, Integer> e : Personal.parts(player).entrySet()) {
            base[e.getKey().ordinal()] = e.getValue();
            if (notes != null) notes.add(new Note("gm", Personal.key(e.getKey()) + " = " + e.getValue()));
        }
        ch.source = "gm";
        Personal.curios(player).forEach(ch::limitCurios);

        for (Map.Entry<ResourceLocation, InventoryModifier> e : RpCoreAPI.inventoryModifiers().entrySet()) {
            ch.source = e.getKey().toString();
            try {
                e.getValue().apply(player, ch);
            } catch (Exception ex) {
                if (FAILED.add(e.getKey())) RpInventory.LOGGER.error("RP Inventory: источник поправок {} упал", e.getKey(), ex);
            }
        }

        int[] caps = new int[PARTS.length];
        for (Part p : PARTS) {
            int i = p.ordinal();
            caps[i] = Math.min(Math.max(0, Math.min(p.size, base[i] + ch.add[i])), ch.limit[i]);
        }
        Map<String, Limits.CuriosRule> curios = new TreeMap<>();
        Set<String> types = new HashSet<>(ch.curiosAdd.keySet());
        types.addAll(ch.curiosLimit.keySet());
        for (String t : types) {
            curios.put(t, new Limits.CuriosRule(ch.curiosAdd.getOrDefault(t, 0), ch.curiosLimit.getOrDefault(t, -1)));
        }
        return new Result(new Limits(caps, ch.frozen, curios, false), notes == null ? List.of() : notes);
    }

    private static final class Changes implements InventoryModifier.Changes {
        final int[] add = new int[PARTS.length];
        final int[] limit = new int[PARTS.length];
        int frozen;
        final Map<String, Integer> curiosAdd = new TreeMap<>();
        final Map<String, Integer> curiosLimit = new TreeMap<>();
        final List<Note> notes;
        String source = "";

        Changes(List<Note> notes) {
            this.notes = notes;
            for (Part p : PARTS) limit[p.ordinal()] = p.size;
        }

        private void note(String what) {
            if (notes != null) notes.add(new Note(source, what));
        }

        @Override
        public void add(Part part, int amount) {
            if (part == null || amount == 0) return;
            add[part.ordinal()] += amount;
            note(Personal.key(part) + (amount > 0 ? " +" : " ") + amount);
        }

        @Override
        public void limit(Part part, int max) {
            if (part == null) return;
            limit[part.ordinal()] = Math.min(limit[part.ordinal()], Math.max(0, max));
            note(Personal.key(part) + " ≤ " + Math.max(0, max));
        }

        @Override
        public void freeze(Part part) {
            if (part == null) return;
            frozen |= 1 << part.ordinal();
            note(Personal.key(part) + " frozen");
        }

        @Override
        public void addCurios(String type, int amount) {
            if (type == null || amount == 0) return;
            curiosAdd.merge(type, amount, Integer::sum);
            note("curios:" + type + (amount > 0 ? " +" : " ") + amount);
        }

        @Override
        public void limitCurios(String type, int max) {
            if (type == null) return;
            curiosLimit.merge(type, Math.max(0, max), Math::min);
            note("curios:" + type + " ≤ " + Math.max(0, max));
        }
    }

    /** Разбор для команды: итог и все поправки. */
    public static Result explain(Player player) {
        return compute(player, new ArrayList<>());
    }
}
