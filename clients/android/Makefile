GRADLE ?= $(shell if [ -x ./gradlew ]; then echo ./gradlew; else echo gradle; fi)
ANDROID_HOME ?= $(shell if [ -d "$$HOME/Library/Android/sdk" ]; then echo "$$HOME/Library/Android/sdk"; elif [ -d "$$HOME/Android/Sdk" ]; then echo "$$HOME/Android/Sdk"; fi)
ADB ?= $(if $(ANDROID_HOME),$(ANDROID_HOME)/platform-tools/adb,adb)
APK := app/build/outputs/apk/debug/app-debug.apk
GRADLE_ENV := $(if $(ANDROID_HOME),ANDROID_HOME="$(ANDROID_HOME)",)

.PHONY: build clean install install-device

build:
	$(GRADLE_ENV) $(GRADLE) :app:assembleDebug

clean:
	$(GRADLE_ENV) $(GRADLE) clean

install: install-device

install-device: build
	"$(ADB)" install -r "$(APK)"
