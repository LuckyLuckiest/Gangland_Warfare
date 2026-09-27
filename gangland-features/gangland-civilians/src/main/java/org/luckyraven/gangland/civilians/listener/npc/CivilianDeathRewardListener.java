package org.luckyraven.gangland.civilians.listener.npc;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.civilians.events.CivilianDeathEvent;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.gangland.events.user.UserLevelUpEvent;
import org.luckyraven.gangland.core.events.level.LevelUpEvent;
import org.luckyraven.gangland.core.user.Level;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;

/**
 * Awards level XP to the killer when a civilian NPC dies. Decoupled from the cops-n-crooks module via
 * {@link CivilianDeathEvent}.
 *
 * <p>This listener does not touch wanted. A civilian kill raises wanted only through {@code EntityDamageListener} via
 * the core {@code WantedKillTrackers} seam, which applies the self-defence exemption (a hostile civilian in
 * {@code COMBAT}) and starts the wanted timer and bounty; raising it here as well counted the same kill twice and left
 * the extra star without a decay timer (fixed in 0.12).
 */
@ListenerHandler
public class CivilianDeathRewardListener implements Listener {

	private final UserManager<Player> userManager;

	public CivilianDeathRewardListener(@Qualifier("online") UserManager<Player> userManager) {
		this.userManager = userManager;
	}

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onCivilianDeath(CivilianDeathEvent event) {
		Player killer = event.getKiller();
		if (killer == null) return;

		User<Player> user = userManager.getUser(killer);
		if (user == null) return;

		// XP reward
		if (event.getExperience() > 0) {
			Level        level        = user.getLevel();
			LevelUpEvent levelUpEvent = new UserLevelUpEvent(false, user, level);
			level.addExperience(event.getExperience(), levelUpEvent);
		}

	}
}
