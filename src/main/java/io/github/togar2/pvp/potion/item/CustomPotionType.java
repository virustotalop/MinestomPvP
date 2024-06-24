package io.github.togar2.pvp.potion.item;

import io.github.togar2.pvp.utils.CombatVersion;
import net.minestom.server.potion.Potion;
import net.minestom.server.potion.PotionType;

import java.util.List;

public class CustomPotionType {
	private final PotionType potionType;
	private final List<Potion> effects;
	private List<Potion> legacyEffects;
	
	public CustomPotionType(PotionType potionType, Potion... effects) {
		this.potionType = potionType;
		this.effects = List.of(effects);
	}
	
	public CustomPotionType legacy(Potion... effects) {
		legacyEffects = List.of(effects);
		return this;
	}
	
	public PotionType getPotionType() {
		return potionType;
	}
	
	public List<Potion> getEffects(CombatVersion version) {
		if (legacyEffects == null) return effects;
		return version.legacy() ? legacyEffects : effects;
	}
}
