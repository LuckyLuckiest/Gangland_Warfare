package org.luckyraven.gangland.copsncrooks.npc.police;

import com.cryptomorin.xseries.particles.XParticle;
import lombok.CustomLog;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.copsncrooks.npc.police.config.CopConfigProvider;
import org.luckyraven.gangland.copsncrooks.npc.police.npc.CopNpc;
import org.luckyraven.gangland.copsncrooks.npc.police.radio.CopRadio;
import org.luckyraven.gangland.copsncrooks.npc.police.state.CopState;
import org.luckyraven.gangland.npc.FieldCareSettings;
import org.luckyraven.keystone.npc.AbstractNpc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Field care for one {@link CopManager} ({@code Cops.Field_Care}), run once per group at the end of each AI tick. A
 * cop at or below the hurt threshold limps, bleeds and radios {@code Hit} once; a Medic-role cop of its group walks
 * over (its fighting behaviour yields movement to this class, still firing), the patient holds still and crouches
 * while treated, and after {@code Channel_Ticks} in range the patient gets {@code Heal_Fraction} of its max health
 * back. A hit on the medic starts the channel over ({@code Medic_Pinned}). No medic in the group, no healing.
 * <p>
 * No static state: per-cop state is weak-keyed, and every treatment ends here, on a cop leaving the fight
 * ({@link CopNpc#transitionTo} drops the care slots on both sides) or through {@link #clear} when its group is dropped.
 */
@CustomLog
public class CopFieldCare {

	/** A medic that has not finished a treatment this long after taking it on gives up. */
	// ponytail: code constant, a Field_Care.Give_Up_Ticks key when owners want to tune it
	static final long GIVE_UP_MS  = 15_000;
	/** Once treating, the patient may drift this far past {@code Heal_Range} before the channel starts over. */
	static final double HOLD_MARGIN = 1.0;
	/** Bleed particles per AI tick while hurt. */
	private static final int BLEED_COUNT = 3;

	private final Supplier<CopConfigProvider> provider;
	private final CopRadio                    radio;
	private final LongSupplier                clock;

	/** The cops currently hurt (limping). Weak: a despawned cop drops out. */
	private final Set<CopNpc>            hurt       = Collections.newSetFromMap(new WeakHashMap<>());
	/** Active treatments, keyed by medic: the medic may already be gone from its group's cop list. */
	private final Map<CopNpc, Treatment> treatments = new WeakHashMap<>();
	/** Patients whose heal another plugin cancelled, and when: not treated again for {@link #GIVE_UP_MS}. */
	private final Map<CopNpc, Long>      refused    = new WeakHashMap<>();

	/** The treatment's group is the medic's ({@link CopNpc#getGroup()}): no group reference is held here. */
	private static final class Treatment {
		final CopNpc patient;
		final long   startedAt;
		int          progressTicks;
		double       lastMedicHealth;

		Treatment(CopNpc patient, long startedAt, double medicHealth) {
			this.patient         = patient;
			this.startedAt       = startedAt;
			this.lastMedicHealth = medicHealth;
		}
	}

	public CopFieldCare(Supplier<CopConfigProvider> provider, CopRadio radio, LongSupplier clock) {
		this.provider = provider;
		this.radio    = radio;
		this.clock    = clock;
	}

	/** One field-care step for {@code group}: hurt edges, new treatments, then every running treatment. */
	public void tick(CopGroup group) {
		CopConfigProvider cfg = provider.get();
		if (cfg == null) return;
		FieldCareSettings settings = cfg.getFieldCareSettings();

		List<CopNpc> cops;
		synchronized (group.getCops()) {
			cops = new ArrayList<>(group.getCops());
		}
		for (CopNpc cop : cops) {
			// a medic killed and collected before end() ran leaves no treatment behind: stand its patient up
			if (cop.isUnderCare() && !hasMedic(cop)) cop.setUnderCare(false);
			updateHurt(group, cop, settings);
		}
		if (settings.medicEnabled()) assign(group, cops, settings);
		step(group, settings, cfg.getAiTickRate());
	}

	/** The limp and the {@code Hit} line on the hurt edge, the normal speed back on the way up; bleeding while hurt. */
	private void updateHurt(CopGroup group, CopNpc cop, FieldCareSettings settings) {
		LivingEntity self = cop.getEntity();
		if (!cop.isValid() || self == null) return;

		boolean isHurt = settings.isHurt(self.getHealth(), self.getMaxHealth());
		if (isHurt != hurt.contains(cop)) {
			cop.applySpeed(isHurt ? settings.limpSpeed() : 1.0);
			if (isHurt) {
				hurt.add(cop);
				log.debug("{} hurt ({}/{}), limping at x{}", CopRadio.callsign(cop), self.getHealth(),
				          self.getMaxHealth(), settings.limpSpeed());
				radio.sayAs(group, cop, "Hit", Map.of());
			} else {
				hurt.remove(cop);
			}
		}
		if (isHurt) particles(self, XParticle.DAMAGE_INDICATOR, BLEED_COUNT);
	}

	/** Each hurt, fighting cop with no medic yet gets the nearest free medic of its group within Medic_Radius. */
	private void assign(CopGroup group, List<CopNpc> cops, FieldCareSettings settings) {
		long now = clock.getAsLong();
		for (CopNpc patient : cops) {
			LivingEntity body = patient.getEntity();
			if (!hurt.contains(patient) || !fighting(patient) || body == null || hasMedic(patient)) continue;
			Long refusedAt = refused.get(patient);
			if (refusedAt != null && now - refusedAt < GIVE_UP_MS) continue;

			CopNpc medic   = null;
			double nearest = settings.medicRadius();
			for (CopNpc candidate : cops) {
				if (candidate == patient || !canTreat(candidate)) continue;
				double distance = candidate.distanceTo(body);
				if (distance <= nearest) {
					nearest = distance;
					medic   = candidate;
				}
			}
			if (medic == null) continue;

			medic.setPatient(patient);
			treatments.put(medic, new Treatment(patient, now, medic.getEntity().getHealth()));
			log.debug("{} treats {}", CopRadio.callsign(medic), CopRadio.callsign(patient));
			// follow-ups to the patient's Hit, one ack delay apart: said now, the squad and player gaps swallow them
			radio.sayAsLater(group, medic, "Medic_Moving", Map.of("member", CopRadio.callsign(patient)), 1);
			coveringFire(group, cops, medic, patient);
		}
	}

	/** A live, fighting Medic-role cop that is neither hurt nor busy with a treatment. */
	private boolean canTreat(CopNpc cop) {
		return cop.isValid() && cop.getEntity() != null && cop.getRole() != null && cop.getRole().medic() &&
		       fighting(cop) && !hurt.contains(cop) && !cop.isUnderCare() && cop.getPatient() == null &&
		       !treatments.containsKey(cop);
	}

	private boolean hasMedic(CopNpc patient) {
		for (Treatment treatment : treatments.values())
			if (treatment.patient == patient) return true;
		return false;
	}

	/**
	 * The squad covers the medic: a radio line only ({@code Covering_Fire}), from the leader unless it is the medic or
	 * the patient, then from any other fighting cop of the group, else nobody.
	 */
	private void coveringFire(CopGroup group, List<CopNpc> cops, CopNpc medic, CopNpc patient) {
		AbstractNpc leader  = group.getSquad().leader();
		CopNpc      speaker = leader instanceof CopNpc cop && cop != medic && cop != patient && cop.isValid() ? cop
		                                                                                                  : null;
		for (Iterator<CopNpc> it = cops.iterator(); speaker == null && it.hasNext(); ) {
			CopNpc cop = it.next();
			if (cop != medic && cop != patient && cop.isValid() && fighting(cop)) speaker = cop;
		}
		if (speaker != null) radio.sayAsLater(group, speaker, "Covering_Fire", Map.of(), 2);
	}

	/** Ends every treatment whose medic or patient belongs to {@code group}: the group is being dropped. */
	public void clear(CopGroup group) {
		for (Iterator<Map.Entry<CopNpc, Treatment>> it = treatments.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<CopNpc, Treatment> entry = it.next();
			CopNpc medic = entry.getKey();
			if (medic == null || medic.getGroup() != group && entry.getValue().patient.getGroup() != group) continue;
			end(medic, entry.getValue());
			it.remove();
		}
	}

	/**
	 * Walks, channels or ends each of {@code group}'s treatments. A treatment whose medic is gone, or no longer in its
	 * patient's group (a radio responder moved on), is ended by whichever group ticks first.
	 */
	private void step(CopGroup group, FieldCareSettings settings, int aiTickRate) {
		long now = clock.getAsLong();
		for (Iterator<Map.Entry<CopNpc, Treatment>> it = treatments.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<CopNpc, Treatment> entry = it.next();
			CopNpc    medic     = entry.getKey();
			Treatment treatment = entry.getValue();
			if (medic == null) continue;
			CopGroup own = medic.getGroup();
			if (own != group && medic.isValid() && own != null && own == treatment.patient.getGroup()) continue;

			if (!ongoing(group, medic, treatment, settings, now) || channel(group, medic, treatment, settings,
			                                                                aiTickRate)) {
				end(medic, treatment);
				it.remove();
			}
		}
	}

	private boolean ongoing(CopGroup group, CopNpc medic, Treatment treatment, FieldCareSettings settings, long now) {
		CopNpc       patient     = treatment.patient;
		LivingEntity medicBody   = medic.getEntity();
		LivingEntity patientBody = patient.getEntity();
		return settings.medicEnabled() && medic.isValid() && patient.isValid() && medicBody != null &&
		       patientBody != null && medic.getPatient() == patient && fighting(medic) && fighting(patient) &&
		       medic.getGroup() == group && patient.getGroup() == group &&
		       settings.isHurt(patientBody.getHealth(), patientBody.getMaxHealth()) &&
		       !settings.isHurt(medicBody.getHealth(), medicBody.getMaxHealth()) &&
		       now - treatment.startedAt < GIVE_UP_MS;
	}

	/**
	 * Out of {@code Heal_Range} (plus {@link #HOLD_MARGIN} once under care): walks the medic over by a direct route,
	 * patient free to move. In range: both hold still, the patient under care, and the channel advances by one AI
	 * tick; a hit on the medic starts it over.
	 *
	 * @return {@code true} once the patient is healed, or its heal was cancelled (the treatment is over).
	 */
	private boolean channel(CopGroup group, CopNpc medic, Treatment treatment, FieldCareSettings settings,
	                        int aiTickRate) {
		CopNpc       patient     = treatment.patient;
		LivingEntity patientBody = patient.getEntity();
		double       health      = medic.getEntity().getHealth();
		boolean      hit         = health < treatment.lastMedicHealth;
		treatment.lastMedicHealth = health;

		double reach = patient.isUnderCare() ? settings.healRange() + HOLD_MARGIN : settings.healRange();
		if (medic.distanceTo(patientBody) > reach) {
			if (patient.isUnderCare()) patient.setUnderCare(false);
			treatment.progressTicks = 0;
			medic.navigateTo(patientBody.getLocation());
			return false;
		}

		// both stop this tick: the patient's own behaviour would only pause it on the next AI tick
		medic.pauseNavigation();
		patient.pauseNavigation();
		if (!patient.isUnderCare()) patient.setUnderCare(true);
		if (hit) {
			treatment.progressTicks = 0;
			radio.sayAs(group, medic, "Medic_Pinned", Map.of("member", CopRadio.callsign(patient)));
			return false;
		}

		treatment.progressTicks += aiTickRate;
		if (treatment.progressTicks < settings.channelTicks()) return false;

		if (!heal(patientBody, settings.healFraction())) {
			refused.put(patient, clock.getAsLong());
			log.debug("heal of {} cancelled", CopRadio.callsign(patient));
			return true;
		}
		radio.sayAs(group, medic, "Patched_Up", Map.of("member", CopRadio.callsign(patient)));
		return true;
	}

	/**
	 * Gives {@code body} {@code fraction} of its max health back, through an {@link EntityRegainHealthEvent}
	 * ({@code CUSTOM}) so health displays refresh and other plugins may change or cancel it.
	 *
	 * @return {@code false} when the event was cancelled (nothing healed).
	 */
	private static boolean heal(LivingEntity body, double fraction) {
		double max = body.getMaxHealth();
		EntityRegainHealthEvent event = new EntityRegainHealthEvent(body, max * fraction,
		                                                            EntityRegainHealthEvent.RegainReason.CUSTOM);
		Bukkit.getPluginManager().callEvent(event);
		if (event.isCancelled()) return false;

		body.setHealth(Math.min(max, body.getHealth() + event.getAmount()));
		log.debug("healed to {}/{}", body.getHealth(), max);
		particles(body, XParticle.HEART, 5);
		return true;
	}

	/** Ends a treatment on both sides: the medic free again, the patient standing up. */
	private static void end(CopNpc medic, Treatment treatment) {
		if (medic.getPatient() == treatment.patient) medic.setPatient(null);
		if (treatment.patient.isUnderCare()) treatment.patient.setUnderCare(false);
	}

	private static boolean fighting(CopNpc cop) {
		CopState state = cop.getCurrentState();
		return state == CopState.PURSUING || state == CopState.COMBAT;
	}

	private static void particles(LivingEntity body, XParticle type, int count) {
		Particle particle = type.get();
		if (particle == null || body.getWorld() == null) return;
		Location at = body.getLocation().add(0, 1.0, 0);
		body.getWorld().spawnParticle(particle, at, count, 0.25, 0.4, 0.25, 0);
	}
}
