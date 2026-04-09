package me.luckyraven.command.sub.civilians;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.copsncrooks.npc.civilian.CivilianGroup;
import me.luckyraven.copsncrooks.npc.civilian.CivilianService;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;

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
				sender.sendMessage(GanglandChatUtil.commandMessage("No civilian groups are currently active."));
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
