package org.luckyraven.gangland.command.sub.gang;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.GangManager;
import org.luckyraven.gangland.gang.chat.GangChatToggles;
import org.luckyraven.gangland.gang.file.GangMessages;
import org.luckyraven.gangland.gang.member.MemberManager;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.command.argument.Argument;
import org.luckyraven.keystone.command.argument.SubArgument;
import org.luckyraven.keystone.command.argument.types.OptionalArgument;
import org.luckyraven.keystone.datastructure.Tree;
import org.luckyraven.keystone.util.TriConsumer;

import java.util.Arrays;
import java.util.List;

/**
 * {@code /glw gang chat [message]}: with a message, sends it to the gang now; without one, toggles gang chat mode, in
 * which everything the player types goes to his gang only. Allies are not included.
 */
final class GangChatCommand extends SubArgument {

	private final JavaPlugin            gangland;
	private final UserManager<Player>   userManager;
	private final MemberManager         memberManager;
	private final GangManager           gangManager;
	private final GangMessages          messages;
	private final GangChatToggles       channel;

	GangChatCommand(JavaPlugin gangland, Tree<Argument> tree, Argument parent,
	                @Qualifier("online") UserManager<Player> userManager, MemberManager memberManager, GangManager gangManager,
	                GangMessages messages, GangChatToggles channel) {
		super(gangland, "chat", tree, parent);
		this.gangland      = gangland;
		this.userManager   = userManager;
		this.memberManager = memberManager;
		this.gangManager   = gangManager;
		this.messages      = messages;
		this.channel       = channel;

		this.addSubArgument(new AlliesStub(gangland, tree, this));
		this.addSubArgument(messageArgument(gangland, tree));
	}

	@Override
	protected TriConsumer<Argument, CommandSender, String[]> action() {
		return (argument, sender, args) -> {
			Gang gang = senderGang(sender);
			if (gang == null) {
				return;
			}

			Player player = (Player) sender;
			if (channel.toggle(player.getUniqueId())) {
				sender.sendMessage(messages.text("Social.Chat_On",
				                                 "&aGang chat on: what you type goes to your gang only. "
				                                 + "&7/glw gang chat again to switch it off."));
			} else {
				sender.sendMessage(messages.text("Social.Chat_Off", "&7Gang chat off."));
			}
		};
	}

	private Argument messageArgument(JavaPlugin gangland, Tree<Argument> tree) {
		OptionalArgument message = new OptionalArgument(gangland, tree, (argument, sender, args) -> {
			Gang gang = senderGang(sender);
			if (gang == null) {
				return;
			}

			String text = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
			channel.broadcast(messages, gang.getId(), ((Player) sender).getName(), text, Bukkit.getOnlinePlayers(),
			                  this::gangIdOf);
		}, sender -> List.of("<message>"));
		// Greedy, so the words after "chat" all reach the action (Keystone stops early on a non-greedy optional).
		message.setGreedy(true);
		return message;
	}

	/** The sender's gang, or {@code null} after telling a sender with no gang (or the console) so. */
	private Gang senderGang(CommandSender sender) {
		if (!(sender instanceof Player player)) {
			sender.sendMessage(Messages.MUST_CREATE_GANG.toString());
			return null;
		}

		User<Player> user = userManager.getUser(player);
		Gang gang = user == null || !user.hasGang() ? null : gangManager.getGang(user.getGangId());
		if (gang == null) {
			sender.sendMessage(Messages.MUST_CREATE_GANG.toString());
		}
		return gang;
	}

	private int gangIdOf(Player player) {
		User<Player> user = userManager.getUser(player);
		return user != null && user.hasGang() ? user.getGangId() : -1;
	}

	/** {@code /glw gang chat allies [message]}: gang and ally chat. Stub: lane W5-C fills the body. */
	private static final class AlliesStub extends SubArgument {

		AlliesStub(JavaPlugin gangland, Tree<Argument> tree, Argument parent) {
			super(gangland, "allies", tree, parent);

			OptionalArgument message = new OptionalArgument(gangland, tree);
			message.setDisplayName("message");
			message.setGreedy(true);
			this.addSubArgument(message);
		}

		@Override
		protected TriConsumer<Argument, CommandSender, String[]> action() {
			return null;
		}
	}

}
