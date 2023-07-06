package me.luckyraven.file.configuration;

import me.luckyraven.Gangland;
import me.luckyraven.file.FileManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Objects;

import static me.luckyraven.util.ChatUtil.*;

public class MessageAddon {

	private static YamlConfiguration message;
	private static FileConfiguration setting;

	public static String PREFIX, ENTITY_DROP_MONEY, ENTITY_DROP_MONEY_ACTION;

	// Format Message
	public static String DATE, TIME;

	// Time Message
	public static String SECOND, MINUTE, HOUR, DAY, WEEK, MONTH, YEAR;

	// Information Message
	public static String INFORMATION_PREFIX, DROPS_CLEARED, DROPS_CLEAR_TIME, UUIDC, UUIDC_TARGET;

	// Command Message
	public static String COMMAND_PREFIX;

	// Syntax
	public static String COMMAND_NOTEXISTS, ARGUMENTS_MISSING, ARGUMENTS_TOO_MUCH, ARGUMENTS_WRONG, PAGE_INVALID;

	// Error Message
	public static String ERROR_PREFIX;

	// Permission Message
	public static String NOPERM_CMD, NOPERM_KIT, NOPERM_SPAWN, NOPERM_WARP, NOPERM_SIGN, NOPERM_OTHER, NOT_PLAYER, NOT_ONLINE, INVALID_PLAYER, CANNOT_NULL, MUSTBE_NUMBER, INV_FULL, CORRECT_SPAWN, CANNOT_EXCEED_MAX, CANNOT_TAKE_LESSTHANZERO, CANNOT_TAKE_MORETHANBALANCE, CANNOT_SET_SAMEBALANCE, CANNOT_CREATE_BANK, CANNOT_CREATE_GANG, MUST_CREATE_BANK, MUST_CREATE_GANG, DONT_USE_SYMBOL, CANNOT_REMOVE_NONEXIST_SPAWN, CANNOT_REMOVE_NONEXIST_WARP, CANNOT_REMOVE_NONEXIST_KIT, CANNOT_FIND_WORLD;
	public static String PLAYER_BALANCE, TARGET_BALANCE, PLAYER_MONEY_ADD, PLAYER_MONEY_TAKE, PLAYER_MONEY_SET, PLAYER_MONEY_RESET, TARGET_MONEY_ADD, TARGET_MONEY_TAKE, TARGET_MONEY_SET, TARGET_MONEY_RESET;

	// Bank Message
	public static String BANK_PLAYER_CREATE, BANK_CREATE_FEE, BANK_PLAYER_REMOVE, CREATED_BANK, REMOVED_BANK, HAVE_BANK;

	// Bank Balance Message
	public static String BANK_PLAYER_BALANCE, BANK_TARGET_BALANCE;

	// Bank Money Message
	public static String BANK_PLAYER_MONEY_ADD, BANK_PLAYER_MONEY_TAKE, BANK_PLAYER_MONEY_SET, BANK_PLAYER_MONEY_RESET, BANK_TARGET_MONEY_ADD, BANK_TARGET_MONEY_TAKE, BANK_TARGET_MONEY_SET, BANK_TARGET_MONEY_RESET;

	// Gang Message
	public static String GANG_PLAYER_CREATE, GANG_PLAYER_REMOVE, CREATED_GANG, REMOVED_GANG;

	// Gang Balance Message
	public static String GANG_PLAYER_BALANCE, GANG_TARGET_BALANCE;

	// Gang Money Message
	public static String GANG_PLAYER_MONEY_ADD, GANG_PLAYER_MONEY_TAKE, GANG_TARGET_MONEY_ADD, GANG_TARGET_MONEY_TAKE;

	// Spawn Message
	public static String SPAWN_SET, SPAWN_NOTSET, SPAWN_REMOVED, PLAYER_SENDTO_SPAWN, PLAYER_SENDTO_SPAWN_COOLDOWN, NEED_DEFAULT_SPAWN, TARGET_SENDTO_SPAWN, TARGET_SENDTO_SPAWN_COOLDOWN, SPAWN_LIST, SPAWN_LIST_UNDEFINED;

	// Warp Message
	public static String WARP_SET, WARP_NOTSET, WARP_REMOVED, PLAYER_SENDTO_WARP, PLAYER_SENDTO_WARP_COOLDOWN, TARGET_SENDTO_WARP, TARGET_SENDTO_WARP_COOLDOWN, WARP_LIST_UNDEFINED;

	// Kits Message
	public static String KIT_RECEIVED, KIT_INVAILD, KIT_REMOVED, KIT_LIST_UNDEFINED;

	// Safe Message
	public static String SAFE_CREATED, SAFE_REMOVED, SAFE_ROBBED, SAFE_ROBBED_ACTION, SAFE_RESPAWNED, ERROR_START, ERROR_END, CONTACT, NO_CONTACT, CANNOT_CHECK_UPDATE;

	// Wanted Message
	public static String NOT_WANTED, PAID_WANTEDLEVEL;

	// Weapons Command Message
	public static String NOT_VALID_AMMO, NOT_VALID_WEAPON, NOT_VALID_AMOUNT, GIVE_AMMO, GIVE_WEAPON, KILLED_PLAYER_DEATH_MESSAGE, GUN_NOT_INV, GUN_BOUGHT, GUN_SOLD, AMMO_NOT_INV, AMMO_BOUGHT, AMMO_SOLD, NOT_ENOUGH_AMMO;

	public MessageAddon(Gangland gangland, FileManager fileManager) {
		MessageAddon.setting = fileManager.getFile("settings").getFileConfiguration();
		MessageAddon.message = gangland.getInitializer().getLanguageLoader().getMessage();
		initialize();
	}

	public void initialize() {
		String moneySymbol = Objects.requireNonNull(setting.getString("Money_Symbol"));

		String normalMessage = "Normal.";
		PREFIX = color(message.getString(normalMessage + "Prefix"));
		ENTITY_DROP_MONEY = prefixMessage(message.getString(normalMessage + "Entity_Drop_Money")).replace(
				"%money_symbol%", moneySymbol);
//		ENTITY_DROP_MONEY_ACTION = color(settingsFile.getString("Killing_Mob.ActionBar_Message")));

		String formatMessage = normalMessage + "Format.";
		DATE = color(message.getString(formatMessage + "Date"));
		TIME = color(message.getString(formatMessage + "Time"));

		String timeMessage = "Time_Unit.";
		SECOND = color(message.getString(timeMessage + "Second"));
		MINUTE = color(message.getString(timeMessage + "Minute"));
		HOUR = color(message.getString(timeMessage + "Hour"));
		DAY = color(message.getString(timeMessage + "Day"));
		WEEK = color(message.getString(timeMessage + "Week"));
		MONTH = color(message.getString(timeMessage + "Month"));
		YEAR = color(message.getString(timeMessage + "Year"));

		String informationMessageMessage = "Information.";
		INFORMATION_PREFIX = color(message.getString(informationMessageMessage + "Prefix"));
		DROPS_CLEARED = informationMessage(message.getString(informationMessageMessage + "Drops_Cleared"));
		DROPS_CLEAR_TIME = informationMessage(message.getString(informationMessageMessage + "Drops_Time"));
		UUIDC = informationMessage(message.getString(informationMessageMessage + "Uuid"));
		UUIDC_TARGET = informationMessage(message.getString(informationMessageMessage + "Uuid_Target"));

		String commandMessage = "Commands.";
		COMMAND_PREFIX = color(message.getString(commandMessage + "Prefix"));

		String weaponsCommandMessage = commandMessage + "Weapons.";
		GIVE_AMMO = commandMessage(message.getString(weaponsCommandMessage + "Gave_Ammo"));
		GIVE_WEAPON = commandMessage(message.getString(weaponsCommandMessage + "Gave_Weapon"));

		String syntax = commandMessage + "Syntax.";
		COMMAND_NOTEXISTS = commandMessage(message.getString(syntax + "Doesnt_Exist"));
		ARGUMENTS_MISSING = commandMessage(message.getString(syntax + "Missing_Arguments"));
		ARGUMENTS_TOO_MUCH = commandMessage(message.getString(syntax + "Too_Much_Arguments"));
		ARGUMENTS_WRONG = commandMessage(message.getString(syntax + "Wrong_Arguments"));
		PAGE_INVALID = commandMessage(message.getString(syntax + "Page_Invalid"));

		String errorMessage = "Errors.";
		ERROR_PREFIX = color(Objects.requireNonNull(message.getString(errorMessage + "Prefix")));

		String permMessage = errorMessage + "Permissions.";
		NOPERM_CMD = errorMessage(message.getString(permMessage + "Command"));
		NOPERM_KIT = errorMessage(message.getString(permMessage + "Kit"));
		NOPERM_SPAWN = errorMessage(message.getString(permMessage + "Spawn"));
		NOPERM_WARP = errorMessage(message.getString(permMessage + "Warp"));
		NOPERM_SIGN = errorMessage(message.getString(permMessage + "Sign"));
		NOPERM_OTHER = errorMessage(message.getString(permMessage + "Other"));
		NOT_PLAYER = errorMessage(message.getString(errorMessage + "Need_Player"));
		NOT_ONLINE = errorMessage(message.getString(errorMessage + "Player_Not_Online"));
		INVALID_PLAYER = errorMessage(message.getString(errorMessage + "Invalid_Player"));
		CANNOT_NULL = errorMessage(message.getString(errorMessage + "Cannot_Null"));
		MUSTBE_NUMBER = errorMessage(message.getString(errorMessage + "Must_Be_Numbers"));
		INV_FULL = errorMessage(message.getString(errorMessage + "Inv_Full"));
		CORRECT_SPAWN = errorMessage(message.getString(errorMessage + "Correct_Spawn"));
		CANNOT_EXCEED_MAX = errorMessage(message.getString(errorMessage + "Cannot_Exceed_Max"));
		CANNOT_TAKE_LESSTHANZERO = errorMessage(message.getString(errorMessage + "Cannot_Take_Less_Than_Zero"));
		CANNOT_TAKE_MORETHANBALANCE = errorMessage(message.getString(errorMessage + "Cannot_Take_More_Than_Balance"));
		CANNOT_SET_SAMEBALANCE = errorMessage(message.getString(errorMessage + "Cannot_Set_Same_Balance"));
		CANNOT_CREATE_BANK = errorMessage(message.getString(errorMessage + "Cannot_Create_Bank"));
		CANNOT_CREATE_GANG = errorMessage(message.getString(errorMessage + "Cannot_Create_Gang"));
		MUST_CREATE_BANK = errorMessage(message.getString(errorMessage + "Must_Create_Bank"));
		MUST_CREATE_GANG = errorMessage(message.getString(errorMessage + "Must_Create_Gang"));
		DONT_USE_SYMBOL = errorMessage(message.getString(errorMessage + "Dont_Use_Symbol"));
		CANNOT_REMOVE_NONEXIST_SPAWN = errorMessage(
				message.getString(errorMessage + "Cannot_Remove_Non_Existing_Spawn"));
		CANNOT_REMOVE_NONEXIST_WARP = errorMessage(message.getString(errorMessage + "Cannot_Remove_Non_Existing_Warp"));
		CANNOT_REMOVE_NONEXIST_KIT = errorMessage(message.getString(errorMessage + "Cannot_Remove_Non_Existing_Kit"));
		CANNOT_FIND_WORLD = errorMessage(message.getString(errorMessage + "Cannot_Find_World"));

		String economyMessage = commandMessage + "Economy.";
		String balanceMessage = economyMessage + "Balance.";
		String moneyMessage   = economyMessage + "Money.";

		PLAYER_BALANCE = commandMessage(message.getString(balanceMessage + "Player_Balance")).replace("%money_symbol%",
		                                                                                              moneySymbol);
		TARGET_BALANCE = commandMessage(message.getString(balanceMessage + "Target_Balance")).replace("%money_symbol%",
		                                                                                              moneySymbol);
		PLAYER_MONEY_ADD = commandMessage(message.getString(moneyMessage + "Player_Money_Add")).replace(
				"%money_symbol%", moneySymbol);
		PLAYER_MONEY_TAKE = commandMessage(message.getString(moneyMessage + "Player_Money_Take")).replace(
				"%money_symbol%", moneySymbol);
		PLAYER_MONEY_SET = commandMessage(message.getString(moneyMessage + "Player_Money_Set")).replace(
				"%money_symbol%", moneySymbol);
		PLAYER_MONEY_RESET = commandMessage(message.getString(moneyMessage + "Player_Money_Reset"));
		TARGET_MONEY_ADD = commandMessage(message.getString(moneyMessage + "Target_Money_Add")).replace(
				"%money_symbol%", moneySymbol);
		TARGET_MONEY_TAKE = commandMessage(message.getString(moneyMessage + "Target_Money_Take")).replace(
				"%money_symbol%", moneySymbol);
		TARGET_MONEY_SET = commandMessage(message.getString(moneyMessage + "Target_Money_Set")).replace(
				"%money_symbol%", moneySymbol);
		TARGET_MONEY_RESET = commandMessage(message.getString(moneyMessage + "Target_Money_Reset")).replace(
				"%money_symbol%", moneySymbol);

		String bankMessage = commandMessage + "Bank.";
		BANK_PLAYER_CREATE = commandMessage(message.getString(bankMessage + "Player_Create_Bank"));
		BANK_CREATE_FEE = commandMessage(message.getString(bankMessage + "Create_Bank_Fee")).replace("%money_symbol%",
		                                                                                             moneySymbol)
		                                                                                    .replace("%amount%",
		                                                                                             String.valueOf(
				                                                                                             formatDouble(
						                                                                                             setting.getDouble(
								                                                                                             "Bank_Account.Create_Cost"))));
		BANK_PLAYER_REMOVE = commandMessage(message.getString(bankMessage + "Player_Remove_Bank"));
		CREATED_BANK = commandMessage(message.getString(bankMessage + "Created_Bank"));
		REMOVED_BANK = commandMessage(message.getString(bankMessage + "Removed_Bank"));
		HAVE_BANK = commandMessage(message.getString(bankMessage + "Have_Bank"));

		String bankBalanceMessage = bankMessage + "Balance.";
		BANK_PLAYER_BALANCE = commandMessage(message.getString(bankBalanceMessage + "Player_Balance")).replace(
				"%money_symbol%", moneySymbol);
		BANK_TARGET_BALANCE = commandMessage(message.getString(bankBalanceMessage + "Target_Balance")).replace(
				"%money_symbol%", moneySymbol);

		String bankMoneyMessage = bankMessage + "Money.";
		BANK_PLAYER_MONEY_ADD = commandMessage(message.getString(bankMoneyMessage + "Player_Money_Add")).replace(
				"%money_symbol%", moneySymbol);
		BANK_PLAYER_MONEY_TAKE = commandMessage(message.getString(bankMoneyMessage + "Player_Money_Take")).replace(
				"%money_symbol%", moneySymbol);
		BANK_PLAYER_MONEY_SET = commandMessage(message.getString(bankMoneyMessage + "Player_Money_Set")).replace(
				"%money_symbol%", moneySymbol);
		BANK_PLAYER_MONEY_RESET = commandMessage(message.getString(bankMoneyMessage + "Player_Money_Reset"));
		BANK_TARGET_MONEY_ADD = commandMessage(message.getString(bankMoneyMessage + "Target_Money_Add")).replace(
				"%money_symbol%", moneySymbol);
		BANK_TARGET_MONEY_TAKE = commandMessage(message.getString(bankMoneyMessage + "Target_Money_Take")).replace(
				"%money_symbol%", moneySymbol);
		BANK_TARGET_MONEY_SET = commandMessage(message.getString(bankMoneyMessage + "Target_Money_Set")).replace(
				"%money_symbol%", moneySymbol);
		BANK_TARGET_MONEY_RESET = commandMessage(message.getString(bankMoneyMessage + "Target_Money_Reset")).replace(
				"%money_symbol%", moneySymbol);

		String gangMessage = commandMessage + "Gang.";
		GANG_PLAYER_CREATE = commandMessage(message.getString(gangMessage + "Player_Create_Gang"));
		GANG_PLAYER_REMOVE = commandMessage(message.getString(gangMessage + "Player_Remove_Gang"));
		CREATED_GANG = commandMessage(message.getString(gangMessage + "Created_Gang"));
		REMOVED_GANG = commandMessage(message.getString(gangMessage + "Removed_Gang"));

		String gangBalanceMessage = gangMessage + "Balance.";
		GANG_PLAYER_BALANCE = commandMessage(message.getString(gangBalanceMessage + "Player_Balance")).replace(
				"%money_symbol%", moneySymbol);
		GANG_TARGET_BALANCE = commandMessage(message.getString(gangBalanceMessage + "Target_Balance")).replace(
				"%money_symbol%", moneySymbol);

		String gangMoneyMessage = gangMessage + "Money.";
		GANG_PLAYER_MONEY_ADD = commandMessage(message.getString(gangMoneyMessage + "Player_Money_Add")).replace(
				"%money_symbol%", moneySymbol);
		GANG_PLAYER_MONEY_TAKE = commandMessage(message.getString(gangMoneyMessage + "Player_Money_Take")).replace(
				"%money_symbol%", moneySymbol);
		GANG_TARGET_MONEY_ADD = commandMessage(message.getString(gangMoneyMessage + "Target_Money_Add")).replace(
				"%money_symbol%", moneySymbol);
		GANG_TARGET_MONEY_TAKE = commandMessage(message.getString(gangMoneyMessage + "Target_Money_Take")).replace(
				"%money_symbol%", moneySymbol);

		String spawnMessage = "Spawn.";
		SPAWN_SET = commandMessage(message.getString(spawnMessage + "Set"));
		SPAWN_NOTSET = commandMessage(message.getString(spawnMessage + "Not_Set"));
		SPAWN_REMOVED = commandMessage(message.getString(spawnMessage + "Removed"));
		NEED_DEFAULT_SPAWN = commandMessage(message.getString(spawnMessage + "Need_Default"));
		PLAYER_SENDTO_SPAWN = commandMessage(message.getString(spawnMessage + "Player_Send_To_Spawn"));
		PLAYER_SENDTO_SPAWN_COOLDOWN = commandMessage(
				message.getString(spawnMessage + "Player_Send_To_Spawn_Cooldown"));
		TARGET_SENDTO_SPAWN = commandMessage(message.getString(spawnMessage + "Target_Send_To_Spawn"));
		TARGET_SENDTO_SPAWN_COOLDOWN = commandMessage(
				message.getString(spawnMessage + "Target_Send_To_Spawn_Cooldown"));
		SPAWN_LIST = commandMessage(message.getString(spawnMessage + "List"));
		SPAWN_LIST_UNDEFINED = commandMessage(message.getString(spawnMessage + "List_Undefined"));

		String warpMessage = "Warp.";
		WARP_SET = commandMessage(message.getString(warpMessage + "Set"));
		WARP_NOTSET = commandMessage(message.getString(warpMessage + "Not_Set"));
		WARP_REMOVED = commandMessage(message.getString(warpMessage + "Removed"));
		PLAYER_SENDTO_WARP = commandMessage(message.getString(warpMessage + "Player_Send_To_Warp"));
		PLAYER_SENDTO_WARP_COOLDOWN = commandMessage(message.getString(warpMessage + "Player_Send_To_Warp_Cooldown"));
		TARGET_SENDTO_WARP = commandMessage(message.getString(warpMessage + "Target_Send_To_Warp"));
		TARGET_SENDTO_WARP_COOLDOWN = commandMessage(message.getString(warpMessage + "Player_Send_To_Warp_Cooldown"));
		WARP_LIST_UNDEFINED = commandMessage(message.getString(warpMessage + "List_Undefined"));

		String kitsMessage = "Kits.";
		KIT_RECEIVED = commandMessage(message.getString(kitsMessage + "Received"));
		KIT_INVAILD = commandMessage(message.getString(kitsMessage + "Invalid"));
		KIT_REMOVED = commandMessage(message.getString(kitsMessage + "Removed"));
		KIT_LIST_UNDEFINED = commandMessage(message.getString(kitsMessage + "List_Undefined"));

		String safeMessage = "Safe.";
		SAFE_CREATED = commandMessage(message.getString(safeMessage + "Created"));
		SAFE_REMOVED = commandMessage(message.getString(safeMessage + "Removed"));
		SAFE_ROBBED = informationMessage(message.getString(safeMessage + "Robbed")).replace("%money_symbol%",
		                                                                                    moneySymbol);
//		SAFE_ROBBED_ACTION = color(Objects.requireNonNull(settingsFile.getString(safeMessage + "ActionBar_Message")));
		SAFE_RESPAWNED = informationMessage(message.getString(safeMessage + "Respawned"));

		String wantedMessage = "Wanted_Level.";
		NOT_WANTED = prefixMessage(message.getString(wantedMessage + "Not_Wanted"));
		PAID_WANTEDLEVEL = prefixMessage(message.getString(wantedMessage + "Paid")).replace("%money_symbol%",
		                                                                                    moneySymbol);

		String weaponsMessage = "Weapons.";
		NOT_VALID_AMMO = prefixMessage(message.getString(weaponsMessage + "Not_Valid_Ammo"));
		NOT_VALID_WEAPON = prefixMessage(message.getString(weaponsMessage + "Not_Valid_Weapon"));
		NOT_VALID_AMOUNT = prefixMessage(message.getString(weaponsMessage + "Not_Valid_Amount"));
		KILLED_PLAYER_DEATH_MESSAGE = prefixMessage(message.getString(weaponsMessage + "Killed_Player"));
		GUN_NOT_INV = prefixMessage(message.getString(weaponsMessage + "Gun_Not_In_Inventory"));
		GUN_BOUGHT = prefixMessage(message.getString(weaponsMessage + "Gun_Bought")).replace("%money_symbol%",
		                                                                                     moneySymbol);
		GUN_SOLD = prefixMessage(message.getString(weaponsMessage + "Gun_Sold")).replace("%money_symbol%", moneySymbol);
		AMMO_NOT_INV = prefixMessage(message.getString(weaponsMessage + "Ammo_Not_In_Inventory"));
		AMMO_BOUGHT = prefixMessage(message.getString(weaponsMessage + "Ammo_Bought")).replace("%money_symbol%",
		                                                                                       moneySymbol);
		AMMO_SOLD = prefixMessage(message.getString(weaponsMessage + "Ammo_Sold")).replace("%money_symbol%",
		                                                                                   moneySymbol);
		NOT_ENOUGH_AMMO = prefixMessage(message.getString(weaponsMessage + "Not_Enough_Ammo"));

		ERROR_START = color("&f------&8[&4Error &5- &3Start&8]&f------");
		ERROR_END = color("&f------&8[&4Error &5- &3End&8]&f------");
		CONTACT = informationMessage("&7Please contact the plugin Developer.");
		NO_CONTACT = informationMessage("&7Please don't contact the plugin Developer.");
		CANNOT_CHECK_UPDATE = errorMessage("&7Could not check for &bupdates&7! &aError Info:");
	}

	public static String formatDouble(double value) {
		String format = Objects.requireNonNull(setting.getString("Balance_Format"));
		if (setting.getBoolean("Balance_Format.Use")) return String.format(format, value);
		return String.valueOf(value);
	}

	public static String confirmCommand(String[] args) {
		return "&cYou need to confirm using &e/glw " + String.join(" ", args) + " confirm &cto execute the command.";
	}

}
