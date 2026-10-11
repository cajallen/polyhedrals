package net.geryu.polyhedrals.api;

import net.geryu.polyhedrals.api.parser.ASTNode;
import net.geryu.polyhedrals.api.parser.PhysicalDice;
import net.geryu.polyhedrals.api.parser.RollContext;

import java.util.Arrays;
import java.util.List;
import java.util.function.IntUnaryOperator;
import java.util.stream.IntStream;

public sealed interface PresetRoll {
    static PresetRoll fair() {
        return new Fair();
    }

    int[] facesFor(ASTNode root, List<PhysicalDice> dice, IntUnaryOperator draw);

    record Fair() implements PresetRoll {
        @Override
        public int[] facesFor(ASTNode root, List<PhysicalDice> dice, IntUnaryOperator draw) {
            return Arrays.stream(sidesInThrowOrder(dice)).map(draw).toArray();
        }
    }

    record Faces(List<Integer> faces) implements PresetRoll {
        public Faces {
            faces = List.copyOf(faces);
        }

        @Override
        public int[] facesFor(ASTNode root, List<PhysicalDice> dice, IntUnaryOperator draw) {
            int[] sides = sidesInThrowOrder(dice);
            int[] shown = new Fair().facesFor(root, dice, draw);
            for (int i = 0; i < Math.min(sides.length, faces.size()); i++) {
                shown[i] = Math.clamp(faces.get(i), 1, sides[i]);
            }
            return shown;
        }
    }

    record Total(int total) implements PresetRoll {
        @Override
        public int[] facesFor(ASTNode root, List<PhysicalDice> dice, IntUnaryOperator draw) {
            int[] sides = sidesInThrowOrder(dice);
            int[] faces = new int[sides.length];
            Arrays.fill(faces, 1);
            int miss = Math.abs(evaluate(root, dice, faces) - total);
            for (int i = 0; i < faces.length && miss > 0; i++) {
                while (faces[i] < sides[i] && miss > 0) {
                    faces[i]++;
                    int next = Math.abs(evaluate(root, dice, faces) - total);
                    if (next >= miss) {
                        faces[i]--;
                        break;
                    }
                    miss = next;
                }
            }
            return faces;
        }

        private static int evaluate(ASTNode root, List<PhysicalDice> dice, int[] faces) {
            return root.evaluate(RollContext.landed(dice, faces, sides -> 1));
        }
    }

    private static int[] sidesInThrowOrder(List<PhysicalDice> dice) {
        return dice.stream()
                .flatMapToInt(term -> IntStream.generate(term::sides).limit(term.count()))
                .toArray();
    }
}
