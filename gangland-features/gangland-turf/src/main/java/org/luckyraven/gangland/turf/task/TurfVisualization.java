package org.luckyraven.gangland.turf.task;

import com.cryptomorin.xseries.particles.XParticle;
import lombok.CustomLog;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.luckyraven.gangland.turf.data.CuboidRegion;

import java.util.ArrayList;
import java.util.List;

/**
 * Spawns a particle outline along every edge of a {@link CuboidRegion} so an admin can see the region they are about to
 * create or the turf they are inside. Renders the full wire-frame (4 vertical pillars plus the top/bottom rectangles)
 * so the shape reads as a box, not just corner posts. Runs for a fixed number of seconds and self-cancels; every viewer
 * scheduling a visualisation gets their own task — no shared state.
 *
 * <p>Only the part of the outline within {@link #CLIP_RADIUS} blocks of the viewer is drawn, at most {@link #BUDGET}
 * particles per 1-second refresh (step 0.5 growing as needed), scoped to the Y-band {@code viewer.getY() ± 10}.
 */
@CustomLog
public final class TurfVisualization {

	private static final double Y_BAND        = 10.0;
	private static final double STEP          = 0.5;
	private static final double CLIP_RADIUS   = 40.0;
	private static final int    BUDGET        = 600;
	private static final long   REFRESH_TICKS = 20L;

	private TurfVisualization() {
	}

	/**
	 * The outline points within {@code radius} blocks (horizontally) of the viewer: the top and bottom rectangles at
	 * {@code minY}/{@code maxY} plus the four pillars between them. The step starts at {@link #STEP} and grows so the
	 * total never exceeds {@code budget} (TF-43: a 512 x 512 turf must not cost thousands of particles a second).
	 */
	static List<double[]> edgePoints(double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
	                                 double viewerX, double viewerY, double viewerZ, double radius, int budget) {
		// segment = {fixedX/fixedZ marker, from, to}; kind 0 = along X at z, 1 = along Z at x, 2 = pillar at (x, z)
		List<double[]> segments = new ArrayList<>();
		double[] zs = {minZ, maxZ};
		double[] xs = {minX, maxX};
		for (double z : zs) {
			double[] range = clip(minX, maxX, viewerX, Math.abs(z - viewerZ), radius);
			if (range != null) {
				segments.add(new double[]{0, z, range[0], range[1]});
			}
		}
		for (double x : xs) {
			double[] range = clip(minZ, maxZ, viewerZ, Math.abs(x - viewerX), radius);
			if (range != null) {
				segments.add(new double[]{1, x, range[0], range[1]});
			}
		}
		for (double x : xs) {
			for (double z : zs) {
				if (Math.hypot(x - viewerX, z - viewerZ) <= radius) {
					segments.add(new double[]{2, x, z, 0});
				}
			}
		}

		double length = 0;
		int    count  = 0;
		for (double[] s : segments) {
			count += s[0] == 2 ? 1 : 2;
			length += s[0] == 2 ? maxY - minY : 2 * (s[3] - s[2]);
		}
		// each segment adds at most one point over length / step
		double step = Math.max(STEP, length / Math.max(1, budget - count));

		List<double[]> points = new ArrayList<>();
		for (double[] s : segments) {
			if (s[0] == 2) {
				for (double y = minY; y <= maxY; y += step) {
					points.add(new double[]{s[1], y, s[2]});
				}
				continue;
			}
			for (double t = s[2]; t <= s[3]; t += step) {
				for (double y : new double[]{minY, maxY}) {
					points.add(s[0] == 0 ? new double[]{t, y, s[1]} : new double[]{s[1], y, t});
				}
			}
		}
		return points;
	}

	/** The part of [lo, hi] within the circle's chord {@code off} blocks from the viewer; null when none. */
	private static double[] clip(double lo, double hi, double viewer, double off, double radius) {
		if (off > radius) {
			return null;
		}
		double half = Math.sqrt(radius * radius - off * off);
		double from = Math.max(lo, viewer - half);
		double to   = Math.min(hi, viewer + half);
		return from > to ? null : new double[]{from, to};
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
		Location at = viewer.getLocation();
		for (double[] p : edgePoints(region.getMinX() + 0.5, at.getY() - Y_BAND, region.getMinZ() + 0.5,
		                             region.getMaxX() + 0.5, at.getY() + Y_BAND, region.getMaxZ() + 0.5,
		                             at.getX(), at.getY(), at.getZ(), CLIP_RADIUS, BUDGET)) {
			viewer.spawnParticle(particle, new Location(world, p[0], p[1], p[2]), 1, 0, 0, 0, 0);
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
