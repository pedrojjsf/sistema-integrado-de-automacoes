package com.accord.sia.core.plugin;

import java.nio.file.Path;

/**
 * Diretorio de um plugin descoberto.
 *
 * @param directory pasta do plugin
 * @param manifestFile {@code plugin.yml}
 * @param artifact JAR do plugin (unico do diretorio)
 */
public record PluginCandidate(Path directory, Path manifestFile, Path artifact) {}
