package lwkfurina.sssavepoint.client;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lwkfurina.sssavepoint.SSSavePoint;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

public final class SaveFolder {
	private static final String KEY = "folder";
	private static final String PICKER_CLASS = "lwkfurina.sssavepoint.client.FolderPicker";
	private static Path chosen = null;
	private static boolean loaded = false;

	private SaveFolder() {
	}

	private static Path configFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("ss-save-point.properties");
	}

	private static void load() {
		if (loaded) {
			return;
		}
		loaded = true;

		Path file = configFile();
		if (!Files.isRegularFile(file)) {
			return;
		}

		Properties props = new Properties();
		try (InputStream in = Files.newInputStream(file)) {
			props.load(in);
			String value = props.getProperty(KEY);
			if (value != null && !value.isBlank()) {
				chosen = Path.of(value);
			}
		} catch (IOException | RuntimeException e) {
			SSSavePoint.LOGGER.warn("SS-Save-Point could not read its settings file", e);
		}
	}

	private static void save(Path folder) {
		Properties props = new Properties();
		props.setProperty(KEY, folder.toString());
		try (OutputStream out = Files.newOutputStream(configFile())) {
			props.store(out, "SS-Save-Point settings");
		} catch (IOException e) {
			SSSavePoint.LOGGER.warn("SS-Save-Point could not save its settings file", e);
		}
	}

	/**
	 * Opens the folder window in its own small Java process, so it does not
	 * matter how the game's own Java was started. Returns null if the player
	 * cancels or if the window cannot be shown.
	 */
	private static Path askForFolder(Path startIn) {
		Process process = null;
		try {
			String java = ProcessHandle.current().info().command()
				.orElse(Path.of(System.getProperty("java.home"), "bin", "java").toString());

			String classpath = FabricLoader.getInstance()
				.getModContainer(SSSavePoint.MOD_ID)
				.map(container -> container.getOrigin().getPaths().stream()
					.map(Path::toString)
					.collect(Collectors.joining(File.pathSeparator)))
				.orElse("");
			if (classpath.isBlank()) {
				SSSavePoint.LOGGER.warn("SS-Save-Point could not find its own files");
				return null;
			}

			ProcessBuilder builder = new ProcessBuilder(
				java,
				"-Djava.awt.headless=false",
				"-cp",
				classpath,
				PICKER_CLASS,
				startIn.toAbsolutePath().toString()
			);
			builder.redirectErrorStream(true);
			process = builder.start();

			final Process started = process;
			CompletableFuture<byte[]> reader = CompletableFuture.supplyAsync(() -> {
				try (InputStream in = started.getInputStream()) {
					return in.readAllBytes();
				} catch (IOException e) {
					return new byte[0];
				}
			});

			byte[] bytes = reader.get(10, TimeUnit.MINUTES);
			int exitCode = started.waitFor();
			String output = new String(bytes, StandardCharsets.UTF_8).trim();

			if (exitCode == 1) {
				// the player closed the window without choosing
				return null;
			}
			if (exitCode != 0 || output.isEmpty()) {
				SSSavePoint.LOGGER.warn("SS-Save-Point folder window failed (exit code " + exitCode + "): " + output);
				return null;
			}

			String[] lines = output.split("\\R");
			Path picked = Path.of(lines[lines.length - 1].trim());
			return Files.isDirectory(picked) ? picked : null;
		} catch (Exception e) {
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			if (process != null) {
				process.destroyForcibly();
			}
			SSSavePoint.LOGGER.warn("SS-Save-Point could not show the folder window", e);
			return null;
		}
	}

	/**
	 * Returns the folder screenshots should go to, or null if the
	 * normal Minecraft behaviour should be used this time.
	 * On the very first call it asks the player to choose a folder.
	 */
	public static File getOrAsk(File gameDir, Consumer<Component> callback) {
		load();

		if (chosen == null) {
			Path defaultDir = new File(gameDir, "screenshots").toPath();

			Path picked = askForFolder(defaultDir);
			if (picked == null) {
				return null;
			}

			chosen = picked;
			save(chosen);
			moveExisting(defaultDir, chosen, callback);
		}

		File folder = chosen.toFile();
		if (!folder.isDirectory() && !folder.mkdirs()) {
			return null;
		}
		return folder;
	}

	private static void moveExisting(Path from, Path to, Consumer<Component> callback) {
		if (!Files.isDirectory(from)) {
			return;
		}

		try {
			Files.createDirectories(to);
			if (Files.isSameFile(from, to)) {
				return;
			}
		} catch (IOException e) {
			SSSavePoint.LOGGER.warn("SS-Save-Point could not prepare the chosen folder", e);
			return;
		}

		Util.ioPool().execute(() -> {
			List<Path> pictures;
			try (Stream<Path> stream = Files.list(from)) {
				pictures = stream
					.filter(p -> Files.isRegularFile(p))
					.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".png"))
					.toList();
			} catch (IOException e) {
				SSSavePoint.LOGGER.warn("SS-Save-Point could not list old screenshots", e);
				return;
			}

			int moved = 0;
			for (Path picture : pictures) {
				Path target = uniqueTarget(to, picture.getFileName().toString());
				try {
					Files.move(picture, target);
					moved++;
				} catch (IOException e) {
					SSSavePoint.LOGGER.warn("SS-Save-Point could not move " + picture, e);
				}
			}

			if (moved > 0) {
				callback.accept(Component.literal("SS-Save-Point moved " + moved + " screenshots to " + to));
			}
		});
	}

	private static Path uniqueTarget(Path folder, String name) {
		Path target = folder.resolve(name);
		if (!Files.exists(target)) {
			return target;
		}

		int dot = name.lastIndexOf('.');
		String base = dot < 0 ? name : name.substring(0, dot);
		String extension = dot < 0 ? "" : name.substring(dot);

		int count = 2;
		while (true) {
			target = folder.resolve(base + "_" + count + extension);
			if (!Files.exists(target)) {
				return target;
			}
			count++;
		}
	}
}
