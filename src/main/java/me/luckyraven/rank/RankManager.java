package me.luckyraven.rank;

import lombok.Getter;

import java.util.LinkedList;
import java.util.List;

public class RankManager {

	private @Getter
	final List<Rank> ranks;

	public RankManager() {
		ranks = new LinkedList<>();
	}

	public RankManager(List<Rank> ranks) {
		this.ranks = ranks;
	}

	public <E extends Rank> void add(E element) {
		ranks.add(element);
	}

	public <E extends Rank> void remove(E element) {
		ranks.remove(element);
	}

	public Rank get(int id) {
		for (Rank rank : ranks)
			if (rank.match(id)) return rank;
		return null;
	}

	@Override
	public String toString() {
		return String.format("ranks=%s", ranks);
	}

}
