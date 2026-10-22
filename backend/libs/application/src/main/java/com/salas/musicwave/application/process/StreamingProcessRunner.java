package com.salas.musicwave.application.process;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public interface StreamingProcessRunner {

    StreamingProcessResult run(StreamingProcessRequest request,
                               Consumer<String> outputLineConsumer,
                               BooleanSupplier cancellationRequested);
}
