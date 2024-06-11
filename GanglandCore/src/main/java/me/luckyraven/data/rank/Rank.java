package me.luckyraven.data.rank;

import lombok.Getter;
import me.luckyraven.datastructure.Tree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
public class Rank {

	private static int ID = 0;

	private final String           name;
	private final Tree.Node<Rank>  node;
	private final int              usedId;
	private final List<Permission> permissions;

	public Rank(String name, int id) {
		this(name, id, new ArrayList<>());
	}

	public Rank(String name, int id, List<Permission> permissions) {
		this.name        = name;
		this.usedId      = id;
		this.permissions = permissions;
		this.node        = new Tree.Node<>(this);
	}

	public static int getNewId() {
		return ID++;
	}

	protected static void setID(int id) {
		Rank.ID = id;
	}

	public boolean match(int id) {
		return id == usedId;
	}

	public void addPermission(Permission permission) {
		permissions.add(permission);
	}

	public void removePermission(String permission) {
		permissions.removeIf(perm -> perm.getPermission().equalsIgnoreCase(permission));
	}

	public void removePermission(Permission permission) {
		permissions.remove(permission);
	}

	public boolean contains(String permission) {
		return permissions.stream().anyMatch(perm -> perm.getPermission().equalsIgnoreCase(permission));
	}

	public boolean contains(Permission permission) {
		return permissions.contains(permission);
	}

	public List<Permission> getPermissions() {
		return Collections.unmodifiableList(permissions);
	}

	@Override
	public String toString() {
		return String.format("Rank{id=%d,name='%s',permissions=%s}", usedId, name, permissions);
	}

}
