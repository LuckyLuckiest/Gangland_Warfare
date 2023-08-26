package me.luckyraven.data.rank;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.datastructure.Tree;

import java.util.ArrayList;
import java.util.List;

public class Rank {

	private static int ID = 0;

	private final @Getter String          name;
	private final @Getter Tree.Node<Rank> node;
	private final         List<String>    permissions;

	private @Getter
	@Setter int usedId;

	public Rank(String name) {
		this(name, new ArrayList<>());
	}

	public Rank(String name, List<String> permissions) {
		this.name = name;
		this.permissions = permissions;
		this.node = new Tree.Node<>(this);
		this.usedId = ++ID;
	}

	protected static void setID(int id) {
		Rank.ID = id;
	}

	public boolean match(int id) {
		return id == usedId;
	}

	public void addPermission(String permission) {
		permissions.add(permission);
	}

	public void removePermission(String permission) {
		permissions.remove(permission);
	}

	public boolean contains(String permission) {
		return permissions.contains(permission);
	}

	public List<String> getPermissions() {
		return new ArrayList<>(permissions);
	}

	@Override
	public String toString() {
		return String.format("{id=%d,name='%s',permissions=%s}", usedId, name, permissions);
	}

}
