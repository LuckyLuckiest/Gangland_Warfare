package org.luckyraven.gangland.command.sub.gang;

import org.luckyraven.gangland.gang.stats.GangStatsService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangAlliance;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.permission.GangPermissions;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.stream.Collectors;

/**
 * {@code /glw gang info [gang|player]}: a card for a gang, found by name (ignoring case), numeric id, or the name of
 * one of its members. The bank line is private: only the gang's own members and holders of
 * {@code gangland.gang.balance.others} see the amount. With no argument it shows the sender's own gang, or the usage
 * line when gangless.
 */
class GangInfoCommand extends SubArgument {

	private final GangManager         gangManager;
	private final UserManager<Player> userManager;
	private final GangMessages        messages;

	private final GangStatsService statsService;

	protected GangInfoCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent, GangManager gangManager,
	                          @Qualifier("online") UserManager<Player> userManager, GangMessages messages,
								GangStatsService statsService) {
		super(gangland, "info", tree, parent);

		this.statsService = statsService;

		this.gangManager = gangManager;
		this.userManager = userManager;
		this.messages    = messages;

		this.addSubArgument(gangArgument(gangland, tree));
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			if (!(sender instanceof Player player)) {
				sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<gang>"));
				return;
			}

			User<Player> user = userManager.getUser(player);
			if (user == null || !user.hasGang()) {
				sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<gang>"));
				return;
			}

			Gang gang = gangManager.getGang(user.getGangId());
			if (gang == null) {
				sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<gang>"));
				return;
			}
			sendCard(sender, gang);
		};
	}

	private Argument gangArgument(JavaPlugin gangland, Tree<Argument> tree) {
		return new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			String key  = args[2];
			Gang   gang = find(key);

			if (gang == null) {
				sender.sendMessage(messages.text("Info.Not_Found", "&cNo gang named %gang%.", "gang", key));
				return;
			}
			sendCard(sender, gang);
		}, sender -> Stream.concat(gangManager.getAll().stream().map(Gang::getName),
		                           Bukkit.getOnlinePlayers().stream().map(Player::getName))
				.filter(Objects::nonNull)
				.toList());
	}

	/** By gang name first (ignoring case and colour codes), then numeric id, then a member's player name. */
	private Gang find(String key) {
		String plain = GangNameRules.plain(key);
		for (Gang gang : gangManager.getAll()) {
			if (gang.getName() != null && GangNameRules.plain(gang.getName()).equalsIgnoreCase(plain)) return gang;
		}

		try {
			Gang byId = gangManager.getGang(Integer.parseInt(key));
			if (byId != null) return byId;
		} catch (NumberFormatException exception) {
			// not an id: fall through to the player lookup
		}

		return findByPlayer(key);
	}

	/**
	 * The gang holding a member of that name. Names come from the members' own UUIDs, never from a by-name lookup,
	 * which can block on a Mojang request. ponytail: a scan of every member; index by name if a server has thousands.
	 */
	private Gang findByPlayer(String name) {
		Player online = Bukkit.getPlayerExact(name);

		for (Gang gang : gangManager.getAll()) {
			for (Member member : gang.getMembers()) {
				UUID id = member.getUuid();
				if (id == null) continue;

				if (online != null) {
					if (id.equals(online.getUniqueId())) return gang;
					continue;
				}

				OfflinePlayer offline = Bukkit.getOfflinePlayer(id);
				if (offline != null && name.equalsIgnoreCase(offline.getName())) return gang;
			}
		}
		return null;
	}

	private boolean maySeeBank(CommandSender sender, Gang gang) {
		if (sender.hasPermission(GangPermissions.BALANCE_OTHERS)) return true;
		if (!(sender instanceof Player player)) return false;

		User<Player> user = userManager.getUser(player);
		return user != null && user.hasGang() && user.getGangId() == gang.getId();
	}

	private void sendCard(CommandSender sender, Gang gang) {
		sender.sendMessage(messages.text("Info.Title", "&6%gang% &7(%name%)",
		                                 "gang", gang.getDisplayNameString(), "name", gang.getName()));
		sender.sendMessage(messages.text("Info.Description", "&7Description: &f%description%",
		                                 "description", gang.getDescription()));

		int online = gang.getOnlineMembers(userManager::getUser).size();
		sender.sendMessage(messages.text("Info.Members", "&7Members: &a%online%&7/&e%total%",
		                                 "online", online, "total", gang.getMembers().size()));

		String owner = owner(gang);
		sender.sendMessage(messages.text("Info.Owner", "&7Owner: &f%owner%",
		                                 "owner", owner == null ? messages.text("Info.None", "none") : owner));

		String allies = gang.getAllies().stream()
				.map(GangAlliance::ally)
				.map(Gang::getDisplayNameString)
				.collect(Collectors.joining(", "));
		sender.sendMessage(messages.text("Info.Allies", "&7Allies: &f%allies%",
		                                 "allies", allies.isEmpty() ? messages.text("Info.None", "none") : allies));

		if (maySeeBank(sender, gang)) {
			sender.sendMessage(messages.text("Info.Bank", "&7Bank: &a%money_symbol%%bank%",
			                                 "money_symbol", Settings.getMoneySymbol(),
			                                 "bank", Settings.formatAmount(gang.getEconomy().getAmount())));
		} else {
			sender.sendMessage(messages.text("Info.Bank_Hidden", "&7Bank: &8private"));
		}

		sender.sendMessage(messages.text("Info.Created", "&7Created: &f%created%",
		                                 "created", gang.getDateCreatedString()));
	}

	/** The name of the member holding the tail rank, or {@code null} when none does. */
	private static String owner(Gang gang) {
		String tail = GangSettings.getGangRankTail();
		for (Member member : gang.getMembers()) {
			if (member.getRank() == null || !member.getRank().getName().equalsIgnoreCase(tail)) continue;

			OfflinePlayer offline = Bukkit.getOfflinePlayer(member.getUuid());
			return offline.getName() == null ? "Unknown" : offline.getName();
		}
		return null;
	}

}
