package me.luckyraven.data.placeholder.worker;

import me.luckyraven.Gangland;
import me.luckyraven.data.account.gang.Gang;
import me.luckyraven.data.account.gang.GangManager;
import me.luckyraven.data.account.gang.Member;
import me.luckyraven.data.account.gang.MemberManager;
import me.luckyraven.data.account.type.Bank;
import me.luckyraven.data.placeholder.PlaceholderHandler;
import me.luckyraven.data.placeholder.replacer.Replacer;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.feature.level.Level;
import me.luckyraven.feature.wanted.Wanted;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.util.color.ColorUtil;
import me.luckyraven.util.utilities.NumberUtil;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class GanglandPlaceholder extends PlaceholderHandler {

	private final UserManager<Player> userManager;
	private final MemberManager       memberManager;
	private final GangManager         gangManager;

	public GanglandPlaceholder(Gangland gangland, Replacer.Closure closure) {
		super(closure);

		this.userManager   = gangland.getInitializer().getUserManager();
		this.memberManager = gangland.getInitializer().getMemberManager();
		this.gangManager   = gangland.getInitializer().getGangManager();
	}

	@Override
	public @Nullable String onRequest(OfflinePlayer player, @NotNull String parameter) {
		if (player == null) return null;

		String param = parameter.toLowerCase();

		String value = null;

		if (param.contains("user_")) value = getUser(player, param);
		if (value != null) return value;

		if (param.contains("bank_")) value = getBank(player, param);
		if (value != null) return value;

		if (param.contains("gang_")) value = getGang(player, param);
		if (value != null) return value;

		return getSetting(param);
	}

	@Nullable
	private String getSetting(String parameter) {
		Object value = SettingAddon.getSettingsPlaceholder().entrySet()
				.stream().filter(entry -> {
					// place _ before capital letters
					String key   = entry.getKey().replaceAll("(?<=[a-z])(?=[A-Z])", "_");
					String lower = key.toLowerCase();

					return lower.equals(parameter);
				}).map(Map.Entry::getValue).findFirst().orElse(null);

		if (value == null) return null;

		if (value instanceof Double) return SettingAddon.formatDouble((double) value);

		return String.valueOf(value);
	}

	@Nullable
	private String getUser(OfflinePlayer player, String parameter) {
		// for member
		Member member  = memberManager.getMember(player.getUniqueId());
		String userStr = "user_";

		if (member == null) return null;

		if (parameter.equals(userStr + "has-gang")) return String.valueOf(member.hasGang());
		if (parameter.equals(userStr + "gang-id")) return !member.hasGang() ? "NA" : String.valueOf(member.getGangId());
		if (parameter.equals(userStr + "gang-join-date"))
			return !member.hasGang() ? "NA" : member.getGangJoinDateString();
		if (parameter.equals(userStr + "contribution"))
			return !member.hasGang() ? "NA" : SettingAddon.formatDouble(member.getContribution());
		if (parameter.equals(userStr + "contributed-amount")) return !member.hasGang() ?
																	 "NA" :
																	 NumberUtil.valueFormat(
																			 SettingAddon.getGangContributionRate() *
																			 member.getContribution());
		if (parameter.equals(userStr + "has-rank")) return String.valueOf(member.hasRank());
		if (parameter.equals(userStr + "rank")) return member.getRank() == null ? "NA" : member.getRank().getName();

		// for user
		Player onlinePlayer = player.getPlayer();
		if (!player.isOnline() || onlinePlayer == null) return null;

		User<Player> user = userManager.getUser(onlinePlayer);

		if (user == null) return null;

		// economy
		if (parameter.equals(userStr + "balance")) return NumberUtil.valueFormat(user.getEconomy().getBalance());

		// bounty
		if (parameter.equals(userStr + "bounty")) return NumberUtil.valueFormat(user.getBounty().getAmount());
		if (parameter.equals(userStr + "has-bounty")) return String.valueOf(user.getBounty().hasBounty());

		if (parameter.equals(userStr + "kd")) return SettingAddon.formatDouble(user.getKillDeathRatio());
		if (parameter.equals(userStr + "mob-kills")) return String.valueOf(user.getMobKills());
		if (parameter.equals(userStr + "kills")) return String.valueOf(user.getKills());
		if (parameter.equals(userStr + "deaths")) return String.valueOf(user.getDeaths());

		// wanted
		Wanted wanted = user.getWanted();
		if (parameter.equals(userStr + "wanted")) return wanted.getLevelStars();
		if (parameter.equals(userStr + "wanted-level")) return String.valueOf(wanted.getLevel());
		if (parameter.equals(userStr + "wanted-max-level")) return String.valueOf(wanted.getMaxLevel());
		if (parameter.equals(userStr + "is-wanted")) return String.valueOf(wanted.isWanted());

		// level
		return getLevelPlaceholder(parameter, userStr, user.getLevel());
	}

	@Nullable
	private String getBank(OfflinePlayer player, String parameter) {
		// for bank
		Player onlinePlayer = player.getPlayer();
		if (!player.isOnline() || onlinePlayer == null) return null;

		User<Player> user = userManager.getUser(onlinePlayer);

		if (user == null) return null;

		Bank   bank    = Bank.getInstance(user);
		String bankStr = "bank_";

		if (bank == null) return "NA";

		if (parameter.equals(bankStr + "name")) return bank.getName();
		if (parameter.equals(bankStr + "balance")) return NumberUtil.valueFormat(bank.getEconomy().getBalance());

		return null;
	}

	@Nullable
	private String getGang(OfflinePlayer player, String parameter) {
		// for gang
		Member member = memberManager.getMember(player.getUniqueId());

		if (member == null) return null;

		Gang   gang    = gangManager.getGang(member.getGangId());
		String gangStr = "gang_";

		if (gang == null) return "NA";

		// info
		if (parameter.equals(gangStr + "id")) return String.valueOf(gang.getId());
		if (parameter.equals(gangStr + "name")) return gang.getName();
		if (parameter.equals(gangStr + "display-name")) return gang.getDisplayNameString();
		if (parameter.equals(gangStr + "state")) return gang.getState().name().toLowerCase();
		if (parameter.equals(gangStr + "color")) return gang.getColor();
		if (parameter.equals(gangStr + "color-name")) return gang.getColor().toLowerCase().replace("_", " ");
		if (parameter.equals(gangStr + "color-code")) return ColorUtil.getColorCode(gang.getColor());
		if (parameter.equals(gangStr + "description")) return gang.getDescription();
		if (parameter.equals(gangStr + "created")) return gang.getDateCreatedString();

		// economy
		if (parameter.equals(gangStr + "balance")) return NumberUtil.valueFormat(gang.getEconomy().getBalance());

		// bounty
		if (parameter.equals(gangStr + "bounty")) return NumberUtil.valueFormat(gang.getBounty().getAmount());
		if (parameter.equals(gangStr + "has-bounty")) return String.valueOf(gang.getBounty().hasBounty());

		// members
		if (parameter.equals(gangStr + "members-size")) return String.valueOf(gang.getGroup().size());
		if (parameter.equals(gangStr + "online-members-size"))
			return String.valueOf(gang.getOnlineMembers(userManager).size());
		if (parameter.equals(gangStr + "offline-members-size"))
			return String.valueOf(gang.getGroup().size() - gang.getOnlineMembers(userManager).size());

		// ally
		if (parameter.equals(gangStr + "ally-list")) return gang.getAllyListString();
		if (parameter.equals(gangStr + "ally-size")) return String.valueOf(gang.getAllies().size());

		// level
		return getLevelPlaceholder(parameter, gangStr, gang.getLevel());
	}

	@Nullable
	private String getLevelPlaceholder(String parameter, String type, Level level) {
		if (parameter.equals(type + "level")) return String.valueOf(level.getLevelValue());
		if (parameter.equals(type + "level-max")) return String.valueOf(level.getMaxLevel());
		if (parameter.equals(type + "level-next")) return String.valueOf(level.nextLevel());
		if (parameter.equals(type + "level-previous")) return String.valueOf(level.previousLevel());
		if (parameter.equals(type + "experience")) return NumberUtil.valueFormat(level.getExperience());
		if (parameter.equals(type + "experience-percentage")) return NumberUtil.valueFormat(level.getPercentage());
		if (parameter.equals(type + "experience-next-level"))
			return NumberUtil.valueFormat(level.experienceCalculation(level.nextLevel()));
		if (parameter.equals(type + "experience-previous-level"))
			return NumberUtil.valueFormat(level.experienceCalculation(level.previousLevel()));
		if (parameter.equals(type + "experience-current-level"))
			return NumberUtil.valueFormat(level.experienceCalculation(level.getLevelValue()));
		if (parameter.startsWith(type + "experience-level-")) {
			String param = parameter.substring(parameter.lastIndexOf('-') + 1);
			int    value;
			try {
				value = Integer.parseInt(param);
			} catch (NumberFormatException exception) {
				return "NA";
			}
			return NumberUtil.valueFormat(level.experienceCalculation(value));
		}

		return null;
	}

}
