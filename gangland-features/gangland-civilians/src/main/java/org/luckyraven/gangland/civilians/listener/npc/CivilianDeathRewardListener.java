package org.luckyraven.gangland.civilians.listener.npc;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.luckyraven.gangland.civilians.events.CivilianDeathEvent;
import org.luckyraven.gangland.civilians.npc.CivilianState;
import org.luckyraven.gangland.civilians.npc.npc.CivilianNpc;
import org.luckyraven.gangland.core.wanted.WantedKillTrackers;
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
 * <p>Wanted: with the cops-n-crooks module loaded, {@link WantedKillTrackers#isActive()} is true and a civilian kill
 * raises wanted only through {@code EntityDamageListener} via that core seam, which applies the self-defence
 * exemption (a hostile civilian in {@code COMBAT}) and starts the wanted timer and bounty; raising it here as well
 * would count the same kill twice and leave the extra star without a decay timer (fixed in 0.12). Without
 * cops-n-crooks loaded, no seam is ever installed and {@code EntityDamageListener} cannot raise wanted for an NPC on
 * its own, so this listener falls back to the pre-0.12 direct increment, keeping the same self-defence exemption.
 */
@ListenerHandler
public class CivilianDeathRewardListener implements Listener {

	private final UserManager<Player> userManager;
	private final WantedKillTrackers  wantedKills;

	public CivilianDeathRewardListener(@Qualifier("online") UserManager<Player> userManager,
	                                   WantedKillTrackers wantedKills) {
		this.userManager = userManager;
		this.wantedKills = wantedKills;
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

		// Wanted fallback: only when no cops-n-crooks seam is installed to raise it instead (see class Javadoc).
		if (!wantedKills.isActive() && !isSelfDefence(event.getCivilianNpc())) {
			user.getWanted().incrementLevel();
		}
	}

	/**
	 * A hostile civilian that is fighting back is fair game: killing it never raises wanted. Mirrors
	 * {@code KillComboWantedTracker#isSelfDefence}.
	 */
	private static boolean isSelfDefence(CivilianNpc npc) {
		return npc.isHostile() && npc.getCurrentState() == CivilianState.COMBAT;
	}
}
