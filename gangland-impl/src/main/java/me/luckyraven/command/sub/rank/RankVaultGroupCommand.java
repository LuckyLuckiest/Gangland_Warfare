package me.luckyraven.command.sub.rank;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.data.account.gang.member.Member;
import me.luckyraven.data.account.gang.member.MemberManager;
import me.luckyraven.data.permission.vault.VaultPermissionBridge;
import me.luckyraven.data.rank.Rank;
import me.luckyraven.data.rank.RankManager;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * {@code /glw rank vaultgroup <rank> <group|clear>} — associates a rank with an external Vault group. When a member is
 * assigned the rank, {@link VaultPermissionBridge} adds them to the configured group; demotion removes them. Passing
 * the literal {@code clear} detaches the group. Non-destructive when Vault is absent.
 */
class RankVaultGroupCommand extends SubArgument {

	private static final String CLEAR_TOKEN = "clear";

	private final Gangland       gangland;
	private final Tree<Argument> tree;
	private final RankManager    rankManager;
	private final MemberManager  memberManager;

	protected RankVaultGroupCommand(Gangland gangland, Tree<Argument> tree, Argument parent, RankManager rankManager,
	                                MemberManager memberManager) {
		super(gangland, new String[]{"vaultgroup", "vgroup"}, tree, parent);

		this.gangland      = gangland;
		this.tree          = tree;
		this.rankManager   = rankManager;
		this.memberManager = memberManager;

		initializeArguments();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<rank>"));
		};
	}

	private void initializeArguments() {
		Argument rankArg = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Rank rank = rankManager.get(args[3]);

			if (rank == null) {
				sender.sendMessage(Messages.INVALID_RANK.toString());
				return;
			}

			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(),
			                                                 "<group|" + CLEAR_TOKEN + ">"));
		}, sender -> rankManager.getRanks().values()
				.stream().map(Rank::getName).toList());

		Argument groupArg = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Rank rank = rankManager.get(args[3]);

			if (rank == null) {
				sender.sendMessage(Messages.INVALID_RANK.toString());
				return;
			}

			if (!VaultPermissionBridge.isEnabled()) {
				sender.sendMessage(Messages.VAULT_PERMISSION_NOT_AVAILABLE.toString());
				return;
			}

			String       token    = args[4];
			boolean      clearing = token.equalsIgnoreCase(CLEAR_TOKEN);
			String       newGroup = clearing ? null : token;
			String       oldGroup = rank.getVaultGroup();
			List<Member> wearing  = memberManager.getMembersByRank(rank);

			rank.setVaultGroup(newGroup);

			for (Member member : wearing) {
				OfflinePlayer player = Bukkit.getOfflinePlayer(member.getUuid());
				VaultPermissionBridge.removeFromGroup(player, oldGroup);
				VaultPermissionBridge.addToGroup(player, newGroup);
			}

			String template = clearing ? Messages.RANK_VAULT_GROUP_CLEARED.toString()
			                           : Messages.RANK_VAULT_GROUP_SET.toString();
			sender.sendMessage(
					template.replace("%rank%", rank.getName()).replace("%group%", newGroup == null ? "" : newGroup));
		}, sender -> List.of(CLEAR_TOKEN));

		rankArg.addSubArgument(groupArg);
		this.addSubArgument(rankArg);
	}
}
