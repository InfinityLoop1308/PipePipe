cd NewPipeEnhanced-client
git pull
cd ../NewPipeExtractor
git pull
cd ..
vim fastlane/metadata/android/en-US/changelogs/$1.txt
git add .
git commit -a
git push
