package com.tweakdapp.backend.storage.internal.enums;

public enum ModificationPhase {
    BEFORE("before"),
    AFTER("after");

    private final String folder;

    ModificationPhase(String folder) { this.folder = folder; }
    public String folder() { return folder; }
}
