package me.luckyraven.account.type;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.data.user.User;
import me.luckyraven.account.Account;

import java.util.Date;
import java.util.Set;

public class Gang extends Account<Integer, Set<User<?>>> {

	@Getter
	private final Set<Gang> alias;

	@Getter
	@Setter
	private String name, description;
	@Getter
	@Setter
	private double bounty, balance;
	@Getter
	@Setter
	private Date created;

	public Gang(int id, Set<User<?>> users, String name) {
		super(id, users);
		this.name = name;
		this.description = "Conquering the hood";
		this.created = new Date();
		this.bounty = 0D;
		this.balance = 0D;
		this.alias = null;
	}

	public int getId() {
		return super.getKey();
	}

	public Set<User<?>> getGroup() {
		return super.getValue();
	}

	@Override
	public String toString() {
		return String.format("ID=%d,name=%s,description=%s,members=%s,created=%s,bounty=%.2f,alias=%s", getId(), name,
		                     description, getGroup(), created, bounty, alias);
	}

}
