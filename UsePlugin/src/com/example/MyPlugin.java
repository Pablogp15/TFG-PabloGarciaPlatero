package com.example;

import org.tzi.use.runtime.impl.Plugin;

public class MyPlugin extends Plugin {

    protected final String PLUGIN_ID = "useAlgorithmExecutor";

    public String getName() {
        return PLUGIN_ID;
    }

    public void run(org.tzi.use.runtime.IPluginRuntime iPluginRuntime) {
        // Nothing to initialize
    }

    public static void main(String[] args) {
        // Empty main method to suppress IDE error messages in manifest file
    }

}

