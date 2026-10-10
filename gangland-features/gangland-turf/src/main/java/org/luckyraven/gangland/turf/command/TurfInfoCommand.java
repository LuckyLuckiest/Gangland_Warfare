package org.luckyraven.gangland.turf.command;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.turf.capture.CaptureEligibility;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.config.TurfSettings;
import org.luckyraven.gangland.turf.contract.TurfMessageContract;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.data.TurfRuntimeState;
import org.luckyraven.gangland.turf.listener.GangDisplayNameResolver;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.selection.WandSelectionManager;
import org.luckyraven.gangland.util.GanglandChatUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /glw turf info [id|name]}: prints owner / bounds / income / state and the capture reason line. With no
 * argument it shows the admin's active-selection turf (or the one they are standing inside); with an id or a turf name
 * it shows that turf.
 */
class TurfInfoCommand extends SubArgument {

	private final JavaPlugin           plugin;
	private final Tree<Argument>       tree;
	private final TurfManager          turfs;
	private final GangLookupContract   gangs;
	private final WandSelectionManager selections;
	private final TurfMessageContract  messages;
	private final TurfSettings         settings;
	private final CaptureEligibility   eligibility;
	private final TurfMessages         text;

	protected TurfInfoCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent,
	                          TurfManager turfs, GangLookupContract gangs, WandSelectionManager selections,
	                          TurfMessageContract messages, TurfSettings settings,
	                          CaptureEligibility eligibility, TurfMessages text) {
		super(plugin, "info", tree, parent);

		this.plugin      = plugin;
		this.tree        = tree;
		this.turfs       = turfs;
		this.gangs       = gangs;
		this.selections  = selections;
		this.messages    = messages;
		this.settings    = settings;
		this.eligibility = eligibility;
		this.text        = text;

		this.addSubArgument(idArgument());
	}

	static void renderInfo(CommandSender sender, GangLookupContract gangs, TurfManager turfs,
	                       TurfMessageContract messages, TurfSettings settings, Turf turf,
	                       CaptureEligibility eligibility, TurfMessages text) {
		String gangName = "Unclaimed";
		if (turf.getOwnerGangId() != null) {
			Gang owner = gangs.findById(turf.getOwnerGangId());
			gangName = GangDisplayNameResolver.resolve(owner);
		}
		TurfRuntimeState state    = turfs.getRuntimeState(turf.getId());
		String           stateStr = state == null ? "IDLE" : state.getState().name();

		messages.send(sender, "TURF_INFO_HEADER",
		              "turf", turf.getDisplayName(),
		              "id", String.valueOf(turf.getId()));
		messages.send(sender, "TURF_INFO_OWNER", "gang", gangName);
		messages.send(sender, "TURF_INFO_REGION",
		              "min", turf.getRegion().getMinX() + "," + turf.getRegion().getMinZ(),
		              "max", turf.getRegion().getMaxX() + "," + turf.getRegion().getMaxZ(),
		              "world", turf.getRegion().getWorld());
		messages.send(sender, "TURF_INFO_INCOME",
		              "money_symbol", Settings.getMoneySymbol(),
		              "amount", Settings.formatAmount(turf.getIncomeAmount()),
		              "time", messages.formatDuration(settings.getIncomeIntervalMinutes() * 60L));
		messages.send(sender, "TURF_INFO_STATE", "state", stateStr);

		long   now    = System.currentTimeMillis();
		String owner  = turf.isUnclaimed() ? "" : gangName;
		String reason = text.reason(eligibility.evaluate(turf, now), owner, now);
		sender.sendMessage(text.text("Info.Reason_Line", "&7Capture: %reason%", "reason", reason));
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Turf turf = TurfSelectionResolver.resolve(sender, turfs, selections, messages);
			if (turf == null) {
				return;
			}
			renderInfo(sender, gangs, turfs, messages, settings, turf, eligibility, text);
		};
	}

	/**
	 * The optional {@code <id|name>} after {@code info}: an integer is a turf id, anything else is matched against the
	 * turf names. Tab completion offers every id and every name.
	 */
	OptionalArgument idArgument() {
		return new OptionalArgument(plugin, tree, (argument, sender, args) -> {
			// args = [turf, info, <id|name>]
			Turf turf = find(args[2]);
			if (turf == null) {
				messages.send(sender, "TURF_NOT_FOUND", "turf", args[2]);
				return;
			}
			renderInfo(sender, gangs, turfs, messages, settings, turf, eligibility, text);
		}, sender -> {
			List<String> tokens = new ArrayList<>();
			for (Turf turf : turfs.getAll()) {
				tokens.add(String.valueOf(turf.getId()));
				tokens.add(token(turf));
			}
			return tokens;
		});
	}

	private Turf find(String token) {
		Turf byId = null;
		try {
			byId = turfs.get(Integer.parseInt(token));
		} catch (NumberFormatException ignored) {
			// not an id: the name match below handles it
		}
		if (byId != null) {
			return byId;
		}
		return turfs.getAll().stream()
				.filter(turf -> token(turf).equalsIgnoreCase(token))
				.findFirst()
				.orElse(null);
	}

	/** The turf name as one typed token: colour codes and hex colours removed, spaces removed. */
	private static String token(Turf turf) {
		return ChatColor.stripColor(GanglandChatUtil.color(turf.getDisplayName())).replace(" ", "");
	}
}
