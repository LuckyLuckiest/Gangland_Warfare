package me.luckyraven.inventory.part;

import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.condition.ConditionalSlotData;
import me.luckyraven.util.ItemBuilder;
import me.luckyraven.util.TriConsumer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

public record ConditionalSlotResult(ItemBuilder item, boolean clickable, boolean draggable,
									TriConsumer<Player, InventoryHandler, ItemBuilder> clickAction,
									@Nullable ConditionalSlotData.ClickAction rawClickAction,
									@Nullable ConditionalSlotData.ClickAction rawRightClickAction) { }
