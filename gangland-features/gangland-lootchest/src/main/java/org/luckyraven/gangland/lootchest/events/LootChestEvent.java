package org.luckyraven.gangland.lootchest.events;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.event.Event;
import org.luckyraven.gangland.lootchest.data.LootChestData;

@Getter
@RequiredArgsConstructor
public abstract class LootChestEvent extends Event {

	private final LootChestData lootChestData;

}
