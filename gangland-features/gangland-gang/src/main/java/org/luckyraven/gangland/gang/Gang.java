package org.luckyraven.gangland.gang;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.color.Color;
import org.luckyraven.keystone.util.ChatUtil;
import org.luckyraven.keystone.economy.EconomyHandler;
import org.luckyraven.gangland.core.bounty.Bounty;
import org.luckyraven.gangland.core.user.IdentitySettings;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.permission.RankPermissionApplier;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.core.user.Level;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.gang.vault.permission.VaultPermissionBridge;

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

	public Gang(int id) {
		this.id      = id;
		this.allies  = new HashSet<>();
		this.level   = GangLevels.newLevel();
		this.bounty  = new Bounty(IdentitySettings.getBountyEachKillValue(), IdentitySettings.getBountyTimerMultiple());
		this.economy = new EconomyHandler(null);
		this.members = new ArrayList<>();

		this.name        = null;
		this.displayName = "";
		this.color       = Color.LIGHT_BLUE.name();
		this.description = "Conquering the hood";
		this.created     = Instant.now().toEpochMilli();
		this.state       = State.INVITE;
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

	/**
	 * Alliances are mutual (TF-24): true when either gang lists the other, so a one-sided entry counts for both.
	 */
	public boolean isAlly(Gang gang) {
		return gang != null && (listsAlly(gang.getId()) || gang.listsAlly(this.id));
	}

	private boolean listsAlly(int gangId) {
		return allies.stream().anyMatch(alliance -> alliance.ally().getId() == gangId);
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

		// A removed member has no rank left, so flush(user, null) only clears.
		RankPermissionApplier.flush(user, null);

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
	public int hashCode() {
		return Integer.hashCode(id);
	}

	@Override
	public String toString() {
		return String.format(
				"Gang{id=%d,name=%s,description=%s,members=%s,created=%s,balance=%s,level=%.2f,bounty=%s,allies=%s}",
				getId(), name, description, getMembers(), getDateCreatedString(), economy.getAmount().toPlainString(),
				level.getExperience(), bounty.getAmount().toPlainString(), getAllyListString());
	}

	/**
	 * OPEN = anyone may /glw gang join; INVITE = invited players only (the default since 0.17.0, D7); CLOSE = read as
	 * INVITE (kept so old rows load).
	 */
	public enum State {
		OPEN,
		INVITE,
		CLOSE
	}

}
