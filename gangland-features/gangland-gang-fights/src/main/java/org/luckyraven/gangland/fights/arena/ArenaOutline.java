package org.luckyraven.gangland.fights.arena;

import com.cryptomorin.xseries.particles.XParticle;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

/** Particle outline of an arena box for one viewer. */
public final class ArenaOutline {

	private static final long   REFRESH_TICKS = 10L;
	private static final double MAX_POINTS    = 600;
	private static final double VIEW_RADIUS   = 64;

	private ArenaOutline() {
	}

	/** The 12 edges of the box (outer faces of its blocks) as points about {@code step} apart, widened to fit the budget. */
	public static List<double[]> edgePoints(ArenaBox box) {
		var    c       = box.cuboid();
		double[] lo    = {c.minX(), c.minY(), c.minZ()};
		double[] hi    = {c.maxX() + 1.0, c.maxY() + 1.0, c.maxZ() + 1.0};
		double perimeter = 4 * ((hi[0] - lo[0]) + (hi[1] - lo[1]) + (hi[2] - lo[2]));
		double step      = Math.max(1.0, perimeter / MAX_POINTS);

		List<double[]> points = new ArrayList<>();
		for (int axis = 0; axis < 3; axis++) {
			int a = (axis + 1) % 3;
			int b = (axis + 2) % 3;
			for (double t = lo[axis]; t <= hi[axis]; t += step) {
				for (double ca : new double[]{lo[a], hi[a]}) {
					for (double cb : new double[]{lo[b], hi[b]}) {
						double[] p = new double[3];
						p[axis] = t;
						p[a]    = ca;
						p[b]    = cb;
						points.add(p);
					}
				}
			}
		}
		return points;
	}

	public static void show(JavaPlugin plugin, Player viewer, ArenaBox box, int seconds) {
		World world = viewer.getServer().getWorld(box.world());
		if (world == null) {
			return;
		}
		Particle       particle = XParticle.FLAME.get();
		List<double[]> points   = edgePoints(box);

		new BukkitRunnable() {
			int ticks;

			@Override
			public void run() {
				if (!viewer.isOnline() || ticks >= seconds * 20) {
					cancel();
					return;
				}
				Location at = viewer.getLocation();
				for (double[] p : points) {
					if (Math.abs(p[0] - at.getX()) <= VIEW_RADIUS && Math.abs(p[2] - at.getZ()) <= VIEW_RADIUS) {
						viewer.spawnParticle(particle, new Location(world, p[0], p[1], p[2]), 1, 0, 0, 0, 0);
					}
				}
				ticks += REFRESH_TICKS;
			}
		}.runTaskTimer(plugin, 0L, REFRESH_TICKS);
	}
}
