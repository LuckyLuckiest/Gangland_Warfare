package me.luckyraven.dependency;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.luckyraven.Gangland;
import me.luckyraven.account.gang.Member;
import me.luckyraven.account.gang.MemberManager;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.file.configuration.SettingAddon;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GanglandExpansion extends PlaceholderExpansion {

	private final Gangland gangland;

	public GanglandExpansion(Gangland gangland) {
		this.gangland = gangland;
	}

	@Override
	public @NotNull String getIdentifier() {
		return "gangland";
	}

	@Override
	public @NotNull String getAuthor() {
		return gangland.getDescription().getAuthors().get(0);
	}

	@Override
	public @NotNull String getVersion() {
		return gangland.getDescription().getVersion();
	}

	@Override
	public boolean persist() {
		return true;
	}

	@Override
	public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
		UserManager<Player> userManager   = gangland.getInitializer().getUserManager();
		MemberManager       memberManager = gangland.getInitializer().getMemberManager();

		// for member
		Member member = memberManager.getMember(player.getUniqueId());

		if (member != null) {
			if (params.equalsIgnoreCase("gang_id")) return String.valueOf(member.getGangId());
			if (params.equalsIgnoreCase("rank")) return String.valueOf(member.getRank().getName());
			if (params.equalsIgnoreCase("contribution")) return SettingAddon.formatDouble(member.getContribution());
		}

		// for user
		if (!player.isOnline()) return null;

		User<Player> user = userManager.getUser((Player) player);

		if (user != null) {
			if (params.equalsIgnoreCase("balance")) return SettingAddon.formatDouble(user.getBalance());
			if (params.equalsIgnoreCase("bounty")) return SettingAddon.formatDouble(user.getBounty().getAmount());
			if (params.equalsIgnoreCase("mob_kills")) return String.valueOf(user.getMobKills());
			if (params.equalsIgnoreCase("kd")) return String.valueOf(user.getKillDeathRatio());
			if (params.equalsIgnoreCase("kills")) return String.valueOf(user.getKills());
			if (params.equalsIgnoreCase("deaths")) return String.valueOf(user.getDeaths());
		}
		return null;
	}

}
