package me.luckyraven.bootstrap;

import lombok.CustomLog;
import me.luckyraven.Gangland;
import me.luckyraven.core.autowire.bean.BeanLifecycle;
import me.luckyraven.core.autowire.bean.BeanPostInitialize;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.scoreboard.Scoreboard;
import me.luckyraven.scoreboard.ScoreboardManager;
import me.luckyraven.scoreboard.driver.DriverHandler;
import org.bukkit.entity.Player;

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

	private final Gangland            gangland;
	private final ScoreboardManager   scoreboardManager;
	private final UserManager<Player> userManager;

	@SuppressWarnings("unused") // forces topo ordering — players loaded before scoreboards created
	private final PlayerBootstrapService playerBootstrapService;

	public ScoreboardLifecycleService(Gangland gangland,
	                                  ScoreboardManager scoreboardManager,
	                                  UserManager<Player> userManager,
	                                  PlayerBootstrapService playerBootstrapService) {
		this.gangland               = gangland;
		this.scoreboardManager      = scoreboardManager;
		this.userManager            = userManager;
		this.playerBootstrapService = playerBootstrapService;
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
			Scoreboard    scoreboard    = new Scoreboard(gangland, driverHandler);

			user.setScoreboard(scoreboard);
			user.getScoreboard().start();
		}

		log.debug("Scoreboard lifecycle complete: {} scoreboard(s) created", userManager.getUsers().size());
	}
}
