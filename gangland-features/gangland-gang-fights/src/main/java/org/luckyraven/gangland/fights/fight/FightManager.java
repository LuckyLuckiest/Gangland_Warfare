package org.luckyraven.gangland.fights.fight;

import org.luckyraven.gangland.fights.arena.ArenaRegistry;
import org.luckyraven.gangland.gang.combat.CombatTag;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** The running fights. */
public class FightManager {

	/** Inert instance: nothing ever runs. For tests of other lanes. */
	public FightManager() {
	}

	public FightManager(FightClock clock, FightSettlement settlement, CombatTag combatTag, ArenaRegistry arenas,
	                    FightWorld world, Supplier<FightSettings> settings) {
	}

	public void propose(UUID actor, String targetGang, int size, double bet) {
	}

	public void accept(UUID actor, String proposerGang) {
	}

	public void decline(UUID actor) {
	}

	public void enlist(UUID actor) {
	}

	public void drop(UUID actor) {
	}

	public void info(UUID actor) {
	}

	public void arenas(UUID actor) {
	}

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
