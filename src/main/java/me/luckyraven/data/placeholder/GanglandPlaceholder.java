package me.luckyraven.data.placeholder;

import me.luckyraven.Gangland;
import me.luckyraven.data.account.Account;
import me.luckyraven.data.account.gang.Gang;
import me.luckyraven.data.account.gang.GangManager;
import me.luckyraven.data.account.gang.Member;
import me.luckyraven.data.account.gang.MemberManager;
import me.luckyraven.data.account.type.Bank;
import me.luckyraven.data.placeholder.replacer.Replacer;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.file.configuration.SettingAddon;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class GanglandPlaceholder extends PlaceholderHandler {

	private final Gangland gangland;

	public GanglandPlaceholder(Gangland gangland, Replacer.Closure closure) {
		super(closure);
		this.gangland = gangland;
	}

	@Override
	public @Nullable String onRequest(OfflinePlayer player, @NotNull String parameters) {
		Object value = SettingAddon.getSettingsPlaceholder().entrySet().stream().filter(
				entry -> entry.getKey().equalsIgnoreCase(parameters)).map(Map.Entry::getValue).findFirst().orElse(null);

		if (value != null) {
			if (value instanceof Double) return SettingAddon.formatDouble((double) value);
			return String.valueOf(value);
		}

		// player related data
		if (parameters.equalsIgnoreCase("player")) return player.getName();

		// for member
		MemberManager memberManager = gangland.getInitializer().getMemberManager();
		Member        member        = memberManager.getMember(player.getUniqueId());
		String        userStr       = "user_";

		if (member != null) {
			if (parameters.equalsIgnoreCase(userStr + "gang-id")) return String.valueOf(member.getGangId());
			if (parameters.equalsIgnoreCase(userStr + "rank"))
				return member.getRank() == null ? "null" : member.getRank().getName();
			if (parameters.equalsIgnoreCase(userStr + "contribution")) return SettingAddon.formatDouble(
					member.getContribution());
			if (parameters.equalsIgnoreCase(userStr + "gang-join-date")) return member.getGangJoinDateString();
		}

		// for user
		if (!player.isOnline()) return null;

		Player              onlinePlayer = Bukkit.getPlayer(player.getUniqueId());
		UserManager<Player> userManager  = gangland.getInitializer().getUserManager();
		User<Player>        user         = userManager.getUser(onlinePlayer);

		if (user != null) {
			if (parameters.equalsIgnoreCase(userStr + "balance")) return SettingAddon.formatDouble(
					user.getEconomy().getBalance());
			if (parameters.equalsIgnoreCase(userStr + "level")) return String.valueOf(user.getLevel().getLevelValue());
			if (parameters.equalsIgnoreCase(userStr + "experience")) return SettingAddon.formatDouble(
					user.getLevel().getExperience());
			if (parameters.equalsIgnoreCase(userStr + "bounty")) return SettingAddon.formatDouble(
					user.getBounty().getAmount());
			if (parameters.equalsIgnoreCase(userStr + "kd")) return String.valueOf(user.getKillDeathRatio());
			if (parameters.equalsIgnoreCase(userStr + "mob-kills")) return String.valueOf(user.getMobKills());
			if (parameters.equalsIgnoreCase(userStr + "kills")) return String.valueOf(user.getKills());
			if (parameters.equalsIgnoreCase(userStr + "deaths")) return String.valueOf(user.getDeaths());

			// for bank
			Bank   bank    = null;
			String bankStr = "bank_";

			for (Account<?, ?> account : user.getLinkedAccounts())
				if (account instanceof Bank found) {
					bank = found;
					break;
				}

			if (bank != null) {
				if (parameters.equalsIgnoreCase(bankStr + "name")) return bank.getName();
				if (parameters.equalsIgnoreCase(bankStr + "balance")) return SettingAddon.formatDouble(
						bank.getEconomy().getBalance());
			}

			// for gang
			GangManager gangManager = gangland.getInitializer().getGangManager();
			Gang        gang        = gangManager.getGang(user.getGangId());
			String      gangStr     = "gang_";

			if (parameters.equalsIgnoreCase(gangStr + "name")) return gang.getName();
			if (parameters.equalsIgnoreCase(gangStr + "display-name")) return gang.getDisplayNameString();
			if (parameters.equalsIgnoreCase(gangStr + "color")) return gang.getColor();
			if (parameters.equalsIgnoreCase(gangStr + "description")) return gang.getDescription();
			if (parameters.equalsIgnoreCase(gangStr + "bounty")) return SettingAddon.formatDouble(
					gang.getBounty().getAmount());
			if (parameters.equalsIgnoreCase(gangStr + "balance")) return SettingAddon.formatDouble(
					gang.getEconomy().getBalance());
			if (parameters.equalsIgnoreCase(gangStr + "level")) return String.valueOf(gang.getLevel().getExperience());
			if (parameters.equalsIgnoreCase(gangStr + "experience")) return SettingAddon.formatDouble(
					gang.getLevel().getExperience());
			if (parameters.equalsIgnoreCase(gangStr + "created")) return gang.getDateCreatedString();
			if (parameters.equalsIgnoreCase(gangStr + "ally-list")) return gang.getAllyListString();
		}

		return null;
	}

}
