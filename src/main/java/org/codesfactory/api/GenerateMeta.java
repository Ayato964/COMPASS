package org.codesfactory.api;

import java.util.List;

public class GenerateMeta {
    private String modelType;
    private List<Integer> list;
    private int tempo;
    private String name;
    private double p;
    private double temperature;

    public GenerateMeta(String modelType, List<Integer> list, int tempo, String name) {
        this.modelType = modelType;
        this.list = list;
        this.tempo = tempo;
        this.name = name;
    }

    public void setP(double p) {
        this.p = p;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }
}
