#Modifications to the Bouncycastle library#

Thormor internally uses the OpenPGP implementation in the bouncycastle
library. As bouncycastle itself is used in many environments (notably,
Android) it also renames the package to avoid versioning and name
collisions.

The renaming process is crude. The relevant jar files are downloaded,
unzipped and the signatures removed. Next, a small perl script renames
strings inside all the class files, and the jar file is rezipped.
