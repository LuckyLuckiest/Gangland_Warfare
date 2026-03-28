package me.luckyraven.sign.bulk;

import me.luckyraven.sign.aspect.AspectResult;
import me.luckyraven.sign.model.ParsedSign;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Implemented by sign types in gangland-impl that support bulk (shift-click) transactions.
 *
 * <p>The two-step lifecycle:
 * <ol>
 *   <li>Player shift-clicks → {@link #previewBulk} is called to generate a preview shown as
 *       confirmation feedback.</li>
 *   <li>Player shift-clicks again within the countdown → {@link #executeBulkAction} is called
 *       to carry out the transaction.</li>
 * </ol>
 */
public interface BulkSignHandler {

	/**
	 * Generates a preview of the bulk transaction (shown before confirmation).
	 *
	 * @param sign the parsed sign data
	 *
	 * @return a snapshot of quantity, price, and item name
	 */
	BulkActionPreview previewBulk(ParsedSign sign);

	/**
	 * Executes the bulk transaction after the player confirms.
	 *
	 * <p>Returns a list of {@link AspectResult}s so the caller can send consistent
	 * success/error feedback using {@link me.luckyraven.sign.service.SignInformation}.
	 *
	 * @param player the confirming player
	 * @param sign the parsed sign data
	 *
	 * @return ordered results - execution stops on the first failure
	 */
	List<AspectResult> executeBulkAction(Player player, ParsedSign sign);

}
