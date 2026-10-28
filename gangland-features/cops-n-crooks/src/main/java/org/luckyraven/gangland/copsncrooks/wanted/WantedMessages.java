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

	/** Shipped {@code Crimes} lines whose id does not read as a name, so a file that predates them shows the same text. */
	private static final Map<String, String> SHIPPED_CRIMES = Map.of("Spotted", "Spotted by the cops",
	                                                                 "Unknown_Crime", "Reported crime");

	/** One line of {@code wanted_messages.yml}: its path in the file and the text used when the file lacks it. */
	public enum Key {
		BAR_SEEN("Hud.Bar.Seen", "&c%stars% &4&lIN SIGHT"),
		BAR_SEARCHING("Hud.Bar.Searching", "&e%stars% &6&lSEARCHING %time%"),
		BAR_EVADED("Hud.Bar.Evaded", "&a%stars% &2&lSTAR LOST"),
		BAR_EVADED_MANY("Hud.Bar.Evaded_Many", "&a%stars% &2&l-%count% STARS"),
		BAR_WAY("Hud.Bar.Way", "   &f%arrow% %distance%m"),
		BAR_BOUNTY("Hud.Bar.Bounty", "&6&lBOUNTY &e%money_symbol%%amount% &7· &cCops still looking"),
		BAR_BOUNTY_NONE("Hud.Bar.Bounty_None", "&7Cops still looking"),
		HUD_NO_COP_SEARCH("Hud.No_Cop_Search", "&7No units on you - leave the area"),
		ANNOUNCE_BOUNTY("Hud.Announce.Bounty", "&6&lBOUNTY &e%money_symbol%%amount% &7is on you, the cops are still looking"),
		ANNOUNCE_NO_BOUNTY("Hud.Announce.No_Bounty", "&7The cops are still looking for you"),
		ANNOUNCE_GAVE_UP("Hud.Announce.Gave_Up", "&7The cops gave up the search"),
		ANNOUNCE_GAVE_UP_BOUNTY("Hud.Announce.Gave_Up_Bounty",
		                        "&7The cops gave up the search &6BOUNTY %money_symbol%%amount% still stands"),
		CARD_RAISE("Hud.Card.Raise", "&f%crime%&7: &e%tier% &7inbound, %stance%"),
		STANCE_CUFFS("Hud.Card.Stance_Cuffs", "they still want you in cuffs"),
		STANCE_SHOOT("Hud.Card.Stance_Shoot", "they shoot first"),
		CARD_DROP_EVASION("Hud.Card.Drop_Evasion", "&aYou stayed out of sight"),
		CARD_DROP_DECAY("Hud.Card.Drop_Decay", "&aThe trail went cold"),
		CARD_DROP_OTHER("Hud.Card.Drop_Other", "&aA star is gone"),
		CARD_DROP_PETTY("Hud.Card.Drop_Petty", "&aSmall fry, the heat is off you for now &7(-%count%)"),
		CARD_DROP_COLD_TRAIL("Hud.Card.Drop_Cold_Trail", "&aThe trail is fading &7(-%count%)"),
		CARD_DROP_CLEAN_BREAK("Hud.Card.Drop_Clean_Break", "&aClean break, you left the area &7(-%count%)"),
		CARD_DROP_STILL_HOT("Hud.Card.Drop_Still_Hot", "&eStill hot, one star at a time"),
		CARD_DROP_KNOWN_FACE("Hud.Card.Drop_Known_Face", "&eThey know your face, one star at a time"),
		CARD_DROP_NARROW("Hud.Card.Drop_Narrow", "&aThat was close, they are losing you"),
		// 0.21.1: the crime of a raise a witness reported (a landed 911 call); 0.21.2: %count% is how many saw it
		CARD_REPORTED("Hud.Card.Reported", "%crime% (reported by %count% witness(es))"),
		// 0.21.2 S3: the crime of a raise a cop reported, dispatch presumed, or a cop spotted at the scene
		CARD_SEEN("Hud.Card.Seen", "%crime% (seen by %count% officer(s))"),
		CARD_MISSING("Hud.Card.Missing", "%crime% (an officer stopped answering)"),
		CARD_SCENE("Hud.Card.Scene", "%crime% (spotted near the scene)"),
		SHEET_HEADER("Charge_Sheet.Header", "&8&m-----&r &6&lCHARGE SHEET &8&m-----"),
		SHEET_CRIME("Charge_Sheet.Crime", "&7- &f%crime% &8x%count%"),
		SHEET_TOTAL("Charge_Sheet.Total", "&7Fine: &c%money_symbol%%amount%"),
		SHEET_PAID("Charge_Sheet.Paid", "&7Paid from your wallet: &a%money_symbol%%amount%"),
		SHEET_EXTRA("Charge_Sheet.Extra_Time", "&7Unpaid &c%money_symbol%%amount% &7served as &c+%time%"),
		PAPERWORK_FINE("Charge_Sheet.Paperwork", "&7Fine paid: &a%money_symbol%%paid% &8| &7Extra time: &c%time%"),
		BRIBE_STAR_TAKEN("Bribe_Star.Taken", "&6You pocketed a police bribe star. &e-%stars% star(s)."),
		BRIBE_STAR_SEEN("Bribe_Star.Seen", "&cNot with a cop watching."),
		// 0.21.0
		SURRENDER_ACCEPTED("Surrender.Accepted", "&eHands where we can see them. &7The cops hold their fire, stay down."),
		SURRENDER_RESISTED("Surrender.Resisted", "&cYou resisted arrest."),
		SURRENDER_TOO_HOT("Surrender.Too_Hot", "&cAt this heat they are not taking you alive."),
		BUSTED_TITLE("Busted.Title", "&c&lBUSTED"),
		BUSTED_SUBTITLE("Busted.Subtitle", "&7A cop got the cuffs on you"),
		LOCKPICK_SNAPPED("Lockpick.Snapped", "&7Your lockpick snapped."),
		CREW_HEAT_SHARED("Crew_Heat.Shared", "&c%player%'s %crime% is on you too."),
		RESCUE_PROGRESS("Rescue.Progress", "&eFreeing %player%... &f%seconds%s"),
		RESCUE_FREED("Rescue.Freed", "&aYou broke %player% out. &cThe cops want you both."),
		RESCUE_FREED_BY("Rescue.Freed_By", "&a%player% broke you out!"),
		REPORT_HEADER("Pursuit_Report.Header", "&8&m-----&r &6&lPURSUIT REPORT &8&m-----"),
		REPORT_ENDING("Pursuit_Report.Ending", "&7Ending: &f%ending%"),
		REPORT_DURATION("Pursuit_Report.Duration", "&7Duration: &f%time%"),
		REPORT_PEAK("Pursuit_Report.Peak", "&7Peak: %stars%"),
		REPORT_COPS_DOWN("Pursuit_Report.Cops_Down", "&7Cops down: &f%count%"),
		REPORT_BUSTS("Pursuit_Report.Busts", "&7Times cuffed: &f%count%"),
		REPORT_COST("Pursuit_Report.Cost", "&7Cost to the city: &c%money_symbol%%amount%"),
		ENDING_ESCAPED("Pursuit_Report.Endings.Escaped", "Escaped"),
		ENDING_SURRENDERED("Pursuit_Report.Endings.Surrendered", "Surrendered"),
		ENDING_BUSTED("Pursuit_Report.Endings.Busted", "Busted"),
		ENDING_ARRESTED("Pursuit_Report.Endings.Arrested", "Arrested"),
		ENDING_WASTED("Pursuit_Report.Endings.Wasted", "Wasted"),
		ENDING_PAID_OFF("Pursuit_Report.Endings.Paid_Off", "Paid off"),
		ENDING_CLEARED("Pursuit_Report.Endings.Cleared", "Cleared"),
		NEWS_LIVE("Breaking_News.Live", "&4&lBREAKING &fPolice are chasing %player% across the city &c%stars%"),
		NEWS_END("Breaking_News.End", "&4&lBREAKING &fThe %stars% &fchase of %player% is over: %ending%"),
		// 0.21.0 wave 2
		GETAWAY_LINE("Getaway.Line", "&7Getaway: &a+%xp% XP"),
		GETAWAY_CAPPED("Getaway.Capped", "&7Getaway: &8daily XP cap reached"),
		GETAWAY_SHARE("Getaway.Share", "&a%player% got away clean: &f+%xp% XP &7for the crew"),
		MOST_WANTED_NEW("Most_Wanted.New", "&4&lMOST WANTED &f%player% &7is now the city's Most Wanted &c%stars%"),
		// 0.20.0
		PATROL_PUT_AWAY("Patrol.Put_Away", "&eBeat cop: &fPut that away! &7(%seconds%s)"),
		PATROL_COMPLIED("Patrol.Complied", "&aThanks, carry on."),
		// 0.21.1
		PATROL_IGNORED("Patrol.Ignored", "&cBeat cop: &fYou were warned. &7Cause: &c%cause%"),
		PATROL_LOST_SIGHT("Patrol.Lost_Sight", "&7Beat cop: &fLost sight of you. Keep it holstered."),
		PATROL_PULL_OVER("Patrol.Pull_Over", "&cBeat cop: &fPull over! Stop within %seconds%s"),
		PATROL_TICKET("Patrol.Ticket", "&7Ticket: &c%money_symbol%%amount%"),
		PATROL_FLED("Patrol.Fled", "&cYou fled a traffic stop!"),
		// 0.21.2 S4: who saw it
		WITNESS_NO_WITNESS("Witness.No_Witness", "&7Nobody saw that."),
		WITNESS_COP_CALLING("Witness.Cop_Calling", "&9%reporter% &7is radioing it in - &e%seconds%s"),
		WITNESS_REPORT_CUT("Witness.Report_Cut", "&aThe radio call never went out."),
		WITNESS_DEFERRED("Witness.Deferred", "&7A report on you came in while you were away."),
		// 0.21.2 S5: a silent officer and the scene
		WITNESS_SILENT("Witness.Silent", "&7%unit% &8misses check-in in &e%seconds%s"),
		WITNESS_SCENE_HEADING("Witness.Scene_Heading", "&eUnits heading to %place%"),
		WITNESS_SCENE_COLD("Witness.Scene_Cold", "&7The trail went cold."),
		TRACKER_TAGGED("Tracker.Tagged", "&cYour car has been tagged. Ditch it."),
		TRACKER_HUD("Tracker.Hud", "&cTagged &7%seconds%s"),
		TRESPASS("Trespass", "&cRestricted area!"),
		SUSPECT_UNKNOWN("Suspect.Unknown", "unknown suspect"),
		SUSPECT_BARE("Suspect.Bare", "suspect"),
		SUSPECT_ON_FOOT("Suspect.On_Foot", "on foot"),
		SUSPECT_IN_CAR("Suspect.In_Car", "in a car"),
		SUSPECT_MASK("Suspect.Mask", "in a %head%"),
		SUSPECT_FORMAT("Suspect.Format", "%who%%mask%, %chest%, %vehicle%"),
		// 0.20.0 hot-cash bag and fence
		BAG_HUD("Bag.Hud", "&6Bag: &a%money_symbol%%amount%"),
		BAG_FULL("Bag.Full", "&cYour bag is full."),
		BAG_BANKED("Bag.Banked", "&aYou got away: &f%money_symbol%%amount% &afrom your bag is clean."),
		BAG_OVER_CAP("Bag.Over_Cap",
		             "&7Over today's limit: the fence took &f%money_symbol%%cut% &7of &f%money_symbol%%rest%&7."),
		BAG_CONVERTED("Bag.Converted",
		              "&7Chase over: the fence turned your bag of &f%money_symbol%%bag% &7into &a%money_symbol%%amount%&7."),
		BAG_SEIZED("Bag.Seized", "&cBag seized: &f%money_symbol%%amount%"),
		BAG_PAPERWORK("Bag.Paperwork", "&7Bag seized: &c%money_symbol%%amount%"),
		BAG_SPILLED("Bag.Spilled", "&cYour bag spilled: &f%money_symbol%%amount% &cis on the street."),
		BAG_HOT("Bag.Hot", "&cHot cash! The cops want you now."),
		BAG_BRIBE("Bag.Bribe",
		          "&7Paid &f%money_symbol%%bag% &7from your bag and &f%money_symbol%%wallet% &7from your wallet."),
		FENCE_DONE("Fence.Done", "&aThe fence paid &f%money_symbol%%amount% &afor your bag of &f%money_symbol%%bag%&a."),
		FENCE_EMPTY("Fence.Empty", "&7Your bag is empty."),
		FENCE_CUFFED("Fence.Cuffed", "&cNot while you are in cuffs.");

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

	/** The display name of a crime id: {@code Crimes.<id>} in the file, else the shipped text, else the id with spaces. */
	public String crimeName(String crimeId) {
		return color("Crimes." + crimeId, SHIPPED_CRIMES.getOrDefault(crimeId, crimeId.replace('_', ' ')));
	}

	/** {@code 45s}, or {@code 1m 05s} from a minute up. */
	public static String duration(int seconds) {
		int safe = Math.max(0, seconds);
		return safe < 60 ? safe + "s" : String.format("%dm %02ds", safe / 60, safe % 60);
	}
}
