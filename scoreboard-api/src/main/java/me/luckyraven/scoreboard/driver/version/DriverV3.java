package me.luckyraven.scoreboard.driver.version;

import com.viaversion.viaversion.api.ViaAPI;
import me.luckyraven.scoreboard.driver.DriverHandler;
import me.luckyraven.scoreboard.part.Line;
import me.luckyraven.util.Placeholder;
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

	public DriverV3(Placeholder placeholder, ViaAPI<?> viaAPI, Player player, Line title, List<Line> lines) {
		super(placeholder, viaAPI, player, title, lines);

		this.clusters         = getLines().stream().collect(Collectors.groupingBy(Line::getInterval));
		this.clustersInterval = new HashMap<>();
		this.cache            = new HashMap<>();

		for (long key : clusters.keySet()) {
			this.clustersInterval.put(key, 0);
		}
	}

	@Override
	public void update() {
		updateFlashLines();

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
					if (line == getTitle()) continue;
					if (isFlashLine(line)) continue;

					String newValue = updateLine(line);
					String oldValue = cache.get(line);

					if (oldValue == null || !oldValue.equals(newValue)) {
						getFastBoard().updateLine(line.getUsedIndex(), newValue);
						cache.put(line, newValue);
					}
				}
			}

			// increment the current interval value
			int  current = currentInterval + 1;
			long inter   = interval + 1;
			int  value   = (int) (current % inter);

			clustersInterval.replace(interval, value);
		}
	}

	/**
	 * Update all lines containing flash effects every tick for smooth animation
	 */
	private void updateFlashLines() {
		for (Line line : getLines()) {
			if (line == getTitle()) continue;

			// Check if this line contains flash effects
			if (!isFlashLine(line)) continue;

			String newValue = updateLine(line);
			String oldValue = cache.get(line);

			// Always update if value changed (flash effect changes every tick)
			if (!(oldValue == null || !oldValue.equals(newValue))) continue;

			getFastBoard().updateLine(line.getUsedIndex(), newValue);
			cache.put(line, newValue);
		}
	}

	/**
	 * Check if a line contains flash effects
	 */
	private boolean isFlashLine(Line line) {
		String content = line.getCurrentContent();

		return content != null && (content.contains("flashif:") || content.contains("flash:"));
	}

}
