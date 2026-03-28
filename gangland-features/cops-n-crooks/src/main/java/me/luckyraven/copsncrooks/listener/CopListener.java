package me.luckyraven.copsncrooks.listener;

import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.events.wanted.WantedEndEvent;
import me.luckyraven.copsncrooks.events.wanted.WantedLevelChangeEvent;
import me.luckyraven.copsncrooks.events.wanted.WantedStartEvent;
import me.luckyraven.copsncrooks.police.CopManager;
import me.luckyraven.copsncrooks.police.npc.CopNpc;
import me.luckyraven.util.downed.PlayerDownedEvent;
import me.luckyraven.util.listener.ListenerHandler;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

@ListenerHandler
@RequiredArgsConstructor
public class CopListener implements Listener {

	private final CopManager copManager;

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
		Player player = event.getPlayer();
		copManager.removeCopAttacker(player.getUniqueId());
		copManager.onWantedEnd(player);
	}

	/**
	 * Removes a player from the cop-attacker registry when they die so that cops do not continue attacking them after
	 * respawn.
	 *
	 * @param event the player death event
	 */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerDeath(PlayerDeathEvent event) {
		copManager.removeCopAttacker(event.getEntity().getUniqueId());
	}

	/**
	 * Removes a player from the cop-attacker registry when they are downed (GTA-style death that prevents actual
	 * death). Mirrors the behaviour of {@link #onPlayerDeath} for the downed state.
	 *
	 * @param event the player downed event
	 */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerDowned(PlayerDownedEvent event) {
		copManager.removeCopAttacker(event.getPlayer().getUniqueId());
	}

	/**
	 * Handles a player attacking a cop NPC - forces the cop into combat mode.
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

		CopNpc cop = copManager.findCopByEntity(event.getEntity());
		if (cop != null) {
			cop.destroy();
		}

		event.getDrops().clear();
		event.setDroppedExp(0);
	}
}