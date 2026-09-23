package org.luckyraven.gangland.gang.placeholder;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.core.user.Level;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.data.placeholder.extension.PlaceholderContribution;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.keystone.color.ColorUtil;
import org.luckyraven.keystone.util.NumberUtil;

import org.luckyraven.keystone.bean.Qualifier;
/**
 * Answers the {@code gang_*} placeholders and the member-touching {@code user_*} family (has-gang, gang-id,
 * gang-join-date, contribution, contributed-amount, has-rank, rank) that used to live inline in
 * {@code GanglandPlaceholder} before the gang module split (WS5 G2 step 12, S4) — impl can no longer name
 * {@link GangManager}/{@link MemberManager}, so this bean answers them instead, reached through the api-hosted
 * {@code PlaceholderContribution} seam.
 */
public final class GangPlaceholderContribution implements PlaceholderContribution {

	private static final String USER_PREFIX = "user_";
	private static final String GANG_PREFIX = "gang_";

	private final UserManager<Player> userManager;
	private final MemberManager       memberManager;
	private final GangManager         gangManager;

	public GangPlaceholderContribution(@Qualifier("online") UserManager<Player> userManager, MemberManager memberManager,
	                                   GangManager gangManager) {
		this.userManager   = userManager;
		this.memberManager = memberManager;
		this.gangManager   = gangManager;
	}

	@Override
	@Nullable
	public String resolve(OfflinePlayer player, String parameter) {
		if (parameter.startsWith(USER_PREFIX)) return resolveMember(player, parameter);
		if (parameter.startsWith(GANG_PREFIX)) return resolveGang(player, parameter);
		return null;
	}

	@Nullable
	private String resolveMember(OfflinePlayer player, String parameter) {
		Member member = memberManager.getMember(player.getUniqueId());
		if (member == null) return null;

		if (parameter.equals(USER_PREFIX + "has-gang")) return String.valueOf(member.hasGang());
		if (parameter.equals(USER_PREFIX + "gang-id")) {
			return !member.hasGang() ? null : String.valueOf(member.getGangId());
		}
		if (parameter.equals(USER_PREFIX + "gang-join-date")) {
			return !member.hasGang() ? null : member.getGangJoinDateString();
		}
		if (parameter.equals(USER_PREFIX + "contribution")) {
			return !member.hasGang() ? null : Settings.formatDouble(member.getContribution());
		}
		if (parameter.equals(USER_PREFIX + "contributed-amount")) {
			return !member.hasGang() ?
			       null :
			       NumberUtil.valueFormat(Settings.getGangContributionRate() * member.getContribution());
		}
		if (parameter.equals(USER_PREFIX + "has-rank")) return String.valueOf(member.hasRank());
		if (parameter.equals(USER_PREFIX + "rank")) return member.getRank() == null ? null : member.getRank().getName();

		return null;
	}

	@Nullable
	private String resolveGang(OfflinePlayer player, String parameter) {
		Member member = memberManager.getMember(player.getUniqueId());
		if (member == null) return null;

		Gang gang = gangManager.getGang(member.getGangId());
		if (gang == null) return null;

		// info
		if (parameter.equals(GANG_PREFIX + "id")) return String.valueOf(gang.getId());
		if (parameter.equals(GANG_PREFIX + "name")) return gang.getName();
		if (parameter.equals(GANG_PREFIX + "display-name")) return gang.getDisplayNameString();
		if (parameter.equals(GANG_PREFIX + "state")) return gang.getState().name().toLowerCase();
		if (parameter.equals(GANG_PREFIX + "color")) return gang.getColor();
		if (parameter.equals(GANG_PREFIX + "color-name")) return gang.getColor().toLowerCase().replace("_", " ");
		if (parameter.equals(GANG_PREFIX + "color-code")) return ColorUtil.getColorCode(gang.getColor());
		if (parameter.equals(GANG_PREFIX + "description")) return gang.getDescription();
		if (parameter.equals(GANG_PREFIX + "created")) return gang.getDateCreatedString();

		// economy
		if (parameter.equals(GANG_PREFIX + "balance")) return NumberUtil.valueFormat(gang.getEconomy().getAmount());

		// bounty
		if (parameter.equals(GANG_PREFIX + "bounty")) return NumberUtil.valueFormat(gang.getBounty().getAmount());
		if (parameter.equals(GANG_PREFIX + "has-bounty")) return String.valueOf(gang.getBounty().hasBounty());

		// members
		if (parameter.equals(GANG_PREFIX + "members-size")) return String.valueOf(gang.getMembers().size());
		if (parameter.equals(GANG_PREFIX + "online-members-size")) {
			return String.valueOf(gang.getOnlineMembers(userManager::getUser).size());
		}
		if (parameter.equals(GANG_PREFIX + "offline-members-size")) {
			return String.valueOf(gang.getMembers().size() - gang.getOnlineMembers(userManager::getUser).size());
		}

		// ally
		if (parameter.equals(GANG_PREFIX + "ally-list")) return gang.getAllyListString();
		if (parameter.equals(GANG_PREFIX + "ally-size")) return String.valueOf(gang.getAllies().size());

		// level
		return getLevelPlaceholder(parameter, GANG_PREFIX, gang.getLevel());
	}

	@Nullable
	private String getLevelPlaceholder(String parameter, String type, Level level) {
		if (parameter.equals(type + "level")) return String.valueOf(level.getLevelValue());
		if (parameter.equals(type + "level-max")) return String.valueOf(level.getMaxLevel());
		if (parameter.equals(type + "level-next")) return String.valueOf(level.nextLevel());
		if (parameter.equals(type + "level-previous")) return String.valueOf(level.previousLevel());
		if (parameter.equals(type + "experience")) return NumberUtil.valueFormat(level.getExperience());
		if (parameter.equals(type + "experience-percentage")) return NumberUtil.valueFormat(level.getPercentage());
		if (parameter.equals(type + "experience-next-level")) {
			return NumberUtil.valueFormat(level.experienceCalculation(level.nextLevel()));
		}
		if (parameter.equals(type + "experience-previous-level")) {
			return NumberUtil.valueFormat(level.experienceCalculation(level.previousLevel()));
		}
		if (parameter.equals(type + "experience-current-level")) {
			return NumberUtil.valueFormat(level.experienceCalculation(level.getLevelValue()));
		}
		if (parameter.startsWith(type + "experience-level-")) {
			String param = parameter.substring(parameter.lastIndexOf('-') + 1);
			int    value;
			try {
				value = Integer.parseInt(param);
			} catch (NumberFormatException exception) {
				return null;
			}
			return NumberUtil.valueFormat(level.experienceCalculation(value));
		}

		return null;
	}

}
