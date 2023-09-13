package me.luckyraven.data.inventory.part;

import me.luckyraven.bukkit.ItemBuilder;

public record Slot(boolean clickable, boolean draggable, ItemBuilder item) {}
