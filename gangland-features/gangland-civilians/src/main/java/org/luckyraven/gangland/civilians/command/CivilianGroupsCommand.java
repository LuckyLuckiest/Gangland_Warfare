package org.luckyraven.gangland.civilians.command;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.gangland.civilians.message.CivilianMessages;
import org.luckyraven.gangland.civilians.npc.CivilianGroup;
import org.luckyraven.gangland.civilians.npc.CivilianService;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.Collection;

class CivilianGroupsCommand extends SubArgument {

	private final CivilianService  civilianService;
	private final CivilianMessages civilianMessages;

	CivilianGroupsCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent, CivilianService civilianService,
	                      CivilianMessages civilianMessages) {
		super(plugin, "groups", tree, parent);
		this.civilianService  = civilianService;
		this.civilianMessages = civilianMessages;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Collection<CivilianGroup> groups = civilianService.getActiveGroups();

			if (groups.isEmpty()) {
				sender.sendMessage(civilianMessages.groupsEmpty());
				return;
			}

			sender.sendMessage(GanglandChatUtil.color("&7Active civilian groups &8(&b" + groups.size() + "&8):"));
			for (CivilianGroup group : groups) {
				int count = group.getMembers().size();
				sender.sendMessage(GanglandChatUtil.color(
						" &b- &7" + group.getGroupId() + " &8| &bMembers: &7" + count));
			}
		};
	}
}
