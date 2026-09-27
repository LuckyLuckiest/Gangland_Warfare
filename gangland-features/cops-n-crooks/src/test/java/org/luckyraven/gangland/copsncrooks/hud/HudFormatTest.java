package org.luckyraven.gangland.copsncrooks.hud;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 0.12 F3: pure HUD string formatting — the flashing star string, the {@code m:ss} clock and applying a
 * boss-bar/title template's {@code %stars%}/{@code %time%} placeholders.
 */
@DisplayName("HudFormat")
class HudFormatTest {

	@Test
	@DisplayName("stars renders red on a flash-on tick, grey on a flash-off tick")
	void stars_flashes() {
		assertEquals("&c★★★☆☆", HudFormat.stars(3, 5, false));
		assertEquals("&7★★★☆☆", HudFormat.stars(3, 5, true));
	}

	@Test
	@DisplayName("time formats m:ss, floored to the whole second")
	void time_formatsMinutesSeconds() {
		assertEquals("0:00", HudFormat.time(0D));
		assertEquals("0:05", HudFormat.time(5.9D));
		assertEquals("1:00", HudFormat.time(60D));
		assertEquals("2:03", HudFormat.time(123D));
		assertEquals("0:00", HudFormat.time(-5D), "negative input floors to 0:00");
	}

	@Test
	@DisplayName("apply replaces %stars% and %time%, leaving other text untouched")
	void apply_replacesPlaceholders() {
		String result = HudFormat.apply("%stars% &e&lSEARCHING %time%", "&c★★☆☆☆", "0:12");

		assertEquals("&c★★☆☆☆ &e&lSEARCHING 0:12", result);
	}

	@Test
	@DisplayName("apply is a no-op for a template without placeholders")
	void apply_noPlaceholders_isNoOp() {
		assertEquals("&c&lWANTED", HudFormat.apply("&c&lWANTED", "&c★★★★★", "0:00"));
	}
}
