package com.pureneko.vjce.client;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.*;
final class CanvasModalGuard {
    static boolean blocked(ModularUI ui){return ui!=null&&contains(ui.mainGroup);}
    private static boolean contains(WidgetGroup group){for(var widget:group.widgets){if(!widget.isVisible())continue;if(widget instanceof DialogWidget)return true;if(widget instanceof WidgetGroup child&&contains(child))return true;}return false;}
}
