package org.luckyraven.gangland.copsncrooks.wanted;

import org.luckyraven.gangland.file.configuration.LocalizedModuleYaml;
import org.luckyraven.keystone.persistence.FileManager;

import java.util.Map;

/**
 * The chase HUD and charge-sheet text, from the module's own {@code copsncrooks/wanted_messages.yml}. Every key has an in-code
 * fallback equal to the shipped line, so a file that predates a key still reads.
 *
 * @since 0.15.0
 */
public class WantedMessages extends LocalizedModuleYaml {

	private static final String BASE_NAME = "wanted_messages";

	/** One line of {@code wanted_messages.yml}: its path in the file and the text used when the file lacks it. */
	public enum Key {
		BAR_SEEN("Hud.Bar.Seen", "&c%stars% &4&lIN SIGHT"),
		BAR_SEARCHING("Hud.Bar.Searching", "&e%stars% &6&lSEARCHING %time%"),
		BAR_EVADED("Hud.Bar.Evaded", "&a%stars% &2&lSTAR LOST"),
		BAR_WAY("Hud.Bar.Way", "   &f%arrow% %distance%m"),
		TITLE("Hud.Title", "&c%stars%"),
		CARD_RAISE("Hud.Card.Raise", "&f%crime%&7: &e%tier% &7inbound, %stance%"),
		STANCE_CUFFS("Hud.Card.Stance_Cuffs", "they still want you in cuffs"),
		STANCE_SHOOT("Hud.Card.Stance_Shoot", "they shoot first"),
		CARD_DROP_EVASION("Hud.Card.Drop_Evasion", "&aYou stayed out of sight"),
		CARD_DROP_DECAY("Hud.Card.Drop_Decay", "&aThe trail went cold"),
		CARD_DROP_OTHER("Hud.Card.Drop_Other", "&aA star is gone"),
		SHEET_HEADER("Charge_Sheet.Header", "&8&m-----&r &6&lCHARGE SHEET &8&m-----"),
		SHEET_CRIME("Charge_Sheet.Crime", "&7- &f%crime% &8x%count%"),
		SHEET_TOTAL("Charge_Sheet.Total", "&7Fine: &c%money_symbol%%amount%"),
		SHEET_PAID("Charge_Sheet.Paid", "&7Paid from your wallet: &a%money_symbol%%amount%"),
		SHEET_EXTRA("Charge_Sheet.Extra_Time", "&7Unpaid &c%money_symbol%%amount% &7served as &c+%time%"),
		PAPERWORK_FINE("Charge_Sheet.Paperwork", "&7Fine paid: &a%money_symbol%%paid% &8| &7Extra time: &c%time%");

		public final String path;
		public final String fallback;

		Key(String path, String fallback) {
			this.path     = path;
			this.fallback = fallback;
		}
	}

	public WantedMessages(FileManager fileManager) {
		super(fileManager, BASE_NAME);
	}

	/** The line for {@code key}, coloured, with every {@code %name%} in {@code values} replaced. */
	public String format(Key key, Map<String, String> values) {
		String line = color(key.path, key.fallback);

		for (Map.Entry<String, String> entry : values.entrySet()) {
			line = line.replace("%" + entry.getKey() + "%", entry.getValue());
		}

		return line;
	}

	/** The display name of a crime id: {@code Crimes.<id>} in the file, else the id with spaces. */
	public String crimeName(String crimeId) {
		return color("Crimes." + crimeId, crimeId.replace('_', ' '));
	}

	/** {@code 45s}, or {@code 1m 05s} from a minute up. */
	public static String duration(int seconds) {
		int safe = Math.max(0, seconds);
		return safe < 60 ? safe + "s" : String.format("%dm %02ds", safe / 60, safe % 60);
	}
}
