package org.luckyraven.gangland.gang.level;

import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.support.FakeIdentitySettingsContract;
import org.luckyraven.gangland.core.user.IdentitySettings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangLevels;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.gang.file.GangRules.LevelRowValues;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/** GangLimits answers from the row of the gang's current level; an unknown gang is never limited. */
@DisplayName("GangLimits - what the current level of a gang allows")
class GangLimitsTest {

	private MockedStatic<Bukkit> bukkit;
	private GangLimits           limits;
	private Gang                 gang;

	@BeforeEach
	void setUp() {
		IdentitySettings.bind(new FakeIdentitySettingsContract());
		GangLevels.bind(3, 200);
		bukkit = mockStatic(Bukkit.class);
		bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));

		Map<Integer, LevelRowValues> rows = Map.of(
				0, new LevelRowValues(0, 8, 1, 1, "", List.of()),
				1, new LevelRowValues(20000, 10, 1, 2, "[1]", List.of()),
				2, new LevelRowValues(40000, 0, 2, 0, "[2]", List.of()));
		gang = new Gang(7);
		GangLookupContract gangs = mock(GangLookupContract.class);
		when(gangs.findById(7)).thenReturn(gang);
		limits = new GangLimits(new GangLevelTable(() -> rows), gangs);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
		GangLevels.bind(20, 200);
	}

	@Test
	@DisplayName("caps, prefix and next cost follow the gang's level row")
	void followsTheLevelRow() {
		assertEquals(8, limits.maxMembers(7));
		assertEquals(1, limits.maxAllies(7));
		assertEquals("", limits.chatPrefix(7));
		assertEquals(20000D, limits.upgradeCost(7), 1e-9);

		gang.getLevel().addLevels(1, null);

		assertEquals(10, limits.maxMembers(7));
		assertEquals(2, limits.maxAllies(7));
		assertEquals("[1]", limits.chatPrefix(7));
		assertEquals(40000D, limits.upgradeCost(7), 1e-9);
	}

	@Test
	@DisplayName("0 means unlimited, an unknown gang is unlimited, the top level has nothing left to buy")
	void zeroAndUnknownAndTop() {
		gang.getLevel().addLevels(2, null);
		assertEquals(0, limits.maxMembers(7));
		assertEquals(0, limits.maxAllies(7));

		assertEquals(0, limits.maxMembers(999));

		gang.getLevel().addLevels(1, null);
		assertEquals(0D, limits.upgradeCost(7), 1e-9);
		assertEquals(0D, limits.upgradeXpMissing(7), 1e-9);
	}

	@Test
	@DisplayName("xp missing is the bar of the next level minus the XP held, never below 0")
	void xpMissing() {
		assertEquals(200D, limits.upgradeXpMissing(7), 1e-9);
		gang.getLevel().addExperience(150, false, null);
		assertEquals(50D, limits.upgradeXpMissing(7), 1e-9);
		gang.getLevel().addExperience(500, false, null);
		assertEquals(0D, limits.upgradeXpMissing(7), 1e-9);
	}
}
