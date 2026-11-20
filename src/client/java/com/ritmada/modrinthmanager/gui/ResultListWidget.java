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

    private static final int ROW_HEIGHT = 36;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int SCROLLBAR_HIT_PADDING = 4; // grab area is wider than the drawn bar

    private final List<ModrinthModels.SearchHit> items = new ArrayList<>();
    private int x, y, width, height;
    private double scroll;
    private int selectedIndex = -1;
    private boolean draggingScrollbar;
    private float dragGrabOffset; // where inside the thumb the drag started, so it doesn't "jump"
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
        context.fill(x, y, x + width, y + height, theme.panelLight());
        context.enableScissor(x, y, x + width, y + height);

        int rowY = y - (int) scroll;
        for (int i = 0; i < items.size(); i++) {
            ModrinthModels.SearchHit hit = items.get(i);
            int top = rowY + i * ROW_HEIGHT;
            if (top + ROW_HEIGHT < y || top > y + height) continue;

            boolean hovered = !draggingScrollbar && mouseX >= x && mouseX <= x + width && mouseY >= top && mouseY <= top + ROW_HEIGHT;
            boolean selected = i == selectedIndex;

            if (selected) {
                context.fill(x, top, x + width, top + ROW_HEIGHT - 1, theme.accentDim());
                context.fill(x, top, x + 3, top + ROW_HEIGHT - 1, theme.accent());
            } else if (hovered) {
                context.fill(x, top, x + width, top + ROW_HEIGHT - 1, theme.rowHover());
            }
            // thin separator so rows read as distinct cards rather than one grey slab
            context.fill(x + 4, top + ROW_HEIGHT - 1, x + width - 4, top + ROW_HEIGHT, theme.border());

            IconCache.Handle icon = IconCache.get(hit.projectId, hit.iconUrl);
            int iconSize = 24;
            int iconX = x + 8;
            int iconY = top + (ROW_HEIGHT - iconSize) / 2 - 1;
            context.fill(iconX - 1, iconY - 1, iconX + iconSize + 1, iconY + iconSize + 1, theme.border());
            context.drawTexture(icon.id(), iconX, iconY, 0, 0, iconSize, iconSize, icon.width(), icon.height());

            int textX = iconX + iconSize + 10;
            context.drawText(theme.textRenderer(), Text.literal(hit.title).formatted(Formatting.BOLD),
                    textX, top + 5, theme.text(), false);

            String desc = hit.description == null ? "" : hit.description;
            int maxDescWidth = width - (textX - x) - 10;
            Text trimmed = Text.literal(theme.textRenderer().trimToWidth(desc, maxDescWidth));
            context.drawText(theme.textRenderer(), trimmed, textX, top + 17, theme.muted(), false);

            String downloads = formatDownloads(hit.downloads);
            int dlWidth = theme.textRenderer().getWidth(downloads);
            context.drawText(theme.textRenderer(), Text.literal(downloads),
                    x + width - dlWidth - 10, top + 5, theme.accent(), false);
        }

        context.disableScissor();
        drawScrollbar(context, theme, mouseX, mouseY);
    }

    private int contentHeight() {
        return items.size() * ROW_HEIGHT;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - height);
    }

    private void drawScrollbar(DrawContext context, Theme theme, int mouseX, int mouseY) {
        int maxScroll = maxScroll();
        if (maxScroll <= 0) return;

        int trackX = x + width - SCROLLBAR_WIDTH - 2;
        context.fill(trackX, y, trackX + SCROLLBAR_WIDTH, y + height, theme.panel());

        int barHeight = thumbHeight();
        int barY = thumbY();
        boolean hoveredThumb = mouseX >= trackX - SCROLLBAR_HIT_PADDING && mouseX <= trackX + SCROLLBAR_WIDTH + SCROLLBAR_HIT_PADDING
                && mouseY >= barY && mouseY <= barY + barHeight;

        int color = draggingScrollbar || hoveredThumb ? theme.snowWhite() : theme.accent();
        context.fill(trackX, barY, trackX + SCROLLBAR_WIDTH, barY + barHeight, color);
    }

    private int thumbHeight() {
        float visibleRatio = (float) height / contentHeight();
        return Math.max(14, (int) (height * visibleRatio));
    }

    private int thumbY() {
        int maxScroll = maxScroll();
        float scrollRatio = maxScroll > 0 ? (float) (scroll / maxScroll) : 0;
        return y + (int) ((height - thumbHeight()) * scrollRatio);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) return false;

        int maxScroll = maxScroll();
        if (maxScroll > 0) {
            int trackX = x + width - SCROLLBAR_WIDTH - 2;
            if (mouseX >= trackX - SCROLLBAR_HIT_PADDING && mouseX <= trackX + SCROLLBAR_WIDTH + SCROLLBAR_HIT_PADDING) {
                int barY = thumbY();
                int barHeight = thumbHeight();
                if (mouseY >= barY && mouseY <= barY + barHeight) {
                    draggingScrollbar = true;
                    dragGrabOffset = (float) (mouseY - barY);
                } else {
                    // clicked the track above/below the thumb: jump the thumb there directly
                    draggingScrollbar = true;
                    dragGrabOffset = barHeight / 2f;
                    dragScrollTo(mouseY);
                }
                return true;
            }
        }

        int relativeY = (int) (mouseY - y + scroll);
        int index = relativeY / ROW_HEIGHT;
        if (index >= 0 && index < items.size()) {
            selectedIndex = index;
            onSelect.accept(items.get(index));
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!draggingScrollbar) return false;
        dragScrollTo(mouseY);
        return true;
    }

    public void mouseReleased() {
        draggingScrollbar = false;
    }

    private void dragScrollTo(double mouseY) {
        int maxScroll = maxScroll();
        if (maxScroll <= 0) return;
        int barHeight = thumbHeight();
        float targetThumbTop = (float) (mouseY - y - dragGrabOffset);
        float ratio = targetThumbTop / Math.max(1, height - barHeight);
        scroll = Math.max(0, Math.min(maxScroll, ratio * maxScroll));
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (mouseX < x || mouseX > x + width || mouseY < y || mouseY > y + height) return false;
        int maxScroll = maxScroll();
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
