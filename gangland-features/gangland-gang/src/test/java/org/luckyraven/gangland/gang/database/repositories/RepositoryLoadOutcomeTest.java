package org.luckyraven.gangland.gang.database.repositories;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.gang.contract.LoadOutcome;
import org.luckyraven.gangland.gang.contract.RankLookupContract;
import org.luckyraven.gangland.gang.database.repositories.gang.GangRepository;
import org.luckyraven.gangland.gang.database.repositories.member.MemberRepository;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.DatabaseBackend;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * A failed gang/member read is reported through {@link LoadOutcome} instead of looking like an empty table (T-249), and
 * a member row with a NULL or malformed uuid is skipped rather than failing the whole load (T-262).
 */
@DisplayName("Gang and member repositories - failed loads are reported; a bad member uuid is skipped")
class RepositoryLoadOutcomeTest {

	private BukkitStatics  bukkit;
	private DatabaseBackend backend;

	@BeforeEach
	void setUp() {
		bukkit  = BukkitStatics.install();
		backend = mock(DatabaseBackend.class);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	private void rows(Object[]... rows) throws SQLException {
		doReturn(Arrays.asList(rows)).when(backend).query(anyString(), any());
	}

	@Test
	@DisplayName("gangRepository_failedLoad_isReported: a failed read is flagged, then cleared by a good read")
	void gangRepository_failedLoad_isReported() throws SQLException {
		GangRepository repository = new GangRepository(mock(JavaPlugin.class), mock(DatabaseHandler.class), backend);
		doThrow(new SQLException("busy")).when(backend).query(anyString(), any());

		assertTrue(repository.loadAll().isEmpty());
		assertTrue(((LoadOutcome) repository).lastLoadFailed());

		rows();

		assertTrue(repository.loadAll().isEmpty());
		assertFalse(((LoadOutcome) repository).lastLoadFailed());
	}

	@Test
	@DisplayName("memberRepository_failedLoad_isReported: a failed read is flagged, then cleared by a good read")
	void memberRepository_failedLoad_isReported() throws SQLException {
		MemberRepository repository = new MemberRepository(mock(JavaPlugin.class), mock(DatabaseHandler.class), backend);
		repository.setRankLookup(mock(RankLookupContract.class));
		repository.setGangLookup(mock(GangLookupContract.class));
		doThrow(new SQLException("busy")).when(backend).query(anyString(), any());

		assertTrue(repository.loadAll().isEmpty());
		assertTrue(((LoadOutcome) repository).lastLoadFailed());

		rows();

		assertTrue(repository.loadAll().isEmpty());
		assertFalse(((LoadOutcome) repository).lastLoadFailed());
	}

	@Test
	@DisplayName("T-262: a member row with a NULL or malformed uuid is skipped; the good row still loads")
	void memberRepository_badUuidRow_isSkipped() throws SQLException {
		MemberRepository repository = new MemberRepository(mock(JavaPlugin.class), mock(DatabaseHandler.class), backend);
		repository.setRankLookup(mock(RankLookupContract.class));
		repository.setGangLookup(mock(GangLookupContract.class));
		rows(new Object[] { "null", -1, 0.0, 1, 0L },
		     new Object[] { "00000000-0000-0000-0000-000000000001", -1, 0.0, 1, 0L });

		Collection<Member> loaded = repository.loadAll();

		assertEquals(1, loaded.size());
		assertEquals("00000000-0000-0000-0000-000000000001", loaded.iterator().next().getUuid().toString());
	}

}
