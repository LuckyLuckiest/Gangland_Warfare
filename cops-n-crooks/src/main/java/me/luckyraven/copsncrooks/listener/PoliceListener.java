package me.luckyraven.copsncrooks.listener;

import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.police.PoliceAIState;
import me.luckyraven.copsncrooks.police.PoliceManager;
import me.luckyraven.copsncrooks.police.PoliceUnit;
import me.luckyraven.copsncrooks.wanted.WantedEndEvent;
import me.luckyraven.copsncrooks.wanted.WantedLevelChangeEvent;
import me.luckyraven.copsncrooks.wanted.WantedStartEvent;
import me.luckyraven.util.listener.ListenerHandler;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

@ListenerHandler
@RequiredArgsConstructor
public class PoliceListener implements Listener {

	private final PoliceManager policeManager;

	@EventHandler(priority = EventPriority.MONITOR)
	public void onWantedStart(WantedStartEvent event) {
		policeManager.onWantedLevelStart(event.getPlayer(), event.getWanted());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onWantedEnd(WantedEndEvent event) {
		policeManager.onWantedLevelEnd(event.getPlayer());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onWantedChange(WantedLevelChangeEvent event) {
		policeManager.onWantedLevelChange(event.getPlayer(), event.getWanted(), event.getOldLevel(),
										  event.getNewLevel());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerQuit(PlayerQuitEvent event) {
		// Clean up police when player leaves
		Player player = event.getPlayer();

		for (PoliceUnit unit : policeManager.getPoliceForPlayer(player.getUniqueId())) {
			unit.setState(PoliceAIState.DESPAWNING);
		}
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onPoliceDamage(EntityDamageByEntityEvent event) {
		Entity victim = event.getEntity();

		// Prevent police from one-shotting players
		if (!policeManager.isPoliceUnit(event.getDamager())) return;
		if (!(victim instanceof Player player)) return;

		double newHealth = player.getHealth() - event.getFinalDamage();

		// Cap damage to prevent instant kills from full health
		if (!(newHealth <= 0 && player.getHealth() > 10)) return;

		event.setDamage(player.getHealth() - 1);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPoliceKilled(EntityDeathEvent event) {
		if (!policeManager.isPoliceUnit(event.getEntity())) return;

		// Clear drops from police
		event.getDrops().clear();
		event.setDroppedExp(0);
	}

}
