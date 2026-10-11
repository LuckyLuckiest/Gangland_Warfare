package org.luckyraven.gangland.turf.map;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * {@code /glw map zoom in|out}: the player's own map cell size (8, 16, 32 or 64 blocks). {@code in} halves the size,
 * {@code out} doubles it; at the end the answer says so and nothing changes. A bare {@code zoom} answers the size in
 * effect. The size is in memory only (MapPrefsService), so logging out resets it to Map.Cell_Blocks.
 */
public final class MapZoomCommand extends SubArgument {

	/** The cell sizes, smallest (closest) first. */
	static final List<Integer> SIZES = List.of(8, 16, 32, 64);
	/** The words, in the order they tab-complete. */
	static final List<String>  WORDS = List.of("in", "out");

	private static final String SET_FALLBACK       = "&7Map zoom: &f%blocks% &7blocks per cell.";
	private static final String LIMIT_IN_FALLBACK  = "&7Closest zoom already: &f%blocks% &7blocks per cell.";
	private static final String LIMIT_OUT_FALLBACK = "&7Widest zoom already: &f%blocks% &7blocks per cell.";
	private static final String USAGE_FALLBACK     = "&7Map zoom: &f%blocks% &7blocks per cell. Use &f/glw map zoom in &7or &fout&7.";

	private final JavaPlugin      plugin;
	private final Tree<Argument>  tree;
	private final MapPrefsService prefs;
	private final TurfMessages    text;

	public MapZoomCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent, MapPrefsService prefs,
	                      TurfMessages text) {
		super(plugin, "zoom", tree, parent);
		this.plugin = plugin;
		this.tree   = tree;
		this.prefs  = prefs;
		this.text   = text;
		this.addSubArgument(wordArgument());
	}

	/** The next closer size: 64 to 32, 16 to 8; 8 stays 8. A size that is not one of the four is read as 16. */
	static int zoomIn(int blocks) {
		return SIZES.get(Math.max(0, indexOf(blocks) - 1));
	}

	/** The next wider size: 8 to 16, 32 to 64; 64 stays 64. A size that is not one of the four is read as 16. */
	static int zoomOut(int blocks) {
		return SIZES.get(Math.min(SIZES.size() - 1, indexOf(blocks) + 1));
	}

	private static int indexOf(int blocks) {
		int index = SIZES.indexOf(blocks);
		return index < 0 ? SIZES.indexOf(16) : index;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			if (sender instanceof Player player) {
				usage(player, prefs.cellBlocks(player.getUniqueId()));
			}
		};
	}

	private OptionalArgument wordArgument() {
		return new OptionalArgument(plugin, tree, (argument, sender, args) -> {
			if (sender instanceof Player player) {
				// args = [map, zoom, <word>]
				apply(player, args[2]);
			}
		}, sender -> WORDS);
	}

	/** Steps the player's size by the word and answers; a limit or an unknown word stores nothing. */
	void apply(Player player, String word) {
		UUID   uuid    = player.getUniqueId();
		int    current = prefs.cellBlocks(uuid);
		String lower   = word.toLowerCase(Locale.ROOT);
		if (!lower.equals("in") && !lower.equals("out")) {
			usage(player, current);
			return;
		}

		boolean in   = lower.equals("in");
		int     next = in ? zoomIn(current) : zoomOut(current);
		if (next == current) {
			String key      = in ? "Map.Zoom_Limit_In" : "Map.Zoom_Limit_Out";
			String fallback = in ? LIMIT_IN_FALLBACK : LIMIT_OUT_FALLBACK;
			player.sendMessage(text.text(key, fallback, "blocks", current));
			return;
		}
		prefs.setCellBlocks(uuid, next);
		player.sendMessage(text.text("Map.Zoom_Set", SET_FALLBACK, "blocks", next));
	}

	private void usage(Player player, int blocks) {
		player.sendMessage(text.text("Map.Zoom_Usage", USAGE_FALLBACK, "blocks", blocks));
	}
}
