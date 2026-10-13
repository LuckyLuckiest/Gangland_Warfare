package org.luckyraven.gangland.gang;

import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.core.support.FakeIdentitySettingsContract;
import org.luckyraven.gangland.core.user.IdentitySettings;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.gang.contract.RankLookupContract;
import org.luckyraven.gangland.gang.database.fk.ForeignUserTable;
import org.luckyraven.gangland.gang.database.repositories.gang.GangAllianceRepository;
import org.luckyraven.gangland.gang.database.repositories.gang.GangRepository;
import org.luckyraven.gangland.gang.database.repositories.member.MemberRepository;
import org.luckyraven.gangland.gang.database.tables.gang.GangAllianceTable;
import org.luckyraven.gangland.gang.database.tables.gang.GangTable;
import org.luckyraven.gangland.gang.database.tables.member.MemberTable;
import org.luckyraven.gangland.gang.database.tables.rank.RankTable;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.support.FakeGangSettingsContract;
import org.luckyraven.keystone.persistence.database.DatabaseHandler;
import org.luckyraven.keystone.persistence.database.backend.SqliteBackend;
import org.luckyraven.keystone.persistence.database.schema.TableSchemas;
import org.luckyraven.keystone.testkit.DbFiles;
import org.luckyraven.keystone.testkit.PluginMocks;
import org.luckyraven.keystone.testkit.SqliteDbs;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * The 0.19.0 entity columns survive a save and a load through a real SQLite {@code TableBackend}: {@code gang.notice},
 * {@code gang.friendly_fire}, {@code gang_ally.friendly_fire} and {@code member.kills/deaths/assists}. Defaults keep
 * today's behaviour (no notice, friendly fire off, zero stats).
 */
@DisplayName("Gang parity columns - round trip")
class GangColumnRoundTripTest {

	@TempDir(cleanup = CleanupMode.NEVER)   // Windows: Hikari holds the .db handle past the test
	Path tempDir;

	private SqliteBackend backend;
	private JavaPlugin    plugin;

	@BeforeEach
	void setUp() throws SQLException {
		GangSettings.bind(new FakeGangSettingsContract());
		IdentitySettings.bind(new FakeIdentitySettingsContract());

		backend = new SqliteBackend();
		backend.connect(SqliteDbs.file(tempDir.resolve("columns.db")));
		backend.applySchema(TableSchemas.fromTable(new GangTable()));
		backend.applySchema(TableSchemas.fromTable(new GangAllianceTable(new GangTable())));
		backend.applySchema(TableSchemas.fromTable(new MemberTable(new ForeignUserTable(), new RankTable())));

		plugin = PluginMocks.plugin(tempDir);
	}

	@AfterEach
	void tearDown() {
		backend.disconnect();
		DbFiles.release(tempDir);
	}

	private GangRepository gangs() {
		return new GangRepository(plugin, mock(DatabaseHandler.class), backend);
	}

	private Gang loadedGang(int id) {
		Collection<Gang> loaded = gangs().loadAll();
		return loaded.stream().filter(gang -> gang.getId() == id).findFirst().orElseThrow();
	}

	@Test
	@DisplayName("existingTablesGainTheColumns_andOldRowsReadAsDefaults")
	void existingTablesGainTheColumns_andOldRowsReadAsDefaults() throws Exception {
		backend.execute("DROP TABLE IF EXISTS member");
		backend.execute("DROP TABLE IF EXISTS gang_ally");
		backend.execute("DROP TABLE IF EXISTS gang");
		backend.execute("CREATE TABLE gang (id INTEGER PRIMARY KEY, name TEXT NOT NULL, display_name TEXT NOT NULL, "
		                + "description TEXT NOT NULL, color TEXT NOT NULL, balance REAL DEFAULT 0, level INTEGER "
		                + "DEFAULT 0, experience REAL DEFAULT 0, bounty REAL DEFAULT 0, created INTEGER NOT NULL, "
		                + "last_member_online_at INTEGER DEFAULT 0, state TEXT DEFAULT 'INVITE')");
		backend.execute("INSERT INTO gang (id, name, display_name, description, color, created) "
		                + "VALUES (9, 'Old', '', 'desc', 'RED', 1)");

		backend.applySchema(TableSchemas.fromTable(new GangTable()));

		Gang gang = loadedGang(9);
		assertNull(gang.getNotice());
		assertFalse(gang.isFriendlyFire());
	}

	@Test
	@DisplayName("notice")
	void notice() {
		Gang gang = new Gang(1);
		gang.setName("Rats");
		assertNull(gang.getNotice(), "default is no notice");
		gangs().save(gang);
		assertNull(loadedGang(1).getNotice());

		gang.setNotice("Meet at the docks");
		gangs().save(gang);

		assertEquals("Meet at the docks", loadedGang(1).getNotice());
	}

	@Test
	@DisplayName("friendlyFire")
	void friendlyFire() {
		Gang gang = new Gang(2);
		gang.setName("Mice");
		assertFalse(gang.isFriendlyFire(), "default is off");
		gangs().save(gang);
		assertFalse(loadedGang(2).isFriendlyFire());

		gang.setFriendlyFire(true);
		gangs().save(gang);

		assertTrue(loadedGang(2).isFriendlyFire());
	}

	@Test
	@DisplayName("allyFriendlyFire")
	void allyFriendlyFire() {
		Gang alpha = new Gang(1);
		Gang bravo = new Gang(2);
		GangAllianceRepository repository = new GangAllianceRepository(plugin, mock(DatabaseHandler.class), backend);
		Map<Integer, Gang> byId = new HashMap<>(Map.of(1, alpha, 2, bravo));
		repository.setGangLookup(new GangLookupContract() {
			@Override
			public @Nullable Gang findById(int gangId) {
				return byId.get(gangId);
			}

			@Override
			public Collection<Gang> getAll() {
				return byId.values();
			}
		});

		GangAlliance off = new GangAlliance(alpha, bravo, 100L);
		GangAlliance on  = new GangAlliance(bravo, alpha, 100L);
		assertFalse(off.isFriendlyFire(), "default is off");
		on.setFriendlyFire(true);
		repository.saveAll(java.util.List.of(off, on));

		Collection<GangAlliance> loaded = repository.loadAll();

		assertEquals(2, loaded.size());
		assertFalse(loaded.stream().filter(a -> a.gang().getId() == 1).findFirst().orElseThrow().isFriendlyFire(),
		            "each direction row keeps its own flag");
		assertTrue(loaded.stream().filter(a -> a.gang().getId() == 2).findFirst().orElseThrow().isFriendlyFire());
	}

	@Test
	@DisplayName("killsDeathsAssists")
	void killsDeathsAssists() {
		MemberRepository repository = new MemberRepository(plugin, mock(DatabaseHandler.class), backend);
		repository.setRankLookup(mock(RankLookupContract.class));
		repository.setGangLookup(new GangLookupContract() {
			@Override
			public @Nullable Gang findById(int gangId) {
				return null;
			}

			@Override
			public Collection<Gang> getAll() {
				return java.util.List.of();
			}
		});

		UUID   uuid   = UUID.randomUUID();
		Member member = new Member(uuid);
		assertEquals(0, member.getKills());
		assertEquals(0, member.getDeaths());
		assertEquals(0, member.getAssists());
		repository.save(member);
		Member fresh = repository.loadAll().stream().filter(m -> m.getUuid().equals(uuid)).findFirst().orElseThrow();
		assertEquals(0, fresh.getKills());

		member.setKills(12);
		member.setDeaths(5);
		member.setAssists(3);
		repository.save(member);

		Member loaded = repository.loadAll().stream().filter(m -> m.getUuid().equals(uuid)).findFirst().orElseThrow();
		assertEquals(12, loaded.getKills());
		assertEquals(5, loaded.getDeaths());
		assertEquals(3, loaded.getAssists());
	}

	@Test
	@DisplayName("oldRowsWithoutTheColumns_readAsDefaults")
	void oldRowsWithoutTheColumns_readAsDefaults() {
		Object[] old = {1, "Rats", "", "desc", "RED", 0D, 0, 0D, 0D, 1L, 0L, "OPEN"};

		Gang gang = GangRepository.fromRow(old);

		assertNull(gang.getNotice());
		assertFalse(gang.isFriendlyFire());
		assertEquals(Gang.State.OPEN, gang.getState());
	}
}
