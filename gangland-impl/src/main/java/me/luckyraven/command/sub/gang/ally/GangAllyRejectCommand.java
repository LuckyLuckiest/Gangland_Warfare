package me.luckyraven.command.sub.gang.ally;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.gang.Gang;
import me.luckyraven.gang.GangManager;
import me.luckyraven.gang.member.MemberManager;
import me.luckyraven.gang.user.User;
import me.luckyraven.gang.user.UserManager;
import me.luckyraven.mail.MailItem;
import me.luckyraven.mail.MailManager;
import me.luckyraven.mail.MailType;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

class GangAllyRejectCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final MemberManager       memberManager;
	private final GangManager         gangManager;
	private final MailManager         mailManager;

	GangAllyRejectCommand(Gangland gangland, Tree<Argument> tree, Argument parent, UserManager<Player> userManager,
	                      MemberManager memberManager, GangManager gangManager, MailManager mailManager) {
		super(gangland, "reject", tree, parent);

		this.gangland      = gangland;
		this.tree          = tree;
		this.userManager   = userManager;
		this.memberManager = memberManager;
		this.gangManager   = gangManager;
		this.mailManager   = mailManager;

		this.addSubArgument(senderGangArgument());
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
			if (userGang == null) return;

			List<MailItem> incoming = mailManager.findPendingForRecipientGang(userGang.getId(),
			                                                                  MailType.GANG_ALLY_REQUEST);
			if (incoming.isEmpty()) {
				user.sendMessage(Messages.NO_GANG_INVITATION.toString());
				return;
			}

			MailItem mail    = incoming.getFirst();
			Gang     sending = gangManager.getGang(mail.getSenderGangId());

			if (sending == null) {
				mailManager.cancel(mail);
				user.sendMessage(Messages.NO_GANG_INVITATION.toString());
				return;
			}

			if (incoming.size() > 1) {
				user.sendMessage(Messages.GANG_ALLY_REJECT_MULTIPLE.toString()
				                                                   .replace("%count%", String.valueOf(incoming.size()))
				                                                   .replace("%gang%", sending.getDisplayNameString()));
			}

			doReject(userGang, sending, mail);
		};
	}

	private Map<String, String> buildIncomingSenderMap(CommandSender sender) {
		if (!(sender instanceof Player player)) return new HashMap<>();
		User<Player> user = userManager.getUser(player);
		if (user == null || !user.hasGang()) return new HashMap<>();

		Gang userGang = gangManager.getGang(user.getGangId());
		if (userGang == null) return new HashMap<>();

		List<MailItem> incoming = mailManager.findPendingForRecipientGang(userGang.getId(),
		                                                                  MailType.GANG_ALLY_REQUEST);

		List<Gang> senders = new ArrayList<>();
		for (MailItem mail : incoming) {
			Gang sending = gangManager.getGang(mail.getSenderGangId());
			if (sending != null) senders.add(sending);
		}

		Map<String, Integer> nameCount = new HashMap<>();
		for (Gang s : senders) nameCount.merge(s.getName(), 1, Integer::sum);

		Map<String, String> map = new HashMap<>();
		for (Gang s : senders) {
			String name        = s.getName();
			String displayName = nameCount.get(name) > 1 ? name + ":" + s.getId() : name;
			map.put(displayName, String.valueOf(s.getId()));
		}
		return map;
	}

	private OptionalArgument senderGangArgument() {
		return new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			OptionalArgument optionalArgument = (OptionalArgument) argument;

			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			if (!user.hasGang()) {
				sender.sendMessage(Messages.MUST_CREATE_GANG.toString());
				return;
			}

			Gang userGang = gangManager.getGang(user.getGangId());
			if (userGang == null) return;

			String value = optionalArgument.getActualValue(args[3], sender);

			int senderId;
			try {
				senderId = Integer.parseInt(value);
			} catch (NumberFormatException exception) {
				user.sendMessage(Messages.MUST_BE_NUMBERS.toString().replace("%command%", value));
				return;
			}

			Optional<MailItem> pending = mailManager.findPendingBetweenGangs(senderId, userGang.getId(),
			                                                                 MailType.GANG_ALLY_REQUEST);
			Gang sending = gangManager.getGang(senderId);

			if (pending.isEmpty() || sending == null) {
				String name = sending == null ? value : sending.getDisplayNameString();
				user.sendMessage(Messages.GANG_ALLY_NO_REQUEST_FROM.toString().replace("%gang%", name));
				return;
			}

			doReject(userGang, sending, pending.get());
		}, sender -> new ArrayList<>(buildIncomingSenderMap(sender).keySet()), this::buildIncomingSenderMap);
	}

	private void doReject(Gang userGang, Gang sending, MailItem mail) {
		Bukkit.getOnlinePlayers()
				.stream()
				.filter(onlinePlayer -> memberManager.getMember(onlinePlayer.getUniqueId()).getGangId() ==
				                        sending.getId())
				.toList()
				.forEach(pl -> pl.sendMessage(
						Messages.GANG_ALLY_REJECT.toString().replace("%gang%", userGang.getDisplayNameString())));

		Bukkit.getOnlinePlayers()
				.stream()
				.filter(onlinePlayer -> memberManager.getMember(onlinePlayer.getUniqueId()).getGangId() ==
				                        userGang.getId())
				.toList()
				.forEach(pl -> pl.sendMessage(
						Messages.GANG_ALLY_REJECT.toString().replace("%gang%", sending.getDisplayNameString())));

		mailManager.reject(mail);
	}

}
