package local.plasmomusic.compat;

import java.io.*;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Structural checks only: no PV, Minecraft or audio class is initialized. */
public final class VoiceCompatibility {
    @FunctionalInterface public interface Classes {ClassNode read(String name)throws Exception;}
    private static final String OWN="local/plasmomusic/";
    private static final String CAPTURE="su/plo/voice/client/audio/capture/VoiceAudioCapture";
    private static final String ACTIVATION="Lsu/plo/voice/api/client/audio/capture/ClientActivation;";
    private static final String ENCODER="Lsu/plo/voice/api/audio/codec/AudioEncoder;";
    private static final String MONITOR="su/plo/voice/client/gui/settings/MicrophoneTestController";
    private static final String SAMPLES="su/plo/voice/api/client/event/audio/capture/AudioCaptureProcessedEvent$ProcessedSamples";
    private static final String SCREEN="su/plo/voice/client/gui/settings/VoiceSettingsScreen";
    private static final String EXTRACTOR="su/plo/voice/client/render/voice/EntityIconStateExtractor";
    private final Classes classes;
    private final Map<String,ClassNode> cache=new HashMap<>();
    private VoiceCompatibility(Classes classes){this.classes=classes;}
    public static String check(Classes classes) {
        try {
            VoiceCompatibility check=new VoiceCompatibility(classes);
            check.hooks();
            check.linkage();
            return "";
        }catch(Exception | LinkageError failure){return failure.getClass().getSimpleName()+": "+failure.getMessage();}
    }
    private ClassNode node(String name)throws Exception {
        ClassNode result=cache.get(name);
        if(result==null){result=classes.read(name);if(result==null)throw new IOException("Missing class "+name);cache.put(name,result);}
        return result;
    }
    private void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private MethodNode declared(String owner,String name,String descriptor)throws Exception {
        for(MethodNode method:node(owner).methods)if(method.name.equals(name)&&method.desc.equals(descriptor))return method;
        throw new IllegalStateException("Changed method "+owner+"."+name+descriptor);
    }
    private void field(String owner,String name,String descriptor,boolean isStatic)throws Exception {
        FieldNode found=resolveField(owner,name,descriptor,new HashSet<>());
        require(found!=null&&((found.access&Opcodes.ACC_STATIC)!=0)==isStatic,"Changed field "+owner+"."+name+":"+descriptor);
    }
    private void method(String owner,String name,String descriptor,boolean isStatic)throws Exception {
        MethodNode found=resolveMethod(owner,name,descriptor,new HashSet<>());
        require(found!=null&&((found.access&Opcodes.ACC_STATIC)!=0)==isStatic,"Changed method "+owner+"."+name+descriptor);
    }
    private MethodNode resolveMethod(String owner,String name,String desc,Set<String> visited)throws Exception {
        if(!visited.add(owner))return null;
        ClassNode type=node(owner);
        for(MethodNode m:type.methods)if(m.name.equals(name)&&m.desc.equals(desc))return m;
        if(name.equals("<init>"))return null;
        if(type.superName!=null){MethodNode m=resolveMethod(type.superName,name,desc,visited);if(m!=null)return m;}
        for(String parent:type.interfaces){MethodNode m=resolveMethod(parent,name,desc,visited);if(m!=null)return m;}
        return null;
    }
    private FieldNode resolveField(String owner,String name,String desc,Set<String> visited)throws Exception {
        if(!visited.add(owner))return null;
        ClassNode type=node(owner);
        for(FieldNode f:type.fields)if(f.name.equals(name)&&f.desc.equals(desc))return f;
        for(String parent:type.interfaces){FieldNode f=resolveField(parent,name,desc,visited);if(f!=null)return f;}
        return type.superName==null?null:resolveField(type.superName,name,desc,visited);
    }
    private void invocation(MethodNode method,String owner,String name,String desc,int opcode)throws Exception {
        int count=0;
        for(AbstractInsnNode instruction:method.instructions)if(instruction instanceof MethodInsnNode call
            &&call.owner.equals(owner)&&call.name.equals(name)&&call.desc.equals(desc)&&call.getOpcode()==opcode)count++;
        require(count==1,"Changed injection site "+method.name+" -> "+owner+"."+name+desc+" (expected one call, found "+count+")");
    }
    private void hooks()throws Exception {
        declared(CAPTURE,"processActivation","(Lsu/plo/voice/api/client/audio/device/InputDevice;"+ACTIVATION+"Lsu/plo/voice/api/client/audio/capture/ClientActivation$Result;[SLsu/plo/voice/client/audio/capture/VoiceAudioCapture$EncodedCapture;)V");
        declared(CAPTURE,"cleanup","()V");
        field(CAPTURE,"monoEncoder",ENCODER,false);field(CAPTURE,"stereoEncoder",ENCODER,false);
        field(CAPTURE,"activationStreams","Ljava/util/Set;",false);
        method(CAPTURE,"encode","("+ENCODER+"[S)[B",false);
        method(CAPTURE,"sendVoicePacket","("+ACTIVATION+"Z[B)V",false);
        method(CAPTURE,"sendVoiceEndPacket","("+ACTIVATION+")V",false);
        MethodNode monitor=declared(MONITOR,"onAudioCaptureProcessed","(Lsu/plo/voice/api/client/event/audio/capture/AudioCaptureProcessedEvent;)V");
        method(MONITOR,"isActive","()Z",false);
        invocation(monitor,SAMPLES,"getSamples","(Z)[S",Opcodes.INVOKEINTERFACE);
        invocation(monitor,"su/plo/voice/api/client/audio/source/LoopbackSource","write","([S)V",Opcodes.INVOKEINTERFACE);
        invocation(monitor,"su/plo/voice/api/util/AudioUtil","calculateHighestAudioLevel","([S)D",Opcodes.INVOKESTATIC);
        field(SCREEN,"voiceClient","Lsu/plo/voice/client/BaseVoiceClient;",false);
        field(SCREEN,"config","Lsu/plo/voice/client/config/VoiceClientConfig;",false);
        invocation(declared(SCREEN,"init","()V"),"su/plo/voice/client/gui/settings/VoiceSettingsNavigation","init","()V",Opcodes.INVOKEVIRTUAL);
        boolean icon=node(EXTRACTOR).methods.stream().anyMatch(m->m.name.equals("extractPlayer")&&(
            m.desc.equals("(Lnet/minecraft/class_1309;Lnet/minecraft/class_746;)Lsu/plo/voice/client/render/voice/EntityVoiceIconState;")||
            m.desc.equals("(Lnet/minecraft/class_1297;Lnet/minecraft/class_746;)Lsu/plo/voice/client/render/voice/EntityVoiceIconState;")));
        require(icon,"Changed local player icon contract");
    }
    /** Follow our class graph, checking every direct PV/config/slib member reference. */
    private void linkage()throws Exception {
        ArrayDeque<String> pending=new ArrayDeque<>(List.of(OWN+"MusicAddon",OWN+"MusicScreen",OWN+"mixin/VoiceCaptureMixin",
            OWN+"mixin/MicrophoneMonitorMixin",OWN+"mixin/SelfMusicIconMixin",OWN+"mixin/VoiceSettingsMusicTabMixin"));
        Set<String> visited=new HashSet<>();
        while(!pending.isEmpty()){
            String name=pending.remove();if(!visited.add(name))continue;
            ClassNode ours;
            try(InputStream input=VoiceCompatibility.class.getClassLoader().getResourceAsStream(name+".class")){
                if(input==null)throw new IOException("Missing own class "+name);
                ours=new ClassNode();new ClassReader(input).accept(ours,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
            }
            type(ours.superName,pending);for(String parent:ours.interfaces)type(parent,pending);
            for(FieldNode f:ours.fields)descriptor(f.desc,pending);
            for(MethodNode m:ours.methods){
                descriptor(m.desc,pending);
                for(AbstractInsnNode instruction:m.instructions){
                    if(instruction instanceof MethodInsnNode call){
                        type(call.owner,pending);descriptor(call.desc,pending);
                        if(call.owner.startsWith("su/plo/"))method(call.owner,call.name,call.desc,call.getOpcode()==Opcodes.INVOKESTATIC);
                    }else if(instruction instanceof FieldInsnNode f){
                        type(f.owner,pending);descriptor(f.desc,pending);
                        if(f.owner.startsWith("su/plo/"))field(f.owner,f.name,f.desc,f.getOpcode()==Opcodes.GETSTATIC||f.getOpcode()==Opcodes.PUTSTATIC);
                    }else if(instruction instanceof TypeInsnNode t)type(t.desc,pending);
                    else if(instruction instanceof InvokeDynamicInsnNode d){
                        descriptor(d.desc,pending);
                        for(Object argument:d.bsmArgs)if(argument instanceof Handle h){
                            type(h.getOwner(),pending);descriptor(h.getDesc(),pending);
                            if(h.getOwner().startsWith("su/plo/"))method(h.getOwner(),h.getName(),h.getDesc(),h.getTag()==Opcodes.H_INVOKESTATIC);
                        }
                    }else if(instruction instanceof LdcInsnNode ldc&&ldc.cst instanceof Type t)descriptor(t.getDescriptor(),pending);
                }
            }
        }
    }
    private void descriptor(String desc,ArrayDeque<String> pending)throws Exception {
        Type t=Type.getType(desc);
        if(t.getSort()==Type.METHOD){for(Type argument:t.getArgumentTypes())descriptor(argument.getDescriptor(),pending);descriptor(t.getReturnType().getDescriptor(),pending);}
        else if(t.getSort()==Type.ARRAY)descriptor(t.getElementType().getDescriptor(),pending);
        else if(t.getSort()==Type.OBJECT)type(t.getInternalName(),pending);
    }
    private void type(String name,ArrayDeque<String> pending)throws Exception {
        if(name==null)return;
        if(name.startsWith("[")){descriptor(name,pending);return;}
        if(name.startsWith(OWN)&&!name.startsWith(OWN+"compat/"))pending.add(name);
        else if(name.startsWith("su/plo/"))node(name);
    }
}
