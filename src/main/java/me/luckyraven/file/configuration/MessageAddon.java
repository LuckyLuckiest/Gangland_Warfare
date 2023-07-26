package me.luckyraven.file.configuration;

import me.luckyraven.Gangland;
import org.bukkit.configuration.file.YamlConfiguration;

import static me.luckyraven.util.ChatUtil.*;

public enum MessageAddon {

	// prefixes
	PREFIX("Normal.Prefix", Type.OTHER), INFORMATION_PREFIX("Information.Prefix", Type.OTHER), COMMAND_PREFIX(
			"Commands.Prefix", Type.OTHER), ERROR_PREFIX("Errors.Prefix", Type.OTHER),

	// date
	DATE("Normal.Format.Date", Type.OTHER), TIME("Normal.Format.Time", Type.OTHER), SECOND("Time_Unit.Second",
	                                                                                       Type.OTHER), MINUTE(
			"Time_Unit.Minute", Type.OTHER), HOUR("Time_Unit.Hour", Type.OTHER), DAY("Time_Unit.Day", Type.OTHER), WEEK(
			"Time_Unit.Week", Type.OTHER), MONTH("Time_Unit.Month", Type.OTHER), YEAR("Time_Unit.Year", Type.OTHER),

	// commands - arguments
	ARGUMENTS_MISSING("Commands.Syntax.Missing_Arguments", Type.COMMAND), ARGUMENTS_WRONG(
			"Commands.Syntax.Wrong_Arguments", Type.COMMAND), ARGUMENTS_DONT_EXIST("Commands.Syntax.Doesnt_Exist",
	                                                                               Type.COMMAND),

	// commands - weapons
	GIVE_AMMO("Commands.Weapons.Gave_Ammo", Type.COMMAND), GIVE_WEAPON("Commands.Weapons.Gave_Weapon", Type.COMMAND),

	// commands - economy
	BALANCE_PLAYER("Commands.Economy.Balance.Player", Type.COMMAND), BALANCE_TARGET("Commands.Economy.Balance.Target",
	                                                                                Type.COMMAND), DEPOSIT_MONEY_PLAYER(
			"Commands.Economy.Money.Player_Add", Type.COMMAND), WITHDRAW_MONEY_PLAYER(
			"Commands.Economy.Money.Player_Take", Type.COMMAND), SET_MONEY_PLAYER("Commands.Economy.Money.Player_Set",
	                                                                              Type.COMMAND), RESET_MONEY_PLAYER(
			"Commands.Economy.Money.Player_Reset", Type.COMMAND), DEPOSIT_MONEY_TARGET(
			"Commands.Economy.Money.Target_Add", Type.COMMAND), WITHDRAW_MONEY_TARGET(
			"Commands.Economy.Money.Target_Take", Type.COMMAND), SET_MONEY_TARGET("Commands.Economy.Money.Target_Set",
	                                                                              Type.COMMAND), RESET_MONEY_TARGET(
			"Commands.Economy.Money.Target_Reset", Type.COMMAND),

	// commands - bank
	BANK_CREATED("Commands.Bank.Create.Created_Bank", Type.COMMAND), BANK_CREATE_FEE("Commands.Bank.Create.Bank_Fee",
	                                                                                 Type.COMMAND), BANK_CREATE_CONFIRM(
			"Commands.Bank.Create.Confirm_Timer", Type.COMMAND), BANK_REMOVED("Commands.Bank.Remove.Removed_Bank",
	                                                                          Type.COMMAND), BANK_REMOVE_CONFIRM(
			"Commands.Bank.Remove.Confirm_Timer", Type.COMMAND), BANK_EXIST("Commands.Bank.Have_Bank", Type.COMMAND),
	BANK_BALANCE_PLAYER("Commands.Bank.Balance.Player", Type.COMMAND), BANK_BALANCE_TARGET(
			"Commands.Bank.Balance.Target", Type.COMMAND), BANK_MONEY_DEPOSIT_PLAYER("Commands.Bank.Money.Player_Add",
	                                                                                 Type.COMMAND),
	BANK_MONEY_WITHDRAW_PLAYER("Commands.Bank.Money.Player_Take", Type.COMMAND), BANK_MONEY_DEPOSIT_TARGET(
			"Commands.Bank.Money.Target_Add", Type.COMMAND), BANK_MONEY_WITHDRAW_TARGET(
			"Commands.Bank.Money.Target_Take", Type.COMMAND),

	// commands - gang
	GANG_CREATED("Commands.Gang.Create.Created_Gang", Type.COMMAND), GANG_CREATE_FEE("Commands.Gang.Create.Gang_Fee",
	                                                                                 Type.COMMAND), GANG_CREATE_CONFIRM(
			"Commands.Gang.Create.Confirm_Timer", Type.COMMAND), GANG_REMOVED("Commands.Gang.Remove.Removed_Gang",
	                                                                          Type.COMMAND), GANG_REMOVE_CONFIRM(
			"Commands.Gang.Remove.Confirm_Timer", Type.COMMAND), PLAYER_IN_GANG(
			"Commands.Gang.Available.Player_In_Gang", Type.COMMAND), TARGET_IN_GANG(
			"Commands.Gang.Available.Target_In_Gang", Type.COMMAND), GANG_BALANCE("Commands.Gang.Balance",
	                                                                              Type.COMMAND), GANG_MONEY_DEPOSIT(
			"Commands.Gang.Money.Add", Type.COMMAND), GANG_MONEY_WITHDRAW("Commands.Gang.Money.Take", Type.COMMAND),
	GANG_INVITE_PLAYER("Commands.Gang.Invite.Player", Type.COMMAND), GANG_INVITE_TARGET("Commands.Gang.Invite.Target",
	                                                                                    Type.COMMAND),
	GANG_INVITE_ACCEPT("Commands.Gang.Invite.Accept", Type.COMMAND), GANG_PLAYER_JOINED("Commands.Gang.Invite.Joined",
	                                                                                    Type.COMMAND), GANG_LEAVE(
			"Commands.Gang.Leave.Left", Type.COMMAND), GANG_TRANSFER_OWNERSHIP("Commands.Gang.Leave.Owner_Rank",
	                                                                           Type.COMMAND),
	GANG_PROMOTE_PLAYER_SUCCESS("Commands.Gang.Promote.Player_Success", Type.COMMAND), GANG_PROMOTE_TARGET_SUCCESS(
			"Commands.Gang.Promote.Target_Success", Type.COMMAND), GANG_PROMOTE_END("Commands.Gang.Promote.End",
	                                                                                Type.COMMAND),
	GANG_DEMOTE_PLAYER_SUCCESS("Commands.Gang.Demote.Player_Success", Type.COMMAND), GANG_DEMOTE_TARGET_SUCCESS(
			"Commands.Gang.Demote.Target_Success", Type.COMMAND), GANG_DEMOTE_END("Commands.Gang.Demote.End",
	                                                                              Type.COMMAND), GANG_KICKED_TARGET(
			"Commands.Gang.Leave.Kicked", Type.COMMAND), GANG_RENAME("Commands.Gang.Create.Rename", Type.COMMAND),
	GANG_ALLY_SEND_REQUEST("Commands.Gang.Ally.Send_Request", Type.COMMAND), GANG_ALLY_RECEIVE_REQUEST(
			"Commands.Gang.Ally.Receive_Request", Type.COMMAND), GANG_ALLY_ACCEPT("Commands.Gang.Ally.Accept_Request",
	                                                                              Type.COMMAND), GANG_ALLY_REJECT(
			"Commands.Gang.Ally.Reject_Request", Type.COMMAND), GANG_ALLY_ABANDON("Commands.Gang.Ally.Abandon",
	                                                                              Type.COMMAND), GANG_DISPLAY_SET(
			"Commands.Gang.Display_Name.Set", Type.COMMAND), GANG_DISPLAY_REMOVED("Commands.Gang.Display_Name.Removed",
	                                                                              Type.COMMAND), GANG_COLOR_SET(
			"Commands.Gang.Color.Set", Type.COMMAND), GANG_COLOR_RESET("Commands.Gang.Color.Reset", Type.COMMAND),

	// commands - rank
	RANK_CREATED("Commands.Rank.Create.Created_Rank", Type.COMMAND), RANK_CREATE_CONFIRM(
			"Commands.Rank.Create.Confirm_Timer", Type.COMMAND), RANK_REMOVED("Commands.Rank.Remove.Removed_Rank",
	                                                                          Type.COMMAND), RANK_REMOVE_CONFIRM(
			"Commands.Rank.Remove.Confirm_Timer", Type.COMMAND), RANK_EXIST("Commands.Rank.Rank_Exists", Type.COMMAND),
	RANK_PERMISSION_ADD("Commands.Rank.Permission.Add", Type.COMMAND), RANK_PERMISSION_REMOVE(
			"Commands.Rank.Permission.Remove", Type.COMMAND), RANK_LIST_PRIMARY("Commands.Rank.List.Primary",
	                                                                            Type.COMMAND), RANK_LIST_SECONDARY(
			"Commands.Rank.List.Secondary", Type.OTHER), RANK_INFO_PRIMARY("Commands.Rank.Info.Primary", Type.COMMAND),
	RANK_INFO_SECONDARY("Commands.Rank.Info.Secondary", Type.OTHER), RANK_PARENT_ADD("Commands.Rank.Parent.Add",
	                                                                                 Type.COMMAND), RANK_PARENT_REMOVE(
			"Commands.Rank.Parent.Remove", Type.COMMAND),

	// commands - teleportation
	PLAYER_SENT_TO_LOCATION("Commands.Spawn.Player.Sent", Type.COMMAND), PLAYER_TELEPORT_COOLDOWN(
			"Commands.Spawn.Player.Cooldown", Type.COMMAND), TARGET_SENT_TO_LOCATION("Commands.Spawn.Target.Sent",
	                                                                                 Type.COMMAND),
	TARGET_TELEPORT_COOLDOWN("Commands.Spawn.Target.Cooldown", Type.COMMAND),

	// commands - spawn
	SPAWN_CREATED("Commands.Spawn.Created", Type.COMMAND), SPAWN_REMOVED("Commands.Spawn.Removed", Type.COMMAND),
	SPAWN_LIST_PRIMARY("Commands.Spawn.List.Primary", Type.COMMAND), SPAWN_LIST_SECONDARY(
			"Commands.Spawn.List.Secondary", Type.OTHER),

	// commands - warp
	WARP_CREATED("Commands.Warp.Created", Type.COMMAND), WARP_REMOVED("Commands.Warp.Removed", Type.COMMAND),
	WARP_LIST_PRIMARY("Commands.Warp.List.Primary", Type.OTHER), WARP_LIST_SECONDARY("Commands.Warp.List.Secondary",
	                                                                                 Type.OTHER),

	// commands - bounty
	BOUNTY_CURRENT("Commands.Bounty.Current", Type.COMMAND), BOUNTY_INCREMENT("Commands.Bounty.Increment", Type.OTHER),
	BOUNTY_CLEAR("Commands.Bounty.Clear", Type.COMMAND), BOUNTY_LIFTED("Commands.Bounty.Lifted", Type.COMMAND),
	BOUNTY_PLAYER_LIFT("Commands.Bounty.Player_Lift", Type.COMMAND), BOUNTY_SET("Commands.Bounty.Bounty_Set",
	                                                                            Type.COMMAND),

	// errors - permissions
	COMMAND_NO_PERM("Errors.Permissions.Command", Type.ERROR), KIT_NO_PERM("Errors.Permissions.Kit", Type.ERROR),
	SPAWN_NO_PERM("Errors.Permissions.Spawn", Type.ERROR), WARP_NO_PERM("Errors.Permissions.Warp", Type.ERROR),
	SIGN_NO_PERM("Errors.Permissions.Sign", Type.ERROR), OTHER_NO_PERM("Errors.Permissions.Other", Type.ERROR),

	// errors - player
	NOT_PLAYER("Errors.Player.Not_Player", Type.ERROR), PLAYER_NOT_FOUND("Errors.Player.Player_Not_Found", Type.ERROR),
	INVENTORY_FULL("Errors.Player.Inv_Full", Type.ERROR),

	// errors - kit
	KIT_NOT_FOUND("Errors.Kit.Not_Found", Type.ERROR),

	// errors - economy
	CANNOT_EXCEED_MAXIMUM("Errors.Economy.Cannot_Exceed_Max", Type.ERROR), CANNOT_TAKE_MORE_THAN_BALANCE(
			"Errors.Economy.Cannot_Take_More_Than_Balance", Type.ERROR), CANNOT_TAKE_LESS_THAN_ZERO(
			"Errors.Economy.Cannot_Take_Less_Than_Zero", Type.ERROR),

	// errors - bank
	MUST_CREATE_BANK("Errors.Bank.Must_Create", Type.ERROR), CANNOT_CREATE_BANK("Errors.Bank.Cannot_Create",
	                                                                            Type.ERROR),

	// errors - gang
	MUST_CREATE_GANG("Errors.Gang.Must_Create", Type.ERROR), CANNOT_CREATE_GANG("Errors.Gang.Cannot_Create",
	                                                                            Type.ERROR), NOT_OWNER(
			"Errors.Gang.Not_Owner", Type.ERROR), DUPLICATE_GANG_NAME("Errors.Gang.Duplicate_Names", Type.ERROR),
	NO_GANG_INVITATION("Errors.Gang.No_Invite", Type.ERROR), GANG_SAME_RANK_ACTION("Errors.Gang.Same_Rank", Type.ERROR),
	GANG_HIGHER_RANK_ACTION("Errors.Gang.Higher_Rank", Type.ERROR), GANG_DOESNT_EXIST("Errors.Gang.Doesnt_Exist",
	                                                                                  Type.ERROR),

	// errors - rank
	INVALID_RANK("Errors.Rank.Invalid", Type.ERROR), INVALID_RANK_PERMISSION("Errors.Rank.Invalid_Permission",
	                                                                         Type.ERROR), INVALID_RANK_PARENT(
			"Errors.Rank.Invalid_Parent", Type.ERROR),

	// errors - teleportation
	SPAWN_NOT_FOUND("Errors.Teleportation.Spawn.Not_Found", Type.ERROR), SPAWN_INCORRECT_TYPE(
			"Errors.Teleportation.Spawn.Correct_Type", Type.ERROR), DEFAULT_SPAWN(
			"Errors.Teleportation.Spawn.Need_Default", Type.ERROR), SPAWN_LIST_UNDEFINED(
			"Errors.Teleportation.Spawn.List_Undefined", Type.ERROR), WARP_NOT_FOUND(
			"Errors.Teleportation.Warp.Not_Found", Type.ERROR), WORLD_NOT_FOUND("Errors.Teleportation.World.Not_Found",
	                                                                            Type.ERROR),

	// errors - bounty
	NO_BOUNTY("Errors.Bounty.No_Bounty", Type.ERROR), NO_USER_SET_BOUNTY("Errors.Bounty.User_Set_Bounty", Type.ERROR),

	// errors - others
	CANNOT_BE_NULL("Errors.Cannot_Null", Type.ERROR), MUST_BE_NUMBERS("Errors.Must_Be_Numbers", Type.ERROR),
	DONT_USE_SYMBOL("Errors.Dont_Use_Symbol", Type.ERROR),

	// information - drops
	DROPS_CLEARED("Information.Drops_Cleared", Type.INFORMATION), DROPS_TIMER("Information.Drops_Timer",
	                                                                          Type.INFORMATION),

	// information - gang
	GANG_ALLIANCE_ALREADY_SENT("Information.Gang.Request_Already_Sent", Type.INFORMATION), ALREADY_ALLIED_GANG(
			"Information.Gang.Already_Allied", Type.INFORMATION), KICKED_FROM_GANG("Information.Gang.Kicked",
	                                                                               Type.INFORMATION),

	// kits
	KIT_RECEIVED("Kits.Received", Type.COMMAND), KIT_REMOVED("Kits.Removed", Type.COMMAND), KIT_INVALID("Kits.Invalid",
	                                                                                                    Type.COMMAND),
	KIT_LIST_UNDEFINED("Kits.List_Undefined", Type.ERROR),

	// safe
	SAFE_CREATED("Safe.Created", Type.COMMAND), SAFE_REMOVED("Safe.Removed", Type.COMMAND), SAFE_ROBBED("Safe.Robbed",
	                                                                                                    Type.PREFIX),
	SAFE_RESPAWNED("Safe.Respawned", Type.PREFIX),

	// wanted level
	NOT_WANTED("Wanted_Level.Not_Wanted", Type.PREFIX), PAID_WANTED("Wanted_Level.Paid", Type.PREFIX),

	// weapons
	INVALID_AMMO("Weapons.Not_Valid_Ammo", Type.ERROR), INVALID_WEAPON("Weapons.Not_Valid_Weapon", Type.ERROR),
	INVALID_AMOUNT("Weapons.Not_Valid_Amount", Type.ERROR), KILLED_PLAYER("Weapons.Killed_Player", Type.PREFIX),
	GUN_NOT_IN_INVENTORY("Weapons.Gun_Not_In_Inventory", Type.PREFIX), GUN_BOUGHT("Weapons.Gun_Bought", Type.PREFIX),
	GUN_SOLD("Weapons.Gun_Sold", Type.PREFIX), AMMO_NOT_IN_INVENTORY("Weapons.Ammo_Not_In_Inventory", Type.PREFIX),
	AMMO_BOUGHT("Weapons.Ammo_Bought", Type.PREFIX), AMMO_SOLD("Weapons.Ammo_Sold", Type.PREFIX), NOT_ENOUGH_AMMO(
			"Weapons.Not_Enough_Ammo", Type.PREFIX),
	;

	private static YamlConfiguration message;
	private final  String            path;
	private final  Type              type;

	MessageAddon(String path, Type type) {
		this.path = path;
		this.type = type;
	}

	// Need to set the plugin inorder to run the messages
	public static void setPlugin(Gangland gangland) {
		message = gangland.getInitializer().getLanguageLoader().getMessage();
	}

	@Override
	public String toString() {
		String data = message.getString(path);
		String value;

		switch (type) {
			case PREFIX -> value = prefixMessage(data);
			case COMMAND -> value = commandMessage(data);
			case ERROR -> value = errorMessage(data);
			case INFORMATION -> value = informationMessage(data);
			default -> value = color(data);
		}

		return value;
	}
}

enum Type {

	PREFIX, COMMAND, ERROR, INFORMATION, OTHER

}
