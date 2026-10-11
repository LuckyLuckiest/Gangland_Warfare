package org.luckyraven.gangland.fights.fight;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.core.downed.DownedPlayerRegistry;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.fights.arena.ArenaPoint;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.permission.GangPermissions;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** {@link FightWorld} over the gang module's managers and the Bukkit server. */
public final class BukkitFightWorld implements FightWorld {

	private final GangManager         gangs;
	private final MemberManager       members;
	private final UserManager<Player> users;
	private final FightMessages       messages;

	public BukkitFightWorld(GangManager gangs, MemberManager members, UserManager<Player> users,
	                        FightMessages messages) {
		this.gangs    = gangs;
		this.members  = members;
		this.users    = users;
		this.messages = messages;
	}

	@Override
	public Optional<Integer> gangOf(UUID player) {
		Member member = members.getMember(player);
		return member == null || !member.hasGang() ? Optional.empty() : Optional.of(member.getGangId());
	}

	@Override
	public Optional<Integer> gangByName(String name) {
		return gangs.getAll().stream().filter(g -> g.getName() != null && g.getName().equalsIgnoreCase(name))
		            .map(Gang::getId).findFirst();
	}

	@Override
	public String gangName(int gangId) {
		Gang gang = gangs.findById(gangId);
		return gang == null || gang.getName() == null ? "?" : gang.getName();
	}

	@Override
	public List<String> gangNames() {
		return gangs.getAll().stream().map(Gang::getName).filter(java.util.Objects::nonNull).toList();
	}

	@Override
	public boolean allied(int gangA, int gangB) {
		Gang a = gangs.findById(gangA);
		Gang b = gangs.findById(gangB);
		return a != null && b != null && (a.isAlly(b) || b.isAlly(a));
	}

	@Override
	public boolean mayFight(UUID player) {
		return allowed(player, GangPermissions.FIGHT);
	}

	@Override
	public boolean mayWithdraw(UUID player) {
		return allowed(player, GangPermissions.WITHDRAW);
	}

	private boolean allowed(UUID player, String node) {
		return GangPermissions.allows(members.getMember(player), Bukkit.getPlayer(player), node);
	}

	@Override
	public double bank(int gangId) {
		Gang gang = gangs.findById(gangId);
		return gang == null ? 0D : gang.getEconomy().getAmount().doubleValue();
	}

	@Override
	public long gangAgeDays(int gangId) {
		Gang gang = gangs.findById(gangId);
		return gang == null ? 0L : (System.currentTimeMillis() - gang.getDateCreated().getTime()) / 86_400_000L;
	}

	@Override
	public long memberAgeHours(UUID player) {
		Member member = members.getMember(player);
		return member == null ? 0L : (System.currentTimeMillis() - member.getGangJoinDate().getTime()) / 3_600_000L;
	}

	@Override
	public boolean online(UUID player) {
		Player online = Bukkit.getPlayer(player);
		return online != null && online.isOnline();
	}

	@Override
	public boolean downed(UUID player) {
		return DownedPlayerRegistry.isDowned(player);
	}

	@Override
	public boolean wanted(UUID player) {
		User<Player> user = users.getUser(player);
		return user != null && user.getWanted().getLevel() > 0;
	}

	@Override
	public List<UUID> officers(int gangId) {
		return onlineMembers(gangId).stream().filter(this::mayFight).toList();
	}

	@Override
	public List<UUID> onlineMembers(int gangId) {
		Gang gang = gangs.findById(gangId);
		List<UUID> online = new ArrayList<>();
		if (gang == null) return online;

		for (Member member : gang.getMembers()) {
			if (online(member.getUuid())) online.add(member.getUuid());
		}
		return online;
	}

	@Override
	public boolean teleport(UUID player, ArenaPoint to) {
		Player target = Bukkit.getPlayer(player);
		if (target == null) return false;

		org.bukkit.World world = Bukkit.getWorld(to.world());
		if (world == null) return false;

		return target.teleport(new org.bukkit.Location(world, to.x(), to.y(), to.z(), to.yaw(), to.pitch()));
	}

	@Override
	public PlayerSnapshot snapshot(UUID player) {
		Player online = Bukkit.getPlayer(player);
		org.bukkit.Location at = online.getLocation();
		String world = at.getWorld() == null ? "" : at.getWorld().getName();
		return new PlayerSnapshot(player, new ArenaPoint(world, at.getX(), at.getY(), at.getZ(), at.getYaw(),
		                                                 at.getPitch()), online.getHealth(), online.getFoodLevel());
	}

	@Override
	public void restore(PlayerSnapshot snapshot) {
		Player online = Bukkit.getPlayer(snapshot.player());
		if (online != null) snapshot.restore(online);
	}

	@Override
	public void tell(UUID player, String key, Object... pairs) {
		Player online = Bukkit.getPlayer(player);
		if (online != null) online.sendMessage(messages.text(key, pairs));
	}
}
