#!/usr/bin/env python3
"""Regenerate the bundled MATLAB function docs from MathWorks' function index.

Usage:
    curl -L --compressed -A "Mozilla/5.0" -o data/functions.xml \\
        https://www.mathworks.com/help/matlab/referencelist_function_cat.xml
    python3 data/update_docs.py data/functions.xml resources/docs

Writes function_list, function_descriptions.properties and function_url.properties.
"""
import os
import re
import sys
import xml.etree.ElementTree as ET

NS = '{https://www.mathworks.com/help/ref/data}'


def escape_properties(text, is_key=False):
    """java.util.Properties escaping: non-ASCII as \\uXXXX, specials backslashed."""
    out = []
    for i, ch in enumerate(text):
        if ch == '\\':
            out.append('\\\\')
        elif ch in '=:#!' or (ch == ' ' and (is_key or i == 0)):
            out.append('\\' + ch)
        elif ord(ch) > 126 or ord(ch) < 32:
            out.append('\\u%04x' % ord(ch))
        else:
            out.append(ch)
    return ''.join(out)


def main(xml_path, out_dir):
    names, urls, descriptions = [], {}, {}
    for ref in ET.parse(xml_path).getroot().iter(NS + 'ref'):
        target = ref.get('target')
        purpose_el = ref.find(NS + 'purpose')
        purpose = re.sub(r'\s+', ' ', ''.join(purpose_el.itertext())).strip() if purpose_el is not None else ''
        # One page can document several names: "if, elseif, else"
        for name in ref.get('name', '').split(','):
            name = name.strip()
            if not re.fullmatch(r'[A-Za-z][A-Za-z0-9_.]*', name) or name in urls:
                continue
            names.append(name)
            if target:
                urls[name] = target
            if purpose:
                descriptions[name] = purpose

    with open(os.path.join(out_dir, 'function_list'), 'w', encoding='ascii') as f:
        f.write('\n'.join(names) + '\n')
    for file_name, data in (('function_url.properties', urls), ('function_descriptions.properties', descriptions)):
        with open(os.path.join(out_dir, file_name), 'w', encoding='ascii') as f:
            for name in names:
                if name in data:
                    f.write(escape_properties(name, True) + '=' + escape_properties(data[name]) + '\n')
    print(f'{len(names)} functions, {len(urls)} URLs, {len(descriptions)} descriptions')


if __name__ == '__main__':
    main(*sys.argv[1:3])
