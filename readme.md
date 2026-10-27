# Matlab support for IntelliJ
Matlab syntax highlighting plugin for JetBrains IDEs.

> **This is a fork** of [kornilova203/matlab-IntelliJ-plugin](https://github.com/kornilova203/matlab-IntelliJ-plugin),
> updated for IntelliJ 2025.2+ (tested on 2026.2). Until it's published, install it from disk:
> run `./gradlew buildPlugin`, then **Settings | Plugins | ⚙ | Install Plugin from Disk...** and pick
> `build/distributions/IntelliJMatlabSupport-*.zip`. It replaces the original plugin if that is installed.

![Screenshot of Matlab plugin](screenshots/screen.png)

https://plugins.jetbrains.com/plugin/10941-matlab-support

## Installation
 1. Open **Settings | Plugins** (Windows) or **Preferences | Plugins** (MacOS)
 2. Click **Browse repositories...**
 3. Type _Matlab Support_ in search bar
 4. Click **Install**

## Running matlab files from IDE
 1. Right click on .m file in files tree.
 2. Choose **Run 'helloWorld.m'**.

MATLAB is found automatically (on `PATH`, or in the standard install folders such as
`/usr/local/MATLAB/R20xx`). To choose an installation, set its executable in
**Settings | Languages & Frameworks | MATLAB**; a run configuration can override it
(**Run | Edit Configurations...** > Interpreter path). [Octave] works too.

The script is run with `matlab -batch "run('file.m')"` (or `octave --eval` when the interpreter
path contains `octave`). The process stays alive while figure windows are open.

## Development

For easier development install [Grammar-Kit](https://plugins.jetbrains.com/plugin/6606-grammar-kit) plugin.

Requirements: JDK 25 (the JetBrains Runtime bundled with IntelliJ works:
`export JAVA_HOME=<IntelliJ install>/jbr`).

Follow these steps to start working on the plugin:
1. Clone project and import it to IntelliJ IDEA as Gradle project.
2. Run/Debug `runIde` Gradle task.

Useful Gradle tasks:
- `test` runs the test suite.
- `verifyPlugin` checks compatibility and API usage against the IDE versions listed in `build.gradle.kts`.
- `test --tests '*CorpusParsingTest' -PmatlabCorpus=/path/to/code` parses every `.m` file in a folder
  and writes the parse errors to `build/corpus-report.txt`.

### How to re-generate parser

After you made changes to bnf or flex files you need to generate new lexer/parser code:
 
1. Generate Matlab lexers. Open [MatlabLexer.flex](grammar/MatlabLexer.flex) and click **Run JFlex Generator** from context menu, do the same for [SingleQuoteStringLexer.flex](grammar/SingleQuoteStringLexer.flex).
2. Generate Matlab parser. Open [Matlab.bnf](grammar/Matlab.bnf) and run **Generate Parser Code** from context menu.

If you have any issue with setting up development environment then please create an issue.

The project is written in Kotlin. It's pretty easy to learn. To get started you may quickly look through [Basic Syntax](https://kotlinlang.org/docs/reference/basic-syntax.html) and [Classes and Inheritance](https://kotlinlang.org/docs/reference/classes.html).  
Contributions in Java are also welcomed.

To build a jar with Matlab Plugin run `buildPlugin` Gradle task.

### Tips for plugin developers

1. _View PSI Structure._
  When you start IDEA with `runIde` Gradle task you may open a matlab file in the IDE and run **View PSI Structure of Current File..** action.


 [Octave]: https://www.gnu.org/software/octave/
