package org.luckyraven.gangland.turf.capture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.support.CaptureFixtures;
import org.luckyraven.gangland.turf.support.FakeGangLookup;
import org.luckyraven.gangland.turf.support.FakeUserLookup;
import org.luckyraven.gangland.turf.support.InMemoryTurfRepository;
import org.luckyraven.gangland.turf.support.RecordingTurfSoundContract;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Proves two small pure-logic helpers on {@link CaptureService}:
 * <ul>
 *   <li>{@link CaptureService#isCapturable(Turf, long)} — cooldown / unclaimed / missing-owner / post-logoff-grace
 *       branches (public API, called directly).</li>
 *   <li>the private {@code dominantGang(Map, Integer)} tie/empty/single-entry helper, invoked via reflection since
 *       it has no public seam — matches the "CaptureService.dominantGang tie/empty/single-entry, and the dead
 *       exclude parameter" bullet in turf.md's Test Surface section.</li>
 * </ul>
 *
 * <p>{@link #dominantGang_excludeParameterWorksInIsolation()} pins Observation #11 (turf.md): the {@code exclude}
 * parameter is fully functional in isolation — it correctly removes a candidate from contention — but grep across
 * {@code CaptureService.java} shows both call sites ({@code tickIdle}'s initial-challenger pick and
 * {@code tickContestingUnclaimed}'s CLAIM→CONSOLIDATE election) always pass {@code null}. The bug is a dead call
 * site, not a broken method; this test documents the method working exactly as advertised.
 */
@DisplayName("CaptureService — isCapturable and dominantGang helpers")
class CaptureServiceHelpersTest {

	private FakeGangLookup gangs;
	private CaptureService service;

	@BeforeEach
	void setUp() {
		InMemoryTurfRepository     repository = new InMemoryTurfRepository();
		gangs = new FakeGangLookup();
		FakeUserLookup             users      = new FakeUserLookup();
		RecordingTurfSoundContract sounds     = new RecordingTurfSoundContract();
		CaptureSettings settings = CaptureFixtures.settings(180, 15, 15, 10, new int[]{25, 50, 75}, 90, 90);
		service = new CaptureService(new TurfManager(repository), gangs, users, settings, sounds);
	}

	private static Turf turf(Integer ownerGangId, long lastCaptureTimestamp) {
		return new Turf(1, "Turf", new CuboidRegion("world", 0, 0, 10, 10), ownerGangId, BigDecimal.TEN, 0L,
		                lastCaptureTimestamp);
	}

	@Test
	@DisplayName("isCapturable is false while still inside the cooldown window")
	void isCapturable_falseWithinCooldown() {
		long now  = 1_000_000L;
		Turf turf = turf(null, now - 60_000L); // captured 1 minute ago, cooldown is 15 minutes

		assertFalse(service.isCapturable(turf, now));
	}

	@Test
	@DisplayName("isCapturable is true for an unclaimed turf once cooldown has cleared")
	void isCapturable_trueForUnclaimedAfterCooldown() {
		long now  = 1_000_000L;
		Turf turf = turf(null, 0L);

		assertTrue(service.isCapturable(turf, now));
	}

	@Test
	@DisplayName("isCapturable is true for an owned turf whose owner gang record no longer exists")
	void isCapturable_trueWhenOwnerGangMissing() {
		long now  = 1_000_000L;
		Turf turf = turf(42, 0L); // gang 42 never registered in the lookup

		assertTrue(service.isCapturable(turf, now));
	}

	@Test
	@DisplayName("isCapturable is false while the owner's post-logoff protection grace is still active")
	void isCapturable_falseDuringPostLogoffGrace() {
		long now  = 1_000_000L;
		Gang owner = CaptureFixtures.gang(1);
		when(owner.getLastMemberOnlineAt()).thenReturn(now - 60_000L); // logged off 1 minute ago, grace is 10 min
		gangs.register(owner);
		Turf turf = turf(1, 0L);

		assertFalse(service.isCapturable(turf, now));
	}

	@Test
	@DisplayName("isCapturable is true once the owner's post-logoff protection grace has elapsed")
	void isCapturable_trueAfterPostLogoffGraceElapses() {
		long now  = 1_000_000L;
		Gang owner = CaptureFixtures.gang(1);
		when(owner.getLastMemberOnlineAt()).thenReturn(now - 700_000L); // logged off ~11.7 minutes ago
		gangs.register(owner);
		Turf turf = turf(1, 0L);

		assertTrue(service.isCapturable(turf, now));
	}

	private Integer dominantGang(Map<Integer, Integer> counts, Integer exclude) throws Exception {
		Method method = CaptureService.class.getDeclaredMethod("dominantGang", Map.class, Integer.class);
		method.setAccessible(true);
		return (Integer) method.invoke(service, counts, exclude);
	}

	@Test
	@DisplayName("dominantGang returns null for an empty count map")
	void dominantGang_emptyMapReturnsNull() throws Exception {
		assertNull(dominantGang(new LinkedHashMap<>(), null));
	}

	@Test
	@DisplayName("dominantGang returns the sole entry's key")
	void dominantGang_singleEntryReturnsThatKey() throws Exception {
		Map<Integer, Integer> counts = new LinkedHashMap<>();
		counts.put(7, 3);

		assertEquals(Integer.valueOf(7), dominantGang(counts, null));
	}

	@Test
	@DisplayName("dominantGang returns null on a tie at the top")
	void dominantGang_tieReturnsNull() throws Exception {
		Map<Integer, Integer> counts = new LinkedHashMap<>();
		counts.put(1, 5);
		counts.put(2, 5);

		assertNull(dominantGang(counts, null));
	}

	@Test
	@DisplayName("dominantGang returns the strictly-highest entry when one exists")
	void dominantGang_returnsStrictlyHighestEntry() throws Exception {
		Map<Integer, Integer> counts = new LinkedHashMap<>();
		counts.put(1, 2);
		counts.put(2, 5);
		counts.put(3, 1);

		assertEquals(Integer.valueOf(2), dominantGang(counts, null));
	}

	@Test
	@DisplayName("the exclude parameter works in isolation: it removes a candidate from contention")
	void dominantGang_excludeParameterWorksInIsolation() throws Exception {
		Map<Integer, Integer> counts = new LinkedHashMap<>();
		counts.put(1, 5);
		counts.put(2, 5); // tied with 1 -> null without exclusion

		assertNull(dominantGang(counts, null), "sanity: unexcluded, the tie still resolves to null");
		assertEquals(Integer.valueOf(2), dominantGang(counts, 1),
		            "excluding gang 1 leaves gang 2 as the sole (thus dominant) entry");
	}
}
