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
import me.luckyraven.gang.GangAlliance;
import me.luckyraven.gang.GangManager;
import me.luckyraven.gang.member.MemberManager;
import me.luckyraven.gang.user.User;
import me.luckyraven.gang.user.UserManager;
import me.luckyraven.util.GanglandChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GangAllyCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final MemberManager       memberManager;
	private final GangManager         gangManager;

	// key -> the gang requesting alliance with, value -> the gang sending the request.
	// Shared across request / abandon / accept / reject. Retired in Phase 3 when the mail system lands.
	private final HashMap<Gang, Gang>           gangsIdMap       = new HashMap<>();
	private final HashMap<Gang, CountdownTimer> gangRequestTimer = new HashMap<>();

	public GangAllyCommand(Gangland gangland, Tree<Argument> tree, Argument parent, UserManager<Player> userManager,
	                       MemberManager memberManager, GangManager gangManager) {
		super(gangland, "ally", tree, parent);

		this.gangland      = gangland;
		this.tree          = tree;
		this.userManager   = userManager;
		this.memberManager = memberManager;
		this.gangManager   = gangManager;

		initializeArguments();
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

			sender.sendMessage(
					GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<request/abandon>"));
		};
	}

	private void initializeArguments() {
		OptionalArgument sharedAllyId = buildSharedAllyId();

		GangAllyRequestCommand request = new GangAllyRequestCommand(gangland, tree, this, userManager, sharedAllyId);
		GangAllyAbandonCommand abandon = new GangAllyAbandonCommand(gangland, tree, this, userManager, sharedAllyId);
		GangAllyAcceptCommand accept = new GangAllyAcceptCommand(gangland, tree, this, userManager, memberManager,
		                                                         gangManager, gangsIdMap, gangRequestTimer);
		GangAllyRejectCommand reject = new GangAllyRejectCommand(gangland, tree, this, userManager, memberManager,
		                                                         gangManager, gangsIdMap, gangRequestTimer);

		List<Argument> arguments = new ArrayList<>();
		arguments.add(request);
		arguments.add(abandon);
		arguments.add(accept);
		arguments.add(reject);

		this.addAllSubArguments(arguments);
	}

	/**
	 * Builds the disambiguated display-name → id map used by both the tab-completion list and the
	 * {@code displayToValue} lookup. Duplicate names are suffixed with {@code :id} so each ally has a unique display
	 * key — and the same keys are what tab-completion shows, so users can't pick a label that fails the action's
	 * lookup.
	 */
	private Map<String, String> buildAllyMap(CommandSender sender) {
		if (!(sender instanceof Player player)) return new HashMap<>();
		User<Player> user = userManager.getUser(player);
		if (user == null || !user.hasGang()) return new HashMap<>();

		Gang gang = gangManager.getGang(user.getGangId());
		if (gang == null) return new HashMap<>();

		List<Gang> allies = gang.getAllies()
				.stream().map(GangAlliance::ally).toList();

		Map<String, Integer> nameCount = new HashMap<>();
		for (Gang ally : allies) {
			nameCount.merge(ally.getName(), 1, Integer::sum);
		}

		Map<String, String> map = new HashMap<>();
		for (Gang ally : allies) {
			String name        = ally.getName();
			String displayName = nameCount.get(name) > 1 ? name + ":" + ally.getId() : name;
			map.put(displayName, String.valueOf(ally.getId()));
		}
		return map;
	}

	private OptionalArgument buildSharedAllyId() {
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

			// should not be null
			Gang sending = gangManager.getGang(user.getGangId());
			// can be null
			Gang receiving = gangManager.getGang(id);

			if (receiving == null) {
				user.sendMessage(Messages.GANG_DOESNT_EXIST.toString());
				return;
			}

			switch (args[2].toLowerCase()) {
				case "request" -> {
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
				}

				case "abandon" -> {
					Bukkit.getOnlinePlayers()
							.stream()
							.filter(onlinePlayer ->
											memberManager.getMember(onlinePlayer.getUniqueId()).getGangId() ==
									        sending.getId())
							.toList()
							.forEach(pl -> pl.sendMessage(Messages.GANG_ALLY_ABANDON.toString()
							                                                        .replace("%gang%",
							                                                                 receiving.getDisplayNameString())));

					Bukkit.getOnlinePlayers()
							.stream()
							.filter(onlinePlayer ->
											memberManager.getMember(onlinePlayer.getUniqueId()).getGangId() ==
									        receiving.getId())
							.toList()
							.forEach(pl -> pl.sendMessage(Messages.GANG_ALLY_ABANDON.toString()
							                                                        .replace("%gang%",
							                                                                 sending.getDisplayNameString())));

					sending.removeAlly(receiving);
					receiving.removeAlly(sending);
				}
			}

		}, sender -> new ArrayList<>(buildAllyMap(sender).keySet()), this::buildAllyMap);
	}

}
