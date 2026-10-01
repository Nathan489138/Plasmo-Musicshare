package local.plasmomusic;

import java.util.*;
import net.minecraft.*;

/** Searchable program-level multi-select; draft choices apply together. */
public final class ProcessSelectionScreen extends class_437 {
    private final MusicAddon app;
    private final class_437 parent;
    private final Set<String> selected;
    private boolean global;
    private class_342 search;
    private class_4185 mode,previous,next;
    private final List<class_4185> rows=new ArrayList<>();
    private List<ProcessCatalog.Program> visible=List.of();
    private ProcessCatalog.Snapshot shown;
    private int page,left,width,capacity;
    public ProcessSelectionScreen(MusicAddon app,class_437 parent){
        super(class_2561.method_43470("选择音乐抓取程序"));this.app=app;this.parent=parent;
        selected=new HashSet<>(app.captureSelection.programs());global=app.captureSelection.global();
    }
    private class_4185 button(String text,int x,int y,int w,Runnable action){
        return method_37063(class_4185.method_46430(class_2561.method_43470(text),b->action.run()).method_46434(x,y,w,20).method_46431());
    }
    @Override protected void method_25426(){
        String query=search==null?"":search.method_1882();rows.clear();
        width=Math.min(520,field_22789-24);left=(field_22789-width)/2;
        mode=button("",left,29,width,()->{global=!global;updateRows();});
        search=method_37063(new class_342(field_22793,left,54,width-92,20,class_2561.method_43470("搜索程序名称")));
        search.method_1852(query);search.method_1863(value->{page=0;filter();});
        button("扫描程序",left+width-88,54,88,app::refreshProcesses);
        capacity=Math.max(1,(field_22790-154)/24);
        for(int i=0;i<capacity;i++){final int row=i;rows.add(button("",left,82+i*24,width,()->toggle(row)));}
        previous=button("上一页",left,field_22790-66,76,()->{page--;updateRows();});
        next=button("下一页",left+width-76,field_22790-66,76,()->{page++;updateRows();});
        button("取消",left,field_22790-25,(width-8)/2,this::method_25419);
        button("应用",left+(width+8)/2,field_22790-25,(width-8)/2,()->{app.setCaptureSelection(new CaptureSelection(global,selected));method_25419();});
        filter();
    }
    private void filter(){
        shown=app.processSnapshot;
        String query=search.method_1882().toLowerCase(Locale.ROOT).strip();
        Map<String,ProcessCatalog.Program> programs=new HashMap<>();for(var p:shown.programs())programs.put(p.path(),p);
        for(String path:selected)programs.putIfAbsent(path,new ProcessCatalog.Program(path,path.substring(path.lastIndexOf('\\')+1),0));
        visible=programs.values().stream().filter(p->p.path().contains(query)||p.name().contains(query))
            .sorted(Comparator.<ProcessCatalog.Program,Boolean>comparing(p->!selected.contains(p.path())).thenComparing(ProcessCatalog.Program::name).thenComparing(ProcessCatalog.Program::path)).toList();
        updateRows();
    }
    private void toggle(int row){
        int index=page*capacity+row;if(index>=visible.size())return;
        String path=visible.get(index).path();if(!selected.remove(path))selected.add(path);
        global=false;updateRows();
    }
    private void updateRows(){
        int pages=Math.max(1,(visible.size()+capacity-1)/capacity);page=Math.max(0,Math.min(pages-1,page));
        mode.method_25355(class_2561.method_43470(global?"抓取模式：全局（排除 Minecraft）· 点击切换":"抓取模式：指定程序 · 点击切换"));
        for(int i=0;i<rows.size();i++){
            int index=page*capacity+i;class_4185 row=rows.get(i);row.field_22764=index<visible.size();row.field_22763=index<visible.size();
            if(index<visible.size()){
                var program=visible.get(index);String text=(selected.contains(program.path())?"☑ ":"☐ ")+program.name()+"  ·  "+(program.instances()==0?"未运行":program.instances()+" 个进程");
                String directory=program.path().substring(0,Math.max(0,program.path().lastIndexOf('\\')));
                if(text.length()+directory.length()>70)directory="…"+directory.substring(Math.max(0,directory.length()-Math.max(0,66-text.length())));
                row.method_25355(class_2561.method_43470(text+"  "+directory));
            }
        }
        previous.field_22763=page>0;next.field_22763=page+1<pages;
    }
    @Override public void method_25393(){if(shown!=app.processSnapshot)filter();}
    @Override public void method_25394(class_332 context,int mouseX,int mouseY,float delta){
        super.method_25394(context,mouseX,mouseY,delta);
        context.method_25300(field_22793,"程序选择 · 可多选",field_22789/2,9,0xffffffff);
        int pages=Math.max(1,(visible.size()+capacity-1)/capacity);
        context.method_25300(field_22793,"已选 "+selected.size()+" 个 · "+(page+1)+" / "+pages+" 页",field_22789/2,field_22790-61,0xffb9e2d4);
        String status=app.processScanStatus+" · 指定模式只采集所选程序及子进程";
        context.method_25300(field_22793,status,field_22789/2,field_22790-39,0xffb7c7d8);
    }
    @Override public boolean method_25421(){return false;}
    @Override public void method_25419(){field_22787.method_1507(parent);}
}
