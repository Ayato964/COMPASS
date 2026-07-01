package org.codesfactory.api;

import com.google.gson.annotations.SerializedName;
import java.util.List;

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
    private double p = 0.95;
    
    @SerializedName("temperature")
    private double temperature = 1.0;

    public GenerateMeta(String modelType, List<Object> program, int tempo, String task) {
        this.modelType = modelType;
        this.program = program;
        this.tempo = tempo;
        this.task = task;
    }

    public void setP(double p) {
        this.p = p;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }
}
