package org.luckyraven.gangland.data.plugin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.support.FakeMessageProvider;
import org.luckyraven.gangland.support.SettingsFixture;
import org.luckyraven.gangland.util.TimeMessages;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link PluginDataCleanupService} against a mocked {@link PluginManager}. Proves the due/not-due branch and that a
 * due scan reschedules via {@code PluginManager.nextPlannedDate}. Since 0.9.0 (weapon module removal), the service
 * no longer iterates any contributed {@code DataCleanupTask} beans — it only maintains the plugin's own scan-date
 * bookkeeping (0.8.4's {@code DataCleanupTask} SPI and its sole implementor, the weapon module, are both gone).
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

		pluginManager = mock(PluginManager.class);
		service       = new PluginDataCleanupService(pluginManager);
	}

	@Test
	@DisplayName("no plugin data rows yet: warns and touches nothing else")
	void checkAndPerformCleanup_noRows_doesNothing() {
		when(pluginManager.getPluginDataList()).thenReturn(List.of());

		assertDoesNotThrow(() -> service.checkAndPerformCleanup());
	}

	@Test
	@DisplayName("scan not yet due: leaves the scan dates untouched")
	void checkAndPerformCleanup_notDue_doesNothing() {
		PluginData future = new PluginData(1, 0L, 0L, System.currentTimeMillis() + Duration.ofDays(1).toMillis());
		when(pluginManager.getPluginDataList()).thenReturn(List.of(future));

		service.checkAndPerformCleanup();

		assertEquals(0L, future.getScanDate());
	}

	@Test
	@DisplayName("scan due: reschedules via nextPlannedDate")
	void checkAndPerformCleanup_due_reschedules() {
		PluginData due = new PluginData(1, 0L, 0L, System.currentTimeMillis() - 1_000);
		when(pluginManager.getPluginDataList()).thenReturn(List.of(due));
		Date nextScan = new Date(System.currentTimeMillis() + Duration.ofDays(30).toMillis());
		when(pluginManager.nextPlannedDate(any())).thenReturn(nextScan);

		service.checkAndPerformCleanup();

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

		assertTrue(notDue.getScanDate() > 0);
	}
}
