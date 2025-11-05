package me.luckyraven.scoreboard.driver.version;

import com.viaversion.viaversion.api.ViaAPI;
import me.luckyraven.scoreboard.driver.DriverHandler;
import me.luckyraven.scoreboard.part.Line;
import me.luckyraven.util.Placeholder;
import org.bukkit.entity.Player;

import java.util.*;

public class DriverV1 extends DriverHandler {

	private final Map<Long, List<Line>> clusters;
	private final Map<Long, Integer>    clustersInterval;

	public DriverV1(Placeholder placeholder, ViaAPI<?> viaAPI, Player player, Line title, List<Line> lines) {
		super(placeholder, viaAPI, player, title, lines);

		this.clusters         = createClusters(getLines());
		this.clustersInterval = new HashMap<>();

		for (long key : clusters.keySet())
			this.clustersInterval.put(key, 0);
	}

	@Override
	public void update() {
		// this method should work each and every tick
		for (Map.Entry<Long, Integer> entry : clustersInterval.entrySet()) {
			long interval        = entry.getKey();
			int  currentInterval = entry.getValue();

			if (interval == currentInterval && interval != 0) {
				// get the lines
				List<Line> lines = clusters.get(interval);

				// update the lines in fastboard
				if (lines.contains(getTitle())) getFastBoard().updateTitle(updateLine(getTitle()));
				else lines.stream()
						.filter(line -> line != getTitle())
						.forEach(line -> getFastBoard().updateLine(line.getUsedIndex(), updateLine(line)));
			}

			// increment the current interval value
			clustersInterval.replace(interval, (int) ((currentInterval + 1) % (interval + 1)));
		}
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
				currentCluster  = new ArrayList<>();
				currentInterval = line.getInterval();

				clusters.put(currentInterval, currentCluster);
			}
			if (currentCluster != null) currentCluster.add(line);
		}

		return clusters;
	}

}
