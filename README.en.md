<div align="center">

<img src="docs/icon.svg" width="96" alt="">

# XenForo Templates

A PhpStorm plugin that understands XenForo 2 templates. It highlights `<xf:…>`, `{{ … }}` and `{$…}`, completes tags, functions and phrases, opens templates and macros on Ctrl+Click, formats with proper indentation and finds the errors that would stop a template from compiling.

[![PhpStorm](https://img.shields.io/badge/PhpStorm-2026.2%2B-7c3aed?style=flat-square)](https://www.jetbrains.com/phpstorm/)
[![XenForo](https://img.shields.io/badge/XenForo-2.x-7c3aed?style=flat-square)](https://xenforo.com)
[![Release](https://img.shields.io/github/v/release/mityakuss-pixel/nt-xenforo-phpstorm?style=flat-square&label=Release&color=7c3aed)](https://github.com/mityakuss-pixel/nt-xenforo-phpstorm/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/mityakuss-pixel/nt-xenforo-phpstorm/total?style=flat-square&label=Downloads&color=7c3aed)](https://github.com/mityakuss-pixel/nt-xenforo-phpstorm/releases)
[![License](https://img.shields.io/github/license/mityakuss-pixel/nt-xenforo-phpstorm?style=flat-square&label=License&color=7c3aed)](LICENSE)

[Русский](README.md) · **English**

<img src="docs/demo.gif" width="900" alt="Formatting a template, completing tags and phrases, jumping to a macro with Ctrl+Click">

</div>

## Who it is for

XenForo add-on and style developers who edit templates in PhpStorm in development mode, where templates live as files in `_output`.

Without the plugin, PhpStorm treats a template as plain HTML. It reports `{{ link(…) }}` in attributes and `<xf:if>` tags as errors, breaks the indentation when formatting and knows nothing about XenForo templates, phrases or functions.

## Features

### Syntax without false errors

`<xf:…>` tags, `{{ … }}` expressions and `{$…}` variables are highlighted on top of HTML, while HTML, CSS and JavaScript inside the template keep working as usual. `{{ link(…) }}` in attributes, `<xf:if>` inside markup and `<xf:else />` branches that open different HTML produce no errors. The plugin is checked against all 1,082 HTML templates of XenForo 2.3.

### Completion

- `<xf:…>` tags with their required attributes: picking `if` inserts `<xf:if is=""></xf:if>` and puts the caret inside the quotes.
- Attributes of the tag under the caret.
- Template functions with parameters, filters after `|`, the `is empty` test.
- Variables used in the template and in macro arguments, `$xf.visitor`, `$xf.options` and other keys.

### Project navigation

The plugin knows templates, macros, phrases, routes and style properties from the `_output` folders of your add-ons and from `_data` of XenForo itself and installed add-ons. These names are completed and open on Ctrl+Click:

| Where | What |
|---|---|
| `<xf:include template="…">`, `<xf:wrap>`, `<xf:extends>` | template |
| `<xf:macro id="template::macro">` | template and the macro in it |
| `<xf:css src="….less">` | style template |
| `phrase('…')` | phrase, with its text shown in the completion list |
| `link('…')`, `link_type('…', '…')` | route |
| `property('…')` | style property |

Renaming a template file updates the `template="…"` references to it.

### Formatting

<kbd>Ctrl</kbd>+<kbd>Alt</kbd>+<kbd>L</kbd> indents with tabs, like XenForo's own templates. `<xf:else />` and `<xf:elseif />` line up with their `<xf:if>`:

```html
<xf:if is="$threads is not empty">
	<ol class="block-body">
		<xf:foreach loop="$threads" value="$thread">
			<li>{$thread.title}</li>
		</xf:foreach>
	</ol>
<xf:else />
	<div class="blockMessage">{{ phrase('there_are_no_threads_to_display') }}</div>
</xf:if>
```

### Compile errors

The plugin marks what XenForo would refuse to compile: unclosed and mismatched tags, `<xf:else />` outside `<xf:if>`, `<xf:elseif />` after `<xf:else />`, unknown tags and unfinished expressions.

### Typing assistance

- `{{` adds the closing ` }}`, a quote inside an expression gets its pair.
- `>` in an opening `<xf:foreach …>` adds `</xf:foreach>`.
- Enter between an opening and a closing tag expands the empty block.
- <kbd>Ctrl</kbd>+<kbd>/</kbd> wraps the line or selection in `<xf:comment>`, long blocks can be folded.

## Installation

1. Download `nt-xenforo-phpstorm-1.0.0.zip` from the [releases page](https://github.com/mityakuss-pixel/nt-xenforo-phpstorm/releases/latest). Do not unpack it.
2. Open the PhpStorm settings: <kbd>Ctrl</kbd>+<kbd>Alt</kbd>+<kbd>S</kbd>, or <kbd>⌘</kbd>+<kbd>,</kbd> on macOS.
3. Go to **Plugins**, click the gear icon next to the tabs and choose **Install Plugin from Disk…**
4. Select the downloaded archive and click **OK**. Restart PhpStorm if it asks you to.

**Check.** Open any template from `src/addons/<add-on>/_output/templates/public`. The tab gets a purple `{$}` icon, and `<xf:…>` tags and `{{ … }}` expressions are highlighted.

**Update.** Download the new archive and install it the same way, over the old version.

**Remove.** **Settings → Plugins → Installed → XenForo Templates → Uninstall.**

## How templates are detected

Files `*.html` in `_output/templates/public`, `admin` and `email` are XenForo templates with no setup. To open other files the same way, add their pattern in **Settings → Editor → File Types → XenForo Template**.

Names for completion and navigation come from `_output` (phrases, templates, routes, style properties) and from `_data/*.xml`. Open the whole forum folder in PhpStorm rather than a single add-on, and XenForo's own names will appear in completion too.

## Shortcuts

| Keys | Action |
|---|---|
| <kbd>Ctrl</kbd>+<kbd>Space</kbd> | completion anywhere in a template |
| <kbd>Ctrl</kbd>+Click, <kbd>Ctrl</kbd>+<kbd>B</kbd> | go to a template, macro, phrase or route |
| <kbd>Ctrl</kbd>+<kbd>Alt</kbd>+<kbd>L</kbd> | format the template |
| <kbd>Ctrl</kbd>+<kbd>/</kbd> | comment the line or selection with `<xf:comment>` |

On macOS, use <kbd>⌘</kbd> instead of <kbd>Ctrl</kbd>.

## Settings

| What | Where |
|---|---|
| Indentation, tabs by default | Settings → Editor → Code Style → XenForo Template |
| Colors | Settings → Editor → Color Scheme → XenForo Template |
| Unknown tag check | Settings → Editor → Inspections → XenForo |

## Limitations

- `.less` and `.css` templates open as regular stylesheets.
- Functions and filters that XenForo add-ons register in code are not completed, and they are not marked as errors either.
- Templates edited in the admin control panel rather than as files are not visible to the plugin.

## Building from source

JDK 25 is required.

```shell
./gradlew buildPlugin
```

The archive is written to `build/distributions`. Run the tests with `./gradlew test`.

## License

[MIT](LICENSE)
