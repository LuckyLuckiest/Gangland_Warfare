package me.luckyraven.inventory.part;

import me.luckyraven.core.ItemBuilder;
import me.luckyraven.core.TriConsumer;
import me.luckyraven.inventory.InventoryHandler;
import me.luckyraven.inventory.condition.ConditionalSlotData;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

public record ConditionalSlotResult(ItemBuilder item, boolean clickable, boolean draggable,
                                    TriConsumer<Player, InventoryHandler, ItemBuilder> clickAction,
                                    @Nullable ConditionalSlotData.ClickAction rawClickAction,
                                    @Nullable ConditionalSlotData.ClickAction rawRightClickAction) { }
