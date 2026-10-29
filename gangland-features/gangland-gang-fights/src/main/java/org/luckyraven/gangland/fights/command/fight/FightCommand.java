package org.luckyraven.gangland.fights.command.fight;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.fights.fight.FightManager;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

/** {@code /glw gang fight}: the parent of the fight verbs, one class each in this package. */
public final class FightCommand extends SubArgument {

	public FightCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent, @Nullable FightManager manager) {
		super(plugin, "fight", tree, parent);

		this.addSubArgument(new FightProposeCommand(plugin, tree, this, manager));
		this.addSubArgument(new FightAcceptCommand(plugin, tree, this, manager));
		this.addSubArgument(new FightDeclineCommand(plugin, tree, this, manager));
		this.addSubArgument(new FightEnlistCommand(plugin, tree, this, manager));
		this.addSubArgument(new FightDropCommand(plugin, tree, this, manager));
		this.addSubArgument(new FightInfoCommand(plugin, tree, this, manager));
		this.addSubArgument(new FightArenasCommand(plugin, tree, this, manager));
		this.addSubArgument(new FightStatsCommand(plugin, tree, this));
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(
				GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "/glw gang fight <action>"));
	}
}
