package com.jruiz.nexo.transcription;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/** Decodifica M4A/AAC con MediaCodec y entrega audio mono a 16 kHz en bloques acotados. */
public final class AudioChunkDecoder {
    private AudioChunkDecoder(){}
    public interface ChunkConsumer { void accept(float[] samples,long startMs) throws Exception; }

    public static void decode(File file,int targetRate,int chunkSeconds,ChunkConsumer consumer)throws Exception{
        MediaExtractor ex=new MediaExtractor();MediaCodec codec=null;
        try{
            ex.setDataSource(file.getAbsolutePath());int track=-1;MediaFormat fmt=null;
            for(int i=0;i<ex.getTrackCount();i++){MediaFormat f=ex.getTrackFormat(i);String mime=f.getString(MediaFormat.KEY_MIME);if(mime!=null&&mime.startsWith("audio/")){track=i;fmt=f;break;}}
            if(track<0||fmt==null)throw new IllegalArgumentException("El archivo no contiene una pista de audio compatible");
            ex.selectTrack(track);String mime=fmt.getString(MediaFormat.KEY_MIME);codec=MediaCodec.createDecoderByType(mime);codec.configure(fmt,null,null,0);codec.start();
            int sampleRate=fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)?fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE):44100;
            int channels=fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)?fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT):1;
            int pcmEncoding=AudioFormat.ENCODING_PCM_16BIT;
            int chunkSize=targetRate*chunkSeconds;float[] chunk=new float[chunkSize];int chunkPos=0;long produced=0;
            boolean inDone=false,outDone=false;MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
            while(!outDone){
                if(!inDone){int idx=codec.dequeueInputBuffer(10000);if(idx>=0){ByteBuffer in=codec.getInputBuffer(idx);if(in!=null){int n=ex.readSampleData(in,0);if(n<0){codec.queueInputBuffer(idx,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inDone=true;}else{long pts=ex.getSampleTime();codec.queueInputBuffer(idx,0,n,pts,0);ex.advance();}}}}
                int oi=codec.dequeueOutputBuffer(info,10000);
                if(oi==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){MediaFormat of=codec.getOutputFormat();if(of.containsKey(MediaFormat.KEY_SAMPLE_RATE))sampleRate=of.getInteger(MediaFormat.KEY_SAMPLE_RATE);if(of.containsKey(MediaFormat.KEY_CHANNEL_COUNT))channels=of.getInteger(MediaFormat.KEY_CHANNEL_COUNT);if(of.containsKey(MediaFormat.KEY_PCM_ENCODING))pcmEncoding=of.getInteger(MediaFormat.KEY_PCM_ENCODING);continue;}
                if(oi>=0){ByteBuffer out=codec.getOutputBuffer(oi);if(out!=null&&info.size>0){out.position(info.offset);out.limit(info.offset+info.size);float[] mono=toMono(out,channels,pcmEncoding);float[] rs=resample(mono,sampleRate,targetRate);int off=0;while(off<rs.length){int take=Math.min(chunk.length-chunkPos,rs.length-off);System.arraycopy(rs,off,chunk,chunkPos,take);chunkPos+=take;off+=take;if(chunkPos==chunk.length){consumer.accept(Arrays.copyOf(chunk,chunk.length),produced*1000L/targetRate);produced+=chunkPos;chunkPos=0;}}}
                    outDone=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;codec.releaseOutputBuffer(oi,false);
                }
            }
            if(chunkPos>0){consumer.accept(Arrays.copyOf(chunk,chunkPos),produced*1000L/targetRate);}
        }finally{try{if(codec!=null){codec.stop();codec.release();}}catch(Exception ignored){}try{ex.release();}catch(Exception ignored){}}
    }
    private static float[] toMono(ByteBuffer b,int channels,int encoding){
        b=b.slice().order(ByteOrder.LITTLE_ENDIAN);channels=Math.max(1,channels);
        if(encoding==AudioFormat.ENCODING_PCM_FLOAT){int frames=b.remaining()/(4*channels);float[] out=new float[frames];for(int i=0;i<frames;i++){float sum=0;for(int c=0;c<channels;c++)sum+=b.getFloat();out[i]=Math.max(-1f,Math.min(1f,sum/channels));}return out;}
        int frames=b.remaining()/(2*channels);float[] out=new float[frames];for(int i=0;i<frames;i++){float sum=0;for(int c=0;c<channels;c++)sum+=b.getShort()/32768f;out[i]=sum/channels;}return out;
    }
    private static float[] resample(float[] in,int inRate,int outRate){if(in.length==0)return in;if(inRate<=0||inRate==outRate)return in;int n=Math.max(1,(int)Math.round(in.length*(double)outRate/inRate));float[] out=new float[n];double step=(double)inRate/outRate;for(int i=0;i<n;i++){double p=i*step;int a=Math.min(in.length-1,(int)p),c=Math.min(in.length-1,a+1);double f=p-a;out[i]=(float)(in[a]*(1-f)+in[c]*f);}return out;}
}
