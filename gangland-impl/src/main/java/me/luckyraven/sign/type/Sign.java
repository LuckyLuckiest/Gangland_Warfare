package me.luckyraven.sign.type;

import me.luckyraven.sign.model.SignFormat;
import me.luckyraven.sign.registry.SignTypeDefinition;

public interface Sign {

	SignTypeDefinition createDefinition();

	SignFormat createFormat();

}
