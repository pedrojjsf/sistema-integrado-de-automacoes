package com.accord.sia.core.manifest;

import com.accord.sia.api.PluginPermission;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Conteudo do {@code plugin.yml}.
 *
 * <p>Dois modos convivem: {@link PluginKind#JAVA} (jar que depende de {@code sia-api} e roda no
 * processo do daemon) e {@link PluginKind#PROCESS} (executavel proprio, falado por JSON Lines).
 *
 * @param id identificador unico
 * @param name nome legivel
 * @param version versao SemVer
 * @param apiVersion versao da API de plugin exigida
 * @param author mantenedor
 * @param description resumo
 * @param kind modo de execucao
 * @param main classe principal, apenas no modo java
 * @param command comando de entrada (lista), apenas no modo process
 * @param protocol protocolo de dialogo, apenas no modo process
 * @param permissions recursos pedidos ao Core
 * @param dependencies ids de outros plugins exigidos
 * @param requiresOs sistemas operacionais aceitos (vazio = qualquer)
 */
public record PluginManifest(
    String id,
    String name,
    String version,
    @JsonProperty("api-version") String apiVersion,
    String author,
    String description,
    PluginKind kind,
    String main,
    List<String> command,
    String protocol,
    List<String> permissions,
    List<String> dependencies,
    @JsonProperty("requires-os") List<String> requiresOs) {

  public static final String DEFAULT_PROCESS_PROTOCOL = "stdio-jsonl-v1";

  public PluginManifest {
    command = command == null ? List.of() : List.copyOf(command);
    permissions = permissions == null ? List.of() : List.copyOf(permissions);
    dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    requiresOs = requiresOs == null ? List.of() : List.copyOf(requiresOs);
    if (kind == PluginKind.PROCESS && protocol == null) {
      protocol = DEFAULT_PROCESS_PROTOCOL;
    }
  }

  public Set<PluginPermission> grantedPermissions() {
    EnumSet<PluginPermission> granted = EnumSet.noneOf(PluginPermission.class);
    for (String permission : permissions) {
      granted.add(PluginPermission.fromWireName(permission));
    }
    return granted;
  }
}
