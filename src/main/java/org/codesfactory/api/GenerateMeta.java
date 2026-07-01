package org.codesfactory.api;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

public class GenerateMeta {
    @SerializedName("model_type")
    private String modelType;
    
    @SerializedName("program")
    private List<Object> program; // Supports instrument names e.g., "PIANO" or MIDI programs e.g., 0
    
    @SerializedName("tempo")
    private int tempo;
    
    @SerializedName("task")
    private String task;
    
    @SerializedName("p")
    private Double p; // Use Double wrapper to allow null to be excluded from serialization
    
    @SerializedName("temperature")
    private Double temperature;

    @SerializedName("key")
    private String key;

    @SerializedName("genre")
    private List<String> genre;

    @SerializedName("gen_note_dense")
    private Map<String, Integer> genNoteDense;

    @SerializedName("thinking")
    private Boolean thinking;

    @SerializedName("genfield_measure")
    private Integer genfieldMeasure;

    public GenerateMeta(String modelType, List<Object> program, int tempo, String task) {
        this.modelType = modelType;
        this.program = program;
        this.tempo = tempo;
        this.task = task;
    }

    public void setP(Double p) {
        this.p = p;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public void setGenre(List<String> genre) {
        this.genre = genre;
    }

    public void setGenNoteDense(Map<String, Integer> genNoteDense) {
        this.genNoteDense = genNoteDense;
    }

    public void setThinking(Boolean thinking) {
        this.thinking = thinking;
    }

    public void setGenfieldMeasure(Integer genfieldMeasure) {
        this.genfieldMeasure = genfieldMeasure;
    }
}
