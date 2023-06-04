package me.luckyraven.data.rank;

import lombok.Getter;

import java.util.LinkedList;
import java.util.List;

public class Rank {

	private @Getter
	final List<RankAttribute> ranks;

	public Rank() {
		ranks = new LinkedList<>();
	}

	public Rank(List<RankAttribute> ranks) {
		this.ranks = ranks;
	}

	public <E extends RankAttribute> void add(E element) {
		ranks.add(element);
	}

	public <E extends RankAttribute> void remove(E element) {
		ranks.remove(element);
	}

	public RankAttribute get(int id) {
		for (RankAttribute rank : ranks)
			if (rank.match(id)) return rank;
		return null;
	}

	@Override
	public String toString() {
		return String.format("ranks=%s", ranks);
	}

}
