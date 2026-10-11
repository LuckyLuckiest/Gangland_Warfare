package org.luckyraven.gangland.gang.placeholder;

import org.luckyraven.gangland.gang.GangSettings;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.core.user.Level;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.data.placeholder.extension.PlaceholderContribution;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.file.GangRules;
import org.luckyraven.gangland.gang.level.GangLimits;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.safehouse.GangSafehouseService;
import org.luckyraven.gangland.gang.stats.GangStatsService;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.keystone.color.ColorUtil;
import org.luckyraven.keystone.util.NumberUtil;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

	/** The gang parity tokens (GP-34): a gangless player gets the configured default value for them. */
	private static final Set<String> PARITY_TOKENS = Set.of(
			"gang_friendly-fire", "gang_leader", "gang_member-list", "gang_online-list", "gang_kills", "gang_deaths",
			"gang_kdr", "gang_assists", "gang_members-money", "gang_safehouses", "gang_notice", "gang_member-cap",
			"gang_ally-cap", "gang_upgrade-cost", "gang_upgrade-xp", "gang_level-prefix", "user_rank-position",
			"user_assists");
	private static final String      STAT_PREFIX   = GANG_PREFIX + "stat_";
	/** {@code gang_board_<stat>_<n>_<name|value|level>}; never {@code bank}, the core catches any token with bank_. */
	private static final Pattern     BOARD         = Pattern.compile("gang_board_(.+)_(\\d+)_(name|value|level)");

	private final UserManager<Player>  userManager;
	private final MemberManager        memberManager;
	private final GangManager          gangManager;
	private final GangStatsService     statsService;
	private final GangSafehouseService safehouseService;
	private final GangLimits           limits;
	private final GangRules            rules;

	public GangPlaceholderContribution(@Qualifier("online") UserManager<Player> userManager, MemberManager memberManager,
	                                   GangManager gangManager) {
		this(userManager, memberManager, gangManager, new GangStatsService(), new GangSafehouseService(),
		     new GangLimits(), null);
	}

	public GangPlaceholderContribution(@Qualifier("online") UserManager<Player> userManager, MemberManager memberManager,
	                                   GangManager gangManager, GangStatsService statsService,
	                                   GangSafehouseService safehouseService, GangLimits limits, GangRules rules) {
		this.userManager      = userManager;
		this.memberManager    = memberManager;
		this.gangManager      = gangManager;
		this.statsService     = statsService;
		this.safehouseService = safehouseService;
		this.limits           = limits;
		this.rules            = rules;
	}

	@Override
	@Nullable
	public String resolve(OfflinePlayer player, String parameter) {
		String setting = resolveSetting(parameter);
		if (setting != null) return setting;
		if (PARITY_TOKENS.contains(parameter) || parameter.startsWith(STAT_PREFIX)) {
			return resolveParity(player, parameter);
		}
		if (parameter.startsWith(USER_PREFIX)) return resolveMember(player, parameter);
		if (parameter.startsWith(GANG_PREFIX)) return resolveGang(player, parameter);
		return null;
	}

	/**
	 * The gang settings moved out of settings.yml into {@code gang/gang_settings.yml} (0.15.1). They need no member or
	 * gang, so they are answered first (a gang-less player still sees the create fee), from this module's own file
	 * instead of the deprecated core field the core would otherwise fall back to.
	 */
	@Override
	@Nullable
	public String resolveSetting(String parameter) {
		return switch (parameter) {
			case GANG_PREFIX + "create_fee" -> GangSettings.getGangCreateFee().toPlainString();
			case GANG_PREFIX + "initial_balance" -> GangSettings.getGangInitialBalance().toPlainString();
			case GANG_PREFIX + "max_balance" -> GangSettings.getGangMaxBalance().toPlainString();
			case GANG_PREFIX + "contribution_rate" -> Settings.formatDouble(GangSettings.getGangContributionRate());
			case GANG_PREFIX + "name_duplicates" -> String.valueOf(GangSettings.isGangNameDuplicates());
			case GANG_PREFIX + "display_name_char" -> GangSettings.getGangDisplayNameChar();
			case GANG_PREFIX + "rank_head" -> GangSettings.getGangRankHead();
			case GANG_PREFIX + "rank_tail" -> GangSettings.getGangRankTail();
			default -> null;
		};
	}

	/**
	 * A gang board row that needs no player: {@code gang_board_kills_1_name}, {@code gang_board_kdr_3_value}. The row
	 * comes from the same stats snapshot as the chat ranking and the menu, so the three always agree.
	 */
	@Override
	@Nullable
	public String resolveGlobal(String token) {
		Matcher matcher = BOARD.matcher(token);
		if (!matcher.matches()) return null;

		String stat = matcher.group(1);
		if (!statsService.snapshot().stats().contains(stat)) return null;

		List<GangStatsService.Row> ranked = statsService.ranking(stat, true);
		int                        place  = Integer.parseInt(matcher.group(2));
		if (place < 1 || place > ranked.size()) return defaultValue();

		GangStatsService.Row row = ranked.get(place - 1);
		return switch (matcher.group(3)) {
			case "name" -> row.name();
			case "level" -> String.valueOf(row.level());
			default -> GangStatsService.format(stat, row.value(stat));
		};
	}

	private String defaultValue() {
		return rules == null ? "-" : rules.getPlaceholdersDefaultValue();
	}

	/** The parity tokens; a gangless (or unknown) player gets the configured default instead of {@code null}. */
	private String resolveParity(OfflinePlayer player, String parameter) {
		Member member = memberManager.getMember(player.getUniqueId());
		Gang   gang   = member == null || !member.hasGang() ? null : gangManager.getGang(member.getGangId());
		if (member == null || gang == null) return defaultValue();

		Optional<GangStatsService.Row> row = statsService.snapshot().row(gang.getId());
		switch (parameter) {
			case "gang_friendly-fire":
				return String.valueOf(gang.isFriendlyFire());
			case "gang_leader":
				return leader(gang);
			case "gang_member-list":
				return names(gang.getMembers().stream().map(Member::getUuid).toList());
			case "gang_online-list":
				return names(gang.getOnlineMembers(userManager::getUser).stream().map(user -> user.getUuid()).toList());
			case "gang_kills":
				return row.map(r -> String.valueOf(r.kills())).orElse("0");
			case "gang_deaths":
				return row.map(r -> String.valueOf(r.deaths())).orElse("0");
			case "gang_kdr":
				return row.map(r -> GangStatsService.format("kdr", r.kdr())).orElse("0.00");
			case "gang_assists":
				return row.map(r -> String.valueOf(r.assists())).orElse("0");
			case "gang_members-money":
				return row.map(r -> NumberUtil.valueFormat(r.membersMoney())).orElse("0");
			case "gang_safehouses":
				return String.valueOf(safehouseService.count(gang.getId()));
			case "gang_notice":
				return gang.getNotice() == null || gang.getNotice().isBlank() ? defaultValue() : gang.getNotice();
			case "gang_member-cap":
				return cap(limits.maxMembers(gang.getId()));
			case "gang_ally-cap":
				return cap(limits.maxAllies(gang.getId()));
			case "gang_upgrade-cost":
				return NumberUtil.valueFormat(limits.upgradeCost(gang.getId()));
			case "gang_upgrade-xp":
				return NumberUtil.valueFormat(limits.upgradeXpMissing(gang.getId()));
			case "gang_level-prefix":
				return limits.chatPrefix(gang.getId());
			case "user_assists":
				return String.valueOf(member.getAssists());
			case "user_rank-position":
				return rankPosition(member);
			default:
				break;
		}

		// gang_stat_<key>: any ranked stat of the player's own gang, including module ones (turfs, wins)
		String key = parameter.substring(STAT_PREFIX.length());
		if (statsService.isWealth(key) || !statsService.snapshot().stats().contains(key)) return defaultValue();
		return row.map(r -> GangStatsService.format(key, r.value(key))).orElse("0");
	}

	private static String cap(int value) {
		return value <= 0 ? "unlimited" : String.valueOf(value);
	}

	/** 1 for the top rank of the preset (the owner), counting down; the default value for a rank outside the preset. */
	private String rankPosition(Member member) {
		if (member.getRank() == null || rules == null) return defaultValue();

		int index = rules.getRankPreset().indexOf(member.getRank().getName().toLowerCase());
		return index < 0 ? defaultValue() : String.valueOf(rules.getRankPreset().size() - index);
	}

	private String leader(Gang gang) {
		String tail = GangSettings.getGangRankTail();
		for (Member member : gang.getMembers()) {
			if (member.getRank() != null && member.getRank().getName().equalsIgnoreCase(tail)) {
				String name = Bukkit.getOfflinePlayer(member.getUuid()).getName();
				return name == null ? defaultValue() : name;
			}
		}
		return defaultValue();
	}

	private String names(List<UUID> uuids) {
		String joined = uuids.stream()
				.map(uuid -> Bukkit.getOfflinePlayer(uuid).getName())
				.filter(Objects::nonNull)
				.collect(Collectors.joining(", "));
		return joined.isEmpty() ? defaultValue() : joined;
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
			       NumberUtil.valueFormat(GangSettings.getGangContributionRate() * member.getContribution());
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
