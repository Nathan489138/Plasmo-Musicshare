package local.plasmomusic;

import java.time.Instant;
import java.util.*;

/** A read-only process inventory. Do not inspect command lines or environment variables. */
public final class ProcessCatalog {
    public record Process(long pid,long parent,String path,Instant started) {
        public String name(){int slash=path.lastIndexOf('\\');return path.substring(slash+1);}
        public String identity(){return pid+":"+started+":"+path;}
    }
    public record Program(String path,String name,int instances){}
    public record Snapshot(List<Process> processes,Set<Long> blocked,List<Program> programs,String error) {
        public Snapshot{processes=List.copyOf(processes);blocked=Set.copyOf(blocked);programs=List.copyOf(programs);}
        public List<Process> targets(Set<String> selected){
            Map<Long,Process> candidates=new HashMap<>(),all=new HashMap<>();
            for(Process p:processes){all.put(p.pid(),p);if(!blocked.contains(p.pid())&&selected.contains(p.path()))candidates.put(p.pid(),p);}
            List<Process> roots=new ArrayList<>();
            for(Process p:candidates.values()){
                long parent=p.parent();Set<Long> visited=new HashSet<>();boolean covered=false;
                while(parent!=0&&visited.add(parent)){
                    if(candidates.containsKey(parent)){covered=true;break;}
                    Process ancestor=all.get(parent);if(ancestor==null)break;parent=ancestor.parent();
                }
                if(!covered)roots.add(p);
            }
            roots.sort(Comparator.comparingLong(Process::pid));return List.copyOf(roots);
        }
    }
    public static Snapshot empty(){return new Snapshot(List.of(),Set.of(),List.of(),"");}
    public static Snapshot scan(){
        List<Process> processes=new ArrayList<>();
        try(var handles=ProcessHandle.allProcesses()){
            handles.forEach(handle->{try{
                ProcessHandle.Info info=handle.info();
                String path=info.command().orElse("");
                processes.add(new Process(handle.pid(),handle.parent().map(ProcessHandle::pid).orElse(0L),
                    path.isBlank()?"":CaptureSelection.normalize(path),info.startInstant().orElse(Instant.EPOCH)));
            }catch(SecurityException ignored){}});
            return from(processes,ProcessHandle.current().pid());
        }catch(RuntimeException failure){return new Snapshot(List.of(),Set.of(),List.of(),"程序扫描失败："+failure.getClass().getSimpleName());}
    }
    static Snapshot from(List<Process> processes,long minecraft){
        Map<Long,Process> all=new HashMap<>();for(Process p:processes)all.put(p.pid(),p);
        Set<Long> blocked=new HashSet<>();
        long ancestor=minecraft;while(ancestor!=0&&blocked.add(ancestor)){Process p=all.get(ancestor);if(p==null)break;ancestor=p.parent();}
        // Exclude Minecraft descendants too. Its ancestors cannot be selected,
        // since capturing an ancestor tree would pull Minecraft back into the mix.
        Set<Long> descendants=new HashSet<>();descendants.add(minecraft);
        boolean changed;do{changed=false;for(Process p:processes)if(descendants.contains(p.parent())&&descendants.add(p.pid()))changed=true;}while(changed);
        blocked.addAll(descendants);
        Map<String,Integer> groups=new TreeMap<>();
        for(Process p:processes)if(!p.path().isBlank()&&!blocked.contains(p.pid()))groups.merge(p.path(),1,Integer::sum);
        List<Program> programs=new ArrayList<>();groups.forEach((path,count)->programs.add(new Program(path,path.substring(path.lastIndexOf('\\')+1),count)));
        programs.sort(Comparator.comparing(Program::name).thenComparing(Program::path));
        return new Snapshot(processes,blocked,programs,"");
    }
    public static boolean stillSame(Process process){
        return ProcessHandle.of(process.pid()).filter(ProcessHandle::isAlive).map(handle->{
            ProcessHandle.Info info=handle.info();
            return info.command().map(CaptureSelection::normalize).filter(process.path()::equals).isPresent()
                &&info.startInstant().orElse(Instant.EPOCH).equals(process.started());
        }).orElse(false);
    }
}
