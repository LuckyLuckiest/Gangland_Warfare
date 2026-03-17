package me.luckyraven.command.sub.rank;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.ConfirmArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.data.rank.Rank;
import me.luckyraven.data.rank.RankManager;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.TimeMessages;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import me.luckyraven.util.timer.CountdownTimer;
import me.luckyraven.util.utilities.TimeUtil;
import org.bukkit.command.CommandSender;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

class RankCreateCommand extends SubArgument {

	private final Gangland       gangland;
	private final Tree<Argument> tree;
	private final RankManager    rankManager;

	protected RankCreateCommand(Gangland gangland, Tree<Argument> tree, Argument parent) {
		super(gangland, "create", tree, parent);

		this.gangland = gangland;
		this.tree     = tree;

		this.rankManager = gangland.getInitializer().getRankManager();

		rankCreate();
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(ChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<name>"));
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

			sender.sendMessage(ChatUtil.confirmCommand(new String[]{"rank", "create"}));
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
