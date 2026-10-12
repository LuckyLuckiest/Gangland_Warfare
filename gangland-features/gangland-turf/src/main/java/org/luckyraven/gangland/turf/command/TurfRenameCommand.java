package org.luckyraven.gangland.turf.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.selection.WandSelectionManager;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.List;

/**
 * {@code /glw turf rename <name>} (TF-39): renames the active-selection turf (or the one the admin stands in) and
 * persists it. Admin only (TF-30); a name taken by another turf is refused (TF-29).
 */
class TurfRenameCommand extends SubArgument {

	private final JavaPlugin           plugin;
	private final Tree<Argument>       tree;
	private final TurfManager          turfs;
	private final WandSelectionManager selections;
	private final TurfMessageContract  messages;
	private final TurfMessages         text;

	protected TurfRenameCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent,
	                            TurfManager turfs, WandSelectionManager selections, TurfMessageContract messages,
	                            TurfMessages text) {
		super(plugin, "rename", tree, parent);

		this.plugin     = plugin;
		this.tree       = tree;
		this.turfs      = turfs;
		this.selections = selections;
		this.messages   = messages;
		this.text       = text;

		this.addSubArgument(nameArgument());
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			if (TurfPermissions.requireAdmin(sender, text)) {
				sender.sendMessage(
						GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<name>"));
			}
		};
	}

	/** args = [turf, rename, name...]. */
	void rename(CommandSender sender, String[] args) {
		if (!TurfPermissions.requireAdmin(sender, text)) {
			return;
		}
		Turf turf = TurfSelectionResolver.resolve(sender, turfs, selections, messages);
		if (turf == null) {
			return;
		}
		String name = TurfCreateCommand.joinFrom(args, 2);
		if (name.isBlank()) {
			sender.sendMessage(
					GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<name>"));
			return;
		}
		if (TurfCreateCommand.plain(name).isEmpty()) {
			sender.sendMessage(text.text("Turf.Name_Empty", "&cA turf name needs at least one letter or digit."));
			return;
		}
		if (TurfCreateCommand.nameTaken(turfs, name, turf.getId())) {
			sender.sendMessage(text.text("Turf.Name_Taken", "&cA turf named %turf% already exists.",
			                             "turf", TurfCreateCommand.plain(name)));
			return;
		}
		String old = turf.getDisplayName();
		turf.setDisplayName(name);
		turfs.persist(turf);
		sender.sendMessage(text.text("Turf.Renamed", "&7Renamed turf &f%old% &7to &f%turf%&7.",
		                             "old", old, "turf", name));
	}

	private OptionalArgument nameArgument() {
		// Tab completion offers the active selection's current name; it never resolves (and so never messages).
		return new OptionalArgument(plugin, tree, (argument, sender, args) -> rename(sender, args), sender -> {
			if (sender instanceof Player player) {
				Integer id   = selections.get(player).getActiveTurfId();
				Turf    turf = id == null ? null : turfs.get(id);
				if (turf != null) {
					return List.of(turf.getDisplayName());
				}
			}
			return List.of("<name>");
		});
	}
}
