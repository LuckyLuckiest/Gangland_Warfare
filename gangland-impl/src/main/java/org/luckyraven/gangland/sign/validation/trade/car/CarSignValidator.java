package org.luckyraven.gangland.sign.validation.trade.car;

import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gadget.car.CarManager;
import org.luckyraven.gangland.sign.SignType;
import org.luckyraven.gangland.sign.validation.AbstractSignValidator;

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
