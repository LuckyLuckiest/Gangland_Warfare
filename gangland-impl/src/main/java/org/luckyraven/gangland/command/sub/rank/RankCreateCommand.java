package org.luckyraven.gangland.command.sub.rank;

import org.bukkit.command.CommandSender;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.command.argument.SubArgument;
import org.luckyraven.gangland.command.argument.types.ConfirmArgument;
import org.luckyraven.gangland.command.argument.types.OptionalArgument;
import org.luckyraven.gangland.core.TriConsumer;
import org.luckyraven.gangland.core.datastructure.Tree;
import org.luckyraven.gangland.core.timer.CountdownTimer;
import org.luckyraven.gangland.core.utilities.TimeUtil;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.gang.rank.RankManager;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.gangland.util.TimeMessages;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

class RankCreateCommand extends SubArgument {

	private final Gangland       gangland;
	private final Tree<Argument> tree;
	private final RankManager    rankManager;

	protected RankCreateCommand(Gangland gangland, Tree<Argument> tree, Argument parent, RankManager rankManager) {
		super(gangland, "create", tree, parent);

		this.gangland = gangland;
		this.tree     = tree;

		this.rankManager = rankManager;

		rankCreate();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<name>"));
		};
	}

	private void rankCreate() {
		HashMap<CommandSender, AtomicReference<String>> createRankName  = new HashMap<>();
		HashMap<CommandSender, CountdownTimer>          createRankTimer = new HashMap<>();

		ConfirmArgument confirmCreate = new ConfirmArgument(gangland, tree, (argument, sender, args) -> {
			Rank rank = new Rank(createRankName.get(sender).get(), Rank.getNewId());

			String string  = Messages.RANK_CREATED.toString();
			String replace = string.replace("%rank%", rank.getName());
			sender.sendMessage(replace);
			rankManager.add(rank);
			createRankName.remove(sender);

			CountdownTimer timer = createRankTimer.get(sender);
			if (timer != null) {
				if (!timer.isCancelled()) timer.cancel();
				createRankTimer.remove(sender);
			}
		});

		this.addSubArgument(confirmCreate);

		Argument createName = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Rank rank = rankManager.get(args[2]);

			if (rank != null) {
				sender.sendMessage(Messages.RANK_EXIST.toString());
				return;
			}

			if (confirmCreate.isLocked(sender)) return;

			sender.sendMessage(GanglandChatUtil.confirmCommand(new String[]{"rank", "create"}));
			createRankName.put(sender, new AtomicReference<>(args[2]));

			confirmCreate.lock(sender, s -> {
				CountdownTimer timer = new CountdownTimer(gangland, 60, null, time -> {
					if (time.getTimeLeft() % 20 != 0) return;

					String string = Messages.RANK_CREATE_CONFIRM.toString();
					String replace = string.replace("%timer%", TimeUtil.formatTime(time.getTimeLeft(), true,
					                                                               TimeMessages.getInstance()));

					s.sendMessage(replace);
				}, time -> {
					confirmCreate.unlock(s);
					createRankName.remove(s);
				});

				timer.start(false);
				createRankTimer.put(s, timer);
			});
		}, sender -> List.of("<name>"));

		this.addSubArgument(createName);
	}

}
