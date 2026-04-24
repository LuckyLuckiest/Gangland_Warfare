package me.luckyraven.command.sub.turf;

import me.luckyraven.Gangland;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.command.argument.SubArgument;
import me.luckyraven.command.argument.types.OptionalArgument;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.core.datastructure.Tree;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.turf.contract.TurfMessageContract;
import me.luckyraven.turf.data.Turf;
import me.luckyraven.turf.manager.TurfManager;
import me.luckyraven.turf.powerups.ActiveBuffManager;
import me.luckyraven.turf.powerups.PowerupDefinition;
import me.luckyraven.turf.powerups.PowerupRegistry;
import me.luckyraven.turf.selection.WandSelectionManager;
import me.luckyraven.util.GanglandChatUtil;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /glw turf buff <powerup_id>} — admin activates a powerup catalogue entry on the active-selection turf. No
 * payment is taken (the buy flow goes through the Quartermaster panel); this is the staff-side test/correct surface so
 * QA can flip a buff without touching SQL or running the panel UI.
 *
 * <p>Bare {@code /glw turf buff} (no id) lists the buffs currently active on the selected turf.
 */
class TurfBuffCommand extends SubArgument {

	private final Gangland             gangland;
	private final Tree<Argument>       tree;
	private final TurfManager          turfs;
	private final WandSelectionManager selections;
	private final TurfMessageContract  messages;
	private final PowerupRegistry      registry;
	private final ActiveBuffManager    buffs;

	protected TurfBuffCommand(Gangland gangland, Tree<Argument> tree, Argument parent,
	                          TurfManager turfs, WandSelectionManager selections,
	                          TurfMessageContract messages,
	                          PowerupRegistry registry, ActiveBuffManager buffs) {
		super(gangland, "buff", tree, parent);

		this.gangland   = gangland;
		this.tree       = tree;
		this.turfs      = turfs;
		this.selections = selections;
		this.messages   = messages;
		this.registry   = registry;
		this.buffs      = buffs;

		this.addSubArgument(idArgument());
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Turf turf = TurfSelectionResolver.resolve(sender, turfs, selections, messages);
			if (turf == null) return;
			var active = buffs.active(turf.getId());
			if (active.isEmpty()) {
				messages.send(sender, "TURF_BUFF_LIST_EMPTY", "turf", turf.getDisplayName());
				return;
			}
			long now = System.currentTimeMillis();
			messages.send(sender, "TURF_BUFF_LIST_HEADER",
			              "turf", turf.getDisplayName(),
			              "count", String.valueOf(active.size()));
			for (var buff : active) {
				long secs = Math.max(0, buff.remainingMillis(now) / 1000L);
				messages.send(sender, "TURF_BUFF_LIST_ROW",
				              "id", buff.getPowerupId(),
				              "type", buff.getEffectType().name(),
				              "magnitude", String.valueOf(buff.getMagnitude()),
				              "time", messages.formatDuration(secs));
			}
		};
	}

	private OptionalArgument idArgument() {
		return new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Turf turf = TurfSelectionResolver.resolve(sender, turfs, selections, messages);
			if (turf == null) return;
			String            id  = args[2].toLowerCase();
			PowerupDefinition def = registry.get(id);
			if (def == null) {
				sender.sendMessage(GanglandChatUtil.setArguments(
						Messages.ARGUMENTS_MISSING.toString(), "<powerup_id>"));
				return;
			}
			buffs.activate(turf.getId(), def);
			messages.send(sender, "TURF_BUFF_ACTIVATED",
			              "turf", turf.getDisplayName(),
			              "id", def.id(),
			              "duration", messages.formatDuration(def.durationSeconds()));
		}, sender -> {
			List<String> ids = new ArrayList<>(registry.ids());
			return ids;
		});
	}
}
