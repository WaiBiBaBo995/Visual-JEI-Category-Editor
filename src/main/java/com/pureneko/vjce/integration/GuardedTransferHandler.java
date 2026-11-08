package com.pureneko.vjce.integration;

import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.*;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.*;
import net.minecraft.world.entity.player.Player;
import java.util.Optional;

public record GuardedTransferHandler<C extends AbstractContainerMenu, R>(IRecipeTransferHandler<C, R> original,
        CapturedCategory<R> category) implements IRecipeTransferHandler<C, R> {
    @Override public Class<? extends C> getContainerClass() { return original.getContainerClass(); }
    @Override public Optional<MenuType<C>> getMenuType() { return original.getMenuType(); }
    @Override public RecipeType<R> getRecipeType() { return original.getRecipeType(); }
    private IRecipeTransferError blocked() {
        return new mezz.jei.library.transfer.RecipeTransferErrorTooltip(Component.translatable("vjce.transfer.display_only"));
    }
    @Override public IRecipeTransferError transferRecipe(IRecipeTransferContext<R, C> context, boolean doTransfer) {
        return category.blocksTransfer(context.getRecipe()) ? blocked() : original.transferRecipe(context, doTransfer);
    }
    @Override @SuppressWarnings("removal")
    public IRecipeTransferError transferRecipe(C container, R recipe, IRecipeSlotsView slots, Player player, boolean max, boolean execute) {
        return category.blocksTransfer(recipe) ? blocked() : original.transferRecipe(container, recipe, slots, player, max, execute);
    }
}
