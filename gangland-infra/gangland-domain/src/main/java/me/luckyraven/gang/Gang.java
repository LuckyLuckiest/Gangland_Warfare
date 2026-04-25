package me.luckyraven.gang;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.core.color.Color;
import me.luckyraven.core.utilities.ChatUtil;
import me.luckyraven.economy.EconomyHandler;
import me.luckyraven.gang.bounty.Bounty;
import me.luckyraven.gang.member.Member;
import me.luckyraven.gang.rank.Rank;
import me.luckyraven.gang.user.Level;
import me.luckyraven.gang.user.User;
import me.luckyraven.gang.vault.permission.VaultPermissionBridge;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;

@Getter
@Setter
public class Gang {

	private final int               id;
	private final Set<GangAlliance> allies;
	private final Level             level;
	private final Bounty            bounty;
	private final EconomyHandler    economy;
	private final List<Member>      members;

	private String name;
	@Nullable
	private String displayName;
	private String color, description;
	private long  created;
	/**
	 * Epoch ms of the most recent member login / heartbeat while the gang has at least one member online. Freezes the
	 * moment the last member logs off; consumed by the turf capture rules (post-logoff grace window and inactivity
	 * auto-release). 0 when the gang has never had an online member.
	 */
	private long  lastMemberOnlineAt;
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
		this.bounty  = new Bounty(GangSettings.getBountyEachKillValue(), GangSettings.getBountyTimerMultiple());
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
		Rank oldRank = member.getRank();

		member.setGangId(this.getId());
		member.setRank(rank);

		VaultPermissionBridge.onRankTransition(member.getUuid(), oldRank, rank);

		if (members.contains(member)) return;

		members.add(member);
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
		if (!members.contains(member)) return;

		user.flushPermissions(null);
		user.resetGang();
		removeMember(member);
	}

	public void removeMember(Member member) {
		if (!members.contains(member)) return;

		Rank oldRank = member.getRank();

		member.resetGang();
		member.setContribution(0D);
		member.setRank(null);

		VaultPermissionBridge.onRankTransition(member.getUuid(), oldRank, null);

		members.remove(member);
	}

	public boolean hasAnyMemberOnline() {
		for (Member member : members) {
			if (Bukkit.getOfflinePlayer(member.getUuid()).isOnline()) return true;
		}
		return false;
	}

	public boolean hasAnyMemberOnlineExcluding(UUID exclude) {
		for (Member member : members) {
			if (exclude != null && exclude.equals(member.getUuid())) continue;
			if (Bukkit.getOfflinePlayer(member.getUuid()).isOnline()) return true;
		}
		return false;
	}

	public List<User<Player>> getOnlineMembers(Function<Player, User<Player>> userLookup) {
		List<User<Player>> users = new ArrayList<>();

		for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
			User<Player> onUser = userLookup.apply(onlinePlayer);

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
		return displayName == null || displayName.isEmpty() ?
		       this.name :
		       ChatUtil.color(this.displayName + "&c" + GangSettings.getGangDisplayNameChar());
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
				"Gang{id=%d,name=%s,description=%s,members=%s,created=%s,balance=%s,level=%.2f,bounty=%s,allies=%s}",
				getId(), name, description, getMembers(), getDateCreatedString(), economy.getAmount().toPlainString(),
				level.getExperience(), bounty.getAmount().toPlainString(), getAllyListString());
	}

	public enum State {
		OPEN,
		INVITE,
		CLOSE
	}

}
