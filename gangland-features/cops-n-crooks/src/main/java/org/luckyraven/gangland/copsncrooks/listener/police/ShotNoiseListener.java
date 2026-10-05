package org.luckyraven.gangland.copsncrooks.listener.police;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.luckyraven.bartizan.api.event.WeaponShootEvent;
import org.luckyraven.gangland.copsncrooks.npc.police.CopGroup;
import org.luckyraven.gangland.copsncrooks.npc.police.CopManager;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopLoader;
import org.luckyraven.gangland.copsncrooks.npc.police.config.ShotNoiseSettings;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.keystone.npc.NpcSupport;

import java.util.Map;

/**
 * A wanted player's shot tells the nearest cop (within the weapon's noise radius) where he is: the squad re-centres
 * on the shooter, which the evasion clock reads as a sighting. MONITOR + ignoreCancelled, so
 * {@link DetainmentListener}'s restrained-shooter cancel wins. The 3 s throttle is the radio's {@code Shots_Fired}
 * cooldown.
 */
@ListenerHandler
public class ShotNoiseListener implements Listener {

	private final CopManager          copManager;
	private final CopLoader           copLoader;
	private final CopRadio            copRadio;
	private final UserManager<Player> users;

	public ShotNoiseListener(CopManager copManager, CopLoader copLoader, CopRadio copRadio,
	                         @Qualifier("online") UserManager<Player> users) {
		this.copManager = copManager;
		this.copLoader  = copLoader;
		this.copRadio   = copRadio;
		this.users      = users;
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onWeaponShoot(WeaponShootEvent event) {
		if (!(event.getShooter() instanceof Player shooter) || NpcSupport.isNpc(shooter)) return;

		CopConfigProvider provider = copLoader.getLoadedProvider();
		ShotNoiseSettings noise    = provider == null ? null : provider.getShotNoiseSettings();
		if (noise == null) noise = ShotNoiseSettings.DEFAULT;

		double radius = noise.radiusFor(event.getWeapon().getCategory().name());
		if (radius <= 0) return;

		User<Player> user = users.getUser(shooter);
		if (user == null || !user.getWanted().isWanted()) return;

		CopGroup group = copManager.groupOf(shooter.getUniqueId());
		if (group == null) return;

		Location here    = shooter.getLocation();
		CopNpc   nearest = null;
		double   best    = radius * radius;
		synchronized (group.getCops()) {
			for (CopNpc cop : group.getCops()) {
				LivingEntity entity = cop.getEntity();
				if (!cop.isValid() || cop.getCurrentState() == CopState.RETURNING || entity == null) continue;

				Location there = entity.getLocation();
				if (there.getWorld() != here.getWorld()) continue;

				double d = there.distanceSquared(here);
				if (d <= best) {
					best    = d;
					nearest = cop;
				}
			}
		}
		if (nearest == null) return;

		group.getSquad().reportSighting(here);
		copRadio.sayAs(group, nearest, "Shots_Fired", Map.of());
	}
}
