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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PluginDataCleanupService} against a mocked {@link DataCleanupTask}. Proves the due/not-due branch, that
 * every registered task's {@code cleanup()} only fires on a due (or forced) scan, and that a due scan reschedules
 * via {@code PluginManager.nextPlannedDate}. Since the 0.8.4 module split, the service no longer knows about any
 * feature-specific repository or manager type directly — it iterates whatever {@link DataCleanupTask} beans the
 * container holds; the weapon module's own guard against a non-owning repository implementation is pinned by its
 * own data-cleanup-task test in that module, not here.
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
	private DataCleanupTask  task;
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
		task          = mock(DataCleanupTask.class);
		service       = new PluginDataCleanupService(pluginManager, () -> List.of(task));
	}

	@Test
	@DisplayName("no plugin data rows yet: warns and touches nothing else")
	void checkAndPerformCleanup_noRows_doesNothing() {
		when(pluginManager.getPluginDataList()).thenReturn(List.of());

		assertDoesNotThrow(() -> service.checkAndPerformCleanup());

		verify(task, never()).cleanup();
	}

	@Test
	@DisplayName("scan not yet due: leaves the registered tasks and scan dates untouched")
	void checkAndPerformCleanup_notDue_doesNothing() {
		PluginData future = new PluginData(1, 0L, 0L, System.currentTimeMillis() + Duration.ofDays(1).toMillis());
		when(pluginManager.getPluginDataList()).thenReturn(List.of(future));

		service.checkAndPerformCleanup();

		verify(task, never()).cleanup();
	}

	@Test
	@DisplayName("scan due: runs every registered task and reschedules via nextPlannedDate")
	void checkAndPerformCleanup_due_runsTasksAndReschedules() {
		PluginData due = new PluginData(1, 0L, 0L, System.currentTimeMillis() - 1_000);
		when(pluginManager.getPluginDataList()).thenReturn(List.of(due));
		when(task.name()).thenReturn("weapons");
		when(task.cleanup()).thenReturn(1);
		Date nextScan = new Date(System.currentTimeMillis() + Duration.ofDays(30).toMillis());
		when(pluginManager.nextPlannedDate(any())).thenReturn(nextScan);

		service.checkAndPerformCleanup();

		verify(task).cleanup();
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

		verify(task).cleanup();
	}
}
