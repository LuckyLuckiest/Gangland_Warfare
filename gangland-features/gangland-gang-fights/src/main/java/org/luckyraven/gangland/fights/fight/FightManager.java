package org.luckyraven.gangland.fights.fight;

import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.fights.arena.Arena;
import org.luckyraven.gangland.fights.arena.ArenaPoint;
import org.luckyraven.gangland.fights.arena.ArenaRegistry;
import org.luckyraven.gangland.fights.fight.ChallengeRules.Candidate;
import org.luckyraven.gangland.fights.fight.ChallengeRules.Party;
import org.luckyraven.gangland.gang.combat.CombatTag;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The fight state machine: PROPOSED, LOBBY, COUNTDOWN, RUNNING, RESULT, ENDED (or CANCELLED from any step before
 * RESULT). Everything runs on the server thread: the {@link FightClock} schedules sync tasks, and the commands and
 * listeners call in from there. Every cancel path goes through {@link #cancel} and refunds a held stake.
 */
public class FightManager implements org.luckyraven.keystone.bean.BeanLifecycle {

	private final @Nullable FightClock            clock;
	private final @Nullable FightSettlement       settlement;
	private final @Nullable CombatTag             combatTag;
	private final @Nullable ArenaRegistry         arenas;
	private final @Nullable FightWorld            world;
	private final @Nullable Supplier<FightSettings> settings;

	private final Map<Long, Fight>           fights        = new LinkedHashMap<>();
	private final Map<UUID, PlayerSnapshot>  snapshots     = new HashMap<>();
	private final Map<String, Long>          declineUntil  = new HashMap<>();
	private       long                       nextId        = 1;

	/** Inert instance: nothing ever runs. For tests of other lanes. */
	public FightManager() {
		this(null, null, null, null, null, null);
	}

	public FightManager(@Nullable FightClock clock, @Nullable FightSettlement settlement,
	                    @Nullable CombatTag combatTag, @Nullable ArenaRegistry arenas, @Nullable FightWorld world,
	                    @Nullable Supplier<FightSettings> settings) {
		this.clock      = clock;
		this.settlement = settlement;
		this.combatTag  = combatTag;
		this.arenas     = arenas;
		this.world      = world;
		this.settings   = settings;
	}

	// ------------------------------------------------------------------ verbs

	public void propose(UUID actor, String targetGang, int size, double bet) {
		Optional<Integer> own = world.gangOf(actor);
		if (own.isEmpty()) {
			world.tell(actor, "Challenge.No_Gang");
			return;
		}
		Optional<Integer> target = world.gangByName(targetGang);
		if (target.isEmpty()) {
			world.tell(actor, "Challenge.Unknown_Gang", "gang", targetGang);
			return;
		}
		int a = own.get();
		int b = target.get();
		if (a != b && (fightOfGang(a) != null || fightOfGang(b) != null)) {
			world.tell(actor, "Challenge.Busy");
			return;
		}

		long now = clock.now().toEpochMilli();
		Long until = declineUntil.get(a + ":" + b);
		if (until != null && until > now) {
			world.tell(actor, "Challenge.Cooldown", "seconds", (until - now + 999) / 1000);
			return;
		}

		FightSettings s = settings.get();
		Party proposer = new Party(world.mayFight(actor), world.mayWithdraw(actor), world.bank(a), world.gangAgeDays(a));
		Party other    = new Party(true, true, world.bank(b), world.gangAgeDays(b));
		String refusal = ChallengeRules.propose(s, a == b, a != b && world.allied(a, b), proposer, other, size, bet);
		if (refusal != null) {
			world.tell(actor, refusal, "min", s.minTeamSize(), "max", s.maxTeamSize(), "gang", world.gangName(b));
			return;
		}

		Fight fight = new Fight(nextId++, a, b, size, bet, s.ranked(bet), now);
		fights.put(fight.id(), fight);
		world.tell(actor, "Challenge.Sent", "gang", world.gangName(b), "size", size, "bet", bet);
		for (UUID officer : world.officers(b)) {
			world.tell(officer, "Challenge.Received", "gang", world.gangName(a), "size", size, "bet", bet,
			           "seconds", s.challengeExpirySeconds());
		}
		clock.schedule(() -> {
			if (fight.state() == FightState.PROPOSED && fights.containsKey(fight.id())) {
				declineUntil.put(a + ":" + b, clock.now().toEpochMilli() + settings.get().declineCooldownSeconds() * 1000L);
				cancel(fight, "Challenge.Expired");
			}
		}, Duration.ofSeconds(s.challengeExpirySeconds()));
	}

	public void accept(UUID actor, @Nullable String proposerGang) {
		Optional<Integer> own = world.gangOf(actor);
		if (own.isEmpty()) {
			world.tell(actor, "Challenge.No_Gang");
			return;
		}
		Fight fight = null;
		for (Fight f : fights.values()) {
			if (f.state() != FightState.PROPOSED || f.gangB() != own.get()) continue;
			if (proposerGang != null && !proposerGang.isBlank()
			    && !world.gangName(f.gangA()).equalsIgnoreCase(proposerGang)) continue;
			fight = f;
			break;
		}
		if (fight == null) {
			world.tell(actor, "Challenge.No_Proposal");
			return;
		}

		if ("?".equals(world.gangName(fight.gangA())) || "?".equals(world.gangName(fight.gangB()))) {
			cancel(fight, "Challenge.Gang_Gone");
			return;
		}
		if (world.allied(fight.gangA(), fight.gangB())) {
			cancel(fight, "Challenge.Allied");
			return;
		}
		FightSettings s = settings.get();
		String refusal = ChallengeRules.accept(s, new Party(world.mayFight(actor), world.mayWithdraw(actor),
		                                                    world.bank(fight.gangB()), 0), fight.bet());
		if (refusal != null) {
			world.tell(actor, refusal);
			return;
		}
		Optional<Arena> arena = arenas.freeArena(busyArenas());
		if (arena.isEmpty()) {
			world.tell(actor, "Challenge.No_Arena");
			return;
		}
		fight.arenaName(arena.get().name());

		if (fight.bet() > 0) {
			EscrowResult escrow = settlement.escrow(fight);
			if (!escrow.ok()) {
				cancel(fight, escrow.failureKey() == null ? "Challenge.Escrow_Failed" : escrow.failureKey());
				return;
			}
			fight.escrowed(true);
		}

		fight.state(FightState.LOBBY);
		Fight lobby = fight;
		for (int gang : new int[]{fight.gangA(), fight.gangB()}) {
			for (UUID member : world.onlineMembers(gang)) {
				world.tell(member, "Challenge.Lobby_Open", "a", world.gangName(fight.gangA()), "b",
				           world.gangName(fight.gangB()), "size", fight.size(), "bet", fight.bet(), "arena",
				           arena.get().name(), "seconds", s.joinWindowSeconds());
			}
		}
		clock.schedule(() -> beginCountdown(lobby), Duration.ofSeconds(s.joinWindowSeconds()));
	}

	public void decline(UUID actor) {
		decline(actor, null);
	}

	public void decline(UUID actor, @Nullable String proposerGang) {
		Optional<Integer> own = world.gangOf(actor);
		Fight fight = null;
		if (own.isPresent()) {
			for (Fight f : fights.values()) {
				if (f.state() == FightState.PROPOSED && f.gangB() == own.get()
				    && (proposerGang == null || proposerGang.isBlank()
				        || world.gangName(f.gangA()).equalsIgnoreCase(proposerGang))) {
					fight = f;
					break;
				}
			}
		}
		if (fight == null) {
			world.tell(actor, "Challenge.No_Proposal");
			return;
		}
		if (!world.mayFight(actor)) {
			world.tell(actor, "Challenge.Rank_Needed");
			return;
		}
		declineUntil.put(fight.gangA() + ":" + fight.gangB(),
		                 clock.now().toEpochMilli() + settings.get().declineCooldownSeconds() * 1000L);
		cancel(fight, "Challenge.Declined", "gang", world.gangName(fight.gangB()));
	}

	public void enlist(UUID actor) {
		Optional<Integer> own = world.gangOf(actor);
		Fight fight = own.isEmpty() ? null : fightOfGang(own.get());
		if (fight == null || fight.state() != FightState.LOBBY) {
			world.tell(actor, fight == null ? "Challenge.Not_In_Fight" : "Challenge.Not_Open");
			return;
		}
		if (fight.participant(actor).isPresent()) {
			world.tell(actor, "Challenge.Already_Enlisted");
			return;
		}
		FightSide side = own.get() == fight.gangA() ? FightSide.A : FightSide.B;
		if (fight.count(side) >= fight.size()) {
			world.tell(actor, "Challenge.Side_Full");
			return;
		}
		String refusal = ChallengeRules.enlist(settings.get(), candidate(actor), fight.ranked());
		if (refusal != null) {
			world.tell(actor, refusal, "hours", settings.get().rankedMinMemberAgeHours());
			return;
		}

		fight.addParticipant(new Participant(actor, own.get(), side));
		world.tell(actor, "Challenge.Enlisted", "side", world.gangName(own.get()), "count", fight.count(side),
		           "size", fight.size());
		if (fight.count(FightSide.A) >= fight.size() && fight.count(FightSide.B) >= fight.size()) {
			beginCountdown(fight);
		}
	}

	public void drop(UUID actor) {
		Fight fight = fightOf(actor).orElse(null);
		if (fight == null) {
			world.tell(actor, "Challenge.Not_In_Fight");
			return;
		}
		if (fight.state() != FightState.LOBBY) {
			world.tell(actor, "Challenge.Cannot_Drop");
			return;
		}
		fight.removeParticipant(actor);
		world.tell(actor, "Challenge.Dropped");
	}

	public void info(UUID actor) {
		Optional<Integer> own = world.gangOf(actor);
		Fight fight = own.isEmpty() ? null : fightOfGang(own.get());
		if (fight == null) {
			world.tell(actor, "Challenge.No_Fight");
			return;
		}
		world.tell(actor, "Challenge.Info", "a", world.gangName(fight.gangA()), "b", world.gangName(fight.gangB()),
		           "state", fight.state().name().toLowerCase(), "size", fight.size(), "bet", fight.bet(), "arena",
		           fight.arenaName() == null ? "-" : fight.arenaName(), "countA", fight.count(FightSide.A), "countB",
		           fight.count(FightSide.B), "ranked", fight.ranked());
	}

	public void arenas(UUID actor) {
		Set<String> busy = busyArenas();
		List<Arena> all  = arenas.enabled();
		if (all.isEmpty()) {
			world.tell(actor, "Challenge.Arenas_None");
			return;
		}
		for (Arena arena : all) {
			world.tell(actor, busy.contains(arena.name()) ? "Challenge.Arena_Busy" : "Challenge.Arena_Free", "arena",
			           arena.name());
		}
	}

	// ---------------------------------------------------------------- queries

	public List<String> gangNames() {
		return world == null ? List.of() : world.gangNames();
	}

	public Optional<Fight> fightOf(UUID player) {
		for (Fight fight : fights.values()) {
			if (fight.participant(player).isPresent()) return Optional.of(fight);
		}
		return Optional.empty();
	}

	public Optional<Fight> fightInArena(String arenaName) {
		for (Fight fight : fights.values()) {
			if (arenaName.equals(fight.arenaName())) return Optional.of(fight);
		}
		return Optional.empty();
	}

	public Collection<Fight> active() {
		return List.copyOf(fights.values());
	}

	public boolean isParticipant(UUID player) {
		return fightOf(player).isPresent();
	}

	// ------------------------------------------------------------ transitions

	/** A fighter left for good: died, quit, was removed. Before the fight that drops him; during it eliminates him. */
	public void forfeit(UUID player, String reason) {
		Fight fight = fightOf(player).orElse(null);
		if (fight == null) return;

		switch (fight.state()) {
			case LOBBY -> fight.removeParticipant(player);
			case COUNTDOWN -> {
				fight.removeParticipant(player);
				restore(player);
				if (fight.count(FightSide.A) == 0 || fight.count(FightSide.B) == 0) {
					cancel(fight, "Challenge.Cancelled_Short");
				}
			}
			case RUNNING -> {
				fight.eliminate(player);
				combatTag.setFighting(player, false);
				restore(player);
				checkWinner(fight);
			}
			default -> {
			}
		}
	}

	/** Finishes a running fight with a result: settles the stakes, then sends everyone home after the result pause. */
	public void end(Fight fight, Outcome outcome) {
		if (fight.state() != FightState.RUNNING && fight.state() != FightState.COUNTDOWN) return;

		fight.state(FightState.RESULT);
		settlement.settle(fight, outcome);
		for (Participant p : fight.participants()) {
			combatTag.setFighting(p.player(), false);
			world.tell(p.player(), "Challenge.Result_" + outcome.name(), "a", world.gangName(fight.gangA()), "b",
			           world.gangName(fight.gangB()));
		}
		clock.schedule(() -> finish(fight), Duration.ofSeconds(settings.get().resultSeconds()));
	}

	@Override
	public void onClear() {
		abortAll("reload");
	}

	@Override
	public void onShutdown() {
		abortAll("disable");
	}

	/** Cancels every fight, refunds the stakes and sends fighters home. Called when the module disables. */
	public void abortAll(String reason) {
		for (Fight fight : new ArrayList<>(fights.values())) {
			if (fight.state() == FightState.RESULT) {
				finish(fight);
			} else {
				cancel(fight, "Challenge.Aborted");
			}
		}
	}

	private void beginCountdown(Fight fight) {
		if (fight.state() != FightState.LOBBY) return;

		FightSettings s = settings.get();
		// a participant tagged (or gone, or downed) since the lobby is dropped here
		for (Participant p : new ArrayList<>(fight.participants())) {
			String reason = null;
			if (!world.online(p.player())) reason = "Challenge.Dropped_Offline";
			else if (combatTag.isTagged(p.player())) reason = "Challenge.Dropped_Tagged";
			else if (world.downed(p.player())) reason = "Challenge.Dropped_Downed";
			if (reason != null) {
				fight.removeParticipant(p.player());
				world.tell(p.player(), reason);
			}
		}

		long a = fight.count(FightSide.A);
		long b = fight.count(FightSide.B);
		boolean enough = s.requireEqualTeams() ? a == fight.size() && b == fight.size()
		                                      : a >= s.minTeamSize() && b >= s.minTeamSize();
		Optional<Arena> arena = fight.arenaName() == null ? Optional.empty() : arenas.byName(fight.arenaName());
		if (!enough || arena.isEmpty()) {
			cancel(fight, enough ? "Challenge.Cancelled_Arena" : "Challenge.Cancelled_Short");
			return;
		}

		fight.state(FightState.COUNTDOWN);
		int indexA = 0;
		int indexB = 0;
		for (Participant p : fight.participants()) {
			List<ArenaPoint> spawns = p.side() == FightSide.A ? arena.get().spawnsA() : arena.get().spawnsB();
			int index = p.side() == FightSide.A ? indexA++ : indexB++;
			snapshots.put(p.player(), world.snapshot(p.player()));
			if (spawns.isEmpty() || !world.teleport(p.player(), spawns.get(index % spawns.size()))) {
				cancel(fight, "Challenge.Cancelled_Teleport");
				return;
			}
			world.tell(p.player(), "Challenge.Countdown", "seconds", s.countdownSeconds());
		}
		clock.schedule(() -> startRunning(fight), Duration.ofSeconds(s.countdownSeconds()));
	}

	private void startRunning(Fight fight) {
		if (fight.state() != FightState.COUNTDOWN) return;

		fight.state(FightState.RUNNING);
		for (Participant p : fight.participants()) {
			if (!world.online(p.player())) {
				fight.eliminate(p.player());
				continue;
			}
			combatTag.setFighting(p.player(), true);
			world.tell(p.player(), "Challenge.Go");
		}
		if (checkWinner(fight)) return;

		clock.schedule(() -> timeout(fight), Duration.ofMinutes(settings.get().maxDurationMinutes()));
	}

	private void timeout(Fight fight) {
		if (fight.state() != FightState.RUNNING) return;

		long a = fight.alive(FightSide.A);
		long b = fight.alive(FightSide.B);
		Outcome outcome = Outcome.DRAW;
		if ("MOST_ALIVE".equalsIgnoreCase(settings.get().timeoutResult()) && a != b) {
			outcome = a > b ? Outcome.WIN_A : Outcome.WIN_B;
		}
		end(fight, outcome);
	}

	private boolean checkWinner(Fight fight) {
		if (fight.state() != FightState.RUNNING) return false;

		long a = fight.alive(FightSide.A);
		long b = fight.alive(FightSide.B);
		if (a > 0 && b > 0) return false;

		end(fight, a == 0 && b == 0 ? Outcome.DRAW : a == 0 ? Outcome.WIN_B : Outcome.WIN_A);
		return true;
	}

	private void finish(Fight fight) {
		if (fight.state() != FightState.RESULT) return;

		for (Participant p : fight.participants()) {
			restore(p.player());
		}
		fight.state(FightState.ENDED);
		fights.remove(fight.id());
	}

	private void cancel(Fight fight, String key, Object... pairs) {
		if (fight.state() == FightState.ENDED || fight.state() == FightState.CANCELLED) return;

		if (fight.escrowed()) {
			settlement.refund(fight);
			fight.escrowed(false);
		}
		for (Participant p : fight.participants()) {
			combatTag.setFighting(p.player(), false);
			restore(p.player());
		}
		fight.state(FightState.CANCELLED);
		fights.remove(fight.id());

		Set<UUID> told = new HashSet<>();
		for (int gang : new int[]{fight.gangA(), fight.gangB()}) {
			for (UUID member : world.onlineMembers(gang)) {
				if (told.add(member)) world.tell(member, key, pairs);
			}
		}
	}

	private void restore(UUID player) {
		PlayerSnapshot snapshot = snapshots.remove(player);
		if (snapshot != null) world.restore(snapshot);
	}

	// ---------------------------------------------------------------- helpers

	private @Nullable Fight fightOfGang(int gangId) {
		for (Fight fight : fights.values()) {
			if (fight.gangA() == gangId || fight.gangB() == gangId) return fight;
		}
		return null;
	}

	private Set<String> busyArenas() {
		Set<String> busy = new HashSet<>();
		for (Fight fight : fights.values()) {
			if (fight.arenaName() != null) busy.add(fight.arenaName());
		}
		return busy;
	}

	private Candidate candidate(UUID player) {
		return new Candidate(combatTag.isTagged(player), world.downed(player), world.wanted(player),
		                     world.memberAgeHours(player));
	}
}
