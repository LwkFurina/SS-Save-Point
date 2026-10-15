package lwkfurina.sssavepoint.client.mixin;

import java.io.File;
import java.util.function.Consumer;

import com.mojang.blaze3d.pipeline.RenderTarget;
import lwkfurina.sssavepoint.SSSavePoint;
import lwkfurina.sssavepoint.client.SaveFolder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screenshot.class)
public class ScreenshotMixin {

	@Inject(
		method = "grab(Ljava/io/File;Ljava/lang/String;Lcom/mojang/blaze3d/pipeline/RenderTarget;ILjava/util/function/Consumer;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private static void sssavepoint$redirectScreenshot(
		File workDir,
		String forceName,
		RenderTarget target,
		int downscaleFactor,
		Consumer<Component> callback,
		CallbackInfo ci
	) {
		// A forced name means a special screenshot (like a panorama), so leave those alone.
		if (forceName != null) {
			return;
		}

		File folder = SaveFolder.getOrAsk(workDir, callback);
		if (folder == null) {
			// No folder chosen, so let Minecraft save the normal way this time.
			return;
		}

		ci.cancel();

		Screenshot.takeScreenshot(target, downscaleFactor, image -> {
			File file = sssavepoint$nextFile(folder);

			Util.ioPool().execute(() -> {
				try (image) {
					image.writeToFile(file);
					Component name = Component.literal(file.getName())
						.withStyle(ChatFormatting.UNDERLINE)
						.withStyle(style -> style.withClickEvent(new ClickEvent.OpenFile(file.getAbsoluteFile())));
					callback.accept(Component.translatable("screenshot.success", name));
				} catch (Exception e) {
					SSSavePoint.LOGGER.warn("SS-Save-Point could not save the screenshot", e);
					callback.accept(Component.translatable("screenshot.failure", e.getMessage()));
				}
			});
		});
	}

	private static File sssavepoint$nextFile(File folder) {
		String name = Util.getFilenameFormattedDateTime();
		int count = 1;

		while (true) {
			File file = new File(folder, name + (count == 1 ? "" : "_" + count) + ".png");
			if (!file.exists()) {
				return file;
			}
			count++;
		}
	}
}