package me.luckyraven.file.configuration.lootchest;

import me.luckyraven.file.configuration.Messages;
import me.luckyraven.lootchest.config.LootChestMessagesProvider;
import me.luckyraven.util.utilities.messages.TimeMessagesProvider;

/**
 * {@link LootChestMessagesProvider} implementation backed by {@link Messages}.
 * <p>
 * All strings are read from the active messages file ({@code message_en.yml} / {@code message_es.yml}) under the
 * {@code Loot_Chest} section.
 */
public class GanglandLootChestMessages implements LootChestMessagesProvider {

	@Override
	public String getCrackingStarted() {
		return Messages.LOOT_CHEST_CRACKING_STARTED.toString();
	}

	@Override
	public String getAlreadyInSession() {
		return Messages.LOOT_CHEST_ALREADY_IN_SESSION.toString();
	}

	@Override
	public String getOnCooldown(String formattedTime) {
		return Messages.LOOT_CHEST_ON_COOLDOWN.toString().replace("%time%", formattedTime);
	}

	@Override
	public String getRequiresLockpick() {
		return Messages.LOOT_CHEST_REQUIRES_LOCKPICK.toString();
	}

	@Override
	public String getRequiresKey() {
		return Messages.LOOT_CHEST_REQUIRES_KEY.toString();
	}

	@Override
	public String getNoPermission() {
		return Messages.LOOT_CHEST_NO_PERMISSION.toString();
	}

	@Override
	public String getInvalidLootTable() {
		return Messages.LOOT_CHEST_INVALID_LOOT_TABLE.toString();
	}

	@Override
	public String getInvalidChest() {
		return Messages.LOOT_CHEST_INVALID_CHEST.toString();
	}

	@Override
	public String getNoItemProvider() {
		return Messages.LOOT_CHEST_NO_ITEM_PROVIDER.toString();
	}

	@Override
	public String getAlreadyLooted() {
		return Messages.LOOT_CHEST_ALREADY_LOOTED.toString();
	}

	@Override
	public String getHologramCooldownStatus() {
		return Messages.LOOT_CHEST_HOLOGRAM_COOLDOWN.toString();
	}

	@Override
	public String getHologramAvailableStatus() {
		return Messages.LOOT_CHEST_HOLOGRAM_AVAILABLE.toString();
	}

	@Override
	public String getHologramAvailableHint() {
		return Messages.LOOT_CHEST_HOLOGRAM_HINT.toString();
	}

	@Override
	public TimeMessagesProvider getTimeMessages() {
		return new TimeMessagesProvider() {

			@Override
			public String getYear() {
				return Messages.LOOT_CHEST_TIME_YEAR.toString();
			}

			@Override
			public String getWeek() {
				return Messages.LOOT_CHEST_TIME_WEEK.toString();
			}

			@Override
			public String getDay() {
				return Messages.LOOT_CHEST_TIME_DAY.toString();
			}

			@Override
			public String getHour() {
				return Messages.LOOT_CHEST_TIME_HOUR.toString();
			}

			@Override
			public String getMinute() {
				return Messages.LOOT_CHEST_TIME_MINUTE.toString();
			}

			@Override
			public String getSecond() {
				return Messages.LOOT_CHEST_TIME_SECOND.toString();
			}
		};
	}
}
