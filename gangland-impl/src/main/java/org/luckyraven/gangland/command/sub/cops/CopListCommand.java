package org.luckyraven.gangland.command.sub.cops;

import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.command.argument.SubArgument;
import org.luckyraven.gangland.command.argument.types.OptionalArgument;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.CopService;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.targeting.TargetingManager;
import org.luckyraven.gangland.core.TriConsumer;
import org.luckyraven.gangland.core.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.List;
import java.util.Objects;

class CopListCommand extends SubArgument {

	private final Gangland         gangland;
	private final Tree<Argument>   tree;
	private final TargetingManager targetingManager;
	private final CopManager       copManager;

	CopListCommand(Gangland gangland, Tree<Argument> tree, Argument parent, CopService copService) {
		super(gangland, "list", tree, parent);

		this.gangland         = gangland;
		this.tree             = tree;
		this.targetingManager = copService.getTargetingManager();
		this.copManager       = copService.getCopManager();

		playerTarget();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			List<Player> targetedPlayers = Bukkit.getOnlinePlayers()
					.stream()
					.filter(player -> targetingManager.isWanted(player.getUniqueId()))
					.map(Player::getPlayer)
					.toList();

			if (targetedPlayers.isEmpty()) {
				sender.sendMessage(Messages.COP_LIST_NONE.toString());
				return;
			}

			List<String> list = targetedPlayers.stream().map(player -> "&b- &r" + player.getName()).toList();
			sender.sendMessage(GanglandChatUtil.color("&7Players being chased by cops:"));
			sender.sendMessage(GanglandChatUtil.color(String.join("\n", list)));
		};
	}

	private void playerTarget() {
		OptionalArgument targetPlayer = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			String playerName = args[2];
			Player target     = Bukkit.getPlayer(playerName);

			if (target == null || !target.isOnline()) {
				sender.sendMessage(Messages.PLAYER_NOT_FOUND.toString().replace("%player%", playerName));
				return;
			}

			List<CopNpc> cops = copManager.getCopsForPlayer(target.getUniqueId());

			if (cops.isEmpty()) {
				sender.sendMessage(Messages.COP_TARGET_NOT_CHASED.toString()
				                                                 .replace("%target%", target.getName()));
				return;
			}

			sender.sendMessage(
					GanglandChatUtil.color("&7Player &e" + target.getName() + "&7 is being chased by cops:"));
			cops.forEach(cop -> {
				NPC npc = cop.getNpc();
				sender.sendMessage(GanglandChatUtil.color("&b- &a" + npc.getName() + "&7 (" + npc.getUniqueId() + ")"));
			});
		}, sender -> Bukkit.getOnlinePlayers()
				.stream()
				.filter(target -> targetingManager.isWanted(target.getUniqueId()))
				.map(Player::getPlayer)
				.filter(Objects::nonNull)
				.map(Player::getName)
				.toList());

		this.addSubArgument(targetPlayer);
	}
}
