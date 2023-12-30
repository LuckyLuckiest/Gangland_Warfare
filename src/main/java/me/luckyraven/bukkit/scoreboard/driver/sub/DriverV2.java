package me.luckyraven.bukkit.scoreboard.driver.sub;

import me.luckyraven.Gangland;
import me.luckyraven.bukkit.scoreboard.driver.DriverHandler;
import me.luckyraven.bukkit.scoreboard.part.Line;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DriverV2 extends DriverHandler {

	private final Map<Long, List<Line>> clusters;
	private final Map<Long, Integer>    clustersInterval;

	public DriverV2(Gangland gangland, Player player) {
		super(gangland, player);

		this.clusters = getLines().stream().collect(Collectors.groupingBy(Line::getInterval));
		this.clustersInterval = new HashMap<>();

		for (long key : clusters.keySet())
			this.clustersInterval.put(key, 0);
	}

	@Override
	public void update() {
		for (Map.Entry<Long, Integer> entry : clustersInterval.entrySet()) {
			long interval        = entry.getKey();
			int  currentInterval = entry.getValue();

			if (interval == currentInterval && interval != 0) {
				// get the lines
				List<Line> lines = clusters.get(interval);

				// update the lines in fastboard
				if (lines.contains(getTitle())) getFastBoard().updateTitle(updateLine(getTitle()));
				else lines.stream().filter(line -> line != getTitle()).forEach(
						line -> getFastBoard().updateLine(line.getUsedIndex(), updateLine(line)));
			}

			// increment the current interval value
			clustersInterval.replace(interval, (int) ((currentInterval + 1) % (interval + 1)));
		}
	}

}
