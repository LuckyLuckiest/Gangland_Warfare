package org.luckyraven.gangland.turf.map;

import org.bukkit.command.CommandSender;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.task.TurfVisualization;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.config.TurfSettings;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

/**
 * {@code /glw map border}: a short particle outline of the turf the player stands in, or the nearest one, clipped
 * to the part near the player. Stub registered by the gt2 kickoff; lane W3-V writes the body.
 */
public final class MapBorderCommand extends SubArgument {

	private final JavaPlugin   plugin;
	private final TurfManager  turfs;
	private final TurfSettings settings;
	private final TurfMessages text;

	/** The outline hook; production draws through {@link TurfVisualization}, tests record the call. */
	@FunctionalInterface
	interface Renderer {

		void show(Player player, Turf turf, int seconds, String particle);
	}

	private final Renderer renderer;

	public MapBorderCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent, TurfManager turfs,
	                        TurfSettings settings, TurfMessages text) {
		this(plugin, tree, parent, turfs, settings, text,
		     (player, turf, seconds, particle) -> TurfVisualization.show(plugin, player, turf.getRegion().getWorld(),
		                                                                 turf.getRegion(), seconds, particle));
	}

	MapBorderCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent, TurfManager turfs,
	                 TurfSettings settings, TurfMessages text, Renderer renderer) {
		super(plugin, "border", tree, parent);
		this.plugin   = plugin;
		this.turfs    = turfs;
		this.settings = settings;
		this.text     = text;
		this.renderer = renderer;
	}

	static final int BORDER_SEARCH  = 64;
	static final int BORDER_SECONDS = 10;

	void run(Player player) {
		Location at   = player.getLocation();
		Turf     turf = turfs.findAt(at);
		if (turf == null) {
			turf = nearest(player.getWorld().getName(), at);
		}
		if (turf == null) {
			player.sendMessage(text.text("Map.Border_None", "&7No turf within &f%distance% &7blocks.", "distance",
			                             BORDER_SEARCH));
			return;
		}
		renderer.show(player, turf, BORDER_SECONDS, settings.getVisualizationParticle());
		player.sendMessage(text.text("Map.Border_Shown",
		                             "&7Showing the border of &f%turf% &7for &f%seconds%s &7(the part near you).",
		                             "turf", turf.getDisplayName(), "seconds", BORDER_SECONDS));
	}

	private @Nullable Turf nearest(String world, Location at) {
		Turf   best     = null;
		double bestDist = BORDER_SEARCH;
		for (Turf candidate : turfs.getTurfsInWorld(world)) {
			CuboidRegion r  = candidate.getRegion();
			double       dx = Math.max(Math.max(r.getMinX() - at.getX(), at.getX() - r.getMaxX()), 0);
			double       dz = Math.max(Math.max(r.getMinZ() - at.getZ(), at.getZ() - r.getMaxZ()), 0);
			double       d  = Math.hypot(dx, dz);
			if (d <= bestDist) {
				best     = candidate;
				bestDist = d;
			}
		}
		return best;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			if (sender instanceof Player player) {
				run(player);
			}
		};
	}
}
