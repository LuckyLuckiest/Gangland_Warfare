package me.luckyraven.data.rank;

import lombok.Getter;

@Getter
public class Permission {

	private static int ID = 0;

	private final int    usedId;
	private final String permission;

	public Permission(int id, String permission) {
		this.usedId     = id;
		this.permission = permission;
	}

	public static int getNewId() {
		return ID++;
	}

	protected static void setID(int id) {
		Permission.ID = id;
	}

}
