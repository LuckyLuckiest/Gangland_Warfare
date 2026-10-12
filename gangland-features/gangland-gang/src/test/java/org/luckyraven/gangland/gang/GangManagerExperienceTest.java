package org.luckyraven.gangland.gang;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.core.support.FakeIdentitySettingsContract;
import org.luckyraven.gangland.core.user.IdentitySettings;
import org.luckyraven.gangland.events.gang.GangLevelUpEvent;
import org.luckyraven.gangland.gang.contract.GangAllianceRepositoryContract;
import org.luckyraven.gangland.gang.contract.GangMigrationsContract;
import org.luckyraven.gangland.gang.file.GangRules.LevelRowValues;
import org.luckyraven.gangland.gang.level.GangLevelTable;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * GR-19: {@link GangManager#addExperience} adds gang XP, levels the gang up and fires one {@link GangLevelUpEvent} per
 * level gained, after the level went up. A non-positive amount, a NaN, or a gang already at its maximum fires nothing.
 */
@DisplayName("GangManager.addExperience - gang XP levels up and fires one GangLevelUpEvent per level gained")
class GangManagerExperienceTest {

	private MockedStatic<Bukkit> bukkit;
	private PluginManager        pluginManager;
	private GangManager          manager;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		IdentitySettings.bind(new FakeIdentitySettingsContract());
		GangLevels.bind(GangLevels.DEFAULT_MAX_LEVEL, GangLevels.DEFAULT_BASE_EXPERIENCE);

		pluginManager = mock(PluginManager.class);
		bukkit        = mockStatic(Bukkit.class);
		bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);

		manager = managerWith(new LevelRowValues(0, 0, 0, 0, "", List.of()));
	}

	/** W5-L: a manager whose level table has one row, inherited by every level (cost 0 = the automatic path). */
	@SuppressWarnings("unchecked")
	private GangManager managerWith(LevelRowValues row) {
		return new GangManager(mock(IRepository.class), mock(GangAllianceRepositoryContract.class),
		                       GangMigrationsContract.NONE, new GangLevelTable(() -> Map.of(0, row)));
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
		GangLevels.bind(GangLevels.DEFAULT_MAX_LEVEL, GangLevels.DEFAULT_BASE_EXPERIENCE);
	}

	/** A gang on whatever level curve is bound at the time of the call. */
	private Gang newGang() {
		return new Gang(1);
	}

	@Test
	@DisplayName("200 XP reaches level 1: exactly one event, and the level already reads 1 when a listener sees it")
	void oneLevel_oneEvent_afterTheIncrement() {
		Gang gang = newGang();
		List<Integer> seen = new ArrayList<>();
		doAnswer(i -> {
			seen.add(((GangLevelUpEvent) i.getArgument(0)).getLevel().getLevelValue());
			return null;
		}).when(pluginManager).callEvent(any(Event.class));

		manager.addExperience(gang, 200);

		assertEquals(1, gang.getLevel().getLevelValue());
		assertEquals(List.of(1), seen);
	}

	@Test
	@DisplayName("T-254: 766 XP reaches level 2 in one call: two events, each reading the level it announces")
	void twoLevels_twoEvents() {
		Gang gang = newGang();
		List<Integer> seen = new ArrayList<>();
		doAnswer(i -> {
			seen.add(((GangLevelUpEvent) i.getArgument(0)).getLevel().getLevelValue());
			return null;
		}).when(pluginManager).callEvent(any(Event.class));

		manager.addExperience(gang, 766);

		assertEquals(2, gang.getLevel().getLevelValue());
		assertEquals(List.of(1, 2), seen);
	}

	@Test
	@DisplayName("T-253: a cancelled gang level-up keeps the gang at the level before that step and eats no XP")
	void cancelledEvent_stopsAtTheLevelBefore() {
		Gang gang = newGang();
		doAnswer(i -> {
			((GangLevelUpEvent) i.getArgument(0)).setCancelled(true);
			return null;
		}).when(pluginManager).callEvent(any(Event.class));

		manager.addExperience(gang, 766);

		assertEquals(0, gang.getLevel().getLevelValue());
		assertEquals(766D, gang.getLevel().getExperience(), 1e-9);
	}

	@Test
	@DisplayName("199 XP is below level 1: no level and no event")
	void belowThreshold_noEvent() {
		Gang gang = newGang();

		manager.addExperience(gang, 199);

		assertEquals(0, gang.getLevel().getLevelValue());
		assertEquals(199D, gang.getLevel().getExperience(), 1e-9);
		verify(pluginManager, never()).callEvent(any(Event.class));
	}

	@Test
	@DisplayName("T-255: a gang at its maximum level gains no XP and fires nothing")
	void maxLevel_noEvent() {
		GangLevels.bind(1, GangLevels.DEFAULT_BASE_EXPERIENCE);
		Gang gang = newGang();
		manager.addExperience(gang, 200);
		assertEquals(1, gang.getLevel().getLevelValue());
		clearInvocations(pluginManager);

		manager.addExperience(gang, 500);

		assertEquals(1, gang.getLevel().getLevelValue());
		assertEquals(0D, gang.getLevel().getExperience(), 1e-9);   // at max level no XP collects
		verify(pluginManager, never()).callEvent(any(Event.class));
	}

	@Test
	@DisplayName("zero, negative and NaN amounts change nothing and fire nothing")
	void nonPositiveOrNaN_ignored() {
		Gang gang = newGang();

		for (double amount : new double[]{0, -5, Double.NaN}) {
			manager.addExperience(gang, amount);
		}

		assertEquals(0, gang.getLevel().getLevelValue());
		assertEquals(0D, gang.getLevel().getExperience(), 1e-9);
		verify(pluginManager, never()).callEvent(any(Event.class));
	}

	@Test
	@DisplayName("one paying turf a day (144 XP) reaches level 1 on day 2, level 2 on day 6, level 3 on day 13")
	void daysToLevel_turfPace() {
		Gang gang = newGang();
		List<Integer> reachedOnDay = new ArrayList<>();

		for (int day = 1; reachedOnDay.size() < 3 && day <= 60; day++) {
			for (int call = 0; call < 144; call++) {
				manager.addExperience(gang, 1.0);
			}
			while (reachedOnDay.size() < gang.getLevel().getLevelValue()) {
				reachedOnDay.add(day);
			}
		}

		assertEquals(List.of(2, 6, 13), reachedOnDay.subList(0, 3));
	}

	@Test
	@DisplayName("bankedXp_noLevelWhenCostPositive: with a fee on the next level the XP is banked and the gang never levels itself")
	void bankedXp_noLevelWhenCostPositive() {
		manager = managerWith(new LevelRowValues(20000, 0, 0, 0, "", List.of()));
		Gang gang = newGang();

		manager.addExperience(gang, 766);
		manager.addExperience(gang, 100);

		assertEquals(0, gang.getLevel().getLevelValue());
		assertEquals(866D, gang.getLevel().getExperience(), 1e-9);
		verify(pluginManager, never()).callEvent(any(Event.class));
	}

	@Test
	@DisplayName("auto-levelling stops at the first level that has a fee")
	void autoLevelling_stopsAtThePaidLevel() {
		manager = new GangManager(mock(IRepository.class), mock(GangAllianceRepositoryContract.class),
		                          GangMigrationsContract.NONE,
		                          new GangLevelTable(() -> Map.of(0, new LevelRowValues(0, 0, 0, 0, "", List.of()),
		                                                          2, new LevelRowValues(5000, 0, 0, 0, "", List.of()))));
		Gang gang = newGang();

		manager.addExperience(gang, 766);

		assertEquals(1, gang.getLevel().getLevelValue());
		assertEquals(566D, gang.getLevel().getExperience(), 1e-9);
	}

}
