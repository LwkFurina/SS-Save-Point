# SS-Save-Point

Pick one folder for all your Minecraft screenshots.

The first time you press F2, a window asks "What folder do you want to put your screenshots in?" After you choose a folder, every screenshot you already have is moved there, and every new screenshot is saved straight into it. The window only appears once. After that, F2 works as it always has.

## Requirements

- Minecraft 26.3
- Fabric Loader 0.19.5 or newer
- Fabric API
- Java 25

SS-Save-Point is a client mod, so it is not needed on servers. Mod Menu is optional.

## Changing your folder

Your chosen folder is stored in a file called `ss-save-point.properties` in the Minecraft config folder. Delete that file, and the window appears again on your next F2 press.

## Building from source

1. Clone this repository.
2. Run `./gradlew build` (or `gradlew.bat build` on Windows).
3. The finished jar is in `build/libs`. Use the one without `-sources` in its name.

## License

This project is available under the MIT license. See the LICENSE file for details.