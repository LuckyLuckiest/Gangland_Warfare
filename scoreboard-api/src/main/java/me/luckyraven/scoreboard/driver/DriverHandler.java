package me.luckyraven.scoreboard.driver;

import com.viaversion.viaversion.api.ViaAPI;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import fr.mrmicky.fastboard.FastBoard;
import lombok.Getter;
import me.luckyraven.scoreboard.part.Line;
import me.luckyraven.util.Placeholder;
import org.bukkit.entity.Player;

import java.util.List;

@Getter
public abstract class DriverHandler {

	private final Placeholder placeholder;
	private final FastBoard   fastBoard;
	private final List<Line>  lines;
	private final Line        title;

	public DriverHandler(Placeholder placeholder, ViaAPI<?> viaAPI, Player player, Line title, List<Line> lines) {
		this.placeholder = placeholder;
		this.fastBoard   = new FastBoard(player) {

			@Override
			protected boolean hasLinesMaxLength() {
				// change the max line length according to the player version using ViaVersion
				if (viaAPI != null)
					return viaAPI.getPlayerVersion(getPlayer().getUniqueId()) < ProtocolVersion.v1_13.getVersion();

				// assuming the players are all >1.13
				return false;
			}
		};

		this.title = title;
		this.lines = lines;

		this.lines.add(title);

		// need to update all the values so when initialized, the user doesn't see the placeholders
		updateBoard();
	}

	public abstract void update();

	@Override
	public String toString() {
		return String.format("DriverHandler{title=%s,lines=%s}", fastBoard, lines);
	}

	protected String updateLine(Line line) {
		return line.update(placeholder, fastBoard.getPlayer());
	}

	private void updateBoard() {
		fastBoard.updateTitle(updateLine(title));
		fastBoard.updateLines(lines.stream().filter(line -> line != title).map(this::updateLine).toList());
	}

}
