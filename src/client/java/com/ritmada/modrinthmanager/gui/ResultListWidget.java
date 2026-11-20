package com.ritmada.modrinthmanager.gui;

import com.ritmada.modrinthmanager.api.ModrinthModels;
import com.ritmada.modrinthmanager.util.IconCache;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Manually driven scroll list. Not a vanilla ElementListWidget subclass on purpose:
 * that base class's constructor and Entry#render signature both drift between
 * Minecraft minor versions, which is a recurring first-compile breakage point.
 * This is plain state + render + input, callable from any Screen unchanged.
 */
public final class ResultListWidget {

    private static final int ROW_HEIGHT = 34;

    private final List<ModrinthModels.SearchHit> items = new ArrayList<>();
    private int x, y, width, height;
    private double scroll;
    private int selectedIndex = -1;
    private Consumer<ModrinthModels.SearchHit> onSelect = hit -> {
    };

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void onSelect(Consumer<ModrinthModels.SearchHit> callback) {
        this.onSelect = callback;
    }

    public void setItems(List<ModrinthModels.SearchHit> newItems) {
        items.clear();
        items.addAll(newItems);
        scroll = 0;
        selectedIndex = -1;
    }

    public void render(DrawContext context, int mouseX, int mouseY, Theme theme) {
        context.enableScissor(x, y, x + width, y + height);

        int rowY = y - (int) scroll;
        for (int i = 0; i < items.size(); i++) {
            ModrinthModels.SearchHit hit = items.get(i);
            int top = rowY + i * ROW_HEIGHT;
            if (top + ROW_HEIGHT < y || top > y + height) continue;

            boolean hovered = mouseX >= x && mouseX <= x + width && mouseY >= top && mouseY <= top + ROW_HEIGHT;
            boolean selected = i == selectedIndex;

            if (selected) {
                context.fill(x, top, x + width, top + ROW_HEIGHT, theme.accentDim());
            } else if (hovered) {
                context.fill(x, top, x + width, top + ROW_HEIGHT, theme.rowHover());
            }

            var icon = IconCache.get(hit.projectId, hit.iconUrl);
            int iconSize = 24;
            context.drawTexture(icon, x + 6, top + (ROW_HEIGHT - iconSize) / 2, 0, 0, iconSize, iconSize, iconSize, iconSize);

            int textX = x + 6 + iconSize + 8;
            context.drawText(theme.textRenderer(), Text.literal(hit.title).formatted(Formatting.BOLD),
                    textX, top + 4, theme.text(), false);

            String desc = hit.description == null ? "" : hit.description;
            int maxDescWidth = width - (textX - x) - 10;
            Text trimmed = Text.literal(theme.textRenderer().trimToWidth(desc, maxDescWidth));
            context.drawText(theme.textRenderer(), trimmed, textX, top + 16, theme.muted(), false);

            String downloads = formatDownloads(hit.downloads);
            int dlWidth = theme.textRenderer().getWidth(downloads);
            context.drawText(theme.textRenderer(), Text.literal(downloads),
                    x + width - dlWidth - 8, top + 4, theme.accent(), false);
        }

        context.disableScissor();
        drawScrollbar(context, theme);
    }

    private void drawScrollbar(DrawContext context, Theme theme) {
        int contentHeight = items.size() * ROW_HEIGHT;
        if (contentHeight <= height) return;

        int trackX = x + width - 4;
        context.fill(trackX, y, trackX + 3, y + height, theme.border());

        float visibleRatio = (float) height / contentHeight;
        int barHeight = Math.max(12, (int) (height * visibleRatio));
        int maxScroll = contentHeight - height;
        float scrollRatio = maxScroll > 0 ? (float) (scroll / maxScroll) : 0;
        int barY = y + (int) ((height - barHeight) * scrollRatio);

        context.fill(trackX, barY, trackX + 3, barY + barHeight, theme.accent());
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) return false;

        int relativeY = (int) (mouseY - y + scroll);
        int index = relativeY / ROW_HEIGHT;
        if (index >= 0 && index < items.size()) {
            selectedIndex = index;
            onSelect.accept(items.get(index));
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) return false;
        int contentHeight = items.size() * ROW_HEIGHT;
        int maxScroll = Math.max(0, contentHeight - height);
        scroll = Math.max(0, Math.min(maxScroll, scroll - amount * 18));
        return true;
    }

    private static String formatDownloads(long downloads) {
        if (downloads >= 1_000_000) return String.format("%.1fM \u2193", downloads / 1_000_000.0);
        if (downloads >= 1_000) return String.format("%.1fK \u2193", downloads / 1_000.0);
        return downloads + " \u2193";
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }
}
