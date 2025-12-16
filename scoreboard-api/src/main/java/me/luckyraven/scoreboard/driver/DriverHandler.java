package me.luckyraven.scoreboard.driver;

import com.viaversion.viaversion.api.ViaAPI;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import fr.mrmicky.fastboard.FastBoard;
import lombok.Getter;
import me.luckyraven.scoreboard.part.Line;
import me.luckyraven.util.Placeholder;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public abstract class DriverHandler {

	private final Placeholder     placeholder;
	private final FastBoard       fastBoard;
	private final List<Line>      lines;
	private final Line            title;
	private final Map<Line, Long> lineUpdateCounts;

	private long globalTickCount;

	public DriverHandler(Placeholder placeholder, ViaAPI<?> viaAPI, Player player, Line title, List<Line> lines) {
		this.placeholder      = placeholder;
		this.fastBoard        = new FastBoardImpl(player, viaAPI);
		this.title            = title;
		this.lines            = lines;
		this.lineUpdateCounts = new HashMap<>();
		this.globalTickCount  = 0L;

		this.lines.add(title);

		// initialize line update counts
		this.lines.forEach(line -> lineUpdateCounts.put(line, 0L));

		// need to update all the values so when initialized, the user doesn't see the placeholders
		updateBoard();
	}

	public abstract void update();

	@Override
	public String toString() {
		return String.format("DriverHandler{title=%s,lines=%s}", fastBoard, lines);
	}

	public long getLineTickCount(Line line) {
		return lineUpdateCounts.getOrDefault(line, 0L);
	}

	protected String updateLine(Line line) {
		return line.update(placeholder, fastBoard.getPlayer());
	}

	protected void incrementTick() {
		globalTickCount++;
	}

	private boolean shouldUpdateLine(Line line) {
		// Static lines never update after initial
		if (line.isStatic() || line.getInterval() == 0L) {
			return false;
		}

		// Update when globalTickCount is divisible by the line's interval
		return globalTickCount % line.getInterval() == 0L;
	}

	private void updateBoard() {
		// update title
		if (shouldUpdateLine(title)) {
			lineUpdateCounts.merge(title, 1L, Long::sum);
			fastBoard.updateTitle(updateLine(title));
		}

		// update lines
		List<String> updateLines = lines.stream().filter(line -> line != title).map(line -> {
			if (shouldUpdateLine(line)) {
				lineUpdateCounts.merge(line, 1L, Long::sum);
				return updateLine(line);
			} else {
				// return cached content
				return line.getCurrentContent();
			}
		}).toList();

		fastBoard.updateLines(updateLines);
	}

	private static class FastBoardImpl extends FastBoard {

		private final ViaAPI<?> viaAPI;

		public FastBoardImpl(Player player, ViaAPI<?> viaAPI) {
			super(player);

			this.viaAPI = viaAPI;
		}

		@Override
		protected boolean hasLinesMaxLength() {
			// change the max line length according to the player version using ViaVersion
			if (viaAPI != null) {
				int playerVersion = viaAPI.getPlayerVersion(getPlayer().getUniqueId());
				int version       = ProtocolVersion.v1_13.getVersion();

				return playerVersion < version;
			}

			// assuming the players are all >1.13
			return false;
		}
	}

}
