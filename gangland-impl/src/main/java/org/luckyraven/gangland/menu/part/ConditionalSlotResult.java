package org.luckyraven.gangland.menu.part;

import org.jetbrains.annotations.Nullable;
import org.luckyraven.keystone.inventory.click.ClickHandler;
import org.luckyraven.keystone.item.ItemBuilder;
import org.luckyraven.gangland.menu.condition.ConditionalSlotData;

public record ConditionalSlotResult(ItemBuilder item, boolean clickable, boolean draggable,
                                    @Nullable ClickHandler clickAction,
                                    @Nullable ConditionalSlotData.ClickAction rawClickAction,
                                    @Nullable ConditionalSlotData.ClickAction rawRightClickAction) { }
