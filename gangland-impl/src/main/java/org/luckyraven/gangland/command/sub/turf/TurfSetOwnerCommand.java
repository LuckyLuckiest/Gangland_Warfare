package org.luckyraven.gangland.command.sub.turf;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.gangland.command.argument.Argument;
import org.luckyraven.gangland.command.argument.SubArgument;
import org.luckyraven.gangland.command.argument.types.OptionalArgument;
import org.luckyraven.gangland.core.TriConsumer;
import org.luckyraven.gangland.core.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.turf.capture.CaptureService;
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
 * active-selection turf.
 */
class TurfSetOwnerCommand extends SubArgument {

	private static final String CLEAR_TOKEN = "none";

	private final Gangland             gangland;
	private final Tree<Argument>       tree;
	private final TurfManager          turfs;
	private final GangLookupContract   gangs;
	private final WandSelectionManager selections;
	private final TurfMessageContract  messages;

	protected TurfSetOwnerCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                              TurfManager turfs, GangLookupContract gangs, WandSelectionManager selections,
	                              TurfMessageContract messages) {
		super(gangland, "setowner", tree, parent);

		this.gangland   = gangland;
		this.tree       = tree;
		this.turfs      = turfs;
		this.gangs      = gangs;
		this.selections = selections;
		this.messages   = messages;

		this.addSubArgument(gangArgument());
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(
				GanglandChatUtil.setArguments(Messages.ARGUMENTS_MISSING.toString(), "<gang|none>"));
	}

	private OptionalArgument gangArgument() {
		return new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Turf turf = TurfSelectionResolver.resolve(sender, turfs, selections, messages);
			if (turf == null) {
				return;
			}
			String  gangToken  = args[2];
			Integer oldOwnerId = turf.getOwnerGangId();
			if (CLEAR_TOKEN.equalsIgnoreCase(gangToken)) {
				turf.setOwnerGangId(null);
				resetCaptureState(turf);
				turfs.persist(turf);
				Bukkit.getPluginManager().callEvent(new TurfOwnerChangedEvent(turf, oldOwnerId, null));
				messages.send(sender, "TURF_SETOWNER_CLEARED", "turf", turf.getDisplayName());
				return;
			}
			Gang target = findGangByName(gangToken);
			if (target == null) {
				messages.send(sender, "TURF_GANG_NOT_FOUND", "gang", gangToken);
				return;
			}
			turf.setOwnerGangId(target.getId());
			resetCaptureState(turf);
			turfs.persist(turf);
			Bukkit.getPluginManager().callEvent(new TurfOwnerChangedEvent(turf, oldOwnerId, target.getId()));
			messages.send(sender, "TURF_SETOWNER_SUCCESS",
			              "turf", turf.getDisplayName(),
			              "gang", GangDisplayNameResolver.resolve(target));
		}, sender -> {
			List<String> names = new ArrayList<>();
			names.add(CLEAR_TOKEN);
			for (Gang gang : gangs.getAll()) {
				names.add(gang.getName());
			}
			return names;
		});
	}

	private Gang findGangByName(String name) {
		for (Gang gang : gangs.getAll()) {
			if (gang.getName().equalsIgnoreCase(name)) {
				return gang;
			}
		}
		return null;
	}

	/**
	 * Admin-initiated ownership change is a clean slate: wipe any in-flight contest / cooldown and the
	 * {@code lastCaptureTimestamp} anchor so {@link CaptureService#isCapturable} no longer rejects the turf for being
	 * in cooldown from a prior capture. Without this, clearing ownership with {@code none} would leave the turf stuck
	 * mid-COOLDOWN and players standing inside would never trigger a new contest.
	 */
	private void resetCaptureState(Turf turf) {
		TurfRuntimeState state = turfs.getRuntimeState(turf.getId());
		if (state != null) {
			state.reset();
		}
		turf.setLastCaptureTimestamp(0L);
	}
}
