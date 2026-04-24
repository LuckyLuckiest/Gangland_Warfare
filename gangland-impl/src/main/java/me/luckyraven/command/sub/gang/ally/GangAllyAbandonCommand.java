package me.luckyraven.command.sub.gang.ally;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.Tree;
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

class GangAllyAbandonCommand extends SubArgument {

	private final Gangland            gangland;
	private final Tree<Argument>      tree;
	private final UserManager<Player> userManager;
	private final MemberManager       memberManager;
	private final GangManager         gangManager;

	GangAllyAbandonCommand(Gangland gangland, Tree<Argument> tree, Argument parent, UserManager<Player> userManager,
	                       MemberManager memberManager, GangManager gangManager) {
		super(gangland, "abandon", tree, parent);

		this.gangland      = gangland;
		this.tree          = tree;
		this.userManager   = userManager;
		this.memberManager = memberManager;
		this.gangManager   = gangManager;

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
	 * Builds the disambiguated display-name → id map of the sender's gang's current allies. Duplicate names are
	 * suffixed with {@code :id} so each option has a unique tab-completion key.
	 */
	private Map<String, String> buildCurrentAllyMap(CommandSender sender) {
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
		}, sender -> new ArrayList<>(buildCurrentAllyMap(sender).keySet()), this::buildCurrentAllyMap);
	}

}
