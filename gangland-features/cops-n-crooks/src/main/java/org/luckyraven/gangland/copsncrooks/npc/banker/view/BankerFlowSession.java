package org.luckyraven.gangland.copsncrooks.npc.banker.view;

import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.npc.banker.BankerNpc;
import org.luckyraven.gangland.inventory.flow.FlowSession;
import org.luckyraven.gangland.inventory.flow.MultiPanelInventory;

import java.math.BigDecimal;

/**
 * Session payload for the banker {@link MultiPanelInventory}. Holds the originating {@link BankerNpc} when the player
 * interacted with a physical banker; {@code null} when the flow was started from the phone's online-banking screen (no
 * NPC present — display strings fall back to "Online Banking").
 *
 * <p>Sub-panels stash their own state here instead of per-view {@code WeakHashMap}s — e.g. the amount panel's staged
 * amount / mode / step index survive anvil detours because the session is owned by the flow, not by any single
 * inventory handle.
 */
public final class BankerFlowSession implements FlowSession {

	public static final String PANEL_MENU    = "menu";
	public static final String PANEL_UPGRADE = "upgrade";
	public static final String PANEL_CLAIM   = "claim";
	public static final String PANEL_AMOUNT  = "amount";
	public static final String PANEL_CREATE  = "create";

	@Nullable
	public final BankerNpc banker;

	// Amount-panel state (deposit/withdraw). Populated by BankerMenuView before switchTo; the amount panel reads
	// + mutates them during render + adjust clicks + anvil callbacks.
	@Nullable
	public BankerAmountView.Mode amountMode;
	@Nullable
	public BigDecimal            amountStaged;
	public int                   amountStepIndex;

	public BankerFlowSession(@Nullable BankerNpc banker) {
		this.banker = banker;
	}

	public String displayName() {
		if (banker != null && banker.getData().getDisplayName() != null) return banker.getData().getDisplayName();
		return "Online Banking";
	}

}
