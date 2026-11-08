package com.pureneko.vjce.integration;

import mezz.jei.api.gui.drawable.IDrawable;

/** Optional integration: no Create class is linked until the mod is actually present. */
public final class CreateAnimation {
    private static final ThreadLocal<Float> TIME = new ThreadLocal<>();
    private CreateAnimation() {}
    public static Float time() { return TIME.get(); }
    public static void scoped(float time, Runnable action) {
        Float before=TIME.get(); TIME.set(time);
        try { action.run(); } finally { if(before==null)TIME.remove();else TIME.set(before); }
    }
    public static IDrawable create(String preset, boolean basin) {
        if(!net.neoforged.fml.ModList.get().isLoaded("create")) return null;
        try {
            String name=switch(preset) { case "PRESS" -> "AnimatedPress"; case "MIXER" -> "AnimatedMixer"; case "CRUSHING_WHEELS" -> "AnimatedCrushingWheels"; default -> throw new IllegalArgumentException(preset); };
            var type=Class.forName("com.simibubi.create.compat.jei.category.animations."+name);
            return (IDrawable)(name.equals("AnimatedPress")?type.getConstructor(boolean.class).newInstance(basin):type.getConstructor().newInstance());
        } catch(ReflectiveOperationException failure) { throw new IllegalStateException("Unsupported Create animation API",failure); }
    }
}
