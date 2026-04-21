package me.luckyraven.bootstrap;

import lombok.CustomLog;
import me.luckyraven.Gangland;
import me.luckyraven.core.bean.BeanLifecycle;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.file.configuration.inventory.InventoryLoader;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.persistence.FileManager;
import me.luckyraven.scoreboard.Scoreboard;
import me.luckyraven.scoreboard.ScoreboardManager;
import me.luckyraven.scoreboard.driver.DriverHandler;
import org.bukkit.entity.Player;

/**
 * Thin facade over the bean lifecycle for plugin reload operations. The full {@link #reload()} delegates entirely to
 * {@link GanglandContext#reloadBeans()}, which drives every {@link BeanLifecycle} bean through its onPreClear → onClear
 * → onInitialize(false) cycle in topological order.
 *
 * <p>Convenience methods ({@link #filesReload()}, {@link #scoreboardReload()}, {@link #inventoryReload()}) support the
 * sub-command variants ({@code /glw reload files}, etc.) that only reload a subset of the plugin.
 */
@CustomLog
public final class ReloadPlugin {

	private final GanglandContext context;

	public ReloadPlugin(GanglandContext context) {
		this.context = context;
	}

	/**
	 * Full plugin reload. Drives every BeanLifecycle bean through the reload cycle:
	 * <ol>
	 *     <li>PeriodicalUpdates stops timer</li>
	 *     <li>UserManager kills scoreboards, stops timers</li>
	 *     <li>FileManager reloads YAML files and re-initializes all file initializers</li>
	 *     <li>Managers reinitialize from database</li>
	 *     <li>PlayerBootstrapService loads online/offline players</li>
	 *     <li>ScoreboardLifecycleService creates scoreboards</li>
	 *     <li>PeriodicalUpdates restarts timer</li>
	 * </ol>
	 */
	public void reload() {
		context.reloadBeans();
	}

	/**
	 * Files-only reload: clears and re-initializes all file addons without touching the database cache or reloading
	 * players. Used by {@code /glw reload files}.
	 */
	public void filesReload() {
		FileManager fileManager = context.get(FileManager.class);
		fileManager.onClear();
		fileManager.onInitialize(false);
	}

	/**
	 * Scoreboard-only reload: kills all active scoreboards and recreates them from the current scoreboard
	 * configuration. Used by {@code /glw reload scoreboard}.
	 */
	@SuppressWarnings("unchecked")
	public void scoreboardReload() {
		if (!Settings.isScoreboardEnabled()) {
			return;
		}

		Gangland          gangland          = context.get(Gangland.class);
		ScoreboardManager scoreboardManager = context.get(ScoreboardManager.class);
		UserManager<Player> userManager = (UserManager<Player>) context.getContainer()
		                                                               .getInstance("online", UserManager.class);

		for (User<Player> user : userManager.getUsers().values()) {
			if (user.getScoreboard() != null) {
				user.getScoreboard().end();
				user.setScoreboard(null);
			}

			DriverHandler driverHandler = scoreboardManager.getDriverHandler(user.getUser());
			Scoreboard    scoreboard    = new Scoreboard(gangland, driverHandler);

			user.setScoreboard(scoreboard);
			user.getScoreboard().start();
		}
	}

	/**
	 * Inventory-only reload: removes all special inventories and re-initializes the inventory loader. Used by
	 * {@code /glw reload inventory}.
	 */
	public void inventoryReload() {
		InventoryHandler.removeAllSpecialInventories();
		InventoryLoader inventoryLoader = context.get(InventoryLoader.class);
		inventoryLoader.clear();
		inventoryLoader.initialize();
	}
}
