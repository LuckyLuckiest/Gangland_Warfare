package org.luckyraven.gangland.turf.map;

import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.turf.capture.TurfRelation;
import org.luckyraven.gangland.turf.data.CuboidRegion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Bukkit-free rasteriser of the {@code /glw map} grid (PLAN 5.1 and 5.4). Cell (row, col) of a window centred on the
 * viewer's cell covers one {@code cellBlocks} square of blocks. A cell shows the turf covering at least half of it
 * (largest share wins, ties to the lower id); a turf that wins no cell is drawn in the cell holding its centre if that
 * cell is empty; the viewer's cell is a plus. Glyphs are allocated per gang (viewer first, allies next, then ascending id)
 * from its name letters, never {@code I}; the rules and their tests live here, the chat components in TurfMapRenderer.
 */
public final class TerritoryRaster {

	static final char   EMPTY     = '-';
	static final char   UNCLAIMED = '#';
	static final char   VIEWER    = '+';
	private static final String ALPHABET = "ABCDEFGHJKLMNOPQRSTUVWXYZ";
	private static final String DIGITS   = "23456789";
	private static final String CODE     = "[&§][0-9a-fk-orA-FK-OR]";
	private static final String HEX      = "&#[0-9A-Fa-f]{6}";

	public enum Style { PLAIN, ITALIC, UNDERLINE, ITALIC_UNDERLINE }

	/** One turf as the raster sees it. ownerGangId null = unclaimed. */
	public record Input(int id, String name, CuboidRegion region, TurfRelation relation, @Nullable Integer ownerGangId,
	                    boolean underAttack, boolean shielded) {
	}

	/**
	 * A gang that may own turf in view: display name (colour codes allowed, stripped here), ASCII name and the viewer's
	 * relation. The raster reads the relation from each {@link Input}; the field is informational.
	 */
	public record GangName(int id, String displayName, String name, @Nullable TurfRelation relation) {
	}

	/** glyph: '-', '#', '+', 'A'-'Z' minus 'I', '2'-'9'. turfId -1 and relation null for '-' and '+'. */
	public record Cell(char glyph, @Nullable TurfRelation relation, Style style, int turfId) {
	}

	public record LegendEntry(char glyph, int gangId, String fullName, TurfRelation relation, boolean shielded,
	                          boolean underAttack) {
	}

	/** cells[row][col]; legend in allocation order (only gangs owning a visible cell); viewerTurfId -1 if none. */
	public record Raster(Cell[][] cells, List<LegendEntry> legend, int visibleTurfs, int viewerTurfId) {

		/** Stable hash of everything the chat block shows (cells + legend), for MapAutoTask. */
		public int contentHash() {
			return Arrays.deepHashCode(cells) * 31 + legend.hashCode();
		}
	}

	private TerritoryRaster() {
	}

	public static Raster render(int viewerX, int viewerZ, int width, int height, int cellBlocks,
	                            List<Input> turfs, Map<Integer, GangName> gangs, @Nullable Integer viewerGangId) {
		int originX   = Math.floorDiv(viewerX, cellBlocks) - width / 2;
		int originZ   = Math.floorDiv(viewerZ, cellBlocks) - height / 2;
		int viewerRow = height / 2;
		int viewerCol = width / 2;

		List<Input> near = turfs.stream()
				.filter(turf -> intersects(turf.region(), originX * cellBlocks, originZ * cellBlocks,
				                           (originX + width) * cellBlocks - 1, (originZ + height) * cellBlocks - 1))
				.sorted(Comparator.comparingInt(Input::id))
				.toList();

		int[][] winner = new int[height][width];
		for (int row = 0; row < height; row++) {
			for (int col = 0; col < width; col++) {
				winner[row][col] = winnerOf(originX + col, originZ + row, cellBlocks, near);
			}
		}
		keepSmallTurfs(winner, near, originX, originZ, cellBlocks);
		int viewerTurfId = viewerTurfId(near, viewerX, viewerZ);
		winner[viewerRow][viewerCol] = -1;

		Set<Integer> visible = new HashSet<>();
		for (int[] row : winner) {
			for (int index : row) {
				if (index >= 0) {
					visible.add(index);
				}
			}
		}

		List<Integer>               owners   = new ArrayList<>();
		Map<Integer, TurfRelation>  relation = new HashMap<>();
		for (int index : visible.stream().sorted().toList()) {
			Input turf = near.get(index);
			if (turf.ownerGangId() != null && !owners.contains(turf.ownerGangId())) {
				owners.add(turf.ownerGangId());
				relation.put(turf.ownerGangId(), turf.relation());
			}
		}
		owners.sort(Comparator.comparingInt((Integer id) -> rank(id, viewerGangId, relation))
				.thenComparingInt(id -> id));

		Map<Integer, Character> glyphs = new HashMap<>();
		Set<Character>          used   = new HashSet<>();
		for (int id : owners) {
			GangName gang  = gangs.get(id);
			char     glyph = pick(gang == null ? "" : nz(gang.displayName()), gang == null ? "" : nz(gang.name()), used);
			used.add(glyph);
			glyphs.put(id, glyph);
		}

		Cell[][] cells = new Cell[height][width];
		for (int row = 0; row < height; row++) {
			for (int col = 0; col < width; col++) {
				int index = winner[row][col];
				if (row == viewerRow && col == viewerCol) {
					cells[row][col] = new Cell(VIEWER, null, Style.PLAIN, -1);
				} else if (index < 0) {
					cells[row][col] = new Cell(EMPTY, null, Style.PLAIN, -1);
				} else {
					Input turf  = near.get(index);
					char  glyph = turf.ownerGangId() == null ? UNCLAIMED : glyphs.get(turf.ownerGangId());
					cells[row][col] = new Cell(glyph, turf.relation(), styleOf(turf), turf.id());
				}
			}
		}

		List<LegendEntry> legend = new ArrayList<>();
		for (int id : owners) {
			boolean shielded = false;
			boolean attacked = false;
			for (int index : visible) {
				Input turf = near.get(index);
				if (Objects.equals(turf.ownerGangId(), id)) {
					shielded |= turf.shielded();
					attacked |= turf.underAttack();
				}
			}
			legend.add(new LegendEntry(glyphs.get(id), id, fullName(gangs.get(id), id), relation.get(id), shielded,
			                           attacked));
		}
		return new Raster(cells, List.copyOf(legend), visible.size(), viewerTurfId);
	}

	/** Own gang first (0), then allies (1), then the rest (2). */
	private static int rank(int id, @Nullable Integer viewerGangId, Map<Integer, TurfRelation> relation) {
		if (viewerGangId != null && viewerGangId == id) {
			return 0;
		}
		return relation.get(id) == TurfRelation.ALLY ? 1 : 2;
	}

	/** The cell with the largest overlap of the turf, when that overlap is at least half the cell; -1 otherwise. */
	private static int winnerOf(int cellX, int cellZ, int cellBlocks, List<Input> near) {
		int minX = cellX * cellBlocks;
		int minZ = cellZ * cellBlocks;
		int maxX = minX + cellBlocks - 1;
		int maxZ = minZ + cellBlocks - 1;
		int  best     = -1;
		long bestArea = 0;
		for (int i = 0; i < near.size(); i++) {
			CuboidRegion region = near.get(i).region();
			long area = (long) overlap(minX, maxX, region.getMinX(), region.getMaxX())
			            * overlap(minZ, maxZ, region.getMinZ(), region.getMaxZ());
			if (area > bestArea) {
				best     = i;
				bestArea = area;
			}
		}
		return best >= 0 && 2 * bestArea >= (long) cellBlocks * cellBlocks ? best : -1;
	}

	/** A turf that won no cell takes the cell holding its centre when that cell is still empty. */
	private static void keepSmallTurfs(int[][] winner, List<Input> near, int originX, int originZ, int cellBlocks) {
		int height = winner.length;
		int width  = winner[0].length;
		Set<Integer> won = new HashSet<>();
		for (int[] row : winner) {
			for (int index : row) {
				won.add(index);
			}
		}
		for (int i = 0; i < near.size(); i++) {
			if (won.contains(i)) {
				continue;
			}
			CuboidRegion region = near.get(i).region();
			int centreX = Math.floorDiv(region.getMinX() + region.getMaxX(), 2);
			int centreZ = Math.floorDiv(region.getMinZ() + region.getMaxZ(), 2);
			int col     = Math.floorDiv(centreX, cellBlocks) - originX;
			int row     = Math.floorDiv(centreZ, cellBlocks) - originZ;
			if (row >= 0 && row < height && col >= 0 && col < width && winner[row][col] < 0) {
				winner[row][col] = i;
				won.add(i);
			}
		}
	}

	private static int viewerTurfId(List<Input> near, int x, int z) {
		for (Input turf : near) {
			CuboidRegion region = turf.region();
			if (x >= region.getMinX() && x <= region.getMaxX() && z >= region.getMinZ() && z <= region.getMaxZ()) {
				return turf.id();
			}
		}
		return -1;
	}

	private static Style styleOf(Input turf) {
		if (turf.underAttack() && turf.shielded()) {
			return Style.ITALIC_UNDERLINE;
		}
		if (turf.underAttack()) {
			return Style.ITALIC;
		}
		return turf.shielded() ? Style.UNDERLINE : Style.PLAIN;
	}

	private static boolean intersects(CuboidRegion region, int minX, int minZ, int maxX, int maxZ) {
		return region.getMaxX() >= minX && region.getMinX() <= maxX && region.getMaxZ() >= minZ
		       && region.getMinZ() <= maxZ;
	}

	private static int overlap(int a1, int a2, int b1, int b2) {
		return Math.max(0, Math.min(a2, b2) - Math.max(a1, b1) + 1);
	}

	/** The first free candidate: letters of the display name, letters of the ASCII name, then A-Z, 2-9, 9. */
	private static char pick(String display, String name, Set<Character> used) {
		for (char c : letters(display).toCharArray()) {
			if (!used.contains(c)) {
				return c;
			}
		}
		for (char c : letters(name).toCharArray()) {
			if (!used.contains(c)) {
				return c;
			}
		}
		for (char c : ALPHABET.toCharArray()) {
			if (!used.contains(c)) {
				return c;
			}
		}
		for (char c : DIGITS.toCharArray()) {
			if (!used.contains(c)) {
				return c;
			}
		}
		return '9';
	}

	/** Upper-case A-Z letters of the text with colour codes removed; I is dropped. */
	static String letters(String raw) {
		String plain = plain(raw).toUpperCase(Locale.ROOT);
		StringBuilder out = new StringBuilder();
		for (char c : plain.toCharArray()) {
			if (c >= 'A' && c <= 'Z' && c != 'I') {
				out.append(c);
			}
		}
		return out.toString();
	}

	private static String fullName(@Nullable GangName gang, int id) {
		String display = gang == null ? "" : plain(nz(gang.displayName()));
		if (!display.isEmpty()) {
			return display;
		}
		String name = gang == null ? "" : plain(nz(gang.name()));
		return name.isEmpty() ? "Gang #" + id : name;
	}

	/** The text without hex and {@code &}/section colour codes, trimmed. */
	static String plain(String raw) {
		return raw.replaceAll(HEX, "").replaceAll(CODE, "").trim();
	}

	private static String nz(@Nullable String value) {
		return value == null ? "" : value;
	}
}
