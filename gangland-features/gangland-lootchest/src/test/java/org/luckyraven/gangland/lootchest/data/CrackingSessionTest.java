package org.luckyraven.gangland.lootchest.data;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Proves the {@code CrackingSession} progress/state machine in isolation from its 20-tick
 * scheduler loop (Test Surface, lootchests-signs-waypoints.md mentions the cracking session under
 * W12). {@code start(...)} is stubbed so the returned {@code BukkitTask} is never actually driven
 * — the state transitions asserted here (PENDING -&gt; IN_PROGRESS -&gt; COMPLETED/CANCELLED) are
 * exactly what {@code addProgress}/{@code complete}/{@code cancel} do synchronously, independent of
 * ticking.
 *
 * <p>Per Observation #2 (lootchests-signs-waypoints.md): nothing in the production codebase ever
 * calls {@code addProgress} or {@code completeCracking} — this class's state machine is internally
 * correct, but the whole mini-game is unreachable in practice. These tests pin the machine's
 * correctness so a future wiring-up of the feature has a safety net, not proof the feature works
 * end-to-end.
 */
class CrackingSessionTest {

	private JavaPlugin      plugin;
	private Player          player;
	private BukkitScheduler scheduler;

	@BeforeEach
	void setUp() {
		plugin = mock(JavaPlugin.class);
		player = mock(Player.class);
		Server server = mock(Server.class);
		scheduler = mock(BukkitScheduler.class);

		when(plugin.getServer()).thenReturn(server);
		when(server.getScheduler()).thenReturn(scheduler);
		when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong()))
				.thenReturn(mock(BukkitTask.class));
	}

	@Test
	@DisplayName("a new session starts PENDING with zero progress and a full target")
	void newSession_startsPendingWithZeroProgress() {
		CrackingSession session = session(10);

		assertEquals(CrackingSession.CrackState.PENDING, session.getState());
		assertEquals(0, session.getProgress());
		assertEquals(100, session.getTargetProgress());
		assertEquals(1.0, session.getTimePercentage(), 1e-9, "timeRemaining starts equal to totalTime");
		assertFalse(session.isSuccessful());
		assertFalse(session.isFailed());
		assertFalse(session.isInProgress());
	}

	@Test
	@DisplayName("start() flips state to IN_PROGRESS without waiting for a tick")
	void start_flipsStateToInProgress() {
		CrackingSession session = session(10);

		session.start(noopTick(), noop(), noop());

		assertTrue(session.isInProgress());
		verify(scheduler).runTaskTimer(eq(plugin), any(Runnable.class), eq(0L), eq(20L));
	}

	@Test
	@DisplayName("addProgress accumulates and clamps at targetProgress without completing early")
	void addProgress_accumulatesAndClampsBelowTarget() {
		CrackingSession session = session(10);
		session.start(noopTick(), noop(), noop());

		session.addProgress(40);

		assertEquals(40, session.getProgress());
		assertEquals(0.4, session.getProgressPercentage(), 1e-9);
		assertTrue(session.isInProgress(), "progress below target must not complete the session");
	}

	@Test
	@DisplayName("addProgress reaching targetProgress completes the session exactly once")
	void addProgress_reachingTarget_completesSession() {
		CrackingSession session = session(10);
		session.start(noopTick(), noop(), noop());

		session.addProgress(60);
		session.addProgress(50); // 110 clamped to 100 -> triggers complete()

		assertEquals(100, session.getProgress(), "progress itself is clamped at targetProgress, never overshoots");
		assertTrue(session.isSuccessful());
		assertEquals(CrackingSession.CrackState.COMPLETED, session.getState());
	}

	@Test
	@DisplayName("complete() is a no-op unless the session is IN_PROGRESS")
	void complete_noopWhenNotInProgress() {
		CrackingSession session = session(10);
		// still PENDING - start() was never called.

		session.complete();

		assertEquals(CrackingSession.CrackState.PENDING, session.getState(),
		             "complete() guards on state == IN_PROGRESS; a PENDING session must be unaffected");
	}

	@Test
	@DisplayName("cancel() moves to CANCELLED and stops the timer task from any state")
	void cancel_movesToCancelledAndStopsTimer() {
		CrackingSession session = session(10);
		BukkitTask      task    = mock(BukkitTask.class);
		when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong())).thenReturn(task);

		session.start(noopTick(), noop(), noop());
		session.cancel();

		assertEquals(CrackingSession.CrackState.CANCELLED, session.getState());
		verify(task).cancel();
	}

	private CrackingSession session(long timeSeconds) {
		return new CrackingSession(plugin, player, null, null, null, timeSeconds);
	}

	private static BiConsumer<CrackingSession, Long> noopTick() {
		return (session, remaining) -> { };
	}

	private static Consumer<CrackingSession> noop() {
		return session -> { };
	}

}
