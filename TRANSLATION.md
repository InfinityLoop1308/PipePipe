Thanks for your interest in contributing to the translation!

## If you know how to use git to clone, modify and then make a pull request

Just clone the client, open `app/src/main/res/values/strings.xml` and find the untranslated strings. The string is like that:
```agsl
<string name="title_activity_about">About AnimePipe</string>
```
there the name is the key, and the content is the value. Copy this line and paste it to the end of the your language's file, which can be found in the same directory. Then translate the value. 

For example, if you are translating to Simplified Chinese, you should paste the line to the end of `app/src/main/res/values-b+zh+HANS+CN/strings.xml`. Then translate the value to Chinese. The result should be like this:
```agsl
<string name="title_activity_about">关于AnimePipe</string>
```
When you finish your translation you can make a pull request.

## If you don't know how to use git

You can just ignore the clone step and open string.xml [online](https://codeberg.org/NullPointerException/AnimePipeClient/src/branch/dev/app/src/main/res/values/strings.xml)
then follow the same steps above. You can also modify your language's file online, and then it will automatically make a pull request.
For example, the link to the Simplified Chinese file is [here](https://codeberg.org/NullPointerException/AnimePipeClient/src/branch/dev/app/src/main/res/values-b+zh+HANS+CN/strings.xml).
