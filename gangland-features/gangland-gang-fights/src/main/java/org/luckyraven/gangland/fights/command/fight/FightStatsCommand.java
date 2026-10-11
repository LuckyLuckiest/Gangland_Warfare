package org.luckyraven.gangland.fights.command.fight;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.fights.command.Operands;
import org.luckyraven.gangland.fights.stat.FightHistory;
import org.luckyraven.gangland.fights.stat.FightStatsReport;
import org.luckyraven.gangland.fights.stat.FightTexts;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

import java.util.List;

/** {@code /glw gang fight stats [gang]}: wins, losses, draws and the win/loss ratio of a gang (default: your own). */
public final class FightStatsCommand extends SubArgument {

	/** What the command reads; bound once by the economy configuration (the command tree is built without beans). */
	public record Services(FightHistory history, FightTexts texts, GangManager gangs, MemberManager members) {
	}

	private static volatile Services services;

	public static void bind(Services bound) {
		services = bound;
	}

	public FightStatsCommand(JavaPlugin plugin, Tree<Argument> tree, Argument parent) {
		super(plugin, "stats", tree, parent);
		OptionalArgument op0 = Operands.operand(plugin, tree, "gang", () -> {
			Services s = services;
			return s == null ? List.of() : s.gangs().getAll().stream().map(Gang::getName).toList();
		});

		this.addSubArgument(op0);
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Services s = services;
			if (s == null) return;

			Gang gang = null;
			if (args.length > 3) {
				String wanted = args[args.length - 1];
				gang = s.gangs()
				        .getAll()
				        .stream()
				        .filter(g -> g.getName().equalsIgnoreCase(wanted))
				        .findFirst()
				        .orElse(null);
				if (gang == null) {
					sender.sendMessage(s.texts().get("Stats.Unknown_Gang"));
					return;
				}
			} else if (sender instanceof Player player) {
				Member member = s.members().getMember(player.getUniqueId());
				if (member != null && member.hasGang()) gang = s.gangs().findById(member.getGangId());
			}
			if (gang == null) {
				sender.sendMessage(s.texts().get("Stats.No_Gang"));
				return;
			}

			FightStatsReport.lines(s.texts(), gang.getName(), s.history().stats(gang.getId()))
			                .forEach(sender::sendMessage);
		};
	}
}
