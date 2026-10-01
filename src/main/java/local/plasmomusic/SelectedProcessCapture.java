package local.plasmomusic;

import java.util.*;
import java.util.function.*;

/** Independent process FIFOs are mixed on a single 48 kHz timeline, never concatenated. */
final class SelectedProcessCapture {
    private static final int RATE=48000,FRAMES=960;
    private static final long FRAME_NANOS=20_000_000L;
    private static final class Worker {
        final ProcessCatalog.Process process;
        final AudioBuffer buffer=new AudioBuffer();
        volatile boolean open=true,ready;
        volatile long retry;
        Thread thread;
        Worker(ProcessCatalog.Process process){this.process=process;}
        void start(MusicAddon.CaptureBackend backend,BooleanSupplier running,Predicate<ProcessCatalog.Process> current){
            thread=new Thread(()->{
                long[] checkedAt={0};boolean[] same={false};
                BooleanSupplier valid=()->{
                    long now=System.nanoTime();if(now>=checkedAt[0]){same[0]=current.test(process);checkedAt[0]=now+250_000_000L;}
                    return same[0];
                };
                try{backend.capture("process:"+process.pid(),()->open&&running.getAsBoolean()&&!Thread.currentThread().isInterrupted()&&valid.getAsBoolean(),new Wasapi.Sink(){
                    public void format(int rate,int channels,int bits){ready=true;}
                    public void samples(float[] stereo,int rate){if(open)buffer.offer(stereo,rate);}
                });}
                catch(InterruptedException stopped){Thread.currentThread().interrupt();}
                catch(Throwable failure){System.err.println("[Plasmo Musicshare] 程序音频暂不可用："+process.name()+" / "+failure);}
                finally{ready=false;buffer.clear();retry=System.nanoTime()+2_000_000_000L;}
            },"Musicshare-Process-"+process.pid());thread.setDaemon(true);thread.start();
        }
        void close(){open=false;ready=false;buffer.clear();if(thread!=null)thread.interrupt();}
    }
    static void capture(Set<String> selected,BooleanSupplier running,Wasapi.Sink sink,
                        Supplier<ProcessCatalog.Snapshot> catalog,MusicAddon.CaptureBackend backend)throws InterruptedException {
        capture(selected,running,sink,catalog,backend,ProcessCatalog::stillSame);
    }
    static void capture(Set<String> selected,BooleanSupplier running,Wasapi.Sink sink,
                        Supplier<ProcessCatalog.Snapshot> catalog,MusicAddon.CaptureBackend backend,Predicate<ProcessCatalog.Process> current)throws InterruptedException {
        Map<String,Worker> workers=new HashMap<>();
        List<Worker> retired=new ArrayList<>();
        sink.format(RATE,2,16);
        long next=System.nanoTime(),refresh=0;
        try{
            while(running.getAsBoolean()&&!Thread.currentThread().isInterrupted()){
                long now=System.nanoTime();
                if(now>=refresh){
                    Set<String> live=new HashSet<>();
                    for(ProcessCatalog.Process process:catalog.get().targets(selected)){
                        if(!current.test(process))continue;
                        live.add(process.identity());
                        workers.computeIfAbsent(process.identity(),id->new Worker(process));
                    }
                    for(Iterator<Worker> iterator=workers.values().iterator();iterator.hasNext();){Worker worker=iterator.next();if(!live.contains(worker.process.identity())){worker.close();retired.add(worker);iterator.remove();}}
                    retired.removeIf(worker->worker.thread==null||!worker.thread.isAlive());
                    // A previous topology may still be releasing COM. Wait rather
                    // than overlap it with a newly selected ancestor/descendant tree.
                    if(retired.isEmpty())for(Worker worker:workers.values())if((worker.thread==null||!worker.thread.isAlive())&&now>=worker.retry&&worker.open)worker.start(backend,running,current);
                    refresh=now+250_000_000L;
                }
                float[] mixed=new float[FRAMES*2];
                for(Worker worker:workers.values())if(worker.ready){float[] samples=worker.buffer.takeFloat(FRAMES,RATE,true,1,false);for(int i=0;i<mixed.length;i++)mixed[i]+=samples[i];}
                sink.samples(mixed,RATE);
                next+=FRAME_NANOS;
                if(next<now-FRAME_NANOS)next=now+FRAME_NANOS;
                long wait=next-System.nanoTime();if(wait>0)Thread.sleep(wait/1_000_000L,(int)(wait%1_000_000L));
            }
        }finally{
            workers.values().forEach(Worker::close);retired.forEach(Worker::close);
            List<Worker> all=new ArrayList<>(workers.values());all.addAll(retired);
            boolean interrupted=Thread.interrupted();long deadline=System.nanoTime()+800_000_000L;
            for(Worker worker:all)if(worker.thread!=null){long remaining=deadline-System.nanoTime();if(remaining<=0)break;try{worker.thread.join(Math.max(1,remaining/1_000_000L));}catch(InterruptedException stopped){interrupted=true;break;}}
            if(interrupted)Thread.currentThread().interrupt();
        }
    }
}
