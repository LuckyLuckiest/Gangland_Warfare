package org.luckyraven.gangland.command.sub.gang;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangAlliance;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

import java.util.Objects;
import java.util.stream.Collectors;

/**
 * {@code /glw gang info [gang]}: a public card for a gang, found by name (ignoring case) or numeric id. The card never
 * shows the bank balance. With no argument it shows the sender's own gang, or the usage line when gangless.
 */
class GangInfoCommand extends SubArgument {

	private final GangManager         gangManager;
	private final UserManager<Player> userManager;
	private final GangMessages        messages;

	protected GangInfoCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent, GangManager gangManager,
	                          @Qualifier("online") UserManager<Player> userManager, GangMessages messages) {
		super(gangland, "info", tree, parent);

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
		}, sender -> gangManager.getAll().stream().map(Gang::getName).filter(Objects::nonNull).toList());
	}

	/** By name first (ignoring case and colour codes), then by numeric id. */
	private Gang find(String key) {
		String plain = GangNameRules.plain(key);
		for (Gang gang : gangManager.getAll()) {
			if (gang.getName() != null && GangNameRules.plain(gang.getName()).equalsIgnoreCase(plain)) return gang;
		}

		try {
			return gangManager.getGang(Integer.parseInt(key));
		} catch (NumberFormatException exception) {
			return null;
		}
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
