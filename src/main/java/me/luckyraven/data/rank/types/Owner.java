package me.luckyraven.data.rank.types;

import me.luckyraven.data.rank.RankAttribute;

import java.util.List;

public class Owner extends RankAttribute {

	public Owner() {
		super("Owner");
	}

	public Owner(List<String> permissions) {
		super("Owner", permissions);
	}

}
