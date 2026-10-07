package org.luckyraven.gangland.data.wanted;

import org.luckyraven.gangland.events.wanted.EvasionState;
import org.luckyraven.gangland.file.configuration.Settings;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * The crooked desk sergeant's books, shared by {@code /glw contact}, the phone page and the {@code [WANTED]} sign:
 * who a cop can see right now (fed by {@code WantedEvasionStateEvent}) and who is still inside the contact cooldown.
 *
 * <p>The two maps are independent. A sighting entry dies with OFF or a quit; a cooldown survives both (the contact
 * itself usually ends the chase) and a new chase. Memory only: a restart clears them.
 */
public final class ContactDesk {

	private final LongSupplier                  clock;
	private final Map<UUID, EvasionState>       states    = new ConcurrentHashMap<>();
	private final Map<UUID, Long>               cooldowns = new ConcurrentHashMap<>();

	public ContactDesk(LongSupplier clock) {
		this.clock = clock;
	}

	/** Records the player's evasion state; OFF removes the sighting entry only. */
	public void observe(UUID id, EvasionState state) {
		if (state == EvasionState.OFF) {
			states.remove(id);
			return;
		}

		states.put(id, state);
	}

	/** A quit: drops the sighting entry, keeps the cooldown. */
	public void forget(UUID id) {
		states.remove(id);
	}

	/** True when the last observed state is SEEN; no entry (no track) counts as unseen. */
	public boolean seen(UUID id) {
		return states.get(id) == EvasionState.SEEN;
	}

	public long cooldownLeftMs(UUID id) {
		Long until = cooldowns.get(id);

		return until == null ? 0L : Math.max(0L, until - clock.getAsLong());
	}

	/** Starts the cooldown from now and prunes the entries that have already run out. */
	public void startCooldown(UUID id) {
		long now = clock.getAsLong();

		cooldowns.values().removeIf(until -> until <= now);
		cooldowns.put(id, now + Settings.getContactsCooldownSeconds() * 1000L);
	}

	public BigDecimal priceFor(int stars) {
		return Settings.getContactsPricePerStar().multiply(BigDecimal.valueOf(stars));
	}

	/** {@code 95000 ms -> "1m 35s"}, {@code 4200 ms -> "5s"}: rounded up to whole seconds. */
	public static String formatLeft(long ms) {
		long seconds = (ms + 999L) / 1000L;

		return seconds >= 60 ? (seconds / 60) + "m " + (seconds % 60) + "s" : seconds + "s";
	}

}
