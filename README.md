# Video Clipper
Android video manipulation app

# Intent
This tool is a wrapper for the FFmpeg and FFprobe libraries. Its purpose is to ease the use of the aforementioned libraries and provide an understandable UI for regular users.

# Functionality
Currently the app can accomplish the following:
- Parse a chosen video file and determine its *Attributes*
- Concatenate multiple videos (at the moment possible only with same resolution videos)
- Re-encode streams to a common codec while concatenating (based on the output chosen)

# Expansions
Future improvements of this app are highly dependent on the functionality of FFmpeg and FFprobe libraries which are the basis of this tool. Currently planned video modifications include:
- Resize
- Crop
- Cut
- Image overlay
- Addition of extra audio streams
- Pre-installed transitions

# Main classes
The main classes and their purposes are:
- `FfmpegService` - queue jobs and contact FFmpeg library via JNI
- `Ffprobe` - parse file Attributes using FFprobe library that's also called via JNI
- `NavDrawerFragment`, `EditorFragment`, `HelperFragment` - specific task fragments
- `MainActivity` - connect all the top-level fragments

## Other info
I am very open to receiving help for this open source project.
If you have any suggestions, critic or questions feel free to contact me!
