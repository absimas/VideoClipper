# Video Clipper
Android video manipulation app

## Support
I am very open to receiving help for this open source project.
If you have any suggestions, critic or questions feel free to contact me!

# Usage (developers)
When first compiling this library you need to complete a few steps:

1. Download ffmpeg library
 
        `git clone https://github.com/FFmpeg/FFmpeg app/src/main/jni/ffmpeg/`
2. Build ffmpeg
 
      `./app/src/main/jni/build_ffmpeg.sh`
3. Compile libvc.so. Uncomment a block of code in `app/build.gradle`:

        // Debug tasks
        /*	// Disable implicit Android.mk creation for all app variants
	        // Custom tasks that compile libraries with ndk-build using the customized makefile
	        //noinspection GroovyAssignabilityCheck
	        task ndkBuildDebug(type: Exec) {
		        // Fetch NDK directory from local.properties
		        Properties properties = new Properties()
		        properties.load(project.rootProject.file('local.properties').newDataInputStream())
		        def ndkDir = properties.getProperty('ndk.dir')
		        println("DEBUG")
		        commandLine "$ndkDir/ndk-build",
				        "NDK_DEBUG=0",
        				"-B", // Force a rebuild
        				'NDK_PROJECT_PATH=build/intermediates/ndk',
        				'NDK_LIBS_OUT=src/main/jniLibs',
				        'APP_BUILD_SCRIPT=src/main/jni/Android.mk',
				        'NDK_APPLICATION_MK=src/main/jni/Application.mk'
	        }
	        tasks.whenTaskAdded { task ->
		        if (task.name == 'compileDebugJava') {
			        task.dependsOn ndkBuildDebug
		        }
        	}
        */
        
**Note:** Code in step 3 needs to be uncommented only if:
- You're compiling for the first time (and lib still doesn't exist)
- When you change native code, i.e. `vc.c`


-----

# Functionality
Currently the app can accomplish the following:
- Parse a chosen video file and determine its *Attributes*
- Concatenate multiple videos (at the moment possible only with same resolution videos)
- Re-encode streams to a common codec while concatenating (based on the output chosen)

# Expansion
Future improvements of this app are highly dependent on the functionality of FFmpeg and FFprobe libraries which are the basis of this tool. Currently planned video modifications include:
- Resize
- Crop
- Cut
- Image overlay
- Addition of extra audio streams
- Pre-installed transitions
