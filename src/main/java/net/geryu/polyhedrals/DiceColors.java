package net.geryu.polyhedrals;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record DiceColors(int faceColor, int detailColor) {
    public static final DiceColors DEFAULT = new DiceColors(0xFFFFFF, 0x000000);

    public static final Codec<DiceColors> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("face_color").forGetter(DiceColors::faceColor),
            Codec.INT.fieldOf("detail_color").forGetter(DiceColors::detailColor)
    ).apply(instance, DiceColors::new));
}
