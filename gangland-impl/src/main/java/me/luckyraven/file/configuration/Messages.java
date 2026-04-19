package me.luckyraven.file.configuration;

import me.luckyraven.util.GanglandChatUtil;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.List;

public enum Messages {

	// prefixes
	PREFIX("Normal.Prefix", Type.OTHER),
	INFORMATION_PREFIX("Information.Prefix", Type.OTHER),
	COMMAND_PREFIX("Commands.Prefix", Type.OTHER),
	ERROR_PREFIX("Errors.Prefix", Type.OTHER),

	// date
	SECOND("Time_Unit.Second", Type.OTHER),
	MINUTE("Time_Unit.Minute", Type.OTHER),
	HOUR("Time_Unit.Hour", Type.OTHER),
	DAY("Time_Unit.Day", Type.OTHER),
	WEEK("Time_Unit.Week", Type.OTHER),
	YEAR("Time_Unit.Year", Type.OTHER),

	// commands - arguments
	ARGUMENTS_MISSING("Commands.Syntax.Missing_Arguments", Type.COMMAND),
	ARGUMENTS_WRONG("Commands.Syntax.Wrong_Arguments", Type.COMMAND),
	ARGUMENTS_DONT_EXIST("Commands.Syntax.Doesnt_Exist", Type.COMMAND),

	// commands - weapons
	RECEIVED_AMMO("Commands.Weapons.Ammo.Received", Type.COMMAND),
	GAVE_AMMO("Commands.Weapons.Ammo.Gave", Type.COMMAND),
	RECEIVED_WEAPON("Commands.Weapons.Weapon.Received", Type.COMMAND),
	GAVE_WEAPON("Commands.Weapons.Weapon.Gave", Type.COMMAND),

	// commands - item money
	ITEM_MONEY_GAVE("Commands.Item.Money.Gave", Type.COMMAND),
	ITEM_MONEY_INVALID("Commands.Item.Money.Invalid", Type.ERROR),
	ITEM_MONEY_NOT_HELD("Commands.Item.Money.Not_Held", Type.ERROR),
	ITEM_MONEY_NOT_REGISTERED("Commands.Item.Money.Not_Registered", Type.ERROR),
	ITEM_MONEY_LIST_HEADER("Commands.Item.Money.List_Header", Type.COMMAND),

	// commands - wanted
	WANTED_CLEARED_OTHER("Commands.Wanted.Cleared_Other", Type.COMMAND),

	// commands - economy
	BALANCE_PLAYER("Commands.Economy.Balance.Player", Type.COMMAND),
	BALANCE_TARGET("Commands.Economy.Balance.Target", Type.COMMAND),
	FREE_TRANSACTION("Commands.Economy.Money.Free", Type.COMMAND),
	DEPOSIT_MONEY_PLAYER("Commands.Economy.Money.Player.Add", Type.COMMAND),
	WITHDRAW_MONEY_PLAYER("Commands.Economy.Money.Player.Take", Type.COMMAND),
	SET_MONEY_PLAYER("Commands.Economy.Money.Player.Set", Type.COMMAND),
	RESET_MONEY_PLAYER("Commands.Economy.Money.Player.Reset", Type.COMMAND),
	DEPOSIT_MONEY_TARGET("Commands.Economy.Money.Target.Add", Type.COMMAND),
	WITHDRAW_MONEY_TARGET("Commands.Economy.Money.Target.Take", Type.COMMAND),
	SET_MONEY_TARGET("Commands.Economy.Money.Target.Set", Type.COMMAND),
	RESET_MONEY_TARGET("Commands.Economy.Money.Target.Reset", Type.COMMAND),

	// commands - bank
	BANK_CREATED("Commands.Bank.Create.Created_Bank", Type.COMMAND),
	BANK_CREATE_FEE("Commands.Bank.Create.Bank_Fee", Type.COMMAND),
	BANK_CREATE_CONFIRM("Commands.Bank.Create.Confirm_Timer", Type.COMMAND),
	BANK_REMOVED("Commands.Bank.Remove.Removed_Bank", Type.COMMAND),
	BANK_REMOVE_CONFIRM("Commands.Bank.Remove.Confirm_Timer", Type.COMMAND),
	BANK_EXIST("Commands.Bank.Have_Bank", Type.COMMAND),
	BANK_BALANCE_PLAYER("Commands.Bank.Balance.Player", Type.COMMAND),
	BANK_BALANCE_TARGET("Commands.Bank.Balance.Target", Type.COMMAND),
	BANK_MONEY_DEPOSIT_PLAYER("Commands.Bank.Money.Player_Add", Type.COMMAND),
	BANK_MONEY_WITHDRAW_PLAYER("Commands.Bank.Money.Player_Take", Type.COMMAND),
	BANK_MONEY_DEPOSIT_TARGET("Commands.Bank.Money.Target_Add", Type.COMMAND),
	BANK_MONEY_WITHDRAW_TARGET("Commands.Bank.Money.Target_Take", Type.COMMAND),

	// commands - gang
	GANG_CREATED("Commands.Gang.Create.Created_Gang", Type.COMMAND),
	GANG_CREATE_FEE("Commands.Gang.Create.Gang_Fee", Type.COMMAND),
	GANG_CREATE_CONFIRM("Commands.Gang.Create.Confirm_Timer", Type.COMMAND),
	GANG_REMOVED("Commands.Gang.Remove.Removed_Gang", Type.COMMAND),
	GANG_REMOVE_CONFIRM("Commands.Gang.Remove.Confirm_Timer", Type.COMMAND),
	PLAYER_IN_GANG("Commands.Gang.Available.Player_In_Gang", Type.COMMAND),
	TARGET_IN_GANG("Commands.Gang.Available.Target_In_Gang", Type.COMMAND),
	GANG_BALANCE("Commands.Gang.Balance", Type.COMMAND),
	GANG_MONEY_DEPOSIT("Commands.Gang.Money.Add", Type.COMMAND),
	GANG_MONEY_WITHDRAW("Commands.Gang.Money.Take", Type.COMMAND),
	GANG_INVITE_PLAYER("Commands.Gang.Invite.Player", Type.COMMAND),
	GANG_INVITE_TARGET("Commands.Gang.Invite.Target", Type.COMMAND),
	GANG_INVITE_ACCEPT("Commands.Gang.Invite.Accept", Type.COMMAND),
	GANG_PLAYER_JOINED("Commands.Gang.Invite.Joined", Type.COMMAND),
	GANG_LEAVE("Commands.Gang.Leave.Left", Type.COMMAND),
	GANG_TRANSFER_OWNERSHIP("Commands.Gang.Leave.Owner_Rank", Type.COMMAND),
	GANG_PROMOTE_PLAYER_SUCCESS("Commands.Gang.Promote.Player_Success", Type.COMMAND),
	GANG_PROMOTE_TARGET_SUCCESS("Commands.Gang.Promote.Target_Success", Type.COMMAND),
	GANG_PROMOTE_END("Commands.Gang.Promote.End", Type.COMMAND),
	GANG_DEMOTE_PLAYER_SUCCESS("Commands.Gang.Demote.Player_Success", Type.COMMAND),
	GANG_DEMOTE_TARGET_SUCCESS("Commands.Gang.Demote.Target_Success", Type.COMMAND),
	GANG_DEMOTE_END("Commands.Gang.Demote.End", Type.COMMAND),
	GANG_KICKED_TARGET("Commands.Gang.Leave.Kicked", Type.COMMAND),
	GANG_RENAME("Commands.Gang.Create.Rename", Type.COMMAND),
	GANG_ALLY_SEND_REQUEST("Commands.Gang.Ally.Send_Request", Type.COMMAND),
	GANG_ALLY_RECEIVE_REQUEST("Commands.Gang.Ally.Receive_Request", Type.COMMAND),
	GANG_ALLY_ACCEPT("Commands.Gang.Ally.Accept_Request", Type.COMMAND),
	GANG_ALLY_REJECT("Commands.Gang.Ally.Reject_Request", Type.COMMAND),
	GANG_ALLY_ABANDON("Commands.Gang.Ally.Abandon", Type.COMMAND),
	GANG_DISPLAY_SET("Commands.Gang.Display_Name.Set", Type.COMMAND),
	GANG_DISPLAY_REMOVED("Commands.Gang.Display_Name.Removed", Type.COMMAND),
	GANG_COLOR_SET("Commands.Gang.Color.Set", Type.COMMAND),
	GANG_COLOR_RESET("Commands.Gang.Color.Reset", Type.COMMAND),
	GANG_DESCRIPTION_CHANGE("Commands.Gang.Description.Change", Type.COMMAND),
	GANG_DESCRIPTION_NO_CHANGE("Commands.Gang.Description.Cancelled", Type.COMMAND),

	// commands - rank
	RANK_CREATED("Commands.Rank.Create.Created_Rank", Type.COMMAND),
	RANK_CREATE_CONFIRM("Commands.Rank.Create.Confirm_Timer", Type.COMMAND),
	RANK_REMOVED("Commands.Rank.Remove.Removed_Rank", Type.COMMAND),
	RANK_REMOVE_CONFIRM("Commands.Rank.Remove.Confirm_Timer", Type.COMMAND),
	RANK_EXIST("Commands.Rank.Rank_Exists", Type.COMMAND),
	RANK_PERMISSION_ADD("Commands.Rank.Permission.Add", Type.COMMAND),
	RANK_PERMISSION_REMOVE("Commands.Rank.Permission.Remove", Type.COMMAND),
	RANK_LIST_PRIMARY("Commands.Rank.List.Primary", Type.COMMAND),
	RANK_LIST_SECONDARY("Commands.Rank.List.Secondary", Type.OTHER),
	RANK_INFO_PRIMARY("Commands.Rank.Info.Primary", Type.COMMAND),
	RANK_INFO_SECONDARY("Commands.Rank.Info.Secondary", Type.OTHER),
	RANK_PARENT_ADD("Commands.Rank.Parent.Add", Type.COMMAND),
	RANK_PARENT_REMOVE("Commands.Rank.Parent.Remove", Type.COMMAND),
	RANK_PERMISSION_EXISTS("Commands.Rank.Permission.Exists", Type.COMMAND),

	// commands - bounty
	BOUNTY_CURRENT("Commands.Bounty.Current", Type.COMMAND),
	BOUNTY_INCREMENT("Commands.Bounty.Increment", Type.OTHER),
	BOUNTY_CLEAR("Commands.Bounty.Clear", Type.COMMAND),
	BOUNTY_LIFTED("Commands.Bounty.Lifted", Type.COMMAND),
	BOUNTY_PLAYER_LIFT("Commands.Bounty.Player_Lift", Type.COMMAND),
	BOUNTY_SET("Commands.Bounty.Bounty_Set", Type.COMMAND),

	// commands - level
	LEVEL_EXP_ADD("Commands.Level.Experience.Add", Type.COMMAND),
	LEVEL_EXP_REMOVE("Commands.Level.Experience.Remove", Type.COMMAND),
	LEVEL_ADD("Commands.Level.Level.Add", Type.COMMAND),
	LEVEL_REMOVE("Commands.Level.Level.Remove", Type.COMMAND),
	LEVEL_NEXT("Commands.Level.Next", Type.COMMAND),

	// commands - waypoint
	WAYPOINT_CREATED("Commands.Waypoint.Create.Created_Waypoint", Type.COMMAND),
	WAYPOINT_CREATE_CONFIRM("Commands.Waypoint.Create.Confirm_Timer", Type.COMMAND),
	WAYPOINT_SELECTED("Commands.Waypoint.Select.Selected", Type.COMMAND),
	WAYPOINT_DESELECTED("Commands.Waypoint.Select.Deselected", Type.COMMAND),
	WAYPOINT_LIST_PRIMARY("Commands.Waypoint.List.Primary", Type.COMMAND),
	WAYPOINT_LIST_SECONDARY("Commands.Waypoint.List.Secondary", Type.COMMAND),
	WAYPOINT_CONFIGURATION_SUCCESS("Commands.Waypoint.Configuration.Updated", Type.COMMAND),
	WAYPOINT_TELEPORT("Commands.Waypoint.Teleportation.Sent", Type.COMMAND),
	WAYPOINT_TELEPORT_TIMER("Commands.Waypoint.Teleportation.Timer", Type.COMMAND),

	// errors - permissions
	COMMAND_NO_PERM("Errors.Permissions.Command", Type.ERROR),
	KIT_NO_PERM("Errors.Permissions.Kit", Type.ERROR),
	SPAWN_NO_PERM("Errors.Permissions.Spawn", Type.ERROR),
	WARP_NO_PERM("Errors.Permissions.Warp", Type.ERROR),
	SIGN_NO_PERM("Errors.Permissions.Sign", Type.ERROR),
	OTHER_NO_PERM("Errors.Permissions.Other", Type.ERROR),

	// errors - player
	NOT_PLAYER("Errors.Player.Not_Player", Type.ERROR),
	PLAYER_NOT_FOUND("Errors.Player.Player_Not_Found", Type.ERROR),
	INVENTORY_FULL("Errors.Player.Inv_Full", Type.ERROR),

	// errors - kit
	KIT_NOT_FOUND("Errors.Kit.Not_Found", Type.ERROR),

	// errors - economy
	CANNOT_EXCEED_MAXIMUM("Errors.Economy.Cannot_Exceed_Max", Type.ERROR),
	CANNOT_TAKE_MORE_THAN_BALANCE("Errors.Economy.Cannot_Take_More_Than_Balance", Type.ERROR),
	CANNOT_TAKE_LESS_THAN_ZERO("Errors.Economy.Cannot_Take_Less_Than_Zero", Type.ERROR),

	// errors - bank
	MUST_CREATE_BANK("Errors.Bank.Must_Create", Type.ERROR),
	CANNOT_CREATE_BANK("Errors.Bank.Cannot_Create", Type.ERROR),

	// errors - gang
	INVALID_GANG_NAME("Errors.Gang.Invalid_Name", Type.ERROR),
	MUST_CREATE_GANG("Errors.Gang.Must_Create", Type.ERROR),
	CANNOT_CREATE_GANG("Errors.Gang.Cannot_Create", Type.ERROR),
	NOT_OWNER("Errors.Gang.Not_Owner", Type.ERROR),
	DUPLICATE_GANG_NAME("Errors.Gang.Duplicate_Names", Type.ERROR),
	NO_GANG_INVITATION("Errors.Gang.No_Invite", Type.ERROR),
	GANG_SAME_RANK_ACTION("Errors.Gang.Same_Rank", Type.ERROR),
	GANG_HIGHER_RANK_ACTION("Errors.Gang.Higher_Rank", Type.ERROR),
	GANG_DOESNT_EXIST("Errors.Gang.Doesnt_Exist", Type.ERROR),

	// errors - rank
	INVALID_RANK("Errors.Rank.Invalid", Type.ERROR),
	INVALID_RANK_PERMISSION("Errors.Rank.Invalid_Permission", Type.ERROR),
	INVALID_RANK_PARENT("Errors.Rank.Invalid_Parent", Type.ERROR),
	RANK_PARENT_SAME("Errors.Rank.Parent_Same", Type.ERROR),

	// errors - teleportation
	LOCATION_NOT_FOUND("Errors.Teleportation.Location.Not_Found", Type.ERROR),
	WORLD_NOT_FOUND("Errors.Teleportation.World.Not_Found", Type.ERROR),

	// errors - bounty
	NO_BOUNTY("Errors.Bounty.No_Bounty", Type.ERROR),
	NO_USER_SET_BOUNTY("Errors.Bounty.User_Set_Bounty", Type.ERROR),

	// errors - waypoint
	INVALID_WAYPOINT("Errors.Waypoint.Invalid", Type.ERROR),
	NOT_SELECTED_WAYPOINT("Errors.Waypoint.Not_Selected", Type.ERROR),
	UNABLE_TELEPORT_WAYPOINT("Errors.Waypoint.Teleport_Issue", Type.ERROR),

	// errors - others
	CANNOT_BE_NULL("Errors.Cannot_Null", Type.ERROR),
	MUST_BE_NUMBERS("Errors.Must_Be_Numbers", Type.ERROR),
	DONT_USE_SYMBOL("Errors.Dont_Use_Symbol", Type.ERROR),

	// information - gang
	GANG_ALLIANCE_ALREADY_SENT("Information.Gang.Request_Already_Sent", Type.INFORMATION),
	ALREADY_ALLIED_GANG("Information.Gang.Already_Allied", Type.INFORMATION),
	KICKED_FROM_GANG("Information.Gang.Kicked", Type.INFORMATION),

	// kits
	KIT_RECEIVED("Kits.Received", Type.COMMAND),
	KIT_REMOVED("Kits.Removed", Type.COMMAND),
	KIT_INVALID("Kits.Invalid", Type.COMMAND),
	KIT_LIST_UNDEFINED("Kits.List_Undefined", Type.ERROR),

	// safe
	SAFE_CREATED("Safe.Created", Type.COMMAND),
	SAFE_REMOVED("Safe.Removed", Type.COMMAND),
	SAFE_ROBBED("Safe.Robbed", Type.PREFIX),
	SAFE_RESPAWNED("Safe.Respawned", Type.PREFIX),

	// wanted level
	WANTED("Wanted_Level.Wanted", Type.PREFIX),
	WANTED_INCREASED("Wanted_Level.Increased", Type.PREFIX),
	WANTED_DECREASED("Wanted_Level.Decreased", Type.PREFIX),
	NOT_WANTED("Wanted_Level.Not_Wanted", Type.PREFIX),
	WANTED_CLEARED("Wanted_Level.Cleared", Type.PREFIX),
	PAID_WANTED("Wanted_Level.Paid", Type.PREFIX),

	// weapons
	INVALID_AMMO("Weapons.Not_Valid_Ammo", Type.ERROR),
	INVALID_WEAPON("Weapons.Not_Valid_Weapon", Type.ERROR),
	INVALID_AMOUNT("Weapons.Not_Valid_Amount", Type.ERROR),
	KILLED_PLAYER("Weapons.Killed_Player", Type.PREFIX),
	GUN_NOT_IN_INVENTORY("Weapons.Gun_Not_In_Inventory", Type.PREFIX),
	GUN_BOUGHT("Weapons.Gun_Bought", Type.PREFIX),
	GUN_SOLD("Weapons.Gun_Sold", Type.PREFIX),
	AMMO_NOT_IN_INVENTORY("Weapons.Ammo_Not_In_Inventory", Type.PREFIX),
	AMMO_BOUGHT("Weapons.Ammo_Bought", Type.PREFIX),
	AMMO_SOLD("Weapons.Ammo_Sold", Type.PREFIX),
	NOT_ENOUGH_AMMO("Weapons.Not_Enough_Ammo", Type.PREFIX),

	// death
	DEAD_USING_WEAPON("Death.Weapon", Type.OTHER, true),

	// level
	LEVEL_STATS("Level.Stats", Type.OTHER, true),
	LEVEL_METER_BAR("Level.Meter.Bar", Type.NO_CHANGE),
	LEVEL_COMPLETE_COLOR("Level.Meter.Complete_Color", Type.NO_CHANGE),
	LEVEL_INCOMPLETE_COLOR("Level.Meter.Incomplete_Color", Type.NO_CHANGE),
	LEVEL_UP_PLAYER("Level.Level_Up.Player", Type.PREFIX),
	LEVEL_UP_GANG("Level.Level_Up.Gang", Type.PREFIX),

	// waypoint
	WAYPOINT_TELEPORT_COOLDOWN("Waypoint.Cooldown", Type.OTHER),
	WAYPOINT_TELEPORT_CANCELLED("Waypoint.Cancelled_Teleport", Type.OTHER),

	// signs
	SIGN_CREATED("Signs.Created", Type.COMMAND),
	SIGN_CREATION_FAILED("Signs.Creation_Failed", Type.ERROR),
	SIGN_INVALID("Signs.Invalid", Type.ERROR),
	SIGN_BULK_CONFIRM_EXPIRED("Signs.Bulk.Confirm_Expired", Type.ERROR),
	SIGN_BULK_CONFIRM_REQUEST("Signs.Bulk.Confirm_Request", Type.COMMAND),
	SIGN_BULK_EXPIRED("Signs.Bulk.Expired", Type.ERROR),
	SIGN_BULK_CANCELLED("Signs.Bulk.Cancelled", Type.ERROR),

	// loot chests - player messages
	LOOT_CHEST_CRACKING_STARTED("Loot_Chest.Cracking_Started", Type.OTHER),
	LOOT_CHEST_ALREADY_IN_SESSION("Loot_Chest.Already_In_Session", Type.ERROR),
	LOOT_CHEST_ON_COOLDOWN("Loot_Chest.On_Cooldown", Type.ERROR),
	LOOT_CHEST_REQUIRES_LOCKPICK("Loot_Chest.Requires_Lockpick", Type.ERROR),
	LOOT_CHEST_REQUIRES_KEY("Loot_Chest.Requires_Key", Type.ERROR),
	LOOT_CHEST_NO_PERMISSION("Loot_Chest.No_Permission", Type.ERROR),
	LOOT_CHEST_INVALID_LOOT_TABLE("Loot_Chest.Invalid_Loot_Table", Type.ERROR),
	LOOT_CHEST_INVALID_CHEST("Loot_Chest.Invalid_Chest", Type.ERROR),
	LOOT_CHEST_NO_ITEM_PROVIDER("Loot_Chest.No_Item_Provider", Type.ERROR),
	LOOT_CHEST_ALREADY_LOOTED("Loot_Chest.Already_Looted", Type.ERROR),

	// loot chests - hologram text
	LOOT_CHEST_HOLOGRAM_COOLDOWN("Loot_Chest.Hologram.Cooldown_Status", Type.OTHER),
	LOOT_CHEST_HOLOGRAM_AVAILABLE("Loot_Chest.Hologram.Available_Status", Type.OTHER),
	LOOT_CHEST_HOLOGRAM_HINT("Loot_Chest.Hologram.Available_Hint", Type.OTHER),
	LOOT_CHEST_HOLOGRAM_LOCKED_REQUIRES("Loot_Chest.Hologram.Locked_Requires", Type.OTHER),
	LOOT_CHEST_HOLOGRAM_LOCKED_PERMISSION("Loot_Chest.Hologram.Locked_Permission", Type.OTHER),
	LOOT_CHEST_HOLOGRAM_UNLOCKED("Loot_Chest.Hologram.Unlocked", Type.OTHER),

	// loot chests - time units for cooldown countdown
	LOOT_CHEST_TIME_YEAR("Loot_Chest.Time_Units.Year", Type.NO_CHANGE),
	LOOT_CHEST_TIME_WEEK("Loot_Chest.Time_Units.Week", Type.NO_CHANGE),
	LOOT_CHEST_TIME_DAY("Loot_Chest.Time_Units.Day", Type.NO_CHANGE),
	LOOT_CHEST_TIME_HOUR("Loot_Chest.Time_Units.Hour", Type.NO_CHANGE),
	LOOT_CHEST_TIME_MINUTE("Loot_Chest.Time_Units.Minute", Type.NO_CHANGE),
	LOOT_CHEST_TIME_SECOND("Loot_Chest.Time_Units.Second", Type.NO_CHANGE),

	// shop framework (reusable across any shop integration)
	SHOP_PURCHASE_SUCCESS("Commands.Shop.Purchase.Success", Type.COMMAND),
	SHOP_PURCHASE_STACK_SUCCESS("Commands.Shop.Purchase.Stack_Success", Type.COMMAND),
	SHOP_PURCHASE_INSUFFICIENT_FUNDS("Errors.Shop.Purchase.Insufficient_Funds", Type.ERROR),
	SHOP_PURCHASE_ECONOMY_ERROR("Errors.Shop.Purchase.Economy_Error", Type.ERROR),
	SHOP_PURCHASE_INVENTORY_FULL("Errors.Shop.Purchase.Inventory_Full", Type.ERROR),
	SHOP_BARTER_SUCCESS("Commands.Shop.Barter.Success", Type.COMMAND),
	SHOP_BARTER_MISSING_ITEMS("Errors.Shop.Barter.Missing_Items", Type.ERROR),
	SHOP_BARTER_INSUFFICIENT_VALUE("Errors.Shop.Barter.Insufficient_Value", Type.ERROR),
	SHOP_BARTER_NOT_ALLOWED("Errors.Shop.Barter.Not_Allowed", Type.ERROR),
	SHOP_NOT_DEFINED("Errors.Shop.Not_Defined", Type.ERROR),
	SHOP_SAVED("Commands.Shop.Saved", Type.COMMAND),
	SHOP_ADMIN_ENTRY_ADDED("Commands.Shop.Admin.Entry_Added", Type.COMMAND),
	SHOP_ADMIN_ENTRY_REMOVED("Commands.Shop.Admin.Entry_Removed", Type.COMMAND),
	SHOP_ADMIN_CATEGORY_CREATED("Commands.Shop.Admin.Category_Created", Type.COMMAND),
	SHOP_ADMIN_CATEGORY_REMOVED("Commands.Shop.Admin.Category_Removed", Type.COMMAND),
	SHOP_SELL_SUCCESS("Commands.Shop.Sell.Success", Type.COMMAND),
	SHOP_SELL_NOTHING_VALUED("Errors.Shop.Sell.Nothing_Valued", Type.ERROR),
	SHOP_SELL_ECONOMY_ERROR("Errors.Shop.Sell.Economy_Error", Type.ERROR),

	// trader (trader NPC feature)
	TRADER_TIP_SUCCESS("Commands.Trader.Tip.Success", Type.COMMAND),
	TRADER_TIP_INSUFFICIENT_FUNDS("Errors.Trader.Tip.Insufficient_Funds", Type.ERROR),
	TRADER_TRAIT_INVALID("Errors.Trader.Trait.Invalid", Type.ERROR),
	TRADER_SHOP_MISSING("Errors.Trader.Shop.Missing", Type.ERROR),
	TRADER_TRAIT_MISSING("Errors.Trader.Trait.Missing", Type.ERROR),

	// detainment (cops-n-crooks cuff → jail → release flow)
	DETAINMENT_HANDCUFFED_TITLE("Cops_N_Crooks.Detainment.Handcuffed.Title", Type.OTHER),
	DETAINMENT_HANDCUFFED_SUBTITLE("Cops_N_Crooks.Detainment.Handcuffed.Subtitle", Type.OTHER),
	DETAINMENT_HANDCUFFED_ACTION_BAR("Cops_N_Crooks.Detainment.Handcuffed.Action_Bar", Type.OTHER),
	DETAINMENT_JAILED_TITLE("Cops_N_Crooks.Detainment.Jailed.Title", Type.OTHER),
	DETAINMENT_JAILED_SUBTITLE("Cops_N_Crooks.Detainment.Jailed.Subtitle", Type.OTHER),
	DETAINMENT_JAILED_ACTION_BAR("Cops_N_Crooks.Detainment.Jailed.Action_Bar", Type.OTHER),
	DETAINMENT_RELEASED_TITLE("Cops_N_Crooks.Detainment.Released.Title", Type.OTHER),
	DETAINMENT_RELEASED_SUBTITLE("Cops_N_Crooks.Detainment.Released.Subtitle", Type.OTHER),
	DETAINMENT_CUFFING_TITLE("Cops_N_Crooks.Detainment.Cuffing.Title", Type.OTHER),
	DETAINMENT_CUFFING_SUBTITLE("Cops_N_Crooks.Detainment.Cuffing.Subtitle", Type.OTHER),
	DETAINMENT_CUFFED_TITLE("Cops_N_Crooks.Detainment.Cuffed.Title", Type.OTHER),
	DETAINMENT_CUFFED_SUBTITLE("Cops_N_Crooks.Detainment.Cuffed.Subtitle", Type.OTHER),
	DETAINMENT_HANDCUFFED_TICK("Cops_N_Crooks.Detainment.Restraint_Tick.Handcuffed", Type.OTHER),
	DETAINMENT_JAILED_TICK("Cops_N_Crooks.Detainment.Restraint_Tick.Jailed", Type.OTHER),

	// gadgets - car
	CAR_NO_PERMISSION("Gadgets.Car.No_Permission", Type.ERROR),
	CAR_ALREADY_DRIVING("Gadgets.Car.Already_Driving", Type.ERROR),
	CAR_FUEL_CAN_EMPTY("Gadgets.Car.Refuel.Fuel_Can_Empty", Type.OTHER),
	CAR_FUEL_TANK_FULL("Gadgets.Car.Refuel.Tank_Full", Type.OTHER),
	CAR_REFUEL_FAILED("Gadgets.Car.Refuel.Failed", Type.OTHER),

	// death (respawn prompt)
	DEATH_RESPAWN_WASTED_PREFIX("Death.Respawn.Wasted_Prefix", Type.OTHER),
	DEATH_RESPAWN_BUTTON("Death.Respawn.Click_Button", Type.OTHER),

	// commands - update
	UPDATE_AVAILABLE("Commands.Update.Available", Type.COMMAND),
	UPDATE_LATEST("Commands.Update.Latest", Type.COMMAND),
	UPDATE_ALREADY_LATEST("Commands.Update.Already_Latest", Type.COMMAND),
	UPDATE_DOWNLOADING("Commands.Update.Downloading", Type.COMMAND),
	UPDATE_DOWNLOAD_SUCCESS("Commands.Update.Success", Type.COMMAND),
	UPDATE_DOWNLOAD_FAILED("Commands.Update.Failed", Type.COMMAND),

	// commands - fuel
	FUEL_CAPACITY_INCREASED("Commands.Fuel.Capacity_Increased", Type.COMMAND),
	FUEL_CAPACITY_DECREASED("Commands.Fuel.Capacity_Decreased", Type.COMMAND),
	FUEL_REFUELED_FULL("Commands.Fuel.Refueled_Full", Type.COMMAND),
	FUEL_REFUELED_AMOUNT("Commands.Fuel.Refueled_Amount", Type.COMMAND),
	FUEL_DEFUELED_FULL("Commands.Fuel.Defueled_Full", Type.COMMAND),
	FUEL_DEFUELED_AMOUNT("Commands.Fuel.Defueled_Amount", Type.COMMAND),
	FUEL_NO_CAPACITY("Fuel.No_Capacity", Type.PREFIX),
	FUEL_AMOUNT_INVALID("Fuel.Amount_Invalid", Type.PREFIX),

	// commands - cuff
	CUFF_HANDCUFFED("Commands.Cuff.Handcuffed", Type.COMMAND),
	CUFF_RELEASED("Commands.Cuff.Released", Type.COMMAND),
	CUFF_ALREADY_CUFFED("Errors.Cuff.Already_Cuffed", Type.ERROR),
	CUFF_NOT_CUFFED("Errors.Cuff.Not_Cuffed", Type.ERROR),

	// commands - jail
	JAIL_THROWN("Commands.Jail.Thrown", Type.COMMAND),
	JAIL_CREATED("Commands.Jail.Created", Type.COMMAND),
	JAIL_REMOVED("Commands.Jail.Removed", Type.COMMAND),
	JAIL_RELEASED("Commands.Jail.Released", Type.COMMAND),
	JAIL_TELEPORTED("Commands.Jail.Teleported", Type.COMMAND),
	JAIL_LIST_HEADER("Jail.List_Header", Type.PREFIX),
	JAIL_NO_EMPTY("Errors.Jail.No_Empty", Type.ERROR),
	JAIL_ALREADY_JAILED("Errors.Jail.Already_Jailed", Type.ERROR),
	JAIL_NOT_JAILED("Errors.Jail.Not_Jailed", Type.ERROR),
	JAIL_EXISTS_NEARBY("Errors.Jail.Exists_Nearby", Type.ERROR),

	// commands - cops
	COP_LIST_NONE("Commands.Cop.No_Chased", Type.COMMAND),
	COP_TARGET_NOT_CHASED("Commands.Cop.Target_Not_Chased", Type.COMMAND),
	COP_SPAWNER_SET("Commands.Cop.Spawner.Set", Type.COMMAND),
	COP_SPAWNER_REMOVED("Commands.Cop.Spawner.Removed", Type.COMMAND),
	COP_SPAWNER_TELEPORTED("Commands.Cop.Spawner.Teleported", Type.COMMAND),
	COP_SPAWNER_LIST_HEADER("Cop.Spawner_List_Header", Type.PREFIX),

	// commands - car
	CAR_GAVE("Commands.Car.Gave", Type.COMMAND),
	CAR_LIST_HEADER("Commands.Car.List_Header", Type.COMMAND),
	CAR_INVALID("Gadgets.Car.Invalid", Type.PREFIX),
	CAR_NOT_A_CAR("Gadgets.Car.Not_A_Car", Type.PREFIX),
	CAR_NOT_REGISTERED("Gadgets.Car.Not_Registered", Type.PREFIX),

	// commands - civilians
	CIVILIAN_LIST_EMPTY("Commands.Civilian.List_Empty", Type.COMMAND),
	CIVILIAN_GROUPS_EMPTY("Commands.Civilian.Groups_Empty", Type.COMMAND),
	CIVILIAN_SPAWNED("Commands.Civilian.Spawned", Type.COMMAND),
	CIVILIAN_GROUP_SPAWNED("Commands.Civilian.Group_Spawned", Type.COMMAND),
	CIVILIAN_GROUP_UNKNOWN("Commands.Civilian.Group_Unknown", Type.COMMAND),
	CIVILIAN_TYPE_UNKNOWN("Commands.Civilian.Type_Unknown", Type.COMMAND),
	CIVILIAN_SPAWNER_REMOVED("Commands.Civilian.Spawner_Removed", Type.COMMAND),
	CIVILIAN_SPAWNER_TELEPORTED("Commands.Civilian.Spawner_Teleported", Type.COMMAND),
	CIVILIAN_SPAWN_FAILED("Commands.Civilian.Spawn_Failed", Type.COMMAND),
	CIVILIAN_SPAWNER_TYPE_SET("Commands.Civilian.Spawner_Type_Set", Type.COMMAND),
	CIVILIAN_SPAWNER_GROUP_SET("Commands.Civilian.Spawner_Group_Set", Type.COMMAND),
	CIVILIAN_SPAWNER_LIST_HEADER("Civilian.Spawner_List_Header", Type.PREFIX),

	// commands - shop (admin)
	SHOP_REMOVED("Commands.Shop.Removed", Type.COMMAND),
	SHOP_LIST_EMPTY("Commands.Shop.List_Empty", Type.COMMAND),
	SHOP_CREATED("Commands.Shop.Created", Type.COMMAND),
	SHOP_TITLE_SET("Commands.Shop.Title_Set", Type.COMMAND),
	SHOP_ALREADY_EXISTS("Errors.Shop.Already_Exists", Type.ERROR),
	SHOP_CREATE_FAILED("Errors.Shop.Create_Failed", Type.ERROR),
	SHOP_REMOVE_UNTRACKED("Errors.Shop.Untracked", Type.ERROR),
	SHOP_REMOVE_FAILED("Errors.Shop.Remove_Failed", Type.ERROR),
	SHOP_ONLY_PLAYERS("Errors.Shop.Only_Players", Type.ERROR),
	SHOP_TITLE_EMPTY("Errors.Shop.Title_Empty", Type.ERROR),
	SHOP_KEY_INVALID("Errors.Shop.Key_Invalid", Type.ERROR),

	// commands - trader (admin)
	TRADER_REMOVED("Commands.Trader.Removed", Type.COMMAND),
	TRADER_RENAMED("Commands.Trader.Renamed", Type.COMMAND),
	TRADER_SHOP_CHANGED("Commands.Trader.Shop_Changed", Type.COMMAND),
	TRADER_TRAIT_CHANGED("Commands.Trader.Trait_Changed", Type.COMMAND),
	TRADER_NAME_EMPTY("Errors.Trader.Name_Empty", Type.ERROR),
	TRADER_LOOK_AT("Errors.Trader.Look_At", Type.ERROR),
	TRADER_NOT_A_TRADER("Errors.Trader.Not_Trader", Type.ERROR),

	// commands - items (unique / wearable)
	ITEM_UNIQUE_GAVE("Commands.Item.Unique.Gave", Type.COMMAND),
	ITEM_UNIQUE_LIST_HEADER("Commands.Item.Unique.List_Header", Type.COMMAND),
	ITEM_UNIQUE_INVALID("Item.Unique.Invalid", Type.PREFIX),
	ITEM_UNIQUE_NOT_REGISTERED("Item.Unique.Not_Registered", Type.PREFIX),
	ITEM_UNIQUE_NOT_UNIQUE("Item.Unique.Not_Unique", Type.PREFIX),
	ITEM_WEARABLE_GAVE("Commands.Item.Wearable.Gave", Type.COMMAND),
	ITEM_WEARABLE_LIST_HEADER("Commands.Item.Wearable.List_Header", Type.COMMAND),
	ITEM_WEARABLE_INVALID("Item.Wearable.Invalid", Type.PREFIX),
	ITEM_WEARABLE_NOT_REGISTERED("Item.Wearable.Not_Registered", Type.PREFIX),
	ITEM_WEARABLE_NOT_WEARABLE("Item.Wearable.Not_Wearable", Type.PREFIX),

	// commands - weapons (list headers)
	WEAPON_LIST_HEADER("Commands.Weapons.Weapon.List_Header", Type.COMMAND),
	AMMO_LIST_HEADER("Commands.Weapons.Ammo.List_Header", Type.COMMAND),

	// commands - waypoint (extras)
	WAYPOINT_LIST_HEADER("Waypoint.List_Header", Type.PREFIX),
	WAYPOINT_DELETED("Commands.Waypoint.Deleted", Type.COMMAND),
	WAYPOINT_TYPE_INVALID_HEADER("Errors.Waypoint.Invalid_Type_Header", Type.ERROR),

	// commands - loot chest (admin)
	LOOT_CHEST_MUST_LOOK_AT_BLOCK("Errors.Loot_Chest.Must_Look_At_Block", Type.ERROR),
	LOOT_CHEST_NO_CHEST_AT_LOCATION("Errors.Loot_Chest.No_Chest_At_Location", Type.ERROR),
	LOOT_CHEST_REQUIRES_WAND("Errors.Loot_Chest.Requires_Wand", Type.ERROR),
	LOOT_CHEST_REMOVED("Commands.Loot_Chest.Removed", Type.COMMAND),

	// commands - wanted / balance
	WANTED_STATUS_HEADER("Commands.Wanted.Status_Header", Type.COMMAND),
	BALANCE_REGISTERED_ONLY("Information.Balance.Registered_Only", Type.INFORMATION),

	// errors - argument framework
	ARGUMENT_NOT_IMPLEMENTED("Errors.Argument.Not_Implemented", Type.ERROR),
	ARGUMENT_CONFIRM_REQUIRED("Errors.Argument.Confirm_Required", Type.ERROR),
	ARGUMENT_CONFIRM_HINT("Information.Argument.Confirm_Hint", Type.INFORMATION),
	;

	private static MessageProvider provider;

	private final String path;
	private final Type   type;

	private boolean isList;

	Messages(String path, Type type) {
		this.path   = path;
		this.type   = type;
		this.isList = false;
	}

	Messages(String path, Type type, boolean isList) {
		this(path, type);
		this.isList = isList;
	}

	/**
	 * Single static seam wired once at startup by {@code LanguageLoader} / {@code FileConfig.languageLoader(...)}. The
	 * provider is a proper bean, so other code paths that want typed access can inject {@link MessageProvider} directly
	 * instead of going through the enum.
	 */
	public static void init(MessageProvider messageProvider) {
		provider = messageProvider;
	}

	/**
	 * Walks every declared enum entry and returns the paths that are absent from {@code yaml}. Call after loading the
	 * language file so the admin sees exactly which keys the plugin expected but didn't find (e.g. a typo that renamed
	 * {@code Normal.Prefix} to {@code Normal.prefix}).
	 */
	public static List<String> findMissingPaths(YamlConfiguration yaml) {
		List<String> missing = new ArrayList<>();

		for (Messages entry : values()) {
			if (!yaml.contains(entry.path)) missing.add(entry.path);
		}

		return missing;
	}

	@Override
	public String toString() {
		return toString(type);
	}

	public String toString(Type type) {
		String data;

		if (isList) data = convertFromList(provider.getStringList(path));
		else data = provider.getString(path);

		if (data == null) return "<missing: " + path + ">";

		return getValue(type, data);
	}

	public List<String> toStringList() {
		List<String> list = provider.getStringList(path);

		list.replaceAll(data -> getValue(type, data));

		return list;
	}

	private String getValue(Type type, String data) {
		String value;

		switch (type) {
			case PREFIX -> value = GanglandChatUtil.prefixMessage(data);
			case COMMAND -> value = GanglandChatUtil.commandMessage(data);
			case ERROR -> value = GanglandChatUtil.errorMessage(data);
			case INFORMATION -> value = GanglandChatUtil.informationMessage(data);
			case OTHER -> value = GanglandChatUtil.color(data);
			default -> value = data;
		}

		return value;
	}

	private String convertFromList(List<String> data) {
		StringBuilder builder = new StringBuilder();

		for (int i = 0; i < data.size(); i++) {
			builder.append(data.get(i));

			if (i < data.size() - 1) builder.append("\n");
		}

		return builder.toString();
	}

	public enum Type {

		PREFIX,
		COMMAND,
		ERROR,
		INFORMATION,
		OTHER,
		NO_CHANGE

	}

}
