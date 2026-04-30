package org.luckyraven.gangland.command.sub.turf;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.command.CommandSender;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.command.argument.SubArgument;
import org.luckyraven.gangland.core.TriConsumer;
import org.luckyraven.gangland.core.datastructure.Tree;
import org.luckyraven.gangland.core.utilities.ChatUtil;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.listener.GangDisplayNameResolver;
import org.luckyraven.gangland.turf.manager.TurfManager;

import java.util.Collection;

class TurfListCommand extends SubArgument {

	private final TurfManager         turfs;
	private final GangLookupContract  gangs;
	private final TurfMessageContract messages;

	protected TurfListCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                          TurfManager turfs, GangLookupContract gangs, TurfMessageContract messages) {
		super(gangland, "list", tree, parent);

		this.turfs    = turfs;
		this.gangs    = gangs;
		this.messages = messages;
	}

	/**
	 * Sends a single row for {@code turf} as a chat component with a clickable {@code (tp)} suffix that runs
	 * {@code /glw turf tp <id>}. Hover shows the world name and region bounds so admins can verify the target before
	 * clicking. Mirrors the pattern from {@code WaypointListCommand}.
	 */
	static void sendRow(CommandSender sender, TurfMessageContract messages, Turf turf, String gangName) {
		String rowText = messages.format("TURF_LIST_ROW",
		                                 "id", String.valueOf(turf.getId()),
		                                 "turf", turf.getDisplayName(),
		                                 "gang", gangName,
		                                 "world", turf.getRegion().getWorld());
		String       tpCommand = String.format("/%s turf tp %d", Gangland.SHORT_PREFIX, turf.getId());
		CuboidRegion region    = turf.getRegion();
		String hover = String.format("%s — (%d,%d) to (%d,%d)\nClick to teleport",
		                             region.getWorld(),
		                             region.getMinX(), region.getMinZ(),
		                             region.getMaxX(), region.getMaxZ());

		BaseComponent[] message = new ComponentBuilder(ChatUtil.color(rowText + " "))
				.append(ChatUtil.color("&e(&btp&e)"))
				.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND, tpCommand))
				.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(hover)))
				.create();
		sender.spigot().sendMessage(message);
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Collection<Turf> all = turfs.getAll();
			if (all.isEmpty()) {
				messages.send(sender, "TURF_LIST_EMPTY");
				return;
			}
			messages.send(sender, "TURF_LIST_HEADER", "count", String.valueOf(all.size()));
			for (Turf turf : all) {
				String gangName = "Unclaimed";
				if (turf.getOwnerGangId() != null) {
					Gang owner = gangs.findById(turf.getOwnerGangId());
					gangName = GangDisplayNameResolver.resolve(owner);
				}
				sendRow(sender, messages, turf, gangName);
			}
		};
	}
}
