package org.luckyraven.gangland.turf;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.turf.powerups.ActiveBuffManager;
import org.luckyraven.gangland.turf.powerups.ActiveBuffRepositoryContract;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.testkit.PluginMocks;

import java.nio.file.Path;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Pins GI-82 (P3, docketed as the same bug class already fixed once for {@code SignManager} — see
 * {@code new-findings.txt} row 7, commit 4304172c): {@code TurfModuleConfig.activeBuffManager()} used to call
 * {@code manager.initialize()} itself right after construction. Gangland never disables {@code BeanFactory}'s
 * LIFECYCLE-phase convention pass ({@code setConventionInitializeEnabled(false)}, unlike Oriel), so that pass calls
 * the bean's public {@code initialize()} again — and {@code ActiveBuffManager.initialize()} is not idempotent: it
 * unconditionally reassigns {@code pruneTask} via {@code Bukkit.getScheduler().runTaskTimer(...)} with no
 * cancel-if-present guard, leaking the first {@link org.bukkit.scheduler.BukkitTask} and leaving two identical 1Hz
 * prune timers running forever.
 *
 * <p>The fix is to stop calling {@code initialize()} from the {@code @Bean} method at all and leave it solely to
 * the framework's convention pass — so the bean-factory method itself must produce an {@code ActiveBuffManager}
 * that has not scheduled anything yet.
 */
@DisplayName("TurfModuleConfig.activeBuffManager — GI-82: does not self-initialize")
class TurfModuleConfigTest {

	@TempDir(cleanup = CleanupMode.NEVER)
	Path tempDir;

	@Test
	@DisplayName("GI-82: the @Bean method leaves initialize() to BeanFactory's convention pass, not a double call")
	void activeBuffManager_doesNotSelfInitialize() {
		JavaPlugin                   plugin = PluginMocks.plugin(tempDir);
		ActiveBuffRepositoryContract repo   = mock(ActiveBuffRepositoryContract.class);
		when(repo.loadAll()).thenReturn(List.of());

		try (BukkitStatics bukkit = BukkitStatics.install()) {
			BukkitScheduler scheduler = bukkit.scheduler();

			ActiveBuffManager manager = new TurfModuleConfig().activeBuffManager(plugin, repo);

			verify(scheduler, never()).runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong());

			// The framework's own convention pass calling initialize() exactly once (simulated here) must be the
			// only thing that schedules the prune task — proving a single, not double, prune timer.
			manager.initialize();
			verify(scheduler, times(1)).runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong());
		}
	}
}
