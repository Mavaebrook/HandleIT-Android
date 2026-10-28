# MCP tool arguments

## Remote registration

Enabled remote entries in `pluginMetadata` are registered with the AI namespace when configuration
is loaded or refreshed, independently of startup network checks and cached tool availability.
Use the configured server ID with `use_package`; display names are not aliases. Activation still
connects to the server and obtains its tools, so an unavailable endpoint returns a connection error.
Registration is not proof of reachability. Disabling/removing a remote entry removes its registration;
late discovery callbacks must respect the current enabled state. Role-card tool restrictions remain
in force. No configuration is copied between the separate Operit and HandleIT apps.

## Argument conversion

`MCPToolExecutor` converts string-valued tool parameters using each property's explicit
`inputSchema.properties` type before dispatch to `McpRuntimeSession`.

Declared strings retain their contents exactly, even when they look like numbers, booleans,
or JSON. Missing, unsupported, and union types also retain the original value. Servers should
declare one supported type when they require top-level conversion.

Array and object parameters are parsed as JSON containers. Quoted values remain strings;
null entries retain their array positions and object keys. Already structured values pass
through unchanged. A parse failure preserves the original string without comma-splitting
repair, and invalid boolean strings are not converted to false. This conversion is not a
complete JSON Schema validator.

Argument conversion does not log parameter contents. The existing replacement-character
validation remains responsible for reporting corrupted arguments without exposing their text.
