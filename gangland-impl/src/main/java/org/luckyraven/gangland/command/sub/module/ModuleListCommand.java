package org.luckyraven.gangland.command.sub.module;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.diagnostics.Fault;
import org.luckyraven.keystone.module.LoadedModule;
import org.luckyraven.keystone.module.ModuleDescriptor;
import org.luckyraven.keystone.module.ModuleLoader;
import org.luckyraven.keystone.util.TriConsumer;

import java.util.List;

/**
 * {@code /glw module list} — every loaded module, then every jar the loader refused, so a module that silently never
 * came up is visible without reading the startup log.
 */
class ModuleListCommand extends SubArgument {

	private final ModuleLoader moduleLoader;

	protected ModuleListCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent, ModuleLoader moduleLoader) {
		super(gangland, "list", tree, parent);

		this.moduleLoader = moduleLoader;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			List<LoadedModule> loaded = moduleLoader.loaded();

			if (loaded.isEmpty()) {
				sender.sendMessage(Messages.MODULE_LIST_EMPTY.toString());
			} else {
				sender.sendMessage(Messages.MODULE_LIST_HEADER.toString()
						                   .replace("%count%", String.valueOf(loaded.size())));

				for (LoadedModule module : loaded) {
					ModuleDescriptor descriptor = module.descriptor();

					sender.sendMessage(Messages.MODULE_LIST_ENTRY.toString()
							                   .replace("%id%", descriptor.id())
							                   .replace("%name%", descriptor.name())
							                   .replace("%version%", descriptor.version())
							                   .replace("%host_api%", descriptor.hostApi()));
				}
			}

			List<Fault> faults = moduleLoader.faults();

			if (faults.isEmpty()) return;

			sender.sendMessage(Messages.MODULE_FAULT_HEADER.toString()
					                   .replace("%count%", String.valueOf(faults.size())));

			for (Fault fault : faults) {
				sender.sendMessage(Messages.MODULE_FAULT_ENTRY.toString()
						                   .replace("%code%", fault.code())
						                   .replace("%message%", fault.message()));
			}
		};
	}

}
