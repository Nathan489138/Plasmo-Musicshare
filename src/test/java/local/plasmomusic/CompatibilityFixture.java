package local.plasmomusic;

import java.util.zip.*;
import java.nio.file.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Work-area fixture only: rename a private PV method and its own callers. */
public final class CompatibilityFixture {
    public static void main(String[] args)throws Exception {
        String owner="su/plo/voice/client/audio/capture/VoiceAudioCapture";
        try(ZipInputStream input=new ZipInputStream(Files.newInputStream(Path.of(args[0])));
            ZipOutputStream output=new ZipOutputStream(Files.newOutputStream(Path.of(args[1])))){
            ZipEntry entry;
            while((entry=input.getNextEntry())!=null){
                byte[] bytes=input.readAllBytes();
                if(entry.getName().equals(owner+".class")){
                    ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
                    for(MethodNode m:node.methods){
                        if(m.name.equals("processActivation"))m.name="changedActivation";
                        for(AbstractInsnNode i:m.instructions)if(i instanceof MethodInsnNode call&&call.owner.equals(owner)&&call.name.equals("processActivation"))call.name="changedActivation";
                    }
                    ClassWriter writer=new ClassWriter(0);node.accept(writer);bytes=writer.toByteArray();
                }
                output.putNextEntry(new ZipEntry(entry.getName()));output.write(bytes);output.closeEntry();input.closeEntry();
            }
        }
        System.out.println("Created incompatible-hook test fixture in work area");
    }
}
