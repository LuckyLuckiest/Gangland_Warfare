package me.luckyraven.listener.player;

import me.luckyraven.Gangland;
import me.luckyraven.Initializer;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.database.DatabaseHelper;
import me.luckyraven.database.GanglandDatabase;
import me.luckyraven.database.component.Table;
import me.luckyraven.database.tables.BankTable;
import me.luckyraven.database.tables.UserTable;
import me.luckyraven.feature.weapon.Weapon;
import me.luckyraven.feature.weapon.WeaponManager;
import me.luckyraven.listener.ListenerHandler;
import me.luckyraven.util.timer.RepeatingTimer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

@ListenerHandler
public final class RemoveAccount implements Listener {

	private final Gangland            gangland;
	private final Initializer         initializer;
	private final UserManager<Player> userManager;
	private final WeaponManager       weaponManager;

	public RemoveAccount(Gangland gangland) {
		this.gangland      = gangland;
		this.initializer   = gangland.getInitializer();
		this.userManager   = initializer.getUserManager();
		this.weaponManager = initializer.getWeaponManager();
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerLeave(PlayerQuitEvent event) {
		Player         player      = event.getPlayer();
		User<Player>   user        = userManager.getUser(player);
		RepeatingTimer bountyTimer = user.getBounty().getRepeatingTimer();

		if (bountyTimer != null) bountyTimer.stop();

		// Remove the user from a user manager group
		userManager.remove(user);

		GanglandDatabase ganglandDatabase = initializer.getGanglandDatabase();
		DatabaseHelper   helper           = new DatabaseHelper(gangland, ganglandDatabase);
		List<Table<?>>   tables           = ganglandDatabase.getTables();

		UserTable userTable = initializer.getInstanceFromTables(UserTable.class, tables);
		BankTable bankTable = initializer.getInstanceFromTables(BankTable.class, tables);

		// must save user info
		helper.runQueriesAsync(database -> {
			userTable.updateTableQuery(database, user);
			bankTable.updateTableQuery(database, user);
		});

		if (user.getScoreboard() != null) {
			user.getScoreboard().end();
			user.setScoreboard(null);
		}

		// search if the player holds a weapon
		// check if it was a weapon
		ItemStack item   = player.getInventory().getItemInMainHand();
		Weapon    weapon = weaponManager.validateAndGetWeapon(player, item);

		if (weapon == null) return;
		if (weapon.isReloading()) weapon.stopReloading();

		weapon.unScope(player, true);
	}

}
