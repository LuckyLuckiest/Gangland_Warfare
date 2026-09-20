package org.luckyraven.gangland.listener.player;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.keystone.bean.listener.ListenerPriority;
import org.luckyraven.gangland.database.GanglandDatabase;
import org.luckyraven.keystone.economy.bank.Bank;
import org.luckyraven.gangland.gang.bounty.Bounty;
import org.luckyraven.gangland.gang.user.User;
import org.luckyraven.gangland.gang.user.UserManager;
import org.luckyraven.gangland.gang.wanted.Wanted;
import org.luckyraven.gangland.file.configuration.inventory.InventoryRuntimeContext;
import org.luckyraven.gangland.inventory.service.InventoryRegistry;
import org.luckyraven.keystone.persistence.repository.IRepository;

@ListenerHandler(priority = ListenerPriority.LOW)
public final class RemoveAccountListener implements Listener {

	private final Gangland                   gangland;
	private final GanglandDatabase           ganglandDatabase;
	private final UserManager<Player>        userManager;
	private final UserManager<OfflinePlayer> offlineUserManager;
	private final InventoryRegistry          inventoryRegistry;
	private final InventoryRuntimeContext    inventoryRuntimeContext;

	public RemoveAccountListener(Gangland gangland,
	                             GanglandDatabase ganglandDatabase,
	                             @Qualifier("online") UserManager<Player> userManager,
	                             @Qualifier("offline") UserManager<OfflinePlayer> offlineUserManager,
	                             InventoryRegistry inventoryRegistry,
	                             InventoryRuntimeContext inventoryRuntimeContext) {
		this.gangland                = gangland;
		this.ganglandDatabase        = ganglandDatabase;
		this.userManager             = userManager;
		this.offlineUserManager      = offlineUserManager;
		this.inventoryRegistry       = inventoryRegistry;
		this.inventoryRuntimeContext = inventoryRuntimeContext;
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public synchronized void onPlayerQuit(PlayerQuitEvent event) {
		Player player = event.getPlayer();

		// Unconditional and synchronous — mirrors PlayerInventoryCleanup's shape (WS2 G1+G2 fix round 2). Must run
		// even when the User lookup below fails (a real path — InventoryRuntimeContext.openInventoryForPlayer
		// itself guards against a null User the same way), or that player's entry in InventoryRuntimeContext's
		// interim per-player map leaks forever: the CONFIG-phase bean is a singleton that survives reload, so
		// nothing else ever clears it. ConcurrentHashMap-backed, safe on the main thread.
		inventoryRuntimeContext.clearPlayer(player.getUniqueId());

		User<Player> user = userManager.getUser(player);

		if (user == null) return;

		Bukkit.getScheduler().runTaskAsynchronously(gangland, () -> {
			// remove all the inventories of that player only (WS2 G2: severed off User, goes through the
			// registry bean directly). NOTE (ruling W38): this only clears bookkeeping, exactly as
			// User.clearInventories() did before it — no held item was ever returned on quit here, before or
			// after this change. Once WS2 G3 retargets menu-opening onto keystone-inventory, Keystone's own
			// MenuListener.onQuit takes over and DOES return held items (its OpenMenuTracker-driven close path),
			// closing that longstanding gap as a side effect of the G3 retarget rather than something this gate
			// fixes.
			inventoryRegistry.clear(user.getUuid());

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
	}

}
