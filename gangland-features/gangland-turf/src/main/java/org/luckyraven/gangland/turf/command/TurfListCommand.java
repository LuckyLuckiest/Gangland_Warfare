package org.luckyraven.gangland.turf.command;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.GanglandApi;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.data.TurfGeometry;
import org.luckyraven.gangland.turf.listener.GangDisplayNameResolver;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.ChatUtil;
import org.luckyraven.keystone.util.TriConsumer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * {@code /glw turf list [page]}. A player sees the turfs of his own world nearest first, then the other worlds by id;
 * the console sees every turf by id. Eight rows a page; each row is clickable to {@code /glw turf info <id>}, and a
 * turf admin also gets a {@code (tp)} suffix.
 */
class TurfListCommand extends SubArgument {

	static final int PAGE_SIZE = 8;

	private final JavaPlugin          plugin;
	private final Tree<Argument>      tree;
	private final TurfManager         turfs;
	private final GangLookupContract  gangs;
	private final TurfMessageContract messages;
	private final TurfMessages        text;

	protected TurfListCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent,
	                          TurfManager turfs, GangLookupContract gangs, TurfMessageContract messages,
	                          TurfMessages text) {
		super(plugin, "list", tree, parent);

		this.plugin   = plugin;
		this.tree     = tree;
		this.turfs    = turfs;
		this.gangs    = gangs;
		this.messages = messages;
		this.text     = text;

		this.addSubArgument(pageArgument());
	}

	/**
	 * The turfs in the order a viewer sees them: {@code from}'s world nearest first (ties by id), then every other world
	 * by id. {@code from} = null (the console) gives id order.
	 */
	static List<Turf> ordered(Collection<Turf> all, @Nullable Location from) {
		List<Turf> byId = new ArrayList<>(all);
		byId.sort(Comparator.comparingInt(Turf::getId));
		if (from == null || from.getWorld() == null) {
			return byId;
		}
		String world = from.getWorld().getName();
		int    x     = from.getBlockX();
		int    z     = from.getBlockZ();
		List<Turf> here      = new ArrayList<>();
		List<Turf> elsewhere = new ArrayList<>();
		for (Turf turf : byId) {
			(turf.getRegion().getWorld().equals(world) ? here : elsewhere).add(turf);
		}
		here.sort(Comparator.comparingInt((Turf turf) -> TurfGeometry.distance(turf.getRegion(), x, z))
		                    .thenComparingInt(Turf::getId));
		List<Turf> ordered = new ArrayList<>(here);
		ordered.addAll(elsewhere);
		return ordered;
	}

	static int pageCount(int total) {
		return Math.max(1, (total + PAGE_SIZE - 1) / PAGE_SIZE);
	}

	/** The rows of one 1-based page; empty past the last page. */
	static List<Turf> pageSlice(List<Turf> all, int page) {
		int from = Math.min(all.size(), (page - 1) * PAGE_SIZE);
		int to   = Math.min(all.size(), from + PAGE_SIZE);
		return all.subList(from, to);
	}

	/**
	 * One list row. {@code from} = the viewer's location: a turf in that world gets distance and direction; any other
	 * turf (or a null {@code from}) gets the "in <world>" row. {@code admin} adds the clickable {@code (tp)} suffix.
	 */
	static BaseComponent[] row(TurfMessages text, Turf turf, String gangName, @Nullable Location from, boolean admin) {
		CuboidRegion region = turf.getRegion();
		String       rowText;
		if (from != null && from.getWorld() != null && from.getWorld().getName().equals(region.getWorld())) {
			int                    x       = from.getBlockX();
			int                    z       = from.getBlockZ();
			TurfGeometry.Nearest   nearest = TurfGeometry.nearest(List.of(turf), x, z);
			String                 dir     = nearest == null ? "" : nearest.direction();
			rowText = text.text("Turf.List_Row", "&f%turf% &7- %owner% &7- &f%distance%m %direction%",
			                    "turf", turf.getDisplayName(), "owner", gangName,
			                    "distance", String.valueOf(TurfGeometry.distance(region, x, z)), "direction", dir);
		} else {
			rowText = text.text("Turf.List_Row_Elsewhere", "&f%turf% &7- %owner% &7- in %world%",
			                    "turf", turf.getDisplayName(), "owner", gangName, "world", region.getWorld());
		}

		String id   = String.valueOf(turf.getId());
		String hover = "Turf #" + id + (admin ? "\n/" + GanglandApi.SHORT_PREFIX + " turf tp " + id : "");
		ComponentBuilder builder = new ComponentBuilder(rowText)
				.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
				                      "/" + GanglandApi.SHORT_PREFIX + " turf info " + id))
				.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(hover)));
		if (admin) {
			builder.append(ChatUtil.color(" &e(&btp&e)"))
					.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
					                      "/" + GanglandApi.SHORT_PREFIX + " turf tp " + id))
					.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("/" + GanglandApi.SHORT_PREFIX
					                                                             + " turf tp " + id)));
		}
		return builder.create();
	}

	/** Sends one row to {@code sender} (console-safe: uses the spigot chat API like the rest of the module). */
	static void sendRow(CommandSender sender, TurfMessages text, Turf turf, String gangName, @Nullable Location from) {
		sender.spigot().sendMessage(row(text, turf, gangName, from, TurfPermissions.isAdmin(sender)));
	}

	/** {@code /glw turf list [page]} for {@code sender}; a page past the end is clamped to the last one. */
	void list(CommandSender sender, int requestedPage) {
		Collection<Turf> all = turfs.getAll();
		if (all.isEmpty()) {
			messages.send(sender, "TURF_LIST_EMPTY");
			return;
		}
		Location     from    = sender instanceof Player player ? player.getLocation() : null;
		List<Turf>   ordered = ordered(all, from);
		int          pages   = pageCount(ordered.size());
		int          page    = Math.max(1, Math.min(requestedPage, pages));

		sender.sendMessage(text.text("Turf.List_Header", "&7Turfs - page %page%/%pages%",
		                             "page", String.valueOf(page), "pages", String.valueOf(pages)));
		for (Turf turf : pageSlice(ordered, page)) {
			String gangName = "Unclaimed";
			if (turf.getOwnerGangId() != null) {
				gangName = GangDisplayNameResolver.resolve(gangs.findById(turf.getOwnerGangId()));
			}
			sendRow(sender, text, turf, gangName, from);
		}
		if (page < pages) {
			sender.spigot().sendMessage(new ComponentBuilder(text.text("Turf.List_Next",
			                                                           "&7Next: /glw turf list %next%",
			                                                           "next", String.valueOf(page + 1)))
					.event(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
					                      "/" + GanglandApi.SHORT_PREFIX + " turf list " + (page + 1)))
					.create());
		}
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> list(sender, 1);
	}

	private OptionalArgument pageArgument() {
		return new OptionalArgument(plugin, tree, (argument, sender, args) -> {
			// args = [turf, list, <page>]; a word that is not a number reads as page 1.
			int page;
			try {
				page = Integer.parseInt(args[2]);
			} catch (NumberFormatException exception) {
				page = 1;
			}
			list(sender, page);
		}, sender -> {
			List<String> numbers = new ArrayList<>();
			for (int page = 1; page <= pageCount(turfs.getAll().size()); page++) {
				numbers.add(String.valueOf(page));
			}
			return numbers;
		});
	}
}
