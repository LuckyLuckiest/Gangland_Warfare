package org.luckyraven.gangland.command.sub.gang.ally;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.command.argument.SubArgument;
import org.luckyraven.gangland.command.argument.types.OptionalArgument;
import org.luckyraven.gangland.core.TriConsumer;
import org.luckyraven.gangland.core.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.mail.MailItem;
import org.luckyraven.gangland.mail.MailManager;
import org.luckyraven.gangland.mail.MailStatus;
import org.luckyraven.gangland.mail.MailType;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

class GangAllyRequestCommand extends SubArgument {

	private static final long REQUEST_EXPIRY_MS = 60_000L;

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final MemberManager       memberManager;
	private final GangManager         gangManager;
	private final MailManager         mailManager;

	GangAllyRequestCommand(Gangland gangland, Tree<Argument> tree, Argument parent, UserManager<Player> userManager,
	                       MemberManager memberManager, GangManager gangManager, MailManager mailManager) {
		super(gangland, "request", tree, parent);

		this.gangland      = gangland;
		this.tree          = tree;
		this.userManager   = userManager;
		this.memberManager = memberManager;
		this.gangManager   = gangManager;
		this.mailManager   = mailManager;

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
	 * Disambiguated display-name → id map of gangs the sender's gang could request an alliance with — every gang except
	 * the sender's own and any gang already allied to it.
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

			if (mailManager.findPendingBetweenGangs(sending.getId(), receiving.getId(), MailType.GANG_ALLY_REQUEST)
			               .isPresent()) {
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

			long now      = System.currentTimeMillis();
			long expireAt = now + REQUEST_EXPIRY_MS;
			MailItem mail = new MailItem(mailManager.allocateId(), MailType.GANG_ALLY_REQUEST, null, sending.getId(),
			                             null, receiving.getId(), null, now, expireAt, MailStatus.PENDING, false);
			if (!receiving.hasAnyMemberOnline()) {
				mail.setPausedAt(now);
			}
			mailManager.send(mail);
		}, sender -> new ArrayList<>(buildRequestableGangMap(sender).keySet()), this::buildRequestableGangMap);
	}

}
