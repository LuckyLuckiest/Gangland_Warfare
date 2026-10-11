package org.luckyraven.gangland.gang.level;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.gang.GangLevels;
import org.luckyraven.gangland.gang.file.GangRules;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.testkit.PluginMocks;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The 15.6 level table read from the shipped gang_rules.yml, and how a missing row, a 0 and a reload behave. */
@DisplayName("GangLevelTable - Levels rows of gang_rules.yml")
class GangLevelTableTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	@AfterEach
	void restoreLevelDefaults() {
		GangLevels.bind(20, 200);
	}

	private GangRules load(String yaml) throws IOException {
		Path file = tempDir.resolve("gang_rules.yml");
		Files.writeString(file, yaml, StandardCharsets.UTF_8);
		JavaPlugin  plugin      = PluginMocks.plugin(tempDir);
		FileManager fileManager = new FileManager(plugin);
		fileManager.addFile(new FileHandler(plugin, file.toFile()), false);
		return new GangRules(fileManager);
	}

	private GangRules shipped() throws IOException {
		try (InputStream in = GangLevelTableTest.class.getResourceAsStream("/gang/gang_rules.yml")) {
			return load(new String(in.readAllBytes(), StandardCharsets.UTF_8));
		}
	}

	private static void assertRow(GangLevelTable.Row row, int level, double cost, int members, int safehouses,
	                              int allies, String prefix) {
		assertEquals(level, row.level());
		assertEquals(cost, row.cost(), 1e-9, "cost of level " + level);
		assertEquals(members, row.maxMembers(), "members of level " + level);
		assertEquals(safehouses, row.maxSafehouses(), "safehouses of level " + level);
		assertEquals(allies, row.maxAllies(), "allies of level " + level);
		assertEquals(prefix, row.chatPrefix(), "prefix of level " + level);
	}

	@Test
	@DisplayName("rowsRead: rows 0-5, 6, 10 and 20 carry the 15.6 values")
	void rowsRead() throws IOException {
		GangLevelTable table = new GangLevelTable(shipped()::getLevelRows);

		assertRow(table.row(0), 0, 0, 8, 1, 1, "");
		assertRow(table.row(1), 1, 20000, 10, 1, 1, "&8[&71&8]");
		assertRow(table.row(2), 2, 40000, 12, 2, 2, "&8[&72&8]");
		assertRow(table.row(3), 3, 75000, 14, 2, 2, "&8[&a3&8]");
		assertRow(table.row(4), 4, 120000, 16, 3, 2, "&8[&a4&8]");
		assertRow(table.row(5), 5, 180000, 18, 3, 3, "&8[&b5&8]");
		assertRow(table.row(6), 6, 230000, 19, 3, 3, "&8[&b6&8]");
		assertRow(table.row(10), 10, 455000, 24, 5, 3, "&8[&610&8]");
		assertRow(table.row(20), 20, 1205000, 29, 5, 3, "&8[&620&8]");
	}

	@Test
	@DisplayName("missingRowInherits: a level with no row keeps the limits, cost and prefix of the row before, with no commands")
	void missingRowInherits() throws IOException {
		GangRules rules = load("""
				Levels:
				   Level_0:
				      Max_Members: 8
				      Max_Allies: 1
				   Level_2:
				      Cost: 5000
				      Max_Members: 12
				      Max_Allies: 2
				      Chat_Prefix: "[2]"
				      Commands:
				         - "say hi"
				""");
		GangLevelTable table = new GangLevelTable(rules::getLevelRows);

		assertRow(table.row(1), 1, 0, 8, 0, 1, "");
		assertRow(table.row(3), 3, 5000, 12, 0, 2, "[2]");
		assertEquals(List.of("say hi"), table.row(2).commands());
		assertEquals(List.of(), table.row(3).commands());
		assertEquals(List.of(), table.row(1).commands());
	}

	@Test
	@DisplayName("zeroIsUnlimited: a 0 limit stays 0 and a gang table with no rows answers all zero")
	void zeroIsUnlimited() throws IOException {
		GangLevelTable empty = new GangLevelTable(Map::of);
		assertRow(empty.row(4), 4, 0, 0, 0, 0, "");

		GangRules rules = load("Levels:\n   Level_0:\n      Max_Members: 0\n      Max_Allies: 0\n");
		GangLevelTable.Row row = new GangLevelTable(rules::getLevelRows).row(0);
		assertEquals(0, row.maxMembers());
		assertEquals(0, row.maxAllies());
	}

	@Test
	@DisplayName("reload: an edited row is read on the next call, no restart")
	void reload() throws IOException {
		GangRules rules = load("Levels:\n   Level_1:\n      Cost: 20000\n      Max_Members: 10\n");
		GangLevelTable table = new GangLevelTable();
		table.reload(rules);
		assertEquals(20000D, table.row(1).cost(), 1e-9);

		Files.writeString(tempDir.resolve("gang_rules.yml"),
		                  "Levels:\n   Level_1:\n      Cost: 30000\n      Max_Members: 12\n", StandardCharsets.UTF_8);
		rules.reload();

		assertEquals(30000D, table.row(1).cost(), 1e-9);
		assertEquals(12, table.row(1).maxMembers());
	}
}
