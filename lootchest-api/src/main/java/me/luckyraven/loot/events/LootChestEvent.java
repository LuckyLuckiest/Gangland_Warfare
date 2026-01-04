package me.luckyraven.loot.events;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.luckyraven.loot.data.LootChestData;
import org.bukkit.event.Event;

@Getter
@RequiredArgsConstructor
public abstract class LootChestEvent extends Event {

	private final LootChestData lootChestData;

}
