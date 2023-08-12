package me.luckyraven.dependency;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.luckyraven.Gangland;
import me.luckyraven.account.gang.Gang;
import me.luckyraven.account.gang.GangManager;
import me.luckyraven.account.gang.Member;
import me.luckyraven.account.gang.MemberManager;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.file.configuration.SettingAddon;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

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

	@Nullable
	@Override
	public String onPlaceholderRequest(Player player, @NotNull String params) {
		Object value = SettingAddon.getSettingsPlaceholder().entrySet().stream().filter(
				entry -> entry.getKey().equalsIgnoreCase(params)).map(Map.Entry::getValue).findFirst().orElse(null);

		if (value != null) {
			if (value instanceof Double) return SettingAddon.formatDouble((double) value);
			return String.valueOf(value);
		}

		// for member
		MemberManager memberManager = gangland.getInitializer().getMemberManager();
		Member        member        = memberManager.getMember(player.getUniqueId());
		String        userStr       = "user_";

		if (member != null) {
			if (params.equalsIgnoreCase(userStr + "gang-id")) return String.valueOf(member.getGangId());
			if (params.equalsIgnoreCase(userStr + "rank"))
				return member.getRank() == null ? "null" : member.getRank().getName();
			if (params.equalsIgnoreCase(userStr + "contribution")) return SettingAddon.formatDouble(
					member.getContribution());
			if (params.equalsIgnoreCase(userStr + "gang-join-date")) return member.getGangJoinDateString();
		}

		// for user
		if (!player.isOnline()) return null;

		UserManager<Player> userManager = gangland.getInitializer().getUserManager();
		User<Player>        user        = userManager.getUser(player);

		if (user != null) {
			if (params.equalsIgnoreCase(userStr + "balance")) return SettingAddon.formatDouble(user.getBalance());
			if (params.equalsIgnoreCase(userStr + "level")) return SettingAddon.formatDouble(
					user.getLevel().getAmount());
			if (params.equalsIgnoreCase(userStr + "bounty")) return SettingAddon.formatDouble(
					user.getBounty().getAmount());
			if (params.equalsIgnoreCase(userStr + "kd")) return String.valueOf(user.getKillDeathRatio());
			if (params.equalsIgnoreCase(userStr + "mob-kills")) return String.valueOf(user.getMobKills());
			if (params.equalsIgnoreCase(userStr + "kills")) return String.valueOf(user.getKills());
			if (params.equalsIgnoreCase(userStr + "deaths")) return String.valueOf(user.getDeaths());

			// for gang
			GangManager gangManager = gangland.getInitializer().getGangManager();
			Gang        gang        = gangManager.getGang(user.getGangId());
			String      gangStr     = "gang_";

			if (params.equalsIgnoreCase(gangStr + "name")) return gang.getName();
			if (params.equalsIgnoreCase(gangStr + "display-name")) return gang.getDisplayNameString();
			if (params.equalsIgnoreCase(gangStr + "color")) return gang.getColor();
			if (params.equalsIgnoreCase(gangStr + "description")) return gang.getDescription();
			if (params.equalsIgnoreCase(gangStr + "bounty")) return SettingAddon.formatDouble(
					gang.getBounty().getAmount());
			if (params.equalsIgnoreCase(gangStr + "balance")) return SettingAddon.formatDouble(gang.getBalance());
			if (params.equalsIgnoreCase(gangStr + "level")) return SettingAddon.formatDouble(
					gang.getLevel().getAmount());
			if (params.equalsIgnoreCase(gangStr + "created")) return gang.getDateCreatedString();
			if (params.equalsIgnoreCase(gangStr + "ally-list")) return gang.getAllyListString();
		}

		return null;
	}

}
