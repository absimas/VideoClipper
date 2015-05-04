#!/bin/bash

NDK=/home/simas/Downloads/android-ndk
PLATFORM=$NDK/platforms/android-9/arch-arm
TOOLCHAIN=$NDK/toolchains/arm-linux-androideabi-4.9/prebuilt/linux-x86_64
CPU=arm
PREFIX=$(pwd)/android/$CPU
ADDI_CFLAGS="-marm"

pushd ffmpeg

# Configure
./configure \
	--target-os=android \
	--prefix=$PREFIX \
	--enable-cross-compile \
	--enable-runtime-cpudetect \
	--disable-asm \
	--arch=arm \
	--cc=$TOOLCHAIN/bin/arm-linux-androideabi-gcc \
	--cross-prefix=$TOOLCHAIN/bin/arm-linux-androideabi- \
	--disable-stripping \
	--nm=$TOOLCHAIN/bin/arm-linux-androideabi-nm \
	--sysroot=$PLATFORM \
	--disable-programs \
	--disable-doc \
	--enable-protocol=file \
	--disable-avresample \
	--enable-gpl \
	--enable-version3 \
	--enable-nonfree \
	--disable-ffplay \
	--disable-ffserver \
	--disable-ffmpeg \
	--disable-ffprobe \
	--extra-cflags="-fPIC -DANDROID -D__thumb__ -mthumb -Wfatal-errors -Wno-deprecated $ADDI_CFLAGS" \
	--extra-libs="-lgcc" \
	--extra-ldflags="-L$PLATFORM/usr/lib -nostdlib -lc -lm -ldl -llog"
# Make
make clean
make -j5
make -j5 install

popd