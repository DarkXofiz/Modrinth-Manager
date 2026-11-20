package com.ritmada.modrinthmanager.gui;

import com.ritmada.modrinthmanager.api.Category;
import com.ritmada.modrinthmanager.api.ModrinthClient;
import com.ritmada.modrinthmanager.api.ModrinthModels;
import com.ritmada.modrinthmanager.util.ChristmasMusic;
import com.ritmada.modrinthmanager.util.SnowOverlay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public final class ModrinthManagerScreen extends Screen {

    private static final int PANEL_MARGIN = 20;
    private static final int SIDEBAR_WIDTH = 140;
    private static final int DETAIL_WIDTH = 240;
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
    private long lastFrameNanos = System.nanoTime();

    public ModrinthManagerScreen(Screen parent) {
        super(Text.literal("Modrinth Manager"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        theme = new Theme(this.textRenderer);
        snow = new SnowOverlay(width, height);

        panelWidth = Math.min(760, width - PANEL_MARGIN * 2);
        panelHeight = Math.min(460, height - PANEL_MARGIN * 2);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;

        int headerHeight = 34;
        int sidebarX = panelX + 8;
        int sidebarY = panelY + headerHeight + 8;

        int y = sidebarY;
        for (Category category : Category.values()) {
            addDrawableChild(ButtonWidget.builder(Text.literal(category.displayName), b -> switchCategory(category))
                    .dimensions(sidebarX, y, SIDEBAR_WIDTH, 20)
                    .build());
            y += 24;
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("Settings"), b -> {
        }).dimensions(sidebarX, panelY + panelHeight - 28, SIDEBAR_WIDTH, 20).build());

        int listX = sidebarX + SIDEBAR_WIDTH + 10;
        int contentTop = panelY + headerHeight + 36;
        int listWidth = panelWidth - SIDEBAR_WIDTH - DETAIL_WIDTH - 40;

        searchField = new TextFieldWidget(this.textRenderer, listX, panelY + headerHeight + 8, listWidth, 20, Text.literal("search"));
        searchField.setSuggestion("search " + activeCategory.displayName.toLowerCase() + "...");
        searchField.setChangedListener(s -> lastTypedAt = System.currentTimeMillis());
        addDrawableChild(searchField);
        setInitialFocus(searchField);

        resultList.setBounds(listX, contentTop, listWidth, panelY + panelHeight - 12 - contentTop);
        resultList.onSelect(hit -> detailPanel.show(activeCategory, hit));

        int detailX = listX + listWidth + 10;
        detailPanel.setBounds(detailX, contentTop, panelX + panelWidth - 8 - detailX, panelY + panelHeight - 12 - contentTop);
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

        context.drawText(textRenderer, Text.literal("\u2744 Modrinth Manager").formatted(Formatting.BOLD),
                panelX + 10, panelY + 10, theme.snowWhite(), false);
        context.drawText(textRenderer, Text.literal(activeCategory.displayName).formatted(Formatting.GRAY),
                panelX + panelWidth - 90, panelY + 10, theme.muted(), false);

        // these two paint full-panel backgrounds, so they must go BEFORE super.render():
        // the install button and search field are real child widgets and need to land on top.
        resultList.render(context, mouseX, mouseY, theme);
        detailPanel.render(context, theme);

        super.render(context, mouseX, mouseY, delta);

        if (loadError != null) {
            context.drawText(textRenderer, Text.literal(loadError).formatted(Formatting.RED),
                    panelX + 10, panelY + panelHeight - 10, 0xFFFF5555, false);
        } else if (resultList.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("no results"), panelX + panelWidth / 2, panelY + panelHeight / 2, theme.muted());
        }
    }

    private void renderPanelBackground(DrawContext context) {
        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, theme.background());
        context.fill(panelX, panelY, panelX + panelWidth, panelY + 34, theme.panelLight());
        context.drawBorder(panelX, panelY, panelWidth, panelHeight, theme.accent());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (resultList.mouseClicked(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (resultList.mouseScrolled(mouseX, mouseY, verticalAmount)) return true;
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
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
