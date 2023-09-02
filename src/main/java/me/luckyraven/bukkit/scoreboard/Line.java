package me.luckyraven.bukkit.scoreboard;

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

	private final @Getter int          interval;
	private final         List<String> contents;
	private               int          index;

	public Line(int interval) {
		this.interval = interval;
		this.index = 0;
		this.contents = new ArrayList<>();
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

	public void update(JavaPlugin plugin, Player player) {
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

		contents.set(index, newContent);
	}

	public void nextContent() {
		index = (index + 1) % contents.size();
	}

}
