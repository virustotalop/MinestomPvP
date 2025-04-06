package io.github.togar2.pvp.feature.enchantment;

import io.github.togar2.pvp.enchantment.CombatEnchantment;
import io.github.togar2.pvp.enchantment.CombatEnchantments;
import io.github.togar2.pvp.enchantment.EntityGroup;
import io.github.togar2.pvp.enums.ArmorMaterial;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.RegistrableFeature;
import io.github.togar2.pvp.feature.config.DefinedFeature;
import io.github.togar2.pvp.feature.config.FeatureConfiguration;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.entity.EntitySetFireEvent;
import net.minestom.server.event.trait.EntityInstanceEvent;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.component.EnchantmentList;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.registry.DynamicRegistry;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

/**
 * Vanilla implementation of {@link EnchantmentFeature}
 * <p>
 * Utilizes the enchantment classes in the {@link io.github.togar2.pvp.enchantment} package.
 */
public class VanillaEnchantmentFeature implements EnchantmentFeature, RegistrableFeature {
	public static final DefinedFeature<VanillaEnchantmentFeature> DEFINED = new DefinedFeature<>(
			FeatureType.ENCHANTMENT, VanillaEnchantmentFeature::new,
			CombatEnchantments.getAllFeatureDependencies()
	);
	
	private final FeatureConfiguration configuration;
	
	public VanillaEnchantmentFeature(FeatureConfiguration configuration) {
		this.configuration = configuration;
	}
	
	@Override
	public void init(EventNode<EntityInstanceEvent> node) {
		node.addListener(EntitySetFireEvent.class, event -> {
			if (event.getEntity() instanceof LivingEntity living)
				event.setFireTicks(getFireDuration(living, event.getFireTicks()));
		});
	}
	
	public static void forEachEnchantment(Iterable<ItemStack> stacks, BiConsumer<CombatEnchantment, Integer> consumer) {
		for (ItemStack itemStack : stacks) {
			EnchantmentList enchantmentList = itemStack.get(DataComponents.ENCHANTMENTS);
			Set<DynamicRegistry.Key<Enchantment>> enchantments = enchantmentList.enchantments().keySet();
			
			for (DynamicRegistry.Key<Enchantment> enchantment : enchantments) {
				CombatEnchantment combatEnchantment = CombatEnchantments.get(enchantment);
				consumer.accept(combatEnchantment, enchantmentList.level(enchantment));
			}
		}
	}
	
	@Override
	public int getEquipmentLevel(LivingEntity entity, DynamicRegistry.Key<Enchantment> enchantment) {
		Iterator<ItemStack> iterator = CombatEnchantments.get(enchantment).getEquipment(entity).values().iterator();
		
		int total = 0;
		while (iterator.hasNext()) {
			ItemStack itemStack = iterator.next();
			total += itemStack.get(DataComponents.ENCHANTMENTS).level(enchantment);
		}
		
		return total;
	}
	
	@Override
	public Map.Entry<EquipmentSlot, ItemStack> pickRandom(LivingEntity entity, DynamicRegistry.Key<Enchantment> enchantment) {
		Map<EquipmentSlot, ItemStack> equipmentMap = CombatEnchantments.get(enchantment).getEquipment(entity);
		if (equipmentMap.isEmpty()) return null;
		
		List<Map.Entry<EquipmentSlot, ItemStack>> possibleStacks = new ArrayList<>();
		
		for (Map.Entry<EquipmentSlot, ItemStack> entry : equipmentMap.entrySet()) {
			ItemStack itemStack = entry.getValue();
			
			if (!itemStack.isAir() && itemStack.get(DataComponents.ENCHANTMENTS).level(enchantment) > 0) {
				possibleStacks.add(entry);
			}
		}
		
		return possibleStacks.isEmpty() ? null :
				possibleStacks.get(ThreadLocalRandom.current().nextInt(possibleStacks.size()));
	}
	
	@Override
	public int getProtectionAmount(LivingEntity entity, DamageType damageType) {
		AtomicInteger result = new AtomicInteger();
		
		List<ItemStack> armorItems = new ArrayList<>();
		for (EquipmentSlot slot : EquipmentSlot.armors()) {
			if (slot.isArmor() && !entity.getEquipment(slot).isAir()) {
				armorItems.add(entity.getEquipment(slot));
			}
		}
		
		forEachEnchantment(armorItems, (enchantment, level) ->
				result.addAndGet(enchantment.getProtectionAmount(level, damageType, this, configuration)));
		return result.get();
	}
	
	@Override
	public float getAttackDamage(ItemStack stack, EntityGroup group) {
		AtomicReference<Float> result = new AtomicReference<>((float) 0);
		stack.get(DataComponents.ENCHANTMENTS).enchantments().forEach((enchantment, level) -> {
			CombatEnchantment combatEnchantment = CombatEnchantments.get(enchantment);
			result.updateAndGet(v -> v + combatEnchantment.getAttackDamage(level, group, this, configuration));
		});
		
		return result.get();
	}
	
	@Override
	public double getExplosionKnockback(LivingEntity entity, double strength) {
		int level = getEquipmentLevel(entity, Enchantment.BLAST_PROTECTION);
		if (level > 0) strength -= Math.floor((strength * (double) (level * 0.15f)));
		return strength;
	}
	
	@Override
	public int getFireDuration(LivingEntity entity, int duration) {
		int level = getEquipmentLevel(entity, Enchantment.FIRE_PROTECTION);
		if (level > 0) duration -= (int) Math.floor((float) duration * (float) level * 0.15F);
		return duration;
	}
	
	@Override
	public int getKnockback(LivingEntity entity) {
		return getEquipmentLevel(entity, Enchantment.KNOCKBACK);
	}
	
	@Override
	public int getSweeping(LivingEntity entity) {
		return getEquipmentLevel(entity, Enchantment.SWEEPING_EDGE);
	}
	
	@Override
	public int getFireAspect(LivingEntity entity) {
		return getEquipmentLevel(entity, Enchantment.FIRE_ASPECT);
	}
	
	@Override
	public boolean shouldUnbreakingPreventDamage(ItemStack stack) {
		int unbreakingLevel = stack.get(DataComponents.ENCHANTMENTS).level(Enchantment.UNBREAKING);
		if (unbreakingLevel <= 0) return false;
		
		ThreadLocalRandom random = ThreadLocalRandom.current();
		if (ArmorMaterial.fromMaterial(stack.material()) != null && random.nextFloat() < 0.6f) {
			return false;
		} else {
			return random.nextInt(unbreakingLevel + 1) > 0;
		}
	}
	
	@Override
	public void onUserDamaged(LivingEntity user, LivingEntity attacker) {
		forEachEnchantment(Arrays.asList(
				user.getBoots(), user.getLeggings(),
				user.getChestplate(), user.getHelmet(),
				user.getItemInMainHand(), user.getItemInOffHand()
		), (enchantment, level) -> enchantment.onUserDamaged(user, attacker, level, this, configuration));
	}
	
	@Override
	public void onTargetDamaged(LivingEntity user, Entity target) {
		forEachEnchantment(Arrays.asList(
				user.getBoots(), user.getLeggings(),
				user.getChestplate(), user.getHelmet(),
				user.getItemInMainHand(), user.getItemInOffHand()
		), (enchantment, level) -> enchantment.onTargetDamaged(user, target, level, this, configuration));
	}
}
