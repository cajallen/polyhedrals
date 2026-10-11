TBD..., but should be:

```groovy
repositories {
    maven { url = "https://api.modrinth.com/maven" }
}

dependencies {
    implementation "maven.modrinth:polyhedrals:0.1.1"
}
```

`neoforge.mods.toml`:
```toml
[[dependencies.yourmodid]]
    modId="polyhedrals"
    type="required"
    versionRange="[0.1.1,)"
    ordering="NONE"
    side="BOTH"
```

# Integrating with Polyhedrals

Roll with one call, and get the result from an event when they land:

```java
UUID roll = PolyhedralsAPI.newRoll("1d20+5").underhand(player, InteractionHand.MAIN_HAND).spawn();

@SubscribeEvent
public static void onRoll(PhysicalRollGroupEvent event) {
    if (event.getGroupId().equals(roll)) {
        int total = event.getResult().total();
    }
}
```

If you only need a number, roll without dice: `PolyhedralsAPI.newRoll("1d20+5").thrownBy(player).evaluateLogical()`.

## Rolling dice

```java
UUID roll = PolyhedralsAPI.newRoll("4d6h3")
        .underhand(player, InteractionHand.MAIN_HAND) // toss from the player's hand
        .colors(0xFFD700, 0x4B0082)                   // face color, detail color
        .canPickup(true)                              // players can pick the dice up once landed
        .broadcast(BroadcastMode.NONE)                // you report the result
        .spawn();
```

`preset(...)` places the dice on chosen faces instead of tumbling them:
- `PresetRoll.fair()` places them on random faces, so the roll resolves instantly.
- `new PresetRoll.Faces(List.of(20, 3))` sets each die's face in throw order.
- `new PresetRoll.Total(15)` picks faces that add up as close to 15 as the expression allows.
I might get rid of this feature, not sure yet.

### Caveats

A d100 is thrown as a tens die and a units d10, and reports their sum once.

Any unsupported die size is rolled as a d20. I may look into rolling these as a generated cylinder die, but it's not something I need currently.

## Expressions

Standard arithmetic (`+ - * /`, parentheses) around dice terms like `2d6`. A term's count and size can be
arithmetic like `(2*3)d6`, but can't contain dice. Terms take these modifiers:

| Modifier | Example  | Effect                    |
|----------|----------|---------------------------|
| `hN`     | `4d6h3`  | keep the highest N        |
| `lN`     | `2d20l1` | keep the lowest N         |
| `r=N`    | `3d6r=1` | reroll dice that show N   |
| `r<N`    | `3d6r<3` | reroll dice below N       |
| `r>N`    | `3d6r>5` | reroll dice above N       |

Rerolls are drawn without throwing another die.

## Events

All are posted on `NeoForge.EVENT_BUS`.

| Event | When | Use it to |
|---|---|---|
| `DiceRollEvent` | each die resolves | `addModifier` to change that die's value |
| `PhysicalDiceLandEvent` | one die landed | read `getResult()`; `getDiceEntity().getRollGroup()` is the ID `spawn()` returned |
| `PhysicalRollGroupEvent` | every die in a roll landed | read `getResult().total()` and each die's value in `rolls()` |

`DiceRollEvent` fires for every die, even when not physical, so changing a roll there changes it everywhere:

```java
@SubscribeEvent
public static void critBonus(DiceRollEvent event) {
    if (event.getSides() == 20 && event.getRoller() instanceof Player player && player.hasEffect(MobEffects.LUCK)) {
        event.addModifier(2);
    }
}
```


```java
@SubscribeEvent
public static void critCelebration(DiceRollEvent event) {
    Entity roller = event.getRoller();
    if (event.getSides() != 20 || event.getNaturalRoll() != 20 || roller == null || roller.level().isClientSide)
        return;
    ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
    rocket.set(DataComponents.FIREWORKS, new Fireworks(1, List.of(new FireworkExplosion(
            FireworkExplosion.Shape.STAR, IntList.of(0xFED83D), IntList.of(), true, true))));
    roller.level().addFreshEntity(new FireworkRocketEntity(roller.level(), roller.getX(), roller.getY() + 1, roller.getZ(), rocket));
}
```
