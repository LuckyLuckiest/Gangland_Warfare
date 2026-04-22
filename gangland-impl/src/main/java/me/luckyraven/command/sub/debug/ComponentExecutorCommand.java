package me.luckyraven.command.sub.debug;

import me.luckyraven.Gangland;
import me.luckyraven.command.Command;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.core.bean.Qualifier;
import me.luckyraven.core.bean.command.CommandHandler;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.gang.Gang;
import me.luckyraven.gang.GangManager;
import me.luckyraven.gang.member.Member;
import me.luckyraven.gang.member.MemberManager;
import me.luckyraven.gang.rank.Rank;
import me.luckyraven.gang.rank.RankManager;
import me.luckyraven.gang.user.User;
import me.luckyraven.gang.user.UserManager;
import me.luckyraven.util.GanglandChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

@CommandHandler
public final class ComponentExecutorCommand extends Command {

	private final UserManager<Player> userManager;
	private final MemberManager       memberManager;
	private final GangManager         gangManager;
	private final RankManager         rankManager;

	public ComponentExecutorCommand(Gangland gangland,
	                                @Qualifier("online") UserManager<Player> userManager,
	                                MemberManager memberManager,
	                                GangManager gangManager,
	                                RankManager rankManager) {
		super(gangland, "option", false);
		this.userManager   = userManager;
		this.memberManager = memberManager;
		this.gangManager   = gangManager;
		this.rankManager   = rankManager;
	}

	@Override
	protected void onExecute(Argument argument, CommandSender commandSender, String[] arguments) { }

	@Override
	protected void initializeArguments() {
		Argument gang     = gangArgument(userManager, memberManager, gangManager, rankManager);
		Argument resource = resourcePack();

		getArgument().addSubArgument(gang);
		getArgument().addSubArgument(resource);
	}

	@Override
	protected void help(CommandSender sender, int page) { }

	private Argument resourcePack() {
		Argument click = new Argument(getGangland(), "click", getArgumentTree());

		// glw option click resource
		Argument resource = new Argument(getGangland(), "resource", getArgumentTree(), (argument, sender, args) -> {
			sender.sendMessage(GanglandChatUtil.prefixMessage(
					"&7You should first disconnect from the server and click the server tab you created, " +
					"next click &8(&bEdit&8)&7 then change &8(&bServer Resource Packs&8)&7 to either " +
					"&aPrompt&7 or &aEnabled&7 then click &8(&bDone&8)&7 button and log back in."));
			sender.sendMessage(GanglandChatUtil.color(
					"&7If you chose &aPrompt &7then click &8(&bYes&8)&7 once you joined back into the server, " +
					"then the download process will start."));
		});

		click.addSubArgument(resource);

		return click;
	}

	private Argument gangArgument(UserManager<Player> userManager, MemberManager memberManager, GangManager gangManager,
	                              RankManager rankManager) {
		Argument gang = new Argument(getGangland(), "gang", getArgumentTree());

		Argument rank = new Argument(getGangland(), "rank", getArgumentTree());

		Argument target = new OptionalArgument(getGangland(), getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			if (!user.hasGang()) {
				user.sendMessage(Messages.MUST_CREATE_GANG.toString());
				return;
			}

			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<rank>"));
		}, sender -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return null;

			if (!user.hasGang()) {
				user.sendMessage(Messages.MUST_CREATE_GANG.toString());
				return null;
			}

			// get all the members in the gang
			Gang         userGang = gangManager.getGang(user.getGangId());
			List<Member> members  = userGang.getMembers();
			Stream<String> allMembers = members.stream()
					.map(Member::getUuid)
					.map(Bukkit::getOfflinePlayer)
					.map(OfflinePlayer::getName);

			return allMembers.toList();
		});

		rank.addSubArgument(target);

		// glw option gang rank <target> <rank>
		Argument rankType = getRankType(userManager, memberManager, gangManager, rankManager);

		target.addSubArgument(rankType);

		gang.addSubArgument(rank);

		return gang;
	}

	private @NotNull Argument getRankType(UserManager<Player> userManager, MemberManager memberManager,
	                                      GangManager gangManager, RankManager rankManager) {
		return new OptionalArgument(getGangland(), getArgumentTree(), (argument, sender, args) -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return;

			Member userMember = memberManager.getMember(player.getUniqueId());

			if (!user.hasGang()) {
				user.sendMessage(Messages.MUST_CREATE_GANG.toString());
				return;
			}

			Gang userGang = gangManager.getGang(user.getGangId());

			String targetStr    = args[3];
			String rankStr      = args[4];
			Member targetMember = null;
			for (Member member : userGang.getMembers()) {
				OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(member.getUuid());
				String        offlineName   = offlinePlayer.getName();

				if (offlineName == null || offlineName.isEmpty() || !offlineName.equalsIgnoreCase(targetStr)) continue;

				targetMember = member;
				break;
			}

			if (targetMember == null) {
				user.sendMessage(Messages.PLAYER_NOT_FOUND.toString().replace("%player%", targetStr));
				return;
			}

			if (userMember.getRank() == null) return;
			// only support promotion
			// cannot promote more than your rank

			if (userMember.getRank().equals(targetMember.getRank())) {
				user.sendMessage(Messages.GANG_SAME_RANK_ACTION.toString());
				return;
			}

			Rank nextRank = rankManager.get(rankStr);

			if (nextRank == null) return;

			memberManager.assignRank(targetMember, nextRank);

			OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetMember.getUuid());
			Player        onlinePlayer  = offlinePlayer.getPlayer();
			User<Player>  onlineUser    = userManager.getUser(onlinePlayer);

			if (onlinePlayer != null && onlineUser != null && offlinePlayer.isOnline()) {
				String string  = Messages.GANG_PROMOTE_TARGET_SUCCESS.toString();
				String replace = string.replace("%rank%", nextRank.getName());
				onlineUser.sendMessage(replace);
			}

			user.sendMessage(Messages.GANG_PROMOTE_PLAYER_SUCCESS.toString()
			                                                     .replace("%player%", targetStr)
			                                                     .replace("%rank%", nextRank.getName()));
		}, sender -> {
			Player       player = (Player) sender;
			User<Player> user   = userManager.getUser(player);

			if (user == null) return null;

			if (!user.hasGang()) {
				user.sendMessage(Messages.MUST_CREATE_GANG.toString());
				return null;
			}

			Collection<Rank> values = rankManager.getRanks().values();

			return values.stream().map(Rank::getName).toList();
		});
	}

}
