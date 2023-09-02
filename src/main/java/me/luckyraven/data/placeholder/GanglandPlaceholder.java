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
	public @Nullable String onRequest(OfflinePlayer player, @NotNull String parameter) {
		if (player == null) return null;

		String param = parameter.toLowerCase();

		if (param.contains("user_")) return getUser(player, param);
		else if (param.contains("bank_")) return getBank(player, param);
		else if (param.contains("gang_")) return getGang(player, param);
		else return getSetting(parameter);
	}

	@Nullable
	private String getSetting(String parameter) {
		Object value = SettingAddon.getSettingsPlaceholder().entrySet().stream().filter(
				entry -> entry.getKey().equals(parameter)).map(Map.Entry::getValue).findFirst().orElse(null);

		if (value == null) return null;

		if (value instanceof Double) return SettingAddon.formatDouble((double) value);
		return String.valueOf(value);
	}

	@Nullable
	private String getUser(OfflinePlayer player, String parameter) {
		// for member
		MemberManager memberManager = gangland.getInitializer().getMemberManager();
		Member        member        = memberManager.getMember(player.getUniqueId());
		String        userStr       = "user_";

		if (member == null) return null;

		if (parameter.equals(userStr + "gang-id")) return String.valueOf(member.getGangId());
		if (parameter.equals(userStr + "rank")) return member.getRank() == null ? "null" : member.getRank().getName();
		if (parameter.equals(userStr + "contribution")) return SettingAddon.formatDouble(member.getContribution());
		if (parameter.equals(userStr + "gang-join-date")) return member.getGangJoinDateString();

		// for user
		if (!player.isOnline()) return null;

		Player              onlinePlayer = Bukkit.getPlayer(player.getUniqueId());
		UserManager<Player> userManager  = gangland.getInitializer().getUserManager();
		User<Player>        user         = userManager.getUser(onlinePlayer);

		if (user == null) return null;

		if (parameter.equals(userStr + "balance")) return SettingAddon.formatDouble(user.getEconomy().getBalance());
		if (parameter.equals(userStr + "level")) return String.valueOf(user.getLevel().getLevelValue());
		if (parameter.equals(userStr + "experience")) return SettingAddon.formatDouble(user.getLevel().getExperience());
		if (parameter.equals(userStr + "bounty")) return SettingAddon.formatDouble(user.getBounty().getAmount());
		if (parameter.equals(userStr + "kd")) return SettingAddon.formatDouble(user.getKillDeathRatio());
		if (parameter.equals(userStr + "mob-kills")) return String.valueOf(user.getMobKills());
		if (parameter.equals(userStr + "kills")) return String.valueOf(user.getKills());
		if (parameter.equals(userStr + "deaths")) return String.valueOf(user.getDeaths());

		return null;
	}

	@Nullable
	private String getBank(OfflinePlayer player, String parameter) {
		// for bank
		if (!(player instanceof Player online)) return null;

		User<Player> user = gangland.getInitializer().getUserManager().getUser(online);

		if (user == null) return null;

		Bank   bank    = null;
		String bankStr = "bank_";

		for (Account<?, ?> account : user.getLinkedAccounts())
			if (account instanceof Bank found) {
				bank = found;
				break;
			}

		if (bank == null) return null;

		if (parameter.equals(bankStr + "name")) return bank.getName();
		if (parameter.equals(bankStr + "balance")) return SettingAddon.formatDouble(bank.getEconomy().getBalance());

		return null;
	}

	@Nullable
	private String getGang(OfflinePlayer player, String parameter) {
		// for gang
		if (!(player instanceof Player online)) return null;

		UserManager<Player> userManager = gangland.getInitializer().getUserManager();
		User<Player>        user        = userManager.getUser(online);

		if (user == null) return null;

		GangManager gangManager = gangland.getInitializer().getGangManager();
		Gang        gang        = gangManager.getGang(user.getGangId());
		String      gangStr     = "gang_";

		if (gang == null) return null;

		if (parameter.equals(gangStr + "name")) return gang.getName();
		if (parameter.equals(gangStr + "display-name")) return gang.getDisplayNameString();
		if (parameter.equals(gangStr + "color")) return gang.getColor();
		if (parameter.equals(gangStr + "description")) return gang.getDescription();
		if (parameter.equals(gangStr + "bounty")) return SettingAddon.formatDouble(gang.getBounty().getAmount());
		if (parameter.equals(gangStr + "balance")) return SettingAddon.formatDouble(gang.getEconomy().getBalance());
		if (parameter.equals(gangStr + "level")) return String.valueOf(gang.getLevel().getExperience());
		if (parameter.equals(gangStr + "experience")) return SettingAddon.formatDouble(gang.getLevel().getExperience());
		if (parameter.equals(gangStr + "created")) return gang.getDateCreatedString();
		if (parameter.equals(gangStr + "ally-list")) return gang.getAllyListString();

		return null;
	}

}
