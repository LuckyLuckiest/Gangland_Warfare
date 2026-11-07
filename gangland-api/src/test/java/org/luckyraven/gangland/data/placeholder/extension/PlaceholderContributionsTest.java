package org.luckyraven.gangland.data.placeholder.extension;

import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("PlaceholderContributions - first non-null answer in registration order")
class PlaceholderContributionsTest {

	private final OfflinePlayer player = mock(OfflinePlayer.class);

	private static PlaceholderContributions of(PlaceholderContribution... contributions) {
		return new PlaceholderContributions(List.of(contributions));
	}

	@Test
	@DisplayName("resolveSetting: the first non-null answer wins and later contributions are not asked")
	void resolveSetting_firstNonNullWins() {
		PlaceholderContribution skipped = mock(PlaceholderContribution.class);
		PlaceholderContribution winner  = mock(PlaceholderContribution.class);
		PlaceholderContribution later   = mock(PlaceholderContribution.class);
		when(winner.resolveSetting("key")).thenReturn("first");
		when(later.resolveSetting("key")).thenReturn("second");

		assertEquals("first", of(skipped, winner, later).resolveSetting("key"));

		InOrder order = inOrder(skipped, winner);
		order.verify(skipped).resolveSetting("key");
		order.verify(winner).resolveSetting("key");
		verify(later, never()).resolveSetting("key");
	}

	@Test
	@DisplayName("resolveSetting: null when nobody owns it, and with no contributions at all")
	void resolveSetting_noAnswer_isNull() {
		assertNull(of(mock(PlaceholderContribution.class), mock(PlaceholderContribution.class)).resolveSetting("key"));
		assertNull(PlaceholderContributions.none().resolveSetting("key"));
	}

	@Test
	@DisplayName("resolveSetting: an empty string is an answer and stops the search")
	void resolveSetting_emptyStringIsAnAnswer() {
		PlaceholderContribution empty = mock(PlaceholderContribution.class);
		PlaceholderContribution later = mock(PlaceholderContribution.class);
		when(empty.resolveSetting("key")).thenReturn("");
		when(later.resolveSetting("key")).thenReturn("second");

		assertEquals("", of(empty, later).resolveSetting("key"));
		verify(later, never()).resolveSetting("key");
	}

	@Test
	@DisplayName("resolveSetting: an exception from a contribution propagates and stops the search")
	void resolveSetting_exceptionPropagates() {
		PlaceholderContribution broken = mock(PlaceholderContribution.class);
		PlaceholderContribution later  = mock(PlaceholderContribution.class);
		when(broken.resolveSetting("key")).thenThrow(new IllegalStateException("boom"));

		assertThrows(IllegalStateException.class, () -> of(broken, later).resolveSetting("key"));
		verify(later, never()).resolveSetting("key");
	}

	@Test
	@DisplayName("resolveGlobal: the first non-null answer wins and later contributions are not asked")
	void resolveGlobal_firstNonNullWins() {
		PlaceholderContribution skipped = mock(PlaceholderContribution.class);
		PlaceholderContribution winner  = mock(PlaceholderContribution.class);
		PlaceholderContribution later   = mock(PlaceholderContribution.class);
		when(winner.resolveGlobal("token")).thenReturn("first");
		when(later.resolveGlobal("token")).thenReturn("second");

		assertEquals("first", of(skipped, winner, later).resolveGlobal("token"));

		InOrder order = inOrder(skipped, winner);
		order.verify(skipped).resolveGlobal("token");
		order.verify(winner).resolveGlobal("token");
		verify(later, never()).resolveGlobal("token");
	}

	@Test
	@DisplayName("resolveGlobal: null when nobody owns it, and with no contributions at all")
	void resolveGlobal_noAnswer_isNull() {
		assertNull(of(mock(PlaceholderContribution.class)).resolveGlobal("token"));
		assertNull(PlaceholderContributions.none().resolveGlobal("token"));
	}

	@Test
	@DisplayName("resolveGlobal: an exception from a contribution propagates and stops the search")
	void resolveGlobal_exceptionPropagates() {
		PlaceholderContribution broken = mock(PlaceholderContribution.class);
		PlaceholderContribution later  = mock(PlaceholderContribution.class);
		when(broken.resolveGlobal("token")).thenThrow(new IllegalStateException("boom"));

		assertThrows(IllegalStateException.class, () -> of(broken, later).resolveGlobal("token"));
		verify(later, never()).resolveGlobal("token");
	}

	@Test
	@DisplayName("resolve: the first non-null answer wins and later contributions are not asked")
	void resolve_firstNonNullWins() {
		PlaceholderContribution skipped = mock(PlaceholderContribution.class);
		PlaceholderContribution winner  = mock(PlaceholderContribution.class);
		PlaceholderContribution later   = mock(PlaceholderContribution.class);
		when(winner.resolve(player, "param")).thenReturn("first");
		when(later.resolve(player, "param")).thenReturn("second");

		assertEquals("first", of(skipped, winner, later).resolve(player, "param"));

		InOrder order = inOrder(skipped, winner);
		order.verify(skipped).resolve(player, "param");
		order.verify(winner).resolve(player, "param");
		verify(later, never()).resolve(player, "param");
	}

	@Test
	@DisplayName("resolve: null when nobody owns it, with a null player passed through, and with no contributions")
	void resolve_noAnswer_isNull() {
		PlaceholderContribution only = mock(PlaceholderContribution.class);

		assertNull(of(only).resolve(null, "param"));
		verify(only).resolve(null, "param");
		assertNull(PlaceholderContributions.none().resolve(player, "param"));
	}

	@Test
	@DisplayName("resolve: an exception from a contribution propagates and stops the search")
	void resolve_exceptionPropagates() {
		PlaceholderContribution broken = mock(PlaceholderContribution.class);
		PlaceholderContribution later  = mock(PlaceholderContribution.class);
		when(broken.resolve(player, "param")).thenThrow(new IllegalStateException("boom"));

		assertThrows(IllegalStateException.class, () -> of(broken, later).resolve(player, "param"));
		verify(later, never()).resolve(player, "param");
	}

	@Test
	@DisplayName("the three lookups are independent: a resolveSetting answer does not satisfy resolve or resolveGlobal")
	void lookups_areIndependent() {
		PlaceholderContribution settingOnly = mock(PlaceholderContribution.class);
		when(settingOnly.resolveSetting("x")).thenReturn("setting");

		PlaceholderContributions contributions = of(settingOnly);

		assertEquals("setting", contributions.resolveSetting("x"));
		assertNull(contributions.resolveGlobal("x"));
		assertNull(contributions.resolve(player, "x"));
	}
}
