package org.codesfactory.api;

public class ModelInfo {
    private String modelName;
    private String displayName;

    public ModelInfo() {
        this("MELODY_GEM", "Melody Gem");
    }

    public ModelInfo(String modelName, String displayName) {
        this.modelName = modelName;
        this.displayName = displayName;
    }

    public String getModelName() {
        return modelName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
