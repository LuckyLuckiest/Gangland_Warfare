package org.luckyraven.gangland.command.sub.module;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.support.FakeMessageProvider;
import org.luckyraven.gangland.support.ModuleRepoFixture;
import org.luckyraven.gangland.support.SettingsFixture;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.diagnostics.Fault;
import org.luckyraven.keystone.module.ModuleLoader;
import org.luckyraven.keystone.module.artifact.ArtifactResolver;
import org.luckyraven.keystone.module.artifact.MavenRepository;
import org.luckyraven.keystone.module.update.ModuleUpdateService;
import org.luckyraven.keystone.testkit.BukkitStatics;
import org.luckyraven.keystone.update.PluginVersion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Drives {@code /glw module install|remove|update} against a real modules folder and a {@code file:} repository, for the
 * jars the loader refused at boot (host-incompatible, so absent from {@code loaded()}) but that still sit on disk.
 */
@DisplayName("/glw module install|remove|update - jars the loader refused")
class ModuleCommandsTest {

	private static final PluginVersion HOST = PluginVersion.parse("1.0");

	@TempDir
	Path dir;

	private Path                repo;
	private Path                modules;
	private BukkitStatics       bukkit;
	private JavaPlugin          plugin;
	private ModuleLoader        moduleLoader;
	private ModuleUpdateService updateService;
	private CommandSender       sender;

	@BeforeEach
	void setUp() {
		repo    = dir.resolve("repo");
		modules = dir.resolve("modules");
		bukkit  = BukkitStatics.install();
		when(bukkit.pluginManager().getPlugins()).thenReturn(new Plugin[0]);
		SettingsFixture.initializeMinimal(dir);
		Messages.init(new FakeMessageProvider());

		plugin       = mock(JavaPlugin.class);
		moduleLoader = mock(ModuleLoader.class);
		sender       = mock(CommandSender.class);
		when(moduleLoader.loaded()).thenReturn(List.of());
		when(moduleLoader.modulesDirectory()).thenReturn(modules);
		when(moduleLoader.hostApi()).thenReturn(HOST);

		updateService = new ModuleUpdateService(plugin, new ArtifactResolver(plugin),
		                                        MavenRepository.of("test", repo.toUri().toString()), modules, null,
		                                        HOST);
	}

	@AfterEach
	void tearDown() {
		bukkit.close();
	}

	@Test
	@DisplayName("install over a same-id jar the loader refused retires it instead of downloading beside it")
	void install_besideRefusedJar_retiresIt() throws Exception {
		ModuleRepoFixture.publish(repo, "org.luckyraven:gangland-gadget:0.10.0", "gadget", "1.0");
		ModuleRepoFixture.moduleJar(modules.resolve("gangland-gadget-0.9.1.jar"), "gadget", "0.9.1", "9.0");

		new ModuleInstallCommand(plugin, new Tree<>(), mock(Argument.class), moduleLoader, updateService)
				.start(sender, "gadget", "0.10.0");

		assertEquals(List.of("gangland-gadget-0.10.0.jar"), jars(),
		             "the refused 0.9.1 jar must not stay beside the new one (module.duplicate every boot)");
	}

	@Test
	@DisplayName("remove marks every jar carrying the id, not whichever one the folder listing returns first")
	void remove_twoJarsSameId_marksBoth() throws Exception {
		ModuleRepoFixture.moduleJar(modules.resolve("gangland-gadget-0.10.0.jar"), "gadget", "0.10.0", "1.0");
		ModuleRepoFixture.moduleJar(modules.resolve("gangland-gadget-0.9.1.jar"), "gadget", "0.9.1", "9.0");

		new ModuleRemoveCommand(plugin, new Tree<>(), mock(Argument.class), moduleLoader).remove(sender, "gadget");

		assertTrue(Files.exists(modules.resolve("gangland-gadget-0.10.0.jar" + ModuleLoader.STALE_MARKER_SUFFIX)));
		assertTrue(Files.exists(modules.resolve("gangland-gadget-0.9.1.jar" + ModuleLoader.STALE_MARKER_SUFFIX)),
		           "the refused jar must be removed too, or the module survives the restart");
	}

	@Test
	@DisplayName("update with nothing loaded says so and points at /glw module list, not 'every module is up to date'")
	void update_nothingLoaded_reportsEmptyAndSkipped() {
		Messages.init(new FakeMessageProvider()
				              .withString("Commands.Module.List_Empty", "no modules loaded")
				              .withString("Commands.Module.All_Up_To_Date", "every module is up to date")
				              .withString("Commands.Module.Update_Skipped", "%count% skipped, see /glw module list"));
		when(moduleLoader.faults()).thenReturn(List.of(
				Fault.userError("module.host.incompatible", "gadget needs Host_Api 9.0").build()));

		new ModuleUpdateCommand(plugin, new Tree<>(), mock(Argument.class), moduleLoader, updateService)
				.start(sender, null);

		ArgumentCaptor<String> sent = ArgumentCaptor.forClass(String.class);
		verify(sender, atLeastOnce()).sendMessage(sent.capture());
		List<String> lines = sent.getAllValues();
		assertTrue(lines.stream().noneMatch(line -> line.contains("every module is up to date")),
		           "nothing was checked, so nothing is known to be up to date; got " + lines);
		assertTrue(lines.stream().anyMatch(line -> line.contains("no modules loaded")), "got " + lines);
		assertTrue(lines.stream().anyMatch(line -> line.contains("1 skipped, see /glw module list")), "got " + lines);
	}

	private List<String> jars() throws IOException {
		try (Stream<Path> files = Files.list(modules)) {
			return files.map(path -> path.getFileName().toString()).filter(name -> name.endsWith(".jar")).toList();
		}
	}
}
