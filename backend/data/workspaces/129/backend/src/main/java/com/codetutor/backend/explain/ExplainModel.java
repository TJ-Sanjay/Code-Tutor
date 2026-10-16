package com.codetutor.backend.explain;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** Response objects for the "click a file / class / method to see what it does" popups. */
public final class ExplainModel {

    private ExplainModel() {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Link(String label, String kind, String id, String file, int line, String group) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Explanation(String kind, String id, String title, String subtitle, String summary,
                              List<String> facts, List<Link> links, String file, int line, int endLine,
                              List<String> tags, String layer) {}
}
