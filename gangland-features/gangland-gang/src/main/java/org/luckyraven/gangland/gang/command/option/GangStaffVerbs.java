package org.luckyraven.gangland.gang.command.option;

import lombok.CustomLog;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.command.sub.gang.GangNameRules;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.chat.ChatSpyService;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.membership.GangMembershipService;
import org.luckyraven.gangland.gang.permission.GangPermissions;
import org.luckyraven.gangland.gang.purge.GangPurgeTask;
import org.luckyraven.gangland.gang.purge.PurgeCandidate;
import org.luckyraven.gangland.gang.safehouse.GangSafehouseService;
import org.luckyraven.gangland.gang.safehouse.Safehouse;
import org.luckyraven.gangland.gang.stats.GangStatsService;
import org.luckyraven.keystone.economy.Currency;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The bodies of the staff verbs under {@code /glw option gang}: disband, leader, bank, safehouse, stats reset, spy and
 * purge. Every verb works from the console; only {@code spy} and {@code safehouse tp} need a player. Each body reads
 * its own arguments from the whole line ({@code option gang <verb> ...}), so a line that stops early gets one usage
 * line wherever it stops.
 */
@CustomLog
public final class GangStaffVerbs {

	private static final String BANK_USAGE      = "bank <gang> <balance|give|take|reset> [amount]";
	private static final String SAFEHOUSE_USAGE = "safehouse <gang> <list|tp|remove> [name]";

	private final GangManager           gangManager;
	private final GangMembershipService membership;
	private final GangSafehouseService  safehouses;
	private final GangStatsService      stats;
	private final ChatSpyService        spy;
	private final GangPurgeTask         purge;
	private final GangMessages          messages;
	private final Consumer<String>      audit;

	public GangStaffVerbs(GangManager gangManager, MemberManager memberManager, GangMembershipService membership,
	                      GangSafehouseService safehouses, GangStatsService stats, ChatSpyService spy,
	                      GangPurgeTask purge, GangMessages messages) {
		this(gangManager, memberManager, membership, safehouses, stats, spy, purge, messages, log::info);
	}

	/** @param audit where a staff write is recorded; the server console log by default */
	public GangStaffVerbs(GangManager gangManager, MemberManager memberManager, GangMembershipService membership,
	                      GangSafehouseService safehouses, GangStatsService stats, ChatSpyService spy,
	                      GangPurgeTask purge, GangMessages messages, Consumer<String> audit) {
		this.gangManager = gangManager;
		this.membership  = membership;
		this.safehouses  = safehouses;
		this.stats       = stats;
		this.spy         = spy;
		this.purge       = purge;
		this.messages    = messages;
		this.audit       = audit;
	}

	/** Tab-completion words for a free-text position of a staff verb. */
	public List<String> complete(String position) {
		if (position.contains("|")) return Arrays.asList(position.split("\\|"));

		return switch (position) {
			case "gang" -> gangManager.getAll().stream().map(g -> GangNameRules.plain(g.getName())).toList();
			case "player" -> Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
			case "confirm" -> List.of("confirm");
			default -> List.of("<" + position + ">");
		};
	}

	/** @param args the whole argument line: {@code option gang <verb> ...} */
	public void handle(String verb, CommandSender sender, String[] args) {
		switch (verb) {
			case "disband" -> disband(sender, args);
			case "leader" -> leader(sender, args);
			case "bank" -> bank(sender, args);
			case "safehouse" -> safehouse(sender, args);
			case "stats" -> statsReset(sender, args);
			case "spy" -> spy(sender);
			case "purge" -> purge(sender, args);
			default -> usage(sender, verb);
		}
	}

	// ---------------------------------------------------------------- disband / leader

	private void disband(CommandSender sender, String[] args) {
		Gang gang = gangAt(sender, args, 3, "disband <gang> confirm");
		if (gang == null) return;

		String name = GangNameRules.plain(gang.getName());
		if (!at(args, 4).equalsIgnoreCase("confirm")) {
			sender.sendMessage(messages.text("Staff.Disband_Confirm",
			                                 "&eThis disbands %gang%. Run &f/glw option gang disband %gang% confirm &eto do it.",
			                                 "gang", name));
			return;
		}

		GangMembershipService.Result result = membership.disband(gang.getId(), actorId(sender), true);
		if (!result.ok()) {
			sender.sendMessage(membership.text(result));
			return;
		}
		audit.accept("staff disband by " + sender.getName() + " on " + name);
		sender.sendMessage(messages.text("Staff.Disbanded", "&aGang %gang% disbanded.", "gang", name));
	}

	private void leader(CommandSender sender, String[] args) {
		Gang gang = gangAt(sender, args, 3, "leader <gang> <player>");
		if (gang == null) return;

		if (at(args, 4).isEmpty()) {
			usage(sender, "leader <gang> <player>");
			return;
		}

		String name   = GangNameRules.plain(gang.getName());
		Member target = memberNamed(gang, args[4]);
		if (target == null) {
			sender.sendMessage(messages.text("Staff.Not_Member", "&c%player% is not in %gang%.", "player", args[4],
			                                 "gang", name));
			return;
		}

		GangMembershipService.Result result = membership.setLeader(gang.getId(), actorId(sender), target.getUuid(),
		                                                           true);
		if (!result.ok()) {
			sender.sendMessage(membership.text(result));
			return;
		}
		audit.accept("staff leader by " + sender.getName() + " on " + name + ": " + args[4]);
		sender.sendMessage(messages.text("Staff.Leader_Set", "&a%player% now leads %gang%.", "player", args[4],
		                                 "gang", name));
	}

	// ---------------------------------------------------------------- bank

	private void bank(CommandSender sender, String[] args) {
		Gang gang = gangAt(sender, args, 3, BANK_USAGE);
		if (gang == null) return;

		String     operation = at(args, 4).toLowerCase();
		String     name      = GangNameRules.plain(gang.getName());
		BigDecimal before    = gang.getEconomy().getAmount();

		switch (operation) {
			case "balance" -> sender.sendMessage(messages.text("Staff.Bank_Balance", "&7%gang% holds &e%amount%&7.",
			                                                   "gang", name, "amount", plain(before)));
			case "reset" -> writeBank(sender, gang, "reset", before, BigDecimal.ZERO, before);
			case "give", "take" -> {
				BigDecimal amount = amountAt(sender, at(args, 5));
				if (amount == null) return;

				if (operation.equals("give")) {
					writeBank(sender, gang, operation, before, before.add(amount), amount);
				} else if (before.compareTo(amount) < 0) {
					sender.sendMessage(messages.text("Staff.Bank_Short", "&c%gang% holds only %amount%.", "gang",
					                                 name, "amount", plain(before)));
				} else {
					writeBank(sender, gang, operation, before, before.subtract(amount), amount);
				}
			}
			default -> usage(sender, BANK_USAGE);
		}
	}

	private void writeBank(CommandSender sender, Gang gang, String operation, BigDecimal before, BigDecimal after,
	                       BigDecimal amount) {
		gang.getEconomy().setAmount(after);

		String name = GangNameRules.plain(gang.getName());
		audit.accept("staff bank " + operation + " by " + sender.getName() + " on " + name + ": " + plain(amount) +
		             " (balance " + plain(before) + " -> " + plain(after) + ")");
		sender.sendMessage(messages.text("Staff.Bank_Changed", "&aBank of %gang%: %before% -> %after%.", "gang", name,
		                                 "before", plain(before), "after", plain(after)));
	}

	@Nullable
	private BigDecimal amountAt(CommandSender sender, String raw) {
		if (raw.isEmpty()) {
			usage(sender, BANK_USAGE);
			return null;
		}

		BigDecimal amount = null;
		try {
			amount = Currency.parse(raw);
		} catch (NumberFormatException ignored) {
			// reported below with the positive-amount case
		}

		if (amount == null || amount.signum() <= 0) {
			sender.sendMessage(messages.text("Staff.Bad_Amount", "&c%value% is not an amount.", "value", raw));
			return null;
		}
		return amount;
	}

	// ---------------------------------------------------------------- safehouse

	private void safehouse(CommandSender sender, String[] args) {
		Gang gang = gangAt(sender, args, 3, SAFEHOUSE_USAGE);
		if (gang == null) return;

		String operation = at(args, 4).toLowerCase();
		String name      = GangNameRules.plain(gang.getName());

		switch (operation) {
			case "list" -> {
				List<Safehouse> all = safehouses.list(gang.getId());
				sender.sendMessage(messages.text("Staff.Safehouse_Header", "&6Safehouses of %gang% &7(%count%)",
				                                 "gang", name, "count", all.size()));
				for (Safehouse house : all) {
					sender.sendMessage(messages.text("Staff.Safehouse_Row", "&7- &f%name% &7(%world% %x% %y% %z%)",
					                                 "name", house.name(), "world", house.world(), "x",
					                                 (int) house.x(), "y", (int) house.y(), "z", (int) house.z()));
				}
			}
			case "tp", "remove" -> {
				String houseName = at(args, 5);
				if (houseName.isEmpty()) {
					usage(sender, SAFEHOUSE_USAGE);
					return;
				}

				Optional<Safehouse> found = safehouses.find(gang.getId(), houseName);
				if (found.isEmpty()) {
					sender.sendMessage(messages.text("Staff.Safehouse_Missing", "&c%gang% has no safehouse %name%.",
					                                 "gang", name, "name", houseName));
				} else if (operation.equals("remove")) {
					safehouses.remove(gang.getId(), found.get().name());
					audit.accept("staff safehouse remove by " + sender.getName() + " on " + name + ": " +
					             found.get().name());
					sender.sendMessage(messages.text("Staff.Safehouse_Removed", "&7Safehouse %name% removed.", "name",
					                                 found.get().name()));
				} else {
					teleport(sender, found.get());
				}
			}
			default -> usage(sender, SAFEHOUSE_USAGE);
		}
	}

	/** Staff go straight there: no warm-up, cooldown or fee (the member-side teleport charges the gang's rules). */
	private void teleport(CommandSender sender, Safehouse house) {
		if (!(sender instanceof Player player)) {
			sender.sendMessage(Messages.NOT_PLAYER.toString());
			return;
		}

		World world = Bukkit.getWorld(house.world());
		if (world == null) {
			sender.sendMessage(messages.text("Staff.World_Missing", "&cWorld %world% is not loaded.", "world",
			                                 house.world()));
			return;
		}
		player.teleport(new Location(world, house.x(), house.y(), house.z(), house.yaw(), house.pitch()));
	}

	// ---------------------------------------------------------------- stats / spy / purge

	private void statsReset(CommandSender sender, String[] args) {
		if (!at(args, 3).equalsIgnoreCase("reset") || at(args, 4).isEmpty()) {
			usage(sender, "stats reset <player>");
			return;
		}

		for (Gang gang : gangManager.getAll()) {
			Member member = memberNamed(gang, args[4]);
			if (member == null) continue;

			stats.resetPlayer(member.getUuid());
			sender.sendMessage(messages.text("Staff.Stats_Reset", "&aKills, deaths and assists of %player% reset.",
			                                 "player", args[4]));
			return;
		}

		sender.sendMessage(messages.text("Staff.Player_Not_Found", "&c%player% is in no gang.", "player", args[4]));
	}

	private void spy(CommandSender sender) {
		if (!(sender instanceof Player player)) {
			sender.sendMessage(Messages.NOT_PLAYER.toString());
			return;
		}
		if (!player.hasPermission(GangPermissions.SPY)) {
			sender.sendMessage(Messages.COMMAND_NO_PERM.toString());
			return;
		}

		boolean on = spy.toggle(player.getUniqueId());
		sender.sendMessage(on ? messages.text("Staff.Spy_On", "&aYou now read gang and ally chat.") :
		                   messages.text("Staff.Spy_Off", "&7You stopped reading gang and ally chat."));
	}

	private void purge(CommandSender sender, String[] args) {
		String  operation = at(args, 3).toLowerCase();
		Instant now       = Instant.now();

		switch (operation) {
			case "preview" -> {
				List<PurgeCandidate> candidates = purge.preview(now);
				if (candidates.isEmpty()) {
					sender.sendMessage(messages.text("Membership.Purge_Preview_Empty",
					                                 "&7No gang is inactive enough to be purged."));
				}
				for (PurgeCandidate candidate : candidates) {
					long days = Duration.between(Instant.ofEpochMilli(candidate.lastSeenMillis()), now).toDays();
					sender.sendMessage(messages.text("Membership.Purge_Preview_Row",
					                                 "&7%gang% &8- &7last seen %days% days ago", "gang",
					                                 GangNameRules.plain(candidate.name()), "days", days));
				}
			}
			case "run" -> {
				int count = purge.run(now);
				audit.accept("staff purge by " + sender.getName() + ": " + count + " gang(s) disbanded");
				sender.sendMessage(messages.text("Membership.Purge_Done", "&aPurged %count% gang(s).", "count",
				                                 count));
			}
			default -> usage(sender, "purge <preview|run>");
		}
	}

	// ---------------------------------------------------------------- helpers

	private void usage(CommandSender sender, String usage) {
		sender.sendMessage(messages.text("Staff.Usage", "&cUsage: /glw option gang %usage%", "usage", usage));
	}

	/** The word at {@code index}, or an empty string when the line stopped earlier. */
	private static String at(String[] args, int index) {
		return index < args.length && args[index] != null ? args[index] : "";
	}

	@Nullable
	private static UUID actorId(CommandSender sender) {
		return sender instanceof Player player ? player.getUniqueId() : null;
	}

	/** The gang named at {@code index} (plain name in any case, or its id); a usage or unknown-gang line otherwise. */
	@Nullable
	private Gang gangAt(CommandSender sender, String[] args, int index, String usage) {
		String word = at(args, index);
		if (word.isEmpty()) {
			usage(sender, usage);
			return null;
		}

		for (Gang gang : gangManager.getAll()) {
			boolean byName = gang.getName() != null && GangNameRules.plain(gang.getName()).equalsIgnoreCase(word);
			if (byName || String.valueOf(gang.getId()).equals(word)) return gang;
		}

		sender.sendMessage(messages.text("Staff.Unknown_Gang", "&cNo gang named %gang%.", "gang", word));
		return null;
	}

	@Nullable
	private static Member memberNamed(Gang gang, String name) {
		for (Member member : gang.getMembers()) {
			OfflinePlayer player = Bukkit.getOfflinePlayer(member.getUuid());
			if (player != null && name.equalsIgnoreCase(player.getName())) return member;
		}
		return null;
	}

	/** An amount without trailing zeros ("50", not "50.0000"). */
	private static String plain(BigDecimal amount) {
		return amount.signum() == 0 ? "0" : amount.stripTrailingZeros().toPlainString();
	}

}
