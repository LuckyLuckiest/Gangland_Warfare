package org.luckyraven.gangland.turf.npc.defender;

import org.luckyraven.gangland.turf.data.CuboidRegion;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.IntBinaryOperator;

/** Pure geometry for a turf garrison: the hidden posts, the leash and the spawn-post choice. */
public final class GarrisonPosts {

	private static final int INSET = 2;

	private GarrisonPosts() {
	}

	/** A standing spot; {@code y} is the block above the surface. */
	public record Post(double x, double y, double z) {
	}

	/** The post a wave spawns at, and whether no attacker can see it. */
	public record Choice(Post post, boolean hidden) {
	}

	/** The five {x, z} block columns: four corners inset 2 blocks (less on a tiny region) plus the centre. */
	public static List<int[]> spots(CuboidRegion region) {
		int ix = Math.min(INSET, (region.getMaxX() - region.getMinX()) / 2);
		int iz = Math.min(INSET, (region.getMaxZ() - region.getMinZ()) / 2);
		int x0 = region.getMinX() + ix;
		int x1 = region.getMaxX() - ix;
		int z0 = region.getMinZ() + iz;
		int z1 = region.getMaxZ() - iz;
		int cx = (region.getMinX() + region.getMaxX()) / 2;
		int cz = (region.getMinZ() + region.getMaxZ()) / 2;
		return List.of(new int[]{x0, z0}, new int[]{x1, z0}, new int[]{x0, z1}, new int[]{x1, z1}, new int[]{cx, cz});
	}

	/** The five posts, each at {@code height(x, z) + 1} and the middle of its block. */
	public static List<Post> posts(CuboidRegion region, IntBinaryOperator height) {
		List<Post> posts = new ArrayList<>(5);
		for (int[] spot : spots(region)) {
			posts.add(post(spot, height));
		}
		return posts;
	}

	/**
	 * Five elements in {@link #spots} order: a post where the chunk is loaded, null where it is not. {@code
	 * isChunkLoaded} takes chunk coordinates, and {@code height} is never called for an unloaded chunk, since a height
	 * read there would load it.
	 */
	public static List<Post> loaded(CuboidRegion region, BiPredicate<Integer, Integer> isChunkLoaded,
	                                IntBinaryOperator height) {
		List<Post> loaded = new ArrayList<>(5);
		for (int[] spot : spots(region)) {
			loaded.add(isChunkLoaded.test(spot[0] >> 4, spot[1] >> 4) ? post(spot, height) : null);
		}
		return loaded;
	}

	private static Post post(int[] spot, IntBinaryOperator height) {
		return new Post(spot[0] + 0.5, height.applyAsInt(spot[0], spot[1]) + 1.0, spot[1] + 0.5);
	}

	/** Whether a point is inside the region widened by {@code margin} blocks on every side. */
	public static boolean withinLeash(double x, double z, CuboidRegion region, int margin) {
		return x >= region.getMinX() - margin && x < region.getMaxX() + 1.0 + margin
		       && z >= region.getMinZ() - margin && z < region.getMaxZ() + 1.0 + margin;
	}

	/**
	 * The post farthest from the nearest attacker that no attacker can see; when none is hidden, the farthest post and
	 * {@code hidden=false} so the caller waits before using it. With no attacker every post is hidden.
	 *
	 * @param attackers attacker eye positions {x, y, z}
	 * @param canSee whether an attacker sees a post
	 */
	public static Choice chooseSpawn(List<Post> posts, List<double[]> attackers, BiPredicate<Post, double[]> canSee) {
		if (attackers.isEmpty()) {
			return new Choice(posts.get(0), true);
		}
		Post   bestHidden = null;
		double hiddenDist = -1;
		Post   bestAny    = null;
		double anyDist    = -1;
		for (Post post : posts) {
			double  dist   = Double.MAX_VALUE;
			boolean hidden = true;
			for (double[] attacker : attackers) {
				dist = Math.min(dist, Math.hypot(post.x() - attacker[0], post.z() - attacker[2]));
				if (canSee.test(post, attacker)) {
					hidden = false;
				}
			}
			if (dist > anyDist) {
				anyDist = dist;
				bestAny = post;
			}
			if (hidden && dist > hiddenDist) {
				hiddenDist = dist;
				bestHidden = post;
			}
		}
		return bestHidden != null ? new Choice(bestHidden, true) : new Choice(bestAny, false);
	}

	/** Hold order: whether a point is within {@code radius} blocks of the post, horizontal distance only. */
	public static boolean withinHold(Post post, double x, double z, double radius) {
		return Math.hypot(post.x() - x, post.z() - z) <= radius;
	}

	/** The post closest to a point. */
	public static Post nearest(List<Post> posts, double x, double z) {
		Post   best     = null;
		double bestDist = Double.MAX_VALUE;
		for (Post post : posts) {
			double dist = Math.hypot(post.x() - x, post.z() - z);
			if (dist < bestDist) {
				bestDist = dist;
				best     = post;
			}
		}
		return best;
	}
}
