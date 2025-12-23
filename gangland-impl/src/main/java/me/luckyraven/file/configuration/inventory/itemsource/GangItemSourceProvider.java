package me.luckyraven.file.configuration.inventory.itemsource;

import com.cryptomorin.xseries.XMaterial;
import me.luckyraven.Gangland;
import me.luckyraven.data.account.gang.Gang;
import me.luckyraven.data.account.gang.GangManager;
import me.luckyraven.data.account.gang.Member;
import me.luckyraven.data.rank.Rank;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.inventory.multi.ItemSourceProvider;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.Pair;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class GangItemSourceProvider implements ItemSourceProvider {

	private final Gangland gangland;

	public GangItemSourceProvider(Gangland gangland) {
		this.gangland = gangland;
	}

	@Override
	public List<ItemStack> getItems(Player player, String source) {
		return switch (source.toLowerCase()) {
			case "gang_members" -> getGangMembers(player);
			case "gang_allies" -> getGangAllies(player);
			default -> new ArrayList<>();
		};
	}

	private List<ItemStack> getGangMembers(Player player) {
		UserManager<Player> userManager = gangland.getInitializer().getUserManager();
		GangManager         gangManager = gangland.getInitializer().getGangManager();

		User<Player> user = userManager.getUser(player);
		if (!user.hasGang()) return new ArrayList<>();

		Gang gang = gangManager.getGang(user.getGangId());
		if (gang == null) return new ArrayList<>();

		List<ItemStack> items = new ArrayList<>();

		for (Member member : gang.getGroup()) {
			OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(member.getUuid());
			Rank          userRank      = member.getRank();
			String        rank          = userRank != null ? userRank.getName() : "null";

			String onlineStatus = offlinePlayer.isOnline() ? "&aOnline" : "&cOffline";

			List<String> data = new ArrayList<>();
			data.add("&7Rank:&e " + rank);
			data.add("&7Contribution:&e " + member.getContribution());
			data.add("&7Joined:&e " + member.getGangJoinDateString());
			data.add("");
			data.add("&7Status: " + onlineStatus);

			ItemBuilder itemBuilder = new ItemBuilder(XMaterial.PLAYER_HEAD.get()).setDisplayName(
					"&b" + offlinePlayer.getName()).setLore(data);

			itemBuilder.modifyNBT(nbt -> nbt.setString("SkullOwner", offlinePlayer.getName()));

			items.add(itemBuilder.build());
		}

		return items;
	}

	private List<ItemStack> getGangAllies(Player player) {
		UserManager<Player> userManager = gangland.getInitializer().getUserManager();
		GangManager         gangManager = gangland.getInitializer().getGangManager();

		User<Player> user = userManager.getUser(player);
		if (!user.hasGang()) return new ArrayList<>();

		Gang gang = gangManager.getGang(user.getGangId());
		if (gang == null) return new ArrayList<>();

		List<ItemStack> items = new ArrayList<>();

		for (Gang ally : gang.getAllies()
							 .stream().map(Pair::first).toList()) {
			List<String> data = new ArrayList<>();
			data.add("&7ID:&e " + ally.getId());
			data.add(String.format("&7Members:&a %d&7/&e%d", ally.getOnlineMembers(userManager).size(),
								   ally.getGroup().size()));
			data.add("&7Created:&e " + ally.getDateCreatedString());
			data.add("");
			data.add("&eClick to view details");

			ItemBuilder itemBuilder = new ItemBuilder(XMaterial.REDSTONE.get()).setDisplayName(
					"&b" + ally.getDisplayNameString()).setLore(data);

			items.add(itemBuilder.build());
		}

		return items;
	}
}
