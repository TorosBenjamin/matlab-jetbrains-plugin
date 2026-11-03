# Changelog

## Unreleased

- `.m` files get the original Matlab Support file icon again (with a variant for dark themes)
- Plugin logo: a plain red-orange M
- Warning when the original Matlab Support plugin is enabled as well: both handle .m files, which breaks highlighting

## 1.1.0

- **MATLAB path**: folders outside the project (like `addpath`) in Settings | Languages & Frameworks | MATLAB, and optionally MATLAB's own `toolbox/matlab`, are indexed, so their functions, classes and packages resolve and complete
- **Other languages**: go to definition from MATLAB into Python (`py.mod.func`, with completion after `py.`), Java classes and members (`java.util.ArrayList`, `import com.example.*`, `javaObject`), MEX sources (`foo(x)` opens `mexFunction` in `foo.c`) and C libraries (`calllib` into the header given to `loadlibrary`)
- **Class inheritance**: inherited properties and methods resolve and complete; gutter icons for subclasses and overridden/overriding methods; Navigate | Implementation(s) and Super Method (Ctrl+U)
- **Parameter info** (Ctrl+P) with types and defaults from `arguments` blocks and name-value options
- **Reformat Code**: block indentation and spacing; matrices, cell arrays and command syntax keep their whitespace
- **Quick fixes**: remove unused or unresolved imports, unused functions and unreachable code; create a function that's called but not defined, in its own file or as a local function
- The settings page is now per project (the MATLAB path differs between projects)
- Indexes are rebuilt once after updating

## 1.0.0

First release as a separate plugin, continuing Matlab Support 0.8.5 by Liudmila Kornilova.
