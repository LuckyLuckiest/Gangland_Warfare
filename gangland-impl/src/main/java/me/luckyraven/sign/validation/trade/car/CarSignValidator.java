package me.luckyraven.sign.validation.trade.car;

import me.luckyraven.file.configuration.Settings;
import me.luckyraven.gadget.car.CarManager;
import me.luckyraven.sign.SignType;
import me.luckyraven.sign.validation.AbstractSignValidator;

public class CarSignValidator extends AbstractSignValidator {

	private final CarManager carManager;

	public CarSignValidator(SignType signType, CarManager carManager) {
		super(signType, Settings.getMoneySymbol());

		this.carManager = carManager;
	}

	@Override
	protected boolean isValidContent(String content) {
		return carManager.getCar(content) != null;
	}

}
