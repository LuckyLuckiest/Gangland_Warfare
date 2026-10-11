package org.luckyraven.gangland.fights.fight;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.fights.arena.Arena;
import org.luckyraven.gangland.fights.arena.ArenaPoint;
import org.luckyraven.gangland.fights.arena.ArenaRegistry;
import org.luckyraven.gangland.gang.combat.CombatTag;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("FightManager - state machine with a fake clock")
class FightStateMachineTest {

	static final int VIPERS = 1;
	static final int RATS   = 2;

	/** Hand-driven clock. */
	static final class FakeClock implements FightClock {

		long now = 0;
		final List<Object[]> tasks = new ArrayList<>();

		@Override
		public Instant now() {
			return Instant.ofEpochMilli(now);
		}

		@Override
		public void schedule(Runnable task, Duration delay) {
			tasks.add(new Object[]{now + delay.toMillis(), task});
		}

		void advance(long seconds) {
			long until = now + seconds * 1000;
			while (true) {
				Object[] next = null;
				for (Object[] t : tasks) {
					if ((long) t[0] <= until && (next == null || (long) t[0] < (long) next[0])) next = t;
				}
				if (next == null) break;
				tasks.remove(next);
				now = (long) next[0];
				((Runnable) next[1]).run();
			}
			now = until;
		}
	}

	static final class FakeSettlement extends FightSettlement {

		boolean escrowOk = true;
		int escrows, refunds, settles;
		Outcome settled;

		@Override
		public EscrowResult escrow(Fight fight) {
			escrows++;
			return escrowOk ? EscrowResult.success() : EscrowResult.fail("Economy.Short");
		}

		@Override
		public void settle(Fight fight, Outcome outcome) {
			settles++;
			settled = outcome;
		}

		@Override
		public void refund(Fight fight) {
			refunds++;
		}
	}

	static final class FakeWorld implements FightWorld {

		final Map<UUID, Integer>   gang       = new HashMap<>();
		final Map<Integer, String> names      = Map.of(VIPERS, "Vipers", RATS, "Rats");
		final Set<UUID>            offline    = new HashSet<>();
		final Set<UUID>            teleported = new HashSet<>();
		final Set<UUID>            restored   = new HashSet<>();
		final List<String>         told       = new ArrayList<>();
		boolean allied;

		@Override
		public Optional<Integer> gangOf(UUID p) {
			return Optional.ofNullable(gang.get(p));
		}

		@Override
		public Optional<Integer> gangByName(String n) {
			return names.entrySet().stream().filter(e -> e.getValue().equalsIgnoreCase(n)).map(Map.Entry::getKey)
			            .findFirst();
		}

		@Override
		public String gangName(int id) {
			return names.get(id);
		}

		@Override
		public List<String> gangNames() {
			return List.copyOf(names.values());
		}

		@Override
		public boolean allied(int a, int b) {
			return allied;
		}

		@Override
		public boolean mayFight(UUID p) {
			return true;
		}

		@Override
		public boolean mayWithdraw(UUID p) {
			return true;
		}

		@Override
		public double bank(int id) {
			return 100000;
		}

		@Override
		public long gangAgeDays(int id) {
			return 30;
		}

		@Override
		public long memberAgeHours(UUID p) {
			return 100;
		}

		@Override
		public boolean online(UUID p) {
			return !offline.contains(p);
		}

		@Override
		public boolean downed(UUID p) {
			return false;
		}

		@Override
		public boolean wanted(UUID p) {
			return false;
		}

		@Override
		public List<UUID> officers(int id) {
			return gang.entrySet().stream().filter(e -> e.getValue() == id).map(Map.Entry::getKey).toList();
		}

		@Override
		public List<UUID> onlineMembers(int id) {
			return officers(id);
		}

		@Override
		public boolean teleport(UUID p, ArenaPoint to) {
			teleported.add(p);
			return true;
		}

		@Override
		public PlayerSnapshot snapshot(UUID p) {
			return new PlayerSnapshot(p, new ArenaPoint("w", 0, 64, 0, 0, 0), 20, 20);
		}

		@Override
		public void restore(PlayerSnapshot s) {
			restored.add(s.player());
		}

		@Override
		public void tell(UUID p, String key, Object... pairs) {
			told.add(p + ":" + key);
		}

		boolean told(UUID p, String key) {
			return told.contains(p + ":" + key);
		}
	}

	static final class FakeArenas extends ArenaRegistry {

		boolean none;
		final Arena arena = new Arena("pit", null, List.of(new ArenaPoint("w", 1, 64, 1, 0, 0)),
		                              List.of(new ArenaPoint("w", 9, 64, 9, 0, 0)),
		                              new ArenaPoint("w", 0, 64, 0, 0, 0), true);

		@Override
		public Optional<Arena> freeArena(Set<String> busy) {
			return none || busy.contains("pit") ? Optional.empty() : Optional.of(arena);
		}

		@Override
		public Optional<Arena> byName(String name) {
			return "pit".equals(name) ? Optional.of(arena) : Optional.empty();
		}

		@Override
		public List<Arena> enabled() {
			return List.of(arena);
		}
	}

	static final class FakeTag extends CombatTag {

		final Set<UUID> tagged = new HashSet<>();

		@Override
		public boolean isTagged(UUID p) {
			return tagged.contains(p);
		}
	}

	FakeClock      clock;
	FakeSettlement settlement;
	FakeWorld      world;
	FakeArenas     arenas;
	FakeTag        tag;
	FightManager   manager;
	UUID           v1, v2, r1, r2;

	@BeforeEach
	void setUp() {
		clock      = new FakeClock();
		settlement = new FakeSettlement();
		world      = new FakeWorld();
		arenas     = new FakeArenas();
		tag        = new FakeTag();
		v1         = UUID.randomUUID();
		v2         = UUID.randomUUID();
		r1         = UUID.randomUUID();
		r2         = UUID.randomUUID();
		world.gang.put(v1, VIPERS);
		world.gang.put(v2, VIPERS);
		world.gang.put(r1, RATS);
		world.gang.put(r2, RATS);
		manager = new FightManager(clock, settlement, tag, arenas, world, FightRulesTest::settings);
	}

	private Fight lobby(double bet) {
		manager.propose(v1, "Rats", 2, bet);
		manager.accept(r1, "Vipers");
		return manager.active().iterator().next();
	}

	private Fight running(double bet) {
		Fight f = lobby(bet);
		manager.enlist(v1);
		manager.enlist(v2);
		manager.enlist(r1);
		manager.enlist(r2);
		clock.advance(10);
		return f;
	}

	@Test
	@DisplayName("everyTransition - PROPOSED, LOBBY, COUNTDOWN, RUNNING, RESULT, ENDED")
	void everyTransition() {
		manager.propose(v1, "Rats", 2, 1000);
		Fight f = manager.active().iterator().next();
		assertEquals(FightState.PROPOSED, f.state());
		assertTrue(world.told(r1, "Challenge.Received"));

		manager.accept(r1, "Vipers");
		assertEquals(FightState.LOBBY, f.state());
		assertEquals("pit", f.arenaName());
		assertEquals(1, settlement.escrows);

		manager.enlist(v1);
		manager.enlist(v2);
		manager.enlist(r1);
		assertEquals(FightState.LOBBY, f.state());
		manager.enlist(r2);
		assertEquals(FightState.COUNTDOWN, f.state());
		assertTrue(world.teleported.containsAll(Set.of(v1, v2, r1, r2)));
		assertFalse(tag.isFighting(v1));

		clock.advance(10);
		assertEquals(FightState.RUNNING, f.state());
		assertTrue(tag.isFighting(v1) && tag.isFighting(r2));

		manager.forfeit(r1, "dead");
		assertEquals(FightState.RUNNING, f.state());
		manager.forfeit(r2, "dead");
		assertEquals(FightState.RESULT, f.state());
		assertEquals(Outcome.WIN_A, settlement.settled);
		assertFalse(tag.isFighting(v1));

		clock.advance(5);
		assertEquals(FightState.ENDED, f.state());
		assertTrue(world.restored.containsAll(Set.of(v1, v2, r1, r2)));
		assertTrue(manager.active().isEmpty());
		assertEquals(0, settlement.refunds);
	}

	@Test
	@DisplayName("everyCancel_refunds - decline, expiry, no arena, escrow failure, short side, abortAll")
	void everyCancel_refunds() {
		// decline before accept: nothing escrowed, nothing to refund
		manager.propose(v1, "Rats", 2, 1000);
		manager.decline(r1);
		assertTrue(manager.active().isEmpty());
		assertEquals(0, settlement.refunds);

		// expiry
		clock.advance(61);
		manager.propose(v1, "Rats", 2, 1000);
		clock.advance(61);
		assertTrue(manager.active().isEmpty());

		// no free arena: stays proposed
		arenas.none = true;
		clock.advance(61);
		manager.propose(v1, "Rats", 2, 1000);
		manager.accept(r1, "Vipers");
		assertEquals(FightState.PROPOSED, manager.active().iterator().next().state());
		manager.decline(r1);
		arenas.none = false;

		// escrow failure cancels without a refund (nothing was held)
		clock.advance(61);
		settlement.escrowOk = false;
		manager.propose(v1, "Rats", 2, 1000);
		manager.accept(r1, "Vipers");
		assertTrue(manager.active().isEmpty());
		assertEquals(0, settlement.refunds);
		settlement.escrowOk = true;

		// lobby window ends short: refund
		clock.advance(61);
		Fight f = lobby(1000);
		manager.enlist(v1);
		clock.advance(60);
		assertEquals(FightState.CANCELLED, f.state());
		assertEquals(1, settlement.refunds);

		// abortAll mid-fight: refund and restore
		clock.advance(61);
		Fight g = running(1000);
		assertEquals(FightState.RUNNING, g.state());
		manager.abortAll("disable");
		assertEquals(FightState.CANCELLED, g.state());
		assertEquals(2, settlement.refunds);
		assertFalse(tag.isFighting(v1));
		assertTrue(world.restored.contains(v1));
	}

	@Test
	@DisplayName("tagDuringLobby_droppedAtCountdown_shortSideCancelsAndRefunds")
	void tagDuringLobby_droppedAtCountdown_shortSideCancelsAndRefunds() {
		Fight f = lobby(1000);
		manager.enlist(v1);
		manager.enlist(v2);
		manager.enlist(r1);
		tag.tagged.add(r1);
		manager.enlist(r2);

		assertEquals(FightState.CANCELLED, f.state());
		assertEquals(1, settlement.refunds);
		assertTrue(world.told(r1, "Challenge.Dropped_Tagged"));
		assertTrue(world.teleported.isEmpty());
	}

	@Test
	@DisplayName("taggedAtEnlist - refused with a sentence and not added")
	void taggedAtEnlist() {
		Fight f = lobby(0);
		tag.tagged.add(v1);
		manager.enlist(v1);

		assertTrue(f.participants().isEmpty());
		assertTrue(world.told(v1, "Challenge.Tagged"));
	}

	@Test
	@DisplayName("timeout_mostAlive")
	void timeout_mostAlive() {
		Fight f = running(0);
		manager.forfeit(r1, "dead");
		clock.advance(10 * 60);

		assertEquals(FightState.RESULT, f.state());
		assertEquals(Outcome.WIN_A, settlement.settled);
	}

	@Test
	@DisplayName("timeout_equalIsDraw")
	void timeout_equalIsDraw() {
		Fight f = running(0);
		clock.advance(10 * 60);

		assertEquals(Outcome.DRAW, settlement.settled);
		assertEquals(FightState.RESULT, f.state());
	}

	@Test
	@DisplayName("refusals - allied, own gang, busy")
	void refusals() {
		world.allied = true;
		manager.propose(v1, "Rats", 2, 0);
		assertTrue(world.told(v1, "Challenge.Allied"));
		world.allied = false;
		manager.propose(v1, "Vipers", 2, 0);
		assertTrue(world.told(v1, "Challenge.Same_Gang"));
		manager.propose(v1, "Rats", 2, 0);
		manager.propose(r1, "Vipers", 2, 0);
		assertTrue(world.told(r1, "Challenge.Busy"));
		assertEquals(1, manager.active().size());
	}

	@Test
	@DisplayName("drop - leaves the lobby")
	void drop() {
		Fight f = lobby(0);
		manager.enlist(v1);
		manager.drop(v1);
		assertTrue(f.participants().isEmpty());
		manager.drop(v1);
		assertTrue(world.told(v1, "Challenge.Not_In_Fight"));
	}

	@Test
	@DisplayName("declineCooldown - the proposer cannot re-challenge at once")
	void declineCooldown() {
		manager.propose(v1, "Rats", 2, 0);
		manager.decline(r1);
		manager.propose(v1, "Rats", 2, 0);
		assertTrue(manager.active().isEmpty());
		assertTrue(world.told(v1, "Challenge.Cooldown"));
	}
}
