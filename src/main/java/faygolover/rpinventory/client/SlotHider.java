package faygolover.rpinventory.client;

import faygolover.rpinventory.limits.InvState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.client.event.ContainerScreenEvent;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Закрытые ячейки не видны: неактивный слот экран и так не рисует, а рамку из текстуры окна закрашиваем фоном окна.
 * Цвет фона читаем с самого окна (пиксели панели рядом с блоком ячеек инвентаря, самый частый цвет) один раз на
 * экран — так работает с любым ресурспаком, включая тёмные.
 */
final class SlotHider {
    private SlotHider() {}

    /** Ванильная панель — если прочитать не вышло. */
    private static final int FALLBACK = 0xFFC6C6C6;

    private record Sample(int left, int top, int color) {}

    private static final Map<AbstractContainerScreen<?>, Sample> COLORS = new WeakHashMap<>();

    static void onBackground(ContainerScreenEvent.Render.Background event) {
        AbstractContainerScreen<?> screen = event.getContainerScreen();
        List<Slot> slots = screen.getMenu().slots;
        boolean any = false;
        for (Slot s : slots) {
            if (s.container instanceof Inventory && InvState.hidden(s)) {
                any = true;
                break;
            }
        }
        if (!any) return;

        GuiGraphics g = event.getGuiGraphics();
        int left = screen.getGuiLeft();
        int top = screen.getGuiTop();
        Sample sample = COLORS.get(screen);
        if (sample == null || sample.left != left || sample.top != top) {
            g.flush();
            sample = new Sample(left, top, sampleColor(slots, left, top));
            COLORS.put(screen, sample);
        }
        for (Slot s : slots) {
            if (!(s.container instanceof Inventory) || !InvState.hidden(s)) continue;
            int x = left + s.x - 1;
            int y = top + s.y - 1;
            g.fill(x, y, x + 18, y + 18, sample.color);
        }
    }

    /**
     * Точки панели вокруг блока ячеек инвентаря игрока: слева и справа от каждого ряда, в зазоре над хотбаром.
     * Самый частый цвет — фон.
     */
    private static int sampleColor(List<Slot> slots, int left, int top) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        java.util.Set<Integer> rows = new java.util.TreeSet<>();
        int hotbarY = Integer.MIN_VALUE, storageMaxY = Integer.MIN_VALUE;
        for (Slot s : slots) {
            if (!(s.container instanceof Inventory)) continue;
            int i = s.getContainerSlot();
            if (i >= 36) continue;
            minX = Math.min(minX, s.x);
            maxX = Math.max(maxX, s.x);
            rows.add(s.y);
            if (i < 9) hotbarY = Math.max(hotbarY, s.y);
            else storageMaxY = Math.max(storageMaxY, s.y);
        }
        if (rows.isEmpty()) return FALLBACK;
        Map<Integer, Integer> counts = new HashMap<>();
        for (int y : rows) {
            count(counts, left + minX - 3, top + y + 8);
            count(counts, left + maxX + 16 + 3, top + y + 8);
        }
        if (hotbarY != Integer.MIN_VALUE && storageMaxY != Integer.MIN_VALUE && hotbarY - storageMaxY > 18) {
            int gapY = top + (storageMaxY + 17 + hotbarY - 1) / 2;
            count(counts, left + minX + 8, gapY);
            count(counts, left + maxX + 8, gapY);
        }
        int best = FALLBACK, bestN = 0;
        for (Map.Entry<Integer, Integer> e : counts.entrySet()) {
            if (e.getValue() > bestN) {
                best = e.getKey();
                bestN = e.getValue();
            }
        }
        return best;
    }

    private static void count(Map<Integer, Integer> counts, int guiX, int guiY) {
        Integer c = readPixel(guiX, guiY);
        if (c != null) counts.merge(c, 1, Integer::sum);
    }

    /** Цвет пикселя кадра под точкой GUI (ARGB, непрозрачный) или null — вне окна. */
    private static Integer readPixel(int guiX, int guiY) {
        Minecraft mc = Minecraft.getInstance();
        double scale = mc.getWindow().getGuiScale();
        var target = mc.getMainRenderTarget();
        int px = (int) ((guiX + 0.5) * scale);
        int py = target.height - 1 - (int) ((guiY + 0.5) * scale);
        if (px < 0 || py < 0 || px >= target.width || py >= target.height) return null;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer buf = stack.malloc(4);
            GL11.glReadPixels(px, py, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
            int r = buf.get(0) & 0xFF, gr = buf.get(1) & 0xFF, b = buf.get(2) & 0xFF;
            return 0xFF000000 | (r << 16) | (gr << 8) | b;
        }
    }
}
