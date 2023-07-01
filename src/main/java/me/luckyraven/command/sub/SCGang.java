package me.luckyraven.command.sub;

import me.luckyraven.Gangland;
import me.luckyraven.command.Argument;
import me.luckyraven.command.CommandHandler;
import org.bukkit.command.CommandSender;

public class SCGang extends CommandHandler {


    public SCGang(Gangland gangland) {
        super(gangland, "gang", false);
    }

    @Override
    protected void onExecute(CommandSender commandSender, String[] arguments) {
        // displays the stats of the gang
        // TODO
    }

    @Override
    protected void initializeArguments() {
        // create argument
        Argument create = new Argument(new String[]{"create"}, getArgumentTree(), (sender, args) -> {

        });

        // delete
        Argument delete = new Argument(new String[]{"create"}, getArgumentTree(), (sender, args) -> {

        });

        // add user
        Argument addUser = new Argument(new String[]{"create"}, getArgumentTree(), (sender, args) -> {

        });

        // remove user
        Argument removeUser = new Argument(new String[]{"create"}, getArgumentTree(), (sender, args) -> {

        });

        // promote user
        Argument promoteUser = new Argument(new String[]{"create"}, getArgumentTree(), (sender, args) -> {

        });

        // demote user
        Argument demoteUser = new Argument(new String[]{"create"}, getArgumentTree(), (sender, args) -> {

        });

        // deposit money
        Argument deposit = new Argument(new String[]{"create"}, getArgumentTree(), (sender, args) -> {

        });

        // withdraw money
        Argument withdraw = new Argument(new String[]{"create"}, getArgumentTree(), (sender, args) -> {

        });

        // balance
        Argument balance = new Argument(new String[]{"create"}, getArgumentTree(), (sender, args) -> {

        });

        // add sub arguments
        getArgument().addSubArgument(create);
        getArgument().addSubArgument(delete);
        getArgument().addSubArgument(addUser);
        getArgument().addSubArgument(removeUser);
        getArgument().addSubArgument(promoteUser);
        getArgument().addSubArgument(demoteUser);
        getArgument().addSubArgument(deposit);
        getArgument().addSubArgument(withdraw);
        getArgument().addSubArgument(balance);
    }

    @Override
    protected void help(CommandSender sender, int page) {

    }
}
