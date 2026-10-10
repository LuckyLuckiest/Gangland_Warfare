package org.luckyraven.gangland.turf.command;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.turf.config.TurfSettings;
import org.luckyraven.gangland.turf.capture.CaptureEligibility;
import org.luckyraven.gangland.turf.config.TurfMessages;
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
	private final TurfSettings         settings;
	private final TurfMessages         text;
	private final CaptureEligibility   eligibility;

	protected TurfStatusCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent,
	                            TurfManager turfs, GangLookupContract gangs, WandSelectionManager selections,
	                            TurfMessageContract messages, TurfSettings settings,
	                            TurfMessages text, CaptureEligibility eligibility) {
		super(plugin, "status", tree, parent);

		this.turfs      = turfs;
		this.gangs      = gangs;
		this.selections = selections;
		this.messages   = messages;
		this.settings   = settings;
		this.text       = text;
		this.eligibility = eligibility;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			if (!TurfPermissions.requireAdmin(sender, text)) {
				return;
			}
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
						              ? text.text("Turf.Status_Phase_Claim", " &8(Phase 1 - Claim)")
						              : text.text("Turf.Status_Phase_Consolidate", " &8(Phase 2 - Consolidate)");
					}
					messages.send(sender, "TURF_STATUS_CONTESTING",
					              "turf", turf.getDisplayName(),
					              "gang", challengerName,
					              "progress", String.valueOf((int) state.getCaptureProgress()),
					              "phase", phaseSuffix);
				}
				case COOLDOWN -> sender.sendMessage(text.text("Turf.Status_Cooldown", "&7%turf% &7- %reason%",
				                                             "turf", turf.getDisplayName(),
				                                             "reason", text.reason(
				                                                     eligibility.evaluate(turf, System.currentTimeMillis()),
				                                                     ownerName, System.currentTimeMillis())));
				case IDLE -> messages.send(sender, "TURF_STATUS_IDLE",
				                           "turf", turf.getDisplayName(),
				                           "gang", ownerName);
			}
		};
	}
}
