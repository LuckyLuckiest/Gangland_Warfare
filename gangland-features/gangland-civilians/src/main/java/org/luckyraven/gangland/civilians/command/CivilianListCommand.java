package org.luckyraven.gangland.civilians.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.gangland.civilians.message.CivilianMessages;
import org.luckyraven.gangland.civilians.npc.CivilianService;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.Collection;

class CivilianListCommand extends SubArgument {

	private final CivilianService  civilianService;
	private final CivilianMessages civilianMessages;

	CivilianListCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent, CivilianService civilianService,
	                    CivilianMessages civilianMessages) {
		super(plugin, "list", tree, parent);
		this.civilianService  = civilianService;
		this.civilianMessages = civilianMessages;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Collection<CivilianNpc> npcs = civilianService.getActiveNpcs();

			if (npcs.isEmpty()) {
				sender.sendMessage(civilianMessages.listEmpty());
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
