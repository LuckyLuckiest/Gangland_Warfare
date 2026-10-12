package org.luckyraven.gangland.turf.map;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.luckyraven.gangland.turf.capture.TurfRelation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * Turns a {@link TerritoryRaster.Raster} and the legend into Bungee components, the same API {@code TurfListCommand}
 * uses (compiles at the 1.16.5 floor). Neighbours with the same glyph, relation, style and turf merge into one component;
 * a turf run hovers its facts and clicks to its info card; dash runs carry neither. Bold is never set.
 */
public final class TurfMapRenderer {

	/** Header, then one line per grid row, then the legend lines. */
	public List<BaseComponent[]> lines(String header, TerritoryRaster.Raster raster, IntFunction<String> hoverForTurf,
	                                   String plusHover, List<BaseComponent[]> legendLines) {
		List<BaseComponent[]> lines = new ArrayList<>();
		lines.add(TextComponent.fromLegacyText(header));
		for (TerritoryRaster.Cell[] row : raster.cells()) {
			lines.add(gridLine(row, hoverForTurf, plusHover));
		}
		lines.addAll(legendLines);
		return lines;
	}

	/** Glyph, relation colour, a space, then the entries separated by two spaces and "+N more" with its hover. */
	public BaseComponent[] legendLine(LegendLine.Packed packed, Function<TerritoryRaster.LegendEntry, String> label,
	                                  String more, String moreHover) {
		List<BaseComponent> parts = new ArrayList<>();
		for (TerritoryRaster.LegendEntry entry : packed.shown()) {
			if (!parts.isEmpty()) {
				parts.add(new TextComponent("  "));
			}
			TextComponent part = new TextComponent(org.bukkit.ChatColor.stripColor(label.apply(entry)));
			part.setColor(relationColour(entry.relation()));
			part.setHoverEvent(hover(entry.fullName()));
			parts.add(part);
		}
		if (!packed.hidden().isEmpty()) {
			if (!parts.isEmpty()) {
				parts.add(new TextComponent("  "));
			}
			TextComponent part = new TextComponent(org.bukkit.ChatColor.stripColor(more));
			part.setColor(ChatColor.GRAY);
			part.setHoverEvent(hover(moreHover));
			part.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/glw map legend"));
			parts.add(part);
		}
		return parts.toArray(BaseComponent[]::new);
	}

	/** One legend row of {@code /glw map legend}: the glyph in its relation colour, then the words. */
	public BaseComponent[] legendRow(TerritoryRaster.LegendEntry entry, String legacy) {
		TextComponent glyph = new TextComponent(String.valueOf(entry.glyph()));
		glyph.setColor(relationColour(entry.relation()));
		List<BaseComponent> parts = new ArrayList<>();
		parts.add(glyph);
		parts.addAll(Arrays.asList(TextComponent.fromLegacyText(" " + legacy)));
		return parts.toArray(BaseComponent[]::new);
	}

	/** A text-form row: the legacy line, clickable to the turf info card and hovering its facts. */
	public BaseComponent[] clickable(String legacy, int turfId, String hover) {
		BaseComponent[] parts = TextComponent.fromLegacyText(legacy);
		for (BaseComponent part : parts) {
			part.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/glw turf info " + turfId));
			part.setHoverEvent(hover(hover));
		}
		return parts;
	}

	private static BaseComponent[] gridLine(TerritoryRaster.Cell[] row, IntFunction<String> hoverForTurf,
	                                        String plusHover) {
		List<BaseComponent> parts = new ArrayList<>();
		int                 start = 0;
		while (start < row.length) {
			int end = start + 1;
			while (end < row.length && row[end].equals(row[start])) {
				end++;
			}
			parts.add(run(row[start], end - start, hoverForTurf, plusHover));
			start = end;
		}
		return parts.toArray(BaseComponent[]::new);
	}

	private static TextComponent run(TerritoryRaster.Cell cell, int count, IntFunction<String> hoverForTurf,
	                                 String plusHover) {
		TextComponent part = new TextComponent(String.valueOf(cell.glyph()).repeat(count));
		part.setColor(colour(cell));
		boolean italic     = cell.style() == TerritoryRaster.Style.ITALIC
		                     || cell.style() == TerritoryRaster.Style.ITALIC_UNDERLINE;
		boolean underlined = cell.style() == TerritoryRaster.Style.UNDERLINE
		                     || cell.style() == TerritoryRaster.Style.ITALIC_UNDERLINE;
		part.setItalic(italic);
		part.setUnderlined(underlined);
		if (cell.glyph() == TerritoryRaster.VIEWER) {
			part.setHoverEvent(hover(plusHover));
		} else if (cell.turfId() >= 0) {
			part.setHoverEvent(hover(hoverForTurf.apply(cell.turfId())));
			part.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/glw turf info " + cell.turfId()));
		}
		return part;
	}

	private static ChatColor colour(TerritoryRaster.Cell cell) {
		if (cell.glyph() == TerritoryRaster.VIEWER) {
			return ChatColor.WHITE;
		}
		if (cell.relation() == null) {
			return ChatColor.DARK_GRAY;
		}
		return switch (cell.relation()) {
			case OWN -> ChatColor.GREEN;
			case ALLY -> ChatColor.AQUA;
			case RIVAL -> ChatColor.RED;
			case GANGLESS -> ChatColor.YELLOW;
			case UNCLAIMED -> ChatColor.GRAY;
		};
	}

	private static ChatColor relationColour(TurfRelation relation) {
		return switch (relation) {
			case OWN -> ChatColor.GREEN;
			case ALLY -> ChatColor.AQUA;
			case RIVAL -> ChatColor.RED;
			case GANGLESS -> ChatColor.YELLOW;
			case UNCLAIMED -> ChatColor.GRAY;
		};
	}

	/** Hover text: the legacy colour codes are kept as coloured components. */
	private static HoverEvent hover(String legacy) {
		return new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(TextComponent.fromLegacyText(legacy)));
	}
}
