package org.luckyraven.gangland.turf.map;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The first legend line: one entry per gang in view ({@code V Vipers (you)}) while the line fits
 * {@link #MAX_PIXELS}, then {@code +N more}. Pure; the renderer and the service decide the colours and the words.
 */
public final class LegendLine {

	public static final int MAX_PIXELS = 300;
	static final        int NAME_CHARS = 8;

	private static final String SEPARATOR = "  ";

	/** shown = entries that fit in order; hidden = the rest. */
	public record Packed(List<TerritoryRaster.LegendEntry> shown, List<TerritoryRaster.LegendEntry> hidden) {
	}

	private LegendLine() {
	}

	/** Glyph, the full name cut to {@link #NAME_CHARS} characters, then the relation word (none for a gangless viewer). */
	public static String label(TerritoryRaster.LegendEntry entry, String you, String ally, String rival) {
		String name = entry.fullName();
		if (name.length() > NAME_CHARS) {
			name = name.substring(0, NAME_CHARS);
		}
		String word = switch (entry.relation()) {
			case OWN -> you;
			case ALLY -> ally;
			case RIVAL -> rival;
			default -> "";
		};
		return entry.glyph() + " " + name + (word.isEmpty() ? "" : " " + word);
	}

	/** Packs entries in order; an entry is kept only if the line with its "+N more" still measures at most 300 px. */
	public static Packed pack(List<TerritoryRaster.LegendEntry> entries,
	                          Function<TerritoryRaster.LegendEntry, String> label,
	                          Function<Integer, String> more) {
		List<TerritoryRaster.LegendEntry> shown = new ArrayList<>();
		String                            line  = "";
		for (int i = 0; i < entries.size(); i++) {
			TerritoryRaster.LegendEntry entry     = entries.get(i);
			String                      candidate = shown.isEmpty() ? label.apply(entry)
			                                                        : line + SEPARATOR + label.apply(entry);
			int                         rest      = entries.size() - i - 1;
			String withMore = rest == 0 ? candidate : candidate + SEPARATOR + more.apply(rest);
			if (PixelWidth.of(withMore) > MAX_PIXELS) {
				break;
			}
			shown.add(entry);
			line = candidate;
		}
		List<TerritoryRaster.LegendEntry> hidden = entries.subList(shown.size(), entries.size());
		return new Packed(List.copyOf(shown), List.copyOf(hidden));
	}
}
