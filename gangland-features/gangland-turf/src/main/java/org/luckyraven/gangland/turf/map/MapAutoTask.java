package org.luckyraven.gangland.turf.map;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.luckyraven.keystone.bean.BeanLifecycle;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.ToIntFunction;

/**
 * 1-Hz task that re-sends the auto map of every viewer that turned it on. A raster is recomputed only when the viewer's
 * cell moved or the interval passed; the map is sent when its content hash differs from the last sent one and the
 * interval has passed. Players that leave auto are forgotten. Never {@code PlayerMoveEvent}.
 *
 * <p>// ponytail: no turf version counter (PLAN 5.3); a recompute per auto viewer per interval or cell move is cheap
 * (287 cells x turfs in view). Add the counter on TurfOwnerChangedEvent/TurfCaptureStartEvent if profiling says otherwise.
 */
public final class MapAutoTask implements BeanLifecycle {

	private static final long TICKS = 20L;

	private final JavaPlugin                             plugin;
	private final MapPrefsService                        prefs;
	private final Function<UUID, Player>                 players;
	private final Function<Player, TerritoryRaster.Raster> rasters;
	private final Consumer<Player>                       sender;
	private final LongSupplier                           clock;
	private final ToIntFunction<Player>                  cellBlocks;
	private final IntSupplier                            minIntervalSeconds;
	private final Map<UUID, Viewer>                      viewers = new HashMap<>();

	private BukkitTask task;

	/** What the task remembers about one auto viewer. */
	private static final class Viewer {

		private String                  key;
		private TerritoryRaster.Raster  raster;
		private long                    computedAt;
		private Integer                 sentHash;
		private long                    sentAt;
	}

	public MapAutoTask(JavaPlugin plugin, MapPrefsService prefs, Function<UUID, Player> players,
	                   Function<Player, TerritoryRaster.Raster> rasters, Consumer<Player> sender, LongSupplier clock,
	                   ToIntFunction<Player> cellBlocks, IntSupplier minIntervalSeconds) {
		this.plugin             = plugin;
		this.prefs              = prefs;
		this.players            = players;
		this.rasters            = rasters;
		this.sender             = sender;
		this.clock              = clock;
		this.cellBlocks         = cellBlocks;
		this.minIntervalSeconds = minIntervalSeconds;
	}

	public void start() {
		if (task != null) {
			return;
		}
		task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, TICKS, TICKS);
	}

	public void stop() {
		if (task != null) {
			task.cancel();
			task = null;
		}
		viewers.clear();
	}

	@Override
	public void onShutdown() {
		stop();
	}

	void tick() {
		long now = clock.getAsLong();
		long intervalMillis = Math.max(1, minIntervalSeconds.getAsInt()) * 1000L;
		Set<UUID> auto = prefs.autoViewers();
		viewers.keySet().removeIf(id -> !auto.contains(id));

		for (UUID id : auto) {
			Player player = players.apply(id);
			if (player == null) {
				continue;
			}
			// A new viewer has no key and no sent hash, so this tick computes and sends its first map. MapAutoCommand does
			// not send: a move between the command and this tick would otherwise be seeded as already sent (T-197).
			Viewer state = viewers.computeIfAbsent(id, unused -> new Viewer());
			String key = cellKey(player);
			boolean moved = !key.equals(state.key);
			if (moved || now - state.computedAt >= intervalMillis) {
				state.raster     = rasters.apply(player);
				state.key        = key;
				state.computedAt = now;
			}

			int hash = state.raster.contentHash();
			if (state.sentHash == null || (hash != state.sentHash && now - state.sentAt >= intervalMillis)) {
				sender.accept(player);
				state.sentHash = hash;
				state.sentAt   = now;
			}
		}
	}

	/** World name and the viewer's cell; the turf under the viewer is left out (it changes no cell). */
	private String cellKey(Player player) {
		Location at    = player.getLocation();
		World    world = at.getWorld();
		int      cell  = Math.max(1, cellBlocks.applyAsInt(player));
		String   name  = world == null ? "" : world.getName();
		return name + ":" + Math.floorDiv(at.getBlockX(), cell) + ":" + Math.floorDiv(at.getBlockZ(), cell);
	}
}
