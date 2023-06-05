package me.luckyraven.rank.types;

import me.luckyraven.rank.Rank;

import java.util.List;

public class Member extends Rank {

	public Member() {
		super("Member");
	}

	public Member(List<String> permissions) {
		super("Member", permissions);
	}

}
