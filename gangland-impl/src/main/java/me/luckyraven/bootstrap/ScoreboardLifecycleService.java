package me.luckyraven.bootstrap;

import lombok.CustomLog;
import me.luckyraven.core.bean.BeanLifecycle;
import me.luckyraven.core.bean.BeanPostInitialize;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.gang.user.User;
import me.luckyraven.gang.user.UserManager;
import me.luckyraven.scoreboard.Scoreboard;
import me.luckyraven.scoreboard.ScoreboardManager;
import me.luckyraven.scoreboard.driver.DriverHandler;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Creates scoreboards for all online players. Runs as a {@link BeanPostInitialize} bean because it depends on
 * {@link PlayerBootstrapService} having populated the user manager, and that bean has itself been moved to post-init to
 * avoid racing against {@link BeanLifecycle#onInitialize(boolean)} on reload. Scoreboard <i>destruction</i> is already
 * handled by {@link UserManager#onPreClear()}, which stops and nulls every user's scoreboard before data is cleared.
 *
 * <p>Depends on {@link PlayerBootstrapService} (via constructor param) to force topological ordering: players must be
 * loaded into the user manager before scoreboards can be created for them.
 */
@CustomLog
public final class ScoreboardLifecycleService implements BeanPostInitialize {

	private final JavaPlugin          plugin;
	private final ScoreboardManager   scoreboardManager;
	private final UserManager<Player> userManager;

	public ScoreboardLifecycleService(JavaPlugin plugin,
	                                  ScoreboardManager scoreboardManager,
	                                  UserManager<Player> userManager) {
		this.plugin            = plugin;
		this.scoreboardManager = scoreboardManager;
		this.userManager       = userManager;
	}

	/**
	 * Creates and starts scoreboards for all online players if the scoreboard feature is enabled.
	 */
	@Override
	public void onPostInitialize(boolean firstLoad) {
		if (!Settings.isScoreboardEnabled()) {
			return;
		}

		for (User<Player> user : userManager.getUsers().values()) {
			DriverHandler driverHandler = scoreboardManager.getDriverHandler(user.getUser());
			Scoreboard    scoreboard    = new Scoreboard(plugin, driverHandler);

			user.setScoreboard(scoreboard);
			user.getScoreboard().start();
		}

		log.debug("Scoreboard lifecycle complete: {} scoreboard(s) created", userManager.getUsers().size());
	}
}
