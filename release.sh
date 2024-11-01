cd PipePipeClient
git pull
cd ../PipePipeExtractor
git pull
cd ..
if [ -n "$1" ]; then
    vim fastlane/metadata/android/en-US/changelogs/$1.txt
else
    echo "No version number provided. Skipping changelog edit."
fi
git add .
git commit -a
git push
git push git@github.com:InfinityLoop1308/PipePipe.git
