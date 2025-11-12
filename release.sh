cd PipePipeClient
git pull
git push git@codeberg.org:NullPointerException/PipePipeClient-legacy.git
cd ../PipePipeExtractor
git pull
git push git@codeberg.org:NullPointerException/PipePipeExtractor-legacy.git
cd ..
if [ -n "$1" ]; then
    vim fastlane/metadata/android/en-US/changelogs/$1.txt
else
    echo "No version number provided. Skipping changelog edit."
fi
git add .
git commit -a
git push origin HEAD
git push git@codeberg.org:NullPointerException/PipePipe.git
