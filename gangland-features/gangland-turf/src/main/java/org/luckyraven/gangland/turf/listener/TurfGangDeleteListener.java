package org.luckyraven.gangland.turf.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.events.gang.GangDeleteEvent;
import org.luckyraven.gangland.turf.capture.TurfOwnership;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.data.TurfRuntimeState;
import org.luckyraven.gangland.turf.events.TurfCaptureFailedEvent;
import org.luckyraven.gangland.turf.events.TurfOwnerChangedEvent;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.state.TurfState;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

import java.util.ArrayList;
import java.util.Objects;

/**
 * TF-25: when a gang is disbanded, releases every turf it owned and cancels any capture it was running. Only the gang
 * id is read from the event; GangDeleteCommand removes the gang before firing, so it is never looked up here.
 */
@ListenerHandler
public final class TurfGangDeleteListener implements Listener {

	private final TurfManager   turfs;
	private final TurfOwnership ownership;

	public TurfGangDeleteListener(TurfManager turfs, TurfOwnership ownership) {
		this.turfs     = turfs;
		this.ownership = ownership;
	}

	@EventHandler
	public void onGangDelete(GangDeleteEvent event) {
		int gangId = event.getGang().getId();
		for (Turf turf : new ArrayList<>(turfs.getAll())) {
			if (Objects.equals(turf.getOwnerGangId(), gangId)) {
				ownership.setOwner(turf, null, TurfOwnerChangedEvent.Cause.GANG_DISBANDED);
				continue;
			}
			TurfRuntimeState state = turfs.getRuntimeState(turf.getId());
			if (state != null && state.getState() == TurfState.CONTESTING
			    && Objects.equals(state.getChallengerGangId(), gangId)) {
				ownership.cancelContest(turf, TurfCaptureFailedEvent.Reason.CANCELLED);
			}
		}
	}
}
