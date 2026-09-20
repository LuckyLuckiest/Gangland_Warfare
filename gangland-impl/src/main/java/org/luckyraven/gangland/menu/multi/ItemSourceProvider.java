package org.luckyraven.gangland.menu.multi;

import org.bukkit.entity.Player;

import java.util.List;

public interface ItemSourceProvider {

	List<ItemSourceEntry> getEntries(Player player, String source);

}
