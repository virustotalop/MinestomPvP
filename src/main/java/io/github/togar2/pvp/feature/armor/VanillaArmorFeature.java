package io.github.togar2.pvp.feature.armor;

import io.github.togar2.pvp.damage.DamageTypeInfo;
import io.github.togar2.pvp.enchantment.EnchantmentUtils;
import io.github.togar2.pvp.entity.EntityUtils;
import io.github.togar2.pvp.feature.CombatFeature;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.config.DefinedFeature;
import io.github.togar2.pvp.feature.config.FeatureConfiguration;
import io.github.togar2.pvp.utils.CombatVersion;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.potion.PotionEffect;
import net.minestom.server.potion.TimedPotion;
import net.minestom.server.utils.MathUtils;

public class VanillaArmorFeature implements ArmorFeature, CombatFeature {
	public static final DefinedFeature<VanillaArmorFeature> DEFINED = new DefinedFeature<>(
			FeatureType.ARMOR, VanillaArmorFeature::new,
			FeatureType.VERSION
	);
	
	private final CombatVersion version;
	
	public VanillaArmorFeature(FeatureConfiguration configuration) {
		this.version = configuration.get(FeatureType.VERSION);
	}
	
	@Override
	public float getDamageWithProtection(LivingEntity entity, DamageType type, float amount) {
		DamageTypeInfo info = DamageTypeInfo.of(type);
		amount = getDamageWithArmor(entity, info, amount);
		return getDamageWithEnchantments(entity, info, amount);
	}
	
	protected float getDamageWithArmor(LivingEntity entity, DamageTypeInfo typeInfo, float amount) {
		if (typeInfo.bypassesArmor()) return amount;
		
		double armorValue = entity.getAttributeValue(Attribute.GENERIC_ARMOR);
		if (version.legacy()) {
			int armorMultiplier = 25 - (int) armorValue;
			return (amount * (float) armorMultiplier) / 25;
		} else {
			return getDamageLeft(
					amount, (float) Math.floor(armorValue),
					(float) entity.getAttributeValue(Attribute.GENERIC_ARMOR_TOUGHNESS)
			);
		}
	}
	
	protected float getDamageWithEnchantments(LivingEntity entity, DamageTypeInfo typeInfo, float amount) {
		if (typeInfo.unblockable()) return amount;
		
		int k;
		TimedPotion effect = entity.getEffect(PotionEffect.RESISTANCE);
		if (effect != null) {
			k = (effect.potion().amplifier() + 1) * 5;
			int j = 25 - k;
			float f = amount * (float) j;
			amount = Math.max(f / 25, 0);
		}
		
		if (amount <= 0) {
			return 0;
		} else {
			k = EnchantmentUtils.getProtectionAmount(EntityUtils.getArmorItems(entity), typeInfo);
			if (version.modern()) {
				if (k > 0) {
					amount = getDamageAfterProtectionEnchantment(amount, (float) k);
				}
			} else {
				if (k > 20) {
					k = 20;
				}
				
				if (k > 0) {
					int j = 25 - k;
					float f = amount * (float) j;
					amount = f / 25;
				}
			}
			
			return amount;
		}
	}
	
	protected float getDamageLeft(float damage, float armor, float armorToughness) {
		float f = 2.0f + armorToughness / 4.0f;
		float g = MathUtils.clamp(armor - damage / f, armor * 0.2f, 20.0f);
		return damage * (1.0F - g / 25.0F);
	}
	
	protected float getDamageAfterProtectionEnchantment(float damageDealt, float protection) {
		float f = MathUtils.clamp(protection, 0.0f, 20.0f);
		return damageDealt * (1.0f - f / 25.0f);
	}
}
