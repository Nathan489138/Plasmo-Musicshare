package local.plasmomusic;

import java.util.*;
import java.util.function.*;
import java.util.concurrent.*;

/** Stable targets need no inventory scans; missing executables use bounded recovery backoff. */
final class ProcessTargetTracker implements Supplier<ProcessCatalog.Snapshot>,AutoCloseable {
    private final Set<String> selected;
    private final Supplier<ProcessCatalog.Snapshot> manual,scan;
    private final Predicate<ProcessCatalog.Process> alive;
    private final LongSupplier clock;
    private ProcessCatalog.Snapshot snapshot,lastManual;
    private long retry,backoff=5_000_000_000L;
    private ExecutorService executor;
    private Future<ProcessCatalog.Snapshot> pending;
    ProcessTargetTracker(Set<String> selected,Supplier<ProcessCatalog.Snapshot> manual){
        this(selected,manual,ProcessCatalog::scan,ProcessCatalog::stillSame,System::nanoTime);
        executor=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"Musicshare-Target-Recovery");t.setDaemon(true);return t;});
    }
    ProcessTargetTracker(Set<String> selected,Supplier<ProcessCatalog.Snapshot> manual,
                         Supplier<ProcessCatalog.Snapshot> scan,Predicate<ProcessCatalog.Process> alive,LongSupplier clock){
        this.selected=Set.copyOf(selected);this.manual=manual;this.scan=scan;this.alive=alive;this.clock=clock;
        snapshot=lastManual=manual.get();
    }
    ProcessTargetTracker(Set<String> selected,Supplier<ProcessCatalog.Snapshot> manual,
                         Supplier<ProcessCatalog.Snapshot> scan,Predicate<ProcessCatalog.Process> alive,LongSupplier clock,ExecutorService executor){
        this(selected,manual,scan,alive,clock);this.executor=executor;
    }
    public ProcessCatalog.Snapshot get(){
        if(pending!=null&&pending.isDone()){
            try{ProcessCatalog.Snapshot fresh=pending.get();if(fresh.error().isEmpty())snapshot=fresh;}
            catch(InterruptedException e){Thread.currentThread().interrupt();}
            catch(ExecutionException|CancellationException ignored){}
            pending=null;
        }
        ProcessCatalog.Snapshot updated=manual.get();
        if(updated!=lastManual){if(pending!=null){pending.cancel(true);pending=null;}snapshot=lastManual=updated;retry=0;backoff=5_000_000_000L;}
        Set<String> found=new HashSet<>();
        // Count every selected executable, including descendants covered by a selected parent.
        for(var process:snapshot.processes())if(selected.contains(process.path())&&!snapshot.blocked().contains(process.pid())&&alive.test(process))found.add(process.path());
        long now=clock.getAsLong();
        if(!found.containsAll(selected)&&now>=retry&&pending==null){
            if(executor!=null)pending=executor.submit(scan::get);
            else {ProcessCatalog.Snapshot fresh=scan.get();if(fresh.error().isEmpty())snapshot=fresh;}
            retry=now+backoff;backoff=Math.min(30_000_000_000L,backoff*2);
        }else if(found.containsAll(selected)){backoff=5_000_000_000L;retry=0;}
        return snapshot;
    }
    public void close(){if(pending!=null)pending.cancel(true);if(executor!=null)executor.shutdownNow();}
}
