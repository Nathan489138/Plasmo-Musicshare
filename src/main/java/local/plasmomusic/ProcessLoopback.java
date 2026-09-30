package local.plasmomusic;
import com.sun.jna.*;
import com.sun.jna.ptr.*;
import com.sun.jna.win32.StdCallLibrary;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Process-tree exclusion, Windows build 20348+. COM callbacks retained until native release. */
final class ProcessLoopback {
    interface Mmdev extends StdCallLibrary {
        Mmdev INSTANCE=Native.load("Mmdevapi",Mmdev.class);
        int ActivateAudioInterfaceAsync(WString path,Pointer iid,Pointer params,Pointer handler,PointerByReference operation);
    }
    interface Query extends StdCallLibrary.StdCallCallback {int invoke(Pointer self,Pointer iid,PointerByReference out);}
    interface Ref extends StdCallLibrary.StdCallCallback {int invoke(Pointer self);}
    interface Completed extends StdCallLibrary.StdCallCallback {int invoke(Pointer self,Pointer operation);}
    private static final Set<Handler> LIVE=ConcurrentHashMap.newKeySet();
    private static final class Handler {
        final Memory vtable=new Memory(4L*Native.POINTER_SIZE),object=new Memory(Native.POINTER_SIZE);
        final Memory args=new Memory(12),variant=new Memory(24);
        final CountDownLatch done=new CountDownLatch(1);
        final AtomicInteger refs=new AtomicInteger(1);
        volatile Pointer client; volatile int hr=0x80004005; volatile boolean abandoned;
        final Query query=(self,iid,out)->{
            for(String s:List.of("00000000-0000-0000-c000-000000000046","41d949ab-9862-444a-80f6-c261334da5eb","94ea2b94-e9cc-49e0-c0ff-ee64ca8f5b90")){
                if(Arrays.equals(iid.getByteArray(0,16),Wasapi.guid(s).getByteArray(0,16))){out.setValue(object);refs.incrementAndGet();return 0;}
            }
            out.setValue(null);return 0x80004002;
        };
        final Ref add=self->refs.incrementAndGet();
        final Ref release=self->{int n=refs.decrementAndGet();if(n==0)LIVE.remove(this);return n;};
        final Completed completed=(self,op)->{
            synchronized(this){
                try {IntByReference result=new IntByReference();PointerByReference out=new PointerByReference();
                    hr=Wasapi.call(op,3,result,out);if(hr>=0)hr=result.getValue();client=out.getValue();
                    if(abandoned){Wasapi.release(client);client=null;}
                }catch(Throwable t){hr=0x80004005;}
                finally{done.countDown();}
            }
            return 0;
        };
        Handler(int pid){
            vtable.setPointer(0,CallbackReference.getFunctionPointer(query));vtable.setPointer(Native.POINTER_SIZE,CallbackReference.getFunctionPointer(add));
            vtable.setPointer(2L*Native.POINTER_SIZE,CallbackReference.getFunctionPointer(release));vtable.setPointer(3L*Native.POINTER_SIZE,CallbackReference.getFunctionPointer(completed));object.setPointer(0,vtable);
            args.clear();args.setInt(0,1);args.setInt(4,pid);args.setInt(8,1);
            variant.clear();variant.setShort(0,(short)65);variant.setInt(8,12);variant.setPointer(16,args);
        }
        synchronized void abandon(){abandoned=true;if(done.getCount()==0){Wasapi.release(client);client=null;}}
    }
    static Pointer activate(int excludePid)throws InterruptedException {
        if(Native.POINTER_SIZE!=8)throw new IllegalStateException("应用隔离模式需要 64 位 Java");
        Handler h=new Handler(excludePid);LIVE.add(h);PointerByReference operation=new PointerByReference();
        try {
            Wasapi.check(Mmdev.INSTANCE.ActivateAudioInterfaceAsync(new WString("VAD\\Process_Loopback"),
                Wasapi.guid("1cb9ad4c-dbfa-4c32-b178-c2f568a703b2"),h.variant,h.object,operation),"应用隔离需要 Windows 11 / build 20348+");
            if(!h.done.await(10,TimeUnit.SECONDS)){h.abandon();throw new IllegalStateException("应用音频隔离启动超时");}
            Wasapi.check(h.hr,"应用音频隔离启动失败");return h.client;
        }catch(InterruptedException|RuntimeException e){h.abandon();throw e;}
        finally{Wasapi.release(operation.getValue());h.release.invoke(h.object);}
    }
}
