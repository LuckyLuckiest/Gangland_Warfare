package org.luckyraven.gangland.turf.map;

/**
 * Pixel width of a chat line in the vanilla font, including the 1 px gap after each glyph. Colour and format codes
 * ({@code &x} or the section sign followed by a code character) measure zero. Non-ASCII characters count as 6 px, an
 * approximation: the vanilla font has many wider glyphs there, so a line of CJK or Cyrillic may measure short.
 */
public final class PixelWidth {

	private static final String CODE = "[&§][0-9a-fk-orA-FK-OR]";

	private PixelWidth() {
	}

	public static int of(String text) {
		String plain = text.replaceAll(CODE, "");
		int    total = 0;
		for (char c : plain.toCharArray()) {
			total += width(c);
		}
		return total;
	}

	private static int width(char c) {
		return switch (c) {
			case '!', ',', '.', ':', ';', '|', 'i', '\'' -> 2;
			case '`', 'l' -> 3;
			case 'I', '[', ']', 't', ' ' -> 4;
			case '"', '(', ')', '*', '<', '>', 'f', 'k', '{', '}' -> 5;
			case '@', '~' -> 7;
			default -> 6;
		};
	}
}
