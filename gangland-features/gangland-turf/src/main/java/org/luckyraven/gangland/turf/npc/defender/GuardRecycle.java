package org.luckyraven.gangland.turf.npc.defender;

import org.luckyraven.gangland.turf.npc.defender.GarrisonPosts.Post;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * The decisions behind stuck-guard recycling (cops' version lives in CopManager): a guard that has found no way to
 * anyone for {@code Garrison.Recycle_Seconds} while no attacker can see it is taken off the map and put back at another
 * post. Pure functions of the clock and a sight predicate so they test without a server.
 */
final class GuardRecycle {

	private GuardRecycle() {
	}

	/**
	 * Whether a guard is recycled now: unreachable for longer than {@code recycleSeconds}, seen by nobody, and not
	 * recycled within the last {@code recycleSeconds}. {@code recycleSeconds <= 0} switches recycling off.
	 */
	static boolean due(long unreachableMs, int recycleSeconds, long lastRecycleAt, long now, boolean seen) {
		if (recycleSeconds <= 0 || seen) return false;
		long window = recycleSeconds * 1000L;
		return unreachableMs > window && (lastRecycleAt < 0 || now - lastRecycleAt >= window);
	}

	/**
	 * Where the recycled guard reappears: the best hidden post other than the one nearest to where it was stuck, or the
	 * only post there is.
	 */
	static Post elsewhere(List<Post> posts, double x, double z, List<double[]> attackers,
	                      BiPredicate<Post, double[]> canSee) {
		Post stuckAt = GarrisonPosts.nearest(posts, x, z);
		List<Post> others = new ArrayList<>(posts);
		others.remove(stuckAt);
		if (others.isEmpty()) return stuckAt;
		return GarrisonPosts.chooseSpawn(others, attackers, canSee).post();
	}
}
