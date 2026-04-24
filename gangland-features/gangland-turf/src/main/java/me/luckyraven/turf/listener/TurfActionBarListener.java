package me.luckyraven.turf.listener;

import lombok.RequiredArgsConstructor;
import me.luckyraven.core.bean.listener.ListenerHandler;
import me.luckyraven.core.utilities.ActionBarManager;
import me.luckyraven.core.utilities.ChatUtil;
import me.luckyraven.gang.Gang;
import me.luckyraven.gang.contract.GangLookupContract;
import me.luckyraven.turf.contract.TurfMessageContract;
import me.luckyraven.turf.events.TurfEnterEvent;
import me.luckyraven.turf.events.TurfExitEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Announces turf crossings to the player. On entry we send both:
 * <ul>
 *   <li>A big {@link Player#sendTitle title + subtitle} so the crossing is impossible to miss — key for letting
 *       players realise "this region is a turf".</li>
 *   <li>An action-bar line that stays visible briefly below the hotbar as a secondary reminder of ownership.</li>
 * </ul>
 * Exit clears the action bar. All user-facing text is routed through the Messages enum via
 * {@link TurfMessageContract} so localisation stays centralised.
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

	private final GangLookupContract  gangs;
	private final TurfMessageContract messages;

	@EventHandler
	public void onEnter(TurfEnterEvent event) {
		Player  player  = event.getPlayer();
		Integer ownerId = event.getTurf().getOwnerGangId();

		String actionBar;
		String subtitle;
		if (ownerId == null) {
			actionBar = messages.format("TURF_ENTER_UNCLAIMED",
			                            "turf", event.getTurf().getDisplayName());
			subtitle  = messages.format("TURF_SUBTITLE_UNCLAIMED");
		} else {
			Gang   owner    = gangs.findById(ownerId);
			String gangName = GangDisplayNameResolver.resolve(owner);
			actionBar = messages.format("TURF_ENTER_OWNED",
			                            "turf", event.getTurf().getDisplayName(),
			                            "gang", gangName);
			subtitle  = messages.format("TURF_SUBTITLE_OWNED",
			                            "gang", gangName);
		}
		String title = messages.format("TURF_TITLE",
		                               "turf", event.getTurf().getDisplayName());

		player.sendTitle(ChatUtil.color(title), ChatUtil.color(subtitle),
		                 TITLE_FADE_IN, TITLE_STAY, TITLE_FADE_OUT);
		ActionBarManager.send(player, ChatUtil.color(actionBar));
	}

	@EventHandler
	public void onExit(TurfExitEvent event) {
		String exitLine = messages.format("TURF_EXIT",
		                                  "turf", event.getTurf().getDisplayName());
		ActionBarManager.send(event.getPlayer(), ChatUtil.color(exitLine));
	}
}
