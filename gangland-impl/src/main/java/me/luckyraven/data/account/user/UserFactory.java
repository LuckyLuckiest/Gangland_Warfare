package me.luckyraven.data.account.user;

import me.luckyraven.inventory.service.InventoryRegistry;
import me.luckyraven.util.Placeholder;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * KERNEL-phase bean that constructs {@link User} instances with their {@link Placeholder} and {@link InventoryRegistry}
 * dependencies wired from the bean container. Use this everywhere instead of calling {@code new User<>(...)} directly
 * so {@code User} no longer needs a static placeholder field.
 */
public class UserFactory {

	private final JavaPlugin        plugin;
	private final Placeholder       placeholder;
	private final InventoryRegistry inventoryRegistry;

	public UserFactory(JavaPlugin plugin, Placeholder placeholder, InventoryRegistry inventoryRegistry) {
		this.plugin            = plugin;
		this.placeholder       = placeholder;
		this.inventoryRegistry = inventoryRegistry;
	}

	public <T extends OfflinePlayer> User<T> create(T offlinePlayer) {
		return new User<>(plugin, offlinePlayer, placeholder, inventoryRegistry);
	}

}
