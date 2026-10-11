package org.luckyraven.gangland.turf.npc.defender;

import java.util.List;

/** STUB (red phase). */
public final class GuardRecycle {

	private GuardRecycle() {
	}

	public static boolean due(long unreachableMs, int recycleSeconds, long lastRecycleAt, long now, boolean seen) {
		return false;
	}

	public static GarrisonPosts.Post elsewhere(List<GarrisonPosts.Post> posts, double x, double z,
	                                           List<double[]> attackers,
	                                           java.util.function.BiPredicate<GarrisonPosts.Post, double[]> canSee) {
		return null;
	}
}
