package org.luckyraven.gangland.config;

import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.data.economy.BankTiers;
import org.luckyraven.gangland.data.wanted.ContactDesk;
import org.luckyraven.gangland.data.placeholder.PlaceholderService;
import org.luckyraven.gangland.data.placeholder.worker.GanglandPlaceholder;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.item.configuration.UniqueItemAddon;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.support.FakeMessageProvider;
import org.luckyraven.gangland.support.ModuleRepoFixture;
import org.luckyraven.gangland.support.SettingsFixture;
import org.luckyraven.keystone.bean.autowire.DependencyContainer;
import org.luckyraven.keystone.module.ModuleLoader;
import org.luckyraven.keystone.module.artifact.ArtifactCoordinate;
import org.luckyraven.keystone.module.update.ModuleUpdate;
import org.luckyraven.keystone.module.update.ModuleUpdateService;
import org.luckyraven.keystone.placeholder.PlaceholderProvider;
import org.luckyraven.keystone.result.Result;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.update.PluginVersion;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WS1 G3 fix round 1 (Opus review I4, orchestrator ruling W36). Plaque resolves a Keystone
 * {@code org.luckyraven.keystone.placeholder.PlaceholderProvider} from the {@code ServicesManager}
 * ({@code Plaque}'s {@code PapiText.java:30-35}, lazy, uncached) as its second placeholder path, behind real
 * PlaceholderAPI, for when PAPI itself has no answer. Before this fix Gangland published only {@code ItemVocabulary}
 * on the {@code ServicesManager} ({@code GanglandContext.java:204-215}) — nothing ever registered a
 * {@code PlaceholderProvider}, so Plaque's non-PAPI branch was dead code. Pins that
 * {@link WiringConfig#ganglandPlaceholder} now publishes one, mirroring the {@code ItemVocabulary} publication
 * idiom Bartizan's {@code ItemConfig.bartizanItemVocabulary} already establishes (construct-then-register-then-log,
 * same {@code @Bean} method).
 */
@DisplayName("WiringConfig.ganglandPlaceholder — publishes a Keystone PlaceholderProvider for external consumers "
             + "(Plaque)")
class WiringConfigTest {

	@Test
	@DisplayName("registers GanglandPlaceholder.asProvider() as a PlaceholderProvider service, owned by the plugin")
	void ganglandPlaceholder_registersPlaceholderProviderService() {
		try (BukkitStatics bukkit = BukkitStatics.install()) {
			Gangland      gangland = mock(Gangland.class);
			WiringConfig  config   = new WiringConfig(gangland);

			@SuppressWarnings("unchecked")
			UserManager<Player> userManager        = mock(UserManager.class);
			UniqueItemAddon     uniqueItemAddon     = mock(UniqueItemAddon.class);
			BankTiers           bankTiers           = mock(BankTiers.class);
			DependencyContainer container           = mock(DependencyContainer.class);
			PlaceholderService  placeholderService  = mock(PlaceholderService.class);

			GanglandPlaceholder placeholder = config.ganglandPlaceholder(userManager, uniqueItemAddon, bankTiers,
			                                                             container, placeholderService,
			                                                             new ContactDesk(() -> 0L));

			assertNotNull(placeholder);
			verify(bukkit.servicesManager()).register(eq(PlaceholderProvider.class), any(PlaceholderProvider.class),
			                                          eq(gangland), eq(ServicePriority.Normal));
		}
	}

	@Test
	@DisplayName("moduleUpdateService carries the loader's host API: a release built for a newer host is refused and "
	             + "the working jar kept (consumer half of KS-MO-09)")
	void moduleUpdateService_hostIncompatibleRelease_keepsInstalledJar(@TempDir Path dir) throws Exception {
		Path repo    = dir.resolve("repo");
		Path modules = dir.resolve("modules");
		ModuleRepoFixture.publish(repo, "org.luckyraven:gangland-gadget:0.10.0", "gadget", "9.0");
		Path installed = ModuleRepoFixture.moduleJar(modules.resolve("gangland-gadget-0.9.1.jar"), "gadget", "0.9.1",
		                                             "1.0");
		SettingsFixture.write(dir, "Money_Symbol: '$'\nModules:\n  Repository: '" + repo.toUri() + "'\n");
		SettingsFixture.initialize(dir);
		Messages.init(new FakeMessageProvider());

		ModuleLoader moduleLoader = mock(ModuleLoader.class);
		when(moduleLoader.hostApi()).thenReturn(PluginVersion.parse("1.0"));
		when(moduleLoader.modulesDirectory()).thenReturn(modules);

		ModuleUpdateService service = new WiringConfig(mock(Gangland.class)).moduleUpdateService(moduleLoader);
		Result<Path> result = service.downloadNow(new ModuleUpdate("gadget", "0.9.1", "0.10.0",
		                                                           ArtifactCoordinate.parse("org.luckyraven:gangland-gadget"),
		                                                           installed));

		assertTrue(result.isFailure(), "a Host_Api 9.0 release must not replace a module on a 1.0 host");
		assertTrue(Files.exists(installed), "the working jar must not be retired for an unloadable release");
	}
}
