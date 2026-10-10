package org.luckyraven.gangland.turf.npc.view;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserLookupContract;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.gang.member.Member;
import org.luckyraven.gangland.gang.permission.GangPermissions;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.manager.TurfManager;

import java.math.BigDecimal;

/**
 * The one re-check every Quartermaster purchase runs before any money moves (W2-Q, TF-19, TF-45). The panel may open
 * for the owning gang and its allies; spending needs the turf to still exist, to still belong to the gang the panel
 * opened for, the viewer to still be a member of that gang, and the member to hold {@link GangPermissions#TURF_UPGRADE}
 * (the top rank always may). Allies see the panel but may only look.
 *
 * <p>{@link #denyKey} returns {@code null} when the viewer may buy now, otherwise the {@code Quartermaster.*} key of the
 * line to show, checked in the order of the constants below.
 */
public final class QuartermasterAccess {

	public static final String TURF_GONE      = "Quartermaster.Turf_Gone";
	public static final String OWNER_CHANGED  = "Quartermaster.Owner_Changed";
	public static final String NOT_MEMBER     = "Quartermaster.Not_Member";
	public static final String NOT_ALLIED     = "Quartermaster.Not_Allied";
	public static final String ALLIES_LOOK    = "Quartermaster.Allies_Look";
	public static final String NO_RANK        = "Quartermaster.No_Rank";

	private final TurfManager        turfs;
	private final GangLookupContract gangs;
	private final UserLookupContract users;

	public QuartermasterAccess(TurfManager turfs, GangLookupContract gangs, UserLookupContract users) {
		this.turfs = turfs;
		this.gangs = gangs;
		this.users = users;
	}

	/**
	 * @return {@code null} when {@code viewer} may buy for {@code session} right now, else the key of the refusal line
	 */
	@Nullable
	public String denyKey(Player viewer, TurfPowerupFlowSession session) {
		Turf turf = turfs.get(session.getTurf().getId());
		if (turf == null) {
			return TURF_GONE;
		}

		Integer ownerId = turf.getOwnerGangId();
		if (ownerId == null || ownerId != session.getOwnerGang().getId()) {
			return OWNER_CHANGED;
		}

		Gang owner = gangs.findById(ownerId);
		if (owner == null) {
			return OWNER_CHANGED;
		}

		User<Player> user = users.findByPlayer(viewer);
		if (user == null || !user.hasGang()) {
			return NOT_MEMBER;
		}

		if (user.getGangId() != ownerId) {
			// The viewer's gang opened the panel as an ally: if the alliance ended since, say so rather than "left".
			Gang viewerGang = session.getViewerGang();
			if (viewerGang != null && viewerGang.getId() == user.getGangId()) {
				return viewerGang.isAlly(owner) ? ALLIES_LOOK : NOT_ALLIED;
			}
			return NOT_MEMBER;
		}

		Member member = memberOf(owner, viewer);
		if (member == null) {
			return NOT_MEMBER;
		}

		return GangPermissions.allows(member, viewer, GangPermissions.TURF_UPGRADE) ? null : NO_RANK;
	}

	/** The refusal line {@code key} in the turf module's YAML, or its shipped fallback. */
	public static String refusal(TurfMessages messages, String key) {
		return messages.text(key, fallback(key));
	}

	/** Whether a refusal ends the panel (the turf is gone, changed hands, or the viewer left or lost the alliance). */
	public static boolean closesPanel(String key) {
		return TURF_GONE.equals(key) || OWNER_CHANGED.equals(key) || NOT_MEMBER.equals(key) || NOT_ALLIED.equals(key);
	}

	/** Whether the panel was opened for an ally: buy lines show {@link #ALLIES_LOOK} instead of a buy action. */
	public static boolean isAllyView(TurfPowerupFlowSession session) {
		return session.getViewerGang().getId() != session.getOwnerGang().getId();
	}

	/** A money amount with the server's configured currency symbol in front of it. */
	public static String money(BigDecimal amount) {
		return Settings.getMoneySymbol() + Settings.formatAmount(amount);
	}

	private static Member memberOf(Gang gang, Player viewer) {
		for (Member member : gang.getMembers()) {
			if (member.getUuid().equals(viewer.getUniqueId())) {
				return member;
			}
		}
		return null;
	}

	private static String fallback(String key) {
		return switch (key) {
			case TURF_GONE -> "&cThis turf no longer exists.";
			case OWNER_CHANGED -> "&cThis turf changed hands. The panel is closed.";
			case NOT_MEMBER -> "&cYou are no longer in the owning gang.";
			case NOT_ALLIED -> "&cYour gang is no longer allied with the owning gang.";
			case ALLIES_LOOK -> "&eAllied gangs can look, but only the owning gang can buy.";
			default -> "&cYour rank may not spend gang money here.";
		};
	}
}
