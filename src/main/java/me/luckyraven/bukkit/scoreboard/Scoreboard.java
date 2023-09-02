package me.luckyraven.bukkit.scoreboard;

import fr.mrmicky.fastboard.FastBoard;
import me.luckyraven.Gangland;
import me.luckyraven.bukkit.scoreboard.part.Line;
import me.luckyraven.data.user.User;
import me.luckyraven.util.timer.RepeatingTimer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class Scoreboard {

	private final Gangland       gangland;
	private final User<Player>   user;
	private final FastBoard      fastBoard;
	private final List<Line>     lines;
	private final RepeatingTimer timer;

	private Line title;

	public Scoreboard(User<Player> user) {
		this.gangland = JavaPlugin.getPlugin(Gangland.class);
		this.user = user;
		this.fastBoard = new FastBoard(user.getUser());
		this.lines = new ArrayList<>();

		initializeLines();

		Map<Long, List<Line>> clusters         = createClusters(lines);
		Map<Long, Integer>    clusterIntervals = new HashMap<>();

		for (long key : clusters.keySet())
			clusterIntervals.put(key, 0);

		// repeating the task each tick
		this.timer = new RepeatingTimer(gangland, 0L, 1L, time -> updateScoreboard(clusters, clusterIntervals));
	}

	private void initializeLines() {
		ScoreboardManager scoreboardManager = gangland.getInitializer().getScoreboardManager();

		this.title = scoreboardManager.getTitle();
		this.lines.addAll(scoreboardManager.getLines());
		this.lines.add(title);

		// need to update all the values so when initialized, the user doesn't see the placeholders
		fastBoard.updateTitle(updateLine(title));
		fastBoard.updateLines(lines.stream().filter(line -> line != title).map(this::updateLine).toList());
	}

	private Map<Long, List<Line>> createClusters(List<Line> lines) {
		List<Line> sortedLines = new ArrayList<>(lines);
		sortedLines.sort(Comparator.comparingLong(Line::getInterval));

		// create the clusters
		// basically each cluster is a collection of lines
		Map<Long, List<Line>> clusters = new HashMap<>();

		List<Line> currentCluster  = null;
		long       currentInterval = -1;

		for (Line line : sortedLines) {
			if (line.getInterval() != currentInterval) {
				currentCluster = new ArrayList<>();
				currentInterval = line.getInterval();

				clusters.put(currentInterval, currentCluster);
			}
			if (currentCluster != null) currentCluster.add(line);
		}

		return clusters;
	}

	private String updateLine(Line line) {
		return line.update(gangland, user.getUser());
	}

	public void updateScoreboard(Map<Long, List<Line>> clusters, Map<Long, Integer> clustersInterval) {
		// this method should work each and every tick
		for (Map.Entry<Long, Integer> entry : clustersInterval.entrySet()) {
			long interval        = entry.getKey();
			int  currentInterval = entry.getValue();

			if (interval == currentInterval && interval != 0) {
				// get the lines
				List<Line> lines = clusters.get(interval);

				// update the lines in fastboard
				if (lines.contains(title)) fastBoard.updateTitle(updateLine(title));
				else lines.stream().filter(line -> line != title).forEach(
						line -> fastBoard.updateLine(line.getUsedIndex(), updateLine(line)));
			}

			// increment the current interval value
			clustersInterval.replace(interval, (int) ((currentInterval + 1) % (interval + 1)));
		}
	}

	public void start() {
		if (timer == null) return;

		timer.start(true);
	}

	public void end() {
		if (timer == null) return;

		timer.stop();
		fastBoard.delete();
	}

}
