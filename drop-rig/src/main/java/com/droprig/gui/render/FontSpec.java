package com.droprig.gui.render;

/**
 * The GUI's typefaces. Each maps to {@code assets/droprig/font/<id>.json} (a TrueType provider),
 * so text is rendered from real vector fonts instead of the pixel font.
 *
 * <p>Outfit Bold for headings and numbers, Poppins Medium for body text (both SIL OFL).
 */
public enum FontSpec {
    TITLE("title", 16f, 0.706f, 1.00f),
    HEADING("heading", 10f, 0.706f, 1.00f),
    LABEL("label", 8f, 0.706f, 1.00f),
    BADGE("badge", 6.5f, 0.706f, 1.00f),
    BODY("body", 8.5f, 0.697f, 1.05f),
    CAPTION("caption", 7f, 0.697f, 1.05f);

    public final String id;
    /** Em size in GUI units (matches the JSON "size"). */
    public final float size;
    /** Cap height / em, from the font file. */
    public final float capRatio;
    /** Ascender / em, from the font file. */
    public final float ascRatio;

    FontSpec(String id, float size, float capRatio, float ascRatio) {
        this.id = id;
        this.size = size;
        this.capRatio = capRatio;
        this.ascRatio = ascRatio;
    }
}
