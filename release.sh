#!/bin/bash

# Usage: ./release.sh <version> [-i]
# Example: ./release.sh v5.0.0 -i
# -i flag: increment versionCode and update fastlane changelog

VERSION=""
INCREMENT_VERSION_CODE=false

# Parse arguments
for arg in "$@"; do
    if [[ "$arg" == "-i" ]]; then
        INCREMENT_VERSION_CODE=true
    elif [[ "$arg" =~ ^v?[0-9]+\.[0-9]+\.[0-9]+(-.*)?$ ]]; then
        VERSION="${arg#v}"  # Remove 'v' prefix if present
    fi
done

if [ -z "$VERSION" ]; then
    echo "Error: Version number required. Usage: ./release.sh <version> [-i]"
    echo "Example: ./release.sh v5.0.0 -i"
    exit 1
fi

echo "Processing release for version: $VERSION"
if [ "$INCREMENT_VERSION_CODE" = true ]; then
    echo "Will increment versionCode and update fastlane changelog"
fi

# Function to update version in build.gradle.kts
update_version() {
    local file=$1
    local version=$2

    if [ ! -f "$file" ]; then
        echo "Warning: $file not found, skipping..."
        return
    fi

    # Check if it's the main client build.gradle.kts (has versionName)
    if grep -q "versionName" "$file"; then
        echo "Updating versionName in $file to $version"
        sed -i "s/versionName = \"[^\"]*\"/versionName = \"$version\"/" "$file"
    fi

    # Update version = "..." pattern (for extractor and shared modules)
    if grep -q "^version = " "$file"; then
        echo "Updating version in $file to $version"
        sed -i "s/^version = \"[^\"]*\"/version = \"$version\"/" "$file"
    fi

    # Update dependency versions for shared and extractor
    echo "Updating dependency versions in $file to $version"
    sed -i "s/implementation(\"project.pipepipe:shared:[^\"]*\")/implementation(\"project.pipepipe:shared:$version\")/" "$file"
    sed -i "s/implementation(\"project.pipepipe:extractor:[^\"]*\")/implementation(\"project.pipepipe:extractor:$version\")/" "$file"
}

# Function to increment versionCode
increment_version_code() {
    local file=$1

    if [ ! -f "$file" ]; then
        echo "Warning: $file not found, skipping versionCode increment..."
        return
    fi

    if grep -q "versionCode" "$file"; then
        local current_code=$(grep "versionCode = " "$file" | sed 's/.*versionCode = \([0-9]*\).*/\1/')
        local new_code=$((current_code + 1))
        echo "Incrementing versionCode from $current_code to $new_code in $file"
        sed -i "s/versionCode = $current_code/versionCode = $new_code/" "$file"
        echo "$new_code"
    fi
}

# Client module
echo "=== Processing client module ==="
git pull
git pull git@codeberg.org:NullPointerException/PipePipe.git

# Update version numbers in client build.gradle.kts
update_version "android/build.gradle.kts" "$VERSION"

# Handle versionCode increment and fastlane changelog
if [ "$INCREMENT_VERSION_CODE" = true ]; then
    NEW_VERSION_CODE=$(increment_version_code "android/build.gradle.kts")

    if [ -n "$NEW_VERSION_CODE" ]; then
        CHANGELOG_FILE="fastlane/metadata/android/en-US/changelogs/${NEW_VERSION_CODE}.txt"
        mkdir -p "fastlane/metadata/android/en-US/changelogs"

        if [ ! -f "$CHANGELOG_FILE" ]; then
            echo "Creating changelog file: $CHANGELOG_FILE"
            touch "$CHANGELOG_FILE"
        fi

        echo "Opening changelog file for editing..."
        vim "$CHANGELOG_FILE"
    fi
else
    echo "Skipping versionCode increment (use -i flag to enable)"
fi

# Commit changes
git add android/build.gradle.kts
if [ "$INCREMENT_VERSION_CODE" = true ] && [ -n "$NEW_VERSION_CODE" ]; then
    git add "fastlane/metadata/android/en-US/changelogs/${NEW_VERSION_CODE}.txt"
fi
git commit -m "v$VERSION"

git push
git push git@codeberg.org:NullPointerException/PipePipe.git

# Extractor module
echo "=== Processing extractor module ==="
cd ../extractor
git pull
git pull git@codeberg.org:NullPointerException/PipePipeExtractor.git

# Update version numbers in extractor build.gradle.kts
update_version "build.gradle.kts" "$VERSION"

# Commit changes
git add build.gradle.kts
git commit -m "v$VERSION"

git push
git push git@codeberg.org:NullPointerException/PipePipeExtractor.git

# Shared module
echo "=== Processing shared module ==="
cd ../shared
git pull

# Update version numbers in shared build.gradle.kts
update_version "build.gradle.kts" "$VERSION"

# Commit changes
git add build.gradle.kts
git commit -m "v$VERSION"

git push

echo ""
echo "=== Release process completed ==="
echo "Version updated to: $VERSION"
if [ "$INCREMENT_VERSION_CODE" = true ] && [ -n "$NEW_VERSION_CODE" ]; then
    echo "Version code incremented to: $NEW_VERSION_CODE"
fi

# Return to client directory
cd ../client

