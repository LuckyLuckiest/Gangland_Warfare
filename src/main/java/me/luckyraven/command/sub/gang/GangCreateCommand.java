package me.luckyraven.command.sub.gang;

import me.luckyraven.Gangland;
import me.luckyraven.data.account.gang.Gang;
import me.luckyraven.data.account.gang.GangManager;
import me.luckyraven.data.account.gang.Member;
import me.luckyraven.data.account.gang.MemberManager;
import me.luckyraven.command.argument.*;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.datastructure.Tree;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.data.rank.RankManager;
import me.luckyraven.util.timer.CountdownTimer;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.TimeUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

class GangCreateCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final MemberManager       memberManager;
	private final GangManager         gangManager;
	private final RankManager         rankManager;

	protected GangCreateCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                            UserManager<Player> userManager, MemberManager memberManager, GangManager gangManager,
	                            RankManager rankManager) {
		super("create", tree, parent);

		this.gangland = gangland;
		this.tree = tree;

		this.userManager = userManager;
		this.memberManager = memberManager;
		this.gangManager = gangManager;
		this.rankManager = rankManager;

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
		HashMap<User<Player>, AtomicReference<String>> createGangName  = new HashMap<>();
		HashMap<CommandSender, CountdownTimer>         createGangTimer = new HashMap<>();

		ConfirmArgument confirmCreate = new ConfirmArgument(tree, (argument, sender, args) -> {
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

			Gang   gang   = new Gang();
			Random random = new Random();

			// need only positive ids
			// 2^31 possible ids
			do gang.setId(random.nextInt(Integer.MAX_VALUE));
			while (gangManager.contains(gang));

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

		Argument createName = new OptionalArgument(tree, (argument, sender, args) -> {
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

			CountdownTimer timer = new CountdownTimer(gangland, 60, time -> sender.sendMessage(
					MessageAddon.GANG_CREATE_CONFIRM.toString()
					                                .replace("%timer%", TimeUtil.formatTime(time.getDuration(), true))),
			                                          null, time -> {
				confirmCreate.setConfirmed(false);
				createGangName.remove(user);
				createGangTimer.remove(sender);
			});

			timer.start();
			createGangTimer.put(sender, timer);
		});

		this.addSubArgument(createName);
	}

}
