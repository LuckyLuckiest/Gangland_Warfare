package me.luckyraven.account.gang;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.account.Account;
import me.luckyraven.rank.Rank;

import java.time.Instant;
import java.util.*;

public class Gang extends Account<Integer, Map<UUID, Rank>> {

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
	private long created;

	public Gang() {
		super(null, null);

		Random random = new Random();
		setKey(random.nextInt(999_999));
		setValue(new HashMap<>());

		this.name = null;
		this.description = "Conquering the hood";
		this.created = Instant.now().toEpochMilli();
		this.bounty = 0D;
		this.balance = 0D;
		this.alias = new HashSet<>();
	}

	public Gang(int id) {
		this();
		setKey(id);
		setValue(new HashMap<>());
	}

	public Gang(int id, Map<UUID, Rank> users) {
		this(id);
		setValue(users);
	}

	public Gang(int id, Map<UUID, Rank> users, String name) {
		this(id, users);
		this.name = name;
	}

	public int getId() {
		return super.getKey();
	}

	public void setId(int id) {
		setKey(id);
	}

	public Map<UUID, Rank> getGroup() {
		return super.getValue();
	}

	public void setGroup(Map<UUID, Rank> users) {
		setValue(users);
	}

	public Date getDateCreated() {
		return new Date(created);
	}

	public Rank getUserRank(UUID user) {
		return getGroup().get(user);
	}

	public void setUserRank(UUID user, Rank rank) {
		getGroup().replace(user, rank);
	}

	@Override
	public String toString() {
		return String.format("ID=%d,name=%s,description=%s,members=%s,created=%s,bounty=%.2f,alias=%s", getId(), name,
		                     description, getGroup(), created, bounty, alias);
	}

}
