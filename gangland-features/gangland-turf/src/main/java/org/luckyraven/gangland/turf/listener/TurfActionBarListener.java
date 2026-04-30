package org.luckyraven.gangland.turf.listener;

import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.core.bean.listener.ListenerHandler;
import org.luckyraven.gangland.core.utilities.ActionBarManager;
import org.luckyraven.gangland.core.utilities.ChatUtil;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.turf.contract.TurfDisplayContract;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.data.TurfRuntimeState;
import org.luckyraven.gangland.turf.events.TurfEnterEvent;
import org.luckyraven.gangland.turf.events.TurfExitEvent;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.state.CapturePhase;
import org.luckyraven.gangland.turf.state.TurfState;

/**
 * Announces turf crossings to the player. On entry we send both:
 * <ul>
 *   <li>A big {@link Player#sendTitle title + subtitle} so the crossing is impossible to miss — key for letting
 *       players realise "this region is a turf".</li>
 *   <li>An action-bar line that stays visible briefly below the hotbar as a secondary reminder of ownership.</li>
 * </ul>
 * The wording branches on whether the turf is currently being contested — entering a turf mid-capture says
 * "Phase 1 in progress" (global claim, anyone helps fill it) or "Phase 2: %gang% consolidating" (per-gang
 * tug-of-war) instead of the static owned/unclaimed labels, so the player understands what they walked into.
 * All user-facing text is routed through the Messages enum via {@link TurfMessageContract} so localisation stays
 * centralised.
 */
@ListenerHandler
@RequiredArgsConstructor
public final class TurfActionBarListener implements Listener {

	/**
	 * Title fade/stay timing in ticks — 10 / 40 / 10 = half a second in, two seconds on screen, half a second out.
	 * Tight enough that sprinting across several turfs doesn't queue titles.
	 */
	private static final int TITLE_FADE_IN  = 10;
	private static final int TITLE_STAY     = 40;
	private static final int TITLE_FADE_OUT = 10;

	private final TurfManager         turfs;
	private final GangLookupContract  gangs;
	private final TurfMessageContract messages;
	private final TurfDisplayContract display;

	@EventHandler
	public void onEnter(TurfEnterEvent event) {
		Player           player = event.getPlayer();
		Turf             turf   = event.getTurf();
		TurfRuntimeState state  = turfs.getRuntimeState(turf.getId());

		String actionBar;
		String subtitle;

		boolean contesting = state != null && state.getState() == TurfState.CONTESTING;
		if (contesting && state.getPhase() == CapturePhase.CLAIM) {
			actionBar = messages.format("TURF_ENTER_CONTESTING_CLAIM",
			                            "turf", turf.getDisplayName());
			subtitle  = messages.format("TURF_SUBTITLE_CONTESTING_CLAIM");
		} else if (contesting && state.getPhase() == CapturePhase.CONSOLIDATE) {
			Gang challenger = state.getChallengerGangId() == null ?
			                  null :
			                  gangs.findById(state.getChallengerGangId());
			String gangName = GangDisplayNameResolver.resolve(challenger);
			actionBar = messages.format("TURF_ENTER_CONTESTING_CONSOLIDATE",
			                            "turf", turf.getDisplayName(),
			                            "gang", gangName);
			subtitle  = messages.format("TURF_SUBTITLE_CONTESTING_CONSOLIDATE",
			                            "gang", gangName);
		} else {
			Integer ownerId = turf.getOwnerGangId();
			if (ownerId == null) {
				actionBar = messages.format("TURF_ENTER_UNCLAIMED",
				                            "turf", turf.getDisplayName());
				subtitle  = messages.format("TURF_SUBTITLE_UNCLAIMED");
			} else {
				Gang   owner    = gangs.findById(ownerId);
				String gangName = GangDisplayNameResolver.resolve(owner);
				actionBar = messages.format("TURF_ENTER_OWNED",
				                            "turf", turf.getDisplayName(),
				                            "gang", gangName);
				subtitle  = messages.format("TURF_SUBTITLE_OWNED",
				                            "gang", gangName);
			}
		}
		String title = messages.format("TURF_TITLE",
		                               "turf", turf.getDisplayName());

		if (display.isEnterTitleEnabled()) {
			player.sendTitle(ChatUtil.color(title), ChatUtil.color(subtitle),
			                 TITLE_FADE_IN, TITLE_STAY, TITLE_FADE_OUT);
		}
		ActionBarManager.send(player, ChatUtil.color(actionBar));
	}

	@EventHandler
	public void onExit(TurfExitEvent event) {
		String exitLine = messages.format("TURF_EXIT",
		                                  "turf", event.getTurf().getDisplayName());
		ActionBarManager.send(event.getPlayer(), ChatUtil.color(exitLine));
	}
}
