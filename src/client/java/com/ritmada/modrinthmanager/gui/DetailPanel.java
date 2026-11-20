package com.ritmada.modrinthmanager.gui;

import com.ritmada.modrinthmanager.api.Category;
import com.ritmada.modrinthmanager.api.ModrinthClient;
import com.ritmada.modrinthmanager.api.ModrinthModels;
import com.ritmada.modrinthmanager.download.DownloadManager;
import com.ritmada.modrinthmanager.util.IconCache;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Shows the selected hit's description, resolves the best matching version for the
 * running Minecraft version on selection, and drives the download button + progress.
 */
public final class DetailPanel {

    private int x, y, width, height;
    private ModrinthModels.SearchHit current;
    private Category category;
    private List<ModrinthModels.ProjectVersion> resolvedVersions;
    private ModrinthModels.ProjectVersion chosenVersion;
    private String statusLine = "";
    private boolean resolving;
    private ButtonWidget installButton;

    /** Call once, before {@link #createInstallButton()}: the button is built from these bounds directly. */
    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public ButtonWidget createInstallButton() {
        installButton = ButtonWidget.builder(Text.literal("Install"), b -> install())
                .dimensions(x + 12, y + height - 28, width - 24, 20)
                .build();
        installButton.active = false;
        return installButton;
    }

    public void show(Category category, ModrinthModels.SearchHit hit) {
        this.category = category;
        this.current = hit;
        this.chosenVersion = null;
        this.resolvedVersions = null;
        this.statusLine = "resolving compatible version...";
        this.resolving = true;
        if (installButton != null) installButton.active = false;

        String mcVersion = MinecraftClient.getInstance().getGameVersion();
        String loader = category == Category.MODS ? "fabric" : null;

        ModrinthClient.getVersions(hit.projectId, mcVersion, loader).whenComplete((versions, error) -> {
            MinecraftClient.getInstance().execute(() -> {
                if (current != hit) return; // selection moved on while this was in flight
                resolving = false;
                if (error != null) {
                    statusLine = "failed to resolve a version: " + rootMessage(error);
                    return;
                }
                if (versions.isEmpty()) {
                    statusLine = "no file published for Minecraft " + mcVersion;
                    return;
                }
                resolvedVersions = versions;
                chosenVersion = versions.get(0); // API returns newest-first
                statusLine = "ready: " + chosenVersion.versionNumber;
                if (installButton != null) installButton.active = true;
            });
        });
    }

    private void install() {
        if (chosenVersion == null || category == null) return;
        installButton.active = false;
        statusLine = "downloading...";
        DownloadManager.install(category, chosenVersion).whenComplete((result, error) -> {
            MinecraftClient.getInstance().execute(() -> {
                if (error != null) {
                    statusLine = "install failed: " + rootMessage(error);
                } else if (result instanceof DownloadManager.Ok ok) {
                    statusLine = "installed -> " + category.installFolder + "/" + ok.path().getFileName();
                } else if (result instanceof DownloadManager.AlreadyInstalled already) {
                    statusLine = "already installed: " + already.path().getFileName();
                } else if (result instanceof DownloadManager.Err err) {
                    statusLine = "install failed: " + err.message();
                }
                if (installButton != null) installButton.active = chosenVersion != null;
            });
        });
    }

    public void render(DrawContext context, Theme theme) {
        context.fill(x, y, x + width, y + height, theme.panel());
        context.drawBorder(x, y, width, height, theme.border());

        if (current == null) {
            Text hint = Text.literal("select something from the list").formatted(Formatting.ITALIC);
            context.drawCenteredTextWithShadow(theme.textRenderer(), hint, x + width / 2, y + height / 2, theme.muted());
            return;
        }

        int pad = 12;
        var icon = IconCache.get(current.projectId, current.iconUrl);
        context.drawTexture(icon, x + pad, y + pad, 0, 0, 48, 48, 48, 48);

        context.drawText(theme.textRenderer(), Text.literal(current.title).formatted(Formatting.BOLD),
                x + pad + 56, y + pad, theme.text(), false);
        context.drawText(theme.textRenderer(), Text.literal("by " + current.author).formatted(Formatting.ITALIC),
                x + pad + 56, y + pad + 12, theme.muted(), false);

        int descY = y + pad + 60;
        List<OrderedText> wrapped = theme.textRenderer().wrapLines(
                Text.literal(current.description == null ? "" : current.description), width - pad * 2);
        int lineHeight = theme.textRenderer().fontHeight + 2;
        for (int i = 0; i < wrapped.size() && descY + i * lineHeight < y + height - 48; i++) {
            context.drawText(theme.textRenderer(), wrapped.get(i), x + pad, descY + i * lineHeight, theme.muted(), false);
        }

        int statusY = y + height - 42;
        int color = resolving ? theme.muted() : (chosenVersion != null ? theme.snowWhite() : theme.accent());
        context.drawText(theme.textRenderer(), Text.literal(statusLine), x + pad, statusY, color, false);
    }

    private static String rootMessage(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null) root = root.getCause();
        return root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();
    }
}
