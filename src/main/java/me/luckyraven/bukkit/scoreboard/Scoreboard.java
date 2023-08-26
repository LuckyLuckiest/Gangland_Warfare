package me.luckyraven.bukkit.scoreboard;

import fr.mrmicky.fastboard.FastBoard;
import me.luckyraven.Gangland;
import me.luckyraven.data.user.User;
import me.luckyraven.util.timer.RepeatingTimer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class Scoreboard {

	private final User<Player>   user;
	private final FastBoard      fastBoard;
	private final RepeatingTimer timer;

	public Scoreboard(User<Player> user) {
		this.user = user;
		this.fastBoard = new FastBoard(user.getUser());

		Gangland gangland = JavaPlugin.getPlugin(Gangland.class);

		this.timer = new RepeatingTimer(gangland, 5 * 20L, (t) -> updateScoreboardContent());
	}

	public void updateScoreboardContent() {
		// TODO improve the scoreboard content
		Player player = user.getUser();

		String line1 = "Health: " + player.getHealth();
		String line2 = "Level: " + player.getLevel();
		String line3 = "Coins: " + user.getEconomy().getBalance();

		fastBoard.updateTitle(player.getName());

		fastBoard.updateLines(line1, line2, line3);
	}

	public void start() {
		timer.startAsync();
	}

	public void end() {
		timer.stop();
		fastBoard.delete();
	}

}
