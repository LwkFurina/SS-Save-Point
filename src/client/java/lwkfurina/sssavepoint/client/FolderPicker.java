package lwkfurina.sssavepoint.client;

import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.UIManager;

/**
 * Runs in its own small Java process, so it never depends on how the game's
 * own Java was started. It must only use classes that come with Java itself.
 * It prints the chosen folder to standard output.
 * Exit code 0 means a folder was chosen, 1 means the window was cancelled,
 * and 2 means the window could not be shown.
 */
public final class FolderPicker {
	private FolderPicker() {
	}

	public static void main(String[] args) {
		System.setProperty("java.awt.headless", "false");

		File start = args.length > 0 ? new File(args[0]) : new File(System.getProperty("user.home"));

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

			JFileChooser chooser = new JFileChooser(start);
			chooser.setDialogTitle("What folder do you want to put your screenshots in?");
			chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			chooser.setApproveButtonText("Choose this folder");

			int result = chooser.showOpenDialog(parent);
			if (result == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
				PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
				out.println(chooser.getSelectedFile().getAbsolutePath());
				out.flush();
				parent.dispose();
				System.exit(0);
			}

			parent.dispose();
			System.exit(1);
		} catch (Throwable t) {
			t.printStackTrace();
			System.exit(2);
		}
	}
}