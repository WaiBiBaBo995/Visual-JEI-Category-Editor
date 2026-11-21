package com.pureneko.vjce.client.canvas;
public final class CanvasUI {
    public final CanvasElement rootElement;
    public CanvasUI(CanvasElement root){rootElement=root;}
    public CanvasTemplate toTemplate(){return CanvasTemplate.of(rootElement);}
}
