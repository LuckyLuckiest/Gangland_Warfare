package me.luckyraven.data.account.gang;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.copsncrooks.bounty.Bounty;
import me.luckyraven.data.account.Level;
import me.luckyraven.data.account.gang.member.Member;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.data.rank.Rank;
import me.luckyraven.economy.bank.EconomyHandler;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.util.GanglandChatUtil;
import me.luckyraven.util.color.Color;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;

@Getter
@Setter
public class Gang {

	private final int               id;
	private final Set<GangAlliance> allies;
	private final Level             level;
	private final Bounty            bounty;
	private final EconomyHandler    economy;
	private final List<Member>      members;

	private String name, displayName, color, description;
	private long  created;
	private State state;

	public Gang(int id, List<Member> members, String name) {
		this(id, members);
		this.name = name;
	}

	public Gang(int id, List<Member> members) {
		this(id);

		this.members.addAll(members);
	}

	public Gang() {
		this(generateId());
	}

	public Gang(int id) {
		this.id      = id;
		this.allies  = new HashSet<>();
		this.level   = new Level();
		this.bounty  = new Bounty(Settings.getBountyEachKillValue(), Settings.getBountyTimerMultiple());
		this.economy = new EconomyHandler(null);
		this.members = new ArrayList<>();

		this.name        = null;
		this.displayName = "";
		this.color       = Color.LIGHT_BLUE.name();
		this.description = "Conquering the hood";
		this.created     = Instant.now().toEpochMilli();
	}

	public static int generateId() {
		Random random = new Random();
		return random.nextInt(Integer.MAX_VALUE);
	}

	public void addAlly(GangAlliance gangAlliance) {
		allies.add(gangAlliance);
	}

	public void addAllAllies(List<GangAlliance> gangAlliances) {
		gangAlliances.forEach(this::addAlly);
	}

	public void addAlly(Gang gang) {
		addAlly(new GangAlliance(this, gang, Instant.now().toEpochMilli()));
	}

	public void removeAlly(Gang gang) {
		allies.removeIf(gangAlliance -> gangAlliance.ally().getId() == gang.getId());
	}

	public boolean isAlly(Gang gang) {
		return !allies.stream().filter(gangAlliance -> gangAlliance.ally().getId() == gang.getId()).toList().isEmpty();
	}

	public Set<GangAlliance> getAllies() {
		return Collections.unmodifiableSet(allies);
	}

	public void addMember(Member member, Rank rank) {
		member.setGangId(this.getId());
		member.setRank(rank);

		List<Member> group        = getMembers();
		boolean      memberExists = group.contains(member);

		if (memberExists) return;

		group.add(member);
	}

	public void addMember(User<? extends OfflinePlayer> user, Member member, Rank rank) {
		user.setGangId(this.getId());
		addMember(member, rank);
	}

	public void addMember(Member member) {
		addMember(member, member.getRank());
	}

	public List<Member> getMembers() {
		return new ArrayList<>(members);
	}

	public void removeMember(User<? extends OfflinePlayer> user, Member member) {
		if (!getMembers().contains(member)) return;

		user.flushPermissions(null);
		user.resetGang();
		removeMember(member);
	}

	public void removeMember(Member member) {
		if (!getMembers().contains(member)) return;

		member.resetGang();
		member.setContribution(0D);
		member.setRank(null);
		getMembers().remove(member);
	}

	public List<User<Player>> getOnlineMembers(UserManager<Player> userManager) {
		List<User<Player>> users = new ArrayList<>();

		for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
			User<Player> onUser = userManager.getUser(onlinePlayer);

			if (onUser == null) continue;

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
		return displayName.isEmpty() ?
		       this.name :
		       GanglandChatUtil.color(this.displayName + "&c" + Settings.getGangDisplayNameChar());
	}

	public String getAllyListString() {
		return allies.stream().map(GangAlliance::ally).map(Gang::getDisplayNameString).toList().toString();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Gang gang = (Gang) o;

		return getId() == gang.getId();
	}

	@Override
	public String toString() {
		return String.format(
				"Gang{id=%d,name=%s,description=%s,members=%s,created=%s,balance=%.2f,level=%.2f,bounty=%,.2f,allies=%s}",
				getId(), name, description, getMembers(), getDateCreatedString(), economy.getBalance(),
				level.getExperience(), bounty.getAmount(), getAllyListString());
	}

	public enum State {
		OPEN,
		INVITE,
		CLOSE
	}

}
