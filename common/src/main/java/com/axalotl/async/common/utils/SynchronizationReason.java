package com.axalotl.async.common.utils;

/**
 * Describes a condition that keeps an entity tick on the server thread.
 */
public enum SynchronizationReason {
    PROCESSOR_SHUTTING_DOWN("Async is shutting down"),
    CLIENT_SIDE_LEVEL("client-side level"),
    ACTIVE_PORTAL_PROCESS("active portal process"),
    ASYNC_DISABLED("Async is disabled"),
    MISSING_ASYNC_COMPATIBILITY("non-Minecraft class is not @AsyncCompatible"),
    PROJECTILE("projectile"),
    PLAYER("player"),
    BLOCKED_ENTITY_CLASS("hardcoded blocked entity class"),
    RUNTIME_BLACKLIST("runtime entity blacklist"),
    SABLE_COMPATIBILITY("Sable compatibility"),
    SYNCHRONIZED_ENTITY_CONFIG("synchronizedEntities configuration");

    private final String description;

    SynchronizationReason(String description) {
        this.description = description;
    }

    /**
     * Returns the command-friendly description of this reason.
     */
    public String description() {
        return description;
    }

}
