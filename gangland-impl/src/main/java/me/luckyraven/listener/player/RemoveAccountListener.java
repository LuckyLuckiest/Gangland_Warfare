package me.luckyraven.listener.player;

import me.luckyraven.Gangland;
import me.luckyraven.copsncrooks.bounty.Bounty;
import me.luckyraven.copsncrooks.wanted.Wanted;
import me.luckyraven.core.bean.Qualifier;
import me.luckyraven.core.bean.listener.ListenerHandler;
import me.luckyraven.core.bean.listener.ListenerPriority;
import me.luckyraven.database.GanglandDatabase;
import me.luckyraven.economy.bank.Bank;
import me.luckyraven.gang.user.User;
import me.luckyraven.gang.user.UserManager;
import me.luckyraven.persistence.repository.IRepository;
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
public final class RemoveAccountListener implements Listener {

	private final Gangland                   gangland;
	private final GanglandDatabase           ganglandDatabase;
	private final UserManager<Player>        userManager;
	private final UserManager<OfflinePlayer> offlineUserManager;
	private final WeaponManager              weaponManager;

	public RemoveAccountListener(Gangland gangland,
	                             GanglandDatabase ganglandDatabase,
	                             @Qualifier("online") UserManager<Player> userManager,
	                             @Qualifier("offline") UserManager<OfflinePlayer> offlineUserManager,
	                             WeaponManager weaponManager) {
		this.gangland           = gangland;
		this.ganglandDatabase   = ganglandDatabase;
		this.userManager        = userManager;
		this.offlineUserManager = offlineUserManager;
		this.weaponManager      = weaponManager;
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public synchronized void onPlayerQuit(PlayerQuitEvent event) {
		Player       player = event.getPlayer();
		User<Player> user   = userManager.getUser(player);

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
		User<OfflinePlayer> offlineUser = offlineUserManager.create(player);

		offlineUser.setKills(user.getKills());
		offlineUser.setDeaths(user.getDeaths());
		offlineUser.setMobKills(user.getMobKills());
		offlineUser.setGangId(user.getGangId());
		offlineUser.getEconomy().setAmount(user.getEconomy().getAmount());
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
