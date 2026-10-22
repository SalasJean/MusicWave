package com.salas.musicwave.application.tools;

import java.util.List;

@FunctionalInterface
public interface ToolInspector {

    List<ToolInspection> inspect();
}
