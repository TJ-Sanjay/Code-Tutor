package com.codetutor.backend.analyzer.dto;

public class ApiEndpoint {
    private String method; // GET, POST, PUT, DELETE, PATCH
    private String path;   // e.g. "/api/users"
    private String handlerClass;
    private String handlerMethod;

    public ApiEndpoint() {}

    public ApiEndpoint(String method, String path, String handlerClass, String handlerMethod) {
        this.method = method;
        this.path = path;
        this.handlerClass = handlerClass;
        this.handlerMethod = handlerMethod;
    }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getHandlerClass() { return handlerClass; }
    public void setHandlerClass(String handlerClass) { this.handlerClass = handlerClass; }

    public String getHandlerMethod() { return handlerMethod; }
    public void setHandlerMethod(String handlerMethod) { this.handlerMethod = handlerMethod; }
}
