package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.OreSprites;

/** Explicit editor styling prevents a recipe's ui-mc stylesheet from recoloring editor tools. */
final class EditorForms {
    static void ore(UIElement root){
        root.select("*").filter(TextField.class::isInstance).map(TextField.class::cast).forEach(field->{
            field.style(s->s.backgroundTexture(OreSprites.RECT2));field.textFieldStyle(s->s.focusOverlay(OreSprites.WHITE_BORDER));
        });
    }
    static TextField number(double value,double min,double max,boolean integer,java.util.function.Consumer<Double> changed){
        var field=new TextField();if(integer)field.setNumbersOnlyInt((int)min,(int)max);else field.setNumbersOnlyDouble(min,max);
        field.setText(integer?Integer.toString((int)value):Double.toString(value),false);
        field.setTextResponder(text->{try{changed.accept(Double.parseDouble(text));}catch(NumberFormatException ignored){}});
        field.layout(l->{l.flex(1);l.minWidth(0);l.height(18);l.minHeight(18);});ore(field);return field;
    }
}
