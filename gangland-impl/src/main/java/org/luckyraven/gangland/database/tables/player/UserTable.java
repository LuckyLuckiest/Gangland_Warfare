package org.luckyraven.gangland.database.tables.player;

import org.bukkit.OfflinePlayer;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.keystone.persistence.database.component.Attribute;
import org.luckyraven.keystone.persistence.database.component.Table;

import java.sql.Types;
import java.util.Map;
import java.util.UUID;

public class UserTable extends Table<User<? extends OfflinePlayer>> {

	public UserTable() {
		super("user");

		Attribute<UUID>    uuid       = new Attribute<>("uuid", true, UUID.class);
		Attribute<Double>  balance    = new Attribute<>("balance", false, Double.class);
		Attribute<Integer> kills      = new Attribute<>("kills", false, Integer.class);
		Attribute<Integer> deaths     = new Attribute<>("deaths", false, Integer.class);
		Attribute<Integer> mobKills   = new Attribute<>("mob_kills", false, Integer.class);
		Attribute<Double>  bounty     = new Attribute<>("bounty", false, Double.class);
		Attribute<Integer> level      = new Attribute<>("level", false, Integer.class);
		Attribute<Double>  experience = new Attribute<>("experience", false, Double.class);
		Attribute<Integer> wanted     = new Attribute<>("wanted", false, Integer.class);
		// ponytail: 4096-char ledger, about 70 posters; a bounty_post table when that is not enough
		Attribute<String>  posters    = new Attribute<>("bounty_posters", false, 4096, String.class);

		balance.setDefaultValue(0D);
		kills.setDefaultValue(0);
		deaths.setDefaultValue(0);
		mobKills.setDefaultValue(0);
		bounty.setDefaultValue(0D);
		level.setDefaultValue(0);
		experience.setDefaultValue(0D);
		wanted.setDefaultValue(0);
		posters.setCanBeNull(true);

		this.addAttribute(uuid);
		this.addAttribute(balance);
		this.addAttribute(kills);
		this.addAttribute(deaths);
		this.addAttribute(mobKills);
		this.addAttribute(bounty);
		this.addAttribute(level);
		this.addAttribute(experience);
		this.addAttribute(wanted);
		this.addAttribute(posters);
	}

	@Override
	public Object[] getData(User<? extends OfflinePlayer> data) {
		return new Object[]{data.getUser().getUniqueId().toString(), data.getEconomy().getAmount().doubleValue(),
		                    data.getKills(),
		                    data.getDeaths(), data.getMobKills(), data.getBounty().getAmount().doubleValue(),
		                    data.getLevel().getLevelValue(), data.getLevel().getExperience(),
		                    data.getWanted().getLevel(), data.getBounty().serializeLedger()};
	}

	@Override
	public Map<String, Object> searchCriteria(User<? extends OfflinePlayer> data) {
		return createSearchCriteria("uuid = ?", new Object[]{data.getUser().getUniqueId().toString()},
		                            new int[]{Types.CHAR}, new int[]{0});
	}
}
