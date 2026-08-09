package com.aresstack.webserver.application;

public class StopWebServer {

    private final CaddyRuntime runtime;

    public StopWebServer(CaddyRuntime runtime) {
        this.runtime = runtime;
    }

    public void stop() {
        runtime.stop();
    }
}
