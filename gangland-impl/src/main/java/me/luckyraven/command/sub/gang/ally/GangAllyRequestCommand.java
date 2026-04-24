package me.luckyraven.command.sub.gang.ally;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.core.timer.CountdownTimer;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.gang.Gang;
import me.luckyraven.gang.GangManager;
import me.luckyraven.gang.member.MemberManager;
import me.luckyraven.gang.user.User;
import me.luckyraven.gang.user.UserManager;
import me.luckyraven.util.GanglandChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

class GangAllyRequestCommand extends SubArgument {

	private final Gangland                      gangland;
	private final Tree<Argument>                tree;
	private final UserManager<Player>           userManager;
	private final MemberManager                 memberManager;
	private final GangManager                   gangManager;
	private final HashMap<Gang, Gang>           gangsIdMap;
	private final HashMap<Gang, CountdownTimer> gangRequestTimer;

	GangAllyRequestCommand(Gangland gangland, Tree<Argument> tree, Argument parent, UserManager<Player> userManager,
	                       MemberManager memberManager, GangManager gangManager,
	                       HashMap<Gang, Gang> gangsIdMap, HashMap<Gang, CountdownTimer> gangRequestTimer) {
		super(gangland, "request", tree, parent);

		this.gangland         = gangland;
		this.tree             = tree;
		this.userManager      = userManager;
		this.memberManager    = memberManager;
		this.gangManager      = gangManager;
		this.gangsIdMap       = gangsIdMap;
		this.gangRequestTimer = gangRequestTimer;

		this.addSubArgument(buildAllyId());
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

			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<id>"));
		};
	}

	/**
	 * Builds the disambiguated display-name → id map of every gang the sender's gang could request an alliance with —
	 * i.e. every gang except the sender's own gang and gangs already allied to it. Duplicate names are suffixed with
	 * {@code :id} so each option has a unique tab-completion key.
	 */
	private Map<String, String> buildRequestableGangMap(CommandSender sender) {
		if (!(sender instanceof Player player)) return new HashMap<>();
		User<Player> user = userManager.getUser(player);
		if (user == null || !user.hasGang()) return new HashMap<>();

		Gang senderGang = gangManager.getGang(user.getGangId());
		if (senderGang == null) return new HashMap<>();

		Collection<Gang> all = gangManager.getGangs().values();

		Map<String, Integer> nameCount = new HashMap<>();
		for (Gang candidate : all) {
			if (candidate == senderGang) continue;
			if (candidate.isAlly(senderGang)) continue;

			nameCount.merge(candidate.getName(), 1, Integer::sum);
		}

		Map<String, String> map = new HashMap<>();
		for (Gang candidate : all) {
			if (candidate == senderGang) continue;
			if (candidate.isAlly(senderGang)) continue;

			String name        = candidate.getName();
			String displayName = nameCount.get(name) > 1 ? name + ":" + candidate.getId() : name;
			map.put(displayName, String.valueOf(candidate.getId()));
		}
		return map;
	}

	private OptionalArgument buildAllyId() {
		return new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			OptionalArgument optionalArgument = (OptionalArgument) argument;

			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			if (!user.hasGang()) {
				sender.sendMessage(Messages.MUST_CREATE_GANG.toString());
				return;
			}

			String value = optionalArgument.getActualValue(args[3], sender);

			int id;

			try {
				id = Integer.parseInt(value);
			} catch (NumberFormatException exception) {
				user.sendMessage(Messages.MUST_BE_NUMBERS.toString().replace("%command%", value));
				return;
			}

			Gang sending   = gangManager.getGang(user.getGangId());
			Gang receiving = gangManager.getGang(id);

			if (receiving == null) {
				user.sendMessage(Messages.GANG_DOESNT_EXIST.toString());
				return;
			}

			if (receiving.isAlly(sending)) {
				user.sendMessage(Messages.ALREADY_ALLIED_GANG.toString());
				return;
			}

			if (gangsIdMap.containsKey(receiving)) {
				user.sendMessage(Messages.GANG_ALLIANCE_ALREADY_SENT.toString());
				return;
			}

			Bukkit.getOnlinePlayers()
					.stream()
					.filter(onlinePlayer ->
									memberManager.getMember(onlinePlayer.getUniqueId()).getGangId() ==
							        sending.getId())
					.toList()
					.forEach(pl -> pl.sendMessage(Messages.GANG_ALLY_SEND_REQUEST.toString()
					                                                             .replace("%gang%",
					                                                                      receiving.getDisplayNameString())));

			Bukkit.getOnlinePlayers()
					.stream()
					.filter(onlinePlayer ->
									memberManager.getMember(onlinePlayer.getUniqueId()).getGangId() ==
							        receiving.getId())
					.toList()
					.forEach(pl -> pl.sendMessage(Messages.GANG_ALLY_RECEIVE_REQUEST.toString()
					                                                                .replace("%gang%",
					                                                                         sending.getDisplayNameString())));

			gangsIdMap.put(receiving, sending);

			CountdownTimer timer = new CountdownTimer(gangland, 60, null, null, time -> {
				gangsIdMap.remove(receiving);
				gangRequestTimer.remove(receiving);
			});

			timer.start(true);
			gangRequestTimer.put(receiving, timer);
		}, sender -> new ArrayList<>(buildRequestableGangMap(sender).keySet()), this::buildRequestableGangMap);
	}

}
