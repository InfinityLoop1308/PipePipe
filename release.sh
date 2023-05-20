cd PipePipeClient
git pull
cd ../PipePipeExtractor
git pull
cd ..
vim fastlane/metadata/android/en-US/changelogs/$1.txt
git add .
git commit -a
git push
git push git@github.com:InfinityLoop1308/PipePipe.git
