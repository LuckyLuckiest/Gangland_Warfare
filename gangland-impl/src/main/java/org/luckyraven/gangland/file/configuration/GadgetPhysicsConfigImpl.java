package org.luckyraven.gangland.file.configuration;

import org.luckyraven.gangland.gadget.config.GadgetPhysicsConfig;

/**
 * Reads gadget physics values from the static fields populated by {@link Settings}.
 */
public class GadgetPhysicsConfigImpl implements GadgetPhysicsConfig {

	@Override
	public int getJetpackThrustRampTicks() {
		return Settings.getGadgetJetpackThrustRampTicks();
	}

	@Override
	public double getJetpackDescentAccel() {
		return Settings.getGadgetJetpackDescentAccel();
	}

	@Override
	public double getJetpackMaxDescentSpeed() {
		return Settings.getGadgetJetpackMaxDescentSpeed();
	}

	@Override
	public double getJetpackHorizInfluence() {
		return Settings.getGadgetJetpackHorizInfluence();
	}

	@Override
	public double getJetpackMaxHorizSpeed() {
		return Settings.getGadgetJetpackMaxHorizSpeed();
	}

	@Override
	public double getCarReverseSpeedRatio() {
		return Settings.getGadgetCarReverseSpeedRatio();
	}

	@Override
	public double getCarHardBrakeMultiplier() {
		return Settings.getGadgetCarHardBrakeMultiplier();
	}

	@Override
	public int getCarFuelConsumePerTick() {
		return Settings.getGadgetCarFuelConsumePerTick();
	}

}
