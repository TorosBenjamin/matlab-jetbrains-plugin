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
  Navigate | Class / Symbol, structure view, breadcrumbs; class inheritance: inherited members, gutter icons for
  subclasses and overridden methods, Go to Implementation and Super Method
- **Completion** that depends on the position: class members after `obj.`, struct fields, attributes and their
  values, types, validators, package members; method results such as singletons (`AppConfig.getInstance()`)
- **Refactoring**: rename (a class, its constructor and its file together), introduce/inline variable
- **Inspections**: unused variables, functions and imports, unreachable code, unresolved imports, and MATLAB's
  own code analyzer (mlint) when MATLAB is installed; quick fixes remove what's unused, suppress mlint warnings
  (`%#ok`) and create functions that are called but not defined
- **Running** scripts, single `%%` sections or the selection (Alt+Shift+E) with MATLAB or GNU Octave, in a **MATLAB
  console** that keeps running between runs, so variables are kept and later runs start instantly; more consoles with +
  in the MATLAB tool window or Tools | New MATLAB Console
- **Imports**: package members used without an import are reported, with fixes to import them or write the package
  name; completion adds the import; Code | Optimize Imports removes unused ones
- **Editing**: Reformat Code, parameter info (Ctrl+P, with types and defaults from `arguments` blocks),
  auto-inserted `end`, folding of `%%` sections, live templates (`for`, `if`, `fun`, `cls`, ...), Surround With,
  TODO and spell-checking
- **MATLAB path**: folders outside the project (like `addpath`) and, optionally, MATLAB's own `toolbox/matlab`
  are indexed, so their functions, classes and packages resolve and complete
- **Other languages**: go to definition from MATLAB into the code it calls:
  - Python: `py.mod.func(x)`, `py.pkg.mod.Class`, with completion after `py.`
  - Java: `java.util.ArrayList()`, `com.example.Util.parse(x)`, after `import com.example.*`, and in
    `javaObject`/`javaMethod`
  - C/C++/Fortran: MEX functions (`foo(x)` opens `mexFunction` in `foo.c`), `mex foo.c`, and
    `calllib('lib', 'func')` into the header passed to `loadlibrary`

  Declarations are found in the files' text, so this works in any JetBrains IDE. Where the IDE supports the
  language (Python in PyCharm, Java in IntelliJ IDEA, C in CLion), find usages and rename also work from the
  other language's side.

## Installation
Until it's on the JetBrains Marketplace: download the zip from the
[releases](https://github.com/TorosBenjamin/matlab-jetbrains-plugin/releases) (or build it, see below), then
**Settings | Plugins | ⚙ | Install Plugin from Disk...**.

Uninstall the original *Matlab Support* plugin first; both handle `.m` files. In CLion, `.m` files are
Objective-C, so the plugin conflicts there.

## Running MATLAB files
Right click a `.m` file, or click ▶ in the gutter next to a `%%` section or a function file's main function. The code
runs in the **MATLAB** tool window, in a MATLAB session that stays open: the next run reuses it, and you can type
commands there. On Windows the console connects through MATLAB's Java Engine API (MATLAB R2016b or newer). To start a
new MATLAB for each run instead, uncheck *Run in the MATLAB console* in the run configuration.

MATLAB is found automatically (on `PATH`, or in the standard install folders such as `/usr/local/MATLAB/R20xx`).
To choose an installation, set its executable in **Settings | Languages & Frameworks | MATLAB**; a run
configuration can override it. [GNU Octave](https://www.gnu.org/software/octave/) works too.

The settings page also turns the code analyzer (mlint) and auto-inserted `end` on or off.

## MATLAB path
Code in folders outside the project is found when they're listed under **MATLAB Path** in
**Settings | Languages & Frameworks | MATLAB**, one per line (relative paths start at the project folder). They show
up under External Libraries. **Include MATLAB's own code** also indexes `<matlabroot>/toolbox/matlab`, so
functions such as `linspace` open in the editor; the first indexing takes a while.

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

### Releasing
1. Set `version` in `build.gradle.kts`, and describe the changes in `CHANGELOG.md` (a `## <version>` section) and in
   `<change-notes>` in `plugin.xml`.
2. Run the **Release** workflow on `master` (Actions | Release | Run workflow), or push a tag `v<version>`. It runs the
   tests, builds the zip and creates the GitHub release `v<version>` with the changelog section as its notes.
3. With the repository secrets above set, it also signs the plugin and publishes it to the JetBrains Marketplace.

## License
MIT, see [LICENSE](LICENSE).
