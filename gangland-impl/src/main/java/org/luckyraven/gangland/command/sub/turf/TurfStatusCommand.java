package org.luckyraven.gangland.command.sub.turf;

import org.bukkit.command.CommandSender;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.data.TurfRuntimeState;
import org.luckyraven.gangland.turf.listener.GangDisplayNameResolver;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.selection.WandSelectionManager;
import org.luckyraven.gangland.turf.state.CapturePhase;

/**
 * {@code /glw turf status} — prints the live capture/cooldown state of the admin's active-selection turf (or the one
 * they are standing inside).
 */
class TurfStatusCommand extends SubArgument {

	private final TurfManager          turfs;
	private final GangLookupContract   gangs;
	private final WandSelectionManager selections;
	private final TurfMessageContract  messages;

	protected TurfStatusCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                            TurfManager turfs, GangLookupContract gangs, WandSelectionManager selections,
	                            TurfMessageContract messages) {
		super(gangland, "status", tree, parent);

		this.turfs      = turfs;
		this.gangs      = gangs;
		this.selections = selections;
		this.messages   = messages;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Turf turf = TurfSelectionResolver.resolve(sender, turfs, selections, messages);
			if (turf == null) {
				return;
			}
			TurfRuntimeState state     = turfs.getRuntimeState(turf.getId());
			String           ownerName = "Unclaimed";
			if (turf.getOwnerGangId() != null) {
				Gang owner = gangs.findById(turf.getOwnerGangId());
				ownerName = GangDisplayNameResolver.resolve(owner);
			}

			if (state == null) {
				messages.send(sender, "TURF_STATUS_IDLE",
				              "turf", turf.getDisplayName(),
				              "gang", ownerName);
				return;
			}
			switch (state.getState()) {
				case CONTESTING -> {
					Gang challenger = state.getChallengerGangId() == null
					                  ? null
					                  : gangs.findById(state.getChallengerGangId());
					String challengerName = GangDisplayNameResolver.resolve(challenger);
					// Owned-turf captures are single-phase, so the phase suffix is blank for them;
					// unclaimed captures tag the row with Phase 1 / Phase 2 for clarity.
					String phaseSuffix = "";
					if (turf.isUnclaimed()) {
						phaseSuffix = state.getPhase() == CapturePhase.CLAIM
						              ? " &8(Phase 1 — Claim)"
						              : " &8(Phase 2 — Consolidate)";
					}
					messages.send(sender, "TURF_STATUS_CONTESTING",
					              "turf", turf.getDisplayName(),
					              "gang", challengerName,
					              "progress", String.valueOf((int) state.getCaptureProgress()),
					              "phase", phaseSuffix);
				}
				case COOLDOWN -> {
					long cooldownMs = Settings.getTurfCaptureCooldownMinutes() * 60_000L;
					long left       = (turf.getLastCaptureTimestamp() + cooldownMs - System.currentTimeMillis());
					long seconds    = Math.max(0L, (left + 999L) / 1000L);
					messages.send(sender, "TURF_STATUS_COOLDOWN",
					              "turf", turf.getDisplayName(),
					              "time", messages.formatDuration(seconds));
				}
				case IDLE -> messages.send(sender, "TURF_STATUS_IDLE",
				                           "turf", turf.getDisplayName(),
				                           "gang", ownerName);
			}
		};
	}
}
