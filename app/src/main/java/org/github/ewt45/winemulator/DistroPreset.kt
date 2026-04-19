package org.github.ewt45.winemulator

enum class DistroPreset(
    val displayName: String,
    val description: String,
    val startupCommand: String
) {
    LINBOX(
        displayName = "Linbox (Default)",
        description = "Wine + Box64 environment for running Windows apps and games.",
        startupCommand = "linbox"
    ),
    ROCKNIX(
        displayName = "ROCKNIX",
        description = "Retro gaming OS with EmulationStation frontend.\n\nSelect your rocknix-rootfs.tar.gz when prompted.\n\nRequires SDL_VIDEODRIVER=x11 — handled automatically.",
        startupCommand = "rocknix-proot"
    )
}
