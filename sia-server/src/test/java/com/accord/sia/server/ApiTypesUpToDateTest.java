package com.accord.sia.server;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Garante que {@code types/api.d.ts} corresponde ao {@code openapi.yaml}.
 *
 * <p>Se este teste falhar, o contrato mudou: rode com {@code -Dsia.types.write=true} para regerar o
 * arquivo (ou apague {@code types/api.d.ts}).
 */
class ApiTypesUpToDateTest {

  private static final Path OPENAPI = Path.of("src", "main", "resources", "openapi.yaml");
  private static final Path TYPES = Path.of("..", "types", "api.d.ts");

  @Test
  void tiposDoFrontendRefletemOContrato() throws Exception {
    String expected = OpenApiTypesGenerator.generate(OPENAPI);

    if (!Files.exists(TYPES) || Boolean.getBoolean("sia.types.write")) {
      Files.createDirectories(TYPES.toAbsolutePath().getParent());
      Files.writeString(TYPES, expected, StandardCharsets.UTF_8);
    }

    assertThat(Files.readString(TYPES, StandardCharsets.UTF_8))
        .as("types/api.d.ts desatualizado: regere com -Dsia.types.write=true")
        .isEqualTo(expected);
  }
}
