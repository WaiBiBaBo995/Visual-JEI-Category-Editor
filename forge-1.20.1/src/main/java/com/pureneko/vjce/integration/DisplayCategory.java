package com.pureneko.vjce.integration;
import com.pureneko.vjce.client.*;
import com.pureneko.vjce.client.canvas.*;
import com.pureneko.vjce.data.CategoryDocument;
import mezz.jei.api.gui.builder.*;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.recipe.*;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import java.util.function.Function;
public class DisplayCategory<T> implements IRecipeCategory<T> {
    public final CategoryDocument document;
    private final RecipeType<T> type;
    protected final Function<T,CategoryDocument.Recipe> resolver;
    private final Map<T,CanvasRuntime> layouts=new LinkedHashMap<>(32,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<T,CanvasRuntime> e){if(size()<=64)return false;e.getValue().onRemoved();return true;}
    };
    public DisplayCategory(CategoryDocument document,RecipeType<T> type,Function<T,CategoryDocument.Recipe> resolver){this.document=document;this.type=type;this.resolver=resolver;}
    public CanvasRuntime getUIForRecipe(T recipe){return layouts.computeIfAbsent(recipe,r->RecipeUI.create(document,resolver.apply(r)));}
    @Override public RecipeType<T> getRecipeType(){return type;}
    @Override public Component getTitle(){return Component.literal(document.title);}
    @Override public int getWidth(){return document.width;}
    @Override public int getHeight(){return document.height;}
    @Override public IDrawable getBackground(){return VjceJeiPlugin.helpers.getGuiHelper().createBlankDrawable(document.width,document.height);}
    @Override public IDrawable getIcon(){return VjceJeiPlugin.helpers.getGuiHelper().createDrawableItemStack(new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(new ResourceLocation(document.icon))));}
    protected String slotName(CategoryDocument.Recipe data,int index){var slot=data.slots.get(index);return slot.name.isBlank()?data.slotIdentifier(index):slot.name;}
    @Override public void setRecipe(IRecipeLayoutBuilder builder,T recipe,IFocusGroup focus){
        var data=resolver.apply(recipe);var root=getUIForRecipe(recipe).ui.rootElement;var names=new LinkedHashMap<String,IIngredientAcceptor<?>>();
        for(int i=0;i<data.slots.size();i++){
            var slot=data.slots.get(i);var e=RecipeUI.slotElement(root,data.slotIdentifier(i));var role=RecipeIngredientRole.valueOf(slot.role);IIngredientAcceptor<?> acceptor;
            if(slot.invisible||!NativePage.visible(e))acceptor=builder.addInvisibleIngredients(role);
            else {var t=new ElementTransform(root,e,16,16);var area=t.area();var real=builder.addSlot(role,area.left(),area.top()).setSlotName(slotName(data,i)).setStandardSlotBackground();
                real.addTooltipCallback((view,tooltip)->{if(slot.chance!=1)tooltip.add(Component.translatable("vjce.ingredient.chance_tooltip",Math.round(slot.chance*100)));slot.ingredients.stream().filter(v->!v.tag.isBlank()).forEach(v->tooltip.add(Component.literal("#"+v.tag)));});acceptor=real;}
            acceptor.addOptionalTypedIngredients(IngredientAccess.decodeChoices(slot));names.put(slotName(data,i),acceptor);
        }
        for(var link:data.focusLinks){var members=link.stream().map(names::get).filter(Objects::nonNull).toList();if(members.size()>1)builder.createFocusLink(members.toArray(IIngredientAcceptor<?>[]::new));}
    }
    @Override public void createRecipeExtras(IRecipeExtrasBuilder builder,T recipe,IFocusGroup focus){
        var runtime=getUIForRecipe(recipe);var widget=new CanvasJeiWidget(runtime);var data=resolver.apply(recipe);
        for(int i=0;i<data.slots.size();i++){var e=RecipeUI.slotElement(runtime.ui.rootElement,data.slotIdentifier(i));if(e==null)continue;builder.getRecipeSlots().findSlotByName(slotName(data,i)).ifPresent(slot->widget.bind(e,slot));}
        builder.addSlottedWidget(widget,new ArrayList<>(widget.slots.values()));
    }
    @Override public ResourceLocation getRegistryName(T recipe){return new ResourceLocation(resolver.apply(recipe).id);}
    @Override public boolean isHandled(T recipe){return !resolver.apply(recipe).hidden;}
}
