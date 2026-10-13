package org.luckyraven.gangland.copsncrooks.scanner;

import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.events.wanted.EvasionState;
import org.luckyraven.gangland.events.wanted.WantedEvasionStateEvent;

import java.util.UUID;

/**
 * The last {@link WantedEvasionStateEvent} per player (state, centre, radius), so the Scanner Map can draw the search
 * circle without asking the evasion clock. {@code OFF} clears the entry.
 *
 * @since 0.20.0
 */
public class SearchZoneCache {

	/** A cached zone; {@code centre} is {@code null} when the event carried none (SEEN). */
	public record Zone(EvasionState state, @Nullable Location centre, double radius) {

	}

	/** Remembers {@code event} as the player's latest zone; {@code OFF} forgets the player. */
	public void record(WantedEvasionStateEvent event) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** The latest zone of {@code playerId}, or {@code null} when there is none. */
	public @Nullable Zone get(UUID playerId) {
		throw new UnsupportedOperationException("TDD stub");
	}

	/** Drops the player's entry (quit). */
	public void evict(UUID playerId) {
		throw new UnsupportedOperationException("TDD stub");
	}
}
