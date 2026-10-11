package org.luckyraven.gangland.gang.command.option;

import lombok.CustomLog;
import org.bukkit.command.CommandSender;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.chat.ChatSpyService;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.membership.GangMembershipService;
import org.luckyraven.gangland.gang.purge.GangPurgeTask;
import org.luckyraven.gangland.gang.safehouse.GangSafehouseService;
import org.luckyraven.gangland.gang.stats.GangStatsService;

import java.util.function.Consumer;

/**
 * The bodies of the staff verbs under {@code /glw option gang}: disband, leader, bank, safehouse, stats reset, spy and
 * purge. Every verb works from the console; only {@code spy} and {@code safehouse tp} need a player.
 */
@CustomLog
public final class GangStaffVerbs {

	final GangManager           gangManager;
	final MemberManager         memberManager;
	final GangMembershipService membership;
	final GangSafehouseService  safehouses;
	final GangStatsService      stats;
	final ChatSpyService        spy;
	final GangPurgeTask         purge;
	final GangMessages          messages;
	final Consumer<String>      audit;

	public GangStaffVerbs(GangManager gangManager, MemberManager memberManager, GangMembershipService membership,
	                      GangSafehouseService safehouses, GangStatsService stats, ChatSpyService spy,
	                      GangPurgeTask purge, GangMessages messages) {
		this(gangManager, memberManager, membership, safehouses, stats, spy, purge, messages, log::info);
	}

	public GangStaffVerbs(GangManager gangManager, MemberManager memberManager, GangMembershipService membership,
	                      GangSafehouseService safehouses, GangStatsService stats, ChatSpyService spy,
	                      GangPurgeTask purge, GangMessages messages, Consumer<String> audit) {
		this.gangManager   = gangManager;
		this.memberManager = memberManager;
		this.membership    = membership;
		this.safehouses    = safehouses;
		this.stats         = stats;
		this.spy           = spy;
		this.purge         = purge;
		this.messages      = messages;
		this.audit         = audit;
	}

	public java.util.List<String> complete(String position) {
		return java.util.List.of();
	}

	/** @param args the whole argument line: {@code option gang <verb> ...} */
	public void handle(String verb, CommandSender sender, String[] args) {
	}

}
