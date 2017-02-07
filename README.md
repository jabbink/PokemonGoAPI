This is just an initial stub for a readme file as there wasn't one previously.

Once you retrieve the git repo, issue the command:
git submodule update --init
That will retrieve the data structures for the Pokemon Go API in buildSrc\lib\POGOProtos

The head will be detached and at the correct version.  For me that was 04c9034
which was Release v2.1.0. If you are revamping the API to spport a different version you could
pull a more recent version with something like 'git pull origin master'
but the supporting code would now break and need to be fixed.

Then from the root of the repo build the api jar file with:
gradlew build
The resulting file will be in build\libs as something like api-1.1.0.jar

If you want to separately look through the protos, insure you have a
current version of python installed and you can produce the java class files
from the subdirectory
buildSrc\lib\POGOProtos
and running the command:
python compile.py java
