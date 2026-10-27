package dev.dsh.kjseditor.compat;

import dev.dsh.kjseditor.client.EditorScreen;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Lets JEI drag an ingredient onto any field of the editor. */
public class EditorGhostHandler implements IGhostIngredientHandler<EditorScreen> {
    @Override
    public <I> List<Target<I>> getTargetsTyped(EditorScreen gui, ITypedIngredient<I> ingredient, boolean doStart) {
        List<Target<I>> targets = new ArrayList<>();
        for (EditorScreen.DropTarget drop : gui.dropTargets()) {
            targets.add(new Target<I>() {
                @Override
                public Rect2i getArea() {
                    return drop.area();
                }

                @Override
                public void accept(I stack) {
                    if (stack instanceof ItemStack item) {
                        drop.fill().accept(BuiltInRegistries.ITEM.getKey(item.getItem()).toString());
                    }
                }
            });
        }
        return targets;
    }

    @Override
    public void onComplete() {
    }

    @Override
    public boolean shouldHighlightTargets() {
        return true;
    }
}
