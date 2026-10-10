package org.luckyraven.gangland.database.repositories.player;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.bootstrap.GanglandContext;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserFactory;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.DatabaseBackend;
import org.luckyraven.keystone.testkit.BukkitStatics;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collection;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * T-199 (impl half, users): the balance, counter and level columns may arrive as Long, BigDecimal or NULL. The hard
 * casts threw and failed the whole load; each numeric column now reads through {@code Number}, a NULL reads as zero.
 */
@DisplayName("UserRepository row safety - numeric columns read through Number (T-199)")
class UserRepositoryRowSafetyTest {

	private static final String UUID_TEXT = "6f1f3b2e-0000-4000-8000-000000000002";

	private BukkitStatics   bukkit;
	private DatabaseBackend backend;
	private UserFactory     factory;
	private User            user;
	private UserRepository  repository;

	@BeforeEach
	void setUp() {
		bukkit = BukkitStatics.install();

		Gangland        gangland = mock(Gangland.class);
		GanglandContext context  = mock(GanglandContext.class);
		factory = mock(UserFactory.class);
		user    = mock(User.class, RETURNS_DEEP_STUBS);
		when(gangland.getContext()).thenReturn(context);
		when(context.get(UserFactory.class)).thenReturn(factory);
		doReturn(user).when(factory).create(any());

		backend    = mock(DatabaseBackend.class);
		repository = new UserRepository(gangland, mock(DatabaseHandler.class), backend);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	private void rows(Object[]... rows) throws SQLException {
		doReturn(Arrays.asList(rows)).when(backend).query(anyString(), any());
	}

	@Test
	@DisplayName("Long and BigDecimal numeric columns load with their values")
	void widerNumericTypes_load() throws SQLException {
		rows(new Object[] {
				UUID_TEXT, new BigDecimal("12.5"), 2L, 3, 4L, new BigDecimal("0"), 1L, 2.0d, 0L, null
		});

		Collection<User<? extends org.bukkit.OfflinePlayer>> loaded = repository.doLoadAll();

		assertEquals(1, loaded.size());
		verify(user).setKills(2);
		verify(user).setDeaths(3);
		verify(user).setMobKills(4);
		verify(user.getWanted()).setLevel(0);
	}

	@Test
	@DisplayName("NULL numeric columns read as zero instead of throwing")
	void nullNumerics_readAsZero() throws SQLException {
		rows(new Object[] {
				UUID_TEXT, null, null, null, null, null, null, null, null, null
		});

		Collection<User<? extends org.bukkit.OfflinePlayer>> loaded = repository.doLoadAll();

		assertEquals(1, loaded.size());
		verify(user).setKills(0);
	}
}
