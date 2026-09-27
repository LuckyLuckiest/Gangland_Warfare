package org.luckyraven.gangland.copsncrooks.listener.heat;

import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.bartizan.api.event.WeaponRaytraceImpactEvent;
import org.luckyraven.gangland.copsncrooks.heat.HeatService;
import org.luckyraven.gangland.core.events.wanted.WantedEndEvent;
import org.luckyraven.gangland.core.events.wanted.WantedLevelChangeEvent;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.keystone.npc.NpcSupport;

/**
 * Drives the 0.12 heat ledger outside of kills (kills score through the core seam, see
 * {@code KillComboWantedTracker}):
 * <ul>
 *     <li>{@code Assault_Cop} — any non-lethal damage a real player deals to a cop NPC, via melee/projectiles
 *     ({@link EntityDamageByEntityEvent}) or the weapon system ({@link WeaponRaytraceImpactEvent}). The per player and
 *     cop cooldown in {@link HeatService#recordAssault} keeps one fight from stacking heat every hit and dedups a
 *     shot delivered through both events.</li>
 *     <li>Star loss (evasion, decay, admin) lowers heat to the floor of the new level.</li>
 *     <li>Chase over or quit forgets the player's heat.</li>
 * </ul>
 */
@ListenerHandler
public class HeatListener implements Listener {

	private final HeatService         heatService;
	private final UserManager<Player> userManager;

	public HeatListener(HeatService heatService, @Qualifier("online") UserManager<Player> userManager) {
		this.heatService = heatService;
		this.userManager = userManager;
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onCopDamaged(EntityDamageByEntityEvent event) {
		if (!heatService.isEnabled()) return;

		Entity victim = event.getEntity();
		if (!heatService.isCop(victim)) return;

		Player attacker = resolvePlayerAttacker(event.getDamager());
		if (attacker == null) return;

		// A lethal hit scores Kill_Cop through the kill path instead.
		if (victim instanceof LivingEntity living && living.getHealth() <= event.getFinalDamage()) return;

		recordAssault(attacker, victim);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onCopShot(WeaponRaytraceImpactEvent event) {
		if (!heatService.isEnabled()) return;

		Entity victim = event.getHitEntity();
		if (victim == null || !heatService.isCop(victim)) return;

		if (!(event.getShooter() instanceof Player attacker) || NpcSupport.isNpc(attacker)) return;

		recordAssault(attacker, victim);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onWantedChange(WantedLevelChangeEvent event) {
		if (event.getNewLevel() >= event.getOldLevel()) return;

		heatService.lowerTo(event.getPlayer().getUniqueId(), event.getNewLevel(), event.getWanted().getMaxLevel());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onWantedEnd(WantedEndEvent event) {
		heatService.clear(event.getPlayer().getUniqueId());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerQuit(PlayerQuitEvent event) {
		heatService.clear(event.getPlayer().getUniqueId());
	}

	private void recordAssault(Player attacker, Entity cop) {
		User<Player> user = userManager.getUser(attacker);
		if (user == null) return;

		heatService.recordAssault(attacker, user.getWanted(), cop);
	}

	private static @Nullable Player resolvePlayerAttacker(Entity damager) {
		if (damager instanceof Player player && !NpcSupport.isNpc(player)) return player;
		if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player
		    && !NpcSupport.isNpc(player)) {
			return player;
		}
		return null;
	}
}
