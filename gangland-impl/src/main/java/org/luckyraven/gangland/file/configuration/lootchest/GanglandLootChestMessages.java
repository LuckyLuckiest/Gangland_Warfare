package org.luckyraven.gangland.file.configuration.lootchest;

import org.luckyraven.keystone.util.messages.TimeMessagesProvider;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.lootchest.config.LootChestMessagesProvider;
import org.luckyraven.gangland.lootchest.data.LootTier;

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
	public String getHologramTierLabel(LootTier tier) {
		return tier.displayName();
	}

	@Override
	public String getHologramLockedRequires(String itemDisplay) {
		return Messages.LOOT_CHEST_HOLOGRAM_LOCKED_REQUIRES.toString().replace("%item%", itemDisplay);
	}

	@Override
	public String getHologramLockedPermission() {
		return Messages.LOOT_CHEST_HOLOGRAM_LOCKED_PERMISSION.toString();
	}

	@Override
	public String getHologramUnlocked() {
		return Messages.LOOT_CHEST_HOLOGRAM_UNLOCKED.toString();
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
