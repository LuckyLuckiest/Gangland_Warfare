package me.luckyraven.npc.banker;

import lombok.RequiredArgsConstructor;
import me.luckyraven.copsncrooks.npc.banker.BankerNpc;
import me.luckyraven.copsncrooks.npc.banker.view.BankerMenuView;
import me.luckyraven.copsncrooks.npc.banker.view.BankerViewOpener;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
public final class BankerViewOpenerImpl implements BankerViewOpener {

	private final BankerMenuView menuView;

	@Override
	public void openFor(Player player, BankerNpc banker) {
		menuView.open(player, banker);
	}

}
