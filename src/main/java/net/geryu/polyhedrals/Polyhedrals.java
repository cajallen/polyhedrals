package net.geryu.polyhedrals;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Mod(Polyhedrals.MODID)
public class Polyhedrals {
    public static final String MODID = "polyhedrals";

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MODID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, MODID);
    public static final DeferredRegister<MapCodec<? extends ICondition>> CONDITIONS = DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, MODID);

    static {
        CONDITIONS.register("recipes_enabled", () -> PolyhedralsConfig.RecipesEnabled.CODEC);
    }

    public static final Supplier<RecipeSerializer<DiceCraftingRecipe>> DICE_CRAFTING = RECIPE_SERIALIZERS.register("dice_crafting",
            () -> new SimpleCraftingRecipeSerializer<>(DiceCraftingRecipe::new));

    public static final Supplier<AttachmentType<DiceColors>> DEFAULT_COLORS = ATTACHMENT_TYPES.register("default_colors",
            () -> AttachmentType.builder(() -> DiceColors.DEFAULT).serialize(DiceColors.CODEC).copyOnDeath().build());

    public static final Supplier<DataComponentType<DiceColors>> TWO_TONE_COLORS =
            COMPONENTS.registerComponentType("two_tone_colors", builder -> builder.persistent(DiceColors.CODEC));

    public static final List<Integer> DIE_SIDES = List.of(4, 6, 8, 10, 12, 20, 100);
    public static final Map<Integer, DeferredItem<Item>> DICE = new LinkedHashMap<>();

    static {
        for (int sides : DIE_SIDES) {
            DICE.put(sides, ITEMS.register("d" + sides, () -> new DiceItem(sides,
                    new Item.Properties().component(TWO_TONE_COLORS.get(), DiceColors.DEFAULT))));
        }
    }

    public static final Supplier<EntityType<DiceEntity>> DICE_ENTITY = ENTITIES.register("dice", () ->
            EntityType.Builder.<DiceEntity>of(DiceEntity::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build("dice")
    );

    public static Item diceItem(int sides) {
        // use d20 for missing model
        return DICE.getOrDefault(sides, DICE.get(20)).get();
    }

    public Polyhedrals(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, PolyhedralsConfig.SPEC);
        ITEMS.register(modEventBus);
        ENTITIES.register(modEventBus);
        ATTACHMENT_TYPES.register(modEventBus);
        COMPONENTS.register(modEventBus);
        RECIPE_SERIALIZERS.register(modEventBus);
        CONDITIONS.register(modEventBus);
        modEventBus.addListener(Polyhedrals::addCreative);
    }

    private static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            DICE.values().forEach(event::accept);
        }
    }
}
