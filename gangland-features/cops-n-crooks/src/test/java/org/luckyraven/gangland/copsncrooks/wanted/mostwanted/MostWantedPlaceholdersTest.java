package org.luckyraven.gangland.copsncrooks.wanted.mostwanted;

import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** P-MW-5: the two scoreboard placeholders. */
@DisplayName("MostWantedPlaceholders")
class MostWantedPlaceholdersTest {

	private final OfflinePlayer who = mock(OfflinePlayer.class);

	@Test
	@DisplayName("a vacant title answers an empty name and 0 stars")
	void vacant() {
		MostWanted mw = mock(MostWanted.class);
		when(mw.holderName()).thenReturn(null);
		when(mw.holderStars()).thenReturn(0);
		MostWantedPlaceholders ph = new MostWantedPlaceholders(mw);

		assertEquals("", ph.resolve(who, "cnc_most_wanted"));
		assertEquals("0", ph.resolve(who, "cnc_most_wanted_stars"));
		assertEquals("", ph.resolveGlobal("cnc_most_wanted"));
		assertEquals("0", ph.resolveGlobal("cnc_most_wanted_stars"));
	}

	@Test
	@DisplayName("a holder answers his name and stars from both entry points")
	void holder() {
		MostWanted mw = mock(MostWanted.class);
		when(mw.holderName()).thenReturn("Steve");
		when(mw.holderStars()).thenReturn(5);
		MostWantedPlaceholders ph = new MostWantedPlaceholders(mw);

		assertEquals("Steve", ph.resolve(who, "cnc_most_wanted"));
		assertEquals("5", ph.resolve(who, "cnc_most_wanted_stars"));
		assertEquals("Steve", ph.resolveGlobal("cnc_most_wanted"));
		assertEquals("5", ph.resolveGlobal("cnc_most_wanted_stars"));
	}

	@Test
	@DisplayName("an unknown token is null, so another contribution can answer")
	void unknownToken() {
		MostWanted mw = mock(MostWanted.class);
		when(mw.holderName()).thenReturn("Steve");
		MostWantedPlaceholders ph = new MostWantedPlaceholders(mw);

		assertEquals("Steve", ph.resolveGlobal("cnc_most_wanted"));
		assertNull(ph.resolve(who, "cnc_something_else"));
		assertNull(ph.resolveGlobal("cnc_something_else"));
		assertNull(ph.resolve(who, "cnc_most_wanted_extra"));
	}
}
