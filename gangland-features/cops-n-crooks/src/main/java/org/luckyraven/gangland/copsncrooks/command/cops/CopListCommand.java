package org.luckyraven.gangland.copsncrooks.command.cops;

import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.gangland.copsncrooks.command.CommandMessages;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.CopService;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.targeting.TargetingManager;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;

import java.util.List;
import java.util.Map;
import java.util.Objects;

class CopListCommand extends SubArgument {

	private final JavaPlugin         plugin;
	private final Tree<Argument>     tree;
	private final TargetingManager   targetingManager;
	private final CopManager         copManager;
	private final CommandMessages    messages;

	CopListCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent, CopService copService,
	               CommandMessages messages) {
		super(plugin, "list", tree, parent);

		this.plugin           = plugin;
		this.tree             = tree;
		this.targetingManager = copService.getTargetingManager();
		this.copManager       = copService.getCopManager();
		this.messages         = messages;

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
				sender.sendMessage(messages.format(CommandMessages.Key.COP_LIST_EMPTY, Map.of()));
				return;
			}

			sender.sendMessage(messages.format(CommandMessages.Key.COP_LIST_HEADER,
			                                   Map.of("count", String.valueOf(targetedPlayers.size()))));
			targetedPlayers.forEach(player -> sender.sendMessage(messages.format(CommandMessages.Key.COP_LIST_ROW,
			                                                                     Map.of("player", player.getName()))));
		};
	}

	private void playerTarget() {
		OptionalArgument targetPlayer = new OptionalArgument(plugin, tree, (argument, sender, args) -> {
			String playerName = args[2];
			Player target     = Bukkit.getPlayer(playerName);

			if (target == null || !target.isOnline()) {
				sender.sendMessage(messages.format(CommandMessages.Key.PLAYER_NOT_FOUND,
                                                   Map.of("player", playerName)));
				return;
			}

			List<CopNpc> cops = copManager.getCopsForPlayer(target.getUniqueId());

			if (cops.isEmpty()) {
				sender.sendMessage(messages.format(CommandMessages.Key.COP_TARGET_NOT_CHASED,
                                                   Map.of("target", target.getName())));
				return;
			}

			sender.sendMessage(messages.format(CommandMessages.Key.COP_TARGET_HEADER,
                                               Map.of("player", target.getName())));
			cops.forEach(cop -> {
				NPC npc = cop.getNpc();
				sender.sendMessage(messages.format(CommandMessages.Key.COP_TARGET_ROW,
				                                   Map.of("callsign", CopRadio.callsign(cop),
				                                          "uuid", String.valueOf(npc.getUniqueId()))));
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
