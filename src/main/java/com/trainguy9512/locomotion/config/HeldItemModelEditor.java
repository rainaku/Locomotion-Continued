package com.trainguy9512.locomotion.config;

import java.util.function.Consumer;

/** A detached draft shared by the sliders and the actual first-person renderer. */
public final class HeldItemModelEditor {
    private final HeldItemModelSettings draft;
    private String selectedItem;

    public HeldItemModelEditor(HeldItemModelSettings saved) {
        this.draft = saved.copy();
    }

    public HeldItemModelSettings preview() { return draft; }

    public void selectItem(String itemId) { selectedItem = itemId; }

    public void setMode(HeldItemModelSettings.Mode mode) { draft.mode = mode; }

    public boolean canEdit() { return draft.mode == HeldItemModelSettings.Mode.GLOBAL || selectedItem != null; }

    public ItemModelTransform current() { return draft.resolve(selectedItem); }

    public void edit(Consumer<ItemModelTransform> change) {
        if (!canEdit()) return;
        ItemModelTransform target = draft.mode == HeldItemModelSettings.Mode.SPECIFIC
                ? draft.specific.computeIfAbsent(selectedItem, id -> draft.global.copy()) : draft.global;
        change.accept(target);
        target.sanitize();
    }

    public void reset() {
        if (draft.mode == HeldItemModelSettings.Mode.GLOBAL) draft.global = new ItemModelTransform();
        else if (selectedItem != null) draft.specific.remove(selectedItem);
    }

    public HeldItemModelSettings snapshot() {
        var saved = draft.copy();
        saved.sanitize();
        return saved;
    }
}
