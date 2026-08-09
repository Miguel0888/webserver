package com.aresstack.webserver.application;

public class GetWebServerStatus {

    public record Status(boolean processRunning, boolean adminReachable) {
    }

    private final CaddyRuntime runtime;
    private final CaddyAdmin admin;

    public GetWebServerStatus(CaddyRuntime runtime, CaddyAdmin admin) {
        this.runtime = runtime;
        this.admin = admin;
    }

    public Status status() {
        return new Status(runtime.isRunning(), admin.isReachable());
    }
}
