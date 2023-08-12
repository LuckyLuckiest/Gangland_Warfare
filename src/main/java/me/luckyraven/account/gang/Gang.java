package me.luckyraven.account.gang;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.account.Account;
import me.luckyraven.bounty.Bounty;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.level.Level;
import me.luckyraven.rank.Rank;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.color.Color;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;

public class Gang extends Account<Integer, List<Member>> {


	private final @Getter Set<Gang> ally;

	private @Getter
	@Setter String name, displayName, color, description;
	private @Getter
	@Setter double balance;
	private @Getter
	@Setter long   created;

	private @Getter Level  level;
	private @Getter Bounty bounty;

	public Gang(int id, List<Member> users, String name) {
		this(id, users);
		this.name = name;
	}

	public Gang(int id, List<Member> users) {
		this(id);
		setValue(users);
	}

	public Gang(int id) {
		this();
		setKey(id);
	}

	public Gang() {
		super(null, new ArrayList<>());

		Random random = new Random();
		setKey(random.nextInt(999_999));

		this.name = null;
		this.displayName = "";
		this.color = Color.LIGHT_BLUE.name();
		this.description = "Conquering the hood";
		this.created = Instant.now().toEpochMilli();
		this.balance = 0D;
		this.bounty = new Bounty();
		this.level = new Level();
		this.ally = new HashSet<>();
	}

	public <T> void addMember(User<T> user, Member member, Rank rank) {
		user.setGangId(this.getId());
		user.addAccount(this);
		addMember(member, rank);
	}

	public int getId() {
		return super.getKey();
	}

	public void setId(int id) {
		setKey(id);
	}

	public void addMember(Member member, Rank rank) {
		member.setGangId(this.getId());
		member.setRank(rank);
		getGroup().add(member);
	}

	public List<Member> getGroup() {
		return super.getValue();
	}

	public void setGroup(List<Member> users) {
		setValue(users);
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

	public String getDateCreatedString() {
		SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
		return sdf.format(getDateCreated());
	}

	public String getDisplayNameString() {
		return displayName.isEmpty() ? this.name : ChatUtil.color(
				this.displayName + "&c" + SettingAddon.getGangDisplayNameChar());
	}

	public String getAllyListString() {
		return ally.stream().map(Gang::getDisplayNameString).toString();
	}

	@Override
	public String toString() {
		return String.format(
				"Gang:{id=%d,name=%s,description=%s,members=%s,created=%s,balance=%.2f,level=%.2f,bounty=%,.2f,ally=%s}",
				getId(), name, description, getGroup(), created, balance, level.getExperience(), bounty.getAmount(), ally);
	}

}
