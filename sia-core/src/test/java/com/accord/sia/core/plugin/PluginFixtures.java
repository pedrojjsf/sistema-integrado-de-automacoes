package com.accord.sia.core.plugin;

import com.accord.sia.api.AutomationPlugin;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/**
 * Compila e empacota os plugins de teste em tempo de execucao.
 *
 * <p>Compilar aqui (em vez de deixar as classes no classpath de teste) e proposital: se as classes
 * estivessem no classpath do teste, o pai do classloader do plugin as encontraria e o teste de
 * isolamento passaria sem provar nada.
 */
public final class PluginFixtures {

  private static final Path SOURCE_ROOT = Path.of("src", "test", "resources", "plugin-fixtures");

  private PluginFixtures() {}

  /** Compila a fixture e devolve o diretorio de plugin pronto (plugin.yml + plugin.jar). */
  public static Path packagePlugin(Path workDirectory, String fixture, Path pluginsDirectory)
      throws IOException {
    Path fixtureRoot = SOURCE_ROOT.resolve(fixture);
    Path classes = Files.createDirectories(workDirectory.resolve("classes").resolve(fixture));
    Path target = Files.createDirectories(pluginsDirectory.resolve(fixture));

    compile(fixtureRoot, classes);
    Path manifest = fixtureRoot.resolve("plugin.yml");
    jar(classes, target.resolve("plugin.jar"), manifest, fixtureRoot);
    Files.copy(manifest, target.resolve("plugin.yml"));
    return target;
  }

  private static void compile(Path fixtureRoot, Path classes) throws IOException {
    List<String> sources = new ArrayList<>();
    try (Stream<Path> files = Files.walk(fixtureRoot)) {
      files
          .filter(path -> path.toString().endsWith(".java"))
          .forEach(path -> sources.add(path.toString()));
    }
    if (sources.isEmpty()) {
      throw new IllegalStateException("fixture sem fontes: " + fixtureRoot);
    }
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      throw new IllegalStateException("sem compilador: rode os testes em um JDK");
    }
    List<String> arguments =
        new ArrayList<>(
            List.of("-d", classes.toString(), "-proc:none", "-nowarn", "-cp", apiClasspath()));
    arguments.addAll(sources);
    int exit = compiler.run(null, null, null, arguments.toArray(String[]::new));
    if (exit != 0) {
      throw new IllegalStateException("falha compilando fixture " + fixtureRoot);
    }
  }

  /** Classpath enxuto: apenas o modulo sia-api, como um plugin de verdade enxerga. */
  private static String apiClasspath() {
    try {
      return Path.of(
              AutomationPlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI())
          .toString();
    } catch (URISyntaxException error) {
      throw new IllegalStateException("nao foi possivel localizar o sia-api", error);
    }
  }

  private static void jar(Path classes, Path jarFile, Path manifest, Path fixtureRoot)
      throws IOException {
    try (OutputStream out = Files.newOutputStream(jarFile);
        JarOutputStream jar = new JarOutputStream(out);
        Stream<Path> files = Files.walk(classes)) {
      jar.putNextEntry(new JarEntry("plugin.yml"));
      jar.write(Files.readAllBytes(manifest));
      jar.closeEntry();
      for (Path file : files.filter(Files::isRegularFile).toList()) {
        String entry = classes.relativize(file).toString().replace('\\', '/');
        jar.putNextEntry(new JarEntry(entry));
        jar.write(Files.readAllBytes(file));
        jar.closeEntry();
      }
      // recursos da fixture (ex.: ui/panel.html) entram no jar como o plugin real embarca
      try (Stream<Path> resources = Files.walk(fixtureRoot)) {
        for (Path file :
            resources
                .filter(Files::isRegularFile)
                .filter(path -> !path.toString().endsWith(".java"))
                .filter(path -> !path.getFileName().toString().equals("plugin.yml"))
                .toList()) {
          String entry = fixtureRoot.relativize(file).toString().replace('\\', '/');
          jar.putNextEntry(new JarEntry(entry));
          jar.write(Files.readAllBytes(file));
          jar.closeEntry();
        }
      }
    } catch (UncheckedIOException error) {
      throw error.getCause();
    }
  }
}
