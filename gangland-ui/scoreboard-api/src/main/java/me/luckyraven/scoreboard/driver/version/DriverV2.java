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

public class DriverV2 extends DriverHandler {

	private final Map<Long, List<Line>> clusters;
	private final Map<Long, Integer>    clustersInterval;

	public DriverV2(Placeholder placeholder, ViaAPI<?> viaAPI, Player player, Line title, List<Line> lines) {
		super(placeholder, viaAPI, player, title, lines);

		this.clusters         = getLines().stream().collect(Collectors.groupingBy(Line::getInterval));
		this.clustersInterval = new HashMap<>();

		for (long key : clusters.keySet())
			this.clustersInterval.put(key, 0);
	}

	@Override
	public void update() {
		updateFlashLines();

		for (Map.Entry<Long, Integer> entry : clustersInterval.entrySet()) {
			long interval        = entry.getKey();
			int  currentInterval = entry.getValue();

			if (interval == currentInterval && interval != 0) {
				// get the lines
				List<Line> lines = clusters.get(interval);

				// update the lines in fastboard
				if (lines.contains(getTitle())) {
					String newTitle = updateLine(getTitle());
					getFastBoard().updateTitle(newTitle);
				} else {
					lines.stream().filter(line -> line != getTitle()).forEach(line -> {
						String updatedLine = updateLine(line);
						getFastBoard().updateLine(line.getUsedIndex(), updatedLine);
					});
				}
			}

			// increment the current interval value
			clustersInterval.replace(interval, (int) ((currentInterval + 1) % (interval + 1)));
		}
	}

	/**
	 * Update all lines containing flash effects every tick for smooth animation
	 */
	private void updateFlashLines() {
		for (Line line : getLines()) {
			if (line == getTitle()) continue;

			if (!isFlashLine(line)) continue;

			getFastBoard().updateLine(line.getUsedIndex(), updateLine(line));
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
