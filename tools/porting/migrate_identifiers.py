#!/usr/bin/env python3
"""One-time, checked ResourceLocation -> Identifier API migration for Forge 26.2.

Evidence: 26.2-65.1.0 generated Identifier.java and FriendlyByteBuf.java.
The constructor migration counts only top-level arguments; it does not guess
from commas inside string literals or nested method invocations.
"""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
TOKEN = re.compile(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/|[()]|,')


def migrate(source):
    edits = []
    for match in re.finditer(r'\bnew ResourceLocation\s*\(', source):
        depth, commas = 1, 0
        for token in TOKEN.finditer(source, match.end()):
            value = token[0]
            if value == '(':
                depth += 1
            elif value == ')':
                depth -= 1
                if depth == 0:
                    break
            elif value == ',' and depth == 1:
                commas += 1
        else:
            raise ValueError("Unclosed ResourceLocation constructor")
        if commas not in (0, 1):
            raise ValueError("Unexpected ResourceLocation constructor arity")
        method = 'parse' if commas == 0 else 'fromNamespaceAndPath'
        edits.append((match.start(), match.end(), f'Identifier.{method}('))
    for start, end, replacement in reversed(edits):
        source = source[:start] + replacement + source[end:]
    source = re.sub(r'\bResourceLocation\b', 'Identifier', source)
    source = source.replace('readResourceLocation', 'readIdentifier').replace('writeResourceLocation', 'writeIdentifier')
    source = source.replace('new Identifier.Serializer()', 'new com.tacz.guns.resource.serialize.IdentifierSerializer()')
    return source


if __name__ == '__main__':
    count = 0
    for path in (ROOT / 'src/main/java').rglob('*.java'):
        old = path.read_text(encoding='utf-8')
        new = migrate(old)
        if old != new:
            path.write_text(new, encoding='utf-8', newline='\n')
            count += 1
    print(f'Migrated {count} files; no source files excluded.')
