package org.luckyraven.gangland.turf.command;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.util.ChatUtil;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.config.TurfRules;
import org.luckyraven.gangland.turf.config.TurfSettings;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.data.CuboidRegion;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.selection.Selection;
import org.luckyraven.gangland.turf.selection.WandSelectionManager;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.List;

/**
 * {@code /glw turf create <displayName>}. The admin never types an id — it is auto-allocated by
 * {@link TurfManager#allocateId()}. The freshly-created turf becomes the admin's active selection so follow-up commands
 * need no id argument either.
 *
 * <p>Before the turf is made: the wand region is snapped to chunks when {@code Create.Snap_To_Chunks} is on (D3), each
 * side must fit {@code Create.Min_Side_Blocks}..{@code Create.Max_Side_Blocks} (TF-21), the name must be free (TF-29)
 * and the region must not overlap another turf.
 */
class TurfCreateCommand extends SubArgument {

	private final JavaPlugin           plugin;
	private final Tree<Argument>       tree;
	private final TurfManager          turfs;
	private final WandSelectionManager selections;
	private final TurfMessageContract  messages;
	private final TurfSettings         settings;
	private final TurfRules            rules;
	private final TurfMessages         text;

	protected TurfCreateCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent,
	                            TurfManager turfs, WandSelectionManager selections, TurfMessageContract messages,
	                            TurfSettings settings, TurfRules rules, TurfMessages text) {
		super(plugin, "create", tree, parent);

		this.plugin     = plugin;
		this.tree       = tree;
		this.turfs      = turfs;
		this.selections = selections;
		this.messages   = messages;
		this.settings   = settings;
		this.rules      = rules;
		this.text       = text;

		this.addSubArgument(displayNameArgument());
	}

	static String joinFrom(String[] args, int from) {
		StringBuilder builder = new StringBuilder();
		for (int i = from; i < args.length; i++) {
			if (i > from) {
				builder.append(' ');
			}
			builder.append(args[i]);
		}
		return builder.toString();
	}

	/** The name with colour codes removed, for comparisons: {@code &cDocks} is {@code Docks}. */
	static String plain(String name) {
		return ChatColor.stripColor(ChatUtil.color(name)).trim();
	}

	/**
	 * true when another turf already has {@code name}, ignoring case and colour codes (TF-29). {@code exceptId} is the
	 * turf being renamed, which may keep its own name; pass -1 for a new turf.
	 */
	static boolean nameTaken(TurfManager turfs, String name, int exceptId) {
		String wanted = plain(name);
		for (Turf turf : turfs.getAll()) {
			if (turf.getId() != exceptId && plain(turf.getDisplayName()).equalsIgnoreCase(wanted)) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(
				GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<displayName>"));
	}

	/** {@code /glw turf create <displayName...>}; args = [turf, create, displayName...]. */
	void create(CommandSender sender, String[] args) {
		if (!TurfPermissions.requireAdmin(sender, text)) {
			return;
		}
		if (!(sender instanceof Player player)) {
			return;
		}
		if (args.length < 3) {
			sender.sendMessage(
					GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<displayName>"));
			return;
		}
		String displayName = joinFrom(args, 2);

		Selection selection = selections.get(player);
		if (!selection.isComplete() || selection.getPos1() == null || selection.getPos2() == null) {
			messages.send(sender, "TURF_CREATE_FAIL_NO_SELECTION");
			return;
		}
		Location pos1 = selection.getPos1();
		Location pos2 = selection.getPos2();
		if (pos1.getWorld() == null || pos2.getWorld() == null
		    || !pos1.getWorld().getName().equals(pos2.getWorld().getName())) {
			messages.send(sender, "TURF_CREATE_FAIL_CROSS_WORLD");
			return;
		}

		CuboidRegion raw     = new CuboidRegion(pos1.getWorld().getName(),
		                                        pos1.getBlockX(), pos1.getBlockZ(),
		                                        pos2.getBlockX(), pos2.getBlockZ());
		CuboidRegion region  = rules.isSnapToChunks() ? raw.snappedToChunks() : raw;
		boolean      changed = region.getMinX() != raw.getMinX() || region.getMinZ() != raw.getMinZ()
		                       || region.getMaxX() != raw.getMaxX() || region.getMaxZ() != raw.getMaxZ();

		int    x   = region.getMaxX() - region.getMinX() + 1;
		int    z   = region.getMaxZ() - region.getMinZ() + 1;
		String min = String.valueOf(rules.getMinSideBlocks());
		String max = String.valueOf(rules.getMaxSideBlocks());
		if (x < rules.getMinSideBlocks() || z < rules.getMinSideBlocks()) {
			sender.sendMessage(text.text("Turf.Create_Too_Small", "&cThat turf is too small.",
			                             "min", min, "max", max,
			                             "x", String.valueOf(x), "z", String.valueOf(z)));
			return;
		}
		if (x > rules.getMaxSideBlocks() || z > rules.getMaxSideBlocks()) {
			sender.sendMessage(text.text("Turf.Create_Too_Large", "&cThat turf is too large.",
			                             "min", min, "max", max,
			                             "x", String.valueOf(x), "z", String.valueOf(z)));
			return;
		}
		if (nameTaken(turfs, displayName, -1)) {
			sender.sendMessage(text.text("Turf.Name_Taken", "&cA turf named %turf% already exists.",
			                             "turf", plain(displayName)));
			return;
		}

		Turf conflict = turfs.findConflict(region);
		if (conflict != null) {
			messages.send(sender, "TURF_CREATE_FAIL_OVERLAP", "turf", conflict.getDisplayName());
			return;
		}

		if (changed) {
			sender.sendMessage(text.text("Turf.Create_Snapped", "&7Snapped to chunks.",
			                             "minx", String.valueOf(region.getMinX()),
			                             "minz", String.valueOf(region.getMinZ()),
			                             "maxx", String.valueOf(region.getMaxX()),
			                             "maxz", String.valueOf(region.getMaxZ())));
		}

		int  id   = turfs.allocateId();
		Turf turf = new Turf(id, displayName, region, null,
		                     settings.getDefaultIncomeAmount(),
		                     System.currentTimeMillis(), 0L);
		turfs.create(turf);

		// Auto-select the new turf so follow-up commands operate on it with no id argument.
		selection.setActiveTurfId(id);

		messages.send(sender, "TURF_CREATE_SUCCESS",
		              "turf", displayName,
		              "id", String.valueOf(id),
		              "min", region.getMinX() + "," + region.getMinZ(),
		              "max", region.getMaxX() + "," + region.getMaxZ(),
		              "world", region.getWorld());
	}

	private OptionalArgument displayNameArgument() {
		// GI-35: nested SubArgument permissions only add onto the base "gangland.command.turf" node; the admin
		// check happens inside create().
		return new OptionalArgument(plugin, tree, (argument, sender, args) -> create(sender, args),
		                            sender -> List.of("<displayName>"));
	}
}
