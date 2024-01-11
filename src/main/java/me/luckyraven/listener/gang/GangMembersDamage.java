package me.luckyraven.listener.gang;

import me.luckyraven.Gangland;
import me.luckyraven.data.account.gang.Gang;
import me.luckyraven.data.account.gang.GangManager;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class GangMembersDamage implements Listener {

	private final UserManager<Player> userManager;
	private final GangManager         gangManager;

	public GangMembersDamage(Gangland gangland) {
		this.userManager = gangland.getInitializer().getUserManager();
		this.gangManager = gangland.getInitializer().getGangManager();
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onGangMemberHitMembers(EntityDamageByEntityEvent event) {
		// checks for player-to-player damage
		Player damager;
		if (event.getDamager() instanceof Player player) damager = player;
		else if (event.getDamager() instanceof Projectile projectile) {
			if (projectile.getShooter() instanceof Player player) damager = player;
			else return;
		} else return;
		if (!(event.getEntity() instanceof Player damaged)) return;

		User<Player> userDamager = userManager.getUser(damager);
		User<Player> userDamaged = userManager.getUser(damaged);

		// checks if they are in a gang
		if (!(userDamager.hasGang() && userDamaged.hasGang())) return;

		// checks if they are alias or in the same gang
		Gang gang1 = gangManager.getGang(userDamager.getGangId());
		Gang gang2 = gangManager.getGang(userDamaged.getGangId());

		if (gang1.getAlly().contains(gang2) || userDamager.getGangId() == userDamaged.getGangId())
			event.setCancelled(true);

	}

}
