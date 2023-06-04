package me.luckyraven.data.rank.types;

import me.luckyraven.data.rank.RankAttribute;

import java.util.List;

public class Member extends RankAttribute {

	public Member() {
		super("Member");
	}

	public Member(List<String> permissions) {
		super("Member", permissions);
	}

}
