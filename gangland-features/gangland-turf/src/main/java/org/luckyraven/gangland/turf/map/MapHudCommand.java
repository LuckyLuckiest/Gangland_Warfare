package org.luckyraven.gangland.turf.map;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.map.MapPrefsService.HudMode;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Locale;

/**
 * {@code /glw map hud on|off|default}: stores the player's turf-line mode (MapPrefsService) and answers with the line
 * of the mode in effect. A bare {@code hud} answers the current line; an unknown word leaves the mode alone.
 */
public final class MapHudCommand extends SubArgument {

	/** The mode words, in the order they tab-complete. */
	static final List<String> MODE_WORDS = List.of("on", "off", "default");

	private final JavaPlugin     plugin;
	private final Tree<Argument> tree;
	private final MapPrefsService prefs;
	private final TurfMessages    text;

	public MapHudCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent, MapPrefsService prefs, TurfMessages text) {
		super(plugin, "hud", tree, parent);
		this.plugin = plugin;
		this.tree   = tree;
		this.prefs  = prefs;
		this.text   = text;
		this.addSubArgument(modeArgument());
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			if (sender instanceof Player player) {
				reply(player, prefs.hudMode(player.getUniqueId()));
			}
		};
	}

	private OptionalArgument modeArgument() {
		return new OptionalArgument(plugin, tree, (argument, sender, args) -> {
			if (sender instanceof Player player) {
				// args = [map, hud, <mode>]
				apply(player, args[2]);
			}
		}, sender -> MODE_WORDS);
	}

	/** Stores the mode the word names and answers with it; an unknown word answers the mode already in effect. */
	void apply(Player player, String word) {
		HudMode mode = parse(word);
		if (mode == null) {
			reply(player, prefs.hudMode(player.getUniqueId()));
			return;
		}
		prefs.setHudMode(player.getUniqueId(), mode);
		reply(player, mode);
	}

	private static @Nullable HudMode parse(String word) {
		return switch (word.toLowerCase(Locale.ROOT)) {
			case "on" -> HudMode.ON;
			case "off" -> HudMode.OFF;
			case "default" -> HudMode.DEFAULT;
			default -> null;
		};
	}

	private void reply(Player player, HudMode mode) {
		String line = switch (mode) {
			case ON -> text.text("Hud.Mode_On", "&7Turf line: &finside turfs and the nearest turf&7.");
			case OFF -> text.text("Hud.Mode_Off", "&7Turf line: &coff&7.");
			case DEFAULT -> text.text("Hud.Mode_Default", "&7Turf line: &finside turfs only&7.");
		};
		player.sendMessage(line);
	}
}
