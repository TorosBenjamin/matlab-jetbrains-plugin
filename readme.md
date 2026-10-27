# Unofficial MATLAB Support

MATLAB® language support for JetBrains IDEs (IntelliJ IDEA, PyCharm, WebStorm, ...): editing, navigation,
code analysis and running. Everything except running scripts works without MATLAB.

This is a continuation of [Matlab Support](https://github.com/kornilova203/matlab-IntelliJ-plugin) by
Liudmila Kornilova, published as a separate plugin. All credit for the original goes to her.

*Not affiliated with or endorsed by MathWorks. MATLAB is a registered trademark of The MathWorks, Inc.*

## Features
- **Parser** for modern MATLAB: classes, packages (`+pkg`), class folders (`@Cls`), `arguments` blocks,
  command syntax (`hold on`), `import`, superclass calls, method signatures
- **Highlighting** of fields, methods, parameters, the object variable, types, attributes, function calls
  (Settings | Editor | Color Scheme | MATLAB)
- **Navigation**: go to definition (including into packages and class folders), find usages, usage counts,
  Navigate | Class / Symbol, structure view, breadcrumbs
- **Completion** that depends on the position: class members after `obj.`, struct fields, attributes and their
  values, types, validators, package members; method results such as singletons (`AppConfig.getInstance()`)
- **Refactoring**: rename (a class, its constructor and its file together), introduce/inline variable
- **Inspections**: unused variables, functions and imports, unreachable code, unresolved imports, and MATLAB's
  own code analyzer (mlint) when MATLAB is installed
- **Running** scripts or single `%%` sections with MATLAB or GNU Octave, from the gutter or the context menu
- **Editing**: auto-inserted `end`, folding of `%%` sections, live templates (`for`, `if`, `fun`, `cls`, ...),
  Surround With, TODO and spell-checking

## Installation
Until it's on the JetBrains Marketplace: download the zip from the
[releases](https://github.com/TorosBenjamin/matlab-jetbrains-plugin/releases) (or build it, see below), then
**Settings | Plugins | ⚙ | Install Plugin from Disk...**.

Uninstall the original *Matlab Support* plugin first; both handle `.m` files. In CLion, `.m` files are
Objective-C, so the plugin conflicts there.

## Running MATLAB files
Right click a `.m` file, or click ▶ in the gutter next to a `%%` section or a function file's main function.

MATLAB is found automatically (on `PATH`, or in the standard install folders such as `/usr/local/MATLAB/R20xx`).
To choose an installation, set its executable in **Settings | Languages & Frameworks | MATLAB**; a run
configuration can override it. [GNU Octave](https://www.gnu.org/software/octave/) works too.

The settings page also turns the code analyzer (mlint) and auto-inserted `end` on or off.

## Development
Requirements: JDK 25 (the JetBrains Runtime bundled with IntelliJ works: `export JAVA_HOME=<IntelliJ install>/jbr`).

- `./gradlew runIde` starts an IDE with the plugin
- `./gradlew test` runs the tests
- `./gradlew buildPlugin` builds `build/distributions/unofficial-matlab-support-<version>.zip`
- `./gradlew verifyPlugin` checks compatibility with the IDE versions listed in `build.gradle.kts`
- `./gradlew generateParserCode` regenerates the lexers and parser in `gen/` from `grammar/`
- `./gradlew test --tests '*CorpusParsingTest' -PmatlabCorpus=/path/to/code` parses every `.m` file in a folder
  and writes the parse errors to `build/corpus-report.txt`
- `data/update_docs.py` refreshes the bundled MATLAB function list and documentation links (see `data/README.md`)

Publishing to the Marketplace reads its secrets from environment variables: `CERTIFICATE_CHAIN`, `PRIVATE_KEY`,
`PRIVATE_KEY_PASSWORD` (`./gradlew signPlugin`) and `PUBLISH_TOKEN` (`./gradlew publishPlugin`).

## License
MIT, see [LICENSE](LICENSE).
