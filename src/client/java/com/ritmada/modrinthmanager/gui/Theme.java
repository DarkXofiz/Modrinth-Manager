package com.ritmada.modrinthmanager.gui;

import net.minecraft.client.font.TextRenderer;

/** Dark, red/white winter palette shared by every panel in the screen. */
public record Theme(TextRenderer textRenderer) {

    public int background() {
        return 0xEE0E0F14;
    }

    public int panel() {
        return 0xF0161A22;
    }

    public int panelLight() {
        return 0xF01D222C;
    }

    public int border() {
        return 0xFF2A313E;
    }

    public int accent() {
        return 0xFFE0463B; // christmas red
    }

    public int accentDim() {
        return 0x55E0463B;
    }

    public int rowHover() {
        return 0x332A313E;
    }

    public int text() {
        return 0xFFF2F2F2;
    }

    public int muted() {
        return 0xFFA6ACB8;
    }

    public int snowWhite() {
        return 0xFFEFF6FF;
    }
}
