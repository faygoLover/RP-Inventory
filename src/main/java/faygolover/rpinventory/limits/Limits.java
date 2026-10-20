package faygolover.rpinventory.limits;

import faygolover.rpcore.api.InventoryProvider.Part;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Итоговая ёмкость инвентаря игрока: сколько ячеек каждой части открыто, какие части заморожены, поправки к типам
 * Curios. Неизменяемая; сервер считает ({@link LimitsCalc}), клиенту приходит по сети.
 *
 * @param caps   открытых ячеек по частям ({@link Part#ordinal()})
 * @param frozen заморожённые части, битами по {@link Part#ordinal()}
 * @param curios поправки к типам Curios: тип → {прибавка, предел или -1}
 * @param exempt ограничения не действуют (ГМ, творческий режим)
 */
public record Limits(int[] caps, int frozen, Map<String, CuriosRule> curios, boolean exempt) {
    /** Поправка к типу Curios: прибавка (может быть отрицательной) и предел ({@code -1} — без предела). */
    public record CuriosRule(int add, int limit) {
        public static final CuriosRule NONE = new CuriosRule(0, -1);

        /** Сколько ячеек оставить при {@code size} ячейках без нас. */
        public int apply(int size) {
            int t = Math.max(0, size + add);
            return limit >= 0 ? Math.min(t, limit) : t;
        }
    }

    private static final Part[] PARTS = Part.values();

    /** Всё открыто, ограничений нет (до первого расчёта, ГМ, нет данных об игроке). */
    public static final Limits NONE = allOpen();

    private static Limits allOpen() {
        int[] caps = new int[PARTS.length];
        for (Part p : PARTS) caps[p.ordinal()] = p.size;
        return new Limits(caps, 0, Map.of(), true);
    }

    public Limits {
        caps = caps.clone();
        curios = Collections.unmodifiableMap(new TreeMap<>(curios));
    }

    public int capacity(Part part) {
        return exempt ? part.size : caps[part.ordinal()];
    }

    public boolean partFrozen(Part part) {
        return !exempt && (frozen & (1 << part.ordinal())) != 0;
    }

    /** Есть ли ячейка (замороженная тоже). Индекс вне 0–40 — есть. */
    public boolean open(int index) {
        if (exempt) return true;
        Part p = Part.of(index);
        return p == null || index - p.first < caps[p.ordinal()];
    }

    public boolean frozen(int index) {
        if (exempt) return false;
        Part p = Part.of(index);
        return p != null && (frozen & (1 << p.ordinal())) != 0;
    }

    /** Можно класть и брать: есть и не заморожена. */
    public boolean usable(int index) {
        return open(index) && !frozen(index);
    }

    /** Сколько ячеек типа Curios оставляет мод, или -1 — тип не ограничен (только предел, без прибавок). */
    public int curiosLimit(String type) {
        CuriosRule r = exempt ? null : curios.get(type);
        return r == null ? -1 : r.limit();
    }

    public CuriosRule curiosRule(String type) {
        CuriosRule r = exempt ? null : curios.get(type);
        return r == null ? CuriosRule.NONE : r;
    }

    // ------------------------------------------------------------------ сеть

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(exempt);
        for (int c : caps) buf.writeByte(c);
        buf.writeVarInt(frozen);
        buf.writeVarInt(curios.size());
        curios.forEach((type, r) -> {
            buf.writeUtf(type);
            buf.writeVarInt(r.add());
            buf.writeVarInt(r.limit());
        });
    }

    public static Limits read(FriendlyByteBuf buf) {
        boolean exempt = buf.readBoolean();
        int[] caps = new int[PARTS.length];
        for (int i = 0; i < caps.length; i++) caps[i] = buf.readByte();
        int frozen = buf.readVarInt();
        int n = buf.readVarInt();
        Map<String, CuriosRule> curios = new TreeMap<>();
        for (int i = 0; i < n; i++) curios.put(buf.readUtf(), new CuriosRule(buf.readVarInt(), buf.readVarInt()));
        return new Limits(caps, frozen, curios, exempt);
    }

    // ------------------------------------------------------------------ сравнение (массив в записи)

    @Override
    public boolean equals(Object o) {
        return o instanceof Limits l && exempt == l.exempt && frozen == l.frozen && Arrays.equals(caps, l.caps)
                && curios.equals(l.curios);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(caps) * 31 + frozen * 7 + curios.hashCode() + (exempt ? 1 : 0);
    }

    @Override
    public String toString() {
        return exempt ? "Limits[exempt]" : "Limits" + Arrays.toString(caps) + " frozen=" + frozen + " curios=" + curios;
    }
}
