package me.luckyraven.command.sub.gang.ally;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.core.timer.CountdownTimer;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.gang.Gang;
import me.luckyraven.gang.GangManager;
import me.luckyraven.gang.member.MemberManager;
import me.luckyraven.gang.user.User;
import me.luckyraven.gang.user.UserManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;

class GangAllyAcceptCommand extends SubArgument {

	private final UserManager<Player>           userManager;
	private final MemberManager                 memberManager;
	private final GangManager                   gangManager;
	private final HashMap<Gang, Gang>           gangsIdMap;
	private final HashMap<Gang, CountdownTimer> gangRequestTimer;

	GangAllyAcceptCommand(Gangland gangland, Tree<Argument> tree, Argument parent, UserManager<Player> userManager,
	                      MemberManager memberManager, GangManager gangManager,
	                      HashMap<Gang, Gang> gangsIdMap, HashMap<Gang, CountdownTimer> gangRequestTimer) {
		super(gangland, "accept", tree, parent);

		this.userManager      = userManager;
		this.memberManager    = memberManager;
		this.gangManager      = gangManager;
		this.gangsIdMap       = gangsIdMap;
		this.gangRequestTimer = gangRequestTimer;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			if (!user.hasGang()) {
				sender.sendMessage(Messages.MUST_CREATE_GANG.toString());
				return;
			}

			Gang userGang = gangManager.getGang(user.getGangId());

			Gang receiving = gangsIdMap.keySet()
					.stream().filter(gang -> gang == userGang).findFirst().orElse(null);

			if (receiving == null) {
				user.sendMessage(Messages.NO_GANG_INVITATION.toString());
				return;
			}

			Gang sending = gangsIdMap.get(receiving);

			if (receiving.isAlly(sending)) {
				user.sendMessage(Messages.ALREADY_ALLIED_GANG.toString());
				return;
			}

			receiving.addAlly(sending);
			sending.addAlly(receiving);

			Bukkit.getOnlinePlayers()
					.stream()
					.filter(onlinePlayer -> memberManager.getMember(onlinePlayer.getUniqueId()).getGangId() ==
					                        sending.getId())
					.toList()
					.forEach(pl -> pl.sendMessage(
							Messages.GANG_ALLY_ACCEPT.toString().replace("%gang%", receiving.getDisplayNameString())));

			Bukkit.getOnlinePlayers()
					.stream()
					.filter(onlinePlayer -> memberManager.getMember(onlinePlayer.getUniqueId()).getGangId() ==
					                        receiving.getId())
					.toList()
					.forEach(pl -> pl.sendMessage(
							Messages.GANG_ALLY_ACCEPT.toString().replace("%gang%", sending.getDisplayNameString())));

			gangsIdMap.remove(receiving);

			CountdownTimer timer = gangRequestTimer.get(receiving);
			if (timer != null) {
				if (!timer.isCancelled()) timer.cancel();
				gangRequestTimer.remove(receiving);
			}
		};
	}

}
