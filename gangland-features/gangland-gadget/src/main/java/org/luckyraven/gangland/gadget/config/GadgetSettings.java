package org.luckyraven.gangland.gadget.config;

import org.luckyraven.gangland.file.configuration.MovedSetting;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileInitializer;
import org.luckyraven.keystone.persistence.FileManager;

/**
 * {@link GadgetPhysicsConfig} backed by the module's own {@code gadget/gadget_settings.yml} (settings ownership,
 * 0.15.1). Values are parsed once per load/reload in {@link #initialize()}; a customised {@code settings.yml}
 * {@code Gadgets.*} value still wins for one release through {@link MovedSetting}.
 */
public class GadgetSettings implements GadgetPhysicsConfig, FileInitializer {

	private final FileHandler fileHandler;
	private final FileManager fileManager;

	private int    jetpackThrustRampTicks = 20;
	private double jetpackDescentAccel    = 0.022;
	private double jetpackMaxDescentSpeed = -0.5;
	private double jetpackHorizInfluence  = 0.03;
	private double jetpackMaxHorizSpeed   = 0.25;
	private double carReverseSpeedRatio   = 0.5;
	private double carHardBrakeMultiplier = 3.0;
	private int    carFuelConsumePerTick  = 1;

	public GadgetSettings(FileHandler fileHandler, FileManager fileManager) {
		this.fileHandler = fileHandler;
		this.fileManager = fileManager;
	}

	@Override
	public FileHandler getFileHandler() {
		return fileHandler;
	}

	@Override
	public void initialize() {
		MovedSetting moved = MovedSetting.of(fileHandler, fileManager, "gadget");

		jetpackThrustRampTicks = moved.getInt("Gadgets.Jetpack.Thrust_Ramp_Ticks",
		                                      "Gadgets.Jetpack.Thrust_Ramp_Ticks", 20);
		jetpackDescentAccel    = moved.getDouble("Gadgets.Jetpack.Descent_Accel",
		                                         "Gadgets.Jetpack.Descent_Accel", 0.022);
		jetpackMaxDescentSpeed = moved.getDouble("Gadgets.Jetpack.Max_Descent_Speed",
		                                         "Gadgets.Jetpack.Max_Descent_Speed", -0.5);
		jetpackHorizInfluence  = moved.getDouble("Gadgets.Jetpack.Horiz_Influence",
		                                         "Gadgets.Jetpack.Horiz_Influence", 0.03);
		jetpackMaxHorizSpeed   = moved.getDouble("Gadgets.Jetpack.Max_Horiz_Speed",
		                                         "Gadgets.Jetpack.Max_Horiz_Speed", 0.25);

		carReverseSpeedRatio   = moved.getDouble("Gadgets.Car.Reverse_Speed_Ratio",
		                                         "Gadgets.Car.Reverse_Speed_Ratio", 0.5);
		carHardBrakeMultiplier = moved.getDouble("Gadgets.Car.Hard_Brake_Multiplier",
		                                         "Gadgets.Car.Hard_Brake_Multiplier", 3.0);
		carFuelConsumePerTick  = moved.getInt("Gadgets.Car.Fuel_Consume_Per_Tick",
		                                      "Gadgets.Car.Fuel_Consume_Per_Tick", 1);
	}

	@Override
	public int getJetpackThrustRampTicks() {
		return jetpackThrustRampTicks;
	}

	@Override
	public double getJetpackDescentAccel() {
		return jetpackDescentAccel;
	}

	@Override
	public double getJetpackMaxDescentSpeed() {
		return jetpackMaxDescentSpeed;
	}

	@Override
	public double getJetpackHorizInfluence() {
		return jetpackHorizInfluence;
	}

	@Override
	public double getJetpackMaxHorizSpeed() {
		return jetpackMaxHorizSpeed;
	}

	@Override
	public double getCarReverseSpeedRatio() {
		return carReverseSpeedRatio;
	}

	@Override
	public double getCarHardBrakeMultiplier() {
		return carHardBrakeMultiplier;
	}

	@Override
	public int getCarFuelConsumePerTick() {
		return carFuelConsumePerTick;
	}

}
