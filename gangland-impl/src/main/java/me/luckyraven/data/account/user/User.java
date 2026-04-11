package me.luckyraven.data.account.user;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.copsncrooks.bounty.Bounty;
import me.luckyraven.copsncrooks.bounty.BountyContext;
import me.luckyraven.copsncrooks.wanted.Wanted;
import me.luckyraven.copsncrooks.wanted.WantedContext;
import me.luckyraven.data.account.Bank;
import me.luckyraven.data.account.Level;
import me.luckyraven.data.economy.EconomyException;
import me.luckyraven.data.economy.EconomyHandler;
import me.luckyraven.data.rank.Permission;
import me.luckyraven.data.rank.Rank;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.service.InventoryRegistry;
import me.luckyraven.scoreboard.Scoreboard;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.Placeholder;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Handles all users registered data, only for online users.
 *
 * @param <T> type of the user
 */
@Getter
@Setter
public class User<T extends OfflinePlayer> implements BountyContext, WantedContext {

	private final T                     user;
	private final UUID                  uuid;
	private final Bounty                bounty;
	private final Level                 level;
	private final Wanted                wanted;
	private final EconomyHandler        economy;
	private final Set<InventoryHandler> inventories;
	private final Placeholder           placeholder;
	private final InventoryRegistry     inventoryRegistry;

	@Nullable
	private Bank bank;
	private int  kills, deaths, mobKills, gangId;
	private Scoreboard           scoreboard;
	private PermissionAttachment permissionAttachment;

	/**
	 * Instantiates a new User. Prefer constructing through {@link UserFactory} so the {@link Placeholder} and
	 * {@link InventoryRegistry} dependencies come from the bean container.
	 */
	public User(JavaPlugin plugin, T user, Placeholder placeholder, InventoryRegistry inventoryRegistry) {
		this.user              = user;
		this.uuid              = user.getUniqueId();
		this.bounty            = new Bounty(Settings.getBountyEachKillValue(), Settings.getBountyTimerMultiple());
		this.level             = new Level();
		this.wanted            = new Wanted(plugin, Settings.getWantedLevelIncrement(),
		                                    Settings.getWantedMaximumLevel());
		this.economy           = new EconomyHandler(this);
		this.inventories       = new HashSet<>();
		this.placeholder       = placeholder;
		this.inventoryRegistry = inventoryRegistry;

		this.wanted.setOwner(user.getPlayer());

		this.kills  = this.deaths = this.mobKills = 0;
		this.gangId = -1;
		this.bank   = null;
	}

	/**
	 * Has bank boolean.
	 *
	 * @return the boolean
	 */
	public boolean hasBank() {
		return this.bank != null;
	}

	/**
	 * Reset gang.
	 */
	public void resetGang() {
		this.gangId = -1;
	}

	/**
	 * Has gang boolean.
	 *
	 * @return the boolean
	 */
	public boolean hasGang() {
		return this.gangId != -1;
	}

	@Override
	public int getUserLevel() {
		return level.getLevelValue();
	}

	@Override
	public double withdraw(double requestedAmount) {
		try {
			economy.withdraw(requestedAmount);
			return requestedAmount;
		} catch (EconomyException ignored) {
			double balance = economy.getBalance();
			try {
				economy.withdraw(balance);
			} catch (EconomyException ignored2) {
				return 0;
			}
			return balance;
		}
	}

	public void sendMessage(String text) {
		if (!(user instanceof Player player)) return;

		String resolved = placeholder.convert(player, text);
		String message  = GanglandChatUtil.color(resolved);

		player.sendMessage(message);
	}

	public void sendMessage(String... texts) {
		for (String text : texts) sendMessage(text);
	}

	/**
	 * Add the inventory to the user.
	 *
	 * @param inventoryHandler the inventory
	 */
	public void addInventory(InventoryHandler inventoryHandler) {
		// remove the inventory if it was already generated
		removeInventory(inventoryHandler.getTitle().getKey());

		// add the inventory to the set
		inventories.add(inventoryHandler);

		// register with the global registry
		inventoryRegistry.registerInventory(uuid, inventoryHandler);
	}

	/**
	 * Remove the inventory from the user.
	 *
	 * @param inventoryHandler the inventory
	 */
	public void removeInventory(InventoryHandler inventoryHandler) {
		inventories.remove(inventoryHandler);

		// unregister from the global registry
		inventoryRegistry.unregisterInventory(uuid, inventoryHandler);
	}

	/**
	 * Remove the inventory from the user.
	 *
	 * @param name the name of the inventory
	 */
	public void removeInventory(String name) {
		InventoryHandler inventory = getInventory(name);

		if (inventory == null) return;

		inventories.remove(inventory);
	}

	/**
	 * Get the inventory from the user.
	 *
	 * @param name the name of the inventory
	 *
	 * @return the inventory if found or null
	 */
	@Nullable
	public InventoryHandler getInventory(String name) {
		return inventories.stream()
				.filter(handler -> handler.getTitle().getKey().equals(name.toLowerCase()))
				.findFirst()
				.orElse(null);
	}

	/**
	 * Clears all the inventories from the user.
	 */
	public void clearInventories() {
		inventories.clear();

		// clear from the global registry
		inventoryRegistry.clear(uuid);
	}

	/**
	 * Get the inventories from the user.
	 *
	 * @return a copy of the inventories registered to the user.
	 */
	public List<InventoryHandler> getInventories() {
		return inventories.stream().toList();
	}

	/**
	 * Gets a kills/deaths ratio of the user.
	 *
	 * @return the kd ratio
	 */
	public double getKillDeathRatio() {
		return deaths == 0 ? 0D : (double) kills / deaths;
	}

	/**
	 * Flushes permissions and (if set) adds the new rank permissions.
	 *
	 * @param rank the rank
	 */
	public void flushPermissions(@Nullable Rank rank) {
		if (!(user instanceof Player player)) return;

		for (String permission : permissionAttachment.getPermissions().keySet())
			permissionAttachment.unsetPermission(permission);

		// add the new rank attachments
		if (rank != null) for (Permission perm : rank.getPermissions())
			permissionAttachment.setPermission(perm.getPermission(), true);

		player.updateCommands();
	}

	@Override
	public String toString() {
		return String.format("User{data=%s,kd=%.2f,balance=%.2f,level=%d,bounty=%.2f,gangId=%d,permissions=%s}", user,
		                     getKillDeathRatio(), economy.getBalance(), level.getLevelValue(), bounty.getAmount(),
		                     gangId, permissionAttachment != null ?
		                             permissionAttachment.getPermissions().keySet()
									 .stream().toList() :
		                             "NA");
	}

}
