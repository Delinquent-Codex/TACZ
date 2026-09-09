package com.tacz.guns.crafting;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Assigns each physical item to at most one requirement, including overlapping tags. */
public final class IngredientAllocation {
    private IngredientAllocation() {}

    public static Optional<int[]> plan(List<GunSmithTableIngredient> ingredients, RecipeInput input) {
        int slots = input.size();
        int sink = 1 + slots + ingredients.size();
        int[][] capacity = new int[sink + 1][sink + 1];
        long required = 0;
        for (int slot = 0; slot < slots; slot++) {
            ItemStack stack = input.getItem(slot);
            capacity[0][1 + slot] = stack.getCount();
            for (int index = 0; index < ingredients.size(); index++) {
                if (!stack.isEmpty() && ingredients.get(index).getIngredient().test(stack)) {
                    capacity[1 + slot][1 + slots + index] = stack.getCount();
                }
            }
        }
        for (int index = 0; index < ingredients.size(); index++) {
            int count = ingredients.get(index).getCount();
            capacity[1 + slots + index][sink] = count;
            required += count;
        }
        long assigned = 0;
        int[] parent = new int[sink + 1];
        while (assigned < required) {
            Arrays.fill(parent, -1);
            parent[0] = 0;
            var queue = new ArrayDeque<Integer>();
            queue.add(0);
            while (!queue.isEmpty() && parent[sink] < 0) {
                int from = queue.remove();
                for (int to = 1; to <= sink; to++) {
                    if (parent[to] < 0 && capacity[from][to] > 0) {
                        parent[to] = from;
                        queue.add(to);
                    }
                }
            }
            if (parent[sink] < 0) return Optional.empty();
            int amount = Integer.MAX_VALUE;
            for (int to = sink; to != 0; to = parent[to]) amount = Math.min(amount, capacity[parent[to]][to]);
            for (int to = sink; to != 0; to = parent[to]) {
                capacity[parent[to]][to] -= amount;
                capacity[to][parent[to]] += amount;
            }
            assigned += amount;
        }
        int[] consumption = new int[slots];
        for (int slot = 0; slot < slots; slot++) consumption[slot] = capacity[1 + slot][0];
        return Optional.of(consumption);
    }
}
