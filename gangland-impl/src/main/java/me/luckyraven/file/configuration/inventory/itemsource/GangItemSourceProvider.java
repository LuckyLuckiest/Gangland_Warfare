package me.luckyraven.file.configuration.inventory.itemsource;

import me.luckyraven.data.account.gang.Gang;
import me.luckyraven.data.account.gang.GangAlliance;
import me.luckyraven.data.account.gang.GangManager;
import me.luckyraven.data.account.gang.member.Member;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.data.rank.Rank;
import me.luckyraven.inventory.multi.ItemSourceEntry;
import me.luckyraven.inventory.multi.ItemSourceProvider;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GangItemSourceProvider implements ItemSourceProvider {

	private final UserManager<Player> userManager;
	private final GangManager         gangManager;

	public GangItemSourceProvider(UserManager<Player> userManager, GangManager gangManager) {
		this.userManager = userManager;
		this.gangManager = gangManager;
	}

	@Override
	public List<ItemSourceEntry> getEntries(Player player, String source) {
		return switch (source.toLowerCase()) {
			case "gang_members" -> getGangMembers(player);
			case "gang_allies" -> getGangAllies(player);
			default -> new ArrayList<>();
		};
	}

	private List<ItemSourceEntry> getGangMembers(Player player) {
		User<Player> user = userManager.getUser(player);

		if (user == null || !user.hasGang()) return new ArrayList<>();

		Gang gang = gangManager.getGang(user.getGangId());
		if (gang == null) return new ArrayList<>();

		List<ItemSourceEntry> entries = new ArrayList<>();

		for (Member member : gang.getMembers()) {
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
}
