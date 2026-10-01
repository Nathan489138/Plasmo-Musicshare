package local.plasmomusic;
import java.util.*;
import java.time.Instant;
import java.util.concurrent.atomic.*;
public final class ProcessTargetTrackerTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);System.out.println("PASS "+message);}
    public static void main(String[] args)throws Exception{
        String path="c:\\music.exe";
        var initial=ProcessCatalog.from(List.of(new ProcessCatalog.Process(10,0,path,Instant.EPOCH)),100);
        var restarted=ProcessCatalog.from(List.of(new ProcessCatalog.Process(20,0,path,Instant.EPOCH)),100);
        AtomicReference<ProcessCatalog.Snapshot> manual=new AtomicReference<>(initial),result=new AtomicReference<>(ProcessCatalog.empty());
        AtomicLong clock=new AtomicLong();AtomicInteger scans=new AtomicInteger();AtomicLong live=new AtomicLong(10);
        var tracker=new ProcessTargetTracker(Set.of(path),manual::get,()->{scans.incrementAndGet();return result.get();},p->p.pid()==live.get(),clock::get);
        for(int i=0;i<1000;i++){clock.addAndGet(250_000_000L);tracker.get();}
        check(scans.get()==0,"stable selected playback never rescans the inventory");
        live.set(0);tracker.get();for(int i=0;i<19;i++){clock.addAndGet(250_000_000L);tracker.get();}
        check(scans.get()==1,"missing target recovery backs off rather than scanning every tick");
        result.set(restarted);live.set(20);clock.addAndGet(250_000_000L);
        check(tracker.get().targets(Set.of(path)).getFirst().pid()==20,"restarted executable is recovered without changing user selection");
        int count=scans.get();for(int i=0;i<1000;i++){clock.addAndGet(250_000_000L);tracker.get();}
        check(scans.get()==count,"successful recovery stops background inventory scans");
        var refreshed=ProcessCatalog.from(initial.processes(),100);
        manual.set(refreshed);live.set(10);check(tracker.get()==refreshed,"manual scan updates capture targets immediately");
        var empty=new ProcessTargetTracker(Set.of(),manual::get,()->{throw new AssertionError("empty selection scanned");},p->true,clock::get);empty.get();
        check(true,"empty selection performs no recovery scans");
        var executor=java.util.concurrent.Executors.newSingleThreadExecutor();
        var entered=new java.util.concurrent.CountDownLatch(1);var stopped=new java.util.concurrent.CountDownLatch(1);
        var asynchronous=new ProcessTargetTracker(Set.of(path),ProcessCatalog::empty,()->{
            entered.countDown();try{new java.util.concurrent.CountDownLatch(1).await();}catch(InterruptedException e){Thread.currentThread().interrupt();}finally{stopped.countDown();}return initial;
        },p->false,clock::get,executor);
        try{
            check(asynchronous.get().processes().isEmpty(),"audio coordinator returns cached silence without waiting for a recovery scan");
            check(entered.await(2,java.util.concurrent.TimeUnit.SECONDS),"recovery scans execute on a separate worker");
        }finally{asynchronous.close();}
        check(stopped.await(2,java.util.concurrent.TimeUnit.SECONDS)&&executor.awaitTermination(2,java.util.concurrent.TimeUnit.SECONDS),"stopping capture cancels and closes its recovery worker");
    }
}
