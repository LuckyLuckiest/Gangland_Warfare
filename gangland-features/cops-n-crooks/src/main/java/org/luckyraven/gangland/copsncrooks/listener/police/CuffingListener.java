package org.luckyraven.gangland.copsncrooks.listener.police;

import lombok.RequiredArgsConstructor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.copsncrooks.detainment.DetainmentService;
import org.luckyraven.gangland.copsncrooks.detainment.message.DetainmentMessageContract;
import org.luckyraven.gangland.copsncrooks.detainment.transit.TransitService;
import org.luckyraven.gangland.copsncrooks.events.police.CuffedEvent;
import org.luckyraven.gangland.copsncrooks.events.police.DuringCuffingEvent;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.gangland.core.downed.DownedPlayerRegistry;
import org.luckyraven.keystone.util.ChatUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ListenerHandler
@RequiredArgsConstructor
public class CuffingListener implements Listener {

	private final Map<Player, Long>         currentCuffCooldown = new ConcurrentHashMap<>();
	private final DetainmentService         detainmentService;
	private final DetainmentMessageContract messages;
	private final TransitService            transitService;

	@EventHandler
	public void onPlayerCuffing(DuringCuffingEvent event) {
		Player target = event.getTarget();

		if (target == null) return;
		if (target.isDead() || DownedPlayerRegistry.isDowned(target.getUniqueId())) return;

		long current = event.getCurrentCuffingCooldown();

		// Convert remaining ticks into a seconds display
		long secondsRemaining = computeInSeconds(current);

		if (!currentCuffCooldown.containsKey(target) ||
		    computeInSeconds(currentCuffCooldown.get(target)) != secondsRemaining) {
			currentCuffCooldown.put(target, current);

			ChatUtil.sendTitle(target, messages.cuffingTitle(), messages.cuffingSubtitle(secondsRemaining));
		}
	}

	@EventHandler
	public void onPlayerSuccessfulCuffing(CuffedEvent event) {
		Player target = event.getTarget();

		if (target == null) return;
		if (target.isDead() || DownedPlayerRegistry.isDowned(target.getUniqueId())) return;

		currentCuffCooldown.remove(target);
		detainmentService.handcuff(target);
		transitService.schedule(target);

		ChatUtil.sendTitle(target, messages.cuffedTitle(), messages.cuffedSubtitle());
	}

	private long computeInSeconds(Long currentCuffCooldown) {
		return (currentCuffCooldown + 19) / 20L;
	}
}
