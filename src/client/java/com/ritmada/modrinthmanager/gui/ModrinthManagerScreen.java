package com.ritmada.modrinthmanager.gui;

import com.ritmada.modrinthmanager.api.Category;
import com.ritmada.modrinthmanager.api.ModrinthClient;
import com.ritmada.modrinthmanager.api.ModrinthModels;
import com.ritmada.modrinthmanager.util.ChristmasMusic;
import com.ritmada.modrinthmanager.util.SnowOverlay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public final class ModrinthManagerScreen extends Screen {

    private static final int PANEL_MARGIN = 20;
    private static final int SIDEBAR_WIDTH = 132;
    private static final int DETAIL_WIDTH = 240;
    private static final int HEADER_HEIGHT = 36;
    private static final int TAB_HEIGHT = 26;
    private static final long SEARCH_DEBOUNCE_MS = 280;

    private final Screen parent;
    private Theme theme;
    private SnowOverlay snow;

    private Category activeCategory = Category.MODS;
    private TextFieldWidget searchField;
    private final ResultListWidget resultList = new ResultListWidget();
    private final DetailPanel detailPanel = new DetailPanel();

    private long lastTypedAt;
    private String lastQueried = "\u0000"; // forces an initial load
    private int searchGeneration;
    private String loadError;

    private int panelX, panelY, panelWidth, panelHeight;
    private int sidebarX, sidebarY, searchBoxX, searchBoxY, searchBoxWidth;
    private long lastFrameNanos = System.nanoTime();

    public ModrinthManagerScreen(Screen parent) {
        super(Text.literal("Modrinth Manager"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        theme = new Theme(this.textRenderer);
        snow = new SnowOverlay(width, height);

        panelWidth = Math.min(780, width - PANEL_MARGIN * 2);
        panelHeight = Math.min(480, height - PANEL_MARGIN * 2);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;

        sidebarX = panelX + 10;
        sidebarY = panelY + HEADER_HEIGHT + 12;

        int listX = sidebarX + SIDEBAR_WIDTH + 12;
        int contentTop = panelY + HEADER_HEIGHT + 40;
        int listWidth = panelWidth - SIDEBAR_WIDTH - DETAIL_WIDTH - 44;

        searchBoxX = listX;
        searchBoxY = panelY + HEADER_HEIGHT + 8;
        searchBoxWidth = listWidth;

        searchField = new TextFieldWidget(this.textRenderer, searchBoxX + 8, searchBoxY + 4, searchBoxWidth - 16, 16, Text.literal("search"));
        searchField.setDrawsBackground(false);
        searchField.setEditableColor(theme.text());
        searchField.setSuggestion("search " + activeCategory.displayName.toLowerCase() + "...");
        searchField.setChangedListener(s -> lastTypedAt = System.currentTimeMillis());
        addDrawableChild(searchField);
        setInitialFocus(searchField);

        resultList.setBounds(listX, contentTop, listWidth, panelY + panelHeight - 14 - contentTop);
        resultList.onSelect(hit -> detailPanel.show(activeCategory, hit));

        int detailX = listX + listWidth + 10;
        detailPanel.setBounds(detailX, contentTop, panelX + panelWidth - 10 - detailX, panelY + panelHeight - 14 - contentTop);
        addDrawableChild(detailPanel.createInstallButton());

        triggerSearch(true);
        ChristmasMusic.playIfNotRunning();
    }

    private void switchCategory(Category category) {
        if (category == activeCategory) return;
        activeCategory = category;
        searchField.setSuggestion("search " + category.displayName.toLowerCase() + "...");
        lastQueried = "\u0000";
        triggerSearch(true);
    }

    @Override
    public void tick() {
        if (!lastQueried.equals(searchField.getText()) && System.currentTimeMillis() - lastTypedAt > SEARCH_DEBOUNCE_MS) {
            triggerSearch(false);
        }
    }

    private void triggerSearch(boolean immediate) {
        String query = searchField == null ? "" : searchField.getText();
        lastQueried = query;
        int myGeneration = ++searchGeneration;
        loadError = null;

        String mcVersion = MinecraftClient.getInstance().getGameVersion();
        ModrinthClient.search(activeCategory, query, mcVersion, 0, 50).whenComplete((response, error) -> {
            MinecraftClient.getInstance().execute(() -> {
                if (myGeneration != searchGeneration) return; // stale response, newer search already fired
                if (error != null) {
                    loadError = "search failed: " + rootMessage(error);
                    resultList.setItems(List.of());
                    return;
                }
                resultList.setItems(response.hits);
            });
        });
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0xAA000000);

        long now = System.nanoTime();
        float deltaSeconds = (now - lastFrameNanos) / 1_000_000_000f;
        lastFrameNanos = now;

        renderPanelBackground(context);
        snow.tickAndRender(context, width, height, Math.min(deltaSeconds, 0.1f));
        renderHeader(context);
        renderSidebar(context, mouseX, mouseY);
        renderSearchBox(context);

        // these paint full-panel backgrounds, so they must go BEFORE super.render():
        // the install button and search field text are real child widgets, drawn on top after.
        resultList.render(context, mouseX, mouseY, theme);
        detailPanel.render(context, theme);

        super.render(context, mouseX, mouseY, delta);

        if (loadError != null) {
            context.drawText(textRenderer, Text.literal(loadError).formatted(Formatting.RED),
                    panelX + 10, panelY + panelHeight - 10, 0xFFFF5555, false);
        } else if (resultList.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("no results"),
                    sidebarX + SIDEBAR_WIDTH + 12 + (panelWidth - SIDEBAR_WIDTH - DETAIL_WIDTH - 44) / 2,
                    panelY + panelHeight / 2, theme.muted());
        }
    }

    private void renderPanelBackground(DrawContext context) {
        context.fill(panelX + 1, panelY + 1, panelX + panelWidth - 1, panelY + panelHeight - 1, theme.background());
        context.drawBorder(panelX, panelY, panelWidth, panelHeight, theme.accent());
        context.drawBorder(panelX + 1, panelY + 1, panelWidth - 2, panelHeight - 2, theme.border());
    }

    private void renderHeader(DrawContext context) {
        context.fillGradient(panelX + 1, panelY + 1, panelX + panelWidth - 1, panelY + HEADER_HEIGHT, theme.panelLight(), theme.panel());
        context.fill(panelX + 1, panelY + HEADER_HEIGHT, panelX + panelWidth - 1, panelY + HEADER_HEIGHT + 1, theme.accent());

        context.drawText(textRenderer, Text.literal("\u2744").formatted(Formatting.WHITE), panelX + 12, panelY + 13, theme.snowWhite(), false);
        context.drawText(textRenderer, Text.literal("Modrinth Manager").formatted(Formatting.BOLD),
                panelX + 24, panelY + 13, theme.snowWhite(), true);

        String tag = activeCategory.displayName;
        int tagWidth = textRenderer.getWidth(tag);
        context.fill(panelX + panelWidth - tagWidth - 24, panelY + 10, panelX + panelWidth - 12, panelY + 26, theme.accentDim());
        context.drawText(textRenderer, Text.literal(tag), panelX + panelWidth - tagWidth - 18, panelY + 13, theme.text(), false);
    }

    private void renderSidebar(DrawContext context, int mouseX, int mouseY) {
        Category[] categories = Category.values();
        for (int i = 0; i < categories.length; i++) {
            Category category = categories[i];
            int top = sidebarY + i * (TAB_HEIGHT + 4);
            boolean active = category == activeCategory;
            boolean hovered = !active && isInside(mouseX, mouseY, sidebarX, top, SIDEBAR_WIDTH, TAB_HEIGHT);

            if (active) {
                context.fill(sidebarX, top, sidebarX + SIDEBAR_WIDTH, top + TAB_HEIGHT, theme.accentDim());
                context.fill(sidebarX, top, sidebarX + 3, top + TAB_HEIGHT, theme.accent());
            } else if (hovered) {
                context.fill(sidebarX, top, sidebarX + SIDEBAR_WIDTH, top + TAB_HEIGHT, theme.rowHover());
            }
            int textColor = active ? theme.snowWhite() : theme.muted();
            context.drawText(textRenderer, Text.literal(category.displayName), sidebarX + 12, top + (TAB_HEIGHT - 8) / 2, textColor, false);
        }

        int settingsTop = panelY + panelHeight - 14 - TAB_HEIGHT;
        boolean settingsHovered = isInside(mouseX, mouseY, sidebarX, settingsTop, SIDEBAR_WIDTH, TAB_HEIGHT);
        if (settingsHovered) {
            context.fill(sidebarX, settingsTop, sidebarX + SIDEBAR_WIDTH, settingsTop + TAB_HEIGHT, theme.rowHover());
        }
        context.drawText(textRenderer, Text.literal("Settings"), sidebarX + 12, settingsTop + (TAB_HEIGHT - 8) / 2, theme.muted(), false);
    }

    private void renderSearchBox(DrawContext context) {
        context.fill(searchBoxX, searchBoxY, searchBoxX + searchBoxWidth, searchBoxY + 24, theme.panelLight());
        context.drawBorder(searchBoxX, searchBoxY, searchBoxWidth, 24, theme.border());
        // small accent tick instead of a glyph that may not exist in the game's font atlas
        context.fill(searchBoxX + 6, searchBoxY + 8, searchBoxX + 8, searchBoxY + 16, theme.accent());
    }

    private static boolean isInside(int px, int py, int x, int y, int w, int h) {
        return px >= x && px <= x + w && py >= y && py <= y + h;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            Category[] categories = Category.values();
            for (int i = 0; i < categories.length; i++) {
                int top = sidebarY + i * (TAB_HEIGHT + 4);
                if (isInside((int) mouseX, (int) mouseY, sidebarX, top, SIDEBAR_WIDTH, TAB_HEIGHT)) {
                    switchCategory(categories[i]);
                    return true;
                }
            }
        }
        if (resultList.mouseClicked(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (resultList.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        resultList.mouseReleased();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (resultList.mouseScrolled(mouseX, mouseY, amount)) return true;
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public void close() {
        ChristmasMusic.stop();
        if (client != null) client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private static String rootMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null) root = root.getCause();
        return root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();
    }
}
