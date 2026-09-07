package org.luckyraven.gangland.copsncrooks.npc.turf.view;

import lombok.Getter;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.inventory.flow.FlowSession;
import org.luckyraven.gangland.turf.data.Turf;

/**
 * Per-viewer state carried across panel switches in the Quartermaster flow. Holds the {@link Turf} the panel targets
 * (resolved at open time so the view never looks it up again) and the viewer's {@link Gang} (for owner-gang gating and
 * bank-balance reads). Both are resolved by {@code TurfPowerupOpenContractImpl} before opening the flow — if either is
 * missing, the contract sends a deny chat and never constructs a session.
 */
public final class TurfPowerupFlowSession implements FlowSession {

	public static final String PANEL_MENU     = "menu";
	public static final String PANEL_BUFFS    = "buffs";
	public static final String PANEL_GARRISON = "garrison";

	@Getter
	private final Turf   turf;
	/**
	 * Gang that actually owns the turf. Used for the "Owning gang" display so allies see the owner's name.
	 */
	@Getter
	private final Gang   ownerGang;
	/**
	 * Gang of the player viewing the panel — owner OR an allied gang. Purchases debit THIS gang's bank.
	 */
	@Getter
	private final Gang   viewerGang;
	@Getter
	private final String npcDisplayName;

	public TurfPowerupFlowSession(Turf turf, Gang ownerGang, Gang viewerGang, String npcDisplayName) {
		this.turf           = turf;
		this.ownerGang      = ownerGang;
		this.viewerGang     = viewerGang;
		this.npcDisplayName = npcDisplayName;
	}
}
