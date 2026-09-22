package org.luckyraven.gangland.lootchest.config;

import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.util.GanglandChatUtil;
import org.luckyraven.keystone.exception.PluginException;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileInitializer;
import org.luckyraven.keystone.persistence.FileManager;
import org.luckyraven.keystone.util.messages.TimeMessagesProvider;
import org.luckyraven.gangland.lootchest.data.LootTier;

import java.io.IOException;

/**
 * {@link LootChestMessagesProvider} implementation backed by the module's own
 * {@code lootchests/lootchest_messages.yml} (English, always shipped as the fallback) or
 * {@code lootchests/lootchest_messages_es.yml} (Spanish, WS3 G4 fix round 1 / W53 F1 — the 26 strings deleted
 * from the core's {@code message_es.yml} live here now, not lost) — picked by {@link Settings#getLanguagePicked()}
 * the same way Keystone's {@code LanguageLoader} chooses core's own {@code message_<lang>.yml}, reusing that
 * existing knob rather than inventing a module-local one. Falls back to the English file whenever the language
 * isn't {@code "es"}, or the Spanish file isn't registered. Module-owned message localisation is ad hoc here —
 * WS6 G3's own mechanism is expected to generalise this to every module. Mirrors {@code JetpackMessages}'s
 * {@link FileInitializer} shape otherwise: flat keys, read lazily per call, each wrapped with the same
 * {@link GanglandChatUtil} color-type the old {@code Messages} enum entry used
 * ({@code ERROR}/{@code COMMAND}/{@code OTHER}/raw) so player-visible output is unchanged.
 */
public class GanglandLootChestMessages implements LootChestMessagesProvider, FileInitializer {

	private static final String ENGLISH_FILE = "lootchest_messages";
	private static final String SPANISH_FILE = "lootchest_messages_es";

	private final FileHandler fileHandler;

	public GanglandLootChestMessages(FileManager fileManager) {
		this.fileHandler = resolveFileHandler(fileManager);
	}

	private static FileHandler resolveFileHandler(FileManager fileManager) {
		if ("es".equalsIgnoreCase(Settings.getLanguagePicked())) {
			FileHandler spanish = tryGetFile(fileManager, SPANISH_FILE);
			if (spanish != null) return spanish;
		}

		FileHandler english = tryGetFile(fileManager, ENGLISH_FILE);
		if (english != null) return english;

		throw new PluginException("Neither " + ENGLISH_FILE + " nor " + SPANISH_FILE + " is registered");
	}

	private static FileHandler tryGetFile(FileManager fileManager, String fileName) {
		try {
			fileManager.checkFileLoaded(fileName);
			return fileManager.getFile(fileName);
		} catch (IOException exception) {
			return null;
		}
	}

	@Override
	public FileHandler getFileHandler() {
		return fileHandler;
	}

	@Override
	public void initialize() {
		// Flat strings read lazily per call — nothing to pre-parse.
	}

	@Override
	public String getCrackingStarted() {
		return GanglandChatUtil.color(get("Cracking_Started", "&eCracking the chest... Complete the minigame!"));
	}

	@Override
	public String getAlreadyInSession() {
		return GanglandChatUtil.errorMessage(get("Already_In_Session", "You are already opening a chest!"));
	}

	@Override
	public String getOnCooldown(String formattedTime) {
		return GanglandChatUtil.errorMessage(get("On_Cooldown", "This chest is empty and on cooldown! &7(%time%)"))
		                       .replace("%time%", formattedTime);
	}

	@Override
	public String getRequiresLockpick() {
		return GanglandChatUtil.errorMessage(get("Requires_Lockpick", "You need a lockpick to open this chest!"));
	}

	@Override
	public String getRequiresKey() {
		return GanglandChatUtil.errorMessage(get("Requires_Key", "You need a key to open this chest!"));
	}

	@Override
	public String getNoPermission() {
		return GanglandChatUtil.errorMessage(get("No_Permission", "You don't have permission to open this chest!"));
	}

	@Override
	public String getInvalidLootTable() {
		return GanglandChatUtil.errorMessage(get("Invalid_Loot_Table", "This chest has an invalid loot table!"));
	}

	@Override
	public String getInvalidChest() {
		return GanglandChatUtil.errorMessage(get("Invalid_Chest", "This chest is invalid!"));
	}

	@Override
	public String getNoItemProvider() {
		return GanglandChatUtil.errorMessage(get("No_Item_Provider", "Loot system is not configured properly!"));
	}

	@Override
	public String getAlreadyLooted() {
		return GanglandChatUtil.errorMessage(get("Already_Looted", "This chest has already been looted!"));
	}

	@Override
	public String getHologramCooldownStatus() {
		return GanglandChatUtil.color(get("Hologram.Cooldown_Status", "&c&lON COOLDOWN"));
	}

	@Override
	public String getHologramAvailableStatus() {
		return GanglandChatUtil.color(get("Hologram.Available_Status", "&a&lAVAILABLE"));
	}

	@Override
	public String getHologramAvailableHint() {
		return GanglandChatUtil.color(get("Hologram.Available_Hint", "&7Right-click to open"));
	}

	@Override
	public String getHologramTierLabel(LootTier tier) {
		return tier.displayName();
	}

	@Override
	public String getHologramLockedRequires(String itemDisplay) {
		return GanglandChatUtil.color(get("Hologram.Locked_Requires", "&c🔒 Requires &e%item%"))
		                       .replace("%item%", itemDisplay);
	}

	@Override
	public String getHologramLockedPermission() {
		return GanglandChatUtil.color(get("Hologram.Locked_Permission", "&c🔒 Permission Only"));
	}

	@Override
	public String getHologramUnlocked() {
		return GanglandChatUtil.color(get("Hologram.Unlocked", "&a🔓 Unlocked"));
	}

	@Override
	public String getMustLookAtBlock() {
		return GanglandChatUtil.errorMessage(get("Must_Look_At_Block", "&cYou must be looking at a block within 5 blocks!"));
	}

	@Override
	public String getNoChestAtLocation() {
		return GanglandChatUtil.errorMessage(get("No_Chest_At_Location", "&cNo loot chest found at that location!"));
	}

	@Override
	public String getRequiresWand() {
		return GanglandChatUtil.errorMessage(get("Requires_Wand", "You must be holding a Loot Chest Wand to edit settings!"));
	}

	@Override
	public String getRemoved() {
		return GanglandChatUtil.commandMessage(get("Removed", "&aLoot chest removed successfully!"));
	}

	@Override
	public TimeMessagesProvider getTimeMessages() {
		return new TimeMessagesProvider() {

			@Override
			public String getYear() {
				return get("Time_Units.Year", "y");
			}

			@Override
			public String getWeek() {
				return get("Time_Units.Week", "w");
			}

			@Override
			public String getDay() {
				return get("Time_Units.Day", "d");
			}

			@Override
			public String getHour() {
				return get("Time_Units.Hour", "h");
			}

			@Override
			public String getMinute() {
				return get("Time_Units.Minute", "m");
			}

			@Override
			public String getSecond() {
				return get("Time_Units.Second", "s");
			}
		};
	}

	private String get(String key, String fallback) {
		return fileHandler.getFileConfiguration().getString(key, fallback);
	}

}
