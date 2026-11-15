package com.droprig.core;

/**
 * The two Drop Rig keybinds. Key codes are GLFW key codes; {@link #UNBOUND} means no key.
 * Pure Java (GLFW constants are copied as literals) so the rules are unit-tested.
 */
public final class KeyBindings {
    public static final int UNBOUND = -1;
    /** GLFW_KEY_J */
    public static final int DEFAULT_TOGGLE = 74;
    /** GLFW_KEY_RIGHT_SHIFT */
    public static final int DEFAULT_MENU = 344;
    /** GLFW_KEY_ESCAPE: reserved for closing screens. */
    public static final int ESCAPE = 256;

    public enum Action {
        TOGGLE_RIG("Toggle Rig", DEFAULT_TOGGLE),
        OPEN_MENU("Open Menu", DEFAULT_MENU);

        public final String label;
        public final int defaultKey;

        Action(String label, int defaultKey) {
            this.label = label;
            this.defaultKey = defaultKey;
        }

        public Action other() {
            return this == TOGGLE_RIG ? OPEN_MENU : TOGGLE_RIG;
        }
    }

    public enum Result {
        ASSIGNED, UNCHANGED, CONFLICT, RESERVED
    }

    private int toggleKey;
    private int menuKey;

    public KeyBindings(int toggleKey, int menuKey) {
        this.toggleKey = sanitize(toggleKey);
        this.menuKey = sanitize(menuKey);
        if (this.toggleKey != UNBOUND && this.toggleKey == this.menuKey) {
            this.menuKey = this.toggleKey == DEFAULT_MENU ? UNBOUND : DEFAULT_MENU;
        }
    }

    private static int sanitize(int key) {
        return key == ESCAPE || key < 0 || key > 348 ? UNBOUND : key;
    }

    public int get(Action action) {
        return action == Action.TOGGLE_RIG ? toggleKey : menuKey;
    }

    public boolean isDefault(Action action) {
        return get(action) == action.defaultKey;
    }

    public Result assign(Action action, int key) {
        if (key == ESCAPE) return Result.RESERVED;
        if (key != UNBOUND && (key < 0 || key > 348)) return Result.RESERVED;
        if (get(action) == key) return Result.UNCHANGED;
        if (key != UNBOUND && get(action.other()) == key) return Result.CONFLICT;
        set(action, key);
        return Result.ASSIGNED;
    }

    /** Restores the default key. If the default is taken by the other action, that one is unbound. */
    public Result reset(Action action) {
        if (get(action) == action.defaultKey) return Result.UNCHANGED;
        if (get(action.other()) == action.defaultKey) set(action.other(), UNBOUND);
        set(action, action.defaultKey);
        return Result.ASSIGNED;
    }

    /** Which action, if any, a key press should trigger. */
    public Action actionFor(int key) {
        if (key == UNBOUND) return null;
        if (key == toggleKey) return Action.TOGGLE_RIG;
        if (key == menuKey) return Action.OPEN_MENU;
        return null;
    }

    private void set(Action action, int key) {
        if (action == Action.TOGGLE_RIG) toggleKey = key;
        else menuKey = key;
    }
}
