package local.plasmomusic;
final class ChannelEffects {
    private final CharacterEffects character=new CharacterEffects();
    private final SingingReverb reverb=new SingingReverb();
    synchronized short[] process(short[] input,int rate,boolean stereo,StudioSettings.Style style,int strength,StudioSettings.Room room,int wet){
        return reverb.process(character.process(input,rate,stereo,style,strength),rate,stereo,room,wet);
    }
    synchronized short[] processVoice(short[] input,int rate,boolean stereo,StudioSettings s){
        return reverb.process(character.processVoice(input,rate,stereo,s),rate,stereo,s.voiceRoom(),s.voiceReverb());
    }
    synchronized boolean hasTail(){return character.hasTail()||reverb.hasTail();}
}
