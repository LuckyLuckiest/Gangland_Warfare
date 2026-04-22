package me.luckyraven.copsncrooks.npc.banker.view;

import me.luckyraven.copsncrooks.npc.banker.BankerNpc;
import me.luckyraven.inventory.flow.FlowSession;
import org.jetbrains.annotations.Nullable;

/**
 * Session payload for the banker {@link me.luckyraven.inventory.flow.MultiPanelInventory}. Holds the originating
 * {@link BankerNpc} when the player interacted with a physical banker; {@code null} when the flow was started from the
 * phone's online-banking screen (no NPC present — display strings fall back to "Online Banking").
 */
public final class BankerFlowSession implements FlowSession {

	public static final String PANEL_MENU = "menu";

	@Nullable
	public final BankerNpc banker;

	public BankerFlowSession(@Nullable BankerNpc banker) {
		this.banker = banker;
	}

	public String displayName() {
		if (banker != null && banker.getData().getDisplayName() != null) return banker.getData().getDisplayName();
		return "Online Banking";
	}

}
