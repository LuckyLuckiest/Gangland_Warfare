package me.luckyraven.bukkit.scoreboard.part;

import lombok.Getter;
import me.clip.placeholderapi.PlaceholderAPI;
import me.luckyraven.Gangland;
import me.luckyraven.data.placeholder.GanglandPlaceholder;
import me.luckyraven.util.ChatUtil;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class Line {

	private final @Getter long         interval;
	private final         List<String> contents;
	private final @Getter int          usedIndex;

	private int index;

	public Line(long interval) {
		this(interval, 0);
	}

	public Line(long interval, int index) {
		this.interval = interval;
		this.index = 0;
		this.contents = new ArrayList<>();
		this.usedIndex = index;
	}

	public void addContent(String content) {
		contents.add(ChatUtil.color(content));
	}

	public void addAllContents(List<String> contents) {
		contents.forEach(this::addContent);
	}

	public String getCurrentContent() {
		return contents.get(index);
	}

	public String update(JavaPlugin plugin, Player player) {
		String data       = getCurrentContent();
		String newContent = "";

		if (plugin instanceof Gangland gangland) {
			if (gangland.getPlaceholderAPIExpansion() != null) {
				if (PlaceholderAPI.containsPlaceholders(data)) newContent = PlaceholderAPI.setPlaceholders(player,
				                                                                                           data);
			} else {
				GanglandPlaceholder placeholder = gangland.getInitializer().getPlaceholder();

				if (placeholder.containsPlaceholder(data)) newContent = placeholder.replacePlaceholder(player, data);
			}
		}

		if (newContent.isEmpty()) newContent = data;

		index = (index + 1) % contents.size();

		return newContent;
	}

	public boolean isStatic() {
		return this instanceof StaticLine || interval == 0 || contents.size() == 1;
	}

	@Override
	public String toString() {
		return String.format("Line{interval=%d,contents=%s}", interval, contents);
	}

}
