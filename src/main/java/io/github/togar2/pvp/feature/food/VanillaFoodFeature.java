package io.github.togar2.pvp.feature.food;

import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.RegistrableFeature;
import io.github.togar2.pvp.feature.config.DefinedFeature;
import io.github.togar2.pvp.feature.config.FeatureConfiguration;
import io.github.togar2.pvp.feature.cooldown.ItemCooldownFeature;
import io.github.togar2.pvp.utils.PotionFlags;
import io.github.togar2.pvp.utils.ViewUtil;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.item.PlayerFinishItemUseEvent;
import net.minestom.server.event.player.PlayerPreEatEvent;
import net.minestom.server.event.player.PlayerTickEvent;
import net.minestom.server.event.trait.EntityInstanceEvent;
import net.minestom.server.item.ItemComponent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.Consumable;
import net.minestom.server.item.component.ConsumeEffect;
import net.minestom.server.item.component.ConsumeEffect.*;
import net.minestom.server.item.component.Food;
import net.minestom.server.item.component.SuspiciousStewEffects;
import net.minestom.server.potion.CustomPotionEffect;
import net.minestom.server.potion.Potion;
import net.minestom.server.potion.PotionEffect;
import net.minestom.server.potion.TimedPotion;
import net.minestom.server.registry.ObjectSet;
import net.minestom.server.sound.SoundEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Vanilla implementation of {@link FoodFeature}
 * <p>
 * This also includes eating of food items.
 */
public class VanillaFoodFeature implements FoodFeature, RegistrableFeature {
	public static final DefinedFeature<VanillaFoodFeature> DEFINED = new DefinedFeature<>(
			FeatureType.FOOD, VanillaFoodFeature::new,
			FeatureType.ITEM_COOLDOWN
	);
	
	private final FeatureConfiguration configuration;
	
	private ItemCooldownFeature itemCooldownFeature;
	
	public VanillaFoodFeature(FeatureConfiguration configuration) {
		this.configuration = configuration;
	}
	
	@Override
	public void initDependencies() {
		this.itemCooldownFeature = configuration.get(FeatureType.ITEM_COOLDOWN);
	}
	
	@Override
	public void init(EventNode<EntityInstanceEvent> node) {
		node.addListener(PlayerPreEatEvent.class, event -> {
			if (!event.getItemStack().has(ItemComponent.CONSUMABLE))
				return;
			@Nullable Food foodComponent = event.getItemStack().get(ItemComponent.FOOD);
			@Nullable Consumable consumableComponent = event.getItemStack().get(ItemComponent.CONSUMABLE);
			
			// If the players hunger is full and the food is not always edible, cancel
			// For some reason vanilla doesn't say honey is always edible but just overrides the method to always consume it
			boolean alwaysEat = foodComponent == null || foodComponent.canAlwaysEat() || event.getItemStack().material() == Material.HONEY_BOTTLE;
			if (event.getPlayer().getGameMode() != GameMode.CREATIVE
					&& !alwaysEat && event.getPlayer().getFood() == 20) {
				event.setCancelled(true);
				return;
			}
			
			if (consumableComponent != null) event.setEatingTime(consumableComponent.consumeTicks());
		});
		
		node.addListener(PlayerFinishItemUseEvent.class, event -> {
			if (event.getItemStack().material() != Material.MILK_BUCKET
					&& !(event.getItemStack().has(ItemComponent.FOOD) || event.getItemStack().has(ItemComponent.CONSUMABLE)))
				return;
			
			onFinishEating(event.getPlayer(), event.getItemStack(), event.getHand());
		});
		
		node.addListener(PlayerTickEvent.class, event -> {
			Player player = event.getPlayer();
			if (player.isSilent() || !player.isEating()) return;
			
			tickEatingSounds(player);
		});
	}
	
	protected void onFinishEating(Player player, ItemStack stack, PlayerHand hand) {
		this.eat(player, stack);
		
		Food food = stack.get(ItemComponent.FOOD);
		Consumable consumable = stack.get(ItemComponent.CONSUMABLE);
		if (consumable == null) return;
		ThreadLocalRandom random = ThreadLocalRandom.current();
		
		triggerEatingSound(player, consumable);
		
		if (food != null) {
			ViewUtil.viewersAndSelf(player).playSound(Sound.sound(
					SoundEvent.ENTITY_PLAYER_BURP, Sound.Source.PLAYER,
					0.5f, random.nextFloat() * 0.1f + 0.9f
			), player);
		}
		
		List<ConsumeEffect> effectList = consumable.effects();
		
		for (ConsumeEffect effect : effectList) {
			switch (effect) {
				case ApplyEffects(List<CustomPotionEffect> effects, float probability) -> {
					if (random.nextFloat() >= probability) continue;
					for (CustomPotionEffect potionEffect : effects) {
						player.addEffect(new Potion(
								potionEffect.id(), (byte)potionEffect.amplifier(),
								potionEffect.duration(),
								PotionFlags.create(
										potionEffect.isAmbient(),
										potionEffect.showParticles(),
										potionEffect.showIcon()
								)
						));
					}
					return;
				}
				case RemoveEffects(ObjectSet<PotionEffect> potionEffects) -> {
					player.getActiveEffects().stream().map(TimedPotion::potion).map(Potion::effect).filter(potionEffects::contains).forEach(player::removeEffect);
					return;
				}
				case ClearAllEffects clearEffects -> {
					player.clearEffects();
					return;
				}
				case TeleportRandomly(float diameter) -> {
					ChorusFruitUtil.tryChorusTeleport(player, itemCooldownFeature, diameter);
					return;
				}
				case PlaySound(SoundEvent sound) -> {
					ViewUtil.viewersAndSelf(player).playSound(Sound.sound().type(sound).build(), player);
					return;
				}
				default -> throw new IllegalArgumentException("Unexpected value: " + effect);
			}
		}
		
		if (stack.has(ItemComponent.SUSPICIOUS_STEW_EFFECTS)) {
			SuspiciousStewEffects effects = stack.get(ItemComponent.SUSPICIOUS_STEW_EFFECTS);
			assert effects != null;
			for (SuspiciousStewEffects.Effect effect : effects.effects()) {
				player.addEffect(new Potion(effect.id(), (byte) 0, effect.durationTicks(), PotionFlags.defaultFlags()));
			}
		}
		
		ItemStack leftOver = getUsingConvertsTo(stack);
		if (leftOver.isAir()) leftOver = getUsingConvertsTo(stack);

		if (player.getGameMode() != GameMode.CREATIVE) {
			if (leftOver != null && !leftOver.isAir()) {
				if (stack.amount() == 1) {
					player.setItemInHand(hand, leftOver);
				} else {
					player.setItemInHand(hand, stack.withAmount(stack.amount() - 1));
					player.getInventory().addItemStack(leftOver);
				}
			} else {
				player.setItemInHand(hand, stack.withAmount(stack.amount() - 1));
			}
		}
	}
	
	@Override
	public void addFood(Player player, int food, float saturation) {
		player.setFood(Math.min(food + player.getFood(), 20));
		player.setFoodSaturation(Math.min(player.getFoodSaturation() + saturation, player.getFood()));
	}
	
	@Override
	public void eat(Player player, int food, float saturationModifier) {
		addFood(player, food, (float) food * saturationModifier * 2.0f);
	}
	
	@Override
	public void eat(Player player, ItemStack stack) {
		Food foodComponent = stack.get(ItemComponent.FOOD);
		if (foodComponent == null) return;
		addFood(player, foodComponent.nutrition(), foodComponent.saturationModifier());
	}
	
	@Override
	public void applySaturationEffect(Player player, int amplifier) {
		eat(player, amplifier + 1, 1.0f);
	}
	
	protected void tickEatingSounds(Player player) {
		ItemStack stack = player.getItemInHand(Objects.requireNonNull(player.getItemUseHand()));
		
		Consumable component = stack.get(ItemComponent.CONSUMABLE);
		if (component == null) return;
		
		long useTime = component.consumeTicks();
		long usedTicks = player.getCurrentItemUseTime();
		long remainingUseTicks = useTime - usedTicks;
		
		boolean canTrigger = component.consumeTicks() < 32 || remainingUseTicks <= useTime - 7;
		boolean shouldTrigger = canTrigger && remainingUseTicks % 4 == 0;
		if (!shouldTrigger) return;
		
		triggerEatingSound(player, component);
	}
	
	protected void triggerEatingSound(Player player, Consumable consumable) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		player.getViewersAsAudience().playSound(Sound.sound(
				consumable.sound(), Sound.Source.PLAYER,
				0.5f + 0.5f * random.nextInt(2),
				(random.nextFloat() - random.nextFloat()) * 0.2f + 1.0f
		), player);
	}
	
	protected static final ItemStack EMPTY_BUCKET = ItemStack.of(Material.BUCKET);
	protected static final ItemStack EMPTY_BOTTLE = ItemStack.of(Material.GLASS_BOTTLE);
	protected static final ItemStack EMPTY_BOWL = ItemStack.of(Material.BOWL);
	
	protected ItemStack getUsingConvertsTo(ItemStack stack) {
		if (stack.material() == Material.MILK_BUCKET) {
			return EMPTY_BUCKET;
		} else if (stack.material() == Material.HONEY_BOTTLE) {
			return EMPTY_BOTTLE;
		} else if (stack.material() == Material.SUSPICIOUS_STEW) {
			return EMPTY_BOWL;
		} else if (stack.material() == Material.MUSHROOM_STEW) {
			return EMPTY_BOWL;
		} else if (stack.material() == Material.BEETROOT_SOUP) {
			return EMPTY_BOWL;
		} else if (stack.material() == Material.RABBIT_STEW) {
			return EMPTY_BOWL;
		}
		
		return ItemStack.AIR;
	}
}
