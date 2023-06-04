package me.luckyraven.data.account.types;

import lombok.Getter;
import me.luckyraven.data.User;
import me.luckyraven.data.account.Account;

import java.util.Date;
import java.util.Set;

public class Gang extends Account<Integer, Set<User<?>>> {

	private @Getter
	final String name;

	private @Getter
	final Date created;

	public Gang(int id, Set<User<?>> users, String name) {
		super(id, users);
		this.name = name;
		this.created = new Date();
	}

	public Gang(int id, Set<User<?>> users, String name, Date date) {
		super(id, users);
		this.name = name;
		this.created = date;
	}

	public int getId() {
		return super.getKey();
	}

	public Set<User<?>> getGroup() {
		return super.getValue();
	}

	@Override
	public String toString() {
		return "ID:" + getId() + ", name=" + name + ", created=" + created;
	}

}
