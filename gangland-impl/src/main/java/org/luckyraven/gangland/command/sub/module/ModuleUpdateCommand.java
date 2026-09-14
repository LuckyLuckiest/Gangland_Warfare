package org.luckyraven.gangland.command.sub.module;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.Nullable;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.diagnostics.Fault;
import org.luckyraven.keystone.module.LoadedModule;
import org.luckyraven.keystone.module.ModuleLoader;
import org.luckyraven.keystone.module.artifact.ArtifactResolver;
import org.luckyraven.keystone.module.update.ModuleMessages;
import org.luckyraven.keystone.module.update.ModuleUpdate;
import org.luckyraven.keystone.module.update.ModuleUpdateService;
import org.luckyraven.keystone.result.Result;
import org.luckyraven.keystone.util.TriConsumer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /glw module update [module]} — check every loaded module (or one) against the repository and download what
 * is newer. The replaced jar is retired by {@link ModuleUpdateService}; the new version loads on the next start.
 */
class ModuleUpdateCommand extends SubArgument {

	private final JavaPlugin            gangland;
	private final ModuleLoader        moduleLoader;
	private final ModuleUpdateService updateService;
	private final ArtifactResolver    resolver;

	protected ModuleUpdateCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent, ModuleLoader moduleLoader,
	                              ModuleUpdateService updateService) {
		super(gangland, "update", tree, parent);

		this.gangland      = gangland;
		this.moduleLoader  = moduleLoader;
		this.updateService = updateService;
		this.resolver      = new ArtifactResolver(gangland);

		OptionalArgument module = new OptionalArgument(gangland, tree,
		                                               (argument, sender, args) -> start(sender, args[2]),
		                                               sender -> moduleLoader.loaded()
				                                               .stream()
				                                               .map(LoadedModule::id)
				                                               .toList());
		module.setDisplayName("module");

		this.addSubArgument(module);
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> start(sender, null);
	}

	private void start(CommandSender sender, @Nullable String name) {
		List<LoadedModule> targets;

		if (name == null) {
			targets = moduleLoader.loaded();
		} else {
			LoadedModule module = moduleLoader.find(name).orElse(null);

			if (module == null) {
				sender.sendMessage(Messages.MODULE_UNKNOWN.toString().replace("%module%", name));
				return;
			}

			targets = List.of(module);
		}

		resolver.supplyAsync(() -> update(targets, name))
				.thenAccept(lines -> Bukkit.getScheduler()
						.runTask(gangland, () -> lines.forEach(sender::sendMessage)));
	}

	/** Off the server thread: one metadata round trip per module, then a download for each one that is behind. */
	private List<String> update(List<LoadedModule> targets, @Nullable String name) {
		ModuleMessages     messages = updateService.messages();
		List<ModuleUpdate> updates  = updateService.checkNow(targets);
		List<String>       lines    = new ArrayList<>();

		if (updates.isEmpty()) {
			lines.add(name == null ? Messages.MODULE_ALL_UP_TO_DATE.toString() : messages.upToDate(name));
			return lines;
		}

		for (ModuleUpdate update : updates) {
			lines.add(messages.updateAvailable(update.moduleId(), update.installedVersion(),
			                                   update.availableVersion()));

			Result<Path> downloaded = updateService.downloadNow(update);

			lines.add(downloaded.isOk()
					          ? messages.restartRequired(update.moduleId())
					          : messages.downloadFailed(update.moduleId(),
					                                    downloaded.faultOpt().map(Fault::message).orElse("")));
		}

		return lines;
	}

}
