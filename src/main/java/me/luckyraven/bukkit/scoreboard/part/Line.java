package me.luckyraven.bukkit.scoreboard.part;

import lombok.Getter;
import me.luckyraven.Gangland;
import me.luckyraven.util.ChatUtil;
import org.bukkit.entity.Player;

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

	public String update(Gangland gangland, Player player) {
		String data       = getCurrentContent();
		String newContent = gangland.usePlaceholder(player, data);

		if (newContent.isEmpty()) newContent = data;

		index = (index + 1) % contents.size();

		return newContent;
	}

	public boolean isStatic() {
		return this instanceof StaticLine || interval == 0L;
	}

	@Override
	public String toString() {
		return String.format("Line{interval=%d,contents=%s}", interval, contents);
	}

}
