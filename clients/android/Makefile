GRADLE := ./gradlew
ANDROID_HOME ?= $(shell if [ -d "$$HOME/Library/Android/sdk" ]; then echo "$$HOME/Library/Android/sdk"; elif [ -d "$$HOME/Android/Sdk" ]; then echo "$$HOME/Android/Sdk"; fi)
ADB ?= $(if $(ANDROID_HOME),$(ANDROID_HOME)/platform-tools/adb,adb)
APK := app/build/outputs/apk/debug/app-debug.apk

ifeq ($(origin JAVA_HOME),undefined)
JAVA_HOME := $(shell \
	if [ "$$(uname -s)" = "Darwin" ]; then \
		candidate=$$(/usr/libexec/java_home -v 17 2>/dev/null || true); \
		if [ -x "$$candidate/bin/java" ] && "$$candidate/bin/java" -version 2>&1 | head -1 | grep -Eq 'version "17([.]|$$)'; then \
			printf '%s' "$$candidate"; \
		else \
			for candidate in \
				/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
				/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home; do \
				if [ -x "$$candidate/bin/java" ]; then printf '%s' "$$candidate"; break; fi; \
			done; \
		fi; \
	fi)
endif

GRADLE_ENV := JAVA_HOME="$(JAVA_HOME)" $(if $(ANDROID_HOME),ANDROID_HOME="$(ANDROID_HOME)",)

.PHONY: build clean install install-device doctor check-java

check-java:
	@if [ -z "$(JAVA_HOME)" ] || [ ! -x "$(JAVA_HOME)/bin/java" ]; then \
		echo "JDK 17 was not found. Install it with 'brew install openjdk@17' or run make with JAVA_HOME pointing to a JDK 17 installation." >&2; \
		exit 1; \
	fi
	@if ! "$(JAVA_HOME)/bin/java" -version 2>&1 | head -1 | grep -Eq 'version "17([.]|$$)'; then \
		echo "JAVA_HOME must point to JDK 17; resolved: $(JAVA_HOME)" >&2; \
		"$(JAVA_HOME)/bin/java" -version >&2; \
		exit 1; \
	fi

build: check-java
	$(GRADLE_ENV) $(GRADLE) :app:assembleDebug

clean: check-java
	$(GRADLE_ENV) $(GRADLE) clean

doctor: check-java
	@echo "JAVA_HOME=$(JAVA_HOME)"
	@"$(JAVA_HOME)/bin/java" -version
	@$(GRADLE_ENV) $(GRADLE) --version
	@echo "ANDROID_HOME=$(if $(ANDROID_HOME),$(ANDROID_HOME),(not found))"

install: install-device

install-device: build
	"$(ADB)" install -r "$(APK)"
