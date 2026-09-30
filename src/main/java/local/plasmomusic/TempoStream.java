package local.plasmomusic;
import be.tarsos.dsp.*;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;

/** WSOLA with fixed-rate playback and a bounded queue for live speech. */
final class TempoStream {
    private final WaveformSimilarityBasedOverlapAdd stretch;
    private final AudioEvent event;private final float[] input,output;
    private final int step;private int filled,head,queued,fade;
    private float last;
    TempoStream(int rate,int speed){
        stretch=new WaveformSimilarityBasedOverlapAdd(new WaveformSimilarityBasedOverlapAdd.Parameters(speed/100.0,rate,40,8,10));
        input=new float[stretch.getInputBufferSize()];step=input.length-stretch.getOverlap();output=new float[rate/2];
        event=new AudioEvent(new TarsosDSPAudioFormat(rate,16,1,true,false));
    }
    float sample(float value){
        input[filled++]=value;
        if(filled==input.length){
            event.setFloatBuffer(input.clone());event.setOverlap(0);stretch.process(event);
            float[] result=event.getFloatBuffer();
            int discard=Math.max(0,queued+result.length-output.length);
            if(discard>0){head=(head+discard)%output.length;queued-=discard;fade=240;}
            for(float x:result){output[(head+queued)%output.length]=x;queued++;}
            System.arraycopy(input,step,input,0,input.length-step);filled=input.length-step;
        }
        float x=0;
        if(queued>0){x=output[head];head=(head+1)%output.length;queued--;}
        if(fade>0){x=last+(x-last)/fade;fade--;}
        last=x;return x;
    }
    int queuedForTest(){return queued;}
}
