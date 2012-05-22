#Thormor#

This repository contains a standalone java library to make it easier
to create and manage
[Thormor vaults](http://kbsriram.github.com/thormor/ "Introduction to Thormor Vaults"). It also contains an example implementation that stores vaults on
[Google Sites](https://sites.google.com), and a simple command line shell
to access vaults.

## Using the library##

Just add [thormor.jar](bin/thormor.jar) to your project. The
jar file already contains the
[BouncyCastle PGP implementation](http://www.bouncycastle.org/java.html) as
as well as [a JSON library](https://github.com/douglascrockford/JSON-java).
These packages have also been internally renamed to avoid version collisions; so you should
be able to use the library jar anywhere.

Please look at the [library documentation](docs/library.md) as well as
[the javadocs](javadoc/index.html) for tips on using the library.

## Running the demo shell##

This is a very simple command line shell to manage thormor
vaults. Please keep in mind that the shell is primarily intended as a
tool for development rather than for actual use. It uses [Google
sites](https://sites.google.com) to host your vault, so you'll need to
have an account on Google to use it.

Run it as

    $ java -jar bin/thormor-cli.jar

and follow the instructions. You can pass in options to change where
local files are saved. Please run
`java -jar thormor-cli.jar --help` for details.

Type `help` in the shell to list the available commands. The
[Introduction to Thormor](http://kbsriram.github.com/thormor/) page
also has a screencast that shows what you can do with the shell.

## License ##

All code in this repository is released under the
[Simplified BSD License](License.txt).

It uses the
[BouncyCastle PGP implementation](http://www.bouncycastle.org/java.html)
which is made available under the following license.

<pre><code>
Copyright (c) 2000 - 2011 The Legion Of The Bouncy Castle (http://www.bouncycastle.org)

Permission is hereby granted, free of charge, to any person obtaining
a copy of this software and associated documentation files (the
"Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish,
distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to
the following conditions:

The above copyright notice and this permission notice shall be
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
</code></pre>

It also uses the [JSON-Java](https://github.com/douglascrockford/JSON-java)
library, which is made available under the following license.

<pre><code>
Copyright (c) 2002 JSON.org

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

The Software shall be used for Good, not Evil.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
</code></pre>

Both these packages have been internally renamed in order to avoid
namespace collisions.
