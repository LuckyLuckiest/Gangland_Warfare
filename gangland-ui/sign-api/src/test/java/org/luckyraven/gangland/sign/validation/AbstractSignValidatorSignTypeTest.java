package org.luckyraven.gangland.sign.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.luckyraven.gangland.sign.SignType;

import static org.junit.jupiter.api.Assertions.assertSame;

@DisplayName("AbstractSignValidator - getSignType")
class AbstractSignValidatorSignTypeTest {

	@Test
	@DisplayName("getSignType returns the sign type passed to the constructor, as seen through SignValidator")
	void getSignType_returnsTheConstructorArgument() {
		SignType      type      = new SignType("glw-buy", "buy");
		SignValidator validator = new AbstractSignValidator(type, "$") {
			@Override
			protected boolean isValidContent(String content) {
				return true;
			}

			@Override
			protected void performCustomValidation(String[] lines) {
			}
		};

		assertSame(type, validator.getSignType());
	}
}
