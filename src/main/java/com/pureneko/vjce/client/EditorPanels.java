package com.pureneko.vjce.client;

import com.lowdragmc.lowdraglib.gui.editor.ui.*;
import com.lowdragmc.lowdraglib.gui.widget.*;
import com.lowdragmc.lowdraglib.utils.*;
import com.lowdragmc.lowdraglib.gui.editor.Icons;
import com.lowdragmc.lowdraglib.gui.texture.*;

/** Resize the existing panels so changing GUI scale never reloads an editing session. */
final class EditorPanels {
    /** Animate actual bounds, so rendering, reflow and input use the same coordinates. */
    private static final class Motion {
        int value,from,target;
        long start;
        Motion(int value){this.value=this.target=value;}
        void to(int target){if(this.target==target)return;advance(System.nanoTime());from=value;this.target=target;start=System.nanoTime();}
        void advance(long now){if(start==0)return;double t=Math.min(1,Math.max(0,(now-start)/500_000_000d));value=(int)Math.round(from+(target-from)*(1-(1-t)*(1-t)));if(t>=1)start=0;}
    }
    static final class Properties extends ConfigPanel {
        private final Motion motion=new Motion(WIDTH);
        final ButtonWidget toggle;
        boolean collapsed;
        Properties(Editor editor){super(editor);toggle=new ButtonWidget(2,2,12,12,Icons.RIGHT,c->setCollapsed(!collapsed));toggle.setHoverTooltips("vjce.panel.collapse");addWidget(toggle);}
        int occupied(){return motion.value;}
        void setCollapsed(boolean value){if(value==collapsed)return;collapsed=value;motion.to(value?16:WIDTH);toggle.setButtonTexture(value?Icons.LEFT:Icons.RIGHT);toggle.setHoverTooltips(value?"vjce.panel.expand":"vjce.panel.collapse");tabContainer.setActive(!value);}
        void advanceAnimation(long now){motion.advance(now);position();}
        private void position(){setSelfPosition(new Position(editor.getSize().width-occupied(),0));for(var widget:widgets)if(widget!=toggle)widget.setVisible(occupied()>16);}
        void resize(int width,int height){
            position();if(getSize().height==height)return;setSize(new Size(WIDTH,height));
            tabContainer.setSize(new Size(WIDTH,height));
            configuratorGroup.forEach((tab,group)->{group.setSize(new Size(WIDTH,Math.max(20,height-25)));group.computeMax();});
        }
        @Override public void drawInBackground(net.minecraft.client.gui.GuiGraphics g,int mx,int my,float pt){var p=getPosition();g.enableScissor(p.x-(occupied()==WIDTH?20:0),0,editor.getSize().width,getSize().height);try{super.drawInBackground(g,mx,my,pt);}finally{g.flush();g.disableScissor();}}
        @Override public boolean mouseClicked(double x,double y,int button){return x>=getPosition().x-(occupied()==WIDTH?20:0)&&super.mouseClicked(x,y,button);}
        @Override public boolean mouseWheelMove(double x,double y,double delta){return x>=getPosition().x&&super.mouseWheelMove(x,y,delta);}
    }
    static final class Resources extends ResourcePanel {
        private final Motion motion=new Motion(HEIGHT);
        Resources(Editor editor){super(editor);}
        int occupied(){return motion.value;}
        @Override public void hide(){if(isShow){isShow=false;motion.to(0);arrow();}}
        @Override public void show(){if(!isShow){isShow=true;motion.to(HEIGHT);arrow();}}
        private void arrow(){if(buttonHide!=null)buttonHide.setButtonTexture(new ColorRectTexture(Forms.BG),new ColorBorderTexture(1,0xff666666),isShow?Icons.DOWN:Icons.UP);}
        void advanceAnimation(long now){motion.advance(now);setSelfPosition(new Position(0,editor.getSize().height-occupied()));}
        void resize(int width,int height){
            setSelfPosition(new Position(0,height-occupied()));boolean changed=getSize().width!=width;setSize(new Size(width,HEIGHT));
            if(buttonHide==null)return;
            buttonHide.setSelfPosition(new Position((width-30)/2,-10));tabContainer.setSize(new Size(width,HEIGHT-14));
            if(!changed)return;
            if(resources!=null){
                var font=net.minecraft.client.Minecraft.getInstance().font;int x=font.width(com.lowdragmc.lowdraglib.utils.LocalizationUtils.format("ldlib.gui.editor.group.resources"))+8;
                int max=Math.max(1,(width-x)/Math.max(1,resources.resources.size()));
                for(var resource:resources.resources.values()){
                    var container=containerMap.get(resource);var button=tabContainer.tabs.inverse().get(container);if(button==null)continue;
                    int tabWidth=Math.max(1,Math.min(max,font.width(com.lowdragmc.lowdraglib.utils.LocalizationUtils.format(resource.name()))+8));
                    button.setSelfPosition(new Position(x,-15));button.setSize(new Size(tabWidth,15));x+=tabWidth;
                }
            }
            containerMap.values().forEach(container->{
                container.setSize(new Size(Math.max(20,width-6),HEIGHT-14));
                for(var widget:((WidgetGroup)container).widgets)if(widget instanceof DraggableScrollableWidgetGroup scroll){
                    // Reposition existing tiles instead of rebuilding (which loses selection and previews).
                    int offset=scroll.getScrollYOffset();scroll.setScrollYOffset(0);scroll.setSize(new Size(Math.max(20,width-8),HEIGHT-16));
                    int x=1,y=3,rowHeight=0;for(var tile:scroll.widgets){int tw=tile.getSize().width,th=tile.getSize().height;if(x>1&&x+tw>scroll.getSize().width-5){x=1;y+=rowHeight+3;rowHeight=0;}tile.setSelfPosition(new Position(x,y));x+=tw+3;rowHeight=Math.max(rowHeight,th);}
                    scroll.computeMax();scroll.setScrollYOffset(Math.min(offset,Math.max(0,scroll.getWidgetBottomHeight()-scroll.getSize().height)));
                }
            });
        }
    }
}
