package org.luckyraven.gangland.turf.capture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.support.CaptureFixtures;
import org.luckyraven.gangland.turf.support.FakeGangLookup;
import org.luckyraven.gangland.turf.support.FakeUserLookup;
import org.luckyraven.gangland.turf.support.InMemoryTurfRepository;
import org.luckyraven.gangland.turf.support.RecordingTurfSoundContract;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Characterization of {@link CaptureService#isCapturable(Turf, long)} pinned BEFORE the verdict is extracted into
 * {@link CaptureEligibility} (W1-K step 1). Every row must pass on the pre-extraction code and stay green after it.
 * Cooldown C = 15 min (900 000 ms), post-logoff grace P = 10 min (600 000 ms).
 */
@DisplayName("CaptureService.isCapturable - characterization before extraction")
class CaptureEligibilityCharacterizationTest {

	static final long NOW = 100_000_000L;
	static final long C   = 900_000L;
	static final long P   = 600_000L;

	private FakeGangLookup gangs;
	private CaptureService service;

	@BeforeEach
	void setUp() {
		InMemoryTurfRepository     repository = new InMemoryTurfRepository();
		gangs = new FakeGangLookup();
		FakeUserLookup             users      = new FakeUserLookup();
		RecordingTurfSoundContract sounds     = new RecordingTurfSoundContract();
		CaptureSettings settings =
				CaptureFixtures.settings(180, 15, 15, 10, new int[]{25, 50, 75}, 90, 90);
		TurfManager turfs = new TurfManager(repository);
		service = new CaptureService(turfs, gangs, users, settings, sounds, new TurfOwnership(turfs));
	}

	static Stream<Arguments> rows() {
		return Stream.of(
				Arguments.of("inside cooldown", null, false, 0L, NOW - 60_000L, false),
				Arguments.of("cooldown exact end", null, false, 0L, NOW - C, true),
				Arguments.of("one ms before cooldown end", null, false, 0L, NOW - C + 1, false),
				Arguments.of("never captured, unclaimed", null, false, 0L, 0L, true),
				Arguments.of("owner gang missing", 7, false, 0L, 0L, true),
				Arguments.of("owners online now", 7, true, NOW, 0L, false),
				Arguments.of("grace exact", 7, true, NOW - P, 0L, false),
				Arguments.of("grace plus one ms", 7, true, NOW - P - 1, 0L, true),
				Arguments.of("owner never seen", 7, true, 0L, 0L, true),
				Arguments.of("cooldown beats owners offline", 7, true, 0L, NOW - 60_000L, false)
		);
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("rows")
	@DisplayName("isCapturable matches the characterized verdict")
	void isCapturable_matchesCharacterization(String description, Integer ownerGangId, boolean ownerExists,
	                                          long lastOnline, long lastCapture, boolean expected) {
		if (ownerExists) {
			var owner = CaptureFixtures.gang(ownerGangId);
			when(owner.getLastMemberOnlineAt()).thenReturn(lastOnline);
			gangs.register(owner);
		}
		Turf turf = new Turf(1, "Turf", new CuboidRegion("world", 0, 0, 10, 10), ownerGangId, BigDecimal.TEN, 0L,
		                     lastCapture);

		assertEquals(expected, service.isCapturable(turf, NOW), description);
	}
}
