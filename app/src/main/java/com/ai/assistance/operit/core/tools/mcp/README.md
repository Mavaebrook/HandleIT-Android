# MCP tool arguments

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
