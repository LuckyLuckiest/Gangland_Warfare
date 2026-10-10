package org.luckyraven.gangland.turf.command;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.turf.capture.TurfOwnership;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.data.TurfRuntimeState;
import org.luckyraven.gangland.turf.events.TurfOwnerChangedEvent;
import org.luckyraven.gangland.turf.listener.GangDisplayNameResolver;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.selection.WandSelectionManager;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /glw turf setowner <gang|none>} — sets (or clears with {@code none}) the owning gang of the admin's
 * active-selection turf. The change goes through {@link TurfOwnership}, which cancels a running capture.
 */
class TurfSetOwnerCommand extends SubArgument {

	private static final String CLEAR_TOKEN = "none";

	private final JavaPlugin           plugin;
	private final Tree<Argument>       tree;
	private final TurfManager          turfs;
	private final GangLookupContract   gangs;
	private final WandSelectionManager selections;
	private final TurfMessageContract  messages;
	private final TurfMessages         text;
	private final TurfOwnership        ownership;

	protected TurfSetOwnerCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent,
	                              TurfManager turfs, GangLookupContract gangs, WandSelectionManager selections,
	                              TurfMessageContract messages, TurfMessages text, TurfOwnership ownership) {
		super(plugin, "setowner", tree, parent);

		this.plugin     = plugin;
		this.tree       = tree;
		this.turfs      = turfs;
		this.gangs      = gangs;
		this.selections = selections;
		this.messages   = messages;
		this.text       = text;
		this.ownership  = ownership;

		this.addSubArgument(gangArgument());
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(
				GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<gang|none>"));
	}

	private OptionalArgument gangArgument() {
		return new OptionalArgument(plugin, tree, (argument, sender, args) -> {
			// GI-35: nested SubArgument permissions only add onto the base "gangland.command.turf" node — they
			// never require the stricter admin one on their own.
			if (!TurfPermissions.requireAdmin(sender, text)) {
				return;
			}
			apply(sender, args[2]);
		}, sender -> {
			List<String> names = new ArrayList<>();
			names.add(CLEAR_TOKEN);
			for (Gang gang : gangs.getAll()) {
				names.add(gang.getName());
			}
			return names;
		});
	}

	/**
	 * Applies {@code gangToken} (a gang name or {@code none}) to the sender's active-selection turf. Package-private so
	 * the nested gang argument's action can be driven directly in tests.
	 */
	void apply(CommandSender sender, String gangToken) {
		Turf turf = TurfSelectionResolver.resolve(sender, turfs, selections, messages);
		if (turf == null) {
			return;
		}
		if (CLEAR_TOKEN.equalsIgnoreCase(gangToken)) {
			setOwner(turf, null);
			messages.send(sender, "TURF_SETOWNER_CLEARED", "turf", turf.getDisplayName());
			return;
		}
		Gang target = findGangByName(gangToken);
		if (target == null) {
			messages.send(sender, "TURF_GANG_NOT_FOUND", "gang", gangToken);
			return;
		}
		setOwner(turf, target.getId());
		messages.send(sender, "TURF_SETOWNER_SUCCESS",
		              "turf", turf.getDisplayName(),
		              "gang", GangDisplayNameResolver.resolve(target));
	}

	/**
	 * Admin-initiated ownership change is a clean slate: the cooldown anchor is wiped and any in-flight contest is
	 * cancelled (boss bars and guards clean up through the event). Without the wipe, clearing ownership would leave the
	 * turf stuck mid-COOLDOWN and players standing inside would never trigger a new contest.
	 */
	private void setOwner(Turf turf, Integer newOwnerGangId) {
		turf.setLastCaptureTimestamp(0L);
		ownership.setOwner(turf, newOwnerGangId, TurfOwnerChangedEvent.Cause.ADMIN);
		TurfRuntimeState state = turfs.getRuntimeState(turf.getId());
		if (state != null) {
			state.reset();
		}
		turfs.persist(turf);
	}

	private Gang findGangByName(String name) {
		for (Gang gang : gangs.getAll()) {
			if (gang.getName().equalsIgnoreCase(name)) {
				return gang;
			}
		}
		return null;
	}
}
