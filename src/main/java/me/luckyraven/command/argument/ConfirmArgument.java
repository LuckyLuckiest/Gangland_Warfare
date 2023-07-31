package me.luckyraven.command.argument;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.datastructure.Tree;
import me.luckyraven.util.ChatUtil;
import org.bukkit.command.CommandSender;

@Getter
@Setter
public class ConfirmArgument extends Argument {

	private boolean confirmed;

	public ConfirmArgument(Tree<Argument> tree) {
		super("confirm", tree);
		this.confirmed = false;
	}

	public ConfirmArgument(Tree<Argument> tree, TriConsumer<Argument, CommandSender, String[]> action) {
		super("confirm", tree, action);
		this.confirmed = false;
	}

	@Override
	public void executeArgument(CommandSender sender, String[] args) {
		if (!confirmed) {
			sender.sendMessage(ChatUtil.errorMessage("Need to execute the initial statement to use this argument."));
			return;
		}
		this.confirmed = false;
		super.executeArgument(sender, args);
	}

}
