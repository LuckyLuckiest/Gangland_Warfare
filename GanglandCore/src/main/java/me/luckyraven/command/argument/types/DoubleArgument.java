package me.luckyraven.command.argument.types;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.command.argument.Argument;
import me.luckyraven.datastructure.Tree;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.TriConsumer;
import org.bukkit.command.CommandSender;

@Getter
@Setter
public class DoubleArgument extends Argument {

	private boolean confirmed;

	public DoubleArgument(String argument, Tree<Argument> tree) {
		this(new String[]{argument}, tree, null);
	}

	public DoubleArgument(String[] arguments, Tree<Argument> tree,
						  TriConsumer<Argument, CommandSender, String[]> action) {
		this(arguments, tree, action, "");
	}

	public DoubleArgument(String argument, Tree<Argument> tree, TriConsumer<Argument, CommandSender, String[]> action,
						  String permission) {
		this(new String[]{argument}, tree, action, permission);
	}

	public DoubleArgument(String[] arguments, Tree<Argument> tree,
						  TriConsumer<Argument, CommandSender, String[]> action, String permission) {
		super(arguments, tree, action, permission);
		this.confirmed = false;
	}

	@Override
	public void executeArgument(CommandSender sender, String[] args) {
		if (!confirmed) {
			sender.sendMessage(ChatUtil.informationMessage("To confirm the command re-type it again."));
			confirmed = true;
			return;
		}

		this.confirmed = false;

		super.executeArgument(sender, args);
	}

}
