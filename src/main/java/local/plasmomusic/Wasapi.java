package local.plasmomusic;

import com.sun.jna.*;
import com.sun.jna.ptr.*;
import com.sun.jna.win32.StdCallLibrary;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Windows shared-mode render endpoint loopback. All COM objects stay on their owning thread. */
public final class Wasapi {
    interface Ole extends StdCallLibrary {
        Ole INSTANCE = Native.load("ole32", Ole.class);
        int CoInitializeEx(Pointer reserved, int flags);
        void CoUninitialize();
        int CoCreateInstance(Pointer clsid, Pointer outer, int context, Pointer iid, PointerByReference result);
        void CoTaskMemFree(Pointer memory);
        int PropVariantClear(Pointer variant);
    }
    public record Device(String id, String name) {
        @Override public String toString() { return name + "  [" + Integer.toHexString(id.hashCode()) + "]"; }
    }
    public interface Sink { void format(int rate, int channels, int bits); void samples(float[] stereo, int rate); }
    static Memory guid(String value) {
        UUID u = UUID.fromString(value);
        Memory m = new Memory(16);
        long hi = u.getMostSignificantBits(), lo = u.getLeastSignificantBits();
        m.setInt(0, (int)(hi >>> 32)); m.setShort(4, (short)(hi >>> 16)); m.setShort(6, (short)hi);
        for (int i=0;i<8;i++) m.setByte(8+i, (byte)(lo >>> (56-8*i)));
        return m;
    }
    static int call(Pointer obj, int slot, Object... args) {
        Object[] all = new Object[args.length+1]; all[0]=obj; System.arraycopy(args,0,all,1,args.length);
        return Function.getFunction(obj.getPointer(0).getPointer((long)slot*Native.POINTER_SIZE), Function.ALT_CONVENTION).invokeInt(all);
    }
    static void check(int hr, String action) {
        if (hr < 0) throw new IllegalStateException(action + " (HRESULT 0x" + Integer.toHexString(hr) + ")");
    }
    static void release(Pointer p) { if(p!=null) call(p,2); }
    static Pointer enumerator() {
        PointerByReference out=new PointerByReference();
        check(Ole.INSTANCE.CoCreateInstance(guid("bcde0395-e52f-467c-8e3d-c4579291692e"),null,1,
            guid("a95664d2-9614-4f35-a746-de8db63617e6"),out),"创建设备枚举器");
        return out.getValue();
    }
    static Device describe(Pointer device) {
        PointerByReference out=new PointerByReference(); check(call(device,5,out),"读取设备 ID");
        String id=out.getValue().getWideString(0); Ole.INSTANCE.CoTaskMemFree(out.getValue());
        Pointer props=null; String name=id;
        try {
            check(call(device,4,0,out),"读取设备名称"); props=out.getValue();
            Memory key=new Memory(20); key.write(0,guid("a45c254e-df1c-4efd-8020-67d146a850e0").getByteArray(0,16),0,16); key.setInt(16,14);
            Memory variant=new Memory(24); variant.clear();
            check(call(props,5,key,variant),"读取设备属性");
            try { if(variant.getShort(0)==31) name=variant.getPointer(8).getWideString(0); }
            finally { Ole.INSTANCE.PropVariantClear(variant); }
        } finally { release(props); }
        return new Device(id,name);
    }
    public static List<Device> devices() {
        check(Ole.INSTANCE.CoInitializeEx(null,0),"初始化 COM");
        Pointer en=null, list=null;
        try {
            en=enumerator(); PointerByReference out=new PointerByReference();
            check(call(en,3,0,1,out),"枚举播放设备"); list=out.getValue();
            IntByReference count=new IntByReference(); check(call(list,3,count),"读取设备数量");
            List<Device> found=new ArrayList<>();
            for(int i=0;i<count.getValue();i++) {
                check(call(list,4,i,out),"读取播放设备"); Pointer dev=out.getValue();
                try { found.add(describe(dev)); } finally { release(dev); }
            }
            return found;
        } finally { release(list); release(en); Ole.INSTANCE.CoUninitialize(); }
    }
    public static void capture(String id, BooleanSupplier running, Sink sink) throws InterruptedException {
        check(Ole.INSTANCE.CoInitializeEx(null,0),"初始化 COM");
        Pointer en=null,device=null,client=null,capture=null,format=null; boolean started=false, process=id.equals("exclude-game");
        try {
            PointerByReference out=new PointerByReference();
            if(process){
                client=ProcessLoopback.activate((int)ProcessHandle.current().pid());
                Memory pcm=new Memory(18);pcm.clear();pcm.setShort(0,(short)1);pcm.setShort(2,(short)2);
                pcm.setInt(4,48000);pcm.setInt(8,192000);pcm.setShort(12,(short)4);pcm.setShort(14,(short)16);format=pcm;
            }else{
                en=enumerator();check(call(en,5,new WString(id),out),"所选播放设备不可用"); device=out.getValue();
                check(call(device,3,guid("1cb9ad4c-dbfa-4c32-b178-c2f568a703b2"),23,null,out),"打开音频客户端"); client=out.getValue();
                check(call(client,8,out),"读取混音格式"); format=out.getValue();
            }
            int tag=Short.toUnsignedInt(format.getShort(0)), channels=Short.toUnsignedInt(format.getShort(2));
            int rate=format.getInt(4), align=Short.toUnsignedInt(format.getShort(12)), bits=Short.toUnsignedInt(format.getShort(14));
            int mask=0;
            if(tag==0xfffe) { mask=format.getInt(20); tag=format.getInt(24); }
            if(channels<1||channels>32||rate<8000||rate>384000||align!=channels*(bits/8)
                || !((tag==3&&bits==32)||(tag==1&&(bits==16||bits==24||bits==32))))
                throw new IllegalStateException("不支持的设备格式："+rate+" Hz / "+channels+" 声道 / "+bits+" bit / "+tag);
            // AUDCLNT_STREAMFLAGS_LOOPBACK; shared mode uses the device's exact mix format.
            check(call(client,3,0,process?0x88020000:0x20000,1_000_000L,0L,format,null),"启动 WASAPI 回环");
            check(call(client,14,guid("c8adbd64-e71e-48a0-a4de-185c395cd317"),out),"获取回环采集服务"); capture=out.getValue();
            sink.format(rate,channels,bits); check(call(client,10),"开始采集"); started=true;
            IntByReference size=new IntByReference(), frames=new IntByReference(),flags=new IntByReference();
            while(running.getAsBoolean()) {
                check(call(capture,5,size),"读取采集状态（设备可能已断开）");
                if(size.getValue()==0) { Thread.sleep(4); continue; }
                check(call(capture,3,out,frames,flags,null,null),"读取回环音频");
                try {
                    int n=frames.getValue();
                    float[] stereo=new float[n*2];
                    if((flags.getValue()&2)==0) {
                        byte[] data=out.getValue().getByteArray(0,n*align);
                        decode(data,n,channels,bits,tag,mask,stereo);
                    }
                    sink.samples(stereo,rate);
                } finally { check(call(capture,4,frames.getValue()),"释放回环缓冲"); }
            }
        } finally {
            if(started) call(client,11);
            release(capture); if(format!=null&&!process) Ole.INSTANCE.CoTaskMemFree(format);
            release(client); release(device); release(en); Ole.INSTANCE.CoUninitialize();
        }
    }
    static void decode(byte[] data,int frames,int channels,int bits,int tag,int mask,float[] output) {
        int bytes=bits/8;
        for(int f=0;f<frames;f++) {
            double l=0,r=0,wl=0,wr=0; int speaker=1;
            for(int c=0;c<channels;c++) {
                int pos=(f*channels+c)*bytes, v=0;
                for(int b=0;b<bytes;b++) v|=(data[pos+b]&255)<<(8*b);
                float x=tag==3?Float.intBitsToFloat(v):bits==16?(short)v/32768f:bits==24?(v<<8)/2147483648f:v/2147483648f;
                if(!Float.isFinite(x)) x=0;
                double a=0,b=0;
                if(channels==1) {a=1;b=1;}
                else if(channels==2||mask==0) { if(c==0)a=1; else if(c==1)b=1; else {a=.5;b=.5;} }
                else {
                    while((mask&speaker)==0&&speaker!=0) speaker<<=1;
                    switch(speaker) {
                        case 1,16,64,512,4096,32768 -> a= speaker==1?1:.707;
                        case 2,32,128,1024,16384,131072 -> b= speaker==2?1:.707;
                        case 8 -> { /* LFE omitted from stereo downmix */ }
                        default -> {a=.707;b=.707;}
                    }
                    speaker<<=1;
                }
                l+=x*a; r+=x*b; wl+=a;wr+=b;
            }
            output[f*2]=(float)(l/Math.max(1,wl)); output[f*2+1]=(float)(r/Math.max(1,wr));
        }
    }
}
