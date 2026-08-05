package com.sheepfromheaven.minenow.dev;

/**
 * Shared flag between the client-side dev-world bootstrap and the server-side starter-kit grant:
 * the client decides whether the playtest world is being created fresh (as opposed to just
 * reopened), and the server reads that decision when the local player logs in. Both run in the
 * same JVM for the integrated singleplayer server this feature targets, so a plain static field
 * is enough - no networking required.
 */
public final class DevWorldState {
    public static volatile boolean pendingStarterKit = false;

    private DevWorldState() {}
}
