package org.luckyraven.gangland.fights.fight;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The running fights. Stub: nothing is ever running until the fight-flow lane (W6-S) fills the bodies. */
public class FightManager {

	public Optional<Fight> fightOf(UUID player) {
		return Optional.empty();
	}

	public Optional<Fight> fightInArena(String arenaName) {
		return Optional.empty();
	}

	public Collection<Fight> active() {
		return List.of();
	}

	public boolean isParticipant(UUID player) {
		return false;
	}

	public void forfeit(UUID player, String reason) {
	}

	public void end(Fight fight, Outcome outcome) {
	}

	/** Cancels every fight, refunds the stakes and sends fighters home. Called when the module disables. */
	public void abortAll(String reason) {
	}
}
