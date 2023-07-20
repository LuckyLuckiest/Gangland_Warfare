package me.luckyraven.account.gang;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.account.Account;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.rank.Rank;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.*;

@Getter
@Setter
public class Gang extends Account<Integer, List<Member>> {

	private final Set<Gang> alias;
	private       String    name, displayName, color, description;
	private double bounty, balance;
	private long created;

	public Gang() {
		super(null, null);

		Random random = new Random();
		setKey(random.nextInt(999_999));
		setValue(new ArrayList<>());

		this.name = null;
		this.displayName = "";
		this.color = "";
		this.description = "Conquering the hood";
		this.created = Instant.now().toEpochMilli();
		this.bounty = 0D;
		this.balance = 0D;
		this.alias = new HashSet<>();
	}

	public Gang(int id) {
		this();
		setKey(id);
	}

	public Gang(int id, List<Member> users) {
		this(id);
		setValue(users);
	}

	public Gang(int id, List<Member> users, String name) {
		this(id, users);
		this.name = name;
	}

	public int getId() {
		return super.getKey();
	}

	public void setId(int id) {
		setKey(id);
	}

	public <T> void addMember(User<T> user, Member member, Rank rank) {
		user.setGangId(this.getId());
		user.addAccount(this);
		addMember(member, rank);
	}

	public void addMember(Member member, Rank rank) {
		member.setGangId(this.getId());
		member.setRank(rank);
		getGroup().add(member);
	}

	public <T> void removeMember(User<T> user, Member member) {
		if (!getGroup().contains(member)) return;
		user.setGangId(-1);
		user.removeAccount(this);
		removeMember(member);
	}

	public void removeMember(Member member) {
		if (!getGroup().contains(member)) return;
		member.setGangId(-1);
		member.setContribution(0D);
		member.setRank(null);
		getGroup().remove(member);
	}

	public List<Member> getGroup() {
		return super.getValue();
	}

	public void setGroup(List<Member> users) {
		setValue(users);
	}

	public List<User<Player>> getOnlineMembers(UserManager<Player> userManager) {
		List<User<Player>> users = new ArrayList<>();

		for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
			User<Player> onUser = userManager.getUser(onlinePlayer);
			if (onUser.hasGang() && onUser.getGangId() == this.getId()) users.add(onUser);
		}

		return users;
	}

	public Date getDateCreated() {
		return new Date(created);
	}

	@Override
	public String toString() {
		return String.format("ID=%d,name=%s,description=%s,members=%s,created=%s,bounty=%,.2f,alias=%s", getId(), name,
		                     description, getGroup(), created, bounty, alias);
	}

}
