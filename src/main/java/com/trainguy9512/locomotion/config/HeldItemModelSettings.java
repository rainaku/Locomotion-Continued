package com.trainguy9512.locomotion.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class HeldItemModelSettings {
    private static final Pattern ITEM_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

    public Mode mode = Mode.GLOBAL;
    public ItemModelTransform global = new ItemModelTransform();
    public Map<String, ItemModelTransform> specific = new LinkedHashMap<>();

    public enum Mode { GLOBAL, SPECIFIC }

    public ItemModelTransform resolve(String itemId) {
        return mode == Mode.SPECIFIC ? specific.getOrDefault(itemId, global) : global;
    }

    public HeldItemModelSettings copy() {
        var copy = new HeldItemModelSettings();
        copy.mode = mode;
        copy.global = global.copy();
        specific.forEach((id, transform) -> copy.specific.put(id, transform.copy()));
        return copy;
    }

    public void sanitize() {
        if (mode == null) mode = Mode.GLOBAL;
        if (global == null) global = new ItemModelTransform();
        global.sanitize();
        if (specific == null) specific = new LinkedHashMap<>();
        specific.entrySet().removeIf(entry -> entry.getKey() == null || !ITEM_ID.matcher(entry.getKey()).matches() || entry.getValue() == null);
        specific.values().forEach(ItemModelTransform::sanitize);
    }
}
