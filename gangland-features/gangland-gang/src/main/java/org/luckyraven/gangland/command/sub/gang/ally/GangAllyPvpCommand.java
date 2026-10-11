package org.luckyraven.gangland.command.sub.gang.ally;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangAlliance;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.combat.FriendlyFirePolicy;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.permission.GangPermissions;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

import java.util.List;

/**
 * {@code /glw gang ally pvp <gang> <on|off>}: lets your gang and an ally hurt each other. Each side sets its own
 * alliance row; the allies hurt each other only while both rows are on. Needs the {@code gangland.gang.ally.pvp} node.
 */
final class GangAllyPvpCommand extends SubArgument {

	private final GangManager        gangManager;
	private final FriendlyFirePolicy friendlyFire;

	GangAllyPvpCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent, GangManager gangManager,
	                   FriendlyFirePolicy friendlyFire) {
		super(gangland, "pvp", tree, parent);

		this.gangManager  = gangManager;
		this.friendlyFire = friendlyFire;

		OptionalArgument ally = new OptionalArgument(gangland, tree, (argument, sender, args) -> sender.sendMessage(
				GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<on|off>")),
		                                             this::allyNames);
		ally.setDisplayName("gang");
		OptionalArgument state = new OptionalArgument(gangland, tree, (argument, sender, args) -> change(sender, args[3], args[4]),
		                                              sender -> List.of("on", "off"));
		state.setDisplayName("on|off");
		ally.addSubArgument(state);
		this.addSubArgument(ally);
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(
				GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<gang> <on|off>"));
	}

	private List<String> allyNames(CommandSender sender) {
		Gang gang = quietGang(sender);
		if (gang == null) return List.of();

		return gang.getAllies().stream().map(alliance -> alliance.ally().getName()).toList();
	}

	void change(CommandSender sender, String allyName, String value) {
		boolean on;
		if ("on".equalsIgnoreCase(value)) {
			on = true;
		} else if ("off".equalsIgnoreCase(value)) {
			on = false;
		} else {
			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<on|off>"));
			return;
		}

		if (!(sender instanceof Player player)) {
			sender.sendMessage(Messages.NOT_PLAYER.toString());
			return;
		}

		Member member = friendlyFire.memberOf(player.getUniqueId());
		Gang   gang   = quietGang(sender);
		if (gang == null) {
			sender.sendMessage(Messages.MUST_CREATE_GANG.toString());
			return;
		}

		if (!GangPermissions.allows(member, player, GangPermissions.ALLY_PVP)) {
			sender.sendMessage(GangPermissions.refusal(GangPermissions.ALLY_PVP));
			return;
		}

		Gang other = findAlly(gang, allyName);
		if (other == null) {
			sender.sendMessage(friendlyFire.messages().text("Combat.Ally_Not_Allied",
			                                                "&c%gang% is not allied with your gang.",
			                                                "gang", allyName));
			return;
		}

		// An alliance only the other side lists has no row of ours yet; give it one so this side can answer too.
		GangAlliance row = FriendlyFirePolicy.findRow(gang, other);
		if (row == null) {
			gang.addAlly(other);
			row = FriendlyFirePolicy.findRow(gang, other);
		}
		row.setFriendlyFire(on);

		if (!on) {
			sender.sendMessage(friendlyFire.messages().text("Combat.Ally_Pvp_Off",
			                                                "&a%gang% can no longer be hurt by your gang.",
			                                                "gang", other.getDisplayNameString()));
		} else if (FriendlyFirePolicy.row(other, gang)) {
			sender.sendMessage(friendlyFire.messages().text("Combat.Ally_Pvp_On",
			                                                "&e%gang% and your gang can now hurt each other.",
			                                                "gang", other.getDisplayNameString()));
		} else {
			sender.sendMessage(friendlyFire.messages().text("Combat.Ally_Pvp_Waiting",
			                                                "&7Your side is on; %gang% must turn it on too.",
			                                                "gang", other.getDisplayNameString()));
		}
	}

	/** The allied gang called {@code name} (name, display name, or {@code name:id}), from either side's list. */
	@Nullable
	private Gang findAlly(Gang gang, String name) {
		for (Gang candidate : gangManager.getAll()) {
			if (candidate.getId() == gang.getId() || !gang.isAlly(candidate)) continue;

			if (candidate.getName().equalsIgnoreCase(name) || candidate.getDisplayNameString().equalsIgnoreCase(name)
			    || (candidate.getName() + ":" + candidate.getId()).equalsIgnoreCase(name)) {
				return candidate;
			}
		}
		return null;
	}

	@Nullable
	private Gang quietGang(CommandSender sender) {
		if (!(sender instanceof Player player)) return null;

		Member member = friendlyFire.memberOf(player.getUniqueId());
		return member == null || member.getGangId() == -1 ? null : gangManager.getGang(member.getGangId());
	}
}
