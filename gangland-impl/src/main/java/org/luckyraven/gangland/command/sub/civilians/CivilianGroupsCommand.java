package org.luckyraven.gangland.command.sub.civilians;

import org.bukkit.command.CommandSender;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.gangland.copsncrooks.npc.civilian.CivilianGroup;
import org.luckyraven.gangland.copsncrooks.npc.civilian.CivilianService;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.Collection;

class CivilianGroupsCommand extends SubArgument {

	private final CivilianService civilianService;

	CivilianGroupsCommand(Gangland gangland, Tree<Argument> tree, Argument parent, CivilianService civilianService) {
		super(gangland, "groups", tree, parent);
		this.civilianService = civilianService;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Collection<CivilianGroup> groups = civilianService.getActiveGroups();

			if (groups.isEmpty()) {
				sender.sendMessage(Messages.CIVILIAN_GROUPS_EMPTY.toString());
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
