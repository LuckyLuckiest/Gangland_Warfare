package me.luckyraven.file.configuration.inventory.itemsource;

import me.luckyraven.config.GangFilterRegistration;
import me.luckyraven.core.color.ColorUtil;
import me.luckyraven.data.account.gang.Gang;
import me.luckyraven.data.account.gang.GangAlliance;
import me.luckyraven.data.account.gang.GangFilterAdapter;
import me.luckyraven.data.account.gang.GangManager;
import me.luckyraven.data.account.gang.member.Member;
import me.luckyraven.data.account.gang.member.MemberFilterAdapter;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.data.rank.Rank;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.inventory.filter.FilterApplier;
import me.luckyraven.inventory.filter.FilterStore;
import me.luckyraven.inventory.filter.SearchFilter;
import me.luckyraven.inventory.multi.ItemSourceEntry;
import me.luckyraven.inventory.multi.ItemSourceProvider;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.*;

public class GangItemSourceProvider implements ItemSourceProvider {

	private final UserManager<Player> userManager;
	private final GangManager         gangManager;
	private final FilterStore         filterStore;
	private final FilterApplier       filterApplier;
	private final GangFilterAdapter   gangFilterAdapter;
	private final MemberFilterAdapter memberFilterAdapter;

	public GangItemSourceProvider(UserManager<Player> userManager, GangManager gangManager,
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
	public List<ItemSourceEntry> getEntries(Player player, String source) {
		return switch (source.toLowerCase()) {
			case "gang_members" -> getGangMembers(player);
			case "gang_allies" -> getGangAllies(player);
			case "gangs" -> getGangs(player);
			default -> new ArrayList<>();
		};
	}

	private List<ItemSourceEntry> getGangMembers(Player player) {
		User<Player> user = userManager.getUser(player);
		if (user == null || !user.hasGang()) return new ArrayList<>();

		Gang gang = gangManager.getGang(user.getGangId());
		if (gang == null) return new ArrayList<>();

		SearchFilter filter  = filterStore.get(GangFilterRegistration.BINDING_GANG_MEMBERS, player);
		List<Member> members = filterApplier.apply(gang.getMembers(), filter, memberFilterAdapter);

		List<ItemSourceEntry> entries = new ArrayList<>(members.size());
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

			entries.add(new ItemSourceEntry(placeholders));
		}
		return entries;
	}

	private List<ItemSourceEntry> getGangAllies(Player player) {
		User<Player> user = userManager.getUser(player);
		if (user == null || !user.hasGang()) return new ArrayList<>();

		Gang gang = gangManager.getGang(user.getGangId());
		if (gang == null) return new ArrayList<>();

		List<ItemSourceEntry> entries = new ArrayList<>();
		for (Gang ally : gang.getAllies()
				.stream().map(GangAlliance::ally).toList()) {
			int online = ally.getOnlineMembers(userManager).size();
			int total  = ally.getMembers().size();

			Map<String, String> placeholders = new LinkedHashMap<>();
			placeholders.put("ally_id", String.valueOf(ally.getId()));
			placeholders.put("ally_name", ally.getDisplayNameString());
			placeholders.put("ally_online", String.valueOf(online));
			placeholders.put("ally_total", String.valueOf(total));
			placeholders.put("ally_created", ally.getDateCreatedString());

			entries.add(new ItemSourceEntry(placeholders));
		}
		return entries;
	}

	private List<ItemSourceEntry> getGangs(Player player) {
		SearchFilter filter = filterStore.get(GangFilterRegistration.BINDING_GANGS, player);
		List<Gang>   gangs  = filterApplier.apply(gangManager.getGangs().values(), filter, gangFilterAdapter);

		String                tail    = Settings.getGangRankTail();
		List<ItemSourceEntry> entries = new ArrayList<>(gangs.size());

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
			                 String.valueOf(gang.getOnlineMembers(userManager).size()));
			placeholders.put("gang_created", gang.getDateCreatedString());

			entries.add(new ItemSourceEntry(placeholders));
		}
		return entries;
	}

}
