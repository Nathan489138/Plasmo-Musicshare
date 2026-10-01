package local.plasmomusic;

import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class ProcessSelectionTest {
    private static final String MUSIC=CaptureSelection.normalize("C:/Apps/Music/music.exe");
    private static final String BROWSER=CaptureSelection.normalize("C:/Apps/Browser/browser.exe");
    private static final String KOOK=CaptureSelection.normalize("C:/Apps/KOOK/kook.exe");
    private static ProcessCatalog.Process process(long pid,long parent,String path){return new ProcessCatalog.Process(pid,parent,CaptureSelection.normalize(path),Instant.ofEpochSecond(pid));}
    private static ProcessCatalog.Snapshot snapshot(long musicPid){return ProcessCatalog.from(List.of(
        process(1,0,"C:/Windows/explorer.exe"),process(90,1,"C:/Launcher/launcher.exe"),process(100,90,"C:/Java/javaw.exe"),
        process(101,100,"C:/MC/helper.exe"),process(musicPid,1,MUSIC),process(musicPid+1,musicPid,MUSIC),
        process(301,1,BROWSER),process(302,301,"C:/Apps/Browser/audio-helper.exe"),process(401,1,KOOK)),100);}
    private static void check(boolean pass,String label){if(!pass)throw new AssertionError(label);System.out.println("PASS "+label);}
    public static void main(String[] args)throws Exception {
        ProcessCatalog.Snapshot scan=snapshot(201);
        check(scan.blocked().containsAll(Set.of(1L,90L,100L,101L)),"Minecraft tree and ancestors cannot be included as capture roots");
        check(scan.programs().stream().noneMatch(p->p.name().equals("javaw.exe")||p.name().equals("launcher.exe")),"Minecraft and its launcher are omitted from selectable programs");
        check(scan.targets(Set.of(MUSIC)).stream().map(ProcessCatalog.Process::pid).toList().equals(List.of(201L)),"music program selection deduplicates parent and child processes");
        check(scan.targets(Set.of(MUSIC,BROWSER,CaptureSelection.normalize("C:/Apps/Browser/audio-helper.exe"))).size()==2,"multiple selected programs never double-capture a selected child tree");
        check(scan.targets(Set.of(MUSIC)).stream().noneMatch(p->p.path().equals(KOOK)),"unselected KOOK is excluded from the target whitelist");
        check(scan.targets(Set.of()).isEmpty(),"empty selection never falls back to global audio");
        check(snapshot(501).targets(Set.of(MUSIC)).get(0).pid()==501,"saved executable identity resolves the restarted player's new PID");
        Path folder=Files.createTempDirectory("musicshare-selection-test"),file=folder.resolve("capture.properties");
        try{
            CaptureSelection choice=new CaptureSelection(false,Set.of("C:/Apps/Music/MUSIC.EXE",BROWSER));choice.save(file);
            check(CaptureSelection.read(file).equals(choice),"program mode and multiple paths survive reload without saving PIDs");
            check(CaptureSelection.read(folder.resolve("missing.properties")).equals(CaptureSelection.defaults()),"existing installations retain global mode by default");
        }finally{Files.deleteIfExists(file);Files.delete(folder);}
        mixing();
        ProcessCatalog.Snapshot real=ProcessCatalog.scan();
        check(real.error().isEmpty()&&!real.programs().isEmpty()&&real.blocked().contains(ProcessHandle.current().pid()),"running process inventory scans this host without exposing its own capture tree");
    }
    private static void mixing()throws Exception {
        AtomicBoolean running=new AtomicBoolean(true);
        AtomicReference<ProcessCatalog.Snapshot> catalog=new AtomicReference<>(snapshot(201));
        Set<String> opened=ConcurrentHashMap.newKeySet();
        Set<String> active=ConcurrentHashMap.newKeySet();
        CountDownLatch mixed=new CountDownLatch(1),restarted=new CountDownLatch(1);
        AtomicReference<Throwable> failure=new AtomicReference<>();
        MusicAddon.CaptureBackend backend=(id,alive,sink)->{
            opened.add(id);active.add(id);
            if(id.equals("process:501"))restarted.countDown();
            boolean browser=id.equals("process:301");float[] samples=new float[1920*2];
            for(int i=0;i<1920;i++){samples[i*2]=browser?.3f:.1f;samples[i*2+1]=browser?.07f:-.05f;}
            sink.format(48000,2,16);
            try{while(alive.getAsBoolean()){sink.samples(samples,48000);Thread.sleep(20);}}
            finally{active.remove(id);}
        };
        Thread coordinator=new Thread(()->{try{SelectedProcessCapture.capture(Set.of(MUSIC,BROWSER),running::get,new Wasapi.Sink(){
            public void format(int rate,int channels,int bits){check(rate==48000&&channels==2,"multiple programs use one fixed stereo mix format");}
            public void samples(float[] samples,int rate){if(Math.abs(samples[0]-.4f)<.001&&Math.abs(samples[1]-.02f)<.001)mixed.countDown();}
        },catalog::get,backend,p->true);}catch(InterruptedException stopped){Thread.currentThread().interrupt();}catch(Throwable error){failure.set(error);}},"Process-selection-test");
        coordinator.start();
        try{
            check(mixed.await(4,TimeUnit.SECONDS),"selected programs are summed on matching stereo frames, not concatenated");
            check(opened.equals(Set.of("process:201","process:301")),"native backends are opened only for selected roots, never KOOK or child duplicates");
            catalog.set(snapshot(501));
            check(restarted.await(4,TimeUnit.SECONDS),"process coordinator follows a restarted player without toggling sharing");
            check(!active.contains("process:201"),"old capture closes before replacement topology starts");
        }finally{running.set(false);coordinator.interrupt();coordinator.join(3000);}
        check(!coordinator.isAlive()&&active.isEmpty()&&failure.get()==null,"stopping sharing closes every selected process worker");
        AtomicInteger calls=new AtomicInteger();
        AtomicBoolean emptyRunning=new AtomicBoolean(true);
        SelectedProcessCapture.capture(Set.of(),emptyRunning::get,new Wasapi.Sink(){
            public void format(int rate,int channels,int bits){}
            public void samples(float[] samples,int rate){check(samples.length==1920&&java.util.stream.IntStream.range(0,samples.length).allMatch(i->samples[i]==0),"empty selected mode emits silence without capturing unrelated audio");emptyRunning.set(false);}
        },()->snapshot(201),(id,alive,sink)->{calls.incrementAndGet();},p->true);
        check(calls.get()==0,"empty selected mode opens no global or device loopback");
    }
}
