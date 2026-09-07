package org.luckyraven.gangland.sign.type;

import org.luckyraven.gangland.sign.model.SignFormat;
import org.luckyraven.gangland.sign.registry.SignTypeDefinition;

public interface Sign {

	SignTypeDefinition createDefinition();

	SignFormat createFormat();

}
