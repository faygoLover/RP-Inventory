package faygolover.rpinventory;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

/** Настройки сервера ({@code serverconfig/rpinventory-server.toml} мира; клиентам приходят сами). */
public final class InvConfig {
    private InvConfig() {}

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue HOTBAR;
    public static final ForgeConfigSpec.IntValue STORAGE;
    public static final ForgeConfigSpec.BooleanValue ARMOR_HEAD;
    public static final ForgeConfigSpec.BooleanValue ARMOR_CHEST;
    public static final ForgeConfigSpec.BooleanValue ARMOR_LEGS;
    public static final ForgeConfigSpec.BooleanValue ARMOR_FEET;
    public static final ForgeConfigSpec.BooleanValue OFFHAND;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CURIOS;

    public static final ForgeConfigSpec.BooleanValue EXEMPT_CREATIVE;
    public static final ForgeConfigSpec.IntValue EXEMPT_GM_LEVEL;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Base inventory for every player (before traits, body state and gear).",
                "Базовый инвентарь каждого игрока (до особенностей, состояния тела и снаряжения).").push("base");
        HOTBAR = b.comment("Open hotbar slots, from the left. / Открытых ячеек хотбара, слева.")
                .defineInRange("hotbar", 9, 0, 9);
        STORAGE = b.comment("Open storage slots (of 27), left to right, top to bottom. / Открытых ячеек хранилища (из 27), слева направо, сверху вниз.")
                .defineInRange("storage", 9, 0, 27);
        ARMOR_HEAD = b.comment("Helmet slot open. / Ячейка шлема открыта.").define("armor_head", true);
        ARMOR_CHEST = b.comment("Chest armor slot open. / Ячейка брони груди открыта.").define("armor_chest", true);
        ARMOR_LEGS = b.comment("Leg armor slot open. / Ячейка брони ног открыта.").define("armor_legs", true);
        ARMOR_FEET = b.comment("Boots slot open. / Ячейка ботинок открыта.").define("armor_feet", true);
        OFFHAND = b.comment("Offhand slot open. / Вторая рука открыта.").define("offhand", true);
        CURIOS = b.comment("Curios slot types, only where needed (others stay as Curios mods set them):",
                        "\"back=0\" — at most 0 (closed), \"ring=1\" — at most 1, \"belt+1\" / \"belt-1\" — add or remove.",
                        "List of types: /rpinv curios.",
                        "Типы слотов Curios, только где нужно (остальные — как задали моды): \"back=0\" — не больше 0 (закрыт),",
                        "\"ring=1\" — не больше 1, \"belt+1\" / \"belt-1\" — прибавить или убрать. Список типов — /rpinv curios.")
                .defineListAllowEmpty("curios", List.of(), o -> o instanceof String s && CuriosEntry.parse(s) != null);
        b.pop();

        b.comment("Who is not limited. / На кого ограничения не действуют.").push("rules");
        EXEMPT_CREATIVE = b.comment("Creative and spectator modes are not limited. / Творческий режим и наблюдатель — без ограничений.")
                .define("exempt_creative", true);
        EXEMPT_GM_LEVEL = b.comment("Operators of this level and higher are not limited; -1 — GMs are limited too (for testing).",
                        "Операторы этого уровня и выше — без ограничений; -1 — ГМы тоже ограничены (для проверки).")
                .defineInRange("exempt_gm_level", 2, -1, 4);
        b.pop();

        SPEC = b.build();
    }

    /** Строка {@code curios}: тип, предел ({@code =}) или прибавка ({@code +}/{@code -}). */
    public record CuriosEntry(String type, int amount, boolean limit) {
        public static CuriosEntry parse(String s) {
            if (s == null) return null;
            s = s.trim();
            int i = indexOfOp(s);
            if (i <= 0 || i == s.length() - 1) return null;
            String type = s.substring(0, i).trim();
            char op = s.charAt(i);
            try {
                int n = Integer.parseInt(s.substring(i + 1).trim());
                if (op == '=') return n < 0 ? null : new CuriosEntry(type, n, true);
                return new CuriosEntry(type, op == '-' ? -n : n, false);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        private static int indexOfOp(String s) {
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '=' || c == '+' || c == '-') return i;
            }
            return -1;
        }
    }
}
