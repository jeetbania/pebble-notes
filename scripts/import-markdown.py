#!/usr/bin/env python3
"""Convert local Markdown exports into a mergeable Pebble backup.

Requires markdown-it-py==3.0.0. Does not access a live library or fetch URLs.
Import the resulting .leafbackup through Pebble's Import Backup command.
"""
import argparse
import base64
import hashlib
import json
import mimetypes
import re
import uuid
from collections import Counter
from pathlib import Path
from urllib.parse import unquote, urlparse
from markdown_it import MarkdownIt

PARSER = MarkdownIt('commonmark').enable(['table', 'strikethrough'])
NAMESPACE = uuid.UUID('6b94bc80-d79d-48ed-8501-1e662579ce7d')


def utf16(text):
    return len(text.encode('utf-16-le')) // 2


def inline(tokens):
    """Translate parsed inline tokens, preserving UTF-16 offsets used by both apps."""
    text, spans, stack = '', [], []
    kinds = {'strong': 'bold', 'em': 'italic', 's': 'strike'}
    for token in tokens:
        name = token.type
        if name in ('strong_open', 'em_open', 's_open', 'link_open'):
            kind = 'link:' + (token.attrGet('href') or '') if name == 'link_open' else kinds[name[:-5]]
            stack.append((kind, utf16(text)))
        elif name in ('strong_close', 'em_close', 's_close', 'link_close'):
            kind, start = stack.pop()
            if utf16(text) > start:
                spans.append(dict(start=start, length=utf16(text)-start, kind=kind))
        elif name in ('softbreak', 'hardbreak'):
            text += '\n'
        elif name == 'image':
            # Missing/local image references remain legible; never silently drop them.
            caption = token.content or 'Image'
            destination = token.attrGet('src') or ''
            start = utf16(text)
            text += caption + (' (' + destination + ')' if destination else '')
            if destination:
                spans.append(dict(start=start, length=utf16(text)-start, kind='link:'+destination))
        else:
            text += token.content
    return text, spans


def block(kind='text', text='', spans=None, indent=0, style=None):
    result = dict(id=str(uuid.uuid4()), kind=kind, text=text, spans=spans or [], checked=False,
                  indent=min(indent, 8), mediaId='', caption='', presentation='large', cells=[])
    if style:
        result['textStyle'] = style
    return result


def convert(source, fallback_title, collection, source_path=None, source_root=None):
    tokens = PARSER.parse(source)
    title, blocks, lists, items, quotes, missing, blobs, attachments = '', [], [], [], 0, 0, {}, []
    i = 0
    while i < len(tokens):
        token = tokens[i]
        kind = token.type
        if kind in ('bullet_list_open', 'ordered_list_open'):
            lists.append('bullet' if kind == 'bullet_list_open' else 'number')
        elif kind in ('bullet_list_close', 'ordered_list_close'):
            lists.pop()
        elif kind == 'list_item_open':
            items.append(False)
        elif kind == 'list_item_close':
            items.pop()
        elif kind == 'blockquote_open':
            quotes += 1
        elif kind == 'blockquote_close':
            quotes -= 1
        elif kind in ('heading_open', 'paragraph_open'):
            child = tokens[i+1]
            text, spans = inline(child.children or [])
            if kind == 'heading_open' and token.tag == 'h1' and not blocks and not title and not lists and not quotes:
                title = text
            else:
                row_kind = lists[-1] if lists and items and not items[-1] else 'text'
                checked = re.match(r'^\[([ xX])\](?:\s+|$)', text) if row_kind in ('bullet', 'number') else None
                if checked:
                    row_kind = 'check'
                    cut = utf16(checked.group(0));text = text[len(checked.group(0)):]
                    spans = [dict(start=max(0,s['start']-cut),length=s['length']-max(0,cut-s['start']),kind=s['kind']) for s in spans if s['start']+s['length']>cut]
                style = {'h1':'title','h2':'subtitle'}.get(token.tag, 'headline') if kind == 'heading_open' else None
                if quotes:
                    prefix = '> '*quotes
                    text = prefix + text
                    spans = [dict(s,start=s['start']+utf16(prefix)) for s in spans]
                children = child.children or []
                image_tokens = [t for t in children if t.type == 'image']
                standalone_image = len(children) == 1 and len(image_tokens) == 1
                imported_image = False
                for image in image_tokens:
                    src = image.attrGet('src') or ''
                    local = None
                    if source_path and source_root and not urlparse(src).scheme:
                        candidate = (source_path.parent / unquote(src)).resolve()
                        if candidate.is_relative_to(source_root.resolve()) and candidate.is_file():
                            local = candidate
                    if local and standalone_image:
                        data = local.read_bytes(); digest = hashlib.sha256(data).hexdigest()
                        mime = mimetypes.guess_type(local.name)[0] or 'application/octet-stream'
                        attachments.append(dict(id=digest,name=local.name,mime=mime));blobs[digest]=base64.b64encode(data).decode()
                        b = block('image' if mime.startswith('image/') else 'file'); b['mediaId']=digest;b['caption']=image.content
                        blocks.append(b);imported_image=True
                    elif not local:
                        missing += 1
                if not imported_image:
                    b = block(row_kind,text,spans,max(0,len(lists)-1)+(1 if lists and items and items[-1] else 0),style)
                    if checked:
                        b['checked'] = checked.group(1).lower() == 'x'
                    blocks.append(b)
                if items:
                    items[-1] = True
            i += 2
        elif kind in ('fence', 'code_block'):
            # Apple exports sometimes indent a standalone checklist by four spaces.
            # Only reparse an unfenced block made entirely of list/checklist rows.
            lines = [line for line in token.content.splitlines() if line.strip()]
            if kind == 'code_block' and lines and all(re.match(r'^[-*+] \[([ xX])\](?:\s+|$)',line) for line in lines):
                nested,_,_ = convert(token.content, fallback_title, collection)
                for b in nested['blocks']:
                    b['indent'] = min(8,b['indent']+max(0,len(lists)-1))
                    blocks.append(b)
            else:
                # Pebble has no code block type; preserve code literally.
                blocks.append(block(text=token.content.rstrip('\n'),indent=max(0,len(lists)-1)))
        elif kind == 'hr':
            blocks.append(block('divider'))
        elif kind == 'table_open':
            rows = []; row = None
            i += 1
            while tokens[i].type != 'table_close':
                cell = tokens[i]
                if cell.type == 'tr_open':
                    row = []
                elif cell.type == 'inline':
                    cell_text, cell_spans = inline(cell.children or [])
                    # Cells do not support rich spans: retain destination URLs as readable text.
                    for span in cell_spans:
                        if span['kind'].startswith('link:') and span['kind'][5:] not in cell_text:
                            cell_text += ' ('+span['kind'][5:]+')'
                    row.append(cell_text)
                elif cell.type == 'tr_close':
                    rows.append(row)
                i += 1
            width = max(map(len, rows),default=1)
            for col in range(0,width,12):
                for start in range(0,len(rows),100):
                    b = block('table');b['cells']=[(r+['']*width)[col:min(col+12,width)] for r in rows[start:start+100]];blocks.append(b)
        elif kind == 'html_block':
            blocks.append(block(text=token.content.rstrip('\n')))
        i += 1
    if not any(b['kind'] in ('text','bullet','number','check','toggle') for b in blocks):
        blocks.append(block())
    # Reproducible block identities make repeated conversion of the same source identical.
    identity = hashlib.sha256((fallback_title+'\0'+source).encode()).hexdigest()
    for n,b in enumerate(blocks):
        b['id'] = str(uuid.uuid5(NAMESPACE,identity+':block:'+str(n)))
    note = dict(title=title or fallback_title, text='',spans=[],attachments=attachments,collection=collection,
                pinned=False,archived=False,deleted=False,blocks=blocks,tags=[],recordType='note',folderEmoji='',folderImage='',textScale=1.0)
    number = 0
    for b in blocks:
        if note['text']:
            note['text'] += '\n'
        prefix = ''
        if b['kind']=='number':
            number += 1;prefix=str(number)+'. '
        elif b['kind']=='bullet':
            prefix='• '
        elif b['kind']=='check':
            prefix='☑ ' if b['checked'] else '☐ '
        content='\n'.join(' | '.join(r) for r in b['cells']) if b['kind']=='table' else b['caption'] if b['kind'] in ('image','file') else b['text']
        start=utf16(note['text']+prefix);note['text']+=prefix+content
        note['spans'] += [dict(s,start=s['start']+start) for s in b['spans']]
        if b.get('textStyle') and content:
            note['spans'].append(dict(start=start,length=utf16(content),kind=b['textStyle']))
    return note, blobs, missing


def archive(folder, collection='Apple Notes'):
    records, media, stats = [], {}, Counter()
    for path in sorted(folder.rglob('*')):
        if not path.is_file() or path.suffix.lower() not in ('.md','.txt'):
            continue
        source = path.read_text(encoding='utf-8-sig')
        relative = str(path.relative_to(folder))
        subfolder = str(path.parent.relative_to(folder))
        target = collection if subfolder=='.' else collection+'/'+subfolder
        note, blobs, missing = convert(source,path.stem,target,path,folder)
        identity = relative+'\0'+source
        revision = dict(schema=2,id=str(uuid.uuid5(NAMESPACE,'revision:'+identity)),noteId=str(uuid.uuid5(NAMESPACE,'note:'+identity)),
                        deviceId='markdown-import',parents=[],createdAt=int(path.stat().st_mtime*1000),note=note)
        records.append(revision);media.update(blobs);stats.update(b['kind'] for b in note['blocks']);stats['missing_images']+=missing
    return dict(format='leaf-backup-1',revisions=records,media=media),dict(stats,notes=len(records))


if __name__=='__main__':
    ap=argparse.ArgumentParser(description=__doc__)
    ap.add_argument('folder',type=Path);ap.add_argument('output',type=Path);ap.add_argument('--collection',default='Apple Notes')
    args=ap.parse_args()
    result,stats=archive(args.folder,args.collection)
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(result,ensure_ascii=False,sort_keys=True),encoding='utf-8')
    args.output.chmod(0o600)
    print(json.dumps(stats,sort_keys=True))
