package me.luckyraven.command.sub.civilians;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.copsncrooks.npc.civilian.CivilianService;
import me.luckyraven.copsncrooks.npc.civilian.npc.CivilianNpc;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;

import java.util.Collection;

class CivilianListCommand extends SubArgument {

	private final CivilianService civilianService;

	CivilianListCommand(Gangland gangland, Tree<Argument> tree, Argument parent, CivilianService civilianService) {
		super(gangland, "list", tree, parent);
		this.civilianService = civilianService;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Collection<CivilianNpc> npcs = civilianService.getActiveNpcs();

			if (npcs.isEmpty()) {
				sender.sendMessage(Messages.CIVILIAN_LIST_EMPTY.toString());
				return;
			}

			sender.sendMessage(GanglandChatUtil.color("&7Active civilians &8(&b" + npcs.size() + "&8):"));
			for (CivilianNpc npc : npcs) {
				Entity entity  = npc.getEntity();
				String uuid    = entity != null ? entity.getUniqueId().toString().substring(0, 8) + "..." : "?";
				String typeId  = npc.getTypeConfig().typeId();
				String groupId = npc.getGroupId() != null ? npc.getGroupId() : "&8none";

				sender.sendMessage(GanglandChatUtil.color(
						" &b- &7" + uuid + " &8| &bType: &7" + typeId + " &8| &bGroup: &7" + groupId));
			}
		};
	}
}
