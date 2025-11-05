package me.luckyraven.command.sub.gang;

import me.luckyraven.Gangland;
import me.luckyraven.TriConsumer;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.ConfirmArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.data.account.gang.Gang;
import me.luckyraven.data.account.gang.GangManager;
import me.luckyraven.data.account.gang.Member;
import me.luckyraven.data.account.gang.MemberManager;
import me.luckyraven.data.rank.RankManager;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.datastructure.Tree;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.timer.CountdownTimer;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.TimeUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

class GangCreateCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final MemberManager       memberManager;
	private final GangManager         gangManager;
	private final RankManager         rankManager;

	protected GangCreateCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "create", tree, parent);

		this.gangland = gangland;
		this.tree     = tree;

		this.userManager   = gangland.getInitializer().getUserManager();
		this.memberManager = gangland.getInitializer().getMemberManager();
		this.gangManager   = gangland.getInitializer().getGangManager();
		this.rankManager   = gangland.getInitializer().getRankManager();

		gangCreate();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user.hasGang()) {
				player.sendMessage(MessageAddon.PLAYER_IN_GANG.toString());
				return;
			}

			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<name>"));
		};
	}

	private void gangCreate() {
		Map<User<Player>, AtomicReference<String>> createGangName  = new HashMap<>();
		Map<CommandSender, CountdownTimer>         createGangTimer = new HashMap<>();

		ConfirmArgument confirmCreate = new ConfirmArgument(gangland, tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);
			Member       member = memberManager.getMember(player.getUniqueId());

			if (user.hasGang()) {
				player.sendMessage(MessageAddon.PLAYER_IN_GANG.toString());
				return;
			}

			if (user.getEconomy().getBalance() < SettingAddon.getGangCreateFee()) {
				player.sendMessage(MessageAddon.CANNOT_CREATE_GANG.toString());
				return;
			}

			Gang gang = new Gang();

			while (gangManager.contains(gang)) gang.setId(Gang.generateId());

			member.setGangJoinDateLong(Instant.now().toEpochMilli());
			gang.addMember(user, member, rankManager.get(SettingAddon.getGangRankTail()));
			gang.setName(createGangName.get(user).get());
			gang.getEconomy().setBalance(SettingAddon.getGangInitialBalance());
			user.getEconomy().withdraw(SettingAddon.getGangCreateFee());

			gangManager.add(gang);

			player.sendMessage(MessageAddon.GANG_CREATED.toString().replace("%gang%", gang.getDisplayNameString()));

			createGangName.remove(user);

			CountdownTimer timer = createGangTimer.get(sender);
			if (timer != null) {
				if (!timer.isCancelled()) timer.cancel();
				createGangTimer.remove(sender);
			}
		});

		this.addSubArgument(confirmCreate);

		Argument createName = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user.hasGang()) {
				player.sendMessage(MessageAddon.PLAYER_IN_GANG.toString());
				return;
			}

			if (confirmCreate.isConfirmed()) return;

			AtomicReference<String> name = new AtomicReference<>(args[2]);

			if (!SettingAddon.isGangNameDuplicates()) for (Gang gang : gangManager.getGangs().values())
				if (gang.getName().equalsIgnoreCase(name.get())) {
					player.sendMessage(MessageAddon.DUPLICATE_GANG_NAME.toString().replace("%gang%", name.get()));
					return;
				}

			createGangName.put(user, name);

			// Need to notify the player and give access to confirm
			player.sendMessage(MessageAddon.GANG_CREATE_FEE.toString()
														   .replace("%amount%", SettingAddon.formatDouble(
																   SettingAddon.getGangCreateFee())));
			player.sendMessage(ChatUtil.confirmCommand(new String[]{"gang", "create"}));
			confirmCreate.setConfirmed(true);

			CountdownTimer timer = new CountdownTimer(gangland, 60, null, time -> {
				if (time.getTimeLeft() % 20 != 0) return;

				sender.sendMessage(MessageAddon.GANG_CREATE_CONFIRM.toString()
																   .replace("%timer%",
																			TimeUtil.formatTime(time.getTimeLeft(),
																								true)));
			}, time -> {
				confirmCreate.setConfirmed(false);
				createGangName.remove(user);
				createGangTimer.remove(sender);
			});

			timer.start(false);
			createGangTimer.put(sender, timer);
		});

		this.addSubArgument(createName);
	}

}
