package me.luckyraven.rank;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.luckyraven.Gangland;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public class RankManager {

	private final List<Rank> ranks;

	public RankManager() {
		ranks = new LinkedList<>();
	}

	public RankManager(List<Rank> ranks) {
		this.ranks = ranks;
	}

	public void processRanks() {
		JsonElement jsonElement = JsonParser.parseReader(
				new InputStreamReader(Objects.requireNonNull(Gangland.class.getResourceAsStream("/ranks.json"))));
		JsonObject jsonObject = jsonElement.getAsJsonObject();
		JsonObject ind;
		JsonArray  permsArray;
		for (String property : jsonObject.keySet()) {
			ind = jsonObject.get(property).getAsJsonObject();
			permsArray = ind.get("permissions").getAsJsonArray();
			List<String> perms = new ArrayList<>();
			for (JsonElement elem : permsArray) perms.add(elem.getAsString());
			add(new Rank(property, perms));
		}
	}

	public void add(Rank element) {
		ranks.add(element);
	}

	public void remove(Rank element) {
		ranks.remove(element);
	}

	public Rank get(int id) {
		for (Rank rank : ranks)
			if (rank.match(id)) return rank;
		return null;
	}

	public List<Rank> getRanks() {
		return new ArrayList<>(ranks);
	}

	@Override
	public String toString() {
		return String.format("ranks=%s", ranks);
	}

}
