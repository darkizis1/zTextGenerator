package fr.maxlego08.text.font;

import fr.maxlego08.text.api.fonts.TtfOffsets;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphMetrics;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Loads {@code .ttf} / {@code .otf} files and measures every character of the font.
 *
 * <p>Since Minecraft cannot use a TTF file directly, the plugin only needs the file to know how to
 * draw and how to measure the letters : the resource pack is generated from these metrics.</p>
 */
public final class TtfFontLoader {

    private static final char[] EXTRA_CHARACTERS = {
            '\u00B0', '\u00A4', '\u00AB', '\u00BB', '\u00B1', '\u00D7', '\u00F7', '\u2013', '\u2014',
            '\u2018', '\u2019', '\u201C', '\u201D', '\u2026', '\u2116', '\u20AC', '\u20BD', '\u2122',
            '\u00A9', '\u00AE', '\u2022', '\u2190', '\u2192', '\u2191', '\u2193'
    };

    private TtfFontLoader() {
    }

    /**
     * Gets every file of the given folder which looks like a font.
     *
     * @param folder the folder to scan
     * @return the list of font files, ordered by name
     */
    public static List<File> findFontFiles(File folder) {

        List<File> files = new ArrayList<>();
        File[] children = folder.listFiles();
        if (children == null) {
            return files;
        }

        for (File file : children) {
            if (!file.isFile()) {
                continue;
            }

            String name = file.getName().toLowerCase(Locale.ROOT);
            if (name.endsWith(".ttf") || name.endsWith(".otf")) {
                files.add(file);
            }
        }

        files.sort((first, second) -> first.getName().compareToIgnoreCase(second.getName()));
        return files;
    }

    /**
     * Loads a font file and measures all its characters.
     *
     * @param file   the {@code .ttf} / {@code .otf} file
     * @param size   the height of the font, in pixels
     * @param logger the logger used to report errors
     * @return the loaded font, or an empty optional if the file cannot be read
     */
    public static Optional<TtfFont> load(File file, int size, Logger logger) {

        try {

            Font baseFont = loadBaseFont(file);
            Font font = baseFont.deriveFont(Font.PLAIN, size);

            BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            FontRenderContext fontRenderContext = graphics.getFontRenderContext();
            FontMetrics fontMetrics = graphics.getFontMetrics(font);

            int ascent = (int) Math.ceil(fontMetrics.getAscent());
            int descent = Math.max(1, (int) Math.ceil(fontMetrics.getDescent()));

            Map<Character, TtfGlyph> glyphs = new LinkedHashMap<>();
            int maxAdvance = 1;

            for (char character : getCharacters(font)) {

                GlyphVector vector = font.createGlyphVector(fontRenderContext, String.valueOf(character));
                GlyphMetrics metrics = vector.getGlyphMetrics(0);

                int advance = Math.max(1, (int) Math.round(metrics.getAdvanceX()));
                Rectangle2D ink = vector.getPixelBounds(fontRenderContext, 0, 0);

                boolean visible = ink.getWidth() >= 1 && ink.getHeight() >= 1;
                int bearing = visible ? Math.max(0, (int) Math.floor(ink.getX())) : 0;
                int inkWidth = visible ? Math.max(1, (int) Math.round(ink.getWidth())) : 0;

                glyphs.put(character, new TtfGlyph(character, advance, bearing, inkWidth, visible));
                maxAdvance = Math.max(maxAdvance, advance);
            }

            graphics.dispose();

            if (glyphs.isEmpty()) {
                logger.warning("The font file " + file.getName() + " does not contain any character.");
                return Optional.empty();
            }

            TtfGlyph space = glyphs.get(' ');
            int spaceAdvance = space == null ? Math.max(2, size / 3) : space.advance();

            if (space == null) {
                glyphs.put(' ', new TtfGlyph(' ', spaceAdvance, 0, 0, false));
            }

            String name = getBaseName(file);
            String fontId = TtfOffsets.NAMESPACE + ":" + sanitize(name);

            return Optional.of(new TtfFont(name, fontId, file, size, ascent, descent, spaceAdvance, maxAdvance, glyphs, font));

        } catch (Exception exception) {
            logger.warning("Unable to load the font " + file.getName() + " : " + exception.getMessage());
            return Optional.empty();
        }
    }

    private static Font loadBaseFont(File file) throws Exception {

        try {
            return Font.createFont(Font.TRUETYPE_FONT, file);
        } catch (Exception exception) {
            // Some OTF files use CFF outlines, Java handles them with the TYPE1 reader on some JDK.
            return Font.createFont(Font.TYPE1_FONT, file);
        }
    }

    /**
     * Gets the name of the font, which is the name of its file without the extension.
     *
     * @param file the font file
     * @return the name of the font
     */
    public static String getBaseName(File file) {

        String name = file.getName();
        int index = name.lastIndexOf('.');
        return index > 0 ? name.substring(0, index) : name;
    }

    /**
     * Transforms a font name into a valid resource pack identifier.
     *
     * @param name the name of the font
     * @return a lowercase identifier which only contains {@code a-z}, {@code 0-9}, {@code _} and {@code -}
     */
    public static String sanitize(String name) {

        StringBuilder builder = new StringBuilder();
        for (char character : name.toLowerCase(Locale.ROOT).toCharArray()) {
            if ((character >= 'a' && character <= 'z') || (character >= '0' && character <= '9') || character == '_' || character == '-') {
                builder.append(character);
            } else {
                builder.append('_');
            }
        }

        String result = builder.toString();
        return result.isEmpty() ? "font" : result;
    }

    /**
     * Gets every character rendered by the plugin : ASCII, latin, cyrillic and a few symbols.
     *
     * @param font the font
     * @return the list of characters, ordered
     */
    private static List<Character> getCharacters(Font font) {

        Set<Character> characters = new LinkedHashSet<>();
        characters.add(' ');

        for (char character = 0x20; character <= 0x7E; character++) {
            characters.add(character);
        }

        for (char character = 0xA0; character <= 0xFF; character++) {
            characters.add(character);
        }

        for (char character = 0x400; character <= 0x45F; character++) {
            characters.add(character);
        }

        for (char character = 0x490; character <= 0x49F; character++) {
            characters.add(character);
        }

        for (char character : EXTRA_CHARACTERS) {
            characters.add(character);
        }

        List<Character> result = new ArrayList<>();
        for (char character : characters) {
            if (character == ' ' || font.canDisplay(character)) {
                result.add(character);
            }
        }

        return result;
    }
}
