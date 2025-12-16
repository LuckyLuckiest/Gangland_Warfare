package me.luckyraven.command.sub.gang;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.data.account.gang.Gang;
import me.luckyraven.data.account.gang.GangManager;
import me.luckyraven.data.account.gang.MemberManager;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.Pair;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import me.luckyraven.util.timer.CountdownTimer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class GangAllyCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final MemberManager       memberManager;
	private final GangManager         gangManager;

	protected GangAllyCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "ally", tree, parent);

		this.gangland = gangland;
		this.tree     = tree;

		this.userManager   = gangland.getInitializer().getUserManager();
		this.memberManager = gangland.getInitializer().getMemberManager();
		this.gangManager   = gangland.getInitializer().getGangManager();

		gangAlly();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.hasGang()) {
				sender.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<request/abandon>"));
		};
	}

	private void gangAlly() {
		Argument requestAlly = getRequestAlly();

		// glw gang ally abandon <id>
		Argument abandonAlly = getAbandonAlly();

		// key -> the gang requesting alliance with, value -> the gang sending the request
		HashMap<Gang, Gang>           gangsIdMap       = new HashMap<>();
		HashMap<Gang, CountdownTimer> gangRequestTimer = new HashMap<>();

		Argument allyId = getAllyId(gangsIdMap, gangRequestTimer);

		requestAlly.addSubArgument(allyId);
		abandonAlly.addSubArgument(allyId);

		// glw gang ally accept
		Argument allyAccept = getAllyAccept(gangsIdMap, gangRequestTimer);

		Argument allyReject = getAllyReject(gangsIdMap, gangRequestTimer);

		this.addSubArgument(requestAlly);
		this.addSubArgument(abandonAlly);
		this.addSubArgument(allyAccept);
		this.addSubArgument(allyReject);
	}

	private @NotNull Argument getRequestAlly() {
		return new Argument(gangland, "request", tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.hasGang()) {
				sender.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<id>"));
		}, this.getPermission() + ".request");
	}

	private @NotNull Argument getAbandonAlly() {
		return new Argument(gangland, "abandon", tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.hasGang()) {
				sender.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<id>"));
		}, this.getPermission() + ".abandon");
	}

	private @NotNull Argument getAllyId(HashMap<Gang, Gang> gangsIdMap,
										HashMap<Gang, CountdownTimer> gangRequestTimer) {
		return new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			OptionalArgument optionalArgument = (OptionalArgument) argument;

			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.hasGang()) {
				sender.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			String value = optionalArgument.getActualValue(args[3], sender);

			int id;

			try {
				id = Integer.parseInt(value);
			} catch (NumberFormatException exception) {
				user.sendMessage(MessageAddon.MUST_BE_NUMBERS.toString().replace("%command%", value));
				return;
			}

			// should not be null
			Gang sending = gangManager.getGang(user.getGangId());
			// can be null
			Gang receiving = gangManager.getGang(id);

			if (receiving == null) {
				user.sendMessage(MessageAddon.GANG_DOESNT_EXIST.toString());
				return;
			}

			switch (args[2].toLowerCase()) {
				case "request" -> {
					// check if they are allied before proceeding
					if (receiving.isAlly(sending)) {
						user.sendMessage(MessageAddon.ALREADY_ALLIED_GANG.toString());
						return;
					}

					if (gangsIdMap.containsKey(receiving)) {
						user.sendMessage(MessageAddon.GANG_ALLIANCE_ALREADY_SENT.toString());
						return;
					}

					// send a message to every member in the sending gang
					Bukkit.getOnlinePlayers()
							.stream()
							.filter(onlinePlayer -> memberManager.getMember(onlinePlayer.getUniqueId()).getGangId() ==
													sending.getId())
							.toList()
							.forEach(pl -> pl.sendMessage(MessageAddon.GANG_ALLY_SEND_REQUEST.toString()
																							 .replace("%gang%",
																									  receiving.getDisplayNameString())));

					// send a message to every member in receiving gang
					Bukkit.getOnlinePlayers()
							.stream()
							.filter(onlinePlayer -> memberManager.getMember(onlinePlayer.getUniqueId()).getGangId() ==
													receiving.getId())
							.toList()
							.forEach(pl -> pl.sendMessage(MessageAddon.GANG_ALLY_RECEIVE_REQUEST.toString()
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
					// send a message to every member in the sending gang
					Bukkit.getOnlinePlayers()
							.stream()
							.filter(onlinePlayer -> memberManager.getMember(onlinePlayer.getUniqueId()).getGangId() ==
													sending.getId())
							.toList()
							.forEach(pl -> pl.sendMessage(MessageAddon.GANG_ALLY_ABANDON.toString()
																						.replace("%gang%",
																								 receiving.getDisplayNameString())));

					// send a message to every member in receiving gang
					Bukkit.getOnlinePlayers()
							.stream()
							.filter(onlinePlayer -> memberManager.getMember(onlinePlayer.getUniqueId()).getGangId() ==
													receiving.getId())
							.toList()
							.forEach(pl -> pl.sendMessage(MessageAddon.GANG_ALLY_ABANDON.toString()
																						.replace("%gang%",
																								 sending.getDisplayNameString())));

					sending.removeAlly(receiving);
					receiving.removeAlly(sending);
				}
			}

		}, sender -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.hasGang()) {
				return null;
			}

			Gang gang = gangManager.getGang(user.getGangId());

			return gang.getAllies()
					.stream().map(Pair::first).map(Gang::getName).toList();
		}, sender -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.hasGang()) {
				return null;
			}

			Gang gang = gangManager.getGang(user.getGangId());

			List<Gang> allies = gang.getAllies()
					.stream().map(Pair::first).toList();

			// First pass: count how many times each name appears
			Map<String, Integer> nameCount = new HashMap<>();
			for (Gang ally : allies) {
				String name = ally.getName();
				nameCount.put(name, nameCount.getOrDefault(name, 0) + 1);
			}

			Map<String, String> gangs = new HashMap<>();

			for (Gang ally : allies) {
				String name        = ally.getName();
				String displayName = nameCount.get(name) > 1 ? name + ":" + ally.getId() : name;

				gangs.put(displayName, String.valueOf(ally.getId()));
			}

			return gangs;
		});
	}

	private @NotNull Argument getAllyAccept(HashMap<Gang, Gang> gangsIdMap,
											HashMap<Gang, CountdownTimer> gangRequestTimer) {
		return new Argument(gangland, "accept", tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.hasGang()) {
				sender.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			Gang userGang = gangManager.getGang(user.getGangId());

			// finds the key if it was similar to acceptor gang
			Gang receiving = gangsIdMap.keySet()
					.stream().filter(gang -> gang == userGang).findFirst().orElse(null);

			if (receiving == null) {
				user.sendMessage(MessageAddon.NO_GANG_INVITATION.toString());
				return;
			}

			Gang sending = gangsIdMap.get(receiving);

			// check if they are allied before proceeding
			if (receiving.isAlly(sending)) {
				user.sendMessage(MessageAddon.ALREADY_ALLIED_GANG.toString());
				return;
			}

			// add both ally
			receiving.addAlly(sending);
			sending.addAlly(receiving);

			// send a message to every member in the sending gang
			Bukkit.getOnlinePlayers()
					.stream()
					.filter(onlinePlayer -> memberManager.getMember(onlinePlayer.getUniqueId()).getGangId() ==
											sending.getId())
					.toList()
					.forEach(pl -> pl.sendMessage(MessageAddon.GANG_ALLY_ACCEPT.toString()
																			   .replace("%gang%",
																						receiving.getDisplayNameString())));

			// send a message to every member in receiving gang
			Bukkit.getOnlinePlayers()
					.stream()
					.filter(onlinePlayer -> memberManager.getMember(onlinePlayer.getUniqueId()).getGangId() ==
											receiving.getId())
					.toList()
					.forEach(pl -> pl.sendMessage(MessageAddon.GANG_ALLY_ACCEPT.toString()
																			   .replace("%gang%",
																						sending.getDisplayNameString())));

			gangsIdMap.remove(receiving);

			CountdownTimer timer = gangRequestTimer.get(receiving);
			if (timer != null) {
				if (!timer.isCancelled()) timer.cancel();
				gangRequestTimer.remove(receiving);
			}
		}, this.getPermission() + ".accept");
	}

	private @NotNull Argument getAllyReject(HashMap<Gang, Gang> gangsIdMap,
											HashMap<Gang, CountdownTimer> gangRequestTimer) {
		return new Argument(gangland, "reject", tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (!user.hasGang()) {
				sender.sendMessage(MessageAddon.MUST_CREATE_GANG.toString());
				return;
			}

			Gang userGang = gangManager.getGang(user.getGangId());

			// finds the key if it was similar to acceptor gang
			Gang receiving = gangsIdMap.keySet()
					.stream().filter(gang -> gang == userGang).findFirst().orElse(null);

			if (receiving == null) {
				user.sendMessage(MessageAddon.NO_GANG_INVITATION.toString());
				return;
			}

			Gang sending = gangsIdMap.get(receiving);

			// check if they are allied before proceeding
			if (receiving.isAlly(sending)) {
				user.sendMessage(MessageAddon.ALREADY_ALLIED_GANG.toString());
				return;
			}

			// send a message to every member in the sending gang
			Bukkit.getOnlinePlayers()
					.stream()
					.filter(onlinePlayer -> memberManager.getMember(onlinePlayer.getUniqueId()).getGangId() ==
											sending.getId())
					.toList()
					.forEach(pl -> pl.sendMessage(MessageAddon.GANG_ALLY_REJECT.toString()
																			   .replace("%gang%",
																						receiving.getDisplayNameString())));

			// send a message to every member in receiving gang
			Bukkit.getOnlinePlayers()
					.stream()
					.filter(onlinePlayer -> memberManager.getMember(onlinePlayer.getUniqueId()).getGangId() ==
											receiving.getId())
					.toList()
					.forEach(pl -> pl.sendMessage(MessageAddon.GANG_ALLY_REJECT.toString()
																			   .replace("%gang%",
																						sending.getDisplayNameString())));

			gangsIdMap.remove(receiving);

			CountdownTimer timer = gangRequestTimer.get(receiving);
			if (timer != null) {
				if (!timer.isCancelled()) timer.cancel();
				gangRequestTimer.remove(receiving);
			}
		}, this.getPermission() + ".reject");
	}

}
