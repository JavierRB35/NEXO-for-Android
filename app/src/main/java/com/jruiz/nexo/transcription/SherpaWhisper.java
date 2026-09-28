package com.jruiz.nexo.transcription;

import android.content.Context;
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig;
import com.k2fsa.sherpa.onnx.OfflineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineStream;
import com.k2fsa.sherpa.onnx.OfflineRecognizerResult;
import java.io.File;

/** Wrapper mínimo de sherpa-onnx para Whisper base multilingüe INT8. */
public final class SherpaWhisper implements AutoCloseable {
    private final OfflineRecognizer recognizer;
    public SherpaWhisper(Context c){
        File enc=TranscriptionModelManager.find(c,TranscriptionModelManager.ENCODER),dec=TranscriptionModelManager.find(c,TranscriptionModelManager.DECODER),tok=TranscriptionModelManager.find(c,TranscriptionModelManager.TOKENS);
        OfflineWhisperModelConfig whisper=new OfflineWhisperModelConfig();whisper.setEncoder(enc.getAbsolutePath());whisper.setDecoder(dec.getAbsolutePath());whisper.setLanguage("es");whisper.setTask("transcribe");
        OfflineModelConfig model=new OfflineModelConfig();model.setWhisper(whisper);model.setTokens(tok.getAbsolutePath());model.setModelType("whisper");model.setProvider("cpu");model.setNumThreads(2);model.setDebug(false);
        OfflineRecognizerConfig cfg=new OfflineRecognizerConfig();cfg.setModelConfig(model);cfg.setDecodingMethod("greedy_search");
        recognizer=new OfflineRecognizer(null,cfg);
    }
    public String transcribe(float[] samples){OfflineStream stream=recognizer.createStream();try{stream.acceptWaveform(samples,16000);recognizer.decode(stream);OfflineRecognizerResult r=recognizer.getResult(stream);String t=r==null?"":r.getText();return t==null?"":t.trim();}finally{try{stream.release();}catch(Exception ignored){}}}
    @Override public void close(){try{recognizer.release();}catch(Exception ignored){}}
}
