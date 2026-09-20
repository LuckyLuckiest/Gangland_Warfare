package org.luckyraven.gangland.gang.member;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.menu.filter.StandardFilterField;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Pins {@link MemberFilterAdapter}'s projection of {@link Member} onto {@link StandardFilterField} after its
 * WS2 G3a move from {@code gangland-domain} (G2) into {@code gangland-impl}'s {@code org.luckyraven.gangland.menu.filter}
 * package — the deferred test from plan §7 / review finding F2.
 */
class MemberFilterAdapterTest {

	private final MemberFilterAdapter adapter = new MemberFilterAdapter();

	@Test
	void name_isLowercasedOfflinePlayerName() {
		UUID uuid = UUID.randomUUID();
		Member member = new Member(uuid);
		OfflinePlayer offline = mock(OfflinePlayer.class);
		when(offline.getName()).thenReturn("SnitchKiller");

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getOfflinePlayer(uuid)).thenReturn(offline);

			assertEquals("snitchkiller", adapter.project(member, StandardFilterField.NAME));
		}
	}

	@Test
	void name_nullOfflineName_projectsEmptyString() {
		UUID uuid = UUID.randomUUID();
		Member member = new Member(uuid);
		OfflinePlayer offline = mock(OfflinePlayer.class);
		when(offline.getName()).thenReturn(null);

		try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
			bukkit.when(() -> Bukkit.getOfflinePlayer(uuid)).thenReturn(offline);

			assertEquals("", adapter.project(member, StandardFilterField.NAME));
		}
	}

	@Test
	void category_projectsRankName() {
		Member member = new Member(UUID.randomUUID());
		member.setRank(new Rank("Boss", 1));

		assertEquals("Boss", adapter.project(member, StandardFilterField.CATEGORY));
	}

	@Test
	void category_nullRank_projectsEmptyString() {
		Member member = new Member(UUID.randomUUID());

		assertEquals("", adapter.project(member, StandardFilterField.CATEGORY));
	}

	@Test
	void members_projectsContribution() {
		Member member = new Member(UUID.randomUUID());
		member.setContribution(42.5D);

		assertEquals(42.5D, adapter.project(member, StandardFilterField.MEMBERS));
	}

	@Test
	void date_projectsRawJoinEpochMillis() {
		Member member = new Member(UUID.randomUUID());
		member.setGangJoinDateLong(1_700_000_000_000L);

		assertEquals(1_700_000_000_000L, adapter.project(member, StandardFilterField.DATE));
	}

	@Test
	void unsupportedField_projectsNull() {
		Member member = new Member(UUID.randomUUID());

		assertNull(adapter.project(member, StandardFilterField.COLOR));
	}

	@Test
	void nullMemberOrNullField_projectsNull() {
		Member member = new Member(UUID.randomUUID());

		assertNull(adapter.project(null, StandardFilterField.NAME));
		assertNull(adapter.project(member, null));
	}

}
