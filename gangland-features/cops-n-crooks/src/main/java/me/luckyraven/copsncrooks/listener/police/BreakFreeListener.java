package me.luckyraven.copsncrooks.listener.police;

import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.detainment.breakfree.BreakFreeService;
import me.luckyraven.core.bean.listener.ListenerHandler;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;

/**
 * Routes sneak-key presses to the break-free minigame. Sneak-release is ignored so only the downward press counts.
 */
@ListenerHandler
@RequiredArgsConstructor
public class BreakFreeListener implements Listener {

	private final BreakFreeService breakFreeService;

	@EventHandler
	public void onSneak(PlayerToggleSneakEvent event) {
		if (!event.isSneaking()) return;
		breakFreeService.registerTap(event.getPlayer());
	}
}
