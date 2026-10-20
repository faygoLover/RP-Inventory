package faygolover.rpinventory.limits;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import faygolover.rpcore.api.InventoryModifier;
import faygolover.rpcore.api.InventoryProvider.Part;
import faygolover.rpinventory.RpInventory;
import faygolover.rpinventory.compat.CuriosCompat;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Предметы-расширители из датапака: {@code data/<ns>/rpinventory/modifiers/*.json}.
 * <pre>
 * { "item": "modid:pouch_belt", "worn_in": ["curios:belt"], "storage": 3 }
 * { "item": "#modid:vests", "worn_in": ["chest"], "storage": 2, "curios": { "pouch": 1 } }
 * </pre>
 * {@code item} — предмет или тег ({@code #}); {@code worn_in} — где он должен быть: {@code head}, {@code chest},
 * {@code legs}, {@code feet}, {@code mainhand}, {@code offhand}, {@code inventory} (любая открытая ячейка 0–35),
 * {@code curios} (любой слот Curios) или {@code curios:<тип>}. Части: {@code hotbar}, {@code storage}, {@code head},
 * {@code chest}, {@code legs}, {@code feet}, {@code offhand} — прибавка (может быть отрицательной); {@code curios} —
 * прибавки к типам слотов. Один и тот же предмет в нескольких местах считается столько раз, сколько раз он там лежит.
 */
public final class ItemModifiers extends SimpleJsonResourceReloadListener implements InventoryModifier {
    public static final ItemModifiers INSTANCE = new ItemModifiers();
    public static final ResourceLocation SOURCE = new ResourceLocation(RpInventory.MODID, "items");

    private static final List<String> WHERE = List.of("head", "chest", "legs", "feet", "mainhand", "offhand", "inventory", "curios");

    record Entry(ResourceLocation id, @Nullable Item item, @Nullable TagKey<Item> tag, List<String> wornIn,
                 Map<Part, Integer> parts, Map<String, Integer> curios) {
        boolean matches(ItemStack stack) {
            return !stack.isEmpty() && (item != null ? stack.is(item) : stack.is(tag));
        }

        boolean wornIn(String where) {
            for (String w : wornIn) {
                if (w.equals(where) || (where.startsWith("curios:") && w.equals("curios"))) return true;
            }
            return false;
        }
    }

    private volatile List<Entry> entries = List.of();

    private ItemModifiers() {
        super(new com.google.gson.Gson(), "rpinventory/modifiers");
    }

    public int size() {
        return entries.size();
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager, ProfilerFiller profiler) {
        List<Entry> out = new ArrayList<>();
        files.forEach((id, json) -> {
            try {
                out.add(parse(id, GsonHelper.convertToJsonObject(json, "modifier")));
            } catch (Exception e) {
                RpInventory.LOGGER.error("RP Inventory: модификатор {} не прочитан: {}", id, e.getMessage());
            }
        });
        entries = List.copyOf(out);
        if (!out.isEmpty()) RpInventory.LOGGER.info("RP Inventory: предметов-расширителей {}", out.size());
    }

    private static Entry parse(ResourceLocation id, JsonObject o) {
        String itemStr = GsonHelper.getAsString(o, "item");
        Item item = null;
        TagKey<Item> tag = null;
        if (itemStr.startsWith("#")) {
            tag = TagKey.create(Registries.ITEM, new ResourceLocation(itemStr.substring(1)));
        } else {
            item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemStr));
            if (item == null || item == net.minecraft.world.item.Items.AIR) throw new IllegalArgumentException("нет предмета " + itemStr);
        }
        List<String> wornIn = new ArrayList<>();
        JsonArray arr = GsonHelper.getAsJsonArray(o, "worn_in");
        for (JsonElement e : arr) {
            String w = e.getAsString().toLowerCase(Locale.ROOT);
            if (!WHERE.contains(w) && !w.startsWith("curios:")) throw new IllegalArgumentException("неизвестное место " + w);
            wornIn.add(w);
        }
        Map<Part, Integer> parts = new EnumMap<>(Part.class);
        for (Part p : Part.values()) {
            String key = Personal.key(p);
            if (o.has(key)) parts.put(p, GsonHelper.getAsInt(o, key));
        }
        Map<String, Integer> curios = new TreeMap<>();
        if (o.has("curios")) {
            for (Map.Entry<String, JsonElement> e : GsonHelper.getAsJsonObject(o, "curios").entrySet()) {
                curios.put(e.getKey(), e.getValue().getAsInt());
            }
        }
        return new Entry(id, item, tag, List.copyOf(wornIn), parts, curios);
    }

    // ------------------------------------------------------------------ как источник поправок

    @Override
    public void apply(Player player, Changes ch) {
        List<Entry> list = entries;
        if (list.isEmpty()) return;
        Inventory inv = player.getInventory();
        Limits current = InvState.get(player);
        check(list, player.getItemBySlot(EquipmentSlot.HEAD), "head", ch);
        check(list, player.getItemBySlot(EquipmentSlot.CHEST), "chest", ch);
        check(list, player.getItemBySlot(EquipmentSlot.LEGS), "legs", ch);
        check(list, player.getItemBySlot(EquipmentSlot.FEET), "feet", ch);
        check(list, player.getMainHandItem(), "mainhand", ch);
        check(list, player.getOffhandItem(), "offhand", ch);
        for (int i = 0; i < 36; i++) {
            if (current.open(i)) check(list, inv.getItem(i), "inventory", ch);
        }
        for (CuriosCompat.Worn w : CuriosCompat.worn(player)) check(list, w.stack(), "curios:" + w.type(), ch);
    }

    private static void check(List<Entry> list, ItemStack stack, String where, Changes ch) {
        if (stack.isEmpty()) return;
        for (Entry e : list) {
            if (!e.wornIn(where) || !e.matches(stack)) continue;
            e.parts.forEach(ch::add);
            e.curios.forEach(ch::addCurios);
        }
    }
}
