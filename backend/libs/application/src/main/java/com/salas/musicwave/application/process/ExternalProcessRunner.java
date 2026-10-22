package com.salas.musicwave.application.process;

@FunctionalInterface
public interface ExternalProcessRunner {

    ProcessResult run(ProcessRequest request);
}
