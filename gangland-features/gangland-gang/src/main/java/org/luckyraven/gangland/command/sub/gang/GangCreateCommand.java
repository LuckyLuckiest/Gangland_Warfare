package org.luckyraven.gangland.command.sub.gang;

import org.luckyraven.gangland.gang.GangSettings;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.ConfirmArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.timer.CountdownTimer;
import org.luckyraven.keystone.util.TimeUtil;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;
import org.luckyraven.keystone.economy.exception.EconomyException;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.keystone.persistence.repository.IRepository;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.gangland.util.TimeMessages;
import org.luckyraven.gangland.gang.file.GangMessages;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import org.luckyraven.keystone.bean.Qualifier;

class GangCreateCommand extends SubArgument {

	private final JavaPlugin            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final MemberManager       memberManager;
	private final GangManager         gangManager;
	private final RankManager         rankManager;
	private final RepositoryRegistry  repositoryRegistry;
	private final Pattern             namePattern;
	private final GangMessages        gangMessages;

	protected GangCreateCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent,
	                            @Qualifier("online") UserManager<Player> userManager, MemberManager memberManager, GangManager gangManager,
	                            RankManager rankManager, RepositoryRegistry repositoryRegistry, Pattern namePattern,
	                            GangMessages gangMessages) {
		super(gangland, "create", tree, parent);

		this.gangland           = gangland;
		this.tree               = tree;
		this.userManager        = userManager;
		this.memberManager      = memberManager;
		this.gangManager        = gangManager;
		this.rankManager        = rankManager;
		this.repositoryRegistry = repositoryRegistry;
		this.namePattern        = namePattern;
		this.gangMessages       = gangMessages;

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

			// GR-01: guard before the creation fee is withdrawn — a player with no cached Member cannot be put in
			// a gang, and NPEing after the withdrawal would burn the fee.
			if (member == null) {
				user.sendMessage(Messages.CANNOT_CREATE_GANG.toString());
				return;
			}

			if (user.hasGang()) {
				user.sendMessage(Messages.PLAYER_IN_GANG.toString());
				return;
			}

			try {
				user.getEconomy().withdrawAmount(GangSettings.getGangCreateFee());
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
			gang.addMember(user, member, rankManager.get(GangSettings.getGangRankTail()));
			gang.setName(createGangName.get(user).get());
			gang.getEconomy().setAmount(GangSettings.getGangInitialBalance());

			gangManager.add(gang);

			// Persist immediately so a reload before the next auto-save doesn't desync gang/member rows
			IRepository<Gang>   gangRepository   = repositoryRegistry.getRepository(Gang.class);
			IRepository<Member> memberRepository = repositoryRegistry.getRepository(Member.class);
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

			// GR-29: names are the command key, so a bad or taken name is refused whatever Name_Duplicates says.
			Optional<String> refusal = GangNameRules.check(name.get(), namePattern, gangManager.getGangs().values(),
			                                               null);
			if (refusal.isPresent()) {
				user.sendMessage(GangNameRules.refusal(gangMessages, refusal.get(), name.get()));
				return;
			}

			createGangName.put(user, name);

			// Need to notify the player and give access to confirm
			String string  = Messages.GANG_CREATE_FEE.toString();
			String replace = string.replace("%amount%", Settings.formatAmount(GangSettings.getGangCreateFee()));

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
