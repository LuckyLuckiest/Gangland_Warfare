package me.luckyraven.rank;

import lombok.Getter;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class Rank {

	private static int ID = 0;

	@Getter
	private final int    usedId;
	@Getter
	private final String name;

	private final List<String> permissions;

	{
		this.usedId = ID++;
	}

	public Rank(String name) {
		this.name = name;
		this.permissions = new LinkedList<>();
	}

	public Rank(String name, List<String> permissions) {
		this.name = name;
		this.permissions = permissions;
	}

	public boolean match(int id) {
		return id == usedId;
	}

	public void add(String permission) {
		permissions.add(permission);
	}

	public void remove(String permission) {
		permissions.remove(permission);
	}

	public boolean contain(String permission) {
		return permissions.contains(permission);
	}

	public List<String> getPermissions() {
		return new ArrayList<>(permissions);
	}

	@Override
	public String toString() {
		return String.format("RankInfo{id=%d, name='%s', permissions=%s}", usedId, name, permissions);
	}

}
