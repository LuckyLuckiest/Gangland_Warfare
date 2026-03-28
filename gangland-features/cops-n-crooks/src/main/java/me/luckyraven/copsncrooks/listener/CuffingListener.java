package me.luckyraven.copsncrooks.listener;

import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.detainment.DetainmentService;
import me.luckyraven.copsncrooks.events.police.CuffedEvent;
import me.luckyraven.copsncrooks.events.police.DuringCuffingEvent;
import me.luckyraven.util.downed.DownedPlayerRegistry;
import me.luckyraven.util.listener.ListenerHandler;
import me.luckyraven.util.utilities.ChatUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ListenerHandler
@RequiredArgsConstructor
public class CuffingListener implements Listener {

	private final Map<Player, Long> currentCuffCooldown = new ConcurrentHashMap<>();
	private final DetainmentService detainmentService;

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

			ChatUtil.sendTitle(target, "&cCuffing", "&7Restraining in &b" + secondsRemaining + "s");
		}
	}

	@EventHandler
	public void onPlayerSuccessfulCuffing(CuffedEvent event) {
		Player target = event.getTarget();

		if (target == null) return;
		if (target.isDead() || DownedPlayerRegistry.isDowned(target.getUniqueId())) return;

		currentCuffCooldown.remove(target);
		detainmentService.handcuff(target);

		ChatUtil.sendTitle(target, "&aCuffed", "&7You are restrained");
	}

	private long computeInSeconds(Long currentCuffCooldown) {
		return (currentCuffCooldown + 19) / 20L;
	}
}
