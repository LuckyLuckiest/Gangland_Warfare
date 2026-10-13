package org.luckyraven.gangland.fights.fight;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** One challenge or fight between two gangs: who, how big, what is staked, and where it stands. */
public final class Fight {

	private final long              id;
	private final int               gangA;
	private final int               gangB;
	private final int               size;
	private final double            bet;
	private final boolean           ranked;
	private final long              createdAtMillis;
	private final List<Participant> participants = new ArrayList<>();
	private       FightState        state        = FightState.PROPOSED;
	private final Set<UUID>         eliminated   = new HashSet<>();
	private @Nullable String        arenaName;
	private       boolean           escrowed;

	public Fight(long id, int gangA, int gangB, int size, double bet, boolean ranked, long createdAtMillis) {
		this.id              = id;
		this.gangA           = gangA;
		this.gangB           = gangB;
		this.size            = size;
		this.bet             = bet;
		this.ranked          = ranked;
		this.createdAtMillis = createdAtMillis;
	}

	public long id() {
		return id;
	}

	public int gangA() {
		return gangA;
	}

	public int gangB() {
		return gangB;
	}

	public int size() {
		return size;
	}

	public double bet() {
		return bet;
	}

	public boolean ranked() {
		return ranked;
	}

	public long createdAtMillis() {
		return createdAtMillis;
	}

	public FightState state() {
		return state;
	}

	public void state(FightState state) {
		this.state = state;
	}

	public @Nullable String arenaName() {
		return arenaName;
	}

	public void arenaName(String arenaName) {
		this.arenaName = arenaName;
	}

	public List<Participant> participants() {
		return Collections.unmodifiableList(participants);
	}

	public void addParticipant(Participant participant) {
		participants.add(participant);
	}

	public Optional<Participant> participant(UUID player) {
		return participants.stream().filter(p -> p.player().equals(player)).findFirst();
	}

	public void removeParticipant(UUID player) {
		participants.removeIf(p -> p.player().equals(player));
		eliminated.remove(player);
	}

	public long count(FightSide side) {
		return participants.stream().filter(p -> p.side() == side).count();
	}

	/** Fighters of the side that are still standing (not forfeited, not dead). */
	public long alive(FightSide side) {
		return participants.stream().filter(p -> p.side() == side && !eliminated.contains(p.player())).count();
	}

	public void eliminate(UUID player) {
		eliminated.add(player);
	}

	public boolean eliminated(UUID player) {
		return eliminated.contains(player);
	}

	/** True once both stakes are held by the settlement, so every cancel path owes a refund. */
	public boolean escrowed() {
		return escrowed;
	}

	public void escrowed(boolean escrowed) {
		this.escrowed = escrowed;
	}
}
