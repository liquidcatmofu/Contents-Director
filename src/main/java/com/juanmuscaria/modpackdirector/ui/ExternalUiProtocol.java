package com.juanmuscaria.modpackdirector.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ExternalUiProtocol {
    private ExternalUiProtocol() {
    }

    public static final class Request {
        public String type;
        public String packName;
        public String title;
        public String message;
        public String acceptLabel;
        public String cancelLabel;
        public String buttonLabel;
        public List<Option> options = new ArrayList<>();
        public List<Group> groups = new ArrayList<>();
        public List<ModEntry> mods = new ArrayList<>();
        public List<ErrorEntry> errors = new ArrayList<>();
    }

    public static final class Response {
        public boolean accepted = true;
        public boolean cancelled;
        public Map<String, Boolean> selections = new HashMap<>();
    }

    public static final class Option {
        public String id;
        public String name;
        public String description;
        public boolean selected;
    }

    public static final class Group {
        public String name;
        public List<Option> options = new ArrayList<>();
    }

    public static final class ModEntry {
        public String name;
        public String url;
        public String target;
        public String source;
    }

    public static final class ErrorEntry {
        public String level;
        public String message;
        public String cause;
    }
}
