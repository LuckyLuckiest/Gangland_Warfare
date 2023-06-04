package me.luckyraven.data.rank;

import lombok.Getter;

import java.util.LinkedList;
import java.util.List;

public class RankAttribute {

	private static int          ID = 0;
	private @Getter
	final          int          usedId;
	private @Getter
	final          String       name;
	private @Getter
	final          List<String> permissions;

	{
		this.usedId = ID++;
	}

	public RankAttribute(String name) {
		this.name = name;
		this.permissions = new LinkedList<>();
	}

	public RankAttribute(String name, List<String> permissions) {
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

	@Override
	public String toString() {
		return String.format("RankInfo{id=%d, name='%s', permissions=%s}", usedId, name, permissions);
	}

}
