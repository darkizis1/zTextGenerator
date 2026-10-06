package fr.maxlego08.text.font;

import java.awt.Font;
import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * A font loaded from a {@code .ttf} (or {@code .otf}) file.
 *
 * <p>Every character is measured once, the plugin then knows the exact width of each letter. The
 * metrics are used to align texts and to generate the resource pack which contains the glyphs.</p>
 */
public class TtfFont {

    private final String name;
    private final String fontId;
    private final File file;
    private final int size;
    private final int ascent;
    private final int descent;
    private final int spaceAdvance;
    private final int maxAdvance;
    private final Map<Character, TtfGlyph> glyphs;
    private final Font renderFont;

    public TtfFont(String name, String fontId, File file, int size, int ascent, int descent, int spaceAdvance, int maxAdvance, Map<Character, TtfGlyph> glyphs, Font renderFont) {
        this.name = name;
        this.fontId = fontId;
        this.file = file;
        this.size = size;
        this.ascent = ascent;
        this.descent = descent;
        this.spaceAdvance = spaceAdvance;
        this.maxAdvance = maxAdvance;
        this.glyphs = glyphs;
        this.renderFont = renderFont;
    }

    /**
     * Gets the {@link Font} used to draw the glyphs of this font.
     *
     * @return the java font, already sized
     */
    public Font getRenderFont() {
        return this.renderFont;
    }

    /**
     * Gets the name of the font, which is the name of the file without its extension.
     *
     * @return the name of the font
     */
    public String getName() {
        return this.name;
    }

    /**
     * Gets the identifier of the font inside the generated resource pack, for example
     * {@code ztextgen:myfont}.
     *
     * @return the font identifier
     */
    public String getFontId() {
        return this.fontId;
    }

    public File getFile() {
        return this.file;
    }

    public int getSize() {
        return this.size;
    }

    public int getAscent() {
        return this.ascent;
    }

    public int getDescent() {
        return this.descent;
    }

    /**
     * Gets the height of a cell, in pixels.
     *
     * @return the height of the font, in pixels
     */
    public int getHeight() {
        return this.ascent + this.descent;
    }

    public int getSpaceAdvance() {
        return this.spaceAdvance;
    }

    public int getMaxAdvance() {
        return this.maxAdvance;
    }

    /**
     * Gets every glyph of this font.
     *
     * @return the glyphs, ordered
     */
    public Map<Character, TtfGlyph> getGlyphs() {
        return this.glyphs;
    }

    /**
     * Gets the glyph of a character.
     *
     * @param character the character
     * @return the glyph, or null if the font does not contain this character
     */
    public TtfGlyph getGlyph(char character) {
        return this.glyphs.get(character);
    }

    /**
     * Gets the width of a character, in pixels.
     *
     * @param character the character
     * @return the width of the character, or the width of a space if the character is unknown
     */
    public int getAdvance(char character) {
        TtfGlyph glyph = this.glyphs.get(character);
        return glyph == null ? this.spaceAdvance : glyph.advance();
    }

    /**
     * Gets the characters which have some ink, these characters are the ones drawn inside the
     * generated texture.
     *
     * @return the visible glyphs, ordered
     */
    public List<TtfGlyph> getVisibleGlyphs() {
        return this.glyphs.values().stream().filter(TtfGlyph::visible).toList();
    }

    /**
     * Gets the characters without any ink (space, non breaking space, ...), they are declared as
     * "space" glyphs inside the generated resource pack.
     *
     * @return the invisible glyphs, ordered
     */
    public List<TtfGlyph> getInvisibleGlyphs() {
        return this.glyphs.values().stream().filter(glyph -> !glyph.visible()).toList();
    }

    @Override
    public String toString() {
        return "TtfFont{name=" + this.name + ", glyphs=" + this.glyphs.size() + ", height=" + getHeight() + "}";
    }
}
