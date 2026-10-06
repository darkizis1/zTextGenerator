package fr.maxlego08.text.api;

import fr.maxlego08.text.api.fonts.TtfOffsets;

public enum FontType {

    ITEMSADDER(":%format%:", ":offset_%pixels%:"),
    NEXO("<glyph:%format%>", "<shift:%pixels%>"),
    ORAXEN("<glyph_%format%>", "<shift:%pixels%>"),

    /**
     * Fonts loaded directly from {font}.ttf files.
     *
     * <p>The plugin generates the resource pack itself, so no glyph and no offset have to be
     * declared inside an external pack plugin. Every character is rendered with the font of the
     * alphabet it belongs to, and the offsets use the {@code ztextgen:offsets} font which is
     * generated in the same pack.</p>
     */
    TTF("", "") {
        @Override
        public String getFormat(String format) {
            // With a TTF font there is no glyph image to reference, the letters themselves are
            // rendered by the generated resource pack. Names of images (inventory backgrounds for
            // example) are ignored.
            return "";
        }

        @Override
        public String getOffset(int pixels) {
            return TtfOffsets.offset(pixels);
        }
    },

    ;

    private final String format;
    private final String offset;

    FontType(String format, String offset) {
        this.format = format;
        this.offset = offset;
    }

    public String getFormat() {
        return this.format;
    }

    public String getFormat(String format) {
        return this.format.replace("%format%", format);
    }

    public String getOffset() {
        return this.offset;
    }

    public String getOffset(int pixels) {
        return this.offset.replace("%pixels%", String.valueOf(pixels));
    }
}
