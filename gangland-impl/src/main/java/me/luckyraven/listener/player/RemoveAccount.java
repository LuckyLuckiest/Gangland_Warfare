package me.luckyraven.listener.player;

import me.luckyraven.Gangland;
import me.luckyraven.Initializer;
import me.luckyraven.copsncrooks.bounty.Bounty;
import me.luckyraven.copsncrooks.wanted.Wanted;
import me.luckyraven.data.account.Bank;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.database.GanglandDatabase;
import me.luckyraven.persistence.repository.IRepository;
import me.luckyraven.util.listener.ListenerHandler;
import me.luckyraven.util.listener.ListenerPriority;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.WeaponManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

@ListenerHandler(priority = ListenerPriority.LOW)
public final class RemoveAccount implements Listener {

	private final Gangland                   gangland;
	private final Initializer                initializer;
	private final UserManager<Player>        userManager;
	private final UserManager<OfflinePlayer> offlineUserManager;
	private final WeaponManager              weaponManager;

	public RemoveAccount(Gangland gangland) {
		this.gangland           = gangland;
		this.initializer        = gangland.getInitializer();
		this.userManager        = initializer.getUserManager();
		this.offlineUserManager = initializer.getOfflineUserManager();
		this.weaponManager      = initializer.getWeaponManager();
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public synchronized void onPlayerQuit(PlayerQuitEvent event) {
		Player       player = event.getPlayer();
		User<Player> user   = gangland.getInitializer().getUserManager().getUser(player);

		if (user == null) return;

		Bukkit.getScheduler().runTaskAsynchronously(gangland, () -> {
			// remove all the inventories of that player only
			user.clearInventories();

			user.getWanted().stopTimer();
			user.getBounty().stopTimer();
		});
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerLeave(PlayerQuitEvent event) {
		Player       player = event.getPlayer();
		User<Player> user   = userManager.getUser(player);

		if (user == null) return;

		Bounty bounty = user.getBounty();
		Wanted wanted = user.getWanted();

		bounty.stopTimer();
		wanted.stopTimer();

		// Remove the user from a user manager group
		userManager.remove(user);

		GanglandDatabase ganglandDatabase = initializer.getGanglandDatabase();

		IRepository<User<? extends OfflinePlayer>> userRepository = ganglandDatabase.getRepositoryRegistry()
																					.getGenericRepository(User.class);
		IRepository<Bank> bankRepository = ganglandDatabase.getRepositoryRegistry().getRepository(Bank.class);

		// must save user info
		userRepository.save(user);

		Bank bank = user.getBank();

		if (bank != null) bankRepository.save(bank);

		if (user.getScoreboard() != null) {
			user.getScoreboard().end();
			user.setScoreboard(null);
		}

		// add to offline user manager - copy in-memory data to avoid a redundant DB round-trip
		User<OfflinePlayer> offlineUser = new User<>(gangland, player);

		offlineUser.setKills(user.getKills());
		offlineUser.setDeaths(user.getDeaths());
		offlineUser.setMobKills(user.getMobKills());
		offlineUser.setGangId(user.getGangId());
		offlineUser.getEconomy().setBalance(user.getEconomy().getBalance());
		offlineUser.getWanted().setLevel(user.getWanted().getLevel());
		offlineUser.getLevel().setLevelValue(user.getLevel().getLevelValue());
		offlineUser.getLevel().setExperience(user.getLevel().getExperience());
		offlineUser.getBounty().setAmount(user.getBounty().getAmount());
		offlineUser.setBank(user.getBank());

		offlineUserManager.add(offlineUser);

		// search if the player holds a weapon
		// check if it was a weapon
		ItemStack item   = player.getInventory().getItemInMainHand();
		Weapon    weapon = weaponManager.validateAndGetWeapon(player, item);

		if (weapon == null) return;
		if (weapon.isReloading()) weapon.stopReloading();

		weapon.unScope(player, true);
	}

}
