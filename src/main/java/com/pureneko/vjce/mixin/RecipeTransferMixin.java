package com.pureneko.vjce.mixin;

import com.pureneko.vjce.integration.*;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.transfer.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Optional;

@Mixin(targets = "mezz.jei.library.recipes.RecipeTransferManager", remap = false)
public abstract class RecipeTransferMixin {
    @WrapMethod(method = "getRecipeTransferHandler")
    private <C extends AbstractContainerMenu, R> Optional<IRecipeTransferHandler<C, R>> vjce$guard(C menu, IRecipeCategory<R> category,
            Operation<Optional<IRecipeTransferHandler<C, R>>> operation) {
        var result = operation.call(menu, category);
        if (category instanceof CapturedCategory<R> adapted)
            return result.map(handler -> new GuardedTransferHandler<>(handler, adapted));
        if (category instanceof DisplayCategory<?>) return Optional.empty();
        return result;
    }
}
