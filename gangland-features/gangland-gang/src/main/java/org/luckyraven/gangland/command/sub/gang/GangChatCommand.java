package org.luckyraven.gangland.command.sub.gang;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.gangland.gang.chat.GangChatChannel;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

/**
 * {@code /glw gang chat} (W2-K stub). The body arrives with W2-P2; until then the verb answers "not available
 * yet" and the constructor shape is final.
 */
final class GangChatCommand extends SubArgument {

	private final JavaPlugin            gangland;
	private final UserManager<Player>   userManager;
	private final MemberManager         memberManager;
	private final GangManager           gangManager;
	private final GangMessages          messages;
	private final GangChatChannel       channel;

	GangChatCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent,
	                             @Qualifier("online") UserManager<Player> userManager, MemberManager memberManager, GangManager gangManager,
	                             GangMessages messages, GangChatChannel channel) {
		super(gangland, "chat", tree, parent);
		this.gangland      = gangland;
		this.userManager   = userManager;
		this.memberManager = memberManager;
		this.gangManager   = gangManager;
		this.messages      = messages;
		this.channel       = channel;
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> sender.sendMessage(messages.text("Social.Not_Ready", "&7Not available yet."));
	}

}
