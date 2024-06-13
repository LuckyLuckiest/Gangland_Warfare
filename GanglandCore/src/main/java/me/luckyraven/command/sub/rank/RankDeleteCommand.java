package me.luckyraven.command.sub.rank;

import me.luckyraven.Gangland;
import me.luckyraven.Initializer;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.ConfirmArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.data.account.gang.Gang;
import me.luckyraven.data.account.gang.GangManager;
import me.luckyraven.data.account.gang.Member;
import me.luckyraven.data.account.gang.MemberManager;
import me.luckyraven.data.rank.Rank;
import me.luckyraven.data.rank.RankManager;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.database.Database;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.GanglandDatabase;
import me.luckyraven.database.component.Table;
import me.luckyraven.database.tables.*;
import me.luckyraven.datastructure.Tree;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.Pair;
import me.luckyraven.util.TimeUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.timer.CountdownTimer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.Types;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

class RankDeleteCommand extends SubArgument {

	private final Gangland       gangland;
	private final Tree<Argument> tree;
	private final RankManager    rankManager;

	protected RankDeleteCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(new String[]{"delete", "remove", "del"}, tree, parent);

		this.gangland = gangland;
		this.tree     = tree;

		this.rankManager = gangland.getInitializer().getRankManager();

		rankDelete();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(ChatUtil.setArguments(MessageAddon.ARGUMENTS_MISSING.toString(), "<name>"));
		};
	}

	private void rankDelete() {
		HashMap<CommandSender, AtomicReference<String>> deleteRankName  = new HashMap<>();
		HashMap<CommandSender, CountdownTimer>          deleteRankTimer = new HashMap<>();

		ConfirmArgument confirmDelete = new ConfirmArgument(tree, (argument, sender, args) -> {
			Rank rank = rankManager.get(deleteRankName.get(sender).get());

			if (rank != null) {
				Initializer      initializer      = gangland.getInitializer();
				GanglandDatabase ganglandDatabase = initializer.getGanglandDatabase();
				DatabaseHelper   helper           = new DatabaseHelper(gangland, ganglandDatabase);
				List<Table<?>>   tables           = ganglandDatabase.getTables().stream().toList();

				// remove all the instances from all the tables
				RankTable       rankTable       = initializer.getInstanceFromTables(RankTable.class, tables);
				RankParentTable rankParentTable = initializer.getInstanceFromTables(RankParentTable.class, tables);
				RankPermissionTable rankPermissionTable = initializer.getInstanceFromTables(RankPermissionTable.class,
																							tables);

				helper.runQueries(database -> {
					rankManager.remove(rank);

					database.table(rankTable.getName()).delete("id", String.valueOf(rank.getUsedId()));
					database.table(rankParentTable.getName()).delete("id", String.valueOf(rank.getUsedId()));
					database.table(rankPermissionTable.getName()).delete("rank_id", String.valueOf(rank.getUsedId()));
				});

				sender.sendMessage(MessageAddon.RANK_REMOVED.toString().replace("%rank%", rank.getName()));
				deleteRankName.remove(sender);

				CountdownTimer timer = deleteRankTimer.get(sender);
				if (timer != null) {
					if (!timer.isCancelled()) timer.cancel();
					deleteRankTimer.remove(sender);
				}
			}
		});

		this.addSubArgument(confirmDelete);

		Argument deleteName = new OptionalArgument(tree, (argument, sender, args) -> {
			Rank rank = rankManager.get(args[2]);

			if (rank == null) {
				sender.sendMessage(MessageAddon.INVALID_RANK.toString());
				return;
			}

			if (confirmDelete.isConfirmed()) return;

			sender.sendMessage(ChatUtil.confirmCommand(new String[]{"rank", "delete"}));
			confirmDelete.setConfirmed(true);
			deleteRankName.put(sender, new AtomicReference<>(args[2]));

			CountdownTimer timer = new CountdownTimer(gangland, 60, time -> {
				sender.sendMessage(MessageAddon.RANK_REMOVE_CONFIRM.toString()
																   .replace("%timer%",
																			TimeUtil.formatTime(time.getPeriod(),
																								true)));
			}, null, time -> {
				confirmDelete.setConfirmed(false);
				deleteRankName.remove(sender);
			});

			timer.start(false);
			deleteRankTimer.put(sender, timer);
		});

		this.addSubArgument(deleteName);
	}

}
