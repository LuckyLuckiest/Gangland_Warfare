package me.luckyraven.listener.player;

import me.luckyraven.Gangland;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.database.DatabaseHandler;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.sub.UserDatabase;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

public class EntityDamage implements Listener {

	private final UserManager<Player> userManager;
	private final Gangland            gangland;

	public EntityDamage(Gangland gangland) {
		this.gangland = gangland;
		this.userManager = gangland.getInitializer().getUserManager();
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onPlayerEntityDeath(EntityDamageByEntityEvent event) {
		Player damager;
		if (event.getDamager() instanceof Player player) damager = player;
		else if (event.getDamager() instanceof Projectile projectile) {
			if (projectile.getShooter() instanceof Player player) damager = player;
			else return;
		} else return;

		if (!(event.getEntity() instanceof LivingEntity livingEntity &&
				livingEntity.getHealth() <= event.getFinalDamage())) return;

		User<Player> user = userManager.getUser(damager.getPlayer());

		// check if it was a player or a mob
		if (event.getEntity() instanceof Player) user.setKills(user.getKills() + 1);
		else user.setMobKills(user.getMobKills() + 1);

		updateDatabase(user);
	}

	private void updateDatabase(User<Player> user) {
		for (DatabaseHandler handler : gangland.getInitializer().getDatabaseManager().getDatabases())
			if (handler instanceof UserDatabase userDatabase) {
				DatabaseHelper helper = new DatabaseHelper(gangland, handler);

				helper.runQueries(database -> userDatabase.updateDataTable(user));
				break;
			}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onPlayerDeath(PlayerDeathEvent event) {
		Player       player = event.getEntity();
		User<Player> user   = userManager.getUser(player);

		user.setDeaths(user.getDeaths() + 1);
		updateDatabase(user);
	}

}
