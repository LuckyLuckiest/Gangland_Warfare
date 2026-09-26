package org.luckyraven.gangland.command.sub.rank;

import lombok.CustomLog;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.ConfirmArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.timer.CountdownTimer;
import org.luckyraven.keystone.util.TimeUtil;
import org.luckyraven.gangland.gang.database.repositories.rank.RankParentRepository;
import org.luckyraven.gangland.gang.database.repositories.rank.RankPermissionRepository;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.GangSettings;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.gangland.gang.rank.RankParent;
import org.luckyraven.gangland.gang.rank.RankPermission;
import org.luckyraven.keystone.persistence.repository.RepositoryRegistry;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.gangland.util.TimeMessages;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@CustomLog
class RankDeleteCommand extends SubArgument {

	private final JavaPlugin          gangland;
	private final Tree<Argument>    tree;
	private final RankManager       rankManager;
	private final RepositoryRegistry repositoryRegistry;
	private final MemberManager      memberManager;

	protected RankDeleteCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent,
	                            RankManager rankManager, RepositoryRegistry repositoryRegistry,
	                            MemberManager memberManager) {
		super(gangland, new String[]{"delete", "remove", "del"}, tree, parent);

		this.gangland           = gangland;
		this.tree               = tree;
		this.rankManager        = rankManager;
		this.repositoryRegistry = repositoryRegistry;
		this.memberManager      = memberManager;

		rankDelete();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(
				GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<name>"));
	}

	private void rankDelete() {
		HashMap<CommandSender, AtomicReference<String>> deleteRankName  = new HashMap<>();
		HashMap<CommandSender, CountdownTimer>          deleteRankTimer = new HashMap<>();

		ConfirmArgument confirmDelete = new ConfirmArgument(gangland, tree, (argument, sender, args) -> {
			Rank rank = rankManager.get(deleteRankName.get(sender).get());

			if (rank != null && !refuseInUse(sender, rank)) {
				var rankRepository           = repositoryRegistry.getRepository(Rank.class);
				var rankParentRepository     = repositoryRegistry.getRepository(RankParent.class);
				var rankPermissionRepository = repositoryRegistry.getRepository(RankPermission.class);

				rankManager.remove(rank);

				// Delete permissions and parent entry before the rank itself (FK order)
				if (rankPermissionRepository instanceof RankPermissionRepository concrete) {
					try {
						concrete.deleteAllForRank(rank.getUsedId());
					} catch (java.sql.SQLException exception) {
						log.warn("Failed to purge rank_permission rows for rank {}: {}",
						         rank.getName(), exception.getMessage());
					}
				}
				// Both sides: a leftover row naming this rank as parent_id breaks the tree rebuild on the next load
				if (rankParentRepository instanceof RankParentRepository concrete) {
					try {
						concrete.deleteAllForRank(rank.getUsedId());
					} catch (java.sql.SQLException exception) {
						log.warn("Failed to purge rank_parent rows for rank {}: {}",
						         rank.getName(), exception.getMessage());
					}
				}
				rankRepository.delete(rank);

				String string  = Messages.RANK_REMOVED.toString();
				String replace = string.replace("%rank%", rank.getName());
				sender.sendMessage(replace);
				deleteRankName.remove(sender);

				CountdownTimer timer = deleteRankTimer.get(sender);
				if (timer != null) {
					if (!timer.isCancelled()) timer.cancel();
					deleteRankTimer.remove(sender);
				}
			}
		});

		this.addSubArgument(confirmDelete);

		Argument deleteName = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Rank rank = rankManager.get(args[2]);

			if (rank == null) {
				sender.sendMessage(Messages.INVALID_RANK.toString());
				return;
			}

			if (refuseInUse(sender, rank)) return;

			if (confirmDelete.isLocked(sender)) return;

			sender.sendMessage(GanglandChatUtil.confirmCommand(new String[]{"rank", "delete"}));
			deleteRankName.put(sender, new AtomicReference<>(args[2]));

			confirmDelete.lock(sender, s -> {
				CountdownTimer timer = new CountdownTimer(gangland, 60, null, time -> {
					if (time.getTimeLeft() % 20 != 0) return;

					String string = Messages.RANK_REMOVE_CONFIRM.toString();
					String replace = string.replace("%timer%", TimeUtil.formatTime(time.getTimeLeft(), true,
					                                                               TimeMessages.getInstance()));

					s.sendMessage(replace);
				}, time -> {
					confirmDelete.unlock(s);
					deleteRankName.remove(s);
				});

				timer.start(false);
				deleteRankTimer.put(s, timer);
			});
		}, sender -> {
			Collection<Rank> values = rankManager.getRanks().values();

			if (values.isEmpty()) return List.of("<name>");

			return values.stream().map(Rank::getName).toList();
		});

		this.addSubArgument(deleteName);
	}

	/**
	 * Refuses the default Head/Tail ranks, a rank a member still holds (left rankless, they could neither leave nor be
	 * kicked) and a rank with ranks above it (they would drop out of the tree).
	 */
	private boolean refuseInUse(CommandSender sender, Rank rank) {
		boolean inUse = rank.getName().equalsIgnoreCase(GangSettings.getGangRankHead()) ||
		                rank.getName().equalsIgnoreCase(GangSettings.getGangRankTail()) ||
		                !rank.getNode().getChildren().isEmpty() ||
		                memberManager.getMembers().values().stream()
		                             .anyMatch(member -> member.getRank() != null &&
		                                                 member.getRank().match(rank.getUsedId()));

		if (inUse) sender.sendMessage(Messages.RANK_REMOVE_IN_USE.toString().replace("%rank%", rank.getName()));

		return inUse;
	}

}
