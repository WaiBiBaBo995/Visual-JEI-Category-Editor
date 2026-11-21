package com.pureneko.vjce.mixin;
import com.pureneko.vjce.integration.*;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Optional;
@Mixin(targets="mezz.jei.library.recipes.RecipeTransferManager",remap=false)
public abstract class RecipeTransferMixin {
    @Inject(method="getRecipeTransferHandler",at=@At("RETURN"),cancellable=true)
    private <C extends AbstractContainerMenu,R> void vjce$guard(C menu,IRecipeCategory<R> category,CallbackInfoReturnable<Optional<IRecipeTransferHandler<C,R>>> result){
        if(category instanceof CapturedCategory<R> adapted)result.setReturnValue(result.getReturnValue().map(h->new GuardedTransferHandler<>(h,adapted)));
        else if(category instanceof DisplayCategory<?>)result.setReturnValue(Optional.empty());
    }
}
