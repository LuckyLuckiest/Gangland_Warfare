package org.luckyraven.gangland.civilians.npc;

import org.luckyraven.keystone.npc.NpcSquad;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.LongSupplier;

/**
 * Squads told to hold: when a commander falls, its squad stops advancing for {@link #COMMANDER_DOWN_MS}. One per
 * {@link CivilianService}; a squad is forgotten with the squad itself.
 */
public final class SquadHold {

	/** How long a squad holds after its commander falls. */
	public static final long COMMANDER_DOWN_MS = 5_000L;

	private final LongSupplier             clock;
	private final Map<NpcSquad, Long> until = new WeakHashMap<>();

	public SquadHold(LongSupplier clock) {
		this.clock = clock;
	}

	/** Makes {@code squad} hold for {@code millis} from now (a longer hold already running is kept). */
	public void start(NpcSquad squad, long millis) {
		until.merge(squad, clock.getAsLong() + millis, Math::max);
	}

	/** Whether {@code squad} is still holding. */
	public boolean holds(NpcSquad squad) {
		Long end = until.get(squad);
		return end != null && clock.getAsLong() < end;
	}
}
