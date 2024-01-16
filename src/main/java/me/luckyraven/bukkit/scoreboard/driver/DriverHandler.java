package me.luckyraven.bukkit.scoreboard.driver;

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import fr.mrmicky.fastboard.FastBoard;
import lombok.Getter;
import me.luckyraven.Gangland;
import me.luckyraven.bukkit.scoreboard.part.Line;
import me.luckyraven.file.configuration.ScoreboardAddon;
import org.bukkit.entity.Player;

import java.util.List;

@Getter
public abstract class DriverHandler {

	private final Gangland   gangland;
	private final FastBoard  fastBoard;
	private final List<Line> lines;
	private final Line       title;

	public DriverHandler(Gangland gangland, Player player) {
		this.gangland = gangland;
		this.fastBoard = new FastBoard(player) {

			@Override
			protected boolean hasLinesMaxLength() {
				// change the max line length according to the player version using ViaVersion
				if (gangland.getViaAPI() != null)
					return gangland.getViaAPI().getPlayerVersion(getPlayer().getUniqueId()) <
						   ProtocolVersion.v1_13.getVersion();

				// assuming the players are all >1.13
				return false;
			}
		};

		ScoreboardAddon addon = gangland.getInitializer().getScoreboardAddon();

		this.lines = addon.getLines();
		this.title = addon.getTitle();

		this.lines.add(title);

		// need to update all the values so when initialized, the user doesn't see the placeholders
		updateBoard();
	}

	public abstract void update();

	protected String updateLine(Line line) {
		return line.update(gangland, fastBoard.getPlayer());
	}

	private void updateBoard() {
		fastBoard.updateTitle(updateLine(title));
		fastBoard.updateLines(lines.stream().filter(line -> line != title).map(this::updateLine).toList());
	}

	@Override
	public String toString() {
		return String.format("DriverHandler{title=%s,lines=%s}", fastBoard, lines);
	}

}
