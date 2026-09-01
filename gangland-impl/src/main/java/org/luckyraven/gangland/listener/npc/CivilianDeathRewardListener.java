package org.luckyraven.gangland.listener.npc;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.copsncrooks.events.npc.CivilianDeathEvent;
import org.luckyraven.gangland.copsncrooks.npc.civilian.CivilianState;
import org.luckyraven.gangland.copsncrooks.npc.civilian.npc.CivilianNpc;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.gangland.events.user.UserLevelUpEvent;
import org.luckyraven.gangland.gang.events.level.LevelUpEvent;
import org.luckyraven.gangland.gang.user.Level;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;

/**
 * Awards level XP to the killer when a civilian NPC dies. Decoupled from the cops-n-crooks module via
 * {@link CivilianDeathEvent}.
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

		CivilianNpc civilianNpc = event.getCivilianNpc();
		if (!(civilianNpc.isHostile() && civilianNpc.getCurrentState() == CivilianState.COMBAT)) {
			user.getWanted().incrementLevel();
		}
	}
}
