package org.luckyraven.gangland.gang.user;

import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.util.Placeholder;

/**
 * KERNEL-phase bean that constructs {@link User} instances with their {@link Placeholder} dependency wired from the
 * bean container. Use this everywhere instead of calling {@code new User<>(...)} directly so {@code User} no longer
 * needs a static placeholder field.
 */
public class UserFactory {

	private final JavaPlugin  plugin;
	private final Placeholder placeholder;

	public UserFactory(JavaPlugin plugin, Placeholder placeholder) {
		this.plugin      = plugin;
		this.placeholder = placeholder;
	}

	public <T extends OfflinePlayer> User<T> create(T offlinePlayer) {
		return new User<>(plugin, offlinePlayer, placeholder);
	}

}
