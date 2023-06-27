package me.luckyraven.account.gang;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.account.Account;

import java.util.*;

public class Gang extends Account<Integer, Set<UUID>> {

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

	public Gang() {
		super(null, null);

		Random random = new Random();
		setKey(random.nextInt(999999));
		setValue(new HashSet<>());

		this.name = null;
		this.description = "Conquering the hood";
		this.created = new Date();
		this.bounty = 0D;
		this.balance = 0D;
		this.alias = new HashSet<>();
	}

	public Gang(int id) {
		this();
		setKey(id);
		setValue(new HashSet<>());
	}

	public Gang(int id, Set<UUID> users) {
		this(id);
		setValue(users);
	}

	public Gang(int id, Set<UUID> users, String name) {
		this(id, users);
		this.name = name;
	}

	public int getId() {
		return super.getKey();
	}

	public void setId(int id) {
		setKey(id);
	}

	public Set<UUID> getGroup() {
		return super.getValue();
	}

	public void setGroup(Set<UUID> users) {
		setValue(users);
	}

	@Override
	public String toString() {
		return String.format("ID=%d,name=%s,description=%s,members=%s,created=%s,bounty=%.2f,alias=%s", getId(), name,
		                     description, getGroup(), created, bounty, alias);
	}

}
