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
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.mail.MailItem;
import org.luckyraven.gangland.mail.MailManager;
import org.luckyraven.gangland.mail.MailType;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.*;

class GangAllyPendingCancelCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final GangManager         gangManager;
	private final MailManager         mailManager;

	GangAllyPendingCancelCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                             UserManager<Player> userManager, GangManager gangManager, MailManager mailManager) {
		super(gangland, "cancel", tree, parent);

		this.gangland    = gangland;
		this.tree        = tree;
		this.userManager = userManager;
		this.gangManager = gangManager;
		this.mailManager = mailManager;

		this.addSubArgument(targetGangArgument());
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

			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<gang>"));
		};
	}

	/**
	 * Builds the disambiguated display-name → gang-id map of the sender gang's outgoing pending ally requests.
	 */
	private Map<String, String> buildPendingTargetMap(CommandSender sender) {
		if (!(sender instanceof Player player)) return new HashMap<>();
		User<Player> user = userManager.getUser(player);
		if (user == null || !user.hasGang()) return new HashMap<>();

		List<MailItem> outgoing = mailManager.findPendingFromSenderGang(user.getGangId(),
		                                                                MailType.GANG_ALLY_REQUEST);

		List<Gang> targets = new ArrayList<>();
		for (MailItem mail : outgoing) {
			Gang target = gangManager.getGang(mail.getRecipientGangId());
			if (target != null) targets.add(target);
		}

		Map<String, Integer> nameCount = new HashMap<>();
		for (Gang target : targets) nameCount.merge(target.getName(), 1, Integer::sum);

		Map<String, String> map = new HashMap<>();
		for (Gang target : targets) {
			String name        = target.getName();
			String displayName = nameCount.get(name) > 1 ? name + ":" + target.getId() : name;
			map.put(displayName, String.valueOf(target.getId()));
		}
		return map;
	}

	private OptionalArgument targetGangArgument() {
		return new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			OptionalArgument optionalArgument = (OptionalArgument) argument;

			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			if (!user.hasGang()) {
				sender.sendMessage(Messages.MUST_CREATE_GANG.toString());
				return;
			}

			String value = optionalArgument.getActualValue(args[4], sender);

			int targetId;
			try {
				targetId = Integer.parseInt(value);
			} catch (NumberFormatException exception) {
				user.sendMessage(Messages.MUST_BE_NUMBERS.toString().replace("%command%", value));
				return;
			}

			Optional<MailItem> pending = mailManager.findPendingBetweenGangs(user.getGangId(), targetId,
			                                                                 MailType.GANG_ALLY_REQUEST);
			if (pending.isEmpty()) {
				user.sendMessage(Messages.GANG_ALLY_PENDING_CANCEL_NONE.toString());
				return;
			}

			MailItem mail    = pending.get();
			Gang     target  = gangManager.getGang(mail.getRecipientGangId());
			Gang     sender0 = gangManager.getGang(mail.getSenderGangId());

			mailManager.cancel(mail);

			String targetName = target != null ? target.getDisplayNameString() : String.valueOf(targetId);
			user.sendMessage(Messages.GANG_ALLY_PENDING_CANCEL_SENDER.toString().replace("%gang%", targetName));

			// Notify the recipient gang's online members so they know the request was withdrawn.
			if (target != null && sender0 != null) {
				String message = Messages.GANG_ALLY_PENDING_CANCEL_TARGET.toString()
				                                                         .replace("%gang%",
				                                                                  sender0.getDisplayNameString());
				Bukkit.getOnlinePlayers()
						.stream()
						.filter(pl -> {
							User<Player> u = userManager.getUser(pl);
							return u != null && u.hasGang() && u.getGangId() == target.getId();
						})
						.toList()
						.forEach(pl -> pl.sendMessage(message));
			}
		}, sender -> new ArrayList<>(buildPendingTargetMap(sender).keySet()), this::buildPendingTargetMap);
	}

}
