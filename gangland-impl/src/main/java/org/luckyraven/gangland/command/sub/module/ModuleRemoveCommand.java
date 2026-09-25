package org.luckyraven.gangland.command.sub.module;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.module.ModuleDescriptor;
import org.luckyraven.keystone.module.ModuleLoader;
import org.luckyraven.keystone.util.TriConsumer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * {@code /glw module remove <module>} — mark a module jar for deletion at the next start. The running loader holds the
 * jar open (Windows locks it outright), so removal is the {@code <jar>.stale} marker Keystone's own
 * {@code ModuleLoader.discover()} purges before anything is loaded. Deleting the marker undoes the removal.
 */
class ModuleRemoveCommand extends SubArgument {

	private final ModuleLoader moduleLoader;

	protected ModuleRemoveCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent, ModuleLoader moduleLoader) {
		super(gangland, "remove", tree, parent);

		this.moduleLoader = moduleLoader;

		OptionalArgument module = new OptionalArgument(gangland, tree,
		                                               (argument, sender, args) -> remove(sender, args[2]),
		                                               sender -> ModuleInstalls.onDisk(moduleLoader.modulesDirectory())
				                                               .stream()
				                                               .map(ModuleDescriptor::id)
				                                               .toList());
		module.setDisplayName("module");

		this.addSubArgument(module);
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) ->
				sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<module>"));
	}

	void remove(CommandSender sender, String id) {
		Path directory = moduleLoader.modulesDirectory().toAbsolutePath().normalize();
		// Every jar carrying the id, not whichever one Files.list returns first: a jar the loader refused can sit
		// beside the one it loaded, and leaving it behind means the module survives the restart.
		List<Path> jars = ModuleInstalls.onDisk(moduleLoader.modulesDirectory())
				.stream()
				.filter(descriptor -> descriptor.id().equalsIgnoreCase(id))
				.map(ModuleDescriptor::jar)
				.toList();

		if (jars.isEmpty()) {
			sender.sendMessage(Messages.MODULE_UNKNOWN.toString().replace("%module%", id));
			return;
		}

		for (Path jar : jars) {
			mark(sender, id, directory, jar);
		}
	}

	private static void mark(CommandSender sender, String id, Path directory, Path jar) {
		Path resolved = jar.toAbsolutePath().normalize();

		if (!resolved.startsWith(directory)) {
			sender.sendMessage(Messages.MODULE_REMOVE_FAILED.toString()
					                   .replace("%module%", id)
					                   .replace("%reason%", "the jar is outside the modules folder"));
			return;
		}

		Path marker = resolved.resolveSibling(resolved.getFileName() + ModuleLoader.STALE_MARKER_SUFFIX);

		try {
			Files.writeString(marker, "");
			sender.sendMessage(Messages.MODULE_REMOVE_MARKED.toString()
					                   .replace("%module%", id)
					                   .replace("%marker%", String.valueOf(marker.getFileName())));
		} catch (IOException exception) {
			sender.sendMessage(Messages.MODULE_REMOVE_FAILED.toString()
					                   .replace("%module%", id)
					                   .replace("%reason%", String.valueOf(exception.getMessage())));
		}
	}

}
