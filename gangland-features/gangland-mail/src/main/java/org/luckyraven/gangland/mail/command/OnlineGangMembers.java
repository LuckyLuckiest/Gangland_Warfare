package org.luckyraven.gangland.mail.command;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;

import java.util.ArrayList;
import java.util.List;

/**
 * The online players who belong to a gang. A player with no cached member record is skipped instead of throwing
 * (GR-14: the old inline filter NPE'd on a null {@code getMember}).
 */
public final class OnlineGangMembers {

	private OnlineGangMembers() {
	}

	public static List<Player> of(MemberManager members, int gangId) {
		List<Player> online = new ArrayList<>();
		for (Player player : Bukkit.getOnlinePlayers()) {
			Member member = members.getMember(player.getUniqueId());
			if (member != null && member.getGangId() == gangId) online.add(player);
		}
		return online;
	}

}
