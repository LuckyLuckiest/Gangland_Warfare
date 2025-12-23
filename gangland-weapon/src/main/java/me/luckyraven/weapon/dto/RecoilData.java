package me.luckyraven.weapon.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import me.luckyraven.exception.PluginException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecoilData implements Cloneable {

	private double         amount;
	private double         pushVelocity;
	private double         pushPowerUp;
	private List<String[]> pattern = new ArrayList<>();

	@Override
	public RecoilData clone() {
		RecoilData recoilData;

		try {
			recoilData = (RecoilData) super.clone();
		} catch (CloneNotSupportedException exception) {
			throw new PluginException(exception);
		}

		List<String[]> patternCopy = new ArrayList<>();

		for (String[] arr : pattern) {
			patternCopy.add(arr.clone());
		}

		recoilData.setPattern(patternCopy);

		return recoilData;
	}

	@Override
	public String toString() {
		return String.format("RecoilConfig{amount=%.2f,pushVelocity=%.2f,pushPowerUp=%.2f,pattern=%s}", amount,
							 pushVelocity, pushPowerUp, pattern.stream().map(Arrays::toString).toList());
	}

}
