package me.luckyraven.bukkit.scoreboard.driver.sub;

import me.luckyraven.Gangland;
import me.luckyraven.bukkit.scoreboard.driver.DriverHandler;
import me.luckyraven.bukkit.scoreboard.part.Line;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * DriverV3: Minimal-diff, low-overhead scoreboard driver.
 * <p>
 * Strategy: - Cluster lines by their interval like previous drivers. - Maintain a cache of the last rendered text per
 * line (including the title). - On each tick, process only clusters whose interval is due, and only send FastBoard
 * updates for lines whose computed text actually changed.
 * <p>
 * This reduces redundant updates (no-ops) and thus lowers bandwidth and CPU usage, while still honoring per-line update
 * intervals.
 */
public class DriverV3 extends DriverHandler {

	private final Map<Long, List<Line>> clusters;
	private final Map<Long, Integer>    clustersInterval;
	private final Map<Line, String>     cache;

	public DriverV3(Gangland gangland, Player player) {
		super(gangland, player);

		this.clusters         = getLines().stream().collect(Collectors.groupingBy(Line::getInterval));
		this.clustersInterval = new HashMap<>();
		this.cache            = new HashMap<>();

		for (long key : clusters.keySet()) {
			this.clustersInterval.put(key, 0);
		}
	}

	@Override
	public void update() {
		for (Map.Entry<Long, Integer> entry : clustersInterval.entrySet()) {
			long interval        = entry.getKey();
			int  currentInterval = entry.getValue();

			// Only process when the cluster is due, and ignore 0 (never updates)
			if (interval == currentInterval && interval != 0) {
				List<Line> lines = clusters.get(interval);

				if (lines == null || lines.isEmpty()) {
					continue;
				}

				// If the cluster contains the title, handle it separately
				if (lines.contains(getTitle())) {
					String newTitle = updateLine(getTitle());
					String oldTitle = cache.get(getTitle());
					if (oldTitle == null || !oldTitle.equals(newTitle)) {
						getFastBoard().updateTitle(newTitle);
						cache.put(getTitle(), newTitle);
					}
				}

				// Update only the lines whose value changed
				for (Line line : lines) {
					if (line == getTitle()) continue; // already handled as title

					String newValue = updateLine(line);
					String oldValue = cache.get(line);

					if (oldValue == null || !oldValue.equals(newValue)) {
						getFastBoard().updateLine(line.getUsedIndex(), newValue);
						cache.put(line, newValue);
					}
				}
			}

			// increment the current interval value
			clustersInterval.replace(interval, (int) ((currentInterval + 1) % (interval + 1)));
		}
	}
}
