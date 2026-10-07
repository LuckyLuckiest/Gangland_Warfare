package org.luckyraven.gangland.copsncrooks.setup;

import com.cryptomorin.xseries.particles.XParticle;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.bean.BeanLifecycle;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Draws the selection of every admin holding the setup wand: the 12 edges of a cuboid (one particle per block, at most
 * {@value #MAX_PARTICLES} per admin per draw) or a one-block marker for a point mode. One sync repeating task, started
 * by {@link #ensureRunning()} and cancelled by the first tick on which nobody holds the wand.
 *
 * @since 0.16.0
 */
public final class SetupOutline implements BeanLifecycle {

	public static final int MAX_PARTICLES = 256;

	private static final @Nullable Method FORCED_PARTICLE = forcedParticle();

	/** Sends one particle to one player. */
	public interface Sink {

		void spawn(Player player, Location at);
	}

	private final JavaPlugin                           plugin;
	private final SetupSelections                      selections;
	private final SetupMessages                        config;
	private final Supplier<Collection<? extends Player>> online;
	private final Sink                                 sink;

	private @Nullable BukkitTask task;

	public SetupOutline(JavaPlugin plugin, SetupSelections selections, SetupMessages config,
	                    Supplier<Collection<? extends Player>> online, Sink sink) {
		this.plugin     = plugin;
		this.selections = selections;
		this.config     = config;
		this.online     = online;
		this.sink       = sink;
	}

	/** The production sink: the configured particle, sent to the one player (forced where the server supports it). */
	public static Sink particles(SetupMessages config) {
		return (player, at) -> {
			Particle particle = resolveParticle(config.particle());
			Object   data     = particle.getDataType() == Particle.DustOptions.class
			                    ? new Particle.DustOptions(Color.AQUA, 1.0F) : null;
			spawnForced(player, particle, at, data);
		};
	}

	public boolean isRunning() {
		return task != null;
	}

	/** Starts the repeating task unless it already runs. */
	public void ensureRunning() {
		if (task != null) return;

		task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 0L, config.intervalTicks());
	}

	public void stop() {
		if (task == null) return;

		task.cancel();
		task = null;
	}

	/** One draw; stops the task when no online admin holds the wand. */
	public void tick() {
		boolean anyHolder = false;

		for (Player player : online.get()) {
			if (!SetupSelections.holdsWand(player)) continue;

			anyHolder = true;
			SetupSelection selection = selections.peek(player.getUniqueId());
			if (selection == null) continue;

			for (Location at : points(selection)) sink.spawn(player, at);
		}

		if (!anyHolder) stop();
	}

	/**
	 * The block centres to draw for {@code selection}: nothing without pos1, one marker for a point mode (or while pos2
	 * is missing), else the cuboid's edges, thinned by a stride to at most {@value #MAX_PARTICLES}.
	 */
	public static List<Location> points(SetupSelection selection) {
		Location a     = selection.getPos1();
		Location b     = selection.getPos2();
		World    world = a == null ? null : a.getWorld();

		if (a == null || world == null) return List.of();
		if (selection.getMode().isPoint() || b == null || !Objects.equals(b.getWorld(), world))
			return List.of(centre(world, a.getBlockX(), a.getBlockY(), a.getBlockZ()));

		int minX = Math.min(a.getBlockX(), b.getBlockX()), maxX = Math.max(a.getBlockX(), b.getBlockX());
		int minY = Math.min(a.getBlockY(), b.getBlockY()), maxY = Math.max(a.getBlockY(), b.getBlockY());
		int minZ = Math.min(a.getBlockZ(), b.getBlockZ()), maxZ = Math.max(a.getBlockZ(), b.getBlockZ());

		// the stride comes from the edge lengths, so the work is bounded by the cap, never by the selection's size;
		// each edge rounds up once, so MAX_PARTICLES - 12 keeps the 12 edges together under the hard cap
		long nx     = (long) maxX - minX + 1, ny = (long) maxY - minY + 1, nz = (long) maxZ - minZ + 1;
		long total  = 4 * (nx + ny + nz);
		long stride = Math.max(1, (total + MAX_PARTICLES - 13) / (MAX_PARTICLES - 12));

		// ponytail: stride thinning, not an even re-walk; the outline stays recognisable, the cap is hard
		Set<Location> kept = new LinkedHashSet<>();
		for (int y : new int[]{minY, maxY}) {
			for (int z : new int[]{minZ, maxZ}) {
				for (long x = minX; x <= maxX; x += stride) kept.add(centre(world, (int) x, y, z));
			}
		}
		for (int x : new int[]{minX, maxX}) {
			for (int z : new int[]{minZ, maxZ}) {
				for (long y = minY; y <= maxY; y += stride) kept.add(centre(world, x, (int) y, z));
			}
		}
		for (int x : new int[]{minX, maxX}) {
			for (int y : new int[]{minY, maxY}) {
				for (long z = minZ; z <= maxZ; z += stride) kept.add(centre(world, x, y, (int) z));
			}
		}
		return new ArrayList<>(kept);
	}

	private static Location centre(World world, int x, int y, int z) {
		return new Location(world, x + 0.5, y + 0.5, z + 0.5);
	}

	@Override
	public void onInitialize(boolean firstLoad) {
		// the task starts on the first wand click
	}

	@Override
	public void onClear() {
		stop();
	}

	// Copy of WantedHud.spawnForced / forcedParticle / resolveParticle: per-player particles, forced where supported.
	private static void spawnForced(Player player, Particle particle, Location at, @Nullable Object data) {
		if (FORCED_PARTICLE != null) {
			try {
				FORCED_PARTICLE.invoke(player, particle, at, 1, 0D, 0D, 0D, 0D, data, true);
				return;
			} catch (ReflectiveOperationException exception) {
				// fall back to the unforced call
			}
		}

		player.spawnParticle(particle, at, 1, 0, 0, 0, 0, data);
	}

	private static @Nullable Method forcedParticle() {
		try {
			return Player.class.getMethod("spawnParticle", Particle.class, Location.class, int.class, double.class,
			                              double.class, double.class, double.class, Object.class, boolean.class);
		} catch (NoSuchMethodException exception) {
			return null;
		}
	}

	private static Particle resolveParticle(String name) {
		try {
			Particle resolved = XParticle.valueOf(name.toUpperCase()).get();
			if (resolved != null) return resolved;
		} catch (IllegalArgumentException exception) {
			// fall through to the default
		}

		return XParticle.DUST.get();
	}
}
