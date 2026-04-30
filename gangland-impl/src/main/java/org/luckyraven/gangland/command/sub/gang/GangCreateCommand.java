package org.luckyraven.gangland.command.sub.gang;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.command.argument.SubArgument;
import org.luckyraven.gangland.command.argument.types.ConfirmArgument;
import org.luckyraven.gangland.command.argument.types.OptionalArgument;
import org.luckyraven.gangland.core.TriConsumer;
import org.luckyraven.gangland.core.datastructure.Tree;
import org.luckyraven.gangland.core.timer.CountdownTimer;
import org.luckyraven.gangland.core.utilities.TimeUtil;
import org.luckyraven.gangland.database.GanglandDatabase;
import org.luckyraven.gangland.economy.exception.EconomyException;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.persistence.repository.IRepository;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.gangland.util.TimeMessages;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

class GangCreateCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final MemberManager       memberManager;
	private final GangManager         gangManager;
	private final RankManager         rankManager;
	private final GanglandDatabase    ganglandDatabase;

	protected GangCreateCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                            UserManager<Player> userManager, MemberManager memberManager, GangManager gangManager,
	                            RankManager rankManager, GanglandDatabase ganglandDatabase) {
		super(gangland, "create", tree, parent);

		this.gangland         = gangland;
		this.tree             = tree;
		this.userManager      = userManager;
		this.memberManager    = memberManager;
		this.gangManager      = gangManager;
		this.rankManager      = rankManager;
		this.ganglandDatabase = ganglandDatabase;

		gangCreate();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			if (user.hasGang()) {
				user.sendMessage(Messages.PLAYER_IN_GANG.toString());
				return;
			}

			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<name>"));
		};
	}

	private void gangCreate() {
		Map<User<Player>, AtomicReference<String>> createGangName  = new HashMap<>();
		Map<CommandSender, CountdownTimer>         createGangTimer = new HashMap<>();

		ConfirmArgument confirmCreate = new ConfirmArgument(gangland, tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			Member member = memberManager.getMember(player.getUniqueId());

			if (user.hasGang()) {
				user.sendMessage(Messages.PLAYER_IN_GANG.toString());
				return;
			}

			try {
				user.getEconomy().withdrawAmount(Settings.getGangCreateFee());
			} catch (EconomyException exception) {
				CountdownTimer timer = createGangTimer.get(sender);
				if (timer != null) {
					timer.stop();
					createGangTimer.remove(sender);
				}

				user.sendMessage(Messages.CANNOT_CREATE_GANG.toString());
				return;
			}

			int id = Gang.generateId();

			while (gangManager.getGang(id) != null) {
				id = Gang.generateId();
			}

			Gang gang = new Gang(id);

			member.setGangJoinDateLong(Instant.now().toEpochMilli());
			gang.addMember(user, member, rankManager.get(Settings.getGangRankTail()));
			gang.setName(createGangName.get(user).get());
			gang.getEconomy().setAmount(Settings.getGangInitialBalance());

			gangManager.add(gang);

			// Persist immediately so a reload before the next auto-save doesn't desync gang/member rows
			IRepository<Gang>   gangRepository   = ganglandDatabase.getRepositoryRegistry().getRepository(Gang.class);
			IRepository<Member> memberRepository = ganglandDatabase.getRepositoryRegistry().getRepository(Member.class);
			gangRepository.save(gang);
			memberRepository.save(member);

			user.sendMessage(Messages.GANG_CREATED.toString().replace("%gang%", gang.getDisplayNameString()));

			createGangName.remove(user);

			CountdownTimer timer = createGangTimer.get(sender);
			if (timer != null) {
				timer.stop();
				createGangTimer.remove(sender);
			}
		});

		this.addSubArgument(confirmCreate);

		Argument createName = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			if (user.hasGang()) {
				user.sendMessage(Messages.PLAYER_IN_GANG.toString());
				return;
			}

			if (confirmCreate.isLocked(sender)) return;

			AtomicReference<String> name = new AtomicReference<>(args[2]);

			if (!Settings.isGangNameDuplicates()) for (Gang gang : gangManager.getGangs().values())
				if (gang.getName().equalsIgnoreCase(name.get())) {
					user.sendMessage(Messages.DUPLICATE_GANG_NAME.toString().replace("%gang%", name.get()));
					return;
				}

			createGangName.put(user, name);

			// Need to notify the player and give access to confirm
			String string  = Messages.GANG_CREATE_FEE.toString();
			String replace = string.replace("%amount%", Settings.formatAmount(Settings.getGangCreateFee()));

			user.sendMessage(replace);
			user.sendMessage(GanglandChatUtil.confirmCommand(new String[]{"gang", "create"}));

			confirmCreate.lock(sender, s -> {
				CountdownTimer timer = new CountdownTimer(gangland, 60, null, time -> {
					if (time.getTimeLeft() % 20 != 0) return;

					String string1     = Messages.GANG_CREATE_CONFIRM.toString();
					String replacement = TimeUtil.formatTime(time.getTimeLeft(), true, TimeMessages.getInstance());
					String replace1    = string1.replace("%timer%", replacement);

					s.sendMessage(replace1);
				}, time -> {
					confirmCreate.unlock(s);
					createGangName.remove(user);
					createGangTimer.remove(s);
				});

				timer.start(false);
				createGangTimer.put(s, timer);
			});
		}, sender -> List.of("<name>"));

		this.addSubArgument(createName);
	}

}
