package org.luckyraven.gangland.copsncrooks.npc.police.state;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the one-target-one-cop lock semantics in {@link CuffLockRegistry}: {@code tryAcquire} is exclusive,
 * {@code release} only frees a lock the caller actually owns, {@code forceRelease} frees unconditionally, and
 * {@code releaseByCop} sweeps every lock a given cop holds. Also pins that {@link CuffLockRegistry#releaseByCop}
 * and {@link CuffLockRegistry#forceRelease} are the only ways to clear a lock other than a successful cuff handoff —
 * cops-detainment-jail.md Observation #24 notes neither has a call site anywhere in the codebase today, so a cop
 * destroyed on a path that skips {@code onExit} leaves a stale lock; this suite pins that the primitives themselves
 * behave correctly so a future fix can wire them in with confidence.
 */
@DisplayName("CuffLockRegistry — target/cop lock ownership")
class CuffLockRegistryTest {

	private CuffLockRegistry registry;
	private UUID             target;
	private UUID             copA;
	private UUID             copB;

	@BeforeEach
	void setUp() {
		registry = new CuffLockRegistry();
		target   = UUID.randomUUID();
		copA     = UUID.randomUUID();
		copB     = UUID.randomUUID();
	}

	@Test
	@DisplayName("tryAcquire on an unlocked target succeeds and records ownership")
	void tryAcquire_unlockedTarget_succeeds() {
		assertTrue(registry.tryAcquire(target, copA));
		assertTrue(registry.isOwner(target, copA));
		assertEquals(copA, registry.getOwner(target));
	}

	@Test
	@DisplayName("a second cop cannot acquire a target already locked by another cop")
	void tryAcquire_alreadyLocked_fails() {
		registry.tryAcquire(target, copA);

		assertFalse(registry.tryAcquire(target, copB));
		assertTrue(registry.isOwner(target, copA));
		assertFalse(registry.isOwner(target, copB));
	}

	@Test
	@DisplayName("the same cop re-acquiring its own lock is a no-op success (putIfAbsent semantics)")
	void tryAcquire_sameCopReacquires_staysOwner() {
		registry.tryAcquire(target, copA);

		// putIfAbsent never overwrites, so a second call from the SAME cop returns false, not true — the lock is
		// already held so the caller doesn't need to re-acquire, but the return value alone can't distinguish
		// "already mine" from "someone else's".
		assertFalse(registry.tryAcquire(target, copA));
		assertTrue(registry.isOwner(target, copA));
	}

	@Test
	@DisplayName("release only frees the lock when called by its owner")
	void release_wrongCop_doesNotFree() {
		registry.tryAcquire(target, copA);

		registry.release(target, copB);

		assertTrue(registry.isOwner(target, copA), "a non-owner's release call must not free the lock");
	}

	@Test
	@DisplayName("release by the owning cop frees the lock for the next acquirer")
	void release_owningCop_frees() {
		registry.tryAcquire(target, copA);

		registry.release(target, copA);

		assertNull(registry.getOwner(target));
		assertTrue(registry.tryAcquire(target, copB), "target must be acquirable again once freed");
	}

	@Test
	@DisplayName("forceRelease frees the lock regardless of ownership")
	void forceRelease_freesUnconditionally() {
		registry.tryAcquire(target, copA);

		registry.forceRelease(target);

		assertNull(registry.getOwner(target));
	}

	@Test
	@DisplayName("releaseByCop clears every target locked by that cop, leaving other cops' locks untouched")
	void releaseByCop_sweepsOnlyThatCopsLocks() {
		UUID otherTarget = UUID.randomUUID();
		registry.tryAcquire(target, copA);
		registry.tryAcquire(otherTarget, copB);

		registry.releaseByCop(copA);

		assertNull(registry.getOwner(target), "copA's lock must be cleared");
		assertEquals(copB, registry.getOwner(otherTarget), "copB's unrelated lock must survive");
	}

	@Test
	@DisplayName("null target or cop ids are rejected rather than throwing or corrupting the map")
	void nullSafety_acrossEveryMethod() {
		assertFalse(registry.tryAcquire(null, copA));
		assertFalse(registry.tryAcquire(target, null));
		assertFalse(registry.isOwner(null, copA));
		assertFalse(registry.isOwner(target, null));
		assertNull(registry.getOwner(null));

		// must not throw
		registry.release(null, copA);
		registry.release(target, null);
		registry.forceRelease(null);
		registry.releaseByCop(null);
	}

}
