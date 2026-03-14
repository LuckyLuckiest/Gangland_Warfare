package me.luckyraven.command.sub.rank;

import me.luckyraven.Gangland;
import me.luckyraven.Initializer;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.ConfirmArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.data.rank.Rank;
import me.luckyraven.data.rank.RankManager;
import me.luckyraven.data.rank.RankParent;
import me.luckyraven.data.rank.RankPermission;
import me.luckyraven.database.GanglandDatabase;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.persistence.repository.RepositoryRegistry;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.TimeMessages;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import me.luckyraven.util.timer.CountdownTimer;
import me.luckyraven.util.utilities.TimeUtil;
import org.bukkit.command.CommandSender;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;

class RankDeleteCommand extends SubArgument {

	private final Gangland       gangland;
	private final Tree<Argument> tree;
	private final RankManager    rankManager;

	protected RankDeleteCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, new String[]{"delete", "remove", "del"}, tree, parent);

		this.gangland = gangland;
		this.tree     = tree;

		this.rankManager = gangland.getInitializer().getRankManager();

		rankDelete();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(
				ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<name>"));
	}

	private void rankDelete() {
		HashMap<CommandSender, AtomicReference<String>> deleteRankName  = new HashMap<>();
		HashMap<CommandSender, CountdownTimer>          deleteRankTimer = new HashMap<>();

		ConfirmArgument confirmDelete = new ConfirmArgument(gangland, tree, (argument, sender, args) -> {
			Rank rank = rankManager.get(deleteRankName.get(sender).get());

			if (rank != null) {
				Initializer        initializer        = gangland.getInitializer();
				GanglandDatabase   ganglandDatabase   = initializer.getGanglandDatabase();
				RepositoryRegistry repositoryRegistry = ganglandDatabase.getRepositoryRegistry();

				var rankRepository           = repositoryRegistry.getRepository(Rank.class);
				var rankParentRepository     = repositoryRegistry.getRepository(RankParent.class);
				var rankPermissionRepository = repositoryRegistry.getRepository(RankPermission.class);

				rankManager.remove(rank);

				// Delete permissions and parent entry before the rank itself (FK order)
				rankPermissionRepository.delete(new RankPermission(rank.getUsedId(), 0));
				rankParentRepository.delete(new RankParent(rank.getUsedId(), 0));
				rankRepository.delete(rank);

				String string  = MessageAddon.RANK_REMOVED.toString();
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
				sender.sendMessage(MessageAddon.INVALID_RANK.toString());
				return;
			}

			if (confirmDelete.isLocked(sender)) return;

			sender.sendMessage(ChatUtil.confirmCommand(new String[]{"rank", "delete"}));
			deleteRankName.put(sender, new AtomicReference<>(args[2]));

			confirmDelete.lock(sender, s -> {
				CountdownTimer timer = new CountdownTimer(gangland, 60, null, time -> {
					if (time.getTimeLeft() % 20 != 0) return;

					String string = MessageAddon.RANK_REMOVE_CONFIRM.toString();
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
		}, sender -> rankManager.getRanks().values()
				.stream().map(Rank::getName).toList());

		this.addSubArgument(deleteName);
	}

}
