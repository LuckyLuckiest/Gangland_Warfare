package org.luckyraven.gangland.data.plugin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.database.repositories.weapon.WeaponRepository;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.support.FakeMessageProvider;
import org.luckyraven.gangland.support.SettingsFixture;
import org.luckyraven.gangland.util.TimeMessages;
import org.luckyraven.gangland.weapon.Weapon;
import org.luckyraven.gangland.weapon.WeaponManager;
import org.luckyraven.keystone.persistence.repository.IRepository;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link PluginDataCleanupService} against mocked collaborators. Proves the due/not-due branch, that the full-table
 * {@code deleteAll()} only fires when the injected {@code IRepository<Weapon>} is actually a
 * {@link WeaponRepository} (the {@code instanceof} guard in {@code resetWeapons()}), and that a due scan reschedules
 * via {@code PluginManager.nextPlannedDate}.
 *
 * <p>{@code Settings}/{@code Messages}/{@code TimeMessages} are process-wide statics with no reset hook
 * (documentation/TESTING.md §4/§8). This service reads {@code Settings.isAutoSaveDebug()} once per construction to
 * decide whether to log via {@code TimeUtil.formatTime(..., TimeMessages.getInstance())} — a path that would throw
 * if {@code Messages}/{@code TimeMessages} were never initialized. Rather than depend on whatever another test
 * class in this module happened to leave {@code Settings.isAutoSaveDebug()} as, every test here defensively
 * initializes all three seams up front, so behaviour is identical regardless of {@code logDebug}.
 */
@DisplayName("PluginDataCleanupService")
class PluginDataCleanupServiceTest {

	@TempDir
	Path tempDir;

	private PluginManager    pluginManager;
	private WeaponRepository weaponRepository;
	private WeaponManager    weaponManager;
	private PluginDataCleanupService service;

	@BeforeEach
	void setUp() {
		SettingsFixture.initializeMinimal(tempDir);
		Messages.init(new FakeMessageProvider()
				.withString("Normal.Prefix", "")
				.withString("Time_Unit.Second", "s")
				.withString("Time_Unit.Minute", "m")
				.withString("Time_Unit.Hour", "h")
				.withString("Time_Unit.Day", "d")
				.withString("Time_Unit.Week", "w")
				.withString("Time_Unit.Year", "y"));
		TimeMessages.initialize();

		pluginManager    = mock(PluginManager.class);
		weaponRepository = mock(WeaponRepository.class);
		weaponManager    = mock(WeaponManager.class);
		service          = new PluginDataCleanupService(pluginManager, weaponRepository, weaponManager);
	}

	@Test
	@DisplayName("no plugin data rows yet: warns and touches nothing else")
	void checkAndPerformCleanup_noRows_doesNothing() {
		when(pluginManager.getPluginDataList()).thenReturn(List.of());

		assertDoesNotThrow(() -> service.checkAndPerformCleanup());

		verifyNoInteractions(weaponManager);
		verify(weaponRepository, never()).deleteAll();
	}

	@Test
	@DisplayName("scan not yet due: leaves weapons and scan dates untouched")
	void checkAndPerformCleanup_notDue_doesNothing() {
		PluginData future = new PluginData(1, 0L, 0L, System.currentTimeMillis() + Duration.ofDays(1).toMillis());
		when(pluginManager.getPluginDataList()).thenReturn(List.of(future));

		service.checkAndPerformCleanup();

		verify(weaponRepository, never()).deleteAll();
		verifyNoInteractions(weaponManager);
	}

	@Test
	@DisplayName("scan due: deletes every weapon row, clears the cache, and reschedules via nextPlannedDate")
	void checkAndPerformCleanup_due_resetsWeaponsAndReschedules() {
		PluginData due = new PluginData(1, 0L, 0L, System.currentTimeMillis() - 1_000);
		when(pluginManager.getPluginDataList()).thenReturn(List.of(due));
		when(weaponManager.getWeapons()).thenReturn(Map.of(UUID.randomUUID(), mock(Weapon.class)));
		Date nextScan = new Date(System.currentTimeMillis() + Duration.ofDays(30).toMillis());
		when(pluginManager.nextPlannedDate(any())).thenReturn(nextScan);

		service.checkAndPerformCleanup();

		verify(weaponRepository).deleteAll();
		verify(weaponManager).clear();
		assertEquals(nextScan.getTime(), due.getScheduledScanDate());
		assertTrue(due.getScanDate() > 0);
	}

	@Test
	@DisplayName("forceCleanup performs the reset immediately even far ahead of the scheduled date")
	void forceCleanup_ignoresSchedule() {
		PluginData notDue = new PluginData(1, 0L, 0L, System.currentTimeMillis() + Duration.ofDays(365).toMillis());
		when(pluginManager.getPluginDataList()).thenReturn(List.of(notDue));
		when(pluginManager.nextPlannedDate(any())).thenReturn(new Date());

		service.forceCleanup();

		verify(weaponRepository).deleteAll();
		verify(weaponManager).clear();
	}

	@Test
	@DisplayName("a plain IRepository<Weapon> (not a WeaponRepository) is skipped by the instanceof guard, but the cache still clears")
	void resetWeapons_nonWeaponRepositoryImplementation_skipsDeleteAll() {
		@SuppressWarnings("unchecked")
		IRepository<Weapon> genericRepository = mock(IRepository.class);
		PluginDataCleanupService genericService =
				new PluginDataCleanupService(pluginManager, genericRepository, weaponManager);
		PluginData due = new PluginData(1, 0L, 0L, System.currentTimeMillis() - 1_000);
		when(pluginManager.getPluginDataList()).thenReturn(List.of(due));
		when(pluginManager.nextPlannedDate(any())).thenReturn(new Date());
		when(weaponManager.getWeapons()).thenReturn(Map.of());

		assertDoesNotThrow(genericService::checkAndPerformCleanup,
				"resetWeapons()'s `if (weaponRepository instanceof WeaponRepository repo)` guard is exactly what " +
						"keeps this from a ClassCastException against a plain IRepository<Weapon>");

		verify(weaponManager).clear();
	}
}
