package com.pureneko.vjce.client;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.pureneko.vjce.client.canvas.*;
import com.pureneko.vjce.integration.NativePage;
public class NativePartElement extends CanvasElement {
    @Persisted public String part="native",sourceLabel="JEI source drawing",replacementText="",replacementTexture="";
    @Persisted public boolean useSourceDrawing=true,overrideTextColor;
    @Persisted public int textColor=0xff000000;
    @Persisted public float sourceX,sourceY,sourceWidth=1,sourceHeight=1;
    public NativePartElement(){super("native");}
    @Override public void drawBackgroundAdditional(CanvasContext context){var page=NativePage.context(this);if(page!=null&&!page.runtime()&&useSourceDrawing)page.drawElement(this,context);}
}
