package me.luckyraven.file.configuration.lootchest;

import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.lootchest.config.LootChestMessagesProvider;
import me.luckyraven.util.utilities.messages.TimeMessagesProvider;

/**
 * {@link LootChestMessagesProvider} implementation backed by {@link MessageAddon}.
 * <p>
 * All strings are read from the active messages file ({@code message_en.yml} / {@code message_es.yml}) under the
 * {@code Loot_Chest} section.
 */
public class GanglandLootChestMessages implements LootChestMessagesProvider {

	@Override
	public String getCrackingStarted() {
		return MessageAddon.LOOT_CHEST_CRACKING_STARTED.toString();
	}

	@Override
	public String getAlreadyInSession() {
		return MessageAddon.LOOT_CHEST_ALREADY_IN_SESSION.toString();
	}

	@Override
	public String getOnCooldown(String formattedTime) {
		return MessageAddon.LOOT_CHEST_ON_COOLDOWN.toString().replace("%time%", formattedTime);
	}

	@Override
	public String getRequiresLockpick() {
		return MessageAddon.LOOT_CHEST_REQUIRES_LOCKPICK.toString();
	}

	@Override
	public String getRequiresKey() {
		return MessageAddon.LOOT_CHEST_REQUIRES_KEY.toString();
	}

	@Override
	public String getNoPermission() {
		return MessageAddon.LOOT_CHEST_NO_PERMISSION.toString();
	}

	@Override
	public String getInvalidLootTable() {
		return MessageAddon.LOOT_CHEST_INVALID_LOOT_TABLE.toString();
	}

	@Override
	public String getInvalidChest() {
		return MessageAddon.LOOT_CHEST_INVALID_CHEST.toString();
	}

	@Override
	public String getNoItemProvider() {
		return MessageAddon.LOOT_CHEST_NO_ITEM_PROVIDER.toString();
	}

	@Override
	public String getAlreadyLooted() {
		return MessageAddon.LOOT_CHEST_ALREADY_LOOTED.toString();
	}

	@Override
	public String getHologramCooldownStatus() {
		return MessageAddon.LOOT_CHEST_HOLOGRAM_COOLDOWN.toString();
	}

	@Override
	public String getHologramAvailableStatus() {
		return MessageAddon.LOOT_CHEST_HOLOGRAM_AVAILABLE.toString();
	}

	@Override
	public String getHologramAvailableHint() {
		return MessageAddon.LOOT_CHEST_HOLOGRAM_HINT.toString();
	}

	@Override
	public TimeMessagesProvider getTimeMessages() {
		return new TimeMessagesProvider() {

			@Override
			public String getYear() {
				return MessageAddon.LOOT_CHEST_TIME_YEAR.toString();
			}

			@Override
			public String getWeek() {
				return MessageAddon.LOOT_CHEST_TIME_WEEK.toString();
			}

			@Override
			public String getDay() {
				return MessageAddon.LOOT_CHEST_TIME_DAY.toString();
			}

			@Override
			public String getHour() {
				return MessageAddon.LOOT_CHEST_TIME_HOUR.toString();
			}

			@Override
			public String getMinute() {
				return MessageAddon.LOOT_CHEST_TIME_MINUTE.toString();
			}

			@Override
			public String getSecond() {
				return MessageAddon.LOOT_CHEST_TIME_SECOND.toString();
			}
		};
	}
}
