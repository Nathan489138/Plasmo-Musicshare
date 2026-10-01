package local.plasmomusic;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.*;

/** Open the actual include-tree API against an isolated silent helper, never user calls. */
public final class ProcessCaptureNativeTest {
    public static void main(String[] args)throws Exception {
        if(args.length>0&&args[0].equals("--child")){System.out.println("READY");System.out.flush();System.in.read();return;}
        String java=Path.of(System.getProperty("java.home"),"bin","java.exe").toString();
        Process helper=new ProcessBuilder(java,"-cp",System.getProperty("java.class.path"),ProcessCaptureNativeTest.class.getName(),"--child").redirectErrorStream(true).start();
        try{
            String ready=new BufferedReader(new InputStreamReader(helper.getInputStream())).readLine();
            if(!"READY".equals(ready))throw new AssertionError("Silent helper did not start");
            boolean[] opened={false};long deadline=System.nanoTime()+1_000_000_000L;
            Wasapi.capture("process:"+helper.pid(),()->System.nanoTime()<deadline,new Wasapi.Sink(){
                public void format(int rate,int channels,int bits){opened[0]=rate==48000&&channels==2&&bits==16;}
                public void samples(float[] stereo,int rate){}
            });
            if(!opened[0])throw new AssertionError("Selected-process native stream did not open");
            System.out.println("PASS NATIVE INCLUDE-TREE: selected silent helper capture opens and closes at 48 kHz stereo; no user audio was recorded");
        }finally{
            helper.getOutputStream().close();
            if(!helper.waitFor(2,TimeUnit.SECONDS)){helper.destroy();if(!helper.waitFor(2,TimeUnit.SECONDS))helper.destroyForcibly();}
        }
    }
}
