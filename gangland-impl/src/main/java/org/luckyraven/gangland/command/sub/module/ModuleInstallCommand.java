package org.luckyraven.gangland.command.sub.module;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.diagnostics.Fault;
import org.luckyraven.keystone.module.LoadedModule;
import org.luckyraven.keystone.module.ModuleDescriptor;
import org.luckyraven.keystone.module.ModuleDescriptorReader;
import org.luckyraven.keystone.module.ModuleLoader;
import org.luckyraven.keystone.module.artifact.ArtifactCoordinate;
import org.luckyraven.keystone.module.artifact.ArtifactResolver;
import org.luckyraven.keystone.module.artifact.MavenMetadata;
import org.luckyraven.keystone.module.artifact.MavenRepository;
import org.luckyraven.keystone.module.update.ModuleUpdate;
import org.luckyraven.keystone.module.update.ModuleUpdateService;
import org.luckyraven.keystone.result.Result;
import org.luckyraven.keystone.update.PluginVersion;
import org.luckyraven.keystone.util.TriConsumer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@code /glw module install <module> [version]} — fetch a module jar from {@code Modules.Repository} into the modules
 * folder. Metadata lookup, download and checksum verification are blocking network work, so everything after the
 * argument parse runs off the server thread and only the replies hop back onto it.
 */
class ModuleInstallCommand extends SubArgument {

	private final JavaPlugin            gangland;
	private final ModuleLoader        moduleLoader;
	private final ModuleUpdateService updateService;
	private final ArtifactResolver    resolver;

	protected ModuleInstallCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent, ModuleLoader moduleLoader,
	                               ModuleUpdateService updateService) {
		super(gangland, "install", tree, parent);

		this.gangland      = gangland;
		this.moduleLoader  = moduleLoader;
		this.updateService = updateService;
		// A stateless wrapper over the plugin handle, not a bean: the service owns the one that does the update work.
		this.resolver = new ArtifactResolver(gangland);

		OptionalArgument version = new OptionalArgument(gangland, tree,
		                                                (argument, sender, args) -> start(sender, args[2], args[3]));
		version.setDisplayName("version");

		OptionalArgument module = new OptionalArgument(gangland, tree,
		                                               (argument, sender, args) -> start(sender, args[2], null),
		                                               sender -> new ArrayList<>(ModuleInstalls.OFFICIAL.keySet()));
		module.setDisplayName("module");

		module.addSubArgument(version);
		this.addSubArgument(module);
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) ->
				sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<module>"));
	}

	/** Main thread: parse, snapshot what the verdict needs, then hand the network work to the async scheduler. */
	private void start(CommandSender sender, String moduleArgument, @Nullable String version) {
		ArtifactCoordinate parsed = ModuleInstalls.coordinate(moduleArgument);

		if (parsed == null) {
			sender.sendMessage(Messages.MODULE_INSTALL_USAGE.toString().replace("%module%", moduleArgument));
			return;
		}

		ArtifactCoordinate requested = version == null || version.isBlank() ? parsed : parsed.withVersion(version);

		Set<String> loadedIds = moduleLoader.loaded()
				.stream()
				.map(LoadedModule::id)
				.collect(Collectors.toSet());
		Set<String> enabledPlugins = Arrays.stream(Bukkit.getPluginManager().getPlugins())
				.filter(Plugin::isEnabled)
				.map(Plugin::getName)
				.collect(Collectors.toSet());

		sender.sendMessage(Messages.MODULE_INSTALL_STARTED.toString().replace("%module%", requested.toString()));

		resolver.supplyAsync(() -> install(moduleArgument, requested, loadedIds, enabledPlugins))
				.thenAccept(lines -> Bukkit.getScheduler()
						.runTask(gangland, () -> lines.forEach(sender::sendMessage)));
	}

	/** Off the server thread: resolve the version, download, then judge what landed. Returns the reply lines. */
	private List<String> install(String moduleArgument, ArtifactCoordinate requested, Set<String> loadedIds,
	                             Set<String> enabledPlugins) {
		List<String>       lines      = new ArrayList<>();
		MavenRepository    repository = updateService.repository();
		ArtifactCoordinate target     = requested;

		if (!target.hasVersion()) {
			Result<MavenMetadata> metadata = resolver.fetchMetadata(repository, target);

			if (metadata.isFailure()) {
				lines.add(failed(metadata));
				return lines;
			}

			String newest = metadata.fold(found -> found.newestVersion().orElse(null), fault -> null);

			if (newest == null) {
				lines.add(Messages.MODULE_INSTALL_NO_VERSION.toString().replace("%module%", target.toString()));
				return lines;
			}

			target = target.withVersion(newest);
		}

		LoadedModule installed  = alreadyInstalled(moduleArgument, target);
		Result<Path> downloaded = installed == null
				? resolver.download(repository, target, moduleLoader.modulesDirectory())
				: updateService.downloadNow(update(installed.descriptor(), target));

		if (downloaded.isFailure()) {
			lines.add(failed(downloaded));
			return lines;
		}

		lines.addAll(verify(downloaded.fold(path -> path, fault -> null), loadedIds, enabledPlugins));
		return lines;
	}

	/** The jar is on disk now: reject it if it is not a module or not built for this host, warn if it cannot run. */
	private List<String> verify(Path jar, Set<String> loadedIds, Set<String> enabledPlugins) {
		List<String>             lines = new ArrayList<>();
		Result<ModuleDescriptor> read  = ModuleDescriptorReader.read(jar);

		if (read.isFailure()) {
			delete(jar);
			lines.add(Messages.MODULE_NOT_A_MODULE.toString()
					          .replace("%file%", String.valueOf(jar.getFileName()))
					          .replace("%reason%", reason(read)));
			return lines;
		}

		ModuleDescriptor       descriptor = read.fold(found -> found, fault -> null);
		PluginVersion          hostApi    = moduleLoader.hostApi();
		ModuleInstalls.Verdict verdict    = ModuleInstalls.inspect(descriptor, hostApi, loadedIds,
		                                                           enabledPlugins::contains);

		if (!verdict.compatible()) {
			delete(jar);
			lines.add(Messages.MODULE_INCOMPATIBLE.toString()
					          .replace("%module%", descriptor.id())
					          .replace("%required%", descriptor.hostApi())
					          .replace("%host%", hostApi.major() + "." + hostApi.minor()));
			return lines;
		}

		for (String depend : verdict.missingDepends()) {
			lines.add(Messages.MODULE_MISSING_DEPEND.toString()
					          .replace("%module%", descriptor.id())
					          .replace("%depend%", depend));
		}

		for (String plugin : verdict.missingPlugins()) {
			lines.add(Messages.MODULE_MISSING_PLUGIN.toString()
					          .replace("%module%", descriptor.id())
					          .replace("%plugin%", plugin));
		}

		lines.add(Messages.MODULE_INSTALLED.toString()
				          .replace("%module%", descriptor.id())
				          .replace("%version%", descriptor.version()));
		return lines;
	}

	/**
	 * The loaded module this coordinate would replace, matched on the descriptor's own {@code Artifact} or on the id
	 * the operator typed — {@code null} when this is a fresh install.
	 */
	private @Nullable LoadedModule alreadyInstalled(String moduleArgument, ArtifactCoordinate target) {
		for (LoadedModule module : moduleLoader.loaded()) {
			ModuleDescriptor descriptor = module.descriptor();

			if (descriptor.id().equalsIgnoreCase(moduleArgument)) return module;
			if (descriptor.artifact() == null) continue;

			ArtifactCoordinate declared = ModuleInstalls.coordinate(descriptor.artifact());

			if (declared == null) continue;
			if (declared.groupId().equals(target.groupId()) && declared.artifactId().equals(target.artifactId())) {
				return module;
			}
		}

		return null;
	}

	private static ModuleUpdate update(ModuleDescriptor installed, ArtifactCoordinate target) {
		return new ModuleUpdate(installed.id(), installed.version(), target.version(),
		                        new ArtifactCoordinate(target.groupId(), target.artifactId(), null), installed.jar());
	}

	private static String failed(Result<?> result) {
		return Messages.MODULE_INSTALL_FAILED.toString()
				.replace("%code%", result.faultOpt().map(Fault::code).orElse("unknown"))
				.replace("%reason%", reason(result));
	}

	private static String reason(Result<?> result) {
		return result.faultOpt().map(Fault::message).orElse("");
	}

	private static void delete(Path jar) {
		try {
			Files.deleteIfExists(jar);
		} catch (IOException ignored) {
			// Nothing to do: the jar is rejected either way and the loader refuses it again on the next start.
		}
	}

}
