package org.luckyraven.gangland.fights.fight;

import java.time.Duration;
import java.time.Instant;

/** Time source of the fight flow, so tests can drive it by hand. */
public interface FightClock {

	Instant now();

	void schedule(Runnable task, Duration delay);
}
