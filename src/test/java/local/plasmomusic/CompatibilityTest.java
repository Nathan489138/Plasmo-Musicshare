package local.plasmomusic;

import java.io.*;
import java.nio.file.*;
import java.util.zip.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import local.plasmomusic.compat.VoiceCompatibility;

/** Check real versions and simulate incompatible upstream changes before transformation. */
public final class CompatibilityTest {
    public static void main(String[] args)throws Exception {
        int tested=0;
        for(String jar:args){
            if(!Files.isRegularFile(Path.of(jar)))continue;
            try(ZipFile archive=new ZipFile(jar)){
                VoiceCompatibility.Classes original=name->read(archive,name);
                String error=VoiceCompatibility.check(original);
                require(error.isEmpty(),jar+" contracts: "+error);tested++;
                System.out.println("PASS compatibility contracts accept "+jar);
                String broken=VoiceCompatibility.check(name->{
                    ClassNode node=read(archive,name);
                    if(name.equals("su/plo/voice/client/audio/capture/VoiceAudioCapture"))
                        node.methods.removeIf(m->m.name.equals("processActivation"));
                    return node;
                });
                require(broken.contains("processActivation"),"missing capture hook must disable integration");
                System.out.println("PASS removed capture hook is rejected before injection");
                broken=VoiceCompatibility.check(name->{
                    ClassNode node=read(archive,name);
                    if(name.equals("su/plo/voice/api/client/audio/capture/AudioCapture"))
                        node.methods.removeIf(m->m.name.equals("isServerMuted"));
                    return node;
                });
                require(broken.contains("isServerMuted"),"changed public API must disable integration");
                System.out.println("PASS changed public API is rejected before initialization");
                broken=VoiceCompatibility.check(name->{
                    ClassNode node=read(archive,name);
                    if(name.equals("su/plo/voice/client/gui/settings/MicrophoneTestController"))
                        for(MethodNode m:node.methods)if(m.name.equals("onAudioCaptureProcessed"))
                            for(AbstractInsnNode i:m.instructions)if(i instanceof MethodInsnNode call&&call.name.equals("getSamples"))call.name="changedSamples";
                    return node;
                });
                require(broken.contains("getSamples"),"changed redirect site must disable integration");
                System.out.println("PASS changed preview injection site is rejected before injection");
            }
        }
        require(tested>0,"no test jar supplied");
    }
    private static ClassNode read(ZipFile archive,String name)throws IOException {
        ZipEntry entry=archive.getEntry(name+".class");
        InputStream stream=entry==null?CompatibilityTest.class.getClassLoader().getResourceAsStream(name+".class"):archive.getInputStream(entry);
        if(stream==null)throw new IOException("Missing "+name);
        try(InputStream input=stream){ClassNode node=new ClassNode();new ClassReader(input).accept(node,0);return node;}
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
