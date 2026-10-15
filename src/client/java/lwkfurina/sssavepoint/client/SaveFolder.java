package lwkfurina.sssavepoint.client;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.UIManager;

import lwkfurina.sssavepoint.SSSavePoint;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

public final class SaveFolder {
	private static final String KEY = "folder";
	private static final String QUESTION = "What folder do you want to put your screenshots in?";
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

	private static Path askForFolder(Path startIn) {
		System.setProperty("java.awt.headless", "false");

		if (GraphicsEnvironment.isHeadless()) {
			SSSavePoint.LOGGER.warn("SS-Save-Point cannot show a window on this computer");
			return null;
		}

		JFrame parent = null;
		try {
			try {
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			} catch (Exception ignored) {
				// the default look is fine if the system look is not available
			}

			parent = new JFrame();
			parent.setUndecorated(true);
			parent.setAlwaysOnTop(true);
			parent.setLocationRelativeTo(null);
			parent.setVisible(true);

			JFileChooser chooser = new JFileChooser(startIn.toFile());
			chooser.setDialogTitle(QUESTION);
			chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			chooser.setApproveButtonText("Choose this folder");

			int result = chooser.showOpenDialog(parent);
			if (result == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
				return chooser.getSelectedFile().toPath();
			}
			return null;
		} catch (Throwable t) {
			SSSavePoint.LOGGER.warn("SS-Save-Point could not show the folder window", t);
			return null;
		} finally {
			if (parent != null) {
				parent.dispose();
			}
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