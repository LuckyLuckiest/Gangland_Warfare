package me.luckyraven.listener.npc;

import me.luckyraven.copsncrooks.events.npc.CivilianDeathEvent;
import me.luckyraven.copsncrooks.npc.civilian.CivilianState;
import me.luckyraven.copsncrooks.npc.civilian.npc.CivilianNpc;
import me.luckyraven.core.autowire.bean.Qualifier;
import me.luckyraven.core.listener.ListenerHandler;
import me.luckyraven.data.account.Level;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.events.level.LevelUpEvent;
import me.luckyraven.events.user.UserLevelUpEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

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
