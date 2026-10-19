# SS-Save-Point

This fabric mod puts your Minecraft screenshots exactly where you want them. The first time you press F2, a window asks “What folder do you want to put your screenshots in?” You may choose a folder and every screenshot you already have will be moved there, and all future screenshots will be automatically moved to the new location. The window only appears once, and after that F2 works as it always has, with Minecraft’s usual “saved as” message in chat. If you close the window without choosing, that screenshot is saved the normal way and the window appears again on your next F2 press.

## Changing your folder

Your chosen folder is stored in a file called `ss-save-point.properties` in the Minecraft config folder. Delete that file, and the window appears again on your next F2 press.

## Building from source

1. Clone this repository.
2. Run `./gradlew build`
