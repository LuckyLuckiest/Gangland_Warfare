package org.luckyraven.gangland.inventory.part;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.keystone.util.TriConsumer;
import org.luckyraven.gangland.inventory.InventoryHandler;
import org.luckyraven.gangland.inventory.condition.ConditionalSlotData;

public record ConditionalSlotResult(ItemBuilder item, boolean clickable, boolean draggable,
                                    TriConsumer<Player, InventoryHandler, ItemBuilder> clickAction,
                                    @Nullable ConditionalSlotData.ClickAction rawClickAction,
                                    @Nullable ConditionalSlotData.ClickAction rawRightClickAction) { }
