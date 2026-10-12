package org.luckyraven.gangland.gang.database.repositories;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.permission.Permission;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.RankLookupContract;
import org.luckyraven.gangland.gang.database.repositories.gang.GangAllianceRepository;
import org.luckyraven.gangland.gang.database.repositories.gang.GangRepository;
import org.luckyraven.gangland.gang.database.repositories.member.MemberRepository;
import org.luckyraven.gangland.gang.database.repositories.permission.PermissionRepository;
import org.luckyraven.gangland.gang.database.repositories.rank.RankParentRepository;
import org.luckyraven.gangland.gang.database.repositories.rank.RankPermissionRepository;
import org.luckyraven.gangland.gang.database.repositories.rank.RankRepository;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.rank.RankParent;
import org.luckyraven.gangland.gang.rank.RankPermission;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * T-199 (gang half): a row read through MySQL can hand back a {@code Long} where the code expected an {@code Integer},
 * a {@code BigDecimal} for a double, or a SQL NULL. Every numeric column is read through {@link Number}, NULL reads as
 * 0, and no repository throws a {@code ClassCastException} or an NPE on the row.
 */
@DisplayName("Gang repositories - numeric row columns read through Number, NULL as 0 (T-199)")
class RowNumberSafetyTest {

	private static final UUID UUID_ONE = UUID.fromString("00000000-0000-0000-0000-000000000001");

	@Test
	@DisplayName("gang row with BIGINT drift reads id, level and balance without a cast")
	void gangRow_longColumns_read() {
		Object[] row = {7L, "Hood", "Hood", "Desc", "RED", new BigDecimal("12.5"), 3L, 20.0D, 0.0D, 99L, 1000L, "OPEN"};

		Gang gang = GangRepository.fromRow(row);

		assertEquals(7, gang.getId());
		assertEquals(3, gang.getLevel().getLevelValue());
	}

	@Test
	@DisplayName("gang row with NULL numeric columns reads as zero, not an NPE")
	void gangRow_nullNumerics_readAsZero() {
		Object[] row = {null, null, null, null, null, null, null, null, null, null, null, null};

		Gang gang = GangRepository.fromRow(row);

		assertEquals(0, gang.getId());
		assertEquals(0, gang.getLevel().getLevelValue());
	}

	@Test
	@DisplayName("member row with a BIGINT gang id, DECIMAL contribution and BIGINT rank id reads cleanly")
	void memberRow_longAndDecimalColumns_read() {
		RankLookupContract ranks = mock(RankLookupContract.class);
		Rank               rank  = new Rank("member", 9);
		when(ranks.get(9)).thenReturn(rank);

		Member member = MemberRepository.fromRow(new Object[]{UUID_ONE.toString(), 4L, new BigDecimal("2.5"), 9L,
		                                                      1234L}, ranks);

		assertEquals(4, member.getGangId());
		assertEquals(2.5D, member.getContribution());
		assertEquals(1234L, member.getGangJoinDateLong());
		assertEquals(rank, member.getRank());
	}

	@Test
	@DisplayName("member row with NULL numeric columns reads as zero and falls back to the root rank")
	void memberRow_nullNumerics_readAsZero() {
		RankLookupContract ranks = mock(RankLookupContract.class);

		Member member = MemberRepository.fromRow(new Object[]{UUID_ONE.toString(), null, null, null, null}, ranks);

		assertEquals(0, member.getGangId());
		assertEquals(0D, member.getContribution());
		assertNull(member.getRank());
	}

	@Test
	@DisplayName("alliance row with BIGINT ids and timestamp reads as longs and ints")
	void allianceRow_longColumns_read() {
		GangAllianceRepository.AllianceRow row = GangAllianceRepository.fromRow(new Object[]{4L, 5L, 777L});

		assertEquals(4, row.gangId());
		assertEquals(5, row.allyId());
		assertEquals(777L, row.since());
	}

	@Test
	@DisplayName("alliance row with NULL columns reads as zero")
	void allianceRow_nulls_readAsZero() {
		GangAllianceRepository.AllianceRow row = GangAllianceRepository.fromRow(new Object[]{null, null, null});

		assertEquals(0, row.gangId());
		assertEquals(0, row.allyId());
		assertEquals(0L, row.since());
	}

	@Test
	@DisplayName("permission row with a BIGINT id reads as an int id")
	void permissionRow_longId_read() {
		Permission permission = PermissionRepository.fromRow(new Object[]{3L, "gang.x"});

		assertEquals(3, permission.getUsedId());
		assertEquals("gang.x", permission.getPermission());
	}

	@Test
	@DisplayName("rank row with a BIGINT id reads as an int id")
	void rankRow_longId_read() {
		Rank rank = RankRepository.fromRow(new Object[]{5L, "owner", null});

		assertEquals(5, rank.getUsedId());
		assertEquals("owner", rank.getName());
	}

	@Test
	@DisplayName("rank parent row with BIGINT ids reads as ints")
	void rankParentRow_longIds_read() {
		assertEquals(new RankParent(2, 3), RankParentRepository.fromRow(new Object[]{2L, 3L}));
	}

	@Test
	@DisplayName("rank permission row with BIGINT ids reads as ints")
	void rankPermissionRow_longIds_read() {
		assertEquals(new RankPermission(2, 4), RankPermissionRepository.fromRow(new Object[]{2L, 4L}));
	}

}
