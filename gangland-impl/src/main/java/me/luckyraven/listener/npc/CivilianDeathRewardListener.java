package me.luckyraven.listener.npc;

import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.events.npc.CivilianDeathEvent;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.events.level.LevelUpEvent;
import me.luckyraven.events.user.UserLevelUpEvent;
import me.luckyraven.features.level.Level;
import me.luckyraven.util.listener.ListenerHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Awards level XP to the killer when a civilian NPC dies. Decoupled from the cops-n-crooks module via
 * {@link CivilianDeathEvent}.
 */
@ListenerHandler
@RequiredArgsConstructor
public class CivilianDeathRewardListener implements Listener {
	private final UserManager<Player> userManager;

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onCivilianDeath(CivilianDeathEvent event) {
		Player killer = event.getKiller();
		if (killer == null || event.getExperience() <= 0) return;

		User<Player> user = userManager.getUser(killer);
		if (user == null) return;

		Level        level        = user.getLevel();
		LevelUpEvent levelUpEvent = new UserLevelUpEvent(false, user, level);
		level.addExperience(event.getExperience(), levelUpEvent);
	}
}
