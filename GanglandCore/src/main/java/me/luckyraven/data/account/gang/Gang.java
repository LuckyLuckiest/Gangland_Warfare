package me.luckyraven.data.account.gang;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.data.account.Account;
import me.luckyraven.data.economy.EconomyHandler;
import me.luckyraven.data.rank.Rank;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.feature.bounty.Bounty;
import me.luckyraven.feature.level.Level;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.Pair;
import me.luckyraven.util.color.Color;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;

@Getter
@Setter
public class Gang extends Account<Integer, List<Member>> {

	private final Set<Pair<Gang, Long>> allies;
	private final Level                 level;
	private final Bounty                bounty;
	private final EconomyHandler        economy;

	private String name, displayName, color, description;
	private long  created;
	private State state;

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

		generateId();

		this.name        = null;
		this.displayName = "";
		this.color       = Color.LIGHT_BLUE.name();
		this.description = "Conquering the hood";
		this.created     = Instant.now().toEpochMilli();
		this.economy     = new EconomyHandler(null);
		this.bounty      = new Bounty();
		this.level       = new Level();
		this.allies      = new HashSet<>();
	}

	public void generateId() {
		Random random = new Random();
		setKey(random.nextInt(Integer.MAX_VALUE));
	}

	public void addAllAllies(List<Pair<Gang, Long>> allieDateList) {
		allieDateList.forEach(this::addAllie);
	}

	public void addAllie(Pair<Gang, Long> allieDate) {
		allies.add(allieDate);
	}

	public void addAllie(Gang gang) {
		allies.add(new Pair<>(gang, Instant.now().toEpochMilli()));
	}

	public void removeAllie(Gang gang) {
		allies.removeIf(pair -> pair.first().getId() == gang.getId());
	}

	public boolean isAllie(Gang gang) {
		return !allies.stream().filter(pair -> pair.first().getId() == gang.getId()).toList().isEmpty();
	}

	public Set<Pair<Gang, Long>> getAllies() {
		return Collections.unmodifiableSet(allies);
	}

	public void addMember(User<? extends OfflinePlayer> user, Member member, Rank rank) {
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

	public void addMember(Member member) {
		addMember(member, member.getRank());
	}

	public List<Member> getGroup() {
		return super.getValue();
	}

	public void setGroup(List<Member> users) {
		setValue(users);
	}

	public void removeMember(User<? extends OfflinePlayer> user, Member member) {
		if (!getGroup().contains(member)) return;

		user.flushPermissions(null);
		user.resetGang();
		user.removeAccount(this);
		removeMember(member);
	}

	public void removeMember(Member member) {
		if (!getGroup().contains(member)) return;

		member.resetGang();
		member.setContribution(0D);
		member.setRank(null);
		getGroup().remove(member);
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
			   ChatUtil.color(this.displayName + "&c" + SettingAddon.getGangDisplayNameChar());
	}

	public String getAllyListString() {
		return allies.stream().map(Pair::first).map(Gang::getDisplayNameString).toString();
	}

	@Override
	public String toString() {
		return String.format(
				"Gang{id=%d,name=%s,description=%s,members=%s,created=%s,balance=%.2f,level=%.2f,bounty=%,.2f,ally=%s}",
				getId(), name, description, getGroup(), created, economy.getBalance(), level.getExperience(),
				bounty.getAmount(), getAllyListString());
	}

	public enum State {
		OPEN,
		INVITE,
		CLOSE
	}

}
