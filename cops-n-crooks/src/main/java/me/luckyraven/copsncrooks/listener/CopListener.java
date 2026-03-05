package me.luckyraven.copsncrooks.listener;

import me.luckyraven.copsncrooks.events.wanted.WantedEndEvent;
import me.luckyraven.copsncrooks.events.wanted.WantedLevelChangeEvent;
import me.luckyraven.copsncrooks.events.wanted.WantedStartEvent;
import me.luckyraven.copsncrooks.police.CopManager;
import me.luckyraven.copsncrooks.police.npc.CopNpc;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listens for relevant game events and delegates to the CopManager.
 */
public class CopListener implements Listener {

	private final CopManager copManager;

	public CopListener(CopManager copManager) {
		this.copManager = copManager;
	}

	/**
	 * Starts cop pursuit when a player becomes wanted.
	 *
	 * @param event the wanted start event
	 */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onWantedStart(WantedStartEvent event) {
		copManager.onWantedStart(event.getPlayer(), event.getWanted());
	}

	/**
	 * Stops cop pursuit when a player is no longer wanted.
	 *
	 * @param event the wanted end event
	 */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onWantedEnd(WantedEndEvent event) {
		copManager.onWantedEnd(event.getPlayer());
	}

	/**
	 * Updates cop assignments when wanted level changes.
	 *
	 * @param event the wanted level change event
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onWantedChange(WantedLevelChangeEvent event) {
		copManager.onWantedLevelChange(event.getPlayer(), event.getWanted(), event.getOldLevel(), event.getNewLevel());
	}

	/**
	 * Cleans up cops when a player leaves the server.
	 *
	 * @param event the player quit event
	 */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerQuit(PlayerQuitEvent event) {
		copManager.onWantedEnd(event.getPlayer());
	}

	/**
	 * Handles a player attacking a cop NPC — forces the cop into combat mode.
	 *
	 * @param event the damage event
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onCopDamaged(EntityDamageByEntityEvent event) {
		Entity victim = event.getEntity();

		if (!copManager.isCopNpc(victim)) return;

		Entity damager = event.getDamager();

		Player attacker;
		if (damager instanceof Player player) {
			attacker = player;
		} else if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
			attacker = player;
		} else return;

		CopNpc cop = copManager.findCopByEntity(victim);
		if (cop == null) return;

		// Alert system: put ALL cops for this player into combat mode
		copManager.onCopAttackedAlert(cop, attacker);
	}

	/**
	 * Clears drops when a cop NPC is killed.
	 *
	 * @param event the death event
	 */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onCopDeath(EntityDeathEvent event) {
		if (!copManager.isCopNpc(event.getEntity())) return;

		event.getDrops().clear();
		event.setDroppedExp(0);
	}
}