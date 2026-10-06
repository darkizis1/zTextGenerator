package fr.maxlego08.text.font;

/**
 * Metrics of a single character inside a TTF font.
 *
 * @param character the character
 * @param advance   the total width of the character, in pixels (pen movement)
 * @param bearing   the left side bearing, in pixels (empty space before the ink)
 * @param inkWidth  the width of the ink, in pixels
 * @param visible   true when the character has some ink to draw
 */
public record TtfGlyph(char character, int advance, int bearing, int inkWidth, boolean visible) {

    /**
     * Gets the amount of empty pixels after the ink.
     *
     * @return the right side bearing, in pixels
     */
    public int rightBearing() {
        return Math.max(0, this.advance - this.bearing - this.inkWidth);
    }
}
