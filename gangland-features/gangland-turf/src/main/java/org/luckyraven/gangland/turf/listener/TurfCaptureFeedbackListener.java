package org.luckyraven.gangland.turf.listener;

import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserLookupContract;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.turf.capture.CaptureEligibility;
import org.luckyraven.gangland.turf.capture.TurfRelation;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.data.TurfRuntimeState;
import org.luckyraven.gangland.turf.events.TurfEnterEvent;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.state.TurfState;
import org.luckyraven.keystone.bean.listener.ListenerHandler;

/**
 * One chat line on {@link TurfEnterEvent} when a gang member walks into a rival or unclaimed turf that cannot be
 * captured yet, so they know why nothing starts. The line carries the same reason sentence the info card and the map
 * show ({@link TurfMessages#reason}), so every surface agrees.
 *
 * <p>Silent for: a gangless player (nothing to explain), their own turf, an allied turf, an attackable turf, and a turf
 * already CONTESTING (any gang member may help fill an active contest; the boss bar already shows it).
 */
@ListenerHandler
@RequiredArgsConstructor
public final class TurfCaptureFeedbackListener implements Listener {

	private final TurfManager           turfs;
	private final GangLookupContract    gangs;
	private final UserLookupContract    users;
	private final CaptureEligibility    eligibility;
	private final TurfMessages          text;

	@EventHandler
	public void onEnter(TurfEnterEvent event) {
		Turf   turf   = event.getTurf();
		Player player = event.getPlayer();
		long   now    = System.currentTimeMillis();

		User<Player> user = users.findByPlayer(player);
		if (user == null || !user.hasGang()) {
			return;
		}
		TurfRelation relation = TurfRelation.of(turf, user.getGangId(), gangs);
		if (relation != TurfRelation.RIVAL && relation != TurfRelation.UNCLAIMED) {
			return;
		}
		TurfRuntimeState state = turfs.getRuntimeState(turf.getId());
		if (state != null && state.getState() == TurfState.CONTESTING) {
			return;
		}

		CaptureEligibility.Verdict verdict = eligibility.evaluate(turf, now);
		if (verdict.attackable()) {
			return;
		}
		String ownerName = turf.isUnclaimed()
		                   ? ""
		                   : GangDisplayNameResolver.resolve(gangs.findById(turf.getOwnerGangId()));
		String reason = text.reason(verdict, ownerName, now);

		player.sendMessage(text.text("Info.Blocked_Line", "&e%turf% &7- %reason%",
		                             "turf", turf.getDisplayName(),
		                             "reason", reason));
	}
}
