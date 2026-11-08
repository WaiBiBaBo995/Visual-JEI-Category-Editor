package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;

/** Focused integer wheel input; clamp before conversion to avoid overflow. */
final class HoverNumberField extends TextField {
    private final int minimum, maximum;
    HoverNumberField(int minimum,int maximum){
        this.minimum=minimum;this.maximum=maximum;
        setNumbersOnlyInt(minimum,maximum);
        layout(l->{l.flex(1);l.height(18);l.minWidth(0);});
    }
    @Override protected void onMouseWheel(UIEvent event){
        if(event.deltaY==0||!isEditable())return;
        try{
            long current=Long.parseLong(getValue());
            long delta=(event.deltaY>0?1:-1)*(event.isShiftDown()?10:1);
            setText(Long.toString(Math.clamp(current+delta,minimum,maximum)),true);
        }catch(NumberFormatException ignored){ /* Keep an unfinished keyboard edit intact. */ }
        event.stopImmediatePropagation();
    }
}
