package org.luckyraven.gangland.gang.menu;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.data.gang.GangItemSourceContribution;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangAlliance;
import org.luckyraven.gangland.gang.GangFilterAdapter;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberFilterAdapter;
import org.luckyraven.gangland.gang.rank.Rank;
import org.luckyraven.gangland.menu.filter.FilterApplier;
import org.luckyraven.gangland.menu.filter.FilterStore;
import org.luckyraven.gangland.menu.filter.SearchFilter;
import org.luckyraven.keystone.color.ColorUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Feeds the {@code gangs}/{@code gang_members}/{@code gang_allies} YAML menu item sources (W54 F1) — the direct
 * port of the deleted {@code GangItemSourceProvider}'s body, rows returned as plain
 * {@code Map<String, String>} placeholders instead of the impl-only {@code ItemSourceEntry} record (see
 * {@link GangItemSourceContribution}'s javadoc). Binding ids ({@code "gangs"}, {@code "gang_members"}) are the
 * same literals {@code GangFilterRegistration} registers in gangland-impl; the two must stay in sync (there is
 * no shared constant, deliberately — see that class's javadoc).
 */
public final class GangMenuItemSourceContribution implements GangItemSourceContribution {

	private static final String SOURCE_GANG_MEMBERS = "gang_members";
	private static final String SOURCE_GANG_ALLIES  = "gang_allies";
	private static final String SOURCE_GANGS        = "gangs";
	private static final String BINDING_GANGS        = "gangs";
	private static final String BINDING_GANG_MEMBERS = "gang_members";

	private static final Set<String> SOURCES = Set.of(SOURCE_GANG_MEMBERS, SOURCE_GANG_ALLIES, SOURCE_GANGS);

	private final UserManager<Player> userManager;
	private final GangManager         gangManager;
	private final FilterStore         filterStore;
	private final FilterApplier       filterApplier;
	private final GangFilterAdapter   gangFilterAdapter;
	private final MemberFilterAdapter memberFilterAdapter;

	public GangMenuItemSourceContribution(UserManager<Player> userManager, GangManager gangManager,
	                                      FilterStore filterStore, FilterApplier filterApplier,
	                                      GangFilterAdapter gangFilterAdapter, MemberFilterAdapter memberFilterAdapter) {
		this.userManager         = userManager;
		this.gangManager         = gangManager;
		this.filterStore         = filterStore;
		this.filterApplier       = filterApplier;
		this.gangFilterAdapter   = gangFilterAdapter;
		this.memberFilterAdapter = memberFilterAdapter;
	}

	@Override
	public boolean supports(String source) {
		return source != null && SOURCES.contains(source.toLowerCase());
	}

	@Override
	public List<Map<String, String>> entries(Player player, String source) {
		return switch (source.toLowerCase()) {
			case SOURCE_GANG_MEMBERS -> getGangMembers(player);
			case SOURCE_GANG_ALLIES -> getGangAllies(player);
			case SOURCE_GANGS -> getGangs(player);
			default -> new ArrayList<>();
		};
	}

	private List<Map<String, String>> getGangMembers(Player player) {
		User<Player> user = userManager.getUser(player);
		if (user == null || !user.hasGang()) return new ArrayList<>();

		Gang gang = gangManager.getGang(user.getGangId());
		if (gang == null) return new ArrayList<>();

		SearchFilter filter  = filterStore.get(BINDING_GANG_MEMBERS, player);
		List<Member> members = filterApplier.apply(gang.getMembers(), filter, memberFilterAdapter);

		List<Map<String, String>> entries = new ArrayList<>(members.size());
		for (Member member : members) {
			OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(member.getUuid());
			Rank          userRank      = member.getRank();
			String        rank          = userRank != null ? userRank.getName() : "null";
			String        onlineStatus  = offlinePlayer.isOnline() ? "&aOnline" : "&cOffline";
			String        name          = offlinePlayer.getName() != null ? offlinePlayer.getName() : "";

			Map<String, String> placeholders = new LinkedHashMap<>();
			placeholders.put("member_name", name);
			placeholders.put("member_rank", rank);
			placeholders.put("member_contribution", String.valueOf(member.getContribution()));
			placeholders.put("member_join_date", member.getGangJoinDateString());
			placeholders.put("member_online_status", onlineStatus);

			entries.add(placeholders);
		}
		return entries;
	}

	private List<Map<String, String>> getGangAllies(Player player) {
		User<Player> user = userManager.getUser(player);
		if (user == null || !user.hasGang()) return new ArrayList<>();

		Gang gang = gangManager.getGang(user.getGangId());
		if (gang == null) return new ArrayList<>();

		List<Map<String, String>> entries = new ArrayList<>();
		for (Gang ally : gang.getAllies()
				.stream().map(GangAlliance::ally).toList()) {
			int online = ally.getOnlineMembers(userManager::getUser).size();
			int total  = ally.getMembers().size();

			Map<String, String> placeholders = new LinkedHashMap<>();
			placeholders.put("ally_id", String.valueOf(ally.getId()));
			placeholders.put("ally_name", ally.getDisplayNameString());
			placeholders.put("ally_online", String.valueOf(online));
			placeholders.put("ally_total", String.valueOf(total));
			placeholders.put("ally_created", ally.getDateCreatedString());

			entries.add(placeholders);
		}
		return entries;
	}

	private List<Map<String, String>> getGangs(Player player) {
		SearchFilter filter = filterStore.get(BINDING_GANGS, player);
		List<Gang>   gangs  = filterApplier.apply(gangManager.getGangs().values(), filter, gangFilterAdapter);

		String                     tail    = Settings.getGangRankTail();
		List<Map<String, String>>  entries = new ArrayList<>(gangs.size());

		for (Gang gang : gangs) {
			UUID leaderUuid = gang.getMembers()
					.stream()
					.filter(m -> m.getRank() != null && m.getRank().getName().equalsIgnoreCase(tail))
					.findFirst().map(Member::getUuid).orElse(null);

			String leaderName = "";
			if (leaderUuid != null) {
				String fromOffline = Bukkit.getOfflinePlayer(leaderUuid).getName();
				if (fromOffline != null) leaderName = fromOffline;
			}

			String description = gang.getDescription() == null ? "" : gang.getDescription();

			Map<String, String> placeholders = new LinkedHashMap<>();
			placeholders.put("gang_id", String.valueOf(gang.getId()));
			placeholders.put("gang_display-name", gang.getDisplayNameString());
			placeholders.put("gang_color-code", ColorUtil.getColorCode(gang.getColor()));
			placeholders.put("gang_description", description);
			placeholders.put("gang_leader_name", leaderName);
			placeholders.put("gang_members-size", String.valueOf(gang.getMembers().size()));
			placeholders.put("gang_online-members-size",
			                 String.valueOf(gang.getOnlineMembers(userManager::getUser).size()));
			placeholders.put("gang_created", gang.getDateCreatedString());

			entries.add(placeholders);
		}
		return entries;
	}

}
