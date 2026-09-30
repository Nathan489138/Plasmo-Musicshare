package local.plasmomusic;

/** Per-channel damped comb/all-pass network. Dry voice stays direct, wet signal has pre-delay. */
final class SingingReverb {
    private static final class Delay {
        final double[] data;int index;double damp;
        Delay(int size){data=new double[Math.max(1,size)];}
        double comb(double x,double feedback){double y=data[index];damp=.35*y+.65*damp;data[index]=x+damp*feedback;index=(index+1)%data.length;return y;}
        double allpass(double x){double y=data[index]-x;data[index]=x+data[index]*.5;index=(index+1)%data.length;return y;}
        double delay(double x){double y=data[index];data[index]=x;index=(index+1)%data.length;return y;}
    }
    private StudioSettings.Room room=StudioSettings.Room.OFF;private int rate,channels;
    private Delay[][] comb,allpass;private Delay[] pre;private double[][] feedback;private boolean tail;
    boolean hasTail(){return tail;}
    short[] process(short[] input,int sampleRate,boolean stereo,StudioSettings.Room next,int amount){
        if(next==StudioSettings.Room.OFF||amount==0){room=StudioSettings.Room.OFF;tail=false;return input;}
        int count=stereo?2:1;
        if(next!=room||sampleRate!=rate||channels!=count){
            room=next;rate=sampleRate;channels=count;tail=false;
            comb=new Delay[count][4];allpass=new Delay[count][2];pre=new Delay[count];feedback=new double[count][4];
            double[] times={.0297,.0371,.0411,.0437};
            for(int c=0;c<count;c++){
                pre[c]=new Delay((int)(rate*room.preDelay/1000));
                for(int j=0;j<4;j++){double seconds=times[j]+c*.0017;comb[c][j]=new Delay((int)(rate*seconds));feedback[c][j]=Math.pow(10,-3*seconds/room.decay);}
                allpass[c][0]=new Delay((int)(rate*(.005+c*.0003)));allpass[c][1]=new Delay((int)(rate*(.0017+c*.0002)));
            }
        }
        short[] out=new short[input.length];double wet=amount/100.0,max=0;
        for(int i=0;i<input.length/count;i++)for(int c=0;c<count;c++){
            double dry=input[i*count+c]/32768.0,x=pre[c].delay(dry),sum=0;
            for(int j=0;j<4;j++)sum+=comb[c][j].comb(x,feedback[c][j])*.25;
            for(int j=0;j<2;j++)sum=allpass[c][j].allpass(sum);
            out[i*count+c]=AudioBuffer.pcm((float)(dry+sum*wet));max=Math.max(max,Math.abs(dry));
        }
        // Include internal delay energy, including the silent pre-delay gap.
        for(int c=0;c<count;c++){
            for(double value:pre[c].data)max=Math.max(max,Math.abs(value));
            for(Delay d:comb[c])for(double value:d.data)max=Math.max(max,Math.abs(value));
            for(Delay d:allpass[c])for(double value:d.data)max=Math.max(max,Math.abs(value));
        }
        tail=max>0.00008;return out;
    }
}
