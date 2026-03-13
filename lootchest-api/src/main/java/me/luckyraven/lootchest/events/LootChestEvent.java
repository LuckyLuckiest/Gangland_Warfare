package me.luckyraven.lootchest.events;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.luckyraven.lootchest.data.LootChestData;
import org.bukkit.event.Event;

@Getter
@RequiredArgsConstructor
public abstract class LootChestEvent extends Event {

	private final LootChestData lootChestData;

}
