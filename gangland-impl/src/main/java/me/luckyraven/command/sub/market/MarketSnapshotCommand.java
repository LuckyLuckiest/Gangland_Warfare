package me.luckyraven.command.sub.market;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.market.snapshot.SnapshotService;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.TriConsumer;
import me.luckyraven.util.datastructure.Tree;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

/**
 * Fires {@link SnapshotService#snapshot()} on demand. The service normally runs daily via the async ticker — this
 * subcommand exists so admins can roll a snapshot immediately (after a shock test, a config reload, etc.). Runs the
 * snapshot off-thread via the server scheduler so the caller's command thread isn't blocked by I/O.
 */
class MarketSnapshotCommand extends SubArgument {

	private final Gangland        gangland;
	private final SnapshotService snapshotService;

	MarketSnapshotCommand(Gangland gangland, Tree<Argument> tree, Argument parent, SnapshotService snapshotService) {
		super(gangland, "snapshot", tree, parent);
		this.gangland        = gangland;
		this.snapshotService = snapshotService;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			sender.sendMessage(GanglandChatUtil.color("&8[&6Market&8] &7Rolling snapshot…"));
			Bukkit.getScheduler().runTaskAsynchronously(gangland, () -> {
				snapshotService.snapshot();
				Bukkit.getScheduler().runTask(gangland, () -> sender.sendMessage(
						GanglandChatUtil.color("&8[&6Market&8] &aSnapshot complete.")));
			});
		};
	}
}
