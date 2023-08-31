package me.luckyraven.bukkit.scoreboard;

import fr.mrmicky.fastboard.FastBoard;
import me.luckyraven.Gangland;
import me.luckyraven.data.user.User;
import me.luckyraven.util.timer.RepeatingTimer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class Scoreboard {

	private static int            index = 0;
	private final  List<String>   items;
	private final  User<Player>   user;
	private final  FastBoard      fastBoard;
	private final  RepeatingTimer timer;

	public Scoreboard(User<Player> user) {
		this.user = user;
		this.fastBoard = new FastBoard(user.getUser());
		this.items = new ArrayList<>();

		items.add("Health: " + user.getUser().getHealth());
		items.add("Hunger: " + user.getUser().getFoodLevel());

		Gangland gangland = JavaPlugin.getPlugin(Gangland.class);

		int time = 2;
		this.timer = new RepeatingTimer(gangland, time * 20L, t -> updateScoreboardContent());
	}

	public void updateScoreboardContent() {
		// TODO improve the scoreboard content
		Player player = user.getUser();

		String line1 = items.get(index++);
		index %= items.size();
		String line2 = "Level: " + user.getLevel().getLevelValue();
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
