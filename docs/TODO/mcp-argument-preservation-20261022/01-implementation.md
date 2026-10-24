# Behavior and validation

## Implemented

The executor's existing conversion now lives in an internal companion function so regression
tests exercise the same boundary used immediately before `McpRuntimeSession.callTool`.
It reads a property's type only when the schema supplies a string type name. Missing, unknown,
and union types leave values unchanged. The conversion no longer logs argument contents.

`MCPToolParameter` converts only top-level strings with an explicit supported type. Parsed JSON
containers retain quoted strings and convert JSON null to Kotlin null without dropping array
positions or object keys. Structured arguments retain their identity and element types.
Invalid booleans remain unchanged instead of becoming false. If JSON parsing fails, the original
string is retained; the old comma-splitting repair has been removed.

The bridge's existing JSON serializer is now internal under the name `toMcpBridgeArguments`.
Tests exercise it after executor conversion, then parse the emitted JSON to verify nested types
and null positions. Serialization behavior itself is unchanged.

## Regression coverage

- Declared strings that resemble numbers, booleans, arrays, and objects
- Whitespace and a long UTF-8 string containing multibyte characters
- Nested arrays and objects with nulls, quoted literals, numbers, and booleans
- Structured lists/maps, missing or unsupported schemas, and union types
- Explicit scalar conversions, parse failures, and the parameter-instance entry point

## Validation status and limits

Source inspection is complete. No build or test has run locally. Hosted Android Build and Android
Tests remain pending; add their run URLs and exact tested SHA before marking validation complete.

This change does not implement full JSON Schema validation, resolve schema references/unions,
or replace the platform JSON parser. Servers that relied on guessing types without a schema
must declare the intended parameter type. The tests cover production conversion and the bridge
JSON payload seam; they do not claim an end-to-end remote MCP network test.
