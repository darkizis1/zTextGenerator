package fr.maxlego08.text.font;

import fr.maxlego08.text.api.fonts.TtfOffsets;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Generates the resource pack which contains the glyphs of every {@code .ttf} font.
 *
 * <p>The generated pack replaces the need of an external pack plugin : the plugin renders every
 * letter of the font into a texture, writes the Minecraft font json and zips everything.</p>
 */
public final class TtfPackGenerator {

    private static final String NAMESPACE = TtfOffsets.NAMESPACE;
    private static final int FILLER_START = 0xE000;

    private TtfPackGenerator() {
    }

    /**
     * Result of the generation.
     *
     * @param file the generated zip
     * @param hash the sha1 of the zip, used to send the pack to the players
     */
    public record GeneratedPack(File file, byte[] hash) {
    }

    /**
     * Generates the resource pack of the given fonts.
     *
     * @param fonts       the fonts to include inside the pack
     * @param output      the folder where the pack is generated
     * @param packFormat  the {@code pack_format} written inside the {@code pack.mcmeta}
     * @param description the description of the pack
     * @param logger      the logger used to report errors
     * @return the generated pack, or null if an error occurred
     */
    public static GeneratedPack generate(List<TtfFont> fonts, File output, int packFormat, String description, Logger logger) {

        try {

            File workFolder = new File(output, "pack");
            delete(workFolder);
            if (!workFolder.mkdirs()) {
                logger.warning("Unable to create the folder " + workFolder.getAbsolutePath());
            }

            File fontFolder = new File(workFolder, "assets/" + NAMESPACE + "/font");
            File textureFolder = new File(workFolder, "assets/" + NAMESPACE + "/textures/font");
            fontFolder.mkdirs();
            textureFolder.mkdirs();

            writeFile(new File(workFolder, "pack.mcmeta"), getMcMeta(packFormat, description));
            writeFile(new File(fontFolder, "offsets.json"), getOffsetsFont());

            for (TtfFont font : fonts) {
                generateFont(font, fontFolder, textureFolder);
            }

            File zip = new File(output, "zTextGenerator-TTF.zip");
            zip(workFolder, zip);

            return new GeneratedPack(zip, sha1(zip));

        } catch (Exception exception) {
            logger.warning("Unable to generate the TTF resource pack : " + exception.getMessage());
            exception.printStackTrace();
            return null;
        }
    }

    private static void generateFont(TtfFont font, File fontFolder, File textureFolder) throws IOException {

        List<TtfGlyph> glyphs = font.getVisibleGlyphs();
        if (glyphs.isEmpty()) {
            return;
        }

        int cellHeight = font.getHeight();
        int cellWidth = font.getMaxAdvance() + 1;
        int columns = Math.max(8, (int) Math.ceil(Math.sqrt(glyphs.size())));
        int rows = (int) Math.ceil(glyphs.size() / (double) columns);

        BufferedImage image = new BufferedImage(powerOfTwo(columns * cellWidth), powerOfTwo(rows * cellHeight), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

        Font renderFont = font.getRenderFont();
        FontRenderContext fontRenderContext = graphics.getFontRenderContext();
        Color padding = new Color(255, 255, 255, 1);

        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        int index = 0;
        int filler = FILLER_START;

        graphics.setColor(Color.WHITE);
        for (TtfGlyph glyph : glyphs) {

            int column = index % columns;
            int row = index / columns;
            int x = column * cellWidth;
            int y = row * cellHeight;

            GlyphVector vector = renderFont.createGlyphVector(fontRenderContext, String.valueOf(glyph.character()));
            graphics.drawGlyphVector(vector, x, y + font.getAscent());

            // The game removes the transparent pixels at the left and at the right of a glyph to
            // know its size : a few (invisible) pixels are added to keep the letter spacing of the
            // ttf file.
            graphics.setColor(padding);
            if (glyph.bearing() > 0) {
                graphics.fillRect(x, y, glyph.bearing(), cellHeight);
            }
            if (glyph.rightBearing() > 0) {
                graphics.fillRect(x + glyph.bearing() + glyph.inkWidth(), y, glyph.rightBearing(), cellHeight);
            }
            graphics.setColor(Color.WHITE);

            line.append(glyph.character());
            if (line.length() >= columns) {
                lines.add(line.toString());
                line.setLength(0);
            }

            index++;
        }

        // The last line has to be completed, every cell of the image has to be declared.
        while (line.length() > 0 && line.length() < columns) {
            line.append((char) filler++);
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }

        graphics.dispose();

        File texture = new File(textureFolder, TtfFontLoader.sanitize(font.getName()) + ".png");
        ImageIO.write(image, "png", texture);

        StringBuilder json = new StringBuilder();
        json.append("{\n  \"providers\": [\n");

        StringBuilder advances = new StringBuilder();
        for (TtfGlyph glyph : font.getInvisibleGlyphs()) {
            if (advances.length() > 0) {
                advances.append(", ");
            }
            advances.append("\"").append(escapeJson(String.valueOf(glyph.character()))).append("\": ").append(glyph.advance());
        }
        if (advances.length() == 0) {
            advances.append("\" \": ").append(font.getSpaceAdvance());
        }

        json.append("    {\"type\": \"space\", \"advances\": {").append(advances).append("}},\n");
        json.append("    {\"type\": \"bitmap\", \"file\": \"").append(NAMESPACE).append(":font/")
                .append(TtfFontLoader.sanitize(font.getName())).append(".png\", \"ascent\": ")
                .append(font.getAscent()).append(", \"height\": ").append(cellHeight).append(", \"chars\": [");

        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                json.append(", ");
            }
            json.append("\"").append(escapeJson(lines.get(i))).append("\"");
        }

        json.append("]}\n  ]\n}\n");

        writeFile(new File(fontFolder, TtfFontLoader.sanitize(font.getName()) + ".json"), json.toString());
    }

    private static String getMcMeta(int packFormat, String description) {
        return "{\n  \"pack\": {\n    \"pack_format\": " + packFormat + ",\n    \"description\": \"" + escapeJson(description) + "\",\n" +
                "    \"supported_formats\": {\"min_inclusive\": 15, \"max_inclusive\": 99}\n  }\n}\n";
    }

    private static String getOffsetsFont() {

        StringBuilder advances = new StringBuilder();
        for (Map.Entry<Character, Integer> entry : TtfOffsets.advances().entrySet()) {
            if (advances.length() > 0) {
                advances.append(", ");
            }
            advances.append("\"").append(escapeJson(String.valueOf(entry.getKey()))).append("\": ").append(entry.getValue());
        }

        return "{\n  \"providers\": [\n    {\"type\": \"space\", \"advances\": {" + advances + "}}\n  ]\n}\n";
    }

    private static String escapeJson(String value) {

        StringBuilder builder = new StringBuilder();
        for (char character : value.toCharArray()) {
            switch (character) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (character < 0x20 || character > 0x7E) {
                        builder.append(String.format("\\u%04x", (int) character));
                    } else {
                        builder.append(character);
                    }
                }
            }
        }

        return builder.toString();
    }

    private static void writeFile(File file, String content) throws IOException {
        Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
    }

    private static int powerOfTwo(int value) {
        int result = 1;
        while (result < value) {
            result <<= 1;
        }
        return Math.max(result, 2);
    }

    private static void zip(File folder, File output) throws IOException {

        if (output.exists() && !output.delete()) {
            throw new IOException("Unable to delete " + output.getAbsolutePath());
        }

        List<File> files = new ArrayList<>();
        collect(folder, files);
        files.sort(Comparator.comparing(File::getAbsolutePath));

        try (OutputStream outputStream = new FileOutputStream(output); ZipOutputStream zip = new ZipOutputStream(outputStream)) {
            for (File file : files) {
                String path = folder.toPath().relativize(file.toPath()).toString().replace('\\', '/');
                zip.putNextEntry(new ZipEntry(path));
                zip.write(Files.readAllBytes(file.toPath()));
                zip.closeEntry();
            }
        }
    }

    private static void collect(File folder, List<File> files) {

        File[] children = folder.listFiles();
        if (children == null) {
            return;
        }

        for (File child : children) {
            if (child.isDirectory()) {
                collect(child, files);
            } else {
                files.add(child);
            }
        }
    }

    private static void delete(File file) {

        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    delete(child);
                }
            }
        }

        file.delete();
    }

    private static byte[] sha1(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        return digest.digest(Files.readAllBytes(file.toPath()));
    }
}
