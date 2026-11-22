<div align="center">

<img src="docs/icon.svg" width="96" alt="">

# XenForo Templates

A PhpStorm plugin that understands XenForo 2 templates: HTML, LESS and CSS. It highlights `<xf:…>`, `{{ … }}` and `{$…}`, completes tags, functions, phrases and `$xf` fields, opens templates, macros, PHP code and JavaScript on Ctrl+Click, formats with proper indentation and finds the errors that would stop a template from compiling. The context menu builds add-on releases and runs other `cmd.php` commands.

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

`<xf:…>` tags, `{{ … }}` expressions and `{$…}` variables are highlighted on top of HTML, while HTML, CSS and JavaScript inside the template keep working as usual. Code inside `<xf:js>…</xf:js>` is parsed as JavaScript in `<script>`, and inside `<xf:css>…</xf:css>` as LESS in `<style>`. `{{ link(…) }}` in attributes, `<xf:if>` inside markup and `<xf:else />` branches that open different HTML produce no errors. The plugin is checked against all 1,198 templates of XenForo 2.3: 1,082 HTML and 116 LESS and CSS.

### Completion

- `<xf:…>` tags with their required attributes: picking `if` inserts `<xf:if is=""></xf:if>` and puts the caret inside the quotes. The tag list comes from `XF\Template\Compiler` in your project and attributes are completed from the tag classes, so suggestions match the installed XF version. Custom tags registered with `setTag()` in `config.php` or in an add-on are completed too and are not reported as unknown.
- Attributes of the tag under the caret.
- Template functions with parameters, filters after `|`, the `is empty` test.
- Functions, filters and tests that your add-ons register in PHP with `addFunction()`, `addFilter()` and `addTest()` in event listeners and class extensions.
- Variables used in the template and in macro arguments.
- `$xf` keys and everything behind them: entity columns, getters, relations and methods (`$xf.visitor.`, `$xf.visitor.Profile.`), board options with their sub-options (`$xf.options.registrationSetup.`), language and style fields. The data comes from the PHP classes and `_data` of your project, so entities of third-party add-ons show up too.

### Project navigation

The plugin knows templates, macros, phrases, routes and style properties from the `_output` folders of your add-ons and from `_data` of XenForo itself and installed add-ons. These names are completed and open on Ctrl+Click:

| Where | What |
|---|---|
| `<xf:include template="…">`, `<xf:wrap>`, `<xf:extends>` | template |
| `<xf:macro id="template::macro">` | template and the macro in it |
| `<xf:css src="….less">` | style template |
| `<xf:js src="…">`, `dev="…"`, `prod="…"` | file in `js/`; with `addon="…"` the add-on's `_files/js` is checked first, the same way XenForo looks it up |
| `data-xf-init="tooltip"`, `data-xf-click="overlay"` | JavaScript handler registered with `XF.Element.register` or `XF.Event.register` |
| `phrase('…')` | phrase, with its text shown in the completion list |
| `link('…')`, `link_type('…', '…')` | route |
| `property('…')` | style property |
| `<xf:avatar>`, `<xf:foreach>` and other tags | the tag class in `XF\Template\Compiler\Tag` or in add-on code |
| `$xf.visitor.username`, `$xf.visitor.canSearch()` | PHP class field or method |
| `$xf.options.boardTitle` | option in `options.xml` or `_output/options` |
| `phrase(…)`, `\|escape`, `demo_badge(…)` | `Templater` method or the add-on code that registers the function |

Phrase name modifiers such as `phrase('user_name:')`, `(…)`, `...` and `,` are stripped the same way `XF\Language` does, so the phrase itself opens. Renaming a template file updates the `template="…"` references to it. Macros of the current template are found right away, even before the file is saved.

The **Structure** tool window shows `<xf:…>` tags as a tree, macros as `xf:macro#name` and the main attribute next to other tags, followed by the usual HTML or LESS structure.

### Documentation and parameters

- <kbd>Ctrl</kbd>+<kbd>Q</kbd> or hovering shows the phrase text, the option title and explanation, tag attributes, function parameters and the add-on that registered a function. The function signature looks like the PHP documentation: highlighted, with the type and name of every parameter. Function and parameter descriptions come from the PHPDoc of the implementing method; without PHPDoc the parameters are still listed with their declared types. For `$xf` fields and methods, the PHP documentation opens; for `data-xf-init` and `data-xf-click`, the handler class and the file that declares it.
- <kbd>Ctrl</kbd>+<kbd>P</kbd> inside parentheses shows the parameters of a function, filter or method and highlights the current one.
- The phrase text is shown in gray after `{{ phrase('…') }}`, so you don't have to look it up.

### LESS and CSS

`.less` and `.css` templates from `_output/templates` open as XenForo templates on top of LESS and CSS: `<xf:if>`, `<xf:foreach>` and `{$…}` inside styles don't break parsing.

Every stylesheet gets what `XF\CssRenderer` adds to it: `public:setup.less` with its nested templates, `<prefix>_prepend.less` and everything included with `{{ include('…') }}`, `<xf:include>` and `@import`. Variables and mixins from them, `@xf-…` and `.xf-…()` from style properties and `@fa-var-…` are not reported as undeclared. Typos in other LESS names are still highlighted. Ctrl+Click on a variable or mixin opens its declaration: a mixin parameter, the file itself, an included template, `setup.less` or a style property.

### Add-on commands

Right-click an add-on folder or any file inside it to open the **XenForo: Vendor/AddOn** menu. The same menu is in the editor tab menu and under **Tools**.

| Item | Command |
|---|---|
| Build Release | `xf-addon:build-release`, the ZIP goes to `_releases` |
| Bump Version… | `xf-addon:bump-version`, `version_id` is calculated from the version string |
| Export Development Output | `xf-dev:export --addon=…`, from the database to `_output` |
| Import Development Output | `xf-dev:import --addon=…`, from `_output` to the database |
| Export _data XML | `xf-addon:export`, XML files to `_data` |
| Sync addon.json to Database | `xf-addon:sync-json` |
| Run cmd.php Command… | any `cmd.php` command, the last one is remembered |

PhpStorm saves open files before a command starts. The output goes to the **Run** tool window, where the command can be stopped or run again. After a build, a notification links to the ZIP, and files changed by a command show up in the project right away.

Commands run with the project PHP interpreter from **Settings → PHP**. If the forum needs a different PHP version, pick it in **Settings → PHP → XenForo**: for example `php82` in a XenForo 2.2 project and plain `php` in a 2.3 one. Remote interpreters work too: Docker, WSL, SSH. When no interpreter is set anywhere, `php` from `PATH` is used. The same page takes the forum folder with `cmd.php` for add-ons kept outside the forum, for example linked with a symlink.

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

PhpStorm 2026.2 or later is required. If yours is older, update it first with **Help → Check for Updates**.

1. Download `nt-xenforo-phpstorm-<version>.zip` from the [releases page](https://github.com/mityakuss-pixel/nt-xenforo-phpstorm/releases/latest). Do not unpack it.
2. Open the PhpStorm settings: <kbd>Ctrl</kbd>+<kbd>Alt</kbd>+<kbd>S</kbd>, or <kbd>⌘</kbd>+<kbd>,</kbd> on macOS.
3. Go to **Plugins**, click the gear icon next to the tabs and choose **Install Plugin from Disk…**
4. Select the downloaded archive and click **OK**. Restart PhpStorm if it asks you to.

**Check.** Open any template from `src/addons/<add-on>/_output/templates/public`. The tab gets a purple `{$}` icon, and `<xf:…>` tags and `{{ … }}` expressions are highlighted.

**Update.** Download the new archive and install it the same way, over the old version.

**Remove.** **Settings → Plugins → Installed → XenForo Templates → Uninstall.**

## How templates are detected

Files `*.html`, `*.less` and `*.css` in `_output/templates/public`, `admin` and `email` are XenForo templates with no setup. To open other files the same way, add their pattern in **Settings → Editor → File Types → XenForo Template**. In projects without these folders the plugin changes nothing.

Names for completion and navigation come from `_output` (phrases, templates, routes, options, style properties), from `_data/*.xml` and from the PHP code of add-ons. Open the whole forum folder in PhpStorm rather than a single add-on, and XenForo's own names and classes will appear in completion too.

## Shortcuts

| Keys | Action |
|---|---|
| <kbd>Ctrl</kbd>+<kbd>Space</kbd> | completion anywhere in a template |
| <kbd>Ctrl</kbd>+Click, <kbd>Ctrl</kbd>+<kbd>B</kbd> | go to a template, macro, phrase, route, field or function |
| <kbd>Ctrl</kbd>+<kbd>Q</kbd> | documentation for a phrase, option, tag or function |
| <kbd>Ctrl</kbd>+<kbd>P</kbd> | parameters of a function, filter or method |
| <kbd>Ctrl</kbd>+<kbd>Alt</kbd>+<kbd>L</kbd> | format the template |
| <kbd>Ctrl</kbd>+<kbd>/</kbd> | comment the line or selection with `<xf:comment>` |

On macOS, use <kbd>⌘</kbd> instead of <kbd>Ctrl</kbd>.

## Settings

| What | Where |
|---|---|
| Indentation, tabs by default | Settings → Editor → Code Style → XenForo Template |
| Colors | Settings → Editor → Color Scheme → XenForo Template |
| Unknown tag check | Settings → Editor → Inspections → XenForo |
| Phrase text after `phrase()` | Settings → Editor → Inlay Hints, **Phrase text** |
| PHP and forum folder for `cmd.php` commands | Settings → PHP → XenForo |

## Limitations

- Templates edited in the admin control panel rather than as files are not visible to the plugin.
- Columns that an add-on adds to a core entity through a class extension are not completed: the plugin reads `@property` docblocks of the entity class itself only.
- Names taken from variables, such as `{{ phrase($name) }}`, are not checked or completed.
- Add-on commands need a working forum database, and `xf-dev:export` and `xf-dev:import` only work with development mode enabled in `config.php`.

## Building from source

JDK 25 is required.

```shell
./gradlew buildPlugin
```

The archive is written to `build/distributions`. Run the tests with `./gradlew test`.

## License

[MIT](LICENSE)
