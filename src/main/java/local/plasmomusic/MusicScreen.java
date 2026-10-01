package local.plasmomusic;

import net.minecraft.*;

/** The source and transmitted sound have separate live meters. */
public final class MusicScreen extends class_437 {
    private final MusicAddon app;
    private final class_437 parent;
    private class_4185 toggle, stereo;
    private int left, top, width;
    private double captureDisplay, outgoingDisplay;
    private long lastPaintNanos;

    public MusicScreen(MusicAddon app,class_437 parent){
        super(class_2561.method_43470("Plasmo Musicshare · 实时抓取"));
        this.app=app;this.parent=parent;
    }
    private class_4185 button(String text,int x,int y,int w,Runnable action){
        return method_37063(class_4185.method_46430(class_2561.method_43470(text),b->action.run()).method_46434(x,y,w,20).method_46431());
    }
    @Override protected void method_25426(){
        width=Math.min(356,field_22789-24);
        left=(field_22789-width)/2;
        top=Math.max(4,(field_22790-252)/2);
        toggle=button("",left,top+122,width,()->app.toggle());
        method_37063(new class_357(left,top+146,width,20,class_2561.method_43470(""),app.settings.volume()/(double)Settings.MAX_VOLUME){
            {method_25346();}
            protected void method_25346(){method_25355(class_2561.method_43470("发送音量："+Math.round(field_22753*Settings.MAX_VOLUME)+"%"));}
            protected void method_25344(){Settings s=app.settings;app.update(new Settings("exclude-game",s.enabled(),(int)Math.round(field_22753*Settings.MAX_VOLUME),s.stereo(),s.forceOpen()));}
        });
        stereo=button("",left,top+170,width,()->{Settings s=app.settings;app.update(new Settings("exclude-game",s.enabled(),s.volume(),!s.stereo(),s.forceOpen()));});
        int gap=8,half=(width-gap)/2;
        button("停止共享",left,top+194,half,()->app.setEnabled(false));
        button("完成",left+half+gap,top+194,half,this::method_25419);
        button("选择抓取程序",left,top+218,width,()->field_22787.method_1507(new ProcessSelectionScreen(app,this)));
        labels();
    }
    private void labels(){
        if(toggle==null)return;
        toggle.method_25355(class_2561.method_43470(app.settings.enabled()?"共享：开（点击关闭）":"共享：关（点击开启）"));
        stereo.method_25355(class_2561.method_43470("立体声："+(app.settings.stereo()?"开（不支持时自动转单声道）":"关")));
    }
    @Override public void method_25393(){labels();}
    private void line(class_332 context,String text,int y,int color){
        if(text.length()>45)text=text.substring(0,44)+"…";
        context.method_25300(field_22793,text,field_22789/2,y,color);
    }
    private static double decibels(double amplitude){
        return amplitude>0?20*Math.log10(amplitude):-127;
    }
    private static double fraction(double db){
        return Math.max(0,Math.min(1,(db+60)/60));
    }
    private static String levelLabel(double db){
        return db<=-60?"静音":Math.round(db)+" dB";
    }
    private static double approach(double from,double to,double dt){
        double tau=to>from?0.09:0.28;
        return from+(to-from)*(1-Math.exp(-dt/tau));
    }
    private void meter(class_332 context,String label,double db,double shown,int y,boolean threshold){
        line(context,label+"  ·  "+levelLabel(db),y-13,0xffdbe9f2);
        int x=left+12,w=width-24,segments=28;
        context.method_25294(x-3,y-3,x+w+3,y+11,0xff26313e);
        int active=(int)Math.round(shown*segments);
        for(int i=0;i<segments;i++){
            int a=x+i*w/segments+1,b=x+(i+1)*w/segments-1;
            int color=i<active?(i<19?0xff40d7b3:i<25?0xffffc857:0xffff715b):0xff334450;
            context.method_25294(a,y,b,y+8,color);
        }
        if(threshold){
            int marker=x+(int)Math.round(w*fraction(app.speakerThresholdDb()));
            context.method_25294(marker,y-3,marker+2,y+11,0xfff4f8ff);
        }
    }
    @Override public void method_25394(class_332 context,int mouseX,int mouseY,float delta){
        super.method_25394(context,mouseX,mouseY,delta);
        long now=System.nanoTime();
        double dt=lastPaintNanos==0?0.016:Math.max(0.005,Math.min(0.1,(now-lastPaintNanos)/1_000_000_000.0));
        lastPaintNanos=now;
        double captureDb=decibels(app.buffer.peak);
        double outgoingDb=app.outgoingDecibels();
        captureDisplay=approach(captureDisplay,fraction(captureDb),dt);
        outgoingDisplay=approach(outgoingDisplay,fraction(outgoingDb),dt);

        context.method_25294(left,top+51,left+width,top+117,0xb0151e2b);
        line(context,"实时音乐抓取 · Talking Heads 适配",top+3,0xffffffff);
        line(context,app.displayStatus,top+20,0xff89d7ff);
        line(context,"音源："+app.captureDescription(),top+37,0xffa9e7c8);
        meter(context,"采集电平",captureDb,captureDisplay,top+68,false);
        meter(context,"发送电平（含麦克风）",outgoingDb,outgoingDisplay,top+100,true);
        line(context,"按住快捷键屏蔽 / 快捷键开关音乐 · 白线为感应阈值",top+111,0xffaab9c5);
    }
    @Override public boolean method_25421(){return false;}
    @Override public void method_25419(){field_22787.method_1507(parent);}
}
