package me.luckyraven.turf.task;

import com.cryptomorin.xseries.particles.XParticle;
import lombok.CustomLog;
import me.luckyraven.turf.data.CuboidRegion;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Spawns a particle outline along every edge of a {@link CuboidRegion} so an admin can see the region they are about to
 * create or the turf they are inside. Renders the full wire-frame (4 vertical pillars plus the top/bottom rectangles)
 * so the shape reads as a box, not just corner posts. Runs for a fixed number of seconds and self-cancels; every viewer
 * scheduling a visualisation gets their own task — no shared state.
 *
 * <p>Particles are spawned every {@code ~0.5} blocks along each edge, on a 1-second refresh, scoped to the
 * Y-band {@code viewer.getY() ± 10} so they are visible regardless of terrain.
 */
@CustomLog
public final class TurfVisualization {

	private static final double Y_BAND        = 10.0;
	private static final double STEP          = 0.5;
	private static final long   REFRESH_TICKS = 20L;

	private TurfVisualization() {
	}

	public static void show(JavaPlugin plugin, Player viewer, String worldName, CuboidRegion region,
	                        int durationSeconds, String particleName) {
		World world = viewer.getServer().getWorld(worldName);
		if (world == null) {
			log.warn("Cannot visualise turf — world {} not loaded", worldName);
			return;
		}
		Particle particle = resolveParticle(particleName);

		new BukkitRunnable() {
			int ticks;

			@Override
			public void run() {
				if (!viewer.isOnline() || ticks >= durationSeconds * 20) {
					cancel();
					return;
				}
				renderEdges(viewer, world, region, particle);
				ticks += REFRESH_TICKS;
			}
		}.runTaskTimer(plugin, 0L, REFRESH_TICKS);
	}

	private static void renderEdges(Player viewer, World world, CuboidRegion region, Particle particle) {
		double minY = viewer.getLocation().getY() - Y_BAND;
		double maxY = viewer.getLocation().getY() + Y_BAND;

		double minX = region.getMinX() + 0.5;
		double maxX = region.getMaxX() + 0.5;
		double minZ = region.getMinZ() + 0.5;
		double maxZ = region.getMaxZ() + 0.5;

		// 4 vertical pillars — corner posts
		drawVerticalEdge(viewer, world, minX, minZ, minY, maxY, particle);
		drawVerticalEdge(viewer, world, maxX, minZ, minY, maxY, particle);
		drawVerticalEdge(viewer, world, minX, maxZ, minY, maxY, particle);
		drawVerticalEdge(viewer, world, maxX, maxZ, minY, maxY, particle);

		// Top + bottom rectangles — 8 horizontal edges that turn the outline from pillars into a full box.
		drawRectangle(viewer, world, minX, maxX, minZ, maxZ, minY, particle);
		drawRectangle(viewer, world, minX, maxX, minZ, maxZ, maxY, particle);
	}

	private static void drawVerticalEdge(Player viewer, World world, double x, double z, double yFrom, double yTo,
	                                     Particle particle) {
		for (double y = yFrom; y <= yTo; y += STEP) {
			viewer.spawnParticle(particle, new Location(world, x, y, z), 1, 0, 0, 0, 0);
		}
	}

	private static void drawRectangle(Player viewer, World world, double minX, double maxX, double minZ, double maxZ,
	                                  double y, Particle particle) {
		for (double x = minX; x <= maxX; x += STEP) {
			viewer.spawnParticle(particle, new Location(world, x, y, minZ), 1, 0, 0, 0, 0);
			viewer.spawnParticle(particle, new Location(world, x, y, maxZ), 1, 0, 0, 0, 0);
		}
		for (double z = minZ; z <= maxZ; z += STEP) {
			viewer.spawnParticle(particle, new Location(world, minX, y, z), 1, 0, 0, 0, 0);
			viewer.spawnParticle(particle, new Location(world, maxX, y, z), 1, 0, 0, 0, 0);
		}
	}

	private static Particle resolveParticle(String name) {
		try {
			Particle resolved = XParticle.valueOf(name.toUpperCase()).get();
			if (resolved != null) {
				return resolved;
			}
		} catch (IllegalArgumentException exception) {
			// fall through to default
		}
		log.warn("Unknown turf visualisation particle '{}', falling back to FLAME", name);
		return XParticle.FLAME.get();
	}
}
