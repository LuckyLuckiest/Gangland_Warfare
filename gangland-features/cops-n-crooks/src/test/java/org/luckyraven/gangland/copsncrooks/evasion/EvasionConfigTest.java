package org.luckyraven.gangland.copsncrooks.evasion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 0.12 F2: {@code Wanted.Evasion} values. Per-star lists are indexed by {@code level - 1} and clamped to the list, empty
 * lists fall back to the shipped values, and an unknown {@code Drop_Mode} means {@code ONE_STAR}.
 */
@DisplayName("EvasionConfig")
class EvasionConfigTest {

	@Test
	@DisplayName("defaults match the 0.12 spec")
	void defaults_matchSpec() {
		EvasionConfig config = EvasionConfig.defaults();

		assertTrue(config.enabled());
		assertEquals(3, config.lostSightSeconds());
		assertEquals(EvasionDropMode.ONE_STAR, config.dropMode());
		assertEquals(List.of(40, 60, 90, 130, 180), config.searchRadius());
		assertEquals(List.of(10, 20, 30, 45, 60), config.secondsToDrop());
		assertEquals(2.0, config.outsideZoneSpeed());
		assertEquals(1.5, config.hideoutSpeed());
	}

	@Test
	@DisplayName("per-star values use index level - 1, clamped to the list")
	void perStar_clampsIndex() {
		EvasionConfig config = EvasionConfig.defaults();

		assertEquals(40, config.radiusFor(1));
		assertEquals(90, config.radiusFor(3));
		assertEquals(180, config.radiusFor(5));
		assertEquals(180, config.radiusFor(9), "above the list reuses the last entry");
		assertEquals(40, config.radiusFor(0), "below one star reuses the first entry");
		assertEquals(10, config.secondsToDropFor(1));
		assertEquals(45, config.secondsToDropFor(4));
		assertEquals(60, config.secondsToDropFor(6));
	}

	@Test
	@DisplayName("empty lists fall back to the shipped values; values and Lost_Sight_Seconds are at least 1")
	void normalizes() {
		EvasionConfig config = new EvasionConfig(true, 0, null, Collections.emptyList(), List.of(0, -5, 7), -1D,
		                                         -1D);

		assertEquals(1, config.lostSightSeconds());
		assertEquals(EvasionDropMode.ONE_STAR, config.dropMode());
		assertEquals(EvasionConfig.DEFAULT_SEARCH_RADIUS, config.searchRadius());
		assertEquals(List.of(1, 1, 7), config.secondsToDrop());
		assertEquals(0D, config.outsideZoneSpeed());
		assertEquals(0D, config.hideoutSpeed());
	}

	@Test
	@DisplayName("Drop_Mode parses case-insensitively and falls back to ONE_STAR")
	void dropMode_parse() {
		assertEquals(EvasionDropMode.ONE_STAR, EvasionDropMode.parse("ONE_STAR"));
		assertEquals(EvasionDropMode.ALL_STARS, EvasionDropMode.parse("all_stars"));
		assertEquals(EvasionDropMode.ALL_STARS, EvasionDropMode.parse(" All-Stars "));
		assertEquals(EvasionDropMode.ONE_STAR, EvasionDropMode.parse("everything"));
		assertEquals(EvasionDropMode.ONE_STAR, EvasionDropMode.parse(null));
	}
}
