package me.luckyraven.data.user;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import me.luckyraven.bukkit.inventory.InventoryHandler;
import me.luckyraven.bukkit.scoreboard.Scoreboard;
import me.luckyraven.data.account.Account;
import me.luckyraven.data.economy.EconomyHandler;
import me.luckyraven.data.rank.Rank;
import me.luckyraven.feature.bounty.Bounty;
import me.luckyraven.feature.level.Level;
import me.luckyraven.feature.phone.Phone;
import me.luckyraven.feature.wanted.Wanted;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Handles all users registered data, only for online users.
 *
 * @param <T> type of the user
 */
@Getter
public class User<T extends OfflinePlayer> {

	private final T                     user;
	private final Bounty                bounty;
	private final Level                 level;
	private final Wanted                wanted;
	private final EconomyHandler        economy;
	private final List<Account<?, ?>>   linkedAccounts;
	private final Set<InventoryHandler> inventories, specialInventories;

	private @Setter int kills, deaths, mobKills, gangId;
	private @Setter Phone                phone;
	private @Setter Scoreboard           scoreboard;
	private @Setter PermissionAttachment permissionAttachment;

	@Getter(value = AccessLevel.NONE) private @Setter boolean hasBank;

	/**
	 * Instantiates a new Database.
	 *
	 * @param user the user
	 */
	public User(T user) {
		this.user               = user;
		this.bounty             = new Bounty();
		this.level              = new Level();
		this.wanted             = new Wanted();
		this.economy            = new EconomyHandler(this);
		this.linkedAccounts     = new ArrayList<>();
		this.inventories        = new HashSet<>();
		this.specialInventories = new HashSet<>();

		this.kills   = this.deaths = this.mobKills = 0;
		this.gangId  = -1;
		this.hasBank = false;
	}

	/**
	 * Has bank boolean.
	 *
	 * @return the boolean
	 */
	public boolean hasBank() {
		return hasBank;
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

	/**
	 * Add account.
	 *
	 * @param account the account
	 */
	public void addAccount(Account<?, ?> account) {
		linkedAccounts.add(account);
	}

	/**
	 * Remove account.
	 *
	 * @param account the account
	 */
	public void removeAccount(Account<?, ?> account) {
		linkedAccounts.remove(account);
	}

	/**
	 * Gets linked accounts.
	 *
	 * @return the linked accounts
	 */
	public List<Account<?, ?>> getLinkedAccounts() {
		return new ArrayList<>(linkedAccounts);
	}

	public void addSpecialInventory(InventoryHandler inventoryHandler) {
		specialInventories.add(inventoryHandler);
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
	}

	/**
	 * Remove the inventory from the user.
	 *
	 * @param inventoryHandler the inventory
	 */
	public void removeInventory(InventoryHandler inventoryHandler) {
		inventories.remove(inventoryHandler);
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
	}

	/**
	 * Get the inventories from the user.
	 *
	 * @return a copy of the inventories registered to the user.
	 */
	public List<InventoryHandler> getInventories() {
		return new ArrayList<>(inventories);
	}

	public List<InventoryHandler> getSpecialInventories() {
		return new ArrayList<>(specialInventories);
	}

	public void clearSpecialInventories() {
		specialInventories.clear();
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
		if (rank != null) for (String perm : rank.getPermissions())
			permissionAttachment.setPermission(perm, true);

		player.updateCommands();
	}

	@Override
	public String toString() {
		return String.format("User{data=%s,kd=%.2f,balance=%.2f,level=%d,bounty=%.2f,gangId=%d,permissions=%s}", user,
							 getKillDeathRatio(), economy.getBalance(), level.getLevelValue(), bounty.getAmount(),
							 gangId, permissionAttachment != null ?
									 permissionAttachment.getPermissions().keySet().stream().toList() :
									 "NA");
	}

}
