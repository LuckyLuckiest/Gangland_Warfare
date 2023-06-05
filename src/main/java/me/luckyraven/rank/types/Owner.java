package me.luckyraven.rank.types;

import me.luckyraven.rank.Rank;

import java.util.List;

public class Owner extends Rank {

	public Owner() {
		super("Owner");
	}

	public Owner(List<String> permissions) {
		super("Owner", permissions);
	}

}
