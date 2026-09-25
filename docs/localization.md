# Localization

Contents Director uses message bundles for user-facing UI text.

## Bundled languages

The built-in bundles are:

| Locale | File | Status |
|---|---|---|
| English / fallback | `messages.xml` | Complete; canonical key set |
| Japanese (`ja`) | `messages_ja.xml` | Complete |
| Simplified Chinese (`zh_CN`) | `messages_zh_CN.xml` | Complete |
| Portuguese (`pt`) | `messages_pt.xml` | Partial; missing keys fall back to English |

The active locale defaults to the JVM locale returned by `Locale.getDefault()`. Contents Director does not currently expose a separate modpack setting for selecting a UI language.

## Bundle location and format

Built-in bundles are stored under:

```text
src/main/resources/com/juanmuscaria/modpackdirector/i18n/
```

They use Java XML properties format. Each entry is a message key and translated value:

```xml
<entry key="modpack_director.selection_page.next_button_label">Next</entry>
```

The English `messages.xml` file is the canonical key set. Complete translations should contain every key from that file.

## Placeholders

Parameterized messages use indexed placeholders such as `{0}`:

```xml
<entry key="modpack_director.progress.install">Installing {0}</entry>
```

Translations may move a placeholder to a grammatically appropriate position, but must preserve the same placeholder identifiers. Tests verify placeholder compatibility for the complete Japanese and Simplified Chinese bundles.

## Modpack-provided overrides

Contents Director also loads an external message bundle from the platform configuration directory using the basename:

```text
messages
```

For the normal Minecraft layout, the configuration directory is typically `config/mod-director/`. For example:

```text
config/mod-director/messages.xml
config/mod-director/messages_ja.xml
config/mod-director/messages_zh_CN.xml
```

External messages take precedence over built-in messages. The bundled classpath messages remain the parent source, so a modpack can override only the keys it wants to customize.

Use the same Java XML properties format and keep placeholders compatible with the canonical entry.

## macOS external UI helper

On macOS, interactive Swing UI may run in a separate helper JVM. Localized strings are resolved by the main Contents Director process first and are then passed to the helper request. This keeps JVM-locale selection and modpack-provided message overrides consistent between the normal UI and the helper UI.

## Adding or updating a translation

1. Use `messages.xml` as the canonical source.
2. Add or update the locale-specific file using Java's resource-bundle naming convention.
3. Preserve all canonical keys for translations intended to be complete.
4. Preserve placeholder identifiers such as `{0}`.
5. Run:

```bash
./gradlew test
```

The localization tests check that the Japanese and Simplified Chinese bundles contain the full canonical key set and compatible placeholders.

## Fallback behavior

If a locale-specific bundle does not define a key, the message source falls back through the resource-bundle hierarchy to the built-in English bundle. This is intentionally retained for legacy partial translations such as Portuguese.

If a key cannot be resolved at all, Contents Director displays the key itself (and parameters, when present) rather than failing the installation solely because a translation is missing.
