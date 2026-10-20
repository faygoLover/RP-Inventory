package faygolover.rpinventory.client;

import com.mojang.blaze3d.systems.RenderSystem;
import faygolover.rpcore.api.InventoryProvider.Part;
import faygolover.rpinventory.limits.InvState;
import faygolover.rpinventory.limits.Limits;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;

/**
 * HUD-хотбар по открытым ячейкам: полоска короче и по центру, вторая рука и индикатор удара — у её краёв.
 * Замороженные ячейки — с серой пеленой. Ограничений нет — ванильный хотбар как есть.
 */
final class ClientHotbar {
    private ClientHotbar() {}

    private static final ResourceLocation WIDGETS = new ResourceLocation("textures/gui/widgets.png");
    private static final ResourceLocation ICONS = new ResourceLocation("textures/gui/icons.png");
    private static final int FROZEN_TINT = 0x90303030;

    static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.player.isSpectator()) return;
        if (!(mc.getCameraEntity() instanceof Player player)) return;
        Limits l = InvState.get(player);
        if (l.exempt() || (l.capacity(Part.HOTBAR) == 9 && !l.partFrozen(Part.HOTBAR) && !l.partFrozen(Part.OFFHAND))) return;
        event.setCanceled(true);
        render(event.getGuiGraphics(), mc, player, l, event.getPartialTick(),
                event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight());
    }

    private static void render(GuiGraphics g, Minecraft mc, Player player, Limits l, float partialTick, int sw, int sh) {
        Inventory inv = player.getInventory();
        int n = l.capacity(Part.HOTBAR);
        int w = n > 0 ? 20 * n + 2 : 0;
        int x0 = sw / 2 - w / 2;
        ItemStack offhand = player.getOffhandItem();
        HumanoidArm offSide = player.getMainArm().getOpposite();

        RenderSystem.enableBlend();
        g.pose().pushPose();
        g.pose().translate(0, 0, -90);
        if (n > 0) {
            g.blit(WIDGETS, x0, sh - 22, 0, 0, 20 * n + 1, 22);
            g.blit(WIDGETS, x0 + 20 * n + 1, sh - 22, 181, 0, 1, 22);
            if (inv.selected < n) g.blit(WIDGETS, x0 - 1 + inv.selected * 20, sh - 23, 0, 22, 24, 22);
        }
        if (!offhand.isEmpty()) {
            if (offSide == HumanoidArm.LEFT) g.blit(WIDGETS, x0 - 29, sh - 23, 24, 22, 29, 24);
            else g.blit(WIDGETS, x0 + w, sh - 23, 53, 22, 29, 24);
        }
        g.pose().popPose();

        int seed = 1;
        int y = sh - 19;
        for (int i = 0; i < n; i++) {
            int x = x0 + 3 + i * 20;
            renderSlot(g, mc, x, y, partialTick, player, inv.items.get(i), seed++);
            if (l.frozen(i)) g.fill(RenderType.guiOverlay(), x, y, x + 16, y + 16, FROZEN_TINT);
        }
        if (!offhand.isEmpty()) {
            int x = offSide == HumanoidArm.LEFT ? x0 - 26 : x0 + w + 10;
            renderSlot(g, mc, x, y, partialTick, player, offhand, seed);
            if (l.frozen(Inventory.SLOT_OFFHAND)) g.fill(RenderType.guiOverlay(), x, y, x + 16, y + 16, FROZEN_TINT);
        }

        if (mc.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR) {
            float f = player.getAttackStrengthScale(0.0F);
            if (f < 1.0F) {
                int ay = sh - 20;
                int ax = offSide == HumanoidArm.LEFT ? x0 + w + 6 : x0 - 22;
                int filled = (int) (f * 19.0F);
                g.blit(ICONS, ax, ay, 0, 94, 18, 18);
                g.blit(ICONS, ax, ay + 18 - filled, 18, 112 - filled, 18, filled);
            }
        }
        RenderSystem.disableBlend();
    }

    /** Как ванильный Gui.renderSlot: «подпрыгивание» подобранного предмета, сам предмет, число и полоска прочности. */
    private static void renderSlot(GuiGraphics g, Minecraft mc, int x, int y, float partialTick, Player player, ItemStack stack, int seed) {
        if (stack.isEmpty()) return;
        float pop = stack.getPopTime() - partialTick;
        if (pop > 0.0F) {
            float s = 1.0F + pop / 5.0F;
            g.pose().pushPose();
            g.pose().translate(x + 8, y + 12, 0);
            g.pose().scale(1.0F / s, (s + 1.0F) / 2.0F, 1.0F);
            g.pose().translate(-(x + 8), -(y + 12), 0);
        }
        g.renderItem(player, stack, x, y, seed);
        if (pop > 0.0F) g.pose().popPose();
        g.renderItemDecorations(mc.font, stack, x, y);
    }
}
