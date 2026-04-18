# Pdf4Eclipse Fork

This repository is a maintenance fork of [Borisvl/Pdf4Eclipse](https://github.com/Borisvl/Pdf4Eclipse).
It keeps the original Eclipse PDF editor usable on newer Eclipse and Java environments while preserving the upstream design as much as possible.

## Purpose

This fork focuses on practical maintenance rather than a full redesign.

- Keep the plugin usable on modern Eclipse
- Adjust the workspace and bundle layout for current PDE usage
- Add Tycho-based reproducible builds
- Publish an installable p2 update site
- Preserve upstream behavior where possible

## Origin

This fork is based on the following upstream projects.

- Pdf4Eclipse: [Borisvl/Pdf4Eclipse](https://github.com/Borisvl/Pdf4Eclipse)
- PDFrenderer fork: [Borisvl/PDFrenderer](https://github.com/Borisvl/PDFrenderer)

Rendering support follows the same general upstream approach.

- Sun PDF Renderer lineage
- JPedal LGPL edition

This is not an official successor of the upstream project.

## About This Fork

This repository is a fork and maintenance port, not a clean-room reimplementation.
To reduce regression risk, most Java package names are intentionally kept close to upstream, while the following were updated for the fork.

- OSGi bundle IDs
- feature IDs
- repository and update site layout
- public editor, command, and preference IDs
- selected Java 21 compatibility fixes

## Codex Usage

This fork was prepared with assistance from OpenAI Codex.

Codex was used to support tasks such as:

- importing and reorganizing the upstream project structure
- adjusting PDE and Tycho build configuration
- applying compatibility fixes for newer Java and Eclipse versions
- resolving dependency and workspace issues
- preparing repository documentation and packaging

Design decisions and final acceptance remain the responsibility of the repository maintainer.

## Target Environment

- Eclipse 2026 stream
- Java 21

The practical baseline for this fork is simple: the plugin should load in Eclipse and open PDF files successfully.

## Repository Layout

- `io.github.h44bc.pdf4eclipse`
  Main editor plugin
- `io.github.h44bc.pdf4eclipse.help`
  Help content plugin
- `io.github.h44bc.pdf4eclipse.jpedal`
  Bundled JPedal-related binary plugin
- `io.github.h44bc.pdf4eclipse.feature`
  Eclipse feature
- `io.github.h44bc.pdf4eclipse.repository`
  p2 update site definition

## Build

Build everything from the repository root:

```bash
mvn -U clean verify
```

The generated p2 repository is created here:

```text
io.github.h44bc.pdf4eclipse.repository/target/repository
```

## Install From GitHub Pages

This repository is configured so that GitHub Actions can build the p2 repository and publish it through GitHub Pages.

### Repository owner setup

1. Open `Settings > Pages`
2. Set `Source` to `GitHub Actions`
3. Push to `main` or `master`, or run the `Publish Update Site` workflow manually
4. After deployment completes, the update site will be available at:

```text
https://<github-user>.github.io/<repository-name>/
```

For this repository, that becomes:

```text
https://afloy.github.io/PDF4Eclipse/
```

### End user installation

1. Open Eclipse
2. Open `Help > Install New Software...`
3. Enter the GitHub Pages URL of the published update site
4. Select the feature and install it
5. Restart Eclipse when prompted

## Development In Eclipse

Import these projects into an Eclipse PDE workspace.

- `io.github.h44bc.pdf4eclipse`
- `io.github.h44bc.pdf4eclipse.help`
- `io.github.h44bc.pdf4eclipse.jpedal`
- `io.github.h44bc.pdf4eclipse.feature`
- `io.github.h44bc.pdf4eclipse.repository`

For runtime testing, use an `Eclipse Application` launch configuration and add required plug-ins as needed.

## Compatibility Policy

This fork prioritizes conservative maintenance.

- Prefer upstream-compatible behavior
- Avoid large renderer replacements
- Avoid unnecessary package renames
- Favor incremental fixes over invasive redesign

## License Notes

License notices are inherited from the upstream project and related bundled components.
Check each module for the applicable license text and notices.

## Acknowledgements

Thanks to Boris von Loesch and the maintainers of the related rendering libraries.
This fork exists because of their original work.
