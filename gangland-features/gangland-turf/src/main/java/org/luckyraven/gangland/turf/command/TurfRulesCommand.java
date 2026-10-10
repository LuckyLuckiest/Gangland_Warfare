package org.luckyraven.gangland.turf.command;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.turf.capture.CaptureRules;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.config.TurfRules;
import org.luckyraven.gangland.turf.config.TurfSettings;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /glw turf rules}: the header and the five sentences of how turf wars work on this server, with its numbers
 * (W2-E1). Read live from TurfRules, CaptureRules and TurfSettings on every call, so a {@code /glw reload} shows at
 * once. A sentence whose rule is off is left out. The in-code fallbacks equal the shipped turf_messages.yml lines.
 */
final class TurfRulesCommand extends SubArgument {

	private static final String HEADER_FALLBACK        = "&6How turf wars work";
	private static final String WINDOW_ONLINE_FALLBACK = "&7You can attack a turf while one of its owners is online, and for &f%window%&7 after the last one logs off.";
	private static final String WINDOW_OFFLINE_FALLBACK = "&7You can attack a turf once all its owners have been offline for &f%window%&7.";
	private static final String WINDOW_ONLINE_ONLY_FALLBACK = "&7You can attack a turf while one of its owners is online.";
	private static final String WAKE_UP_FALLBACK       ="&7When the first owner logs in, the turf stays shielded for &f%wake_up%&7.";
	private static final String START_FALLBACK         = "&7A capture starts when your gang has more people inside than the owners and their allies, and more than any other gang.";
	private static final String START_PRESENCE_FALLBACK = "&7You count after &f%presence%&7 inside; dying or leaving starts your count again.";
	private static final String BAR_GUARDS_FALLBACK    = "&7Each living guard holds back one attacker (up to &f%guards%&7).";
	private static final String BAR_MIN_CAPTURE_FALLBACK = "&7A capture takes at least &f%min_capture%&7.";
	private static final String BAR_MAX_CONTEST_FALLBACK = "&7A capture that goes nowhere for &f%max_contest%&7 ends.";
	private static final String BAR_THIRD_GANG_FALLBACK = "&7A third gang inside a capture of someone else's turf does not count.";
	private static final String CAP_FALLBACK          = "&7Your gang can hold &f%cap_base%&7 turfs, plus &f%per_level%&7 for each gang level, at most &f%ceiling%&7.";
	private static final String CAP_FLAT_FALLBACK     = "&7Your gang can hold &f%cap_base%&7 turfs, at most &f%ceiling%&7.";
	private static final String INCOME_FALLBACK       = "&7A new turf pays &f%money_symbol%%amount%&7 every &f%interval%&7 while one of its owners was online in the last &f%idle%&7; boosts at most x&f%max_mult%&7.";

	private final TurfRules            rules;
	private final TurfSettings         settings;
	private final TurfMessages         text;
	private final TurfMessageContract  messages;

	TurfRulesCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent, TurfRules rules, TurfSettings settings,
	                 TurfMessages text, TurfMessageContract messages) {
		super(plugin, "rules", tree, parent);
		this.rules    = rules;
		this.settings = settings;
		this.text     = text;
		this.messages = messages;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			for (String line : lines()) {
				sender.sendMessage(line);
			}
		};
	}

	/** The header, then one line per sentence that applies to the rules in force now. */
	private List<String> lines() {
		CaptureRules   capture = rules.capture();
		List<String>   lines   = new ArrayList<>();
		lines.add(text.text("Rules.Header", HEADER_FALLBACK));

		long windowSeconds = settings.getCapturePostLogoffProtectionMinutes() * 60L;
		if (capture.window() == CaptureRules.Window.OWNERS_OFFLINE) {
			lines.add(text.text("Rules.Window_Offline", WINDOW_OFFLINE_FALLBACK,
			                    "window", messages.formatDuration(windowSeconds)));
		} else {
			if (windowSeconds > 0) {
				lines.add(text.text("Rules.Window_Online", WINDOW_ONLINE_FALLBACK,
				                    "window", messages.formatDuration(windowSeconds)));
			} else {
				lines.add(text.text("Rules.Window_Online_Only", WINDOW_ONLINE_ONLY_FALLBACK));
			}
			if (capture.wakeUpShieldSeconds() > 0) {
				lines.add(text.text("Rules.Wake_Up", WAKE_UP_FALLBACK,
				                    "wake_up", messages.formatDuration(capture.wakeUpShieldSeconds())));
			}
		}

		StringBuilder start = new StringBuilder(text.text("Rules.Start", START_FALLBACK));
		if (capture.presenceSeconds() > 0) {
			start.append(' ').append(text.text("Rules.Start_Presence", START_PRESENCE_FALLBACK,
			                                   "presence", messages.formatDuration(capture.presenceSeconds())));
		}
		lines.add(start.toString());

		List<String> bar = new ArrayList<>();
		if (capture.guardWeightCap() > 0) {
			bar.add(text.text("Rules.Bar_Guards", BAR_GUARDS_FALLBACK, "guards", capture.guardWeightCap()));
		}
		if (capture.minCaptureSeconds() > 0) {
			bar.add(text.text("Rules.Bar_Min_Capture", BAR_MIN_CAPTURE_FALLBACK,
			                  "min_capture", messages.formatDuration(capture.minCaptureSeconds())));
		}
		if (capture.maxContestMinutes() > 0) {
			bar.add(text.text("Rules.Bar_Max_Contest", BAR_MAX_CONTEST_FALLBACK,
			                  "max_contest", messages.formatDuration(capture.maxContestMinutes() * 60L)));
		}
		bar.add(text.text("Rules.Bar_Third_Gang", BAR_THIRD_GANG_FALLBACK));
		lines.add(String.join(" ", bar));

		String cap    = rules.getTurfCapPerLevel() > 0
		                ? text.text("Rules.Cap", CAP_FALLBACK,
		                            "cap_base", rules.getTurfCapBase(),
		                            "per_level", rules.getTurfCapPerLevel(),
		                            "ceiling", rules.getTurfCapCeiling())
		                : text.text("Rules.Cap_Flat", CAP_FLAT_FALLBACK,
		                            "cap_base", rules.getTurfCapBase(),
		                            "ceiling", rules.getTurfCapCeiling());
		String income = text.text("Rules.Income", INCOME_FALLBACK,
		                          "money_symbol", Settings.getMoneySymbol(),
		                          "amount", Settings.formatAmount(settings.getDefaultIncomeAmount()),
		                          "interval", messages.formatDuration(settings.getIncomeIntervalMinutes() * 60L),
		                          "idle", messages.formatDuration(rules.getIncomeIdleHours() * 3600L),
		                          "max_mult", multiplier(rules.getMaxIncomeMultiplier()));
		lines.add(cap + " " + income);
		return lines;
	}

	/** 2.0 reads as {@code 2}, 1.5 as {@code 1.5}. */
	private static String multiplier(double value) {
		return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
	}

}
